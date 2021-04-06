package com.sx4.bot.commands.mod;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.events.mod.UnbanEvent;
import com.sx4.bot.utility.ModUtility;
import com.sx4.bot.utility.SearchUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;

public class UnbanCommand extends Sx4Command {

	public UnbanCommand() {
		super("unban", 149);
		
		super.setAliases("unban user");
		super.setDescription("Unban a user which is banned from the current server");
		super.setAuthorDiscordPermissions(Permission.BAN_MEMBERS);
		super.setAuthorDiscordPermissions(Permission.BAN_MEMBERS);
		super.setExamples("unban @Shea#6653", "unban Shea template:appeal", "unban Shea#6653 Mistake", "unban 402557516728369153 t:appeal and Mistake");
		super.setCategoryAll(ModuleCategory.MODERATION);
	}
	
	public void onCommand(Sx4CommandEvent event, @Argument(value="user") String userArgument, @Argument(value="reason", endless=true, nullDefault=true) Reason reason) {
		SearchUtility.getUserRest(event.getShardManager(), userArgument).thenAccept(user -> {
			if (user == null) {
				event.replyFailure("I could not find that user").queue();
				return;
			}
			
			if (event.getGuild().isMember(user)) {
				event.replyFailure("That user is not banned").queue();
				return;
			}
			
			event.getGuild().retrieveBan(user).submit()
				.thenCompose(ban -> event.getGuild().unban(user).reason(ModUtility.getAuditReason(reason, event.getAuthor())).submit())
				.whenComplete(($, exception) -> {
					if (exception instanceof ErrorResponseException && ((ErrorResponseException) exception).getErrorResponse() == ErrorResponse.UNKNOWN_BAN) {
						event.replyFailure("That user is not banned").queue();
						return;
					}

					event.reply("**" + user.getAsTag() + "** has been unbanned <:done:403285928233402378>:ok_hand:").queue();

					event.getBot().getModActionManager().onModAction(new UnbanEvent(event.getMember(), user, reason));
					event.getBot().getTemporaryBanManager().removeBan(event.getGuild().getIdLong(), user.getIdLong(), false);
				});
		});
	}
	
}
