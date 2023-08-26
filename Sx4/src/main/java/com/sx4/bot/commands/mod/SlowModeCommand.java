package com.sx4.bot.commands.mod;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.utility.PermissionUtility;
import com.sx4.bot.utility.TimeUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.attribute.ISlowmodeChannel;

import java.time.Duration;
import java.util.EnumSet;

public class SlowModeCommand extends Sx4Command {

	public SlowModeCommand() {
		super("slowmode", 322);

		super.setDescription("Set the slowmode for the current channel");
		super.setAliases("slow mode");
		super.setExamples("slowmode 10m", "slowmode #channel 10s", "slowmode #images 6h");
		super.setAuthorDiscordPermissions(Permission.MANAGE_CHANNEL);
		super.setBotDiscordPermissions(Permission.MANAGE_CHANNEL);
		super.setCategoryAll(ModuleCategory.MODERATION);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="channel", nullDefault=true) ISlowmodeChannel channel, @Argument(value="duration", endless=true) Duration duration) {
		if (channel == null) {
			event.replyFailure("You cannot set slowmode in this channel").queue();
			return;
		}

		if (!event.getSelfMember().hasPermission(channel, Permission.MANAGE_CHANNEL)) {
			event.replyFailure(PermissionUtility.formatMissingPermissions(EnumSet.of(Permission.MANAGE_CHANNEL))).queue();
			return;
		}

		long seconds = duration.toSeconds();
		if (seconds < 0) {
			event.replyFailure("The duration cannot be any lower than 0 seconds");
			return;
		}

		if (seconds > 21600) {
			event.replyFailure("The duration cannot be any more than 6 hours").queue();
			return;
		}

		if (seconds == channel.getSlowmode()) {
			event.replyFailure("The slow mode in that channel is already set to that").queue();
			return;
		}

		channel.getManager().setSlowmode((int) seconds)
			.flatMap($ -> event.replySuccess(seconds == 0 ? "Turned off the slow mode in " + channel.getAsMention() : "Set the slow mode in " + channel.getAsMention() + " to " + TimeUtility.LONG_TIME_FORMATTER.parse(seconds)))
			.queue();
	}

}
