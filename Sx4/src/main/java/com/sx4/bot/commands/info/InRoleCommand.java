package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.paged.PagedResult;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

import java.util.List;

public class InRoleCommand extends Sx4Command {

	public InRoleCommand() {
		super("in role", 233);

		super.setAliases("inrole");
		super.setDescription("View all users in one or more roles");
		super.setExamples("in role @Role", "in role Role", "in role @Role @AnotherRole");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="roles") Role... roles) {
		List<Member> members = event.getGuild().getMembersWithRoles(roles);
		if (members.isEmpty()) {
			event.replyFailure("There is no one in those roles").queue();
			return;
		}

		PagedResult<Member> paged = new PagedResult<>(event.getBot(), members)
			.setPerPage(15)
			.setDisplayFunction(member -> member.getUser().getAsTag())
			.setIndexed(false)
			.setAuthor("Users (" + members.size() + ")", null, event.getGuild().getIconUrl());

		paged.execute(event);
	}

}
