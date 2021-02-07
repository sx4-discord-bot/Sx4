package com.sx4.bot.handlers;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.sx4.bot.database.Database;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bson.Document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AutoRoleHandler extends ListenerAdapter {

	private final Database database = Database.get();
	
	public void onGuildMemberJoin(GuildMemberJoinEvent event) {
		Guild guild = event.getGuild();
		Member member = event.getMember();
		
		Member selfMember = guild.getSelfMember();
		if (!selfMember.hasPermission(Permission.MANAGE_ROLES)) {
			return;
		}
		
		Document data = this.database.getGuildById(guild.getIdLong(), Projections.include("autoRole.roles", "autoRole.enabled")).get("autoRole", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false)) {
			return;
		}
		
		List<Document> rolesData = data.getList("roles", Document.class, Collections.emptyList());

		List<Role> roles = new ArrayList<>();
		Roles : for (Document roleData : rolesData) {
			Role role = guild.getRoleById(roleData.getLong("id"));
			if (role == null || !selfMember.canInteract(role)) {
				continue;
			}

			List<Document> filters = roleData.getList("filters", Document.class, Collections.emptyList());
			for (Document filter : filters) {
				String key = filter.getString("key");
				Object value = filter.get("value");
				
				switch (key) {
					case "BOT":
						if (member.getUser().isBot() != (boolean) value) {
							continue Roles;
						}
						
						break;
					default:
						break;
				}
			}

			roles.add(role);
		}
		
		if (!roles.isEmpty()) {
			guild.modifyMemberRoles(member, roles, null).queue();
		}
	}
	
	public void onRoleDelete(RoleDeleteEvent event) {
		Database.get().updateGuildById(event.getGuild().getIdLong(), Updates.pull("autoRole.roles", Filters.eq("id", event.getRole().getIdLong())))
			.whenComplete(Database.exceptionally());
	}

}
