package com.sx4.bot.utility;

import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import com.sx4.bot.database.Database;
import com.sx4.bot.database.model.Operators;
import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.entities.mod.action.Action;
import com.sx4.bot.entities.mod.action.ModAction;
import com.sx4.bot.entities.mod.action.TimeAction;
import com.sx4.bot.entities.mod.warn.Warn;
import com.sx4.bot.events.mod.WarnEvent;
import com.sx4.bot.exceptions.mod.AuthorPermissionException;
import com.sx4.bot.exceptions.mod.BotHierarchyException;
import com.sx4.bot.exceptions.mod.BotPermissionException;
import com.sx4.bot.exceptions.mod.MaxRolesException;
import com.sx4.bot.managers.ModActionManager;
import com.sx4.bot.managers.MuteManager;
import com.sx4.bot.managers.TempBanManager;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ModUtility {

	public static String getAuditReason(Reason reason, User moderator) {
		return (reason == null ? "None Given" : reason.getParsed()) + " [" + moderator.getAsTag() + "]";
	}

	public static CompletableFuture<Role> upsertMuteRole(Guild guild, long roleId, boolean autoUpdate) {
		if (roleId != 0L) {
			Role role = guild.getRoleById(roleId);
			if (role != null) {
				return CompletableFuture.completedFuture(role);
			} else {
				return ModUtility.createMuteRole(guild, autoUpdate);
			}
		} else {
			return ModUtility.createMuteRole(guild, autoUpdate);
		}
	}

	private static CompletableFuture<Role> createMuteRole(Guild guild, boolean autoUpdate) {
		Member selfMember = guild.getSelfMember();

		if (guild.getRoleCache().size() >= 250) {
			return CompletableFuture.failedFuture(new MaxRolesException("The guild has the max roles possible (250) so I was unable to make the mute role"));
		}

		if (!selfMember.hasPermission(Permission.MANAGE_ROLES)) {
			return CompletableFuture.failedFuture(new BotPermissionException(Permission.MANAGE_ROLES));
		}

		CompletableFuture<Role> future = new CompletableFuture<>();
		guild.createRole().setName("Muted - " + selfMember.getUser().getName()).queue(newRole -> {
			Database.get().updateGuildById(guild.getIdLong(), Updates.set("mute.roleId", newRole.getIdLong())).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendErrorMessage(exception)) {
					future.completeExceptionally(exception);
				} else {
					future.complete(newRole);

					if (autoUpdate && selfMember.hasPermission(Permission.MANAGE_PERMISSIONS)) {
						guild.getTextChannels().forEach(channel -> channel.upsertPermissionOverride(newRole).deny(Permission.MESSAGE_WRITE).queue());
					}
				}
			});
		});

		return future;
	}

	public static CompletableFuture<Warn> warn(Member target, Member moderator, Reason reason) {
		ModActionManager manager = ModActionManager.get();
		MuteManager muteManager = MuteManager.get();
		Database database = Database.get();
		CompletableFuture<Warn> future = new CompletableFuture<>();

		Guild guild = target.getGuild();

		Bson maxWarnNumber = Operators.ifNull(Operators.reduce("$warn.config", -1, Operators.cond(Operators.gt("$$this.number", "$$value.number"), "$$this.number", "$$value.number")), 4);
		Bson userFilter = Operators.filter("$warn.users", Operators.eq("$$this.id", target.getIdLong()));
		Bson amount = Operators.ifNull(Operators.first(Operators.map(userFilter, "$$this.amount")), 0);
		Bson nextWarnNumber = Operators.cond(Operators.gte(amount, maxWarnNumber), 1, Operators.add(amount, 1));
		Bson nextWarnFilter = Operators.filter("$warn.config", Operators.eq("$$this.number", nextWarnNumber));
		Bson nextWarnAction = Operators.ifNull(Operators.first(Operators.map(nextWarnFilter, "$$this.action.type")), ModAction.WARN.getType());
		Bson nextWarnDuration = Operators.first(Operators.map(nextWarnFilter, "$$this.action.duration"));

		Bson muteExtend = Operators.eq(nextWarnAction, ModAction.MUTE_EXTEND.getType());
		Bson muteFilter = Operators.filter("$mute.users", Operators.eq("$$this.id", target.getIdLong()));
		Bson unmuteAt = Operators.first(Operators.map(muteFilter, "$$this.unmuteAt"));

		Bson permissionCheck = Operators.cond(
			Operators.or(muteExtend, Operators.eq(nextWarnAction, ModAction.MUTE.getType())), guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES),
			Operators.cond(Operators.eq(nextWarnAction, ModAction.KICK.getType()), Operators.and(guild.getSelfMember().hasPermission(Permission.KICK_MEMBERS), guild.getSelfMember().canInteract(target), moderator.hasPermission(Permission.KICK_MEMBERS)),
				Operators.cond(Operators.or(Operators.eq(nextWarnAction, ModAction.BAN.getType()), Operators.eq(nextWarnAction, ModAction.TEMP_BAN.getType())), Operators.and(guild.getSelfMember().hasPermission(Permission.BAN_MEMBERS), guild.getSelfMember().canInteract(target), moderator.hasPermission(Permission.BAN_MEMBERS)), true)
			)
		);

		List<Bson> update = List.of(
			Operators.set("warn.users", Operators.cond(permissionCheck, Operators.concatArrays(List.of(Operators.mergeObjects(Operators.ifNull(Operators.first(userFilter), Database.EMPTY_DOCUMENT), new Document("id", target.getIdLong()).append("amount", nextWarnNumber))), Operators.ifNull(Operators.filter("$warn.users", Operators.ne("$$this.id", target.getIdLong())), Collections.EMPTY_LIST)), "$warn.users")),
			Operators.set("mute.users", Operators.cond(Operators.and(permissionCheck, Operators.or(Operators.eq(nextWarnAction, ModAction.MUTE.getType()), muteExtend), Operators.nonNull(nextWarnDuration)), Operators.concatArrays(List.of(Operators.mergeObjects(Operators.ifNull(Operators.first(muteFilter), Database.EMPTY_DOCUMENT), new Document("id", target.getIdLong()).append("unmuteAt", Operators.cond(Operators.and(muteExtend, Operators.nonNull(unmuteAt)), Operators.add(unmuteAt, nextWarnDuration), Operators.add(Operators.nowEpochSecond(), nextWarnDuration))))), Operators.ifNull(Operators.filter("$mute.users", Operators.ne("$$this.id", target.getIdLong())), Collections.EMPTY_LIST)), "$mute.users")),
			Operators.set("tempBan.users", Operators.cond(Operators.and(permissionCheck, Operators.eq(nextWarnAction, ModAction.TEMP_BAN.getType()), Operators.nonNull(nextWarnDuration)), Operators.concatArrays(List.of(Operators.mergeObjects(Operators.ifNull(Operators.first(Operators.filter("$tempBan.users", Operators.filter("$$this.id", target.getIdLong()))), Database.EMPTY_DOCUMENT), new Document("id", target.getIdLong()).append("unbanAt", Operators.add(Operators.nowEpochSecond(), nextWarnDuration)))), Operators.ifNull(Operators.filter("$tempBan.users", Operators.ne("$$this.id", target.getIdLong())), Collections.EMPTY_LIST)), "$tempBan.users"))
		);

		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.include("warn", "mute.roleId", "mute.autoUpdate"));
		database.findAndUpdateGuildById(guild.getIdLong(), update, options).whenComplete((data, exception) -> {
			try {
				if (exception != null) {
					future.completeExceptionally(exception);
					return;
				}

				data = data == null ? Database.EMPTY_DOCUMENT : data;

				Document warn = data.get("warn", Database.EMPTY_DOCUMENT);

				List<Document> config = warn.getList("config", Document.class, Warn.DEFAULT_CONFIG);
				int maxWarning = config.stream()
					.map(d -> d.getInteger("number"))
					.max(Integer::compareTo)
					.get();

				List<Document> users = warn.getList("users", Document.class, Collections.emptyList());
				int nextWarning = users.stream()
					.filter(d -> d.getLong("id") == target.getIdLong())
					.map(d -> {
						int current = d.getInteger("amount");
						return current >= maxWarning ? 1 : current + 1;
					})
					.findFirst()
					.orElse(1);

				Action action = config.stream()
					.filter(d -> d.getInteger("number") == nextWarning)
					.map(d -> d.get("action", Document.class))
					.map(Action::fromData)
					.findFirst()
					.orElse(new Action(ModAction.WARN));

				Warn warnConfig = new Warn(action, nextWarning);

				switch (action.getModAction()) {
					case WARN:
						manager.onModAction(new WarnEvent(moderator, target.getUser(), reason, warnConfig));
						future.complete(warnConfig);

						break;
					case MUTE:
					case MUTE_EXTEND:
						if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
							future.completeExceptionally(new BotPermissionException(Permission.MANAGE_ROLES));
							return;
						}

						Document mute = data.get("mute", Database.EMPTY_DOCUMENT);

						ModUtility.upsertMuteRole(guild, mute.getLong("roleId"), mute.get("autoUpdate", true)).whenComplete((role, roleException) -> {
							if (roleException != null) {
								future.completeExceptionally(roleException);
								return;
							}

							guild.addRoleToMember(target, role).reason(ModUtility.getAuditReason(reason, moderator.getUser())).queue($ -> {
								muteManager.putMute(guild.getIdLong(), target.getIdLong(), role.getIdLong(), ((TimeAction) action).getDuration(), action.getModAction() == ModAction.MUTE_EXTEND);

								manager.onModAction(new WarnEvent(moderator, target.getUser(), reason, warnConfig));

								future.complete(warnConfig);
							});
						});

						break;
					case KICK:
						if (!guild.getSelfMember().hasPermission(Permission.KICK_MEMBERS)) {
							future.completeExceptionally(new BotPermissionException(Permission.KICK_MEMBERS));
							return;
						}

						if (!guild.getSelfMember().canInteract(target)) {
							future.completeExceptionally(new BotHierarchyException("kick"));
							return;
						}

						if (!moderator.hasPermission(Permission.KICK_MEMBERS)) {
							future.completeExceptionally(new AuthorPermissionException(Permission.KICK_MEMBERS));
							return;
						}

						target.kick(ModUtility.getAuditReason(reason, moderator.getUser())).queue($ -> {
							manager.onModAction(new WarnEvent(moderator, target.getUser(), reason, warnConfig));

							future.complete(warnConfig);
						});

						break;
					case TEMP_BAN:
						if (!guild.getSelfMember().hasPermission(Permission.BAN_MEMBERS)) {
							future.completeExceptionally(new BotPermissionException(Permission.BAN_MEMBERS));
							return;
						}

						if (!guild.getSelfMember().canInteract(target)) {
							future.completeExceptionally(new BotHierarchyException("ban"));
							return;
						}

						if (!moderator.hasPermission(Permission.BAN_MEMBERS)) {
							future.completeExceptionally(new AuthorPermissionException(Permission.BAN_MEMBERS));
							return;
						}

						target.ban(1).reason(ModUtility.getAuditReason(reason, moderator.getUser())).queue($ -> {
							manager.onModAction(new WarnEvent(moderator, target.getUser(), reason, warnConfig));

							TempBanManager.get().putBan(guild.getIdLong(), target.getIdLong(), ((TimeAction) action).getDuration());

							future.complete(warnConfig);
						});

						break;
					case BAN:
						if (!guild.getSelfMember().hasPermission(Permission.BAN_MEMBERS)) {
							future.completeExceptionally(new BotPermissionException(Permission.BAN_MEMBERS));
							return;
						}

						if (!guild.getSelfMember().canInteract(target)) {
							future.completeExceptionally(new BotHierarchyException("ban"));
							return;
						}

						if (!moderator.hasPermission(Permission.BAN_MEMBERS)) {
							future.completeExceptionally(new AuthorPermissionException(Permission.BAN_MEMBERS));
							return;
						}

						target.ban(1).reason(ModUtility.getAuditReason(reason, moderator.getUser())).queue($ -> {
							manager.onModAction(new WarnEvent(moderator, target.getUser(), reason, warnConfig));

							future.complete(warnConfig);
						});

						break;
					default:
						break;
				}
			}catch(Throwable e) {
				e.printStackTrace();
			}
		});

		return future;
	}
	
}
