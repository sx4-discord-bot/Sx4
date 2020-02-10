package com.sx4.bot.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.json.JSONObject;

import com.sx4.bot.core.Sx4Bot;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;

public class Config {
	
	public static final Config INSTANCE = new Config();
	
	public static Config get() {
		return Config.INSTANCE;
	}
	
	private boolean canary;
	private boolean test;
	
	private long canaryId;
	
	private long supportGuildId;
	
	private long errorsChannelId;
	private long changesChannelId;
	private long commandsChannelId;
	private long eventsChannelId;
	private long statsChannelId;
	private long guildsChannelId;
	private long milestonesChannelId;
	
	private long donatorRoleId;
	
	private long eventsWebhookId;
	private String eventsWebhookToken;
	private long commandsWebhookId;
	private String commandsWebhookToken;
	
	private String canaryDatabase;
	private String mainDatabase;
	private String database;
	
	private int colour;
	private int green;
	private int orange;
	private int red;
	
	private String token;
	private String topGG;
	private String botlistSpace;
	private String dbl;
	private String discordBots;
	private String google;
	private String steam;
	private String currencyConvertor;
	private String mashape;
	private String weebsh;
	private String bitly;
	private String openWeather;
	private String igdb;
	private String youtube;
	private String vainGlory;
	private String oxfordDictionaries;
	
	private JSONObject voteApi;
	
	private String localHost;
	private String domain;
	private int port;
	
	private String adDescription;
	private String adImage;
	
	public Config() {
		this.loadConfig();
	}
	
	public boolean isCanary() {
		return this.canary;
	}
	
	public boolean isTest() {
		return this.test;
	}
	
	public boolean isMain() {
		return !this.test && !this.canary;
	}
	
	public long getCanaryId() {
		return this.canaryId;
	}
	
	public long getSupportGuildId() {
		return this.supportGuildId;
	}
	
	public Guild getSupportGuild() {
		return Sx4Bot.getShardManager().getGuildById(this.supportGuildId);
	}
	
	public long getErrorsChannelId() {
		return this.errorsChannelId;
	}
	
	public TextChannel getErrorsChannel() {
		Guild guild = this.getSupportGuild();
		
		return guild == null ? null : guild.getTextChannelById(this.errorsChannelId);
	}
	
	public long getChangesChannelId() {
		return this.changesChannelId;
	}
	
	public TextChannel getChangesChannel() {
		Guild guild = this.getSupportGuild();
		
		return guild == null ? null : guild.getTextChannelById(this.changesChannelId);
	}
	
	public long getCommandsChannelId() {
		return this.commandsChannelId;
	}
	
	public TextChannel getCommandsChannel() {
		Guild guild = this.getSupportGuild();
		
		return guild == null ? null : guild.getTextChannelById(this.commandsChannelId);
	}
	
	public long getEventsChannelId() {
		return this.eventsChannelId;
	}
	
	public TextChannel getEventsChannel() {
		Guild guild = this.getSupportGuild();
		
		return guild == null ? null : guild.getTextChannelById(this.eventsChannelId);
	}
	
	public long getStatsChannelId() {
		return this.statsChannelId;
	}
	
	public TextChannel getStatsChannel() {
		Guild guild = this.getSupportGuild();
		
		return guild == null ? null : guild.getTextChannelById(this.statsChannelId);
	}
	
	public long getGuildsChannelId() {
		return this.guildsChannelId;
	}
	
	public TextChannel getGuildsChannel() {
		Guild guild = this.getSupportGuild();
		
		return guild == null ? null : guild.getTextChannelById(this.guildsChannelId);
	}
	
	public long getMilstonesChannelId() {
		return this.milestonesChannelId;
	}
	
	public TextChannel getMilestonesChannel() {
		Guild guild = this.getSupportGuild();
		
		return guild == null ? null : guild.getTextChannelById(this.milestonesChannelId);
	}
	
	public long getDonatorRoleId() {
		return this.donatorRoleId;
	}
	
	public Role getDonatorRole() {
		Guild guild = this.getSupportGuild();
		
		return guild == null ? null : guild.getRoleById(this.donatorRoleId);
	}
	
	public long getEventsWebhookId() {
		return this.eventsWebhookId;
	}
	
	public String getEventsWebhookToken() {
		return this.eventsWebhookToken;
	}
	
	public long getCommandsWebhookId() {
		return this.commandsWebhookId;
	}
	
	public String getCommandsWebhookToken() {
		return this.commandsWebhookToken;
	}
	
	public String getCanaryDatabase() {
		return this.canaryDatabase;
	}
	
	public String getMainDatabase() {
		return this.mainDatabase;
	}
	
	public String getDatabase() {
		return this.database;
	}
	
	public String getToken() {
		return this.token;
	}
	
	public String getLocalHost() {
		return this.localHost;
	}
	
	public String getDomain() {
		return this.domain;
	}
	
