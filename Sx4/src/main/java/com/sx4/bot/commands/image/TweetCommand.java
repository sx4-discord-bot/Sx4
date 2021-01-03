package com.sx4.bot.commands.image;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.image.ImageRequest;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.utility.ImageUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import okhttp3.Request;

import java.util.ArrayList;
import java.util.List;

public class TweetCommand extends Sx4Command {

	public TweetCommand() {
		super("tweet", 26);

		super.setDescription("Send a tweet as a discord user");
		super.setExamples("tweet @Shea hello #Sx4", "tweet Shea#6653 @Sx4 prefix");
		super.setBotDiscordPermissions(Permission.MESSAGE_ATTACH_FILES);
		super.setCooldownDuration(5);
		super.setCategoryAll(ModuleCategory.IMAGE);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="user") Member member, @Argument(value="text", endless=true) @Limit(max=280) String text) {
		User user = member.getUser();
		Guild guild = event.getGuild();

		int memberCount = guild.getMemberCount();
		int likes = event.getRandom().nextInt(memberCount);

		List<Member> members = guild.getMembers();

		List<String> urls = new ArrayList<>();
		for (int i = 0; i < Math.min(10, likes); i++) {
			urls.add(members.get(event.getRandom().nextInt(memberCount)).getUser().getEffectiveAvatarUrl() + "?size=64");
		}

		Request request = new ImageRequest("tweet")
			.addField("display_name", member.getEffectiveName())
			.addField("name", user.getName())
			.addField("avatar", user.getEffectiveAvatarUrl() + "?size=128")
			.addField("retweets", event.getRandom().nextInt(memberCount))
			.addField("likes", likes)
			.addField("text", ImageUtility.escapeMentions(guild, text))
			.addField("urls", urls)
			.build();

		event.getClient().newCall(request).enqueue((HttpCallback) response -> ImageUtility.getImageMessage(event, response).queue());
	}

}
