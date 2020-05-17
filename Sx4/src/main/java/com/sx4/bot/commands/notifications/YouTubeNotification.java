package com.sx4.bot.commands.notifications;

import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionException;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.json.JSONArray;
import org.json.JSONObject;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.command.Command.AuthorPermissions;
import com.jockie.bot.core.command.Command.BotPermissions;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.Category;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.model.Operators;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.managers.YouTubeManager;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.paged.PagedResult.SelectType;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.HelpUtility;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.TextChannel;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;

public class YouTubeNotification extends Sx4Command {
	
	public YouTubeNotification() {
		super("youtube notification");
		
		super.setDescription("Subscribe to a youtube channel so anytime it uploads it's sent in a channel of your choice");
		super.setAliases("yt notif", "yt notification", "youtube notif");
		super.setExamples("youtube notification add", "youtube notification remove", "youtube notification list");
		super.setCategory(Category.NOTIFICATIONS);
	}
	
	public void onCommand(Sx4CommandEvent event) {
		event.reply(HelpUtility.getHelpMessage(event.getCommand(), event.getSelfMember().hasPermission(event.getTextChannel(), Permission.MESSAGE_EMBED_LINKS))).queue();
	}
	
	@Command(value="add", description="Add a youtube notification to be posted to a specific channel when the user uploads")
	@AuthorPermissions({Permission.MANAGE_SERVER})
	@Examples({"youtube notification add videos mrbeast", "youtube notification add #videos pewdiepie"})
	public void add(Sx4CommandEvent event, @Argument(value="channel") TextChannel channel, @Argument(value="youtube channel", endless=true) String youtubeChannelArgument) {
		Request channelRequest = new Request.Builder()
				.url("https://www.googleapis.com/youtube/v3/search?key=" + this.config.getYoutube() + "&q=" + URLEncoder.encode(youtubeChannelArgument, StandardCharsets.UTF_8) + "&part=id&type=channel&maxResults=50")
				.build();
		
		this.client.newCall(channelRequest).enqueue((HttpCallback) channelResponse -> {
			JSONObject json = new JSONObject(channelResponse.body().string());
			
			JSONArray items = json.getJSONArray("items");
			if (items.isEmpty()) {
				event.reply("I could not find that youtube channel :no_entry:").queue();
				return;
			}
			
			String channelId = items.getJSONObject(0).getJSONObject("id").getString("channelId");
				
			ObjectId id = ObjectId.get();
			
			Document notificationData = new Document("uploaderId", channelId)
					.append("channelId", channel.getIdLong())
					.append("id", id);
			
			if (!this.youtubeManager.hasExecutor(channelId)) {
				RequestBody body = new MultipartBody.Builder()
						.addFormDataPart("hub.mode", "subscribe")
						.addFormDataPart("hub.topic", "https://www.youtube.com/xml/feeds/videos.xml?channel_id=" + channelId)
						.addFormDataPart("hub.callback", "http://" + this.config.getDomain() + ":" + this.config.getPort() + "/api/v1/youtube")
						.addFormDataPart("hub.verify", "sync")
						.addFormDataPart("hub.verify_token", this.config.getYoutube())
						.setType(MultipartBody.FORM)
						.build();
				
				Request request = new Request.Builder()
						.url("https://pubsubhubbub.appspot.com/subscribe")
						.post(body)
						.build();
				
				this.client.newCall(request).enqueue((HttpCallback) response -> {
					if (response.isSuccessful()) {
						this.database.updateGuildById(event.getGuild().getIdLong(), Updates.push("youtube.notifications", notificationData)).whenComplete((result, exception) -> {
							if (exception != null) {
								ExceptionUtility.sendExceptionally(event, exception);
							} else {
								event.reply("Notifications will now be sent in " + channel.getAsMention() + " when that user uploads with id `" + id.toHexString() + "` <:done:403285928233402378>").queue();
							}
						});
					} else {
						event.reply("Oops something went wrong there, try again. If this repeats report this to my developer (Message: " + response.body().string() + ") :no_entry:").queue();
					}
				});
			} else {
				List<Bson> update = List.of(Operators.set("youtube.notifications", Operators.cond(Operators.or(Operators.extinct("$youtube.notifications"), Operators.eq(Operators.filter("$youtube.notifications", Operators.and(Operators.eq("$$this.uploaderId", channelId), Operators.eq("$$this.channelId", channel.getIdLong()))), List.of())), Operators.cond(Operators.exists("$youtube.notifications"), Operators.concatArrays("$youtube.notifications", List.of(notificationData)), List.of(notificationData)), "$youtube.notifications")));
				this.database.updateGuildById(event.getGuild().getIdLong(), update).whenComplete((result, exception) -> {
					if (exception != null) {
						ExceptionUtility.sendExceptionally(event, exception);
					} else {
						if (result.getModifiedCount() == 0) {
							event.reply("You already have a notification for that user in that channel :no_entry:").queue();
							return;
						}
						
						event.reply("Notifications will now be sent in " + channel.getAsMention() + " when that user uploads with id `" + id.toHexString() + "` <:done:403285928233402378>").queue();
					}
				});
			}
		});
	}
	
