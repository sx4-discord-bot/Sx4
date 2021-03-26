package com.sx4.bot.managers;

import com.sx4.bot.core.Sx4;
import org.bson.types.ObjectId;

import java.util.HashMap;
import java.util.Map;

public class AntiRegexManager {

    public static final String DEFAULT_MATCH_MESSAGE = "{user.mention}, you cannot send that content here due to the regex `{regex.id}`"
        + "({regex.action.exists}?, you will receive a {regex.action.name} if you continue **({regex.attempts.current}/{regex.attempts.max})**:) :no_entry:";

    public static final String DEFAULT_MOD_MESSAGE = "**{user.tag}** has received a {regex.action.name} for sending a message which matched the regex "
        + "`{regex.id}` {regex.attempts.max} time({regex.attempts.max}!=1?s:) <:done:403285928233402378>";

    public static final String DEFAULT_INVITE_MATCH_MESSAGE = "{user.mention}, you cannot send discord invites here"
        + "({regex.action.exists}?, you will receive a {regex.action.name} if you continue **({regex.attempts.current}/{regex.attempts.max})**:) :no_entry:";

    public static final String DEFAULT_INVITE_MOD_MESSAGE = "**{user.tag}** has received a {regex.action.name} for sending a discord invite "
        + "{regex.attempts.max} time({regex.attempts.max}!=1?s:) <:done:403285928233402378>";

    // TODO: Would also be nice for a way to combine attempts across multiple anti regexes
    private final Map<ObjectId, Map<Long, Integer>> attempts;

    private final Sx4 bot;

    public AntiRegexManager(Sx4 bot) {
        this.attempts = new HashMap<>();
        this.bot = bot;
    }

    public synchronized int getAttempts(ObjectId id, long userId) {
        Map<Long, Integer> userAttempts = this.attempts.get(id);
        if (userAttempts != null) {
            return userAttempts.getOrDefault(userId, 0);
        }

        return 0;
    }

    public synchronized void setAttempts(ObjectId id, long userId, int amount) {
        this.attempts.compute(id, (idKey, idValue) -> {
            if (idValue == null) {
                if (amount < 1) {
                    return null;
                }

                Map<Long, Integer> userAttempts = new HashMap<>();
                userAttempts.put(userId, amount);

                return userAttempts;
            }

            idValue.compute(userId, (userKey, userValue) -> amount);

            return idValue;
        });
    }

    public synchronized void clearAttempts(ObjectId id, long userId) {
        Map<Long, Integer> userAttempts = this.attempts.get(id);
        if (userAttempts != null) {
            userAttempts.remove(userId);
        }
    }

}
