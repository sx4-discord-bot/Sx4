package com.sx4.bot.commands.info;

import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;

public class SupportServerCommand extends Sx4Command {

	public SupportServerCommand() {
		super("support server", 327);

		super.setDescription("Get the link for the Sx4 support server");
		super.setAliases("support", "supportserver");
		super.setExamples("support");
		super.setPrivateTriggerable(true);
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event) {
		String inviteUrl = event.getConfig().getSupportGuildInvite();
		if (event.isFromGuild() && !event.getSelfMember().hasPermission(event.getTextChannel(), Permission.MESSAGE_EMBED_LINKS)) {
			event.reply(inviteUrl).queue();
			return;
		}

		EmbedBuilder embed = new EmbedBuilder()
			.setColor(event.getConfig().getColour())
			.setDescription(event.getConfig().getSupportDescription())
			.setAuthor("Support Server", inviteUrl, event.getSelfUser().getEffectiveAvatarUrl())
			.addField("Support Server", "[Click Here](" + inviteUrl + ")", true);

		event.reply(embed.build()).queue();
	}

}
