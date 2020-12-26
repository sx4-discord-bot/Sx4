package com.sx4.bot.utility;

import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.entities.image.ImageRequest;
import com.sx4.bot.formatter.JsonFormatter;
import com.sx4.bot.formatter.Formatter;
import com.sx4.bot.http.HttpCallback;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.bson.Document;

import java.time.OffsetDateTime;
import java.util.function.BiConsumer;

public class WelcomerUtility {

	public static void getWelcomerMessage(Document messageData, Member member, boolean image, boolean gif, BiConsumer<WebhookMessageBuilder, Throwable> consumer) {
		Guild guild = member.getGuild();

		WebhookMessageBuilder builder;
		if (messageData == null) {
			builder = new WebhookMessageBuilder();
		} else {
			Formatter<Document> formatter = new JsonFormatter(messageData)
				.member(member)
				.guild(guild)
				.append("now", OffsetDateTime.now());

			try {
				builder = MessageUtility.fromJson(formatter.parse());
			} catch (IllegalArgumentException e) {
				consumer.accept(null, e);
				return;
			}
		}

		if (!image) {
			consumer.accept(builder, null);
		} else {
			User user = member.getUser();

			ImageRequest request = new ImageRequest("welcomer")
				.addQuery("avatar", user.getEffectiveAvatarUrl())
				.addQuery("name", user.getAsTag())
				.addQuery("gif", gif)
				.addQuery("bannerId", guild.getId());

			Sx4.getClient().newCall(request.build()).enqueue((HttpCallback) response -> {
				if (response.isSuccessful()) {
					builder.addFile("welcomer." + response.header("Content-Type").split("/")[1], response.body().bytes());

					consumer.accept(builder, null);
				}
			});
		}
	}

}
