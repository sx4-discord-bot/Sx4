package com.sx4.bot.commands.mod;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.option.Option;
import com.mongodb.client.model.*;
import com.sx4.bot.annotations.command.AuthorPermissions;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.annotations.command.Redirects;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.model.Operators;
import com.sx4.bot.entities.argument.Alternative;
import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.ModUtility;
import com.sx4.bot.utility.TimeUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

		ModUtility.mute(event.getBot(), member, event.getMember(), time, extend, reason).whenComplete((action, exception) -> {
			if (exception != null) {
				event.replyFailure(exception.getMessage()).queue();
				return;
			}

			event.replyFormat("**%s** has %s for %s %s", member.getUser().getAsTag(), action.getModAction().isExtend() ? "had their mute extended" : "been muted", TimeUtility.getTimeString(action.getDuration()), event.getConfig().getSuccessEmote()).queue();
		});
	}

	@Command(value="role", description="Set the mute role to a custom one")
	@CommandId(340)
	@Examples({"mute role @Muted", "mute role Muted", "mute role reset"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void role(Sx4CommandEvent event, @Argument(value="role | reset", endless=true) Alternative<Role> option) {
		Role role = option.getValue();

		Bson update = option.isAlternative() ? Updates.unset("mute.roleId") : Updates.set("mute.roleId", role.getIdLong());
		event.getDatabase().updateGuildById(event.getGuild().getIdLong(), update).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getModifiedCount() == 0 && result.getUpsertedId() == null) {
				event.replyFailure("Your mute role was already " + (option.isAlternative() ? "unset" : "set to that")).queue();
				return;
			}

			event.replySuccess("Your mute role has been set to " + role.getAsMention()).queue();
		});
	}

	@Command(value="auto update", description="Toggles whether Sx4 should auto update the permissions for the mute role")
	@CommandId(341)
	@Examples({"mute auto update"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void autoUpdate(Sx4CommandEvent event) {
		List<Bson> update = List.of(Operators.set("mute.autoUpdate", Operators.cond(Operators.exists("$mute.autoUpdate"), Operators.REMOVE, false)));
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().upsert(true).projection(Projections.include("mute.autoUpdate")).returnDocument(ReturnDocument.AFTER);

		event.getDatabase().findAndUpdateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			event.replySuccess("The mute role will " + (data.getEmbedded(List.of("mute", "autoUpdate"), true) ? "now" : "no longer") + " automatically update its permissions").queue();
		});
	}

	@Command(value="default time", aliases={"default duration"}, description="Sets the default time to be used when a duration argument isn't given")
	@CommandId(342)
	@Examples({"mute default time 10m", "mute default time 5d", "mute default time 1h 30m"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void defaultTime(Sx4CommandEvent event, @Argument(value="duration", endless=true) Duration duration) {
		long seconds = duration.toSeconds();

		Bson update = seconds == ModUtility.DEFAULT_MUTE_DURATION ? Updates.unset("mute.defaultTime") : Updates.set("mute.defaultTime", seconds);
		event.getDatabase().updateGuildById(event.getGuild().getIdLong(), update).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getModifiedCount() == 0 && result.getUpsertedId() == null) {
				event.replyFailure("Your mute default time was already set to that").queue();
				return;
			}

			event.replySuccess("Your mute default time has been set to **" + TimeUtility.getTimeString(seconds) + "**").queue();
		});
	}

	@Command(value="list", description="Lists all the currently muted users in the server")
	@CommandId(343)
	@Redirects({"muted list"})
	@Examples({"mute list"})
	public void list(Sx4CommandEvent event) {
		List<Document> mutes = event.getDatabase().getMutes(Filters.eq("guildId", event.getGuild().getIdLong()), Projections.include("unmuteAt", "userId")).into(new ArrayList<>());
		if (mutes.isEmpty()) {
			event.replyFailure("There is no one muted in this server").queue();
			return;
		}

		mutes.sort(Comparator.comparingLong(d -> d.getLong("unmuteAt")));

		PagedResult<Document> paged = new PagedResult<>(event.getBot(), mutes)
			.setAuthor("Muted Users", null, event.getGuild().getIconUrl())
			.setIndexed(false)
			.setSelect()
			.setDisplayFunction(data -> {
				User user = event.getShardManager().getUserById(data.getLong("userId"));

				return (user == null ? "Anonymous#0000" : user.getAsTag()) + " - " + TimeUtility.getTimeString(data.getLong("unmuteAt") - Clock.systemUTC().instant().getEpochSecond());
			});

		paged.execute(event);
	}

}
