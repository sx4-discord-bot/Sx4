package com.sx4.bot.commands.image;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.annotations.argument.ImageUrl;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.image.ImageRequest;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.utility.ImageUtility;
import net.dv8tion.jda.api.Permission;
import okhttp3.Request;

public class InvertCommand extends Sx4Command {

	public InvertCommand() {
		super("invert", 18);

		super.setDescription("Inverts the RGB of every pixel in an image");
		super.setExamples("invert", "invert @Shea#6653", "invert https://example.com/image.png");
		super.setBotDiscordPermissions(Permission.MESSAGE_ATTACH_FILES);
		super.setCooldownDuration(3);
		super.setCategoryAll(ModuleCategory.IMAGE);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="image url", endless=true, acceptEmpty=true) @ImageUrl String imageUrl) {
		Request request = new ImageRequest("invert")
			.addQuery("image", imageUrl)
			.build();

		event.getClient().newCall(request).enqueue((HttpCallback) response -> ImageUtility.getImageMessage(event, response).queue());
	}

}
