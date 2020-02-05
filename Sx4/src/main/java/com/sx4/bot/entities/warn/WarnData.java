package com.sx4.bot.entities.warn;

import java.util.Collections;
import java.util.List;

import org.bson.Document;

import com.sx4.bot.core.Sx4Bot;
import com.sx4.bot.events.mod.WarnEvent;
import com.sx4.bot.exceptions.mod.AuthorPermissionException;
import com.sx4.bot.exceptions.mod.BotHierarchyException;
import com.sx4.bot.exceptions.mod.BotPermissionException;
import com.sx4.bot.hooks.mod.ModAction;
import com.sx4.bot.hooks.mod.ModActionManager;
import com.sx4.bot.utility.ModUtility;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

public class WarnData {
	
	private final List<WarnUser> users;
	private final List<WarnConfig> config;
	
	private final boolean punishments;
	
	public WarnData(Document data) {
		List<Document> users = data.getList("users", Document.class, Collections.emptyList());
		List<Document> config = data.getList("config", Document.class);
		
		this.users = WarnUser.fromData(users);
		this.config = config == null ? WarnConfig.DEFAULT : WarnConfig.fromData(config);
		this.punishments = data.getBoolean("punishments", false);
	}
	
	public WarnData(List<WarnUser> users, List<WarnConfig> config, boolean punishments) {
		this.users = users;
		this.config = config;
		this.punishments = punishments;
	}
	
	public List<WarnUser> getUsers() {
		return this.users;
	}
	
	public WarnUser getUserById(long userId) {
		return this.users.stream()
			.filter(user -> user.getUserId() == userId)
			.findFirst()
			.orElse(new WarnUser(userId, 0));
	}
	
	public List<WarnConfig> getConfig() {
		return this.config;
	}
	
	public WarnConfig getConfigById(int number) {
		if (this.punishments) {
			return new WarnConfig(ModAction.WARN, number);
		}
		
		if (number >= this.config.size()) {
			return null;
		}
		
		return this.config.stream()
			.filter(config -> config.getNumber() == number)
			.findFirst()
			.orElse(new WarnConfig(ModAction.WARN, number));
	}
	
	public WarnConfig getLastConfig() {
		return this.config.stream()
			.max((a, b) -> Integer.compare(b.getNumber(), a.getNumber()))
			.get();
	}
	
	public boolean hasPunishments() {
		return this.punishments;
	}
	
	public void warn(Member target, Member moderator, String reason) {
		ModActionManager manager = Sx4Bot.getModActionManager();
		
		Guild guild = target.getGuild();
		
		WarnUser user = this.getUserById(target.getIdLong());
		int nextWarning = user.getAmount() + 1;
		
		WarnConfig config = this.getConfigById(nextWarning >= this.getLastConfig().getNumber() ? 1 : nextWarning);
		
		switch (config.getAction()) {
			case WARN:
				user.incrementAmount();
				manager.onModAction(new WarnEvent(moderator, target.getUser(), reason, this));
				
				break;
			case MUTE:
				if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) throw new BotPermissionException(Permission.MANAGE_ROLES);
				
				break;
			case MUTE_EXTEND:
				if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) throw new BotPermissionException(Permission.MANAGE_ROLES);
				
				break;
			case KICK:
				if (!guild.getSelfMember().hasPermission(Permission.KICK_MEMBERS)) throw new BotPermissionException(Permission.KICK_MEMBERS);
				if (!guild.getSelfMember().canInteract(target)) throw new BotHierarchyException("I cannot kick a user higher or equal than my top role");
				if (!moderator.hasPermission(Permission.KICK_MEMBERS)) throw new AuthorPermissionException(Permission.KICK_MEMBERS);
					
				
				target.kick(ModUtility.getAuditReason(reason, moderator.getUser())).queue($ -> {
					user.incrementAmount();
					manager.onModAction(new WarnEvent(moderator, target.getUser(), reason, this));
				});
				
				break;
			case TEMP_BAN:
				if (!guild.getSelfMember().hasPermission(Permission.BAN_MEMBERS)) throw new BotPermissionException(Permission.BAN_MEMBERS);
				if (!guild.getSelfMember().canInteract(target)) throw new BotHierarchyException("I cannot ban a user higher or equal than my top role");
				if (!moderator.hasPermission(Permission.BAN_MEMBERS)) throw new AuthorPermissionException(Permission.BAN_MEMBERS);
					
				
				break;
			case BAN:
				if (!guild.getSelfMember().hasPermission(Permission.BAN_MEMBERS)) throw new BotPermissionException(Permission.BAN_MEMBERS);
				if (!guild.getSelfMember().canInteract(target)) throw new BotHierarchyException("I cannot ban a user higher or equal than my top role");
				if (!moderator.hasPermission(Permission.BAN_MEMBERS)) throw new AuthorPermissionException(Permission.BAN_MEMBERS);
						
					
				target.ban(1).reason(ModUtility.getAuditReason(reason, moderator.getUser())).queue($ -> {
					user.incrementAmount();
					manager.onModAction(new WarnEvent(moderator, target.getUser(), reason, this));
				});
				
				break;
			default:
				break;
		}
	}
	
}
