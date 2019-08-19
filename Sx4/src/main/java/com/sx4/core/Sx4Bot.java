package com.sx4.core;

import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.jockie.bot.core.command.factory.impl.MethodCommandFactory;
import com.jockie.bot.core.command.impl.CommandListener;
import com.jockie.bot.core.command.impl.CommandStore;
import com.jockie.bot.core.command.manager.impl.ContextManagerFactory;
import com.rethinkdb.RethinkDB;
import com.rethinkdb.gen.exc.ReqlRuntimeError;
import com.rethinkdb.net.Connection;
import com.sx4.cache.ChangesMessageCache;
import com.sx4.cache.GuildMessageCache;
import com.sx4.cache.SteamCache;
import com.sx4.events.AntiInviteEvents;
import com.sx4.events.AntiLinkEvents;
import com.sx4.events.AutoroleEvents;
import com.sx4.events.AwaitEvents;
import com.sx4.events.ConnectionEvents;
import com.sx4.events.EventWaiterEvents;
import com.sx4.events.GiveawayEvents;
import com.sx4.events.ImageModeEvents;
import com.sx4.events.ModEvents;
import com.sx4.events.MuteEvents;
import com.sx4.events.ReminderEvents;
import com.sx4.events.SelfroleEvents;
import com.sx4.events.ServerLogEvents;
import com.sx4.events.ServerPostEvents;
import com.sx4.events.StatsEvents;
import com.sx4.events.StatusEvents;
import com.sx4.events.TriggerEvents;
import com.sx4.events.WelcomerEvents;
import com.sx4.logger.handler.EventHandler;
import com.sx4.logger.handler.ExceptionHandler;
import com.sx4.settings.Settings;
import com.sx4.utils.CheckUtils;
import com.sx4.utils.DatabaseUtils;
import com.sx4.utils.HelpUtils;
import com.sx4.utils.ModUtils;
import com.sx4.utils.TimeUtils;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.hooks.InterfacedEventManager;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.dv8tion.jda.internal.JDAImpl;
import net.dv8tion.jda.internal.handle.GuildSetupController;
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
	
	private static ShardManager bot;
	
	private static CommandListener listener;
	
	private static Connection connection;
	
	private static EventHandler eventHandler;
	
	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");

	public static void main(String[] args) throws Exception {	
		connection = RethinkDB.r
				.connection()
				.connect();
		
		try {
			RethinkDB.r.dbCreate(Settings.DATABASE_NAME).run(connection);
		} catch(ReqlRuntimeError e) {}
		
		connection.use(Settings.DATABASE_NAME);
		
		eventHandler = new EventHandler(connection);
		
		DatabaseUtils.ensureTables("antiad", "antilink", "auction", "autorole", "await", "bank", "blacklist", 
				"botstats", "fakeperms", "giveaway", "imagemode", "logs", "marriage", "modlogs", "mute", 
				"offence", "prefix", "reactionrole", "reminders", "rps", "selfroles", "stats", "suggestions", 
				"tax", "triggers", "userprofile", "warn", "welcomer");
		
		ContextManagerFactory.getDefault()
			.registerContext(Connection.class, (event, type) -> connection)
			.registerContext(Map.class, (event, type) -> ((Sx4Command) event.getCommand()).getStrings());
		
		MethodCommandFactory.setDefault(new Sx4CommandFactory());
		
		listener = new Sx4CommandListener()
				.addCommandStore(CommandStore.of("com.sx4.modules"))
				.addDevelopers(402557516728369153L, 190551803669118976L)
				.setDefaultPrefixes("s?", "sx4 ", "S?")
				.setHelpFunction((event, prefix, failures) -> {
					if (CheckUtils.canReply(event, prefix)) {
						Member self = event.getGuild().getMember(event.getJDA().getSelfUser());
						if (self.hasPermission(Permission.MESSAGE_EMBED_LINKS)) {
							event.getTextChannel().sendMessage(HelpUtils.getHelpMessage(failures.get(0).getCommand())).queue();
						} else {
							event.getTextChannel().sendMessage("I am missing the permission `Embed Links`, therefore I cannot show you the help menu for `" + failures.get(0).getCommand().getCommandTrigger() + "` :no_entry:").queue();
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
						List<String> permissionNames = new ArrayList<String>();
						for (Permission permission : permissions) {
							permissionNames.add(permission.getName());
						}
						
						event.reply("I am missing the permission" + (permissions.size() == 1 ? "" : "s") + " `" + String.join("`, `", permissionNames) + "`, therefore I cannot execute that command :no_entry:").queue();
					}
				});
		
		listener.addPreParseCheck(message -> {
			if (message.isFromGuild()) {
				if (!message.getGuild().getSelfMember().hasPermission(message.getTextChannel(), Permission.MESSAGE_WRITE)) {
					return false;
				}
			}
			
			return true;
		});
		
		listener.removeDefaultPreExecuteChecks()
				.addPreExecuteCheck((event, command) -> CheckUtils.checkBlacklist(event))
				.addPreExecuteCheck((event, command) -> CheckUtils.canReply(event.getMessage(), event.getPrefix()))
				.addPreExecuteCheck((event, command) -> {
					if (command instanceof Sx4Command) {
						Sx4Command sx4Command = (Sx4Command) command;
						if (sx4Command.isDonator()) {
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
					}
					
					return true;
				})
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
					}
					
					return true;
				})
				.addPreExecuteCheck(listener.defaultBotPermissionCheck)
				.addPreExecuteCheck(listener.defaultNsfwCheck)
				.addPreExecuteCheck((event, command) -> {
					List<Permission> permissions = command.getAuthorDiscordPermissions();
					
					return CheckUtils.checkPermissions(event, permissions.isEmpty() ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(permissions), true);
				});
		
		listener.setPrefixesFunction(event -> ModUtils.getPrefixes(event.isFromGuild() ? event.getGuild() : null, event.getAuthor()));
		listener.addCommandEventListener(new Sx4CommandEventListener());
		
		InterfacedEventManager eventManager = new InterfacedEventManager();

		eventManager.register(Sx4Bot.listener);
		eventManager.register(Sx4Bot.waiter);
		eventManager.register(Sx4Bot.eventHandler);

		eventManager.register(new ChangesMessageCache());
		eventManager.register(GuildMessageCache.INSTANCE);

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

		eventManager.register(new ExceptionHandler());
		
		bot = new DefaultShardManagerBuilder()
				.setToken(Settings.BOT_OAUTH)
				.setEventManagerProvider(shardId -> eventManager)
				.build();
		
		Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> {
			System.err.println("[Uncaught]");
			
			exception.printStackTrace();
			
			Sx4CommandEventListener.sendErrorMessage(bot.getGuildById(Settings.SUPPORT_SERVER_ID).getTextChannelById(Settings.ERRORS_CHANNEL_ID), exception, new Object[0]);
		});
	}
	
	public static Connection getConnection() {
		return connection;
	}
	
	public static ShardManager getShardManager() {
		return bot;
	}
	
	public static CommandListener getCommandListener() {
		return listener;
	}
	
	public static EventHandler getEventHandler() {
		return eventHandler;
	}
	
	public static DateTimeFormatter getTimeFormatter() {
		return TIME_FORMATTER;
	}
}
