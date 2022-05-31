package com.sx4.bot.commands.info;

import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;

public class InviteCommand extends Sx4Command {

	public InviteCommand() {
		super("invite", 325);

		super.setDescription("Get the invite url for Sx4");
		super.setExamples("invite");
		super.setPrivateTriggerable(true);
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event) {
		String url = event.getConfig().getInviteUrl(event.getSelfUser().getId());
		if (event.isFromGuild() && !event.getSelfMember().hasPermission(event.getGuildChannel(), Permission.MESSAGE_EMBED_LINKS)) {
			event.reply("<" + url + ">").queue();
			return;
		}

		EmbedBuilder embed = new EmbedBuilder()
			.setColor(event.getConfig().getColour())
			.setDescription(event.getConfig().getInviteDescription())
			.setAuthor("Invite", url, event.getSelfUser().getEffectiveAvatarUrl())
			.addField("Invite", "[Click Here](" + url + ")", true);

		event.reply(embed.build()).queue();
	}

}
