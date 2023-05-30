package com.sx4.bot.managers;

import club.minnced.discord.webhook.send.WebhookEmbed;
import club.minnced.discord.webhook.send.WebhookMessage;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.entities.webhook.WebhookChannel;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.MessageUtility;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.RestConfig;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class LoggerManager {

    public static class Request {

        private final JDA jda;

        private final long guildId;
        private final long channelId;
        private final List<WebhookEmbed> embeds;
        private final Document logger;

        private int attempts = 0;

        public Request(JDA jda, long guildId, long channelId, List<WebhookEmbed> embeds, Document logger) {
            this.jda = jda;
            this.guildId = guildId;
            this.channelId = channelId;
            this.embeds = embeds;
            this.logger = logger;
        }

        public Request incrementAttempts() {
            this.attempts++;

            return this;
        }

        public int getAttempts() {
            return this.attempts;
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

        public GuildMessageChannelUnion getChannel() {
            return this.jda.getChannelById(GuildMessageChannelUnion.class, this.channelId);
        }

        public GuildMessageChannelUnion getChannel(Guild guild) {
            return guild.getChannelById(GuildMessageChannelUnion.class, this.channelId);
        }

        public List<WebhookEmbed> getEmbeds() {
            return this.embeds;
        }

        public Document getLogger() {
            return this.logger;
        }

    }

    private static final int MAX_RETRIES = 3;

    private String webhook;
    private final BlockingDeque<Request> queue;

    private final OkHttpClient client;
    private final ScheduledExecutorService executor;

    private long reset = 0L;

    private final Sx4 bot;

    public LoggerManager(Sx4 bot, OkHttpClient client, ScheduledExecutorService executor) {
        this.queue = new LinkedBlockingDeque<>();
        this.bot = bot;
        this.executor = executor;
        this.client = client;
    }

    private String getWebhookUrl(long id, String token) {
        return RestConfig.DEFAULT_BASE_URL + "webhooks/" + id + "/" + token;
    }

    private void disableLogger(long channelId) {
        Bson update = Updates.combine(
            Updates.set("enabled", false),
            Updates.unset("webhook.id"),
            Updates.unset("webhook.token")
        );

        this.bot.getMongo().updateLogger(Filters.eq("channelId", channelId), update, new UpdateOptions()).whenComplete((result, databaseException) -> {
            ExceptionUtility.sendErrorMessage(databaseException);
            this.queue.clear();
            this.webhook = null;
        });
    }

    private void createWebhook(WebhookChannel channel, List<Request> requests) {
        channel.createWebhook("Sx4 - Logger").submit().whenComplete((webhook, exception) -> {
            Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
            if (cause instanceof ErrorResponseException) {
                ErrorResponse errorResponse = ((ErrorResponseException) cause).getErrorResponse();
                if (errorResponse == ErrorResponse.MAX_WEBHOOKS) {
                    this.disableLogger(channel.getIdLong());
                    return;
                } else if (errorResponse == ErrorResponse.UNKNOWN_CHANNEL) {
                    this.bot.getMongo().deleteLogger(Filters.eq("channelId", channel.getIdLong())).whenComplete((result, databaseException) -> {
                        ExceptionUtility.sendErrorMessage(databaseException);
                        this.queue.clear();
                        this.webhook = null;
                    });

                    return;
                }
            }

            if (ExceptionUtility.sendErrorMessage(exception)) {
                requests.forEach(failedRequest -> this.queue.addFirst(failedRequest.incrementAttempts()));
                this.handleQueue();

                return;
            }

            this.webhook = webhook.getUrl();

            Bson update = Updates.combine(
                Updates.set("webhook.id", webhook.getIdLong()),
                Updates.set("webhook.token", webhook.getToken())
            );

            this.bot.getMongo().updateManyLoggers(Filters.eq("webhook.channelId", channel.getWebhookChannel().getIdLong()), update, new UpdateOptions()).whenComplete((result, databaseException) -> {
                ExceptionUtility.sendErrorMessage(databaseException);

                requests.forEach(failedRequest -> this.queue.addFirst(failedRequest.incrementAttempts()));
                this.handleQueue();
            });
        });
    }

    private void drainQueue() {
        try {
            Request request = this.queue.poll();
            if (request == null) {
                return;
            }

            if (request.getAttempts() == LoggerManager.MAX_RETRIES) {
                this.handleQueue();
                return;
            }

            Guild guild = request.getGuild();
            if (guild == null) {
                this.handleQueue();
                return;
            }

            long channelId = request.getChannelId();
            GuildMessageChannelUnion channel = request.getChannel(guild);
            if (channel == null) {
                this.bot.getMongo().deleteLogger(Filters.eq("channelId", channelId)).whenComplete((result, exception) -> {
                    ExceptionUtility.sendErrorMessage(exception);
                    this.queue.clear();
                });

                return;
            }

            WebhookChannel webhookChannel = new WebhookChannel(channel);

            List<WebhookEmbed> embeds = new ArrayList<>(request.getEmbeds());
            int length = MessageUtility.getWebhookEmbedLength(embeds);

            List<Request> requests = new ArrayList<>();
            requests.add(request);

            Request nextRequest;
            while ((nextRequest = this.queue.peek()) != null) {
                List<WebhookEmbed> nextEmbeds = nextRequest.getEmbeds();
                int nextLength = MessageUtility.getWebhookEmbedLength(nextEmbeds);

                if (embeds.size() + nextEmbeds.size() > WebhookMessage.MAX_EMBEDS || length + nextLength > MessageEmbed.EMBED_MAX_LENGTH_BOT) {
                    break;
                }

                embeds.addAll(nextEmbeds);
                requests.add(this.queue.poll());
                length += nextLength;
            }

            Document logger = request.getLogger();
            Document webhookData = logger.get("webhook", MongoDatabase.EMPTY_DOCUMENT);
            boolean premium = logger.getBoolean("premium");

            WebhookMessage message = new WebhookMessageBuilder()
                .addEmbeds(embeds)
                .setUsername(premium ? webhookData.get("name", "Sx4 - Logger") : "Sx4 - Logger")
                .setAvatarUrl(premium ? webhookData.get("avatar", request.getJDA().getSelfUser().getEffectiveAvatarUrl()) : request.getJDA().getSelfUser().getEffectiveAvatarUrl())
                .build();

            if (this.webhook == null) {
                if (!webhookData.containsKey("id")) {
                    if (guild.getSelfMember().hasPermission(channel, Permission.MANAGE_WEBHOOKS)) {
                        this.createWebhook(webhookChannel, requests);
                        return;
                    }

                    this.handleQueue();
                    return;
                } else {
                    this.webhook = this.getWebhookUrl(webhookData.getLong("id"), webhookData.getString("token"));
                }
            }

            okhttp3.Request httpRequest = new okhttp3.Request.Builder()
                .url(this.webhook)
                .post(message.getBody())
                .build();

            Response response = this.client.newCall(httpRequest).execute();

            int code = response.code();
            if (code == 404) {
                if (guild.getSelfMember().hasPermission(channel, Permission.MANAGE_WEBHOOKS)) {
                    this.createWebhook(webhookChannel, requests);
                    return;
                }

                this.disableLogger(channel.getIdLong());
                return;
            } else if (code == 429) {
                String retryAfter = response.header("Retry-After");
                if (retryAfter == null) {
                    requests.forEach(failedRequest -> this.queue.addFirst(failedRequest.incrementAttempts()));
                    this.handleQueue();
                    return;
                }

                requests.forEach(this.queue::addFirst);

                long delay = Long.parseLong(retryAfter) * 1000;
                this.reset = System.currentTimeMillis() + delay;
            } else if (code < 200 || code >= 300) {
                requests.forEach(failedRequest -> this.queue.addFirst(failedRequest.incrementAttempts()));
            }

            this.handleQueue();
        } catch (Throwable exception) {
            // Continue queue even if an exception occurs to avoid the queue getting stuck
            ExceptionUtility.sendErrorMessage(exception);
            this.handleQueue();
        }
    }

    private void handleQueue() {
        this.executor.schedule(this::drainQueue, this.reset - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
    }

    private void queue(Request request) {
        if (this.queue.isEmpty()) {
            this.queue.add(request);
            this.handleQueue();
        } else {
            this.queue.add(request);
        }
    }

    public void queue(GuildMessageChannelUnion channel, Document logger, List<WebhookEmbed> embeds) {
        List<Request> requests = new ArrayList<>();

        int index = 0;
        while (index != embeds.size()) {
            List<WebhookEmbed> splitEmbeds = new ArrayList<>(embeds.subList(index, Math.min(index + 10, embeds.size())));
            while (MessageUtility.getWebhookEmbedLength(splitEmbeds) > MessageEmbed.EMBED_MAX_LENGTH_BOT) {
                splitEmbeds.remove(splitEmbeds.size() - 1);
            }

            index += Math.max(1, splitEmbeds.size()); // Make a min value of 1 to avoid an infinite loop

            requests.add(new Request(channel.getJDA(), channel.getGuild().getIdLong(), channel.getIdLong(), splitEmbeds, logger));
        }

        requests.forEach(this::queue);
    }

}
