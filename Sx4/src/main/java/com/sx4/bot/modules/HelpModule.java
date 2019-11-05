package com.sx4.bot.modules;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.category.impl.CategoryImpl;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.command.Command.BotPermissions;
import com.jockie.bot.core.command.ICommand;
import com.jockie.bot.core.command.Initialize;
import com.jockie.bot.core.command.impl.CommandEvent;
import com.jockie.bot.core.command.impl.CommandImpl;
import com.jockie.bot.core.module.Module;
import com.jockie.bot.core.option.Option;
import com.sx4.bot.categories.Categories;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.settings.Settings;
import com.sx4.bot.utils.ArgumentUtils;
import com.sx4.bot.utils.GeneralUtils;
import com.sx4.bot.utils.HelpUtils;
import com.sx4.bot.utils.PagedUtils;
import com.sx4.bot.utils.PagedUtils.PagedResult;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;

@Module
public class HelpModule {
	
	private final String defaultSponsorMessage = "This sponsor spot is up for grabs. What do you get with being the sponsor? Well you get this embed field with a direct link to your product/service and 300 characters to briefly "
			+ "explain your product/service as well as the banner spot below which is 1000x200. Statistics wise Sx4s help menu gets around 3500 impressions per month and this number will continue to increase. More "
			+ "information will be provided if you contact Shea#6653 (Easiest way to contact is joining the bots support server).";
			
	private final String defaultSponsorImage = "https://cdn.discordapp.com/attachments/344091594972069888/634867715860987914/Sponsor.png";

