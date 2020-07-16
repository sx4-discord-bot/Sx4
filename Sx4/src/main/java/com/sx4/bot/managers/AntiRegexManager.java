package com.sx4.bot.managers;

import org.bson.types.ObjectId;

import java.util.HashMap;
import java.util.Map;

public class AntiRegexManager {

    private static final AntiRegexManager INSTANCE = new AntiRegexManager();

    public static AntiRegexManager get() {
        return AntiRegexManager.INSTANCE;
    }

    // TODO has to be linked per anti regex, would also be nice for a way to combine attempts across multiple anti regexes
    private final Map<Long, Map<ObjectId, Map<Long, Integer>>> attempts;

    private AntiRegexManager() {
        this.attempts = new HashMap<>();
    }

    public int getAttempts(long guildId, ObjectId id, long userId) {
        Map<ObjectId, Map<Long, Integer>> regexAttempts = this.attempts.get(guildId);
        if (regexAttempts != null) {
            Map<Long, Integer> userAttempts = regexAttempts.get(id);
            if (userAttempts != null) {
                return userAttempts.get(userId);
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
    }

}
