package com.sx4.bot.utility;

import com.mongodb.client.model.*;
import com.sx4.bot.database.Database;
import com.sx4.bot.database.model.Operators;
import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.entities.mod.action.Action;
import com.sx4.bot.entities.mod.action.ModAction;
import com.sx4.bot.entities.mod.action.TimeAction;
import com.sx4.bot.entities.mod.Warn;
import com.sx4.bot.events.mod.WarnEvent;
import com.sx4.bot.exceptions.mod.AuthorPermissionException;
import com.sx4.bot.exceptions.mod.BotHierarchyException;
import com.sx4.bot.exceptions.mod.BotPermissionException;
import com.sx4.bot.exceptions.mod.MaxRolesException;
import com.sx4.bot.managers.ModActionManager;
import com.sx4.bot.managers.MuteManager;
import com.sx4.bot.managers.TemporaryBanManager;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

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

					if (autoUpdate) {
						guild.getTextChannels().forEach(channel -> {
							if (selfMember.hasPermission(channel, Permission.MANAGE_PERMISSIONS)) {
								channel.upsertPermissionOverride(newRole).deny(Permission.MESSAGE_WRITE).queue();
							}
						});
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

		Document data = database.getGuildById(guild.getIdLong(), Projections.include("warn.config", "mute", "temporaryBan"));
		List<Document> config = data.getEmbedded(List.of("warn", "config"), Warn.DEFAULT_CONFIG);

		int maxWarning = config.stream()
			.map(d -> d.getInteger("number"))
			.max(Integer::compareTo)
			.get();

		List<Bson> update = List.of(Operators.set("warn.warnings", Operators.add(Operators.mod(Operators.ifNull("$warn.warnings", 0), maxWarning), 1)));
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER).projection(Projections.include("warn.warnings")).upsert(true);

		database.findAndUpdateMemberById(target.getIdLong(), guild.getIdLong(), update, options).whenComplete((result, exception) -> {
			if (exception != null) {
				future.completeExceptionally(exception);
				return;
			}

			int warnings = result.getEmbedded(List.of("warn", "warnings"), Integer.class);

			Action action = config.stream()
				.filter(d -> d.getInteger("number") == warnings)
				.map(d -> d.get("action", Document.class))
				.map(Action::fromData)
				.findFirst()
				.orElse(new Action(ModAction.WARN));

			Warn warnConfig = new Warn(action, warnings);

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

					AtomicReference<Role> atomicRole = new AtomicReference<>();

					Document mute = data.get("mute", Database.EMPTY_DOCUMENT);
					long muteDuration = ((TimeAction) action).getDuration();
					boolean extend = action.getModAction() == ModAction.MUTE_EXTEND;

					ModUtility.upsertMuteRole(guild, mute.get("roleId", 0L), mute.get("autoUpdate", true)).thenCompose(role -> {
						atomicRole.set(role);

						List<Bson> muteUpdate = List.of(Operators.set("mute.unmuteAt", Operators.add(muteDuration, Operators.cond(Operators.and(extend, Operators.exists("$mute.unmuteAt")), "$mute.unmuteAt", Operators.nowEpochSecond()))));
						return database.updateMemberById(target.getIdLong(), guild.getIdLong(), muteUpdate);
					}).whenComplete((muteResult, muteException) -> {
						if (muteException != null) {
							future.completeExceptionally(muteException);
							return;
						}

						Role role = atomicRole.get();

						guild.addRoleToMember(target, role).reason(ModUtility.getAuditReason(reason, moderator.getUser())).queue($ -> {
							muteManager.putMute(guild.getIdLong(), target.getIdLong(), role.getIdLong(), muteDuration,extend && muteResult.getUpsertedId() == null);

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
				case TEMPORARY_BAN:
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

					long temporaryBanDuration = ((TimeAction) action).getDuration();

					Bson temporaryBanUpdate = Updates.set("temporaryBan.unbanAt", Clock.systemUTC().instant().getEpochSecond() + temporaryBanDuration);
					database.updateMemberById(target.getIdLong(), guild.getIdLong(), temporaryBanUpdate).whenComplete((temporaryBanResult, temporaryBanException) -> {
						if (temporaryBanException != null) {
							future.completeExceptionally(temporaryBanException);
							return;
						}

						target.ban(1).reason(ModUtility.getAuditReason(reason, moderator.getUser())).queue($ -> {
							manager.onModAction(new WarnEvent(moderator, target.getUser(), reason, warnConfig));

							TemporaryBanManager.get().putBan(guild.getIdLong(), target.getIdLong(), ((TimeAction) action).getDuration());

							future.complete(warnConfig);
						});
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
		});

		return future;
	}
	
}
