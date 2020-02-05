package com.sx4.bot.utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jockie.bot.core.command.ICommand;
import com.sx4.bot.category.Category;
import com.sx4.bot.core.Sx4Bot;
import com.sx4.bot.core.Sx4Category;
import com.sx4.bot.core.Sx4Command;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message.MentionType;
import net.dv8tion.jda.api.entities.User;

public class SearchUtility {
	
	private static final Pattern USER_MENTION = MentionType.USER.getPattern();
	private static final Pattern USER_TAG = Pattern.compile("(.{2,32})#(\\d{4})");
	
	private static Member findMember(List<Member> members, String query) {
		List<Member> startsWith = new ArrayList<>();
		List<Member> contains = new ArrayList<>();

		query = query.toLowerCase();
		for (Member member : members) {
		    String name = member.getUser().getName().toLowerCase();
		    if (name.equals(query)) {
		        return member;
		    }
		    
		    if (name.startsWith(query)) {
		        startsWith.add(member);
		    }
		    
		    if (name.contains(query)) {
		        contains.add(member);
		    }
		}

		if (startsWith.size() > 0) {
		    return startsWith.get(0);
		}

		if (contains.size() > 0) {
		    return contains.get(0);
		}
		
		return null;
	}
	
	public static Member getMember(Guild guild, String userArgument) {
		Matcher mentionMatch = SearchUtility.USER_MENTION.matcher(userArgument);
		Matcher tagMatch = SearchUtility.USER_TAG.matcher(userArgument);
		if (mentionMatch.matches()) {
			try {
				long id = Long.parseLong(mentionMatch.group(1));
				
				return guild.getMemberById(id);
			} catch (NumberFormatException e) {
				return null;
			}
		} else if (tagMatch.matches()) {
			String name = tagMatch.group(1);
			String discriminator = tagMatch.group(2);
			
			return guild.getMemberCache().stream()
				.filter(member -> member.getUser().getName().equalsIgnoreCase(name) && member.getUser().getDiscriminator().equals(discriminator))
				.findFirst()
				.orElse(null);
		} else if (NumberUtility.isNumberUnsigned(userArgument)) {
			try {
				long id = Long.parseLong(userArgument);
				
				return guild.getMemberById(id);
			} catch (NumberFormatException e) {
				return null;
			}
		} else {
			return SearchUtility.findMember(guild.getMembers(), userArgument);
		}
	}
	
	public static User getUser(String userArgument) {
		Matcher mentionMatch = SearchUtility.USER_MENTION.matcher(userArgument);
		Matcher tagMatch = SearchUtility.USER_TAG.matcher(userArgument);
		if (mentionMatch.matches()) {
			try {
				long id = Long.parseLong(mentionMatch.group(1));
				
				return Sx4Bot.getShardManager().getUserById(id);
			} catch (NumberFormatException e) {
				return null;
			}
		} else if (tagMatch.matches()) {
			String name = tagMatch.group(1);
			String discriminator = tagMatch.group(2);
			
			return Sx4Bot.getShardManager().getUserCache().stream()
				.filter(user -> user.getName().equalsIgnoreCase(name) && user.getDiscriminator().equals(discriminator))
				.findFirst()
				.orElse(null);
		} else if (NumberUtility.isNumberUnsigned(userArgument)) {
			try {
				long id = Long.parseLong(userArgument);
				
				return Sx4Bot.getShardManager().getUserById(id);
			} catch (NumberFormatException e) {
				return null;
			}
		} else {
			return Sx4Bot.getShardManager().getUserCache().stream()
					.filter(user -> user.getName().equalsIgnoreCase(userArgument))
					.findFirst()
					.orElse(null);
		}
	}
	
