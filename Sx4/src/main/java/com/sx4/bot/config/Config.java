package com.sx4.bot.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

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
	
	private JSONObject json;
	
	public Config() {
		this.json = this.getConfig();
	}
	
	public <Type> Type get(String path) {
		return this.get(path, (Type) null); 
	}
	
	public <Type> Type get(String path, Type defaultValue) {
		return this.get(List.of(path.split("\\.")), defaultValue);
	}
	
	@SuppressWarnings("unchecked")
	public <Type> Type get(List<String> path, Type defaultValue) {
		JSONObject json = this.json;
		
		for (int i = 0; i < path.size(); i++) {
			String key = path.get(i);
			if (!json.has(key)) {
				return defaultValue;
			}
			
			Object value = json.get(key);
			if (i == path.size() - 1) {
				if (value == JSONObject.NULL) {
					return null;
				}
				
				return (Type) value;
			}
			
			if (value instanceof JSONObject) {
				json = (JSONObject) value;
			} else {
				return defaultValue;
			}
		}
		
		return defaultValue;
	}
	
	public boolean isCanary() {
		return this.get("state.canary", true);
	}
	
	public boolean isTest() {
		return this.get("state.test", true);
	}
	
	public boolean isMain() {
		return !this.isTest() && !this.isCanary();
	}
	
	public String getState() {
		return this.isMain() ? "main" : "canary";
	}
	
	public long getCanaryId() {
		return this.get("token.canaryId", 0L);
	}
	
	public long getSupportGuildId() {
		return this.get(this.getState() + ".supportGuild.id", 0L);
	}
	
	public Guild getSupportGuild() {
		return Sx4Bot.getShardManager().getGuildById(this.getSupportGuildId());
	}
	
	public long getErrorsChannelId() {
		return this.get(this.getState() + ".supportGuild.channel.errorsId", 0L);
	}
	
	public TextChannel getErrorsChannel() {
		Guild guild = this.getSupportGuild();
		
		return guild == null ? null : guild.getTextChannelById(this.getErrorsChannelId());
	}
	
	public long getChangesChannelId() {
		return this.get(this.getState() + ".supportGuild.channel.changesId", 0L);
	}
	
	public TextChannel getChangesChannel() {
		Guild guild = this.getSupportGuild();
		
		return guild == null ? null : guild.getTextChannelById(this.getChangesChannelId());
	}
	
	public long getCommandsChannelId() {
		return this.get(this.getState() + ".supportGuild.channel.commandsId", 0L);
	}
	
	public TextChannel getCommandsChannel() {
		Guild guild = this.getSupportGuild();
		
		return guild == null ? null : guild.getTextChannelById(this.getCommandsChannelId());
	}
	
	public long getEventsChannelId() {
		return this.get(this.getState() + ".supportGuild.channel.eventsId", 0L);
	}
	
	public TextChannel getEventsChannel() {
		Guild guild = this.getSupportGuild();
		
		return guild == null ? null : guild.getTextChannelById(this.getEventsChannelId());
	}
	
	public long getStatsChannelId() {
		return this.get(this.getState() + ".supportGuild.channel.statsId", 0L);
	}
	
	public TextChannel getStatsChannel() {
		Guild guild = this.getSupportGuild();
		
		return guild == null ? null : guild.getTextChannelById(this.getStatsChannelId());
	}
	
	public long getGuildsChannelId() {
		return this.get(this.getState() + ".supportGuild.channel.guildsId", 0L);
	}
	
	public TextChannel getGuildsChannel() {
		Guild guild = this.getSupportGuild();
		
		return guild == null ? null : guild.getTextChannelById(this.getGuildsChannelId());
	}
	
	public long getMilstonesChannelId() {
		return this.get(this.getState() + ".supportGuild.channel.milestonesId", 0L);
	}
	
	public TextChannel getMilestonesChannel() {
		Guild guild = this.getSupportGuild();
		
		return guild == null ? null : guild.getTextChannelById(this.getMilstonesChannelId());
	}
	
	public long getDonatorRoleId() {
		return this.get(this.getState() + ".supportGuild.role.donatorId", 0L);
	}
	
	public Role getDonatorRole() {
		Guild guild = this.getSupportGuild();
		
		return guild == null ? null : guild.getRoleById(this.getDonatorRoleId());
	}
	
	public long getEventsWebhookId() {
		return this.get(this.getState() + ".supportGuild.webhook.eventsId", 0L);
	}
	
	public String getEventsWebhookToken() {
		return this.get(this.getState() + ".supportGuild.webhook.eventsToken");
	}
	
	public long getCommandsWebhookId() {
		return this.get(this.getState() + ".supportGuild.webhook.commandsId", 0L);
	}
	
	public String getCommandsWebhookToken() {
		return this.get(this.getState() + ".supportGuild.webhook.commandsToken");
	}
	
	public String getCanaryDatabase() {
		return this.get("canary.database", "sx4Canary");
	}
	
	public String getMainDatabase() {
		return this.get("main.database", "sx4");
	}
	
	public String getDatabase() {
		return this.get(this.getState() + ".database");
	}
	
	public String getToken() {
		return this.get("token." + (this.isTest() ? "test" : this.isCanary() ? "canary" : "main"));
	}
	
	public String getLocalHost() {
		return this.get(this.getState() + ".host.localHost", "localhost");
	}
	
	public String getDomain() {
		return this.get(this.getState() + ".host.domain");
	}
	
	public int getPort() {
		return this.get(this.getState() + ".host.port", 8080);
	}
	
	public String getAdDescription() {
		return this.get(this.getState() + ".ad.description");
	}
	
	public String getAdImage() {
		return this.get(this.getState() + ".ad.image");
	}
	
	public String getVainGlory() {
		return this.get("token.vainGlory");
	}
	
	public String getBotlistSpace() {
		return this.get("token.botlistSpace");
	}
	
	public String getTopGG() {
		return this.get("token.topGG");
	}
	
	public String getDiscordBots() {
		return this.get("token.discordBots");
	}
	
	public String getDBL() {
		return this.get("token.dbl");
	}
	
	public String getGoogle() {
		return this.get("token.google");
	}
	
	public String getSteam() {
		return this.get("token.steam");
	}
	
	public String getOxfordDictionaries() {
		return this.get("token.oxfordDictionaries");
	}
	
	public String getCurrencyConvertor() {
		return this.get("token.currencyConvertor");
	}
	
	public String getIGDB() {
		return this.get("token.igdb");
	}
	
	public String getBitly() {
		return this.get("token.bitly");
	}
	
	public String getYoutube() {
		return this.get("token.youtube");
	}
	
	public String getMashape() {
		return this.get("token.mashape");
	}
	
	public String getOpenWeather() {
		return this.get("token.openWeather");
	}
	
	public String getWeebsh() {
		return this.get("token.weebsh");
	}
	
	public JSONObject getVoteApi() {
		return this.get("token.voteApi");
	}
	
	public String getVoteApi(boolean sx4) {
		return this.get("token.voteApi." + (sx4 ? "sx4" : "jockieMusic"));
	}
	
	public int getColour() {
		return this.get(this.getState() + ".colour", 0);
	}
	
	public int getRed() {
		return this.get("colour.red", 16711680);
	}
	
	public int getOrange() {
		return this.get("colour.orange", 16753920);
	}
	
	public int getGreen() {
		return this.get("colour.green", 65280);
	}
	
	public JSONObject getConfig() {
		try (FileInputStream stream = new FileInputStream(new File("config.json"))) {
			return new JSONObject(new String(stream.readAllBytes()));
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
	
}