package com.sx4.bot.commands.image;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.image.ImageRequest;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.utility.ImageUtility;
import com.sx4.bot.utility.SearchUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import okhttp3.Request;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

public class TweetCommand extends Sx4Command {

	private String escapeMentions(Guild guild, String text) {
		Matcher userMatcher = SearchUtility.USER_MENTION.matcher(text);
		while (userMatcher.find()) {
			User user = Sx4.get().getShardManager().getUserById(userMatcher.group(1));
			if (user != null) {
				Member member = guild.getMember(user);
				String name = member == null ? user.getName() : member.getEffectiveName();

				text = text.replace(userMatcher.group(0), "@" + name);
			}
		}

		Matcher channelMatcher = SearchUtility.CHANNEL_MENTION.matcher(text);
		while (channelMatcher.find()) {
			TextChannel channel = guild.getTextChannelById(channelMatcher.group(1));
			if (channel != null) {
				text = text.replace(channelMatcher.group(0), "#" + channel.getName());
			}
		}

		return text;
	}

	public TweetCommand() {
		super("tweet");

		super.setDescription("Send a tweet as a discord user");
		super.setExamples("tweet @Shea hello #Sx4", "tweet Shea#6653 @Sx4 prefix");
		super.setBotDiscordPermissions(Permission.MESSAGE_ATTACH_FILES);
		super.setCooldownDuration(5);
		super.setCategory(ModuleCategory.IMAGE);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="user") Member member, @Argument(value="text", endless=true) String text) {
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
			.addField("text", this.escapeMentions(guild, text))
			.addField("urls", urls)
			.build();

		event.getClient().newCall(request).enqueue((HttpCallback) response -> ImageUtility.sendImage(event, response).queue());
	}

}
