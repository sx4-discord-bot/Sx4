package com.sx4.bot.commands.mod;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;

public class RenameCommand extends Sx4Command {

	public RenameCommand() {
		super("rename", 252);

		super.setDescription("Rename a member in the server");
		super.setAliases("nick", "nickname");
		super.setExamples("rename @Shea#6653 tjej", "rename lakrits e-girl hunter", "rename tjej");
		super.setBotDiscordPermissions(Permission.NICKNAME_CHANGE);
		super.setAuthorDiscordPermissions(Permission.NICKNAME_CHANGE);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="user", nullDefault=true) Member member, @Argument(value="nickname", endless=true, nullDefault=true) @Limit(max=32) String nick) {
		if (member != null && member.canInteract(event.getMember())) {
			event.replyFailure("You cannot change the nickname of someone higher or equal than your top role").queue();
			return;
		}

		Member effectiveMember = member == null ? event.getMember() : member;

		event.getGuild().modifyNickname(effectiveMember, nick)
			.flatMap($ -> event.replySuccess("Renamed " + effectiveMember.getAsMention() + " to **" + (nick == null ? effectiveMember.getEffectiveName() : nick) + "**"))
			.queue();
	}

}
