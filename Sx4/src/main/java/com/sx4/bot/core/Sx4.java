package com.sx4.bot.core;

import com.jockie.bot.core.argument.IArgument;
import com.jockie.bot.core.argument.factory.impl.ArgumentFactory;
import com.jockie.bot.core.argument.factory.impl.ArgumentFactoryImpl;
import com.jockie.bot.core.argument.factory.impl.BuilderConfigureFunction;
import com.jockie.bot.core.argument.impl.ArgumentImpl;
import com.jockie.bot.core.command.ICommand;
import com.jockie.bot.core.command.exception.parser.ArgumentParseException;
import com.jockie.bot.core.command.exception.parser.OutOfContentException;
import com.jockie.bot.core.command.factory.impl.MethodCommandFactory;
import com.jockie.bot.core.command.impl.CommandEvent;
import com.jockie.bot.core.command.impl.CommandListener;
import com.jockie.bot.core.command.impl.CommandListener.Failure;
import com.jockie.bot.core.command.impl.CommandStore;
import com.jockie.bot.core.command.impl.DummyCommand;
import com.jockie.bot.core.command.manager.IErrorManager;
import com.jockie.bot.core.command.manager.impl.ContextManagerFactory;
import com.jockie.bot.core.command.manager.impl.ErrorManagerImpl;
import com.jockie.bot.core.command.parser.ParseContext;
import com.jockie.bot.core.command.parser.impl.CommandParserImpl;
import com.jockie.bot.core.option.factory.impl.OptionFactory;
import com.jockie.bot.core.option.factory.impl.OptionFactoryImpl;
import com.jockie.bot.core.parser.IParser;
import com.jockie.bot.core.parser.ParsedResult;
import com.jockie.bot.core.utility.StringUtility.QuoteCharacter;
import com.mongodb.client.model.Projections;
import com.sx4.api.Sx4Server;
import com.sx4.bot.annotations.argument.*;
import com.sx4.bot.annotations.command.ChannelTypes;
import com.sx4.bot.cache.GoogleSearchCache;
import com.sx4.bot.cache.MessageCache;
import com.sx4.bot.cache.SteamGameCache;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.config.Config;
import com.sx4.bot.config.TwitchConfig;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.entities.argument.*;
import com.sx4.bot.entities.economy.item.Item;
import com.sx4.bot.entities.economy.item.ItemStack;
import com.sx4.bot.entities.info.Currency;
import com.sx4.bot.entities.info.game.FreeGame;
import com.sx4.bot.entities.info.game.FreeGameType;
import com.sx4.bot.entities.management.AutoRoleFilter;
import com.sx4.bot.entities.mod.PartialEmote;
import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.entities.mod.StickerArgument;
import com.sx4.bot.entities.twitch.TwitchStream;
import com.sx4.bot.entities.twitch.TwitchStreamType;
import com.sx4.bot.entities.twitch.TwitchStreamer;
import com.sx4.bot.entities.webhook.WebhookChannel;
import com.sx4.bot.entities.youtube.YouTubeChannel;
import com.sx4.bot.entities.youtube.YouTubeVideo;
import com.sx4.bot.formatter.input.InputFormatter;
import com.sx4.bot.formatter.input.InputFormatterManager;
import com.sx4.bot.formatter.output.FormatterManager;
import com.sx4.bot.formatter.output.function.FormatterParser;
import com.sx4.bot.formatter.output.function.FormatterResponse;
import com.sx4.bot.handlers.*;
import com.sx4.bot.managers.*;
import com.sx4.bot.paged.MessagePagedResult;
import com.sx4.bot.paged.PagedHandler;
import com.sx4.bot.paged.PagedManager;
import com.sx4.bot.utility.*;
import com.sx4.bot.utility.TimeUtility.OffsetTimeZone;
import com.sx4.bot.waiter.WaiterHandler;
import com.sx4.bot.waiter.WaiterManager;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.entities.emoji.UnicodeEmoji;
import net.dv8tion.jda.api.entities.sticker.GuildSticker;
import net.dv8tion.jda.api.entities.sticker.Sticker;
import net.dv8tion.jda.api.entities.sticker.StickerItem;
import net.dv8tion.jda.api.hooks.IEventManager;
import net.dv8tion.jda.api.hooks.InterfacedEventManager;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.dv8tion.jda.api.utils.MiscUtil;
import net.dv8tion.jda.api.utils.messages.MessageRequest;
import okhttp3.OkHttpClient;
import org.bson.Document;
import org.bson.json.JsonParseException;
import org.bson.types.ObjectId;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.util.List;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

public class Sx4 {

	public static final String GIT_HASH = "@GIT_HASH@";

	private final Config config = Config.get();
	private final TwitchConfig twitchConfig;

	private final MongoDatabase mongo;
	private final MongoDatabase mongoCanary;
	private final MongoDatabase mongoMain;

	private final CommandListener commandListener;
	private final ShardManager shardManager;
	private final OkHttpClient httpClient;
	private final ExecutorService executor;
	private final ScheduledExecutorService scheduledExecutor;

	private final SteamGameCache steamGameCache;
	private final MessageCache messageCache;
	private final GoogleSearchCache googleCache;

	private final LoggerHandler loggerHandler;
	private final ConnectionHandler connectionHandler;

	/* Managers */
	private final YouTubeManager youTubeManager;
	private final EconomyManager economyManager;
	private final MuteManager muteManager;
	private final GiveawayManager giveawayManager;
	private final PatreonManager patreonManager;
	private final ModLogManager modLogManager;
	private final ReminderManager reminderManager;
	private final StarboardManager starboardManager;
	private final TemporaryBanManager temporaryBanManager;
	private final WelcomerManager welcomerManager;
	private final LeaverManager leaverManager;
	private final ModActionManager modActionManager;
	private final SuggestionManager suggestionManager;
	private final PagedManager pagedManager;
	private final WaiterManager waiterManager;
	private final ServerStatsManager serverStatsManager;
	private final TwitchTokenManager twitchTokenManager;
	private final MysteryBoxManager mysteryBoxManager;
	private final SkinPortManager skinPortManager;
	private final FreeGameManager freeGameManager;
	private final TwitchManager twitchManager;
	private final GuessTheNumberManager guessTheNumberManager;
	
