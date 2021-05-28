package com.sx4.bot.commands.mod;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.option.Option;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.sx4.bot.annotations.argument.DefaultNumber;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.annotations.command.AuthorPermissions;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.events.mod.TemporaryBanEvent;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.ModUtility;
import com.sx4.bot.utility.SearchUtility;
import com.sx4.bot.utility.TimeUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TemporaryBanCommand extends Sx4Command {

	public TemporaryBanCommand() {
		super("temporary ban", 148);
		
		super.setAliases("temp ban", "temporaryban");
		super.setDescription("Ban a user for a set amount of time");
		super.setExamples("temporary ban @Shea#6653 5d", "temporary ban Shea 30m Spamming", "temporary ban 402557516728369153 10d t:tos and Spamming");
		super.setAuthorDiscordPermissions(Permission.BAN_MEMBERS);
		super.setBotDiscordPermissions(Permission.BAN_MEMBERS);
		super.setCategoryAll(ModuleCategory.MODERATION);
	}
	
	public void onCommand(Sx4CommandEvent event, @Argument(value="user") String userArgument, @Argument(value="time", nullDefault=true) Duration time, @Argument(value="reason", endless=true, nullDefault=true) Reason reason, @Option(value="days", description="Set how many days of messages should be deleted from the user") @DefaultNumber(1) @Limit(min=0, max=7) int days) {
		SearchUtility.getUser(event.getShardManager(), userArgument).thenAccept(user -> {
			if (user == null) {
				event.replyFailure("I could not find that user").queue();
				return;
			}

			if (user.getIdLong() == event.getSelfUser().getIdLong()) {
				event.replyFailure("You cannot ban me, that is illegal").queue();
				return;
			}

			Guild guild = event.getGuild();

			Member member = guild.getMember(user);
			if (member != null) {
				if (!event.getMember().canInteract(member)) {
					event.replyFailure("You cannot ban someone higher or equal than your top role").queue();
					return;
				}

				if (!event.getSelfMember().canInteract(member)) {
					event.replyFailure("I cannot ban someone higher or equal than my top role").queue();
					return;
				}
			}

			event.getGuild().retrieveBan(user).submit().whenComplete((ban, exception) -> {
				if (exception instanceof ErrorResponseException && ((ErrorResponseException) exception).getErrorResponse() == ErrorResponse.UNKNOWN_BAN) {
					Document data = event.getMongo().getGuildById(guild.getIdLong(), Projections.include("temporaryBan.defaultTime")).get("temporaryBan", MongoDatabase.EMPTY_DOCUMENT);

					long duration = time == null ? data.get("defaultTime", ModUtility.DEFAULT_TEMPORARY_BAN_DURATION) : time.toSeconds();

					List<Bson> update = List.of(Operators.set("unbanAt", Operators.add(Operators.nowEpochSecond(), duration)));
					Bson filter = Filters.and(
						Filters.eq("userId", event.getMember().getIdLong()),
						Filters.eq("guildId", event.getGuild().getIdLong())
					);

					event.getMongo().updateTemporaryBan(filter, update, new UpdateOptions().upsert(true)).whenComplete((result, resultException) -> {
						if (ExceptionUtility.sendExceptionally(event, resultException)) {
							return;
						}

						event.getGuild().ban(user, days).reason(ModUtility.getAuditReason(reason, event.getAuthor())).queue($ -> {
							event.replySuccess("**" + user.getAsTag() + "** has been temporarily banned for " + TimeUtility.getTimeString(duration)).queue();

							event.getBot().getModActionManager().onModAction(new TemporaryBanEvent(event.getMember(), user, reason, member != null, duration));
							event.getBot().getTemporaryBanManager().putBan(event.getGuild().getIdLong(), user.getIdLong(), duration);
						});
					});
				} else {
					if (ExceptionUtility.sendExceptionally(event, exception)) {
						return;
					}

					event.replyFailure("That user is already banned").queue();
				}
			});
		});
	}

	@Command(value="default time", aliases={"default duration"}, description="Sets the default time to be used when a duration argument isn't given")
	@CommandId(344)
	@Examples({"temporary ban default time 10m", "temporary ban default time 5d", "temporary ban default time 1h 30m"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void defaultTime(Sx4CommandEvent event, @Argument(value="duration", endless=true) Duration duration) {
		long seconds = duration.toSeconds();

		Bson update = seconds == ModUtility.DEFAULT_TEMPORARY_BAN_DURATION ? Updates.unset("temporaryBan.defaultTime") : Updates.set("temporaryBan.defaultTime", seconds);
		event.getMongo().updateGuildById(event.getGuild().getIdLong(), update).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getModifiedCount() == 0 && result.getUpsertedId() == null) {
				event.replyFailure("Your temporary ban default time was already set to that").queue();
				return;
			}

			event.replySuccess("Your temporary ban default time has been set to **" + TimeUtility.getTimeString(seconds) + "**").queue();
		});
	}

	@Command(value="list", description="Lists all the users who have temporary bans")
	@CommandId(345)
	@Examples({"temporary ban list"})
	public void list(Sx4CommandEvent event) {
		List<Document> bans = event.getMongo().getTemporaryBans(Filters.eq("guildId", event.getGuild().getIdLong()), Projections.include("unbanAt", "userId")).into(new ArrayList<>());
		if (bans.isEmpty()) {
			event.replyFailure("There is no one with a temporary ban in this server").queue();
			return;
		}

		bans.sort(Comparator.comparingLong(d -> d.getLong("unbanAt")));

		PagedResult<Document> paged = new PagedResult<>(event.getBot(), bans)
			.setAuthor("Muted Users", null, event.getGuild().getIconUrl())
			.setIndexed(false)
			.setSelect()
			.setDisplayFunction(data -> {
				User user = event.getShardManager().getUserById(data.getLong("userId"));

				return (user == null ? "Anonymous#0000" : user.getAsTag()) + " - " + TimeUtility.getTimeString(data.getLong("unbanAt") - Clock.systemUTC().instant().getEpochSecond());
			});

		paged.execute(event);
	}
	
}
