package com.sx4.bot.managers;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.sx4.bot.database.Database;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class AntiRegexManager {

    public static final String DEFAULT_MATCH_MESSAGE = "{user.mention}, you cannot send that content here due to the regex `{regex.id}`"
        + "({regex.action.exists}?, you will receive a {regex.action.name} if you continue **({regex.attempts.current}/{regex.attempts.max})**:) :no_entry:";

    public static final String DEFAULT_MOD_MESSAGE = "**{user.tag}** has received a {regex.action.name} for sending a message which matched the regex"
        + "`{regex.id}` {regex.attempts.max} time({regex.attempts.max}!=1?s:) <:done:403285928233402378>";

    private static final AntiRegexManager INSTANCE = new AntiRegexManager();

    public static AntiRegexManager get() {
        return AntiRegexManager.INSTANCE;
    }

    // TODO Would also be nice for a way to combine attempts across multiple anti regexes
    private final Map<Long, Map<ObjectId, Map<Long, Integer>>> attempts;

    private final Map<Long, Map<ObjectId, Map<Long, ScheduledFuture<?>>>> executors;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(20);

    private AntiRegexManager() {
        this.attempts = new HashMap<>();
        this.executors = new HashMap<>();
    }

    public int getAttempts(long guildId, ObjectId id, long userId) {
        Map<ObjectId, Map<Long, Integer>> regexAttempts = this.attempts.get(guildId);
        if (regexAttempts != null) {
            Map<Long, Integer> userAttempts = regexAttempts.get(id);
            if (userAttempts != null) {
                return userAttempts.getOrDefault(userId, 0);
            }
        }

        return 0;
    }

    public void increaseAttempts(long guildId, ObjectId id, long userId, int amount) {
        this.attempts.compute(guildId, (guildKey, guildValue) -> {
            if (guildValue == null) {
                if (amount < 1) {
                    return null;
                }

                Map<ObjectId, Map<Long, Integer>> regexAttempts = new HashMap<>();
                Map<Long, Integer> userAttempts = new HashMap<>();

                userAttempts.put(userId, amount);
                regexAttempts.put(id, userAttempts);

                return regexAttempts;
            }

            guildValue.compute(id, (idKey, idValue) -> {
                if (idValue == null) {
                    if (amount < 1) {
                        return null;
                    }

                    Map<Long, Integer> userAttempts = new HashMap<>();
                    userAttempts.put(userId, amount);

                    return userAttempts;
                }

                idValue.compute(userId, (userKey, userValue) -> {
                    int newAmount = Math.max(0, userValue == null ? amount : userValue + amount);
                    return newAmount == 0 ? null : newAmount;
                });

                return idValue;
            });

            return guildValue;
        });
    }

    public void incrementAttempts(long guildId, ObjectId id, long userId) {
        this.increaseAttempts(guildId, id, userId,1);
    }

    public void decreaseAttempts(long guildId, ObjectId id, long userId, int amount) {
        this.increaseAttempts(guildId, id, userId, -amount);
    }

    public void clearAttempts(long guildId, ObjectId id, long userId) {
        Map<ObjectId, Map<Long, Integer>> regexAttempts = this.attempts.get(guildId);
        if (regexAttempts != null) {
            Map<Long, Integer> userAttempts = regexAttempts.get(id);
            if (userAttempts != null) {
                userAttempts.remove(userId);
            }
        }

        this.cancelExecutor(guildId, id, userId);
    }

    public void cancelExecutor(long guildId, ObjectId id, long userId) {
        Map<ObjectId, Map<Long, ScheduledFuture<?>>> regexExecutors = this.executors.get(guildId);
        if (regexExecutors != null) {
            Map<Long, ScheduledFuture<?>> userExecutors = regexExecutors.get(id);
            if (userExecutors != null) {
                ScheduledFuture<?> executor = userExecutors.remove(userId);
                if (executor != null) {
                    executor.cancel(true);
                }
            }
        }
    }

    public void putExecutor(long guildId, ObjectId id, long userId, ScheduledFuture<?> executor) {
        this.executors.compute(guildId, (guildKey, guildValue) -> {
            if (guildValue == null) {
                Map<ObjectId, Map<Long, ScheduledFuture<?>>> regexAttempts = new HashMap<>();
                Map<Long, ScheduledFuture<?>> userAttempts = new HashMap<>();

                userAttempts.put(userId, executor);
                regexAttempts.put(id, userAttempts);

                return regexAttempts;
            }

            guildValue.compute(id, (idKey, idValue) -> {
                if (idValue == null) {
                    Map<Long, ScheduledFuture<?>> userAttempts = new HashMap<>();
                    userAttempts.put(userId, executor);

                    return userAttempts;
                }

                idValue.compute(userId, (userKey, userValue) -> {
                    if (userValue != null && !userValue.isDone()) {
                        userValue.cancel(true);
                    }

                    return executor;
                });

                return idValue;
            });

            return guildValue;
        });
    }

    public void resetAttempts(long guildId, ObjectId id, long userId, int amount) {
        this.decreaseAttempts(guildId, id, userId, amount);
        int attempts = this.getAttempts(guildId, id, userId);

        List<Bson> arrayFilters;
        Bson update;
        if (attempts == 0) {
            this.cancelExecutor(guildId, id, userId);

            update = Updates.pull("antiRegex.regexes.$[regex].users", Filters.eq("id", userId));
            arrayFilters = List.of(Filters.eq("regex.id", id));
        } else {
            update = Updates.set("antiRegex.regexes.$[regex].users.$[user].attempts", attempts);
            arrayFilters = List.of(Filters.eq("regex.id", id), Filters.eq("user.id", userId));
        }

        Database.get().updateGuildById(guildId, update, new UpdateOptions().arrayFilters(arrayFilters)).whenComplete(Database.exceptionally());
    }

    public void resetAttempts(long guildId, ObjectId id, long userId, long duration, int amount) {
        ScheduledFuture<?> executor = this.executor.scheduleAtFixedRate(() -> this.resetAttempts(guildId, id, userId, amount), duration, duration, TimeUnit.SECONDS);
        this.putExecutor(guildId, id, userId, executor);
    }

    public void ensureAttempts() {
        Database.get().getGuilds(Filters.elemMatch("antiRegex.regexes", Filters.exists("id")), Projections.include("antiRegex.regexes")).forEach(data -> {
            long guildId = data.getLong("_id");

            List<Document> regexes = data.getEmbedded(List.of("antiRegex", "regexes"), Collections.emptyList());
            for (Document regex : regexes) {
                ObjectId id = regex.getObjectId("id");

                Document reset = regex.getEmbedded(List.of("action", "mod", "attempts", "reset"), Database.EMPTY_DOCUMENT);
                long duration = reset.get("after", 0L);
                int amount = reset.getInteger("amount");

                List<Document> users = regex.getList("users", Document.class, Collections.emptyList());
                for (Document user : users) {
                    long userId = user.getLong("id");
                    this.increaseAttempts(guildId, id, userId, user.getInteger("attempts"));
                    if (duration != 0L) {
                        this.resetAttempts(guildId, id, userId, duration, amount);
                    }
                }
            }
        });
    }

}
