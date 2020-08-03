package com.sx4.bot.commands.mod;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.option.Option;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReturnDocument;
import com.sx4.bot.category.Category;
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
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class MuteCommand extends Sx4Command {

	public MuteCommand() {
		super("mute");
		
		super.setExamples("mute @Shea#6653 20m", "mute Shea 30m Spamming", "mute 402557516728369153 12h template:offensive & Spamming");
		super.setDescription("Mute a user server wide");
		super.setAuthorDiscordPermissions(Permission.MESSAGE_MANAGE);
		super.setBotDiscordPermissions(Permission.MANAGE_ROLES);
		super.setCategoryAll(Category.MODERATION);
	}

	// TODO: Find a good way to avoid pushing data if a role failure happens, maybe cache mute roles
	public void onCommand(Sx4CommandEvent event, @Argument(value="user") Member member, @Argument(value="time", nullDefault=true) Duration time, @Argument(value="reason", endless=true, nullDefault=true) Reason reason, @Option(value="extend", description="Will extend the mute of the user if muted") boolean extend) {
		if (!event.getMember().canInteract(member)) {
			event.reply("You cannot mute someone higher or equal than your top role " + this.config.getFailureEmote()).queue();
			return;
		}

		if (!event.getSelfMember().canInteract(member)) {
			event.reply("I cannot mute someone higher or equal than your top role " + this.config.getFailureEmote()).queue();
			return;
		}

		Object seconds = time == null ? Operators.ifNull("$mute.defaultTime", 1800L) : time.toSeconds();
		AtomicLong atomicDuration = new AtomicLong();

		Bson muteFilter = Operators.filter("$mute.users", Operators.filter("$$this.id", member.getIdLong()));
		Bson unmuteAt = Operators.first(Operators.map(muteFilter, "$$this.unmuteAt"));

		List<Bson> update = List.of(Operators.set("mute.users", Operators.concatArrays(List.of(Operators.mergeObjects(Operators.ifNull(Operators.first(muteFilter), Database.EMPTY_DOCUMENT), new Document("id", member.getIdLong()).append("unmuteAt", Operators.cond(Operators.and(extend, Operators.nonNull(unmuteAt)), Operators.add(unmuteAt, seconds), Operators.add(Operators.nowEpochSecond(), seconds))))), Operators.ifNull(Operators.filter("$mute.users", Operators.ne("$$this.id", member.getIdLong())), Collections.EMPTY_LIST))));

		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.include("mute.roleId", "mute.autoUpdate", "mute.defaultTime"));
		this.database.findAndUpdateGuildById(event.getGuild().getIdLong(), update, options).thenCompose(data -> {
			data = data == null ? Database.EMPTY_DOCUMENT : data;

			Document mute = data.get("mute", Database.EMPTY_DOCUMENT);
			atomicDuration.set(mute.get("defaultTime", 1800L));

			return ModUtility.upsertMuteRole(event.getGuild(), mute.getLong("roleId"), mute.get("autoUpdate", true));
		}).whenComplete((role, exception) -> {
			if (exception != null) {
				if (exception instanceof MaxRolesException) {
					event.reply(exception.getMessage() + " " + this.config.getFailureEmote()).queue();
					return;
				}

				ExceptionUtility.sendExceptionally(event, exception);
				return;
			}

			long duration = atomicDuration.get();

			event.getGuild().addRoleToMember(member, role).reason(ModUtility.getAuditReason(reason, event.getAuthor())).queue($ -> {
				event.reply("**" + member.getUser().getAsTag() + "** has " + (extend ? "had their mute extended" : "been muted") + " for " + TimeUtility.getTimeString(duration) + " " + this.config.getSuccessEmote()).queue();

				this.muteManager.putMute(event.getGuild().getIdLong(), member.getIdLong(), role.getIdLong(), duration, extend);

				ModActionEvent modEvent = extend ? new MuteExtendEvent(event.getMember(), member.getUser(), reason, duration) : new MuteEvent(event.getMember(), member.getUser(), reason, duration);
				this.modManager.onModAction(modEvent);
			});
		});
	}

}
