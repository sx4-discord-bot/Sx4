package com.sx4.bot.handlers;

import com.mongodb.client.model.Projections;
import com.sx4.bot.config.Config;
import com.sx4.bot.database.Database;
import com.sx4.bot.database.model.Operators;
import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.entities.mod.action.Action;
import com.sx4.bot.entities.mod.auto.MatchAction;
import com.sx4.bot.entities.settings.HolderType;
import com.sx4.bot.formatter.StringFormatter;
import com.sx4.bot.managers.AntiRegexManager;
import com.sx4.bot.managers.ModActionManager;
import com.sx4.bot.managers.MuteManager;
import com.sx4.bot.managers.TemporaryBanManager;
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
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AntiRegexHandler extends ListenerAdapter {

    private final AntiRegexManager manager = AntiRegexManager.get();
    private final ModActionManager modActionManager = ModActionManager.get();
    private final MuteManager muteManager = MuteManager.get();
    private final TemporaryBanManager banManager = TemporaryBanManager.get();
    private final Database database = Database.get();
    private final Config config = Config.get();

    private final ExecutorService executor = Executors.newCachedThreadPool();

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

            List<Document> regexes = this.database.getGuildById(guildId, Projections.include("antiRegex.regexes")).getEmbedded(List.of("antiRegex", "regexes"), Collections.emptyList());

            String content = message.getContentRaw();
            Regexes : for (Document regex : regexes) {
                ObjectId id = regex.getObjectId("id");

                Pattern pattern = Pattern.compile(regex.getString("pattern"));

                List<Document> channels = regex.getEmbedded(List.of("whitelist", "channels"), Collections.emptyList());
                Document channel = channels.stream()
                    .filter(d -> d.getLong("id") == channelId)
                    .findFirst()
                    .orElse(Database.EMPTY_DOCUMENT);

                List<Document> holders = channel.getList("holders", Document.class, Collections.emptyList());

                Matcher matcher = pattern.matcher(content);
                if (!matcher.matches()) {
                    continue;
                }

                for (Document holder : holders) {
                    long holderId = holder.getLong("id");
                    int type = holder.getInteger("type");
                    if (type == HolderType.USER.getType() && userId == holderId) {
                        continue Regexes;
                    } else if (type == HolderType.ROLE.getType() && roles.stream().anyMatch(role -> role.getIdLong() == holderId)) {
                        continue Regexes;
                    }
                }

                List<Document> groups = channel.getList("groups", Document.class, Collections.emptyList());
                for (Document group : groups) {
                    List<String> strings = group.getList("strings", String.class);

                    String match = matcher.group(group.getInteger("group"));
                    if (match != null && strings.contains(match)) {
                        continue Regexes;
                    }
                }

                int currentAttempts = this.manager.getAttempts(guildId, id, userId);

                Document actionData = regex.get("action", Database.EMPTY_DOCUMENT);

                Document match = actionData.get("match", Database.EMPTY_DOCUMENT);
                long matchRaw = match.get("raw", MatchAction.ALL);

                Document mod = actionData.get("mod", Database.EMPTY_DOCUMENT);
                Action action = mod.isEmpty() ? null : Action.fromData(mod);

                Document attempts = mod.get("attempts", Database.EMPTY_DOCUMENT);
                int maxAttempts = attempts.get("amount", 3);

                String matchMessage = this.format(match.get("message", AntiRegexManager.DEFAULT_MATCH_MESSAGE),
                    user, textChannel, id, currentAttempts + 1, maxAttempts, action);

                String modMessage = this.format(mod.get("message", AntiRegexManager.DEFAULT_MOD_MESSAGE),
                    user, textChannel, id, currentAttempts + 1, maxAttempts, action);

                if ((matchRaw & MatchAction.DELETE_MESSAGE.getRaw()) == MatchAction.DELETE_MESSAGE.getRaw() && selfMember.hasPermission(textChannel, Permission.MESSAGE_MANAGE)) {
                    message.delete().queue();
                }

                boolean canSend = selfMember.hasPermission(textChannel, Permission.MESSAGE_WRITE);
                boolean send = (matchRaw & MatchAction.SEND_MESSAGE.getRaw()) == MatchAction.SEND_MESSAGE.getRaw() && canSend;
                if (action != null && currentAttempts + 1 >= maxAttempts) {
                    Reason reason = new Reason(String.format("Sent a message which matched regex `%s` %d time%s", id.toHexString(), maxAttempts, maxAttempts == 1 ? "" : "s"));

                    // TODO: have a new collection for user attempts and update after the action has been performed

                    ModUtility.performAction(action, member, selfMember, reason).whenComplete((result, exception) -> {
                        if (exception != null) {
                            textChannel.sendMessage(exception.getMessage() + " " + this.config.getFailureEmote()).queue();
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

                this.manager.incrementAttempts(guildId, id, userId);

                Document userData = new Document("id", userId);
                Document reset = attempts.get("reset", Database.EMPTY_DOCUMENT);

                long duration = reset.get("after", 0L);
                if (duration != 0L) {
                    this.manager.scheduleResetAttempts(guildId, id, userId, duration, reset.getInteger("amount"));
                    userData.append("resetAt", Clock.systemUTC().instant().getEpochSecond() + duration);
                }

                Bson regexFilter = Operators.filter("$antiRegex.regexes", Operators.eq("$$this.id", id));
                Bson users = Operators.first(Operators.map(regexFilter, "$$this.users"));
                Bson userFilter = Operators.filter(users, Operators.eq("$$this.id", userId));
                Bson userAttempts = Operators.ifNull(Operators.first(Operators.map(userFilter, "$$this.attempts")), 0);
                List<Bson> update = List.of(Operators.set("antiRegex.regexes", Operators.concatArrays(List.of(Operators.mergeObjects(Operators.first(regexFilter), new Document("users", Operators.concatArrays(List.of(Operators.mergeObjects(Operators.first(userFilter), userData.append("attempts", Operators.add(userAttempts, 1)))), Operators.ifNull(Operators.filter(users, Operators.ne("$$this.id", userId)), Collections.EMPTY_LIST))))), Operators.filter("$antiRegex.regexes", Operators.ne("$$this.id", id)))));
                this.database.updateGuildById(guildId, update).whenComplete(Database.exceptionally());

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


