package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.option.Option;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.http.HttpCallback;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import okhttp3.Request;
import org.bson.Document;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

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
				.min(Comparator.comparing(a -> Math.abs(a.getDegrees() - degrees)))
				.orElse(null);
		}

	}

	public WeatherCommand() {
		super("weather", 35);

		super.setDescription("Find out the weather in a specific city");
		super.setCooldownDuration(3);
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setExamples("weather London", "weather Stockholm", "weather London --country=uk");
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="city", endless=true) String city, @Option(value="country", description="Country code of the country you want the weather to be from") String countryCode) {
		Request request = new Request.Builder()
			.url(String.format("https://api.openweathermap.org/data/2.5/weather?q=%s&appid=%s&units=metric", city + (countryCode == null ? "" : "," + countryCode), this.config.getOpenWeather()))
			.build();

		event.getClient().newCall(request).enqueue((HttpCallback) response -> {
			Document json = Document.parse(response.body().string());

			Object code = json.get("cod");
			if ("404".equals(code)) {
				event.replyFailure("I could not find that city").queue();
				return;
			}

			Document weather = json.getList("weather", Document.class).get(0);
			Document info = json.get("main", Document.class);

			Document wind = json.get("wind", Document.class);
			int degrees = wind.getInteger("deg");

			EmbedBuilder embed = new EmbedBuilder();
			embed.setTitle(String.format("%s (%s)", json.getString("name"), json.getEmbedded(List.of("sys", "country"), String.class)));
			embed.setThumbnail("http://openweathermap.org/img/w/" + weather.getString("icon") + ".png");
			embed.addField("Temperature", String.format("Minimum: %.2f°C\nCurrent: %.2f°C\nMaximum: %.2f°C\nFeels Like: %.2f°C", info.get("temp_min", Number.class).doubleValue(), info.get("temp", Number.class).doubleValue(), info.get("temp_max", Number.class).doubleValue(), info.get("feels_like", Number.class).doubleValue()), false);
			embed.addField("Humidity", info.getInteger("humidity") + "%", false);
			embed.addField("Wind", String.format("Speed: %.1fm/s\nDirection: %d° (%s)", wind.get("speed", Number.class).doubleValue(), degrees, Direction.getDirection(degrees).getName()), false);

			event.reply(embed.build()).queue();
		});
	}

}
