package com.sx4.bot.commands.mod;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.option.Option;
import com.mongodb.client.model.Projections;
import com.sx4.bot.annotations.argument.DefaultNumber;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.Database;
import com.sx4.bot.database.model.Operators;
import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.events.mod.TemporaryBanEvent;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.ModUtility;
import com.sx4.bot.utility.SearchUtility;
import com.sx4.bot.utility.TimeUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Duration;
import java.util.List;

public class TemporaryBanCommand extends Sx4Command {

	public TemporaryBanCommand() {
		super("temporary ban", 148);
		
		super.setAliases("temp ban", "temporaryBan", "temporaryban");
		super.setDescription("Ban a user for a set amount of time");
		super.setExamples("temporary ban @Shea#6653 5d", "temporary ban Shea 30m Spamming", "temporary ban 402557516728369153 10d t:tos and Spamming");
		super.setAuthorDiscordPermissions(Permission.BAN_MEMBERS);
		super.setBotDiscordPermissions(Permission.BAN_MEMBERS);
		super.setCategoryAll(ModuleCategory.MODERATION);
	}
	
	public void onCommand(Sx4CommandEvent event, @Argument(value="user") String userArgument, @Argument(value="time", nullDefault=true) Duration time, @Argument(value="reason", endless=true, nullDefault=true) Reason reason, @Option(value="days", description="Set how many days of messages should be deleted from the user") @DefaultNumber(1) @Limit(min=0, max=7) int days) {
		SearchUtility.getUserRest(userArgument).thenAccept(user -> {
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
				if (member.canInteract(event.getMember())) {
					event.replyFailure("You cannot ban someone higher or equal than your top role").queue();
					return;
				}

				if (member.canInteract(event.getSelfMember())) {
					event.replyFailure("I cannot ban someone higher or equal than my top role").queue();
					return;
				}
			}

			event.getGuild().retrieveBan(user).submit().whenComplete((ban, exception) -> {
				if (exception instanceof ErrorResponseException && ((ErrorResponseException) exception).getErrorResponse() == ErrorResponse.UNKNOWN_BAN) {
					Document data = this.database.getGuildById(guild.getIdLong(), Projections.include("temporaryBan.defaultTime")).get("temporaryBan", Database.EMPTY_DOCUMENT);

					long duration = time == null ? data.get("defaultTime", 86400L) : time.toSeconds();

					List<Bson> update = List.of(Operators.set("temporaryBan.unbanAt", Operators.add(Operators.nowEpochSecond(), duration)));
					this.database.updateMemberById(user.getIdLong(), guild.getIdLong(), update).whenComplete((result, resultException) -> {
						if (ExceptionUtility.sendExceptionally(event, resultException)) {
							return;
						}

						event.getGuild()
							.ban(user, days)
							.reason(ModUtility.getAuditReason(reason, event.getAuthor()))
							.queue($ -> {
								event.reply("**" + user.getAsTag() + "** has been temporarily banned for " + TimeUtility.getTimeString(duration) + " <:done:403285928233402378>:ok_hand:").queue();

								this.modManager.onModAction(new TemporaryBanEvent(event.getMember(), user, reason, member != null, duration));

								this.banManager.putBan(event.getGuild().getIdLong(), user.getIdLong(), duration);
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
	
}
