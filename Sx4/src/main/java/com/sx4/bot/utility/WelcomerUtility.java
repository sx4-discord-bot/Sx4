package com.sx4.bot.utility;

import com.sx4.bot.config.Config;
import com.sx4.bot.entities.image.ImageRequest;
import com.sx4.bot.formatter.output.Formatter;
import com.sx4.bot.formatter.output.JsonFormatter;
import com.sx4.bot.http.HttpCallback;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import okhttp3.OkHttpClient;
import org.bson.Document;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.function.BiConsumer;

public class WelcomerUtility {

	public static void getWelcomerMessage(OkHttpClient httpClient, Document messageData, String bannerId, Member member, boolean canary, boolean image, boolean gif, BiConsumer<MessageCreateBuilder, Throwable> consumer) {
		Guild guild = member.getGuild();
		User user = member.getUser();
		OffsetDateTime now = OffsetDateTime.now();

		Formatter<Document> formatter = new JsonFormatter(messageData)
			.member(member)
			.user(user)
			.guild(guild)
			.addVariable(User.class, "age", TimeUtility.LONG_TIME_FORMATTER.parse(Duration.between(user.getTimeCreated(), now).toSeconds()))
			.addVariable("now", now);

		if (!image) {
			MessageCreateBuilder builder;
			if (messageData != null) {
				try {
					builder = MessageUtility.fromCreateJson(formatter.parse(), true);
				} catch (IllegalArgumentException e) {
					consumer.accept(null, e);
					return;
				}
			} else {
				builder = new MessageCreateBuilder();
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

					MessageCreateBuilder builder;
					if (messageData == null) {
						builder = new MessageCreateBuilder();
					} else {
						try {
							builder = MessageUtility.fromCreateJson(formatter.parse(), true);
						} catch (IllegalArgumentException e) {
							consumer.accept(null, e);
							return;
						}
					}

					builder.addFiles(FileUpload.fromData(response.body().bytes(), fileName));

					consumer.accept(builder, null);
				} else {
					response.close();
				}
			});
		}
	}

}
