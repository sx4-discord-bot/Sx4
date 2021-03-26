package com.sx4.bot.commands.info;

import com.mongodb.client.model.Filters;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.Database;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import org.bson.Document;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ServerStatsCommand extends Sx4Command {

	public ServerStatsCommand() {
		super("server stats", 324);

		super.setDescription("View some basic statistics on the current server");
		super.setAliases("serverstats");
		super.setExamples("server stats");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event) {
		List<Document> data = event.getDatabase().getServerStats(Filters.eq("guildId", event.getGuild().getIdLong()), Database.EMPTY_DOCUMENT).into(new ArrayList<>());
		if (data.isEmpty()) {
			event.replyFailure("There has been no data recorded for this server yet").queue();
			return;
		}

		Date lastUpdate = null;

		int joinsDay = 0, messagesDay = 0, joinsWeek = 0, messagesWeek = 0;
		for (int i = 0; i < data.size(); i++) {
			Document stats = data.get(i);
			if (i < 25) {
				joinsDay += stats.getInteger("joins", 0);
				messagesDay += stats.getInteger("messages", 0);
			}

			joinsWeek += stats.getInteger("joins", 0);
			messagesWeek += stats.getInteger("messages", 0);

			Date time = stats.getDate("time");
			lastUpdate = lastUpdate == null || lastUpdate.getTime() < time.getTime() ? time : lastUpdate;
		}

		EmbedBuilder embed = new EmbedBuilder()
			.setAuthor("Server Stats", null, event.getGuild().getIconUrl())
			.addField("Messages (Last 24h)", String.format("%,d", messagesDay), true)
			.addBlankField(true)
			.addField("Joins (Last 24h)", String.format("%,d", joinsDay), true)
			.addField("Messages (Last 7d)", String.format("%,d", messagesWeek), true)
			.addBlankField(true)
			.addField("Joins (Last 7d)", String.format("%,d", joinsWeek), true)
			.setFooter("Updated every hour")
			.setTimestamp(lastUpdate.toInstant());

		event.reply(embed.build()).queue();
	}

}
