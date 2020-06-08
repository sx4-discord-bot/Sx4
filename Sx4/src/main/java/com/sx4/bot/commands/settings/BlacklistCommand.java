package com.sx4.bot.commands.settings;

import java.util.List;
import java.util.stream.Collectors;

import org.bson.Document;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.command.Command.AuthorPermissions;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.IPermissionHolder;
import net.dv8tion.jda.api.entities.TextChannel;

public class BlacklistCommand extends Sx4Command {

	public BlacklistCommand() {
		super("blacklist");
		
		super.setDescription("Blacklist roles/users from being able to use specific commands/modules in channels");
		super.setExamples("blacklist add", "blacklist remove", "blacklist info");
	}
	
	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}
	
	@Command(value="add", description="Add a role/user to be blacklisted from a specified command/module in a channel")
	@Examples({"blacklist add #general @Shea#6653 fish", "blacklist add #bots @Members ban"})
	@AuthorPermissions({Permission.MANAGE_SERVER})
	public void add(Sx4CommandEvent event, @Argument(value="channel") TextChannel channel, @Argument(value="user | role") IPermissionHolder holder, @Argument(value="command | module", endless=true) List<Sx4Command> commands) {
		List<Document> data = commands.stream().map(command -> {
			return new Document("id", command.getCommandTrigger())
				.append("blacklisted", true);
		}).collect(Collectors.toList());
		
		
	}
	
}
