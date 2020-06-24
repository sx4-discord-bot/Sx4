package com.sx4.bot.core;

import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.jockie.bot.core.command.factory.impl.MethodCommandFactory;
import com.jockie.bot.core.command.impl.CommandListener;
import com.jockie.bot.core.command.impl.CommandStore;
import com.jockie.bot.core.command.manager.impl.ContextManagerFactory;
import com.sx4.bot.cache.ChangesMessageCache;
import com.sx4.bot.cache.GuildMessageCache;
import com.sx4.bot.database.Database;
import com.sx4.bot.events.AntiInviteEvents;
import com.sx4.bot.events.AntiLinkEvents;
import com.sx4.bot.events.AutoroleEvents;
import com.sx4.bot.events.AwaitEvents;
import com.sx4.bot.events.ConnectionEvents;
import com.sx4.bot.events.EventWaiterEvents;
import com.sx4.bot.events.ImageModeEvents;
import com.sx4.bot.events.ModEvents;
import com.sx4.bot.events.MuteEvents;
import com.sx4.bot.events.NotificationEvents;
import com.sx4.bot.events.SelfroleEvents;
import com.sx4.bot.events.ServerLogEvents;
import com.sx4.bot.events.StarboardEvents;
import com.sx4.bot.events.StatsEvents;
import com.sx4.bot.events.TriggerEvents;
import com.sx4.bot.events.WelcomerEvents;
import com.sx4.bot.logger.handler.EventHandler;
import com.sx4.bot.logger.handler.ExceptionHandler;
import com.sx4.bot.settings.Settings;
import com.sx4.bot.utils.CheckUtils;
import com.sx4.bot.utils.HelpUtils;
import com.sx4.bot.utils.ModUtils;
import com.sx4.bot.utils.TimeUtils;
import com.sx4.bot.youtube.YouTubeManager;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.Message.MentionType;
import net.dv8tion.jda.api.hooks.InterfacedEventManager;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import okhttp3.OkHttpClient;

public class Sx4Bot {
	
	static {
		if (!Charset.defaultCharset().equals(StandardCharsets.UTF_8)) {
			System.setProperty("file.encoding", "UTF-8");
			Field charset = null;
			try {
				charset = Charset.class.getDeclaredField("defaultCharset");
			} catch (NoSuchFieldException | SecurityException e) {
				e.printStackTrace();
			}
			charset.setAccessible(true);
			try {
				charset.set(null, null);
			} catch (IllegalArgumentException | IllegalAccessException e) {
				e.printStackTrace();
			}
		}
	}
	
	public static final EventWaiterEvents waiter = new EventWaiterEvents();
	
	public static OkHttpClient client = new OkHttpClient.Builder()
			.connectTimeout(5, TimeUnit.SECONDS)
			.callTimeout(5, TimeUnit.SECONDS)
			.build();
	
	public static ScheduledExecutorService scheduledExectuor = Executors.newSingleThreadScheduledExecutor();
	
	private static final Database DATABASE = Database.get();
	
	private static ShardManager bot;
	
	private static CommandListener listener;
	
	private static EventHandler eventHandler;
	
	private static YouTubeManager youtubeManager;
	
	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");

