package com.sx4.bot.core;

import com.jockie.bot.core.argument.IArgument;
import com.jockie.bot.core.argument.factory.impl.ArgumentFactory;
import com.jockie.bot.core.argument.factory.impl.ArgumentFactoryImpl;
import com.jockie.bot.core.argument.factory.impl.BuilderConfigureFunction;
import com.jockie.bot.core.command.exception.parser.ArgumentParseException;
import com.jockie.bot.core.command.exception.parser.OutOfContentException;
import com.jockie.bot.core.command.factory.impl.MethodCommandFactory;
import com.jockie.bot.core.command.impl.CommandListener;
import com.jockie.bot.core.command.impl.CommandListener.Failure;
import com.jockie.bot.core.command.impl.CommandStore;
import com.jockie.bot.core.command.manager.IErrorManager;
import com.jockie.bot.core.command.manager.impl.ContextManagerFactory;
import com.jockie.bot.core.command.manager.impl.ErrorManagerImpl;
import com.jockie.bot.core.option.factory.impl.OptionFactory;
import com.jockie.bot.core.option.factory.impl.OptionFactoryImpl;
import com.jockie.bot.core.parser.ParsedResult;
import com.jockie.bot.core.parser.impl.essential.EnumParser;
import com.sx4.api.Sx4Server;
import com.sx4.bot.annotations.argument.*;
import com.sx4.bot.cache.GuildMessageCache;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.config.Config;
import com.sx4.bot.entities.argument.*;
import com.sx4.bot.entities.management.AutoRoleFilter;
import com.sx4.bot.entities.mod.PartialEmote;
import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.entities.mod.action.ModAction;
import com.sx4.bot.handlers.*;
import com.sx4.bot.managers.ModActionManager;
import com.sx4.bot.managers.PatreonManager;
import com.sx4.bot.managers.YouTubeManager;
import com.sx4.bot.paged.PagedHandler;
import com.sx4.bot.utility.*;
import com.sx4.bot.waiter.WaiterHandler;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.entities.MessageReaction.ReactionEmote;
import net.dv8tion.jda.api.hooks.InterfacedEventManager;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import okhttp3.OkHttpClient;
import org.bson.types.ObjectId;

import javax.security.auth.login.LoginException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.DateTimeException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

public class Sx4 {
	
	private static final Sx4 INSTANCE = new Sx4();
	
	public static Sx4 get() {
		return Sx4.INSTANCE;
	}
	
	private final Config config = Config.get();

	private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
		.connectTimeout(15, TimeUnit.SECONDS)
		.readTimeout(15, TimeUnit.SECONDS)
		.writeTimeout(15, TimeUnit.SECONDS)
		.build();

	private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
	
	private final CommandListener commandListener;
	private final ShardManager shardManager;
	
	private Sx4() {
		ModActionManager.get()
			.addListener(ModHandler.INSTANCE);
	
		YouTubeManager.get()
			.addListener(YouTubeHandler.get());
		
		PatreonManager.get()
			.addListener(PatreonHandler.INSTANCE);
		
		MessageAction.setDefaultMentions(EnumSet.noneOf(Message.MentionType.class));
		
		MethodCommandFactory.setDefault(new Sx4CommandFactory());
		
		ContextManagerFactory.getDefault()
			.registerContext(Sx4CommandEvent.class, (event, type) -> (Sx4CommandEvent) event)
			.setEnforcedContext(Sx4CommandEvent.class, true);
		
		this.setupArgumentFactory();
		this.setupOptionFactory();
		
		this.commandListener = this.createCommandListener(this.createErrorManager());
		this.shardManager = this.createShardManager();
	}

	public static OkHttpClient getClient() {
		return Sx4.CLIENT;
	}

	public static ExecutorService getExecutor() {
		return Sx4.EXECUTOR;
	}
	
	public CommandListener getCommandListener() {
		return this.commandListener;
	}
	
	public ShardManager getShardManager() {
		return this.shardManager;
	}
	
	private ShardManager createShardManager() {
		try {
			InterfacedEventManager eventManager = new InterfacedEventManager();
			eventManager.register(this.commandListener);
			eventManager.register(new PagedHandler());
			eventManager.register(new WaiterHandler());
			eventManager.register(new GiveawayHandler());
			eventManager.register(ModHandler.INSTANCE);
			eventManager.register(PatreonHandler.INSTANCE);
			eventManager.register(new GuildMessageCache());
			eventManager.register(new ConnectionHandler());
			eventManager.register(new ReactionRoleHandler());
			eventManager.register(new LoggerHandler());
			eventManager.register(new AntiRegexHandler());

			return DefaultShardManagerBuilder.create(this.config.getToken(), GatewayIntent.getIntents(6094))
				.setBulkDeleteSplittingEnabled(false)
				.setEventManagerProvider(shardId -> eventManager)
				.build();
		} catch (LoginException | IllegalArgumentException e) {
			System.exit(1);
			return null;
		}
	}
	
