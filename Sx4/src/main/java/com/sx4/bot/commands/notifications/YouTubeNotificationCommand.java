package com.sx4.bot.commands.notifications;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndDeleteOptions;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.sx4.bot.annotations.argument.AdvancedMessage;
import com.sx4.bot.annotations.argument.ImageUrl;
import com.sx4.bot.annotations.command.AuthorPermissions;
import com.sx4.bot.annotations.command.BotPermissions;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.managers.YouTubeManager;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.paged.PagedResult.SelectType;
import com.sx4.bot.utility.ExceptionUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.TextChannel;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletionException;

public class YouTubeNotificationCommand extends Sx4Command {
	
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
	@Examples({"youtube notification add videos mrbeast", "youtube notification add #videos pewdiepie"})
	public void add(Sx4CommandEvent event, @Argument(value="channel") TextChannel channel, @Argument(value="youtube channel", endless=true) String youtubeChannelArgument) {
		Request channelRequest = new Request.Builder()
			.url("https://www.googleapis.com/youtube/v3/search?key=" + event.getConfig().getYoutube() + "&q=" + URLEncoder.encode(youtubeChannelArgument, StandardCharsets.UTF_8) + "&part=id&type=channel&maxResults=1")
			.build();
		
		event.getHttpClient().newCall(channelRequest).enqueue((HttpCallback) channelResponse -> {
			Document json = Document.parse(channelResponse.body().string());
			
			List<Document> items = json.getList("items", Document.class);
			if (items.isEmpty()) {
				event.replyFailure("I could not find that youtube channel").queue();
				return;
			}
			
			String channelId = items.get(0).getEmbedded(List.of("id", "channelId"), String.class);
			
			Document notificationData = new Document("uploaderId", channelId)
				.append("channelId", channel.getIdLong())
				.append("guildId", event.getGuild().getIdLong());
			
			if (!event.getBot().getYouTubeManager().hasExecutor(channelId)) {
				RequestBody body = new MultipartBody.Builder()
					.addFormDataPart("hub.mode", "subscribe")
					.addFormDataPart("hub.topic", "https://www.youtube.com/xml/feeds/videos.xml?channel_id=" + channelId)
					.addFormDataPart("hub.callback", event.getConfig().getBaseUrl() + "/api/youtube")
					.addFormDataPart("hub.verify", "sync")
					.addFormDataPart("hub.verify_token", event.getConfig().getYoutube())
					.setType(MultipartBody.FORM)
					.build();
				
				Request request = new Request.Builder()
					.url("https://pubsubhubbub.appspot.com/subscribe")
					.post(body)
					.build();
				
				event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
					if (response.isSuccessful()) {
						event.getDatabase().insertYouTubeNotification(notificationData).whenComplete((result, exception) -> {
							if (ExceptionUtility.sendExceptionally(event, exception)) {
								return;
							}

							event.replyFormat("Notifications will now be sent in %s when that user uploads with id `%s` %s", channel.getAsMention(), result.getInsertedId().asObjectId().getValue().toHexString(), event.getConfig().getSuccessEmote()).queue();
						});
					} else {
						event.replyFailure("Oops something went wrong there, try again. If this repeats report this to my developer (Message: " + response.body().string() + ")").queue();
					}
				});
			} else {
				event.getDatabase().insertYouTubeNotification(notificationData).whenComplete((result, exception) -> {
					Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
					if (cause instanceof MongoWriteException && ((MongoWriteException) cause).getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
						event.replyFailure("You already have a notification setup for that youtube channel in " + channel.getAsMention()).queue();
						return;
					}

					if (ExceptionUtility.sendExceptionally(event, exception)) {
						return;
					}
					
					event.replyFormat("Notifications will now be sent in %s when that user uploads with id `%s` %s", channel.getAsMention(), result.getInsertedId().asObjectId().getValue().toHexString(), event.getConfig().getSuccessEmote()).queue();
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
		event.getDatabase().findAndDeleteYouTubeNotificationById(id, options).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
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
		event.getDatabase().updateYouTubeNotificationById(id, Updates.set("message.content", message)).whenComplete((result, exception) -> {
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
		event.getDatabase().updateYouTubeNotificationById(id, Updates.set("message", json)).whenComplete((result, exception) -> {
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
		event.getDatabase().updateYouTubeNotificationById(id, Updates.set("webhook.name", name)).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getModifiedCount() == 0) {
				event.replyFailure("Your webhook name for that notification was already set to that").queue();
				return;
			}
			
			event.replySuccess("Your webhook name has been updated for that notification").queue();
		});
	}
	
	@Command(value="avatar", description="Set the avatar of the webhook that sends youtube notifications for a specific notification")
	@CommandId(163)
	@Examples({"youtube notification avatar 5e45ce6d3688b30ee75201ae Shea#6653", "youtube notification avatar 5e45ce6d3688b30ee75201ae https://i.imgur.com/i87lyNO.png"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void avatar(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="avatar", endless=true, acceptEmpty=true) @ImageUrl String url) {
		event.getDatabase().updateYouTubeNotificationById(id, Updates.set("webhook.avatar", url)).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getModifiedCount() == 0) {
				event.replyFailure("Your webhook avatar for that notification was already set to that").queue();
				return;
			}

			event.replySuccess("Your webhook avatar has been updated for that notification").queue();
		});
	}
	
	@Command(value="formatting", aliases={"format", "formats"}, description="View the formats you are able to use to customize your notifications message", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@CommandId(164)
	@Examples({"youtube notification formatting"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void formatting(Sx4CommandEvent event) {
		String example = String.format("{channel.name} - The youtube channels name\n"
			+ "{channel.id} - The youtube channels id\n"
			+ "{channel.url} - The youtube channels url\n"
			+ "{video.title} - The youtube videos current title\n"
			+ "{video.id} - The youtube videos id\n"
			+ "{video.url} - The youtube videos url\n"
			+ "{video.thumbnail} - The youtube videos thumbnail\n"
			+ "{video.published} - The youtube date time of when it was uploaded, (10 December 2019 15:30)\n\n"
			+ "Make sure to keep the **{}** brackets when using the formatting\n"
			+ "Example: `%syoutube notification message #videos pewdiepie **{channel.name}** just uploaded, check it out: {video.url}`", event.getPrefix());
		
		EmbedBuilder embed = new EmbedBuilder()
			.setAuthor("YouTube Notification Formatting", null, event.getGuild().getIconUrl())
			.setDescription(example);
		
		event.reply(embed.build()).queue();
	}
	
	@Command(value="list", description="View all the notifications you have setup throughout your server")
	@CommandId(165)
	@Examples({"youtube notification list"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void list(Sx4CommandEvent event) {
		List<Document> notifications = event.getDatabase().getYouTubeNotifications(Filters.eq("guildId", event.getGuild().getIdLong()), Projections.include("uploaderId", "channelId")).into(new ArrayList<>());
		if (notifications.isEmpty()) {
			event.replyFailure("You have no notifications setup in this server").queue();
			return;
		}

		notifications.sort(Comparator.comparingLong(a -> a.getLong("channelId")));
		
		PagedResult<Document> paged = new PagedResult<>(event.getBot(), notifications)
			.setIncreasedIndex(true)
			.setAutoSelect(false)
			.setAuthor("YouTube Notifications", null, event.getGuild().getIconUrl())
			.setDisplayFunction(data -> String.format("<#%d> - [%s](https://youtube.com/channel/%s)", data.getLong("channelId"), data.getObjectId("_id").toHexString(), data.getString("uploaderId")))
			.setSelect(SelectType.INDEX);
		
		paged.onSelect(selected -> this.sendStats(event, selected.getSelected()));
		
		paged.execute(event);
	}
	
	public void sendStats(Sx4CommandEvent event, Document notification) {
		ObjectId id = notification.getObjectId("_id");
		
		EmbedBuilder embed = new EmbedBuilder()
			.setAuthor("YouTube Notification Stats", null, event.getGuild().getIconUrl())
			.addField("Id", id.toHexString(), true)
			.addField("YouTube Channel", String.format("[%s](https://youtube.com/channel/%<s)", notification.getString("uploaderId")), true)
			.addField("Channel", "<#" + notification.getLong("channelId") + ">", true)
			.addField("Message", "`" + notification.getEmbedded(List.of("message", "content"), YouTubeManager.DEFAULT_MESSAGE) + "`", false)
			.setFooter("Created at")
			.setTimestamp(Instant.ofEpochSecond(id.getTimestamp()));
		
		event.reply(embed.build()).queue();
	}
	
	@Command(value="stats", aliases={"settings", "setting"}, description="View the settings for a specific notification")
	@CommandId(166)
	@Examples({"youtube notification stats 5e45ce6d3688b30ee75201ae"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void stats(Sx4CommandEvent event, @Argument(value="id") ObjectId id) {
		Document notification = event.getDatabase().getYouTubeNotification(Filters.eq("_id", id), Projections.include("uploaderId", "channelId", "message.content"));
		if (notification == null) {
			event.replyFailure("You don't have a notification with that id").queue();
			return;
		}
		
		this.sendStats(event, notification);
	}
	
}
