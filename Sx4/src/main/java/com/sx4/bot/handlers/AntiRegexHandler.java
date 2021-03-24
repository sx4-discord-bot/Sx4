package com.sx4.bot.handlers;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.Database;
import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.entities.mod.action.Action;
import com.sx4.bot.entities.mod.auto.MatchAction;
import com.sx4.bot.entities.mod.auto.RegexType;
import com.sx4.bot.entities.settings.HolderType;
import com.sx4.bot.exceptions.mod.ModException;
import com.sx4.bot.formatter.StringFormatter;
import com.sx4.bot.managers.AntiRegexManager;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.FutureUtility;
import com.sx4.bot.utility.ModUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.time.Clock;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AntiRegexHandler implements EventListener {

    private final Pattern invitePattern = Pattern.compile("discord(?:(?:(?:app)?\\.com|\\.co|\\.media)/invite|\\.gg)/([a-z\\-0-9]{2,32})", Pattern.CASE_INSENSITIVE);

    private final ExecutorService executor = Executors.newCachedThreadPool();

    private final Sx4 bot;

    public AntiRegexHandler(Sx4 bot) {
        this.bot = bot;
    }

    private String format(String message, User user, TextChannel channel, ObjectId id, int currentAttempts, int maxAttempts, Action action) {
        return new StringFormatter(message)
            .user(user)
            .channel(channel)
            .append("regex.id", id.toHexString())
            .append("regex.action.name", action == null ? null : action.getName().toLowerCase())
            .append("regex.action.exists", action != null)
            .append("regex.attempts.current", currentAttempts)
            .append("regex.attempts.max", maxAttempts)
            .parse();
    }

    public void handle(Message message) {
        Member member = message.getMember();
        if (member == null || member.hasPermission(Permission.ADMINISTRATOR)) {
            return;
        }

        User user = member.getUser();
        Guild guild = message.getGuild();
        Member selfMember = guild.getSelfMember();
        TextChannel textChannel = message.getTextChannel();

        if (user.isBot()) {
            return;
        }

        this.executor.submit(() -> {
            long guildId = guild.getIdLong(), userId = member.getIdLong(), channelId = textChannel.getIdLong();
            List<Role> roles = member.getRoles();

            List<Document> regexes = this.bot.getDatabase().getRegexes(Filters.eq("guildId", guildId), Database.EMPTY_DOCUMENT).into(new ArrayList<>());

            String content = message.getContentRaw();

            List<CompletableFuture<Document>> matches = new ArrayList<>();
            Regexes : for (Document regex : regexes) {
                List<Document> channels = regex.getList("whitelist", Document.class, Collections.emptyList());
                Document channel = channels.stream()
                    .filter(d -> d.getLong("id") == channelId)
                    .findFirst()
                    .orElse(Database.EMPTY_DOCUMENT);

                List<Document> holders = channel.getList("holders", Document.class, Collections.emptyList());
                for (Document holder : holders) {
                    long holderId = holder.getLong("id");
                    int type = holder.getInteger("type");
                    if (type == HolderType.USER.getType() && userId == holderId) {
                        continue Regexes;
                    } else if (type == HolderType.ROLE.getType() && (guildId == holderId || roles.stream().anyMatch(role -> role.getIdLong() == holderId))) {
                        continue Regexes;
                    }
                }

                CompletableFuture<Document> future = null;

                RegexType type = RegexType.fromId(regex.getInteger("type"));
                if (type == RegexType.REGEX) {
                    Pattern pattern = Pattern.compile(regex.getString("pattern"));

                    Matcher matcher = pattern.matcher(content);

                    int matchCount = 0, totalCount = 0;
                    while (matcher.find()) {
                        List<Document> groups = channel.getList("groups", Document.class, Collections.emptyList());
                        for (Document group : groups) {
                            List<String> strings = group.getList("strings", String.class, Collections.emptyList());

                            String match = matcher.group(group.getInteger("group"));
                            if (match != null && strings.contains(match)) {
                                matchCount++;
                            }
                        }

                        totalCount++;
                    }

                    System.out.println(matchCount + " - " + totalCount);

                    if (matchCount == totalCount) {
                        continue;
                    }

                    future = CompletableFuture.completedFuture(regex);
                } else if (type == RegexType.INVITE) {
                    Matcher matcher = this.invitePattern.matcher(content);

                    Set<String> codes = new HashSet<>();
                    while (matcher.find()) {
                        codes.add(matcher.group(1));
                    }

                    List<CompletableFuture<Invite>> futures = codes.stream()
                        .map(code -> Invite.resolve(message.getJDA(), code, true).submit())
                        .collect(Collectors.toList());

                    List<Long> guilds = channel.getList("guilds", Long.class, Collections.emptyList());

                    future = FutureUtility.anyOf(futures, invite -> {
                        Invite.Guild inviteGuild = invite.getGuild();
                        return inviteGuild == null || (!guilds.contains(inviteGuild.getIdLong()) && inviteGuild.getIdLong() != guildId);
                    }).thenApply(invite -> invite == null ? null : regex);
                }

                if (future != null) {
                    matches.add(future);
                }
            }

            FutureUtility.anyOf(matches, Objects::nonNull).thenAccept(regex -> {
                if (regex == null) {
                    return;
                }

                ObjectId id = regex.getObjectId("_id"), regexId = regex.getObjectId("regexId");

                int currentAttempts = this.bot.getAntiRegexManager().getAttempts(id, userId);

                Document match = regex.get("match", Database.EMPTY_DOCUMENT);
                long matchAction = match.get("action", MatchAction.ALL);

                Document mod = regex.get("mod", Database.EMPTY_DOCUMENT);
                Document actionData = mod.get("action", Document.class);

                Action action = actionData == null ? null : Action.fromData(actionData);

                Document attempts = regex.get("attempts", Database.EMPTY_DOCUMENT);
                int maxAttempts = attempts.get("amount", 3);

                String matchMessage = this.format(match.get("message", AntiRegexManager.DEFAULT_MATCH_MESSAGE),
                    user, textChannel, regexId, currentAttempts + 1, maxAttempts, action);

                String modMessage = this.format(mod.get("message", AntiRegexManager.DEFAULT_MOD_MESSAGE),
                    user, textChannel, regexId, currentAttempts + 1, maxAttempts, action);

                if ((matchAction & MatchAction.DELETE_MESSAGE.getRaw()) == MatchAction.DELETE_MESSAGE.getRaw() && selfMember.hasPermission(textChannel, Permission.MESSAGE_MANAGE)) {
                    message.delete().queue();
                }

                boolean canSend = selfMember.hasPermission(textChannel, Permission.MESSAGE_WRITE);
                boolean send = (matchAction & MatchAction.SEND_MESSAGE.getRaw()) == MatchAction.SEND_MESSAGE.getRaw() && canSend;
                if (action != null && currentAttempts + 1 >= maxAttempts) {
                    Reason reason = new Reason(String.format("Sent a message which matched regex `%s` %d time%s", regexId.toHexString(), maxAttempts, maxAttempts == 1 ? "" : "s"));

                    ModUtility.performAction(this.bot, action, member, selfMember, reason).thenCompose(result -> {
                        if (send) {
                            textChannel.sendMessage(modMessage).allowedMentions(EnumSet.allOf(Message.MentionType.class)).queue();
                        }

                        Bson filter = Filters.and(Filters.eq("userId", userId), Filters.eq("regexId", id));
                        return this.bot.getDatabase().deleteRegexAttempt(filter);
                    }).whenComplete((result, exception) -> {
                        if (exception instanceof ModException) {
                            textChannel.sendMessage(exception.getMessage() + " " + this.bot.getConfig().getFailureEmote()).queue();
                            return;
                        }

                        if (ExceptionUtility.sendErrorMessage(this.bot.getShardManager(), exception)) {
                            return;
                        }

                        this.bot.getAntiRegexManager().clearAttempts(id, userId);
                    });

                    return;
                }

                if (send) {
                    textChannel.sendMessage(matchMessage).allowedMentions(EnumSet.allOf(Message.MentionType.class)).queue();
                }

                Document reset = attempts.get("reset", Database.EMPTY_DOCUMENT);
                long duration = reset.get("after", 0L);

                Bson update = Updates.inc("attempts", 1);
                if (duration != 0L) {
                    update = Updates.combine(update, Updates.set("resetAt", Clock.systemUTC().instant().getEpochSecond() + duration));
                }

                Bson filter = Filters.and(Filters.eq("userId", userId), Filters.eq("regexId", id));
                this.bot.getDatabase().updateRegexAttempt(filter, update).whenComplete((result, exception) -> {
                    if (ExceptionUtility.sendErrorMessage(this.bot.getShardManager(), exception)) {
                        return;
                    }

                    this.bot.getAntiRegexManager().incrementAttempts(id, userId);
                    if (duration != 0L) {
                        this.bot.getAntiRegexManager().scheduleResetAttempts(id, userId, duration, reset.getInteger("amount"));
                    }
                });
            });
        });
    }

    @Override
    public void onEvent(GenericEvent event) {
        if (event instanceof GuildMessageReceivedEvent) {
            this.handle(((GuildMessageReceivedEvent) event).getMessage());
        } else if (event instanceof GuildMessageUpdateEvent) {
            this.handle(((GuildMessageUpdateEvent) event).getMessage());
        }
    }

}


