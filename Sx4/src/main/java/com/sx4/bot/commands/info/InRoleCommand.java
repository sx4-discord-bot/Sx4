package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.option.Option;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.paged.PagedResult;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class InRoleCommand extends Sx4Command {

	public InRoleCommand() {
		super("in role", 233);

		super.setAliases("inrole");
		super.setDescription("View all users in one or more roles");
		super.setExamples("in role @Role", "in role Role", "in role @Role @AnotherRole");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="roles") Role[] roles, @Option(value="not", description="Shows who is not in the specified roles instead") boolean not) {
		List<Member> members = event.getGuild().getMemberCache().applyStream(stream -> stream.filter(member -> not != member.getRoles().containsAll(Arrays.asList(roles))).collect(Collectors.toList()));
		if (members.isEmpty()) {
			event.replyFailure("There is no one " + (not ? "not " : "") + "in those roles").queue();
			return;
		}

		PagedResult<Member> paged = new PagedResult<>(event.getBot(), members)
			.setPerPage(15)
			.setSelect()
			.setDisplayFunction(member -> member.getUser().getAsTag())
			.setIndexed(false)
			.setAuthor("Users (" + members.size() + ")", null, event.getGuild().getIconUrl());

		paged.execute(event);
	}

}
