package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.annotations.argument.UserId;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;

import java.time.format.DateTimeFormatter;

public class UserInfoCommand extends Sx4Command {

	private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd LLL yyyy HH:mm");

	public UserInfoCommand() {
		super("user info", 264);

		super.setAliases("user", "ui");
		super.setDescription("Get basic info on any user on discord by their mention or id");
		super.setExamples("user info @Shea#6653", "user info 402557516728369153");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="user") @UserId long userId) {
		event.getJDA().retrieveUserById(userId)
			.flatMap(user -> {
				EmbedBuilder embed = new EmbedBuilder();
				embed.setAuthor(user.getAsTag(), user.getEffectiveAvatarUrl(), user.getEffectiveAvatarUrl());
				embed.setThumbnail(user.getEffectiveAvatarUrl());
				embed.addField("User ID", user.getId(), true);
				embed.addBlankField(true);
				embed.addField("Joined Discord", user.getTimeCreated().format(this.formatter), true);
				embed.addField("Bot", user.isBot() ? "Yes" : "No", true);

				return event.reply(embed.build());
			}).onErrorFlatMap(exception -> {
				if (exception instanceof ErrorResponseException && ((ErrorResponseException) exception).getErrorResponse() == ErrorResponse.UNKNOWN_USER) {
					return event.replyFailure("I could not find that user");
				}

				return null;
			}).queue();
	}

}
