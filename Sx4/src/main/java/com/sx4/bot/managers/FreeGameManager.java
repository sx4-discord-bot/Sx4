package com.sx4.bot.managers;

import club.minnced.discord.webhook.exception.HttpException;
import club.minnced.discord.webhook.send.WebhookMessage;
import com.mongodb.client.model.*;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.info.FreeGame;
import com.sx4.bot.entities.webhook.ReadonlyMessage;
import com.sx4.bot.entities.webhook.WebhookClient;
import com.sx4.bot.exceptions.mod.BotPermissionException;
import com.sx4.bot.formatter.JsonFormatter;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.FreeGameUtility;
import com.sx4.bot.utility.MessageUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.TextChannel;
import okhttp3.OkHttpClient;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
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
				new Document("name", "Promotion Duration").append("value", "{game.promotion_start.format(dd MMM HH:mm)} - {game.promotion_end.format(dd MMM HH:mm)}").append("inline", false)
			))
	);

	private final Sx4 bot;

	private final Map<Long, WebhookClient> webhooks;

	private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(5);

	private final ScheduledExecutorService webhookExecutor = Executors.newSingleThreadScheduledExecutor();
	private final OkHttpClient client = new OkHttpClient();

	public FreeGameManager(Sx4 bot) {
		this.bot = bot;
		this.webhooks = new HashMap<>();
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

	private CompletableFuture<ReadonlyMessage> createWebhook(TextChannel channel, WebhookMessage message) {
		if (!channel.getGuild().getSelfMember().hasPermission(channel, Permission.MANAGE_WEBHOOKS)) {
			return CompletableFuture.failedFuture(new BotPermissionException(Permission.MANAGE_WEBHOOKS));
		}

		return channel.createWebhook("Sx4 - Free Games").submit().thenCompose(webhook -> {
			WebhookClient webhookClient = new WebhookClient(webhook.getIdLong(), webhook.getToken(), this.webhookExecutor, this.client);

			this.webhooks.put(channel.getIdLong(), webhookClient);

			Bson update = Updates.combine(
				Updates.set("webhook.id", webhook.getIdLong()),
				Updates.set("webhook.token", webhook.getToken())
			);

			return this.bot.getMongo().updateFreeGameChannel(Filters.eq("channelId", channel.getIdLong()), update, new UpdateOptions())
				.thenCompose(result -> webhookClient.send(message))
				.thenApply(webhookMessage -> new ReadonlyMessage(webhookMessage, webhook.getIdLong(), webhook.getToken()));
		}).exceptionallyCompose(exception -> {
			Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
			if (cause instanceof HttpException && ((HttpException) cause).getCode() == 404) {
				this.webhooks.remove(channel.getIdLong());

				return this.createWebhook(channel, message);
			}

			return CompletableFuture.failedFuture(exception);
		});
	}

	public CompletableFuture<ReadonlyMessage> sendFreeGameNotification(TextChannel channel, Document webhookData, WebhookMessage message) {
		WebhookClient webhook;
		if (this.webhooks.containsKey(channel.getIdLong())) {
			webhook = this.webhooks.get(channel.getIdLong());
		} else if (!webhookData.containsKey("id")) {
			return this.createWebhook(channel, message);
		} else {
			webhook = new WebhookClient(webhookData.getLong("id"), webhookData.getString("token"), this.webhookExecutor, this.client);

			this.webhooks.put(channel.getIdLong(), webhook);
		}

		return webhook.send(message)
			.thenApply(webhookMessage -> new ReadonlyMessage(webhookMessage, webhook.getId(), webhook.getToken()))
			.exceptionallyCompose(exception -> {
				Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
				if (cause instanceof HttpException && ((HttpException) cause).getCode() == 404) {
					this.webhooks.remove(channel.getIdLong());

					return this.createWebhook(channel, message);
				}

				return CompletableFuture.failedFuture(exception);
			});
	}

	public void scheduleFreeGameNotification(long duration, List<FreeGame> games) {
		this.executor.schedule(() -> {
			List<Bson> guildPipeline = List.of(
				Aggregates.match(Operators.expr(Operators.eq("$_id", "$$guildId"))),
				Aggregates.project(Projections.computed("premium", Operators.lt(Operators.nowEpochSecond(), Operators.ifNull("$premium.endAt", 0L))))
			);

			List<Bson> pipeline = List.of(
				Aggregates.lookup("guilds", List.of(new Variable<>("guildId", "$guildId")), guildPipeline, "premium"),
				Aggregates.addFields(new Field<>("premium", Operators.cond(Operators.isEmpty("$premium"), false, Operators.get(Operators.arrayElemAt("$premium", 0), "premium"))))
			);

			this.bot.getMongo().aggregateFreeGameChannels(pipeline).whenComplete((documents, exception) -> {
				if (ExceptionUtility.sendErrorMessage(exception)) {
					return;
				}

				this.bot.getExecutor().submit(() -> {
					documents.forEach(data -> {
						TextChannel channel = this.bot.getShardManager().getTextChannelById(data.getLong("channelId"));
						if (channel == null) {
							return;
						}

						String avatar = channel.getJDA().getSelfUser().getEffectiveAvatarUrl();
						boolean premium = data.getBoolean("premium");
						Document webhookData = data.get("webhook", MongoDatabase.EMPTY_DOCUMENT);

						List<WebhookMessage> messages = new ArrayList<>();
						for (FreeGame game : games) {
							JsonFormatter formatter = new JsonFormatter(data.get("message", FreeGameManager.DEFAULT_MESSAGE))
								.addVariable("game", game);

							WebhookMessage message = MessageUtility.fromJson(formatter.parse())
								.setAvatarUrl(premium ? webhookData.get("avatar", avatar) : avatar)
								.setUsername(premium ? webhookData.get("name", "Sx4 - Free Games") : "Sx4 - Free Games")
								.build();

							messages.add(message);
						}

						messages.forEach(message -> this.sendFreeGameNotification(channel, webhookData, message));
					});
				});
			});
		}, duration, TimeUnit.SECONDS);
	}

	public void ensureFreeGameScheduler() {
		this.bot.getHttpClient().newCall(FreeGameUtility.REQUEST).enqueue((HttpCallback) response -> {
			Document document = Document.parse(response.body().string());

			List<Document> elements = document.getEmbedded(List.of("data", "Catalog", "searchStore", "elements"), Collections.emptyList());

			OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

			List<Document> upcomingGames = elements.stream()
				.filter(game -> {
					Document offer = FreeGameUtility.getUpcomingPromotionalOffer(game);
					return offer != null && offer.getEmbedded(List.of("discountSetting", "discountPercentage"), Integer.class) == 0;
				})
				.collect(Collectors.toList());

			Map<Long, List<FreeGame>> intervals = new HashMap<>();
			for (Document gameData : upcomingGames) {
				FreeGame game = FreeGame.fromData(gameData);

				long seconds = Duration.between(now, game.getStart()).toSeconds();
				intervals.compute(seconds, (key, value) -> {
					List<FreeGame> games = value == null ? new ArrayList<>() : value;
					games.add(game);
					return games;
				});
			}

			for (long interval : intervals.keySet()) {
				this.scheduleFreeGameNotification(interval, intervals.get(interval));
			}
		});
	}

}

