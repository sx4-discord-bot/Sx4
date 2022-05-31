package com.sx4.bot.handlers;

import com.sx4.bot.core.Sx4;
import com.sx4.bot.entities.games.GuessTheNumberGame;
import com.sx4.bot.entities.interaction.CustomModalId;
import com.sx4.bot.entities.interaction.ModalType;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import org.jetbrains.annotations.NotNull;

public class ModalHandler implements EventListener {

	private final Sx4 bot;

	public ModalHandler(Sx4 bot) {
		this.bot = bot;
	}

	public void handleGuessTheNumber(ModalInteractionEvent event, CustomModalId customId) {
		long messageId = customId.getArgumentLong(0);

		GuessTheNumberGame game = this.bot.getGuessTheNumberManager().getGame(messageId);
		if (game == null) {
			event.reply("This game has expired :stopwatch:").setEphemeral(true).queue();
			return;
		}

		int number;
		try {
			number = Integer.parseInt(event.getValue("number").getAsString());
		} catch (NumberFormatException e) {
			event.reply("You did not provide a valid number " + this.bot.getConfig().getFailureEmote()).setEphemeral(true).queue();
			return;
		}

		if (number < game.getMin()) {
			event.reply("You cannot provide a number less than **" + game.getMin() + "** " + this.bot.getConfig().getSuccessEmote()).setEphemeral(true).queue();
			return;
		}

		if (number > game.getMax()) {
			event.reply("You cannot provide a number more than **" + game.getMax() + "** " + this.bot.getConfig().getSuccessEmote()).setEphemeral(true).queue();
			return;
		}

		event.reply("Your guess of **" + number + "** was submitted " + this.bot.getConfig().getSuccessEmote()).setEphemeral(true).queue();

		game.setGuess(event.getHook(), event.getUser().getIdLong(), number);
	}

	@Override
	public void onEvent(@NotNull GenericEvent genericEvent) {
		if (!(genericEvent instanceof ModalInteractionEvent event)) {
			return;
		}

		CustomModalId customId = CustomModalId.fromId(event.getModalId());
		ModalType type = ModalType.fromId(customId.getType());
		switch (type) {
			case GUESS_THE_NUMBER -> this.handleGuessTheNumber(event, customId);
		}
	}

}
