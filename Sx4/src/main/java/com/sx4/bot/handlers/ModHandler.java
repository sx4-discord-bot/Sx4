package com.sx4.bot.handlers;

import club.minnced.discord.webhook.send.WebhookEmbed;
import com.mongodb.client.model.Projections;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.Database;
import com.sx4.bot.entities.mod.ModLog;
import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.entities.mod.action.Action;
import com.sx4.bot.entities.mod.action.ModAction;
import com.sx4.bot.entities.mod.action.Warn;
import com.sx4.bot.events.mod.*;
import com.sx4.bot.hooks.ModActionListener;
import com.sx4.bot.utility.TimeUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.bson.Document;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.concurrent.TimeUnit;

public class ModHandler implements ModActionListener, EventListener {

	private final Sx4 bot;

	public ModHandler(Sx4 bot) {
		this.bot = bot;
	}

	public EmbedBuilder getGenericEmbed(Guild guild, User moderator, Action action, Reason reason) {
		return new EmbedBuilder()
			.setAuthor(action.toString(), null, guild.getIconUrl())
			.addField("Moderator", moderator.getAsTag() + " (" + moderator.getIdLong() + ")", false)
			.addField("Reason", reason == null ? "None Given" : reason.getParsed(), false)
			.setTimestamp(Instant.now());
	}

	public void handle(Guild guild, Action action, User moderator, User target, Reason reason) {
		ModAction modAction = action.getModAction();
		if (modAction.isOffence()) {
			Document data = new Document("action", action.toData())
				.append("guildId", guild.getIdLong())
				.append("targetId", target.getIdLong())
				.append("moderatorId", moderator.getIdLong());

			if (reason != null) {
				data.append("reason", reason.getParsed());
			}

			this.bot.getDatabase().insertOffence(data).whenComplete(Database.exceptionally(this.bot.getShardManager()));
		}

		Document data = this.bot.getDatabase().getGuildById(guild.getIdLong(), Projections.include("modLog.channelId", "modLog.enabled", "modLog.webhook")).get("modLog", Database.EMPTY_DOCUMENT);

		long channelId = data.get("channelId", 0L);
		if (!data.getBoolean("enabled", false) || channelId == 0L) {
			return;
		}

		TextChannel channel = guild.getTextChannelById(channelId);
		if (channel == null) {
			return;
		}

		ModLog modLog = new ModLog(
			channel.getIdLong(),
			guild.getIdLong(),
			target.getIdLong(),
			moderator.getIdLong(),
			reason,
			action
		);

		WebhookEmbed embed = modLog.getWebhookEmbed(moderator, target);

		this.bot.getModLogManager().sendModLog(channel, data.get("webhook", Database.EMPTY_DOCUMENT), embed).whenComplete((webhookMessage, exception) -> {
			modLog.setMessageId(webhookMessage.getId());

			this.bot.getDatabase().insertModLog(modLog.toData()).whenComplete(Database.exceptionally(this.bot.getShardManager()));
		});
	}
	
	public void onAction(ModActionEvent event) {
		this.handle(event.getGuild(), event.getAction(), event.getModerator().getUser(), event.getTarget(), event.getReason());
	}

	public void onBan(BanEvent event) {
		if (event.wasMember()) {
			EmbedBuilder embed = this.getGenericEmbed(event.getGuild(), event.getModerator().getUser(), event.getAction(), event.getReason());

			event.getTarget().openPrivateChannel()
				.flatMap(channel -> channel.sendMessage(embed.build()))
				.queue(null, ErrorResponseException.ignore(ErrorResponse.CANNOT_SEND_TO_USER));
		}
	}
	
	public void onTemporaryBan(TemporaryBanEvent event) {
		if (event.wasMember()) {
			EmbedBuilder embed = this.getGenericEmbed(event.getGuild(), event.getModerator().getUser(), event.getAction(), event.getReason());

			event.getTarget().openPrivateChannel()
				.flatMap(channel -> channel.sendMessage(embed.build()))
				.queue(null, ErrorResponseException.ignore(ErrorResponse.CANNOT_SEND_TO_USER));
		}
	}
	
