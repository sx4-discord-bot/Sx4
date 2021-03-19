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
import com.jockie.bot.core.command.parser.impl.CommandParserImpl;
import com.jockie.bot.core.option.factory.impl.OptionFactory;
import com.jockie.bot.core.option.factory.impl.OptionFactoryImpl;
import com.jockie.bot.core.parser.ParsedResult;
import com.mongodb.client.model.Projections;
import com.sx4.api.Sx4Server;
import com.sx4.bot.annotations.argument.*;
import com.sx4.bot.cache.GuildMessageCache;
import com.sx4.bot.cache.SteamGameCache;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.config.Config;
import com.sx4.bot.database.Database;
import com.sx4.bot.entities.argument.*;
import com.sx4.bot.entities.management.AutoRoleFilter;
import com.sx4.bot.entities.mod.PartialEmote;
import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.handlers.*;
import com.sx4.bot.managers.*;
import com.sx4.bot.paged.PagedHandler;
import com.sx4.bot.paged.PagedManager;
import com.sx4.bot.utility.*;
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
import okhttp3.OkHttpClient;
import org.bson.Document;
import org.bson.json.JsonParseException;
import org.bson.types.ObjectId;

import javax.security.auth.login.LoginException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class Sx4 {

	private final Config config = Config.get();
	private final Database database;
	
	private final CommandListener commandListener;
	private final ShardManager shardManager;
	private final OkHttpClient httpClient;
	private final ExecutorService executor;

	private final SteamGameCache steamGameCache;

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
	private final PremiumManager premiumManager;
	private final SuggestionManager suggestionManager;
	private final PagedManager pagedManager;
	private final WaiterManager waiterManager;
	
	private Sx4() {
		this.database = new Database(this.config.getDatabase());
		this.executor = Executors.newSingleThreadExecutor();

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

		this.antiRegexManager = new AntiRegexManager(this);
		this.economyManager = new EconomyManager();
		this.giveawayManager = new GiveawayManager(this);
		this.leaverManager = new LeaverManager(this);
		this.loggerManager = new LoggerManager(this);
		this.modActionManager = new ModActionManager().addListener(modHandler);
		this.muteManager = new MuteManager(this);
		this.patreonManager = new PatreonManager().addListener(new PatreonHandler(this));
		this.premiumManager = new PremiumManager(this);
		this.reminderManager = new ReminderManager(this);
		this.starboardManager = new StarboardManager(this);
		this.suggestionManager = new SuggestionManager(this);
		this.temporaryBanManager = new TemporaryBanManager(this);
		this.welcomerManager = new WelcomerManager(this);
		this.youTubeManager = new YouTubeManager(this).addListener(youTubeHandler);
		this.pagedManager = new PagedManager();
		this.waiterManager = new WaiterManager();

		this.steamGameCache = new SteamGameCache(this);

		this.setupArgumentFactory();
		this.setupOptionFactory();

		this.commandListener = this.createCommandListener(this.createErrorManager());
		((CommandParserImpl) this.commandListener.getCommandParser()).addOptionPrefix("");

		List<Object> listeners = List.of(
			new GuildMessageCache(this),
			this.commandListener,
			new PagedHandler(this),
			new WaiterHandler(this),
			new GiveawayHandler(this),
			modHandler,
			new ConnectionHandler(this),
			new ReactionRoleHandler(this),
			new LoggerHandler(this),
			new AntiRegexHandler(this),
			new WelcomerHandler(this),
			new LeaverHandler(this),
			new StarboardHandler(this),
			new TriggerHandler(this),
			youTubeHandler
		);

		this.shardManager = this.createShardManager(listeners);
	}

	public Database getDatabase() {
		return this.database;
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

	public PremiumManager getPremiumManager() {
		return this.premiumManager;
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

	public SteamGameCache getSteamGameCache() {
		return this.steamGameCache;
	}

	public ShardManager createShardManager(List<Object> listeners) {
		try {
			return DefaultShardManagerBuilder.create(this.config.getToken(), GatewayIntent.getIntents(6094))
				.setBulkDeleteSplittingEnabled(false)
				.addEventListeners(listeners)
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
			.setHelpFunction((message, prefix, commands) -> {
				MessageChannel channel = message.getChannel();
				boolean embed = !message.isFromGuild() || message.getGuild().getSelfMember().hasPermission((TextChannel) channel, Permission.MESSAGE_EMBED_LINKS);
				
				channel.sendMessage(HelpUtility.getHelpMessage(commands.get(0), embed)).queue();
			}).setMessageParseFailureFunction((message, content, failures) -> {
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
			}).addPreExecuteCheck((event, command) -> {
				Document guildData = this.database.getGuildById(event.getGuild().getIdLong(), Projections.include("prefixes", "fakePermissions.holders"));

				List<Document> holders = guildData.getEmbedded(List.of("fakePermissions", "holders"), Collections.emptyList());
				event.setProperty("fakePermissions", holders);

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
					event.reply(PermissionUtility.formatMissingPermissions(missingPermissions)).queue();
					return false;
				}
			}).addPreExecuteCheck((event, command) -> {
				if (command instanceof Sx4Command) {
					boolean canUseCommand = CheckUtility.canUseCommand(this, event.getMember(), event.getTextChannel(), (Sx4Command) command);
					if (!canUseCommand) {
						event.reply("You are blacklisted from using that command in this channel " + this.config.getFailureEmote()).queue();
					}

					return canUseCommand;
				} else {
					return true;
				}
			}).setPrefixesFunction(message -> {
				List<String> guildPrefixes = message.isFromGuild() ? this.database.getGuildById(message.getGuild().getIdLong(), Projections.include("prefixes")).getList("prefixes", String.class, Collections.emptyList()) : Collections.emptyList();
				List<String> userPrefixes = this.database.getUserById(message.getAuthor().getIdLong(), Projections.include("prefixes")).getList("prefixes", String.class, Collections.emptyList());

				return userPrefixes.isEmpty() ? guildPrefixes.isEmpty() ? this.config.getDefaultPrefixes() : guildPrefixes : userPrefixes;
			}).setCooldownFunction((event, cooldown) -> {
				ICommand command = event.getCommand();
				if (command instanceof Sx4Command) {
					Sx4Command sx4Command = (Sx4Command) command;
					if (sx4Command.hasCooldownMessage()) {
						event.reply(sx4Command.getCooldownMessage()).queue();
						return;
					}
				}

				event.reply("Slow down there! You can execute this command again in " + TimeUtility.getTimeString(cooldown.getTimeRemainingMillis(), TimeUnit.MILLISECONDS) + " :stopwatch:").queue();
			}).addPreParseCheck(message -> !message.getAuthor().isBot());
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

			return builder;
		});

		optionFactory.registerParser(Duration.class, (context, option, content) -> content == null ? new ParsedResult<>(true, null) : new ParsedResult<>(TimeUtility.getDurationFromString(content)))
			.registerParser(Guild.class, (context, argument, content) -> new ParsedResult<>(SearchUtility.getGuild(this.shardManager, content)))
			.registerParser(TextChannel.class, (context, argument, content) -> new ParsedResult<>(SearchUtility.getTextChannel(context.getMessage().getGuild(), content.trim())))
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
			});

		optionFactory.addParserAfter(Integer.class, (context, argument, content) -> {
			Limit limit = argument.getProperty("limit", Limit.class);
			if (limit != null) {
				content = Math.min(Math.max(limit.min(), content), limit.max());
			}

			return new ParsedResult<>(content);
		}).addParserAfter(String.class, (context, argument, content) -> {
			Limit limit = argument.getProperty("limit", Limit.class);
			if (limit != null && (content.length() < limit.min() || content.length() > limit.max())) {
				return new ParsedResult<>();
			}

			return new ParsedResult<>(content);
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private void setupArgumentFactory() {
		ArgumentFactoryImpl argumentFactory = (ArgumentFactoryImpl) ArgumentFactory.getDefault();
		
		argumentFactory.addBuilderConfigureFunction(Emote.class, (parameter, builder) -> builder.setProperty("global", parameter.isAnnotationPresent(Global.class)))
			.addBuilderConfigureFunction(Attachment.class, (parameter, builder) -> builder.setAcceptEmpty(true))
			.addGenericBuilderConfigureFunction(Object.class, (parameter, builder) -> builder.setProperty("parameter", parameter))
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
				Class<?> clazz = (Class<?>) ((ParameterizedType) parameter.getParameterizedType()).getActualTypeArguments()[0];

				List<?> builders = argumentFactory.getBuilderConfigureFunctions(clazz);
				for (Object builderFunction : builders) {
					builder = ((BuilderConfigureFunction) builderFunction).configure(parameter, builder);
				}

				builder.setProperty("class", clazz);

				return builder;
			}).addBuilderConfigureFunction(Range.class, (parameter, builder) -> {
				Class<?> clazz = (Class<?>) ((ParameterizedType) parameter.getParameterizedType()).getActualTypeArguments()[0];

				List<?> builders = argumentFactory.getBuilderConfigureFunctions(clazz);
				for (Object builderFunction : builders) {
					builder = ((BuilderConfigureFunction) builderFunction).configure(parameter, builder);
				}

				builder.setProperty("class", clazz);

				return builder;
			}).addBuilderConfigureFunction(Alternative.class, (parameter, builder) -> {
				Class<?> clazz = (Class<?>) ((ParameterizedType) parameter.getParameterizedType()).getActualTypeArguments()[0];

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

					builder.setProperty("options", enums);
				}

				return builder;
			}).addBuilderConfigureFunction(Or.class, (parameter, builder) -> {
				Type[] classes = ((ParameterizedType) parameter.getParameterizedType()).getActualTypeArguments();
				Class<?> firstClass = (Class<?>) classes[0], secondClass = (Class<?>) classes[1];

				builder.setProperty("firstClass", firstClass);
				builder.setProperty("secondClass", secondClass);

				return builder;
			});
			
		argumentFactory.registerParser(Member.class, (context, argument, content) -> new ParsedResult<>(SearchUtility.getMember(context.getMessage().getGuild(), content.trim())))
			.registerParser(TextChannel.class, (context, argument, content) -> new ParsedResult<>(SearchUtility.getTextChannel(context.getMessage().getGuild(), content.trim())))
			.registerParser(Duration.class, (context, argument, content) -> new ParsedResult<>(TimeUtility.getDurationFromString(content)))
			.registerParser(List.class, (context, argument, content) -> new ParsedResult<>(SearchUtility.getCommandOrModule(this.commandListener, content)))
			.registerParser(Reason.class, (context, argument, content) -> new ParsedResult<>(Reason.parse(this.database, context.getMessage().getGuild().getIdLong(), content)))
			.registerParser(ObjectId.class, (context, argument, content) -> new ParsedResult<>(ObjectId.isValid(content) ? new ObjectId(content) : null))
			.registerParser(IPermissionHolder.class, (context, argument, content) -> new ParsedResult<>(SearchUtility.getPermissionHolder(context.getMessage().getGuild(), content)))
			.registerParser(Role.class, (context, argument, content) -> new ParsedResult<>(SearchUtility.getRole(context.getMessage().getGuild(), content)))
			.registerParser(Attachment.class, (context, argument, content) -> context.getMessage().getAttachments().isEmpty() ? new ParsedResult<>() : new ParsedResult<>(context.getMessage().getAttachments().get(0)))
			.registerParser(Emote.class, (context, argument, content) -> new ParsedResult<>(argument.getProperty("global") ? SearchUtility.getEmote(this.shardManager, content) : SearchUtility.getGuildEmote(context.getMessage().getGuild(), content)))
			.registerParser(Guild.class, (context, argument, content) -> new ParsedResult<>(SearchUtility.getGuild(this.shardManager, content)))
			.registerParser(MessageArgument.class, (context, argument, content) -> new ParsedResult<>(SearchUtility.getMessageArgument(context.getMessage().getTextChannel(), content)))
			.registerParser(ReactionEmote.class, (context, argument, content) -> new ParsedResult<>(SearchUtility.getReactionEmote(this.shardManager, content)))
			.registerParser(Sx4Command.class, (context, argument, content) -> new ParsedResult<>(SearchUtility.getCommand(this.commandListener, content)))
			.registerParser(TimeZone.class, (context, argument, content) -> new ParsedResult<>(TimeZone.getTimeZone(content.toUpperCase().replace("UTC", "GMT"))))
			.registerParser(ReminderArgument.class, (context, argument, content) -> {
				try {
					return new ParsedResult<>(ReminderArgument.parse(this.database, context.getMessage().getAuthor().getIdLong(), content));
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
			}).registerParser(String.class, (context, argument, content) -> {
				boolean imageUrl = argument.getProperty("imageUrl", false);
				if (imageUrl || argument.getProperty("url", false)) {
					Message message = context.getMessage();

					if (content.isEmpty()) {
						Attachment attachment = message.getAttachments().stream()
							.filter(Attachment::isImage)
							.findFirst()
							.orElse(null);

						if (attachment == null) {
							return imageUrl ? new ParsedResult<>(message.getAuthor().getEffectiveAvatarUrl()) : new ParsedResult<>();
						} else {
							return new ParsedResult<>(attachment.getUrl());
						}
					}

					if (imageUrl) {
						Member member = SearchUtility.getMember(message.getGuild(), content);
						if (member != null) {
							return new ParsedResult<>(member.getUser().getEffectiveAvatarUrl());
						}
					}

					try {
						new URL(content);
					} catch (MalformedURLException e) {
						return new ParsedResult<>();
					}
				}

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
			}).registerParser(PartialEmote.class, (context, argument, content) -> {
				if (content.isEmpty()) {
					Attachment attachment = context.getMessage().getAttachments().stream()
						.filter(Attachment::isImage)
						.findFirst()
						.orElse(null);
					
					if (attachment != null) {
						return new ParsedResult<>(new PartialEmote(attachment.getUrl(), attachment.getFileName(), attachment.getFileExtension().equalsIgnoreCase("gif")));
					}
					
					return new ParsedResult<>();
				}
				
				PartialEmote partialEmote = SearchUtility.getPartialEmote(this.shardManager, content);
				if (partialEmote != null) {
					return new ParsedResult<>(partialEmote);
				}
				
				try {
					new URL(content);
				} catch (MalformedURLException e) {
					return new ParsedResult<>();
				}
				
				String extension = StringUtility.getFileExtension(content);
				if (extension != null) {
					return new ParsedResult<>(new PartialEmote(content, null, extension.equalsIgnoreCase("gif")));
				} else {
					return new ParsedResult<>();
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

				ParsedResult<?> parsedArgument = CommandUtility.getParsedResult(clazz, argumentFactory, context, argument, index == -1 ? content : content.substring(0, index));
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
			}).registerParser(Alternative.class, (context, argument, content) -> {
				String[] options = argument.getProperty("options", new String[0]);
				for (String option : options) {
					if (content.equalsIgnoreCase(option)) {
						return new ParsedResult<>(new Alternative<>(null, option));
					}
				}

				Class<?> clazz = argument.getProperty("class", Class.class);

				ParsedResult<?> parsedArgument = CommandUtility.getParsedResult(clazz, argumentFactory, context, argument, content);
				if (!parsedArgument.isValid()) {
					return new ParsedResult<>();
				}
					
				return new ParsedResult<>(new Alternative<>(parsedArgument.getObject(), null));
			}).registerGenericParser(Enum.class, (context, type, argument, content) -> {
				List<Enum<?>> options = argument.getProperty("options");

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

				ParsedResult<?> firstParsedArgument = CommandUtility.getParsedResult(firstClass, argumentFactory, context, argument, content);
				ParsedResult<?> secondParsedArgument = CommandUtility.getParsedResult(secondClass, argumentFactory, context, argument, content);
				
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
			if (limit != null) {
				content = Math.min(limit.max(), Math.max(limit.min(), content));
			}
			
			return new ParsedResult<>(content);
		}).addParserAfter(Double.class, (context, argument, content) -> {
			Limit limit = argument.getProperty("limit", Limit.class);
			if (limit != null) {
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
			.registerResponse(Duration.class, "Invalid time string given, a good example would be `5d 1h 24m 36s` " + this.config.getFailureEmote())
			.registerResponse(ObjectId.class, "Invalid id given, an example id would be `5e45ce6d3688b30ee75201ae` " + this.config.getFailureEmote())
			.registerResponse(List.class, "I could not find that command/module " + this.config.getFailureEmote())
			.registerResponse(MessageArgument.class, "I could not find that message " + this.config.getFailureEmote())
			.registerResponse(ReminderArgument.class, "Invalid reminder format given, view `help reminder add` for more info " + this.config.getFailureEmote())
			.registerResponse(PartialEmote.class, "I could not find that emote " + this.config.getFailureEmote())
			.registerResponse(Guild.class, "I could not find that server " + this.config.getFailureEmote())
			.registerResponse(AutoRoleFilter.class, "I could not find that filter " + this.config.getFailureEmote())
			.registerResponse(Pattern.class, "Regex syntax was incorrect " + this.config.getFailureEmote())
			.registerResponse(Integer.class, (argument, message, content) -> {
				if (argument.getProperty("colour", false)) {
					message.getChannel().sendMessage("I could not find that colour " + this.config.getFailureEmote()).queue();
				} else {
					message.getChannel().sendMessage("The argument `" + argument.getName() + "` needs to be a number " + this.config.getFailureEmote()).queue();
				}
			}).registerResponse(String.class, (argument, message, content) -> {
				if (argument.getProperty("imageUrl", false) || argument.getProperty("url", false)) {
					message.getChannel().sendMessage("Invalid url given " + this.config.getFailureEmote()).queue();
				}

				String[] options = argument.getProperty("options");
				if (options != null && options.length != 0) {
					message.getChannel().sendMessageFormat("Invalid option given, `%s` are valid options %s", String.join("`, `", options), this.config.getFailureEmote()).queue();
				}

				Limit limit = argument.getProperty("limit", Limit.class);
				if (limit != null && content.length() < limit.min()) {
					message.getChannel().sendMessageFormat("You cannot use less than **%,d** character%s for `%s` %s", limit.min(), limit.min() == 1 ? "" : "s", argument.getName(), this.config.getFailureEmote()).queue();
				}

				if (limit != null && content.length() > limit.max()) {
					message.getChannel().sendMessageFormat("You cannot use more than **%,d** character%s for `%s` %s", limit.max(), limit.max() == 1 ? "" : "s", argument.getName(), this.config.getFailureEmote()).queue();
				}
			}).registerResponse(Enum.class, (argument, message, content) -> {
				List<Enum<?>> enums = argument.getProperty("options", Arrays.asList(argument.getType().getEnumConstants()));

				StringJoiner joiner = new StringJoiner("`, `");
				for (Enum<?> enumEntry : enums) {
					joiner.add(enumEntry.name());
				}

				message.getChannel().sendMessage("Invalid argument given, give any of the following `" + joiner.toString() + "` " + this.config.getFailureEmote()).queue();
			}).setHandleInheritance(Enum.class, true);
	}
	
	public static void main(String[] args) throws Exception {
		Sx4 bot = new Sx4();

		Sx4Server.initiateWebserver(bot);
		
		Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> {
			System.err.println("[Uncaught]");
			
			exception.printStackTrace();
			
			ExceptionUtility.sendErrorMessage(bot.getShardManager(), exception);
		});
	}

}
