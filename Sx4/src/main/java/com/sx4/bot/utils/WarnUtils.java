package com.sx4.bot.utils;

import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.sx4.bot.database.Database;
import com.sx4.bot.events.MuteEvents;
import com.sx4.bot.exceptions.HierarchyException;
import com.sx4.bot.exceptions.PermissionException;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

public class WarnUtils {
	
	public static class Warning {
		
		private int warning;
		private String action;
		private Long duration = null;
		
		public Warning(Document document) {
			this.warning = document.getInteger("warning");
			this.action = document.getString("action");
			this.duration = document.getLong("duration");
		}
		
		public Warning(int warning, String action) {
			this.warning = warning;
			this.action = action;
		}
		
		public Warning(int warning, String action, long duration) {
			this.warning = warning;
			this.action = action;
			this.duration = duration;
		}
		
		public int getWarning() {
			return this.warning;
		}
		
		public String getAction() {
			return this.action;
		}
		
		public boolean hasDuration() {
			return this.duration != null;
		}
		
		public Long getDuration() {
			return this.duration;
		}
		
	}
	
	public static class UserWarning {
		
		private List<String> reasons;
		private int warning;
		
		public UserWarning(Document document) {
			this.reasons = document.getList("reasons", String.class, Collections.emptyList());
			this.warning = document.getInteger("warnings");
		}
		
		public UserWarning(int warning, List<String> reasons) {
			this.reasons = reasons;
			this.warning = warning;
		}
		
		public int getWarning() {
			return this.warning;
		}
		
		public List<String> getReasons() {
			return this.reasons;
			
		}
		
	}
	
	public static void handleWarning(Document data, Guild guild, Member user, Member moderator, String reason, BiConsumer<Warning, Throwable> warning) {
		Document warnData = data.get("warn", Database.EMPTY_DOCUMENT);
		
		List<Document> users = warnData.getList("users", Document.class, Collections.emptyList());
		List<Document> configuration = warnData.getList("configuration", Document.class, Collections.emptyList());
		boolean punishments = warnData.getBoolean("punishments", true);
		
		UserWarning userNextWarning = WarnUtils.getUserWarning(users, user.getIdLong());
		
		Warning nextWarning;
		if (punishments) {
			nextWarning = WarnUtils.getWarning(configuration, userNextWarning.getWarning() + 1);
		} else {
			nextWarning = new Warning(userNextWarning.getWarning() + 1, "warn");
		}
		
		WarnUtils.handleWarningPunishments(guild, user, moderator, reason, punishments, configuration, nextWarning, exception -> {
			if (exception != null) {
				warning.accept(null, exception);
			} else {
				warning.accept(nextWarning, null);
			}
		});
	}
	
	public static void handleWarning(Guild guild, Member user, Member moderator, String reason, BiConsumer<Warning, Throwable> warning) {
		Document data = Database.get().getGuildById(guild.getIdLong(), null, Projections.include("warn.configuration", "warn.users", "warn.punishments"));
		
		WarnUtils.handleWarning(data, guild, user, moderator, reason, warning);
	}
	
