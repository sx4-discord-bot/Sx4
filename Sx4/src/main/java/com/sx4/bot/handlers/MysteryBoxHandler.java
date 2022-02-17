package com.sx4.bot.handlers;

import com.sx4.bot.core.Sx4;
import com.sx4.bot.entities.games.MysteryBoxGame;
import com.sx4.bot.entities.games.MysteryBoxResult;
import com.sx4.bot.managers.MysteryBoxManager;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.interactions.components.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public class MysteryBoxHandler implements EventListener {

	private final Sx4 bot;

	public MysteryBoxHandler(Sx4 bot) {
		this.bot = bot;
	}

	private List<ActionRow> editRows(List<ActionRow> rows, List<Button> buttons) {
		for (ActionRow row : rows) {
			List<Component> components = row.getComponents();
			for (ListIterator<Component> it = components.listIterator(); it.hasNext();) {
				Component component = it.next();
				if (!(component instanceof Button button)) {
					continue;
				}

				for (Button newButton : buttons) {
					if (newButton.getId().equals(button.getId())) {
						it.set(newButton);
					}
				}
			}
		}

		return rows;
	}

	public void handle(ButtonClickEvent event) {
		if (event.isAcknowledged()) {
			return;
		}

		Button button = event.getButton();
		if (button == null || button.isDisabled()) {
			return;
		}

		MysteryBoxManager manager = this.bot.getMysteryBoxManager();

		MysteryBoxGame game = manager.getGame(event.getUser());
		if (game == null || game.getMessageId() != event.getMessageIdLong()) {
			if (manager.hasGame(event.getMessageIdLong())) {
				event.reply("This is not your mystery box game " + this.bot.getConfig().getFailureEmote()).setEphemeral(true).queue();
				return;
			}

			return;
		}

		int clicks = game.incrementClicks();

		String id = event.getComponentId();
		if (id.equals("24")) {
			manager.removeGame(event.getMessageIdLong());

			game.end(new MysteryBoxResult(game, event));

			return;
		}

		if (game.getBox(id)) {
			manager.removeGame(event.getMessageIdLong());

			game.setWinnings(0L);
			game.end(new MysteryBoxResult(game, event));

			return;
		}

		long winnings = game.increaseWinnings();

		if (clicks == MysteryBoxManager.MONEY_COUNT) {
			manager.removeGame(event.getMessageIdLong());

			game.end(new MysteryBoxResult(game, event));

			return;
		}

		Button newButton = Button.success(id, Emoji.fromUnicode("\uD83D\uDCB0")).asDisabled();

		List<Button> edit = clicks == 1 ? List.of(Button.primary("24", "Quit").asEnabled(), newButton) : List.of(newButton);

		String description = String.format("Your current winnings: **$%,d**. You will get $%,d for your next click if successful", winnings + game.getBet(), game.getWinningsIncrease());

		event.editComponents(this.editRows(new ArrayList<>(event.getMessage().getActionRows()), edit)).setContent(description).queue();
	}

	@Override
	public void onEvent(@NotNull GenericEvent event) {
		if (event instanceof ButtonClickEvent) {
			this.handle((ButtonClickEvent) event);
		}
	}

}
