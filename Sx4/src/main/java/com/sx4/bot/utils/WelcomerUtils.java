package com.sx4.bot.utils;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.bson.Document;

import com.sx4.bot.core.Sx4Bot;
import com.sx4.bot.database.Database;
import com.sx4.bot.exceptions.ImageProcessingException;
import com.sx4.bot.interfaces.Sx4Callback;
import com.sx4.bot.modules.ImageModule;
import com.sx4.bot.modules.WelcomerModule.LeaverCommand;
import com.sx4.bot.modules.WelcomerModule.WelcomerCommand;
import com.sx4.bot.settings.Settings;

import club.minnced.discord.webhook.send.WebhookEmbed;
import club.minnced.discord.webhook.send.WebhookEmbed.EmbedAuthor;
import club.minnced.discord.webhook.send.WebhookEmbed.EmbedField;
import club.minnced.discord.webhook.send.WebhookEmbed.EmbedFooter;
import club.minnced.discord.webhook.send.WebhookEmbed.EmbedTitle;
import club.minnced.discord.webhook.send.WebhookEmbedBuilder;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageEmbed.Field;
import net.dv8tion.jda.api.entities.Role;
import okhttp3.Request;
import okhttp3.Response;

public class WelcomerUtils {
	
	public static String getLeaverMessage(Guild guild, Member user, String message) {
		int guildMemberCount = guild.getMembers().size();
		
		int index = -1;
		while ((index = message.indexOf('{', index + 1)) != -1) {
		    if (index > 0 && message.charAt(index - 1) == '\\') {
		        message = message.substring(0, index - 1) + message.substring(index);
		        continue;
		    }

		    int endIndex = message.indexOf('}', index + 1);
		    if (endIndex != -1)  {
		        if (message.charAt(endIndex - 1) == '\\') {
		            message = message.substring(0, endIndex - 1) + message.substring(endIndex);
		            continue;
		        } else {
		            String formatter = message.substring(index + 1, endIndex);
		            String placeHolder = message.substring(0, index) + "%s" + message.substring(endIndex + 1);
		            
		            switch (formatter.trim().toLowerCase()) {
		            	case "server":
		            		message = String.format(placeHolder, guild.getName());
		            		break;
		            	case "user.mention":
		            		message = String.format(placeHolder, user.getAsMention());
		            		break;
		            	case "user.name":
		            		message = String.format(placeHolder, user.getUser().getName());
		            		break;
		            	case "user":
		            		message = String.format(placeHolder, user.getUser().getAsTag());
		            		break;
		            	case "server.members":
		            		message = String.format(placeHolder, String.format("%,d", guildMemberCount));
		            		break;
		            	case "server.members.prefix":
		            		message = String.format(placeHolder, String.format("%,d", guildMemberCount) + GeneralUtils.getNumberSuffixRaw(guildMemberCount));
		            		break;
		            	case "user.stayed.length":
		            		message = String.format(placeHolder, TimeUtils.toTimeString(Clock.systemUTC().instant().getEpochSecond() - user.getTimeJoined().toEpochSecond(), ChronoUnit.SECONDS));
		            		break;
		            }
		        }
		    }
		}
		
		return message;
	}
	
	public static WebhookMessageBuilder getLeaver(Member user, Guild guild, Document data) {
		MessageBuilder messageBuilder = WelcomerUtils.getLeaverPreview(user, guild, data);
		
		WebhookMessageBuilder webhookMessageBuilder = new WebhookMessageBuilder();
		
		Message previewMessage = messageBuilder.build();
		
		List<WebhookEmbed> embeds = new ArrayList<>();
		for (MessageEmbed embed : previewMessage.getEmbeds()) {
			embeds.add(WelcomerUtils.toWebhookEmbedBuilder(embed).build());
		}
		
		webhookMessageBuilder.setContent(previewMessage.getContentRaw()).addEmbeds(embeds);
		
		return webhookMessageBuilder;
	}
	
	public static MessageBuilder getLeaverPreview(Member user, Guild guild, Document data) {
		MessageBuilder message = new MessageBuilder();
		
		String messageString = WelcomerUtils.getLeaverMessage(guild, user, data.get("message", LeaverCommand.DEFAULT_MESSAGE));
		
		Document embedData = data.get("embed", Database.EMPTY_DOCUMENT);
		if (embedData.getBoolean("enabled", false)) {
			return message.setEmbed(WelcomerUtils.getPreviewEmbed(user, messageString, embedData.getInteger("colour")).build());
		} else {
			return message.setContent(messageString);
		}
	}
	
