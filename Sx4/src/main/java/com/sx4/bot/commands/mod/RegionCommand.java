package com.sx4.bot.commands.mod;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.Region;
import net.dv8tion.jda.api.entities.VoiceChannel;

public class RegionCommand extends Sx4Command {

	public RegionCommand() {
		super("region", 321);

		super.setDescription("Changes the server region in the current server");
		super.setExamples("region voice-channel Europe", "region London", "region voice-channel automatic");
		super.setCategoryAll(ModuleCategory.MODERATION);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="channel", nullDefault=true) VoiceChannel channel, @Argument(value="region", endless=true) Region region) {
		if (region.isVip() && !event.getGuild().getFeatures().contains("VIP_REGIONS")) {
			event.replyFailure("You cannot set the region to a VIP region without the VIP server feature").queue();
			return;
		}

		VoiceChannel effectiveChannel = channel == null ? event.getMember().getVoiceState().getChannel() : channel;
		if (effectiveChannel == null) {
			event.replyFailure("You are not in a voice channel").queue();
			return;
		}

		if (!event.getSelfMember().hasPermission(effectiveChannel, Permission.MANAGE_CHANNEL)) {
			event.replyFailure("I do not have permission to edit the region of " + effectiveChannel.getAsMention()).queue();
			return;
		}

		if (!event.getMember().hasPermission(effectiveChannel, Permission.MANAGE_CHANNEL)) {
			event.replyFailure("You do not have permission to edit the region of " + effectiveChannel.getAsMention()).queue();
			return;
		}

		if (region == effectiveChannel.getRegion()) {
			event.replyFailure("The region is already set to that").queue();
			return;
		}

		effectiveChannel.getManager().setRegion(region)
			.flatMap($ -> event.replySuccess("Successfully changed the voice region to **" + (region == Region.AUTOMATIC ? "Automatic" : region.getName() + " " + region.getEmoji()) + "** for " + effectiveChannel.getAsMention()))
			.queue();
	}

}
