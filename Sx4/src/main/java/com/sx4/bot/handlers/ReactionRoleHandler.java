package com.sx4.bot.handlers;

import com.mongodb.MongoWriteException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.sx4.bot.config.Config;
import com.sx4.bot.database.Database;
import com.sx4.bot.entities.settings.HolderType;
import com.sx4.bot.utility.ExceptionUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageReaction.ReactionEmote;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.guild.react.GenericGuildMessageReactionEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.bson.Document;

import java.util.*;
import java.util.stream.Collectors;

public class ReactionRoleHandler extends ListenerAdapter {

	public void onGenericGuildMessageReaction(GenericGuildMessageReactionEvent event) {
		User user = event.getUser();
		Guild guild = event.getGuild();
		ReactionEmote emote = event.getReactionEmote();
		
		if (user.isBot()) {
			return;
		}
		
		Config config = Config.get();
		
		Document reactionRole = Database.get().getReactionRole(Filters.eq("messageId", event.getMessageIdLong()), Database.EMPTY_DOCUMENT);
		if (reactionRole == null) {
			return;
		}

		if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
			user.openPrivateChannel()
				.flatMap(channel -> channel.sendMessage("I am missing the `" + Permission.MANAGE_ROLES.getName() + "` permission " + config.getFailureEmote()))
				.queue(null, ErrorResponseException.ignore(ErrorResponse.CANNOT_SEND_TO_USER));

			return;
		}
		
		int reactedTo = 0;
		boolean remove = false;
		List<Document> permissions = Collections.emptyList();

		Set<Role> memberRoles = new HashSet<>(event.getMember().getRoles());
		List<Role> roles = null;
		for (Document data : reactionRole.getList("reactions", Document.class)) {
			List<Role> rolesData = data.getList("roles", Long.class).stream()
				.map(guild::getRoleById)
				.filter(Objects::nonNull)
				.filter(role -> guild.getSelfMember().canInteract(role))
				.collect(Collectors.toList());
			
			boolean removeData = memberRoles.containsAll(rolesData);
			if (removeData) {
				reactedTo++;
			}
			
			Document emoteData = data.get("emote", Document.class);
			if (emote.isEmoji()) {
				if (emoteData.containsKey("name") && emoteData.getString("name").equals(emote.getEmoji())) {
					roles = rolesData;
					permissions = data.getList("permissions", Document.class, Collections.emptyList());
					remove = removeData;
				}
			} else {
				if (emoteData.getLong("id") == emote.getEmote().getIdLong()) {
					roles = rolesData;
					permissions = data.getList("permissions", Document.class, Collections.emptyList());
					remove = removeData;
				}
			}
		}
		
		if (roles == null || roles.isEmpty()) {
			return;
		}

		if (!remove) {
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
		if (reactedTo >= maxReactions && maxReactions != 0) {
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
		Database.get().deleteManyReactionRoles(Filters.in("messageId", messageIds)).whenComplete(Database.exceptionally());
	}
	
	public void onMessageBulkDelete(MessageBulkDeleteEvent event) {
		this.handle(event.getMessageIds().stream().map(Long::valueOf).collect(Collectors.toList()));
	}
	
	public void onMessageDelete(MessageDeleteEvent event) {
		this.handle(List.of(event.getMessageIdLong()));
	}
	
	public void onRoleDelete(RoleDeleteEvent event) {
		Database.get().updateReactionRole(Filters.eq("guildId", event.getGuild().getIdLong()), Updates.pull("reactions.$[].roles", event.getRole().getIdLong())).whenComplete((result, exception) -> {
			Throwable cause = exception == null ? null : exception.getCause();
			if (cause instanceof MongoWriteException && ((MongoWriteException) cause).getCode() == 2) {
				return;
			}

			ExceptionUtility.sendErrorMessage(exception);
		});
	}
	
}
