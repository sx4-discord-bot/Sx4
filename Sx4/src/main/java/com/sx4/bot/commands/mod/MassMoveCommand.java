package com.sx4.bot.commands.mod;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.utility.PermissionUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.AudioChannel;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.requests.RestAction;

import java.util.ArrayList;
import java.util.List;

public class MassMoveCommand extends Sx4Command {

	public MassMoveCommand() {
		super("mass move", 319);

		super.setDescription("Move everyone in one voice channel to another");
		super.setAliases("massmove", "mm");
		super.setExamples("mass move Music", "mass move General 330400904589213697", "mass move 330399611090894848");
		super.setAuthorDiscordPermissions(Permission.VOICE_MOVE_OTHERS);
		super.setBotDiscordPermissions(Permission.VOICE_MOVE_OTHERS);
		super.setCategoryAll(ModuleCategory.MODERATION);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="from", nullDefault=true) AudioChannel from, @Argument(value="to", endless=true) AudioChannel to) {
		if (!PermissionUtility.canConnect(event.getSelfMember(), to)) {
			event.replyFailure("I cannot move people to that voice channel").queue();
			return;
		}

		if (!PermissionUtility.canConnect(event.getMember(), to)) {
			event.replyFailure("You cannot move people to that voice channel").queue();
			return;
		}

		AudioChannel channel = event.getMember().getVoiceState().getChannel();
		if (from == null && channel == null) {
			event.replyFailure("You are not in a voice channel").queue();
			return;
		}

		AudioChannel effectiveFrom = from == null ? channel : from;

		if (effectiveFrom.getIdLong() == to.getIdLong()) {
			event.replyFailure("You cannot provide the same voice channel twice").queue();
			return;
		}

		List<Member> members = effectiveFrom.getMembers();
		if (members.isEmpty()) {
			event.replyFailure("There are no users is that voice channel").queue();
			return;
		}

		List<RestAction<Void>> actions = new ArrayList<>();
		for (Member member : members) {
			actions.add(event.getGuild().moveVoiceMember(member, to));
		}

		RestAction.allOf(actions)
			.flatMap(completed -> event.replySuccess("Moved **" + completed.size() + "** user" + (completed.size() == 1 ? "" : "s") + " from `" + effectiveFrom.getName() + "` to `" + to.getName() + "`"))
			.queue();
	}

}
