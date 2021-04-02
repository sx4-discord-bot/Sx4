package com.sx4.bot.utility;

import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.sx4.bot.formatter.Formatter;
import com.sx4.bot.formatter.JsonFormatter;
import com.sx4.bot.formatter.parser.FormatterTimeParser;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.bson.Document;

import java.time.OffsetDateTime;

public class LeaverUtility {

	public static WebhookMessageBuilder getLeaverMessage(Document messageData, Member member) {
		Guild guild = member.getGuild();

		Formatter<Document> formatter = new JsonFormatter(messageData)
			.member(member)
			.guild(guild)
			.append("now", new FormatterTimeParser(OffsetDateTime.now()));

		return MessageUtility.fromJson(formatter.parse());
	}

}
