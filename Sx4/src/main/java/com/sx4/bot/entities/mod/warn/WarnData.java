package com.sx4.bot.entities.mod.warn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import com.sx4.bot.database.Database;
import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.entities.mod.action.Action;
import com.sx4.bot.entities.mod.action.ModAction;
import com.sx4.bot.entities.mod.action.TimeAction;
import com.sx4.bot.entities.mod.mute.MuteData;
import com.sx4.bot.entities.mod.tempban.TempBanData;
import com.sx4.bot.events.mod.WarnEvent;
import com.sx4.bot.exceptions.mod.AuthorPermissionException;
import com.sx4.bot.exceptions.mod.BotHierarchyException;
import com.sx4.bot.exceptions.mod.BotPermissionException;
import com.sx4.bot.managers.ModActionManager;
import com.sx4.bot.managers.MuteManager;
import com.sx4.bot.managers.TempBanManager;
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
		this.punishments = data.getBoolean("punishments", true);
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
			.filter(user -> user.getId() == userId)
			.findFirst()
			.orElse(new WarnUser(userId, 0, true));
	}
	
	public List<WarnConfig> getConfig() {
		return this.config;
	}
	
	public WarnConfig getConfigById(int number) {
		if (!this.punishments) {
			return new WarnConfig(new Action(ModAction.WARN), number);
		}
		
		if (number > this.getLastConfig().getNumber()) {
			return null;
		}
		
		return this.config.stream()
			.filter(config -> config.getNumber() == number)
			.findFirst()
			.orElse(new WarnConfig(new Action(ModAction.WARN), number));
	}
	
	public WarnConfig getLastConfig() {
		return this.config.stream()
			.max((a, b) -> Integer.compare(a.getNumber(), b.getNumber()))
			.get();
	}
	
	public boolean hasPunishments() {
		return this.punishments;
	}
	
	public void warn(Member target, Member moderator, Reason reason, BiConsumer<WarnConfig, Throwable> consumer) {
		ModActionManager manager = ModActionManager.get();
		MuteManager muteManager = MuteManager.get();
		Database database = Database.get();
		
		Guild guild = target.getGuild();
		
		WarnUser user = this.getUserById(target.getIdLong());
		int nextWarning = user.getAmount() + 1;
		
		WarnConfig config = this.getConfigById(nextWarning > this.getLastConfig().getNumber() ? 1 : nextWarning);
		
		List<WriteModel<Document>> bulkData = new ArrayList<>();
		
		Action action = config.getAction();
		switch (action.getModAction()) {
			case WARN:
				database.updateGuildById(this.getUpdate(guild.getIdLong(), target.getIdLong(), config.getNumber())).whenComplete((result, exception) -> {
					if (exception != null) {
						consumer.accept(null, exception);
					} else {
						manager.onModAction(new WarnEvent(moderator, target.getUser(), reason, config));
						
						consumer.accept(config, null);
					}
				});
				
				break;
			case MUTE:
				if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) consumer.accept(null, new BotPermissionException(Permission.MANAGE_ROLES));
				
				MuteData muteData = new MuteData(database.getGuildById(guild.getIdLong(), Projections.include("mute")).get("mute", Database.EMPTY_DOCUMENT));
				muteData.getOrCreateRole(guild, (role, roleException) -> {
					if (roleException != null) {
						consumer.accept(null, roleException);
					} else {
						long seconds = ((TimeAction) action).getDuration();
						
						bulkData.add(muteData.getUpdate(target, seconds));
						bulkData.add(this.getUpdate(guild.getIdLong(), target.getIdLong(), config.getNumber()));
						
						database.bulkWriteGuilds(bulkData).whenComplete((result, writeException) -> {
							if (writeException != null) {
								consumer.accept(null, writeException);
							} else {
								guild.addRoleToMember(target, role).reason(ModUtility.getAuditReason(reason, moderator.getUser())).queue($ -> {
									muteManager.putMute(guild.getIdLong(), target.getIdLong(), role.getIdLong(), seconds);
									
									manager.onModAction(new WarnEvent(moderator, target.getUser(), reason, config));
									
									consumer.accept(config, null);
								});
							}
						});
					}
				});
				
				break;
			case MUTE_EXTEND:
				if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) consumer.accept(null, new BotPermissionException(Permission.MANAGE_ROLES));
				
				MuteData muteExtendData = new MuteData(database.getGuildById(guild.getIdLong(), Projections.include("mute")).get("mute", Database.EMPTY_DOCUMENT));
				muteExtendData.getOrCreateRole(guild, (role, roleException) -> {
					if (roleException != null) {
						consumer.accept(null, roleException);
					} else {
						long seconds = ((TimeAction) action).getDuration();
						
						bulkData.add(muteExtendData.getUpdate(guild.getIdLong(), target.getIdLong(), seconds, true));
						bulkData.add(this.getUpdate(guild.getIdLong(), target.getIdLong(), config.getNumber()));
						
						database.bulkWriteGuilds(bulkData).whenComplete((result, writeException) -> {
							if (writeException != null) {
								consumer.accept(null, writeException);
							} else {
								guild.addRoleToMember(target, role).reason(ModUtility.getAuditReason(reason, moderator.getUser())).queue($ -> {
									muteManager.putMute(guild.getIdLong(), target.getIdLong(), role.getIdLong(), seconds, true);
									
									manager.onModAction(new WarnEvent(moderator, target.getUser(), reason, config));
									
									consumer.accept(config, null);
								});
							}
						});
					}
				});
				
				break;
			case KICK:
				if (!guild.getSelfMember().hasPermission(Permission.KICK_MEMBERS)) consumer.accept(null, new BotPermissionException(Permission.KICK_MEMBERS));
				if (!guild.getSelfMember().canInteract(target)) consumer.accept(null, new BotHierarchyException("I cannot kick a user higher or equal than my top role"));
				if (!moderator.hasPermission(Permission.KICK_MEMBERS)) consumer.accept(null, new AuthorPermissionException(Permission.KICK_MEMBERS));
					
				database.updateGuildById(this.getUpdate(guild.getIdLong(), target.getIdLong(), config.getNumber())).whenComplete((result, exception) -> {
					if (exception != null) {
						consumer.accept(null, exception);
					} else {
						target.kick(ModUtility.getAuditReason(reason, moderator.getUser())).queue($ -> {
							manager.onModAction(new WarnEvent(moderator, target.getUser(), reason, config));
							
							consumer.accept(config, null);
						});
					}
				});
				
				break;
			case TEMP_BAN:
				if (!guild.getSelfMember().hasPermission(Permission.BAN_MEMBERS)) consumer.accept(null, new BotPermissionException(Permission.BAN_MEMBERS));
				if (!guild.getSelfMember().canInteract(target)) consumer.accept(null, new BotHierarchyException("I cannot ban a user higher or equal than my top role"));
				if (!moderator.hasPermission(Permission.BAN_MEMBERS)) consumer.accept(null, new AuthorPermissionException(Permission.BAN_MEMBERS));
				
				TempBanData tempBanData = new TempBanData(database.getGuildById(guild.getIdLong(), Projections.include("tempBan")).get("tempBan", Database.EMPTY_DOCUMENT));
				
				long seconds = ((TimeAction) action).getDuration();
				
				bulkData.add(tempBanData.getUpdate(guild.getIdLong(), target.getIdLong(), seconds));
				bulkData.add(this.getUpdate(guild.getIdLong(), target.getIdLong(), config.getNumber()));
				
				database.bulkWriteGuilds(bulkData).whenComplete((result, exception) -> {
					if (exception != null) {
						consumer.accept(null, exception);
					} else {
						target.ban(1).reason(ModUtility.getAuditReason(reason, moderator.getUser())).queue($ -> {
							manager.onModAction(new WarnEvent(moderator, target.getUser(), reason, config));
							
							TempBanManager.get().putBan(guild.getIdLong(), target.getIdLong(), seconds);
							
							consumer.accept(config, null);
						});
					}
				});
				
				break;
			case BAN:
				if (!guild.getSelfMember().hasPermission(Permission.BAN_MEMBERS)) consumer.accept(null, new BotPermissionException(Permission.BAN_MEMBERS));
				if (!guild.getSelfMember().canInteract(target)) consumer.accept(null, new BotHierarchyException("I cannot ban a user higher or equal than my top role"));
				if (!moderator.hasPermission(Permission.BAN_MEMBERS)) consumer.accept(null, new AuthorPermissionException(Permission.BAN_MEMBERS));
						
					
				database.updateGuildById(this.getUpdate(guild.getIdLong(), target.getIdLong(), config.getNumber())).whenComplete((result, exception) -> {
					if (exception != null) {
						consumer.accept(null, exception);
					} else {
						target.ban(1).reason(ModUtility.getAuditReason(reason, moderator.getUser())).queue($ -> {
							manager.onModAction(new WarnEvent(moderator, target.getUser(), reason, config));
							
							consumer.accept(config, null);
						});
					}
				});
				
				break;
			default:
				break;
		}
	}
	
	private UpdateOneModel<Document> getUpdate(long guildId, long userId, int amount) {
		WarnUser user = this.getUserById(userId);
		
		Bson update;
		List<Bson> arrayFilters = null;
		if (user.isFake()) {
			Document rawData = new Document("id", userId)
					.append("amount", amount);
			
			update = Updates.push("warn.users", rawData);
		} else {
			arrayFilters = List.of(Filters.eq("user.id", user.getId()));
			
			update = Updates.set("warn.users.$[user].amount", amount);
		}
		
		return new UpdateOneModel<>(Filters.eq("_id", guildId), update, new UpdateOptions().arrayFilters(arrayFilters).upsert(true));
	}
	
}
