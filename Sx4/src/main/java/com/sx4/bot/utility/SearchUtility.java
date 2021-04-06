package com.sx4.bot.utility;

import com.jockie.bot.core.command.ICommand;
import com.jockie.bot.core.command.impl.CommandListener;
import com.jockie.bot.core.command.impl.DummyCommand;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Category;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.entities.mod.PartialEmote;
import com.vdurmont.emoji.EmojiParser;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.Message.MentionType;
import net.dv8tion.jda.api.entities.MessageReaction.ReactionEmote;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.dv8tion.jda.api.utils.MiscUtil;
import net.dv8tion.jda.api.utils.cache.CacheView;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SearchUtility {
	
	public static final Pattern USER_MENTION = MentionType.USER.getPattern();
	public static final Pattern USER_TAG = Pattern.compile("(.{2,32})#(\\d{4})");
	public static final Pattern CHANNEL_MENTION = MentionType.CHANNEL.getPattern();
	public static final Pattern ROLE_MENTION = MentionType.ROLE.getPattern();
	public static final Pattern EMOTE_MENTION = Pattern.compile("<(a)?:(\\w+):([0-9]+)>");
	public static final Pattern EMOTE_URL = Pattern.compile("https?://cdn\\.discordapp\\.com/emojis/([0-9]+)\\.(png|gif|jpeg|jpg)(?:\\?\\S*)?(?:#\\S*)?", Pattern.CASE_INSENSITIVE);
	
	private static <Type> Type find(Iterable<Type> iterable, String query, List<Function<Type, String>> nameFunctions) {
		List<Type> startsWith = new ArrayList<>();
		List<Type> contains = new ArrayList<>();

		query = query.toLowerCase();
		for (Type object : iterable) {
			for (Function<Type, String> nameFunction : nameFunctions) {
				String modified = nameFunction.apply(object);
				if (modified == null) {
					continue;
				}

				String name = modified.toLowerCase();
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
		}

		if (!startsWith.isEmpty()) {
		    return startsWith.get(0);
		}

		if (!contains.isEmpty()) {
		    return contains.get(0);
		}
		
		return null;
	}

	private static <Type> Type find(Iterable<Type> iterable, String query, Function<Type, String> nameFunction) {
		return SearchUtility.find(iterable, query, Collections.singletonList(nameFunction));
	}
	
	private static TextChannel findTextChannel(CacheView<TextChannel> channels, String query) {
		return SearchUtility.find(channels, query, TextChannel::getName);
	}

	private static VoiceChannel findVoiceChannel(CacheView<VoiceChannel> channels, String query) {
		return SearchUtility.find(channels, query, VoiceChannel::getName);
	}

	private static StoreChannel findStoreChannel(CacheView<StoreChannel> channels, String query) {
		return SearchUtility.find(channels, query, StoreChannel::getName);
	}

	private static Category findCategory(CacheView<Category> channels, String query) {
		return SearchUtility.find(channels, query, Category::getName);
	}
	
	private static Member findMember(CacheView<Member> members, String query) {
		return SearchUtility.find(members, query, List.of(Member::getNickname, member -> member.getUser().getName()));
	}
	
	private static Role findRole(CacheView<Role> roles, String query) {
		return SearchUtility.find(roles, query, Role::getName);
	}
	
	private static Emote findEmote(CacheView<Emote> emotes, String query) {
		return SearchUtility.find(emotes, query, Emote::getName);
	}
	
	private static Guild findGuild(CacheView<Guild> guilds, String query) {
		return SearchUtility.find(guilds, query, Guild::getName);
	}
	
	public static Guild getGuild(ShardManager manager, String query) {
		if (NumberUtility.isNumberUnsigned(query)) {
			try {
				return manager.getGuildById(query);
			} catch (NumberFormatException e) {
				return null;
			}
		} else {
			return SearchUtility.findGuild(manager.getGuildCache(), query);
		}
	}
	
	public static PartialEmote getPartialEmote(ShardManager manager, String query) {
		Matcher mentionMatch = SearchUtility.EMOTE_MENTION.matcher(query);
		Matcher urlMatch = SearchUtility.EMOTE_URL.matcher(query);
		if (mentionMatch.matches()) {
			String id = mentionMatch.group(3);
			
			try {
				return new PartialEmote(Long.parseLong(id), mentionMatch.group(2), mentionMatch.group(1) != null);
			} catch (NumberFormatException e) {
				return null;
			}	
		} else if (NumberUtility.isNumberUnsigned(query)) {
			try {
				Emote emote = manager.getEmoteById(query);
				if (emote == null) {
					return new PartialEmote(Long.parseLong(query), null, null);
				} else {
					return new PartialEmote(emote);
				}
			} catch (NumberFormatException e) {
				return null;
			}
		} else if (urlMatch.matches()) {
			try {
				long id = Long.parseLong(urlMatch.group(1));
				
				Emote emote = manager.getEmoteById(id);
				if (emote == null) {
					return new PartialEmote(id, null, urlMatch.group(2).equals("gif"));
				} else {
					return new PartialEmote(emote);
				}
			} catch (NumberFormatException e) {
				return null;
			}
			
		}
		
		return null;
	}
	
	public static Emote getEmote(ShardManager manager, String query) {
		Matcher mentionMatch = SearchUtility.EMOTE_MENTION.matcher(query);
		Matcher urlMatch = SearchUtility.EMOTE_URL.matcher(query);
		if (mentionMatch.matches()) {
			try {
				return manager.getEmoteById(mentionMatch.group(3));
			} catch (NumberFormatException e) {
				return null;
			}
		} else if (NumberUtility.isNumberUnsigned(query)) {
			try {
				return manager.getEmoteById(query);
			} catch (NumberFormatException e) {
				return null;
			}
		} else if (urlMatch.matches()) {
			try {
				return manager.getEmoteById(urlMatch.group(1));
			} catch (NumberFormatException e) {
				return null;
			}
		}

		return null;
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
			return SearchUtility.findEmote(guild.getEmoteCache(), query);
		}
	}
	
	public static ReactionEmote getReactionEmote(ShardManager manager, String query) {
		List<String> emojis = EmojiParser.extractEmojis(query);
		if (!emojis.isEmpty()) {
			return ReactionEmote.fromUnicode(emojis.get(0), manager.getShardById(0));
		} else {
			Emote emote = SearchUtility.getEmote(manager, query);
			return emote == null ? null : ReactionEmote.fromCustom(emote);
		}
	}
	
	public static IPermissionHolder getPermissionHolder(Guild guild, String query) {
		Role role = SearchUtility.getRole(guild, query);
		if (role == null) {
			return SearchUtility.getMember(guild, query);
		} else {
			return role;
		}
	}
	
	public static Role getRole(Guild guild, String query) {
		Matcher mentionMatch = SearchUtility.ROLE_MENTION.matcher(query);
		if (mentionMatch.matches()) {
			try {
				return guild.getRoleById(mentionMatch.group(1));
			} catch (NumberFormatException e) {
				return null;
			}
		} else if (NumberUtility.isNumberUnsigned(query)) {
			try {
				return guild.getRoleById(query);
			} catch (NumberFormatException e) {
				return null;
			}
		} else {
			return SearchUtility.findRole(guild.getRoleCache(), query);
		}
	}

	public static GuildChannel getGuildChannel(Guild guild, ChannelType type, String query) {
		if (type == ChannelType.TEXT) {
			return SearchUtility.getTextChannel(guild, query);
		} else if (type == ChannelType.CATEGORY) {
			return SearchUtility.getCategory(guild, query);
		} else if (type == ChannelType.STORE) {
			return SearchUtility.getStoreChannel(guild, query);
		} else if (type == ChannelType.VOICE) {
			return SearchUtility.getVoiceChannel(guild, query);
		}

		return null;
	}

	public static GuildChannel getGuildChannel(Guild guild, String query) {
		GuildChannel channel = SearchUtility.getTextChannel(guild, query);
		if (channel == null) {
			channel = SearchUtility.getVoiceChannel(guild, query);
		}

		if (channel == null) {
			channel = SearchUtility.getCategory(guild, query);

		}

		if (channel == null) {
			channel = SearchUtility.getStoreChannel(guild, query);
		}

		return channel;
	}
	
	public static TextChannel getTextChannel(Guild guild, String query) {
		Matcher mentionMatch = SearchUtility.CHANNEL_MENTION.matcher(query);
		if (mentionMatch.matches()) {
			try {
				return guild.getTextChannelById(mentionMatch.group(1));
			} catch (NumberFormatException e) {
				return null;
			}
		} else if (NumberUtility.isNumberUnsigned(query)) {
			try {
				return guild.getTextChannelById(query);
			} catch (NumberFormatException e) {
				return null;
			}
		} else {
			return SearchUtility.findTextChannel(guild.getTextChannelCache(), query);
		}
	}

	public static StoreChannel getStoreChannel(Guild guild, String query) {
		Matcher mentionMatch = SearchUtility.CHANNEL_MENTION.matcher(query);
		if (mentionMatch.matches()) {
			try {
				return guild.getStoreChannelById(mentionMatch.group(1));
			} catch (NumberFormatException e) {
				return null;
			}
		} else if (NumberUtility.isNumberUnsigned(query)) {
			try {
				return guild.getStoreChannelById(query);
			} catch (NumberFormatException e) {
				return null;
			}
		} else {
			return SearchUtility.findStoreChannel(guild.getStoreChannelCache(), query);
		}
	}

	public static VoiceChannel getVoiceChannel(Guild guild, String query) {
		if (NumberUtility.isNumberUnsigned(query)) {
			try {
				return guild.getVoiceChannelById(query);
			} catch (NumberFormatException e) {
				return null;
			}
		} else {
			return SearchUtility.findVoiceChannel(guild.getVoiceChannelCache(), query);
		}
	}

	public static Category getCategory(Guild guild, String query) {
		if (NumberUtility.isNumberUnsigned(query)) {
			try {
				return guild.getCategoryById(query);
			} catch (NumberFormatException e) {
				return null;
			}
		} else {
			return SearchUtility.findCategory(guild.getCategoryCache(), query);
		}
	}
	
	public static Member getMember(Guild guild, String query) {
		Matcher mentionMatch = SearchUtility.USER_MENTION.matcher(query);
		Matcher tagMatch = SearchUtility.USER_TAG.matcher(query);
		if (mentionMatch.matches()) {
			try {
				return guild.getMemberById(mentionMatch.group(1));
			} catch (NumberFormatException e) {
				return null;
			}
		} else if (tagMatch.matches()) {
			String name = tagMatch.group(1);
			String discriminator = tagMatch.group(2);
			
			return guild.getMemberCache().applyStream(stream ->
				stream.filter(member -> member.getUser().getName().equalsIgnoreCase(name) && member.getUser().getDiscriminator().equals(discriminator))
					.findFirst()
					.orElse(null)
			);
		} else if (NumberUtility.isNumberUnsigned(query)) {
			try {
				return guild.getMemberById(query);
			} catch (NumberFormatException e) {
				return null;
			}
		} else {
			return SearchUtility.findMember(guild.getMemberCache(), query);
		}
	}

	public static long getUserId(String query) {
		Matcher mentionMatch = SearchUtility.USER_MENTION.matcher(query);
		if (mentionMatch.matches()) {
			try {
				return MiscUtil.parseSnowflake(mentionMatch.group(1));
			} catch (NumberFormatException e) {
				return -1L;
			}
		} else if (NumberUtility.isNumberUnsigned(query)) {
			try {
				return MiscUtil.parseSnowflake(query);
			} catch (NumberFormatException e) {
				return -1L;
			}
		} else {
			return -1L;
		}
	}
	
	public static CompletableFuture<User> getUser(ShardManager manager, String query) {
		Matcher mentionMatch = SearchUtility.USER_MENTION.matcher(query);
		Matcher tagMatch = SearchUtility.USER_TAG.matcher(query);
		if (mentionMatch.matches()) {
			try {
				return manager.retrieveUserById(mentionMatch.group(1)).submit();
			} catch (NumberFormatException e) {
				return CompletableFuture.completedFuture(null);
			}
		} else if (tagMatch.matches()) {
			String name = tagMatch.group(1);
			String discriminator = tagMatch.group(2);

			return CompletableFuture.completedFuture(
				manager.getUserCache().applyStream(userStream ->
					userStream.filter(user -> user.getName().equalsIgnoreCase(name) && user.getDiscriminator().equals(discriminator))
						.findFirst()
						.orElse(null)
				)
			);
		} else if (NumberUtility.isNumberUnsigned(query)) {
			try {
				return manager.retrieveUserById(query).submit();
			} catch (NumberFormatException e) {
				return CompletableFuture.completedFuture(null);
			}
		} else {
			return CompletableFuture.completedFuture(
				manager.getUserCache().applyStream(stream ->
					stream.filter(user -> user.getName().equalsIgnoreCase(query))
						.findFirst()
						.orElse(null)
				)
			);
		}
	}
	
	public static List<Sx4Command> getCommandOrModule(CommandListener commandListener, String query) {
		Sx4Command command = SearchUtility.getCommand(commandListener, query, false);
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
		return Arrays.stream(ModuleCategory.ALL_ARRAY)
			.filter(category -> category.getName().equalsIgnoreCase(query) || Arrays.stream(category.getAliases()).anyMatch(query::equalsIgnoreCase))
			.findFirst()
			.orElse(null);
	}
	
	public static Sx4Command getCommand(CommandListener commandListener, String query) {
		return SearchUtility.getCommand(commandListener, query, false, true);
	}
	
	public static Sx4Command getCommand(CommandListener commandListener, String query, boolean includeDeveloper) {
		return SearchUtility.getCommand(commandListener, query, false, includeDeveloper);
	}
	
	public static Sx4Command getCommand(CommandListener commandListener, String query, boolean caseSensitive, boolean includeDeveloper) {
		List<Sx4Command> commands = SearchUtility.getCommands(commandListener, query, caseSensitive, includeDeveloper);
		return commands.isEmpty() ? null : commands.get(0);
	}
	
	public static List<Sx4Command> getCommands(CommandListener commandListener, String query) {
		return SearchUtility.getCommands(commandListener, query, false, true);
	}
	
	public static List<Sx4Command> getCommands(CommandListener commandListener, String query, boolean includeDeveloper) {
		return SearchUtility.getCommands(commandListener, query, false, includeDeveloper);
	}

	public static List<Sx4Command> getCommands(CommandListener commandListener, String query, boolean caseSensitive, boolean includeDeveloper) {
		query = caseSensitive ? query.trim() : query.toLowerCase().trim();
		
		List<Sx4Command> commands = new ArrayList<>();
		Command : for (ICommand commandObject : commandListener.getAllCommands(includeDeveloper, false)) {
			Sx4Command command = (Sx4Command) (commandObject instanceof DummyCommand ? ((DummyCommand) commandObject).getActualCommand() : commandObject);
			
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

			ICommand parent = command;
			List<String> parentAliases = new ArrayList<>(parent.getAliases());
			parentAliases.add(parent.getCommand());
			
			while (parent.hasParent()) {
				parent = parent.getParent();
				List<String> continuousParentAliases = new ArrayList<>(parent.getAliases());
				continuousParentAliases.add(parent.getCommand());
				for (String aliases : new ArrayList<>(parentAliases)) {
					for (String alias : continuousParentAliases) {
						parentAliases.remove(aliases);
						parentAliases.add(alias + " " + aliases);
					}
				}
			}
			
			for (String commandAlias : parentAliases) {
				commandAlias = caseSensitive ? commandAlias : commandAlias.toLowerCase();
				if (query.equals(commandAlias)) {
					commands.add(command);
					continue Command;
				}
			}
		}
		
		return commands;
	}

	public static Locale getLocale(String query) {
		for (Locale locale : Locale.getAvailableLocales()) {
			if (query.length() == 3 && query.equalsIgnoreCase(locale.getISO3Language())) {
				return locale;
			} else if (query.equalsIgnoreCase(locale.getLanguage())) {
				return locale;
			} else if (query.equalsIgnoreCase(locale.getDisplayLanguage())) {
				return locale;
			} else if (query.equalsIgnoreCase(locale.getDisplayLanguage(locale))) {
				return locale;
			}
		}

		return null;
	}

	public static Locale getLocaleFromTag(String tag) {
		for (Locale locale : Locale.getAvailableLocales()) {
			if (tag.equalsIgnoreCase(locale.getLanguage())) {
				return locale;
			}
		}

		return null;
	}
	
}
