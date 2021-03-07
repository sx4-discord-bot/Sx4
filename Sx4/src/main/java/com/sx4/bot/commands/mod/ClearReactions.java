package com.sx4.bot.commands.mod;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.argument.MessageArgument;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;

public class ClearReactions extends Sx4Command {

	public ClearReactions() {
		super("clear reactions", 260);

		super.setDescription("Clears all the reactions off a specific message");
		super.setAliases("remove reactions", "removereactions", "clearreactions");
		super.setExamples("clear reactions 818123437238255660", "clear reactions https://canary.discord.com/channels/330399610273136641/344091594972069888/818123437238255660");
		super.setAuthorDiscordPermissions(Permission.MESSAGE_MANAGE);
		super.setBotDiscordPermissions(Permission.MESSAGE_MANAGE);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="message id") MessageArgument messageArgument) {
		messageArgument.getChannel().clearReactionsById(messageArgument.getMessageId())
			.flatMap($ -> event.replySuccess("All reactions have been cleared from that message"))
			.onErrorFlatMap(e -> {
				if (e instanceof ErrorResponseException && ((ErrorResponseException) e).getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE) {
					return event.reply("I could not find that message :no_entry:");
				}

				return null;
			})
			.queue();
	}

}
