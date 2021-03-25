package com.sx4.bot.commands.mod;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.Region;

public class RegionCommand extends Sx4Command {

	public RegionCommand() {
		super("region", 321);

		super.setDescription("Changes the server region in the current server");
		super.setExamples("region Europe", "region London");
		super.setAuthorDiscordPermissions(Permission.MANAGE_SERVER);
		super.setBotDiscordPermissions(Permission.MANAGE_SERVER);
		super.setCategoryAll(ModuleCategory.MODERATION);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="region", endless=true) Region region) {
		if (region.isVip() && !event.getGuild().getFeatures().contains("VIP_REGIONS")) {
			event.replyFailure("You cannot set the region to a VIP region without the VIP server feature").queue();
			return;
		}

		if (region == event.getGuild().getRegion()) {
			event.replyFailure("The region is already set to that").queue();
			return;
		}

		event.getGuild().getManager().setRegion(region)
			.flatMap($ -> event.replySuccess("Succesfully changed the voice region to **" + region.getName() + " " + region.getEmoji() + "**"))
			.queue();
	}

}
