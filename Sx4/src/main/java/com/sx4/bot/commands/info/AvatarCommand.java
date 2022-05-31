package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.option.Option;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.image.ImageRequest;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.utility.ImageUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import okhttp3.Request;
import org.bson.Document;

public class AvatarCommand extends Sx4Command {

	public AvatarCommand() {
		super("avatar", 273);

		super.setDescription("View the avatar of a user");
		super.setAliases("av");
		super.setExamples("avatar", "avatar @Shea#6653", "avatar Shea --global");
		super.setCategoryAll(ModuleCategory.INFORMATION);
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="user", endless=true, nullDefault=true) Member member, @Option(value="global", description="Forced to show their global avatar") boolean global) {
		Member effectiveMember = member == null ? event.getMember() : member;
		User user = effectiveMember.getUser();

		String avatar = global ? user.getEffectiveAvatarUrl() : effectiveMember.getEffectiveAvatarUrl();

		Request request = new ImageRequest(event.getConfig().getImageWebserverUrl("median-colour"))
			.addQuery("image", avatar)
			.build(event.getConfig().getImageWebserver());

		event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
			if (!response.isSuccessful()) {
				ImageUtility.getErrorMessage(event.getChannel(), response.code(), response.body().string()).queue();
				return;
			}

			Document data = Document.parse(response.body().string());

			String sizedAvatar = avatar + "?size=1024";

			EmbedBuilder embed = new EmbedBuilder()
				.setImage(sizedAvatar)
				.setColor(data.getInteger("colour"))
				.setAuthor(user.getAsTag(), sizedAvatar, user.getEffectiveAvatarUrl());

			event.reply(embed.build()).queue();
		});
	}

}
