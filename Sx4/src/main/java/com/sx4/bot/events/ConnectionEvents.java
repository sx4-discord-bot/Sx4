package com.sx4.bot.events;

import java.time.Instant;

import com.sx4.api.Main;
import com.sx4.bot.cache.SteamCache;
import com.sx4.bot.core.Sx4Bot;
import com.sx4.bot.economy.Item;
import com.sx4.bot.settings.Settings;
import com.sx4.bot.utils.HelpUtils;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.send.WebhookEmbed.EmbedAuthor;
import club.minnced.discord.webhook.send.WebhookEmbed.EmbedField;
import club.minnced.discord.webhook.send.WebhookEmbed.EmbedFooter;
import club.minnced.discord.webhook.send.WebhookEmbedBuilder;
import net.dv8tion.jda.api.events.DisconnectEvent;
import net.dv8tion.jda.api.events.GatewayPingEvent;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.ReconnectedEvent;
import net.dv8tion.jda.api.events.ResumedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class ConnectionEvents extends ListenerAdapter {
	
	public static long lastGatewayPing = -1;
	
	public static long getLastGatewayPing() {
		return ConnectionEvents.lastGatewayPing;
	}
	
	private WebhookClient webhook = new WebhookClientBuilder(Settings.EVENTS_WEBHOOK_ID, Settings.EVENTS_WEBHOOK_TOKEN).build();
	
	private int readyEventsCalled = 0;
	
	public void onReady(ReadyEvent event) {
		this.readyEventsCalled++;
		if (this.readyEventsCalled == Sx4Bot.getShardManager().getShardsTotal()) {
			int availableGuilds = event.getGuildAvailableCount();
			int totalGuilds = event.getGuildTotalCount();
			System.out.println(String.format("Connected to %s with %,d/%,d available servers and %,d users", event.getJDA().getSelfUser().getAsTag(), availableGuilds, totalGuilds, Sx4Bot.getShardManager().getUsers().size()));
			
			try {
				Main.initiateWebserver();
			} catch(Exception e) {
				e.printStackTrace();
			}
			
			SteamCache.getGames();
			HelpUtils.ensureAdvertisement();
			StatusEvents.initialize();
			ServerPostEvents.initializePosting();
			MuteEvents.ensureMuteRoles();
			StatsEvents.initializeBotLogs();
			StatsEvents.initializeGuildStats();
			ReminderEvents.ensureReminders();
			GiveawayEvents.ensureGiveaways();
			AwaitEvents.ensureAwaitData();
			MuteEvents.ensureMutes();
			AutoroleEvents.ensureAutoroles();
			Item.loadConfig();
			Sx4Bot.getYouTubeManager().ensureResubscriptions();
			
			System.gc();
		}
	}
	
	public void onGatewayPing(GatewayPingEvent event) {
		ConnectionEvents.lastGatewayPing = event.getOldPing();
	}
	
	public void onDisconnect(DisconnectEvent event) {
		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setAuthor(new EmbedAuthor(event.getJDA().getSelfUser().getAsTag(), event.getJDA().getSelfUser().getEffectiveAvatarUrl(), null));
		embed.setTimestamp(event.getTimeDisconnected());
		embed.setColor(Settings.COLOR_RED.hashCode());
		embed.addField(new EmbedField(false, "Shard", (event.getJDA().getShardInfo().getShardId() + 1) + "/" + event.getJDA().getShardInfo().getShardTotal()));
		if (event.getCloseCode() != null) {
			embed.addField(new EmbedField(false, "Reason", event.getCloseCode().getMeaning() + " [" + event.getCloseCode().getCode() + "]"));
		}
		
		embed.setFooter(new EmbedFooter("Disconnect", null));
		
		this.webhook.send(embed.build());
	}
	
	public void onResume(ResumedEvent event) {
		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setAuthor(new EmbedAuthor(event.getJDA().getSelfUser().getAsTag(), event.getJDA().getSelfUser().getEffectiveAvatarUrl(), null));
		embed.setTimestamp(Instant.now());
		embed.setColor(Settings.COLOR_GREEN.hashCode());
		embed.addField(new EmbedField(false, "Shard", (event.getJDA().getShardInfo().getShardId() + 1) + "/" + event.getJDA().getShardInfo().getShardTotal()));
		
		embed.setFooter(new EmbedFooter("Resume", null));
		
		this.webhook.send(embed.build());
	}
	
	public void onReconnect(ReconnectedEvent event) {
		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setAuthor(new EmbedAuthor(event.getJDA().getSelfUser().getAsTag(), event.getJDA().getSelfUser().getEffectiveAvatarUrl(), null));
		embed.setTimestamp(Instant.now());
		embed.setColor(Settings.COLOR_GREEN.hashCode());
		embed.addField(new EmbedField(false, "Shard", (event.getJDA().getShardInfo().getShardId() + 1) + "/" + event.getJDA().getShardInfo().getShardTotal()));
		
		embed.setFooter(new EmbedFooter("Reconnect", null));

		this.webhook.send(embed.build());
	}
	
}
