package com.sx4.bot.commands.mod;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.events.mod.KickEvent;
import com.sx4.bot.utility.ModUtility;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;

public class KickCommand extends Sx4Command {

	public KickCommand() {
		super("kick");
		
		super.setAliases("kick user");
		super.setAuthorDiscordPermissions(Permission.KICK_MEMBERS);
		super.setBotDiscordPermissions(Permission.KICK_MEMBERS);
		super.setDescription("Kick a user from the current server");
		super.setExamples("kick @Shea", "kick Shea Spamming", "kick Shea#6653 template:tos", "kick 402557516728369153 t:tos and Spamming");
		super.setCategoryAll(ModuleCategory.MODERATION);
	}
	
	public void onCommand(Sx4CommandEvent event, @Argument(value="user") Member member, @Argument(value="reason", endless=true, nullDefault=true) Reason reason) {
		if (member.getIdLong() == event.getSelfUser().getIdLong()) {
			event.replyFailure("You cannot kick me, that is illegal").queue();
			return;
		}
		
		if (member.canInteract(event.getMember())) {
			event.replyFailure("You cannot kick someone higher or equal than your top role").queue();
			return;
		}
		
		if (member.canInteract(event.getSelfMember())) {
			event.replyFailure("I cannot kick someone higher or equal than my top role").queue();
		}
		
		member.kick().reason(ModUtility.getAuditReason(reason, event.getAuthor())).queue($ -> {
			event.reply("**" + member.getUser().getAsTag() + "** has been kicked <:done:403285928233402378>:ok_hand:").queue();
			
			this.modManager.onModAction(new KickEvent(event.getMember(), member.getUser(), reason));
		});
	}
	
}