	public static void main(String[] args) throws Exception {
		eventHandler = new EventHandler();
		
		youtubeManager = new YouTubeManager().addListener(NotificationEvents.get());
		
		MessageAction.setDefaultMentions(EnumSet.noneOf(MentionType.class));
		
		ContextManagerFactory.getDefault()
			.registerContext(Database.class, (event, type) -> Sx4Bot.DATABASE)
			.registerContext(Map.class, (event, type) -> ((Sx4Command) event.getCommand()).getStrings());
		
		MethodCommandFactory.setDefault(new Sx4CommandFactory());
		
		listener = new Sx4CommandListener()
				.addCommandStores(CommandStore.of("com.sx4.bot.modules"))
				.addDevelopers(402557516728369153L, 190551803669118976L)
				.setDefaultPrefixes("s?", "sx4 ", "S?")
				.setHelpFunction((event, prefix, commands) -> {
					if (CheckUtils.canReply(event, prefix)) {
						Member self = event.getGuild().getMember(event.getJDA().getSelfUser());
						if (self.hasPermission(event.getTextChannel(), Permission.MESSAGE_EMBED_LINKS)) {
							event.getTextChannel().sendMessage(HelpUtils.getHelpMessage(commands.get(0))).queue();
						} else {
							event.getTextChannel().sendMessage("I am missing the permission `Embed Links`, therefore I cannot show you the help menu for `" + commands.get(0).getCommandTrigger() + "` :no_entry:").queue();
						}
					}
				})
				.setCooldownFunction((event, cooldown) -> {
					if (CheckUtils.canReply(event.getMessage(), event.getPrefix())) {
						event.reply("Slow down there! You can execute this command again in " + TimeUtils.toTimeString(cooldown.getTimeRemainingMillis(), ChronoUnit.MILLIS) + " :stopwatch:").queue(); 
					}
				})
				.setMissingPermissionExceptionFunction((event, permission) -> {
					if (CheckUtils.canReply(event.getMessage(), event.getPrefix())) {
						event.reply("I am missing the permission `" + permission.getName() + "`, therefore I cannot execute that command :no_entry:").queue();
					}
				})
				.setMissingPermissionFunction((event, permissions) -> {
					if (CheckUtils.canReply(event.getMessage(), event.getPrefix())) {
						List<String> permissionNames = new ArrayList<>();
						for (Permission permission : permissions) {
							permissionNames.add(permission.getName());
						}
						
						event.reply("I am missing the permission" + (permissions.size() == 1 ? "" : "s") + " `" + String.join("`, `", permissionNames) + "`, therefore I cannot execute that command :no_entry:").queue();
					}
				})
				.setNSFWFunction(event -> {
					event.reply("You can not use this command in a non-nsfw channel :no_entry:").queue();
				});
		
		listener.removeDefaultPreExecuteChecks()
				.addPreExecuteCheck((event, command) -> CheckUtils.checkBlacklist(event))
				.addPreExecuteCheck((event, command) -> CheckUtils.canReply(event.getMessage(), event.getPrefix()))
				.addPreExecuteCheck((event, command) -> {
					if (command instanceof Sx4Command) {
						Sx4Command sx4Command = (Sx4Command) command;
						if (sx4Command.isDisabled()) {
							if (sx4Command.hasDisabledMessage()) {
								event.reply(sx4Command.getDisabledMessage()).queue();
							} else {
								event.reply("That command is currently disabled :no_entry:").queue();
							}
							
							return false;
						} 
						
						if (sx4Command.isDonatorCommand()) {
							Guild guild = event.getShardManager().getGuildById(Settings.SUPPORT_SERVER_ID);
							Role donatorRole = guild.getRoleById(Settings.DONATOR_ONE_ROLE_ID);
				
							Member member = guild.getMemberById(event.getAuthor().getIdLong());
				
							if (member != null) {
								if (!event.getGuild().getMembersWithRoles(donatorRole).contains(member)) {
									event.reply("You need to be a donator to execute this command :no_entry:").queue();
									return false;
								}
							}
						}
						
						if (!Settings.CANARY) {
							if (sx4Command.isCanaryCommand()) {
								event.reply("This command can only be used on the canary version of the bot :no_entry:").queue();
								return false;
							}
						}
					}
					
					return true;
				})
				.addPreExecuteCheck(listener.defaultBotPermissionCheck)
				.addPreExecuteCheck(listener.defaultNsfwCheck)
				.addPreExecuteCheck((event, command) -> {
					Set<Permission> permissions = command.getAuthorDiscordPermissions();
					
					return CheckUtils.checkPermissions(event, permissions.isEmpty() ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(permissions), true);
				});
		
		listener.setPrefixesFunction(event -> ModUtils.getPrefixes(event.isFromGuild() ? event.getGuild() : null, event.getAuthor()));
		listener.addCommandEventListener(new Sx4CommandEventListener());
		listener.setCommandExecutor(Executors.newCachedThreadPool(new ThreadFactoryBuilder()
		    .setNameFormat("jockie-utils-async-executor-%d")
		    .build()));
		
		InterfacedEventManager eventManager = new InterfacedEventManager();
		
		eventManager.register(Sx4Bot.listener);
		eventManager.register(Sx4Bot.waiter);
		eventManager.register(Sx4Bot.eventHandler);

		eventManager.register(new ChangesMessageCache());

		eventManager.register(new StarboardEvents());
		eventManager.register(new SelfroleEvents());
		eventManager.register(new ModEvents());
		eventManager.register(new ConnectionEvents());
		eventManager.register(new AwaitEvents());
		eventManager.register(new StatsEvents());
		eventManager.register(new WelcomerEvents());
		eventManager.register(new AutoroleEvents());
		eventManager.register(new TriggerEvents());
		eventManager.register(new ImageModeEvents());
		eventManager.register(new MuteEvents());
		eventManager.register(new AntiInviteEvents());
		eventManager.register(new AntiLinkEvents());
		eventManager.register(new ServerLogEvents());
		eventManager.register(new NotificationEvents());	

		eventManager.register(new ExceptionHandler());
		eventManager.register(GuildMessageCache.INSTANCE);
			
		bot = DefaultShardManagerBuilder.create(Settings.BOT_OAUTH, EnumSet.allOf(GatewayIntent.class))
				.setMemberCachePolicy(MemberCachePolicy.ALL)
				.disableIntents(GatewayIntent.DIRECT_MESSAGE_TYPING, GatewayIntent.DIRECT_MESSAGE_REACTIONS, GatewayIntent.GUILD_MESSAGE_TYPING)
				.setEventManagerProvider(shardId -> eventManager)
				.setBulkDeleteSplittingEnabled(false)
				.build();
		
		Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> {
			System.err.println("[Uncaught]");
			
			exception.printStackTrace();
			
			Sx4CommandEventListener.sendErrorMessage(bot.getGuildById(Settings.SUPPORT_SERVER_ID).getTextChannelById(Settings.ERRORS_CHANNEL_ID), exception, new Object[0]);
		});
	}
	
	public static YouTubeManager getYouTubeManager() {
		return Sx4Bot.youtubeManager;
	}
	
	public static Database getDatabase() {
		return Sx4Bot.DATABASE;
	}
	
	public static ShardManager getShardManager() {
		return Sx4Bot.bot;
	}
	
	public static CommandListener getCommandListener() {
		return Sx4Bot.listener;
	}
	
	public static EventHandler getEventHandler() {
		return Sx4Bot.eventHandler;
	}
	
	public static DateTimeFormatter getTimeFormatter() {
		return Sx4Bot.TIME_FORMATTER;
	}
}
