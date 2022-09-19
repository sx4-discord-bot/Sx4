package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;

public class VcLinkCommand extends Sx4Command {

	public VcLinkCommand() {
		super("vc link", 354);

		super.setDescription("Gets the voice channel link for a voice channel");
		super.setAliases("vclink");
		super.setExamples("vc link", "vc link general");
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="voice channel", endless=true, nullDefault=true) AudioChannel channel) {
		AudioChannel effectiveChannel;
		if (channel == null) {
			effectiveChannel = event.getMember().getVoiceState().getChannel();
			if (effectiveChannel == null) {
				event.replyFailure("You are not in a voice channel").queue();
				return;
			}
		} else {
			effectiveChannel = channel;
		}

		event.reply("https://discord.com/channels/" + event.getGuild().getId() + "/" + effectiveChannel.getId()).queue();
	}

}
