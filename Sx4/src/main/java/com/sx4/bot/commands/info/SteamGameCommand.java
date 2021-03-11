package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.option.Option;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.NumberUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import okhttp3.Request;
import org.bson.Document;
import org.jsoup.Jsoup;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SteamGameCommand extends Sx4Command {

	private final Pattern urlPattern = Pattern.compile("https?://store.steampowered.com/app/([0-9]+)[\\S]*", Pattern.CASE_INSENSITIVE);

	public SteamGameCommand() {
		super("steam game", 34);

		super.setDescription("Look up a game on steam");
		super.setAliases("steam search", "steamsearch", "steamgame");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCooldownDuration(5);
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="game", endless=true, nullDefault=true) String query, @Option(value="random", description="Gets a random game") boolean random) {
		if (query == null && !random) {
			event.replyHelp().queue();
			return;
		}

		Matcher urlMatcher;

		List<Document> games;
		if (query == null) {
			List<Document> cache = event.getBot().getSteamGameCache().getGames();
			games = List.of(cache.get(event.getRandom().nextInt(cache.size())));
		} else if (NumberUtility.isNumberUnsigned(query)) {
			games = List.of(new Document("appid", Integer.parseInt(query)));
		} else if ((urlMatcher = this.urlPattern.matcher(query)).matches()) {
			games = List.of(new Document("appid", Integer.parseInt(urlMatcher.group(1))));
		} else {
			games = event.getBot().getSteamGameCache().getGames(query);
			if (games.isEmpty()) {
				event.replyFailure("I could not find any games with that query").queue();
				return;
			}
		}

		PagedResult<Document> paged = new PagedResult<>(event.getBot(), games)
			.setAuthor("Steam Search", null, "https://upload.wikimedia.org/wikipedia/commons/thumb/8/83/Steam_icon_logo.svg/2000px-Steam_icon_logo.svg.png")
			.setIncreasedIndex(true)
			.setAutoSelect(true)
			.setTimeout(60)
			.setDisplayFunction(game -> game.getString("name"));

		paged.onSelect(select -> {
			Document game = select.getSelected();

			int appId = game.getInteger("appid");
			Request request = new Request.Builder()
				.url("https://store.steampowered.com/api/appdetails?cc=gb&appids=" + appId)
				.build();

			event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
				Document json = Document.parse(response.body().string()).get(String.valueOf(appId), Document.class);

				if (!json.getBoolean("success")) {
					event.replyFailure("Steam failed to get data for that game").queue();
					return;
				}

				Document gameInfo = json.get("data", Document.class);

				String description = Jsoup.parse(gameInfo.getString("short_description")).text();

				String price;
				if (gameInfo.containsKey("price_overview")) {
					Document priceOverview = gameInfo.get("price_overview", Document.class);
					double initialPrice = priceOverview.getInteger("initial") / 100D, finalPrice = priceOverview.getInteger("final") / 100D;

					price = initialPrice == finalPrice ? String.format("£%,.2f", finalPrice) : String.format("~~£%,.2f~~ £%,.2f (-%d%%)", initialPrice, finalPrice, priceOverview.getInteger("discount_percent"));
				} else {
					price = gameInfo.getBoolean("is_free") ? "Free" : "Unknown";
				}

				EmbedBuilder embed = new EmbedBuilder();
				embed.setDescription(description);
				embed.setAuthor(gameInfo.getString("name"), "https://store.steampowered.com/app/" + appId, "https://upload.wikimedia.org/wikipedia/commons/thumb/8/83/Steam_icon_logo.svg/2000px-Steam_icon_logo.svg.png");
				embed.setImage(gameInfo.getString("header_image"));
				embed.addField("Price", price, true);
				embed.setFooter("Developed by " + (gameInfo.containsKey("developers") ? String.join(", ", gameInfo.getList("developers", String.class)) : "Unknown"), null);

				Document releaseDate = gameInfo.get("release_date", Document.class);
				String date = releaseDate.getString("date");

				embed.addField("Release Date", String.format("%s%s", date.isBlank() ? "Unknown" : date, releaseDate.getBoolean("coming_soon") ? " (Coming Soon)" : ""), true);

				int age = gameInfo.getInteger("required_age");
				embed.addField("Required Age", age == 0 ? "No Age Restriction" : String.valueOf(age), true);
				embed.addField("Recommendations", String.format("%,d", gameInfo.getEmbedded(List.of("recommendations", "total"), 0)), true);
				embed.addField("Supported Languages", gameInfo.containsKey("supported_languages") ? Jsoup.parse(gameInfo.getString("supported_languages")).text() : "Unknown", true);

				List<Document> genres = gameInfo.getList("genres", Document.class);
				embed.addField("Genres", genres == null ? "None" : genres.stream().map(genre -> genre.getString("description")).collect(Collectors.joining("\n")), true);

				event.reply(embed.build()).queue();
			});
		});

		paged.onTimeout(() -> event.reply("Response timed out :stopwatch:").queue());

		paged.execute(event);
	}

}
