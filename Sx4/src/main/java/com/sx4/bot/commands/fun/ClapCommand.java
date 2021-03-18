package com.sx4.bot.commands.fun;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.annotations.argument.Replace;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import net.dv8tion.jda.api.entities.Message;

public class ClapCommand extends Sx4Command {

	public ClapCommand() {
		super("clap", 294);

		super.setDescription("Puts a clap emoji in between each whitespace");
		super.setAliases("clapify");
		super.setExamples("clap two words", "clap I don't know");
		super.setCategoryAll(ModuleCategory.FUN);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="text", endless=true) @Replace(replace=" ", with=":clap:") @Limit(max=Message.MAX_CONTENT_LENGTH, error=false) String text) {
		event.reply(text).queue();
	}

}
