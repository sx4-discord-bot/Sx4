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

public class FearCommand extends Sx4Command {

	public FearCommand() {
		super("fear", 11);

		super.setDescription("Creates an image of someone fearing a specified image");
		super.setExamples("fear", "fear @Shea#6653", "fear https://example.com/image.png");
		super.setBotDiscordPermissions(Permission.MESSAGE_ATTACH_FILES);
		super.setCategoryAll(ModuleCategory.IMAGE);
		super.setCooldownDuration(3);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="image url", endless=true, acceptEmpty=true) @ImageUrl String imageUrl) {
		Request request = new ImageRequest("fear")
			.addQuery("image", imageUrl)
			.build();

		event.getClient().newCall(request).enqueue((HttpCallback) response -> ImageUtility.getImageMessage(event, response).queue());
	}

}