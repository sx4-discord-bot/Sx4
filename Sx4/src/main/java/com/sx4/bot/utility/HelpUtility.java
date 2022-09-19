package com.sx4.bot.utility;

import com.jockie.bot.core.command.ICommand;
import com.jockie.bot.core.command.impl.DummyCommand;
import com.jockie.bot.core.option.IOption;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.paged.PagedResult.SelectType;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import java.util.Formatter;
import java.util.List;
import java.util.stream.Collectors;

public class HelpUtility {

	public static MessageCreateData getHelpMessage(ICommand initialCommand, boolean embed) {
		MessageCreateBuilder builder = new MessageCreateBuilder();
		
		Sx4Command command = initialCommand instanceof DummyCommand ? (Sx4Command) ((DummyCommand) initialCommand).getActualCommand() : (Sx4Command) initialCommand;
		String usage = command.getSubCommands().isEmpty() ? command.getUsage() : command.getUsage().trim().equals(command.getCommandTrigger()) ? command.getUsage() + " <sub command>" : command.getUsage() + " | <sub command>";
		
		StringBuilder options = new StringBuilder();
		for (int i = 0; i < command.getOptions().size(); i++) {
			IOption<?> option = command.getOptions().get(i);
			
			options.append("`").append(option.getName()).append(option.getType() != boolean.class ? "=<value>" : "").append("` - ").append(option.getDescription()).append(i == command.getOptions().size() - 1 ? "" : "\n");
		}
		
		if (embed) {
			EmbedBuilder embedBuilder = new EmbedBuilder();
			embedBuilder.setTitle(command.getCommandTrigger());
			embedBuilder.addField("Description", command.getDescription(), false);
			embedBuilder.addField("Usage", usage, false);
			
			if (!command.getOptions().isEmpty()) {
				embedBuilder.addField("Options", options.toString(), false);
			}
			
			if (command.getExamples().length != 0) {
				embedBuilder.addField("Examples", "`" + String.join("`\n`", command.getExamples()) + "`", false);
			}

			if (!command.getAuthorDiscordPermissions().isEmpty()) {
				embedBuilder.addField("Required Permissions", command.getAuthorDiscordPermissions().stream().map(Permission::getName).collect(Collectors.joining(", ")), false);
			}
			
			if (!command.getAliases().isEmpty()) {
				embedBuilder.addField("Aliases", String.join(", ", command.getAliases()), false);
			}
			
			if (command.getRedirects().length != 0) {
				embedBuilder.addField("Redirects", String.join(", ", command.getRedirects()), false);
			}
			
			if (!command.getSubCommands().isEmpty()) {
				embedBuilder.addField("Sub Commands", command.getSubCommands().stream().map(ICommand::getCommand).collect(Collectors.joining(", ")), false);
			}

			if (command.isPremiumCommand()) {
				embedBuilder.setFooter("Premium Command ⭐");
			}
	
			return builder.setEmbeds(embedBuilder.build()).build();
		} else {
			String placeHolder = "%s:\n%s\n\n";
			
			Formatter formatter = new Formatter();
			formatter.format(">>> **" + command.getCommandTrigger() + "**\n\n");
			formatter.format(placeHolder, "Description", command.getDescription());
			formatter.format(placeHolder, "Usage", usage);
			
			if (!command.getOptions().isEmpty()) {
				formatter.format(placeHolder, "Options", options);
			}
			
			if (command.getExamples().length != 0) {
				formatter.format(placeHolder, "Examples", "`" + String.join("`\n`", command.getExamples()) + "`");
			}

			if (!command.getAuthorDiscordPermissions().isEmpty()) {
				formatter.format(placeHolder, "Required Permissions", command.getAuthorDiscordPermissions().stream().map(Permission::getName).collect(Collectors.joining(", ")));
			}
			
			if (!command.getAliases().isEmpty()) {
				formatter.format(placeHolder, "Aliases", String.join(", ", command.getAliases()));
			}
			
			if (command.getRedirects().length != 0) {
				formatter.format(placeHolder, "Redirects", String.join(", ", command.getRedirects()));
			}
			
			if (!command.getSubCommands().isEmpty()) {
				formatter.format(placeHolder, "Required Permissions", command.getSubCommands().stream().map(ICommand::getCommand).collect(Collectors.joining(", ")));
			}
			
			MessageCreateData message = builder.setContent(formatter.toString()).build();
			
			formatter.close();
			
			return message;
		}
	}
	
	public static PagedResult<Sx4Command> getCommandsPaged(Sx4 bot, List<Sx4Command> commands) {
		return new PagedResult<>(bot, commands)
			.setAutoSelect(true)
			.setPerPage(15)
			.setDisplayFunction(command -> "`" + command.getCommandTrigger() + "` - " + command.getDescription())
			.setSelectablePredicate((content, command) -> command.getCommandTrigger().equals(content))
			.setSelect(SelectType.OBJECT)
			.setIndexed(false)
			.setAutoSelect(false);
	}
	
}
