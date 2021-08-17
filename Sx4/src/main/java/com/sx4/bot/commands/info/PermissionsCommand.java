package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.IPermissionHolder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;

import java.util.EnumSet;
import java.util.StringJoiner;

public class PermissionsCommand extends Sx4Command {

	public PermissionsCommand() {
		super("permissions", 323);

		super.setDescription("Check the permissions of a user or role");
		super.setAliases("perms");
		super.setExamples("permissions", "permissions @Role", "permissions @Shea#6653");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="role | user", endless=true, nullDefault=true) IPermissionHolder holder) {
		IPermissionHolder effectiveHolder = holder == null ? event.getMember() : holder;
		boolean isRole = effectiveHolder instanceof Role;

		Member member = isRole ? null : (Member) effectiveHolder;
		User user = isRole ? null : member.getUser();

		Role role = isRole ? (Role) holder : null;

		EnumSet<Permission> permissions = effectiveHolder.getPermissions();

		StringJoiner joiner = new StringJoiner("\n");
		for (Permission permission : permissions) {
			joiner.add(permission.getName());
		}

		EmbedBuilder embed = new EmbedBuilder()
			.setAuthor(isRole ? role.getName() : user.getAsTag(), null, isRole ? event.getGuild().getIconUrl() : user.getEffectiveAvatarUrl())
			.setDescription(joiner.toString())
			.setColor(isRole ? role.getColorRaw() : member.getColorRaw());

		event.reply(embed.build()).queue();
	}

}
