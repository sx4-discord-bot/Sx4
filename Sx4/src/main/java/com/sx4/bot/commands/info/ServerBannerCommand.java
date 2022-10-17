package com.sx4.bot.commands.info;

import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.utility.ImageUtility;
import net.dv8tion.jda.api.Permission;

public class ServerBannerCommand extends Sx4Command {

	public ServerBannerCommand() {
		super("server banner", 510);

		super.setDescription("View the banner of the current server");
		super.setExamples("server banner");
		super.setAliases("serverbanner");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event) {
		String banner = event.getGuild().getBannerUrl();
		if (banner == null) {
			event.replyFailure("This server does not have a banner").queue();
			return;
		}

		ImageUtility.sendImageEmbed(event, banner, event.getGuild().getName());
	}

}
