package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.image.ImageRequest;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.utility.ImageUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import okhttp3.Request;
import org.bson.Document;

public class UserBannerCommand extends Sx4Command {

	public UserBannerCommand() {
		super("user banner", 472);

		super.setDescription("View a users banner from their profile");
		super.setExamples("user banner", "user banner @Shea#6653", "user banner Shea");
		super.setAliases("banner", "userbanner");
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="user", endless=true, nullDefault=true) Member member) {
		User user = member == null ? event.getAuthor() : member.getUser();

		user.retrieveProfile().queue(profile -> {
			String banner = profile.getBannerUrl();
			if (banner == null) {
				int accent = profile.getAccentColorRaw();
				if (accent == User.DEFAULT_ACCENT_COLOR_RAW) {
					event.replyFailure("That user does not have a banner").queue();
					return;
				}

				String accentBanner = event.getConfig().getImageWebserverUrl("colour") + "?colour=" + accent + "&w=1024&h=205";

				EmbedBuilder embed = new EmbedBuilder()
					.setImage(accentBanner)
					.setColor(accent)
					.setAuthor(user.getAsTag(), accentBanner, user.getEffectiveAvatarUrl());

				event.reply(embed.build()).queue();
				return;
			}

			Request request = new ImageRequest(event.getConfig().getImageWebserverUrl("median-colour"))
				.addQuery("image", banner)
				.build(event.getConfig().getImageWebserver());

			event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
				if (!response.isSuccessful()) {
					ImageUtility.getErrorMessage(event.getTextChannel(), response.code(), response.body().string()).queue();
					return;
				}

				Document data = Document.parse(response.body().string());

				String sizedAvatar = banner + "?size=1024";

				EmbedBuilder embed = new EmbedBuilder()
					.setImage(sizedAvatar)
					.setColor(data.getInteger("colour"))
					.setAuthor(user.getAsTag(), sizedAvatar, user.getEffectiveAvatarUrl());

				event.reply(embed.build()).queue();
			});
		});
	}

}
