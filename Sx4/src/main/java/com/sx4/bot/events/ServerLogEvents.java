package com.sx4.bot.events;

import java.util.List;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

import com.sx4.bot.core.Sx4Bot;
import com.sx4.bot.settings.Settings;
import com.sx4.bot.utils.ModUtils;
import com.sx4.bot.utils.TimeUtils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.sharding.ShardManager;

public class ServerLogEvents extends ListenerAdapter {
	
	private int latestMilestone = 0;
	private final int perMilestone = 100;

	public void onGuildJoin(GuildJoinEvent event) {
		ShardManager shardManager = Sx4Bot.getShardManager();
		Guild supportServer = shardManager.getGuildById(Settings.SUPPORT_SERVER_ID);
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setColor(Settings.COLOR_GREEN);
		embed.setThumbnail(event.getGuild().getIconUrl());
		embed.setTimestamp(Instant.now());
		embed.setDescription(String.format("I am now in %,d servers and connected to %,d users", shardManager.getGuilds().size(), shardManager.getUsers().size()));
		embed.setAuthor("Joined Server!", null, event.getJDA().getSelfUser().getEffectiveAvatarUrl());
		embed.addField("Server Name", event.getGuild().getName(), true);
		embed.addField("Server ID", event.getGuild().getId(), true);
		embed.addField("Server Owner", event.getGuild().getOwner().getUser().getAsTag() + "\n" + event.getGuild().getOwnerId(), true);
		embed.addField("Server Members", String.format("%,d member%s", event.getGuild().getMembers().size(), event.getGuild().getMembers().size() == 1 ? "" : "s"), true);
		
		supportServer.getTextChannelById(Settings.SERVER_LOGS_ID).sendMessage(embed.build()).queue();
		
		if (event.getGuild().getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			event.getGuild().retrieveAuditLogs().type(ActionType.BOT_ADD).queueAfter(500, TimeUnit.MILLISECONDS, audits -> {
				AuditLogEntry audit = audits.stream()
						.filter(auditLog -> auditLog.getTargetIdLong() == event.getJDA().getSelfUser().getIdLong())
						.filter(auditLog -> Duration.between(auditLog.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds() <= 5)
						.findFirst()
						.orElse(null);
				
				if (audit != null) {
					audit.getUser().openPrivateChannel().queue(channel -> {
						List<String> prefixes = ModUtils.getPrefixes(null, audit.getUser());
						channel.sendMessageFormat("Thanks for adding me to your server!\nYour prefix%s `%s`\nAll my info and commands can be found in `%shelp`\nIf you need any help feel free to join the support server: https://discord.gg/PqJNcfB", prefixes.size() == 1 ? " is" : "es are", String.join("`, `", prefixes), prefixes.get(0)).queue();
					});
				}
			});
		}
		
		if (this.latestMilestone == 0) {
			this.latestMilestone = (int) Math.floor((double) shardManager.getGuilds().size() / this.perMilestone) * this.perMilestone;
		}
		
		if (shardManager.getGuilds().size() % this.perMilestone == 0) {
			supportServer.getTextChannelById(Settings.MILESTONES_CHANNEL_ID).sendMessage(String.format("%,d servers :tada:", shardManager.getGuilds().size())).queue();
			this.latestMilestone = shardManager.getGuilds().size();
		}
	}
	
	public void onGuildLeave(GuildLeaveEvent event) {
		ShardManager shardManager = Sx4Bot.getShardManager();
		Guild supportServer = shardManager.getGuildById(Settings.SUPPORT_SERVER_ID);
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setColor(Settings.COLOR_RED);
		embed.setThumbnail(event.getGuild().getIconUrl());
		embed.setTimestamp(Instant.now());
		embed.setDescription(String.format("I am now in %,d servers and connected to %,d users", shardManager.getGuilds().size(), shardManager.getUsers().size()));
		embed.setAuthor("Left Server!", null, event.getJDA().getSelfUser().getEffectiveAvatarUrl());
		embed.addField("Server Name", event.getGuild().getName(), true);
		embed.addField("Server ID", event.getGuild().getId(), true);
		embed.addField("Server Owner", event.getGuild().getOwner().getUser().getAsTag() + "\n" + event.getGuild().getOwnerId(), true);
		embed.addField("Server Members", String.format("%,d member%s", event.getGuild().getMembers().size(), event.getGuild().getMembers().size() == 1 ? "" : "s"), true);
		embed.addField("Stayed for", TimeUtils.toTimeString(Duration.between(event.getGuild().getSelfMember().getTimeJoined(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds(), ChronoUnit.SECONDS), false);
		
		supportServer.getTextChannelById(Settings.SERVER_LOGS_ID).sendMessage(embed.build()).queue();
		
		if (this.latestMilestone == 0) {
			this.latestMilestone = (int) Math.floor((double) shardManager.getGuilds().size() / this.perMilestone) * this.perMilestone;
		}
		
		if (shardManager.getGuilds().size() < this.latestMilestone) {
			supportServer.getTextChannelById(Settings.MILESTONES_CHANNEL_ID).getHistory().retrievePast(1).queue(messages -> {
				messages.get(0).delete().queue();
			});
			
			this.latestMilestone -= this.perMilestone;
		}
	}
	
}
