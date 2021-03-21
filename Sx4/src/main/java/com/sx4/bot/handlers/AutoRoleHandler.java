package com.sx4.bot.handlers;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.Database;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import org.bson.Document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AutoRoleHandler implements EventListener {

	private final Sx4 bot;

	public AutoRoleHandler(Sx4 bot) {
		this.bot = bot;
	}
	
	public void onGuildMemberJoin(GuildMemberJoinEvent event) {
		Guild guild = event.getGuild();
		Member member = event.getMember();
		
		Member selfMember = guild.getSelfMember();
		if (!selfMember.hasPermission(Permission.MANAGE_ROLES)) {
			return;
		}
		
		Document data = this.bot.getDatabase().getGuildById(guild.getIdLong(), Projections.include("autoRole.roles", "autoRole.enabled")).get("autoRole", Database.EMPTY_DOCUMENT);
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
		this.bot.getDatabase().updateGuildById(event.getGuild().getIdLong(), Updates.pull("autoRole.roles", Filters.eq("id", event.getRole().getIdLong())))
			.whenComplete(Database.exceptionally(this.bot.getShardManager()));
	}

	@Override
	public void onEvent(GenericEvent event) {
		if (event instanceof GuildMemberJoinEvent) {
			this.onGuildMemberJoin((GuildMemberJoinEvent) event);
		} else if (event instanceof RoleDeleteEvent) {
			this.onRoleDelete((RoleDeleteEvent) event);
		}
	}

}
