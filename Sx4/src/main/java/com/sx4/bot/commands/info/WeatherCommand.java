package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.http.HttpCallback;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import okhttp3.Request;
import org.bson.Document;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;

public class WeatherCommand extends Sx4Command {

	public enum Direction {

		NORTH(0, "North"),
		NORTH_EAST(45, "North East"),
		EAST(90, "East"),
		SOUTH_EAST(135, "South East"),
		SOUTH(180, "South"),
		SOUTH_WEST(225, "South West"),
		WEST(270, "West"),
		NORTH_WEST(315, "North West");

		private final int degrees;
		private final String name;

		private Direction(int degrees, String name) {
			this.degrees = degrees;
			this.name = name;
		}

		public String getName() {
			return this.name;
		}

		public int getDegrees() {
			return this.degrees;
		}

		public static Direction getDirection(int degrees) {
			return Arrays.stream(Direction.values())
				.min(Comparator.comparingInt(a -> Math.abs(a.getDegrees() - degrees)))
				.orElse(null);
		}

	}

	private final DateTimeFormatter parseFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssZZ");
	private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

	public WeatherCommand() {
		super("weather", 35);

		super.setDescription("Find out the weather in a specific area");
		super.setCooldownDuration(3);
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setExamples("weather London", "weather Stockholm", "weather Florida");
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="query", endless=true) String query) {
		Request request = new Request.Builder()
			.url(this.config.getSearchWebserverUrl("weather") + "?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8))
			.build();

		event.getClient().newCall(request).enqueue((HttpCallback) response -> {
			if (response.code() == 404) {
				event.replyFailure("I could not find that location").queue();
				return;
			}

			Document document = Document.parse(response.body().string());
			if (!response.isSuccessful()) {
				StringBuilder builder = new StringBuilder("Command failed with status " + response.code());
				if (document.containsKey("message")) {
					builder.append(" with message `").append(document.getString("message")).append("`");
				}

				event.replyFailure(builder.toString()).queue();
				return;
			}

			Document now = document.get("now", Document.class);
			Document today = document.get("today", Document.class);

			int windDegrees = now.getInteger("wdir");
			Integer maxTemp = today.getInteger("max_temp");

			EmbedBuilder embed = new EmbedBuilder();
			embed.setAuthor(document.getString("location"), document.getString("url"), null);
			embed.setTitle(now.getString("phrase_32char"));
			embed.addField("Temperature", String.format("Maximum: %s°C\nCurrent: %d°C\nMinimum: %d°C", maxTemp == null ? "--" : maxTemp, now.getInteger("temp"), today.getInteger("min_temp")), true);
			embed.addBlankField(true);
			embed.addField("Wind", String.format("Direction: %d° (%s)\nSpeed: %d mph", windDegrees, Direction.getDirection(windDegrees).getName(), now.getInteger("wspd")), true);
			embed.addField("Humidity", now.getInteger("rh") + "%", true);
			embed.addBlankField(true);
			embed.addField("Precipitation", now.getInteger("pop") + "%", true);
			embed.setThumbnail(now.getString("icon"));
			embed.setFooter(now.getString("dow") + " " + OffsetDateTime.parse(now.getString("fcst_valid_local"), this.parseFormatter).format(this.formatter));

			event.reply(embed.build()).queue();
		});
	}

}
