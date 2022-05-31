package com.sx4.bot.commands.mod;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;

public class LockdownCommand extends Sx4Command {

	public LockdownCommand() {
		super("lockdown", 223);

		super.setDescription("Locks down a specific channel, makes it so no one can send messages in the channel");
		super.setExamples("lockdown", "lockdown #channel");
		super.setCategoryAll(ModuleCategory.MODERATION);
		super.setAuthorDiscordPermissions(Permission.MANAGE_PERMISSIONS);
		super.setBotDiscordPermissions(Permission.MANAGE_PERMISSIONS);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="channel", endless=true, nullDefault=true) BaseGuildMessageChannel channel) {
		MessageChannel messageChannel = event.getChannel();
		if (channel == null && !(messageChannel instanceof IPermissionContainer)) {
			event.replyFailure("You cannot use this channel type").queue();
			return;
		}

		IPermissionContainer effectiveChannel = channel == null ? (IPermissionContainer) messageChannel : channel;

		Role role = event.getGuild().getPublicRole();
		PermissionOverride override = effectiveChannel.getPermissionOverride(role);

		if (override != null && override.getDenied().contains(Permission.MESSAGE_SEND)) {
			effectiveChannel.upsertPermissionOverride(role).clear(Permission.MESSAGE_SEND)
				.flatMap($ -> event.replySuccess(effectiveChannel.getAsMention() + " is no longer locked down"))
				.queue();
		} else {
			effectiveChannel.upsertPermissionOverride(role).deny(Permission.MESSAGE_SEND)
				.flatMap($ -> event.replySuccess(effectiveChannel.getAsMention() + " is now locked down"))
				.queue();
		}
	}

}