	public int getPort() {
		return this.port;
	}
	
	public String getAdDescription() {
		return this.adDescription;
	}
	
	public String getAdImage() {
		return this.adImage;
	}
	
	public String getVainGlory() {
		return this.vainGlory;
	}
	
	public String getBotlistSpace() {
		return this.botlistSpace;
	}
	
	public String getTopGG() {
		return this.topGG;
	}
	
	public String getDiscordBots() {
		return this.discordBots;
	}
	
	public String getDBL() {
		return this.dbl;
	}
	
	public String getGoogle() {
		return this.google;
	}
	
	public String getSteam() {
		return this.steam;
	}
	
	public String getOxfordDictionaries() {
		return this.oxfordDictionaries;
	}
	
	public String getCurrencyConvertor() {
		return this.currencyConvertor;
	}
	
	public String getIGDB() {
		return this.igdb;
	}
	
	public String getBitly() {
		return this.bitly;
	}
	
	public String getYoutube() {
		return this.youtube;
	}
	
	public String getMashape() {
		return this.mashape;
	}
	
	public String getOpenWeather() {
		return this.openWeather;
	}
	
	public String getWeebsh() {
		return this.weebsh;
	}
	
	public JSONObject getVoteApi() {
		return this.voteApi;
	}
	
	public int getColour() {
		return this.colour;
	}
	
	public int getRed() {
		return this.red;
	}
	
	public int getOrange() {
		return this.orange;
	}
	
	public int getGreen() {
		return this.green;
	}
	
	public void loadConfig() {
		try (FileInputStream stream = new FileInputStream(new File("config.json"))) {
			JSONObject json = new JSONObject(new String(stream.readAllBytes()));
			
			JSONObject state = json.getJSONObject("state");
			this.canary = state.getBoolean("canary");
			this.test = state.getBoolean("test");
			
			JSONObject colours = json.getJSONObject("colours");
			this.green = colours.getInt("green");
			this.orange = colours.getInt("orange");
			this.red = colours.getInt("red");
			
			JSONObject token = json.getJSONObject("token");
			this.token = this.test ? token.getString("test") : this.canary ? token.getString("canary") : token.getString("main");
			this.canaryId = token.getLong("canaryId");
			this.vainGlory = token.getString("vainGlory");
			this.topGG = token.getString("top.gg");
			this.botlistSpace = token.getString("botlist.space");
			this.dbl = token.getString("dbl");
			this.discordBots = token.getString("discord.bots");
			this.google = token.getString("google");
			this.steam = token.getString("steam");
			this.oxfordDictionaries = token.getString("oxfordDictionaries");
			this.mashape = token.getString("mashape");
			this.youtube = token.getString("youtube");
			this.bitly = token.getString("bitly");
			this.weebsh = token.getString("weebsh");
			this.currencyConvertor = token.getString("currencyConvertor");
			this.openWeather = token.getString("openWeather");
			this.igdb = token.getString("igdb");
			
			this.voteApi = token.getJSONObject("voteApi");
			
			JSONObject bot = json.getJSONObject(this.canary ? "canary" : "main");
			JSONObject otherBot = json.getJSONObject(this.canary ? "main" : "canary");
			
			this.colour = bot.getInt("colour");
			
			JSONObject supportGuild = bot.getJSONObject("supportGuild");
			this.supportGuildId = supportGuild.getLong("id");
			
			JSONObject channel = supportGuild.getJSONObject("channel");
			this.errorsChannelId = channel.getLong("errorsId");
			this.changesChannelId = channel.getLong("changesId");
			this.commandsChannelId = channel.getLong("commandsId");
			this.eventsChannelId = channel.getLong("eventsId");
			this.statsChannelId = channel.getLong("statsId");
			this.guildsChannelId = channel.getLong("guildsId");
			this.milestonesChannelId = channel.getLong("milestonesId");
			
			JSONObject role = supportGuild.getJSONObject("role");
			this.donatorRoleId = role.getLong("donatorId");
			
			JSONObject webhook = supportGuild.getJSONObject("webhook");
			this.eventsWebhookId = webhook.getLong("eventsId");
			this.eventsWebhookToken = webhook.getString("eventsToken");
			this.commandsWebhookId = webhook.getLong("commandsId");
			this.commandsWebhookToken = webhook.getString("commandsToken");
			
			this.database = bot.getString("database");
			this.canaryDatabase = this.canary ? bot.getString("database") : otherBot.getString("database");
			this.mainDatabase = !this.canary ? bot.getString("database") : otherBot.getString("database");
			
			JSONObject ad = bot.getJSONObject("ad");
			this.adDescription = ad.isNull("description") ? null : ad.getString("description");
			this.adImage = ad.isNull("image") ? null : ad.getString("image");
			
			JSONObject host = bot.getJSONObject("host");
			this.localHost = host.getString("localHost");
			this.domain = host.getString("domain");
			this.port = host.getInt("port");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}