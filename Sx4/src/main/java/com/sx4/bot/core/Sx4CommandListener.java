package com.sx4.bot.core;

import com.jockie.bot.core.command.ICommand;
import com.jockie.bot.core.command.impl.CommandEvent;
import com.jockie.bot.core.command.impl.CommandListener;
import com.sx4.bot.exceptions.argument.Sx4ArgumentException;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.GenericMessageEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;

import java.util.function.BiPredicate;
import java.util.function.Function;

public class Sx4CommandListener extends CommandListener {

	private final Sx4 bot;

	public Sx4CommandListener(Sx4 bot) {
		this.bot = bot;
	}

	public Sx4CommandListener removePreExecuteCheck(Function<Sx4CommandListener, BiPredicate<CommandEvent, ICommand>> function) {
		super.removePreExecuteCheck(function.apply(this));

		return this;
	}

	public Sx4CommandListener addPreExecuteCheck(Function<Sx4CommandListener, BiPredicate<CommandEvent, ICommand>> function) {
		super.addPreExecuteCheck(function.apply(this));

		return this;
	}

	public void onEvent(GenericEvent event) {
		try {
			if (event instanceof MessageReceivedEvent) {
				this.handle(((MessageReceivedEvent) event).getMessage());
			} else if (event instanceof MessageUpdateEvent) {
				Message editedMessage = ((MessageUpdateEvent) event).getMessage();
				if (editedMessage.isPinned()) {
					return;
				}

				this.handle(editedMessage);
			}
		} catch (Sx4ArgumentException e) {
			((GenericMessageEvent) event).getChannel().sendMessage(e.getMessage() + " " + this.bot.getConfig().getFailureEmote()).queue();
		}
	}
	
}