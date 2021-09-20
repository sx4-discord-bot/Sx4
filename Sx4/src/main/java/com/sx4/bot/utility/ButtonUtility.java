package com.sx4.bot.utility;

import com.sx4.bot.config.Config;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.interactions.components.Button;

public class ButtonUtility {

	public static void handleButtonFailure(ButtonClickEvent event, Message message) {
		if (event.isAcknowledged() || event.getMessageIdLong() != message.getIdLong()) {
			return;
		}

		event.reply("This is not your button to click " + Config.get().getFailureEmote()).setEphemeral(true).queue();
	}

	public static boolean handleButtonConfirmation(ButtonClickEvent event, Message message, User user, String buttonId) {
		Button button = event.getButton();
		return button != null && button.getId().equals(buttonId) && event.getMessageIdLong() == message.getIdLong() && event.getUser().getIdLong() == user.getIdLong();
	}

	public static boolean handleButtonConfirmation(ButtonClickEvent event, Message message, User user) {
		return ButtonUtility.handleButtonConfirmation(event, message, user, "yes");
	}

	public static boolean handleButtonCancellation(ButtonClickEvent event, Message message, User user, String buttonId) {
		Button button = event.getButton();
		return button != null && button.getId().equals(buttonId) && event.getMessageIdLong() == message.getIdLong() && event.getUser().getIdLong() == user.getIdLong();
	}

	public static boolean handleButtonCancellation(ButtonClickEvent event, Message message, User user) {
		return ButtonUtility.handleButtonConfirmation(event, message, user, "no");
	}

}