	public static void handleWarningPunishments(Guild guild, Member target, Member moderator, String reason, boolean punishments, List<Document> warnConfiguration, Warning warning, Consumer<Throwable> exception) {
		switch (warning.getAction().toLowerCase()) {
			case "ban":
				if (guild.getSelfMember().hasPermission(Permission.BAN_MEMBERS)) {
					if (guild.getSelfMember().canInteract(target)) {
						if (!target.getUser().isBot()) {
							target.getUser().openPrivateChannel().queue(channel -> channel.sendMessage(ModUtils.getWarnEmbed(guild, moderator.getUser(), punishments, warnConfiguration, warning, reason)).queue(), e -> {});
						}
						
						guild.ban(target, 1).queue(ban -> {
							ModUtils.createModLogAndOffence(guild, moderator.getUser(), target.getUser(), GeneralUtils.title(warning.getAction()) + " (" + GeneralUtils.getNumberSuffix(warning.getWarning()) + " warning)", reason);
							
							exception.accept(null);
						}, e -> {
							exception.accept(e);
						});
					} else {
						exception.accept(new HierarchyException("I am unable to ban that user as their top role is higher than mine"));
					}
				} else {
					exception.accept(new PermissionException("I am unable to ban that user because I am missing the Ban Members permission"));
				}
				
				break;
			case "kick": 
				if (guild.getSelfMember().hasPermission(Permission.KICK_MEMBERS)) {
					if (guild.getSelfMember().canInteract(target)) {
						if (!target.getUser().isBot()) {
							target.getUser().openPrivateChannel().queue(channel -> channel.sendMessage(ModUtils.getWarnEmbed(guild, moderator.getUser(), punishments, warnConfiguration, warning, reason)).queue(), e -> {});
						}
						
						guild.kick(target).queue(kick -> {
							ModUtils.createModLogAndOffence(guild, moderator.getUser(), target.getUser(), GeneralUtils.title(warning.getAction()) + " (" + GeneralUtils.getNumberSuffix(warning.getWarning()) + " warning)", reason);
							
							exception.accept(null);
						}, e -> {
							exception.accept(e);
						});
					} else {
						exception.accept(new HierarchyException("I am unable to kick that user as their top role is higher than mine"));
					}
				} else {
					exception.accept(new PermissionException("I am unable to kick that user because I am missing the Kick Members permission"));
				}
				
				break;
			case "mute": 				
				ModUtils.getOrCreateMuteRole(guild, (muteRole, error) -> {
					if (error != null) {
						exception.accept(new IllegalStateException(error));
						return;
					}
					
					if (guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
						if (guild.getSelfMember().canInteract(muteRole)) {
							if (!target.getUser().isBot()) {
								target.getUser().openPrivateChannel().queue(channel -> channel.sendMessage(ModUtils.getWarnEmbed(guild, moderator.getUser(), punishments, warnConfiguration, warning, reason)).queue(), e -> {});
							}
							
							guild.addRoleToMember(target, muteRole).queue(mute -> {
								Long duration = warning.getDuration();
								if (duration != null) {
									ScheduledFuture<?> executor = MuteEvents.scheduledExectuor.schedule(() -> MuteEvents.removeUserMute(guild.getIdLong(), target.getIdLong(), muteRole.getIdLong()), warning.getDuration(), TimeUnit.SECONDS);
									MuteEvents.putExecutor(guild.getIdLong(), target.getIdLong(), executor);
								}
								
								ModUtils.createModLogAndOffence(guild, moderator.getUser(), target.getUser(), GeneralUtils.title(warning.getAction()) + (duration == null ? " Infinite" : " " + TimeUtils.toTimeString(duration, ChronoUnit.SECONDS)) + " (" + GeneralUtils.getNumberSuffix(warning.getWarning()) + " warning)", reason);
								
								exception.accept(null);
							}, e -> {
								exception.accept(e);
							});
						} else {
							exception.accept(new HierarchyException("I am unable to mute that user as their top role is higher than mine"));
						}
					} else {
						exception.accept(new PermissionException("I am unable to mute that user because I am missing the Manage Roles permission"));
					}
				});
				
				break;
			default:
				if (!target.getUser().isBot()) {
					target.getUser().openPrivateChannel().queue(channel -> channel.sendMessage(ModUtils.getWarnEmbed(guild, moderator.getUser(), punishments, warnConfiguration, warning, reason)).queue(), e -> {});
				}
				
				ModUtils.createModLogAndOffence(guild, moderator.getUser(), target.getUser(), GeneralUtils.title(warning.getAction()) + " (" + GeneralUtils.getNumberSuffix(warning.getWarning()) + " warning)", reason);
				
				exception.accept(null);
				
				break;
		}
	}
	
	public static UserWarning getUserWarning(List<Document> users, long userId) {
		for (Document userData : users) {
			if (userData.getLong("id") == userId) {
				return new UserWarning(userData);
			}
		}
		
		return new UserWarning(0, Collections.emptyList());
	}
	
	public static UpdateOneModel<Document> getUserUpdate(List<Document> users, List<Document> configuration, long guildId, long userId, String reason) {
		int maxWarning = WarnUtils.getMaxWarning(configuration);
		for (Document userData : users) {
			if (userData.getLong("id") == userId) {
				Bson update = maxWarning <= userData.getInteger("warnings") ? Updates.set("warn.users.$[user].warnings", 1) : Updates.inc("warn.users.$[user].warnings", 1);
				if (reason != null) {
					update = Updates.combine(update, Updates.push("warn.users.$[user].reasons", reason));
				}
				
				return new UpdateOneModel<>(Filters.eq("_id", guildId), update, new UpdateOptions().arrayFilters(List.of(Filters.eq("user.id", userId))));
			}
		}
		
		Document userData = new Document("id", userId).append("warnings", 1);
		if (reason != null) {
			userData.append("reasons", List.of(reason));
		}
		
		return new UpdateOneModel<>(Filters.eq("_id", guildId), Updates.push("warn.users", userData), new UpdateOptions().upsert(true));
	}

	public static Warning getWarning(List<Document> warnConfiguration, int warning) {
		if (WarnUtils.getMaxWarning(warnConfiguration) + 1 <= warning) {
			return WarnUtils.getWarning(warnConfiguration, 1);
		}
		
		for (Document warn : warnConfiguration) {
			int warningNumber = warn.getInteger("warning");
			if (warningNumber == warning) {
				return new Warning(warn);
			}
		}
		
		return new Warning(warning, "warn");
	}
	
	public static int getMaxWarning(List<Document> warnConfiguration) {
		int maxWarning = 0;
		for (Document warn : warnConfiguration) {
			int warningNumber = warn.getInteger("warning");
			if (maxWarning == 0) {
				maxWarning = warningNumber;
			} else {
				if (warningNumber > maxWarning) {
					maxWarning = warningNumber;
				}
			}
		}
		
		return maxWarning;
	}
	
	public static String getSuffixedAction(String action) {
		return action.equals("mute") ? action + "d" : action.equals("ban") ? action + "ned" : action + "ed";
	}
	
}