	@Command(value="remove", description="Removes a notification from a channel you had setup prior for a youtube channel")
	@AuthorPermissions({Permission.MANAGE_SERVER})
	@Examples({"youtube notification remove 5e45ce6d3688b30ee75201ae"})
	public void remove(Sx4CommandEvent event, @Argument(value="id") ObjectId id) {
		Bson projection = Projections.include("youtube.notifications.webhookId", "youtube.notifications.channelId", "youtube.notifications.id");
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(projection);
		this.database.findAndUpdateGuildById(event.getGuild().getIdLong(), Updates.pull("youtube.notifications", Filters.eq("id", id)), options).whenComplete((data, exception) -> {
			if (exception != null) {
				ExceptionUtility.sendExceptionally(event, exception);
			} else {
				List<Document> notifications = data.getEmbedded(List.of("youtube", "notifications"), Collections.emptyList());
				
				Document notification = notifications.stream()
					.filter(d -> d.getObjectId("id").equals(id))
					.findFirst()
					.orElse(null);
				
				if (notification == null) {
					event.reply("You don't have a notification with that id :no_entry:").queue();
					return;
				}
				
				long webhookId = notification.get("webhookId", 0L);
				long channelId = notification.getLong("channelId");
				
				long amount = notifications.stream()
					.filter(d -> d.getLong("channelId") == channelId)
					.count();
				
				TextChannel channel = event.getGuild().getTextChannelById(channelId);
				if (amount == 1 && channel != null && webhookId != 0L) {
					channel.deleteWebhookById(String.valueOf(webhookId)).queue();
				}
				
				event.reply("You will no longer receive notifications in <#" + channelId + "> for that user <:done:403285928233402378>").queue();
			}
		});
	}
	
