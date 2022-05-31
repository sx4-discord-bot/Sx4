package com.sx4.bot.utility;

import com.jockie.bot.core.command.impl.CommandEvent;
import com.sx4.bot.config.Config;
import com.sx4.bot.entities.image.ImageError;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import okhttp3.Response;
import org.bson.Document;

import java.io.IOException;
import java.util.List;
import java.util.function.BiFunction;
import java.util.regex.Matcher;

public class ImageUtility {

	public static MessageAction getImageMessage(CommandEvent event, Response response) throws IOException {
		return ImageUtility.getImageMessage(event.getChannel(), response);
	}

	public static MessageAction getImageMessage(CommandEvent event, Response response, BiFunction<Document, ImageError, MessageAction> badRequest) throws IOException {
		return ImageUtility.getImageMessage(event.getChannel(), response, badRequest);
	}

	public static MessageAction getImageMessage(MessageChannel channel, Response response, BiFunction<Document, ImageError, MessageAction> badRequest) throws IOException {
		int status = response.code();
		if (status == 200) {
			byte[] bytes = response.body().bytes();
			if (bytes.length > Message.MAX_FILE_SIZE) {
				return channel.sendMessageFormat("File size cannot exceed %s (**%s**) %s", NumberUtility.getBytesReadable(Message.MAX_FILE_SIZE), NumberUtility.getBytesReadable(bytes.length), Config.get().getFailureEmote());
			}

			return channel.sendFile(bytes, String.format("image.%s", response.header("Content-Type").split("/")[1]));
		} else {
			return ImageUtility.getErrorMessage(channel, status, response.body().string(), badRequest);
		}
	}

	public static MessageAction getImageMessage(MessageChannel channel, Response response) throws IOException {
		return ImageUtility.getImageMessage(channel, response, null);
	}

	public static MessageAction getErrorMessage(MessageChannel channel, int status, String fullBody, BiFunction<Document, ImageError, MessageAction> badRequest) {
		if (status == 400) {
			Document body = Document.parse(fullBody);
			int code = body.getEmbedded(List.of("details", "code"), Integer.class);

			ImageError error = ImageError.fromCode(code);
			if (error != null && error.isUrlError()) {
				return channel.sendMessageFormat("That url could not be formed to a valid image %s", Config.get().getFailureEmote());
			}

			MessageAction messageAction = badRequest == null ? null : badRequest.apply(body, error);
			if (messageAction == null) {
				return channel.sendMessageEmbeds(ExceptionUtility.getSimpleErrorMessage(String.format("- Code: %d\n- %s", error.getCode(), body.getString("message")), "diff"));
			} else {
				return messageAction;
			}
		} else {
			return channel.sendMessageEmbeds(ExceptionUtility.getSimpleErrorMessage(String.format("- Status: %d\n- %s", status, fullBody), "diff"));
		}
	}

	public static MessageAction getErrorMessage(MessageChannel channel, int status, String fullBody) {
		return ImageUtility.getErrorMessage(channel, status, fullBody, null);
	}

	public static String escapeMentions(Guild guild, String text) {
		Matcher userMatcher = SearchUtility.USER_MENTION.matcher(text);
		while (userMatcher.find()) {
			User user = guild.getJDA().getShardManager().getUserById(userMatcher.group(1));
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
