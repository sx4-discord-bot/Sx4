package com.sx4.bot.handlers;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.bson.Document;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.sx4.bot.database.Database;
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
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.ErrorResponse;

public class ReactionRoleHandler extends ListenerAdapter {

	public void onGenericGuildMessageReaction(GenericGuildMessageReactionEvent event) {
		User user = event.getUser();
		Guild guild = event.getGuild();
		ReactionEmote emote = event.getReactionEmote();
		
		
		if (user.isBot()) {
			return;
		}
		
		if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
			user.openPrivateChannel()
				.flatMap(channel -> channel.sendMessage("I am missing the `" + Permission.MANAGE_ROLES.getName() + "` permission :no_entry:"))
				.queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
			
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
		
		List<Role> memberRoles = event.getMember().getRoles(), roles = null;
		for (Document data : reactionRole.getList("reactions", Document.class)) {
			List<Role> rolesData = data.getList("roles", Long.class).stream()
				.map(guild::getRoleById)
				.filter(role -> role != null)
				.filter(role -> guild.getSelfMember().canInteract(role))
				.collect(Collectors.toList());
			
			if (memberRoles.containsAll(rolesData)) {
				reactedTo++;
			}
			
			Document emoteData = data.get("emote", Document.class);
			if (emote.isEmoji()) {
				if (emoteData.containsKey("name") && emoteData.getString("name").equals(emote.getEmoji())) {
					roles = rolesData;
				}
			} else {
				if (emoteData.get("id", 0L) == emote.getEmote().getIdLong()) {
					roles = rolesData;
				}
			}
		}
		
		if (roles == null || roles.isEmpty()) {
			return;
		}
		
		int maxReactions = reactionRole.getInteger("maxReactions", 0);
		if (reactedTo >= maxReactions && maxReactions != 0) {
			user.openPrivateChannel()
				.flatMap(channel -> channel.sendMessage("You can only react to **" + maxReactions + "** reaction" + (maxReactions == 1 ? "" : "s") + " on this message :no_entry:"))
				.queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
			
			return;
		}
		
		boolean remove = memberRoles.containsAll(roles);
		if (remove) {
			guild.modifyMemberRoles(event.getMember(), null, roles).queue();
		} else {
			guild.modifyMemberRoles(event.getMember(), roles, null).queue();
		}
		
		String message = "You " + (remove ? "no longer" : "now") + " have the role" + (roles.size() == 1 ? "" : "s") + " `" + roles.stream().map(Role::getName).collect(Collectors.joining("`, `")) + "` <:done:403285928233402378>";
		if (reactionRole.getBoolean("dm", true)) {
			user.openPrivateChannel()
				.flatMap(channel -> channel.sendMessage(message))
				.queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
		}
	}
	
	public void checkReactionRole(long guildId, List<Long> messageIds) {
		Database.get().updateGuildById(guildId, Updates.pull("reactionRole.reactionRoles", Filters.in("id", messageIds))).whenComplete((result, exception) -> {
			if (exception != null) {
				ExceptionUtility.sendErrorMessage(exception);
			}
		});
	}
	
	public void onMessageBulkDelete(MessageBulkDeleteEvent event) {
		this.checkReactionRole(event.getGuild().getIdLong(), event.getMessageIds().stream().map(Long::valueOf).collect(Collectors.toList()));
	}
	
	public void onMessageDelete(MessageDeleteEvent event) {
		this.checkReactionRole(event.getGuild().getIdLong(), List.of(event.getMessageIdLong()));
	}
	
	public void onRoleDelete(RoleDeleteEvent event) {
		Database.get().updateGuildById(event.getGuild().getIdLong(), Updates.pull("reactionRole.reactionRoles.$[].reactions.$[].roles", event.getRole().getIdLong())).whenComplete((result, exception) -> {
			if (exception != null) {
				ExceptionUtility.sendErrorMessage(exception);
			}
		});
	}
	
}
