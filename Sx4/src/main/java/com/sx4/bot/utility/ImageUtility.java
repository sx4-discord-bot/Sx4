package com.sx4.bot.utility;

import com.sx4.bot.core.Sx4;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.image.ImageError;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import okhttp3.Response;
import org.bson.Document;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;

public class ImageUtility {

	public static MessageAction sendImage(Sx4CommandEvent event, Response response) throws IOException {
		int status = response.code();
		if (status == 200) {
			return event.replyFile(response.body().bytes(), String.format("canny.%s", response.header("Content-Type").split("/")[1]));
		} else if (status == 400) {
			Document body = Document.parse(response.body().string());
			int code = body.getEmbedded(List.of("details", "code"), Integer.class);

			ImageError error = ImageError.fromCode(code);
			if (error != null && error.isUrlError()) {
				return event.replyFailure("That url could not be formed to a valid image");
			} else {
				return event.reply(ExceptionUtility.getSimpleErrorMessage(String.format("- Code: %d\n- %s", code, body.getString("message")), "diff"));
			}
		} else {
			return event.reply(ExceptionUtility.getSimpleErrorMessage(String.format("- Status: %d\n- %s", status, response.body().string()), "diff"));
		}
	}

	public static String escapeMentions(Guild guild, String text) {
		Matcher userMatcher = SearchUtility.USER_MENTION.matcher(text);
		while (userMatcher.find()) {
			User user = Sx4.get().getShardManager().getUserById(userMatcher.group(1));
			if (user != null) {
				Member member = guild.getMember(user);
				String name = member == null ? user.getName() : member.getEffectiveName();

				text = text.replace(userMatcher.group(0), "@" + name);
			}
		}

		Matcher channelMatcher = SearchUtility.CHANNEL_MENTION.matcher(text);
		while (channelMatcher.find()) {
			TextChannel channel = guild.getTextChannelById(channelMatcher.group(1));
			if (channel != null) {
				text = text.replace(channelMatcher.group(0), "#" + channel.getName());
			}
		}

		return text;
	}

}
