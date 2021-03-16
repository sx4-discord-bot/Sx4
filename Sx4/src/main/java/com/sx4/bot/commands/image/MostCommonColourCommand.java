package com.sx4.bot.commands.image;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.annotations.argument.ImageUrl;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.image.ImageRequest;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.utility.ColourUtility;
import com.sx4.bot.utility.ImageUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import okhttp3.Request;
import org.bson.Document;

public class MostCommonColourCommand extends Sx4Command {

	public MostCommonColourCommand() {
		super("most common colour", 19);

		super.setDescription("Shows you the most common colour of an image");
		super.setAliases("most common color", "mostcommoncolour", "mostcommoncolor");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setExamples("most common colour", "most common colour Shea#6653", "most common colour https://example.com/image.png");
		super.setCooldownDuration(3);
		super.setCategoryAll(ModuleCategory.IMAGE);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="image url", acceptEmpty=true, endless=true) @ImageUrl String imageUrl) {
		Request request = new ImageRequest(event.getConfig().getImageWebserverUrl("common-colour"))
			.addQuery("image", imageUrl)
			.build(event.getConfig().getImageWebserver());

		event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
			if (!response.isSuccessful()) {
				ImageUtility.getErrorMessage(event.getTextChannel(), response.code(), response.body().string()).queue();
				return;
			}

			Document data = Document.parse(response.body().string());
			Document common = data.getList("colours", Document.class).get(0);

			int colour = common.getInteger("colour");

			EmbedBuilder embed = new EmbedBuilder()
				.setTitle("Most Common Colour")
				.setThumbnail(imageUrl)
				.setColor(ImageUtility.getEmbedColour(colour))
				.addField("Colour", String.format("Hex: #%s\nRGB: %s", ColourUtility.toHexString(colour), ColourUtility.toRGBString(colour)), true)
				.addField("Pixels", String.format("Amount: %,d", common.getInteger("pixels")), true);

			event.reply(embed.build()).queue();
		});
	}

}
