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

public class HueCommand extends Sx4Command {

	public HueCommand() {
		super("hue");

		super.setDescription("Rotates the hue on an image for 60 frames");
		super.setExamples("hue", "hue @Shea#6653", "hue https://some.domain/image.png");
		super.setBotDiscordPermissions(Permission.MESSAGE_ATTACH_FILES);
		super.setCategory(ModuleCategory.IMAGE);
		super.setCooldownDuration(3);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="image url", endless=true, acceptEmpty=true) @ImageUrl String imageUrl) {
		Request request = new ImageRequest("hue")
			.addQuery("image", imageUrl)
			.build();

		event.getClient().newCall(request).enqueue((HttpCallback) response -> ImageUtility.sendImage(event, response).queue());
	}

}
