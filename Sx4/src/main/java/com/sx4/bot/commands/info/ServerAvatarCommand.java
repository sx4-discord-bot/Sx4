package com.sx4.bot.commands.info;

import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;

public class ServerAvatarCommand extends Sx4Command {

	public ServerAvatarCommand() {
		super("server avatar", 274);

		super.setDescription("View the avatar of the current server");
		super.setExamples("server avatar");
		super.setAliases("server icon", "servericon", "serverav", "sav", "server av");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event) {
		EmbedBuilder embed = new EmbedBuilder()
			.setImage(event.getGuild().getIconUrl())
			.setAuthor(event.getGuild().getName(), event.getGuild().getIconUrl(), event.getGuild().getIconUrl());

		event.reply(embed.build()).queue();
	}

}
