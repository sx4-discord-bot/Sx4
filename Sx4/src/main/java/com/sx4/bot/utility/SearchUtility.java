package com.sx4.bot.utility;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.jockie.bot.core.command.ICommand;
import com.sx4.bot.category.Category;
import com.sx4.bot.core.Sx4Bot;
import com.sx4.bot.core.Sx4Category;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.entities.argument.MessageArgument;
import com.sx4.bot.entities.mod.PartialEmote;

import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.IPermissionHolder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.MentionType;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageReaction.ReactionEmote;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.utils.MiscUtil;

public class SearchUtility {
	
	private static final List<String> SUPPORTED_TYPES = List.of("png", "jpg", "gif", "webp", "jpeg");
	
	private static final Pattern USER_MENTION = MentionType.USER.getPattern();
	private static final Pattern USER_TAG = Pattern.compile("(.{2,32})#(\\d{4})");
	private static final Pattern CHANNEL_MENTION = MentionType.CHANNEL.getPattern();
	private static final Pattern ROLE_MENTION = MentionType.ROLE.getPattern();
	private static final Pattern EMOTE_MENTION = Pattern.compile("<(a)?:([a-zA-Z0-9_]+):([0-9]+)>");
	private static final Pattern EMOTE_URL = Pattern.compile("https?://cdn\\.discordapp\\.com/emojis/([0-9]+)\\.(png|gif|jpeg|jpg)(?:\\?\\S*)?(?:#\\S*)?", Pattern.CASE_INSENSITIVE);
	
