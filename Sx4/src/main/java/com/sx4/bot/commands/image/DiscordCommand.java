package com.sx4.bot.commands.image;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.option.Option;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.argument.MessageArgument;
import com.sx4.bot.entities.argument.Or;
import com.sx4.bot.entities.image.ImageRequest;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.utility.ImageUtility;
import com.sx4.bot.utility.SearchUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import okhttp3.Request;
import org.bson.Document;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;

public class DiscordCommand extends Sx4Command {

	public DiscordCommand() {
		super("discord", 7);

		super.setDescription("Create a discord message as an image");
		super.setExamples("discord @Shea hello", "discord Shea#6653 hello @Shea go to #channel");
		super.setBotDiscordPermissions(Permission.MESSAGE_ATTACH_FILES);
		super.setCategoryAll(ModuleCategory.IMAGE);
		super.setCooldownDuration(5);
	}

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
			GuildMessageChannel channel = guild.getChannelById(GuildMessageChannel.class, channelMatcher.group(1));
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

	private CompletableFuture<Pair<Member, String>> getContext(Or<MessageArgument, String> option, Member member) {
		if (option.hasFirst()) {
			return option.getFirst().retrieveMessage().submit().thenApply(message -> Pair.of(message.getMember(), message.getContentRaw()));
		} else {
			return CompletableFuture.completedFuture(Pair.of(member, option.getSecond()));
		}
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="user", nullDefault=true) Member member, @Argument(value="text | message id", endless=true) @Limit(max=250) Or<MessageArgument, String> option, @Option(value="light", description="Sets the discord theme to light") boolean light) {
		if (member == null && option.hasSecond()) {
			event.replyFailure("You need to provide a user when not giving a message").queue();
			return;
		}

		this.getContext(option, member).thenAccept(pair -> {
			Member effectiveMember = pair.getLeft();
			User user = effectiveMember.getUser();
			String text = pair.getRight();

			Request request = new ImageRequest(event.getConfig().getImageWebserverUrl("discord"))
				.addField("name", effectiveMember.getEffectiveName())
				.addField("avatar", user.getEffectiveAvatarUrl())
				.addField("bot", user.isBot())
				.addField("dark_theme", !light)
				.addField("colour", effectiveMember.getColorRaw())
				.addField("text", text)
				.addAllFields(this.getMentions(event.getShardManager(), event.getGuild(), text))
				.build(event.getConfig().getImageWebserver());

			event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> ImageUtility.getImageMessage(event, response).queue());
		});
	}

}
