package com.sx4.bot.commands.mod;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.option.Option;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.utility.ModUtility;
import com.sx4.bot.utility.TimeUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;

import java.time.Duration;

public class MuteCommand extends Sx4Command {

	public MuteCommand() {
		super("mute", 139);
		
		super.setExamples("mute @Shea#6653 20m", "mute Shea 30m Spamming", "mute 402557516728369153 12h template:offensive & Spamming");
		super.setDescription("Mute a user server wide");
		super.setAuthorDiscordPermissions(Permission.MESSAGE_MANAGE);
		super.setBotDiscordPermissions(Permission.MANAGE_ROLES);
		super.setCategoryAll(ModuleCategory.MODERATION);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="user") Member member, @Argument(value="time", nullDefault=true) Duration time, @Argument(value="reason", endless=true, nullDefault=true) Reason reason, @Option(value="extend", description="Will extend the mute of the user if muted") boolean extend) {
		if (!event.getMember().canInteract(member)) {
			event.replyFailure("You cannot mute someone higher or equal than your top role").queue();
			return;
		}

		if (!event.getSelfMember().canInteract(member)) {
			event.replyFailure("I cannot mute someone higher or equal than your top role").queue();
			return;
		}

		ModUtility.mute(member, event.getMember(), time, extend, reason).whenComplete((action, exception) -> {
			if (exception != null) {
				event.replyFailure(exception.getMessage()).queue();
				return;
			}

			event.replyFormat("**%s** has %s for %s %s", member.getUser().getAsTag(), action.getModAction().isExtend() ? "had their mute extended" : "been muted", TimeUtility.getTimeString(action.getDuration()), this.config.getSuccessEmote()).queue();
		});
	}

}
