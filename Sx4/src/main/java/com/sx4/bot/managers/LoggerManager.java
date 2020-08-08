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
import java.util.concurrent.*;

public class LoggerManager {

    public class Request {

        private final JDA jda;
        private final long channelId;
        private final List<WebhookEmbed> embeds;
        private final Document logger;

        public Request(JDA jda, long channelId, List<WebhookEmbed> embeds, Document logger) {
            this.jda = jda;
            this.channelId = channelId;
            this.embeds = embeds;
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
    private final BlockingDeque<Request> queue;

    private final OkHttpClient webhookClient = new OkHttpClient();
    private final ScheduledExecutorService webhookExecutor = Executors.newSingleThreadScheduledExecutor();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private LoggerManager() {
        this.queue = new LinkedBlockingDeque<>();
        this.webhooks = new HashMap<>();
    }

    public void createWebhook(TextChannel channel, BlockingDeque<Request> deque, List<Request> requests, int retries) {
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

                requests.forEach(deque::addFirst);
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

                this.webhooks.remove(channelId);
                this.handleQueue(deque, 0);

                return;
            }

            int length = 0; // embed length

            List<WebhookEmbed> embeds = new ArrayList<>(request.getEmbeds());

            List<Request> skippedRequests = new ArrayList<>(), requests = new ArrayList<>();
            requests.add(request);

            Request nextRequest;
            while ((nextRequest = deque.poll()) != null) {
                List<WebhookEmbed> nextEmbeds = nextRequest.getEmbeds();
                int nextLength = 0; // embed length
                if (request.getChannelId() != nextRequest.getChannelId() || embeds.size() + nextEmbeds.size() > 10 || length + nextLength > MessageEmbed.EMBED_MAX_LENGTH_BOT) {
                    skippedRequests.add(nextRequest);
                    continue;
                }

                embeds.addAll(nextEmbeds);
                requests.add(nextRequest);
            }

            // Keep order of logs
            skippedRequests.forEach(deque::addFirst);

            Document logger = request.getLogger();
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
                    this.createWebhook(channel, deque, requests, retries);
                    return;
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
                        this.createWebhook(channel, deque, requests, retries);
                        return;
                    }

                    this.handleQueue(deque, 0);
                    return;
                }

                if (ExceptionUtility.sendErrorMessage(exception)) {
                    requests.forEach(deque::addFirst);
                    this.handleQueue(deque, retries + 1);

                    return;
                }

                this.handleQueue(deque, 0);
            });
        });
    }

    public void queue(Request request) {
        if (this.queue.isEmpty()) {
            this.queue.add(request);
            this.handleQueue(this.queue, 0);
        } else {
            this.queue.add(request);
        }
    }

    public void queue(TextChannel channel, Document logger, LoggerEvent event, WebhookEmbed... embeds) {
        this.queue(channel, logger, event, Arrays.asList(embeds));
    }

    public void queue(TextChannel channel, Document logger, LoggerEvent event, List<WebhookEmbed> embeds) {
        List<Request> requests = new ArrayList<>();

        double messages = Math.ceil((double) embeds.size() / 10);
        for (int i = 0; i < messages; i++) {
            List<WebhookEmbed> splitEmbeds = embeds.subList(i * 10, i == messages - 1 ? embeds.size() : (i + 1) * 10);

            requests.add(new Request(channel.getJDA(), channel.getIdLong(), splitEmbeds, logger));
        }

        requests.forEach(this::queue);
    }

}
