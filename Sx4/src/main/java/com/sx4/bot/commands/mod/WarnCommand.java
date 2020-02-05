package com.sx4.bot.commands.mod;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.impl.CommandEvent;
import com.sx4.bot.core.Sx4Command;

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
	
	public void onCommand(CommandEvent event, @Argument(value="user") Member member, @Argument(value="reason", endless=true, nullDefault=true) String reason) {
		if (member.getIdLong() == event.getSelfUser().getIdLong()) {
			event.reply("You cannot warn me, that is illegal :no_entry:").queue();
			return;
		}
		
		if (member.canInteract(event.getMember())) {
			event.reply("You cannot warn someone higher or equal than your top role :no_entry:").queue();
			return;
		}
		
		if (member.canInteract(event.getSelfMember())) {
			event.reply("I cannot warn someone higher or equal than my top role :no_entry:").queue();
			return;
		}
	}
	
}
