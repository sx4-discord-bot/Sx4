package com.sx4.bot.managers;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.exception.HttpException;
import club.minnced.discord.webhook.send.WebhookEmbed;
import club.minnced.discord.webhook.send.WebhookMessage;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.mongodb.client.model.Updates;
import com.sx4.bot.database.Database;
import com.sx4.bot.entities.management.logger.LoggerEvent;
import com.sx4.bot.utility.ExceptionUtility;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;
import okhttp3.OkHttpClient;
import org.bson.Document;

import java.util.*;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class LoggerManager {

    public class Request {

        private final JDA jda;
        private final long channelId;
        private final List<WebhookEmbed> embeds;
        private final LoggerEvent event;
        private final Document logger;

        public Request(JDA jda, long channelId, List<WebhookEmbed> embeds, LoggerEvent event, Document logger) {
            this.jda = jda;
            this.channelId = channelId;
            this.embeds = embeds;
            this.event = event;
            this.logger = logger;
        }

        public JDA getJDA() {
            return this.jda;
        }

        public long getChannelId() {
            return this.channelId;
        }

        public TextChannel getChannel() {
            return this.jda.getTextChannelById(this.channelId);
        }

        public List<WebhookEmbed> getEmbeds() {
            return this.embeds;
        }

        public LoggerEvent getEvent() {
            return this.event;
        }

        public Document getLogger() {
            return this.logger;
        }

    }

    private static final int MAX_RETRIES = 3;

    private static final LoggerManager INSTANCE = new LoggerManager();

    public static LoggerManager get() {
        return LoggerManager.INSTANCE;
    }

    private final Map<Long, WebhookClient> webhooks;
    private final Map<Long, BlockingDeque<Request>> queue;

    private final OkHttpClient webhookClient = new OkHttpClient();
    private final ScheduledExecutorService webhookExecutor = Executors.newSingleThreadScheduledExecutor();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private LoggerManager() {
        this.queue = new HashMap<>();
        this.webhooks = new HashMap<>();
    }

    public void createWebhook(TextChannel channel, BlockingDeque<Request> deque, Request request, int retries) {
        channel.createWebhook("Sx4 - Logger").queue(webhook -> {
            WebhookClient webhookClient = new WebhookClientBuilder(webhook.getUrl())
                .setExecutorService(this.webhookExecutor)
                .setHttpClient(this.webhookClient)
                .build();

            this.webhooks.put(channel.getIdLong(), webhookClient);

            Document webhookData = new Document("id", webhook.getIdLong())
                .append("token", webhook.getToken());

            Database.get().updateChannelById(channel.getIdLong(), Updates.set("logger.webhook", webhookData)).whenComplete((result, exception) -> {
                if (ExceptionUtility.sendErrorMessage(exception)) {
                    return;
                }

                deque.addFirst(request);
                this.handleQueue(deque, retries + 1);
            });
        });
    }

    public void handleQueue(BlockingDeque<Request> deque, int retries) {
        this.executor.submit(() -> {
            Request request = deque.poll();
            if (request == null) {
                return;
            }

            if (retries == LoggerManager.MAX_RETRIES) {
                this.handleQueue(deque, 0);
                return;
            }

            long channelId = request.getChannelId();
            TextChannel channel = request.getChannel();

            if (channel == null) {
                Database.get().deleteChannelById(channelId).whenComplete(Database.exceptionally());

                this.queue.remove(channelId);
                this.webhooks.remove(channelId);

                return;
            }

            int length = 0; // embed length

            List<WebhookEmbed> embeds = new ArrayList<>(request.getEmbeds());
            List<Request> skippedRequests = new ArrayList<>();

            Document logger = request.getLogger();
            List<Document> blacklist = logger.getEmbedded(List.of("blacklist", "entities"), Collections.emptyList());
            long events = logger.get("events", 0L);

            while (length < MessageEmbed.EMBED_MAX_LENGTH_BOT && embeds.size() < 10) {
                Request nextRequest = deque.poll();
                if (nextRequest == null) {
                    break;
                }

                Document nextLogger = nextRequest.getLogger();
                boolean equal = blacklist.equals(nextLogger.getEmbedded(List.of("blacklist", "entities"), Collections.emptyList())) && nextLogger.get("events", 0L) == events;

                List<WebhookEmbed> nextEmbeds = nextRequest.getEmbeds();
                int nextLength = 0; // embed length
                if (!equal || embeds.size() + nextEmbeds.size() > 10 || length + nextLength > MessageEmbed.EMBED_MAX_LENGTH_BOT) {
                    skippedRequests.add(nextRequest);
                    continue;
                }

                embeds.addAll(nextEmbeds);
            }

            // Keep order of logs
            skippedRequests.forEach(deque::addFirst);

            Document webhookData = logger.get("webhook", Document.class);

            WebhookMessage message = new WebhookMessageBuilder()
                .addEmbeds(embeds)
                .setAvatarUrl(request.getJDA().getSelfUser().getEffectiveAvatarUrl())
                .build();

            WebhookClient webhook;
            if (this.webhooks.containsKey(channelId)) {
                webhook = this.webhooks.get(channelId);
            } else if (webhookData == null) {
                if (channel.getGuild().getSelfMember().hasPermission(channel, Permission.MANAGE_WEBHOOKS)) {
                    this.createWebhook(channel, deque, request, retries);
                }

                this.handleQueue(deque, 0);
                return;
            } else {
                webhook = new WebhookClientBuilder(webhookData.getLong("id"), webhookData.getString("token"))
                    .setExecutorService(this.webhookExecutor)
                    .setHttpClient(this.webhookClient)
                    .build();

                this.webhooks.put(channelId, webhook);
            }

            webhook.send(message).whenComplete((result, exception) -> {
                if (exception instanceof HttpException && ((HttpException) exception).getCode() == 404) {
                    if (channel.getGuild().getSelfMember().hasPermission(channel, Permission.MANAGE_WEBHOOKS)) {
                        this.createWebhook(channel, deque, request, retries);
                    }

                    return;
                }

                if (ExceptionUtility.sendErrorMessage(exception)) {
                    deque.addFirst(request);
                    this.handleQueue(deque, retries + 1);

                    return;
                }

                this.handleQueue(deque, 0);
            });
        });
    }

}
