package com.sx4.bot.utility;

import com.mongodb.client.model.Projections;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.core.Sx4Command;
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

	public static boolean canUseCommand(Member member, TextChannel channel, Sx4Command command) {
		if (Sx4.get().getCommandListener().isDeveloper(member.getIdLong()) || member.hasPermission(Permission.ADMINISTRATOR)) {
			return true;
		}

		Guild guild = member.getGuild();

		List<Document> holders = Database.get().getChannelById(channel.getIdLong(), Projections.include("blacklist.holders")).getEmbedded(List.of("blacklist", "holders"), Collections.emptyList());

		Set<Long> roleIds = member.getRoles().stream()
			.map(Role::getIdLong)
			.collect(Collectors.toSet());

		boolean canUseCommand = true;
		for (Document holder : holders) {
			long id = holder.getLong("id");
			int type = holder.getInteger("type");

			List<Long> whitelisted = holder.getList("whitelisted", Long.class, Collections.emptyList());
			List<Long> blacklisted = holder.getList("blacklisted", Long.class, Collections.emptyList());

			BitSet whitelistBitSet = BitSet.valueOf(whitelisted.stream().mapToLong(l -> l).toArray());
			BitSet blacklistBitSet = BitSet.valueOf(blacklisted.stream().mapToLong(l -> l).toArray());

			// Check if the role is equal to the guild id for the @everyone role which every member has
			if ((type == HolderType.ROLE.getType() && (id == guild.getIdLong() || roleIds.contains(id))) || (type == HolderType.USER.getType() && member.getIdLong() == id)) {
				if (whitelistBitSet.get(command.getId())) {
					return true;
				} else if (blacklistBitSet.get(command.getId())) {
					canUseCommand = false;
				}
			}
		}

		return canUseCommand;
	}
	
	public static boolean hasPermissions(Member member, TextChannel channel, List<Document> holders, Permission... permissions) {
		return CheckUtility.missingPermissions(member, channel, holders, permissions).isEmpty();
	}
	
	public static boolean hasPermissions(Member member, TextChannel channel, List<Document> holders, EnumSet<Permission> permissions) {
		return CheckUtility.missingPermissions(member, channel, holders, permissions).isEmpty();
	}
	
	public static EnumSet<Permission> missingPermissions(Member member, TextChannel channel, List<Document> holders, Permission... permissions) {
		return CheckUtility.missingPermissions(member, channel, holders, permissions.length == 0 ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(Arrays.asList(permissions)));
	}

	public static EnumSet<Permission> missingPermissions(Member member, TextChannel channel, List<Document> holders, EnumSet<Permission> permissions) {
		if (Sx4.get().getCommandListener().isDeveloper(member.getIdLong()) || member.hasPermission(channel, permissions)) {
			return EnumSet.noneOf(Permission.class);
		}

		Guild guild = channel.getGuild();

		Set<Long> roleIds = member.getRoles().stream()
			.map(Role::getIdLong)
			.collect(Collectors.toSet());
		
		long permissionsRaw = Permission.getRaw(member.getPermissions(channel)), permissionsNeededRaw = Permission.getRaw(permissions);
		for (Document holder : holders) {
			long id = holder.getLong("id");
			int type = holder.getInteger("type");

			// Check if the role is equal to the guild id for the @everyone role which every member has
			if ((type == HolderType.ROLE.getType() && (id == guild.getIdLong() || roleIds.contains(id))) || (type == HolderType.USER.getType() && member.getIdLong() == id)) {
				permissionsRaw |= holder.getLong("permissions");
			}
		}
		
		return (permissionsNeededRaw & permissionsRaw) != 0 ? EnumSet.noneOf(Permission.class) : Permission.getPermissions(permissionsNeededRaw & ~permissionsRaw);
	}
	
}