	public void onKick(KickEvent event) {
		EmbedBuilder embed = this.getGenericEmbed(event.getGuild(), event.getModerator().getUser(), event.getAction(), event.getReason());

		event.getTarget().openPrivateChannel()
			.flatMap(channel -> channel.sendMessage(embed.build()))
			.queue(null, ErrorResponseException.ignore(ErrorResponse.CANNOT_SEND_TO_USER));
	}
	
	public void onMute(MuteEvent event) {
		EmbedBuilder embed = this.getGenericEmbed(event.getGuild(), event.getModerator().getUser(), event.getAction(), event.getReason());
		embed.getFields().add(0, new MessageEmbed.Field("Duration", TimeUtility.getTimeString(event.getDuration()), false));

		event.getTarget().openPrivateChannel()
			.flatMap(channel -> channel.sendMessage(embed.build()))
			.queue(null, ErrorResponseException.ignore(ErrorResponse.CANNOT_SEND_TO_USER));
	}
	
	public void onWarn(WarnEvent event) {
		Reason reason = event.getReason();
		User moderator = event.getModerator().getUser();
		Warn nextWarn = event.getNextWarning();

		EmbedBuilder embed = new EmbedBuilder()
			.setAuthor(event.getAction().toString(), null, event.getGuild().getIconUrl())
			.addField("Warning", String.valueOf(event.getAction().getWarning().getNumber()), false);

		if (nextWarn != null) {
			embed.addField("Next Action", nextWarn.getAction().toString(), false);
		}

		embed.addField("Moderator", moderator.getAsTag() + " (" + moderator.getIdLong() + ")", false);
		embed.addField("Reason", reason == null ? "None Given" : reason.getParsed(), false);
		embed.setTimestamp(Instant.now());

		event.getTarget().openPrivateChannel()
			.flatMap(channel -> channel.sendMessage(embed.build()))
			.queue(null, ErrorResponseException.ignore(ErrorResponse.CANNOT_SEND_TO_USER));
	}
	
	public void onUnmute(UnmuteEvent event) {
		EmbedBuilder embed = this.getGenericEmbed(event.getGuild(), event.getModerator().getUser(), event.getAction(), event.getReason());

		event.getTarget().openPrivateChannel()
			.flatMap(channel -> channel.sendMessage(embed.build()))
			.queue(null, ErrorResponseException.ignore(ErrorResponse.CANNOT_SEND_TO_USER));
	}


	// TODO: Make a whole event system which contains the AuditLogEntry so I don't have to make this call for both this and logger
	@Override
	public void onEvent(GenericEvent event) {
		Guild guild;
		User user;
		ActionType actionType;
		Action action;

		if (event instanceof GuildMemberRemoveEvent) {
			GuildMemberRemoveEvent removeEvent = (GuildMemberRemoveEvent) event;

			guild = removeEvent.getGuild();
			user = removeEvent.getUser();
			actionType = ActionType.KICK;
			action = new Action(ModAction.KICK);
		} else if (event instanceof GuildBanEvent) {
			GuildBanEvent banEvent = (GuildBanEvent) event;

			guild = banEvent.getGuild();
			user = banEvent.getUser();
			actionType = ActionType.BAN;
			action = new Action(ModAction.BAN);
		} else if (event instanceof GuildUnbanEvent) {
			GuildUnbanEvent unbanEvent = (GuildUnbanEvent) event;

			guild = unbanEvent.getGuild();
			user = unbanEvent.getUser();
			actionType = ActionType.UNBAN;
			action = new Action(ModAction.UNBAN);
		} else {
			return;
		}

		if (guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			guild.retrieveAuditLogs().type(actionType).queueAfter(LoggerHandler.DELAY, TimeUnit.MILLISECONDS, logs -> {
				AuditLogEntry entry = logs.stream()
					.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds() <= 5)
					.filter(e -> e.getTargetIdLong() == user.getIdLong())
					.findFirst()
					.orElse(null);

				if (entry == null) {
					return;
				}

				User moderator = entry.getUser();
				String reason = entry.getReason();

				if (moderator != null && moderator.getIdLong() != guild.getSelfMember().getIdLong()) {
					if (actionType == ActionType.UNBAN) {
						this.bot.getTemporaryBanManager().removeBan(guild.getIdLong(), user.getIdLong(), false);
					}

					this.handle(guild, action, moderator, user, reason == null ? null : new Reason(reason));
				}
			});
		}
	}

}
