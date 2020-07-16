package com.sx4.bot.handlers;

import com.mongodb.client.model.Projections;
import com.sx4.bot.database.Database;
import com.sx4.bot.entities.settings.HolderType;
import com.sx4.bot.managers.AntiRegexManager;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bson.Document;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AntiRegexHandler extends ListenerAdapter {

    private final AntiRegexManager manager = AntiRegexManager.get();

    private final ExecutorService executor = Executors.newCachedThreadPool();

    public void handle(Message message) {
        Database database = Database.get();
        this.executor.submit(() -> {
            long guildId = message.getGuild().getIdLong(), userId = message.getAuthor().getIdLong(), channelId = message.getChannel().getIdLong();
            List<Role> roles = message.getMember().getRoles();

            List<Document> regexes = database.getGuildById(guildId, Projections.include("antiRegex.regexes")).getEmbedded(List.of("antiRegex", "regexes"), Collections.emptyList());

            String content = message.getContentRaw();
            Regexes : for (Document regex : regexes) {
                Pattern pattern = Pattern.compile(regex.getString("pattern"));

                Matcher matcher = pattern.matcher(content);
                if (!matcher.matches()) {
                    continue;
                }

                List<Document> channels = regex.getEmbedded(List.of("whitelist", "channels"), Collections.emptyList());
                Document channel = channels.stream()
                    .filter(d -> d.get("id", 0L) == channelId)
                    .findFirst()
                    .orElse(null);

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

                // TODO: Mod action and Match action handling and update attempts for user in db
                //this.manager.incrementAttempts(guildId, attemptId, userId);
            }
        });
    }

    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        this.handle(event.getMessage());
    }

    public void onGuildMessageUpdate(GuildMessageUpdateEvent event) {
        this.handle(event.getMessage());
    }
}


