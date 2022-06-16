package com.sx4.bot.commands.fun;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.option.Option;
import com.sx4.bot.annotations.argument.DefaultString;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.utility.StringUtility;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;

public class SpoilerCommand extends Sx4Command {

	public SpoilerCommand() {
		super("spoiler", 291);

		super.setDescription("Puts a spoiler on every character of the text given");
		super.setCooldownDuration(10);
		super.setAliases("spoilerfy");
		super.setExamples("spoiler hello", "spoiler click each character");
		super.setCategoryAll(ModuleCategory.FUN);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="text", endless=true) String text, @Option(value="split", description="What to split the text by") @DefaultString("") String split) {
		event.reply(StringUtility.limit("||" + String.join("||" + split + "||", text.split(MarkdownSanitizer.escape(split))) + "||", Message.MAX_CONTENT_LENGTH)).queue();
	}

}
