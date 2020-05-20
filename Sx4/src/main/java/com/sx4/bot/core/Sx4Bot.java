package com.sx4.bot.core;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.DateTimeException;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import org.bson.types.ObjectId;

import com.jockie.bot.core.argument.IArgument;
import com.jockie.bot.core.argument.factory.impl.ArgumentFactory;
import com.jockie.bot.core.argument.factory.impl.ArgumentFactoryImpl;
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
import com.sx4.bot.annotations.argument.DefaultInt;
import com.sx4.bot.annotations.argument.DefaultLong;
import com.sx4.bot.annotations.argument.DefaultString;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.annotations.argument.Lowercase;
import com.sx4.bot.annotations.argument.Uppercase;
import com.sx4.bot.config.Config;
import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.entities.reminder.ReminderArgument;
import com.sx4.bot.handlers.ConnectionHandler;
import com.sx4.bot.handlers.ModHandler;
import com.sx4.bot.handlers.YouTubeHandler;
import com.sx4.bot.managers.ModActionManager;
import com.sx4.bot.managers.YouTubeManager;
import com.sx4.bot.message.cache.GuildMessageCache;
import com.sx4.bot.paged.PagedHandler;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.HelpUtility;
import com.sx4.bot.utility.SearchUtility;
import com.sx4.bot.utility.TimeUtility;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Category;
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
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import okhttp3.OkHttpClient;

public class Sx4Bot {
	
	private static ShardManager shardManager;
	private static CommandListener commandListener;
	private static ModActionManager modActionManager;
	private static YouTubeManager youtubeManager;
	
	private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
			.connectTimeout(15, TimeUnit.SECONDS)
			.readTimeout(15, TimeUnit.SECONDS)
			.writeTimeout(15, TimeUnit.SECONDS)
			.build();
	
	public static void main(String[] args) throws Throwable {
		Sx4Bot.modActionManager = new ModActionManager()
				.addListener(new ModHandler());
				
		Sx4Bot.youtubeManager = new YouTubeManager()
			.addListener(new YouTubeHandler());
		
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
			.registerResponse(Duration.class, "Invalid time string given, a good example would be `5d 1h 24m 36s` :no_entry:")
			.registerResponse(ObjectId.class, "Invalid id given, an example id would be `5e45ce6d3688b30ee75201ae` :no_entry:")
			.registerResponse(List.class, "I could not find that command/module :no_entry:")
			.registerResponse(URL.class, "Invalid image given :no_entry:")
			.registerResponse(RestAction.class, "I could not find that message :no_entry:")
			.registerResponse(ReminderArgument.class, "Invalid reminder format given, view `help reminder add` for more info :no_entry:");
		
		ArgumentFactoryImpl argumentFactory = (ArgumentFactoryImpl) ArgumentFactory.getDefault();
			
		argumentFactory.registerParser(Member.class, (context, argument, content) -> new ParsedArgument<>(SearchUtility.getMember(context.getMessage().getGuild(), content.trim())))
			.registerParser(User.class, (context, argument, content) -> new ParsedArgument<>(SearchUtility.getUser(content.trim())))
			.registerParser(TextChannel.class, (context, argument, content) -> new ParsedArgument<>(SearchUtility.getTextChannel(context.getMessage().getGuild(), content.trim())))
			.registerParser(Duration.class, (context, argument, content) -> new ParsedArgument<>(TimeUtility.getDurationFromString(content)))
			.registerParser(Reason.class, (context, argument, content) -> new ParsedArgument<>(new Reason(context.getMessage().getGuild().getIdLong(), content)))
			.registerParser(ObjectId.class, (context, argument, content) -> new ParsedArgument<>(ObjectId.isValid(content) ? new ObjectId(content) : null))
			.registerParser(List.class, (context, argument, content) -> new ParsedArgument<>(SearchUtility.getCommandOrModule(content)))
			.registerParser(IPermissionHolder.class, (context, argument, content) -> new ParsedArgument<>(SearchUtility.getPermissionHolder(context.getMessage().getGuild(), content)))
			.registerParser(Role.class, (context, argument, content) -> new ParsedArgument<>(SearchUtility.getRole(context.getMessage().getGuild(), content)))
			.registerParser(RestAction.class, (context, argument, content) -> new ParsedArgument<>(SearchUtility.getMessageAction(context.getMessage().getTextChannel(), content)))
			.registerParser(ReactionEmote.class, (context, argument, content) -> new ParsedArgument<>(SearchUtility.getEmote(content)))
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
			});
		
		argumentFactory.addBuilderConfigureFunction(String.class, (parameter, builder) -> {
			builder.setProperty("lowercase", parameter.isAnnotationPresent(Lowercase.class));
			builder.setProperty("uppercase", parameter.isAnnotationPresent(Uppercase.class));
			
			DefaultString defaultString = parameter.getAnnotation(DefaultString.class);
			if (defaultString != null) {
				builder.setDefaultValue(defaultString.value());
			}
			
			return builder;
		}).addBuilderConfigureFunction(Integer.class, (parameter, builder) -> {
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
		
		argumentFactory.addParserAfter(String.class, (context, argument, content) -> {
			if (argument.getProperty("lowercase", boolean.class)) {
				content = content.toLowerCase();
			}
			
			if (argument.getProperty("uppercase", boolean.class)) {
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
		});
		
		Sx4Bot.commandListener = new Sx4CommandListener()
			.addCommandStores(CommandStore.of("com.sx4.bot.commands"))
			.addDevelopers(402557516728369153L, 190551803669118976L)
			.setErrorManager(errorManager)
			.setCommandEventFactory(new Sx4CommandEventFactory())
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
							message.getAuthor().openPrivateChannel().queue(channel -> {
								channel.sendMessage("Missing permission **" + Permission.MESSAGE_WRITE.getName() + "** in " + message.getChannel().getName() + ", " + message.getGuild().getName()).queue();
							});
							
							return;
						} else if (!bot.hasPermission(Permission.MESSAGE_EMBED_LINKS)) {
							message.getChannel().sendMessage("Missing permission **" + Permission.MESSAGE_EMBED_LINKS.getName() + "** in " + message.getChannel().getName() + ", " + message.getGuild().getName()).queue();
							
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
		eventManager.register(GuildMessageCache.INSTANCE);
		eventManager.register(new ConnectionHandler());
		
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
	
	public static ModActionManager getModActionManager() {
		return Sx4Bot.modActionManager;
	}
	public static YouTubeManager getYouTubeManager() {
		return Sx4Bot.youtubeManager;
	}
	
}
