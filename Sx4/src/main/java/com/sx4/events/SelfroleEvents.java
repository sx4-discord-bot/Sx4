package com.sx4.events;

import static com.rethinkdb.RethinkDB.r;

import java.util.List;
import java.util.Map;

import com.rethinkdb.gen.ast.Get;
import com.sx4.core.Sx4Bot;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.guild.GuildMessageDeleteEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionRemoveEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class SelfroleEvents extends ListenerAdapter {

	@SuppressWarnings("unchecked")
	public void onGuildMessageReactionAdd(GuildMessageReactionAddEvent event) {
		if (event.getMember().getUser().isBot()) {
			return;
		}
		
		Map<String, Object> data = r.table("reactionrole").get(event.getGuild().getId()).run(Sx4Bot.getConnection());	
		if (data == null) {
			return;
		}
		
		List<Map<String, Object>> messages = (List<Map<String, Object>>) data.get("messages");
		for (Map<String, Object> messageData : messages) {
			if (event.getMessageId().equals(messageData.get("id"))) {
				List<Map<String, Object>> roles = (List<Map<String, Object>>) messageData.get("roles");
				
				String roleId = null;
				if (event.getReactionEmote().isEmote()) {
					for (Map<String, Object> roleData : roles) {
						if (roleData.get("emote").equals(event.getReactionEmote().getId())) {
							roleId = (String) roleData.get("id");
						}
					}
				} else {
					for (Map<String, Object> roleData : roles) {
						if (roleData.get("emote").equals(event.getReactionEmote().getName())) {
							roleId = (String) roleData.get("id");
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
						
						if (role.getPosition() >= event.getGuild().getSelfMember().getRoles().get(0).getPosition()) {
							event.getMember().getUser().openPrivateChannel().queue(channel -> channel.sendMessage("That role is higher or equal to my top role so I am unable to give it to you :no_entry:").queue(), e -> {});
							return;
						}
						
						if (event.getMember().getRoles().contains(role)) {
							event.getGuild().removeRoleFromMember(event.getMember(), role).queue();
							if ((boolean) data.get("dm")) {
								event.getMember().getUser().openPrivateChannel().queue(channel -> channel.sendMessage("You no longer have the role **" + role.getName() + "** <:done:403285928233402378>").queue(), e -> {});
							}
						} else {
							if (messageData.containsKey("max_roles")) {
								int memberRoles = 0;
								for (Role memberRole : event.getMember().getRoles()) {
									for (Map<String, Object> roleData : roles) {
										if (memberRole.getId().equals(roleData.get("id"))) {
											memberRoles += 1;
										}
									}
								}
								
								long maxRoles = (long) messageData.get("max_roles");
								if (maxRoles != 0 && memberRoles >= maxRoles) {
									event.getMember().getUser().openPrivateChannel().queue(channel -> channel.sendMessage("You already have the max amount of roles from this reaction role menu, the max amount is **" + maxRoles + "** role" + (maxRoles == 1 ? "" : "s") + " :no_entry:").queue(), e -> {});
									return;
								}
							}
							
							event.getGuild().addRoleToMember(event.getMember(), role).queue();
							if ((boolean) data.get("dm")) {
								event.getMember().getUser().openPrivateChannel().queue(channel -> channel.sendMessage("You now have the role **" + role.getName() + "** <:done:403285928233402378>").queue(), e -> {});
							}
						}
					}
				}
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	public void onGuildMessageReactionRemove(GuildMessageReactionRemoveEvent event) {
		if (event.getMember().getUser().isBot()) {
			return;
		}
		
		Map<String, Object> data = r.table("reactionrole").get(event.getGuild().getId()).run(Sx4Bot.getConnection());	
		if (data == null) {
			return;
		}
		
		List<Map<String, Object>> messages = (List<Map<String, Object>>) data.get("messages");
		for (Map<String, Object> messageData : messages) {
			if (event.getMessageId().equals(messageData.get("id"))) {
				List<Map<String, Object>> roles = (List<Map<String, Object>>) messageData.get("roles");
				
				String roleId = null;
				if (event.getReactionEmote().isEmote()) {
					for (Map<String, Object> roleData : roles) {
						if (roleData.get("emote").equals(event.getReactionEmote().getId())) {
							roleId = (String) roleData.get("id");
						}
					}
				} else {
					for (Map<String, Object> roleData : roles) {
						if (roleData.get("emote").equals(event.getReactionEmote().getName())) {
							roleId = (String) roleData.get("id");
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
						
						if (role.getPosition() >= event.getGuild().getSelfMember().getRoles().get(0).getPosition()) {
							event.getMember().getUser().openPrivateChannel().queue(channel -> channel.sendMessage("That role is higher or equal to my top role so I am unable to give it to you :no_entry:").queue(), e -> {});
							return;
						}
						
						if (event.getMember().getRoles().contains(role)) {
							event.getGuild().removeRoleFromMember(event.getMember(), role).queue();
							if ((boolean) data.get("dm")) {
								event.getMember().getUser().openPrivateChannel().queue(channel -> channel.sendMessage("You no longer have the role **" + role.getName() + "** <:done:403285928233402378>").queue(), e -> {});
							}
						} else {
							if (messageData.containsKey("max_roles")) {
								int memberRoles = 0;
								for (Role memberRole : event.getMember().getRoles()) {
									for (Map<String, Object> roleData : roles) {
										if (memberRole.getId().equals(roleData.get("id"))) {
											memberRoles += 1;
										}
									}
								}
								
								long maxRoles = (long) messageData.get("max_roles");
								if (maxRoles != 0 && memberRoles >= maxRoles) {
									event.getMember().getUser().openPrivateChannel().queue(channel -> channel.sendMessage("You already have the max amount of roles from this reaction role menu, the max amount is **" + maxRoles + "** role" + (maxRoles == 1 ? "" : "s") + " :no_entry:").queue(), e -> {});
									return;
								}
							}
							
							event.getGuild().addRoleToMember(event.getMember(), role).queue();
							if ((boolean) data.get("dm")) {
								event.getMember().getUser().openPrivateChannel().queue(channel -> channel.sendMessage("You now have the role **" + role.getName() + "** <:done:403285928233402378>").queue(), e -> {});
							}
						}
					}
				}
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	public void onGuildMessageDelete(GuildMessageDeleteEvent event) {
		Get data = r.table("reactionrole").get(event.getGuild().getId());
		Map<String, Object> dataRan = data.run(Sx4Bot.getConnection());
		if (dataRan == null) {
			return;
		}
		
		List<Map<String, Object>> messages = (List<Map<String, Object>>) dataRan.get("messages");
		for (Map<String, Object> messageData : messages) {
			if (event.getMessageId().equals(messageData.get("id"))) {
				data.update(row -> r.hashMap("messages", row.g("messages").filter(d -> d.g("id").ne(event.getMessageId())))).runNoReply(Sx4Bot.getConnection());
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	public void onRoleDelete(RoleDeleteEvent event) {
		Get data = r.table("selfroles").get(event.getGuild().getId());
		Map<String, Object> dataRan = data.run(Sx4Bot.getConnection());
		if (dataRan == null) {
			return;
		}
		
		List<String> roles = (List<String>) dataRan.get("roles");
		for (String roleId : roles) {
			if (roleId.equals(event.getRole().getId())) {
				data.update(row -> r.hashMap("roles", row.g("roles").filter(d -> d.ne(roleId)))).runNoReply(Sx4Bot.getConnection());
			}
		}
	}
	
}
