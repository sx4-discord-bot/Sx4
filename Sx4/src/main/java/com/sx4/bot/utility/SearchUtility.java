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
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.entities.sticker.GuildSticker;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.dv8tion.jda.api.utils.MiscUtil;
import net.dv8tion.jda.api.utils.cache.CacheView;
import net.dv8tion.jda.internal.utils.cache.UnifiedCacheViewImpl;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
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

	public static <Type> Type findAny(Iterable<Type> iterable, String query, List<Function<Type, String>> nameFunctions, Predicate<Type> predicate) {
		List<Type> equal = new ArrayList<>();
		List<Type> startsWith = new ArrayList<>();
		List<Type> contains = new ArrayList<>();

		query = query.toLowerCase();
		for (Type object : iterable) {
			if (predicate != null && !predicate.test(object)) {
				continue;
			}

			for (Function<Type, String> nameFunction : nameFunctions) {
				String modified = nameFunction.apply(object);
				if (modified == null) {
					continue;
				}

				String name = modified.toLowerCase();
				if (name.equals(query)) {
					equal.add(object);
				}

				if (name.startsWith(query)) {
					startsWith.add(object);
				}

				if (name.contains(query)) {
					contains.add(object);
				}
			}
		}

		if (!equal.isEmpty()) {
			return equal.get(0);
		}

		if (!startsWith.isEmpty()) {
			return startsWith.get(0);
		}

		if (!contains.isEmpty()) {
			return contains.get(0);
		}

		return null;
	}

	public static <Type> Type findFirst(Iterable<Type> iterable, String query, List<Function<Type, String>> nameFunctions) {
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

	private static <Type> Type findFirst(Iterable<Type> iterable, String query, Function<Type, String> nameFunction) {
		return SearchUtility.findFirst(iterable, query, Collections.singletonList(nameFunction));
	}
	
	private static TextChannel findTextChannel(CacheView<TextChannel> channels, String query) {
		return SearchUtility.findFirst(channels, query, TextChannel::getName);
	}

	private static AudioChannel findAudioChannel(Iterable<AudioChannel> channels, String query) {
		return SearchUtility.findFirst(channels, query, AudioChannel::getName);
	}

	private static Category findCategory(CacheView<Category> channels, String query) {
		return SearchUtility.findFirst(channels, query, Category::getName);
	}
	
	private static Member findMember(CacheView<Member> members, String query) {
		return SearchUtility.findFirst(members, query, List.of(Member::getNickname, member -> member.getUser().getName()));
	}
	
	private static Role findRole(CacheView<Role> roles, String query) {
		return SearchUtility.findFirst(roles, query, Role::getName);
	}
	
	private static RichCustomEmoji findEmoji(CacheView<RichCustomEmoji> emotes, String query) {
		return SearchUtility.findFirst(emotes, query, Emoji::getName);
	}
	
	private static Guild findGuild(CacheView<Guild> guilds, String query) {
		return SearchUtility.findFirst(guilds, query, Guild::getName);
	}

	public static CompletableFuture<Guild.Ban> getBan(Guild guild, String query) {
		Matcher matcher;
		if (NumberUtility.isNumberUnsigned(query)) {
			try {
				return guild.retrieveBan(User.fromId(query)).submit();
			} catch (NumberFormatException e) {
				return guild.retrieveBanList().submit().thenApply(bans -> SearchUtility.findFirst(bans, query, ban -> ban.getUser().getName()));
			}
		} else if ((matcher = SearchUtility.USER_MENTION.matcher(query)).matches()) {
			try {
				return guild.retrieveBan(User.fromId(matcher.group(1))).submit();
			} catch (NumberFormatException e) {
				return CompletableFuture.completedFuture(null);
			}
		} else if ((matcher = SearchUtility.USER_TAG.matcher(query)).matches()) {
			String name = matcher.group(1), discriminator = matcher.group(2);

			return guild.retrieveBanList().submit().thenApply(bans -> {
				return bans.stream()
					.filter(ban -> {
						User user = ban.getUser();
						return user.getName().equalsIgnoreCase(name) && user.getDiscriminator().equalsIgnoreCase(discriminator);
					})
					.findFirst()
					.orElse(null);
			});
		} else {
			return guild.retrieveBanList().submit().thenApply(bans -> SearchUtility.findFirst(bans, query, ban -> ban.getUser().getName()));
		}
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

	public static GuildSticker getSticker(Guild guild, String query) {
		if (NumberUtility.isNumberUnsigned(query)) {
			try {
				return guild.getStickerById(query);
			} catch (NumberFormatException e) {
				return null;
			}
		} else {
			return SearchUtility.findFirst(guild.getStickerCache(), query, GuildSticker::getName);
		}
	}

	public static GuildSticker getSticker(ShardManager manager, String query) {
		if (NumberUtility.isNumberUnsigned(query)) {
			try {
				return manager.getGuildCache().applyStream(stream -> stream.map(guild -> guild.getStickerById(query)).filter(Objects::nonNull).findAny().orElse(null));
			} catch (NumberFormatException e) {
				return null;
			}
		} else {
			List<CacheView<GuildSticker>> cacheViews = manager.getGuildCache().applyStream(stream -> stream.map(Guild::getStickerCache).collect(Collectors.toList()));
			CacheView<GuildSticker> stickers = new UnifiedCacheViewImpl<>(cacheViews::stream);

			return SearchUtility.findFirst(stickers, query, GuildSticker::getName);
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
				CustomEmoji emote = manager.getEmojiById(query);
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
				
				CustomEmoji emote = manager.getEmojiById(id);
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
	
	public static RichCustomEmoji getCustomEmoji(ShardManager manager, String query) {
		Matcher mentionMatch = SearchUtility.EMOTE_MENTION.matcher(query);
		Matcher urlMatch = SearchUtility.EMOTE_URL.matcher(query);
		if (mentionMatch.matches()) {
			try {
				return manager.getEmojiById(mentionMatch.group(3));
			} catch (NumberFormatException e) {
				return null;
			}
		} else if (NumberUtility.isNumberUnsigned(query)) {
			try {
				return manager.getEmojiById(query);
			} catch (NumberFormatException e) {
				return null;
			}
		} else if (urlMatch.matches()) {
			try {
				return manager.getEmojiById(urlMatch.group(1));
			} catch (NumberFormatException e) {
				return null;
			}
		}

		return null;
	}
	
	public static RichCustomEmoji getGuildCustomEmoji(Guild guild, String query) {
		Matcher mentionMatch = SearchUtility.EMOTE_MENTION.matcher(query);
		Matcher urlMatch = SearchUtility.EMOTE_URL.matcher(query);
		if (mentionMatch.matches()) {
			try {
				return guild.getEmojiById(mentionMatch.group(3));
			} catch (NumberFormatException e) {
				return null;
			}
		} else if (NumberUtility.isNumberUnsigned(query)) {
			try {
				return guild.getEmojiById(query);
			} catch (NumberFormatException e) {
				return null;
			}
		} else if (urlMatch.matches()) {
			try {
				return guild.getEmojiById(urlMatch.group(1));
			} catch (NumberFormatException e) {
				return null;
			}
		} else {
			return SearchUtility.findEmoji(guild.getEmojiCache(), query);
		}
	}
	
	public static EmojiUnion getEmoji(ShardManager manager, String query) {
		List<String> emojis = EmojiParser.extractEmojis(query);
		if (!emojis.isEmpty()) {
			return (EmojiUnion) Emoji.fromUnicode(emojis.get(0));
		} else {
			return (EmojiUnion) SearchUtility.getCustomEmoji(manager, query);
		}
	}

	public static EmojiUnion getUncheckedEmoji(ShardManager manager, String query) {
		CustomEmoji emoji = SearchUtility.getCustomEmoji(manager, query);
		if (emoji == null) {
			return (EmojiUnion) Emoji.fromUnicode(query);
		} else {
			return (EmojiUnion) emoji;
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

	public static GuildChannel getGuildChannel(Guild guild, Collection<ChannelType> types, String query) {
		return SearchUtility.getGuildChannel(guild, types.toArray(new ChannelType[0]), query);
	}

	private static GuildChannel getGuildChannelById(Guild guild, String id, ChannelType... types) {
		GuildChannel channel = guild.getGuildChannelById(id);
		if (channel == null) {
			return null;
		}

		for (ChannelType type : types) {
			if (type == channel.getType()) {
				return channel;
			}
		}

		return null;
	}

	public static GuildChannel getGuildChannel(Guild guild, ChannelType[] types, String query) {
		Matcher mentionMatch = SearchUtility.CHANNEL_MENTION.matcher(query);
		if (mentionMatch.matches()) {
			try {
				return SearchUtility.getGuildChannelById(guild, mentionMatch.group(1), types);
			} catch (NumberFormatException e) {
				return null;
			}
		} else if (NumberUtility.isNumberUnsigned(query)) {
			try {
				return SearchUtility.getGuildChannelById(guild, query, types);
			} catch (NumberFormatException e) {
				return null;
			}
		} else {
			return SearchUtility.findAny(guild.getChannels(), query, List.of(GuildChannel::getName), channel -> {
				for (ChannelType type : types) {
					if (channel.getType() == type) {
						return true;
					}
				}

				return false;
			});
		}
	}

	public static GuildChannel getGuildChannel(Guild guild, String query) {
		GuildChannel channel = SearchUtility.getTextChannel(guild, query);
		if (channel == null) {
			channel = SearchUtility.getAudioChannel(guild, query);
		}

		if (channel == null) {
			channel = SearchUtility.getCategory(guild, query);

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

	public static AudioChannel getAudioChannel(Guild guild, String query) {
		Matcher mentionMatch = SearchUtility.CHANNEL_MENTION.matcher(query);
		if (mentionMatch.matches()) {
			try {
				return guild.getChannelById(AudioChannel.class, mentionMatch.group(1));
			} catch (NumberFormatException e) {
				return null;
			}
		} else if (NumberUtility.isNumberUnsigned(query)) {
			try {
				return guild.getChannelById(AudioChannel.class, query);
			} catch (NumberFormatException e) {
				return null;
			}
		} else {
			List<CacheView<? extends AudioChannel>> cacheViews = List.of(guild.getVoiceChannelCache(), guild.getStageChannelCache());
			List<AudioChannel> channels = cacheViews.stream().flatMap(CacheView::stream).distinct().collect(Collectors.toList());

			return SearchUtility.findAudioChannel(channels, query);
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
		} else {
			try {
				Member member = guild.getMemberById(query);
				if (member == null) {
					return SearchUtility.findMember(guild.getMemberCache(), query);
				} else {
					return member;
				}
			} catch (NumberFormatException e) {
				return SearchUtility.findMember(guild.getMemberCache(), query);
			}
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
		} else {
			try {
				return manager.retrieveUserById(query).submit();
			} catch (NumberFormatException e) {
				return CompletableFuture.completedFuture(
					manager.getUserCache().applyStream(stream ->
						stream.filter(user -> user.getName().equalsIgnoreCase(query))
							.findFirst()
							.orElse(null)
					)
				);
			}
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
	
	public static QueryMatches<Sx4Category> getModules(String query) {
		QueryMatches<Sx4Category> matches = new QueryMatches<>(query, 50);
		for (Sx4Category category : ModuleCategory.ALL_ARRAY) {
			matches.addValue(category, Sx4Category::getName);
			for (String alias : category.getAliases()) {
				matches.addValue(category, alias);
			}
		}

		return matches;
	}
	
	public static Sx4Command getCommand(CommandListener commandListener, String query) {
		return SearchUtility.getCommand(commandListener, query, true);
	}

	public static Sx4Command getCommand(CommandListener commandListener, String query, boolean includeDeveloper) {
		List<Sx4Command> commands = SearchUtility.getCommands(commandListener, query, includeDeveloper).toList();
		return commands.isEmpty() ? null : commands.get(0);
	}
	
	public static QueryMatches<Sx4Command> getCommands(CommandListener commandListener, String query) {
		return SearchUtility.getCommands(commandListener, query, true);
	}

	public static QueryMatches<Sx4Command> getCommands(CommandListener commandListener, String query, boolean includeDeveloper) {
		query = query.trim();
		
		QueryMatches<Sx4Command> matches = new QueryMatches<>(query, 50);
		for (ICommand commandObject : commandListener.getAllCommands(includeDeveloper, false)) {
			Sx4Command command = (Sx4Command) (commandObject instanceof DummyCommand ? ((DummyCommand) commandObject).getActualCommand() : commandObject);

			matches.addValue(command, Sx4Command::getCommandTrigger);

			for (String redirect : command.getRedirects()) {
				matches.addValue(command, redirect);
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
				matches.addValue(command, commandAlias);
			}
		}

		return matches;
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
