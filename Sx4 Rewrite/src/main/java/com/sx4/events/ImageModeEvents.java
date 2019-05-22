package com.sx4.events;

import static com.rethinkdb.RethinkDB.r;

import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.rethinkdb.gen.ast.Get;
import com.rethinkdb.net.Connection;
import com.sx4.core.Sx4Bot;
import com.sx4.utils.TimeUtils;

import net.dv8tion.jda.core.entities.EmbedType;
import net.dv8tion.jda.core.entities.Message.Attachment;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

public class ImageModeEvents extends ListenerAdapter {

	private List<String> supportedFileTypes = List.of("png", "jpg", "jpeg", "gif", "webp", "mp4", "gifv", "mov", "image");
	
	@SuppressWarnings("unchecked")
	public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
		if (event.getAuthor().isBot()) {
			return;
		}
		
		Connection connection = Sx4Bot.getConnection();
		Get data = r.table("imagemode").get(event.getGuild().getId());
		Map<String, Object> dataRan = data.run(connection);
		if (dataRan == null) {
			return;
		}
		
		List<Map<String, Object>> channels = (List<Map<String, Object>>) dataRan.get("channels");
		for (Map<String, Object> channelData : channels) {
			if (channelData.get("id").equals(event.getChannel().getId())) {
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
				
				long slowmode = Long.parseLong((String) channelData.get("slowmode"));
				if (slowmode != 0) {
					List<Map<String, Object>> users = (List<Map<String, Object>>) channelData.get("users");
					for (Map<String, Object> userData : users) {
						if (userData.get("id").equals(event.getAuthor().getId())) {
							if (event.getMessage().getCreationTime().toEpochSecond() - (double) userData.get("timestamp") < slowmode) {
								long timeTill = (long) ((double) userData.get("timestamp") - event.getMessage().getCreationTime().toEpochSecond() + slowmode);
								event.getMessage().delete().queue(null, e -> {});
								event.getChannel().sendMessage(event.getAuthor().getAsMention() + ", You can send another image in " + TimeUtils.toTimeString(timeTill, ChronoUnit.SECONDS) + " :stopwatch:").queue(message -> {
									message.delete().queueAfter(10, TimeUnit.SECONDS, null, e -> {});
								});
							} else {
								channels.remove(channelData);
								users.remove(userData);
								userData.put("timestamp", (double) event.getMessage().getCreationTime().toInstant().toEpochMilli() / 1000);
								users.add(userData);
								channelData.put("users", users);
								channels.add(channelData);
								
								data.update(r.hashMap("channels", channels)).runNoReply(connection);
							}
							
							return;
						}
					}
					
					channels.remove(channelData);
					Map<String, Object> userData = new HashMap<>();
					userData.put("id", event.getAuthor().getId());
					userData.put("timestamp", (double) event.getMessage().getCreationTime().toInstant().toEpochMilli() / 1000);
					users.add(userData);
					channelData.put("users", users);
					channels.add(channelData);
					
					data.update(r.hashMap("channels", channels)).runNoReply(connection);
				}
			}
		}
	}
	
}
