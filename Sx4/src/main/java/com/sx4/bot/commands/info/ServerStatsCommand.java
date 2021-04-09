package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.mongodb.client.model.Filters;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.Database;
import com.sx4.bot.entities.info.ServerStatsType;
import com.sx4.bot.utility.StringUtility;
import com.sx4.bot.utility.TimeUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import org.bson.Document;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class ServerStatsCommand extends Sx4Command {

	public ServerStatsCommand() {
		super("server stats", 324);

		super.setDescription("View some basic statistics on the current server");
		super.setAliases("serverstats");
		super.setExamples("server stats", "server stats 2d", "server stats 1h");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void compute(Map<Long, Map<ServerStatsType, Integer>> map, long hours, int joins, int messages) {
		map.computeIfPresent(hours, (mapKey, type) -> {
			type.compute(ServerStatsType.JOINS, (typeKey, amount) -> amount += joins);
			type.compute(ServerStatsType.MESSAGES, (typeKey, amount) -> amount += messages);

			return type;
		});
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="duration", endless=true, nullDefault=true) Duration duration) {
		if (duration != null && (duration.toHours() < 1 || duration.toHours() > 168)) {
			event.replyFailure("Time frame cannot be less than 1 hour or more than 7 days").queue();
			return;
		}

		List<Document> data = event.getDatabase().getServerStats(Filters.eq("guildId", event.getGuild().getIdLong()), Database.EMPTY_DOCUMENT).into(new ArrayList<>());
		if (data.isEmpty()) {
			event.replyFailure("There has been no data recorded for this server yet").queue();
			return;
		}

		Date lastUpdate = event.getBot().getServerStatsManager().getLastUpdate();
		OffsetDateTime currentHour = OffsetDateTime.now(ZoneOffset.UTC).withMinute(0).withSecond(0).withNano(0);

		Map<Long, Map<ServerStatsType, Integer>> map = new HashMap<>();
		if (duration == null) {
			Map<ServerStatsType, Integer> defaultStatsDay = new HashMap<>();
			defaultStatsDay.put(ServerStatsType.JOINS, 0);
			defaultStatsDay.put(ServerStatsType.MESSAGES, 0);
			map.put(24L, defaultStatsDay);

			Map<ServerStatsType, Integer> defaultStatsWeek = new HashMap<>();
			defaultStatsWeek.put(ServerStatsType.JOINS, 0);
			defaultStatsWeek.put(ServerStatsType.MESSAGES, 0);
			map.put(168L, defaultStatsWeek);
		} else {
			Map<ServerStatsType, Integer> defaultStats = new HashMap<>();
			defaultStats.put(ServerStatsType.JOINS, 0);
			defaultStats.put(ServerStatsType.MESSAGES, 0);
			map.put(duration.toHours(), defaultStats);
		}

		for (Document stats : data) {
			Date time = stats.getDate("time");
			Duration difference = Duration.between(time.toInstant(), currentHour);

			int joins = stats.getInteger("joins", 0), messages = stats.getInteger("messages", 0);
			if (duration != null && difference.toHours() <= duration.toHours()) {
				this.compute(map, duration.toHours(), joins, messages);
			}

			if (duration == null && difference.toHours() <= 24) {
				this.compute(map, 24, joins, messages);
			}

			if (duration == null && difference.toDays() <= 7) {
				this.compute(map, 168, joins, messages);
			}

			lastUpdate = lastUpdate == null || lastUpdate.getTime() < time.getTime() ? time : lastUpdate;
		}

		EmbedBuilder embed = new EmbedBuilder()
			.setAuthor("Server Stats", null, event.getGuild().getIconUrl())
			.setTimestamp(lastUpdate.toInstant());

		int i = 0;
		for (long key : map.keySet()) {
			Map<ServerStatsType, Integer> stats = map.get(key);
			for (ServerStatsType type : stats.keySet()) {
				embed.addField(StringUtility.title(type.getField()) + " (" + TimeUtility.getTimeString(key, TimeUnit.HOURS) + ")", String.format("%,d", stats.get(type)), true);

				if ((i++ & 1) == 0) {
					embed.addBlankField(true);
				}
			}
		}

		event.reply(embed.build()).queue();
	}

}
