package com.sx4.bot.commands.image;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.annotations.argument.Colour;
import com.sx4.bot.annotations.argument.ImageUrl;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.image.ImageRequest;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.utility.ImageUtility;
import net.dv8tion.jda.api.Permission;
import okhttp3.Request;

public class ChangeColourCommand extends Sx4Command {

	public ChangeColourCommand() {
		super("change colour", 469);

		super.setDescription("Change the colour of an image");
		super.setAliases("changecolour", "manipulate colour", "manipulatecolour");
		super.setExamples("change colour https://example.com/image.png #ffff00", "change colour @Shea#6653 255, 0, 0");
		super.setCategoryAll(ModuleCategory.IMAGE);
		super.setBotDiscordPermissions(Permission.MESSAGE_ATTACH_FILES);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="image", acceptEmpty=true) @ImageUrl String imageUrl, @Argument(value="colour", endless=true) @Colour int colour) {
		Request request = new ImageRequest(event.getConfig().getImageWebserverUrl("manipulate-colour"))
			.addQuery("image", imageUrl)
			.addQuery("colour", colour)
			.build(event.getConfig().getImageWebserver());

		event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> ImageUtility.getImageMessage(event, response).queue());
	}

}