	public static String getWelcomerMessage(Guild guild, Member user, String message) {
		int guildMemberCount = guild.getMembers().size();
		
		int index = -1;
		while ((index = message.indexOf('{', index + 1)) != -1) {
		    if (index > 0 && message.charAt(index - 1) == '\\') {
		        message = message.substring(0, index - 1) + message.substring(index);
		        continue;
		    }

		    int endIndex = message.indexOf('}', index + 1);
		    if (endIndex != -1)  {
		        if (message.charAt(endIndex - 1) == '\\') {
		            message = message.substring(0, endIndex - 1) + message.substring(endIndex);
		            continue;
		        } else {
		            String formatter = message.substring(index + 1, endIndex);
		            String placeHolder = message.substring(0, index) + "%s" + message.substring(endIndex + 1);
		            
		            switch (formatter.trim().toLowerCase()) {
		            	case "server":
		            		message = String.format(placeHolder, guild.getName());
		            		break;
		            	case "user.mention":
		            		message = String.format(placeHolder, user.getAsMention());
		            		break;
		            	case "user.name":
		            		message = String.format(placeHolder, user.getUser().getName());
		            		break;
		            	case "user":
		            		message = String.format(placeHolder, user.getUser().getAsTag());
		            		break;
		            	case "server.members":
		            		message = String.format(placeHolder, String.format("%,d", guildMemberCount));
		            		break;
		            	case "server.members.prefix":
		            		message = String.format(placeHolder, String.format("%,d", guildMemberCount) + GeneralUtils.getNumberSuffixRaw(guildMemberCount));
		            		break;
		            	case "user.created.length":
		            		message = String.format(placeHolder, TimeUtils.toTimeString(Clock.systemUTC().instant().getEpochSecond() - user.getUser().getTimeCreated().toEpochSecond(), ChronoUnit.SECONDS));
		            		break;
		            }
		        }
		    }
		}
		
		return message;
	}
	
	public static WebhookEmbedBuilder getEmbed(Member user, String message, Long colour) {
		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setAuthor(new EmbedAuthor(user.getUser().getAsTag(), user.getUser().getEffectiveAvatarUrl(), null));
		embed.setColor(colour == null ? Role.DEFAULT_COLOR_RAW : colour.intValue());
		embed.setDescription(message);
		embed.setTimestamp(Instant.now());
		
		return embed;
	}
	
	public static EmbedBuilder getPreviewEmbed(Member user, String message, Integer colour) {
		EmbedBuilder embed = new EmbedBuilder();
		embed.setAuthor(user.getUser().getAsTag(), null, user.getUser().getEffectiveAvatarUrl());
		embed.setColor(colour == null ? Role.DEFAULT_COLOR_RAW : colour);
		embed.setDescription(message);
		embed.setTimestamp(Instant.now());
		
		return embed;
	}
	
	public static void getImageWelcomer(Member user, String banner, boolean gif, Consumer<Response> imageResponse, Consumer<ImageProcessingException> error) {
		Request request = new Request.Builder()
			.url("http://" + Settings.LOCAL_HOST + ":8443/api/welcomer?userAvatar=" + user.getUser().getEffectiveAvatarUrl() + "&userName=" + URLEncoder.encode(user.getUser().getAsTag(), StandardCharsets.UTF_8) + (banner == null ? "" : "&background=" + banner) + (gif ? "&gif=true" : ""))
			.build();
		
		ImageModule.client.newCall(request).enqueue((Sx4Callback) response -> {
			if (response.code() != 200) {
				error.accept(new ImageProcessingException(response.code(), response.body().string()));
			} else {
				imageResponse.accept(response);
			}
		});
	}
	
