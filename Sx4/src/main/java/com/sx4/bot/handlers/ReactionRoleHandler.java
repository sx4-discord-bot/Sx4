package com.sx4.bot.handlers;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
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
		
		if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
			user.openPrivateChannel()
				.flatMap(channel -> channel.sendMessage("I am missing the `" + Permission.MANAGE_ROLES.getName() + "` permission " + config.getFailureEmote()))
				.queue(null, ErrorResponseException.ignore(ErrorResponse.CANNOT_SEND_TO_USER));
			
			return;
		}
		
		List<Document> reactionRoles = Database.get().getGuildById(guild.getIdLong(), Projections.include("reactionRole.reactionRoles")).getEmbedded(List.of("reactionRole", "reactionRoles"), Collections.emptyList());
		
		Document reactionRole = reactionRoles.stream()
			.filter(data -> data.getLong("id") == event.getMessageIdLong())
			.findFirst()
			.orElse(null);
		
		if (reactionRole == null) {
			return;
		}
		
		int reactedTo = 0;
		boolean remove = false;
		List<Document> whitelists = Collections.emptyList();

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
					whitelists = data.getList("whitelist", Document.class, Collections.emptyList());
					remove = removeData;
				}
			} else {
				if (emoteData.getLong("id") == emote.getEmote().getIdLong()) {
					roles = rolesData;
					whitelists = data.getList("whitelist", Document.class, Collections.emptyList());
					remove = removeData;
				}
			}
		}
		
		if (roles == null || roles.isEmpty()) {
			return;
		}

		if (!remove) {
			for (int i = 0; i < whitelists.size(); i++) {
				Document whitelist = whitelists.get(i);

				long holderId = whitelist.getLong("id");
				if (whitelist.getInteger("type") == HolderType.USER.getType()) {
					if (holderId == user.getIdLong()) {
						break;
					}
				} else {
					if (memberRoles.stream().anyMatch(role -> role.getIdLong() == holderId)) {
						break;
					}
				}

				if (i == whitelists.size() - 1) {
					user.openPrivateChannel()
						.flatMap(channel -> channel.sendMessage("You are not whitelisted to be able to get the roles behind this reaction  " + config.getFailureEmote()))
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
	
	public void handle(long guildId, List<Long> messageIds) {
		Database.get().updateGuildById(guildId, Updates.pull("reactionRole.reactionRoles", Filters.in("id", messageIds))).whenComplete((result, exception) -> ExceptionUtility.sendErrorMessage(exception));
	}
	
	public void onMessageBulkDelete(MessageBulkDeleteEvent event) {
		this.handle(event.getGuild().getIdLong(), event.getMessageIds().stream().map(Long::valueOf).collect(Collectors.toList()));
	}
	
	public void onMessageDelete(MessageDeleteEvent event) {
		this.handle(event.getGuild().getIdLong(), List.of(event.getMessageIdLong()));
	}
	
	public void onRoleDelete(RoleDeleteEvent event) {
		Database.get().updateGuildById(event.getGuild().getIdLong(), Updates.pull("reactionRole.reactionRoles.$[].reactions.$[].roles", event.getRole().getIdLong())).whenComplete((result, exception) -> ExceptionUtility.sendErrorMessage(exception));
	}
	
}
