package com.sx4.bot.commands.image;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.annotations.argument.Colour;
import com.sx4.bot.annotations.argument.DefaultNumber;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.image.ImageRequest;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.utility.ColourUtility;
import com.sx4.bot.utility.ImageUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import okhttp3.Request;

public class ColourCommand extends Sx4Command {

	public ColourCommand() {
		super("colour", 5);

		super.setDescription("Get info and visualize a specific colour");
		super.setExamples("colour", "colour #ffff00", "colour 255, 255, 0");
		super.setAliases("color");
		super.setCategoryAll(ModuleCategory.IMAGE);
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="colour", endless=true) @DefaultNumber(-1) @Colour int colour) {
		if (colour == -1) {
			colour = event.getRandom().nextInt(0xFFFFFF + 1);
		}

		String hex = "#" + ColourUtility.toHexString(colour);

		MessageEmbed embed = new EmbedBuilder()
			.setColor(ImageUtility.getEmbedColour(colour))
			.setAuthor(hex, null, "attachment://image.png")
			.setDescription(String.format("Hex: %s\nRGB: %s", hex, ColourUtility.toRGBString(colour)))
			.setImage("attachment://image.png")
			.build();

		Request request = new ImageRequest("colour")
			.addQuery("colour", colour)
			.build(event.getConfig().getImageWebserver());

		event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
			MessageAction action = ImageUtility.getImageMessage(event, response);
			if (response.isSuccessful()) {
				action.embed(embed);
			}

			action.queue();
		});
	}

}
