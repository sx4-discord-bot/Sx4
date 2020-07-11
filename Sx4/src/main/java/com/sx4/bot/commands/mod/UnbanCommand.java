package com.sx4.bot.commands.mod;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.Category;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.events.mod.UnbanEvent;
import com.sx4.bot.utility.ModUtility;
import com.sx4.bot.utility.SearchUtility;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.requests.ErrorResponse;

public class UnbanCommand extends Sx4Command {

	public UnbanCommand() {
		super("unban");
		
		super.setAliases("unban user");
		super.setDescription("Unban a user which is banned from the current server");
		super.setAuthorDiscordPermissions(Permission.BAN_MEMBERS);
		super.setAuthorDiscordPermissions(Permission.BAN_MEMBERS);
		super.setExamples("unban @Shea", "unban Shea template:appeal", "unban Shea#6653 Mistake", "unban 402557516728369153 t:appeal and Mistake");
		super.setCategoryAll(Category.MODERATION);
	}
	
	public void onCommand(Sx4CommandEvent event, @Argument(value="user") String userArgument, @Argument(value="reason", endless=true, nullDefault=true) Reason reason) {
		SearchUtility.getUserRest(userArgument, user -> {
			if (user == null) {
				event.reply("I could not find that user " + this.config.getFailureEmote()).queue();
				return;
			}
			
			if (event.getGuild().isMember(user)) {
				event.reply("That user is not banned " + this.config.getFailureEmote()).queue();
				return;
			}
			
			event.getGuild().retrieveBan(user).queue(ban -> {
				event.getGuild().unban(user).reason(ModUtility.getAuditReason(reason, event.getAuthor())).queue($ -> {
					event.reply("**" + user.getAsTag() + "** has been unbanned <:done:403285928233402378>:ok_hand:").queue();
					
					this.modManager.onModAction(new UnbanEvent(event.getMember(), user, reason));
				});
			}, new ErrorHandler().handle(ErrorResponse.UNKNOWN_BAN, e -> event.reply("That user is not banned " + this.config.getFailureEmote()).queue()));
		});
	}
	
}
