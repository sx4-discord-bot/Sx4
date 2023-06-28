package com.sx4.bot.utility;

import com.sx4.bot.config.Config;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.*;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.interactions.MessageEditCallbackAction;

import java.util.List;
import java.util.ListIterator;
import java.util.stream.Collectors;

public class ButtonUtility {

	public static List<ActionRow> disableButtons(List<ActionRow> rows, String... ids) {
		for (ActionRow row : rows) {
			List<ItemComponent> components = row.getComponents();
			for (ListIterator<ItemComponent> it = components.listIterator(); it.hasNext();) {
				Component component = it.next();
				if (!(component instanceof Button button)) {
					continue;
				}

				for (String id : ids) {
					if (id.equals(button.getId())) {
						it.set(button.asDisabled());
					}
				}
			}
		}

		return rows;
	}

	public static MessageEditCallbackAction disableButtons(ButtonInteractionEvent event) {
		return event.editComponents(event.getMessage().getActionRows().stream().map(ActionRow::asDisabled).collect(Collectors.toList()));
	}

	public static void handleButtonFailure(ButtonInteractionEvent event, Message message) {
		if (event.isAcknowledged() || event.getMessageIdLong() != message.getIdLong()) {
			return;
		}

		event.reply("This is not your button to click " + Config.get().getFailureEmote()).setEphemeral(true).queue();
	}

	public static boolean handleButtonConfirmation(ButtonInteractionEvent event, Message message, User user, String buttonId) {
		Button button = event.getButton();
		return button != null && button.getId().equals(buttonId) && event.getMessageIdLong() == message.getIdLong() && event.getUser().getIdLong() == user.getIdLong();
	}

	public static boolean handleButtonConfirmation(ButtonInteractionEvent event, Message message, User user) {
		return ButtonUtility.handleButtonConfirmation(event, message, user, "yes");
	}

	public static boolean handleButtonCancellation(ButtonInteractionEvent event, Message message, User user, String buttonId) {
		Button button = event.getButton();
		return button != null && button.getId().equals(buttonId) && event.getMessageIdLong() == message.getIdLong() && event.getUser().getIdLong() == user.getIdLong();
	}

	public static boolean handleButtonCancellation(ButtonInteractionEvent event, Message message, User user) {
		return ButtonUtility.handleButtonConfirmation(event, message, user, "no");
	}

}
