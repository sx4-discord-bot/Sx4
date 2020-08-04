package com.sx4.bot.handlers;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.sx4.bot.database.Database;
import com.sx4.bot.managers.AutoRoleManager;
import com.sx4.bot.utility.ExceptionUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import org.bson.Document;

import java.time.Clock;
import java.util.*;

public class AutoRoleHandler extends ListenerAdapter {
	
	public void onGuildMemberJoin(GuildMemberJoinEvent event) {
		Guild guild = event.getGuild();
		Member member = event.getMember();
		
		Member me = guild.getSelfMember();
		if (!me.hasPermission(Permission.MANAGE_ROLES)) {
			return;
		}
		
		AutoRoleManager manager = AutoRoleManager.get();
		Database database = Database.get();
		
		Document data = database.getGuildById(guild.getIdLong(), Projections.include("autoRole.roles", "autoRole.enabled")).get("autoRole", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false)) {
			return;
		}
		
		List<Document> rolesData = data.getList("roles", Document.class, Collections.emptyList());
		
		Map<Long, Pair<List<Long>, List<Long>>> roleMap = new HashMap<>();
		List<Role> roles = new ArrayList<>();
		for (Document roleData : rolesData) {
			List<Document> filters = roleData.getList("filters", Document.class);
			if (filters == null) {
				Role role = guild.getRoleById(roleData.getLong("id"));
				if (role != null && me.canInteract(role)) {
					roles.add(role);
				}
				
				continue;
			}
			
			Pair<Long, Boolean> longestTime = null;
			boolean allTrue = true;
			for (Document filter : filters) {
				String key = filter.getString("key");
				Object value = filter.get("value");
				long duration = filter.getLong("duration");
				
				switch (key) {
					case "BOT":
						if (member.getUser().isBot() != (boolean) value) {
							allTrue = false;
						}
						
						break;
					case "JOINED":
						if (longestTime == null || longestTime.getLeft() < duration) {
							longestTime = Pair.of(duration, (boolean) value);
						}
						
						break;
					case "CREATED":
						long timeCreated = Clock.systemUTC().instant().getEpochSecond() - member.getUser().getTimeCreated().toEpochSecond();
						if (timeCreated < duration && (longestTime == null || longestTime.getLeft() < duration)) {
							longestTime = Pair.of(duration, (boolean) value);
						}
						
						break;
				}
			}
			
			if (allTrue && longestTime != null) {
				
			}
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
