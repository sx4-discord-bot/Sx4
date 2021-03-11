package com.sx4.bot.commands.image;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.option.Option;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.image.ImageRequest;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.utility.ImageUtility;
import com.sx4.bot.utility.SearchUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.sharding.ShardManager;
import okhttp3.Request;
import org.bson.Document;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;

public class DiscordCommand extends Sx4Command {

	private Document getMentions(ShardManager manager, Guild guild, String text) {
		Document users = new Document(), channels = new Document(), roles = new Document();
		Set<String> emotes = new HashSet<>();

		Matcher userMatcher = SearchUtility.USER_MENTION.matcher(text);
		while (userMatcher.find()) {
			User user = manager.getUserById(userMatcher.group(1));
			if (user != null) {
				Member member = guild.getMember(user);

				users.put(user.getId(), new Document("name", member == null ? user.getName() : member.getEffectiveName()));
			}
		}

		Matcher channelMatcher = SearchUtility.CHANNEL_MENTION.matcher(text);
		while (channelMatcher.find()) {
			TextChannel channel = guild.getTextChannelById(channelMatcher.group(1));
			if (channel != null) {
				channels.put(channel.getId(), new Document("name", channel.getName()));
			}
		}

		Matcher roleMatcher = SearchUtility.ROLE_MENTION.matcher(text);
		while (roleMatcher.find()) {
			Role role = guild.getRoleById(roleMatcher.group(1));
			if (role != null) {
				roles.put(role.getId(), new Document("name", role.getName()).append("colour", role.getColorRaw()));
			}
		}

		Matcher emoteMatcher = SearchUtility.EMOTE_MENTION.matcher(text);
		while (emoteMatcher.find()) {
			emotes.add(emoteMatcher.group(3));
		}

		return new Document("users", users)
			.append("channels", channels)
			.append("roles", roles)
			.append("emotes", emotes);
	}

	public DiscordCommand() {
		super("discord", 7);

		super.setDescription("Create a discord message as an image");
		super.setExamples("discord @Shea hello", "discord Shea#6653 hello @Shea go to #channel");
		super.setBotDiscordPermissions(Permission.MESSAGE_ATTACH_FILES);
		super.setCategoryAll(ModuleCategory.IMAGE);
		super.setCooldownDuration(5);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="user") Member member, @Argument(value="text", endless=true) @Limit(max=250) String text, @Option(value="light", description="Sets the discord theme to light") boolean light) {
		User user = member.getUser();

		Request request = new ImageRequest("discord")
			.addField("name", member.getEffectiveName())
			.addField("avatar", user.getEffectiveAvatarUrl())
			.addField("bot", user.isBot())
			.addField("dark_theme", !light)
			.addField("colour", member.getColorRaw())
			.addField("text", text)
			.addAllFields(this.getMentions(event.getShardManager(), event.getGuild(), text))
			.build(event.getConfig().getImageWebserver());

		event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> ImageUtility.getImageMessage(event, response).queue());
	}

}
