package com.sx4.bot.managers;

import com.mongodb.client.model.*;
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

    private static final AntiRegexManager INSTANCE = new AntiRegexManager();

    public static AntiRegexManager get() {
        return AntiRegexManager.INSTANCE;
    }

    // TODO Would also be nice for a way to combine attempts across multiple anti regexes
    private final Map<Long, Map<ObjectId, Map<Long, Integer>>> attempts;

    private final Map<Long, Map<ObjectId, Map<Long, ScheduledFuture<?>>>> executors;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

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
                    this.cancelExecutor(guildId, id, userId);
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
                        this.cancelExecutor(guildId, id, userId);
                        return null;
                    }

                    Map<Long, Integer> userAttempts = new HashMap<>();
                    userAttempts.put(userId, amount);

                    return userAttempts;
                }

                idValue.compute(userId, (userKey, userValue) -> {
                    int newAmount = userValue == null ? amount : userValue + amount;
                    if (newAmount <= 0) {
                        this.cancelExecutor(guildId, id, userId);
                        return null;
                    }

                    return newAmount;
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
                if (executor != null && !executor.isDone()) {
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

    public UpdateOneModel<Document> resetAttemptsBulk(long guildId, ObjectId id, long userId, long duration, int amount) {
        this.decreaseAttempts(guildId, id, userId, amount);
        int attempts = this.getAttempts(guildId, id, userId);

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

        return new UpdateOneModel<>(Filters.eq("_id", guildId), update, new UpdateOptions().arrayFilters(arrayFilters));
    }

    public void resetAttempts(long guildId, ObjectId id, long userId, long duration, int amount) {
        Database.get().updateGuild(this.resetAttemptsBulk(guildId, id, userId, duration, amount)).whenComplete(Database.exceptionally());
    }

    public void scheduleResetAttempts(long guildId, ObjectId id, long userId, long duration, int amount) {
        this.scheduleResetAttempts(guildId, id, userId, duration, duration, amount);
    }

    public void scheduleResetAttempts(long guildId, ObjectId id, long userId, long initialDuration, long duration, int amount) {
        ScheduledFuture<?> executor = this.executor.scheduleAtFixedRate(() -> this.resetAttempts(guildId, id, userId, duration, amount), initialDuration, duration, TimeUnit.SECONDS);
        this.putExecutor(guildId, id, userId, executor);
    }

    public void ensureAttempts() {
        Database database = Database.get();

        List<WriteModel<Document>> bulkData = new ArrayList<>();
        database.getGuilds(Filters.elemMatch("antiRegex.regexes", Filters.exists("id")), Projections.include("antiRegex.regexes")).forEach(data -> {
            long guildId = data.getLong("_id");

            List<Document> regexes = data.getEmbedded(List.of("antiRegex", "regexes"), Collections.emptyList());
            for (Document regex : regexes) {
                ObjectId id = regex.getObjectId("id");

                Document reset = regex.getEmbedded(List.of("action", "mod", "attempts", "reset"), Database.EMPTY_DOCUMENT);
                long duration = reset.get("after", 0L);
                int amount = reset.get("amount", 0);

                List<Document> users = regex.getList("users", Document.class, Collections.emptyList());
                for (Document user : users) {
                    long userId = user.getLong("id");
                    int attempts = user.getInteger("attempts");

                    this.increaseAttempts(guildId, id, userId, attempts);
                    if (duration != 0L) {
                        long resetAt = user.getLong("resetAt"), now = Clock.systemUTC().instant().getEpochSecond();
                        if (now >= resetAt) {
                            int remove = (int) Math.floorDiv(now - resetAt, duration) + 1;
                            long currentDuration = duration - (now - resetAt);

                            bulkData.add(this.resetAttemptsBulk(guildId, id, userId, currentDuration, remove));
                            if (remove < attempts) {
                                this.scheduleResetAttempts(guildId, id, userId, currentDuration, duration, amount);
                                this.increaseAttempts(guildId, id, userId, attempts - remove);
                            }
                        } else {
                            this.scheduleResetAttempts(guildId, id, userId, resetAt - now, duration, amount);
                            this.increaseAttempts(guildId, id, userId, attempts);
                        }
                    }
                }
            }
        });

        if (!bulkData.isEmpty()) {
            database.bulkWriteGuilds(bulkData).whenComplete(Database.exceptionally());
        }
    }

}
