package com.sx4.bot.utility;

import com.mongodb.client.model.Projections;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.Database;
import com.sx4.bot.entities.settings.HolderType;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import org.bson.Document;

import java.util.*;
import java.util.stream.Collectors;

public class CheckUtility {
	
	public static boolean hasPermissions(Member member, TextChannel channel, Permission... permissions) {
		return CheckUtility.missingPermissions(member, channel, permissions).isEmpty();
	}
	
	public static boolean hasPermissions(Member member, TextChannel channel, EnumSet<Permission> permissions) {
		return CheckUtility.missingPermissions(member, channel, permissions).isEmpty();
	}
	
	public static EnumSet<Permission> missingPermissions(Member member, TextChannel channel, Permission... permissions) {
		return CheckUtility.missingPermissions(member, channel, permissions.length == 0 ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(Arrays.asList(permissions)));
	}

	public static EnumSet<Permission> missingPermissions(Member member, TextChannel channel, EnumSet<Permission> permissions) {
		if (Sx4.get().getCommandListener().isDeveloper(member.getIdLong()) || member.hasPermission(channel, permissions)) {
			return EnumSet.noneOf(Permission.class);
		}

		Guild guild = channel.getGuild();

		List<Document> holders = Database.get().getGuildById(guild.getIdLong(), Projections.include("fakePermissions.holders")).getEmbedded(List.of("fakePermissions", "holders"), Collections.emptyList());
		
		Set<Long> roleIds = member.getRoles().stream()
			.map(Role::getIdLong)
			.collect(Collectors.toSet());
		
		long permissionsRaw = Permission.getRaw(member.getPermissions(channel)), permissionsNeededRaw = Permission.getRaw(permissions);
		for (Document holder : holders) {
			long id = holder.getLong("id");
			int type = holder.getInteger("type");

			// Check if the role is equal to the guild id for the @everyone role which every member has
			if (type == HolderType.ROLE.getType() && (id == guild.getIdLong() || roleIds.contains(id))) {
				permissionsRaw |= holder.getLong("permissions");
			} else if (type == HolderType.USER.getType() && member.getIdLong() == id) {
				permissionsRaw |= holder.getLong("permissions");
			}
		}
		
		return (permissionsNeededRaw & permissionsRaw) == permissionsNeededRaw ? EnumSet.noneOf(Permission.class) : Permission.getPermissions(permissionsNeededRaw & ~permissionsRaw);
	}
	
}
