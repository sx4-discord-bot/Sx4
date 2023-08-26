package com.sx4.bot.handlers;

import com.jockie.bot.core.command.ICommand;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.entities.interaction.CustomSelectMenuId;
import com.sx4.bot.entities.interaction.SelectMenuType;
import com.sx4.bot.utility.HelpUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import org.jetbrains.annotations.NotNull;

public class SelectMenuHandler implements EventListener {

	private final Sx4 bot;

	public SelectMenuHandler(Sx4 bot) {
		this.bot = bot;
	}

	public void handleSubCommandSelect(StringSelectInteractionEvent event, CustomSelectMenuId customId) {
		int commandId = Integer.parseInt(event.getValues().get(0));
		ICommand command = this.bot.getCommandListener().getAllCommands().stream()
			.map(Sx4Command.class::cast)
			.filter(c -> c.getId() == commandId)
			.findFirst()
			.orElse(null);

		if (command == null) {
			return;
		}

		event.reply(HelpUtility.getHelpMessage(command, event.getUser(), !event.isFromGuild() || event.getGuild().getSelfMember().hasPermission(event.getGuildChannel(), Permission.MESSAGE_EMBED_LINKS))).queue();
	}

	@Override
	public void onEvent(@NotNull GenericEvent genericEvent) {
		if (!(genericEvent instanceof StringSelectInteractionEvent event)) {
			return;
		}

		CustomSelectMenuId customId = CustomSelectMenuId.fromId(event.getComponentId());
		if (!customId.isOwner(event.getUser().getIdLong())) {
			event.reply("You cannot use this select menu " + this.bot.getConfig().getFailureEmote()).queue();
			return;
		}

		SelectMenuType type = SelectMenuType.fromId(customId.getType());
		switch (type) {
			case SUB_COMMAND_SELECT -> this.handleSubCommandSelect(event, customId);
		}
	}

}
