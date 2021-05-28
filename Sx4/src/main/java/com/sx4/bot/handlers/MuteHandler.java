package com.sx4.bot.handlers;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.sx4.bot.core.Sx4;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Clock;
import java.util.List;

public class MuteHandler implements EventListener {

	private final Sx4 bot;

	public MuteHandler(Sx4 bot) {
		this.bot = bot;
	}

	// TODO: Allow users to set an action in the future rather than just giving the role back
	public void onGuildMemberJoin(GuildMemberJoinEvent event) {
		Bson filter = Filters.and(
			Filters.eq("userId", event.getMember().getIdLong()),
			Filters.eq("guildId", event.getGuild().getIdLong())
		);

		Document mute = this.bot.getMongo().getMute(filter, Projections.include("unmuteAt"));

		long unmuteAt = mute == null ? 0L : mute.get("unmuteAt", 0L);
		if (unmuteAt > Clock.systemUTC().instant().getEpochSecond()) {
			long roleId = this.bot.getMongo().getGuildById(event.getGuild().getIdLong(), Projections.include("mute.roleId")).getEmbedded(List.of("mute", "roleId"), 0L);
			if (roleId != 0L) {
				Role role = event.getGuild().getRoleById(roleId);
				if (role != null) {
					event.getGuild().addRoleToMember(event.getMember(), role).queue();
				}
			}
		}
	}

	@Override
	public void onEvent(GenericEvent event) {
		if (event instanceof GuildMemberJoinEvent) {
			this.onGuildMemberJoin((GuildMemberJoinEvent) event);
		}
	}

}
