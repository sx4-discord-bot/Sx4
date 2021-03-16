package com.sx4.bot.commands.fun;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;

public class RandomCapsCommand extends Sx4Command {

	public RandomCapsCommand() {
		super("random caps", 280);

		super.setDescription("Repeats your text with each character having random capitalization");
		super.setAliases("randomcaps");
		super.setExamples("random caps hello", "random caps this sentence is in random caps");
		super.setCategoryAll(ModuleCategory.FUN);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="text", endless=true) String text) {
		StringBuilder builder = new StringBuilder();
		for (char character : text.toCharArray()) {
			builder.append(event.getRandom().nextBoolean() ? Character.toUpperCase(character) : Character.toLowerCase(character));
		}

		event.reply(builder.toString()).queue();
	}

}
