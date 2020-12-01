package com.sx4.bot.utility;

import com.jockie.bot.core.command.impl.CommandEvent;
import com.sx4.bot.config.Config;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.entities.image.ImageError;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import okhttp3.Response;
import org.bson.Document;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;

public class ImageUtility {

	public static MessageAction getImageMessage(CommandEvent event, Response response) throws IOException {
		return ImageUtility.getImageMessage(event.getTextChannel(), response);
	}

	public static MessageAction getImageMessage(TextChannel channel, Response response) throws IOException {
		int status = response.code();
		if (status == 200) {
			byte[] bytes = response.body().bytes();
			if (bytes.length > Message.MAX_FILE_SIZE) {
				return channel.sendMessageFormat("File size cannot exceed %s (**%s**) %s", NumberUtility.getBytesReadable(Message.MAX_FILE_SIZE), NumberUtility.getBytesReadable(bytes.length), Config.get().getFailureEmote());
			}

			return channel.sendFile(bytes, String.format("image.%s", response.header("Content-Type").split("/")[1]));
		} else {
			return ImageUtility.getErrorMessage(channel, status, response.body().string());
		}
	}

	public static MessageAction getErrorMessage(TextChannel channel, int status, String fullBody) {
		if (status == 400) {
			Document body = Document.parse(fullBody);
			int code = body.getEmbedded(List.of("details", "code"), Integer.class);

			ImageError error = ImageError.fromCode(code);
			if (error != null && error.isUrlError()) {
				return channel.sendMessageFormat("That url could not be formed to a valid image %s", Config.get().getFailureEmote());
			} else {
				return channel.sendMessage(ExceptionUtility.getSimpleErrorMessage(String.format("- Code: %d\n- %s", code, body.getString("message")), "diff"));
			}
		} else {
			return channel.sendMessage(ExceptionUtility.getSimpleErrorMessage(String.format("- Status: %d\n- %s", status, fullBody), "diff"));
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

	public static int getEmbedColour(int colour) {
		return colour == 0 ? 65793 : colour == 16777215 ? 16711422 : colour;
	}

}
