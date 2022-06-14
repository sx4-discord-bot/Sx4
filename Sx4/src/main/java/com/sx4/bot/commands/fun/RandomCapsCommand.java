package com.sx4.bot.commands.fun;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import net.dv8tion.jda.api.entities.Message;

public class RandomCapsCommand extends Sx4Command {

	public RandomCapsCommand() {
		super("random caps", 280);

		super.setDescription("Repeats your text with each character having random capitalization");
		super.setAliases("randomcaps");
		super.setExamples("random caps hello", "random caps this sentence is in random caps");
		super.setCategoryAll(ModuleCategory.FUN);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="text", endless=true) @Limit(max=Message.MAX_CONTENT_LENGTH) String text) {
		char[] characters = text.toCharArray();
		for (int i = 0; i < characters.length; i++) {
			characters[i] = event.getRandom().nextBoolean() ? Character.toUpperCase(characters[i]) : Character.toLowerCase(characters[i]);
		}

		event.reply(new String(characters)).queue();
	}

}
