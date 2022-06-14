package com.sx4.bot.commands.fun;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import net.dv8tion.jda.api.entities.Message;

public class AlternateCapsCommand extends Sx4Command {

	public AlternateCapsCommand() {
		super("alternate caps", 281);

		super.setDescription("Repeats your text with each character having alternating capitalization");
		super.setAliases("alternatecaps", "alternative caps", "alternativecaps");
		super.setExamples("alternate caps hello", "alternate caps this sentence is in alternating caps");
		super.setCategoryAll(ModuleCategory.FUN);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="text", endless=true) @Limit(max=Message.MAX_CONTENT_LENGTH) String text) {
		char[] characters = text.toCharArray();
		for (int i = 0; i < characters.length; i++) {
			characters[i] = (i & 1) == 0 ? Character.toUpperCase(characters[i]) : Character.toLowerCase(characters[i]);
		}

		event.reply(new String(characters)).queue();
	}

}
