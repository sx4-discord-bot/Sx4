package com.sx4.core;

import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import com.jockie.bot.core.command.impl.CommandListener;
import com.jockie.bot.core.command.impl.CommandStore;
import com.jockie.bot.core.command.manager.impl.ContextManagerFactory;
import com.rethinkdb.RethinkDB;
import com.rethinkdb.gen.exc.ReqlRuntimeError;
import com.rethinkdb.net.Connection;
import com.sx4.cache.ChangesMessageCache;
import com.sx4.cache.SteamCache;
import com.sx4.events.AntiInviteEvents;
import com.sx4.events.AntiLinkEvents;
import com.sx4.events.AutoroleEvents;
import com.sx4.events.AwaitEvents;
import com.sx4.events.ConnectionEvents;
import com.sx4.events.ImageModeEvents;
import com.sx4.events.ModEvents;
import com.sx4.events.MuteEvents;
import com.sx4.events.SelfroleEvents;
import com.sx4.events.ServerLogEvents;
import com.sx4.events.StatsEvents;
import com.sx4.events.TriggerEvents;
import com.sx4.events.WelcomerEvents;
import com.sx4.logger.Statistics;
import com.sx4.logger.handler.EventHandler;
import com.sx4.logger.handler.ExceptionHandler;
import com.sx4.logger.handler.GuildMessageCache;
import com.sx4.logger.util.Utils;
import com.sx4.settings.Settings;
import com.sx4.utils.CheckUtils;
import com.sx4.utils.DatabaseUtils;
import com.sx4.utils.HelpUtils;
import com.sx4.utils.ModUtils;
import com.sx4.utils.TimeUtils;

