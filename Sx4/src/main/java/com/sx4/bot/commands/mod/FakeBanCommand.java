package com.sx4.bot.commands.mod;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;

public class FakeBanCommand extends Sx4Command {

	public FakeBanCommand() {
		super("Ban", 433);

		super.setDescription("Ban someone without actually banning them");
		super.setCaseSensitive(true);
		super.setExamples("Ban @Shea#6653");
		super.setAuthorDiscordPermissions(Permission.BAN_MEMBERS);
		super.setCategoryAll(ModuleCategory.MODERATION);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="user", endless=true) Member member) {
		event.replySuccess("**" + member.getUser().getAsTag() + "** has been banned").queue();
	}

}
