package com.sx4.bot.handlers;

import com.mongodb.MongoWriteException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.sx4.bot.config.Config;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.entities.settings.HolderType;
import com.sx4.bot.utility.ExceptionUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageReaction.ReactionEmote;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.react.GenericMessageReactionEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.bson.Document;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

public class ReactionRoleHandler implements EventListener {

	private final Sx4 bot;

	public ReactionRoleHandler(Sx4 bot) {
		this.bot = bot;
	}

	public void onGenericMessageReaction(GenericMessageReactionEvent event) {
		if (!event.isFromGuild()) {
			return;
		}

		User user = event.getUser();
		Guild guild = event.getGuild();
		ReactionEmote emote = event.getReactionEmote();
		
		if (user == null || user.isBot()) {
			return;
		}
		
		Config config = this.bot.getConfig();

		List<Document> reactionRoles = this.bot.getMongo().getReactionRoles(Filters.eq("messageId", event.getMessageIdLong()), MongoDatabase.EMPTY_DOCUMENT).into(new ArrayList<>());
		if (reactionRoles.isEmpty()) {
			return;
		}

		if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
			user.openPrivateChannel()
				.flatMap(channel -> channel.sendMessage("I am missing the `" + Permission.MANAGE_ROLES.getName() + "` permission " + config.getFailureEmote()))
				.queue(null, ErrorResponseException.ignore(ErrorResponse.CANNOT_SEND_TO_USER));

			return;
		}

		int reactedTo = 0;

		List<Role> roles = null;
		Document reactionRole = null;
		boolean remove = false;

		Set<Role> memberRoles = new HashSet<>(event.getMember().getRoles());
		for (Document data : reactionRoles) {
			List<Role> rolesData = data.getList("roles", Long.class).stream()
				.map(guild::getRoleById)
				.filter(Objects::nonNull)
				.filter(role -> guild.getSelfMember().canInteract(role))
				.collect(Collectors.toList());

			boolean allRoles = memberRoles.containsAll(rolesData);
			if (allRoles) {
				reactedTo++;
			}
			
			Document emoteData = data.get("emote", Document.class);
			if ((emote.isEmoji() && emoteData.containsKey("name") && emoteData.getString("name").equals(emote.getEmoji())) || (emote.isEmote() && emoteData.containsKey("id") && emoteData.getLong("id") == emote.getEmote().getIdLong())) {
				if (rolesData.isEmpty()) {
					return;
				}

				roles = rolesData;
				reactionRole = data;
				remove = allRoles;
			}
		}

		if (roles == null) {
			return;
		}

		if (!remove) {
			List<Document> permissions = reactionRole.getList("permissions", Document.class, Collections.emptyList());
			for (int i = 0; i < permissions.size(); i++) {
				Document permission = permissions.get(i);

				long holderId = permission.getLong("id");
				int type = permission.getInteger("type");
				boolean granted = permission.getBoolean("granted");

				if (type == HolderType.USER.getType() && holderId == user.getIdLong()) {
					if (granted) {
						break;
					}
				} else if (type == HolderType.ROLE.getType() && (holderId == guild.getIdLong() || memberRoles.stream().anyMatch(role -> role.getIdLong() == holderId))) {
					if (granted) {
						break;
					}
				}

				if (i == permissions.size() - 1) {
					user.openPrivateChannel()
						.flatMap(channel -> channel.sendMessage("You are not whitelisted to be able to get the roles behind this reaction " + config.getFailureEmote()))
						.queue(null, ErrorResponseException.ignore(ErrorResponse.CANNOT_SEND_TO_USER));

					return;
				}
			}
		}

		int maxReactions = reactionRole.get("maxReactions", 0);
		if (!remove && reactedTo >= maxReactions && maxReactions != 0) {
			user.openPrivateChannel()
				.flatMap(channel -> channel.sendMessage("You can only react to **" + maxReactions + "** reaction" + (maxReactions == 1 ? "" : "s") + " on this message " + config.getFailureEmote()))
				.queue(null, ErrorResponseException.ignore(ErrorResponse.CANNOT_SEND_TO_USER));

			return;
		}

		if (remove) {
			guild.modifyMemberRoles(event.getMember(), null, roles).queue();
		} else {
			guild.modifyMemberRoles(event.getMember(), roles, null).queue();
		}

		String message = "You " + (remove ? "no longer" : "now") + " have the role" + (roles.size() == 1 ? "" : "s") + " `" + roles.stream().map(Role::getName).collect(Collectors.joining("`, `")) + "` " + config.getSuccessEmote();
		if (reactionRole.getBoolean("dm", true)) {
			user.openPrivateChannel()
				.flatMap(channel -> channel.sendMessage(message))
				.queue(null, ErrorResponseException.ignore(ErrorResponse.CANNOT_SEND_TO_USER));
		}
	}
	
	public void handle(List<Long> messageIds) {
		this.bot.getMongo().deleteManyReactionRoles(Filters.in("messageId", messageIds)).whenComplete(MongoDatabase.exceptionally());
	}
	
	public void onRoleDelete(RoleDeleteEvent event) {
		this.bot.getMongo().updateReactionRole(Filters.eq("guildId", event.getGuild().getIdLong()), Updates.pull("roles", event.getRole().getIdLong()), new UpdateOptions()).whenComplete((result, exception) -> {
			Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
			if (cause instanceof MongoWriteException && ((MongoWriteException) cause).getCode() == 2) {
				return;
			}

			ExceptionUtility.sendErrorMessage(exception);
		});
	}

	@Override
	public void onEvent(@NotNull GenericEvent event) {
		if (event instanceof GenericMessageReactionEvent) {
			this.onGenericMessageReaction((GenericMessageReactionEvent) event);
		} else if (event instanceof MessageDeleteEvent) {
			this.handle(List.of(((MessageDeleteEvent) event).getMessageIdLong()));
		} else if (event instanceof MessageBulkDeleteEvent) {
			this.handle(((MessageBulkDeleteEvent) event).getMessageIds().stream().map(Long::parseLong).collect(Collectors.toList()));
		} else if (event instanceof RoleDeleteEvent) {
			this.onRoleDelete((RoleDeleteEvent) event);
		}
	}
}