	public static void getUserRest(String userArgument, Consumer<User> userConsumer) {
		Matcher mentionMatch = SearchUtility.USER_MENTION.matcher(userArgument);
		Matcher tagMatch = SearchUtility.USER_TAG.matcher(userArgument);
		if (mentionMatch.matches()) {
			try {
				long id = Long.parseLong(mentionMatch.group(1));
				
				Sx4Bot.getShardManager().retrieveUserById(id).queue(user -> {
					userConsumer.accept(user);
				});
			} catch (NumberFormatException e) {
				userConsumer.accept(null);
			}
		} else if (tagMatch.matches()) {
			String name = tagMatch.group(1);
			String discriminator = tagMatch.group(2);
			
			userConsumer.accept(Sx4Bot.getShardManager().getUserCache().stream()
				.filter(user -> user.getName().equalsIgnoreCase(name) && user.getDiscriminator().equals(discriminator))
				.findFirst()
				.orElse(null));
		} else if (NumberUtility.isNumberUnsigned(userArgument)) {
			try {
				long id = Long.parseLong(userArgument);
				
				Sx4Bot.getShardManager().retrieveUserById(id).queue(user -> {
					userConsumer.accept(user);
				});
			} catch (NumberFormatException e) {
				userConsumer.accept(null);
			}
		} else {
			userConsumer.accept(Sx4Bot.getShardManager().getUserCache().stream()
					.filter(user -> user.getName().equalsIgnoreCase(userArgument))
					.findFirst()
					.orElse(null));
		}
	}
	
	public static Sx4Category getModule(String moduleName) {
		return Arrays.stream(Category.ALL)
				.filter(category -> category.getName().equalsIgnoreCase(moduleName) || Arrays.stream(category.getAliases()).anyMatch(moduleName::equalsIgnoreCase))
				.findFirst()
				.orElse(null);
	}
	
	public static Sx4Command getCommand(String commandName) {
		return SearchUtility.getCommand(commandName, false, true);
	}
	
	public static Sx4Command getCommand(String commandName, boolean includeDeveloper) {
		return SearchUtility.getCommand(commandName, false, includeDeveloper);
	}
	
	public static Sx4Command getCommand(String commandName, boolean caseSensitive, boolean includeDeveloper) {
		List<Sx4Command> commands = SearchUtility.getCommands(commandName, caseSensitive, includeDeveloper);
		return commands.isEmpty() ? null : commands.get(0);
	}
	
	public static List<Sx4Command> getCommands(String commandName) {
		return SearchUtility.getCommands(commandName, false, true);
	}
	
	public static List<Sx4Command> getCommands(String commandName, boolean includeDeveloper) {
		return SearchUtility.getCommands(commandName, false, includeDeveloper);
	}

	public static List<Sx4Command> getCommands(String commandName, boolean caseSensitive, boolean includeDeveloper) {
		commandName = caseSensitive ? commandName.trim() : commandName.toLowerCase().trim();
		
		List<Sx4Command> commands = new ArrayList<>();
		for (ICommand commandObject : Sx4Bot.getCommandListener().getAllCommands(includeDeveloper, false)) {
			Sx4Command command = (Sx4Command) commandObject;
			String commandTrigger = caseSensitive ? command.getCommandTrigger() : command.getCommandTrigger().toLowerCase();
			if (commandTrigger.equals(commandName)) {
				commands.add(command);
			} else {
				List<String> allAliases = new ArrayList<>();
				ICommand parent = command;
				List<String> parentAliases = new ArrayList<>(parent.getAliases());
				parentAliases.add(parent.getCommand());
				for (String alias : parentAliases) {
					allAliases.add(alias);
				}
				
				while (parent.hasParent()) {
					parent = parent.getParent();
					List<String> continuousParentAliases = new ArrayList<>(parent.getAliases());
					continuousParentAliases.add(parent.getCommand());
					for (String aliases : new ArrayList<>(allAliases)) {
						for (String alias : continuousParentAliases) {
							allAliases.remove(aliases);
							allAliases.add(alias + " " + aliases);
						}
					}
				}
				
				for (String commandAlias : allAliases) {
					commandAlias = caseSensitive ? commandAlias : commandAlias.toLowerCase();
					if (commandName.equals(commandAlias)) {
						commands.add(command);
					}
				}
			}
		}
		
		return commands;
	}
	
}
