package com.sx4.bot.commands.image;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.annotations.argument.ImageUrl;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.image.ImageRequest;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.paged.MessagePagedResult;
import com.sx4.bot.utility.ColourUtility;
import com.sx4.bot.utility.ImageUtility;
import com.sx4.bot.utility.NumberUtility;
import net.dv8tion.jda.api.Permission;
import okhttp3.Request;
import org.bson.Document;

import java.util.List;

public class ColourCoverageCommand extends Sx4Command {

	public ColourCoverageCommand() {
		super("colour coverage", 328);

		super.setDescription("View the coverage of each individual colour in an image");
		super.setAliases("colourcoverage", "color coverage", "colorcoverage");
		super.setExamples("colour coverage", "colour coverage @Shea#6653", "colour coverage https://example.com/image.png");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.IMAGE);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="image url", endless=true, acceptEmpty=true) @ImageUrl String imageUrl) {
		Request request = new ImageRequest(event.getConfig().getImageWebserverUrl("common-colour"))
			.addQuery("image", imageUrl)
			.build(event.getConfig().getImageWebserver());

		event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
			if (!response.isSuccessful()) {
				ImageUtility.sendErrorMessage(event.getChannel(), response.code(), response.body().string()).queue();
				return;
			}

			Document data = Document.parse(response.body().string());

			List<Document> colours = data.getList("colours", Document.class);
			long totalPixels = colours.stream().mapToInt(colour -> colour.getInteger("pixels")).sum();

			MessagePagedResult<Document> paged = new MessagePagedResult.Builder<>(event.getBot(), colours)
				.setAuthor("Colours", null, imageUrl)
				.setIncreasedIndex(true)
				.setSelect()
				.setPerPage(15)
				.setDisplayFunction(colour -> {
					int pixels = colour.getInteger("pixels");
					return "`#" + ColourUtility.toHexString(colour.getInteger("colour")) + "` - " + pixels + " pixel" + (pixels == 1 ? "" : "s") + " (" + NumberUtility.DEFAULT_DECIMAL_FORMAT.format((pixels / (double) totalPixels) * 100) + "%)";
				}).build();

			paged.execute(event);
		});
	}

}