import net.dv8tion.jda.bot.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.bot.sharding.ShardManager;
import net.dv8tion.jda.core.OnlineStatus;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
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
	
	public static final EventWaiter waiter = new EventWaiter();
	
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
		SteamCache.getGames();
		
		connection = RethinkDB.r
			    .connection()
			    .connect();
		
		try {
			RethinkDB.r.dbCreate(Settings.DATABASE_NAME).run(connection);
		} catch(ReqlRuntimeError e) {}
		
		connection.use(Settings.DATABASE_NAME);
		
		Sx4Bot.eventHandler = new EventHandler(connection);
		
		DatabaseUtils.ensureTables("antiad", "antilink", "auction", "autorole", "await", "bank", "blacklist", 
				"botstats", "fakeperms", "giveaway", "imagemode", "logs", "marriage", "modlogs", "mute", 
				"offence", "prefix", "reactionrole", "reminders", "rps", "selfroles", "stats", "suggestions", 
				"tax", "triggers", "userprofile", "warn", "welcomer");
		
		ContextManagerFactory.getDefault().registerContext(Connection.class, (event, type) -> connection);
		
		listener = new Sx4CommandListener()
				.addCommandStore(CommandStore.of("com.sx4.modules"))
				.addDevelopers(402557516728369153L, 190551803669118976L)
				.setDefaultPrefixes("s?", "sx4 ", "S?")
				.setHelpFunction((event, prefix, failures) -> {
					Member self = event.getGuild().getMember(event.getJDA().getSelfUser());
					if (self.hasPermission(Permission.MESSAGE_EMBED_LINKS)) {
						event.getTextChannel().sendMessage((HelpUtils.getHelpMessage(failures.get(0).getCommand()))).queue();
					} else {
						event.getTextChannel().sendMessage("I am missing the permission `Embed Links`, therefore I cannot show you the help menu for `" + failures.get(0).getCommand().getCommandTrigger() + "` :no_entry:").queue();
					}
				})
				.setCooldownFunction((event, cooldown) -> {
					event.reply("Slow down there! You can execute this command again in " + TimeUtils.toTimeString(cooldown.getTimeRemainingMillis(), ChronoUnit.MILLIS)).queue(); 
				})
				.setMissingPermissionExceptionFunction((event, permission) -> {
					event.reply("I am missing the permission `" + permission.getName() + "`, therefore I cannot execute this command :no_entry:").queue();
				})
				.setMissingPermissionFunction((event, permissions) -> {
					List<String> permissionNames = new ArrayList<String>();
					for (Permission permission : permissions) {
						permissionNames.add(permission.getName());
					}
					
					event.reply("I am missing the permission" + (permissions.size() == 1 ? "" : "s") + " `" + String.join("`, `", permissionNames) + "`, therefore I cannot execute this command :no_entry:").queue();
				});
		
		listener.removeDefaultPreExecuteChecks()
				.addPreExecuteCheck((event, command) -> {
					return CheckUtils.checkBlacklist(event, connection);
				})
				.addPreExecuteCheck((event, command) -> {
					if (!Settings.CANARY) {
						List<String> canaryPrefixes = ModUtils.getPrefixes(event.getGuild(), event.getAuthor(), Settings.CANARY_DATABASE_NAME);
						if (!canaryPrefixes.contains(event.getPrefix())) {
							return true;
						}
						
						Member canaryBot = event.getGuild().getMemberById(Settings.CANARY_BOT_ID);
						if (canaryBot != null && !event.isPrefixMention() && event.getTextChannel().canTalk(canaryBot) && !canaryBot.getOnlineStatus().equals(OnlineStatus.OFFLINE)) {
							return false;
						}
					}
					
					return true;
				})
				.addPreExecuteCheck(listener.defaultBotPermissionCheck)
				.addPreExecuteCheck(listener.defaultNsfwCheck)
				.addPreExecuteCheck((event, command) -> {
					return CheckUtils.checkPermissions(event, connection, command.getAuthorDiscordPermissions().toArray(new Permission[0]), true);
				});
		
		listener.setPrefixesFunction(event -> ModUtils.getPrefixes(event.getGuild(), event.getAuthor()));
		listener.addCommandEventListener(new Sx4CommandEventListener());
		
		bot = new DefaultShardManagerBuilder().setToken(Settings.BOT_OATH).build();
		bot.addEventListener(waiter, listener);
		bot.addEventListener(new ChangesMessageCache(), new SelfroleEvents(), new ModEvents(), new ConnectionEvents(), new AwaitEvents(), new StatsEvents(), new WelcomerEvents(), new AutoroleEvents(), 
				new TriggerEvents(), new ImageModeEvents(), new MuteEvents(), new AntiInviteEvents(), new AntiLinkEvents(), new ServerLogEvents(), Sx4Bot.eventHandler, new ExceptionHandler(), GuildMessageCache.INSTANCE);
		
		Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> {
			System.err.println("[Uncaught]");
			
			exception.printStackTrace();
		});
		
		System.gc();
		
		/* Used for debugging */
		try(Scanner scanner = new Scanner(System.in)) {
			String line;
			while((line = scanner.nextLine()) != null) {
				if(line.startsWith("help")) {
					System.out.println(Utils.getMessageSeperated(new StringBuilder()
						.append("\nqueued - sends information about the queued requests")
						.append("\nstats - sends the statistics")
						.append("\nclear - clears the console")));
					
					continue;
				}
				
				if(line.equalsIgnoreCase("queued")) {
					StringBuilder message = new StringBuilder();
					
					Map<Long, BlockingDeque<EventHandler.Request>> queue = Sx4Bot.getEventHandler().getQueue();
					
					List<Long> mostQueued = queue.keySet().stream()
						.sorted((key, key2) -> -Integer.compare(queue.get(key).size(), queue.get(key2).size()))
						.limit(10)
						.collect(Collectors.toList());
					
					for(long guildId : mostQueued) {
						int queued = queue.get(guildId).size();
						if(queued > 0) {
							Guild guild = Sx4Bot.getShardManager().getGuildById(guildId);
							if(guild != null) {
								message.append('\n').append(guild.getName() + " (" + guildId + ") - " + queued);
							}else{
								message.append('\n').append("Unknown guild (" + guildId + ") - " + queued);
							}
						}
					}
					
					message.append('\n').append("Total queued requests: " + Sx4Bot.getEventHandler().getTotalRequestsQueued());

					System.out.println(Utils.getMessageSeperated(message));
					
					continue;
				}
				
				if(line.equalsIgnoreCase("stats")) {
					Statistics.printStatistics();
					
					continue;
				}
				
				if(line.equalsIgnoreCase("clear")) {
				    System.out.print("\033[H\033[2J");
				    System.out.flush();
				    
				    continue;
				}
				
				System.out.println(Utils.getMessageSeperated("\nUnknown command"));
			}
		}
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
		return Sx4Bot.eventHandler;
	}
	
	public static DateTimeFormatter getTimeFormatter() {
		return TIME_FORMATTER;
	}
}
