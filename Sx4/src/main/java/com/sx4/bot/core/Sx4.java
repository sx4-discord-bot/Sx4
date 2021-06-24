package com.sx4.bot.core;

import com.jockie.bot.core.argument.IArgument;
import com.jockie.bot.core.argument.factory.impl.ArgumentFactory;
import com.jockie.bot.core.argument.factory.impl.ArgumentFactoryImpl;
import com.jockie.bot.core.argument.factory.impl.BuilderConfigureFunction;
import com.jockie.bot.core.command.ICommand;
import com.jockie.bot.core.command.exception.parser.ArgumentParseException;
import com.jockie.bot.core.command.exception.parser.OutOfContentException;
import com.jockie.bot.core.command.factory.impl.MethodCommandFactory;
import com.jockie.bot.core.command.impl.CommandListener;
import com.jockie.bot.core.command.impl.CommandListener.Failure;
import com.jockie.bot.core.command.impl.CommandStore;
import com.jockie.bot.core.command.manager.IErrorManager;
import com.jockie.bot.core.command.manager.impl.ContextManagerFactory;
import com.jockie.bot.core.command.manager.impl.ErrorManagerImpl;
import com.jockie.bot.core.command.parser.ParseContext;
import com.jockie.bot.core.command.parser.impl.CommandParserImpl;
import com.jockie.bot.core.option.factory.impl.OptionFactory;
import com.jockie.bot.core.option.factory.impl.OptionFactoryImpl;
import com.jockie.bot.core.parser.IParser;
import com.jockie.bot.core.parser.ParsedResult;
import com.mongodb.client.model.Projections;
import com.sx4.api.Sx4Server;
import com.sx4.bot.annotations.argument.*;
import com.sx4.bot.cache.GoogleSearchCache;
import com.sx4.bot.cache.GuildMessageCache;
import com.sx4.bot.cache.SteamGameCache;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.config.Config;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.database.postgres.PostgresDatabase;
import com.sx4.bot.entities.argument.*;
import com.sx4.bot.entities.economy.item.Item;
import com.sx4.bot.entities.economy.item.ItemStack;
import com.sx4.bot.entities.management.AutoRoleFilter;
import com.sx4.bot.entities.mod.PartialEmote;
import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.entities.youtube.YouTubeChannel;
import com.sx4.bot.entities.youtube.YouTubeVideo;
import com.sx4.bot.formatter.FormatterManager;
import com.sx4.bot.formatter.function.FormatterParser;
import com.sx4.bot.handlers.*;
import com.sx4.bot.managers.*;
import com.sx4.bot.paged.PagedHandler;
import com.sx4.bot.paged.PagedManager;
import com.sx4.bot.utility.*;
import com.sx4.bot.utility.TimeUtility.OffsetTimeZone;
import com.sx4.bot.waiter.WaiterHandler;
import com.sx4.bot.waiter.WaiterManager;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.entities.MessageReaction.ReactionEmote;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.dv8tion.jda.api.utils.MiscUtil;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import okhttp3.OkHttpClient;
import org.bson.Document;
import org.bson.json.JsonParseException;
import org.bson.types.ObjectId;

