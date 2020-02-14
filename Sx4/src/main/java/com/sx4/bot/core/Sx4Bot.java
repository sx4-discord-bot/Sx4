package com.sx4.bot.core;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.bson.types.ObjectId;

import com.jockie.bot.core.argument.IArgument;
import com.jockie.bot.core.argument.factory.impl.ArgumentFactory;
import com.jockie.bot.core.argument.parser.ParsedArgument;
import com.jockie.bot.core.command.exception.parser.ArgumentParseException;
import com.jockie.bot.core.command.exception.parser.OutOfContentException;
import com.jockie.bot.core.command.factory.impl.MethodCommandFactory;
import com.jockie.bot.core.command.impl.CommandListener;
import com.jockie.bot.core.command.impl.CommandListener.Failure;
import com.jockie.bot.core.command.impl.CommandStore;
import com.jockie.bot.core.command.manager.IErrorManager;
import com.jockie.bot.core.command.manager.impl.ErrorManagerImpl;
import com.sx4.api.Main;
import com.sx4.bot.config.Config;
import com.sx4.bot.entities.mod.Reason;
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
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.IPermissionHolder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.hooks.InterfacedEventManager;
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
		
		IErrorManager errorManager = new ErrorManagerImpl()
			.registerResponse(Member.class, "I could not find that user :no_entry:")
			.registerResponse(User.class, "I could not find that user :no_entry:")
			.registerResponse(Role.class, "I could not find that role :no_entry:")
			.registerResponse(Emote.class, "I could not find that emote :no_entry:")
			.registerResponse(TextChannel.class, "I could not find that text channel :no_entry:")
			.registerResponse(VoiceChannel.class, "I could not find that voice channel :no_entry:")
			.registerResponse(Category.class, "I could not find that category :no_entry:")
			.registerResponse(GuildChannel.class, "I could not find that channel :no_entry:")
			.registerResponse(IPermissionHolder.class, "I could not find that user/role :no_entry:")
			.registerResponse(Duration.class, "Invalid time string given, a good example would be `5d 1h 24m 36s` :no_entry:")
			.registerResponse(ObjectId.class, "Invalid id given, an example id would be `5e45ce6d3688b30ee75201ae` :no_entry:");
		
		ArgumentFactory.getDefault()
			.registerParser(Member.class, (context, argument, content) -> new ParsedArgument<>(SearchUtility.getMember(context.getMessage().getGuild(), content.trim())))
			.registerParser(User.class, (context, argument, content) -> new ParsedArgument<>(SearchUtility.getUser(content.trim())))
			.registerParser(TextChannel.class, (context, argument, content) -> new ParsedArgument<>(SearchUtility.getTextChannel(context.getMessage().getGuild(), content.trim())))
			.registerParser(Duration.class, (context, argument, content) -> new ParsedArgument<>(TimeUtility.getTimeFromString(content)))
			.registerParser(Reason.class, (context, argument, content) -> new ParsedArgument<>(new Reason(context.getMessage().getGuild().getIdLong(), content)))
			.registerParser(ObjectId.class, (context, argument, content) -> new ParsedArgument<>(ObjectId.isValid(content) ? new ObjectId(content) : null));
		
		Sx4Bot.commandListener = new Sx4CommandListener()
			.addCommandStores(CommandStore.of("com.sx4.bot.commands"))
			.addDevelopers(402557516728369153L, 190551803669118976L)
			.setErrorManager(errorManager)
			.setHelpFunction((message, prefix, failures) -> {
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
					
					if (errorManager.handle(argument, message, value)) {
						return;
					}
				}
				
				MessageChannel channel = message.getChannel();
				boolean embed = message.isFromGuild() ? message.getGuild().getSelfMember().hasPermission((TextChannel) channel, Permission.MESSAGE_EMBED_LINKS) : true;
				
				channel.sendMessage(HelpUtility.getHelpMessage(failures.get(0).getCommand(), embed)).queue();
			});
				
		InterfacedEventManager eventManager = new InterfacedEventManager();
		
		eventManager.register(Sx4Bot.commandListener);
		eventManager.register(new PagedHandler());
		eventManager.register(GuildMessageCache.INSTANCE);
		
		Sx4Bot.shardManager = new DefaultShardManagerBuilder()
			.setToken(Config.get().getToken())
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