	private Sx4() {
		this.mongoMain = new MongoDatabase(this.config.getMainDatabase());
		this.mongoCanary = new MongoDatabase(this.config.getCanaryDatabase());

		this.mongo = this.config.isMain() ? this.mongoMain : this.mongoCanary;

		this.executor = Executors.newCachedThreadPool();
		this.scheduledExecutor = Executors.newSingleThreadScheduledExecutor();

		this.httpClient = new OkHttpClient.Builder()
			.connectTimeout(15, TimeUnit.SECONDS)
			.readTimeout(15, TimeUnit.SECONDS)
			.writeTimeout(15, TimeUnit.SECONDS)
			.build();

		this.twitchConfig = new TwitchConfig();

		Currency.pollCurrencies(this.httpClient);

		ContextManagerFactory.getDefault()
			.registerContext(Sx4CommandEvent.class, (event, type) -> (Sx4CommandEvent) event)
			.setEnforcedContext(Sx4CommandEvent.class, true);

		MessageRequest.setDefaultMentions(EnumSet.noneOf(Message.MentionType.class));

		MethodCommandFactory.setDefault(new Sx4CommandFactory());

		this.modLogManager = new ModLogManager(this);

		ModHandler modHandler = new ModHandler(this);
		YouTubeHandler youTubeHandler = new YouTubeHandler(this);
		TwitchHandler twitchHandler = new TwitchHandler(this);
		this.loggerHandler = new LoggerHandler(this);
		this.connectionHandler = new ConnectionHandler(this);

		this.economyManager = new EconomyManager();
		this.giveawayManager = new GiveawayManager(this);
		this.leaverManager = new LeaverManager(this);
		this.modActionManager = new ModActionManager().addListener(modHandler);
		this.muteManager = new MuteManager(this);
		this.patreonManager = new PatreonManager(this).addListener(new PatreonHandler(this));
		this.reminderManager = new ReminderManager(this);
		this.starboardManager = new StarboardManager(this);
		this.suggestionManager = new SuggestionManager(this);
		this.temporaryBanManager = new TemporaryBanManager(this);
		this.welcomerManager = new WelcomerManager(this);
		this.youTubeManager = new YouTubeManager(this).addListener(youTubeHandler);
		this.twitchManager = new TwitchManager(this).addListener(twitchHandler);
		this.pagedManager = new PagedManager();
		this.waiterManager = new WaiterManager();
		this.serverStatsManager = new ServerStatsManager(this);
		this.twitchTokenManager = new TwitchTokenManager(this);
		this.mysteryBoxManager = new MysteryBoxManager();
		this.skinPortManager = new SkinPortManager(this);
		this.freeGameManager = new FreeGameManager(this);
		this.guessTheNumberManager = new GuessTheNumberManager();

		this.steamGameCache = new SteamGameCache(this);
		this.messageCache = new MessageCache();
		this.googleCache = new GoogleSearchCache(this);

		this.setupArgumentFactory();
		this.setupOptionFactory();

		this.commandListener = this.createCommandListener(this.createErrorManager());
		((CommandParserImpl) this.commandListener.getCommandParser()).addOptionPrefix("");

		IEventManager manager = new InterfacedEventManager();
		manager.register(this.commandListener);
		manager.register(new PagedHandler(this));
		manager.register(new GiveawayHandler(this));
		manager.register(new AutoRoleHandler(this));
		manager.register(modHandler);
		manager.register(this.connectionHandler);
		manager.register(new ReactionRoleHandler(this));
		manager.register(this.loggerHandler);
		manager.register(new AntiRegexHandler(this));
		manager.register(new WelcomerHandler(this));
		manager.register(new LeaverHandler(this));
		manager.register(new StarboardHandler(this));
		manager.register(new TriggerHandler(this));
		manager.register(new ServerStatsHandler(this));
		manager.register(new SelfRoleHandler(this));
		manager.register(new MuteHandler(this));
		manager.register(new MediaModeHandler(this));
		manager.register(new MysteryBoxHandler(this));
		manager.register(new ServerLogHandler(this));
		manager.register(youTubeHandler);
		manager.register(new WaiterHandler(this));
		manager.register(new ButtonHandler(this));
		manager.register(new ModalHandler(this));
		manager.register(this.messageCache);

		this.shardManager = this.createShardManager(manager);

		InputFormatterManager inputFormatterManager = new InputFormatterManager()
			.addMapping("text", "min", InputFormatter.parseNumber("min", Integer::parseUnsignedInt))
			.addMapping("text", "max", InputFormatter.parseNumber("max", Integer::parseUnsignedInt))
			.addMapping("int", "min", InputFormatter.parseNumber("min", Long::parseLong))
			.addMapping("int", "max", InputFormatter.parseNumber("max", Long::parseLong))
			.addParser("member", (context, text, options) -> SearchUtility.getMember(context.getGuild(), text))
			.addParser("role", (context, text, options) -> SearchUtility.getRole(context.getGuild(), text))
			.addParser("prefix", (context, text, options) ->  context.getVariable("prefixes", Collections.emptyList()).contains(text) ? text : null)
			.addParser("text", (context, text, options) -> {
				int max = (int) options.getOrDefault("max", Integer.MAX_VALUE), min = (int) options.getOrDefault("min", 0);
				String name = (String) options.getOrDefault("name", "text");

				if (text.length() > max) {
					throw new IllegalArgumentException("`" + name + "` field was more than **" + max + "** characters");
				}

				if (text.length() < min) {
					throw new IllegalArgumentException("`" + name + "` field was less than **" + min + "** characters");
				}

				return text;
			}).addParser("int", (context, text, options) -> {
				long max = (long) options.getOrDefault("max", Long.MAX_VALUE), min = (long) options.getOrDefault("min", Long.MIN_VALUE);
				String name = (String) options.getOrDefault("name", "int");

				long number;
				try {
					number = Long.parseLong(text);
				} catch (NumberFormatException e) {
					throw new IllegalArgumentException("`" + name + "` field was not a number");
				}

				if (number > max) {
					throw new IllegalArgumentException("`" + name + "` field was more than **" + max + "**");
				}

				if (number < min) {
					throw new IllegalArgumentException("`" + name + "` field was less than **" + min + "**");
				}

				return number;
			});

		InputFormatterManager.setDefaultManager(inputFormatterManager);

		FormatterManager formatterManager = new FormatterManager()
			.addFunctions("com.sx4.bot.formatter.output.parser")
			.addVariable("id", "Gets the id of a twitch stream", TwitchStream.class, String.class, TwitchStream::getId)
			.addVariable("type", "Gets the type of a twitch stream", TwitchStream.class, TwitchStreamType.class, TwitchStream::getType)
			.addVariable("start", "Gets the start time of a twitch stream", TwitchStream.class, OffsetDateTime.class, TwitchStream::getStartTime)
			.addVariable("preview", "Gets the preview image of a twitch stream", TwitchStream.class, String.class, TwitchStream::getPreviewUrl)
			.addVariable("title", "Gets the title of a twitch stream", TwitchStream.class, String.class, TwitchStream::getTitle)
			.addVariable("game", "Gets the game name of a twitch stream", TwitchStream.class, String.class, TwitchStream::getGame)
			.addVariable("id", "Gets the id of a twitch streamer", TwitchStreamer.class, String.class, TwitchStreamer::getId)
			.addVariable("name", "Gets the name of a twitch streamer", TwitchStreamer.class, String.class, TwitchStreamer::getName)
			.addVariable("url", "Gets the url of a twitch streamer", TwitchStreamer.class, String.class, TwitchStreamer::getUrl)
			.addVariable("not", "Inverts a boolean value", Boolean.class, Boolean.class, bool -> !bool)
			.addVariable("id", "Gets the id of a message", Message.class, Long.class, Message::getIdLong)
			.addVariable("content", "Gets the content of a message", Message.class, String.class, Message::getContentRaw)
			.addVariable("channel", "Gets the channel the message is in", Message.class, MessageChannel.class, Message::getChannel)
			.addVariable("length", "Gets the length of a string", String.class, Integer.class, String::length)
			.addVariable("isNumber", "Gets whether or not this string is a number", String.class, Boolean.class, NumberUtility::isNumber)
			.addVariable("urlEncode", "Encodes a string to a URL standard", String.class, String.class, string -> URLEncoder.encode(string, StandardCharsets.UTF_8))
			.addVariable("status", "Gets the status code of the response", FormatterResponse.class, Integer.class, FormatterResponse::getStatus)
			.addVariable("raw", "Gets the raw response body", FormatterResponse.class, String.class, FormatterResponse::getRaw)
			.addVariable("json", "Gets the response body as json", FormatterResponse.class, Document.class, FormatterResponse::asJson)
			.addVariable("array", "Gets the response body as a json array", FormatterResponse.class, Collection.class, FormatterResponse::asArray)
			.addVariable("icon", "Get the icon url of the free game platform", FreeGameType.class, String.class, FreeGameType::getIconUrl)
			.addVariable("name", "Get the name of the free game platform", FreeGameType.class, String.class, FreeGameType::getName)
			.addVariable("platform", "Get the platform of the free game", FreeGame.class, FreeGameType.class, FreeGame::getType)
			.addVariable("title", "Gets the title of the game", FreeGame.class, String.class, FreeGame::getTitle)
			.addVariable("description", "Gets the description for the game", FreeGame.class, String.class, FreeGame::getDescription)
			.addVariable("publisher", "Gets the publisher of the game", FreeGame.class, String.class, FreeGame::getPublisher)
			.addVariable("image", "Gets the image of the game", FreeGame.class, String.class, FreeGame::getImage)
			.addVariable("url", "Gets the Epic Games url for the game", FreeGame.class, String.class, FreeGame::getUrl)
			.addVariable("run_url", "Gets the Epic Games client url for the game", FreeGame.class, String.class, FreeGame::getRunUrl)
			.addVariable("promotion_start", "Gets the start date of the promotion", FreeGame.class, OffsetDateTime.class, FreeGame::getPromotionStart)
			.addVariable("promotion_end", "Gets the end date of the promotion", FreeGame.class, OffsetDateTime.class, FreeGame::getPromotionEnd)
			.addVariable("price", "Gets the updated price of the game", FreeGame.class, Currency.class, game -> new Currency(game.getDiscountPriceDecimal(), "GBP"))
			.addVariable("original_price", "Gets the original price of the game", FreeGame.class, Currency.class, game -> new Currency(game.getOriginalPriceDecimal(), "GBP"))
			.addVariable("dlc", "Returns true if the game is a DLC", FreeGame.class, Boolean.class, FreeGame::isDLC)
			.addVariable("suffix", "Gets the suffixed version of a number", Integer.class, String.class, NumberUtility::getSuffixed)
			.addVariable("round", "Gets the rounded number", Double.class, Long.class, Math::round)
			.addVariable("floor", "Gets the floored number", Double.class, Double.class, Math::floor)
			.addVariable("ceil", "Gets the ceiled number", Double.class, Double.class, Math::ceil)
			.addVariable("length", "Gets the length of the list", Collection.class, Integer.class, Collection::size)
			.addVariable("empty", "Gets whether the list is empty or not", Collection.class, Boolean.class, Collection::isEmpty)
			.addVariable("name", "Gets the name of the role", Role.class, String.class, Role::getName)
			.addVariable("id", "Gets the id or the role", Role.class, Long.class, Role::getIdLong)
			.addVariable("created", "Gets the date the role was created", Role.class, OffsetDateTime.class, Role::getTimeCreated)
			.addVariable("colour", "Gets the colour of the role", Role.class, Color.class, Role::getColor)
			.addVariable("color", "Gets the color of the role", Role.class, Color.class, Role::getColor)
			.addVariable("raw", "Gets the raw RGB value of the colour", Color.class, Integer.class, Color::getRGB)
			.addVariable("hex", "Gets the hex code of the colour", Color.class, String.class, colour -> "#" + ColourUtility.toHexString(colour.getRGB()))
			.addVariable("name", "Gets the name of the emote", EmojiUnion.class, String.class, Emoji::getName)
			.addVariable("id", "Gets the id of the emote", EmojiUnion.class, Object.class, emoji -> emoji instanceof UnicodeEmoji ? emoji.getName() : emoji.asCustom().getIdLong())
			.addVariable("mention", "Gets the mention of the emote", EmojiUnion.class, String.class, emoji -> emoji instanceof UnicodeEmoji ? emoji.getName() : emoji.asCustom().getAsMention())
			.addVariable("created", "Gets the date when the emote was created", EmojiUnion.class, OffsetDateTime.class, emoji -> emoji instanceof UnicodeEmoji ? OffsetDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC) : emoji.asCustom().getTimeCreated())
			.addVariable("raw", "Gets the raw value of the permission", Permission.class, Long.class, Permission::getRawValue)
			.addVariable("name", "Gets the name of the permission", Permission.class, String.class, Permission::getName)
			.addVariable("permissions", "Gets the permissions of the role or user", IPermissionHolder.class, Collection.class, IPermissionHolder::getPermissions)
			.addVariable("mention", "Gets the mention of the entity", IMentionable.class, String.class, IMentionable::getAsMention)
			.addVariable("id", "Gets the id of the channel", GuildChannel.class, Long.class, GuildChannel::getIdLong)
			.addVariable("name", "Gets the name of the channel", GuildChannel.class, String.class, GuildChannel::getName)
			.addVariable("created", "Gets the date the channel was created", GuildChannel.class, OffsetDateTime.class, GuildChannel::getTimeCreated)
			.addVariable("slowmode", "Gets the slowmode of the text channel", TextChannel.class, Integer.class, TextChannel::getSlowmode)
			.addVariable("bitrate", "Gets the bitrate of the voice channel", AudioChannel.class, Integer.class, AudioChannel::getBitrate)
			.addVariable("limit", "Gets the user limit of the voice channel", VoiceChannel.class, Integer.class, VoiceChannel::getUserLimit)
			.addVariable("name", "Gets the name of the server", Guild.class, String.class, Guild::getName)
			.addVariable("id", "Gets the id of the server", Guild.class, Long.class, Guild::getIdLong)
			.addVariable("owner", "Gets the owner of the server", Guild.class, Member.class, Guild::getOwner)
			.addVariable("boosts", "Gets the boost count of the server", Guild.class, Integer.class, Guild::getBoostCount)
			.addVariable("boosters", "Gets the members boosting the server", Guild.class, Collection.class, Guild::getBoosters)
			.addVariable("members", "Gets the member count of the server", Guild.class, Integer.class, Guild::getMemberCount)
			.addVariable("avatar", "Gets the icon url of the server", Guild.class, String.class, Guild::getIconUrl)
			.addVariable("created", "Gets the date when the server was created", Guild.class, OffsetDateTime.class, Guild::getTimeCreated)
			.addVariable("user", "Gets the user of the member", Member.class, User.class, Member::getUser)
			.addVariable("nickname", "Gets the nickname of the member", Member.class, String.class, Member::getNickname)
			.addVariable("roles", "Gets the roles of the member", Member.class, Collection.class, Member::getRoles)
			.addVariable("colour", "Gets the colour of the member", Member.class, Color.class, Member::getColor)
			.addVariable("color", "Gets the color of the member", Member.class, Color.class, Member::getColor)
			.addVariable("joined", "Gets the date when the member joined the server", Member.class, OffsetDateTime.class, Member::getTimeJoined)
			.addVariable("id", "Gets the id of the user", User.class, Long.class, User::getIdLong)
			.addVariable("name", "Gets the name of the user", User.class, String.class, User::getName)
			.addVariable("avatar", "Gets the avatar url of the user", User.class, String.class, User::getEffectiveAvatarUrl)
			.addVariable("discriminator", "Gets the discriminator of the user", User.class, String.class, User::getDiscriminator)
			.addVariable("badges", "Gets the badges of the user", User.class, Collection.class, User::getFlags)
			.addVariable("tag", "Gets the tag of the user, name#discriminator", User.class, String.class, User::getAsTag)
			.addVariable("created", "Gets the date when the user was created", User.class, OffsetDateTime.class, User::getTimeCreated)
			.addVariable("name", "Gets the name of the badge", User.UserFlag.class, String.class, User.UserFlag::getName)
			.addVariable("raw", "Gets the raw value of the badge", User.UserFlag.class, Long.class, User.UserFlag::getRawValue)
			.addVariable("offset", "Gets the offset of the badge", User.UserFlag.class, Integer.class, User.UserFlag::getOffset)
			.addVariable("day", "Gets the day of the month of the date", OffsetDateTime.class, Integer.class, OffsetDateTime::getDayOfMonth)
			.addVariable("month", "Gets the month of the year of the date", OffsetDateTime.class, Integer.class, OffsetDateTime::getMonthValue)
			.addVariable("year", "Gets the year of the date", OffsetDateTime.class, Integer.class, OffsetDateTime::getYear)
			.addVariable("epoch", "Gets the epoch seconds of the date", OffsetDateTime.class, Long.class, OffsetDateTime::toEpochSecond)
			.addVariable("id", "Gets the id of the YouTube video", YouTubeVideo.class, String.class, YouTubeVideo::getId)
			.addVariable("url", "Gets the url of the YouTube video", YouTubeVideo.class, String.class, YouTubeVideo::getUrl)
			.addVariable("title", "Gets the title of the YouTube video", YouTubeVideo.class, String.class, YouTubeVideo::getTitle)
			.addVariable("thumbnail", "Gets the thumbnail of the YouTube video", YouTubeVideo.class, String.class, YouTubeVideo::getThumbnail)
			.addVariable("published", "Gets the date when the YouTube video was published", YouTubeVideo.class, OffsetDateTime.class, YouTubeVideo::getPublishedAt)
			.addVariable("id", "Gets the id of the YouTube channel", YouTubeChannel.class, String.class, YouTubeChannel::getId)
			.addVariable("url", "Gets the url of the YouTube channel", YouTubeChannel.class, String.class, YouTubeChannel::getUrl)
			.addVariable("name", "Gets the name of the YouTube channel", YouTubeChannel.class, String.class, YouTubeChannel::getName)
			.addParser(String.class, Function.identity())
			.addParser(Boolean.class, text -> {
				if (text.equals("true")) {
					return true;
				} else {
					return false;
				}
			}).addParser(Temporal.class, text -> {
				try {
					return OffsetDateTime.parse(text);
				} catch (DateTimeParseException e) {
					return null;
				}
			}).addParser(Integer.class, text -> {
				try {
					return Integer.parseInt(text);
				} catch (NumberFormatException e) {
					return null;
				}
			}).addParser(Long.class, text -> {
				try {
					return Long.parseLong(text);
				} catch (NumberFormatException e) {
					return null;
				}
			}).addParser(Double.class, text -> {
				try {
					return Double.parseDouble(text);
				} catch (NumberFormatException e) {
					return null;
				}
			}).addParser(Number.class, text -> {
				try {
					return Double.parseDouble(text);
				} catch (NumberFormatException e) {
					return null;
				}
			}).addParser(Currency.class, text -> {
				try {
					return new Currency(Double.parseDouble(text));
				} catch (NumberFormatException e) {
					return null;
				}
			});