import javax.security.auth.login.LoginException;
import java.awt.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.*;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.Temporal;
import java.util.List;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class Sx4 {

	private final Config config = Config.get();

	private final PostgresDatabase postgres;
	private final PostgresDatabase postgresCanary;
	private final PostgresDatabase postgresMain;

	private final MongoDatabase mongo;
	private final MongoDatabase mongoCanary;
	private final MongoDatabase mongoMain;

	private final CommandListener commandListener;
	private final ShardManager shardManager;
	private final OkHttpClient httpClient;
	private final ExecutorService executor;
	private final ScheduledExecutorService scheduledExecutor;

	private final SteamGameCache steamGameCache;
	private final GuildMessageCache messageCache;
	private final GoogleSearchCache googleCache;

	/* Managers */
	private final YouTubeManager youTubeManager;
	private final AntiRegexManager antiRegexManager;
	private final LoggerManager loggerManager;
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
	
	private Sx4() {
		this.postgresMain = new PostgresDatabase(this.config.getMainDatabase());
		this.postgresCanary = new PostgresDatabase(this.config.getCanaryDatabase());

		this.postgres = this.config.isMain() ? this.postgresMain : this.postgresCanary;

		this.mongoMain = new MongoDatabase(this.config.getMainDatabase());
		this.mongoCanary = new MongoDatabase(this.config.getCanaryDatabase());

		this.mongo = this.config.isMain() ? this.mongoMain : this.mongoCanary;

		this.executor = Executors.newSingleThreadExecutor();
		this.scheduledExecutor = Executors.newSingleThreadScheduledExecutor();

		this.httpClient = new OkHttpClient.Builder()
			.connectTimeout(15, TimeUnit.SECONDS)
			.readTimeout(15, TimeUnit.SECONDS)
			.writeTimeout(15, TimeUnit.SECONDS)
			.build();

		ContextManagerFactory.getDefault()
			.registerContext(Sx4CommandEvent.class, (event, type) -> (Sx4CommandEvent) event)
			.setEnforcedContext(Sx4CommandEvent.class, true);

		MessageAction.setDefaultMentions(EnumSet.noneOf(Message.MentionType.class));

		MethodCommandFactory.setDefault(new Sx4CommandFactory());

		this.modLogManager = new ModLogManager(this);

		ModHandler modHandler = new ModHandler(this);
		YouTubeHandler youTubeHandler = new YouTubeHandler(this);

		this.antiRegexManager = new AntiRegexManager();
		this.economyManager = new EconomyManager();
		this.giveawayManager = new GiveawayManager(this);
		this.leaverManager = new LeaverManager(this);
		this.loggerManager = new LoggerManager(this);
		this.modActionManager = new ModActionManager().addListener(modHandler);
		this.muteManager = new MuteManager(this);
		this.patreonManager = new PatreonManager(this).addListener(new PatreonHandler(this));
		this.reminderManager = new ReminderManager(this);
		this.starboardManager = new StarboardManager(this);
		this.suggestionManager = new SuggestionManager(this);
		this.temporaryBanManager = new TemporaryBanManager(this);
		this.welcomerManager = new WelcomerManager(this);
		this.youTubeManager = new YouTubeManager(this).addListener(youTubeHandler);
		this.pagedManager = new PagedManager();
		this.waiterManager = new WaiterManager();
		this.serverStatsManager = new ServerStatsManager(this);
		this.twitchTokenManager = new TwitchTokenManager(this);
		this.mysteryBoxManager = new MysteryBoxManager();

		this.steamGameCache = new SteamGameCache(this);
		this.messageCache = new GuildMessageCache();
		this.googleCache = new GoogleSearchCache(this);

		this.setupArgumentFactory();
		this.setupOptionFactory();

		this.commandListener = this.createCommandListener(this.createErrorManager());
		((CommandParserImpl) this.commandListener.getCommandParser()).addOptionPrefix("");

		List<Object> listeners = List.of(
			this.commandListener,
			new PagedHandler(this),
			new WaiterHandler(this),
			new GiveawayHandler(this),
			new AutoRoleHandler(this),
			modHandler,
			new ConnectionHandler(this),
			new ReactionRoleHandler(this),
			new LoggerHandler(this),
			new AntiRegexHandler(this),
			new WelcomerHandler(this),
			new LeaverHandler(this),
			new StarboardHandler(this),
			new TriggerHandler(this),
			new ServerStatsHandler(this),
			new SelfRoleHandler(this),
			new MuteHandler(this),
			new MediaModeHandler(this),
			new MysteryBoxHandler(this),
			new ServerLogHandler(this),
			youTubeHandler,
			this.messageCache
		);

		this.shardManager = this.createShardManager(listeners);

		FormatterManager formatterManager = new FormatterManager()
			.addFunctions("com.sx4.bot.formatter.parser")
			.addVariable("suffix", "Gets the suffixed version of a number", Integer.class, NumberUtility::getSuffixed)
			.addVariable("round", "Gets the rounded number", Double.class, Math::round)
			.addVariable("floor", "Gets the floored number", Double.class, Math::floor)
			.addVariable("ceil", "Gets the ceiled number", Double.class, Math::ceil)
			.addVariable("length", "Gets the length of the list", Collection.class, Collection::size)
			.addVariable("empty", "Gets whether the list is empty or not", Collection.class, Collection::isEmpty)
			.addVariable("name", "Gets the name of the role", Role.class, Role::getName)
			.addVariable("id", "Gets the id or the role", Role.class, Role::getIdLong)
			.addVariable("created", "Gets the date the role was created", Role.class, Role::getTimeCreated)
			.addVariable("colour", "Gets the colour of the role", Role.class, Role::getColor)
			.addVariable("color", "Gets the color of the role", Role.class, Role::getColor)
			.addVariable("raw", "Gets the raw RGB value of the colour", Color.class, Color::getRGB)
			.addVariable("hex", "Gets the hex code of the colour", Color.class, colour -> "#" + ColourUtility.toHexString(colour.getRGB()))
			.addVariable("name", "Gets the name of the emote", ReactionEmote.class, emote -> emote.isEmoji() ? emote.getName() : emote.getEmote().getName())
			.addVariable("id", "Gets the id of the emote", ReactionEmote.class, emote -> emote.isEmoji() ? emote.getName() : emote.getEmote().getIdLong())
			.addVariable("mention", "Gets the mention of the emote", ReactionEmote.class, emote -> emote.isEmoji() ? emote.getName() : emote.getEmote().getAsMention())
			.addVariable("created", "Gets the date when the emote was created", ReactionEmote.class, emote -> emote.isEmoji() ? OffsetDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC) : emote.getEmote().getTimeCreated())
			.addVariable("raw", "Gets the raw value of the permission", Permission.class, Permission::getRawValue)
			.addVariable("name", "Gets the name of the permission", Permission.class, Permission::getName)
			.addVariable("permissions", "Gets the permissions of the role or user", IPermissionHolder.class, IPermissionHolder::getPermissions)
			.addVariable("mention", "Gets the mention of the entity", IMentionable.class, IMentionable::getAsMention)
			.addVariable("id", "Gets the id of the channel", GuildChannel.class, GuildChannel::getIdLong)
			.addVariable("name", "Gets the name of the channel", GuildChannel.class, GuildChannel::getName)
			.addVariable("created", "Gets the date the channel was created", GuildChannel.class, GuildChannel::getTimeCreated)
			.addVariable("slowmode", "Gets the slowmode of the text channel", TextChannel.class, TextChannel::getSlowmode)
			.addVariable("bitrate", "Gets the bitrate of the voice channel", VoiceChannel.class, VoiceChannel::getBitrate)
			.addVariable("limit", "Gets the user limit of the voice channel", VoiceChannel.class, VoiceChannel::getUserLimit)
			.addVariable("name", "Gets the name of the server", Guild.class, Guild::getName)
			.addVariable("id", "Gets the id of the server", Guild.class, Guild::getIdLong)
			.addVariable("owner", "Gets the owner of the server", Guild.class, Guild::getOwner)
			.addVariable("boosts", "Gets the boost count of the server", Guild.class, Guild::getBoostCount)
			.addVariable("boosters", "Gets the members boosting the server", Guild.class, Guild::getBoosters)
			.addVariable("members", "Gets the member count of the server", Guild.class, Guild::getMemberCount)
			.addVariable("avatar", "Gets the icon url of the server", Guild.class, Guild::getIconUrl)
			.addVariable("created", "Gets the date when the server was created", Guild.class, Guild::getTimeCreated)
			.addVariable("user", "Gets the user of the member", Member.class, Member::getUser)
			.addVariable("nickname", "Gets the nickname of the member", Member.class, Member::getNickname)
			.addVariable("roles", "Gets the roles of the member", Member.class, Member::getRoles)
			.addVariable("colour", "Gets the colour of the member", Member.class, Member::getColor)
			.addVariable("color", "Gets the color of the member", Member.class, Member::getColor)
			.addVariable("joined", "Gets the date when the member joined the server", Member.class, Member::getTimeJoined)
			.addVariable("id", "Gets the id of the user", User.class, User::getIdLong)
			.addVariable("name", "Gets the name of the user", User.class, User::getName)
			.addVariable("avatar", "Gets the avatar url of the user", User.class, User::getEffectiveAvatarUrl)
			.addVariable("discriminator", "Gets the discriminator of the user", User.class, User::getDiscriminator)
			.addVariable("badges", "Gets the badges of the user", User.class, User::getFlags)
			.addVariable("tag", "Gets the tag of the user, name#discriminator", User.class, User::getAsTag)
			.addVariable("created", "Gets the date when the user was created", User.class, User::getTimeCreated)
			.addVariable("name", "Gets the name of the badge", User.UserFlag.class, User.UserFlag::getName)
			.addVariable("raw", "Gets the raw value of the badge", User.UserFlag.class, User.UserFlag::getRawValue)
			.addVariable("offset", "Gets the offset of the badge", User.UserFlag.class, User.UserFlag::getOffset)
			.addVariable("day", "Gets the day of the month of the date", OffsetDateTime.class, OffsetDateTime::getDayOfMonth)
			.addVariable("month", "Gets the month of the year of the date", OffsetDateTime.class, OffsetDateTime::getMonthValue)
			.addVariable("year", "Gets the year of the date", OffsetDateTime.class, OffsetDateTime::getYear)
			.addVariable("id", "Gets the id of the YouTube video", YouTubeVideo.class, YouTubeVideo::getId)
			.addVariable("url", "Gets the url of the YouTube video", YouTubeVideo.class, YouTubeVideo::getUrl)
			.addVariable("title", "Gets the title of the YouTube video", YouTubeVideo.class, YouTubeVideo::getTitle)
			.addVariable("thumbnail", "Gets the thumbnail of the YouTube video", YouTubeVideo.class, YouTubeVideo::getThumbnail)
			.addVariable("published", "Gets the date when the YouTube video was published", YouTubeVideo.class, YouTubeVideo::getPublishedAt)
			.addVariable("id", "Gets the id of the YouTube channel", YouTubeChannel.class, YouTubeChannel::getId)
			.addVariable("url", "Gets the url of the YouTube channel", YouTubeChannel.class, YouTubeChannel::getUrl)
			.addVariable("name", "Gets the name of the YouTube channel", YouTubeChannel.class, YouTubeChannel::getName)
			.addParser(String.class, text -> text)
			.addParser(Boolean.class, text -> {
				if (text.equals("true")) {
					return true;
				} else if (text.equals("false")) {
					return false;
				} else {
					return null;
				}
			})
			.addParser(Temporal.class, text -> {
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
		});

		FormatterManager.setDefaultManager(formatterManager);
	}

	public PostgresDatabase getPostgres() {
		return this.postgres;
	}

	public PostgresDatabase getPostgresMain() {
		return this.postgresMain;
	}

	public PostgresDatabase getPostgresCanary() {
		return this.postgresCanary;
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

	public AntiRegexManager getAntiRegexManager() {
		return this.antiRegexManager;
	}

	public LoggerManager getLoggerManager() {
		return this.loggerManager;
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

	public SteamGameCache getSteamGameCache() {
		return this.steamGameCache;
	}

	public GuildMessageCache getMessageCache() {
		return this.messageCache;
	}

	public GoogleSearchCache getGoogleCache() {
		return this.googleCache;
	}

	public ShardManager createShardManager(List<Object> listeners) {
		try {
			return DefaultShardManagerBuilder.create(this.config.getToken(), GatewayIntent.getIntents(5838))
				.setBulkDeleteSplittingEnabled(false)
				.addEventListeners(listeners)
				.setActivity(Activity.watching("s?help"))
				.build();
		} catch (LoginException | IllegalArgumentException e) {
			e.printStackTrace();
			System.exit(1);
			return null;
		}
	}
	
	private CommandListener createCommandListener(IErrorManager errorManager) {
		return new Sx4CommandListener(this)
			.removePreExecuteCheck(listener -> listener.defaultAuthorPermissionCheck)
			.addCommandStores(CommandStore.of("com.sx4.bot.commands"))
			.addDevelopers(this.config.getOwnerIds())
			.setErrorManager(errorManager)
			.setCommandEventFactory(new Sx4CommandEventFactory(this))
			.addCommandEventListener(new Sx4CommandEventListener(this))
			.setDefaultPrefixes(this.config.getDefaultPrefixes().toArray(String[]::new))
			.addPreParseCheck(message -> !message.getAuthor().isBot())
			.addPreExecuteCheck((event, command) -> CheckUtility.canReply(this, event.getMessage(), event.getPrefix()))
			.addPreExecuteCheck((event, command) -> {
				if (command instanceof Sx4Command && ((Sx4Command) command).isCanaryCommand() && this.config.isMain()) {
					event.reply("This command can only be used on the canary version of the bot " + this.config.getFailureEmote()).queue();
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
				Set<Permission> permissions = command.getAuthorDiscordPermissions();
				if (permissions.isEmpty()) {
					return true;
				}

				EnumSet<Permission> missingPermissions = CheckUtility.missingPermissions(this, event.getMember(), event.getTextChannel(), event.getProperty("fakePermissions"), EnumSet.copyOf(permissions));
				if (missingPermissions.isEmpty()) {
					return true;
				} else {
					event.reply(PermissionUtility.formatMissingPermissions(missingPermissions) + " " + this.config.getFailureEmote()).queue();
					return false;
				}
			}).addPreExecuteCheck((event, command) -> {
				if (command instanceof Sx4Command && event.isFromGuild()) {
					boolean canUseCommand = CheckUtility.canUseCommand(this, event.getMember(), event.getTextChannel(), (Sx4Command) command);
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
				boolean embed = !message.isFromGuild() || message.getGuild().getSelfMember().hasPermission((TextChannel) channel, Permission.MESSAGE_EMBED_LINKS);
				
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
				
				Failure : if (failure != null) {
					ArgumentParseException parseException = (ArgumentParseException) failure.getReason();

					// Because error manager doesn't give a command instance this condition is needed
					IArgument<?> argument = parseException.getArgument();
					String[] options = argument.getProperty("options");
					Limit limit = argument.getProperty("limit", Limit.class);
					String content = message.getContentRaw();

					if (argument.getType() == String.class && !argument.getProperty("imageUrl", false) && !argument.getProperty("url", false) && (options == null || options.length == 0) && (limit == null || (content.length() >= limit.min() && content.length() > limit.max()))) {
						break Failure;
					}

					String value = parseException.getValue();
					
					if (message.getChannelType().isGuild()) {
						Member bot = message.getGuild().getSelfMember();
						
						if (!bot.hasPermission(Permission.MESSAGE_WRITE)) {
							message.getAuthor().openPrivateChannel()
								.flatMap(channel -> channel.sendMessage("I am missing the `" + Permission.MESSAGE_WRITE.getName() + "` permission in " + message.getTextChannel().getAsMention() + " " + this.config.getFailureEmote()))
								.queue();
							
							return;
						}
					}
					
					BiConsumer<Message, String> errorConsumer = argument.getErrorConsumer();
					if (errorConsumer != null) {
						errorConsumer.accept(message, value);
						
						return;
					}

					if (errorManager.handle(argument, message, value)) {
						return;
					}
				}
				
				MessageChannel channel = message.getChannel();
				boolean embed = !message.isFromGuild() || message.getGuild().getSelfMember().hasPermission((TextChannel) channel, Permission.MESSAGE_EMBED_LINKS);
				
				channel.sendMessage(HelpUtility.getHelpMessage(failures.get(0).getCommand(), embed)).queue();
			}).setPrefixesFunction(message -> {
				List<String> guildPrefixes = message.isFromGuild() ? this.mongo.getGuildById(message.getGuild().getIdLong(), Projections.include("prefixes")).getList("prefixes", String.class, Collections.emptyList()) : Collections.emptyList();
				List<String> userPrefixes = this.mongo.getUserById(message.getAuthor().getIdLong(), Projections.include("prefixes")).getList("prefixes", String.class, Collections.emptyList());

				return userPrefixes.isEmpty() ? guildPrefixes.isEmpty() ? this.config.getDefaultPrefixes() : guildPrefixes : userPrefixes;
			}).setCooldownFunction((event, cooldown) -> {
				if (!CheckUtility.canReply(this, event.getMessage(), event.getPrefix())) {
					return;
				}

				ICommand command = event.getCommand();
				if (command instanceof Sx4Command) {
					Sx4Command sx4Command = (Sx4Command) command;
					if (sx4Command.hasCooldownMessage()) {
						event.reply(sx4Command.getCooldownMessage()).queue();
						return;
					}
				}

				event.reply("Slow down there! You can execute this command again in " + TimeUtility.getTimeString(cooldown.getTimeRemainingMillis(), TimeUnit.MILLISECONDS) + " :stopwatch:").queue();
			}).setNSFWFunction(event -> {
				if (!CheckUtility.canReply(this, event.getMessage(), event.getPrefix())) {
					return;
				}

				event.reply("You cannot use this command in a non-nsfw channel " + this.config.getFailureEmote()).queue();
			}).setMissingPermissionExceptionFunction((event, permission) -> {
				if (!CheckUtility.canReply(this, event.getMessage(), event.getPrefix())) {
					return;
				}

				event.reply(PermissionUtility.formatMissingPermissions(EnumSet.of(permission), "I am") + " " + this.config.getFailureEmote()).queue();
			}).setMissingPermissionFunction((event, permissions) -> {
				if (!CheckUtility.canReply(this, event.getMessage(), event.getPrefix())) {
					return;
				}

				event.reply(PermissionUtility.formatMissingPermissions(permissions, "I am") + " " + this.config.getFailureEmote()).queue();
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
		
		argumentFactory.addBuilderConfigureFunction(Emote.class, (parameter, builder) -> builder.setProperty("global", parameter.isAnnotationPresent(Global.class)))
			.addBuilderConfigureFunction(Attachment.class, (parameter, builder) -> builder.setAcceptEmpty(true))
			.addBuilderConfigureFunction(MessageArgument.class, (parameter, builder) -> builder.setAcceptEmpty(true))
			.addGenericBuilderConfigureFunction(Object.class, (parameter, builder) -> builder.setProperty("parameter", parameter))
			.addBuilderConfigureFunction(ReactionEmote.class, (parameter, builder) -> builder.setProperty("unchecked", parameter.isAnnotationPresent(Unchecked.class)))
			.addBuilderConfigureFunction(String.class, (parameter, builder) -> {
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
				Class<?> clazz = ClassUtility.getParameterTypes(parameter, TimedArgument.class)[0];

				List<?> builders = argumentFactory.getBuilderConfigureFunctions(clazz);
				for (Object builderFunction : builders) {
					builder = ((BuilderConfigureFunction) builderFunction).configure(parameter, builder);
				}

				builder.setProperty("class", clazz);

				return builder;
			}).addBuilderConfigureFunction(ItemStack.class, (parameter, builder) -> {
				Class<?> clazz = ClassUtility.getParameterTypes(parameter, ItemStack.class)[0];
				builder.setProperty("itemClass", clazz);

				return builder;
			}).addBuilderConfigureFunction(Range.class, (parameter, builder) -> {
				Class<?> clazz = ClassUtility.getParameterTypes(parameter, Range.class)[0];

				List<?> builders = argumentFactory.getBuilderConfigureFunctions(clazz);
				for (Object builderFunction : builders) {
					builder = ((BuilderConfigureFunction) builderFunction).configure(parameter, builder);
				}

				builder.setProperty("class", clazz);

				return builder;
			}).addBuilderConfigureFunction(Alternative.class, (parameter, builder) -> {
				Class<?> clazz = ClassUtility.getParameterTypes(parameter, Alternative.class)[0];

				List<?> builders = argumentFactory.getBuilderConfigureFunctions(clazz);
				for (Object builderFunction : builders) {
					builder = ((BuilderConfigureFunction) builderFunction).configure(parameter, builder);
				}

				builder.setProperty("class", clazz);

				Options options = parameter.getAnnotation(Options.class);
				if (options != null) {
					builder.setProperty("options", options.value());
				}

				return builder;
			}).addGenericBuilderConfigureFunction(Enum.class, (parameter, builder) -> {
				Options options = parameter.getAnnotation(Options.class);
				if (options != null) {
					List<Enum<?>> enums = new ArrayList<>();
					for (Object object : parameter.getType().getEnumConstants()) {
						for (String option : options.value()) {
							Enum<?> enumConstant = (Enum<?>) object;
							if (option.equals(enumConstant.name())) {
								enums.add(enumConstant);
							}
						}
					}

					builder.setProperty("enumOptions", enums);
				}

				return builder;
			}).addBuilderConfigureFunction(Or.class, (parameter, builder) -> {
				Class<?>[] classes = ClassUtility.getParameterTypes(parameter, Or.class);
				Class<?> firstClass = classes[0], secondClass = classes[1];

				List<?> builders = argumentFactory.getBuilderConfigureFunctions(firstClass);
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
			.registerParser(VoiceChannel.class, (context, argument, content) -> new ParsedResult<>(SearchUtility.getVoiceChannel(context.getMessage().getGuild(), content.trim())))
			.registerParser(Attachment.class, (context, argument, content) -> context.getMessage().getAttachments().isEmpty() ? new ParsedResult<>() : new ParsedResult<>(context.getMessage().getAttachments().get(0)))
			.registerParser(Emote.class, (context, argument, content) -> new ParsedResult<>(argument.getProperty("global") ? SearchUtility.getEmote(this.shardManager, content.trim()) : SearchUtility.getGuildEmote(context.getMessage().getGuild(), content.trim())))
			.registerParser(GuildChannel.class, (context, argument, content) -> new ParsedResult<>(SearchUtility.getGuildChannel(context.getMessage().getGuild(), content.trim())))
			.registerParser(Locale.class, (context, argument, content) -> new ParsedResult<>(SearchUtility.getLocale(content.trim())))
			.registerParser(Guild.class, (context, argument, content) -> new ParsedResult<>(SearchUtility.getGuild(this.shardManager, content.trim())))
			.registerParser(AmountArgument.class, (context, argument, content) -> new ParsedResult<>(AmountArgument.parse(content)))
			.registerParser(ReactionEmote.class, (context, argument, content) -> new ParsedResult<>(argument.getProperty("unchecked") ? SearchUtility.getUncheckedReactionEmote(this.shardManager, content.trim()) : SearchUtility.getReactionEmote(this.shardManager, content.trim())))
			.registerParser(Sx4Command.class, (context, argument, content) -> new ParsedResult<>(SearchUtility.getCommand(this.commandListener, content.trim())))
			.registerGenericParser(Item.class, (context, type, argument, content) -> new ParsedResult<>(this.economyManager.getItemByQuery(content.trim(), type)))
			.registerParser(Item.class, (context, argument, content) -> new ParsedResult<>(this.economyManager.getItemByQuery(content.trim(), Item.class)))
			.registerParser(OffsetTimeZone.class, (context, argument, content) -> new ParsedResult<>(OffsetTimeZone.getTimeZone(content.trim().toUpperCase())))
			.registerParser(ItemStack.class, (context, argument, content) -> {
				Class type = argument.getProperty("itemClass");
				return new ParsedResult<>(ItemStack.parse(this.economyManager, content, type));
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
				public ParsedResult<String> parse(ParseContext context, IArgument<String> argument, String content) {
					Message message = context.getMessage();

					CommandParserImpl parser = (CommandParserImpl) context.getCommandParser();

					String contentToHandle = null;
					if (!argument.isEndless()) {
						for (Pair<Character, Character> quotes : parser.getQuoteCharacters()) {
							contentToHandle = com.jockie.bot.core.utility.StringUtility.parseWrapped(content, quotes.getLeft(), quotes.getRight());
							if (contentToHandle != null) {
								content = content.substring(contentToHandle.length());
								contentToHandle = com.jockie.bot.core.utility.StringUtility.unwrap(contentToHandle, quotes.getLeft(), quotes.getRight());

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
								return imageUrl ? new ParsedResult<>(message.getAuthor().getEffectiveAvatarUrl(), content) : new ParsedResult<>();
							} else {
								return new ParsedResult<>(attachment.getUrl(), content);
							}
						}

						if (imageUrl) {
							Member member = SearchUtility.getMember(message.getGuild(), contentToHandle);
							if (member != null) {
								return new ParsedResult<>(member.getUser().getEffectiveAvatarUrl());
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
				public ParsedResult<PartialEmote> parse(ParseContext context, IArgument<PartialEmote> argument, String content) {
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
			}).registerParser(MessageArgument.class, new IParser<>() {
				public ParsedResult<MessageArgument> parse(ParseContext context, IArgument<MessageArgument> argument, String content) {
					Message message = context.getMessage();
					TextChannel channel = message.getTextChannel();

					int nextSpace = content.indexOf(' ');
					String query = nextSpace == -1 || argument.isEndless() ? content : content.substring(0, nextSpace);
					if (query.isEmpty()) {
						return new ParsedResult<>();
					}

					Matcher jumpMatch = Message.JUMP_URL_PATTERN.matcher(query);
					if (jumpMatch.matches()) {
						try {
							long messageId = MiscUtil.parseSnowflake(jumpMatch.group(3));

							TextChannel linkChannel = channel.getGuild().getTextChannelById(jumpMatch.group(2));

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
						Message reference = message.getReferencedMessage();
						if (reference != null) {
							return new ParsedResult<>(new MessageArgument(reference), content.isEmpty() ? "" : " " + content);
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
				Class<?> clazz = argument.getProperty("class", Class.class);

				int index = content.indexOf(' ');
				Duration duration = index == -1 ? null : TimeUtility.getDurationFromString(content.substring(index));

				ParsedResult<?> parsedArgument = CommandUtility.getParsedResult(clazz, argumentFactory, context, argument, index == -1 ? content : content.substring(0, index), null);
				if (!parsedArgument.isValid()) {
					return new ParsedResult<>();
				}
				
				return new ParsedResult<>(new TimedArgument<>(duration, parsedArgument.getObject()));
			}).registerParser(Range.class, (context, argument, content) -> {
				Class<?> clazz = argument.getProperty("class", Class.class);

				if (clazz == ObjectId.class) {
					return new ParsedResult<>(Range.getRange(content, it -> ObjectId.isValid(it) ? new ObjectId(it) : null));
				} else if (clazz == String.class) {
					return new ParsedResult<>(Range.getRange(content));
				}

				return new ParsedResult<>();
			}).registerParser(Alternative.class, new IParser<>() {
				public ParsedResult<Alternative> parse(ParseContext context, IArgument<Alternative> argument, String content) {
					int nextSpace = content.indexOf(' ');
					String argumentContent = nextSpace == -1 || argument.isEndless() ? content : content.substring(0, nextSpace);
					if (argumentContent.isEmpty()) {
						return new ParsedResult<>();
					}

					String[] options = argument.getProperty("options", new String[0]);
					for (String option : options) {
						if (argumentContent.equalsIgnoreCase(option)) {
							return new ParsedResult<>(new Alternative<>(null, option), content.substring(argumentContent.length()));
						}
					}

					Class<?> clazz = argument.getProperty("class", Class.class);

					ParsedResult<?> parsedArgument = CommandUtility.getParsedResult(clazz, argumentFactory, context, argument, argumentContent, content);
					if (!parsedArgument.isValid()) {
						return new ParsedResult<>();
					}

					return new ParsedResult<>(new Alternative<>(parsedArgument.getObject(), null), parsedArgument.getContentLeft());
				}

				public boolean isHandleAll() {
					return true;
				}
			}).registerGenericParser(Enum.class, (context, type, argument, content) -> {
				List<Enum<?>> options = argument.getProperty("enumOptions");

				for (Enum<?> enumEntry : type.getEnumConstants()) {
					if (options != null && !options.contains(enumEntry)) {
						continue;
					}

					String name = enumEntry.name();
					if (name.equalsIgnoreCase(content) || name.replace("_", " ").equalsIgnoreCase(content)) {
						return new ParsedResult<>(enumEntry);
					}
				}

				return new ParsedResult<>();
			}).registerParser(Or.class, (context, argument, content) -> {
				Class<?> firstClass = argument.getProperty("firstClass"), secondClass = argument.getProperty("secondClass");

				ParsedResult<?> firstParsedArgument = CommandUtility.getParsedResult(firstClass, argumentFactory, context, argument, content, null);
				ParsedResult<?> secondParsedArgument = CommandUtility.getParsedResult(secondClass, argumentFactory, context, argument, content, null);
				
				if (firstParsedArgument.isValid()) {
					return new ParsedResult<>(new Or<>(firstParsedArgument.getObject(), null));
				} else if (secondParsedArgument.isValid()) {
					return new ParsedResult<>(new Or<>(null, secondParsedArgument.getObject()));
				} else {
					return new ParsedResult<>();
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
	
	@SuppressWarnings("unchecked")
	private IErrorManager createErrorManager() {
		return new ErrorManagerImpl()
			.registerResponse(Member.class, "I could not find that user " + this.config.getFailureEmote())
			.registerResponse(User.class, "I could not find that user " + this.config.getFailureEmote())
			.registerResponse(Role.class, "I could not find that role " + this.config.getFailureEmote())
			.registerResponse(Sx4Command.class, "I could not find that command" + this.config.getFailureEmote())
			.registerResponse(ReactionEmote.class, "I could not find that emote " + this.config.getFailureEmote())
			.registerResponse(TextChannel.class, "I could not find that text channel " + this.config.getFailureEmote())
			.registerResponse(VoiceChannel.class, "I could not find that voice channel " + this.config.getFailureEmote())
			.registerResponse(ModuleCategory.class, "I could not find that category " + this.config.getFailureEmote())
			.registerResponse(GuildChannel.class, "I could not find that channel " + this.config.getFailureEmote())
			.registerResponse(IPermissionHolder.class, "I could not find that user/role " + this.config.getFailureEmote())
			.registerResponse(Emote.class, "I could not find that emote " + this.config.getFailureEmote())
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
			.registerResponse(AmountArgument.class, "Invalid amount argument, make sure it is either a number or percentage" + this.config.getFailureEmote())
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
				}
			}).registerResponse(Enum.class, (argument, message, content) -> {
				List<Enum<?>> enums = argument.getProperty("enumOptions", Arrays.asList(argument.getType().getEnumConstants()));

				StringJoiner joiner = new StringJoiner("`, `", "`", "`");
				for (Enum<?> enumEntry : enums) {
					joiner.add(enumEntry.name());
				}

				message.getChannel().sendMessage("Invalid argument given, give any of the following " + joiner + " " + this.config.getFailureEmote()).queue();
			})
			.setHandleInheritance(Enum.class, true)
			.setHandleInheritance(Item.class, true);
	}
	
	public static void main(String[] args) throws Exception {
		Sx4 bot = new Sx4();

		Sx4Server.initiateWebserver(bot);
		
		Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> {
			System.err.println("[Uncaught]");
			
			exception.printStackTrace();
			
			ExceptionUtility.sendErrorMessage(exception);
		});
	}

}
