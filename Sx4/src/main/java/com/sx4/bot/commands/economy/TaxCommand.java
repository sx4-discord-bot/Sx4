package com.sx4.bot.commands.economy;

import com.mongodb.client.model.Projections;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;

import java.util.List;

public class TaxCommand extends Sx4Command {

	public TaxCommand() {
		super("tax", 385);

		super.setDescription("View the tax across the economy which is given away in the [Support Server](https://discord.gg/PqJNcfB) every week");
		super.setExamples("tax");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.ECONOMY);
	}

	public void onCommand(Sx4CommandEvent event) {
		long balance = event.getMongo().getUserById(event.getSelfUser().getIdLong(), Projections.include("economy.balance")).getEmbedded(List.of("economy", "balance"), 0L);

		EmbedBuilder embed = new EmbedBuilder()
			.setAuthor(event.getSelfUser().getName(), null, event.getSelfUser().getEffectiveAvatarUrl())
			.setColor(event.getSelfMember().getColorRaw())
			.setDescription(String.format("Their balance: **$%,d**", balance));

		event.reply(embed.build()).queue();
	}

}
