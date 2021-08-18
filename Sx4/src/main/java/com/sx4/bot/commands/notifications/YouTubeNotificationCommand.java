package com.sx4.bot.commands.notifications;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.*;
import com.sx4.bot.annotations.argument.AdvancedMessage;
import com.sx4.bot.annotations.argument.ImageUrl;
import com.sx4.bot.annotations.command.AuthorPermissions;
import com.sx4.bot.annotations.command.BotPermissions;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.youtube.YouTubeChannel;
import com.sx4.bot.entities.youtube.YouTubeVideo;
import com.sx4.bot.formatter.FormatterManager;
import com.sx4.bot.formatter.JsonFormatter;
import com.sx4.bot.formatter.function.FormatterVariable;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.managers.YouTubeManager;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.paged.PagedResult.SelectType;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.FutureUtility;
import com.sx4.bot.utility.MessageUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.TextChannel;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.XML;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class YouTubeNotificationCommand extends Sx4Command {

	private final Pattern url = Pattern.compile("^https?://(?:www\\.)?youtube\\.com/(?:(user|channel)/)?([\\w-]+)/?$");
	private final Pattern id = Pattern.compile("^UC[\\w-]{21}[AQgw]$");
	
	public YouTubeNotificationCommand() {
		super("youtube notification", 157);
		
		super.setDescription("Subscribe to a youtube channel so anytime it uploads it's sent in a channel of your choice");
		super.setAliases("yt notif", "yt notification", "youtube notif");
		super.setExamples("youtube notification add", "youtube notification remove", "youtube notification list");
		super.setCategoryAll(ModuleCategory.NOTIFICATIONS);
	}
	
	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}
	
	@Command(value="add", description="Add a youtube notification to be posted to a specific channel when the user uploads")
	@CommandId(158)
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	@Examples({"youtube notification add videos mrbeast", "youtube notification add mrbeast", "youtube notification add #videos pewdiepie"})
	public void add(Sx4CommandEvent event, @Argument(value="channel", nullDefault=true) TextChannel channel, @Argument(value="youtube channel", endless=true) String channelQuery) {
		TextChannel effectiveChannel = channel == null ? event.getTextChannel() : channel;

		boolean id = this.id.matcher(channelQuery).matches();
		boolean search;
		String queryName, query;

		Matcher matcher = this.url.matcher(channelQuery);
		if (matcher.matches()) {
			String path = matcher.group(1);

			search = false;
			queryName = path == null || path.equals("user") ? "forUsername" : "id";
			query = matcher.group(2);
		} else {
			search = !id;
			queryName = id ? "id" : "q";
			query = channelQuery;
		}

		Request channelRequest = new Request.Builder()
			.url("https://www.googleapis.com/youtube/v3/" + (search ? "search" : "channels") + "?key=" + event.getConfig().getYouTube() + "&" + queryName + "=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&part=snippet&type=channel&maxResults=1")
			.build();
		
		event.getHttpClient().newCall(channelRequest).enqueue((HttpCallback) channelResponse -> {
			Document json = Document.parse(channelResponse.body().string());
			
			List<Document> items = json.getList("items", Document.class, Collections.emptyList());
			if (items.isEmpty()) {
				event.replyFailure("I could not find that youtube channel").queue();
				return;
			}

			Document item = items.get(0);
			String channelId = search ? item.getEmbedded(List.of("id", "channelId"), String.class) : item.getString("id");
			
			Document notificationData = new Document("uploaderId", channelId)
				.append("channelId", effectiveChannel.getIdLong())
				.append("guildId", event.getGuild().getIdLong());
			
			if (!event.getBot().getYouTubeManager().hasExecutor(channelId)) {
				RequestBody body = new MultipartBody.Builder()
					.addFormDataPart("hub.mode", "subscribe")
					.addFormDataPart("hub.topic", "https://www.youtube.com/xml/feeds/videos.xml?channel_id=" + channelId)
					.addFormDataPart("hub.callback", event.getConfig().getBaseUrl() + "/api/youtube")
					.addFormDataPart("hub.verify", "sync")
					.addFormDataPart("hub.verify_token", event.getConfig().getYouTube())
					.setType(MultipartBody.FORM)
					.build();
				
				Request request = new Request.Builder()
					.url("https://pubsubhubbub.appspot.com/subscribe")
					.post(body)
					.build();
				
				event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
					if (response.isSuccessful()) {
						event.getMongo().insertYouTubeNotification(notificationData).whenComplete((result, exception) -> {
							Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
							if (cause instanceof MongoWriteException && ((MongoWriteException) cause).getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
								event.replyFailure("You already have a notification setup for that youtube channel in " + effectiveChannel.getAsMention()).queue();
								return;
							}

							if (ExceptionUtility.sendExceptionally(event, exception)) {
								return;
							}

							event.replyFormat("Notifications will now be sent in %s when **%s** uploads with id `%s` %s", effectiveChannel.getAsMention(), item.getEmbedded(List.of("snippet", "title"), String.class), result.getInsertedId().asObjectId().getValue().toHexString(), event.getConfig().getSuccessEmote()).queue();
						});
					} else {
						event.replyFailure("Oops something went wrong there, try again. If this repeats report this to my developer (Message: " + response.body().string() + ")").queue();
					}
				});
			} else {
				event.getMongo().insertYouTubeNotification(notificationData).whenComplete((result, exception) -> {
					Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
					if (cause instanceof MongoWriteException && ((MongoWriteException) cause).getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
						event.replyFailure("You already have a notification setup for that youtube channel in " + effectiveChannel.getAsMention()).queue();
						return;
					}

					if (ExceptionUtility.sendExceptionally(event, exception)) {
						return;
					}

					event.replyFormat("Notifications will now be sent in %s when **%s** uploads with id `%s` %s", effectiveChannel.getAsMention(), item.getEmbedded(List.of("snippet", "title"), String.class), result.getInsertedId().asObjectId().getValue().toHexString(), event.getConfig().getSuccessEmote()).queue();
				});
			}
		});
	}
	
	@Command(value="remove", description="Removes a notification from a channel you had setup prior for a youtube channel")
	@CommandId(159)
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	@Examples({"youtube notification remove 5e45ce6d3688b30ee75201ae"})
	public void remove(Sx4CommandEvent event, @Argument(value="id") ObjectId id) {
		FindOneAndDeleteOptions options = new FindOneAndDeleteOptions().projection(Projections.include("channelId"));
		event.getMongo().findAndDeleteYouTubeNotification(Filters.and(Filters.eq("_id", id), Filters.eq("guildId", event.getGuild().getIdLong())), options).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (data == null) {
				event.replyFailure("I could not find that notification").queue();
				return;
			}
			
			event.replySuccess("You will no longer receive notifications in <#" + data.getLong("channelId") + "> for that user").queue();
		});
	}
	
	@Command(value="message", description="Set the message you want to be sent for your specific notification, view the formatters for messages in `youtube notification formatting`")
	@CommandId(160)
	@Examples({"youtube notification message 5e45ce6d3688b30ee75201ae {video.url}", "youtube notification message 5e45ce6d3688b30ee75201ae **{channel.name}** just uploaded, check it out: {video.url}"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void message(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="message", endless=true) String message) {
		event.getMongo().updateYouTubeNotification(Filters.and(Filters.eq("_id", id), Filters.eq("guildId", event.getGuild().getIdLong())), Updates.set("message", new Document("content", message)), new UpdateOptions()).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getModifiedCount() == 0) {
				event.replyFailure("Your message for that notification was already set to that").queue();
				return;
			}

			event.replySuccess("Your message has been updated for that notification").queue();
		});
	}

	@Command(value="advanced message", description="Same as `youtube notification message` but takes json for more advanced options")
	@CommandId(161)
	@Examples({"youtube notification advanced message 5e45ce6d3688b30ee75201ae {\"content\": \"{video.url}\"}", "youtube notification advanced message 5e45ce6d3688b30ee75201ae {\"embed\": {\"description\": \"{video.url}\"}}"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void advancedMessage(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="json", endless=true) @AdvancedMessage Document json) {
		event.getMongo().updateYouTubeNotification(Filters.and(Filters.eq("_id", id), Filters.eq("guildId", event.getGuild().getIdLong())), Updates.set("message", json), new UpdateOptions()).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getModifiedCount() == 0) {
				event.replyFailure("Your message for that notification was already set to that").queue();
				return;
			}

			event.replySuccess("Your message has been updated for that notification").queue();
		});
	}
	
	@Command(value="name", description="Set the name of the webhook that sends youtube notifications for a specific notification")
	@CommandId(162)
	@Examples({"youtube notification name 5e45ce6d3688b30ee75201ae YouTube", "youtube notification name 5e45ce6d3688b30ee75201ae Pewdiepie's Minion"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void name(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="name", endless=true) String name) {
		event.getMongo().updateYouTubeNotification(Filters.and(Filters.eq("_id", id), Filters.eq("guildId", event.getGuild().getIdLong())), Updates.set("webhook.name", name), new UpdateOptions()).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getModifiedCount() == 0) {
				event.replyFailure("Your webhook name for that notification was already set to that").queue();
				return;
			}
			
			event.replySuccess("Your webhook name has been updated for that notification, this only works with premium <https://patreon.com/Sx4>").queue();
		});
	}
	
	@Command(value="avatar", description="Set the avatar of the webhook that sends youtube notifications for a specific notification")
	@CommandId(163)
	@Examples({"youtube notification avatar 5e45ce6d3688b30ee75201ae Shea#6653", "youtube notification avatar 5e45ce6d3688b30ee75201ae https://i.imgur.com/i87lyNO.png"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void avatar(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="avatar", endless=true, acceptEmpty=true) @ImageUrl String url) {
		event.getMongo().updateYouTubeNotification(Filters.and(Filters.eq("_id", id), Filters.eq("guildId", event.getGuild().getIdLong())), Updates.set("webhook.avatar", url), new UpdateOptions()).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getModifiedCount() == 0) {
				event.replyFailure("Your webhook avatar for that notification was already set to that").queue();
				return;
			}

			event.replySuccess("Your webhook avatar has been updated for that notification, this only works with premium <https://patreon.com/Sx4>").queue();
		});
	}

	@Command(value="preview", description="Previews your YouTube notification message")
	@CommandId(465)
	@Examples({"youtube notification preview 5e45ce6d3688b30ee75201ae"})
	public void preview(Sx4CommandEvent event, @Argument(value="id") ObjectId id) {
		Document data = event.getMongo().getYouTubeNotification(Filters.and(Filters.eq("_id", id), Filters.eq("guildId", event.getGuild().getIdLong())), Projections.include("uploaderId", "message"));
		if (data == null) {
			event.replyFailure("I could not find that notification").queue();
			return;
		}

		String channelId = data.getString("uploaderId");

		Request request = new Request.Builder()
			.url("https://www.youtube.com/feeds/videos.xml?channel_id=" + channelId)
			.build();

		event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
			if (response.code() == 404) {
				event.replyFailure("The YouTube channel for this notification no longer exists").queue();
				return;
			}

			JSONObject channel = XML.toJSONObject(response.body().string());
			JSONObject feed = channel.getJSONObject("feed");

			JSONArray entries = feed.getJSONArray("entry");
			YouTubeVideo video;
			if (entries.isEmpty()) {
				OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
				video = new YouTubeVideo("dQw4w9WgXcQ", "This channel had no uploads", now, now);
			} else {
				JSONObject entry = entries.getJSONObject(0);
				video = new YouTubeVideo(entry.getString("yt:videoId"), entry.getString("title"), entry.getString("updated"), entry.getString("published"));
			}

			String channelName = feed.getString("title");

			Document message =  new JsonFormatter(data.get("message", YouTubeManager.DEFAULT_MESSAGE))
				.addVariable("video", video)
				.addVariable("channel", new YouTubeChannel(channelId, channelName))
				.parse();

			try {
				MessageUtility.fromWebhookMessage(event.getTextChannel(), MessageUtility.fromJson(message).build()).queue();
			} catch (IllegalArgumentException e) {
				event.replyFailure(e.getMessage()).queue();
			}
		});
	}
	
	@Command(value="list", description="View all the notifications you have setup throughout your server")
	@CommandId(165)
	@Examples({"youtube notification list"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void list(Sx4CommandEvent event) {
		List<Document> notifications = event.getMongo().getYouTubeNotifications(Filters.eq("guildId", event.getGuild().getIdLong()), Projections.include("uploaderId")).into(new ArrayList<>());
		if (notifications.isEmpty()) {
			event.replyFailure("You have no notifications setup in this server").queue();
			return;
		}

		int size = notifications.size();

		List<CompletableFuture<Map<String, String>>> futures = new ArrayList<>();
		for (int i = 0; i < Math.ceil(size / 50D); i++) {
			List<Document> splitNotifications = notifications.subList(i * 50, Math.min((i + 1) * 50, size));
			String ids = splitNotifications.stream().map(d -> d.getString("uploaderId")).collect(Collectors.joining(","));

			Request request = new Request.Builder()
				.url("https://www.googleapis.com/youtube/v3/channels?key=" + event.getConfig().getYouTube() + "&id=" + ids + "&part=snippet&maxResults=50")
				.build();

			CompletableFuture<Map<String, String>> future = new CompletableFuture<>();
			event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
				Document data = Document.parse(response.body().string());

				List<Document> items = data.getList("items", Document.class);

				Map<String, String> names = new HashMap<>();
				for (Document item : items) {
					names.put(item.getString("id"), item.getEmbedded(List.of("snippet", "title"), String.class));
				}

				future.complete(names);
			});

			futures.add(future);
		}

		FutureUtility.allOf(futures).whenComplete((maps, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			Map<String, String> names = new HashMap<>();
			for (Map<String, String> map : maps) {
				names.putAll(map);
			}

			PagedResult<Document> paged = new PagedResult<>(event.getBot(), notifications)
				.setIncreasedIndex(true)
				.setAutoSelect(false)
				.setAuthor("YouTube Notifications", null, event.getGuild().getIconUrl())
				.setDisplayFunction(data -> {
					String uploaderId = data.getString("uploaderId");
					return String.format("%s - [%s](https://youtube.com/channel/%s)", data.getObjectId("_id").toHexString(), names.getOrDefault(uploaderId, "Unknown"), uploaderId);
				})
				.setSelect(SelectType.INDEX);

			paged.onSelect(selected -> this.sendStats(event, selected.getSelected()));

			paged.execute(event);
		});
	}
	
	public void sendStats(Sx4CommandEvent event, Document notification) {
		ObjectId id = notification.getObjectId("_id");
		
		EmbedBuilder embed = new EmbedBuilder()
			.setAuthor("YouTube Notification Stats", null, event.getGuild().getIconUrl())
			.addField("Id", id.toHexString(), true)
			.addField("YouTube Channel", String.format("[%s](https://youtube.com/channel/%<s)", notification.getString("uploaderId")), true)
			.addField("Channel", "<#" + notification.getLong("channelId") + ">", true)
			.addField("Message", "`" + notification.getEmbedded(List.of("message", "content"), YouTubeManager.DEFAULT_MESSAGE.getString("content")) + "`", false)
			.setFooter("Created at")
			.setTimestamp(Instant.ofEpochSecond(id.getTimestamp()));
		
		event.reply(embed.build()).queue();
	}

	@Command(value="formatters", aliases={"format", "formatting"}, description="Get all the formatters for YouTube notifications you can use")
	@CommandId(445)
	@Examples({"youtube notification formatters"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void formatters(Sx4CommandEvent event) {
		EmbedBuilder embed = new EmbedBuilder()
			.setAuthor("YouTube Notification Formatters", null, event.getSelfUser().getEffectiveAvatarUrl());

		FormatterManager manager = FormatterManager.getDefaultManager();

		StringJoiner content = new StringJoiner("\n");
		for (FormatterVariable<?> variable : manager.getVariables(YouTubeVideo.class)) {
			content.add("`{video." + variable.getName() + "}` - " + variable.getDescription());
		}

		for (FormatterVariable<?> variable : manager.getVariables(YouTubeChannel.class)) {
			content.add("`{channel." + variable.getName() + "}` - " + variable.getDescription());
		}

		embed.setDescription(content.toString());

		event.reply(embed.build()).queue();
	}
	
	@Command(value="stats", aliases={"settings", "setting"}, description="View the settings for a specific notification")
	@CommandId(166)
	@Examples({"youtube notification stats 5e45ce6d3688b30ee75201ae"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void stats(Sx4CommandEvent event, @Argument(value="id") ObjectId id) {
		Document notification = event.getMongo().getYouTubeNotification(Filters.eq("_id", id), Projections.include("uploaderId", "channelId", "message.content"));
		if (notification == null) {
			event.replyFailure("You don't have a notification with that id").queue();
			return;
		}
		
		this.sendStats(event, notification);
	}
	
}
