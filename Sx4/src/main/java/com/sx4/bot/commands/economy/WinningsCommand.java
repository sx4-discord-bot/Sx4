package com.sx4.bot.commands.economy;

import com.jockie.bot.core.argument.Argument;
import com.mongodb.client.model.Projections;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

import java.util.List;

public class WinningsCommand extends Sx4Command {

	public WinningsCommand() {
		super("winnings", 425);

		super.setDescription("View a users winnings");
		super.setExamples("winnings", "winnings @Shea#6653", "winnings Shea");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.ECONOMY);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="user", endless=true, nullDefault=true) Member member) {
		boolean self = member == null;
		Member effectiveMember = self ? event.getMember() : member;
		User user = effectiveMember.getUser();

		long winnings = event.getMongo().getUserById(user.getIdLong(), Projections.include("economy.winnings")).getEmbedded(List.of("economy", "winnings"), 0L);

		EmbedBuilder embed = new EmbedBuilder()
			.setAuthor(user.getName(), null, user.getEffectiveAvatarUrl())
			.setColor(effectiveMember.getColorRaw())
			.setDescription((self ? "Your" : "Their") + String.format(" winnings: **$%,d**", winnings));

		event.reply(embed.build()).queue();
	}

}
