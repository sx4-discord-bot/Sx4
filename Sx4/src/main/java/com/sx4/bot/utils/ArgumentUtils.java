package com.sx4.bot.utils;

import java.awt.Color;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.jockie.bot.core.category.impl.CategoryImpl;
import com.jockie.bot.core.command.ICommand;
import com.sx4.bot.categories.Categories;
import com.sx4.bot.core.Sx4Bot;
import com.sx4.bot.core.Sx4Command;

import net.dv8tion.jda.api.Region;
import net.dv8tion.jda.api.entities.Category;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.VoiceChannel;

public class ArgumentUtils {
	
	public static final Pattern ID_REGEX = Pattern.compile("(^\\d+$)");
	public static final Pattern BIG_NAME_REGEX = Pattern.compile("(.{1,100})");
	public static final Pattern USER_TAG_REGEX = Pattern.compile("(.{2,32})#(\\d{4})");
	public static final Pattern SMALL_NAME_REGEX = Pattern.compile("(.{1,32})");
	public static final Pattern USER_MENTION_REGEX = Pattern.compile("<@(?:!|)(\\d+)>");
	public static final Pattern CHANNEL_MENTION_REGEX = Pattern.compile("<#(\\d+)>");
	public static final Pattern EMOTE_REGEX = Pattern.compile("<(?:a|):(.{2,32}):(\\d+)>");
	public static final Pattern ROLE_MENTION_REGEX = Pattern.compile("<@&(\\d+)>");
	public static final Pattern HEX_REGEX = Pattern.compile("(?:#|)([A-Fa-f|\\d]{6})");
	public static final Pattern RGB_REGEX = Pattern.compile("(?:\\(|)(\\d{1,3})(?: |,|, )(\\d{1,3})(?: |,|, )(\\d{1,3})(?:\\)|)");
	public static final Pattern LIST_OF_NUMBERS_REGEX = Pattern.compile("(\\d+)(?: |, |,|)");
	public static final Pattern RANGE_OF_NUMBERS_REGEX = Pattern.compile("(\\d+)-(\\d+)(?: |,|, |)");
	