	@Command(value="help", aliases={"h", "commands", "commandlist", "command list"}, description="Lists commands on the bot and gives you info on specific commands")
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void help(CommandEvent event, @Argument(value="command | module", endless=true, nullDefault=true) String commandName, @Option(value="all", aliases={"a"}) boolean all, @Option(value="module") boolean moduleChoice, @Option(value="command") boolean commandChoice) {
		if (commandName == null) {
			if (all) {
				List<Sx4Command> allCommands = new ArrayList<>();
				for (ICommand command : event.getCommandListener().getAllCommands(true, true)) {
					if ((command.isDeveloperCommand() || command.isHidden()) && !event.isAuthorDeveloper()) {
						continue;
					} else {
						allCommands.add((Sx4Command) command);
					}
				}
				
				allCommands.sort((a, b) -> a.getCommandTrigger().toLowerCase().compareTo(b.getCommandTrigger().toLowerCase()));
				
				PagedResult<Sx4Command> paged = HelpUtils.getCommandPagedResult(allCommands);
				paged.setAuthor("All Commands", null, event.getAuthor().getEffectiveAvatarUrl());
				
				PagedUtils.getPagedResult(event, paged, 300, pagedReturn -> {
					event.reply(HelpUtils.getHelpMessage(pagedReturn.getData())).queue();
				});
			} else {
				JSONObject advertisement = HelpUtils.getAdvertisement();
				String description = advertisement.isNull("description") ? null : advertisement.getString("description");
				String imageUrl = advertisement.isNull("image") ? null : advertisement.getString("image");
				
				List<String> moduleNames = new ArrayList<>();
				for (CategoryImpl category : event.isAuthorDeveloper() ? Categories.ALL : Categories.ALL_PUBLIC) {
					moduleNames.add(category.getName());
				}
				
				moduleNames.sort((a, b) -> a.compareTo(b));
				
				EmbedBuilder embed = new EmbedBuilder();
				embed.setAuthor("Help", null, event.getSelfUser().getEffectiveAvatarUrl());
				embed.setColor(Settings.EMBED_COLOUR);
				embed.setFooter(event.getPrefix() + "help <module> or respond below with a name of a module", event.getAuthor().getEffectiveAvatarUrl());
				embed.setDescription("All commands are put in a set category also known as a module, use `" + event.getPrefix() + "help <module>` on the module of your choice, The bot will then "
				+ "list all the commands in that module. If you need further help feel free to join the [support server](https://discord.gg/PqJNcfB).");
				embed.addField("Modules", "`" + String.join("`, `", moduleNames) + "`", false);
				embed.addField("Sponsor", description == null ? this.defaultSponsorMessage : description, false);
				embed.setImage(imageUrl == null ? this.defaultSponsorImage : imageUrl);
				event.reply(embed.build()).queue(message -> {
					PagedUtils.getResponse(event, 300, e -> {
						return event.getChannel().equals(e.getChannel()) && event.getAuthor().equals(e.getAuthor()) && moduleNames.contains(e.getMessage().getContentRaw());
					}, null, response -> {
						PagedResult<Sx4Command> paged = HelpUtils.getModulePagedResult(ArgumentUtils.getModule(response.getContentRaw(), false, event.isAuthorDeveloper()), event.getAuthor());
						PagedUtils.getPagedResult(event, paged, 300, pagedReturn -> {
							event.reply(HelpUtils.getHelpMessage(pagedReturn.getData())).queue();
						});
						
						response.delete().queue(null, e -> {});
						message.delete().queue(null, e -> {});
					});
				});
			}
		} else {
			CategoryImpl module = ArgumentUtils.getModule(commandName, false, event.isAuthorDeveloper());
			List<Sx4Command> commands = ArgumentUtils.getCommands(commandName, false, event.isAuthorDeveloper(), event.isAuthorDeveloper());
			if (module == null && moduleChoice) {
				event.reply("I could not find that module :no_entry:").queue();
				return;
			} else if (commands.isEmpty() && commandChoice) {
				event.reply("I could not find that command :no_entry:").queue();
				return;
			} else if (commands.isEmpty() && module == null) {
				event.reply("I could not find that command/module :no_entry:").queue();
				return;
			}
			
			if (moduleChoice) {
				PagedResult<Sx4Command> paged = HelpUtils.getModulePagedResult(module, event.getAuthor());
				PagedUtils.getPagedResult(event, paged, 300, pagedReturn -> {
					event.reply(HelpUtils.getHelpMessage(pagedReturn.getData())).queue();
				});
			} else if (commandChoice) {
				if (commands.size() > 1) {
					PagedResult<Sx4Command> paged = HelpUtils.getCommandPagedResult(commands);
					paged.setAuthor(GeneralUtils.title(commands.get(0).getCommandTrigger()), null, event.getSelfUser().getEffectiveAvatarUrl());
					PagedUtils.getPagedResult(event, paged, 60, pagedReturn -> {
						event.reply(HelpUtils.getHelpMessage(pagedReturn.getData())).queue();
					});
				} else {
					event.reply(HelpUtils.getHelpMessage(commands.get(0))).queue();
				}
			} else {
				if (module != null) {
					PagedResult<Sx4Command> paged = HelpUtils.getModulePagedResult(module, event.getAuthor());
					PagedUtils.getPagedResult(event, paged, 300, pagedReturn -> {
						event.reply(HelpUtils.getHelpMessage(pagedReturn.getData())).queue();
					});
				} else if (!commands.isEmpty()) {
					if (commands.size() > 1) {
						PagedResult<Sx4Command> paged = HelpUtils.getCommandPagedResult(commands);
						paged.setAuthor(GeneralUtils.title(commands.get(0).getCommandTrigger()), null, event.getSelfUser().getEffectiveAvatarUrl());
						PagedUtils.getPagedResult(event, paged, 60, pagedReturn -> {
							event.reply(HelpUtils.getHelpMessage(pagedReturn.getData())).queue();
						});
					} else {
						event.reply(HelpUtils.getHelpMessage(commands.get(0))).queue();
					}
				}
			}
		}
	}
	
	@Initialize(all=true, subCommands=true, recursive=true)
	public void initialize(CommandImpl command) {
		command.setCategory(Categories.HELP);
	}
	
}
