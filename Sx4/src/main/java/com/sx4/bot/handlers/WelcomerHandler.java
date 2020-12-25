package com.sx4.bot.handlers;

import club.minnced.discord.webhook.send.WebhookMessage;
import com.mongodb.client.model.Projections;
import com.sx4.bot.database.Database;
import com.sx4.bot.managers.WelcomerManager;
import com.sx4.bot.utility.WelcomerUtility;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bson.Document;

import java.time.Clock;
import java.util.List;

public class WelcomerHandler extends ListenerAdapter {

	public void onGuildMemberJoin(GuildMemberJoinEvent event) {
		Document data = Database.get().getGuildById(event.getGuild().getIdLong(), Projections.include("welcomer", "premium.endAt"));

		Document welcomer = data.get("welcomer", Database.EMPTY_DOCUMENT);
		Document image = welcomer.get("image", Database.EMPTY_DOCUMENT);

		boolean messageEnabled = welcomer.get("enabled", false), imageEnabled = image.get("enabled", false);
		if (!messageEnabled && !imageEnabled) {
			return;
		}

		long channelId = welcomer.get("channelId", 0L);

		TextChannel channel = channelId == 0L ? null : event.getGuild().getTextChannelById(channelId);
		if (channel == null) {
			return;
		}

		Document webhookData = welcomer.get("webhook", Database.EMPTY_DOCUMENT);

		boolean gif = data.getEmbedded(List.of("premium", "endAt"), 0L) >= Clock.systemUTC().instant().getEpochSecond();

		WelcomerUtility.getWelcomerMessage(messageEnabled ? welcomer.get("message", WelcomerManager.DEFAULT_MESSAGE) : null, event.getMember(), imageEnabled, gif, builder -> {
			WebhookMessage message = builder
				.setUsername(webhookData.get("name", "Sx4 - Welcomer"))
				.setAvatarUrl(webhookData.get("avatar", event.getJDA().getSelfUser().getEffectiveAvatarUrl()))
				.build();

			WelcomerManager.get().sendWelcomer(channel, webhookData, message);
		});
	}

}
