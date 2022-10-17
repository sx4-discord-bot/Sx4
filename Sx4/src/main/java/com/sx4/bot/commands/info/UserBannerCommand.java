package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.utility.ImageUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

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

			ImageUtility.sendImageEmbed(event, banner, user.getAsTag());
		});
	}

}
