package com.sx4.bot.commands.mod;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.utility.PermissionUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.AudioChannel;
import net.dv8tion.jda.api.entities.Member;

public class MoveCommand extends Sx4Command {

	public MoveCommand() {
		super("move", 320);

		super.setDescription("Move a user from their current voice channel to another");
		super.setExamples("move Music", "move @Shea#6653 330400904589213697", "move Shea");
		super.setAuthorDiscordPermissions(Permission.VOICE_MOVE_OTHERS);
		super.setBotDiscordPermissions(Permission.VOICE_MOVE_OTHERS);
		super.setCategoryAll(ModuleCategory.MODERATION);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="user", nullDefault=true) Member member, @Argument(value="voice channel", nullDefault=true) AudioChannel channel) {
		if (member == null && channel == null) {
			event.replyFailure("Either a user or voice channel needs to be provided").queue();
			return;
		}

		Member effectiveMember = member == null ? event.getMember() : member;
		AudioChannel memberChannel = effectiveMember.getVoiceState().getChannel();
		if (memberChannel == null) {
			event.replyFailure("That user is not in a voice channel").queue();
			return;
		}

		AudioChannel effectiveChannel = channel == null ? event.getMember().getVoiceState().getChannel() : channel;
		if (effectiveChannel == null) {
			event.replyFailure("You are not in a voice channel").queue();
			return;
		}

		if (memberChannel.getIdLong() == effectiveChannel.getIdLong()) {
			event.replyFailure("You cannot move a user to the channel they are already connected to").queue();
			return;
		}

		if (!PermissionUtility.canConnect(event.getSelfMember(), effectiveChannel)) {
			event.replyFailure("I cannot move people to that voice channel").queue();
			return;
		}

		if (!PermissionUtility.canConnect(event.getMember(), effectiveChannel)) {
			event.replyFailure("You cannot move people to that voice channel").queue();
			return;
		}

		event.getGuild().moveVoiceMember(effectiveMember, effectiveChannel)
			.flatMap($ -> event.replySuccess("Moved **" + effectiveMember.getUser().getAsTag() + "** to `" + effectiveChannel.getName() + "`"))
			.queue();
	}

}
