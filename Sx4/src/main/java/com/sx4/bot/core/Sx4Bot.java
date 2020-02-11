package com.sx4.bot.core;

import java.time.Duration;

import com.jockie.bot.core.argument.factory.impl.ArgumentFactory;
import com.jockie.bot.core.argument.parser.ParsedArgument;
import com.jockie.bot.core.command.factory.impl.MethodCommandFactory;
import com.jockie.bot.core.command.impl.CommandListener;
import com.jockie.bot.core.command.impl.CommandStore;
import com.jockie.bot.core.command.manager.IErrorManager;
import com.jockie.bot.core.command.manager.impl.ContextManagerFactory;
import com.jockie.bot.core.command.manager.impl.ErrorManagerImpl;
import com.sx4.bot.config.Config;
import com.sx4.bot.database.Database;
import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.handlers.ModHandler;
import com.sx4.bot.hooks.mod.ModActionManager;
import com.sx4.bot.message.cache.GuildMessageCache;
import com.sx4.bot.paged.PagedHandler;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.SearchUtility;
import com.sx4.bot.utility.TimeUtility;

import net.dv8tion.jda.api.entities.Category;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.IPermissionHolder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.hooks.InterfacedEventManager;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;

public class Sx4Bot {
	
	private static ShardManager shardManager;
	private static CommandListener commandListener;
	private static ModActionManager modActionManager;
	
	public static void main(String[] args) throws Throwable {
		Sx4Bot.modActionManager = new ModActionManager()
			.addListener(new ModHandler());
		
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
			.registerResponse(Duration.class, "Invalid time string given, a good example would be `5d 1h 24m 36s` :no_entry:");
		
		ArgumentFactory.getDefault()
			.registerParser(Member.class, (context, argument, content) -> new ParsedArgument<>(SearchUtility.getMember(context.getMessage().getGuild(), content.trim())))
			.registerParser(User.class, (context, argument, content) -> new ParsedArgument<>(SearchUtility.getUser(content.trim())))
			.registerParser(Duration.class, (context, argument, content) -> new ParsedArgument<>(TimeUtility.getTimeFromString(content)))
			.registerParser(Reason.class, (context, argument, content) -> new ParsedArgument<>(new Reason(context.getMessage().getGuild().getIdLong(), content)));
		
		ContextManagerFactory.getDefault()
			.registerContext(Database.class, (event, type) -> Database.INSTANCE);
		
		Sx4Bot.commandListener = new Sx4CommandListener()
			.addCommandStores(CommandStore.of("com.sx4.bot.commands"))
			.addDevelopers(402557516728369153L, 190551803669118976L)
			.setErrorManager(errorManager);
				
		InterfacedEventManager eventManager = new InterfacedEventManager();
		
		eventManager.register(Sx4Bot.commandListener);
		eventManager.register(new PagedHandler());
		eventManager.register(GuildMessageCache.INSTANCE);
		
		Sx4Bot.shardManager = new DefaultShardManagerBuilder()
			.setToken(Config.get().getToken())
			.setBulkDeleteSplittingEnabled(false)
			.setEventManagerProvider(shardId -> eventManager)
			.build();
		
		Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> {
			System.err.println("[Uncaught]");
			
			exception.printStackTrace();
			
			ExceptionUtility.sendErrorMessage(exception);
		});
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
	
}