	@Command(value="message", description="Set the message you want to be sent for your specific notification, view the formatters for messages in `youtube notification formatting`")
	@Examples({"youtube notification message 5e45ce6d3688b30ee75201ae {video.url}", "youtube notification message 5e45ce6d3688b30ee75201ae **{channel.name}** just uploaded, check it out: {video.url}"})
	@AuthorPermissions({Permission.MANAGE_SERVER})
	public void message(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="message", endless=true) String message) {
		Bson projection = Projections.include("youtube.notifications.id", "youtube.notifications.message");
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(projection).arrayFilters(List.of(Filters.eq("notification.id", id)));
		this.database.findAndUpdateGuildById(event.getGuild().getIdLong(), Updates.set("youtube.notifications.$[notification].message", message), options).whenComplete((data, exception) -> {
			if (exception != null) {
				if (exception instanceof CompletionException) {
					Throwable cause = ((CompletionException) exception).getCause();
					if (cause instanceof MongoWriteException && ((MongoWriteException) cause).getCode() == 2) {
						event.reply("You don't have a notification with that id :no_entry:").queue();
						return;
					}
				}
				
				ExceptionUtility.sendExceptionally(event, exception);
			} else {
				List<Document> notifications = data.getEmbedded(List.of("youtube", "notifications"), Collections.emptyList());
				
				Document notification = notifications.stream()
					.filter(d -> d.getObjectId("id").equals(id))
					.findFirst()
					.orElse(null);
				
				if (notification == null) {
					event.reply("You don't have a notification with that id :no_entry:").queue();
					return;
				}
				
				String oldMessage = notification.getString("message");
				if (oldMessage != null && oldMessage.equals(message)) {
					event.reply("Your message for that notification was already set to that :no_entry:").queue();
					return;
				}
				
				event.reply("Your message has been updated for that notification <:done:403285928233402378>").queue();
			}
		});
	}
	
	@Command(value="name", description="Set the name of the webhook that sends youtube notifications for a specific notification")
	@Examples({"youtube notification name 5e45ce6d3688b30ee75201ae YouTube", "youtube notification name 5e45ce6d3688b30ee75201ae Pewdiepie's Minion"})
	@AuthorPermissions({Permission.MANAGE_SERVER})
	public void name(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="name", endless=true) String name) {
		Bson projection = Projections.include("youtube.notifications.id", "youtube.notifications.name");
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(projection).arrayFilters(List.of(Filters.eq("notification.id", id)));
		this.database.findAndUpdateGuildById(event.getGuild().getIdLong(), Updates.set("youtube.notifications.$[notification].name", name), options).whenComplete((data, exception) -> {
			if (exception != null) {
				if (exception instanceof CompletionException) {
					Throwable cause = ((CompletionException) exception).getCause();
					if (cause instanceof MongoWriteException && ((MongoWriteException) cause).getCode() == 2) {
						event.reply("You don't have a notification with that id :no_entry:").queue();
						return;
					}
				}
				
				ExceptionUtility.sendExceptionally(event, exception);
			} else {
				List<Document> notifications = data.getEmbedded(List.of("youtube", "notifications"), Collections.emptyList());
				
				Document notification = notifications.stream()
					.filter(d -> d.getObjectId("id").equals(id))
					.findFirst()
					.orElse(null);
				
				if (notification == null) {
					event.reply("You don't have a notification with that id :no_entry:").queue();
					return;
				}
				
				String oldName = notification.getString("name");
				if (oldName != null && oldName.equals(name)) {
					event.reply("Your webhook name for that notification was already set to that :no_entry:").queue();
					return;
				}
				
				event.reply("Your webhook name has been updated for that notification <:done:403285928233402378>").queue();
			}
		});
	}
	
	@Command(value="avatar", description="Set the avatar of the webhook that sends youtube notifications for a specific notification")
	@Examples({"youtube notification avatar 5e45ce6d3688b30ee75201ae", "youtube notification avatar 5e45ce6d3688b30ee75201ae https://i.imgur.com/i87lyNO.png"})
	@AuthorPermissions({Permission.MANAGE_SERVER})
	public void avatar(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="avatar", endless=true, acceptEmpty=true) URL avatar) {
		String url = avatar.toString();
		
		Bson projection = Projections.include("youtube.notifications.id", "youtube.notifications.avatar");
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(projection).arrayFilters(List.of(Filters.eq("notification.id", id)));
		this.database.findAndUpdateGuildById(event.getGuild().getIdLong(), Updates.set("youtube.notifications.$[notification].avatar", url), options).whenComplete((data, exception) -> {
			if (exception != null) {
				if (exception instanceof CompletionException) {
					Throwable cause = ((CompletionException) exception).getCause();
					if (cause instanceof MongoWriteException && ((MongoWriteException) cause).getCode() == 2) {
						event.reply("You don't have a notification with that id :no_entry:").queue();
						return;
					}
				}
				
				ExceptionUtility.sendExceptionally(event, exception);
			} else {
				List<Document> notifications = data.getEmbedded(List.of("youtube", "notifications"), Collections.emptyList());
				
				Document notification = notifications.stream()
					.filter(d -> d.getObjectId("id").equals(id))
					.findFirst()
					.orElse(null);
				
				if (notification == null) {
					event.reply("You don't have a notification with that id :no_entry:").queue();
					return;
				}
				
				String oldAvatar = notification.getString("avatar");
				if (oldAvatar != null && oldAvatar.equals(url)) {
					event.reply("Your webhook avatar for that notification was already set to that :no_entry:").queue();
					return;
				}
				
				event.reply("Your webhook avatar has been updated for that notification <:done:403285928233402378>").queue();
			}
		});
	}
	
	@Command(value="formatting", aliases={"format", "formats"}, description="View the formats you are able to use to customize your notifications message", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"youtube notification formatting"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void formatting(Sx4CommandEvent event) {
		String example = String.format("{channel.name} - The youtube channels name\n"
				+ "{channel.id} - The youtube channels id\n"
				+ "{channel.url} - The youtube channels url\n"
				+ "{video.title} - The youtube videos current title\n"
				+ "{video.id} - The youtube videos id\n"
				+ "{video.url} - The youtube videos url\n"
				+ "{video.published} - The youtube date time of when it was uploaded, (10 December 2019 15:30)\n\n"
				+ "Make sure to keep the **{}** brackets when using the formatting\n"
				+ "Example: `%syoutube notification message #videos pewdiepie **{channel.name}** just uploaded, check it out: {video.url}`", event.getPrefix());
		
		EmbedBuilder embed = new EmbedBuilder()
				.setAuthor("YouTube Notification Formatting", null, event.getGuild().getIconUrl())
				.setDescription(example)
				.setColor(this.config.getColour());
		
		event.reply(embed.build()).queue();
	}
	
	@Command(value="list", description="View all the notifications you have setup throughout your server")
	@Examples({"youtube notification list"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void list(Sx4CommandEvent event) {
		List<Document> notifications = this.database.getGuildById(event.getGuild().getIdLong(), Projections.include("youtube.notifications.uploaderId", "youtube.notifications.channelId", "youtube.notifications.id")).getEmbedded(List.of("youtube", "notifications"), Collections.emptyList());
		notifications.sort((a, b) -> Long.compare(a.getLong("channelId"), b.getLong("channelId")));
		
		if (notifications.isEmpty()) {
			event.reply("You have no notifications setup in this server :no_entry:").queue();
			return;
		}
		
		PagedResult<Document> paged = new PagedResult<>(notifications)
				.setIncreasedIndex(true)
				.setAutoSelect(false)
				.setAuthor("YouTube Notifications", null, event.getGuild().getIconUrl())
				.setDisplayFunction(data -> String.format("<#%d> - [%s](https://youtube.com/channel/%s)", data.getLong("channelId"), data.getObjectId("id").toHexString(), data.getString("uploaderId")))
				.setSelect(SelectType.INDEX);
		
		paged.onSelect(selected -> this.sendStats(event, selected.getSelected()));
		
		paged.execute(event);
	}
	
	public void sendStats(Sx4CommandEvent event, Document notification) {
		ObjectId id = notification.getObjectId("id");
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setColor(this.config.getColour());
		embed.setAuthor("YouTube Notification Stats", null, event.getGuild().getIconUrl());
		embed.addField("Id", id.toHexString(), true);
		embed.addField("YouTube Channel", String.format("[%s](https://youtube.com/channel/%<s)", notification.getString("uploaderId")), true);
		embed.addField("Channel", "<#" + notification.getLong("channelId") + ">", true);
		embed.addField("Message", "`" + notification.get("message", YouTubeManager.DEFAULT_MESSAGE) + "`", false);
		embed.setFooter("Created at");
		embed.setTimestamp(Instant.ofEpochSecond(id.getTimestamp()));
		
		event.reply(embed.build()).queue();
	}
	
	@Command(value="stats", aliases={"settings", "setting"}, description="View the settings for a specific notification")
	@Examples({"youtube notification stats 5e45ce6d3688b30ee75201ae"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void stats(Sx4CommandEvent event, @Argument(value="id") ObjectId id) {
		List<Document> notifications = this.database.getGuildById(event.getGuild().getIdLong(), Projections.include("youtube.notifications")).getEmbedded(List.of("youtube", "notifications"), Collections.emptyList());
		
		Document notification = notifications.stream()
				.filter(d -> d.getObjectId("id").equals(id))
				.findFirst()
				.orElse(null);
			
		if (notification == null) {
			event.reply("You don't have a notification with that id :no_entry:").queue();
			return;
		}
		
		this.sendStats(event, notification);
	}
	
}
