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

public class ServerAvatarCommand extends Sx4Command {

	public ServerAvatarCommand() {
		super("server avatar", 274);

		super.setDescription("View the avatar of the current server");
		super.setExamples("server avatar");
		super.setAliases("server icon", "servericon", "serverav", "sav", "server av");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event) {
		String icon = event.getGuild().getIconUrl();
		if (icon == null) {
			event.replyFailure("This server does not have an icon").queue();
			return;
		}

		Request request = new ImageRequest(event.getConfig().getImageWebserverUrl("median-colour"))
			.addQuery("image", icon)
			.build(event.getConfig().getImageWebserver());

		event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
			if (!response.isSuccessful()) {
				ImageUtility.getErrorMessage(event.getChannel(), response.code(), response.body().string()).queue();
				return;
			}

			Document data = Document.parse(response.body().string());

			String sizedIcon = icon + "?size=1024";

			EmbedBuilder embed = new EmbedBuilder()
				.setImage(sizedIcon)
				.setColor(data.getInteger("colour"))
				.setAuthor(event.getGuild().getName(), sizedIcon, sizedIcon);

			event.reply(embed.build()).queue();
		});
	}

}