	public static void getWelcomerMessage(Member user, Guild guild, Document data, boolean gif, Consumer<WebhookMessageBuilder> message) {
		WelcomerUtils.getWelcomerPreview(user, guild, data, gif, (messageBuilder, response) -> {
			WebhookMessageBuilder webhookMessageBuilder = new WebhookMessageBuilder();
			
			if (!messageBuilder.isEmpty()) {
				Message previewMessage = messageBuilder.build();
				
				List<WebhookEmbed> embeds = new ArrayList<>();
				for (MessageEmbed embed : previewMessage.getEmbeds()) {
					embeds.add(WelcomerUtils.toWebhookEmbedBuilder(embed).build());
				}
				
				webhookMessageBuilder.setContent(previewMessage.getContentRaw()).addEmbeds(embeds);
			}
			
			if (response != null) {
				String fileName = "welcomer." + response.headers().get("Content-Type").split("/")[1];
				
				try {
					webhookMessageBuilder.addFile(fileName, response.body().bytes());
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			
			message.accept(webhookMessageBuilder);
		});
	}
	
	public static void getWelcomerPreview(Member user, Guild guild, Document data, boolean gif, BiConsumer<MessageBuilder, Response> message) {
		MessageBuilder messageBuilder = new MessageBuilder();
		
		Document welcomerData = data.get("welcomer", Database.EMPTY_DOCUMENT), imageWelcomerData = data.get("imageWelcomer", Database.EMPTY_DOCUMENT);
		if (!welcomerData.getBoolean("enabled", false) && imageWelcomerData.getBoolean("enabled", false)) {
			WelcomerUtils.getImageWelcomer(user, imageWelcomerData.getString("banner"), gif, response -> {
				if (response != null) {
					String fileName = "welcomer." + response.headers().get("Content-Type").split("/")[1];
					
					Document embedData = welcomerData.get("embed", Database.EMPTY_DOCUMENT);
					if (embedData.getBoolean("enabled", false)) {
						EmbedBuilder embed = WelcomerUtils.getPreviewEmbed(user, "", embedData.getInteger("colour"));
						embed.setImage("attachment://" + fileName);
						
						message.accept(messageBuilder.setEmbed(embed.build()), response);
					} else {
						message.accept(messageBuilder, response);
					}
				}
			}, error -> {
				if (error != null) {
					Sx4Bot.getShardManager()
						.getGuildById(Settings.SUPPORT_SERVER_ID)
						.getTextChannelById(Settings.ERRORS_CHANNEL_ID)
						.sendMessage("Image welcomer failed to create (Status code: " + error.getStatusCode() + ")\n```diff\n- " + String.join("\n- ", error.getMessage().split("\n")) + "```")
						.queue();
				}
			});
		} else {
			String messageString = WelcomerUtils.getWelcomerMessage(guild, user, welcomerData.get("message", WelcomerCommand.DEFAULT_MESSAGE));
			
			Document embedData = welcomerData.get("embed", Database.EMPTY_DOCUMENT);
			if (embedData.getBoolean("enabled", false)) {
				EmbedBuilder embed = WelcomerUtils.getPreviewEmbed(user, messageString, embedData.getInteger("colour"));
				if (imageWelcomerData.getBoolean("enabled", false)) {
					WelcomerUtils.getImageWelcomer(user, imageWelcomerData.getString("banner"), gif, response -> {
						if (response != null) {
							String fileName = "welcomer." + response.headers().get("Content-Type").split("/")[1];
							
							embed.setImage("attachment://" + fileName);
							
							message.accept(messageBuilder.setEmbed(embed.build()), response);
						}
					}, error -> {
						if (error != null) {
							Sx4Bot.getShardManager()
								.getGuildById(Settings.SUPPORT_SERVER_ID)
								.getTextChannelById(Settings.ERRORS_CHANNEL_ID)
								.sendMessage("Image welcomer failed to create (Status code: " + error.getStatusCode() + ")\n```diff\n- " + String.join("\n- ", error.getMessage().split("\n")) + "```")
								.queue();
						}
					});
				} else {
					message.accept(messageBuilder.setEmbed(embed.build()), null);
				}
			} else {
				if (imageWelcomerData.getBoolean("enabled", false)) {
					WelcomerUtils.getImageWelcomer(user, imageWelcomerData.getString("banner"), gif, response -> {
						if (response != null) {						
							message.accept(messageBuilder.setContent(messageString), response);
						}
					}, error -> {
						if (error != null) {
							Sx4Bot.getShardManager()
							.getGuildById(Settings.SUPPORT_SERVER_ID)
							.getTextChannelById(Settings.ERRORS_CHANNEL_ID)
							.sendMessage("Image welcomer failed to create (Status code: " + error.getStatusCode() + ")\n```diff\n- " + String.join("\n- ", error.getMessage().split("\n")) + "```")
							.queue();
						}
					});
				} else {
					message.accept(messageBuilder.setContent(messageString), null);
				}
			}
		}
	}
	
	public static WebhookEmbedBuilder toWebhookEmbedBuilder(MessageEmbed embed) {
		WebhookEmbedBuilder webhookEmbed = new WebhookEmbedBuilder();
		
		if (embed.getAuthor() != null) {
			webhookEmbed.setAuthor(new EmbedAuthor(embed.getAuthor().getName(), embed.getAuthor().getIconUrl(), embed.getAuthor().getUrl()));
		}
		
		if (embed.getColor() != null) {
			webhookEmbed.setColor(embed.getColorRaw());
		}
		
		if (embed.getDescription() != null) {
			webhookEmbed.setDescription(embed.getDescription());
		}
		
		if (embed.getFooter() != null) {
			webhookEmbed.setFooter(new EmbedFooter(embed.getFooter().getText(), embed.getFooter().getIconUrl()));
		}
		
		if (embed.getImage() != null) {
			webhookEmbed.setImageUrl(embed.getImage().getUrl());
		}
		
		if (embed.getThumbnail() != null) {
			webhookEmbed.setThumbnailUrl(embed.getThumbnail().getUrl());
		}
		
		if (embed.getTimestamp() != null) {
			webhookEmbed.setTimestamp(embed.getTimestamp());
		}
		
		if (embed.getTitle() != null) {
			webhookEmbed.setTitle(new EmbedTitle(embed.getTitle(), null));
		}
		
		for (Field field : embed.getFields()) {
			webhookEmbed.addField(new EmbedField(field.isInline(), field.getName(), field.getValue()));
		}
		
		return webhookEmbed;
	}
	
}