	private static <Type> Type find(List<Type> list, String query, Function<Type, String> nameFunction) {
		List<Type> startsWith = new ArrayList<>();
		List<Type> contains = new ArrayList<>();

		query = query.toLowerCase();
		for (Type object : list) {
		    String name = nameFunction.apply(object).toLowerCase();
		    if (name.equals(query)) {
		        return object;
		    }
		    
		    if (name.startsWith(query)) {
		        startsWith.add(object);
		    }
		    
		    if (name.contains(query)) {
		        contains.add(object);
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
	
	private static TextChannel findTextChannel(List<TextChannel> channels, String query) {
		return SearchUtility.find(channels, query, channel -> channel.getName());
	}
	
	private static Member findMember(List<Member> members, String query) {
		return SearchUtility.find(members, query, member -> member.getEffectiveName());
	}
	
	private static Role findRole(List<Role> roles, String query) {
		return SearchUtility.find(roles, query, role -> role.getName());
	}
	
	private static Emote findEmote(List<Emote> emotes, String query) {
		return SearchUtility.find(emotes, query, emote -> emote.getName());
	}
	
	public static PartialEmote getPartialEmote(String query) {
		Matcher mentionMatch = SearchUtility.EMOTE_MENTION.matcher(query);
		Matcher urlMatch = SearchUtility.EMOTE_URL.matcher(query);
		if (mentionMatch.matches()) {
			String id = mentionMatch.group(3);
			
			try {
				return new PartialEmote(Long.valueOf(id), mentionMatch.group(2), mentionMatch.group(1) != null);
			} catch (NumberFormatException e) {
				return null;
			}	
		} else if (NumberUtility.isNumberUnsigned(query)) {
			try {
				Emote emote = Sx4Bot.getShardManager().getEmoteById(query);
				if (emote == null) {
					return new PartialEmote(Long.valueOf(query), null, null);
				} else {
					return new PartialEmote(emote);
				}
			} catch (NumberFormatException e) {
				return null;
			}
		} else if (urlMatch.matches()) {
			try {
				Long id = Long.parseLong(urlMatch.group(1));
				
				Emote emote = Sx4Bot.getShardManager().getEmoteById(id);
				if (emote == null) {
					return new PartialEmote(id, null, urlMatch.group(2).equals("gif"));
				} else {
					return new PartialEmote(emote);
				}
			} catch (NumberFormatException e) {
				return null;
			}
			
		} else {
			Emote emote = SearchUtility.findEmote(Sx4Bot.getShardManager().getEmotes(), query);
			if (emote != null) {
				return new PartialEmote(emote);
			}
		}
		
		return null;
	}
	
	public static Emote getEmote(String query) {
		Matcher mentionMatch = SearchUtility.EMOTE_MENTION.matcher(query);
		Matcher urlMatch = SearchUtility.EMOTE_URL.matcher(query);
		if (mentionMatch.matches()) {
			try {
				return Sx4Bot.getShardManager().getEmoteById(mentionMatch.group(3));
			} catch (NumberFormatException e) {
				return null;
			}
		} else if (NumberUtility.isNumberUnsigned(query)) {
			try {
				return Sx4Bot.getShardManager().getEmoteById(query);
			} catch (NumberFormatException e) {
				return null;
			}
		} else if (urlMatch.matches()) {
			try {
				return Sx4Bot.getShardManager().getEmoteById(urlMatch.group(1));
			} catch (NumberFormatException e) {
				return null;
			}
		} else {
			return SearchUtility.findEmote(Sx4Bot.getShardManager().getEmotes(), query);
		}
	}
	
	public static Emote getGuildEmote(Guild guild, String query) {
		Matcher mentionMatch = SearchUtility.EMOTE_MENTION.matcher(query);
		Matcher urlMatch = SearchUtility.EMOTE_URL.matcher(query);
		if (mentionMatch.matches()) {
			try {
				return guild.getEmoteById(mentionMatch.group(3));
			} catch (NumberFormatException e) {
				return null;
			}
		} else if (NumberUtility.isNumberUnsigned(query)) {
			try {
				return guild.getEmoteById(query);
			} catch (NumberFormatException e) {
				return null;
			}
		} else if (urlMatch.matches()) {
			try {
				return guild.getEmoteById(urlMatch.group(1));
			} catch (NumberFormatException e) {
				return null;
			}
		} else {
			return SearchUtility.findEmote(guild.getEmotes(), query);
		}
	}
	
	public static ReactionEmote getReactionEmote(String query) {
		Emote emote = SearchUtility.getEmote(query);
		if (emote != null) {
			return ReactionEmote.fromCustom(emote);
		} else {
			return ReactionEmote.fromUnicode(query, Sx4Bot.getShardManager().getShardById(0));
		}
	}
	
	public static MessageArgument getMessageArgument(TextChannel channel, String query) {
		Matcher jumpMatch = Message.JUMP_URL_PATTERN.matcher(query);
		if (jumpMatch.matches()) {
			try {
				Guild guild = Sx4Bot.getShardManager().getGuildById(jumpMatch.group(1));
				if (guild == null) {
					return null;
				}
				
				TextChannel linkChannel = guild.getTextChannelById(jumpMatch.group(2));
				if (linkChannel == null) {
					return null;
				}
				
				long messageId = MiscUtil.parseSnowflake(jumpMatch.group(3));
				
				return new MessageArgument(messageId, linkChannel.retrieveMessageById(messageId));
			} catch (NumberFormatException e) {
				return null;
			}
		} else {
			try {
				long messageId = MiscUtil.parseSnowflake(query);
				
				return new MessageArgument(messageId, channel.retrieveMessageById(messageId));
			} catch (NumberFormatException e) {
				return null;
			}
		}
	}
	
	public static IPermissionHolder getPermissionHolder(Guild guild, String query) {
		Member member = SearchUtility.getMember(guild, query);
		Role role = SearchUtility.getRole(guild, query);
		
		if (role != null) {
			return role;
		} else {
			return member;
		} 
	}
	
	public static URL getURL(Message message, String query) {
		URL url;
		try {
			url = new URL(query);
		} catch (MalformedURLException e) {
			return null;
		}
		
		String urlString = url.toString();
		int periodIndex = urlString.lastIndexOf(".");
		
		if (periodIndex == -1 || !SearchUtility.SUPPORTED_TYPES.contains(urlString.substring(periodIndex + 1).toLowerCase())) {
			if (message.getEmbeds().isEmpty()) {
				return null;
			} else {
				MessageEmbed imageEmbed = message.getEmbeds().stream()
					.filter(embed -> embed.getThumbnail() != null)
					.findFirst()
					.orElse(null);
				
				if (imageEmbed != null) {
					String embedUrl = imageEmbed.getThumbnail().getUrl();
					int periodIndexEmbed = embedUrl.lastIndexOf(".");
					
					if (!SearchUtility.SUPPORTED_TYPES.contains(embedUrl.substring(periodIndexEmbed + 1).toUpperCase())) {
						return null;
					} else {
						try {
							url = new URL(embedUrl);
						} catch (MalformedURLException e) {}
					}
				} else {
					return null;
				}
			}
		}
		
		return url;
	}
	
	public static Role getRole(Guild guild, String query) {
		Matcher mentionMatch = SearchUtility.ROLE_MENTION.matcher(query);
		if (mentionMatch.matches()) {
			try {
				long id = Long.parseLong(mentionMatch.group(1));
				
				return guild.getRoleById(id);
			} catch (NumberFormatException e) {
				return null;
			}
		} else if (NumberUtility.isNumberUnsigned(query)) {
			try {
				long id = Long.parseLong(query);
				
				return guild.getRoleById(id);
			} catch (NumberFormatException e) {
				return null;
			}
		} else {
			return SearchUtility.findRole(guild.getRoles(), query);
		}
	}
	
	public static TextChannel getTextChannel(Guild guild, String query) {
		Matcher mentionMatch = SearchUtility.CHANNEL_MENTION.matcher(query);
		if (mentionMatch.matches()) {
			try {
				long id = Long.parseLong(mentionMatch.group(1));
				
				return guild.getTextChannelById(id);
			} catch (NumberFormatException e) {
				return null;
			}
		} else if (NumberUtility.isNumberUnsigned(query)) {
			try {
				long id = Long.parseLong(query);
				
				return guild.getTextChannelById(id);
			} catch (NumberFormatException e) {
				return null;
			}
		} else {
			return SearchUtility.findTextChannel(guild.getTextChannels(), query);
		}
	}
	
	public static Member getMember(Guild guild, String query) {
		Matcher mentionMatch = SearchUtility.USER_MENTION.matcher(query);
		Matcher tagMatch = SearchUtility.USER_TAG.matcher(query);
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
		} else if (NumberUtility.isNumberUnsigned(query)) {
			try {
				long id = Long.parseLong(query);
				
				return guild.getMemberById(id);
			} catch (NumberFormatException e) {
				return null;
			}
		} else {
			return SearchUtility.findMember(guild.getMembers(), query);
		}
	}
	
	public static User getUser(String query) {
		Matcher mentionMatch = SearchUtility.USER_MENTION.matcher(query);
		Matcher tagMatch = SearchUtility.USER_TAG.matcher(query);
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
		} else if (NumberUtility.isNumberUnsigned(query)) {
			try {
				long id = Long.parseLong(query);
				
				return Sx4Bot.getShardManager().getUserById(id);
			} catch (NumberFormatException e) {
				return null;
			}
		} else {
			return Sx4Bot.getShardManager().getUserCache().stream()
					.filter(user -> user.getName().equalsIgnoreCase(query))
					.findFirst()
					.orElse(null);
		}
	}
	
	public static void getUserRest(Guild guild, String query, Consumer<User> userConsumer) {
		Matcher mentionMatch = SearchUtility.USER_MENTION.matcher(query);
		Matcher tagMatch = SearchUtility.USER_TAG.matcher(query);
		if (mentionMatch.matches()) {
			try {
				long id = Long.parseLong(mentionMatch.group(1));
				
				Member member = guild.getMemberById(id);
				if (member == null) {
					Sx4Bot.getShardManager().retrieveUserById(id).queue(user -> {
						userConsumer.accept(user);
					});
				} else {
					userConsumer.accept(member.getUser());
				}
			} catch (NumberFormatException e) {
				userConsumer.accept(null);
			}
		} else if (tagMatch.matches()) {
			String name = tagMatch.group(1);
			String discriminator = tagMatch.group(2);
			
			userConsumer.accept(
				guild.getMemberCache().stream()
					.map(Member::getUser)
					.filter(user -> user.getName().equalsIgnoreCase(name) && user.getDiscriminator().equals(discriminator))
					.findFirst()
					.orElseGet(() -> {
						return Sx4Bot.getShardManager().getUserCache().stream()
							.filter(user -> user.getName().equalsIgnoreCase(name) && user.getDiscriminator().equals(discriminator))
							.findFirst()
							.orElse(null);
					})
			);
		} else if (NumberUtility.isNumberUnsigned(query)) {
			try {
				long id = Long.parseLong(query);
				
				Member member = guild.getMemberById(id);
				if (member == null) {
					Sx4Bot.getShardManager().retrieveUserById(id).queue(user -> {
						userConsumer.accept(user);
					});
				} else {
					userConsumer.accept(member.getUser());
				}
			} catch (NumberFormatException e) {
				userConsumer.accept(null);
			}
		} else {
			Member member = SearchUtility.findMember(guild.getMembers(), query);
			if (member == null) {
				userConsumer.accept(Sx4Bot.getShardManager().getUserCache().stream()
						.filter(user -> user.getName().equalsIgnoreCase(query))
						.findFirst()
						.orElse(null));
			} else {
				userConsumer.accept(member.getUser());
			}
		}
	}
	
	public static void getUserRest(String query, Consumer<User> userConsumer) {
		Matcher mentionMatch = SearchUtility.USER_MENTION.matcher(query);
		Matcher tagMatch = SearchUtility.USER_TAG.matcher(query);
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
		} else if (NumberUtility.isNumberUnsigned(query)) {
			try {
				long id = Long.parseLong(query);
				
				Sx4Bot.getShardManager().retrieveUserById(id).queue(user -> {
					userConsumer.accept(user);
				});
			} catch (NumberFormatException e) {
				userConsumer.accept(null);
			}
		} else {
			userConsumer.accept(Sx4Bot.getShardManager().getUserCache().stream()
					.filter(user -> user.getName().equalsIgnoreCase(query))
					.findFirst()
					.orElse(null));
		}
	}
	
