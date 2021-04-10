package com.sx4.bot.utility;

import com.mongodb.client.model.*;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.Database;
import com.sx4.bot.database.model.Operators;
import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.entities.mod.action.*;
import com.sx4.bot.events.mod.*;
import com.sx4.bot.exceptions.mod.AuthorPermissionException;
import com.sx4.bot.exceptions.mod.BotHierarchyException;
import com.sx4.bot.exceptions.mod.BotPermissionException;
import com.sx4.bot.exceptions.mod.MaxRolesException;
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

	public static long DEFAULT_TEMPORARY_BAN_DURATION = 86400L;
	public static long DEFAULT_MUTE_DURATION = 1800L;

	public static String getAuditReason(Reason reason, User moderator) {
		return (reason == null ? "None Given" : reason.getParsed()) + " [" + moderator.getAsTag() + "]";
	}

	public static CompletableFuture<Role> upsertMuteRole(Database database, Guild guild, long roleId, boolean autoUpdate) {
		if (roleId != 0L) {
			Role role = guild.getRoleById(roleId);
			if (role != null) {
				return CompletableFuture.completedFuture(role);
			} else {
				return ModUtility.createMuteRole(database, guild, autoUpdate);
			}
		} else {
			return ModUtility.createMuteRole(database, guild, autoUpdate);
		}
	}

	private static CompletableFuture<Role> createMuteRole(Database database, Guild guild, boolean autoUpdate) {
		Member selfMember = guild.getSelfMember();

		if (guild.getRoleCache().size() >= 250) {
			return CompletableFuture.failedFuture(new MaxRolesException("The guild has the max roles possible (250) so I was unable to make the mute role"));
		}

		if (!selfMember.hasPermission(Permission.MANAGE_ROLES)) {
			return CompletableFuture.failedFuture(new BotPermissionException(Permission.MANAGE_ROLES));
		}

		CompletableFuture<Role> future = new CompletableFuture<>();
		guild.createRole().setName("Muted - " + selfMember.getUser().getName()).queue(newRole -> {
			database.updateGuildById(guild.getIdLong(), Updates.set("mute.roleId", newRole.getIdLong())).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendErrorMessage(guild.getJDA().getShardManager(), exception)) {
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

	public static CompletableFuture<TimeAction> mute(Sx4 bot, Member target, Member moderator, Duration time, boolean extend, Reason reason) {
		Guild guild = target.getGuild();

		long guildId = guild.getIdLong(), userId = target.getIdLong();

		Document mute = bot.getDatabase().getGuildById(guildId, Projections.include("mute.roleId", "mute.defaultTime", "mute.autoUpdate")).get("mute", Database.EMPTY_DOCUMENT);
		long duration = time == null ? mute.get("defaultTime", ModUtility.DEFAULT_MUTE_DURATION) : time.toSeconds();

		AtomicReference<Role> atomicRole = new AtomicReference<>();

		return ModUtility.upsertMuteRole(bot.getDatabase(), guild, mute.get("roleId", 0L), mute.get("autoUpdate", true)).thenCompose(role -> {
			atomicRole.set(role);

			List<Bson> update = List.of(Operators.set("unmuteAt", Operators.add(duration, Operators.cond(Operators.and(extend, Operators.exists("$unmuteAt")), "$unmuteAt", Operators.nowEpochSecond()))));
			Bson filter = Filters.and(
				Filters.eq("userId", userId),
				Filters.eq("guildId", guildId)
			);

			return bot.getDatabase().updateMute(filter, update, new UpdateOptions().upsert(true));
		}).thenCompose(result -> {
			Role role = atomicRole.get();
			boolean wasExtended = extend && result.getUpsertedId() == null;

			return guild.addRoleToMember(target, role).reason(ModUtility.getAuditReason(reason, moderator.getUser())).submit().thenApply($ -> {
				bot.getMuteManager().putMute(guild.getIdLong(), target.getIdLong(), role.getIdLong(), duration, wasExtended);

				ModActionEvent modEvent = wasExtended ? new MuteExtendEvent(moderator, target.getUser(), reason, duration) : new MuteEvent(moderator, target.getUser(), reason, duration);
				bot.getModActionManager().onModAction(modEvent);

				return new TimeAction(wasExtended ? ModAction.MUTE_EXTEND : ModAction.MUTE, duration);
			});
		});
	}

	public static CompletableFuture<? extends Action> performAction(Sx4 bot, Action action, Member target, Member moderator, Reason reason) {
		Guild guild = target.getGuild();

		switch (action.getModAction()) {
			case WARN:
				return ModUtility.warn(bot, target, moderator, reason);
			case MUTE:
			case MUTE_EXTEND:
				if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
					return CompletableFuture.failedFuture(new BotPermissionException(Permission.MANAGE_ROLES));
				}

				return ModUtility.mute(bot, target, moderator, Duration.ofSeconds(((TimeAction) action).getDuration()), action.getModAction().isExtend(), reason);
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

				return target.kick(ModUtility.getAuditReason(reason, moderator.getUser())).submit().thenApply($ -> {
					bot.getModActionManager().onModAction(new KickEvent(moderator, target.getUser(), reason));

					return action;
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

				List<Bson> temporaryBanUpdate = List.of(Operators.set("unbanAt", Operators.add(Operators.nowEpochSecond(), temporaryBanDuration)));
				Bson filter = Filters.and(
					Filters.eq("userId", target.getIdLong()),
					Filters.eq("guildId", guild.getIdLong())
				);

				return bot.getDatabase().updateTemporaryBan(filter, temporaryBanUpdate, new UpdateOptions().upsert(true))
					.thenCompose(temporaryBanResult -> target.ban(1).reason(ModUtility.getAuditReason(reason, moderator.getUser())).submit())
					.thenApply($ -> {
						bot.getModActionManager().onModAction(new TemporaryBanEvent(moderator, target.getUser(), reason, true, temporaryBanDuration));

						bot.getTemporaryBanManager().putBan(guild.getIdLong(), target.getIdLong(), temporaryBanDuration);

						return action;
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

				return target.ban(1).reason(ModUtility.getAuditReason(reason, moderator.getUser())).submit().thenApply($ -> {
					bot.getModActionManager().onModAction(new BanEvent(moderator, target.getUser(), reason, true));

					return action;
				});
			default:
				return CompletableFuture.completedFuture(null);
		}
	}

	public static CompletableFuture<WarnAction> warn(Sx4 bot, Member target, Member moderator, Reason reason) {
		CompletableFuture<WarnAction> future = new CompletableFuture<>();

		Guild guild = target.getGuild();

		Document data = bot.getDatabase().getGuildById(guild.getIdLong(), Projections.include("warn", "mute", "temporaryBan"));

		Document warnData = data.get("warn", Database.EMPTY_DOCUMENT);

		List<Document> config = warnData.getList("config", Document.class, Warn.DEFAULT_CONFIG);
		boolean punishments = warnData.getBoolean("punishments", true);

		int maxWarning = punishments ? config.stream()
			.map(d -> d.getInteger("number"))
			.max(Integer::compareTo)
			.get() : Integer.MAX_VALUE;

		List<Bson> update = List.of(Operators.set("warnings", Operators.add(Operators.mod(Operators.ifNull("$warnings", 0), maxWarning), 1)));
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER).projection(Projections.include("warnings")).upsert(true);
		Bson filter = Filters.and(
			Filters.eq("userId", target.getIdLong()),
			Filters.eq("guildId", guild.getIdLong())
		);

		bot.getDatabase().findAndUpdateWarnings(filter, update, options).whenComplete((result, exception) -> {
			if (exception != null) {
				future.completeExceptionally(exception);
				return;
			}

			int warnings = result.getInteger("warnings");

			Action warnAction = new Action(ModAction.WARN);

			Action currentAction = warnAction, nextAction = punishments ? warnAction : null;
			for (Document configData : config) {
				int number = configData.getInteger("number");
				if (number == warnings) {
					currentAction = Action.fromData(configData.get("action", Document.class));
				} else if (number == warnings + 1) {
					nextAction = Action.fromData(configData.get("action", Document.class));
				}
			}

			Action action = currentAction;

			Warn currentWarning = new Warn(action, warnings);
			Warn nextWarning = new Warn(nextAction, warnings + 1);

			switch (action.getModAction()) {
				case WARN:
					bot.getModActionManager().onModAction(new WarnEvent(moderator, target.getUser(), reason, currentWarning, nextWarning));
					future.complete(new WarnAction(currentWarning));

					break;
				case MUTE_EXTEND:
				case MUTE:
					if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
						future.completeExceptionally(new BotPermissionException(Permission.MANAGE_ROLES));
						return;
					}

					AtomicReference<Role> atomicRole = new AtomicReference<>();

					Document mute = data.get("mute", Database.EMPTY_DOCUMENT);
					long muteDuration = ((TimeAction) action).getDuration();
					boolean extend = action.getModAction().isExtend();

					ModUtility.upsertMuteRole(bot.getDatabase(), guild, mute.get("roleId", 0L), mute.get("autoUpdate", true)).thenCompose(role -> {
						atomicRole.set(role);

						List<Bson> muteUpdate = List.of(Operators.set("unmuteAt", Operators.add(muteDuration, Operators.cond(Operators.and(extend, Operators.exists("$unmuteAt")), "$unmuteAt", Operators.nowEpochSecond()))));
						Bson muteFilter = Filters.and(
							Filters.eq("userId", target.getIdLong()),
							Filters.eq("guildId", guild.getIdLong())
						);

						return bot.getDatabase().updateMute(muteFilter, muteUpdate, new UpdateOptions().upsert(true));
					}).whenComplete((muteResult, muteException) -> {
						if (muteException != null) {
							future.completeExceptionally(muteException);
							return;
						}

						Role role = atomicRole.get();

						guild.addRoleToMember(target, role).reason(ModUtility.getAuditReason(reason, moderator.getUser())).queue($ -> {
							bot.getMuteManager().putMute(guild.getIdLong(), target.getIdLong(), role.getIdLong(), muteDuration,extend && muteResult.getUpsertedId() == null);

							bot.getModActionManager().onModAction(new WarnEvent(moderator, target.getUser(), reason, currentWarning, nextWarning));

							future.complete(new WarnAction(currentWarning));
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
						bot.getModActionManager().onModAction(new WarnEvent(moderator, target.getUser(), reason, currentWarning, nextWarning));

						future.complete(new WarnAction(currentWarning));
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

					Bson temporaryBanUpdate = Updates.set("unbanAt", Clock.systemUTC().instant().getEpochSecond() + temporaryBanDuration);
					Bson temporaryBanFilter = Filters.and(
						Filters.eq("userId", target.getIdLong()),
						Filters.eq("guildId", guild.getIdLong())
					);

					bot.getDatabase().updateTemporaryBan(temporaryBanFilter, temporaryBanUpdate, new UpdateOptions().upsert(true)).whenComplete((temporaryBanResult, temporaryBanException) -> {
						if (temporaryBanException != null) {
							future.completeExceptionally(temporaryBanException);
							return;
						}

						target.ban(1).reason(ModUtility.getAuditReason(reason, moderator.getUser())).queue($ -> {
							bot.getModActionManager().onModAction(new WarnEvent(moderator, target.getUser(), reason, currentWarning, nextWarning));

							bot.getTemporaryBanManager().putBan(guild.getIdLong(), target.getIdLong(), ((TimeAction) action).getDuration());

							future.complete(new WarnAction(currentWarning));
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
						bot.getModActionManager().onModAction(new WarnEvent(moderator, target.getUser(), reason, currentWarning, nextWarning));

						future.complete(new WarnAction(currentWarning));
					});

					break;
				default:
					break;
			}
		});

		return future;
	}
	
}
