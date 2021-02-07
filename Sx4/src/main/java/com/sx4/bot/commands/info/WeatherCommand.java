package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.http.HttpCallback;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import okhttp3.Request;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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

	public WeatherCommand() {
		super("weather", 35);

		super.setDescription("Find out the weather in a specific area");
		super.setCooldownDuration(3);
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setExamples("weather London", "weather Stockholm", "weather England");
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="query", endless=true) String query) {
		Request request = new Request.Builder()
			.url("https://google.com/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "%20weather")
			.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.141 Safari/537.3")
			.build();

		event.getClient().newCall(request).enqueue((HttpCallback) response -> {
			Document document = Jsoup.parse(response.body().string());

			Element element = document.getElementById("wob_wc");
			if (element == null) {
				event.replyFailure("I could not find that city").queue();
				return;
			}

			Element urlElement = document.getElementsByTag("td").first();
			String url = urlElement.getElementsByTag("a").first().attr("href");

			Element info = element.getElementById("wob_d");
			Element weather = info.getElementById("wob_tci");
			if (weather == null) {
				event.replyFailure("I failed to get weather data for that, try again and it should work").queue();
				return;
			}

			Element hourly = element.getElementById("wob_gsp");

			Element wind = hourly.getElementById("wob_wg");
			Element windDirection = wind.getElementsByClass("wob_hw").first();
			Element image = windDirection.getElementsByTag("img").first();
			String windStyle = image.attr("style");

			int degreesIndex = windStyle.indexOf("transform:rotate(") + 17;
			int degrees = Integer.parseInt(windStyle.substring(degreesIndex, windStyle.indexOf("deg);", degreesIndex + 1))) % 360;

			Element today = element.getElementsByClass("wob_df wob_ds").first();
			Elements temperatures = today.getElementsByClass("wob_t");

			String iconUrl = weather.attr("src");
			String icon = iconUrl.substring(iconUrl.lastIndexOf('/') + 1);

			EmbedBuilder embed = new EmbedBuilder();
			embed.setAuthor(element.getElementById("wob_loc").text(), url, null);
			embed.setTitle(weather.attr("alt"));
			embed.addField("Temperature", String.format("Maximum: %s°C\nCurrent: %s°C\nMinimum: %s°C", temperatures.get(0).text(), info.getElementById("wob_tm").text(), temperatures.get(2).text()), true);
			embed.addBlankField(true);
			embed.addField("Wind", String.format("Direction: %d° (%s)\nSpeed: %s", degrees, Direction.getDirection(degrees).getName(), info.getElementById("wob_ws").text()), true);
			embed.addField("Humidity", info.getElementById("wob_hm").text(), true);
			embed.addBlankField(true);
			embed.addField("Precipitation", info.getElementById("wob_pp").text(), true);
			embed.setThumbnail("https://ssl.gstatic.com/onebox/weather/256/" + icon);
			embed.setFooter(element.getElementById("wob_dts").text());

			event.reply(embed.build()).queue();
		});
	}

}
