package com.sx4.bot.core;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.DateTimeException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;

import com.jockie.bot.core.argument.IArgument;
import com.jockie.bot.core.argument.factory.impl.ArgumentFactory;
import com.jockie.bot.core.argument.factory.impl.ArgumentFactoryImpl;
import com.jockie.bot.core.argument.factory.impl.BuilderConfigureFunction;
import com.jockie.bot.core.argument.parser.ParsedArgument;
import com.jockie.bot.core.command.exception.parser.ArgumentParseException;
import com.jockie.bot.core.command.exception.parser.OutOfContentException;
import com.jockie.bot.core.command.factory.impl.MethodCommandFactory;
import com.jockie.bot.core.command.impl.CommandListener;
import com.jockie.bot.core.command.impl.CommandListener.Failure;
import com.jockie.bot.core.command.impl.CommandStore;
import com.jockie.bot.core.command.manager.IErrorManager;
import com.jockie.bot.core.command.manager.impl.ContextManagerFactory;
import com.jockie.bot.core.command.manager.impl.ErrorManagerImpl;
import com.sx4.api.Main;
import com.sx4.bot.annotations.argument.Colour;
import com.sx4.bot.annotations.argument.DefaultInt;
import com.sx4.bot.annotations.argument.DefaultLong;
import com.sx4.bot.annotations.argument.DefaultString;
import com.sx4.bot.annotations.argument.ExcludeUpdate;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.annotations.argument.Lowercase;
import com.sx4.bot.annotations.argument.Uppercase;
import com.sx4.bot.config.Config;
import com.sx4.bot.entities.argument.All;
import com.sx4.bot.entities.argument.MessageArgument;
import com.sx4.bot.entities.argument.Or;
import com.sx4.bot.entities.argument.UpdateType;
import com.sx4.bot.entities.mod.PartialEmote;
import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.entities.reminder.ReminderArgument;
import com.sx4.bot.handlers.ConnectionHandler;
import com.sx4.bot.handlers.ModHandler;
import com.sx4.bot.handlers.PatreonHandler;
import com.sx4.bot.handlers.ReactionRoleHandler;
import com.sx4.bot.handlers.YouTubeHandler;
import com.sx4.bot.managers.ModActionManager;
import com.sx4.bot.managers.PatreonManager;
import com.sx4.bot.managers.YouTubeManager;
import com.sx4.bot.message.cache.GuildMessageCache;
import com.sx4.bot.paged.PagedHandler;
import com.sx4.bot.utility.ColourUtility;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.HelpUtility;
import com.sx4.bot.utility.SearchUtility;
import com.sx4.bot.utility.StringUtility;
import com.sx4.bot.utility.TimeUtility;
import com.sx4.bot.waiter.WaiterHandler;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Category;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.IPermissionHolder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageReaction.ReactionEmote;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.hooks.InterfacedEventManager;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import okhttp3.OkHttpClient;

public class Sx4Bot {
	
	private static ShardManager shardManager;
	private static CommandListener commandListener;
	
