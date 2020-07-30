package com.sx4.bot.handlers;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.sx4.bot.database.Database;
import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.entities.mod.action.Action;
import com.sx4.bot.entities.mod.auto.MatchAction;
import com.sx4.bot.entities.settings.HolderType;
import com.sx4.bot.formatter.Formatter;
import com.sx4.bot.managers.AntiRegexManager;
import com.sx4.bot.managers.ModActionManager;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.ModUtility;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AntiRegexHandler extends ListenerAdapter {

    private final AntiRegexManager manager = AntiRegexManager.get();
    private final ModActionManager modActionManager = ModActionManager.get();
    private final Database database = Database.get();

    private final ExecutorService executor = Executors.newCachedThreadPool();

    private String format(String message, User user, TextChannel channel, String messageId, ObjectId id, int currentAttempts, int maxAttempts, Action action) {
        return new Formatter(message)
            .user(user)
            .channel(channel)
            .append("message.id", messageId)
            .append("regex.id", id.toHexString())
            .append("regex.action", action == null ? null : action.getName().toLowerCase())
            .append("regex.action.exists", action != null)
            .append("regex.attempts.current", currentAttempts + 1)
            .append("regex.attempts.max", maxAttempts)
            .format();
    }

    public void handle(Message message) {
        Member member = message.getMember();
        if (member == null) {
            return;
        }

        User user = member.getUser();
        Guild guild = message.getGuild();
        TextChannel textChannel = message.getTextChannel();

        if (user.isBot()) {
            return;
        }


        Database database = Database.get();
        //this.executor.submit(() -> {
            long guildId = guild.getIdLong(), userId = member.getIdLong(), channelId = textChannel.getIdLong();
            List<Role> roles = member.getRoles();

            List<Document> regexes = database.getGuildById(guildId, Projections.include("antiRegex.regexes")).getEmbedded(List.of("antiRegex", "regexes"), Collections.emptyList());

            String content = message.getContentRaw();
            Regexes : for (Document regex : regexes) {
                ObjectId id = regex.getObjectId("id");

                Pattern pattern = Pattern.compile(regex.getString("pattern"));

                Matcher matcher = pattern.matcher(content);
                if (!matcher.matches()) {
                    continue;
                }

                List<Document> channels = regex.getEmbedded(List.of("whitelist", "channels"), Collections.emptyList());
                Document channel = channels.stream()
                    .filter(d -> d.get("id", 0L) == channelId)
                    .findFirst()
                    .orElse(Database.EMPTY_DOCUMENT);

                List<Document> holders = channel.getList("holders", Document.class, Collections.emptyList());
                for (Document holder : holders) {
                    long holderId = holder.get("id", 0L);
                    if (holder.get("type", 0) == HolderType.USER.getType() && userId == holderId) {
                        continue Regexes;
                    } else if (roles.stream().anyMatch(role -> role.getIdLong() == holderId)) {
                        continue Regexes;
                    }
                }

                List<Document> groups = channel.getList("groups", Document.class, Collections.emptyList());
                for (Document group : groups) {
                    List<String> strings = group.getList("strings", String.class);

                    String match = matcher.group(group.get("group", 0));
                    if (match != null && strings.contains(match)) {
                        continue Regexes;
                    }
                }

                int currentAttempts = this.manager.getAttempts(guildId, id, userId);

                Document actionData = regex.get("action", Database.EMPTY_DOCUMENT);

                Document match = actionData.get("match", Database.EMPTY_DOCUMENT);
                long matchRaw = match.get("raw", MatchAction.ALL);

                Document modAction = actionData.get("mod", Database.EMPTY_DOCUMENT);
                Action action = modAction.isEmpty() ? null : Action.fromData(modAction);

                Document attempts = modAction.get("attempts", Database.EMPTY_DOCUMENT);
                int maxAttempts = attempts.get("amount", 3);

                String matchMessage = this.format(match.get("message", AntiRegexManager.DEFAULT_MATCH_MESSAGE),
                    user, textChannel, message.getId(), id, currentAttempts, maxAttempts, action);

                String modMessage = this.format(modAction.get("message", AntiRegexManager.DEFAULT_MOD_MESSAGE),
                    user, textChannel, message.getId(), id, currentAttempts, maxAttempts, action);

                if ((matchRaw & MatchAction.DELETE_MESSAGE.getRaw()) == MatchAction.DELETE_MESSAGE.getRaw()) {
                    message.delete().queue();
                }

                if (action != null && currentAttempts + 1 >= maxAttempts) {
                    // TODO: execute mod action
                    Reason reason = new Reason(String.format("Sent a message which matched regex `%s` %d time%s", id.toHexString(), maxAttempts, maxAttempts == 1 ? "" : "s"));
                    switch (action.getModAction()) {
                        case WARN:
                            ModUtility.warn(member, guild.getSelfMember(), reason).whenComplete((warn, exception) -> {
                                if (exception == null) {
                                    this.manager.clearAttempts(guildId, id, userId);

                                    UpdateOptions options = new UpdateOptions().arrayFilters(List.of(Filters.eq("regex.id", id)));
                                    this.database.updateGuildById(guildId, Updates.pull("antiRegex.regexes.$[regex].users", Filters.eq("id", userId)), options).whenComplete((result, writeException) ->
                                        ExceptionUtility.sendErrorMessage(writeException)
                                    );
                                }
                            });
                        case MUTE:
                        case MUTE_EXTEND:


                            break;
                        case KICK:
                            break;
                        case TEMP_BAN:
                            break;
                        case BAN:
                            break;
                        default:
                            break;
                    }

                    break;
                }

                if ((matchRaw & MatchAction.SEND_MESSAGE.getRaw()) == MatchAction.SEND_MESSAGE.getRaw()) {
                    textChannel.sendMessage(matchMessage).queue();
                }

                this.manager.incrementAttempts(guildId, id, userId);

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


