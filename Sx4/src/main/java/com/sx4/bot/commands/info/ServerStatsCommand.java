package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.option.Option;
import com.mongodb.client.model.Filters;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.entities.info.ServerStatsType;
import com.sx4.bot.entities.interaction.ButtonType;
import com.sx4.bot.entities.interaction.CustomButtonId;
import com.sx4.bot.managers.ServerStatsManager;
import com.sx4.bot.utility.StringUtility;
import com.sx4.bot.utility.TimeUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import org.bson.Document;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class ServerStatsCommand extends Sx4Command {

	public static final DateTimeFormatter GRAPH_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/uu hh:mm");

	public ServerStatsCommand() {
		super("server stats", 324);

		super.setDescription("View some basic statistics on the current server");
		super.setAliases("serverstats");
		super.setExamples("server stats", "server stats 12h", "server stats 2d --live", "server stats --reset", "server stats --reset --live");
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

	public void onCommand(Sx4CommandEvent event, @Argument(value="duration", endless=true, nullDefault=true) Duration duration, @Option(value="live", description="Adds the live counting data to the total") boolean live, @Option(value="reset", description="Gets the stats since 00:00 UTC") boolean reset) {
		if (duration != null && (duration.toHours() < 1 || duration.toHours() > 168)) {
			event.replyFailure("Time frame cannot be less than 1 hour or more than 7 days").queue();
			return;
		}

		List<Document> data = event.getMongo().getServerStats(Filters.eq("guildId", event.getGuild().getIdLong()), MongoDatabase.EMPTY_DOCUMENT).into(new ArrayList<>());
		if (data.isEmpty()) {
			event.replyFailure("There has been no data recorded for this server yet").queue();
			return;
		}

		Date lastUpdate = event.getBot().getServerStatsManager().getLastUpdate();
		OffsetDateTime currentHour = OffsetDateTime.now(ZoneOffset.UTC).withMinute(0).withSecond(0).withNano(0);

		if (reset) {
			duration = Duration.between(currentHour.withHour(0), currentHour);
		}

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

		StringJoiner dataPoints = new StringJoiner("&");
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

			String timeReadable = URLEncoder.encode(ServerStatsCommand.GRAPH_FORMATTER.format(time.toInstant().atOffset(ZoneOffset.UTC)), StandardCharsets.UTF_8);
			dataPoints.add(timeReadable + "=" + messages);

			lastUpdate = lastUpdate == null || lastUpdate.getTime() < time.getTime() ? time : lastUpdate;
		}

		CustomButtonId buttonId = new CustomButtonId.Builder()
			.setType(ButtonType.SHOW_SERVER_STATS_JOIN_GRAPH)
			.setAnyOwner()
			.build();

		EmbedBuilder embed = new EmbedBuilder()
			.setAuthor("Server Stats", null, event.getGuild().getIconUrl())
			.setFooter("Updated every hour")
			.setImage(event.getConfig().getImageWebserverUrl("line-graph") + "?x_header=Time&y_header=Messages%20Sent&" + dataPoints)
			.setTimestamp(lastUpdate.toInstant());

		ServerStatsManager manager = event.getBot().getServerStatsManager();
		for (long key : map.keySet()) {
			Map<ServerStatsType, Integer> stats = map.get(key);
			for (ServerStatsType type : stats.keySet()) {
				embed.addField(StringUtility.title(type.getField()) + " (" + TimeUtility.LONG_TIME_FORMATTER.parse(Duration.of(key, ChronoUnit.HOURS)) + ")", String.format("%,d", stats.get(type) + (live ? manager.getCounter(event.getGuild().getIdLong(), type) : 0)), true);
			}
		}

		Button button = buttonId.asButton(ButtonStyle.SECONDARY, "View Joins Graph").withEmoji(Emoji.fromUnicode("\uD83D\uDCC8"));

		event.reply(embed.build()).setActionRow(button).queue();
	}

}
