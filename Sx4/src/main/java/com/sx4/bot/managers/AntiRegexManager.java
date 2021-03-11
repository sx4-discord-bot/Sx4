package com.sx4.bot.managers;

import com.mongodb.client.model.*;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.Database;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.time.Clock;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class AntiRegexManager {

    public static final String DEFAULT_MATCH_MESSAGE = "{user.mention}, you cannot send that content here due to the regex `{regex.id}`"
        + "({regex.action.exists}?, you will receive a {regex.action.name} if you continue **({regex.attempts.current}/{regex.attempts.max})**:) :no_entry:";

    public static final String DEFAULT_MOD_MESSAGE = "**{user.tag}** has received a {regex.action.name} for sending a message which matched the regex "
        + "`{regex.id}` {regex.attempts.max} time({regex.attempts.max}!=1?s:) <:done:403285928233402378>";

    // TODO Would also be nice for a way to combine attempts across multiple anti regexes
    private final Map<ObjectId, Map<Long, Integer>> attempts;

    private final Map<ObjectId, Map<Long, ScheduledFuture<?>>> executors;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    private final Sx4 bot;

    public AntiRegexManager(Sx4 bot) {
        this.attempts = new HashMap<>();
        this.executors = new HashMap<>();
        this.bot = bot;
    }

    public int getAttempts(ObjectId id, long userId) {
        Map<Long, Integer> userAttempts = this.attempts.get(id);
        if (userAttempts != null) {
            return userAttempts.getOrDefault(userId, 0);
        }

        return 0;
    }

    public void increaseAttempts(ObjectId id, long userId, int amount) {
        this.attempts.compute(id, (idKey, idValue) -> {
            if (idValue == null) {
                if (amount < 1) {
                    this.deleteExecutor(id, userId);
                    return null;
                }

                Map<Long, Integer> userAttempts = new HashMap<>();
                userAttempts.put(userId, amount);

                return userAttempts;
            }

            idValue.compute(userId, (userKey, userValue) -> {
                int newAmount = userValue == null ? amount : userValue + amount;
                if (newAmount <= 0) {
                    this.deleteExecutor(id, userId);
                    return null;
                }

                return newAmount;
            });

            return idValue;
        });
    }

    public void incrementAttempts(ObjectId id, long userId) {
        this.increaseAttempts(id, userId,1);
    }

    public void decreaseAttempts(ObjectId id, long userId, int amount) {
        this.increaseAttempts(id, userId, -amount);
    }

    public void clearAttempts(ObjectId id, long userId) {
        Map<Long, Integer> userAttempts = this.attempts.get(id);
        if (userAttempts != null) {
            userAttempts.remove(userId);
        }

        this.deleteExecutor(id, userId);
    }

    public void deleteExecutor(ObjectId id, long userId) {
        Map<Long, ScheduledFuture<?>> userExecutors = this.executors.get(id);
        if (userExecutors != null) {
            ScheduledFuture<?> executor = userExecutors.remove(userId);
            if (executor != null && !executor.isDone()) {
                executor.cancel(true);
            }
        }
    }

    public void putExecutor(ObjectId id, long userId, ScheduledFuture<?> executor) {
        this.executors.compute(id, (idKey, idValue) -> {
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
    }

    public UpdateOneModel<Document> resetAttemptsBulk(ObjectId id, long userId, long duration, int amount) {
        this.decreaseAttempts(id, userId, amount);
        int attempts = this.getAttempts(id, userId);

        List<Bson> arrayFilters;
        Bson update;
        if (attempts == 0) {
            update = Updates.pull("antiRegex.regexes.$[regex].users", Filters.eq("id", userId));
            arrayFilters = List.of(Filters.eq("regex.id", id));
        } else {
            update = Updates.combine(
                Updates.set("antiRegex.regexes.$[regex].users.$[user].attempts", attempts),
                Updates.set("antiRegex.regexes.$[regex].users.$[user].resetAt", Clock.systemUTC().instant().getEpochSecond() + duration)
            );
            arrayFilters = List.of(Filters.eq("regex.id", id), Filters.eq("user.id", userId));
        }

        return new UpdateOneModel<>(Filters.eq("_id", id), update, new UpdateOptions().arrayFilters(arrayFilters));
    }

    public void resetAttempts(ObjectId id, long userId, long duration, int amount) {
        this.bot.getDatabase().updateGuild(this.resetAttemptsBulk(id, userId, duration, amount)).whenComplete(Database.exceptionally(this.bot.getShardManager()));
    }

    public void scheduleResetAttempts(ObjectId id, long userId, long duration, int amount) {
        this.scheduleResetAttempts(id, userId, duration, duration, amount);
    }

    public void scheduleResetAttempts(ObjectId id, long userId, long initialDuration, long duration, int amount) {
        ScheduledFuture<?> executor = this.executor.scheduleAtFixedRate(() -> this.resetAttempts(id, userId, duration, amount), initialDuration, duration, TimeUnit.SECONDS);
        this.putExecutor(id, userId, executor);
    }

    public void ensureAttempts() {
        List<WriteModel<Document>> bulkData = new ArrayList<>();
        this.bot.getDatabase().getRegexes(Database.EMPTY_DOCUMENT, Projections.include("attempts.reset")).forEach(regex -> {
            ObjectId id = regex.getObjectId("id");

            Document reset = regex.getEmbedded(List.of("attempts", "reset"), Database.EMPTY_DOCUMENT);
            long duration = reset.get("after", 0L);
            int amount = reset.get("amount", 0);

            List<Document> users = /* database.getRegexAttempts(id, Projections.include("userId", "attempts")).forEach */ Collections.emptyList();
            for (Document user : users) {
                long userId = user.getLong("id");
                int attempts = user.getInteger("attempts");

                this.increaseAttempts(id, userId, attempts);
                if (duration != 0L) {
                    long resetAt = user.getLong("resetAt"), now = Clock.systemUTC().instant().getEpochSecond();
                    if (now >= resetAt) {
                        int remove = (int) Math.floorDiv(now - resetAt, duration) + 1;
                        long currentDuration = duration - (now - resetAt);

                        bulkData.add(this.resetAttemptsBulk(id, userId, currentDuration, remove));
                        if (remove < attempts) {
                            this.scheduleResetAttempts(id, userId, currentDuration, duration, amount);
                            this.increaseAttempts(id, userId, attempts - remove);
                        }
                    } else {
                        this.scheduleResetAttempts(id, userId, resetAt - now, duration, amount);
                        this.increaseAttempts(id, userId, attempts);
                    }
                }
            }
        });

        if (!bulkData.isEmpty()) {
            this.bot.getDatabase().bulkWriteGuilds(bulkData).whenComplete(Database.exceptionally(this.bot.getShardManager()));
        }
    }

}
