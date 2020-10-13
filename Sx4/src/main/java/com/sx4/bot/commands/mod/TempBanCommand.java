package com.sx4.bot.commands.mod;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.option.Option;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReturnDocument;
import com.sx4.bot.annotations.argument.DefaultInt;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.Database;
import com.sx4.bot.database.model.Operators;
import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.events.mod.TempBanEvent;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.ModUtility;
import com.sx4.bot.utility.SearchUtility;
import com.sx4.bot.utility.TimeUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

public class TempBanCommand extends Sx4Command {

	public TempBanCommand() {
		super("temporary ban");
		
		super.setAliases("temp ban", "tempban", "temporaryban");
		super.setDescription("Ban a user for a set amount of time");
		super.setExamples("temporary ban @Shea#6653 5d", "temporary ban Shea 30m Spamming", "temporary ban 402557516728369153 10d t:tos and Spamming");
		super.setAuthorDiscordPermissions(Permission.BAN_MEMBERS);
		super.setBotDiscordPermissions(Permission.BAN_MEMBERS);
		super.setCategoryAll(ModuleCategory.MODERATION);
	}
	
	public void onCommand(Sx4CommandEvent event, @Argument(value="user") String userArgument, @Argument(value="time", nullDefault=true) Duration time, @Argument(value="reason", endless=true, nullDefault=true) Reason reason, @Option(value="days", description="Set how many days of messages should be deleted from the user") @DefaultInt(1) @Limit(min=0, max=7) int days) {
		SearchUtility.getUserRest(event.getGuild(), userArgument).thenAccept(user -> {
			if (user == null) {
				event.replyFailure("I could not find that user").queue();
				return;
			}

			if (user.getIdLong() == event.getSelfUser().getIdLong()) {
				event.replyFailure("You cannot ban me, that is illegal").queue();
				return;
			}

			Member member = event.getGuild().getMember(user);
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
					Bson banFilter = Operators.filter("$tempBan.users", Operators.eq("$$this.id", user.getIdLong()));
					List<Bson> update = List.of(Operators.set("tempBan.users", Operators.concatArrays(List.of(Operators.mergeObjects(Operators.ifNull(Operators.first(banFilter), Database.EMPTY_DOCUMENT), new Document("id", user.getIdLong()).append("unbanAt", Operators.add(Operators.nowEpochSecond(), time == null ? Operators.ifNull("$tempBan.defaultTime", 86400L) : time.toSeconds())))), Operators.ifNull(Operators.filter("$tempBan.users", Operators.ne("$$this.id", user.getIdLong())), Collections.EMPTY_LIST))));

					FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.include("tempBan.defaulTime"));
					this.database.findAndUpdateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((data, dataException) -> {
						if (ExceptionUtility.sendExceptionally(event, dataException)) {
							return;
						}

						long seconds = time == null ? data == null ? 86400L : data.getEmbedded(List.of("tempBan", "defaultTime"), 86400L) : time.toSeconds();

						event.getGuild()
							.ban(user, days)
							.reason(ModUtility.getAuditReason(reason, event.getAuthor()))
							.queue($ -> {
								event.reply("**" + user.getAsTag() + "** has been temporarily banned for " + TimeUtility.getTimeString(seconds) + " <:done:403285928233402378>:ok_hand:").queue();

								this.modManager.onModAction(new TempBanEvent(event.getMember(), user, reason, member != null, seconds));

								this.banManager.putBan(event.getGuild().getIdLong(), user.getIdLong(), seconds);
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
