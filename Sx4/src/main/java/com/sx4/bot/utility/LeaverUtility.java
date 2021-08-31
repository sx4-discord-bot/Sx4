package com.sx4.bot.utility;

import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.sx4.bot.entities.utility.TimeFormatter;
import com.sx4.bot.formatter.IFormatter;
import com.sx4.bot.formatter.JsonFormatter;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.bson.Document;

import java.time.Duration;
import java.time.OffsetDateTime;

public class LeaverUtility {

	private static final TimeFormatter FORMATTER = TimeUtility.LONG_TIME_FORMATTER_BUILDER.build();

	public static WebhookMessageBuilder getLeaverMessage(Document messageData, Member member) {
		Guild guild = member.getGuild();
		User user = member.getUser();
		OffsetDateTime now = OffsetDateTime.now();

		IFormatter<Document> formatter = new JsonFormatter(messageData)
			.member(member)
			.user(user)
			.guild(guild)
			.addVariable(Member.class, "age", LeaverUtility.FORMATTER.parse(Duration.between(member.getTimeJoined(), now).toSeconds()))
			.addVariable(User.class, "age", LeaverUtility.FORMATTER.parse(Duration.between(user.getTimeCreated(), now).toSeconds()))
			.addVariable("now", now);

		return MessageUtility.fromJson(formatter.parse());
	}

}
