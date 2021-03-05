package com.sx4.bot.commands.mod;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.option.Option;
import com.sx4.bot.annotations.argument.Colour;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.annotations.command.*;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

public class RoleCommand extends Sx4Command {

	public RoleCommand() {
		super("role", 246);

		super.setDescription("Allows you to do multiple actions with roles");
		super.setExamples("role create", "role delete", "role edit");
	}

	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}

	@Command(value="create", description="Creates a role in the current server")
	@CommandId(247)
	@Redirects({"create role", "cr"})
	@Examples({"role create Yellow", "role create Dog person", "role create Shea"})
	@AuthorPermissions(permissions={Permission.MANAGE_ROLES})
	@BotPermissions(permissions={Permission.MANAGE_ROLES})
	public void create(Sx4CommandEvent event, @Argument(value="name", endless=true) String name) {
		event.getGuild().createRole().setName(name)
			.flatMap(role -> event.replySuccess(role.getAsMention() + " has been created"))
			.queue();
	}

	@Command(value="delete", description="Deletes a role in the current server")
	@CommandId(248)
	@Redirects({"delete role", "dr"})
	@Examples({"role delete @Yellow", "role delete Dog person", "role delete 406240455622262784"})
	@AuthorPermissions(permissions={Permission.MANAGE_ROLES})
	@BotPermissions(permissions={Permission.MANAGE_ROLES})
	public void delete(Sx4CommandEvent event, @Argument(value="name", endless=true) Role role) {
		if (role.isManaged()) {
			event.replyFailure("I cannot delete managed roles").queue();
			return;
		}

		if (role.isPublicRole()) {
			event.replyFailure("I cannot delete the @everyone role").queue();
			return;
		}

		if (!event.getMember().canInteract(role)) {
			event.replyFailure("You cannot delete a role higher or equal then your top role").queue();
			return;
		}

		if (!event.getSelfMember().canInteract(role)) {
			event.replyFailure("I cannot delete a role higher or equal then my top role").queue();
			return;
		}

		role.delete()
			.flatMap($ -> event.replySuccess("The role **" + role.getName() + "** is now deleted"))
			.queue();
	}

	@Command(value="edit", description="Edit multiple role attributes")
	@CommandId(249)
	@Examples({"role edit @Role colour=#ffff00", "role edit Role name=Shea colour=#ffff00", "role edit 402557516728369153 mentionable=true hoisted=false"})
	@AuthorPermissions(permissions={Permission.MANAGE_ROLES})
	@BotPermissions(permissions={Permission.MANAGE_ROLES})
	public void edit(Sx4CommandEvent event, @Argument(value="role") Role role, @Option(value="colour", description="Set the colour of the role") @Colour Integer colour, @Option(value="name", description="Set the name of the role") @Limit(max=32) String name, @Option(value="mentionable", description="Set the mention state of the role") Boolean mentionable, @Option(value="hoisted", description="Set the hoist state of the role") Boolean hosted, @Option(value="permissions", description="Set the permissions of the role") Long permissions) {
		if (!event.getMember().canInteract(role)) {
			event.replyFailure("You cannot edit a role higher or equal then your top role").queue();
			return;
		}

		if (!event.getSelfMember().canInteract(role)) {
			event.replyFailure("I cannot edit a role higher or equal then my top role").queue();
			return;
		}

		if (event.getOptions().isEmpty()) {
			event.replyFailure("You need to edit at least 1 attribute of the role").queue();
			return;
		}

		role.getManager()
			.setColor(colour == null ? role.getColorRaw() : colour)
			.setHoisted(hosted == null ? role.isHoisted() : hosted)
			.setMentionable(mentionable == null ? role.isMentionable() : mentionable)
			.setPermissions(permissions == null ? role.getPermissionsRaw() : permissions)
			.setName(name == null ? role.getName() : name)
			.flatMap($ -> event.replySuccess(role.getAsMention() + " has been edited"))
			.queue();
	}

	@Command(value="add", description="Add a role to a member")
	@CommandId(250)
	@Redirects({"addrole", "add role", "ar"})
	@Examples({"role add @Shea#6653 Role", "role add Shea 345718366373150720", "role add @Role"})
	@AuthorPermissions(permissions={Permission.MANAGE_ROLES})
	@BotPermissions(permissions={Permission.MANAGE_ROLES})
	public void add(Sx4CommandEvent event, @Argument(value="user", nullDefault=true) Member member, @Argument(value="role", endless=true) Role role) {
		if (role.isManaged()) {
			event.replyFailure("I cannot give managed roles").queue();
			return;
		}

		if (role.isPublicRole()) {
			event.replyFailure("I cannot give the @everyone role").queue();
			return;
		}

		if (!event.getMember().canInteract(role)) {
			event.replyFailure("You cannot give a role higher or equal then your top role").queue();
			return;
		}

		if (!event.getSelfMember().canInteract(role)) {
			event.replyFailure("I cannot give a role higher or equal then my top role").queue();
			return;
		}

		Member effectiveMember = member == null ? event.getMember() : member;

		event.getGuild().addRoleToMember(effectiveMember, role)
			.flatMap($ -> event.replySuccess(role.getAsMention() + " has been added to " + effectiveMember.getAsMention()))
			.queue();
	}

	@Command(value="remove", description="Remove a role from a member")
	@CommandId(251)
	@Redirects({"removerole", "remove role", "rr"})
	@Examples({"role remove @Shea#6653 Role", "role remove Shea 345718366373150720", "role remove @Role"})
	@AuthorPermissions(permissions={Permission.MANAGE_ROLES})
	@BotPermissions(permissions={Permission.MANAGE_ROLES})
	public void remove(Sx4CommandEvent event, @Argument(value="user", nullDefault=true) Member member, @Argument(value="role", endless=true) Role role) {
		if (role.isManaged()) {
			event.replyFailure("I cannot remove managed roles").queue();
			return;
		}

		if (role.isPublicRole()) {
			event.replyFailure("I cannot remove the @everyone role").queue();
			return;
		}

		if (!event.getMember().canInteract(role)) {
			event.replyFailure("You cannot remove a role higher or equal then your top role").queue();
			return;
		}

		if (!event.getSelfMember().canInteract(role)) {
			event.replyFailure("I cannot remove a role higher or equal then my top role").queue();
			return;
		}

		Member effectiveMember = member == null ? event.getMember() : member;

		event.getGuild().removeRoleFromMember(effectiveMember, role)
			.flatMap($ -> event.replySuccess(role.getAsMention() + " has been removed from " + effectiveMember.getAsMention()))
			.queue();
	}

}
