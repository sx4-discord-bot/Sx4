package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.option.Option;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.utility.ImageUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

public class AvatarCommand extends Sx4Command {

	public AvatarCommand() {
		super("avatar", 273);

		super.setDescription("View the avatar of a user");
		super.setAliases("av");
		super.setExamples("avatar", "avatar @Shea#6653", "avatar Shea --global");
		super.setCategoryAll(ModuleCategory.INFORMATION);
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="user", endless=true, nullDefault=true) Member member, @Option(value="global", description="Forced to show their global avatar") boolean global) {
		Member effectiveMember = member == null ? event.getMember() : member;
		User user = effectiveMember.getUser();

		ImageUtility.sendImageEmbed(event, global ? user.getEffectiveAvatarUrl() : effectiveMember.getEffectiveAvatarUrl(), user.getAsTag());
	}

}
