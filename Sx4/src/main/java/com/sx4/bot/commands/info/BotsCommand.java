package com.sx4.bot.commands.info;

import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.paged.PagedResult;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

import java.util.List;
import java.util.stream.Collectors;

public class BotsCommand extends Sx4Command {

	public BotsCommand() {
		super("bots", 302);

		super.setDescription("View all the bots in the current server");
		super.setExamples("bots");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event) {
		List<User> bots = event.getGuild().getMemberCache().applyStream(stream -> stream.map(Member::getUser).filter(User::isBot).collect(Collectors.toList()));
		if (bots.isEmpty()) {
			event.replyFailure("There are no bots in this server").queue();
			return;
		}

		PagedResult<User> paged = new PagedResult<>(event.getBot(), bots)
			.setAuthor("Bots", null, event.getGuild().getIconUrl())
			.setPerPage(15)
			.setSelect()
			.setIndexed(false)
			.setDisplayFunction(User::getAsTag);

		paged.execute(event);
	}

}
