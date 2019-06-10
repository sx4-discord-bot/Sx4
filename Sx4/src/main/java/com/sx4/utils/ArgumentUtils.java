package com.sx4.utils;

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
import com.sx4.categories.Categories;
import com.sx4.core.Sx4Bot;
import com.sx4.core.Sx4Command;

import net.dv8tion.jda.core.Region;
import net.dv8tion.jda.core.entities.Category;
import net.dv8tion.jda.core.entities.Channel;
import net.dv8tion.jda.core.entities.Emote;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Role;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.entities.VoiceChannel;

public class ArgumentUtils {
	
	public static Pattern IdRegex = Pattern.compile("(^\\d+$)");
	public static Pattern bigNameRegex = Pattern.compile("(.{1,100})");
	public static Pattern userTagRegex = Pattern.compile("(.{2,32})#(\\d{4})");
	public static Pattern smallNameRegex = Pattern.compile("(.{1,32})");
	public static Pattern userMentionRegex = Pattern.compile("<@(?:!|)(\\d+)>");
	public static Pattern channelMentionRegex = Pattern.compile("<#(\\d+)>");
	public static Pattern emoteRegex = Pattern.compile("<(?:a|):(.{2,32}):(\\d+)>");
	public static Pattern roleMentionRegex = Pattern.compile("<@&(\\d+)>");
	public static Pattern hexRegex = Pattern.compile("(?:#|)([A-Za-z|\\d]{6})");
	public static Pattern RGBRegex = Pattern.compile("(?:\\(|)(\\d{1,3})(?: |,|, )(\\d{1,3})(?: |,|, )(\\d{1,3})(?:\\)|)");
	public static Pattern listOfNumbersRegex = Pattern.compile("(\\d+)(?: |, |,|)");
	public static Pattern rangeOfNumbersRegex = Pattern.compile("(\\d+)-(\\d+)(?: |,|, |)");
	
