package com.sx4.bot.managers;

import club.minnced.discord.webhook.exception.HttpException;
import club.minnced.discord.webhook.send.WebhookMessage;
import com.mongodb.client.model.*;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.info.EpicFreeGame;
import com.sx4.bot.entities.info.FreeGame;
import com.sx4.bot.entities.info.FreeGameType;
import com.sx4.bot.entities.info.SteamFreeGame;
import com.sx4.bot.entities.webhook.ReadonlyMessage;
import com.sx4.bot.entities.webhook.WebhookClient;
import com.sx4.bot.formatter.Formatter;
import com.sx4.bot.formatter.JsonFormatter;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.FreeGameUtility;
import com.sx4.bot.utility.FutureUtility;
import com.sx4.bot.utility.MessageUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.TextChannel;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class FreeGameManager implements WebhookManager {

	public static final Document DEFAULT_MESSAGE = new Document("embed",
		new Document("title", "{game.title}")
			.append("url", "{game.url}")
			.append("description", "{game.description}")
			.append("image", new Document("url", "{game.image}"))
			.append("fields", List.of(
				new Document("name", "Price").append("value", "{game.original_price.equals(0).then(Free).else(~~£{game.original_price.format(,##0.00)}~~ Free)}").append("inline", true),
				new Document("name", "Publisher").append("value", "{game.publisher}").append("inline", true),
				new Document("name", "Promotion End").append("value", "<t:{game.promotion_end.epoch}:f>").append("inline", false)
			))
	);

	private final Sx4 bot;

	private final Map<Object, List<FreeGame<?>>> announcedGames;
	private final List<EpicFreeGame> queuedGames;

	private final Map<Long, WebhookClient> webhooks;

	private final ScheduledExecutorService epicExecutor = Executors.newSingleThreadScheduledExecutor();
	private final ScheduledExecutorService steamExecutor = Executors.newSingleThreadScheduledExecutor();

	private final ScheduledExecutorService webhookExecutor = Executors.newSingleThreadScheduledExecutor();
	private final OkHttpClient client = new OkHttpClient();

	public FreeGameManager(Sx4 bot) {
		this.bot = bot;
		this.webhooks = new HashMap<>();
		this.queuedGames = new ArrayList<>();
		this.announcedGames = new HashMap<>();
	}

	public boolean isAnnounced(FreeGame<?> game) {
		List<FreeGame<?>> announcedGames = this.announcedGames.get(game.getId());
		if (announcedGames == null) {
			return false;
		}

		return announcedGames.stream().anyMatch(g -> g.getPromotionEnd().equals(game.getPromotionEnd()));
	}

	public void addAnnouncedGame(FreeGame<?> game) {
		this.announcedGames.compute(game.getId(), (key, value) -> {
			if (value == null) {
				List<FreeGame<?>> games = new ArrayList<>();
				games.add(game);
				return games;
			} else {
				value.add(game);
				return value;
			}
		});
	}

	public WebhookClient getWebhook(long id) {
		return this.webhooks.get(id);
	}

	public WebhookClient removeWebhook(long id) {
		return this.webhooks.remove(id);
	}

	public void putWebhook(long id, WebhookClient webhook) {
		this.webhooks.put(id, webhook);
	}

	private void disableFreeGameChannel(TextChannel channel) {
		Bson update = Updates.combine(
			Updates.unset("webhook.id"),
			Updates.unset("webhook.token"),
			Updates.set("enabled", false)
		);

		this.bot.getMongo().updateFreeGameChannel(Filters.eq("channelId", channel.getIdLong()), update, new UpdateOptions()).whenComplete(MongoDatabase.exceptionally());
	}

	private CompletableFuture<List<ReadonlyMessage>> createWebhook(TextChannel channel, List<WebhookMessage> messages) {
		if (!channel.getGuild().getSelfMember().hasPermission(channel, Permission.MANAGE_WEBHOOKS)) {
			this.disableFreeGameChannel(channel);
			return CompletableFuture.completedFuture(Collections.emptyList());
		}

		return channel.createWebhook("Sx4 - Free Games").submit().thenCompose(webhook -> {
			WebhookClient webhookClient = new WebhookClient(webhook.getIdLong(), webhook.getToken(), this.webhookExecutor, this.client);

			this.webhooks.put(channel.getIdLong(), webhookClient);

			Bson update = Updates.combine(
				Updates.set("webhook.id", webhook.getIdLong()),
				Updates.set("webhook.token", webhook.getToken())
			);

			Document webhookData = new Document("webhook", new Document("id", webhook.getIdLong()).append("token", webhook.getToken()));

			return this.bot.getMongo().updateFreeGameChannel(Filters.eq("channelId", channel.getIdLong()), update, new UpdateOptions())
				.thenCompose(result -> this.sendFreeGameNotificationMessages(channel, webhookData, messages));
		}).exceptionallyCompose(exception -> {
			Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
			if (cause instanceof HttpException && ((HttpException) cause).getCode() == 404) {
				this.webhooks.remove(channel.getIdLong());

				return this.createWebhook(channel, messages);
			}

			return CompletableFuture.completedFuture(Collections.emptyList());
		});
	}

	public CompletableFuture<List<ReadonlyMessage>> sendFreeGameNotificationMessages(TextChannel channel, Document webhookData, List<WebhookMessage> messages) {
		WebhookClient webhook;
		if (this.webhooks.containsKey(channel.getIdLong())) {
			webhook = this.webhooks.get(channel.getIdLong());
		} else if (!webhookData.containsKey("id")) {
			return this.createWebhook(channel, messages);
		} else {
			webhook = new WebhookClient(webhookData.getLong("id"), webhookData.getString("token"), this.webhookExecutor, this.client);

			this.webhooks.put(channel.getIdLong(), webhook);
		}

		List<ReadonlyMessage> completedMessages = new ArrayList<>();

		CompletableFuture<Boolean> future = CompletableFuture.completedFuture(null);
		for (WebhookMessage message : messages) {
			future = future.thenCompose($ -> webhook.send(message))
				.thenApply(webhookMessage -> completedMessages.add(new ReadonlyMessage(webhookMessage, webhook.getId(), webhook.getToken())));
		}

		return future.thenApply($ -> completedMessages).exceptionallyCompose(exception -> {
			Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
			if (cause instanceof HttpException && ((HttpException) cause).getCode() == 404) {
				this.webhooks.remove(channel.getIdLong());

				return this.createWebhook(channel, messages);
			}

			return CompletableFuture.completedFuture(Collections.emptyList());
		});
	}

	public CompletableFuture<List<ReadonlyMessage>> sendFreeGameNotifications(List<? extends FreeGame<?>> games) {
		games.forEach(this::addAnnouncedGame);

		List<Document> gameData = games.stream().map(FreeGame::toData).collect(Collectors.toList());
		if (!gameData.isEmpty()) {
			this.bot.getMongo().insertManyAnnouncedGames(gameData).whenComplete(MongoDatabase.exceptionally());
		}

		List<Bson> guildPipeline = List.of(
			Aggregates.match(Operators.expr(Operators.eq("$_id", "$$guildId"))),
			Aggregates.project(Projections.computed("premium", Operators.lt(Operators.nowEpochSecond(), Operators.ifNull("$premium.endAt", 0L))))
		);

		List<Bson> pipeline = List.of(
			Aggregates.lookup("guilds", List.of(new Variable<>("guildId", "$guildId")), guildPipeline, "premium"),
			Aggregates.addFields(new Field<>("premium", Operators.cond(Operators.isEmpty("$premium"), false, Operators.get(Operators.arrayElemAt("$premium", 0), "premium"))))
		);

		return this.bot.getMongo().aggregateFreeGameChannels(pipeline).thenComposeAsync(documents -> {
			List<WriteModel<Document>> bulkData = new ArrayList<>();
			List<CompletableFuture<List<ReadonlyMessage>>> futures = new ArrayList<>();
			for (Document data : documents) {
				if (!data.getBoolean("enabled", true)) {
					continue;
				}

				TextChannel channel = this.bot.getShardManager().getTextChannelById(data.getLong("channelId"));
				if (channel == null) {
					continue;
				}

				String avatar = channel.getJDA().getSelfUser().getEffectiveAvatarUrl();
				boolean premium = data.getBoolean("premium");
				Document webhookData = data.get("webhook", MongoDatabase.EMPTY_DOCUMENT);

				long platforms = data.get("platforms", FreeGameType.ALL);

				List<WebhookMessage> messages = new ArrayList<>();
				for (FreeGame<?> game : games) {
					long raw = game.getType().getRaw();
					if ((platforms & raw) != raw) {
						continue;
					}

					Formatter<Document> formatter = new JsonFormatter(data.get("message", FreeGameManager.DEFAULT_MESSAGE))
						.addVariable("game", game);

					WebhookMessage message;
					try {
						message = MessageUtility.fromJson(formatter.parse())
							.setAvatarUrl(premium ? webhookData.get("avatar", avatar) : avatar)
							.setUsername(premium ? webhookData.get("name", "Sx4 - Free Games") : "Sx4 - Free Games")
							.build();
					} catch (IllegalArgumentException e) {
						bulkData.add(new UpdateOneModel<>(Filters.eq("_id", data.getObjectId("_id")), Updates.unset("message")));
						continue;
					}

					messages.add(message);
				}

				futures.add(this.sendFreeGameNotificationMessages(channel, webhookData, messages));
			}

			if (!bulkData.isEmpty()) {
				this.bot.getMongo().bulkWriteFreeGameChannels(bulkData).whenComplete(MongoDatabase.exceptionally());
			}

			return FutureUtility.allOf(futures).thenApply(list -> list.stream().flatMap(List::stream).collect(Collectors.toList()));
		});
	}

	public void scheduleEpicFreeGameNotifications(long duration, List<EpicFreeGame> games) {
		this.epicExecutor.schedule(() -> {
			this.sendFreeGameNotifications(games).whenComplete(MongoDatabase.exceptionally());

			Set<String> ids = games.stream()
				.filter(EpicFreeGame::isMysteryGame)
				.map(EpicFreeGame::getId)
				.collect(Collectors.toSet());

			this.ensureEpicFreeGames(ids);
			this.queuedGames.removeAll(games);
		}, duration, TimeUnit.SECONDS);
	}

	public void ensureEpicFreeGames(Set<String> ids) {
		FreeGameUtility.retrieveFreeGames(this.bot.getHttpClient(), games -> {
			OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

			List<EpicFreeGame> mysteryGames = games.stream()
				.filter(Predicate.not(EpicFreeGame::isMysteryGame))
				.filter(game -> ids.contains(game.getId()))
				.collect(Collectors.toList());

			if (mysteryGames.size() != ids.size()) {
				this.epicExecutor.schedule(() -> this.ensureEpicFreeGames(ids), 1, TimeUnit.MINUTES);
				return;
			}

			// Add unaccounted for games in the case Epic Games releases more free games than shown
			List<EpicFreeGame> currentGames = games.stream()
				.filter(Predicate.not(this::isAnnounced))
				.filter(game -> !game.getPromotionStart().isAfter(now) && game.getPromotionEnd().isAfter(now))
				.collect(Collectors.toList());

			mysteryGames.addAll(currentGames);


			if (!mysteryGames.isEmpty()) {
				this.sendFreeGameNotifications(mysteryGames).whenComplete(MongoDatabase.exceptionally());
			}

			List<EpicFreeGame> upcomingGames = games.stream()
				.filter(game -> game.getPromotionStart().isAfter(now))
				.sorted(Comparator.comparing(game -> Duration.between(now, game.getPromotionStart())))
				.collect(Collectors.toList());

			EpicFreeGame newestGame = upcomingGames.get(0);
			OffsetDateTime promotionStart = newestGame.getPromotionStart();
			if (!promotionStart.isAfter(now)) {
				// re-request games as they have not been refreshed on the API
				this.epicExecutor.schedule((Runnable) this::ensureEpicFreeGames, 10, TimeUnit.MINUTES);
				return;
			}

			this.queuedGames.add(newestGame);

			for (EpicFreeGame game : games.subList(1, games.size())) {
				if (game.getPromotionStart().equals(promotionStart)) {
					this.queuedGames.add(game);
				} else {
					break;
				}
			}

			this.scheduleEpicFreeGameNotifications(Duration.between(now, promotionStart).toSeconds() + 5, this.queuedGames);
		});
	}

	public void ensureEpicFreeGames() {
		this.ensureEpicFreeGames(Collections.emptySet());
	}

	public void ensureSteamFreeGames() {
		this.steamExecutor.scheduleAtFixedRate(() -> {
			Request resultRequest = new Request.Builder()
				.url("https://store.steampowered.com/search/?maxprice=free&specials=1&cc=gb")
				.addHeader("Accept-Language", "en")
				.build();

			this.bot.getHttpClient().newCall(resultRequest).enqueue((HttpCallback) resultResponse -> {
				org.jsoup.nodes.Document resultsDocument = Jsoup.parse(resultResponse.body().string());

				Element results = resultsDocument.getElementById("search_resultsRows");
				if (results == null) {
					return;
				}

				List<CompletableFuture<SteamFreeGame>> futures = new ArrayList<>();
				for (Element result : results.children()) {
					Element discount = result.getElementsByClass("col search_discount responsive_secondrow").first();
					// Just in case steam search is inaccurate
					if (!discount.text().equals("-100%")) {
						continue;
					}

					int id = Integer.parseInt(result.attr("data-ds-appid"));

					Request gameRequest = new Request.Builder()
						.url("https://store.steampowered.com/app/" + id + "?cc=gb")
						.addHeader("Accept-Language", "en")
						.build();

					CompletableFuture<SteamFreeGame> future = new CompletableFuture<>();
					this.bot.getHttpClient().newCall(gameRequest).enqueue((HttpCallback) gameResponse -> {
						org.jsoup.nodes.Document document = Jsoup.parse(gameResponse.body().string());
						Element content = document.getElementsByClass("page_content_ctn").first();

						SteamFreeGame game = SteamFreeGame.fromData(id, content);
						if (this.isAnnounced(game)) {
							future.complete(null);
							return;
						}

						future.complete(game);
					});

					futures.add(future);
				}

				FutureUtility.allOf(futures, Objects::nonNull).whenComplete((games, exception) -> {
					if (ExceptionUtility.sendErrorMessage(exception) || games.isEmpty()) {
						return;
					}

					this.sendFreeGameNotifications(games).whenComplete(MongoDatabase.exceptionally());
				});
			});
		}, 0, 30, TimeUnit.MINUTES);
	}

	public void ensureAnnouncedGames() {
		this.bot.getMongo().getAnnouncedGames().find().forEach(data -> this.addAnnouncedGame(FreeGameUtility.getFreeGame(data)));
	}

}

