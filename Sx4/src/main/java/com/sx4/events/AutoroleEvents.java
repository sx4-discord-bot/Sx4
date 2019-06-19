package com.sx4.events;

import static com.rethinkdb.RethinkDB.r;

import java.util.List;
import java.util.Map;

import com.rethinkdb.net.Cursor;
import com.sx4.core.Sx4Bot;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.sharding.ShardManager;

public class AutoroleEvents extends ListenerAdapter {

	public void onGuildMemberJoin(GuildMemberJoinEvent event) {
		Map<String, Object> data = r.table("autorole").get(event.getGuild().getId()).run(Sx4Bot.getConnection());
		if (data == null || (boolean) data.get("toggle") == false || (data.get("role") == null && data.get("botrole") == null)) {
			return;
		}
		
		Member self = event.getGuild().getSelfMember();
		if (!self.hasPermission(Permission.MANAGE_ROLES)) {
			return;
		}
		
		String roleData = (String) data.get("role");
		String botRoleData = (String) data.get("botrole");
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
		
		try (Cursor<Map<String, Object>> cursor = r.table("autorole").run(Sx4Bot.getConnection())) {
			List<Map<String, Object>> data = cursor.toList();
			for (Map<String, Object> guildData : data) {
				if ((boolean) guildData.get("toggle") == false || (boolean) guildData.get("auto_update") == false) {
					continue;
				}
				
				Guild guild = shardManager.getGuildById((String) guildData.get("id"));
				if (guild != null) {
					Member self = guild.getSelfMember();
					if (!self.hasPermission(Permission.MANAGE_ROLES)) {
						continue;
					}
					
					String roleData = (String) guildData.get("role");
					String botRoleData = (String) guildData.get("botrole");
					
					Role role = null, botRole = null;
					if (roleData != null) {
						role = guild.getRoleById(roleData);
					} 
					
					if (botRoleData != null) {
						botRole = guild.getRoleById(botRoleData);
					}
					
					if (botRole == null && role == null) {
						continue;
					}
					
					for (Member member : guild.getMembers()) {
						if (roleData != null && botRoleData == null) {
							if (role != null && self.canInteract(role) && !member.getRoles().contains(role)) {
								guild.addRoleToMember(member, role).queue();
							}
						} else if (roleData == null && botRoleData != null) {
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
	
}
