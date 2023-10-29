package com.sx4.bot.commands.fun;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.annotations.argument.AdvancedMessage;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.utility.MessageUtility;
import net.dv8tion.jda.api.Permission;
import org.bson.Document;

public class AdvancedSayCommand extends Sx4Command {

	public AdvancedSayCommand() {
		super("advanced say", 275);

		super.disable();

		super.setDescription("Same as `say` but takes json to be more customisable");
		super.setAliases("advancedsay");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.FUN);
		super.setExamples(
			"advanced say {\"embed\": {\"title\": \"My title\", \"description\": \"My description\"}}",
			"advanced say {\"embed\": {\"title\": \"My title\", \"description\": \"My description\"}, \"content\": \"My content\"}"
		);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="json", endless=true) @AdvancedMessage Document json) {
		try {
			event.reply(MessageUtility.fromCreateJson(json, true).build()).queue();
		} catch (IllegalArgumentException e) {
			event.replyFailure(e.getMessage()).queue();
		}
	}

}
