package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.utility.NumberUtility;
import com.sx4.bot.utility.StringUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import okhttp3.Request;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.XML;
import org.jsoup.Jsoup;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

public class SteamProfileCommand extends Sx4Command {

	private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd LLLL yyyy");

	public SteamProfileCommand() {
		super("steam profile", 212);

		super.setDescription("Look up a profile on steam");
		super.setAliases("steam");
		super.setExamples("steam profile dog", "steam profile https://steamcommunity.com/id/dog");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	private final Pattern url = Pattern.compile("https?://steamcommunity\\.com/(?:profiles|id)/[\\s\\d]+/?");

	public void onCommand(Sx4CommandEvent event, @Argument(value="query", endless=true) String query) {
		String url;
		if (NumberUtility.isNumber(query)) {
			url = "https://steamcommunity.com/profiles/" + query;
		} else if (this.url.matcher(query).matches()) {
			url = query;
		} else {
			url = "https://steamcommunity.com/id/" + URLEncoder.encode(query, StandardCharsets.UTF_8);
		}

		Request request = new Request.Builder()
			.url(url + "?xml=1")
			.build();

		event.getClient().newCall(request).enqueue((HttpCallback) response -> {
			JSONObject json = XML.toJSONObject(response.body().string());
			if (json.has("response")) {
				event.replyFailure("I could not find that steam user").queue();
				return;
			}

			JSONObject profile = json.getJSONObject("profile");
			if (profile.getInt("visibilityState") == 1) {
				event.replyFailure("That profile is private").queue();
				return;
			}

			JSONObject mostPlayedGames = profile.optJSONObject("mostPlayedGames");
			JSONArray gamesArray = mostPlayedGames == null ? new JSONArray() : mostPlayedGames.getJSONArray("mostPlayedGame");

			double hours = 0D;
			StringBuilder gamesString = new StringBuilder();
			for (int i = 0; i < gamesArray.length(); i++) {
				JSONObject game = gamesArray.getJSONObject(i);

				hours += game.getDouble("hoursPlayed");

				gamesString.append(String.format("[%s](%s) - **%.1f** hours\n", game.getString("gameName"), game.getString("gameLink"), game.getDouble("hoursPlayed")));
			}

			String stateMessage = profile.getString("stateMessage");
			String location = profile.getString("location");
			String realName = profile.getString("realname");

			EmbedBuilder embed = new EmbedBuilder();
			embed.setAuthor(profile.getString("steamID"), url, profile.getString("avatarFull"));
			embed.setDescription(Jsoup.parse(profile.getString("summary")).text());
			embed.setFooter("ID: " + profile.getLong("steamID64"));
			embed.setThumbnail(profile.getString("avatarFull"));
			embed.addField("Real Name", realName.isBlank() ? "None Given" : realName, true);
			embed.addField("Created At", LocalDate.parse(profile.getString("memberSince"), DateTimeFormatter.ofPattern("LLLL d, yyyy")).format(this.formatter), true);
			embed.addField("Status", StringUtility.title(profile.getString("onlineState")), true);
			embed.addField("State Message", Jsoup.parse(stateMessage).text(), true);

			embed.addField("Vac Bans", String.valueOf(profile.getInt("vacBanned")), true);

			if (!location.isBlank()) {
				embed.addField("Location", location, true);
			}

			if (hours != 0) {
				gamesString.append(String.format("\nTotal - **%.1f** hours", hours));
				embed.addField("Games Played (2 Weeks)", gamesString.toString(), false);
			}

			event.reply(embed.build()).queue();
		});
	}

}