	private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
			.connectTimeout(15, TimeUnit.SECONDS)
			.readTimeout(15, TimeUnit.SECONDS)
			.writeTimeout(15, TimeUnit.SECONDS)
			.build();
	
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void main(String[] args) throws Throwable {
		ModActionManager.get()
			.addListener(new ModHandler());
		
		YouTubeManager.get()
			.addListener(new YouTubeHandler());
		
		PatreonManager.get()
			.addListener(new PatreonHandler());
		
		MethodCommandFactory.setDefault(new Sx4CommandFactory());
		
		ContextManagerFactory.getDefault()
			.registerContext(Sx4CommandEvent.class, (event, type) -> {
				return (Sx4CommandEvent) event;
			})
			.setEnforcedContext(Sx4CommandEvent.class, true);
		
		IErrorManager errorManager = new ErrorManagerImpl()
			.registerResponse(Member.class, "I could not find that user :no_entry:")
			.registerResponse(User.class, "I could not find that user :no_entry:")
			.registerResponse(Role.class, "I could not find that role :no_entry:")
			.registerResponse(ReactionEmote.class, "I could not find that emote :no_entry:")
			.registerResponse(TextChannel.class, "I could not find that text channel :no_entry:")
			.registerResponse(VoiceChannel.class, "I could not find that voice channel :no_entry:")
			.registerResponse(Category.class, "I could not find that category :no_entry:")
			.registerResponse(GuildChannel.class, "I could not find that channel :no_entry:")
			.registerResponse(IPermissionHolder.class, "I could not find that user/role :no_entry:")
			.registerResponse(Emote.class, "I could not find that emote :no_entry:")
			.registerResponse(Duration.class, "Invalid time string given, a good example would be `5d 1h 24m 36s` :no_entry:")
			.registerResponse(ObjectId.class, "Invalid id given, an example id would be `5e45ce6d3688b30ee75201ae` :no_entry:")
			.registerResponse(List.class, "I could not find that command/module :no_entry:")
			.registerResponse(URL.class, "Invalid image given :no_entry:")
			.registerResponse(MessageArgument.class, "I could not find that message :no_entry:")
			.registerResponse(ReminderArgument.class, "Invalid reminder format given, view `help reminder add` for more info :no_entry:")
			.registerResponse(PartialEmote.class, "I could not find that emote :no_entry:")
			.registerResponse(Guild.class, "I could not find that server :no_entry:")
			.registerResponse(UpdateType.class, (argument, message, content) -> {
				List<UpdateType> updates = argument.getProperty("updates", List.class);
				message.getChannel().sendMessage("Invalid update type given, update types you can use are `" + updates.stream().map(t -> t.name().toLowerCase()).collect(Collectors.joining("`, `")) + "` :no_entry:").queue();
			}).registerResponse(int.class, (argument, message, content) -> {
				if (argument.getProperty("colour", boolean.class)) {
					message.getChannel().sendMessage("I could not find that colour :no_entry:").queue();
				} else {
					message.getChannel().sendMessage("The argument `" + argument.getName() + "` needs to be a number :no_entry:").queue();
				}
			});
		
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
			
		argumentFactory.registerParser(Member.class, (context, argument, content) -> new ParsedArgument<>(SearchUtility.getMember(context.getMessage().getGuild(), content.trim())))
			.registerParser(User.class, (context, argument, content) -> new ParsedArgument<>(SearchUtility.getUser(content.trim())))
			.registerParser(TextChannel.class, (context, argument, content) -> new ParsedArgument<>(SearchUtility.getTextChannel(context.getMessage().getGuild(), content.trim())))
			.registerParser(Duration.class, (context, argument, content) -> new ParsedArgument<>(TimeUtility.getDurationFromString(content)))
			.registerParser(Reason.class, (context, argument, content) -> new ParsedArgument<>(new Reason(context.getMessage().getGuild().getIdLong(), content)))
			.registerParser(ObjectId.class, (context, argument, content) -> new ParsedArgument<>(ObjectId.isValid(content) ? new ObjectId(content) : null))
			.registerParser(List.class, (context, argument, content) -> new ParsedArgument<>(SearchUtility.getCommandOrModule(content)))
			.registerParser(IPermissionHolder.class, (context, argument, content) -> new ParsedArgument<>(SearchUtility.getPermissionHolder(context.getMessage().getGuild(), content)))
			.registerParser(Role.class, (context, argument, content) -> new ParsedArgument<>(SearchUtility.getRole(context.getMessage().getGuild(), content)))
			.registerParser(Emote.class, (context, argument, content) -> new ParsedArgument<>(SearchUtility.getGuildEmote(context.getMessage().getGuild(), content)))
			.registerParser(Guild.class, (context, argument, content) -> new ParsedArgument<>(SearchUtility.getGuild(content)))
			.registerParser(MessageArgument.class, (context, argument, content) -> new ParsedArgument<>(SearchUtility.getMessageArgument(context.getMessage().getTextChannel(), content)))
			.registerParser(ReactionEmote.class, (context, argument, content) -> new ParsedArgument<>(SearchUtility.getReactionEmote(content)))
			.registerParser(TimeZone.class, (context, argument, content) -> new ParsedArgument<>(TimeZone.getTimeZone(content.toUpperCase().replace("UTC", "GMT"))))
			.registerParser(ReminderArgument.class, (context, argument, content) -> {
				try {
					return new ParsedArgument<>(new ReminderArgument(context.getMessage().getAuthor().getIdLong(), content));
				} catch (DateTimeException | IllegalArgumentException e) {
					return new ParsedArgument<>();
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
							return new ParsedArgument<>(new URL(attachment.getUrl()));
						} catch (MalformedURLException e) {}
					}
					
					return new ParsedArgument<>();
				} else {
					return new ParsedArgument<>(SearchUtility.getURL(context.getMessage(), content));
				}
			}).registerParser(Integer.class, (context, argument, content) -> {
				if (argument.getProperty("colour", false)) {
					int colour = ColourUtility.fromQuery(content);
					if (colour == -1) {
						return new ParsedArgument<>();
					} else {
						return new ParsedArgument<>(colour);
					}
				}
				
				try {
					return new ParsedArgument<>(Integer.parseInt(content));
				} catch (NumberFormatException e) {
					return new ParsedArgument<>();
				}
			}).registerParser(PartialEmote.class, (context, argument, content) -> {
				if (content.isEmpty()) {
					Attachment attachment = context.getMessage().getAttachments().stream()
						.filter(Attachment::isImage)
						.findFirst()
						.orElse(null);
					
					if (attachment != null) {
						return new ParsedArgument<>(new PartialEmote(attachment.getUrl(), attachment.getFileName(), attachment.getFileExtension().equalsIgnoreCase("gif")));
					}
					
					return new ParsedArgument<>();
				}
				
				PartialEmote partialEmote = SearchUtility.getPartialEmote(content);
				if (partialEmote != null) {
					return new ParsedArgument<>(partialEmote);
				}
				
				try {
					new URL(content);
				} catch (MalformedURLException e) {
					return new ParsedArgument<>();
				}
				
				String extension = StringUtility.getFileExtension(content);
				if (extension != null) {
					return new ParsedArgument<>(new PartialEmote(content, null, extension.equalsIgnoreCase("gif")));
				} else {
					return new ParsedArgument<>();
				}
			}).registerParser(All.class, (context, argument, content) -> {
				if (content.equalsIgnoreCase("all")) {
					return new ParsedArgument<>(new All<>(null));
				} else {
					Class<?> clazz = argument.getProperty("class", Class.class);
					
					ParsedArgument<?> parsedArgument = argumentFactory.getParser(clazz).parse(context, (IArgument) argument, content);
					if (!parsedArgument.isValid()) {
						return new ParsedArgument<>();
					}
					
					return new ParsedArgument<>(new All<>(parsedArgument.getObject()));
				}
			}).registerParser(Or.class, (context, argument, content) -> {
				Class<?> firstClass = argument.getProperty("firstClass"), secondClass = argument.getProperty("secondClass");
				
				ParsedArgument<?> firstParsedArgument = argumentFactory.getParser(firstClass).parse(context, (IArgument) argument, content);
				ParsedArgument<?> secondParsedArgument = argumentFactory.getParser(secondClass).parse(context, (IArgument) argument, content);
				
				if (firstParsedArgument.isValid()) {
					return new ParsedArgument<>(new Or<>(firstParsedArgument.getObject(), null));
				} else if (secondParsedArgument.isValid()) {
					return new ParsedArgument<>(new Or<>(null, secondParsedArgument.getObject()));
				} else {
					return new ParsedArgument<>();
				}
			});
		
		argumentFactory.addParserAfter(String.class, (context, argument, content) -> {
			if (argument.getProperty("lowercase", false)) {
				content = content.toLowerCase();
			}
			
			if (argument.getProperty("uppercase", false)) {
				content = content.toUpperCase();
			}
			
			return new ParsedArgument<>(content);
		}).addParserAfter(Integer.class, (context, argument, content) -> {
			Integer lowerLimit = argument.getProperty("lowerLimit", Integer.class);
			if (lowerLimit != null) {
				content = content < lowerLimit ? lowerLimit : content;
			}
			
			Integer upperLimit = argument.getProperty("upperLimit", Integer.class);
			if (upperLimit != null) {
				content = content > upperLimit ? upperLimit : content;
			}
			
			return new ParsedArgument<>(content);
		}).addParserAfter(UpdateType.class, (context, argument, content) -> {
			List<UpdateType> updates = argument.getProperty("updates", Collections.emptyList());
			
			boolean match = updates.stream().anyMatch(content::equals);
			
			return new ParsedArgument<>(match ? content : null);
		});
		
		Sx4Bot.commandListener = new Sx4CommandListener()
			.addCommandStores(CommandStore.of("com.sx4.bot.commands"))
			.addDevelopers(402557516728369153L, 190551803669118976L)
			.setErrorManager(errorManager)
			.setCommandEventFactory(new Sx4CommandEventFactory())
			.setDefaultPrefixes("!")
			.setHelpFunction((message, prefix, commands) -> {
				MessageChannel channel = message.getChannel();
				boolean embed = message.isFromGuild() ? message.getGuild().getSelfMember().hasPermission((TextChannel) channel, Permission.MESSAGE_EMBED_LINKS) : true;
				
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
								.flatMap(channel -> channel.sendMessage("I am missing the `" + Permission.MESSAGE_WRITE.getName() + "` permission in " + message.getTextChannel().getAsMention() + " :no_entry:"))
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
			});
				
		InterfacedEventManager eventManager = new InterfacedEventManager();
		eventManager.register(Sx4Bot.commandListener);
		eventManager.register(new PagedHandler());
		eventManager.register(new WaiterHandler());
		eventManager.register(GuildMessageCache.INSTANCE);
		eventManager.register(new ConnectionHandler());
		eventManager.register(new ReactionRoleHandler());
		
		Sx4Bot.shardManager = DefaultShardManagerBuilder.create(EnumSet.allOf(GatewayIntent.class))
			.setToken(Config.get().getToken())
			.disableIntents(GatewayIntent.DIRECT_MESSAGE_TYPING, GatewayIntent.DIRECT_MESSAGE_REACTIONS, GatewayIntent.GUILD_MESSAGE_TYPING)
			.setBulkDeleteSplittingEnabled(false)
			.setEventManagerProvider(shardId -> eventManager)
			.build();
		
		Main.initiateWebserver();
		
		Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> {
			System.err.println("[Uncaught]");
			
			exception.printStackTrace();
			
			ExceptionUtility.sendErrorMessage(exception);
		});
	}
	
	public static OkHttpClient getClient() {
		return Sx4Bot.CLIENT;
	}
	
	public static ShardManager getShardManager() {
		return Sx4Bot.shardManager;
	}
	
	public static CommandListener getCommandListener() {
		return Sx4Bot.commandListener;
	}
	
}
