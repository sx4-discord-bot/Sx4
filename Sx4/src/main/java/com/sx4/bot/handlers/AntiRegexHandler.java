package com.sx4.bot.handlers;

import com.mongodb.client.model.Filters;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.Database;
import com.sx4.bot.database.model.Operators;
import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.entities.mod.action.Action;
import com.sx4.bot.entities.mod.auto.MatchAction;
import com.sx4.bot.entities.settings.HolderType;
import com.sx4.bot.formatter.StringFormatter;
import com.sx4.bot.managers.AntiRegexManager;
import com.sx4.bot.utility.ModUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AntiRegexHandler extends ListenerAdapter {

    private final Sx4 bot;

    private final ExecutorService executor = Executors.newCachedThreadPool();

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
        if (member == null) {
            return;
        }

        User user = member.getUser();
        Guild guild = message.getGuild();
        Member selfMember = guild.getSelfMember();
        TextChannel textChannel = message.getTextChannel();

        if (user.isBot()) {
            return;
        }

        //this.executor.submit(() -> {
            long guildId = guild.getIdLong(), userId = member.getIdLong(), channelId = textChannel.getIdLong();
            List<Role> roles = member.getRoles();

            List<Document> regexes = this.bot.getDatabase().getRegexes(Filters.eq("guildId", guildId), Database.EMPTY_DOCUMENT).into(new ArrayList<>());

            String content = message.getContentRaw();
            Regexes : for (Document regex : regexes) {
                ObjectId regexId = regex.getObjectId("regexId"), id = regex.getObjectId("_id");

                Pattern pattern = Pattern.compile(regex.getString("pattern"));

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
                    } else if (type == HolderType.ROLE.getType() && roles.stream().anyMatch(role -> role.getIdLong() == holderId)) {
                        continue Regexes;
                    }
                }

                Matcher matcher = pattern.matcher(content);

                int matchCount = 0, totalCount = 0;
                while (matcher.find()) {
                    List<Document> groups = channel.getList("groups", Document.class, Collections.emptyList());
                    for (Document group : groups) {
                        List<String> strings = group.getList("strings", String.class);

                        String match = matcher.group(group.getInteger("group"));
                        if (match != null && strings.contains(match)) {
                            matchCount++;
                        }
                    }

                    totalCount++;
                }

                if (matchCount == totalCount) {
                    continue;
                }

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

                    // TODO: have a new collection for user attempts and update after the action has been performed

                    ModUtility.performAction(this.bot, action, member, selfMember, reason).whenComplete((result, exception) -> {
                        if (exception != null) {
                            textChannel.sendMessage(exception.getMessage() + " " + this.bot.getConfig().getFailureEmote()).queue();
                            return;
                        }

                        if (send) {
                            textChannel.sendMessage(modMessage).allowedMentions(EnumSet.allOf(Message.MentionType.class)).queue();
                        }
                    });

                    break;
                }

                if (send) {
                    textChannel.sendMessage(matchMessage).allowedMentions(EnumSet.allOf(Message.MentionType.class)).queue();
                }

                this.bot.getAntiRegexManager().incrementAttempts(id, userId);

                Document userData = new Document("id", userId);
                Document reset = attempts.get("reset", Database.EMPTY_DOCUMENT);

                long duration = reset.get("after", 0L);
                if (duration != 0L) {
                    this.bot.getAntiRegexManager().scheduleResetAttempts(id, userId, duration, reset.getInteger("amount"));
                    userData.append("resetAt", Clock.systemUTC().instant().getEpochSecond() + duration);
                }

                Bson regexFilter = Operators.filter("$antiRegex.regexes", Operators.eq("$$this.id", regexId));
                Bson users = Operators.first(Operators.map(regexFilter, "$$this.users"));
                Bson userFilter = Operators.filter(users, Operators.eq("$$this.id", userId));
                Bson userAttempts = Operators.ifNull(Operators.first(Operators.map(userFilter, "$$this.attempts")), 0);
                List<Bson> update = List.of(Operators.set("antiRegex.regexes", Operators.concatArrays(List.of(Operators.mergeObjects(Operators.first(regexFilter), new Document("users", Operators.concatArrays(List.of(Operators.mergeObjects(Operators.first(userFilter), userData.append("attempts", Operators.add(userAttempts, 1)))), Operators.ifNull(Operators.filter(users, Operators.ne("$$this.id", userId)), Collections.EMPTY_LIST))))), Operators.filter("$antiRegex.regexes", Operators.ne("$$this.id", regexId)))));
                this.bot.getDatabase().updateGuildById(guildId, update).whenComplete(Database.exceptionally(this.bot.getShardManager()));

                break;
            }
        //});
    }

    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        this.handle(event.getMessage());
    }

    public void onGuildMessageUpdate(GuildMessageUpdateEvent event) {
        this.handle(event.getMessage());
    }
}


