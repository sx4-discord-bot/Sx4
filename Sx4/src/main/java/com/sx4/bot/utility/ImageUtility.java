package com.sx4.bot.utility;

import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.image.ImageError;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import okhttp3.Response;
import org.bson.Document;

import java.io.IOException;
import java.util.List;

public class ImageUtility {

	public static MessageAction sendImage(Sx4CommandEvent event, Response response) throws IOException {
		int status = response.code();
		if (status == 200) {
			return event.replyFile(response.body().bytes(), String.format("image.%s", response.header("Content-Type").split("/")[1]));
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

}
