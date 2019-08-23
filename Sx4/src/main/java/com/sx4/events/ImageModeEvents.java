package com.sx4.events;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.sx4.database.Database;
import com.sx4.utils.TimeUtils;

import net.dv8tion.jda.api.entities.EmbedType;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class ImageModeEvents extends ListenerAdapter {

	private List<String> supportedFileTypes = List.of("png", "jpg", "jpeg", "gif", "webp", "mp4", "gifv", "mov", "image");
	
	public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
		if (event.getAuthor().isBot()) {
			return;
		}
		
		List<Document> channels = Database.get().getGuildById(event.getGuild().getIdLong(), null, Projections.include("imageMode.channels")).getEmbedded(List.of("imageMode", "channels"), Collections.emptyList());
		for (Document channelData : channels) {
			if (channelData.getLong("id") == event.getChannel().getIdLong()) {
				if (event.getMessage().getAttachments().isEmpty()) {
					if (event.getMessage().getEmbeds().isEmpty()) {
						event.getMessage().delete().queue(null, e -> {});
						event.getChannel().sendMessage(event.getAuthor().getAsMention() + ", You can only send images in this channel :no_entry:").queue(message -> {
							message.delete().queueAfter(10, TimeUnit.SECONDS, null, e -> {});
						});
						
						return;
					} else {
						List<MessageEmbed> embeds = event.getMessage().getEmbeds();
						for (int i = 0; i < embeds.size(); i ++) {
							MessageEmbed embed = embeds.get(i);
							
							String url = null;
							if (embed.getThumbnail() != null) {
								url = embed.getThumbnail().getUrl();
							}
							
							String fileType = null;
							if (url != null) {
								int periodIndex = url.lastIndexOf(".") + 1;
								fileType = url.substring(periodIndex);
							}
							
							if (embed.getType().equals(EmbedType.IMAGE) || (fileType != null && supportedFileTypes.contains(fileType))) {
								break;
							} else if (i == embeds.size() - 1) {
								event.getMessage().delete().queue(null, e -> {});
								event.getChannel().sendMessage(event.getAuthor().getAsMention() + ", You can only send images in this channel :no_entry:").queue(message -> {
									message.delete().queueAfter(10, TimeUnit.SECONDS, null, e -> {});
								});
								
								return;
							}
						}
					}
				} else {
					List<Attachment> attachments = event.getMessage().getAttachments();
					for (int i = 0; i < attachments.size(); i ++) {
						Attachment attachment = attachments.get(i);
						
						String url = attachment.getUrl();
						int periodIndex = url.lastIndexOf(".") + 1;
						String fileType = url.substring(periodIndex);
						
						if (supportedFileTypes.contains(fileType)) {
							break;
						} else if (i == attachments.size() - 1) {
							event.getMessage().delete().queue(null, e -> {});
							event.getChannel().sendMessage(event.getAuthor().getAsMention() + ", You can only send images in this channel :no_entry:").queue(message -> {
								message.delete().queueAfter(10, TimeUnit.SECONDS, null, e -> {});
							});
							
							return;
						}
					}
				}
				
				Long slowmode = channelData.getLong("slowmode");
				if (slowmode != null && slowmode != 0) {
					List<Document> users = channelData.getList("users", Document.class, Collections.emptyList());
					OffsetDateTime timeCreated = event.getMessage().getTimeCreated();
					for (Document userData : users) {
						if (userData.getLong("id") == event.getAuthor().getIdLong()) {
							if (timeCreated.toEpochSecond() - userData.getLong("timestamp") < slowmode) {
								long timeTill = userData.getLong("timestamp") - timeCreated.toEpochSecond() + slowmode;
								event.getMessage().delete().queue(null, e -> {});
								event.getChannel().sendMessage(event.getAuthor().getAsMention() + ", You can send another image in " + TimeUtils.toTimeString(timeTill, ChronoUnit.SECONDS) + " :stopwatch:").queue(message -> {
									message.delete().queueAfter(10, TimeUnit.SECONDS, null, e -> {});
								});
							} else {
								Bson update = Updates.set("imageMode.channels.$[channel].users.$[user].timestamp", timeCreated.toInstant().getEpochSecond());
								
								List<Bson> arrayFilters = List.of(Filters.eq("channel.id", event.getChannel().getIdLong()), Filters.eq("user.id", event.getAuthor().getIdLong()));
								UpdateOptions updateOptions = new UpdateOptions().arrayFilters(arrayFilters).upsert(true);
								
								Database.get().updateGuildById(event.getGuild().getIdLong(), null, update, updateOptions, (result, exception) -> {
									if (exception != null) {
										exception.printStackTrace();
									}
								});
							}
							
							return;
						}
					}
					
					Document newUserData = new Document("id", event.getAuthor().getIdLong())
							.append("timestamp", timeCreated.toInstant().getEpochSecond());
					
					Bson update = Updates.push("imageMode.channels.$[channel].users", newUserData);
					
					UpdateOptions updateOptions = new UpdateOptions().arrayFilters(List.of(Filters.eq("channel.id", event.getChannel().getIdLong())));
					
					Database.get().updateGuildById(event.getGuild().getIdLong(), null, update, updateOptions, (result, exception) -> {
						if (exception != null) {
							exception.printStackTrace();
						}
					});
				}
			}
		}
	}
	
}
