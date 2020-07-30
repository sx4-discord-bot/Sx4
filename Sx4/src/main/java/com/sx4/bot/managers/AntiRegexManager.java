package com.sx4.bot.managers;

import org.bson.types.ObjectId;

import java.util.HashMap;
import java.util.Map;

public class AntiRegexManager {

    public static final String DEFAULT_MATCH_MESSAGE = "{user.mention}, you cannot send that content here due to the regex {regex.id}"
        + "{{regex.action.exists}?, you will receive a {regex.action.name} if you continue **({regex.attempts.current}/{regex.attempts.max})**} :no_entry:";

    public static final String DEFAULT_MOD_MESSAGE = "**{user.tag}** has received a {regex.action.name} for sending a message which matched the regex"
        + "`{regex.id}` {regex.attempts.max} time{{regex.attempts.max}!=1?s} <:done:403285928233402378>";

    private static final AntiRegexManager INSTANCE = new AntiRegexManager();

    public static AntiRegexManager get() {
        return AntiRegexManager.INSTANCE;
    }

    // TODO Would also be nice for a way to combine attempts across multiple anti regexes
    private final Map<Long, Map<ObjectId, Map<Long, Integer>>> attempts;

    private AntiRegexManager() {
        this.attempts = new HashMap<>();
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
    }

}
