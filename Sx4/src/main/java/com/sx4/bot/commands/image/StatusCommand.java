package com.sx4.bot.commands.image;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.image.ImageRequest;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.utility.ImageUtility;
import net.dv8tion.jda.api.entities.Member;
import okhttp3.Request;

public class StatusCommand extends Sx4Command {

	private enum Status {
		ONLINE,
		DND,
		INVISIBLE,
		OFFLINE,
		STREAMING,
		IDLE
	}

	public StatusCommand() {
		super("status", 490);

		super.setDescription("View the profile picture of a user with a specified status");
		super.setExamples("status online @Shea#6653", "status streaming", "status idle Shea");
		super.setCategoryAll(ModuleCategory.IMAGE);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="status") Status status, @Argument(value="user", endless=true, nullDefault=true) Member member) {
		Request request = new ImageRequest(event.getConfig().getImageWebserverUrl("status"))
			.addQuery("image", member == null ? event.getMember().getEffectiveAvatarUrl() : member.getEffectiveAvatarUrl())
			.addQuery("status", status.toString().toLowerCase())
			.build(event.getConfig().getImageWebserver());

		event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> ImageUtility.sendImageMessage(event, response).queue());
	}

}
