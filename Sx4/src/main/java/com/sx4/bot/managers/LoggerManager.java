package com.sx4.bot.managers;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.exception.HttpException;
import club.minnced.discord.webhook.send.WebhookEmbed;
import club.minnced.discord.webhook.send.WebhookMessage;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.sx4.bot.database.Database;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.LoggerUtility;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;
import okhttp3.OkHttpClient;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.*;
import java.util.concurrent.*;

public class LoggerManager {

    public class Request {

        private final JDA jda;
        private final long guildId;
        private final long channelId;
        private final List<WebhookEmbed> embeds;
        private final Document logger;

        public Request(JDA jda, long guildId, long channelId, List<WebhookEmbed> embeds, Document logger) {
            this.jda = jda;
            this.guildId = guildId;
            this.channelId = channelId;
            this.embeds = embeds;
            this.logger = logger;
        }

        public JDA getJDA() {
            return this.jda;
        }

        public long getGuildId() {
            return this.guildId;
        }

        public Guild getGuild() {
            return this.jda.getGuildById(this.guildId);
        }

        public long getChannelId() {
            return this.channelId;
        }

        public TextChannel getChannel() {
            return this.jda.getTextChannelById(this.channelId);
        }

        public TextChannel getChannel(Guild guild) {
            return guild.getTextChannelById(this.channelId);
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
    private final Map<Long, BlockingDeque<Request>> queues;

    private final OkHttpClient webhookClient = new OkHttpClient();
    private final ScheduledExecutorService webhookExecutor = Executors.newSingleThreadScheduledExecutor();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private LoggerManager() {
        this.queues = new HashMap<>();
        this.webhooks = new HashMap<>();
    }

    private void createWebhook(TextChannel channel, BlockingDeque<Request> deque, List<Request> requests, int retries) {
        channel.createWebhook("Sx4 - Logger").queue(webhook -> {
            WebhookClient webhookClient = new WebhookClientBuilder(webhook.getUrl())
                .setExecutorService(this.webhookExecutor)
                .setHttpClient(this.webhookClient)
                .build();

            this.webhooks.put(channel.getIdLong(), webhookClient);

            Bson update = Updates.combine(
                Updates.set("logger.loggers.$[logger].webhook.id", webhook.getIdLong()),
                Updates.set("logger.loggers.$[logger].webhook.token", webhook.getToken())
            );

            UpdateOptions options = new UpdateOptions().arrayFilters(List.of(Filters.eq("logger.id", channel.getIdLong())));
            Database.get().updateGuildById(channel.getGuild().getIdLong(), update, options).whenComplete((result, exception) -> {
                if (ExceptionUtility.sendErrorMessage(exception)) {
                    return;
                }

                requests.forEach(deque::addFirst);
                this.handleQueue(deque, retries + 1);
            });
        });
    }

    private void handleQueue(BlockingDeque<Request> deque, int retries) {
        this.executor.submit(() -> {
            Request request = deque.poll();
            if (request == null) {
                return;
            }

            if (retries == LoggerManager.MAX_RETRIES) {
                this.handleQueue(deque, 0);
                return;
            }

            Guild guild = request.getGuild();
            if (guild == null) {
                this.handleQueue(deque, 0);
                return;
            }

            long channelId = request.getChannelId();
            TextChannel channel = request.getChannel(guild);

            if (channel == null) {
                Database.get().updateGuildById(request.getGuildId(), Updates.pull("logger.loggers", Filters.eq("id", channelId))).whenComplete(Database.exceptionally());

                this.webhooks.remove(channelId);
                this.handleQueue(deque, 0);

                return;
            }

            List<WebhookEmbed> embeds = new ArrayList<>(request.getEmbeds());
            int length = LoggerUtility.getWebhookEmbedLength(embeds);

            List<Request> skippedRequests = new ArrayList<>(), requests = new ArrayList<>();
            requests.add(request);

            Request nextRequest;
            while ((nextRequest = deque.poll()) != null) {
                List<WebhookEmbed> nextEmbeds = nextRequest.getEmbeds();

                int nextLength = LoggerUtility.getWebhookEmbedLength(nextEmbeds);
                if (request.getChannelId() != nextRequest.getChannelId() || embeds.size() + nextEmbeds.size() > 10 || length + nextLength > MessageEmbed.EMBED_MAX_LENGTH_BOT) {
                    skippedRequests.add(nextRequest);
                    continue;
                }

                embeds.addAll(nextEmbeds);
                length += nextLength;

                requests.add(nextRequest);
            }

            // Keep order of logs
            skippedRequests.forEach(deque::addFirst);

            Document logger = request.getLogger();
            Document webhookData = logger.get("webhook", Database.EMPTY_DOCUMENT);

            WebhookMessage message = new WebhookMessageBuilder()
                .addEmbeds(embeds)
                .setUsername(webhookData.get("name", "Sx4 - Logger"))
                .setAvatarUrl(webhookData.get("avatar", request.getJDA().getSelfUser().getEffectiveAvatarUrl()))
                .build();

            WebhookClient webhook;
            if (this.webhooks.containsKey(channelId)) {
                webhook = this.webhooks.get(channelId);
            } else if (!webhookData.containsKey("id")) {
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

    private void queue(Request request) {
        BlockingDeque<Request> queue = this.queues.computeIfAbsent((request.getChannelId() >> 22) % 2, (key) -> new LinkedBlockingDeque<>());
        if (queue.isEmpty()) {
            queue.add(request);
            this.handleQueue(queue, 0);
        } else {
            queue.add(request);
        }
    }

    public void queue(TextChannel channel, Document logger, WebhookEmbed... embeds) {
        this.queue(channel, logger, Arrays.asList(embeds));
    }

    public void queue(TextChannel channel, Document logger, List<WebhookEmbed> embeds) {
        List<Request> requests = new ArrayList<>();

        double messages = Math.ceil((double) embeds.size() / 10);
        for (int i = 0; i < messages; i++) {
            List<WebhookEmbed> splitEmbeds = embeds.subList(i * 10, i == messages - 1 ? embeds.size() : (i + 1) * 10);

            requests.add(new Request(channel.getJDA(), channel.getGuild().getIdLong(), channel.getIdLong(), splitEmbeds, logger));
        }

        requests.forEach(this::queue);
    }

}
