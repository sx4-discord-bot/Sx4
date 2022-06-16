package com.sx4.bot.commands.fun;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;

public class BackwardsCommand extends Sx4Command {

	public BackwardsCommand() {
		super("backwards", 293);

		super.disable();

		super.setDescription("Reverses the text given");
		super.setCooldownDuration(10);
		super.setAliases("reverse");
		super.setExamples("backwards hello", "backwards racecar");
		super.setCategoryAll(ModuleCategory.FUN);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="text", endless=true) String text) {
		char[] charArray = text.toCharArray();

		StringBuilder builder = new StringBuilder();
		for (int i = charArray.length - 1;  i >= 0; i--) {
			builder.append(charArray[i]);
		}

		event.reply(builder.toString()).queue();
	}

}
