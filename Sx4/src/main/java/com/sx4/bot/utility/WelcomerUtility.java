package com.sx4.bot.utility;

import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.sx4.bot.config.Config;
import com.sx4.bot.entities.image.ImageRequest;
import com.sx4.bot.formatter.IFormatter;
import com.sx4.bot.formatter.JsonFormatter;
import com.sx4.bot.http.HttpCallback;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import okhttp3.OkHttpClient;
import org.bson.Document;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.function.BiConsumer;

public class WelcomerUtility {

	public static void getWelcomerMessage(OkHttpClient httpClient, Document messageData, String bannerId, Member member, boolean canary, boolean image, boolean gif, BiConsumer<WebhookMessageBuilder, Throwable> consumer) {
		Guild guild = member.getGuild();
		User user = member.getUser();
		OffsetDateTime now = OffsetDateTime.now();

		IFormatter<Document> formatter = new JsonFormatter(messageData)
			.member(member)
			.user(user)
			.guild(guild)
			.addVariable("user.created.length", TimeUtility.getTimeString(Duration.between(user.getTimeCreated(), now).toSeconds()))
			.addVariable("now", now);

		if (!image) {
			WebhookMessageBuilder builder;
			if (messageData != null) {
				try {
					builder = MessageUtility.fromJson(formatter.parse());
				} catch (IllegalArgumentException e) {
					consumer.accept(null, e);
					return;
				}
			} else {
				builder = new WebhookMessageBuilder();
			}

			consumer.accept(builder, null);
		} else {
			ImageRequest request = new ImageRequest(Config.get().getImageWebserverUrl("welcomer"))
				.addQuery("avatar", user.getEffectiveAvatarUrl())
				.addQuery("name", user.getAsTag())
				.addQuery("gif", gif)
				.addQuery("directory", canary ? "sx4-canary" : "sx4-main");

			if (bannerId != null) {
				request.addQuery("banner_id", bannerId);
			}

			httpClient.newCall(request.build(Config.get().getImageWebserver())).enqueue((HttpCallback) response -> {
				if (response.isSuccessful()) {
					String fileName = "welcomer." + response.header("Content-Type").split("/")[1];
					formatter.addVariable("file.name", fileName).addVariable("file.url", "attachment://" + fileName);

					WebhookMessageBuilder builder;
					if (messageData == null) {
						builder = new WebhookMessageBuilder();
					} else {
						try {
							builder = MessageUtility.fromJson(formatter.parse());
						} catch (IllegalArgumentException e) {
							consumer.accept(null, e);
							return;
						}
					}

					builder.addFile(fileName, response.body().bytes());

					consumer.accept(builder, null);
				} else {
					response.close();
				}
			});
		}
	}

}
