package com.sx4.bot.commands.info;

import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.image.ImageRequest;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.utility.ImageUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import okhttp3.Request;
import org.bson.Document;

public class ServerBannerCommand extends Sx4Command {

	public ServerBannerCommand() {
		super("server banner", 274);

		super.setDescription("View the banner of the current server");
		super.setExamples("server banner");
		super.setAliases("serverbanner");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event) {
		String banner = event.getGuild().getBannerUrl();
		if (banner == null) {
			event.replyFailure("This server does not have a banner").queue();
			return;
		}

		Request request = new ImageRequest(event.getConfig().getImageWebserverUrl("median-colour"))
			.addQuery("image", banner)
			.build(event.getConfig().getImageWebserver());

		event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
			if (!response.isSuccessful()) {
				ImageUtility.getErrorMessage(event.getChannel(), response.code(), response.body().string()).queue();
				return;
			}

			Document data = Document.parse(response.body().string());

			String sizedBanner = banner + "?size=1024";

			EmbedBuilder embed = new EmbedBuilder()
				.setImage(sizedBanner)
				.setColor(data.getInteger("colour"))
				.setAuthor(event.getGuild().getName(), sizedBanner, sizedBanner);

			event.reply(embed.build()).queue();
		});
	}

}
