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

public class StreakCommand extends Sx4Command {

	public StreakCommand() {
		super("streak", 422);

		super.setDescription("View the streak of a user");
		super.setExamples("streak", "streak @Shea#6653", "streak Shea");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.ECONOMY);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="user", endless=true, nullDefault=true) Member member) {
		boolean self = member == null;
		Member effectiveMember = self ? event.getMember() : member;
		User user = self ? event.getAuthor() : member.getUser();

		int streak = event.getMongo().getUserById(user.getIdLong(), Projections.include("economy.streak")).getEmbedded(List.of("economy", "streak"), 0);

		EmbedBuilder embed = new EmbedBuilder()
			.setAuthor(user.getName(), null, user.getEffectiveAvatarUrl())
			.setColor(effectiveMember.getColorRaw())
			.setDescription(String.format("%s streak: **%,d day%s**", self ? "Your" : "Their", streak, streak == 1 ? "" : "s"));

		event.reply(embed.build()).queue();
	}

}
