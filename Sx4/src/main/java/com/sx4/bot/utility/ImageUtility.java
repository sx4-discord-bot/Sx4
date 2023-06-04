package com.sx4.bot.utility;

import com.jockie.bot.core.command.impl.CommandEvent;
import com.sx4.bot.config.Config;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.image.ImageError;
import com.sx4.bot.entities.image.ImageRequest;
import com.sx4.bot.http.HttpCallback;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import okhttp3.Request;
import okhttp3.Response;
import org.bson.Document;

import java.io.IOException;
import java.util.List;
import java.util.function.BiFunction;
import java.util.regex.Matcher;

public class ImageUtility {

	public static void sendImageEmbed(Sx4CommandEvent event, String image, String title) {
		Request request = new ImageRequest(event.getConfig().getImageWebserverUrl("median-colour"))
			.addQuery("image", image)
			.build(event.getConfig().getImageWebserver());

		event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
			if (!response.isSuccessful()) {
				ImageUtility.sendErrorMessage(event.getChannel(), response.code(), response.body().string()).queue();
				return;
			}

			Document data = Document.parse(response.body().string());

			String sizedImage = image + "?size=1024";

			EmbedBuilder embed = new EmbedBuilder()
				.setImage(sizedImage)
				.setColor(data.getInteger("colour"))
				.setAuthor(title, sizedImage, sizedImage);

			event.reply(embed.build()).queue();
		});
	}

	public static MessageCreateBuilder getImageMessage(Response response) throws IOException {
		return ImageUtility.getImageMessage(response, null);
	}

	public static MessageCreateBuilder getImageMessage(Response response, BiFunction<Document, ImageError, MessageCreateBuilder> badRequest) throws IOException {
		MessageCreateBuilder message = new MessageCreateBuilder();

		int status = response.code();
		if (status == 200) {
			byte[] bytes = response.body().bytes();
			if (bytes.length > Message.MAX_FILE_SIZE) {
				return message.setContent(String.format("File size cannot exceed %s (**%s**) %s", NumberUtility.getBytesReadable(Message.MAX_FILE_SIZE), NumberUtility.getBytesReadable(bytes.length), Config.get().getFailureEmote()));
			}

			return message.setFiles(FileUpload.fromData(bytes, String.format("image.%s", response.header("Content-Type").split("/")[1])));
		} else {
			return ImageUtility.getErrorMessage(status, response.body().string(), badRequest);
		}
	}

	public static MessageCreateAction sendImageMessage(MessageChannel channel, Response response, BiFunction<Document, ImageError, MessageCreateBuilder> badRequest) throws IOException {
		return channel.sendMessage(ImageUtility.getImageMessage(response, badRequest).build());
	}

	public static MessageCreateAction sendImageMessage(MessageChannel channel, Response response) throws IOException {
		return ImageUtility.sendImageMessage(channel, response, null);
	}

	public static MessageCreateAction sendImageMessage(CommandEvent event, Response response) throws IOException {
		return ImageUtility.sendImageMessage(event.getChannel(), response);
	}

	public static MessageCreateAction sendImageMessage(CommandEvent event, Response response, BiFunction<Document, ImageError, MessageCreateBuilder> badRequest) throws IOException {
		return ImageUtility.sendImageMessage(event.getChannel(), response, badRequest);
	}

	public static MessageCreateBuilder getErrorMessage(int status, String fullBody, BiFunction<Document, ImageError, MessageCreateBuilder> badRequest) {
		MessageCreateBuilder message = new MessageCreateBuilder();

		if (status == 400) {
			Document body = Document.parse(fullBody);
			int code = body.getEmbedded(List.of("details", "code"), Integer.class);

			ImageError error = ImageError.fromCode(code);
			if (error != null && error.isUrlError()) {
				return message.setContent(String.format("That url could not be formed to a valid image %s", Config.get().getFailureEmote()));
			}

			MessageCreateBuilder builder = badRequest == null ? null : badRequest.apply(body, error);
			if (builder == null) {
				return message.setEmbeds(ExceptionUtility.getSimpleErrorMessage(String.format("- Code: %d\n- %s", error.getCode(), body.getString("message")), "diff"));
			} else {
				return builder;
			}
		} else {
			return message.setEmbeds(ExceptionUtility.getSimpleErrorMessage(String.format("- Status: %d\n- %s", status, fullBody), "diff"));
		}
	}

	public static MessageCreateAction sendErrorMessage(MessageChannel channel, int status, String fullBody, BiFunction<Document, ImageError, MessageCreateBuilder> badRequest) {
		return channel.sendMessage(ImageUtility.getErrorMessage(status, fullBody, badRequest).build());
	}

	public static MessageCreateAction sendErrorMessage(MessageChannel channel, int status, String fullBody) {
		return ImageUtility.sendErrorMessage(channel, status, fullBody, null);
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
			GuildMessageChannel channel = guild.getChannelById(GuildMessageChannel.class, channelMatcher.group(1));
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
