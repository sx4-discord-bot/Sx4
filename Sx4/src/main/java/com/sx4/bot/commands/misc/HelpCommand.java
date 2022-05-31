package com.sx4.bot.commands.misc;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Category;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.paged.PagedResult.SelectType;
import com.sx4.bot.utility.HelpUtility;
import com.sx4.bot.utility.SearchUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class HelpCommand extends Sx4Command {

	public HelpCommand() {
		super("help", 103);
		
		super.setDescription("Lists commands on the bot and gives you info on specific commands");
		super.setAliases("h", "commands", "cmds");
		super.setExamples("help", "help logs", "help all", "help welcomer");
		super.setPrivateTriggerable(true);
		super.setCategoryAll(ModuleCategory.MISC);
	}
	
	public void onCommand(Sx4CommandEvent event, @Argument(value="command | module", endless=true, nullDefault=true) String commandName) {
		boolean embed = !event.isFromGuild() || event.getGuild().getSelfMember().hasPermission(event.getGuildChannel(), Permission.MESSAGE_EMBED_LINKS);
		if (commandName == null) {
			List<Sx4Category> categories = Arrays.stream(ModuleCategory.ALL_ARRAY)
				.filter(category -> !category.getCommands(event.isAuthorDeveloper()).isEmpty())
				.collect(Collectors.toList());
			
			PagedResult<Sx4Category> paged = new PagedResult<>(event.getBot(), categories)
				.setPerPage(categories.size())
				.setSelect(SelectType.OBJECT)
				.setSelectablePredicate((content, category) -> category.getName().equalsIgnoreCase(content) || Arrays.stream(category.getAliases()).anyMatch(content::equalsIgnoreCase))
				.setCustomFunction(page -> {
					MessageBuilder builder = new MessageBuilder();
					
					EmbedBuilder embedBuilder = new EmbedBuilder();
					embedBuilder.setAuthor("Help", null, event.getSelfUser().getEffectiveAvatarUrl());
					embedBuilder.setFooter(event.getPrefix() + "help <module> or respond below with a name of a module", event.getAuthor().getEffectiveAvatarUrl());
					embedBuilder.setDescription("All commands are put in a set category also known as a module, use `" + event.getPrefix() + "help <module>` on the module of your choice, The bot will then "
					+ "list all the commands in that module. If you need further help feel free to join the [support server](https://discord.gg/PqJNcfB).");
					embedBuilder.addField("Modules", "`" + categories.stream().map(Sx4Category::getName).collect(Collectors.joining("`, `")) + "`", false);
					
					return builder.setEmbeds(embedBuilder.build());
				});
					
			paged.onSelect(select -> {
				Sx4Category category = select.getSelected();
				
				List<Sx4Command> categoryCommands = category.getCommands(event.isAuthorDeveloper()).stream()
					.map(Sx4Command.class::cast)
					.sorted(Comparator.comparing(Sx4Command::getCommandTrigger))
					.collect(Collectors.toList());
				
				PagedResult<Sx4Command> categoryPaged = HelpUtility.getCommandsPaged(event.getBot(), categoryCommands)
					.setAuthor(category.getName(), null, event.getAuthor().getEffectiveAvatarUrl());
				
				categoryPaged.onSelect(categorySelect -> event.reply(HelpUtility.getHelpMessage(categorySelect.getSelected(), embed)).queue());
				
				categoryPaged.execute(event);
			});
			
			paged.execute(event);
		} else {
			Sx4Category category = SearchUtility.getModule(commandName);
			List<Sx4Command> commands = SearchUtility.getCommands(event.getCommandListener(), commandName, event.isAuthorDeveloper());
			
			if (category != null) {
				List<Sx4Command> categoryCommands = category.getCommands(event.isAuthorDeveloper()).stream()
					.map(Sx4Command.class::cast)
					.sorted(Comparator.comparing(Sx4Command::getCommandTrigger))
					.collect(Collectors.toList());
				
				PagedResult<Sx4Command> paged = HelpUtility.getCommandsPaged(event.getBot(), categoryCommands)
					.setAuthor(category.getName(), null, event.getAuthor().getEffectiveAvatarUrl());
				
				paged.onSelect(select -> event.reply(HelpUtility.getHelpMessage(select.getSelected(), embed)).queue());
				
				paged.execute(event);
			} else if (!commands.isEmpty()) {
				PagedResult<Sx4Command> paged = new PagedResult<>(event.getBot(), commands)
					.setAuthor(commandName, null, event.getAuthor().getEffectiveAvatarUrl())
					.setAutoSelect(true)
					.setPerPage(15)
					.setSelectablePredicate((content, command) -> command.getCommandTrigger().equals(content))
					.setDisplayFunction(Sx4Command::getUsage);
				
				paged.onSelect(select -> event.reply(HelpUtility.getHelpMessage(select.getSelected(), embed)).queue());
				
				paged.execute(event);
			} else {
				event.replyFailure("I could not find that command/module").queue();
			}
		}
	}
	
}
