package com.sx4.bot.commands.mod;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.sx4.bot.annotations.command.AuthorPermissions;
import com.sx4.bot.annotations.command.BotPermissions;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Role;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EmoteWhitelistCommand extends Sx4Command {

	public EmoteWhitelistCommand() {
		super("emote whitelist");

		super.setDescription("Allows you to set roles which have access to the specified emote");
		super.setExamples("emote whitelist set", "emote whitelist add", "emote whitelist remove");
		super.setCategoryAll(ModuleCategory.MODERATION);
	}

	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}

	@Command(value="set", description="Sets what roles should be whitelisted to use the emote")
	@Examples({"emote whitelist set <:rain:748240799719882762> \"@Emote Role\"", "emote whitelist set rain \"Emote Role\" @Emotes"})
	@AuthorPermissions(permissions={Permission.MANAGE_EMOTES})
	@BotPermissions(permissions={Permission.MANAGE_EMOTES})
	public void set(Sx4CommandEvent event, @Argument(value="emote") Emote emote, @Argument(value="roles") Role... roles) {
		List<Role> currentRoles = emote.getRoles(), newRoles = Arrays.asList(roles);
		if (currentRoles.containsAll(newRoles)) {
			event.reply("That emote is already has all those roles whitelisted " + this.config.getFailureEmote()).queue();
			return;
		}

		emote.getManager().setRoles(new HashSet<>(newRoles))
			.flatMap($ -> event.replyFormat("The emote %s is now only whitelisted from those roles %s", emote.getAsMention(), this.config.getSuccessEmote()))
			.queue();
	}

	@Command(value="add", description="Adds a role to be whitelisted to use the emote")
	@Examples({"emote whitelist add <:rain:748240799719882762> @Emote Role", "emote whitelist add rain Emote Role"})
	@AuthorPermissions(permissions={Permission.MANAGE_EMOTES})
	@BotPermissions(permissions={Permission.MANAGE_EMOTES})
	public void add(Sx4CommandEvent event, @Argument(value="emote") Emote emote, @Argument(value="roles") Role role) {
		Set<Role> currentRoles = new HashSet<>(emote.getRoles());
		if (currentRoles.contains(role)) {
			event.reply("That emote already has that role whitelisted " + this.config.getFailureEmote()).queue();
			return;
		}

		currentRoles.add(role);

		emote.getManager().setRoles(currentRoles)
			.flatMap($ -> event.replyFormat("The emote %s is now whitelisted from that role %s", emote.getAsMention(), this.config.getSuccessEmote()))
			.queue();
	}

	@Command(value="remove", description="Removes a role from being whitelisted to use the emote")
	@Examples({"emote whitelist remove <:rain:748240799719882762> @Emote Role", "emote whitelist remove rain Emote Role"})
	@AuthorPermissions(permissions={Permission.MANAGE_EMOTES})
	@BotPermissions(permissions={Permission.MANAGE_EMOTES})
	public void remove(Sx4CommandEvent event, @Argument(value="emote") Emote emote, @Argument(value="roles") Role role) {
		Set<Role> currentRoles = new HashSet<>(emote.getRoles());
		if (!currentRoles.contains(role)) {
			event.reply("That emote does not have that role whitelisted " + this.config.getFailureEmote()).queue();
			return;
		}

		currentRoles.remove(role);

		emote.getManager().setRoles(currentRoles)
			.flatMap($ -> event.replyFormat("The emote %s is no longer whitelisted from that role %s", emote.getAsMention(), this.config.getSuccessEmote()))
			.queue();
	}

	@Command(value="reset", description="Resets the emote so everyone can use it")
	@Examples({"emote whitelist reset <:rain:748240799719882762>", "emote whitelist reset rain"})
	@AuthorPermissions(permissions={Permission.MANAGE_EMOTES})
	@BotPermissions(permissions={Permission.MANAGE_EMOTES})
	public void remove(Sx4CommandEvent event, @Argument(value="emote") Emote emote) {
		emote.getManager().setRoles(null)
			.flatMap($ -> event.replyFormat("The emote %s no longer has any whitelisted roles %s", emote.getAsMention(), this.config.getSuccessEmote()))
			.queue();
	}

}
