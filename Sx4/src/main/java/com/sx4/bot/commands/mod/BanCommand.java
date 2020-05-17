package com.sx4.bot.commands.mod;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.option.Option;
import com.sx4.bot.category.Category;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.events.mod.BanEvent;
import com.sx4.bot.utility.ModUtility;
import com.sx4.bot.utility.SearchUtility;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.requests.ErrorResponse;

public class BanCommand extends Sx4Command {

	public BanCommand() {
		super("ban");
		
		super.setCaseSensitive(true);
		super.setAuthorDiscordPermissions(Permission.BAN_MEMBERS);
		super.setBotDiscordPermissions(Permission.BAN_MEMBERS);
		super.setDescription("Ban any user from the current server");
		super.setAliases("ban user");
		super.setExamples("ban @Shea", "ban Shea Spamming", "ban Shea#6653 template:tos", "ban 402557516728369153 t:tos and Spamming");
		super.setCategory(Category.MODERATION);
	}
	
	public void onCommand(Sx4CommandEvent event, @Argument(value="user") String userArgument, @Argument(value="reason", endless=true, nullDefault=true) Reason reason, @Option(value="days", description="Set how many days of messages should be deleted from the user") String days) {
		SearchUtility.getUserRest(event.getGuild(), userArgument, user -> {
			if (user == null) {
				event.reply("I could not find that user :no_entry:").queue();
				return;
			}
			
			if (user.getIdLong() == event.getSelfUser().getIdLong()) {
				event.reply("You cannot ban me, that is illegal :no_entry:").queue();
				return;
			}
			
			Member member = event.getGuild().getMember(user);
			if (member != null) {
				if (member.canInteract(event.getMember())) {
					event.reply("You cannot ban someone higher or equal than your top role :no_entry:").queue();
					return;
				}
				
				if (member.canInteract(event.getSelfMember())) {
					event.reply("I cannot ban someone higher or equal than my top role :no_entry:").queue();
					return;
				}
			}
			
			event.getGuild().retrieveBan(user).queue(ban -> {
				event.reply("That user is already banned :no_entry:").queue();
			}, new ErrorHandler().handle(ErrorResponse.UNKNOWN_BAN, e -> {
				int deleteDays = 1;
				if (days != null) {
					try {
						deleteDays = Integer.parseInt(days);
					} catch (NumberFormatException ex) {}
				}
				
				deleteDays = deleteDays < 0 ? 0 : deleteDays > 7 ? 7 : deleteDays;
				
				event.getGuild()
					.ban(user, deleteDays)
					.reason(ModUtility.getAuditReason(reason, event.getAuthor()))
					.queue($ -> {
						event.reply("**" + user.getAsTag() + "** has been banned <:done:403285928233402378>:ok_hand:").queue();
						
						this.modManager.onModAction(new BanEvent(event.getMember(), user, reason, member != null));
					});
			}));
		});
	}
	
}
