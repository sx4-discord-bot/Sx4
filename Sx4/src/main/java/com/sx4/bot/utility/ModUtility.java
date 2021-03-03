package com.sx4.bot.utility;

import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import com.sx4.bot.database.Database;
import com.sx4.bot.database.model.Operators;
import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.entities.mod.action.*;
import com.sx4.bot.events.mod.*;
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
import java.time.Duration;
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

	public static CompletableFuture<TimeAction> mute(Member target, Member moderator, Duration time, boolean extend, Reason reason) {
		Guild guild = target.getGuild();
		Database database = Database.get();
		MuteManager manager = MuteManager.get();
		ModActionManager modManager = ModActionManager.get();

		CompletableFuture<TimeAction> future = new CompletableFuture<>();

		long guildId = guild.getIdLong(), userId = target.getIdLong();

		Document mute = database.getGuildById(guildId, Projections.include("mute.roleId", "mute.defaultTime", "mute.autoUpdate")).get("mute", Database.EMPTY_DOCUMENT);
		long duration = time == null ? mute.get("defaultTime", 1800L) : time.toSeconds();

		AtomicReference<Role> atomicRole = new AtomicReference<>();

		ModUtility.upsertMuteRole(guild, mute.get("roleId", 0L), mute.get("autoUpdate", true)).thenCompose(role -> {
			atomicRole.set(role);

			List<Bson> update = List.of(Operators.set("mute.unmuteAt", Operators.add(duration, Operators.cond(Operators.and(extend, Operators.exists("$mute.unmuteAt")), "$mute.unmuteAt", Operators.nowEpochSecond()))));
			return database.updateMemberById(userId, guildId, update);
		}).whenComplete((result, exception) -> {
			if (exception != null) {
				future.completeExceptionally(exception);
				return;
			}

			Role role = atomicRole.get();
			boolean wasExtended = extend && result.getUpsertedId() == null;

			guild.addRoleToMember(target, role).reason(ModUtility.getAuditReason(reason, moderator.getUser())).queue($ -> {
				manager.putMute(guild.getIdLong(), target.getIdLong(), role.getIdLong(), duration, wasExtended);

				ModActionEvent modEvent = wasExtended ? new MuteExtendEvent(moderator, target.getUser(), reason, duration) : new MuteEvent(moderator, target.getUser(), reason, duration);
				modManager.onModAction(modEvent);

				future.complete(new TimeAction(wasExtended ? ModAction.MUTE_EXTEND : ModAction.MUTE, duration));
			});
		});

		return future;
	}

	public static CompletableFuture<? extends Action> performAction(Action action, Member target, Member moderator, Reason reason) {
		ModActionManager manager = ModActionManager.get();
		Database database = Database.get();
		Guild guild = target.getGuild();

		switch (action.getModAction()) {
			case WARN:
				return ModUtility.warn(target, moderator, reason);
			case MUTE:
			case MUTE_EXTEND:
				if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
					return CompletableFuture.failedFuture(new BotPermissionException(Permission.MANAGE_ROLES));
				}

				return ModUtility.mute(target, moderator, Duration.ofSeconds(((TimeAction) action).getDuration()), action.getModAction().isExtend(), reason);
			case KICK:
				if (!guild.getSelfMember().hasPermission(Permission.KICK_MEMBERS)) {
					return CompletableFuture.failedFuture(new BotPermissionException(Permission.KICK_MEMBERS));
				}

				if (!guild.getSelfMember().canInteract(target)) {
					return CompletableFuture.failedFuture(new BotHierarchyException("kick"));
				}

				if (!moderator.hasPermission(Permission.KICK_MEMBERS)) {
					return CompletableFuture.failedFuture(new AuthorPermissionException(Permission.KICK_MEMBERS));
				}

				return target.kick(ModUtility.getAuditReason(reason, moderator.getUser())).submit().thenCompose($ -> {
					manager.onModAction(new KickEvent(moderator, target.getUser(), reason));

					return CompletableFuture.completedFuture(action);
				});
			case TEMPORARY_BAN:
				if (!guild.getSelfMember().hasPermission(Permission.BAN_MEMBERS)) {
					return CompletableFuture.failedFuture(new BotPermissionException(Permission.BAN_MEMBERS));
				}

				if (!guild.getSelfMember().canInteract(target)) {
					return CompletableFuture.failedFuture(new BotHierarchyException("ban"));
				}

				if (!moderator.hasPermission(Permission.BAN_MEMBERS)) {
					return CompletableFuture.failedFuture(new AuthorPermissionException(Permission.BAN_MEMBERS));
				}

				long temporaryBanDuration = ((TimeAction) action).getDuration();

				Bson temporaryBanUpdate = Updates.set("temporaryBan.unbanAt", Clock.systemUTC().instant().getEpochSecond() + temporaryBanDuration);
				return database.updateMemberById(target.getIdLong(), guild.getIdLong(), temporaryBanUpdate)
					.thenCompose(temporaryBanResult -> {
						return target.ban(1).reason(ModUtility.getAuditReason(reason, moderator.getUser())).submit().thenCompose($ -> {
							manager.onModAction(new TemporaryBanEvent(moderator, target.getUser(), reason, true, temporaryBanDuration));

							TemporaryBanManager.get().putBan(guild.getIdLong(), target.getIdLong(), temporaryBanDuration);

							return CompletableFuture.completedFuture(action);
						});
					});
			case BAN:
				if (!guild.getSelfMember().hasPermission(Permission.BAN_MEMBERS)) {
					return CompletableFuture.failedFuture(new BotPermissionException(Permission.BAN_MEMBERS));
				}

				if (!guild.getSelfMember().canInteract(target)) {
					return CompletableFuture.failedFuture(new BotHierarchyException("ban"));
				}

				if (!moderator.hasPermission(Permission.BAN_MEMBERS)) {
					return CompletableFuture.failedFuture(new AuthorPermissionException(Permission.BAN_MEMBERS));
				}

				return target.ban(1).reason(ModUtility.getAuditReason(reason, moderator.getUser())).submit().thenCompose($ -> {
					manager.onModAction(new BanEvent(moderator, target.getUser(), reason, true));

					return CompletableFuture.completedFuture(action);
				});
			default:
				return CompletableFuture.completedFuture(null);
		}
	}

	public static CompletableFuture<WarnAction> warn(Member target, Member moderator, Reason reason) {
		ModActionManager manager = ModActionManager.get();
		MuteManager muteManager = MuteManager.get();
		Database database = Database.get();
		CompletableFuture<WarnAction> future = new CompletableFuture<>();

		Guild guild = target.getGuild();

		Document data = database.getGuildById(guild.getIdLong(), Projections.include("warn", "mute", "temporaryBan"));

		Document warnData = data.get("warn", Database.EMPTY_DOCUMENT);

		List<Document> config = warnData.getList("config", Document.class, Warn.DEFAULT_CONFIG);
		boolean punishments = warnData.getBoolean("punishments", true);

		int maxWarning = punishments ? config.stream()
			.map(d -> d.getInteger("number"))
			.max(Integer::compareTo)
			.get() : Integer.MAX_VALUE;

		List<Bson> update = List.of(Operators.set("warn.warnings", Operators.add(Operators.mod(Operators.ifNull("$warn.warnings", 0), maxWarning), 1)));
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER).projection(Projections.include("warn.warnings")).upsert(true);

		database.findAndUpdateMemberById(target.getIdLong(), guild.getIdLong(), update, options).whenComplete((result, exception) -> {
			if (exception != null) {
				future.completeExceptionally(exception);
				return;
			}

			int warnings = result.getEmbedded(List.of("warn", "warnings"), Integer.class);

			Action warnAction = new Action(ModAction.WARN);

			Action action = punishments ? config.stream()
				.filter(d -> d.getInteger("number") == warnings)
				.map(d -> d.get("action", Document.class))
				.map(Action::fromData)
				.findFirst()
				.orElse(warnAction) : warnAction;

			Warn warnConfig = new Warn(action, warnings);

			switch (action.getModAction()) {
				case WARN:
					manager.onModAction(new WarnEvent(moderator, target.getUser(), reason, warnConfig));
					future.complete(new WarnAction(warnConfig));

					break;
				case MUTE:
					if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
						future.completeExceptionally(new BotPermissionException(Permission.MANAGE_ROLES));
						return;
					}

					AtomicReference<Role> atomicRole = new AtomicReference<>();

					Document mute = data.get("mute", Database.EMPTY_DOCUMENT);
					long muteDuration = ((TimeAction) action).getDuration();
					boolean extend = action.getModAction().isExtend();

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

							future.complete(new WarnAction(warnConfig));
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

						future.complete(new WarnAction(warnConfig));
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

							future.complete(new WarnAction(warnConfig));
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

						future.complete(new WarnAction(warnConfig));
					});

					break;
				default:
					break;
			}
		});

		return future;
	}
	
}
