package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.paged.PagedResult;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

import java.util.List;

public class MutualServersCommand extends Sx4Command {

	public MutualServersCommand() {
		super("mutual servers", 339);

		super.setDescription("View your mutual servers with another user and Sx4");
		super.setAliases("mutual guilds");
		super.setExamples("mutual servers @Shea#6653", "mutual servers Shea", "mutual servers 402557516728369153");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="user", endless=true) Member member) {
		List<Guild> mutual = event.getShardManager().getMutualGuilds(member.getUser());

		PagedResult<Guild> paged = new PagedResult<>(event.getBot(), mutual)
			.setAuthor("Mutual Servers", null, event.getAuthor().getEffectiveAvatarUrl())
			.setIndexed(false)
			.setSelect()
			.setDisplayFunction(Guild::getName);

		paged.execute(event);
	}

}
