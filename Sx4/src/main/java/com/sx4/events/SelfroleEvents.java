package com.sx4.events;

import java.util.Collections;
import java.util.List;

import org.bson.Document;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.sx4.database.Database;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.guild.GuildMessageDeleteEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionRemoveEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class SelfroleEvents extends ListenerAdapter {

	public void onGuildMessageReactionAdd(GuildMessageReactionAddEvent event) {
		if (event.getMember().getUser().isBot()) {
			return;
		}
		
		Document data = Database.get().getGuildById(event.getGuild().getIdLong(), null, Projections.include("reactionRole.reactionRoles", "reactionRole.dm")).get("reactionRoles", Database.EMPTY_DOCUMENT);
		List<Document> reactionRoles = data.getList("reactionRoles", Document.class, Collections.emptyList()); 
		for (Document reactionRole : reactionRoles) {
			if (event.getMessageIdLong() == reactionRole.getLong("id")) {
				List<Document> roles = reactionRole.getList("roles", Document.class, Collections.emptyList());
				
				Long roleId = null;
				if (event.getReactionEmote().isEmote()) {
					for (Document roleData : roles) {
						Long emoteId = roleData.getEmbedded(List.of("emote", "id"), Long.class);
						if (emoteId != null && emoteId == event.getReactionEmote().getIdLong()) {
							roleId = roleData.getLong("id");
						}
					}
				} else {
					for (Document roleData : roles) {
						String emoteName = roleData.getEmbedded(List.of("emote", "name"), String.class);
						if (emoteName != null && emoteName.equals(event.getReactionEmote().getName())) {
							roleId = roleData.getLong("id");
						}
					}
				}
				
				if (roleId != null) { 
					Role role = event.getGuild().getRoleById(roleId);
					if (role == null) {
						event.getMember().getUser().openPrivateChannel().queue(channel -> channel.sendMessage("The role corresponding to that emote has been deleted :no_entry:").queue(), e -> {});
						return;
					} else {
						if (!event.getGuild().getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
							event.getMember().getUser().openPrivateChannel().queue(channel -> channel.sendMessage("I do not have the `Manage Roles` permission so I cannot give you the role :no_entry:").queue(), e -> {});
							return;
						}
						
						if (!event.getGuild().getSelfMember().canInteract(role)) {
							event.getMember().getUser().openPrivateChannel().queue(channel -> channel.sendMessage("That role is higher or equal to my top role so I am unable to give it to you :no_entry:").queue(), e -> {});
							return;
						}
						
						if (event.getMember().getRoles().contains(role)) {
							event.getGuild().removeRoleFromMember(event.getMember(), role).queue();
							if (data.getBoolean("dm", true)) {
								event.getMember().getUser().openPrivateChannel().queue(channel -> channel.sendMessage("You no longer have the role **" + role.getName() + "** <:done:403285928233402378>").queue(), e -> {});
							}
						} else {
							int memberRoles = 0;
							for (Role memberRole : event.getMember().getRoles()) {
								for (Document roleData : roles) {
									if (memberRole.getIdLong() == roleData.getLong("id")) {
										memberRoles++;
									}
								}
							}
							
							long maxRoles = reactionRole.getInteger("maxRoles", 0);
							if (maxRoles != 0 && memberRoles >= maxRoles) {
								event.getMember().getUser().openPrivateChannel().queue(channel -> channel.sendMessage("You already have the max amount of roles from this reaction role menu, the max amount is **" + maxRoles + "** role" + (maxRoles == 1 ? "" : "s") + " :no_entry:").queue(), e -> {});
								return;
							}
							
							event.getGuild().addRoleToMember(event.getMember(), role).queue();
							if (data.getBoolean("dm", true)) {
								event.getMember().getUser().openPrivateChannel().queue(channel -> channel.sendMessage("You now have the role **" + role.getName() + "** <:done:403285928233402378>").queue(), e -> {});
							}
						}
					}
				}
			}
		}
	}
	
	public void onGuildMessageReactionRemove(GuildMessageReactionRemoveEvent event) {
		if (event.getMember().getUser().isBot()) {
			return;
		}
		
		Document data = Database.get().getGuildById(event.getGuild().getIdLong(), null, Projections.include("reactionRole.reactionRoles", "reactionRole.dm")).get("reactionRoles", Database.EMPTY_DOCUMENT);
		List<Document> reactionRoles = data.getList("reactionRoles", Document.class, Collections.emptyList()); 
		for (Document reactionRole : reactionRoles) {
			if (event.getMessageIdLong() == reactionRole.getLong("id")) {
				List<Document> roles = reactionRole.getList("roles", Document.class, Collections.emptyList());
				
				Long roleId = null;
				if (event.getReactionEmote().isEmote()) {
					for (Document roleData : roles) {
						Long emoteId = roleData.getEmbedded(List.of("emote", "id"), Long.class);
						if (emoteId != null && emoteId == event.getReactionEmote().getIdLong()) {
							roleId = roleData.getLong("id");
						}
					}
				} else {
					for (Document roleData : roles) {
						String emoteName = roleData.getEmbedded(List.of("emote", "name"), String.class);
						if (emoteName != null && emoteName.equals(event.getReactionEmote().getName())) {
							roleId = roleData.getLong("id");
						}
					}
				}
				
				if (roleId != null) { 
					Role role = event.getGuild().getRoleById(roleId);
					if (role == null) {
						event.getMember().getUser().openPrivateChannel().queue(channel -> channel.sendMessage("The role corresponding to that emote has been deleted :no_entry:").queue(), e -> {});
						return;
					} else {
						if (!event.getGuild().getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
							event.getMember().getUser().openPrivateChannel().queue(channel -> channel.sendMessage("I do not have the `Manage Roles` permission so I cannot give you the role :no_entry:").queue(), e -> {});
							return;
						}
						
						if (!event.getGuild().getSelfMember().canInteract(role)) {
							event.getMember().getUser().openPrivateChannel().queue(channel -> channel.sendMessage("That role is higher or equal to my top role so I am unable to give it to you :no_entry:").queue(), e -> {});
							return;
						}
						
						if (event.getMember().getRoles().contains(role)) {
							event.getGuild().removeRoleFromMember(event.getMember(), role).queue();
							if (data.getBoolean("dm", true)) {
								event.getMember().getUser().openPrivateChannel().queue(channel -> channel.sendMessage("You no longer have the role **" + role.getName() + "** <:done:403285928233402378>").queue(), e -> {});
							}
						} else {
							int memberRoles = 0;
							for (Role memberRole : event.getMember().getRoles()) {
								for (Document roleData : roles) {
									if (memberRole.getIdLong() == roleData.getLong("id")) {
										memberRoles++;
									}
								}
							}
							
							long maxRoles = reactionRole.getInteger("maxRoles", 0);
							if (maxRoles != 0 && memberRoles >= maxRoles) {
								event.getMember().getUser().openPrivateChannel().queue(channel -> channel.sendMessage("You already have the max amount of roles from this reaction role menu, the max amount is **" + maxRoles + "** role" + (maxRoles == 1 ? "" : "s") + " :no_entry:").queue(), e -> {});
								return;
							}
							
							event.getGuild().addRoleToMember(event.getMember(), role).queue();
							if (data.getBoolean("dm", true)) {
								event.getMember().getUser().openPrivateChannel().queue(channel -> channel.sendMessage("You now have the role **" + role.getName() + "** <:done:403285928233402378>").queue(), e -> {});
							}
						}
					}
				}
			}
		}
	}
	
	public void onGuildMessageDelete(GuildMessageDeleteEvent event) {
		List<Document> reactionRoles = Database.get().getGuildById(event.getGuild().getIdLong(), null, Projections.include("reactionRole.reactionRoles")).getEmbedded(List.of("reactionRole", "reactionRoles"), Collections.emptyList());
		for (Document reactionRole : reactionRoles) {
			if (event.getMessageIdLong() == reactionRole.getLong("id")) {
				Database.get().updateGuildById(event.getGuild().getIdLong(), Updates.pull("reactionRole.reactionRoles", Filters.eq("id", event.getMessageIdLong())), (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
					}
				});
			}
		}
	}
	
	public void onRoleDelete(RoleDeleteEvent event) {
		List<Long> selfRoles = Database.get().getGuildById(event.getGuild().getIdLong(), null, Projections.include("selfRoles")).getList("selfRoles", Long.class, Collections.emptyList());
		for (Long selfRoleId : selfRoles) {
			if (selfRoleId == event.getRole().getIdLong()) {
				Database.get().updateGuildById(event.getGuild().getIdLong(), Updates.pull("selfRoles", selfRoleId), (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
					}
				});
			}
		}
	}
	
}
