package com.sx4.bot.commands.image;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.annotations.argument.ImageUrl;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.image.ImageError;
import com.sx4.bot.entities.image.ImageRequest;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.utility.ImageUtility;
import net.dv8tion.jda.api.Permission;
import okhttp3.Request;

public class FlagCommand extends Sx4Command {

	public FlagCommand() {
		super("flag", 12);

		super.setDescription("Puts a flag over a specified image");
		super.setExamples("flag fr", "flag gb @Shea#6653", "hot se https://example.com/image.png");
		super.setBotDiscordPermissions(Permission.MESSAGE_ATTACH_FILES);
		super.setCategory(ModuleCategory.IMAGE);
		super.setCooldownDuration(3);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="flag code") String flagCode, @Argument(value="image url", endless=true, acceptEmpty=true) @ImageUrl String imageUrl) {
		Request request = new ImageRequest("flag")
			.addQuery("image", imageUrl)
			.addQuery("flag", flagCode)
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