	public static List<Sx4Command> getCommandOrModule(String query) {
		Sx4Command command = SearchUtility.getCommand(query, false);
		Sx4Category category = SearchUtility.getModule(query);
		
		if (category != null) {
			return category.getCommands().stream().map(Sx4Command.class::cast).collect(Collectors.toList());
		} else if (command != null) {
			return List.of(command);
		} else {
			return null;
		}
	}
	
	public static Sx4Category getModule(String query) {
		return Arrays.stream(Category.ALL_ARRAY)
				.filter(category -> category.getName().equalsIgnoreCase(query) || Arrays.stream(category.getAliases()).anyMatch(query::equalsIgnoreCase))
				.findFirst()
				.orElse(null);
	}
	
	public static Sx4Command getCommand(String query) {
		return SearchUtility.getCommand(query, false, true);
	}
	
	public static Sx4Command getCommand(String query, boolean includeDeveloper) {
		return SearchUtility.getCommand(query, false, includeDeveloper);
	}
	
	public static Sx4Command getCommand(String query, boolean caseSensitive, boolean includeDeveloper) {
		List<Sx4Command> commands = SearchUtility.getCommands(query, caseSensitive, includeDeveloper);
		return commands.isEmpty() ? null : commands.get(0);
	}
	
	public static List<Sx4Command> getCommands(String query) {
		return SearchUtility.getCommands(query, false, true);
	}
	
	public static List<Sx4Command> getCommands(String query, boolean includeDeveloper) {
		return SearchUtility.getCommands(query, false, includeDeveloper);
	}

	public static List<Sx4Command> getCommands(String query, boolean caseSensitive, boolean includeDeveloper) {
		query = caseSensitive ? query.trim() : query.toLowerCase().trim();
		
		List<Sx4Command> commands = new ArrayList<>();
		Command : for (ICommand commandObject : Sx4Bot.getCommandListener().getAllCommands(includeDeveloper, false)) {
			Sx4Command command = (Sx4Command) commandObject;
			
			String commandTrigger = caseSensitive ? command.getCommandTrigger() : command.getCommandTrigger().toLowerCase();
			if (commandTrigger.equals(query)) {
				commands.add(command);
				continue;
			}
			
			for (String redirect : command.getRedirects()) {
				if ((caseSensitive ? redirect : redirect.toLowerCase()).equals(query)) {
					commands.add(command);
					continue Command;
				}
			}
			
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
				if (query.equals(commandAlias)) {
					commands.add(command);
					continue Command;
				}
			}
		}
		
		return commands;
	}
	
}