	private static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
		Set<Object> seen = ConcurrentHashMap.newKeySet();
		return t -> seen.add(keyExtractor.apply(t));
	}

	public static List<Member> getAllUniqueMembers() {
		return Sx4Bot.getShardManager().getGuilds().stream()
			.map(guild -> guild.getMembers())
			.flatMap(List::stream)
			.filter(distinctByKey(member -> member.getUser().getIdLong()))
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
		Matcher listOfNumbers = listOfNumbersRegex.matcher(rangeArgument);
		Matcher rangeOfNumbers = rangeOfNumbersRegex.matcher(rangeArgument);
		
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
		Matcher RGBMatch = RGBRegex.matcher(colourString);
		Matcher hexMatch = hexRegex.matcher(colourString);
		
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
	
	public static Region getGuildRegion(String region) {
		region = region.toLowerCase();
		for (Region regionObject : Region.values()) {
			if (regionObject.getName().toLowerCase().equals(region)) {
				return regionObject;
			}
		}
		
		return null;
	}
	
	public static CategoryImpl getModule(String module, boolean includeHidden) {
		for (CategoryImpl category : includeHidden ? Categories.ALL : Categories.ALL_PUBLIC) {
			if (category.getName().equals(module)) {
				return category;
			}
		}
		
		return null;
	}
	
	public static CategoryImpl getModule(String module) {
		return ArgumentUtils.getModule(module, true);
	}
	
	public static Sx4Command getCommand(String command) {
		List<Sx4Command> commands = ArgumentUtils.getCommands(command);
		if (commands.isEmpty()) {
			return null;
		} else {
			return commands.get(0);
		}
	}
	
	public static List<Sx4Command> getCommands(String commandName) {
		List<Sx4Command> commands = new ArrayList<>();
		for (ICommand commandObject : Sx4Bot.getCommandListener().getAllCommands()) {
			Sx4Command command = (Sx4Command) commandObject;
			if (command.getCommandTrigger().equals(commandName)) {
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
					if (commandName.equals(commandAlias)) {
						commands.add(command);
					}
				}
			}
		}
		
		return commands;
	}
	
	public static Role getRole(Guild guild, String role) {
		Matcher roleMention = roleMentionRegex.matcher(role);
		Matcher roleName = bigNameRegex.matcher(role);
		Matcher roleId = IdRegex.matcher(role);
		
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
			roleObject = guild.getRolesByName(roleName.group(1), true).stream().findFirst().orElse(null);
			if (roleObject == null) {
				for (Role guildRole : guild.getRoles()) {
					if (guildRole.getName().toLowerCase().startsWith(roleName.group(1).toLowerCase())) {
						return guildRole;
					}
				}
				
				for (Role guildRole : guild.getRoles()) {
					if (guildRole.getName().toLowerCase().contains(roleName.group(1).toLowerCase())) {
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
		Matcher emoteMention = emoteRegex.matcher(emote);
		Matcher emoteId = IdRegex.matcher(emote);
		Matcher emoteName = smallNameRegex.matcher(emote);
		
		Emote emoteObject;
		if (emoteMention.matches()) {
			try {
				emoteObject = guild.getEmoteById(emoteMention.group(2));
				if (emoteObject == null) {
					return guild.getJDA().asBot().getShardManager().getEmoteById(emoteMention.group(2));
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
					return guild.getJDA().asBot().getShardManager().getEmoteById(emote);
				} else {
					return emoteObject;
				}
			} catch(NumberFormatException e) {
				return null;
			}
		} else if (emoteName.matches()) {
			emoteObject = guild.getEmotesByName(emote, true).stream().findFirst().orElse(null);
			if (emoteObject == null) {
				return guild.getJDA().asBot().getShardManager().getEmotesByName(emote, true).stream().findFirst().orElse(null);
			} else {
				return emoteObject;
			}
		} else {
			return null;
		}
	}
	
	public static Category getCategory(Guild guild, String category) {
		if (IdRegex.matcher(category).matches()) {
			try {
				return guild.getCategoryById(category);
			} catch (NumberFormatException e) {
				return null;
			}
		} else if (bigNameRegex.matcher(category).matches()) {
			if (guild.getCategoriesByName(category, true).stream().findFirst().orElse(null) == null) {
				for (Category channel : guild.getCategories()) {
					if (channel.getName().toLowerCase().startsWith(category.toLowerCase())) {
						return channel;
					}
				} 
				
				for (Category channel : guild.getCategories()) {
					if (channel.getName().toLowerCase().contains(category.toLowerCase())) {
						return channel;
					}
				}
			} else {
				return guild.getCategoriesByName(category, true).get(0);
			}		
		}
		
		return null;
	}
	
	public static Channel getTextChannelOrParent(Guild guild, String channelArgument) {
		Channel channel = getTextChannel(guild, channelArgument);
		if (channel == null) {
			return getCategory(guild, channelArgument);
		} else {
			return channel;
		}
	}
	
	public static TextChannel getTextChannel(Guild guild, String textChannel) {
		Matcher mention = channelMentionRegex.matcher(textChannel);
		
		if (IdRegex.matcher(textChannel).matches()) {
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
		} else if (bigNameRegex.matcher(textChannel).matches()) {
			if (guild.getVoiceChannelsByName(textChannel, true).stream().findFirst().orElse(null) == null) {
				for (TextChannel channel : guild.getTextChannels()) {
					if (channel.getName().toLowerCase().startsWith(textChannel.toLowerCase())) {
						return channel;
					}
				} 
				
				for (TextChannel channel : guild.getTextChannels()) {
					if (channel.getName().toLowerCase().contains(textChannel.toLowerCase())) {
						return channel;
					}
				}
			} else {
				return guild.getTextChannelsByName(textChannel, true).get(0);
			}		
		}
		
		return null;
	}
	
	private static Member getMemberInfoFromId(Guild guild, String id) {
		try {
			if (guild.getMemberById(id) == null)  {
				for (Member member : getAllMembers()) {
					if (member.getUser().getId().equals(id)) {
						return member;
					}
				}
			} else {
				return guild.getMemberById(id);
			}
		} catch(NumberFormatException e) {
			return null;
		}
		
		return null;
	}
	
	public static User getUser(Guild guild, String user) {
		Matcher mention = userMentionRegex.matcher(user);
		Matcher nameTag = userTagRegex.matcher(user);
		Matcher userName = smallNameRegex.matcher(user);
		if (IdRegex.matcher(user).matches()) {
			try { 
				return guild.getJDA().asBot().getShardManager().getUserById(user);
			} catch(NumberFormatException e) {
				return null;
			}
		} else if (mention.matches()) {
			try {
				return guild.getJDA().asBot().getShardManager().getUserById(mention.group(1));
			} catch(NumberFormatException e) {
				return null;
			}
		} else if (nameTag.matches()) {
			String name = nameTag.group(1);
			String discriminator = nameTag.group(2);
			
			for (User u : guild.getJDA().asBot().getShardManager().getUsers()) {
				if (u.getName().toLowerCase().equals(name.toLowerCase()) && u.getDiscriminator().equals(discriminator)) {
					return u;
				}
			}
		} else if (userName.matches()) {
			
			for (User u : guild.getJDA().asBot().getShardManager().getUsers()) {
				if (u.getName().toLowerCase().equals(user.toLowerCase())) {
					return u;
				}
			}

			for (User u : guild.getJDA().asBot().getShardManager().getUsers()) {
				if (u.getName().toLowerCase().startsWith(user.toLowerCase())) {
					return u;
				}
			}
				
			for (User u : guild.getJDA().asBot().getShardManager().getUsers()) {
				if (u.getName().toLowerCase().contains(user.toLowerCase())) {
					return u;
				}
			}
		}
		
		return null;
	}
	
	public static Member getMember(Guild guild, String user) {
		Matcher mention = userMentionRegex.matcher(user);
		Matcher nameTag = userTagRegex.matcher(user);
		Matcher userName = smallNameRegex.matcher(user);
		if (IdRegex.matcher(user).matches()) {
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
						if (member.getEffectiveName().toLowerCase().startsWith(user.toLowerCase())) {
							return member;
						}
					}
					
					for (Member member : guild.getMembers()) {
						if (member.getEffectiveName().toLowerCase().contains(user.toLowerCase())) {
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
		Matcher mention = userMentionRegex.matcher(user);
		if (IdRegex.matcher(user).matches()) {
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
		Matcher mention = userMentionRegex.matcher(user);
		Matcher nameTag = userTagRegex.matcher(user);
		Matcher userName = smallNameRegex.matcher(user);
		if (IdRegex.matcher(user).matches()) {
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
		Matcher mentionRegex = channelMentionRegex.matcher(voiceChannel);
		
		if (IdRegex.matcher(voiceChannel).matches()) {
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
		} else if (bigNameRegex.matcher(voiceChannel).matches()) {
			if (guild.getVoiceChannelsByName(voiceChannel, true).stream().findFirst().orElse(null) == null) {
				for (VoiceChannel channel : guild.getVoiceChannels()) {
					if (channel.getName().toLowerCase().startsWith(voiceChannel.toLowerCase())) {
						return channel;
					}
				} 
				
				for (VoiceChannel channel : guild.getVoiceChannels()) {
					if (channel.getName().toLowerCase().contains(voiceChannel.toLowerCase())) {
						return channel;
					}
				}
			} else {
				return guild.getVoiceChannelsByName(voiceChannel, true).get(0);
			}		
		}
		
		return null;
	}
	
}
