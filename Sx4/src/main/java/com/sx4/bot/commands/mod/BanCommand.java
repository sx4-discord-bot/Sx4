package com.sx4.bot.commands.mod;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.option.Option;
import com.sx4.bot.annotations.argument.DefaultNumber;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.events.mod.BanEvent;
import com.sx4.bot.utility.ModUtility;
import com.sx4.bot.utility.SearchUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;

import java.util.concurrent.TimeUnit;

public class BanCommand extends Sx4Command {

	public BanCommand() {
		super("ban", 128);
		
		super.setCaseSensitive(true);
		super.setAuthorDiscordPermissions(Permission.BAN_MEMBERS);
		super.setBotDiscordPermissions(Permission.BAN_MEMBERS);
		super.setDescription("Ban any user from the current server");
		super.setAliases("ban user");
		super.setExamples("ban @Shea#6653", "ban Shea Spamming", "ban Shea#6653 template:tos", "ban 402557516728369153 t:tos and Spamming");
		super.setCategoryAll(ModuleCategory.MODERATION);
	}
	
	public void onCommand(Sx4CommandEvent event, @Argument(value="user") String userArgument, @Argument(value="reason", endless=true, nullDefault=true) Reason reason, @Option(value="days", description="Set how many days of messages should be deleted from the user") @DefaultNumber(1) @Limit(min=0, max=7) int days) {
		SearchUtility.getUser(event.getShardManager(), userArgument).thenAccept(user -> {
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
					event.getGuild().ban(user, days, TimeUnit.DAYS).reason(ModUtility.getAuditReason(reason, event.getAuthor())).queue($ -> {
						event.replySuccess("**" + user.getAsTag() + "** has been banned").queue();

						event.getBot().getModActionManager().onModAction(new BanEvent(event.getMember(), user, reason, member != null));
					});
				} else {
					event.replyFailure("That user is already banned").queue();
				}
			});
		});
	}
	
}
