package com.sx4.bot.commands.mod;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.option.Option;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.Database;
import com.sx4.bot.database.model.Operators;
import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.events.mod.ModActionEvent;
import com.sx4.bot.events.mod.MuteEvent;
import com.sx4.bot.events.mod.MuteExtendEvent;
import com.sx4.bot.exceptions.mod.MaxRolesException;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.ModUtility;
import com.sx4.bot.utility.TimeUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class MuteCommand extends Sx4Command {

	public MuteCommand() {
		super("mute", 139);
		
		super.setExamples("mute @Shea#6653 20m", "mute Shea 30m Spamming", "mute 402557516728369153 12h template:offensive & Spamming");
		super.setDescription("Mute a user server wide");
		super.setAuthorDiscordPermissions(Permission.MESSAGE_MANAGE);
		super.setBotDiscordPermissions(Permission.MANAGE_ROLES);
		super.setCategoryAll(ModuleCategory.MODERATION);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="user") Member member, @Argument(value="time", nullDefault=true) Duration time, @Argument(value="reason", endless=true, nullDefault=true) Reason reason, @Option(value="extend", description="Will extend the mute of the user if muted") boolean extend) {
		if (!event.getMember().canInteract(member)) {
			event.replyFailure("You cannot mute someone higher or equal than your top role").queue();
			return;
		}

		if (!event.getSelfMember().canInteract(member)) {
			event.replyFailure("I cannot mute someone higher or equal than your top role").queue();
			return;
		}

		long guildId = event.getGuild().getIdLong(), userId = member.getIdLong();

		Document mute = this.database.getGuildById(guildId, Projections.include("mute.roleId", "mute.defaultTime", "mute.autoUpdate")).get("mute", Database.EMPTY_DOCUMENT);
		long duration = time == null ? mute.get("defaultTime", 1800L) : time.toSeconds();

		AtomicReference<Role> atomicRole = new AtomicReference<>();

		ModUtility.upsertMuteRole(event.getGuild(), mute.get("roleId", 0L), mute.get("autoUpdate", true)).thenCompose(role -> {
			atomicRole.set(role);

			List<Bson> update = List.of(Operators.set("unmuteAt", Operators.add(duration, Operators.cond(Operators.and(extend, Operators.exists("$unmuteAt")), "$unmuteAt", Operators.nowEpochSecond()))));
			return this.database.updateMute(Filters.and(Filters.eq("guildId", guildId), Filters.eq("userId", userId)), update);
		}).whenComplete((result, exception) -> {
			if (exception instanceof MaxRolesException) {
				event.replyFailure(exception.getMessage()).queue();
				return;
			}

			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			Role role = atomicRole.get();
			boolean wasExtended = extend && result.getUpsertedId() == null;

			event.getGuild().addRoleToMember(member, role).reason(ModUtility.getAuditReason(reason, event.getAuthor())).queue($ -> {
				event.replyFormat("**%s** has %s for %s %s", member.getUser().getAsTag(), wasExtended ? "had their mute extended" : "been muted", TimeUtility.getTimeString(duration), this.config.getSuccessEmote()).queue();

				this.muteManager.putMute(event.getGuild().getIdLong(), member.getIdLong(), role.getIdLong(), duration, wasExtended);

				ModActionEvent modEvent = wasExtended ? new MuteExtendEvent(event.getMember(), member.getUser(), reason, duration) : new MuteEvent(event.getMember(), member.getUser(), reason, duration);
				this.modManager.onModAction(modEvent);
			});
		});
	}

}
