package com.sx4.bot.events;

import org.bson.Document;

import com.mongodb.client.FindIterable;
import com.mongodb.client.model.Projections;
import com.sx4.bot.core.Sx4Bot;
import com.sx4.bot.database.Database;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.sharding.ShardManager;

public class AutoroleEvents extends ListenerAdapter {

	public void onGuildMemberJoin(GuildMemberJoinEvent event) {
		Document data = Database.get().getGuildById(event.getGuild().getIdLong(), null, Projections.include("autorole.roleId", "autorole.enabled", "autorole.botRoleId")).get("autorole", Database.EMPTY_DOCUMENT);
		if (data.isEmpty() || data.getBoolean("enabled", false) == false) {
			return;
		}
		
		Member self = event.getGuild().getSelfMember();
		if (!self.hasPermission(Permission.MANAGE_ROLES)) {
			return;
		}
		
		Long roleData = data.getLong("roleId");
		Long botRoleData = data.getLong("botRoleId");
		if (roleData == null && botRoleData == null) {
			return;
		}
		
		if (roleData != null && botRoleData == null) {
			Role role = event.getGuild().getRoleById(roleData);
			if (role != null && self.canInteract(role)) {
				event.getGuild().addRoleToMember(event.getMember(), role).queue();
			}
		} else if (roleData == null && botRoleData != null) {
			if (event.getMember().getUser().isBot()) {
				Role role = event.getGuild().getRoleById(botRoleData);
				if (role != null && self.canInteract(role)) {
					event.getGuild().addRoleToMember(event.getMember(), role).queue();
				}
			}
		} else {
			if (event.getMember().getUser().isBot()) {
				Role role = event.getGuild().getRoleById(botRoleData);
				if (role != null && self.canInteract(role)) {
					event.getGuild().addRoleToMember(event.getMember(), role).queue();
				}
			} else {
				Role role = event.getGuild().getRoleById(roleData);
				if (role != null && self.canInteract(role)) {
					event.getGuild().addRoleToMember(event.getMember(), role).queue();
				}
			}
		}
	}
	
	public static void ensureAutoroles() {
		ShardManager shardManager = Sx4Bot.getShardManager();
		FindIterable<Document> allData = Database.get().getGuilds().find().projection(Projections.include("autorole.autoUpdate", "autorole.enabled", "autorole.roleId", "autorole.botRoleId"));
		allData.forEach((Document data) -> {
			Document autoroleData = data.get("autorole", Database.EMPTY_DOCUMENT);
			if (autoroleData.getBoolean("enabled", false) && autoroleData.getBoolean("autoUpdate", true)) {
				Guild guild = shardManager.getGuildById(data.getLong("_id"));
				if (guild != null) {
					Member self = guild.getSelfMember();
					if (self.hasPermission(Permission.MANAGE_ROLES)) {
						Long roleId = autoroleData.getLong("roleId");
						Long botRoleId = autoroleData.getLong("botRoleId");
						
						Role role = roleId == null ? null : guild.getRoleById(roleId);
						Role botRole = botRoleId == null ? null : guild.getRoleById(botRoleId);
						
						if (botRole != null || role != null) {						
							for (Member member : guild.getMembers()) {
								if (roleId != null && botRoleId == null) {
									if (role != null && self.canInteract(role) && !member.getRoles().contains(role)) {
										guild.addRoleToMember(member, role).queue();
									}
								} else if (roleId == null && botRoleId != null) {
									if (member.getUser().isBot()) {
										if (botRole != null && self.canInteract(botRole) && !member.getRoles().contains(botRole)) {
											guild.addRoleToMember(member, botRole).queue();
										}
									}
								} else {
									if (member.getUser().isBot()) {
										if (botRole != null && self.canInteract(botRole) && !member.getRoles().contains(botRole)) {
											guild.addRoleToMember(member, botRole).queue();
										}
									} else {
										if (role != null && self.canInteract(role) && !member.getRoles().contains(role)) {
											guild.addRoleToMember(member, role).queue();
										}
									}
								}
							}
						} 
					}
				}
			}
		});
	}
	
}
