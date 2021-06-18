package com.sx4.bot.commands.mod;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.utility.PermissionUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;

import java.util.EnumSet;

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
		Member effectiveMember = member == null ? event.getMember() : member;
		if (effectiveMember.getIdLong() != event.getMember().getIdLong() && !event.hasPermission(event.getMember(), Permission.NICKNAME_MANAGE)) {
			event.replyFailure(PermissionUtility.formatMissingPermissions(EnumSet.of(Permission.NICKNAME_MANAGE))).queue();
			return;
		}

		if (effectiveMember.getIdLong() != event.getMember().getIdLong() && !event.getMember().canInteract(effectiveMember)) {
			event.replyFailure("You cannot change the nickname of someone higher or equal than your top role").queue();
			return;
		}

		if (effectiveMember.getIdLong() != event.getSelfUser().getIdLong() && !event.getSelfMember().hasPermission(Permission.NICKNAME_MANAGE)) {
			event.replyFailure(PermissionUtility.formatMissingPermissions(EnumSet.of(Permission.NICKNAME_MANAGE), "I am")).queue();
			return;
		}

		if (effectiveMember.getIdLong() != event.getSelfUser().getIdLong() && !event.getSelfMember().canInteract(effectiveMember)) {
			event.replyFailure("I cannot change the nickname of someone higher or equal than my top role").queue();
			return;
		}

		event.getGuild().modifyNickname(effectiveMember, nick)
			.flatMap($ -> event.replySuccess("Renamed " + effectiveMember.getAsMention() + " to **" + (nick == null ? effectiveMember.getEffectiveName() : nick) + "**"))
			.queue();
	}

}
