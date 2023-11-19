package com.sx4.bot.managers;

import com.mongodb.client.model.*;
import com.mongodb.client.result.UpdateResult;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.info.game.*;
import com.sx4.bot.entities.webhook.SentWebhookMessage;
import com.sx4.bot.entities.webhook.WebhookChannel;
import com.sx4.bot.formatter.output.Formatter;
import com.sx4.bot.formatter.output.JsonFormatter;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.utility.FreeGameUtility;
import com.sx4.bot.utility.FutureUtility;
import com.sx4.bot.utility.MessageUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.WebhookClient;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.components.Component;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import okhttp3.Request;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class FreeGameManager implements WebhookManager {

	public static final Document DEFAULT_MESSAGE = new Document("embed", new Document("title", "{game.title}")
			.append("url", "{game.url}")
			.append("description", "{game.description}")
			.append("thumbnail", new Document("url", "{game.platform.icon}"))
			.append("image", new Document("url", "{game.image}"))
			.append("fields", List.of(
				new Document("name", "Price").append("value", "{game.original_price.equals(0).then(Free).else(~~£{game.original_price.format(,##0.00)}~~ Free)}").append("inline", true),
				new Document("name", "Publisher").append("value", "{game.publisher}").append("inline", true),
				new Document("name", "Promotion End").append("value", "<t:{game.promotion_end.epoch}:f>").append("inline", false)
			))
		).append("components", List.of(
			new Document("type", Component.Type.ACTION_ROW.getKey())
				.append("components", List.of(
					new Document("type", Component.Type.BUTTON.getKey())
						.append("label", "Open in Client")
						.append("style", ButtonStyle.LINK.getKey())
						.append("url", "https://sx4.dev/redirect?url={game.run_url.substring(1,-1)}")
						.append("disabled", "{game.exists(run_url).not}")
				))
		));

	private final Sx4 bot;

	private final Map<FreeGameType, Map<Object, List<FreeGame<?>>>> announcedGames;

	private final Map<Long, WebhookClient<Message>> webhooks;

	private final ScheduledExecutorService epicExecutor = Executors.newSingleThreadScheduledExecutor();
	private final ScheduledExecutorService steamExecutor = Executors.newSingleThreadScheduledExecutor();
	private final ScheduledExecutorService gogExecutor = Executors.newSingleThreadScheduledExecutor();

	public FreeGameManager(Sx4 bot) {
		this.bot = bot;
		this.webhooks = new HashMap<>();
		this.announcedGames = new HashMap<>();
	}

	public long getInitialDelay(long offset) {
		OffsetDateTime time = OffsetDateTime.now(ZoneOffset.UTC);
		return Duration.between(time, time.withMinute(30 * (int) Math.floor(time.getMinute() / 30D)).withSecond(0).withNano(0).plusMinutes(30).plusSeconds(offset)).toSeconds();
	}

	public boolean isAnnounced(FreeGame<?> game) {
		Map<Object, List<FreeGame<?>>> gameIds = this.announcedGames.get(game.getType());
		if (gameIds == null) {
			return false;
		}

		List<FreeGame<?>> announcedGames = gameIds.get(game.getId());
		if (announcedGames == null) {
			return false;
		}

		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

		return announcedGames.stream().map(FreeGame::getPromotionEnd).anyMatch(now::isBefore);
	}

	public void addAnnouncedGame(FreeGame<?> game) {
		this.announcedGames.compute(game.getType(), (type, ids) -> {
			Map<Object, List<FreeGame<?>>> gameIds = ids == null ? new HashMap<>() : ids;
			gameIds.compute(game.getId(), (key, value) -> {
				if (value == null) {
					List<FreeGame<?>> games = new ArrayList<>();
					games.add(game);
					return games;
				} else {
					value.add(game);
					return value;
				}
			});

			return gameIds;
		});
	}

	public WebhookClient<Message> getWebhook(long id) {
		return this.webhooks.get(id);
	}

	public WebhookClient<Message> removeWebhook(long id) {
		return this.webhooks.remove(id);
	}

	public void putWebhook(long id, WebhookClient<Message> webhook) {
		this.webhooks.put(id, webhook);
	}

	private CompletableFuture<UpdateResult> disableFreeGameChannel(Channel channel) {
		Bson update = Updates.combine(
			Updates.unset("webhook.id"),
			Updates.unset("webhook.token"),
			Updates.set("enabled", false)
		);

		return this.bot.getMongo().updateFreeGameChannel(Filters.eq("channelId", channel.getIdLong()), update, new UpdateOptions());
	}

	private CompletableFuture<List<SentWebhookMessage>> createWebhook(WebhookChannel channel, Document webhookData, List<MessageCreateData> messages, boolean premium) {
		if (!channel.getGuild().getSelfMember().hasPermission(channel, Permission.MANAGE_WEBHOOKS)) {
			this.disableFreeGameChannel(channel).whenComplete(MongoDatabase.exceptionally());
			return CompletableFuture.completedFuture(Collections.emptyList());
		}

		return channel.createWebhook("Sx4 - Free Games").submit().thenCompose(webhook -> {
			this.webhooks.put(channel.getIdLong(), webhook);

			Bson update = Updates.combine(
				Updates.set("webhook.id", webhook.getIdLong()),
				Updates.set("webhook.token", webhook.getToken())
			);

			Document webhookDataCopy = new Document(webhookData).append("id", webhook.getIdLong()).append("token", webhook.getToken());

			long webhookChannelId = channel.getWebhookChannel().getIdLong();

			return this.bot.getMongo().updateManyFreeGameChannels(Filters.or(Filters.eq("channelId", webhookChannelId), Filters.eq("webhook.channelId", webhookChannelId)), update, new UpdateOptions())
				.thenCompose(result -> this.sendFreeGameNotificationMessages(channel, webhookDataCopy, messages, premium));
		}).exceptionallyCompose(exception -> {
			Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
			if (cause instanceof ErrorResponseException && ((ErrorResponseException) cause).getErrorCode() == 10015) {
				this.webhooks.remove(channel.getIdLong());

				return this.createWebhook(channel, webhookData, messages, premium);
			}

			return CompletableFuture.completedFuture(Collections.emptyList());
		});
	}

	public CompletableFuture<List<SentWebhookMessage>> sendFreeGameNotificationMessages(WebhookChannel channel, Document webhookData, List<MessageCreateData> messages, boolean premium) {
		WebhookClient<Message> webhook;
		if (this.webhooks.containsKey(channel.getIdLong())) {
			webhook = this.webhooks.get(channel.getIdLong());
		} else if (!webhookData.containsKey("id")) {
			return this.createWebhook(channel, webhookData, messages, premium);
		} else {
			webhook = WebhookClient.createClient(channel.getJDA(), Long.toString(webhookData.getLong("id")), webhookData.getString("token"));

			this.webhooks.put(channel.getIdLong(), webhook);
		}

		String avatar = channel.getJDA().getSelfUser().getEffectiveAvatarUrl();

		List<SentWebhookMessage> completedMessages = new ArrayList<>();

		CompletableFuture<Boolean> future = CompletableFuture.completedFuture(null);
		for (MessageCreateData message : messages) {
			WebhookMessageCreateAction<Message> action = webhook.sendMessage(message)
				.setAvatarUrl(premium ? webhookData.get("avatar", avatar) : avatar)
				.setUsername(premium ? webhookData.get("name", "Sx4 - Free Games") : "Sx4 - Free Games");

			future = future.thenCompose($ -> channel.sendWebhookMessage(action))
				.thenApply(webhookMessage -> completedMessages.add(new SentWebhookMessage(webhookMessage, webhook.getIdLong(), webhook.getToken())));
		}

		return future.thenApply($ -> completedMessages).exceptionallyCompose(exception -> {
			Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
			if (cause instanceof ErrorResponseException && ((ErrorResponseException) cause).getErrorCode() == 10015) {
				this.webhooks.remove(channel.getIdLong());

				return this.createWebhook(channel, webhookData, messages, premium);
			}

			return CompletableFuture.completedFuture(Collections.emptyList());
		});
	}

	public CompletableFuture<List<SentWebhookMessage>> sendFreeGameNotifications(List<? extends FreeGame<?>> games) {
		if (games.isEmpty()) {
			return CompletableFuture.completedFuture(Collections.emptyList());
		}

		games.forEach(this::addAnnouncedGame);

		List<Document> gameData = games.stream().map(FreeGame::toData).collect(Collectors.toList());

		this.bot.getMongo().insertManyAnnouncedGames(gameData).whenComplete(MongoDatabase.exceptionally());

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
			List<CompletableFuture<List<SentWebhookMessage>>> futures = new ArrayList<>();
			for (Document data : documents) {
				if (!data.getBoolean("enabled", true)) {
					continue;
				}

				GuildMessageChannelUnion channel = this.bot.getShardManager().getChannelById(GuildMessageChannelUnion.class, data.getLong("channelId"));
				if (channel == null) {
					continue;
				}

				boolean premium = data.getBoolean("premium");
				Document webhookData = data.get("webhook", MongoDatabase.EMPTY_DOCUMENT);

				long platforms = data.get("platforms", FreeGameType.ALL);

				List<MessageCreateData> messages = new ArrayList<>();
				for (FreeGame<?> game : games) {
					long raw = game.getType().getRaw();
					if ((platforms & raw) != raw) {
						continue;
					}

					Formatter<Document> formatter = new JsonFormatter(data.get("message", FreeGameManager.DEFAULT_MESSAGE))
						.addVariable("game", game);

					MessageCreateData message;
					try {
						message = MessageUtility.fromCreateJson(formatter.parse(), true)
							.build();
					} catch (IllegalArgumentException e) {
						bulkData.add(new UpdateOneModel<>(Filters.eq("_id", data.getObjectId("_id")), Updates.unset("message")));
						continue;
					}

					messages.add(message);
				}

				if (messages.isEmpty()) {
					continue;
				}

				MessageCreateData firstMessage = messages.get(0);
				String firstContent = firstMessage.getContent();
				if (messages.size() == 1 || !messages.stream().skip(1).allMatch(message -> message.getContent().equals(firstContent))) {
					futures.add(this.sendFreeGameNotificationMessages(new WebhookChannel(channel), webhookData, messages, premium));
					continue;
				}

				List<MessageCreateData> updatedMessages = new ArrayList<>();

				MessageCreateBuilder baseMessage = new MessageCreateBuilder()
					.setContent(firstContent);

				int length = 0;
				List<MessageEmbed> embeds = new ArrayList<>();
				for (MessageCreateData message : messages) {
					List<MessageEmbed> nextEmbeds = message.getEmbeds();
					int nextLength = MessageUtility.getEmbedLength(nextEmbeds);

					if (embeds.size() + nextEmbeds.size() > Message.MAX_EMBED_COUNT || length + nextLength > MessageEmbed.EMBED_MAX_LENGTH_BOT) {
						baseMessage.addEmbeds(embeds);
						updatedMessages.add(baseMessage.build());
						baseMessage.setEmbeds();
						embeds.clear();
						embeds.addAll(nextEmbeds);
						length = nextLength;
						continue;
					}

					embeds.addAll(nextEmbeds);
					length += nextLength;
				}

				baseMessage.addEmbeds(embeds);
				updatedMessages.add(baseMessage.build());

				futures.add(this.sendFreeGameNotificationMessages(new WebhookChannel(channel), webhookData, updatedMessages, premium));
			}

			if (!bulkData.isEmpty()) {
				this.bot.getMongo().bulkWriteFreeGameChannels(bulkData).whenComplete(MongoDatabase.exceptionally());
			}

			return FutureUtility.allOf(futures).thenApply(list -> list.stream().flatMap(List::stream).collect(Collectors.toList()));
		});
	}

	public void ensureEpicFreeGames() {
		FreeGameUtility.retrieveFreeGames(this.bot.getHttpClient(), games -> {
			OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

			List<EpicFreeGame> currentGames = games.stream()
				.filter(Predicate.not(this::isAnnounced))
				.filter(game -> !game.getPromotionStart().isAfter(now) && game.getPromotionEnd().isAfter(now))
				.collect(Collectors.toList());

			if (!currentGames.isEmpty()) {
				this.sendFreeGameNotifications(currentGames).whenComplete(MongoDatabase.exceptionally());
			}

			EpicFreeGame newestGame = games.stream()
				.filter(game -> game.getPromotionStart().isAfter(now))
				.min(Comparator.comparing(game -> Duration.between(now, game.getPromotionStart())))
				.orElse(null);

			if (newestGame == null) {
				// re-request games as they have not been refreshed on the API
				this.epicExecutor.schedule(this::ensureEpicFreeGames, 10, TimeUnit.MINUTES);
				return;
			}

			OffsetDateTime promotionStart = newestGame.getPromotionStart();

			this.epicExecutor.schedule(this::ensureEpicFreeGames, Duration.between(now, promotionStart).toSeconds() + 5, TimeUnit.SECONDS);
		});
	}

	private GOGFreeGame getGOGGameFromData(org.jsoup.nodes.Document document, String url, OffsetDateTime end, String image) {
		Element element = document.getElementsByAttributeValue("type", "application/ld+json").first();
		Document data = Document.parse(element.html());

		int id = Integer.parseInt(data.getString("sku"));

		Document offers = data.get("offers", Document.class);
		String priceText = offers.getString("price");

		int price = (int) (Double.parseDouble(priceText) * 100);

		Element titleElement = document.getElementsByClass("productcard-basics__title").first();
		String title = titleElement.text();

		Element descriptionElement = document.getElementsByClass("description").first();
		String description = descriptionElement.textNodes().get(0).text();

		Elements infoElements = document.getElementsByClass("table__row details__rating details__row ");

		String publisher = infoElements.stream()
			.filter(infoElement -> {
				Element labelElement = infoElement.getElementsByClass("details__category table__row-label").first();
				return labelElement.text().contains("Company:");
			})
			.map(infoElement -> infoElement.getElementsByClass("details__content table__row-content").first())
			.flatMap(publishers -> publishers.children().stream())
			.filter(publisherElement -> publisherElement.attr("href").contains("publishers"))
			.map(Element::text)
			.findFirst()
			.orElse(null);

		return GOGFreeGame.fromData(id, title, description, publisher, url, price, end, image);
	}

	public CompletableFuture<List<GOGFreeGame>> retrieveFreeGOGGames() {
		Request resultRequest = new Request.Builder()
			.url("https://gog.com/en")
			.addHeader("Accept-Language", "en")
			.build();

		CompletableFuture<List<GOGFreeGame>> future = new CompletableFuture<>();
		this.bot.getHttpClient().newCall(resultRequest).enqueue((HttpCallback) resultResponse -> {
			org.jsoup.nodes.Document resultsDocument = Jsoup.parse(resultResponse.body().string());

			List<CompletableFuture<GOGFreeGame>> futures = new ArrayList<>();

			Element giveawayElement = resultsDocument.getElementsByAttribute("giveaway-banner").first();
			if (giveawayElement != null) {
				String url = giveawayElement.attr("ng-href");

				Element countdownElement = giveawayElement.getElementsByClass("giveaway-banner__countdown-timer").first();
				OffsetDateTime end = OffsetDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(countdownElement.attr("end-date"))), ZoneOffset.UTC);

				Element imageElement = giveawayElement.getElementsByAttributeValue("type", "image/png").first();
				String imageSource = imageElement.attr("srcset");

				int commaIndex = imageSource.indexOf(',');
				String image = imageSource.substring(0, commaIndex - 3);

				Request gameRequest = new Request.Builder()
					.url("https://gog.com" + url)
					.addHeader("Cookie", "gog_lc=GB_GBP_en-US")
					.build();

				CompletableFuture<GOGFreeGame> gameFuture = new CompletableFuture<>();
				this.bot.getHttpClient().newCall(gameRequest).enqueue((HttpCallback) gameResponse -> {
					org.jsoup.nodes.Document gameDocument = Jsoup.parse(gameResponse.body().string());

					GOGFreeGame game = this.getGOGGameFromData(gameDocument, url, end, "https:" + image.trim());
					if (this.isAnnounced(game)) {
						gameFuture.complete(null);
						return;
					}

					gameFuture.complete(game);
				});

				futures.add(gameFuture);
			}

			Elements spots = resultsDocument.getElementsByClass("big-spot");
			if (!spots.isEmpty()) {
				for (Element spot : spots) {
					Element priceElement = spot.getElementsByClass("big-spot__price-amount").first();
					if (priceElement == null || !priceElement.text().equals("0.00")) {
						continue;
					}

					String url = spot.attr("href");

					Element countdown = spot.getElementsByTag("gog-countdown-timer").first();
					OffsetDateTime end = OffsetDateTime.parse(countdown.attr("end-date"));

					Element imageElement = spot.getElementsByAttributeValue("type", "image/png").first();
					String imageSource = imageElement.attr("lazy-srcset");
					String image = "https:" + imageSource.substring(0, imageSource.indexOf(',')).trim();

					Request gameRequest = new Request.Builder()
						.url("https://gog.com" + url)
						.addHeader("Cookie", "gog_lc=GB_GBP_en-US")
						.build();

					CompletableFuture<GOGFreeGame> gameFuture = new CompletableFuture<>();
					this.bot.getHttpClient().newCall(gameRequest).enqueue((HttpCallback) gameResponse -> {
						org.jsoup.nodes.Document gameDocument = Jsoup.parse(gameResponse.body().string());

						GOGFreeGame game = this.getGOGGameFromData(gameDocument, url, end, image);
						if (this.isAnnounced(game)) {
							gameFuture.complete(null);
							return;
						}

						gameFuture.complete(game);
					});

					futures.add(gameFuture);
				}
			}

			FutureUtility.allOf(futures, Objects::nonNull).thenAccept(future::complete);
		});

		return future;
	}

	public CompletableFuture<List<SteamFreeGame>> retrieveFreeSteamGames() {
		Request resultRequest = new Request.Builder()
			.url("https://store.steampowered.com/search/?maxprice=free&specials=1&cc=gb")
			.addHeader("Accept-Language", "en")
			.build();

		CompletableFuture<List<SteamFreeGame>> future = new CompletableFuture<>();
		this.bot.getHttpClient().newCall(resultRequest).enqueue((HttpCallback) resultResponse -> {
			org.jsoup.nodes.Document resultsDocument = Jsoup.parse(resultResponse.body().string());

			Element results = resultsDocument.getElementById("search_resultsRows");
			if (results == null) {
				return;
			}

			List<CompletableFuture<SteamFreeGame>> futures = new ArrayList<>();
			for (Element result : results.children()) {
				int id = Integer.parseInt(result.attr("data-ds-appid"));

				Request gameRequest = new Request.Builder()
					.url("https://store.steampowered.com/app/" + id + "?cc=gb")
					.addHeader("Accept-Language", "en")
					.addHeader("Cookie", "birthtime=-1801439999;mature_content=1")
					.build();

				CompletableFuture<SteamFreeGame> gameFuture = new CompletableFuture<>();
				this.bot.getHttpClient().newCall(gameRequest).enqueue((HttpCallback) gameResponse -> {
					org.jsoup.nodes.Document document = Jsoup.parse(gameResponse.body().string());
					Element content = document.getElementsByClass("page_content_ctn").first();

					Element discount = document.getElementsByClass("discount_pct").first();
					if (discount == null || !discount.text().equals("-100%")) {
						gameFuture.complete(null);
						return;
					}

					SteamFreeGame game = SteamFreeGame.fromData(id, content);
					if (game == null || this.isAnnounced(game)) {
						gameFuture.complete(null);
						return;
					}

					gameFuture.complete(game);
				});

				futures.add(gameFuture);
			}

			FutureUtility.allOf(futures, Objects::nonNull).thenAccept(future::complete);
		});

		return future;
	}

	public void ensureGOGFreeGames() {
		this.gogExecutor.scheduleAtFixedRate(() -> {
			this.retrieveFreeGOGGames()
				.thenCompose(this::sendFreeGameNotifications)
				.whenComplete(MongoDatabase.exceptionally());
		}, this.getInitialDelay(300), 1800, TimeUnit.SECONDS);
	}

	public void ensureSteamFreeGames() {
		this.steamExecutor.scheduleAtFixedRate(() -> {
			this.retrieveFreeSteamGames()
				.thenCompose(this::sendFreeGameNotifications)
				.whenComplete(MongoDatabase.exceptionally());
		}, this.getInitialDelay(300), 1800, TimeUnit.SECONDS);
	}

	public void ensureAnnouncedGames() {
		this.bot.getMongo().getAnnouncedGames().find().forEach(data -> this.addAnnouncedGame(FreeGameUtility.getFreeGame(data)));
	}

}

