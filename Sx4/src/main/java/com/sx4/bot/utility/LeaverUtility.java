package com.sx4.bot.utility;

import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.sx4.bot.formatter.IFormatter;
import com.sx4.bot.formatter.JsonFormatter;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.bson.Document;

import java.time.OffsetDateTime;

public class LeaverUtility {

	public static WebhookMessageBuilder getLeaverMessage(Document messageData, Member member) {
		Guild guild = member.getGuild();

		IFormatter<Document> formatter = new JsonFormatter(messageData)
			.member(member)
			.user(member.getUser())
			.guild(guild)
			.addVariable("now", OffsetDateTime.now());

		return MessageUtility.fromJson(formatter.parse());
	}

}
