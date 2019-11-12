package com.sx4.bot.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.json.JSONObject;

import com.jockie.bot.core.category.impl.CategoryImpl;
import com.jockie.bot.core.command.ICommand;
import com.jockie.bot.core.option.IOption;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.settings.Settings;
import com.sx4.bot.utils.PagedUtils.PagedResult;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;

public class HelpUtils {
	
	private static JSONObject advertisement = new JSONObject();
	
	public static JSONObject updateAdvertisementDescription(Object description) {
		advertisement.put("description", description);
		
		return advertisement;
	}
	
	public static JSONObject updateAdvertisementImage(Object imageUrl) {
		advertisement.put("image", imageUrl);
		
		return advertisement;
	}
	
	public static JSONObject getAdvertisement() {
		return advertisement;
	}
	
	public static void ensureAdvertisement() {
		try (FileInputStream stream = new FileInputStream(new File("advertisement.json"))) {
			advertisement = new JSONObject(new String(stream.readAllBytes()));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static MessageEmbed getHelpMessage(ICommand initialCommand) {
		Sx4Command command = (Sx4Command) initialCommand;
		String usage = command.getSubCommands().isEmpty() ? command.getUsage() : command.getUsage().trim().equals(command.getCommandTrigger()) ? command.getUsage() + " <sub command>" : command.getUsage() + " | <sub command>";
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setTitle(command.getCommandTrigger());
		embed.addField("Description", command.getDescription(), false);
		embed.addField("Usage", usage, false);
		
		StringBuilder options = new StringBuilder();
		for (IOption<?> option : command.getOptions()) {
			options.append("`" + option.getName() + (option.getType().equals(String.class) ? "=<value>" : "") + "` - " + option.getDescription() + "\n");
		}
		
		if (!command.getOptions().isEmpty()) {
			embed.addField("Options", options.toString(), false);
		}
		
		if (command.getExamples().length != 0) {
			embed.addField("Examples", "`" + String.join("`\n`", command.getExamples()) + "`", false);
		}
		
		if (command.isDeveloperCommand()) {
			embed.addField("Required Permissions", "Developer", false);
		} else if (!command.getAuthorDiscordPermissions().isEmpty()) {
			embed.addField("Required Permissions", GeneralUtils.joinGrammatical(command.getAuthorDiscordPermissions().stream().map(Permission::getName).collect(Collectors.toList())), false);
		}
		
		if (!command.getAliases().isEmpty()) {
			embed.addField("Aliases", String.join(", ", command.getAliases()), false);
		}
		
		if (!command.getSubCommands().isEmpty()) {
			embed.addField("Sub Commands", String.join(", ", command.getSubCommands().stream().map(ICommand::getCommand).collect(Collectors.toList())), false);
		}

		return embed.build();
	}
	
	public static PagedResult<Sx4Command> getCommandPagedResult(List<Sx4Command> commands) {
		return new PagedResult<>(commands)
				.setDeleteMessage(false)
				.setPerPage(15)
				.setIndexed(false)
				.setSelectableByObject(true)
				.setSelectableObject(object -> object.getCommandTrigger()) 
				.setEmbedColour(Settings.EMBED_COLOUR)
				.setFunction(commandObject -> {
					return "`" + commandObject.getCommandTrigger() + "` - " + commandObject.getDescription();
				});
	}
	
	public static PagedResult<Sx4Command> getModulePagedResult(CategoryImpl module, User author) {
		List<Sx4Command> commands = new ArrayList<>();
		for (ICommand command : module.getCommands()) {
			commands.add((Sx4Command) command);
		}
		
		commands.sort((a, b) -> a.getCommandTrigger().compareTo(b.getCommandTrigger()));
		
		PagedResult<Sx4Command> paged = HelpUtils.getCommandPagedResult(commands);
		paged.setAuthor("Commands in " + module.getName(), null, author.getEffectiveAvatarUrl());
		
		return paged;
	}
	
}
