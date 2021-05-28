package com.sx4.bot.handlers;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.mongo.MongoDatabase;
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
		
		List<Document> autoRoles = this.bot.getMongo().getAutoRoles(Filters.eq("guildId", event.getGuild().getIdLong()), Projections.include("enabled", "filters", "roleId")).into(new ArrayList<>());
		if (autoRoles.isEmpty()) {
			return;
		}

		List<Role> roles = new ArrayList<>();
		Roles : for (Document autoRole : autoRoles) {
			Role role = guild.getRoleById(autoRole.getLong("roleId"));
			if (role == null || !selfMember.canInteract(role)) {
				continue;
			}

			List<Document> filters = autoRole.getList("filters", Document.class, Collections.emptyList());
			for (Document filter : filters) {
				int type = filter.getInteger("type");
				Object value = filter.get("value");
				
				switch (type) {
					case 0:
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
			guild.modifyMemberRoles(member, roles, null).reason("Auto Roles").queue();
		}
	}
	
	public void onRoleDelete(RoleDeleteEvent event) {
		this.bot.getMongo().deleteAutoRole(Filters.eq("roleId", event.getRole().getIdLong())).whenComplete(MongoDatabase.exceptionally(this.bot.getShardManager()));
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
