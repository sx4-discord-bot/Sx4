package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

public class AvatarCommand extends Sx4Command {

	public AvatarCommand() {
		super("avatar", 273);

		super.setDescription("View the avatar of a user");
		super.setAliases("av");
		super.setExamples("avatar", "avatar @Shea#6653", "avatar Shea");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="user", endless=true, nullDefault=true) Member member) {
		User user = member == null ? event.getAuthor() : member.getUser();

		EmbedBuilder embed = new EmbedBuilder()
			.setImage(user.getEffectiveAvatarUrl())
			.setColor(member == null ? event.getMember().getColorRaw() : member.getColorRaw())
			.setAuthor(user.getAsTag(), user.getEffectiveAvatarUrl(), user.getEffectiveAvatarUrl());

		event.reply(embed.build()).queue();
	}

}
