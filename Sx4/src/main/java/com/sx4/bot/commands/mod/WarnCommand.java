package com.sx4.bot.commands.mod;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.entities.mod.action.Action;
import com.sx4.bot.entities.mod.action.TimeAction;
import com.sx4.bot.utility.ModUtility;
import com.sx4.bot.utility.NumberUtility;
import com.sx4.bot.utility.TimeUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;

public class WarnCommand extends Sx4Command {

	public WarnCommand() {
		super("warn", 151);
		
		super.setAliases("warn user");
		super.setDescription("Warn a user in the server, warning can give punishments on each warn a user gets");
		super.setAuthorDiscordPermissions(Permission.MESSAGE_MANAGE);
		super.setExamples("warn @Shea", "warn Shea Spamming", "warn Shea#6653 template:tos", "warn 402557516728369153 t:tos and Spamming");
		super.setCategoryAll(ModuleCategory.MODERATION);
	}
	
	public void onCommand(Sx4CommandEvent event, @Argument(value="user") Member member, @Argument(value="reason", endless=true, nullDefault=true) Reason reason) {
		if (member.getIdLong() == event.getSelfUser().getIdLong()) {
			event.replyFailure("You cannot warn me, that is illegal").queue();
			return;
		}
		
		if (member.canInteract(event.getMember())) {
			event.replyFailure("You cannot warn someone higher or equal than your top role").queue();
			return;
		}

		ModUtility.warn(member, event.getMember(), reason).whenComplete((warning, exception) -> {
			if (exception != null) {
				event.replyFailure(exception.getMessage()).queue();
			} else {
				Action action = warning.getAction();

				event.replyFormat("**%s** has received a %s%s (%s warning) " + this.config.getSuccessEmote(), member.getUser().getAsTag(), action.getModAction().getName().toLowerCase(), action instanceof TimeAction ? " for " + TimeUtility.getTimeString(((TimeAction) action).getDuration()) : "", NumberUtility.getSuffixed(warning.getNumber())).queue();
			}
		});
	}
	
}
