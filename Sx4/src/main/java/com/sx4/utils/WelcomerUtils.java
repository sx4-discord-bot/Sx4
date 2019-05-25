package com.sx4.utils;

import static com.rethinkdb.RethinkDB.r;

import java.awt.Color;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.rethinkdb.gen.ast.Insert;
import com.rethinkdb.model.MapObject;
import com.sx4.core.Sx4Bot;
import com.sx4.exceptions.ImageProcessingException;
import com.sx4.interfaces.Sx4Callback;
import com.sx4.modules.ImageModule;
import com.sx4.settings.Settings;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import okhttp3.Request;
import okhttp3.Response;

public class WelcomerUtils {

	public static MapObject getMapData(Guild guild) {
		return r.hashMap("id", guild.getId())
		.with("toggle", false)
		.with("leavetoggle", false)
		.with("imgwelcomertog", false)
		.with("channel", null)
		.with("leavechannel", null)
		.with("message", "{user.mention}, Welcome to **{server}**. Enjoy your time here! The server now has {server.members} members.")
		.with("leave-message", "**{user.name}** has just left **{server}**. Bye **{user.name}**!")
		.with("dm", false)
		.with("banner", null)
		.with("embed", false)
		.with("leaveembed", false)
		.with("embedcolour", null)
		.with("leaveembedcolour", null);
	}
	
	public static Insert insertData(Guild guild) {
		return r.table("welcomer").insert(WelcomerUtils.getMapData(guild));
	}
	
	public static String getLeaverMessage(Guild guild, Member user, String message) {
		int guildMemberCount = guild.getMembers().size();
		message = message.replace("{server}", guild.getName());
		message = message.replace("{user.mention}", user.getAsMention());
		message = message.replace("{user.name}", user.getUser().getName());
		message = message.replace("{user}", user.getUser().getAsTag());
		message = message.replace("{server.members}", String.format("%,d", guildMemberCount));
		message = message.replace("{server.members.prefix}", String.format("%,d", guildMemberCount) + GeneralUtils.getNumberSuffixRaw(guildMemberCount)); 
		message = message.replace("{user.stayed.length}", TimeUtils.toTimeString(Clock.systemUTC().instant().getEpochSecond() - user.getJoinDate().toEpochSecond(), ChronoUnit.SECONDS));
		
		return message;
	}
	
	public static MessageBuilder getLeaver(Member user, Guild guild, Map<String, Object> data) {
		MessageBuilder message = new MessageBuilder();
		String messageString = WelcomerUtils.getLeaverMessage(guild, user, (String) data.get("leave-message"));
		if ((boolean) data.get("leaveembed")) {
			return message.setEmbed(WelcomerUtils.getEmbed(user, messageString, (Long) data.get("leaveembedcolour")).build());
		} else {
			return message.setContent(messageString);
		}
	}
	
	public static String getWelcomerMessage(Guild guild, Member user, String message) {
		int guildMemberCount = guild.getMembers().size();
		message = message.replace("{server}", guild.getName());
		message = message.replace("{user.mention}", user.getAsMention());
		message = message.replace("{user.name}", user.getUser().getName());
		message = message.replace("{user}", user.getUser().getAsTag());
		message = message.replace("{server.members}", String.format("%,d", guildMemberCount));
		message = message.replace("{server.members.prefix}", String.format("%,d", guildMemberCount) + GeneralUtils.getNumberSuffixRaw(guildMemberCount)); 
		message = message.replace("{user.created.length}", TimeUtils.toTimeString(Clock.systemUTC().instant().getEpochSecond() - user.getUser().getCreationTime().toEpochSecond(), ChronoUnit.SECONDS));
		
		return message;
	}
	
	public static EmbedBuilder getEmbed(Member user, String message, Long colour) {
		EmbedBuilder embed = new EmbedBuilder();
		embed.setAuthor(user.getUser().getAsTag(), null, user.getUser().getEffectiveAvatarUrl());
		embed.setColor(colour == null ? null : new Color(colour.intValue()));
		embed.setDescription(message);
		embed.setTimestamp(Instant.now());
		
		return embed;
	}
	
	public static void getImageWelcomer(Member user, String banner, Consumer<Response> imageResponse, Consumer<ImageProcessingException> error) {
		Request request = null;
		try {
			request = new Request.Builder()
					.url(new URL("http://" + Settings.LOCAL_HOST + ":8443/api/welcomer?userAvatar=" + user.getUser().getEffectiveAvatarUrl() + "&userName=" + URLEncoder.encode(user.getUser().getAsTag(), StandardCharsets.UTF_8) + (banner == null ? "" : "&background=" + banner)))
					.build();
		} catch (MalformedURLException e) {}
		
		ImageModule.client.newCall(request).enqueue((Sx4Callback) response -> {
			if (response.code() != 200) {
				error.accept(new ImageProcessingException(response.code(), response.body().string()));
			} else {
				imageResponse.accept(response);
			}
		});
	}
	
	public static void getWelcomerMessage(Member user, Guild guild, Map<String, Object> data, BiConsumer<MessageBuilder, Response> message) {
		MessageBuilder messageBuilder = new MessageBuilder();
		
		if ((boolean) data.get("toggle") == false && (boolean) data.get("imgwelcomertog") == true) {
			WelcomerUtils.getImageWelcomer(user, (String) data.get("banner"), response -> {
				if (response != null) {
					String fileName = "welcomer." + response.headers().get("Content-Type").split("/")[1];
					
					if ((boolean) data.get("embed")) {
						EmbedBuilder embed = WelcomerUtils.getEmbed(user, "", (Long) data.get("embedcolour"));
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
			String messageString = WelcomerUtils.getWelcomerMessage(guild, user, (String) data.get("message"));
			if ((boolean) data.get("embed")) {
				EmbedBuilder embed = WelcomerUtils.getEmbed(user, messageString, (Long) data.get("embedcolour"));
				if ((boolean) data.get("imgwelcomertog")) {
					WelcomerUtils.getImageWelcomer(user, (String) data.get("banner"), response -> {
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
				if ((boolean) data.get("imgwelcomertog")) {
					WelcomerUtils.getImageWelcomer(user, (String) data.get("banner"), response -> {
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
	
}
