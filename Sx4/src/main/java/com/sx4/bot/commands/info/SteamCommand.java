package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.option.Option;
import com.mongodb.client.model.Projections;
import com.sx4.bot.annotations.command.BotPermissions;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.cache.SteamGameCache;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.HmacUtility;
import com.sx4.bot.utility.NumberUtility;
import com.sx4.bot.utility.StringUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.EmbedType;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import okhttp3.Request;
import org.bson.Document;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.XML;
import org.jsoup.Jsoup;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.jockie.bot.core.command.Command.Cooldown;

public class SteamCommand extends Sx4Command {

	private final Pattern gamePattern = Pattern.compile("https?://store\\.steampowered\\.com/app/([0-9]+)/?[\\S]*", Pattern.CASE_INSENSITIVE);
	private final Pattern profilePattern = Pattern.compile("https?://steamcommunity\\.com/(?:profiles|id)/[\\S]+(/?)");

	private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd LLLL yyyy");

	public SteamCommand() {
		super("steam", 278);

		super.setDescription("View information about specific attributes on steam");
		super.setExamples("steam profile", "steam game", "steam compare");
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public String getProfileUrl(String query) {
		Matcher profileMatcher;

		String url;
		if (NumberUtility.isNumber(query)) {
			url = "https://steamcommunity.com/profiles/" + query + "/";
		} else if ((profileMatcher = this.profilePattern.matcher(query)).matches()) {
			url = query + (profileMatcher.group(1).isEmpty() ? "/" : "");
		} else {
			url = "https://steamcommunity.com/id/" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "/";
		}

		return url;
	}

	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}

