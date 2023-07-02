package com.sx4.bot.commands.info;

import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.paged.MessagePagedResult;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Role;

public class ServerRolesCommand extends Sx4Command {

	public ServerRolesCommand() {
		super("server roles", 304);

		super.setDescription("View all the roles in the current server");
		super.setAliases("serverroles", "roles");
		super.setExamples("server roles");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event) {
		MessagePagedResult<Role> paged = new MessagePagedResult.Builder<>(event.getBot(), event.getGuild().getRoles())
			.setPerPage(15)
			.setAuthor("Roles", null, event.getGuild().getIconUrl())
			.setSelect()
			.setIndexed(false)
			.setDisplayFunction(Role::getAsMention)
			.build();

		paged.execute(event);
	}

}
