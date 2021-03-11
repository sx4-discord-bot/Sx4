package com.sx4.bot.commands.image;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.image.ImageRequest;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.utility.ImageUtility;
import net.dv8tion.jda.api.Permission;
import okhttp3.Request;

public class TrumpTweetCommand extends Sx4Command {

	public TrumpTweetCommand() {
		super("trump tweet", 25);

		super.setAliases("trump", "trumptweet");
		super.setDescription("Send a tweet as trump himself");
		super.setExamples("trump tweet I am Trump", "trump tweet I WON THE ELECTION");
		super.setBotDiscordPermissions(Permission.MESSAGE_ATTACH_FILES);
		super.setCooldownDuration(5);
		super.setCategoryAll(ModuleCategory.IMAGE);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="text", endless=true) @Limit(max=280) String text) {
		Request request = new ImageRequest("trump")
			.addQuery("text", ImageUtility.escapeMentions(event.getGuild(), text))
			.build(event.getConfig().getImageWebserver());

		event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> ImageUtility.getImageMessage(event, response).queue());
	}

}
