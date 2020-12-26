package com.sx4.bot.utility;

import com.sx4.bot.entities.logger.LoggerContext;
import com.sx4.bot.entities.management.logger.LoggerCategory;
import com.sx4.bot.entities.management.logger.LoggerEvent;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import org.bson.Document;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class LoggerUtility {

    public static Set<LoggerCategory> getCommonCategories(LoggerEvent... events) {
        if (events.length == 1) {
            return new HashSet<>(Arrays.asList(events[0].getCategories()));
        }

        List<List<LoggerCategory>> eventCategories = Arrays.stream(events)
            .map(LoggerEvent::getCategories)
            .map(Arrays::asList)
            .collect(Collectors.toList());

        Set<LoggerCategory> common = new LinkedHashSet<>();
        if (!eventCategories.isEmpty()) {
            Iterator<List<LoggerCategory>> iterator = eventCategories.iterator();
            common.addAll(iterator.next());
            while (iterator.hasNext()) {
                common.retainAll(iterator.next());
            }
        }

        return common;
    }

    public static long getEntityIdFromType(String query, Guild guild, LoggerCategory loggerCategory) {
        switch (loggerCategory) {
            case AUDIT:
            case USER:
                Member member = SearchUtility.getMember(guild, query);
                return member == null ? 0L : member.getIdLong();
            case VOICE_CHANNEL:
                VoiceChannel voiceChannel = SearchUtility.getVoiceChannel(guild, query);
                return voiceChannel == null ? 0L : voiceChannel.getIdLong();
            case CATEGORY:
                Category category = SearchUtility.getCategory(guild, query);
                return category == null ? 0L : category.getIdLong();
            case TEXT_CHANNEL:
                TextChannel textChannel = SearchUtility.getTextChannel(guild, query);
                return textChannel == null ? 0L : textChannel.getIdLong();
            case ROLE:
                Role role = SearchUtility.getRole(guild, query);
                return role == null ? 0L : role.getIdLong();
            case STORE_CHANNEL:
                StoreChannel storeChannel = SearchUtility.getStoreChannel(guild, query);
                return storeChannel == null ? 0L : storeChannel.getIdLong();
            case EMOTE:
                Emote emote = SearchUtility.getGuildEmote(guild, query);
                return emote == null ? 0L : emote.getIdLong();
        }

        return 0L;
    }

    public static boolean isWhitelisted(List<Document> entities, LoggerEvent event, LoggerContext context) {
        for (Document entity : entities) {
            if ((entity.getLong("events") & event.getRaw()) != event.getRaw()) {
                continue;
            }

            long id = entity.getLong("id");
            LoggerCategory category = LoggerCategory.fromType(entity.getInteger("type"));
            switch (category) {
                case ROLE:
                    if (context.getRoleId() == id) {
                        return false;
                    }

                    break;
                case USER:
                    if (context.getUserId() == id) {
                        return false;
                    }

                    break;
                case AUDIT:
                    if (context.getModeratorId() == id) {
                        return false;
                    }

                    break;
                case EMOTE:
                    if (context.getEmoteId() == id) {
                        return false;
                    }

                    break;
                case VOICE_CHANNEL:
                case TEXT_CHANNEL:
                case STORE_CHANNEL:
                case CATEGORY:
                    if (context.getChannelId() == id) {
                        return false;
                    }

                    break;
            }
        }

        return true;
    }

    public static boolean canSend(Document logger, LoggerEvent event, LoggerContext context) {
        if (!logger.get("enabled", true)) {
            return false;
        }

        if ((logger.get("events", LoggerEvent.ALL) & event.getRaw()) != event.getRaw()) {
            return false;
        }

        List<Document> entities = logger.getEmbedded(List.of("blacklist", "entities"), Collections.emptyList());
        return LoggerUtility.isWhitelisted(entities, event, context);
    }

    public static String getPermissionOverrideDifference(long permissionsBeforeAllowed, long permissionsBeforeInherit, long permissionsBeforeDenied, PermissionOverride permissionOverrideAfter) {
        StringBuilder builder = new StringBuilder();

        long permissionsAfterAllowed = permissionOverrideAfter.getAllowedRaw();
        long permissionsAllowed =  permissionsBeforeAllowed ^ permissionsAfterAllowed;

        EnumSet<Permission> permissionsAddedAllowed = Permission.getPermissions(permissionsAfterAllowed & permissionsAllowed);

        long permissionsAfterInherit = permissionOverrideAfter.getInheritRaw();
        long permissionsInherit =  permissionsBeforeInherit ^ permissionsAfterInherit;

        EnumSet<Permission> permissionsAddedInherit = Permission.getPermissions(permissionsAfterInherit & permissionsInherit);

        long permissionsAfterDenied = permissionOverrideAfter.getDeniedRaw();
        long permissionsDenied =  permissionsBeforeDenied ^ permissionsAfterDenied;

        EnumSet<Permission> permissionsAddedDenied = Permission.getPermissions(permissionsAfterDenied & permissionsDenied);

        if (!permissionsAddedAllowed.isEmpty() || !permissionsAddedInherit.isEmpty() || !permissionsAddedDenied.isEmpty()) {
            builder.append("\n```diff");

            for (Permission permission : permissionsAddedAllowed) {
                builder.append("\n+ ").append(permission.getName());
            }

            for (Permission permission : permissionsAddedInherit) {
                builder.append("\n/ ").append(permission.getName());
            }

            for (Permission permission : permissionsAddedDenied) {
                builder.append("\n- ").append(permission.getName());
            }

            builder.append("```");
        }

        return builder.toString();
    }

    public static String getRolePermissionDifference(long permissionsBefore, long permissionsAfter) {
        StringBuilder builder = new StringBuilder();

        long permissions = permissionsBefore ^ permissionsAfter;

        EnumSet<Permission> permissionsAdded = Permission.getPermissions(permissionsAfter & permissions);
        EnumSet<Permission> permissionsRemoved = Permission.getPermissions(permissionsBefore & permissions);

        if (!permissionsAdded.isEmpty() || !permissionsRemoved.isEmpty()) {
            builder.append("\n```diff");

            for (Permission permissionAdded : permissionsAdded) {
                builder.append("\n+ ").append(permissionAdded.getName());
            }

            for (Permission permissionRemoved : permissionsRemoved) {
                builder.append("\n- ").append(permissionRemoved.getName());
            }

            builder.append("```");
        }

        return builder.toString();
    }

    public static Pair<List<Role>, List<Role>> getRoleDifference(List<Role> newRoles, List<Role> oldRoles) {
        List<Role> rolesAdded = newRoles.stream()
            .filter(Predicate.not(oldRoles::contains))
            .collect(Collectors.toList());

        List<Role> rolesRemoved = oldRoles.stream()
            .filter(Predicate.not(newRoles::contains))
            .collect(Collectors.toList());

        return Pair.of(rolesAdded, rolesRemoved);
    }

    public static String getRoleDifferenceMessage(List<Role> rolesRemoved, List<Role> rolesAdded, int extraLength) {
        int maxLength = MessageEmbed.TEXT_MAX_LENGTH
            - extraLength
            - 9 /* "\n```diff" */
            - 3 /* "```" */
            - 18 /* "\n+ x more added" */
            - 18 /* "\n- x more removed" */;

        StringBuilder builder = new StringBuilder("\n```diff");
        for (int i = 0; i < rolesAdded.size(); i++) {
            Role role = rolesAdded.get(i);

            String entry = "\n+ " + role.getName();
            if (builder.length() + entry.length() <= maxLength) {
                builder.append(entry);
            } else {
                return builder.append(String.format("\n+ %d more added", rolesAdded.size() - i))
                    .append(String.format("\n- %d more removed```", rolesRemoved.size()))
                    .toString();
            }
        }

        for (int i = 0; i < rolesRemoved.size(); i++) {
            Role role = rolesRemoved.get(i);

            String entry = "\n- " + role.getName();
            if (builder.length() + entry.length() <= maxLength) {
                builder.append(entry);
            } else {
                builder.append(String.format("\n- %d more removed```", rolesRemoved.size() - i));
                break;
            }
        }

        return builder.append("```").toString();
    }

    public static String getChannelTypeReadable(ChannelType channelType) {
        String type;
        if (channelType == ChannelType.TEXT || channelType == ChannelType.VOICE || channelType == ChannelType.STORE) {
            type = channelType.toString().toLowerCase() + " channel";
        } else if(channelType == ChannelType.CATEGORY) {
            type = "category";
        } else {
            // return unknown so it can easily be reported as a bug user side rather than throwing an NPE
            return "unknown";
        }

        return type;
    }

}
