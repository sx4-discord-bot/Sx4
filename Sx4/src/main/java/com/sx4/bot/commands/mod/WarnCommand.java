package com.sx4.bot.commands.mod;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Context;
import com.jockie.bot.core.command.impl.CommandEvent;
import com.mongodb.client.model.Projections;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.database.Database;
import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.entities.mod.action.Action;
import com.sx4.bot.entities.mod.action.TimeAction;
import com.sx4.bot.entities.mod.warn.WarnData;
import com.sx4.bot.utility.NumberUtility;
import com.sx4.bot.utility.TimeUtility;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;

public class WarnCommand extends Sx4Command {

	public WarnCommand() {
		super("warn");
		
		super.setAliases("warn user");
		super.setDescription("Warn a user in the server, warning can give punishments on each warn a user gets");
		super.setAuthorDiscordPermissions(Permission.MESSAGE_MANAGE);
		super.setExamples("warn @Shea", "warn Shea Spamming", "warn Shea#6653 template:tos", "warn 402557516728369153 t:tos and Spamming");
	}
	
	public void onCommand(CommandEvent event, @Context Database database, @Argument(value="user") Member member, @Argument(value="reason", endless=true, nullDefault=true) Reason reason) {
		if (member.getIdLong() == event.getSelfUser().getIdLong()) {
			event.reply("You cannot warn me, that is illegal :no_entry:").queue();
			return;
		}
		
		if (member.canInteract(event.getMember())) {
			event.reply("You cannot warn someone higher or equal than your top role :no_entry:").queue();
			return;
		}
		
		WarnData data = new WarnData(database.getGuildById(event.getGuild().getIdLong(), Projections.include("warn")).get("warn", Database.EMPTY_DOCUMENT));
		data.warn(member, event.getMember(), reason, (warning, exception) -> {
			if (exception != null) {
				event.reply(exception.getMessage() + " :no_entry:").queue();
			} else {
				Action action = warning.getAction();
				
				event.replyFormat("**%s** has received a %s%s (%s warning) <:done:403285928233402378>", member.getUser().getAsTag(), action.getModAction().getName().toLowerCase(), action instanceof TimeAction ? " for " + TimeUtility.getTimeString(((TimeAction) action).getDuration()) : "", NumberUtility.getSuffixed(warning.getNumber())).queue();
			}
		});
	}
	
}
