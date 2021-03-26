package com.sx4.bot.commands.info;

import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;

public class DonateCommand extends Sx4Command {

	public DonateCommand() {
		super("donate", 326);

		super.setDescription("Get links to donate to Sx4");
		super.setExamples("donate");
		super.setPrivateTriggerable(true);
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event) {
		String paypalUrl = event.getConfig().getPayPalUrl(), patreonUrl = event.getConfig().getPatreonUrl();
		if (event.isFromGuild() && !event.getSelfMember().hasPermission(event.getTextChannel(), Permission.MESSAGE_EMBED_LINKS)) {
			event.reply("PayPal: <" + paypalUrl + ">\nPatreon: <" + patreonUrl + ">").queue();
			return;
		}

		EmbedBuilder embed = new EmbedBuilder()
			.setColor(event.getConfig().getColour())
			.setDescription(event.getConfig().getDonationDescription())
			.setAuthor("Donate", patreonUrl, event.getSelfUser().getEffectiveAvatarUrl())
			.addField("Patreon", "[Click Here](" + patreonUrl + ")", true)
			.addField("PayPal", "[Click Here](" + paypalUrl + ")", true);

		event.reply(embed.build()).queue();
	}

}
