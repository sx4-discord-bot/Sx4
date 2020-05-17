package com.sx4.bot.commands.roles;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.command.Command.AuthorPermissions;
import com.jockie.bot.core.command.Command.BotPermissions;
import com.jockie.bot.core.command.impl.CommandEvent;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.utility.HelpUtility;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction.ReactionEmote;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.requests.RestAction;

public class ReactionRole extends Sx4Command {
	
	public ReactionRole() {
		super("reaction role");
		
		super.setDescription("Set up a reaction role so users can simply react to an emote and get a specified role");
		super.setAliases("reactionrole");
		super.setExamples("reaction role add", "reaction role remove");
	}
	
	public void onCommand(CommandEvent event) {
		event.reply(HelpUtility.getHelpMessage(event.getCommand(), event.getSelfMember().hasPermission(event.getTextChannel(), Permission.MESSAGE_EMBED_LINKS))).queue();
	}
	
	@Command(value="add", description="Adds a role to be given when a user reacts to the specified emote")
	@Examples({"reaction role add 643945552865919002 🐝 @Yellow", "reaction role add https://discordapp.com/channels/330399610273136641/678274453158887446/680051429460803622 :doggo: Dog person"})
	@AuthorPermissions({Permission.MANAGE_ROLES})
	@BotPermissions({Permission.MESSAGE_ADD_REACTION, Permission.MESSAGE_HISTORY})
	public void add(Sx4CommandEvent event, @Argument(value="message id") RestAction<Message> action, @Argument(value="emote") ReactionEmote emoji, @Argument(value="role", endless=true) Role role) {
		if (role.isManaged()) {
			event.reply("I cannot give a role which is managed :no_entry:").queue();
			return;
		}
		
		if (role.isPublicRole()) {
			event.reply("I cannot give users the `@everyone` role :no_entry:").queue();
			return;
		}
		
		if (!event.getMember().canInteract(role)) {
			event.reply("You cannot add a role which is higher or equal than my your role :no_entry:").queue();
			return;
		}
		
		if (!event.getSelfMember().canInteract(role)) {
			event.reply("I cannot give a role which is higher or equal than my top role :no_entry:").queue();
			return;
		}
		
	}
	
}
