package com.sx4.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

import com.jockie.bot.core.category.impl.CategoryImpl;
import com.jockie.bot.core.command.ICommand;
import com.sx4.core.Sx4Bot;
import com.sx4.core.Sx4Command;
import com.sx4.settings.Settings;
import com.sx4.utils.PagedUtils.PagedResult;

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
		embed.appendDescription("Usage: " + usage + "\n");
		if (command.getAliases().size() != 0) {
			embed.appendDescription("Command aliases: " + String.join(", ", command.getAliases()) + "\n");
		}
		
		if (command.getOptions().size() != 0) {
			String[] optionNames = new String[command.getOptions().size()];
			for (int i = 0; i < command.getOptions().size(); i++) {
				optionNames[i] = command.getOptions().get(i).getName();
			}
			embed.appendDescription("Command options: " + String.join(", ", optionNames) + "\n");
		}
		
		if (!command.isDeveloperCommand()) {
			if (command.getAuthorDiscordPermissions().size() != 0) {
				List<String> permissionNames = new ArrayList<>();
				for (Permission permission : command.getAuthorDiscordPermissions()) {
					permissionNames.add(permission.getName());
				}
				
				embed.appendDescription("Required permissions: " + GeneralUtils.joinGrammatical(permissionNames) + "\n");
			}
		} else {
			embed.appendDescription("Required permissions: Developer\n");
		}
		
		embed.appendDescription("Command description: " + (command.getDescription() == null || command.getDescription().equals("")  ? "None" : command.getDescription()) + "\n" + (command.getExample() == null || command.getExample().equals("") ? "" : command.getExample()));
		String[] subCommandNames = null;
		if (!command.getSubCommands().isEmpty()) {
			subCommandNames = new String[command.getSubCommands().size()];
			for (int i = 0; i < command.getSubCommands().size(); i++) {
				subCommandNames[i] = command.getSubCommands().get(i).getCommand();
			}
		}
		
		embed.appendDescription("\nSub commands: " + (subCommandNames != null ? String.join(", ", subCommandNames) : "None"));
		embed.setAuthor(command.getCommandTrigger(), null, Sx4Bot.getShardManager().getShards().get(0).getSelfUser().getEffectiveAvatarUrl());
		
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
