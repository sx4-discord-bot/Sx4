package com.sx4.modules;

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
import com.sx4.categories.Categories;
import com.sx4.core.Sx4Command;
import com.sx4.settings.Settings;
import com.sx4.utils.ArgumentUtils;
import com.sx4.utils.GeneralUtils;
import com.sx4.utils.HelpUtils;
import com.sx4.utils.PagedUtils;
import com.sx4.utils.PagedUtils.PagedResult;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;

@Module
public class HelpModule {
	
	private final String defaultSponsorMessage = "This sponsor spot is up for grabs. What do you get with being the sponsor? Well you get this embed field with a direct link to your product/service and 300 characters to briefly "
			+ "explain your product/service as well as the banner spot below which is 1000x200. Statistics wise Sx4s help menu gets around 3500 impressions per month and this number will continue to increase. More "
			+ "information will be provided if you contact Shea#6653 (Easiest way to contact is joining the bots support server).";
			
	private final String defaultSponsorImage = "https://cdn.discordapp.com/attachments/344091594972069888/563072607667093504/unknown.png";

	@Command(value="help", aliases={"h", "commands", "commandlist", "command list"}, description="Lists commands on the bot and gives you info on specific commands")
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void help(CommandEvent event, @Argument(value="command | module", endless=true, nullDefault=true) String commandName, @Option(value="all") boolean all) {
		if (commandName == null) {
			if (all) {
				List<Sx4Command> allCommands = new ArrayList<>();
				for (ICommand command : event.getCommandListener().getAllCommands()) {
					if (command.isDeveloperCommand() && !event.isAuthorDeveloper()) {
						continue;
					} else {
						allCommands.add((Sx4Command) command);
					}
				}
				
				allCommands.sort((a, b) -> a.getCommandTrigger().toLowerCase().compareTo(b.getCommandTrigger().toLowerCase()));
				
				PagedResult<Sx4Command> paged = HelpUtils.getCommandPagedResult(allCommands);
				paged.setAuthor("All Commands", null, event.getAuthor().getEffectiveAvatarUrl());
				
				PagedUtils.getPagedResult(event, paged, 300, pagedReturn -> {
					event.reply(HelpUtils.getHelpMessage(pagedReturn.getObject())).queue();
				});
			} else {
				JSONObject advertisement = HelpUtils.getAdvertisement();
				String description = advertisement.get("description").equals(JSONObject.NULL) ? null : advertisement.getString("description");
				String imageUrl = advertisement.get("image").equals(JSONObject.NULL) ? null : advertisement.getString("image");
				
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
						PagedResult<Sx4Command> paged = HelpUtils.getModuleMessage(ArgumentUtils.getModule(response.getContentRaw()), event.getAuthor());
						PagedUtils.getPagedResult(event, paged, 300, pagedReturn -> {
							event.reply(HelpUtils.getHelpMessage(pagedReturn.getObject())).queue();
						});
						
						response.delete().queue(null, e -> {});
						message.delete().queue(null, e -> {});
					});
				});
			}
		} else {
			CategoryImpl module = ArgumentUtils.getModule(commandName, event.isAuthorDeveloper());
			List<Sx4Command> commands = ArgumentUtils.getCommands(commandName);
			if (commands.isEmpty() && module == null) {
				event.reply("I could not find that command/module :no_entry:").queue();
				return;
			}
			
			if (!commands.isEmpty()) {
				PagedResult<Sx4Command> paged = new PagedResult<>(commands)
						.setDeleteMessage(true)
						.setIncreasedIndex(true)
						.setAutoSelect(true)
						.setSelectableByIndex(true)
						.setAuthor(GeneralUtils.title(commands.get(0).getCommandTrigger()), null, event.getSelfUser().getEffectiveAvatarUrl())
						.setFunction(command -> "`" + command.toString() + "`");
				
				PagedUtils.getPagedResult(event, paged, 60, pagedReturn -> {
					event.reply(HelpUtils.getHelpMessage(pagedReturn.getObject())).queue();
				});
			} else if (module != null) {
				PagedResult<Sx4Command> paged = HelpUtils.getModuleMessage(module, event.getAuthor());
				PagedUtils.getPagedResult(event, paged, 300, pagedReturn -> {
					event.reply(HelpUtils.getHelpMessage(pagedReturn.getObject())).queue();
				});
			}
		}
	}
	
	@Initialize(all=true)
	public void initialize(CommandImpl command) {
		command.setCategory(Categories.HELP);
	}
	
}
