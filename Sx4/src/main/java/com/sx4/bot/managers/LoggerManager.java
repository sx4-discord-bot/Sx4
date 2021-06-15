package com.sx4.bot.managers;

import club.minnced.discord.webhook.exception.HttpException;
import club.minnced.discord.webhook.send.WebhookEmbed;
import club.minnced.discord.webhook.send.WebhookMessage;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.entities.management.LoggerContext;
import com.sx4.bot.entities.management.LoggerEvent;
import com.sx4.bot.entities.webhook.WebhookClient;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.LoggerUtility;
import com.sx4.bot.utility.MessageUtility;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;
import okhttp3.OkHttpClient;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.glassfish.jersey.internal.guava.ThreadFactoryBuilder;

import java.util.*;
import java.util.concurrent.*;

public class LoggerManager implements WebhookManager {

    public static class Request {

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

    private final Map<Long, WebhookClient> webhooks;
    private final BlockingDeque<Request> queue;

    private final OkHttpClient webhookClient = new OkHttpClient();
    private final ScheduledExecutorService webhookExecutor = Executors.newSingleThreadScheduledExecutor();

    private final ExecutorService executor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("logger-executor-%d").build());

    private final Sx4 bot;

    public LoggerManager(Sx4 bot) {
        this.queue = new LinkedBlockingDeque<>();
        this.webhooks = new HashMap<>();
        this.bot = bot;
    }

    public WebhookClient getWebhook(long channelId) {
        return this.webhooks.get(channelId);
    }

    public WebhookClient removeWebhook(long channelId) {
        return this.webhooks.remove(channelId);
    }

    public void putWebhook(long channelId, WebhookClient webhook) {
        this.webhooks.put(channelId, webhook);
    }

    private void createWebhook(TextChannel channel, List<Request> requests, int retries) {
        channel.createWebhook("Sx4 - Logger").queue(webhook -> {
            WebhookClient webhookClient = new WebhookClient(webhook.getIdLong(), webhook.getToken(), this.webhookExecutor, this.webhookClient);

            this.webhooks.put(channel.getIdLong(), webhookClient);

            Bson update = Updates.combine(
                Updates.set("webhook.id", webhook.getIdLong()),
                Updates.set("webhook.token", webhook.getToken())
            );

            this.bot.getMongo().updateLogger(Filters.eq("channelId", channel.getIdLong()), update, new UpdateOptions()).whenComplete((result, exception) -> {
                if (ExceptionUtility.sendErrorMessage(exception)) {
                    return;
                }

                requests.forEach(this.queue::addFirst);
                this.handleQueue(retries + 1);
            });
        });
    }

