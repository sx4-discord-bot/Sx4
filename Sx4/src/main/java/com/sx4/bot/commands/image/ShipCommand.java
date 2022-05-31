package com.sx4.bot.commands.image;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.image.ImageRequest;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.utility.ImageUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import okhttp3.Request;

import java.util.Random;

public class ShipCommand extends Sx4Command {

	private final Random random = new Random();

	public ShipCommand() {
		super("ship", 22);

		super.setDescription("Ships two users to find out their ship name and percentage");
		super.setExamples("ship @Shea#6653", "ship Shea#6653 203421890637856768");
		super.setCategoryAll(ModuleCategory.IMAGE);
		super.setCooldownDuration(3);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="first user") Member firstMember, @Argument(value="second user", endless=true, nullDefault=true) Member secondMember) {
		User firstUser = firstMember.getUser();
		User secondUser = secondMember == null ? event.getAuthor() : secondMember.getUser();

		this.random.setSeed(firstUser.getIdLong() + secondUser.getIdLong());
		int percent = this.random.nextInt(100) + 1;

		String firstName = firstUser.getName(), secondName = secondUser.getName();
		String shipName = firstName.substring(0, (int) Math.ceil((double) firstName.length() / 2)) + secondName.substring((int) Math.ceil((double) secondName.length() / 2));

		String message = String.format("Ship Name: **%s**\nLove Percentage: **%d%%**", shipName, percent);

		Request request = new ImageRequest(event.getConfig().getImageWebserverUrl("ship"))
			.addQuery("first_image", firstUser.getEffectiveAvatarUrl())
			.addQuery("second_image", secondUser.getEffectiveAvatarUrl())
			.addQuery("percent", percent)
			.build(event.getConfig().getImageWebserver());

		if (event.getSelfMember().hasPermission(event.getGuildChannel(), Permission.MESSAGE_ATTACH_FILES)) {
			event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
				MessageAction action = ImageUtility.getImageMessage(event, response);
				if (response.isSuccessful()) {
					action.content(message);
				}

				action.queue();
			});
		} else {
			event.reply(message).queue();
		}
	}

}
