package com.sx4.bot.commands.fun;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;

public class SayCommand extends Sx4Command {

	public SayCommand() {
		super("say", 276);

		super.setDescription("Get the bot to repeat what you say");
		super.setCooldownDuration(10);
		super.setAliases("echo");
		super.setExamples("say hello", "say I'm a bot");
		super.setCategoryAll(ModuleCategory.FUN);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="query", endless=true) String query) {
		event.reply(query).queue();
	}

}
