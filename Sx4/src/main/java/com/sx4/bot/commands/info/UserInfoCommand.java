package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.annotations.argument.UserId;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.utility.TimeUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.internal.requests.CompletedRestAction;

public class UserInfoCommand extends Sx4Command {

	public UserInfoCommand() {
		super("user info", 264);

		super.setAliases("user", "ui", "uinfo");
		super.setDescription("Get basic info on any user on discord by their mention or id");
		super.setExamples("user info", "user info @Shea#6653", "user info 402557516728369153");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="user", nullDefault=true) @UserId long userId) {
		RestAction<User> action = userId == 0L ? new CompletedRestAction<>(event.getJDA(), event.getAuthor()) : event.getJDA().retrieveUserById(userId);

		action.flatMap(user -> {
			EmbedBuilder embed = new EmbedBuilder()
				.setDescription(event.getConfig().getUserFlagEmotes(user.getFlags()))
				.setAuthor(user.getAsTag(), user.getEffectiveAvatarUrl(), user.getEffectiveAvatarUrl())
				.setThumbnail(user.getEffectiveAvatarUrl())
				.addField("Joined Discord", user.getTimeCreated().format(TimeUtility.DEFAULT_FORMATTER), true)
				.addField("User ID", user.getId(), true)
				.addField("Bot", user.isBot() ? "Yes" : "No", false);

			return event.reply(embed.build());
		}).onErrorFlatMap(exception -> {
			if (exception instanceof ErrorResponseException && ((ErrorResponseException) exception).getErrorResponse() == ErrorResponse.UNKNOWN_USER) {
				return event.replyFailure("I could not find that user");
			}

			return null;
		}).queue();
	}

}
