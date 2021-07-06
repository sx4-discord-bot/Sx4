package com.sx4.bot.config;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.bson.Document;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class Config {

	private static final Config INSTANCE = new Config();

	public static Config get() {
		return Config.INSTANCE;
	}
	
	private Document json;
	
	// Avoid iterating the json everytime they're used
	private String emoteSuccess;
	private String emoteFailure;

	private WebhookClient errorsWebhook;
	
	private Config() {
		this.reload();
	}
	
	public <Type> Type get(String path) {
		return this.get(path, (Type) null);
	}

	public <Type> Type get(String path, Class<Type> clazz) {
		return this.get(path);
	}
	
	public <Type> Type get(String path, Type defaultValue) {
		return this.get(Arrays.asList(path.split("\\.")), defaultValue);
	}
	
	@SuppressWarnings("unchecked")
	public <Type> Type get(List<String> path, Type defaultValue) {
		Document json = this.json;
		
		for (int i = 0; i < path.size(); i++) {
			String key = path.get(i);
			if (!json.containsKey(key)) {
				return defaultValue;
			}
			
			Object value = json.get(key);
			if (i == path.size() - 1) {
				return (Type) value;
			}
			
			if (value instanceof Document) {
				json = (Document) value;
			} else {
				return defaultValue;
			}
		}
		
		return defaultValue;
	}

	public Config set(String path, Object value) {
		return this.set(Arrays.asList(path.split("\\.")), value);
	}

	public Config set(List<String> path, Object value) {
		Document json = this.json;

		for (int i = 0; i < path.size(); i++) {
			String key = path.get(i);
			if (i == path.size() - 1) {
				json.append(key, value);
				break;
			}

			Object oldValue = json.get(key);
			if (oldValue instanceof Document) {
				json = (Document) oldValue;
			} else {
				json.append(key, json = new Document());
			}
		}

		return this;
	}

	public String getSupportDescription() {
		return this.get("support.description");
	}

	public String getPayPalUrl() {
		return this.get("donate.paypal");
	}

	public String getPatreonUrl() {
		return this.get("donate.patreon");
	}

	public String getDonationDescription() {
		return this.get("donate.description");
	}

	public String getInviteUrl(String id) {
		return String.format(this.get("invite.url", String.class), id);
	}

	public String getInviteDescription() {
		return this.get("invite.description");
	}

	public String getUserFlagEmotes(Collection<User.UserFlag> flags) {
		Document emotes = this.get("emote.userFlags");

		StringBuilder builder = new StringBuilder();
		for (User.UserFlag flag : flags) {
			String emote = emotes.getString(String.valueOf(flag.getOffset()));
			if (emote != null) {
				builder.append(emote).append(" ");
			}
		}

		return builder.toString();
	}

	public List<Document> getCredits() {
		return this.get("credits", Collections.emptyList());
	}

	public String getBackStory() {
		return this.get("backstory");
	}

	public List<Document> getOwners() {
		return this.get("owners", Collections.emptyList());
	}

	public long[] getOwnerIds() {
		return this.getOwners().stream().mapToLong(d -> d.getLong("id")).toArray();
	}

	public List<String> getDefaultPrefixes() {
		return this.get("defaultPrefixes");
	}
	
	public boolean isCanary() {
		return this.get("state.canary", true);
	}
	
	public boolean isTest() {
		return this.get("state.test", true);
	}
	
	public boolean isMain() {
		return !this.isCanary();
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

	public String getSupportGuildInvite() {
		return "https://discord.gg/" + this.get(this.getState() + ".supportGuild.invite");
	}
	
	public Guild getSupportGuild(ShardManager manager) {
		return manager.getGuildById(this.getSupportGuildId());
	}
	
	public long getErrorsChannelId() {
		return this.get(this.getState() + ".supportGuild.channel.errorsId", 0L);
	}
	
	public TextChannel getErrorsChannel(ShardManager manager) {
		Guild guild = this.getSupportGuild(manager);
		
		return guild == null ? null : guild.getTextChannelById(this.getErrorsChannelId());
	}
	
	public long getEventsChannelId() {
		return this.get(this.getState() + ".supportGuild.channel.eventsId", 0L);
	}
	
	public TextChannel getEventsChannel(ShardManager manager) {
		Guild guild = this.getSupportGuild(manager);
		
		return guild == null ? null : guild.getTextChannelById(this.getEventsChannelId());
	}
	
	public long getGuildsChannelId() {
		return this.get(this.getState() + ".supportGuild.channel.guildsId", 0L);
	}
	
	public TextChannel getGuildsChannel(ShardManager manager) {
		Guild guild = this.getSupportGuild(manager);
		
		return guild == null ? null : guild.getTextChannelById(this.getGuildsChannelId());
	}
	
	public long getDonatorRoleId() {
		return this.get(this.getState() + ".supportGuild.role.donatorId", 0L);
	}
	
	public Role getDonatorRole(ShardManager manager) {
		Guild guild = this.getSupportGuild(manager);
		
		return guild == null ? null : guild.getRoleById(this.getDonatorRoleId());
	}

	public long getErrorsWebhookId() {
		return this.get(this.getState() + ".supportGuild.webhook.errors.id");
	}

	public String getErrorsWebhookToken() {
		return this.get(this.getState() + ".supportGuild.webhook.errors.token");
	}

	public WebhookClient getErrorsWebhook() {
		if (this.errorsWebhook == null) {
			this.errorsWebhook = new WebhookClientBuilder(this.getErrorsWebhookId(), this.getErrorsWebhookToken()).build();
		}

		return this.errorsWebhook;
	}
	
	public long getEventsWebhookId() {
		return this.get(this.getState() + ".supportGuild.webhook.events.id");
	}
	
	public String getEventsWebhookToken() {
		return this.get(this.getState() + ".supportGuild.webhook.events.token");
	}

	public long getGuildsWebhookId() {
		return this.get(this.getState() + ".supportGuild.webhook.guilds.id");
	}

	public String getGuildsWebhookToken() {
		return this.get(this.getState() + ".supportGuild.webhook.guilds.token");
	}

	public long getPatreonWebhookId() {
		return this.get(this.getState() + ".supportGuild.webhook.patreon.id");
	}

	public String getPatreonWebhookToken() {
		return this.get(this.getState() + ".supportGuild.webhook.patreon.token");
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

	public String getIp() {
		return this.get(this.getState() + ".host.ip", "localhost");
	}
	
	public String getDomain() {
		return this.get(this.getState() + ".host.domain", this.getIp() + ":" + this.getPort());
	}

	public String getBaseUrl() {
		String domain = this.get(this.getState() + ".host.domain");
		return domain == null ? "http://" + this.getIp() + ":" + this.getPort() : "https://" + domain;
	}
	
	public int getPort() {
		return this.get(this.getState() + ".host.port", 8082);
	}

	public String getVoteWebserverIp() {
		return this.get("webserver.vote.ip", "localhost");
	}

	public String getVoteWebserverPath() {
		return this.get("webserver.vote.path", "/");
	}

	public int getVoteWebserverPort() {
		return this.get("webserver.vote.port", 8080);
	}

	private String getVoteWebserverBaseUrl() {
		String domain = this.get("webserver.vote.domain");
		return domain == null ? "http://" + this.getVoteWebserverIp() + ":" + this.getVoteWebserverPort() : "https://" + domain;
	}

	public String getVoteWebserverUrl(String endpoint) {
		return this.getVoteWebserverBaseUrl() + this.getVoteWebserverPath() + endpoint;
	}

	public String getImageWebserverIp() {
		return this.get("webserver.image.ip", "localhost");
	}

	public String getImageWebserverPath() {
		return this.get("webserver.image.path", "/");
	}

	private String getImageWebserverBaseUrl() {
		String domain = this.get("webserver.image.domain");
		return domain == null ? "http://" + this.getImageWebserverIp() + ":" + this.getImageWebserverPort() : "https://" + domain;
	}

	public int getImageWebserverPort() {
		return this.get("webserver.image.port", 8443);
	}

	public String getImageWebserverUrl(String endpoint) {
		return this.getImageWebserverBaseUrl() + this.getImageWebserverPath() + endpoint;
	}

	public String getSearchWebserverIp() {
		return this.get("webserver.search.ip", "localhost");
	}

	public String getSearchWebserverPath() {
		return this.get("webserver.search.path", "/");
	}

	private String getSearchWebserverBaseUrl() {
		String domain = this.get("webserver.search.domain");
		return domain == null ? "http://" + this.getSearchWebserverIp() + ":" + this.getSearchWebserverPort() : "https://" + domain;
	}

	public int getSearchWebserverPort() {
		return this.get("webserver.search.port", 8084);
	}

	public String getSearchWebserverUrl(String endpoint) {
		return this.getSearchWebserverBaseUrl() + this.getSearchWebserverPath() + endpoint;
	}
	
	public String getVainGlory() {
		return this.get("token.vainGlory");
	}
	
	public String getDiscordListSpace() {
		return this.get("token.discordListSpace");
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
	
	public String getCurrencyConvertor() {
		return this.get("token.currencyConvertor");
	}
	
	public String getIGDB() {
		return this.get("token.igdb");
	}
	
	public String getBitly() {
		return this.get("token.bitly");
	}
	
	public String getYouTube() {
		return this.get("token.youtube");
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
	
	public String getPatreonWebhookSecret() {
		return this.get("token.patreon.webhook");
	}

	public String getPatreonAccessToken() {
		return this.get("token.patreon.access_token");
	}

	public int getPatreonCampaignId() {
		return this.get("token.patreon.campaign_id");
	}

	public String getTwitch() {
		return this.get("token.twitch.token");
	}

	public long getTwitchExpiresAt() {
		return this.get("token.twitch.expiresAt", Number.class).longValue();
	}

	public String getTwitchClientSecret() {
		return this.get("token.twitch.clientSecret");
	}

	public String getTwitchClientId() {
		return this.get("token.twitch.clientId");
	}
	
	public String getVoteApi(boolean sx4) {
		return this.get("token.voteApi." + (sx4 ? "sx4" : "jockieMusic"));
	}

	public String getGitHubWebhookSecret() {
		return this.get("token.github.webhook");
	}

	public String getGitHubBranch() {
		return this.get(this.getState() + ".github.branch");
	}

	public String getImageWebserver() {
		return this.get("token.imageWebserver");
	}
	
	public int getPremiumPrice() {
		return this.get("premium.price", 500);
	}
	
	public String getSuccessEmote() {
		return this.emoteSuccess;
	}
	
	public String getFailureEmote() {
		return this.emoteFailure;
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

	public List<Document> getPolicies() {
		return this.get("policy");
	}

	public void update() {
		try (FileOutputStream stream = new FileOutputStream("config.json")) {
			stream.write(this.json.toJson().getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void reload() {
		try (FileInputStream stream = new FileInputStream("config.json")) {
			this.json = Document.parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
			
			this.emoteSuccess = this.get("emote.success");
			this.emoteFailure = this.get("emote.failure");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}