	private static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
		Set<Object> seen = ConcurrentHashMap.newKeySet();
		return t -> seen.add(keyExtractor.apply(t));
	}
	
	public static List<Member> getAllUniqueMembers() {
		return Sx4Bot.getShardManager().getGuilds().stream()
			.map(guild -> guild.getMembers())
			.flatMap(List::stream)
			.filter(ArgumentUtils.distinctByKey(member -> member.getUser().getIdLong()))
			.collect(Collectors.toList());
	}

	public static List<Member> getAllMembers() {
		List<Member> members = new ArrayList<Member>();
		for (Guild guild : Sx4Bot.getShardManager().getGuilds()) {
			members.addAll(guild.getMembers());
		}
		
		return members;
	}
	
	public static Month getMonthValue(String monthArgument) {
		Month monthValue;
		try {
			int monthNumber = Integer.parseInt(monthArgument);
			monthValue = Month.values()[monthNumber - 1];
		} catch(IndexOutOfBoundsException i) {
			throw new IllegalArgumentException("The month value has to be between 1 and 12 :no_entry:");
		} catch(NumberFormatException e) {
			try {
				monthValue = Month.valueOf(monthArgument.toUpperCase());
			} catch(IllegalArgumentException ex) {
				throw new IllegalArgumentException("I could not find that month :no_entry:");
			}
		}
		
		return monthValue;
	}
	
	public static List<Integer> getRange(String rangeArgument) {
		Matcher listOfNumbers = LIST_OF_NUMBERS_REGEX.matcher(rangeArgument);
		Matcher rangeOfNumbers = RANGE_OF_NUMBERS_REGEX.matcher(rangeArgument);
		
		List<Integer> numbers = new ArrayList<>();
		while (rangeOfNumbers.find()) {
			int rangeOne = Integer.parseInt(rangeOfNumbers.group(1));
			int rangeTwo = Integer.parseInt(rangeOfNumbers.group(2));
			if (rangeTwo < rangeOne) {
				throw new NumberFormatException("In one of the ranges, range one is larger than range two");
			}
			
			for (int i = rangeOne; i < rangeTwo + 1; i++) {
				if (!numbers.contains(i)) {
					numbers.add(i);
				}
			}
		}
		
		if (numbers.isEmpty()) {
			while (listOfNumbers.find()) {
				int number = Integer.parseInt(listOfNumbers.group(1));
				if (!numbers.contains(number)) {
					numbers.add(number);
				}
			}

			return numbers;
		} else {
			return numbers;
		}
	}
	
	public static Color getColourFromString(String colourString) {
		Matcher RGBMatch = RGB_REGEX.matcher(colourString);
		Matcher hexMatch = HEX_REGEX.matcher(colourString);
		
		if (hexMatch.matches()) {
			try {
				return Color.decode("#" + hexMatch.group(1));
			} catch(NumberFormatException e) {
				return null;
			}
		} else if (RGBMatch.matches()) {
			int red = Integer.parseInt(RGBMatch.group(1));
			int green = Integer.parseInt(RGBMatch.group(2));
			int blue = Integer.parseInt(RGBMatch.group(3));
			try {
				return new Color(red, green, blue);
			} catch(IllegalArgumentException e) {
				return null;
			}
		} else {
			return null;
		}
	}
	
	public static Guild getGuild(String guildArgument) {
		guildArgument = guildArgument.toLowerCase();
		
		Matcher guildId = ID_REGEX.matcher(guildArgument);
		Matcher guildName = BIG_NAME_REGEX.matcher(guildArgument);
		
		if (guildId.matches()) {
			return Sx4Bot.getShardManager().getGuildById(guildId.group(1));
		} else if (guildName.matches()) {
			List<Guild> guilds = Sx4Bot.getShardManager().getGuilds();
			for (Guild guild : guilds) {
				if (guild.getName().toLowerCase().equals(guildArgument)) {
					return guild;
				}
			}
			
			for (Guild guild : guilds) {
				if (guild.getName().toLowerCase().startsWith(guildArgument)) {
					return guild;
				}
			}
			
			for (Guild guild : guilds) {
				if (guild.getName().toLowerCase().contains(guildArgument)) {
					return guild;
				}
			}
		}
		
		return null;
	}
	
	public static Region getGuildRegion(String region) {
		region = region.toLowerCase();
		for (Region regionObject : Region.values()) {
			if (regionObject.getName().toLowerCase().equals(region)) {
				return regionObject;
			}
		}
		
		return null;
	}
	
	public static CategoryImpl getModule(String module, boolean caseSensitive, boolean includeHidden) {
		module = caseSensitive ? module : module.toLowerCase();
		for (CategoryImpl category : includeHidden ? Categories.ALL : Categories.ALL_PUBLIC) {
			String categoryName = caseSensitive ? category.getName() : category.getName().toLowerCase();
			if (categoryName.equals(module)) {
				return category;
			}
		}
		
		return null;
	}
	
	public static CategoryImpl getModule(String module) {
		return ArgumentUtils.getModule(module, false, false);
	}
	
	public static CategoryImpl getModule(String module, boolean caseSensitive) {
		return ArgumentUtils.getModule(module, caseSensitive, false);
	}
	
	public static Sx4Command getCommand(String command) {
		return ArgumentUtils.getCommand(command, false);
	}
	
	public static Sx4Command getCommand(String command, boolean caseSensitive) {
		return ArgumentUtils.getCommand(command, caseSensitive, false, false);
	}
	
	public static Sx4Command getCommand(String command, boolean caseSensitive, boolean includeDeveloper, boolean includeHidden) {
		List<Sx4Command> commands = ArgumentUtils.getCommands(command, caseSensitive, includeDeveloper, includeHidden);
		if (commands.isEmpty()) {
			return null;
		} else {
			return commands.get(0);
		}
	}
	
	public static List<Sx4Command> getCommands(String commandName) {
		return ArgumentUtils.getCommands(commandName, false, false, false);
	}
	
	public static List<Sx4Command> getCommands(String commandName, boolean caseSensitive) {
		return ArgumentUtils.getCommands(commandName, caseSensitive, false, false);
	}
	
	public static List<Sx4Command> getCommands(String commandName, boolean caseSensitive, boolean includeDeveloper, boolean includeHidden) {
		commandName = caseSensitive ? commandName.trim() : commandName.toLowerCase().trim();
		
		List<Sx4Command> commands = new ArrayList<>();
		for (ICommand commandObject : Sx4Bot.getCommandListener().getAllCommands(includeDeveloper, includeHidden)) {
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
	
	public static Role getRole(Guild guild, String role) {
		role = role.toLowerCase().trim();
		
		Matcher roleMention = ROLE_MENTION_REGEX.matcher(role);
		Matcher roleName = BIG_NAME_REGEX.matcher(role);
		Matcher roleId = ID_REGEX.matcher(role);
		
		Role roleObject;
		if (roleId.matches()) {
			try {
				return guild.getRoleById(roleId.group(1));
			} catch(NumberFormatException e) {
				return null;
			}
		} else if (roleMention.matches()) {
			try { 
				return guild.getRoleById(roleMention.group(1));
			} catch(NumberFormatException e) {
				return null;
			}
		} else if (roleName.matches()) {
			roleObject = guild.getRolesByName(role, true).stream().findFirst().orElse(null);
			if (roleObject == null) {
				for (Role guildRole : guild.getRoles()) {
					if (guildRole.getName().toLowerCase().startsWith(role)) {
						return guildRole;
					}
				}
				
				for (Role guildRole : guild.getRoles()) {
					if (guildRole.getName().toLowerCase().contains(role)) {
						return guildRole;
					}
				}
				
				return null;
			} else {
				return roleObject;
			}
		} else {
			return null;
		}
	}
	
	public static Emote getEmote(Guild guild, String emote) {
		emote = emote.trim();
		
		Matcher emoteMention = EMOTE_REGEX.matcher(emote);
		Matcher emoteId = ID_REGEX.matcher(emote);
		Matcher emoteName = SMALL_NAME_REGEX.matcher(emote);
		
		Emote emoteObject;
		if (emoteMention.matches()) {
			try {
				emoteObject = guild.getEmoteById(emoteMention.group(2));
				if (emoteObject == null) {
					return Sx4Bot.getShardManager().getEmoteById(emoteMention.group(2));
				} else {
					return emoteObject;
				}
			} catch(NumberFormatException e) {
				return null;
			}
		} else if (emoteId.matches()) {
			try {
				emoteObject = guild.getEmoteById(emote);
				if (emoteObject == null) {
					return Sx4Bot.getShardManager().getEmoteById(emote);
				} else {
					return emoteObject;
				}
			} catch(NumberFormatException e) {
				return null;
			}
		} else if (emoteName.matches()) {
			emoteObject = guild.getEmotesByName(emote, true).stream().findFirst().orElse(null);
			if (emoteObject == null) {
				return Sx4Bot.getShardManager().getEmotesByName(emote, true).stream().findFirst().orElse(null);
			} else {
				return emoteObject;
			}
		} else {
			return null;
		}
	}
	
	public static Category getCategory(Guild guild, String category) {
		category = category.toLowerCase().trim();
		if (ID_REGEX.matcher(category).matches()) {
			try {
				return guild.getCategoryById(category);
			} catch (NumberFormatException e) {
				return null;
			}
		} else if (BIG_NAME_REGEX.matcher(category).matches()) {
			Category categoryMatch = guild.getCategoriesByName(category, true).stream().findFirst().orElse(null);
			if (categoryMatch == null) {
				for (Category channel : guild.getCategories()) {
					if (channel.getName().toLowerCase().startsWith(category)) {
						return channel;
					}
				} 
				
				for (Category channel : guild.getCategories()) {
					if (channel.getName().toLowerCase().contains(category)) {
						return channel;
					}
				}
			} else {
				return categoryMatch;
			}		
		}
		
		return null;
	}
	
	public static GuildChannel getGuildChannel(Guild guild, String channelArgument) {
		channelArgument = channelArgument.toLowerCase().trim();
		
		Matcher channelId = ID_REGEX.matcher(channelArgument);
		Matcher channelName = BIG_NAME_REGEX.matcher(channelArgument);
		Matcher channelMention = CHANNEL_MENTION_REGEX.matcher(channelArgument);
		
		if (channelId.matches()) {
			try {
				return guild.getGuildChannelById(channelArgument);
			} catch(NumberFormatException e) {
				return null;
			}
		} else if (channelMention.matches()) {
			try {
				return guild.getGuildChannelById(channelMention.group(1));
			} catch(NumberFormatException e) {
				return null;
			}
		} else if (channelName.matches()) {
			List<GuildChannel> guildChannels = new ArrayList<>(guild.getTextChannels());
			guildChannels.addAll(guild.getStoreChannels());
			guildChannels.addAll(guild.getCategories());
			guildChannels.addAll(guild.getVoiceChannels());
			
			for (GuildChannel channel : guildChannels) {
				if (channel.getName().toLowerCase().equals(channelArgument)) {
					return channel;
				}
			}
			
			for (GuildChannel channel : guildChannels) {
				if (channel.getName().toLowerCase().startsWith(channelArgument)) {
					return channel;
				}
			} 
			
			for (GuildChannel channel : guildChannels) {
				if (channel.getName().toLowerCase().contains(channelArgument)) {
					return channel;
				}
			}
		}
		
		return null;
	}
	
	public static TextChannel getTextChannel(Guild guild, String textChannel) {
		textChannel = textChannel.toLowerCase().trim();
		
		Matcher mention = CHANNEL_MENTION_REGEX.matcher(textChannel);
		
		if (ID_REGEX.matcher(textChannel).matches()) {
			try {
				return guild.getTextChannelById(textChannel);
			} catch(NumberFormatException e) {
				return null;
			}
		} else if (mention.matches()) {
			try {
				return guild.getTextChannelById(mention.group(1));
			} catch(NumberFormatException e) {
				return null;
			}
		} else if (BIG_NAME_REGEX.matcher(textChannel).matches()) {
			TextChannel channelByName = guild.getTextChannelsByName(textChannel, true).stream().findFirst().orElse(null);
			if (channelByName == null) {
				for (TextChannel channel : guild.getTextChannels()) {
					if (channel.getName().toLowerCase().startsWith(textChannel)) {
						return channel;
					}
				} 
				
				for (TextChannel channel : guild.getTextChannels()) {
					if (channel.getName().toLowerCase().contains(textChannel)) {
						return channel;
					}
				}
			} else {
				return channelByName;
			}		
		}
		
		return null;
	}
	
	private static Member getMemberInfoFromId(Guild guild, String id) {
		try {
			Member guildMember = guild.getMemberById(id);
			if (guildMember == null)  {
				for (Member member : getAllMembers()) {
					if (member.getUser().getId().equals(id)) {
						return member;
					}
				}
			} else {
				return guildMember;
			}
		} catch(NumberFormatException e) {
			return null;
		}
		
		return null;
	}
	
	public static User getUser(String user) {
		Matcher mention = USER_MENTION_REGEX.matcher(user);
		Matcher nameTag = USER_TAG_REGEX.matcher(user);
		Matcher userName = SMALL_NAME_REGEX.matcher(user);
		if (ID_REGEX.matcher(user).matches()) {
			try { 
				return Sx4Bot.getShardManager().getUserById(user);
			} catch(NumberFormatException e) {
				return null;
			}
		} else if (mention.matches()) {
			try {
				return Sx4Bot.getShardManager().getUserById(mention.group(1));
			} catch(NumberFormatException e) {
				return null;
			}
		} else if (nameTag.matches()) {
			String name = nameTag.group(1);
			String discriminator = nameTag.group(2);
			
			for (User u : Sx4Bot.getShardManager().getUsers()) {
				if (u.getName().toLowerCase().equals(name.toLowerCase()) && u.getDiscriminator().equals(discriminator)) {
					return u;
				}
			}
		} else if (userName.matches()) {
			List<User> users = Sx4Bot.getShardManager().getUsers();
			for (User u : users) {
				if (u.getName().toLowerCase().equals(user.toLowerCase())) {
					return u;
				}
			}

			for (User u : users) {
				if (u.getName().toLowerCase().startsWith(user.toLowerCase())) {
					return u;
				}
			}
				
			for (User u : users) {
				if (u.getName().toLowerCase().contains(user.toLowerCase())) {
					return u;
				}
			}
		}
		
		return null;
	}
	
	public static Member getMember(Guild guild, String user) {
		user = user.toLowerCase().trim();
		
		Matcher mention = USER_MENTION_REGEX.matcher(user);
		Matcher nameTag = USER_TAG_REGEX.matcher(user);
		Matcher userName = SMALL_NAME_REGEX.matcher(user);
		if (ID_REGEX.matcher(user).matches()) {
			try { 
				return guild.getMemberById(user);
			} catch(NumberFormatException e) {
				return null;
			}
		} else if (mention.matches()) {
			try {
				return guild.getMemberById(mention.group(1));
			} catch(NumberFormatException e) {
				return null;
			}
		} else if (nameTag.matches()) {
			String name = nameTag.group(1);
			String discriminator = nameTag.group(2);
			
			return guild.getMembersByName(name, true).stream().filter(it -> it.getUser().getDiscriminator().equals(discriminator)).findFirst().orElse(null);
		} else if (userName.matches()) {
			Member memberEffectiveName = guild.getMembersByEffectiveName(user, true).stream().findFirst().orElse(null);
			if (memberEffectiveName == null) {
				Member memberName = guild.getMembersByName(user, true).stream().findFirst().orElse(null);
				if (memberName == null) {	
					
					for (Member member : guild.getMembers()) {
						if (member.getEffectiveName().toLowerCase().startsWith(user)) {
							return member;
						}
					}
					
					for (Member member : guild.getMembers()) {
						if (member.getEffectiveName().toLowerCase().contains(user)) {
							return member;
						}
					}
					
				} else {
					return memberName;
				}
			} else {
				return memberEffectiveName;
			}
		}
		
		return null;
	}
	
	public static void getUserInfo(String user, Consumer<User> userObject) {
		Matcher mention = USER_MENTION_REGEX.matcher(user);
		if (ID_REGEX.matcher(user).matches()) {
			try {
				Sx4Bot.getShardManager().retrieveUserById(user).queue(u -> userObject.accept(u), e -> userObject.accept(null));
			} catch(NumberFormatException e) {
				userObject.accept(null);
			}
			
			return;
		} else if (mention.matches()) {
			try {
				Sx4Bot.getShardManager().retrieveUserById(mention.group(1)).queue(u -> userObject.accept(u), e -> userObject.accept(null));
			} catch(NumberFormatException e) {
				userObject.accept(null);
			}
			
			return;
		} else {
			userObject.accept(null);
		}
	}
	
	public static Member getMemberInfo(Guild guild, String user) {
		Matcher mention = USER_MENTION_REGEX.matcher(user);
		Matcher nameTag = USER_TAG_REGEX.matcher(user);
		Matcher userName = SMALL_NAME_REGEX.matcher(user);
		if (ID_REGEX.matcher(user).matches()) {
			try {
				return getMemberInfoFromId(guild, user);
			} catch(NumberFormatException e) {
				return null;
			}
		} else if (mention.matches()) {
			try {
				return getMemberInfoFromId(guild, mention.group(1));
			} catch (NumberFormatException e) {
				return null;
			}
		} else if (nameTag.matches()) {
			String name = nameTag.group(1);
			String discriminator = nameTag.group(2);
			
			Member m = guild.getMembersByName(name, true).stream().filter(it -> it.getUser().getDiscriminator().equals(discriminator)).findFirst().orElse(null);
			if (m == null) {
				for (Member member : getAllMembers()) {
					if (member.getUser().getName().toLowerCase().equals(name.toLowerCase()) && member.getUser().getDiscriminator().equals(discriminator)) {
						return member;
					}
				}
			} else {
				return m;
			}
		} else if (userName.matches()) {
			if (guild.getMembersByEffectiveName(user, true).stream().findFirst().orElse(null) == null) {
				
				if (guild.getMembersByName(user, true).stream().findFirst().orElse(null) == null) {
					
					for (Member member : getAllMembers()) {
						if (member.getUser().getName().toLowerCase().equals(user.toLowerCase())) {
							return member;
						}
					}
					
				} else {
					return guild.getMembersByName(user, true).get(0);
				}
			} else {
				return guild.getMembersByEffectiveName(user, true).get(0);
			}
		} 
		
		return null;
	}

	public static VoiceChannel getVoiceChannel(Guild guild, String voiceChannel) {	
		voiceChannel = voiceChannel.toLowerCase().trim();
	
		Matcher mentionRegex = CHANNEL_MENTION_REGEX.matcher(voiceChannel);
		
		if (ID_REGEX.matcher(voiceChannel).matches()) {
			try { 
				return guild.getVoiceChannelById(voiceChannel);
			} catch(NumberFormatException e) {
				return null;
			}
		} else if (mentionRegex.matches()) {
			try { 
				return guild.getVoiceChannelById(mentionRegex.group(1));
			} catch(NumberFormatException e) {
				return null;
			}
		} else if (BIG_NAME_REGEX.matcher(voiceChannel).matches()) {
			VoiceChannel voiceChannelName = guild.getVoiceChannelsByName(voiceChannel, true).stream().findFirst().orElse(null);
			if (voiceChannelName == null) {
				for (VoiceChannel channel : guild.getVoiceChannels()) {
					if (channel.getName().toLowerCase().startsWith(voiceChannel)) {
						return channel;
					}
				} 
				
				for (VoiceChannel channel : guild.getVoiceChannels()) {
					if (channel.getName().toLowerCase().contains(voiceChannel)) {
						return channel;
					}
				}
			} else {
				return voiceChannelName;
			}		
		}
		
		return null;
	}
	
}