    private void handleQueue(int retries) {
        this.executor.submit(() -> {
            try {
                Request request = this.queue.poll();
                if (request == null) {
                    return;
                }

                if (retries == LoggerManager.MAX_RETRIES) {
                    this.handleQueue(0);
                    return;
                }

                Guild guild = request.getGuild();
                if (guild == null) {
                    this.handleQueue(0);
                    return;
                }

                long channelId = request.getChannelId();
                TextChannel channel = request.getChannel(guild);
                if (channel == null) {
                    this.bot.getMongo().deleteLogger(Filters.eq("channelId", channelId)).whenComplete(MongoDatabase.exceptionally(this.bot.getShardManager()));

                    this.webhooks.remove(channelId);
                    this.handleQueue(0);

                    return;
                }

                List<WebhookEmbed> embeds = new ArrayList<>(request.getEmbeds());
                int length = MessageUtility.getWebhookEmbedLength(embeds);

                List<Request> skippedRequests = new ArrayList<>(), requests = new ArrayList<>();
                requests.add(request);

                Request nextRequest;
                while ((nextRequest = this.queue.poll()) != null) {
                    List<WebhookEmbed> nextEmbeds = nextRequest.getEmbeds();

                    int nextLength = MessageUtility.getWebhookEmbedLength(nextEmbeds);
                    if (request.getChannelId() != nextRequest.getChannelId()) {
                        skippedRequests.add(nextRequest);
                        continue;
                    }

                    if (embeds.size() + nextEmbeds.size() > 10 || length + nextLength > MessageEmbed.EMBED_MAX_LENGTH_BOT) {
                        break;
                    }

                    embeds.addAll(nextEmbeds);
                    requests.add(nextRequest);
                    length += nextLength;
                }

                // Keep order of logs
                skippedRequests.forEach(this.queue::addFirst);

                Document logger = request.getLogger();
                Document webhookData = logger.get("webhook", MongoDatabase.EMPTY_DOCUMENT);
                boolean premium = logger.getBoolean("premium");

                WebhookMessage message = new WebhookMessageBuilder()
                    .addEmbeds(embeds)
                    .setUsername(premium ? webhookData.get("name", "Sx4 - Logger") : "Sx4 - Logger")
                    .setAvatarUrl(premium ? webhookData.get("avatar", request.getJDA().getSelfUser().getEffectiveAvatarUrl()) : request.getJDA().getSelfUser().getEffectiveAvatarUrl())
                    .build();

                WebhookClient webhook;
                if (this.webhooks.containsKey(channelId)) {
                    webhook = this.webhooks.get(channelId);
                } else if (!webhookData.containsKey("id")) {
                    if (channel.getGuild().getSelfMember().hasPermission(channel, Permission.MANAGE_WEBHOOKS)) {
                        this.createWebhook(channel, requests, retries);
                        return;
                    }

                    this.handleQueue(0);
                    return;
                } else {
                    webhook = new WebhookClient(webhookData.getLong("id"), webhookData.getString("token"), this.webhookExecutor, this.webhookClient);

                    this.webhooks.put(channelId, webhook);
                }

                webhook.send(message).whenComplete((result, exception) -> {
                    Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
                    if (cause instanceof HttpException && ((HttpException) cause).getCode() == 404) {
                        if (channel.getGuild().getSelfMember().hasPermission(channel, Permission.MANAGE_WEBHOOKS)) {
                            this.createWebhook(channel, requests, retries);
                            return;
                        }

                        this.handleQueue(0);

                        return;
                    }

                    if (ExceptionUtility.sendErrorMessage(exception)) {
                        requests.forEach(this.queue::addFirst);
                        this.handleQueue(retries + 1);

                        return;
                    }

                    this.handleQueue(0);
                });
            } catch (Throwable exception) {
                // Continue queue even if an exception occurs to avoid the queue getting stuck
                ExceptionUtility.sendErrorMessage(exception);
                this.handleQueue(0);
            }
        });
    }

    private void queue(Request request) {
        // use size() as it is thread safe
        if (this.queue.size() == 0) {
            this.queue.add(request);
            this.handleQueue(0);
        } else {
            this.queue.add(request);
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

    public void queue(Guild guild, List<Document> loggers, LoggerEvent event, LoggerContext context, WebhookEmbed... embeds) {
        this.queue(guild, loggers, event, context, Arrays.asList(embeds));
    }

    public void queue(Guild guild, List<Document> loggers, LoggerEvent event, LoggerContext context, List<WebhookEmbed> embeds) {
        List<Long> deletedLoggers = new ArrayList<>();
        for (Document logger : loggers) {
            if (!LoggerUtility.canSend(logger, event, context)) {
                continue;
            }

            long channelId = logger.getLong("channelId");
            TextChannel channel = guild.getTextChannelById(channelId);
            if (channel == null) {
                deletedLoggers.add(channelId);
                continue;
            }

            this.queue(channel, logger, embeds);
        }

        if (!deletedLoggers.isEmpty()) {
            this.bot.getMongo().deleteManyLoggers(Filters.in("channelId", deletedLoggers)).whenComplete(MongoDatabase.exceptionally(this.bot.getShardManager()));
        }
    }

}