	@Command(value="game", description="View information about a game on steam")
	@CommandId(34)
	@Examples({"steam game Grand Theft Auto", "steam game 1293830", "steam game https://store.steampowered.com/app/1293830/Forza_Horizon_4/"})
	@Cooldown(5)
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void game(Sx4CommandEvent event, @Argument(value="query", endless=true, nullDefault=true) String query, @Option(value="random", description="Gets a random game") boolean random) {
		if (query == null && !random) {
			event.replyHelp().queue();
			return;
		}

		SteamGameCache cache = event.getBot().getSteamGameCache();
		if (cache.getGames().isEmpty()) {
			event.replyFailure("The steam cache is currently empty, try again").queue();
			return;
		}

		Matcher urlMatcher;

		List<Document> games;
		if (query == null) {
			List<Document> cacheGames = cache.getGames();
			games = List.of(cacheGames.get(event.getRandom().nextInt(cacheGames.size())));
		} else if (NumberUtility.isNumberUnsigned(query)) {
			games = List.of(new Document("appid", Integer.parseInt(query)));
		} else if ((urlMatcher = this.gamePattern.matcher(query)).matches()) {
			games = List.of(new Document("appid", Integer.parseInt(urlMatcher.group(1))));
		} else {
			games = cache.getGames(query);
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

				List<MessageEmbed> embeds = new ArrayList<>();

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
				embed.setTitle(gameInfo.getString("name"), "https://store.steampowered.com/app/" + appId);
				embed.setAuthor("Steam", "https://steamcommunity.com", "https://upload.wikimedia.org/wikipedia/commons/thumb/8/83/Steam_icon_logo.svg/2000px-Steam_icon_logo.svg.png");
				embed.setImage(gameInfo.getString("header_image"));
				embed.addField("Price", price, true);
				embed.setFooter("Developed by " + (gameInfo.containsKey("developers") ? String.join(", ", gameInfo.getList("developers", String.class)) : "Unknown"), null);

				Document releaseDate = gameInfo.get("release_date", Document.class);
				String date = releaseDate.getString("date");

				embed.addField("Release Date", String.format("%s%s", date.isBlank() ? "Unknown" : date, releaseDate.getBoolean("coming_soon") ? " (Coming Soon)" : ""), true);

				Object age = gameInfo.get("required_age");
				embed.addField("Required Age", age instanceof Integer ? ((int) age) == 0 ? "No Age Restriction" : String.valueOf((int) age) : (String) age, true);
				embed.addField("Recommendations", String.format("%,d", gameInfo.getEmbedded(List.of("recommendations", "total"), 0)), true);
				embed.addField("Supported Languages", gameInfo.containsKey("supported_languages") ? Jsoup.parse(gameInfo.getString("supported_languages")).text() : "Unknown", true);

				List<Document> genres = gameInfo.getList("genres", Document.class);
				embed.addField("Genres", genres == null ? "None" : genres.stream().map(genre -> genre.getString("description")).collect(Collectors.joining("\n")), true);

				embeds.add(embed.build());

				gameInfo.getList("screenshots", Document.class).stream().map(d -> d.getString("path_thumbnail")).limit(3).forEach(thumbnail -> {
					embeds.add(new MessageEmbed("https://store.steampowered.com/app/" + appId, null, null, EmbedType.RICH, null, Role.DEFAULT_COLOR_RAW, null, null, null, null, null, new MessageEmbed.ImageInfo(thumbnail, null, 0, 0), List.of()));
				});

				event.getChannel().sendMessageEmbeds(embeds).queue();
			});
		});

		paged.onTimeout(() -> event.reply("Response timed out :stopwatch:").queue());

		paged.execute(event);
	}

	@Command(value="profile", description="Look up information about a steam profile")
	@CommandId(212)
	@Examples({"steam profile dog", "steam profile https://steamcommunity.com/id/dog"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void game(Sx4CommandEvent event, @Argument(value="query", endless=true, nullDefault=true) String query) {
		List<Document> profiles;
		if (query == null) {
			List<Document> connections = event.getMongo().getUserById(event.getAuthor().getIdLong(), Projections.include("connections.steam")).getEmbedded(List.of("connections", "steam"), Collections.emptyList());
			if (connections.isEmpty()) {
				event.replyFailure("You do not have a steam account linked, use `steam connect` to link an account or provide an argument to search").queue();
				return;
			}

			profiles = connections.stream()
				.map(data -> data.append("url", "https://steamcommunity.com/profiles/" + data.getLong("id")))
				.collect(Collectors.toList());
		} else {
			profiles = List.of(new Document("url", this.getProfileUrl(query)));
		}

		PagedResult<Document> paged = new PagedResult<>(event.getBot(), profiles)
			.setAutoSelect(true)
			.setAuthor("Steam Profiles", null, "https://upload.wikimedia.org/wikipedia/commons/thumb/8/83/Steam_icon_logo.svg/2000px-Steam_icon_logo.svg.png")
			.setDisplayFunction(data -> "[" + data.getString("name") + "](" + data.getString("url") + ")");

		paged.onSelect(select -> {
			String url = select.getSelected().getString("url");

			Request request = new Request.Builder()
				.url(url + "?xml=1")
				.build();

			event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
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
		});

		paged.execute(event);
	}

	@Command(value="compare", description="Compare what games 2 steam accounts have in common")
	@CommandId(279)
	@Examples({"steam compare dog cat", "steam compare https://steamcommunity.com/id/dog https://steamcommunity.com/id/cat"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void compare(Sx4CommandEvent event, @Argument(value="first profile") String firstQuery, @Argument(value="second profile", endless=true) String secondQuery) {
		String firstUrl = this.getProfileUrl(firstQuery), secondUrl = this.getProfileUrl(secondQuery);

		Request firstRequest = new Request.Builder()
			.url(firstUrl + "games/?tab=all&xml=1")
			.build();

		Request secondRequest = new Request.Builder()
			.url(secondUrl + "games/?tab=all&xml=1")
			.build();

		event.getHttpClient().newCall(firstRequest).enqueue((HttpCallback) firstResponse -> {
			JSONObject firstData = XML.toJSONObject(firstResponse.body().string()).getJSONObject("gamesList");
			if (firstData.has("error")) {
				event.replyFailure("The steam profile <https://steamcommunity.com/profiles/" + firstData.getLong("steamID64") + "> is private").queue();
				return;
			}

			event.getHttpClient().newCall(secondRequest).enqueue((HttpCallback) secondResponse -> {
				JSONObject secondData = XML.toJSONObject(secondResponse.body().string()).getJSONObject("gamesList");
				if (secondData.has("error")) {
					event.replyFailure("The steam profile <https://steamcommunity.com/profiles/" + secondData.getLong("steamID64") + "> is private").queue();
					return;
				}

				JSONArray firstGames = firstData.getJSONObject("games").getJSONArray("game"), secondGames = secondData.getJSONObject("games").getJSONArray("game");

				Map<Integer, String> commonGames = new HashMap<>();
				for (int x = 0; x < firstGames.length(); x++) {
					for (int y = 0; y < secondGames.length(); y++) {
						JSONObject firstGame = firstGames.getJSONObject(x), secondGame = secondGames.getJSONObject(y);
						if (firstGame.getInt("appID") == secondGame.getInt("appID")) {
							commonGames.put(firstGame.getInt("appID"), firstGame.getString("name"));
						}
					}
				}

				PagedResult<Map.Entry<Integer, String>> paged = new PagedResult<>(event.getBot(), new ArrayList<>(commonGames.entrySet()))
					.setAuthor("Games In Common (" + commonGames.size() + ")", null, "https://upload.wikimedia.org/wikipedia/commons/thumb/8/83/Steam_icon_logo.svg/2000px-Steam_icon_logo.svg.png")
					.setPerPage(15)
					.setIncreasedIndex(true)
					.setDisplayFunction(d -> "[" + d.getValue() + "](https://store.steampowered.com/app/" + d.getKey() + ")");

				paged.execute(event);
			});
		});
	}

	@Command(value="connect", description="Connect your steam account with Sx4")
	@CommandId(488)
	@Examples({"steam connect"})
	public void connect(Sx4CommandEvent event) {
		String id = event.getAuthor().getId();
		long timestamp = Instant.now().getEpochSecond();

		String signature;
		try {
			signature = HmacUtility.getSignatureHex(event.getConfig().getSteam(), id + timestamp, HmacUtility.HMAC_MD5);
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			event.replyFailure("Something went wrong there, try again").queue();
			return;
		}

		String redirectUrl = URLEncoder.encode(event.getConfig().getBaseUrl() + "/redirect/steam?user_id=" + id + "&timestamp=" + timestamp + "&signature=" + signature, StandardCharsets.UTF_8);

		MessageEmbed embed = new EmbedBuilder()
			.setAuthor("Steam Authorization")
			.setDescription("The link below will allow you to link your steam account on Sx4\n**:warning: Do not give this link to anyone :warning:**\n\n[Authorize](https://sx4.dev/connect/steam?redirect_url=" + redirectUrl + ")")
			.setColor(event.getConfig().getOrange())
			.setFooter("The authorization link will expire in 5 minutes")
			.build();

		event.getAuthor().openPrivateChannel()
			.flatMap(channel -> channel.sendMessageEmbeds(embed))
			.flatMap($ -> event.replySuccess("I sent you a message containing your authorization link"))
			.onErrorFlatMap($ -> event.replyFailure("I failed to send you your authorization link, make sure to have your dms open"))
			.queue();
	}

}
