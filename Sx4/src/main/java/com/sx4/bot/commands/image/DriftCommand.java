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
import net.dv8tion.jda.api.entities.User;

public class DriftCommand extends Sx4Command {

	public DriftCommand() {
		super("drift");

		super.setDescription("Sends an image of your drifting towards a junction away from a destination");
		super.setExamples("drift Shea healthy", "drift Dyno Sx4", "drift \"Something you hate\"");
		super.setCategory(ModuleCategory.IMAGE);
		super.setBotDiscordPermissions(Permission.MESSAGE_ATTACH_FILES);
		super.setCooldownDuration(3);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="user", nullDefault=true) User user, @Argument(value="left text") @Limit(max=30) String leftText, @Argument(value="right text", endless=true, nullDefault=true) @Limit(max=40) String rightText) {
		ImageRequest request = new ImageRequest("drift")
			.addQuery("left_text", leftText)
			.addQuery("image", (user == null ? event.getAuthor() : user).getEffectiveAvatarUrl());

		if (rightText != null) {
			request.addQuery("right_text", rightText);
		}

		event.getClient().newCall(request.build()).enqueue((HttpCallback) response -> ImageUtility.getImageMessage(event, response).queue());
	}

}
