package com.sx4.bot.commands.notifications;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.*;
import com.sx4.bot.annotations.argument.AdvancedMessage;
import com.sx4.bot.annotations.argument.ImageUrl;
import com.sx4.bot.annotations.argument.Lowercase;
import com.sx4.bot.annotations.command.AuthorPermissions;
import com.sx4.bot.annotations.command.BotPermissions;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.model.AggregateOperators;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.twitch.TwitchStream;
import com.sx4.bot.entities.twitch.TwitchStreamType;
import com.sx4.bot.entities.twitch.TwitchStreamer;
import com.sx4.bot.entities.webhook.WebhookChannel;
import com.sx4.bot.formatter.output.FormatterManager;
import com.sx4.bot.formatter.output.JsonFormatter;
import com.sx4.bot.formatter.output.function.FormatterVariable;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.managers.TwitchManager;
import com.sx4.bot.paged.MessagePagedResult;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.FutureUtility;
import com.sx4.bot.utility.MessageUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.attribute.IWebhookContainer;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import okhttp3.Request;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class TwitchNotificationCommand extends Sx4Command {

	public TwitchNotificationCommand() {
		super("twitch notification", 493);

		super.setDescription("Subscribe to a twitch streamer so anytime they go live it's sent in a channel of your choice");
		super.setAliases("twitch notif");
		super.setExamples("twitch notification add", "twitch notification remove", "twitch notification list");
		super.setCategoryAll(ModuleCategory.NOTIFICATIONS);
	}

	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}

	@Command(value="add", description="Adds a twitch notification to a specific channel")
	@CommandId(494)
	@Command.Cooldown(5)
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	@Examples({"twitch notification add #channel esl_csgo", "twitch notification add pgl"})
	public void add(Sx4CommandEvent event, @Argument(value="channel", nullDefault=true) WebhookChannel channel, @Argument(value="streamer name", endless=true) @Lowercase String streamer) {
		Request request = new Request.Builder()
			.url("https://api.twitch.tv/helix/users?login=" + URLEncoder.encode(streamer, StandardCharsets.UTF_8))
			.addHeader("Authorization", "Bearer " + event.getBot().getTwitchConfig().getToken())
			.addHeader("Client-Id", event.getConfig().getTwitchClientId())
			.build();

		event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
			if (response.code() == 400) {
				event.replyFailure("I could not find that twitch streamer").queue();

				response.close();
				return;
			}

			Document json = Document.parse(response.body().string());

			List<Document> entries = json.getList("data", Document.class);
			if (entries.isEmpty()) {
				event.replyFailure("I could not find that twitch streamer").queue();
				return;
			}

			long webhookChannelId = channel.getWebhookChannel().getIdLong();

			Document data = entries.get(0);
			String id = data.getString("id"), name = data.getString("display_name");

			List<Bson> guildPipeline = List.of(
				Aggregates.project(Projections.fields(Projections.computed("premium", Operators.lt(Operators.nowEpochSecond(), Operators.ifNull("$premium.endAt", 0L))), Projections.computed("guildId", "$_id"))),
				Aggregates.match(Filters.eq("guildId", event.getGuild().getIdLong()))
			);

			List<Bson> countPipeline = List.of(
				Aggregates.match(Filters.and(Filters.eq("streamerId", id), Filters.exists("enabled", false))),
				Aggregates.limit(1),
				Aggregates.group(null, Accumulators.sum("streamerCount", 1))
			);

			List<Bson> pipeline = List.of(
				Aggregates.match(Filters.eq("guildId", event.getGuild().getIdLong())),
				Aggregates.group(null, Accumulators.push("notifications", Operators.ROOT)),
				Aggregates.unionWith("twitchNotifications", countPipeline),
				Aggregates.unionWith("guilds", guildPipeline),
				AggregateOperators.mergeFields("premium", "notifications", "streamerCount"),
				Aggregates.project(Projections.fields(Projections.computed("webhook", Operators.first(Operators.map(Operators.filter(Operators.ifNull("$notifications", Collections.EMPTY_LIST), Operators.eq(Operators.ifNull("$$this.webhook.channelId", "$$this.channelId"), channel.getWebhookChannel().getIdLong())), "$$this.webhook"))), Projections.computed("subscribe", Operators.eq(Operators.ifNull("$streamerCount", 0), 0)), Projections.computed("premium", Operators.ifNull("$premium", false)), Projections.computed("count", Operators.size(Operators.filter(Operators.ifNull("$notifications", Collections.EMPTY_LIST), Operators.extinct("$$this.enabled"))))))
			);

			AtomicBoolean subscribe = new AtomicBoolean();
			event.getMongo().aggregateTwitchNotifications(pipeline).thenCompose(documents -> {
				Document counter = documents.isEmpty() ? null : documents.get(0);

				int count = counter == null ? 0 : counter.getInteger("count");
				if (counter != null && count >= 3 && !counter.getBoolean("premium")) {
					throw new IllegalArgumentException("You need to have Sx4 premium to have more than 3 enabled twitch notifications, you can get premium at <https://www.patreon.com/Sx4>");
				}

				if (count >= 10) {
					throw new IllegalArgumentException("You can not have any more than 10 enabled twitch notifications");
				}

				subscribe.set(counter == null || counter.getBoolean("subscribe"));

				Document notification = new Document("streamerId", id)
					.append("channelId", channel.getIdLong())
					.append("guildId", event.getGuild().getIdLong());

				Document webhook = counter == null ? new Document() : counter.get("webhook", new Document());
				if (channel.getIdLong() != webhookChannelId) {
					webhook.append("channelId", webhookChannelId);
				}

				if (!webhook.isEmpty()) {
					notification.append("webhook", webhook);
				}

				return event.getMongo().insertTwitchNotification(notification);
			}).whenComplete((result, exception) -> {
				Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
				if (cause instanceof MongoWriteException && ((MongoWriteException) cause).getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
					event.replyFailure("You already have a notification setup for that twitch streamer in " + channel.getAsMention()).queue();
					return;
				}

				if (cause instanceof IllegalArgumentException) {
					event.replyFailure(cause.getMessage()).queue();
					return;
				}

				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (subscribe.get()) {
					event.getBot().getTwitchManager().subscribe(id);
				}

				event.replyFormat("Notifications will now be sent in %s when **%s** goes live with id `%s` %s", channel.getAsMention(), name, result.getInsertedId().asObjectId().getValue().toHexString(), event.getConfig().getSuccessEmote()).queue();
			});
		});
	}

	@Command(value="remove", description="Remove a twitch notification by id")
	@CommandId(495)
	@Command.Cooldown(5)
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	@Examples({"twitch notification remove 5e45ce6d3688b30ee75201ae"})
	public void remove(Sx4CommandEvent event, @Argument(value="id") ObjectId id) {
		FindOneAndDeleteOptions options = new FindOneAndDeleteOptions().projection(Projections.include("channelId", "webhook", "streamerId"));
		event.getMongo().findAndDeleteTwitchNotification(Filters.and(Filters.eq("_id", id), Filters.eq("guildId", event.getGuild().getIdLong())), options).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (data == null) {
				event.replyFailure("I could not find that notification").queue();
				return;
			}

			Document webhook = data.get("webhook", Document.class);

			long channelId = data.getLong("channelId");
			long webhookChannelId = webhook == null ? channelId : webhook.get("channelId", channelId);

			String streamerId = data.getString("streamerId");

			event.getBot().getTwitchManager().removeWebhook(channelId);

			IWebhookContainer channel = event.getGuild().getChannelById(IWebhookContainer.class, webhookChannelId);

			if (webhook != null && channel != null) {
				channel.deleteWebhookById(Long.toString(webhook.getLong("id"))).queue(null, ErrorResponseException.ignore(ErrorResponse.UNKNOWN_WEBHOOK));
			}

			long count = event.getMongo().countTwitchNotifications(Filters.eq("streamerId", streamerId), new CountOptions().limit(1));
			if (count == 0) {
				event.getBot().getTwitchManager().unsubscribe(streamerId);
			}

			event.replySuccess("You will no longer receive notifications in <#" + channelId + "> for that streamer").queue();
		});
	}

	@Command(value="toggle", description="Enables/disables a specific Twitch notification")
	@CommandId(496)
	@Command.Cooldown(5)
	@Examples({"twitch notification toggle 5e45ce6d3688b30ee75201ae"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void toggle(Sx4CommandEvent event, @Argument(value="id") ObjectId id) {
		List<Bson> guildPipeline = List.of(
			Aggregates.project(Projections.fields(Projections.computed("premium", Operators.lt(Operators.nowEpochSecond(), Operators.ifNull("$premium.endAt", 0L))), Projections.computed("guildId", "$_id"))),
			Aggregates.match(Filters.eq("guildId", event.getGuild().getIdLong()))
		);

		List<Bson> countPipeline = List.of(
			Aggregates.match(Operators.expr(Operators.and(Operators.and(Operators.eq("$streamerId", "$$notification.streamerId"), Operators.extinct("$enabled"))))),
			Aggregates.limit(2),
			Aggregates.group(null, Accumulators.sum("streamerCount", 1))
		);

		List<Bson> pipeline = List.of(
			Aggregates.match(Filters.eq("guildId", event.getGuild().getIdLong())),
			Aggregates.project(Projections.include("streamerId", "enabled", "webhook", "channelId")),
			Aggregates.group(null, Accumulators.push("notifications", Operators.ROOT)),
			Aggregates.unionWith("guilds", guildPipeline),
			AggregateOperators.mergeFields("premium", "notifications"),
			Aggregates.project(Projections.fields(Projections.include("premium", "notifications"), Projections.computed("notification", Operators.first(Operators.filter(Operators.ifNull("$notifications", Collections.EMPTY_LIST), Operators.eq("$$this._id", id)))))),
			Aggregates.lookup("twitchNotifications", List.of(new Variable<>("notification", "$notification")), countPipeline, "streamerCount"),
			Aggregates.project(Projections.fields(Projections.computed("webhook", Operators.first(Operators.map(Operators.filter(Operators.ifNull("$notifications", Collections.EMPTY_LIST), Operators.eq(Operators.ifNull("$$this.webhook.channelId", "$$this.channelId"), Operators.ifNull("$notification.webhook.channelId", "$notification.channelId"))), "$$this.webhook"))), Projections.computed("streamerId", "$notification.streamerId"), Projections.computed("streamerCount", Operators.cond(Operators.isEmpty("$streamerCount"), 0, Operators.get(Operators.arrayElemAt("$streamerCount", 0), "streamerCount"))), Projections.computed("premium", Operators.ifNull("$premium", false)), Projections.computed("count", Operators.size(Operators.ifNull(Operators.filter("$notifications", Operators.extinct("$$this.enabled")), Collections.EMPTY_LIST))), Projections.computed("disabled", Operators.not(Operators.ifNull("$notification.enabled", true)))))
		);

		AtomicInteger subscribe = new AtomicInteger();
		AtomicReference<String> streamerId = new AtomicReference<>();
		event.getMongo().aggregateTwitchNotifications(pipeline).thenCompose(documents -> {
			Document data = documents.isEmpty() ? null : documents.get(0);
			if (data == null || !data.containsKey("streamerId")) {
				throw new IllegalArgumentException("There is not a twitch notification with that id");
			}

			boolean disabled = data.getBoolean("disabled");
			int count = data.getInteger("count");
			if (disabled && count >= 3 && !data.getBoolean("premium")) {
				throw new IllegalArgumentException("You need to have Sx4 premium to have more than 3 enabled twitch notifications, you can get premium at <https://www.patreon.com/Sx4>");
			}

			if (count >= 10) {
				throw new IllegalArgumentException("You can not have any more than 10 enabled twitch notifications");
			}

			int streamerCount = data.getInteger("streamerCount", -1);
			if (disabled && streamerCount == 0) {
				subscribe.set(1);
			} else if (!disabled && streamerCount == 1) {
				subscribe.set(2);
			}

			streamerId.set(data.getString("streamerId"));

			List<Bson> update = new ArrayList<>();
			update.add(Operators.set("enabled", Operators.cond(Operators.exists("$enabled"), Operators.REMOVE, false)));

			Document webhook = data.get("webhook", new Document());
			if (disabled && !webhook.isEmpty()) {
				update.add(Operators.set("webhook", webhook));
			}

			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER).projection(Projections.include("enabled"));

			return event.getMongo().findAndUpdateTwitchNotification(Filters.eq("_id", id), update, options);
		}).whenComplete((data, exception) -> {
			Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
			if (cause instanceof IllegalArgumentException) {
				event.replyFailure(cause.getMessage()).queue();
				return;
			}

			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (data == null) {
				event.replyFailure("There is not a twitch notification with that id").queue();
				return;
			}

			int value = subscribe.get();
			if (value == 1) {
				event.getBot().getTwitchManager().subscribe(streamerId.get());
			} else if (value == 2) {
				event.getBot().getTwitchManager().unsubscribe(streamerId.get());
			}

			event.replySuccess("That twitch notification is now **" + (data.get("enabled", true) ? "enabled" : "disabled") + "**").queue();
		});
	}

	@Command(value="message", description="Set the message you want to be sent for your specific notification, view the formatters for messages in `twitch notification formatting`")
	@CommandId(506)
	@Examples({"twitch notification message 5e45ce6d3688b30ee75201ae {streamer.url}", "twitch notification message 5e45ce6d3688b30ee75201ae **{streamer.name}** just went live, check it out: {streamer.url}"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void message(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="message", endless=true) String message) {
		event.getMongo().updateTwitchNotification(Filters.and(Filters.eq("_id", id), Filters.eq("guildId", event.getGuild().getIdLong())), Updates.set("message", new Document("content", message)), new UpdateOptions()).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getMatchedCount() == 0) {
				event.replyFailure("I could not find that notification").queue();
				return;
			}

			if (result.getModifiedCount() == 0) {
				event.replyFailure("Your message for that notification was already set to that").queue();
				return;
			}

			event.replySuccess("Your message has been updated for that notification").queue();
		});
	}

	@Command(value="advanced message", description="Same as `twitch notification message` but takes json for more advanced options")
	@CommandId(498)
	@Examples({"twitch notification advanced message 5e45ce6d3688b30ee75201ae {\"content\": \"{streamer.url}\"}", "twitch notification advanced message 5e45ce6d3688b30ee75201ae {\"embed\": {\"description\": \"{streamer.url}\"}}"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void advancedMessage(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="json", endless=true) @AdvancedMessage Document json) {
		event.getMongo().updateTwitchNotification(Filters.and(Filters.eq("_id", id), Filters.eq("guildId", event.getGuild().getIdLong())), Updates.set("message", json), new UpdateOptions()).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getMatchedCount() == 0) {
				event.replyFailure("I could not find that notification").queue();
				return;
			}

			if (result.getModifiedCount() == 0) {
				event.replyFailure("Your message for that notification was already set to that").queue();
				return;
			}

			event.replySuccess("Your message has been updated for that notification").queue();
		});
	}

	@Command(value="name", description="Set the name of the webhook that sends Twitch notifications for a specific notification")
	@CommandId(499)
	@Examples({"twitch notification name 5e45ce6d3688b30ee75201ae Twitch", "twitch notification name 5e45ce6d3688b30ee75201ae Quin's Minion"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void name(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="name", endless=true) String name) {
		event.getMongo().updateTwitchNotification(Filters.and(Filters.eq("_id", id), Filters.eq("guildId", event.getGuild().getIdLong())), Updates.set("webhook.name", name), new UpdateOptions()).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getMatchedCount() == 0) {
				event.replyFailure("I could not find that notification").queue();
				return;
			}

			if (result.getModifiedCount() == 0) {
				event.replyFailure("Your webhook name for that notification was already set to that").queue();
				return;
			}

			event.replySuccess("Your webhook name has been updated for that notification, this only works with premium <https://patreon.com/Sx4>").queue();
		});
	}

	@Command(value="avatar", description="Set the avatar of the webhook that sends Twitch notifications for a specific notification")
	@CommandId(500)
	@Examples({"twitch notification avatar 5e45ce6d3688b30ee75201ae Shea#6653", "twitch notification avatar 5e45ce6d3688b30ee75201ae https://i.imgur.com/i87lyNO.png"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void avatar(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="avatar", endless=true, acceptEmpty=true) @ImageUrl String url) {
		event.getMongo().updateTwitchNotification(Filters.and(Filters.eq("_id", id), Filters.eq("guildId", event.getGuild().getIdLong())), Updates.set("webhook.avatar", url), new UpdateOptions()).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getMatchedCount() == 0) {
				event.replyFailure("I could not find that notification").queue();
				return;
			}

			if (result.getModifiedCount() == 0) {
				event.replyFailure("Your webhook avatar for that notification was already set to that").queue();
				return;
			}

			event.replySuccess("Your webhook avatar has been updated for that notification, this only works with premium <https://patreon.com/Sx4>").queue();
		});
	}

	@Command(value="formatters", aliases={"format", "formatting"}, description="Get all the formatters for Twitch notifications you can use")
	@CommandId(501)
	@Examples({"twitch notification formatters"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void formatters(Sx4CommandEvent event) {
		EmbedBuilder embed = new EmbedBuilder()
			.setAuthor("Twitch Notification Formatters", null, event.getSelfUser().getEffectiveAvatarUrl());

		FormatterManager manager = FormatterManager.getDefaultManager();

		StringJoiner content = new StringJoiner("\n");
		for (FormatterVariable<?> variable : manager.getVariables(TwitchStream.class)) {
			content.add("`{stream." + variable.getName() + "}` - " + variable.getDescription());
		}

		for (FormatterVariable<?> variable : manager.getVariables(TwitchStreamer.class)) {
			content.add("`{streamer." + variable.getName() + "}` - " + variable.getDescription());
		}

		embed.setDescription(content.toString());

		event.reply(embed.build()).queue();
	}

	@Command(value="list", description="View all the twitch notifications you have setup throughout your server")
	@CommandId(502)
	@Examples({"twitch notification list"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void list(Sx4CommandEvent event) {
		List<Document> notifications = event.getMongo().getTwitchNotifications(Filters.eq("guildId", event.getGuild().getIdLong()), Projections.include("streamerId", "channelId", "message")).into(new ArrayList<>());
		if (notifications.isEmpty()) {
			event.replyFailure("You have no notifications setup in this server").queue();
			return;
		}

		List<String> streamers = notifications.stream().map(d -> d.getString("streamerId")).distinct().collect(Collectors.toList());
		int size = streamers.size();

		List<CompletableFuture<Map<String, TwitchStreamer>>> futures = new ArrayList<>();
		for (int i = 0; i < Math.ceil(size / 100D); i++) {
			List<String> splitStreamers = streamers.subList(i * 100, Math.min((i + 1) * 100, size));
			String ids = String.join("&id=", splitStreamers);

			Request request = new Request.Builder()
				.url("https://api.twitch.tv/helix/users?id=" + ids)
				.addHeader("Authorization", "Bearer " + event.getBot().getTwitchConfig().getToken())
				.addHeader("Client-Id", event.getBot().getConfig().getTwitchClientId())
				.build();

			CompletableFuture<Map<String, TwitchStreamer>> future = new CompletableFuture<>();
			event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
				Document json = Document.parse(response.body().string());

				List<Document> entries = json.getList("data", Document.class);

				Map<String, TwitchStreamer> names = new HashMap<>();
				for (Document entry : entries) {
					String id = entry.getString("id");
					names.put(id, new TwitchStreamer(id, entry.getString("display_name"), entry.getString("login")));
				}

				future.complete(names);
			});

			futures.add(future);
		}

		FutureUtility.allOf(futures).whenComplete((maps, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			Map<String, TwitchStreamer> names = new HashMap<>();
			for (Map<String, TwitchStreamer> map : maps) {
				names.putAll(map);
			}

			MessagePagedResult<Document> paged = new MessagePagedResult.Builder<>(event.getBot(), notifications)
				.setSelect()
				.setAuthor("Twitch Notifications", null, event.getGuild().getIconUrl())
				.setDisplayFunction(data -> {
					TwitchStreamer streamer = names.get(data.getString("streamerId"));

					return String.format("%s - [%s](%s)", data.getObjectId("_id").toHexString(), streamer == null ? "Unknown" : streamer.getName(), streamer == null ? "https://twitch.tv" : streamer.getUrl());
				}).build();

			paged.execute(event);
		});
	}

	@Command(value="preview", description="Preview a twitch notification")
	@CommandId(503)
	@Examples({"twitch notification preview 5e45ce6d3688b30ee75201ae"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void preview(Sx4CommandEvent event, @Argument(value="id") ObjectId id) {
		Document data = event.getMongo().getTwitchNotification(Filters.and(Filters.eq("_id", id), Filters.eq("guildId", event.getGuild().getIdLong())), Projections.include("streamerId", "message"));
		if (data == null) {
			event.replyFailure("I could not find that notification").queue();
			return;
		}

		String streamerId = data.getString("streamerId");

		Request request = new Request.Builder()
			.url("https://api.twitch.tv/helix/users?id=" + streamerId)
			.addHeader("Authorization", "Bearer " + event.getBot().getTwitchConfig().getToken())
			.addHeader("Client-Id", event.getBot().getConfig().getTwitchClientId())
			.build();

		event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
			Document json = Document.parse(response.body().string());

			List<Document> entries = json.getList("data", Document.class);
			if (entries.isEmpty()) {
				event.replyFailure("The twitch streamer for that notification no longer exists").queue();
				return;
			}

			Document entry = entries.get(0);

			Document message =  new JsonFormatter(data.get("message", TwitchManager.DEFAULT_MESSAGE))
				.addVariable("stream", new TwitchStream("0", TwitchStreamType.LIVE, "https://cdn.discordapp.com/attachments/344091594972069888/969319515714371604/twitch-test-preview.png", "Preview Title", "Preview Game", OffsetDateTime.now()))
				.addVariable("streamer", new TwitchStreamer(entry.getString("id"), entry.getString("display_name"), entry.getString("login")))
				.parse();

			try {
				event.reply(MessageUtility.fromCreateJson(message, true).build()).queue();
			} catch (IllegalArgumentException e) {
				event.replyFailure(e.getMessage()).queue();
			}
		});
	}

	@Command(value="types", description="Set what kind of twitch streams you want to receive from a specific notification")
	@CommandId(504)
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	@Examples({"twitch notification types 5e45ce6d3688b30ee75201ae LIVE", "twitch notification types 5e45ce6d3688b30ee75201ae PLAYLIST RERUN", "twitch notification types 5e45ce6d3688b30ee75201ae LIVE WATCH_PARTY"})
	public void types(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="types") TwitchStreamType... types) {
		event.getMongo().updateTwitchNotification(Filters.and(Filters.eq("_id", id), Filters.eq("guildId", event.getGuild().getIdLong())), Updates.set("types", TwitchStreamType.getRaw(types)), new UpdateOptions()).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getMatchedCount() == 0) {
				event.replyFailure("I could not find that notification").queue();
				return;
			}

			if (result.getModifiedCount() == 0) {
				event.replyFailure("Your twitch stream types were already set to that for that notification").queue();
				return;
			}

			event.replySuccess("Your twitch stream types has been updated for that notification").queue();
		});
	}

}
