package com.sx4.bot.commands.mod;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;

public class DisconnectCommand extends Sx4Command {

	public DisconnectCommand() {
		super("disconnect", 232);

		super.setAliases("voice kick", "voicekick");
		super.setDescription("Disconnects a user from the voice channel they are in");
		super.setExamples("disconnect", "disconnect @Shea#6653", "disconnect Shea");
		super.setCategoryAll(ModuleCategory.MODERATION);
		super.setAuthorDiscordPermissions(Permission.VOICE_MOVE_OTHERS);
		super.setBotDiscordPermissions(Permission.VOICE_MOVE_OTHERS);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="user", endless=true, nullDefault=true) Member memberOptional) {
		Member member = memberOptional == null ? event.getMember() : memberOptional;

		AudioChannel channel = member.getVoiceState().getChannel();
		if (channel == null) {
			event.replyFailure("That user is not in a voice channel").queue();
			return;
		}

		event.getGuild().moveVoiceMember(member, null)
			.flatMap($ -> event.replySuccess("**" + member.getUser().getAsTag() + "** has been disconnected"))
			.queue();
	}

}