		formatterManager.addParser(Object.class, text -> {
			Map<Class<?>, FormatterParser<?>> parsers = formatterManager.getParsers();
			for (Class<?> key : parsers.keySet()) {
				if (key == Object.class) {
					continue;
				}

				FormatterParser<?> parser = parsers.get(key);

				Object value = parser.parse(text);
				if (value != null) {
					return value;
				}
			}

			return null;
		}).addParser(Collection.class, text -> {
			if (text.isEmpty() || text.charAt(0) != '[' || text.charAt(text.length() - 1) != ']') {
				return null;
			}

			FormatterParser<?> parser = formatterManager.getParser(Object.class);

			String textList = text.substring(1, text.length() - 1);
			char[] characters = textList.toCharArray();
			List<Object> collection = new ArrayList<>();
			int lastIndex = 0;

			for (int i = 0; i < characters.length; i++) {
				char character = characters[i];
				if (character == ',') {
					if (i != 0 && characters[i - 1] == '\\') {
						continue;
					}

					collection.add(parser.parse(textList.substring(lastIndex, i)));
					lastIndex = i + 1;
				}
			}

			collection.add(parser.parse(textList.substring(lastIndex)));

			return collection;
		});

		FormatterManager.setDefaultManager(formatterManager);
	}

	public MongoDatabase getMongo() {
		return this.mongo;
	}

	public MongoDatabase getMongoMain() {
		return this.mongoMain;
	}

	public MongoDatabase getMongoCanary() {
		return this.mongoCanary;
	}

	public Config getConfig() {
		return this.config;
	}

	public TwitchConfig getTwitchConfig() {
		return this.twitchConfig;
	}

	public OkHttpClient getHttpClient() {
		return this.httpClient;
	}

	public ExecutorService getExecutor() {
		return this.executor;
	}

	public ScheduledExecutorService getScheduledExecutor() {
		return this.scheduledExecutor;
	}
	
	public CommandListener getCommandListener() {
		return this.commandListener;
	}
	
	public ShardManager getShardManager() {
		return this.shardManager;
	}

	public EconomyManager getEconomyManager() {
		return this.economyManager;
	}

	public MuteManager getMuteManager() {
		return this.muteManager;
	}

	public GiveawayManager getGiveawayManager() {
		return this.giveawayManager;
	}

	public ModLogManager getModLogManager() {
		return this.modLogManager;
	}

	public LeaverManager getLeaverManager() {
		return this.leaverManager;
	}

	public ModActionManager getModActionManager() {
		return this.modActionManager;
	}

	public YouTubeManager getYouTubeManager() {
		return this.youTubeManager;
	}

	public PatreonManager getPatreonManager() {
		return this.patreonManager;
	}

	public ReminderManager getReminderManager() {
		return this.reminderManager;
	}

	public StarboardManager getStarboardManager() {
		return this.starboardManager;
	}

	public TemporaryBanManager getTemporaryBanManager() {
		return this.temporaryBanManager;
	}

	public WelcomerManager getWelcomerManager() {
		return this.welcomerManager;
	}

	public SuggestionManager getSuggestionManager() {
		return this.suggestionManager;
	}

	public PagedManager getPagedManager() {
		return this.pagedManager;
	}

	public WaiterManager getWaiterManager() {
		return this.waiterManager;
	}

	public ServerStatsManager getServerStatsManager() {
		return this.serverStatsManager;
	}

	public TwitchTokenManager getTwitchTokenManager() {
		return this.twitchTokenManager;
	}

	public MysteryBoxManager getMysteryBoxManager() {
		return this.mysteryBoxManager;
	}

	public SkinPortManager getSkinPortManager() {
		return this.skinPortManager;
	}

	public FreeGameManager getFreeGameManager() {
		return this.freeGameManager;
	}

	public TwitchManager getTwitchManager() {
		return this.twitchManager;
	}

	public GuessTheNumberManager getGuessTheNumberManager() {
		return this.guessTheNumberManager;
	}

	public SteamGameCache getSteamGameCache() {
		return this.steamGameCache;
	}

	public MessageCache getMessageCache() {
		return this.messageCache;
	}

	public GoogleSearchCache getGoogleCache() {
		return this.googleCache;
	}

	public LoggerHandler getLoggerHandler() {
		return this.loggerHandler;
	}

	public ConnectionHandler getConnectionHandler() {
		return this.connectionHandler;
	}

	public ShardManager createShardManager(IEventManager manager) {
		try {
			return DefaultShardManagerBuilder.create(this.config.getToken(), GatewayIntent.getIntents(38606))
				.setBulkDeleteSplittingEnabled(false)
				.setEventManagerProvider(shardId -> manager)
				.setActivity(Activity.watching("s?help"))
				.build();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
			System.exit(1);
			return null;
		}
	}

	private CommandListener createCommandListener(IErrorManager errorManager) {
		return new Sx4CommandListener(this)
			.removePreExecuteCheck(listener -> listener.defaultAuthorPermissionCheck)
			.removePreExecuteCheck(listener -> listener.defaultBotPermissionCheck)
			.addCommandStores(CommandStore.of("com.sx4.bot.commands"))
			.addDevelopers(this.config.getOwnerIds())
			.setErrorManager(errorManager)
			.setCommandEventFactory(new Sx4CommandEventFactory(this))
			.addCommandEventListener(new Sx4CommandEventListener(this))
			.setDefaultPrefixes(this.config.getDefaultPrefixes().toArray(String[]::new))
			.addPreParseCheck(message -> !message.getAuthor().isBot())
			.addPreExecuteCheck((event, command) -> {
				if (this.config.isCanary() || event.isFromType(ChannelType.PRIVATE)) {
					return true;
				}

				List<String> guildPrefixes = event.getMessage().isFromGuild() ? this.mongoCanary.getGuildById(event.getGuild().getIdLong(), Projections.include("prefixes")).getList("prefixes", String.class, Collections.emptyList()) : Collections.emptyList();
				List<String> userPrefixes = this.mongoCanary.getUserById(event.getAuthor().getIdLong(), Projections.include("prefixes")).getList("prefixes", String.class, Collections.emptyList());

				List<String> prefixes = userPrefixes.isEmpty() ? guildPrefixes.isEmpty() ? this.config.getDefaultPrefixes() : guildPrefixes : userPrefixes;
				event.setProperty("canaryPrefixes", prefixes);

				return CheckUtility.canReply(this, event.getMessage(), event.getPrefix(), prefixes);
			}).addPreExecuteCheck((event, cmd) -> {
				if (!(cmd instanceof Sx4Command command)) {
					return true;
				}

				if (command.isCanaryCommand() && this.config.isMain()) {
					event.reply("This command can only be used on the canary version of the bot " + this.config.getFailureEmote()).queue();
					return false;
				}

				if (command.isDisabled()) {
					event.reply(command.getDisabledMessage()).queue();
					return false;
				}

				return true;
			}).addPreExecuteCheck((event, command) -> {
				if (event.isFromGuild()) {
					Document guildData = this.mongo.getGuildById(event.getGuild().getIdLong(), Projections.include("fakePermissions.holders"));

					List<Document> holders = guildData.getEmbedded(List.of("fakePermissions", "holders"), Collections.emptyList());
					event.setProperty("fakePermissions", holders);
				} else {
					event.setProperty("fakePermissions", Collections.emptyList());
				}

				return true;
			}).addPreExecuteCheck((event, command) -> {
				if (event.isFromType(ChannelType.PRIVATE)) {
					return true;
				}

				Set<Permission> permissions = command.getAuthorDiscordPermissions();
				if (permissions.isEmpty()) {
					return true;
				}

				if (permissions.contains(Permission.MANAGE_ROLES) && !CheckUtility.hasPermissions(this, event.getMember(), event.getProperty("fakePermissions"), EnumSet.of(Permission.MANAGE_ROLES))) {
					event.reply(PermissionUtility.formatMissingPermissions(EnumSet.of(Permission.MANAGE_ROLES)) + " " + this.config.getFailureEmote()).queue();
					return false;
				}

				EnumSet<Permission> missingPermissions = CheckUtility.missingPermissions(this, event.getMember(), event.getGuildChannel(), event.getProperty("fakePermissions"), EnumSet.copyOf(permissions));
				if (missingPermissions.isEmpty()) {
					return true;
				} else {
					event.reply(PermissionUtility.formatMissingPermissions(missingPermissions) + " " + this.config.getFailureEmote()).queue();
					return false;
				}
			}).addPreExecuteCheck((event, command) -> {
				if (event.isFromType(ChannelType.PRIVATE)) {
					return true;
				}

				Set<Permission> permissions = command.getBotDiscordPermissions();
				if (permissions.isEmpty()) {
					return true;
				}

				if (permissions.contains(Permission.MANAGE_ROLES) && !event.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
					event.reply(PermissionUtility.formatMissingPermissions(EnumSet.of(Permission.MANAGE_ROLES), "I am") + " " + this.config.getFailureEmote()).queue();
					return false;
				}

				EnumSet<Permission> missingPermissions = Permission.getPermissions(Permission.getRaw(permissions) & ~Permission.getRaw(event.getSelfMember().getPermissions((GuildMessageChannel) event.getChannel())));
				if (missingPermissions.isEmpty()) {
					return true;
				} else {
					event.reply(PermissionUtility.formatMissingPermissions(missingPermissions, "I am") + " " + this.config.getFailureEmote()).queue();
					return false;
				}
			}).addPreExecuteCheck((event, command) -> {
				Sx4Command effectiveCommand = (Sx4Command) (command instanceof DummyCommand ? ((DummyCommand) command).getActualCommand() : command);
				if (event.isFromGuild()) {
					boolean canUseCommand = CheckUtility.canUseCommand(this, event.getMember(), event.getChannel(), effectiveCommand);
					if (!canUseCommand) {
						event.reply("You are blacklisted from using that command in this channel " + this.config.getFailureEmote()).queue();
					}

					return canUseCommand;
				} else {
					return true;
				}
			}).setHelpFunction((message, prefix, commands) -> {
				if (!CheckUtility.canReply(this, message, prefix)) {
					return;
				}

				MessageChannel channel = message.getChannel();
				boolean embed = !message.isFromGuild() || message.getGuild().getSelfMember().hasPermission((GuildChannel) channel, Permission.MESSAGE_EMBED_LINKS);
				
				channel.sendMessage(HelpUtility.getHelpMessage(commands.get(0), embed)).queue();
			}).setMessageParseFailureFunction((message, prefix, failures) -> {
				if (!CheckUtility.canReply(this, message, prefix)) {
					return;
				}

				Failure failure = failures.stream()
					.filter(f -> {
						Throwable reason = f.getReason();
						
						return reason instanceof ArgumentParseException && !(reason instanceof OutOfContentException);
					})
					.findFirst()
					.orElse(null);
				
				if (failure != null) {
					ArgumentParseException parseException = (ArgumentParseException) failure.getReason();

					IArgument<?> argument = parseException.getArgument();
					String value = parseException.getValue();
					
					if (message.getChannelType().isGuild()) {
						Member bot = message.getGuild().getSelfMember();
						
						if (!bot.hasPermission(Permission.MESSAGE_SEND)) {
							message.getAuthor().openPrivateChannel()
								.flatMap(channel -> channel.sendMessage("I am missing the `" + Permission.MESSAGE_SEND.getName() + "` permission in " + message.getChannel().getAsMention() + " " + this.config.getFailureEmote()))
								.queue();
							
							return;
						}
					}
					
					BiConsumer<Message, String> errorConsumer = argument.getErrorConsumer();
					if (errorConsumer != null) {
						errorConsumer.accept(message, value);
						
						return;
					}

					Class<?> failedClass = (Class<?>) argument.getProperty("failedClass", ThreadLocal.class).get();

					IArgument<?> copy = failedClass == null ? argument : new ArgumentImpl.Builder<>(failedClass)
						.setProperties(argument.getProperties())
						.setName(argument.getName())
						.build();

					if (errorManager.handle(copy, message, value)) {
						return;
					}
				}

				MessageChannel channel = message.getChannel();
				boolean embed = message.isFromGuild() || message.getGuild().getSelfMember().hasPermission((GuildChannel) channel, Permission.MESSAGE_EMBED_LINKS);

				ICommand firstCommand = failures.get(0).getCommand();

				List<ICommand> commands = failures.stream()
					.map(Failure::getCommand)
					.map(command -> command instanceof DummyCommand ? ((DummyCommand) command).getActualCommand() : command)
					.distinct()
					.filter(command -> command.getCommandTrigger().equals(firstCommand.getCommandTrigger()))
					.collect(Collectors.toList());

				MessagePagedResult<ICommand> paged = new MessagePagedResult.Builder<>(this, commands)
					.setAuthor("Commands", null, message.getAuthor().getEffectiveAvatarUrl())
					.setAutoSelect(true)
					.setPerPage(15)
					.setSelectablePredicate((content, command) -> command.getCommandTrigger().equals(content))
					.setDisplayFunction(command -> command.getUsage())
					.build();

				paged.onSelect(select -> channel.sendMessage(HelpUtility.getHelpMessage(select.getSelected(), embed)).queue());

				paged.execute(message);
			}).setPrefixesFunction(message -> {
				List<String> guildPrefixes = message.isFromGuild() ? this.mongo.getGuildById(message.getGuild().getIdLong(), Projections.include("prefixes")).getList("prefixes", String.class, Collections.emptyList()) : Collections.emptyList();
				List<String> userPrefixes = this.mongo.getUserById(message.getAuthor().getIdLong(), Projections.include("prefixes")).getList("prefixes", String.class, Collections.emptyList());

				return userPrefixes.isEmpty() ? guildPrefixes.isEmpty() ? this.config.getDefaultPrefixes() : guildPrefixes : userPrefixes;
			}).setCooldownFunction((event, cooldown) -> {
				if (!CheckUtility.canReply(this, event.getMessage(), event.getPrefix(), event.getProperty("canaryPrefixes"))) {
					return;
				}

				ICommand command = event.getCommand();
				if (command instanceof Sx4Command sx4Command) {
					if (sx4Command.hasCooldownMessage()) {
						event.reply(sx4Command.getCooldownMessage()).queue();
						return;
					}
				}

				event.reply("Slow down there! You can execute this command again in " + TimeUtility.LONG_TIME_FORMATTER.parse(Duration.of(cooldown.getTimeRemainingMillis(), ChronoUnit.MILLIS)) + " :stopwatch:").queue();
			}).setNSFWFunction(event -> {
				if (!CheckUtility.canReply(this, event.getMessage(), event.getPrefix(), event.getProperty("canaryPrefixes"))) {
					return;
				}

				event.reply("You cannot use this command in a non-nsfw channel " + this.config.getFailureEmote()).queue();
			}).setMissingPermissionExceptionFunction((event, permission) -> {
				if (!CheckUtility.canReply(this, event.getMessage(), event.getPrefix(), event.getProperty("canaryPrefixes"))) {
					return;
				}

				String message = PermissionUtility.formatMissingPermissions(EnumSet.of(permission), "I am") + " " + this.config.getFailureEmote();
				if (event.getSelfMember().hasPermission((GuildMessageChannel) event.getChannel(), Permission.MESSAGE_SEND)) {
					event.reply(message).queue();
				} else {
					event.getAuthor().openPrivateChannel()
						.flatMap(channel -> channel.sendMessage(message))
						.queue();
				}
			});
	}

	private void setupOptionFactory() {
		OptionFactoryImpl optionFactory = (OptionFactoryImpl) OptionFactory.getDefault();

		optionFactory.addBuilderConfigureFunction(Integer.class, (parameter, builder) -> {
			builder.setProperty("colour", parameter.isAnnotationPresent(Colour.class));

			Limit limit = parameter.getAnnotation(Limit.class);
			if (limit != null) {
				builder.setProperty("limit", limit);
			}

			DefaultNumber defaultInt = parameter.getAnnotation(DefaultNumber.class);
			if (defaultInt != null) {
				builder.setDefaultValue((int) defaultInt.value());
			}

			return builder;
		}).addBuilderConfigureFunction(Long.class, (parameter, builder) -> {
			DefaultNumber defaultLong = parameter.getAnnotation(DefaultNumber.class);
			if (defaultLong != null) {
				builder.setDefaultValue((long) defaultLong.value());
			}

			return builder;
		}).addBuilderConfigureFunction(Double.class, (parameter, builder) -> {
			DefaultNumber defaultLong = parameter.getAnnotation(DefaultNumber.class);
			if (defaultLong != null) {
				builder.setDefaultValue(defaultLong.value());
			}

			return builder;
		}).addBuilderConfigureFunction(String.class, (parameter, builder) -> {
			builder.setProperty("lowercase", parameter.isAnnotationPresent(Lowercase.class));
			builder.setProperty("uppercase", parameter.isAnnotationPresent(Uppercase.class));

			DefaultString defaultString = parameter.getAnnotation(DefaultString.class);
			if (defaultString != null) {
				builder.setDefaultValue(defaultString.value());
			}

			Options options = parameter.getAnnotation(Options.class);
			if (options != null) {
				builder.setProperty("options", options.value());
			}

			return builder;
		}).addBuilderConfigureFunction(Locale.class, (parameter, builder) -> {
			DefaultLocale defaultLocale = parameter.getAnnotation(DefaultLocale.class);
			if (defaultLocale != null) {
				builder.setDefaultValue(SearchUtility.getLocaleFromTag(defaultLocale.value()));
			}

			return builder;
		});

		optionFactory.registerParser(Duration.class, (context, option, content) -> content == null ? new ParsedResult<>(true, null) : new ParsedResult<>(TimeUtility.getDurationFromString(content)))
			.registerParser(Guild.class, (context, argument, content) -> new ParsedResult<>(SearchUtility.getGuild(this.shardManager, content)))
			.registerParser(TextChannel.class, (context, argument, content) -> new ParsedResult<>(SearchUtility.getTextChannel(context.getMessage().getGuild(), content.trim())))
			.registerParser(Sx4Command.class, (context, argument, content) -> new ParsedResult<>(SearchUtility.getCommand(this.commandListener, content.trim())))
			.registerParser(Locale.class, (context, argument, content) -> new ParsedResult<>(SearchUtility.getLocale(content.trim())))
			.registerParser(Integer.class, (context, argument, content) -> {
				if (argument.getProperty("colour", false)) {
					int colour = ColourUtility.fromQuery(content);
					if (colour == -1) {
						return new ParsedResult<>();
					} else {
						return new ParsedResult<>(colour);
					}
				}

				try {
					return new ParsedResult<>(Integer.parseInt(content));
				} catch (NumberFormatException e) {
					return new ParsedResult<>();
				}
			}).registerParser(String.class, (context, argument, content) -> {
				String[] options = argument.getProperty("options");
				if (argument.getProperty("class") == null && options != null && options.length != 0) {
					for (String option : options) {
						if (option.equalsIgnoreCase(content)) {
							return new ParsedResult<>(option);
						}
					}

					return new ParsedResult<>();
				}

				return new ParsedResult<>(content);
			});

		optionFactory.addParserAfter(Integer.class, (context, argument, content) -> {
			Limit limit = argument.getProperty("limit", Limit.class);
			if (limit != null) {
				content = Math.min(Math.max(limit.min(), content), limit.max());
			}

			return new ParsedResult<>(content);
		}).addParserAfter(String.class, (context, argument, content) -> {
			if (argument.getProperty("lowercase", false)) {
				content = content.toLowerCase();
			}

			if (argument.getProperty("uppercase", false)) {
				content = content.toUpperCase();
			}

			Limit limit = argument.getProperty("limit", Limit.class);
			if (limit != null && limit.error() && (content.length() < limit.min() || content.length() > limit.max())) {
				return new ParsedResult<>();
			} else if (limit != null && !limit.error()) {
				content = content.substring(Math.min(Math.max(0, limit.min()), content.length()), Math.min(Math.max(0, limit.max()), content.length()));
			}

			return new ParsedResult<>(content);
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private void setupArgumentFactory() {
		ArgumentFactoryImpl argumentFactory = (ArgumentFactoryImpl) ArgumentFactory.getDefault();

		argumentFactory.addBuilderFunction(parameter -> {
			return new ArgumentImpl.Builder<>(parameter.getType())
				.setProperty("failedClass", new ThreadLocal<>())
				.setProperty("command", new ThreadLocal<>())
				.setProperty("parameter", parameter);
		});

		argumentFactory.addBuilderConfigureFunction(RichCustomEmoji.class, (parameter, builder) -> builder.setProperty("global", parameter.isAnnotationPresent(Global.class)))
			.addBuilderConfigureFunction(Attachment.class, (parameter, builder) -> builder.setAcceptEmpty(true))
			.addBuilderConfigureFunction(MessageArgument.class, (parameter, builder) -> builder.setAcceptEmpty(true))
			.addBuilderConfigureFunction(EmojiUnion.class, (parameter, builder) -> builder.setProperty("unchecked", parameter.isAnnotationPresent(Unchecked.class)))
			.addGenericBuilderConfigureFunction(Object.class, (parameter, builder) -> {
				builder.setProperty("failedClass", new ThreadLocal<>());
				builder.setProperty("command", new ThreadLocal<>());
				builder.setProperty("parameter", parameter);

				return builder;
			}).addGenericBuilderConfigureFunction(Channel.class, (parameter, builder) -> {
				if (builder.build().hasDefault() && !parameter.isAnnotationPresent(DefaultNull.class)) {
					builder.setDefaultValue(event -> {
						if (parameter.getType().isInstance(event.getChannel())) {
							return event.getChannel();
						}

						return null;
					});
				}

				ChannelTypes channelTypes = parameter.getAnnotation(ChannelTypes.class);
				builder.setProperty("channelTypes", channelTypes == null ? ChannelTypes.DEFAULT : channelTypes.value());

				return builder;
			}).addBuilderConfigureFunction(WebhookChannel.class, (parameter, builder) -> {
				if (builder.build().hasDefault()) {
					builder.setDefaultValue(event -> new WebhookChannel((GuildMessageChannelUnion) event.getChannel()));
				}

				return builder;
			}).addBuilderConfigureFunction(String.class, (parameter, builder) -> {
				builder.setProperty("imageUrl", parameter.isAnnotationPresent(ImageUrl.class));
				builder.setProperty("url", parameter.isAnnotationPresent(Url.class));
				builder.setProperty("lowercase", parameter.isAnnotationPresent(Lowercase.class));
				builder.setProperty("uppercase", parameter.isAnnotationPresent(Uppercase.class));

				Replace replace = parameter.getAnnotation(Replace.class);
				if (replace != null) {
					builder.setProperty("replace", replace);
				}

				Limit limit = parameter.getAnnotation(Limit.class);
				if (limit != null) {
					builder.setProperty("limit", limit);
				}

				DefaultString defaultString = parameter.getAnnotation(DefaultString.class);
				if (defaultString != null) {
					builder.setDefaultValue(defaultString.value());
				}

				Options options = parameter.getAnnotation(Options.class);
				if (options != null) {
					builder.setProperty("options", options.value());
				}

				return builder;
			}).addBuilderConfigureFunction(Integer.class, (parameter, builder) -> {
				builder.setProperty("colour", parameter.isAnnotationPresent(Colour.class));

				Limit limit = parameter.getAnnotation(Limit.class);
				if (limit != null) {
					builder.setProperty("limit", limit);
				}

				DefaultNumber defaultInt = parameter.getAnnotation(DefaultNumber.class);
				if (defaultInt != null) {
					builder.setDefaultValue((int) defaultInt.value());
				}

				return builder;
			}).addBuilderConfigureFunction(Long.class, (parameter, builder) -> {
				builder.setProperty("userId", parameter.isAnnotationPresent(UserId.class));

				DefaultNumber defaultLong = parameter.getAnnotation(DefaultNumber.class);
				if (defaultLong != null) {
					builder.setDefaultValue((long) defaultLong.value());
				}

				return builder;
			}).addBuilderConfigureFunction(Double.class, (parameter, builder) -> {
				Limit limit = parameter.getAnnotation(Limit.class);
				if (limit != null) {
					builder.setProperty("limit", limit);
				}

				DefaultNumber defaultLong = parameter.getAnnotation(DefaultNumber.class);
				if (defaultLong != null) {
					builder.setDefaultValue(defaultLong.value());
				}

				return builder;
			}).addBuilderConfigureFunction(Locale.class, (parameter, builder) -> {
				DefaultLocale defaultLocale = parameter.getAnnotation(DefaultLocale.class);
				if (defaultLocale != null) {
					builder.setDefaultValue(SearchUtility.getLocaleFromTag(defaultLocale.value()));
				}

				return builder;
			}).addBuilderConfigureFunction(Document.class, (parameter, builder) -> {
				builder.setProperty("advancedMessage", parameter.isAnnotationPresent(AdvancedMessage.class));

				return builder;
			}).addBuilderConfigureFunction(LocalDate.class, (parameter, builder) -> {
				DateTimePattern pattern = parameter.getAnnotation(DateTimePattern.class);
				if (pattern != null) {
					builder.setProperty("dateTimePattern", pattern);
				}

				DefaultDateTime defaultDateTime = parameter.getAnnotation(DefaultDateTime.class);
				if (defaultDateTime != null) {
					builder.setProperty("defaultDateTime", defaultDateTime);
				}

				return builder;
			}).addBuilderConfigureFunction(TimedArgument.class, (parameter, builder) -> {
				Class<?> clazz = (Class<?>) ClassUtility.getParameterTypes(parameter, TimedArgument.class)[0];

				builder.setProperty("timedArgumentClass", clazz);
				builder.setProperty("finalClass", clazz);

				List<?> builders = CommandUtility.getBuilderConfigureFunctions(argumentFactory, clazz);
				for (Object builderFunction : builders) {
					builder = ((BuilderConfigureFunction) builderFunction).configure(parameter, builder);
				}

				return builder;
			}).addBuilderConfigureFunction(ItemStack.class, (parameter, builder) -> {
				Class<?> clazz = (Class<?>) ClassUtility.getParameterTypes(parameter, ItemStack.class)[0];
				builder.setProperty("itemClass", clazz);

				return builder;
			}).addBuilderConfigureFunction(Range.class, (parameter, builder) -> {
				Class<?> clazz = (Class<?>) ClassUtility.getParameterTypes(parameter, Range.class)[0];

				List<?> builders = CommandUtility.getBuilderConfigureFunctions(argumentFactory, clazz);
				for (Object builderFunction : builders) {
					builder = ((BuilderConfigureFunction) builderFunction).configure(parameter, builder);
				}

				builder.setProperty("rangeClass", clazz);

				return builder;
			}).addBuilderConfigureFunction(Alternative.class, (parameter, builder) -> {
				Class<?> clazz = (Class<?>) ClassUtility.getParameterTypes(parameter, Alternative.class)[0];

				builder.setProperty("alternativeClass", clazz);
				builder.setProperty("finalClass", clazz);

				List<?> builders = CommandUtility.getBuilderConfigureFunctions(argumentFactory, clazz);
				for (Object builderFunction : builders) {
					builder = ((BuilderConfigureFunction) builderFunction).configure(parameter, builder);
				}

				Function<CommandEvent, ?> function = builder.getDefaultValueFunction();
				if (function != null) {
					builder.setDefaultValue(event -> {
						Object value = function.apply(event);
						return value == null ? null : new Alternative<>(value, null);
					});
				}

				AlternativeOptions options = parameter.getAnnotation(AlternativeOptions.class);
				if (options != null) {
					builder.setProperty("alternativeOptions", options.value());
				}

				return builder;
			}).addGenericBuilderConfigureFunction(Enum.class, (parameter, builder) -> {
				EnumOptions options = parameter.getAnnotation(EnumOptions.class);
				if (options != null) {
					List<Enum<?>> enums = new ArrayList<>(), allEnums = new ArrayList<>();
					for (Object object : ((Class<?>) builder.getProperties().getOrDefault("finalClass", parameter.getType())).getEnumConstants()) {
						Enum<?> enumConstant = (Enum<?>) object;
						allEnums.add(enumConstant);
						for (String option : options.value()) {
							if (option.equals(enumConstant.name())) {
								enums.add(enumConstant);
								break;
							}
						}
					}

					if (options.exclude()) {
						allEnums.removeAll(enums);
					}

					builder.setProperty("enumOptions", options.exclude() ? allEnums : enums);
				}

				return builder;
			}).addBuilderConfigureFunction(Or.class, (parameter, builder) -> {
				Type[] classes = ClassUtility.getParameterTypes(parameter, Or.class);
				Class<?> firstClass = (Class<?>) classes[0], secondClass = (Class<?>) classes[1];

				List<?> builders = CommandUtility.getBuilderConfigureFunctions(argumentFactory, firstClass);
				for (Object builderFunction : builders) {
					builder = ((BuilderConfigureFunction) builderFunction).configure(parameter, builder);
				}

				builder.setProperty("firstClass", firstClass);
				builder.setProperty("secondClass", secondClass);

				return builder;
			});
			
		argumentFactory.registerParser(Member.class, (context, argument, content) -> new ParsedResult<>(SearchUtility.getMember(context.getMessage().getGuild(), content.trim())))
			.registerParser(TextChannel.class, (context, argument, content) -> new ParsedResult<>(SearchUtility.getTextChannel(context.getMessage().getGuild(), content.trim())))
			.registerParser(Duration.class, (context, argument, content) -> new ParsedResult<>(TimeUtility.getDurationFromString(content.trim())))
			.registerParser(List.class, (context, argument, content) -> new ParsedResult<>(SearchUtility.getCommandOrModule(this.commandListener, content.trim())))
			.registerParser(Reason.class, (context, argument, content) -> new ParsedResult<>(Reason.parse(this.mongo, context.getMessage().getGuild().getIdLong(), content.trim())))
			.registerParser(ObjectId.class, (context, argument, content) -> new ParsedResult<>(ObjectId.isValid(content) ? new ObjectId(content.trim()) : null))
			.registerParser(IPermissionHolder.class, (context, argument, content) -> new ParsedResult<>(SearchUtility.getPermissionHolder(context.getMessage().getGuild(), content.trim())))
			.registerParser(Role.class, (context, argument, content) -> new ParsedResult<>(SearchUtility.getRole(context.getMessage().getGuild(), content.trim())))
			.registerParser(AudioChannel.class, (context, argument, content) -> new ParsedResult<>(SearchUtility.getAudioChannel(context.getMessage().getGuild(), content.trim())))
			.registerParser(Attachment.class, (context, argument, content) -> context.getMessage().getAttachments().isEmpty() ? new ParsedResult<>() : new ParsedResult<>(context.getMessage().getAttachments().get(0)))
			.registerParser(RichCustomEmoji.class, (context, argument, content) -> new ParsedResult<>(argument.getProperty("global") ? SearchUtility.getCustomEmoji(this.shardManager, content.trim()) : SearchUtility.getGuildCustomEmoji(context.getMessage().getGuild(), content.trim())))
			.registerParser(Locale.class, (context, argument, content) -> new ParsedResult<>(SearchUtility.getLocale(content.trim())))
			.registerParser(Guild.class, (context, argument, content) -> new ParsedResult<>(SearchUtility.getGuild(this.shardManager, content.trim())))
			.registerParser(AmountArgument.class, (context, argument, content) -> new ParsedResult<>(AmountArgument.parse(content)))
			.registerParser(EmojiUnion.class, (context, argument, content) -> new ParsedResult<>(argument.getProperty("unchecked") ? SearchUtility.getUncheckedEmoji(this.shardManager, content.trim()) : SearchUtility.getEmoji(this.shardManager, content.trim())))
			.registerParser(Sx4Command.class, (context, argument, content) -> new ParsedResult<>(SearchUtility.getCommand(this.commandListener, content.trim())))
			.registerGenericParser(Item.class, (context, type, argument, content) -> new ParsedResult<>(this.economyManager.getItemByQuery(content.trim(), type)))
			.registerParser(Item.class, (context, argument, content) -> new ParsedResult<>(this.economyManager.getItemByQuery(content.trim(), Item.class)))
			.registerParser(OffsetTimeZone.class, (context, argument, content) -> new ParsedResult<>(OffsetTimeZone.getTimeZone(content.trim().toUpperCase())))
			.registerParser(WebhookChannel.class, (context, argument, content) -> {
				GuildChannel channel = SearchUtility.getGuildChannel(context.getMessage().getGuild(), WebhookChannel.CHANNEL_TYPES, content.trim());
				return new ParsedResult<>(channel == null ? null : new WebhookChannel((GuildMessageChannelUnion) channel));
			}).registerGenericParser(GuildChannel.class, (context, type, argument, content) -> {
				ChannelType[] channelTypes = argument.getProperty("channelTypes");
				GuildChannel channel = SearchUtility.getGuildChannel(context.getMessage().getGuild(), channelTypes.length == 0 ? ChannelType.values() : channelTypes, content.trim());
				return new ParsedResult<>(channel);
			}).registerParser(ItemStack.class, (context, argument, content) -> {
				Class type = argument.getProperty("itemClass");
				ItemStack<?> stack = ItemStack.parse(this.economyManager, content, type);
				if (stack == null || stack.isOverflow()) {
					return new ParsedResult<>();
				}

				return new ParsedResult<>(stack);
			}).registerParser(ReminderArgument.class, (context, argument, content) -> {
				try {
					return new ParsedResult<>(ReminderArgument.parse(this.mongo, context.getMessage().getAuthor().getIdLong(), content));
				} catch (DateTimeException | IllegalArgumentException e) {
					return new ParsedResult<>();
				}
			}).registerParser(Document.class, (context, argument, content) -> {
				Document json;
				try {
					json = Document.parse(content);
				} catch (JsonParseException e) {
					return new ParsedResult<>();
				}

				if (argument.getProperty("advancedMessage", false)) {
					if (!MessageUtility.isValid(json, false)) {
						return new ParsedResult<>();
					}

					MessageUtility.removeFields(json);
				}

				return new ParsedResult<>(json);
			}).registerParser(Integer.class, (context, argument, content) -> {
				if (argument.getProperty("colour", false)) {
					int colour = ColourUtility.fromQuery(content);
					if (colour == -1) {
						return new ParsedResult<>();
					} else {
						return new ParsedResult<>(colour);
					}
				}

				try {
					return new ParsedResult<>(Integer.parseInt(content));
				} catch (NumberFormatException e) {
					return new ParsedResult<>();
				}
			}).registerParser(String.class, new IParser<>() {
				public @NotNull ParsedResult<String> parse(@NotNull ParseContext context, @NotNull IArgument<String> argument, @NotNull String content) {
					Message message = context.getMessage();

					ThreadLocal<ICommand> local = argument.getProperty("command", ThreadLocal.class);
					if (local != null) {
						local.set(context.getCommand());
					}

					CommandParserImpl parser = (CommandParserImpl) context.getCommandParser();

					String contentToHandle = null;
					if (!argument.isEndless()) {
						for (QuoteCharacter quotes : parser.getQuoteCharacters()) {
							contentToHandle = com.jockie.bot.core.utility.StringUtility.parseWrapped(content, quotes.start, quotes.end);
							if (contentToHandle != null) {
								content = content.substring(contentToHandle.length());
								contentToHandle = com.jockie.bot.core.utility.StringUtility.unwrap(contentToHandle, quotes.start, quotes.end);

								if (context.getCommand().getArgumentTrimType().equals(ICommand.ArgumentTrimType.STRICT)) {
									contentToHandle = com.jockie.bot.core.utility.StringUtility.strip(contentToHandle);
								}

								break;
							}
						}
					}

					if (contentToHandle == null) {
						int nextSpace = content.indexOf(' ');
						contentToHandle = nextSpace == -1 || argument.isEndless() ? content : content.substring(0, nextSpace);
						content = content.substring(contentToHandle.length());
					}

					boolean imageUrl = argument.getProperty("imageUrl", false);
					if (imageUrl || argument.getProperty("url", false)) {
						if (contentToHandle.isEmpty()) {
							Attachment attachment = message.getAttachments().stream()
								.filter(Attachment::isImage)
								.findFirst()
								.orElse(null);

							if (attachment == null) {
								return imageUrl ? new ParsedResult<>(message.getMember().getEffectiveAvatarUrl(), content) : new ParsedResult<>();
							} else {
								return new ParsedResult<>(attachment.getUrl(), content);
							}
						}

						if (imageUrl) {
							Member member = SearchUtility.getMember(message.getGuild(), contentToHandle);
							if (member != null) {
								return new ParsedResult<>(member.getEffectiveAvatarUrl(), content);
							}
						}

						try {
							new URL(contentToHandle);
						} catch (MalformedURLException e) {
							return new ParsedResult<>();
						}
					}

					String[] options = argument.getProperty("options");
					if (argument.getProperty("class") == null && options != null && options.length != 0) {
						for (String option : options) {
							if (option.equalsIgnoreCase(contentToHandle)) {
								return new ParsedResult<>(option, content);
							}
						}

						return new ParsedResult<>();
					}

					if (contentToHandle.isEmpty()) {
						return new ParsedResult<>();
					}

					return new ParsedResult<>(contentToHandle, content);
				}

				public boolean isHandleAll() {
					return true;
				}
			}).registerParser(Long.class, (context, argument, content) -> {
				if (argument.getProperty("userId", false)) {
					long userId = SearchUtility.getUserId(content);

					return new ParsedResult<>(userId == -1L ? null : userId);
				}

				try {
					return new ParsedResult<>(Long.parseLong(content));
				} catch (NumberFormatException e) {
					return new ParsedResult<>();
				}
			}).registerParser(Pattern.class, (context, argument, content) -> {
				try {
					return new ParsedResult<>(Pattern.compile(content));
				} catch (PatternSyntaxException e) {
					return new ParsedResult<>();
				}
			}).registerParser(PartialEmote.class, new IParser<>() {
				public @NotNull ParsedResult<PartialEmote> parse(@NotNull ParseContext context, @NotNull IArgument<PartialEmote> argument, @NotNull String content) {
					int nextSpace = content.indexOf(' ');
					String query = nextSpace == -1 || argument.isEndless() ? content : content.substring(0, nextSpace);

					if (query.isEmpty()) {
						Attachment attachment = context.getMessage().getAttachments().stream()
							.filter(Attachment::isImage)
							.findFirst()
							.orElse(null);

						if (attachment != null) {
							String fileName = attachment.getFileName(), extension = attachment.getFileExtension();
							if (extension != null) {
								fileName = fileName.substring(0, fileName.length() - (extension.length() + 1));
							}

							return new ParsedResult<>(new PartialEmote(attachment.getUrl(), fileName, extension != null && extension.equalsIgnoreCase("gif")), content.substring(query.length()));
						}

						return new ParsedResult<>();
					}

					PartialEmote partialEmote = SearchUtility.getPartialEmote(context.getMessage().getJDA().getShardManager(), query);
					if (partialEmote != null) {
						return new ParsedResult<>(partialEmote, content.substring(query.length()));
					}

					try {
						new URL(query);
					} catch (MalformedURLException e) {
						return new ParsedResult<>();
					}

					String extension = StringUtility.getFileExtension(query);
					if (extension != null) {
						return new ParsedResult<>(new PartialEmote(query, null, extension.equalsIgnoreCase("gif")), content.substring(query.length()));
					} else {
						return new ParsedResult<>();
					}
				}

				public boolean isHandleAll() {
					return true;
				}
			}).registerParser(StickerArgument.class, new IParser<>() {
				public @NotNull ParsedResult<StickerArgument> parse(@NotNull ParseContext context, @NotNull IArgument<StickerArgument> argument, @NotNull String content) {
					int nextSpace = content.indexOf(' ');
					String query = nextSpace == -1 || argument.isEndless() ? content : content.substring(0, nextSpace);

					if (query.isEmpty()) {
						List<StickerItem> stickers = context.getMessage().getStickers();
						if (stickers.isEmpty()) {
							return new ParsedResult<>();
						}

						return new ParsedResult<>(StickerArgument.fromSticker(stickers.get(0)), content.substring(query.length()));
					}

					Sticker sticker = SearchUtility.getSticker(context.getMessage().getJDA().getShardManager(), query);
					if (sticker != null) {
						return new ParsedResult<>(StickerArgument.fromSticker(sticker), content.substring(query.length()));
					}

					try {
						new URL(query);
					} catch (MalformedURLException e) {
						return new ParsedResult<>();
					}

					return new ParsedResult<>(StickerArgument.fromUrl(query), content.substring(query.length()));
				}

				public boolean isHandleAll() {
					return true;
				}
			}).registerParser(GuildSticker.class, new IParser<>() {
				public @NotNull ParsedResult<GuildSticker> parse(@NotNull ParseContext context, @NotNull IArgument<GuildSticker> argument, @NotNull String content) {
					int nextSpace = content.indexOf(' ');
					String query = nextSpace == -1 || argument.isEndless() ? content : content.substring(0, nextSpace);

					if (query.isEmpty()) {
						List<StickerItem> stickers = context.getMessage().getStickers();
						if (stickers.isEmpty()) {
							return new ParsedResult<>();
						}

						GuildSticker sticker = context.getMessage().getGuild().getStickerById(stickers.get(0).getIdLong());
						if (sticker == null) {
							return new ParsedResult<>();
						}

						return new ParsedResult<>(sticker, content.substring(query.length()));
					}

					GuildSticker sticker = SearchUtility.getSticker(context.getMessage().getGuild(), query);
					if (sticker != null) {
						return new ParsedResult<>(sticker, content.substring(query.length()));
					}

					return new ParsedResult<>();
				}

				public boolean isHandleAll() {
					return true;
				}
			}).registerParser(MessageArgument.class, new IParser<>() {
				public @NotNull ParsedResult<MessageArgument> parse(@NotNull ParseContext context, @NotNull IArgument<MessageArgument> argument, @NotNull String content) {
					Message message = context.getMessage();
					MessageChannel channel = message.getChannel();

					int nextSpace = content.indexOf(' ');
					String query = nextSpace == -1 || argument.isEndless() ? content : content.substring(0, nextSpace);
					if (query.isEmpty() && !argument.acceptEmpty()) {
						return new ParsedResult<>();
					}

					Matcher jumpMatch = Message.JUMP_URL_PATTERN.matcher(query);
					if (jumpMatch.matches()) {
						try {
							long messageId = MiscUtil.parseSnowflake(jumpMatch.group(3));

							GuildMessageChannel linkChannel = message.getGuild().getChannelById(GuildMessageChannel.class, jumpMatch.group(2));

							return new ParsedResult<>(new MessageArgument(messageId, linkChannel == null ? channel : linkChannel), content.substring(query.length()));
						} catch (NumberFormatException e) {
							return new ParsedResult<>();
						}
					} else if (NumberUtility.isNumber(query)) {
						try {
							long messageId = MiscUtil.parseSnowflake(query);

							return new ParsedResult<>(new MessageArgument(messageId, channel), content.substring(query.length()));
						} catch (NumberFormatException e) {
							return new ParsedResult<>();
						}
					} else {
						MessageReference reference = message.getMessageReference();
						if (reference != null) {
							Message messageReference = reference.getMessage();

							// When a message is null it can be a webhook message so still handle it
							return new ParsedResult<>(messageReference == null ? new MessageArgument(reference.getMessageIdLong(), channel) : new MessageArgument(messageReference), content.isEmpty() ? "" : " " + content);
						}

						return new ParsedResult<>();
					}
				}

				public boolean isHandleAll() {
					return true;
				}
			}).registerParser(LocalDate.class, (context, argument, content) -> {
				DefaultDateTime defaultDateTime = argument.getProperty("defaultDateTime", DefaultDateTime.class);
				DateTimePattern pattern = argument.getProperty("dateTimePattern", DateTimePattern.class);

				DateTimeFormatterBuilder builder = null;
				if (pattern != null) {
					builder = new DateTimeFormatterBuilder().appendPattern(pattern.value());

					if (defaultDateTime != null) {
						String[] types = defaultDateTime.types();
						for (int i = 0; i < types.length; i++) {
							builder.parseDefaulting(ChronoField.valueOf(types[i]), defaultDateTime.values()[i]);
						}
					}
				}

				try {
					return new ParsedResult<>(builder == null ? LocalDate.parse(content) : LocalDate.parse(content, builder.toFormatter()));
				} catch (DateTimeParseException e) {
					return new ParsedResult<>();
				}
			}).registerParser(TimedArgument.class, (context, argument, content) -> {
				Class<?> clazz = argument.getProperty("timedArgumentClass", Class.class);

				int index = content.indexOf(' ');
				Duration duration = index == -1 ? null : TimeUtility.getDurationFromString(content.substring(index));

				ParsedResult<?> parsedArgument = CommandUtility.getParsedResult(clazz, argumentFactory, context, argument, index == -1 ? content : content.substring(0, index), null);
				if (!parsedArgument.isValid()) {
					argument.getProperty("failedClass", ThreadLocal.class).set(clazz);
					return new ParsedResult<>();
				}
				
				return new ParsedResult<>(new TimedArgument<>(duration, parsedArgument.getObject()));
			}).registerParser(Range.class, (context, argument, content) -> {
				Class<?> clazz = argument.getProperty("rangeClass", Class.class);

				if (clazz == ObjectId.class) {
					return new ParsedResult<>(Range.getRange(content, it -> ObjectId.isValid(it) ? new ObjectId(it) : null));
				} else if (clazz == String.class) {
					return new ParsedResult<>(Range.getRange(content));
				}

				return new ParsedResult<>();
			}).registerParser(Alternative.class, new IParser<>() {
				public @NotNull ParsedResult<Alternative> parse(@NotNull ParseContext context, @NotNull IArgument<Alternative> argument, @NotNull String content) {
					int nextSpace = content.indexOf(' ');
					String argumentContent = nextSpace == -1 || argument.isEndless() ? content : content.substring(0, nextSpace);
					if (!argument.acceptEmpty() && argumentContent.isEmpty()) {
						return new ParsedResult<>();
					}

					String[] options = argument.getProperty("alternativeOptions", new String[0]);
					for (String option : options) {
						if (argumentContent.equalsIgnoreCase(option)) {
							return new ParsedResult<>(new Alternative<>(null, option), content.substring(argumentContent.length()));
						}
					}

					Class<?> clazz = argument.getProperty("alternativeClass", Class.class);

					ParsedResult<?> parsedArgument = CommandUtility.getParsedResult(clazz, argumentFactory, context, argument, argumentContent, content);
					if (!parsedArgument.isValid()) {
						argument.getProperty("failedClass", ThreadLocal.class).set(clazz);
						return new ParsedResult<>();
					}

					String contentLeft = parsedArgument.getContentLeft();

					return new ParsedResult<>(new Alternative<>(parsedArgument.getObject(), null), contentLeft == null ? content.substring(argumentContent.length()) : contentLeft);
				}

				public boolean isHandleAll() {
					return true;
				}
			}).registerGenericParser(Enum.class, (context, type, argument, content) -> {
				List<Enum<?>> options = argument.getProperty("enumOptions");

				Class<?> finalClass = argument.getProperty("finalClass", Class.class);
				finalClass = finalClass == null ? argument.getType() : finalClass;

				for (Object object : finalClass.getEnumConstants()) {
					Enum<?> enumEntry = (Enum<?>) object;
					if (options != null && !options.contains(enumEntry)) {
						continue;
					}

					String name = enumEntry.name();
					if (name.equalsIgnoreCase(content) || name.replace("_", " ").equalsIgnoreCase(content)) {
						return new ParsedResult<>(enumEntry);
					}
				}

				return new ParsedResult<>();
			}).registerParser(Or.class, new IParser<>() {
				public @NotNull ParsedResult parse(@NotNull ParseContext context, @NotNull IArgument<Or> argument, @NotNull String content) {
					int nextSpace = content.indexOf(' ');
					String argumentContent = nextSpace == -1 || argument.isEndless() ? content : content.substring(0, nextSpace);
					if (!argument.acceptEmpty() && argumentContent.isEmpty()) {
						return new ParsedResult<>();
					}

					Class<?> firstClass = argument.getProperty("firstClass"), secondClass = argument.getProperty("secondClass");

					ParsedResult<?> firstParsedArgument = CommandUtility.getParsedResult(firstClass, argumentFactory, context, argument, argumentContent, content);
					ParsedResult<?> secondParsedArgument = CommandUtility.getParsedResult(secondClass, argumentFactory, context, argument, argumentContent, content);

					if (firstParsedArgument.isValid()) {
						String contentLeft = firstParsedArgument.getContentLeft();
						return new ParsedResult<>(new Or<>(firstParsedArgument.getObject(), null), contentLeft == null ? content.substring(argumentContent.length()) : contentLeft);
					} else if (secondParsedArgument.isValid()) {
						String contentLeft = secondParsedArgument.getContentLeft();
						return new ParsedResult<>(new Or<>(null, secondParsedArgument.getObject()), contentLeft == null ? content.substring(argumentContent.length()) : contentLeft);
					} else {
						argument.getProperty("failedClass", ThreadLocal.class).set(firstClass);
						return new ParsedResult<>();
					}
				}

				public boolean isHandleAll() {
					return true;
				}
			});
		
		argumentFactory.addParserAfter(String.class, (context, argument, content) -> {
			Replace replace = argument.getProperty("replace", Replace.class);
			if (replace != null) {
				content = content.replace(replace.replace(), replace.with());
			}

			if (argument.getProperty("lowercase", false)) {
				content = content.toLowerCase();
			}

			if (argument.getProperty("uppercase", false)) {
				content = content.toUpperCase();
			}

			Limit limit = argument.getProperty("limit", Limit.class);
			if (limit != null && limit.error() && (content.length() < limit.min() || content.length() > limit.max())) {
				return new ParsedResult<>();
			} else if (limit != null && !limit.error()) {
				content = content.substring(Math.min(Math.max(0, limit.min()), content.length()), Math.min(Math.max(0, limit.max()), content.length()));
			}

			return new ParsedResult<>(content);
		}).addParserAfter(Integer.class, (context, argument, content) -> {
			Limit limit = argument.getProperty("limit", Limit.class);
			if (limit != null && limit.error() && (content < limit.min() || content > limit.max())) {
				return new ParsedResult<>();
			} else if (limit != null && !limit.error()) {
				content = Math.min(limit.max(), Math.max(limit.min(), content));
			}
			
			return new ParsedResult<>(content);
		}).addParserAfter(Double.class, (context, argument, content) -> {
			Limit limit = argument.getProperty("limit", Limit.class);
			if (limit != null && limit.error() && (content < limit.min() || content > limit.max())) {
				return new ParsedResult<>();
			} else if (limit != null && !limit.error()) {
				content = Math.min(limit.max(), Math.max(limit.min(), content));
			}

			return new ParsedResult<>(content);
		});
	}

	private IErrorManager createErrorManager() {
		return new ErrorManagerImpl()
			.registerResponse(Member.class, "I could not find that user " + this.config.getFailureEmote())
			.registerResponse(User.class, "I could not find that user " + this.config.getFailureEmote())
			.registerResponse(Role.class, "I could not find that role " + this.config.getFailureEmote())
			.registerResponse(Sx4Command.class, "I could not find that command" + this.config.getFailureEmote())
			.registerResponse(EmojiUnion.class, "I could not find that emote " + this.config.getFailureEmote())
			.registerResponse(WebhookChannel.class, "I could not find that text channel " + this.config.getFailureEmote())
			.registerResponse(MessageChannel.class, "I could not find that text channel " + this.config.getFailureEmote())
			.registerResponse(AudioChannel.class, "I could not find that voice channel " + this.config.getFailureEmote())
			.registerResponse(ModuleCategory.class, "I could not find that category " + this.config.getFailureEmote())
			.registerResponse(GuildChannel.class, "I could not find that channel " + this.config.getFailureEmote())
			.registerResponse(IPermissionHolder.class, "I could not find that user/role " + this.config.getFailureEmote())
			.registerResponse(RichCustomEmoji.class, "I could not find that emote " + this.config.getFailureEmote())
			.registerResponse(GuildSticker.class, "I could not find that sticker " + this.config.getFailureEmote())
			.registerResponse(StickerArgument.class, "I could not find that sticker " + this.config.getFailureEmote())
			.registerResponse(ItemStack.class, "I could not find that item " + this.config.getFailureEmote())
			.registerResponse(Item.class, "I could not find that item " + this.config.getFailureEmote())
			.registerResponse(Duration.class, "Invalid time string given, a good example would be `5d 1h 24m 36s` " + this.config.getFailureEmote())
			.registerResponse(ObjectId.class, "Invalid id given, an example id would be `5e45ce6d3688b30ee75201ae` " + this.config.getFailureEmote())
			.registerResponse(Locale.class, "I could not find that language " + this.config.getFailureEmote())
			.registerResponse(List.class, "I could not find that command/module " + this.config.getFailureEmote())
			.registerResponse(MessageArgument.class, "I could not find that message " + this.config.getFailureEmote())
			.registerResponse(ReminderArgument.class, "Invalid reminder format given, view `help reminder add` for more info " + this.config.getFailureEmote())
			.registerResponse(PartialEmote.class, "I could not find that emote " + this.config.getFailureEmote())
			.registerResponse(Guild.class, "I could not find that server " + this.config.getFailureEmote())
			.registerResponse(AutoRoleFilter.class, "I could not find that filter " + this.config.getFailureEmote())
			.registerResponse(Pattern.class, "Regex syntax was incorrect " + this.config.getFailureEmote())
			.registerResponse(AmountArgument.class, "Invalid amount argument, make sure it is either a number or percentage " + this.config.getFailureEmote())
			.registerResponse(int.class, (argument, message, content) -> {
				if (argument.getProperty("colour", false)) {
					message.getChannel().sendMessage("I could not find that colour " + this.config.getFailureEmote()).queue();
					return;
				}

				int number;
				try {
					number = Integer.parseInt(content);
				} catch (NumberFormatException e) {
					message.getChannel().sendMessage("The argument `" + argument.getName() + "` needs to be a number " + this.config.getFailureEmote()).queue();
					return;
				}

				Limit limit = argument.getProperty("limit", Limit.class);
				if (limit != null && number > limit.max()) {
					message.getChannel().sendMessageFormat("The maximum number you can give for `%s` is **%,d** %s", argument.getName(), limit.max(), this.config.getFailureEmote()).queue();
					return;
				}

				if (limit != null && number < limit.min()) {
					message.getChannel().sendMessageFormat("The minimum number you can give for `%s` is **%,d** %s", argument.getName(), limit.min(), this.config.getFailureEmote()).queue();
				}
			}).registerResponse(double.class, (argument, message, content) -> {
				double number;
				try {
					number = Double.parseDouble(content);
				} catch (NumberFormatException e) {
					message.getChannel().sendMessage("The argument `" + argument.getName() + "` needs to be a number " + this.config.getFailureEmote()).queue();
					return;
				}

				Limit limit = argument.getProperty("limit", Limit.class);
				if (limit != null && number > limit.max()) {
					message.getChannel().sendMessageFormat("The maximum number you can give for `%s` is **%,d** %s", argument.getName(), limit.max(), this.config.getFailureEmote()).queue();
					return;
				}

				if (limit != null && number < limit.min()) {
					message.getChannel().sendMessageFormat("The minimum number you can give for `%s` is **%,d** %s", argument.getName(), limit.min(), this.config.getFailureEmote()).queue();
				}
			}).registerResponse(String.class, (argument, message, content) -> {
				if (argument.getProperty("imageUrl", false) || argument.getProperty("url", false)) {
					message.getChannel().sendMessage("Invalid url given " + this.config.getFailureEmote()).queue();
					return;
				}

				String[] options = argument.getProperty("options");
				if (options != null && options.length != 0) {
					message.getChannel().sendMessageFormat("Invalid option given, `%s` are valid options %s", String.join("`, `", options), this.config.getFailureEmote()).queue();
					return;
				}

				Limit limit = argument.getProperty("limit", Limit.class);
				if (limit != null && content.length() < limit.min()) {
					message.getChannel().sendMessageFormat("You cannot use less than **%,d** character%s for `%s` %s", limit.min(), limit.min() == 1 ? "" : "s", argument.getName(), this.config.getFailureEmote()).queue();
					return;
				}

				if (limit != null && content.length() > limit.max()) {
					message.getChannel().sendMessageFormat("You cannot use more than **%,d** character%s for `%s` %s", limit.max(), limit.max() == 1 ? "" : "s", argument.getName(), this.config.getFailureEmote()).queue();
					return;
				}

				ThreadLocal<ICommand> local = argument.getProperty("command", ThreadLocal.class);
				ICommand command = local == null ? null : local.get();
				if (command == null) {
					return;
				}

				message.getChannel().sendMessage(HelpUtility.getHelpMessage(command, !message.isFromGuild() || message.getGuild().getSelfMember().hasPermission(message.getGuildChannel(), Permission.MESSAGE_EMBED_LINKS))).queue();
			}).registerResponse(Enum.class, (argument, message, content) -> {
				Class<?> finalClass = argument.getProperty("finalClass", Class.class);
				finalClass = finalClass == null ? argument.getType() : finalClass;

				List<Object> enums = argument.getProperty("enumOptions", Arrays.asList(finalClass.getEnumConstants()));

				StringJoiner joiner = new StringJoiner("`, `", "`", "`");
				for (Object object : enums) {
					joiner.add(((Enum<?>) object).name());
				}

				message.getChannel().sendMessage("Invalid argument given, give any of the following " + joiner + " " + this.config.getFailureEmote()).queue();
			})
			.setHandleInheritance(Enum.class, true)
			.setHandleInheritance(MessageChannel.class, true)
			.setHandleInheritance(Item.class, true);
	}
	
	public static void main(String[] args) throws Exception {
		//MemoryOptimizations.installOptimizations();

		Sx4 bot = new Sx4();
		Sx4Server.initiateWebserver(bot);
		
		Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> {
			System.err.println("[Uncaught]");
			
			exception.printStackTrace();
			
			ExceptionUtility.sendErrorMessage(exception);
		});
	}

}