	private CommandListener createCommandListener(IErrorManager errorManager) {
		return new Sx4CommandListener()
			.removePreExecuteCheck(listener -> listener.defaultAuthorPermissionCheck)
			.addCommandStores(CommandStore.of("com.sx4.bot.commands"))
			.addDevelopers(402557516728369153L, 190551803669118976L)
			.setErrorManager(errorManager)
			.setCommandEventFactory(new Sx4CommandEventFactory())
			.setDefaultPrefixes("!")
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
				Set<Permission> permissions = command.getAuthorDiscordPermissions();
				if (permissions.isEmpty()) {
					return true;
				}

				EnumSet<Permission> missingPermissions = CheckUtility.missingPermissions(event.getMember(), event.getTextChannel(), EnumSet.copyOf(permissions));
				if (missingPermissions.isEmpty()) {
					return true;
				} else {
					event.reply(PermissionUtility.formatMissingPermissions(missingPermissions)).queue();
					return false;
				}
			});
	}

	private void setupOptionFactory() {
		OptionFactoryImpl optionFactory = (OptionFactoryImpl) OptionFactory.getDefault();

		optionFactory.addBuilderConfigureFunction(Integer.class, (parameter, builder) -> {
			builder.setProperty("colour", parameter.isAnnotationPresent(Colour.class));

			Limit limit = parameter.getAnnotation(Limit.class);
			if (limit != null) {
				builder.setProperty("upperLimit", limit.max());
				builder.setProperty("lowerLimit", limit.min());
			}

			DefaultInt defaultInt = parameter.getAnnotation(DefaultInt.class);
			if (defaultInt != null) {
				builder.setDefaultValue(defaultInt.value());
			}

			return builder;
		}).addBuilderConfigureFunction(Long.class, (parameter, builder) -> {
			DefaultLong defaultLong = parameter.getAnnotation(DefaultLong.class);
			if (defaultLong != null) {
				builder.setDefaultValue(defaultLong.value());
			}

			return builder;
		});

		optionFactory.registerParser(Duration.class, (context, option, content) -> content == null ? new ParsedResult<>(true, null) : new ParsedResult<>(TimeUtility.getDurationFromString(content)));

		optionFactory.addParserAfter(Integer.class, (context, argument, content) -> {
			Integer lowerLimit = argument.getProperty("lowerLimit", Integer.class);
			if (lowerLimit != null) {
				content = Math.max(lowerLimit, content);
			}

			Integer upperLimit = argument.getProperty("upperLimit", Integer.class);
			if (upperLimit != null) {
				content = Math.min(upperLimit, content);
			}

			return new ParsedResult<>(content);
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private void setupArgumentFactory() {
		ArgumentFactoryImpl argumentFactory = (ArgumentFactoryImpl) ArgumentFactory.getDefault();
		
		argumentFactory.addBuilderConfigureFunction(String.class, (parameter, builder) -> {
			builder.setProperty("lowercase", parameter.isAnnotationPresent(Lowercase.class));
			builder.setProperty("uppercase", parameter.isAnnotationPresent(Uppercase.class));
			
			DefaultString defaultString = parameter.getAnnotation(DefaultString.class);
			if (defaultString != null) {
				builder.setDefaultValue(defaultString.value());
			}
			
			return builder;
		}).addBuilderConfigureFunction(Integer.class, (parameter, builder) -> {
			builder.setProperty("colour", parameter.isAnnotationPresent(Colour.class));
			
			Limit limit = parameter.getAnnotation(Limit.class);
			if (limit != null) {
				builder.setProperty("upperLimit", limit.max());
				builder.setProperty("lowerLimit", limit.min());
			}
			
			DefaultInt defaultInt = parameter.getAnnotation(DefaultInt.class);
			if (defaultInt != null) {
				builder.setDefaultValue(defaultInt.value());
			}
			
			return builder;
		}).addBuilderConfigureFunction(Long.class, (parameter, builder) -> {
			DefaultLong defaultLong = parameter.getAnnotation(DefaultLong.class);
			if (defaultLong != null) {
				builder.setDefaultValue(defaultLong.value());
			}
			
			return builder;
		}).addBuilderConfigureFunction(UpdateType.class, (parameter, builder) -> {
			ExcludeUpdate exclude = parameter.getAnnotation(ExcludeUpdate.class);
			if (exclude != null) {
				UpdateType[] excluded = exclude.value();
				if (excluded.length != 0) {
					List<UpdateType> values = new LinkedList<>(Arrays.asList(UpdateType.values()));
					for (UpdateType type : exclude.value()) {
						values.remove(type);
					}
					
					builder.setProperty("updates", values);
				}
			}
			
			return builder;
		}).addBuilderConfigureFunction(TimedArgument.class, (parameter, builder) -> {
			Class<?> clazz = (Class<?>) ((ParameterizedType) parameter.getParameterizedType()).getActualTypeArguments()[0];
			
			builder.setProperty("class", clazz);
			
			List<?> builders = argumentFactory.getBuilderConfigureFunctions(clazz);
			for (Object builderFunction : builders) {
				builder = ((BuilderConfigureFunction) builderFunction).configure(parameter, builder);
			}
			
			return builder;
		}).addBuilderConfigureFunction(All.class, (parameter, builder) -> {
			Class<?> clazz = (Class<?>) ((ParameterizedType) parameter.getParameterizedType()).getActualTypeArguments()[0];
			
			builder.setProperty("class", clazz);
			
			List<?> builders = argumentFactory.getBuilderConfigureFunctions(clazz);
			for (Object builderFunction : builders) {
				builder = ((BuilderConfigureFunction) builderFunction).configure(parameter, builder);
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
			.registerParser(User.class, (context, argument, content) -> new ParsedResult<>(SearchUtility.getUser(content.trim())))
			.registerParser(TextChannel.class, (context, argument, content) -> new ParsedResult<>(SearchUtility.getTextChannel(context.getMessage().getGuild(), content.trim())))
			.registerParser(Duration.class, (context, argument, content) -> new ParsedResult<>(TimeUtility.getDurationFromString(content)))
			.registerParser(Reason.class, (context, argument, content) -> new ParsedResult<>(new Reason(context.getMessage().getGuild().getIdLong(), content)))
			.registerParser(ObjectId.class, (context, argument, content) -> new ParsedResult<>(ObjectId.isValid(content) ? new ObjectId(content) : null))
			.registerParser(List.class, (context, argument, content) -> new ParsedResult<>(SearchUtility.getCommandOrModule(content)))
			.registerParser(IPermissionHolder.class, (context, argument, content) -> new ParsedResult<>(SearchUtility.getPermissionHolder(context.getMessage().getGuild(), content)))
			.registerParser(Role.class, (context, argument, content) -> new ParsedResult<>(SearchUtility.getRole(context.getMessage().getGuild(), content)))
			.registerParser(Emote.class, (context, argument, content) -> new ParsedResult<>(SearchUtility.getGuildEmote(context.getMessage().getGuild(), content)))
			.registerParser(Guild.class, (context, argument, content) -> new ParsedResult<>(SearchUtility.getGuild(content)))
			.registerParser(MessageArgument.class, (context, argument, content) -> new ParsedResult<>(SearchUtility.getMessageArgument(context.getMessage().getTextChannel(), content)))
			.registerParser(ReactionEmote.class, (context, argument, content) -> new ParsedResult<>(SearchUtility.getReactionEmote(content)))
			.registerParser(ModAction.class, new EnumParser<>())
			.registerParser(TimeZone.class, (context, argument, content) -> new ParsedResult<>(TimeZone.getTimeZone(content.toUpperCase().replace("UTC", "GMT"))))
			.registerParser(ReminderArgument.class, (context, argument, content) -> {
				try {
					return new ParsedResult<>(new ReminderArgument(context.getMessage().getAuthor().getIdLong(), content));
				} catch (DateTimeException | IllegalArgumentException e) {
					return new ParsedResult<>();
				}
			})
			.registerParser(URL.class, (context, argument, content) -> {
				if (content.isEmpty()) {
					Attachment attachment = context.getMessage().getAttachments().stream()
						.filter(Attachment::isImage)
						.findFirst()
						.orElse(null);
					
					if (attachment != null) {
						try {
							return new ParsedResult<>(new URL(attachment.getUrl()));
						} catch (MalformedURLException e) {}
					}
					
					return new ParsedResult<>();
				} else {
					return new ParsedResult<>(SearchUtility.getURL(context.getMessage(), content));
				}
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
				
				PartialEmote partialEmote = SearchUtility.getPartialEmote(content);
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
			}).registerParser(TimedArgument.class, (context, argument, content) -> {
				Class<?> clazz = argument.getProperty("class", Class.class);
				
				ParsedResult<?> parsedArgument = argumentFactory.getParser(clazz).parse(context, (IArgument) argument, content);
				if (!parsedArgument.isValid()) {
					return new ParsedResult<>();
				}
				
				int lastIndex = content.lastIndexOf(' ');
				if (lastIndex == -1) {
					return new ParsedResult<>(new TimedArgument<>(null, parsedArgument.getObject()));
				}
				
				Duration duration = TimeUtility.getDurationFromString(content.substring(lastIndex));
				
				return new ParsedResult<>(new TimedArgument<>(duration, parsedArgument.getObject()));
			}).registerParser(All.class, (context, argument, content) -> {
				if (content.equalsIgnoreCase("all")) {
					return new ParsedResult<>(new All<>(null));
				} else {
					Class<?> clazz = argument.getProperty("class", Class.class);

					ParsedResult<?> parsedArgument = argumentFactory.getParser(clazz).parse(context, (IArgument) argument, content);
					if (!parsedArgument.isValid()) {
						return new ParsedResult<>();
					}
					
					return new ParsedResult<>(new All<>(parsedArgument.getObject()));
				}
			}).registerParser(Or.class, (context, argument, content) -> {
				Class<?> firstClass = argument.getProperty("firstClass"), secondClass = argument.getProperty("secondClass");

				ParsedResult<?> firstParsedArgument = argumentFactory.getParser(firstClass).parse(context, (IArgument) argument, content);
				ParsedResult<?> secondParsedArgument = argumentFactory.getParser(secondClass).parse(context, (IArgument) argument, content);
				
				if (firstParsedArgument.isValid()) {
					return new ParsedResult<>(new Or<>(firstParsedArgument.getObject(), null));
				} else if (secondParsedArgument.isValid()) {
					return new ParsedResult<>(new Or<>(null, secondParsedArgument.getObject()));
				} else {
					return new ParsedResult<>();
				}
			});
		
		argumentFactory.addParserAfter(String.class, (context, argument, content) -> {
			if (argument.getProperty("lowercase", false)) {
				content = content.toLowerCase();
			}
			
			if (argument.getProperty("uppercase", false)) {
				content = content.toUpperCase();
			}
			
			return new ParsedResult<>(content);
		}).addParserAfter(Integer.class, (context, argument, content) -> {
			Integer lowerLimit = argument.getProperty("lowerLimit", Integer.class);
			if (lowerLimit != null) {
				content = Math.max(lowerLimit, content);
			}

			Integer upperLimit = argument.getProperty("upperLimit", Integer.class);
			if (upperLimit != null) {
				content = Math.min(upperLimit, content);
			}
			
			return new ParsedResult<>(content);
		}).addParserAfter(UpdateType.class, (context, argument, content) -> {
			List<UpdateType> updates = argument.getProperty("updates", Collections.emptyList());
			
			boolean match = updates.stream().anyMatch(content::equals);
			
			return new ParsedResult<>(match ? content : null);
		});
	}
	
	@SuppressWarnings("unchecked")
	private IErrorManager createErrorManager() {
		return new ErrorManagerImpl()
			.registerResponse(Member.class, "I could not find that user " + this.config.getFailureEmote())
			.registerResponse(User.class, "I could not find that user " + this.config.getFailureEmote())
			.registerResponse(Role.class, "I could not find that role " + this.config.getFailureEmote())
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
			.registerResponse(URL.class, "Invalid image given " + this.config.getFailureEmote())
			.registerResponse(MessageArgument.class, "I could not find that message " + this.config.getFailureEmote())
			.registerResponse(ReminderArgument.class, "Invalid reminder format given, view `help reminder add` for more info " + this.config.getFailureEmote())
			.registerResponse(PartialEmote.class, "I could not find that emote " + this.config.getFailureEmote())
			.registerResponse(Guild.class, "I could not find that server " + this.config.getFailureEmote())
			.registerResponse(AutoRoleFilter.class, "I could not find that filter " + this.config.getFailureEmote())
			.registerResponse(Pattern.class, "Regex syntax was incorrect " + this.config.getFailureEmote())
			.registerResponse(UpdateType.class, (argument, message, content) -> {
				List<UpdateType> updates = argument.getProperty("updates", List.class);
				message.getChannel().sendMessage("Invalid update type given, update types you can use are `" + updates.stream().map(t -> t.name().toLowerCase()).collect(Collectors.joining("`, `")) + "` " + this.config.getFailureEmote()).queue();
			}).registerResponse(int.class, (argument, message, content) -> {
				if (argument.getProperty("colour", boolean.class)) {
					message.getChannel().sendMessage("I could not find that colour " + this.config.getFailureEmote()).queue();
				} else {
					message.getChannel().sendMessage("The argument `" + argument.getName() + "` needs to be a number " + this.config.getFailureEmote()).queue();
				}
			});
	}
	
	public static void main(String[] args) throws Exception {
		Sx4Server.initiateWebserver();
		
		Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> {
			System.err.println("[Uncaught]");
			
			exception.printStackTrace();
			
			ExceptionUtility.sendErrorMessage(exception);
		});
	}
	
}
