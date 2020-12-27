package com.sx4.bot.commands.image;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.annotations.argument.ImageUrl;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.image.ImageRequest;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.utility.ImageUtility;
import okhttp3.Request;

public class WhoWouldWinCommand extends Sx4Command {

	public WhoWouldWinCommand() {
		super("who would win", 28);

		super.setAliases("whowouldwin", "www");
		super.setDescription("Who would win out of 2 images");
		super.setExamples("who would win Shea#6653", "who would win Sx4 Dyno", "who would win https://example.com/image.png Shea#6653");
		super.setCategory(ModuleCategory.IMAGE);
		super.setCooldownDuration(5);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="first image url") @ImageUrl String firstImageUrl, @Argument(value="second image url", endless=true, acceptEmpty=true) @ImageUrl String secondImageUrl) {
		Request request = new ImageRequest("www")
			.addQuery("first_image", firstImageUrl)
			.addQuery("second_image", secondImageUrl)
			.build();

		event.getClient().newCall(request).enqueue((HttpCallback) response -> ImageUtility.getImageMessage(event, response).queue());
	}

}
