package com.sx4.bot.commands.image;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.annotations.argument.DefaultNumber;
import com.sx4.bot.annotations.argument.ImageUrl;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.image.ImageError;
import com.sx4.bot.entities.image.ImageRequest;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.utility.ImageUtility;
import net.dv8tion.jda.api.Permission;
import okhttp3.Request;

public class ResizeCommand extends Sx4Command {

	public ResizeCommand() {
		super("resize", 20);

		super.setDescription("Resizes an image by percentage if a decimal and pixels if a whole number");
		super.setExamples("resize Shea 0.1 100", "resize Shea#6653 0.5 0.5", "resize https://example.com/image.png 500 500");
		super.setCategoryAll(ModuleCategory.IMAGE);
		super.setBotDiscordPermissions(Permission.MESSAGE_ATTACH_FILES);
		super.setCooldownDuration(5);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="image url") @ImageUrl String imageUrl, @Argument(value="width") @Limit(min=0) double width, @Argument(value="height") @Limit(min=0) @DefaultNumber(1) double height) {
		Request request = new ImageRequest("resize")
			.addQuery("w", width)
			.addQuery("h", height)
			.addQuery("image", imageUrl)
			.build();

		event.getClient().newCall(request).enqueue((HttpCallback) response -> {
			ImageUtility.getImageMessage(event, response, (body, error) -> {
				if (error == ImageError.INVALID_QUERY_VALUE) {
					return event.replyFailure(body.getString("message"));
				}

				return null;
			}).queue();
		});
	}

}
