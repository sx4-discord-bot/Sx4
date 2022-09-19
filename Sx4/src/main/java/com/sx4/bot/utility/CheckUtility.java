package com.sx4.bot.utility;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.entities.settings.HolderType;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer;
import org.bson.Document;

import java.util.*;
import java.util.stream.Collectors;

public class CheckUtility {

	private static final List<Long> DEFAULT_BLACKLIST;
	static {
		BitSet bitSet = new BitSet();
		bitSet.set(275);
		bitSet.set(276);
		bitSet.set(281);
		bitSet.set(292);
		bitSet.set(293);
		bitSet.set(294);

		DEFAULT_BLACKLIST = Arrays.stream(bitSet.toLongArray()).boxed().collect(Collectors.toList());
	}

	public static List<Document> getCombinedBlacklist(long guildId, List<Document> holders) {
		List<Document> combined = new ArrayList<>(CheckUtility.getDefaultBlacklist(guildId));
		for (Document defaultHolder : CheckUtility.getDefaultBlacklist(guildId)) {
			for (Document holder : holders) {
				if (defaultHolder.getLong("id").equals(holder.getLong("id"))) {
					combined.remove(defaultHolder);
				}
			}
		}

		combined.addAll(holders);
		return combined;
	}

	public static List<Document> getDefaultBlacklist(long guildId) {
		return List.of(new Document("id", guildId).append("type", 1).append("blacklisted", CheckUtility.DEFAULT_BLACKLIST));
	}

	public static boolean canReply(Sx4 bot, Message message, String prefix) {
		List<String> guildPrefixes = message.isFromGuild() ? bot.getMongoCanary().getGuildById(message.getGuild().getIdLong(), Projections.include("prefixes")).getList("prefixes", String.class, Collections.emptyList()) : Collections.emptyList();
		List<String> userPrefixes = bot.getMongoCanary().getUserById(message.getAuthor().getIdLong(), Projections.include("prefixes")).getList("prefixes", String.class, Collections.emptyList());

		List<String> prefixes = userPrefixes.isEmpty() ? guildPrefixes.isEmpty() ? bot.getConfig().getDefaultPrefixes() : guildPrefixes : userPrefixes;

		return CheckUtility.canReply(bot, message, prefix, prefixes);
	}

	public static boolean canReply(Sx4 bot, Message message, String prefix, List<String> prefixes) {
		if (message.isFromType(ChannelType.PRIVATE)) {
			return true;
		}

		if (bot.getConfig().isMain()) {
			if (!prefixes.contains(prefix)) {
				return true;
			}

			Member canary = message.getGuild().getMemberById(bot.getConfig().getCanaryId());
			return canary == null || !message.getGuildChannel().canTalk(canary);
		}

		return true;
	}

	public static boolean canUseCommand(Sx4 bot, Member member, Channel channel, Sx4Command command) {
		if (bot.getCommandListener().isDeveloper(member.getIdLong()) || member.hasPermission(Permission.ADMINISTRATOR)) {
			return true;
		}

		Guild guild = member.getGuild();

		Document blacklist = bot.getMongo().getBlacklist(Filters.eq("channelId", LoggerUtility.getChannelId(channel)), Projections.include("holders"));

		List<Document> holders = blacklist == null ? CheckUtility.getDefaultBlacklist(guild.getIdLong()) : CheckUtility.getCombinedBlacklist(guild.getIdLong(), blacklist.getList("holders", Document.class));

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
	
	public static boolean hasPermissions(Sx4 bot, Member member, IPermissionContainer channel, List<Document> holders, Permission... permissions) {
		return CheckUtility.missingPermissions(bot, member, channel, holders, permissions).isEmpty();
	}
	
	public static boolean hasPermissions(Sx4 bot, Member member, IPermissionContainer channel, List<Document> holders, EnumSet<Permission> permissions) {
		return CheckUtility.missingPermissions(bot, member, channel, holders, permissions).isEmpty();
	}

	public static boolean hasPermissions(Sx4 bot, Member member, List<Document> holders, Permission... permissions) {
		return CheckUtility.missingPermissions(bot, member, holders, permissions.length == 0 ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(Arrays.asList(permissions))).isEmpty();
	}

	public static boolean hasPermissions(Sx4 bot, Member member, List<Document> holders, EnumSet<Permission> permissions) {
		return CheckUtility.missingPermissions(bot, member, null, holders, permissions).isEmpty();
	}
	
	public static EnumSet<Permission> missingPermissions(Sx4 bot, Member member, IPermissionContainer channel, List<Document> holders, Permission... permissions) {
		return CheckUtility.missingPermissions(bot, member, channel, holders, permissions.length == 0 ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(Arrays.asList(permissions)));
	}

	public static EnumSet<Permission> missingPermissions(Sx4 bot, Member member, List<Document> holders, Permission... permissions) {
		return CheckUtility.missingPermissions(bot, member, holders, permissions.length == 0 ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(Arrays.asList(permissions)));
	}

	public static EnumSet<Permission> missingPermissions(Sx4 bot, Member member, List<Document> holders, EnumSet<Permission> permissions) {
		return CheckUtility.missingPermissions(bot, member, null, holders, permissions);
	}

	public static EnumSet<Permission> missingPermissions(Sx4 bot, Member member, IPermissionContainer channel, List<Document> holders, EnumSet<Permission> permissions) {
		if (bot.getCommandListener().isDeveloper(member.getIdLong()) || (channel == null ? member.hasPermission(permissions) : member.hasPermission(channel, permissions))) {
			return EnumSet.noneOf(Permission.class);
		}

		Guild guild = member.getGuild();

		Set<Long> roleIds = member.getRoles().stream()
			.map(Role::getIdLong)
			.collect(Collectors.toSet());
		
		long permissionsRaw = Permission.getRaw(channel == null ? member.getPermissions() : member.getPermissions(channel)), permissionsNeededRaw = Permission.getRaw(permissions);
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
