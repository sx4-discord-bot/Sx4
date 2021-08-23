package com.sx4.bot.commands.fun;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.annotations.argument.Replace;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import net.dv8tion.jda.api.entities.Message;

public class AscendCommand extends Sx4Command {

	public AscendCommand() {
		super("ascend", 292);

		super.setDescription("Puts a space in between each character of the text given");
		super.setCooldownDuration(10);
		super.setExamples("ascend hello", "ascend my text is ascended");
		super.setCategoryAll(ModuleCategory.FUN);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="text", endless=true) @Replace(replace="", with=" ") @Limit(max=Message.MAX_CONTENT_LENGTH, error=false) String text) {
		event.reply(text).queue();
	}

}
