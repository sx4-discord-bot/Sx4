package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import net.dv8tion.jda.api.entities.Message;

import java.io.IOException;

public class DecodeCommand extends Sx4Command {

	public DecodeCommand() {
		super("decode", 261);

		super.setDescription("Decode any text file into discord markdown");
		super.setExamples("decode");
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="attachment") Message.Attachment attachment) {
		if (attachment.isImage() || attachment.isVideo()) {
			event.replyFailure("The attachment cannot be an image or video").queue();
			return;
		}

		attachment.retrieveInputStream().thenAccept(file -> {
			try {
				String fileContent = new String(file.readAllBytes()), fileExtension = attachment.getFileExtension();

				event.reply(("```" + fileExtension + "\n" + fileContent).substring(0, Math.min(1997, fileContent.length())) + "```").queue();
			} catch (IOException e) {
				event.reply("Oops, something went wrong there, try again :no_entry:").queue();
			}
		});
	}

}
