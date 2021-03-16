package com.sx4.bot.commands.fun;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;

public class AlternateCapsCommand extends Sx4Command {

	public AlternateCapsCommand() {
		super("alternate caps", 281);

		super.setDescription("Repeats your text with each character having alternating capitalization");
		super.setAliases("alternatecaps", "alternative caps", "alternativecaps");
		super.setExamples("alternate caps hello", "alternate caps this sentence is in alternating caps");
		super.setCategoryAll(ModuleCategory.FUN);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="text", endless=true) String text) {
		char[] charArray = text.toCharArray();

		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < charArray.length; i++) {
			builder.append(i % 2 == 0 ? Character.toUpperCase(charArray[i]) : Character.toLowerCase(charArray[i]));
		}

		event.reply(builder.toString()).queue();
	}

}
