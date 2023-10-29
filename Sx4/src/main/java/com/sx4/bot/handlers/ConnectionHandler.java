package com.sx4.bot.handlers;

import com.sx4.bot.core.Sx4;
import com.sx4.bot.utility.ExceptionUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDA.ShardInfo;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.WebhookClient;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.session.SessionDisconnectEvent;
import net.dv8tion.jda.api.events.session.SessionRecreateEvent;
import net.dv8tion.jda.api.events.session.SessionResumeEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.requests.CloseCode;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.OffsetDateTime;

public class ConnectionHandler implements EventListener {

	private WebhookClient<Message> eventsWebhook;

	private final Sx4 bot;

	public ConnectionHandler(Sx4 bot) {
		this.bot = bot;
	}

	private WebhookClient<Message> getWebhook(JDA jda) {
		if (this.eventsWebhook == null) {
			this.eventsWebhook = WebhookClient.createClient(jda, Long.toString(this.bot.getConfig().getEventsWebhookId()), this.bot.getConfig().getEventsWebhookToken());
		}

		return this.eventsWebhook;
	}
	
	private int readyEventsCalled = 0;
	private boolean ready = false;

	public boolean isReady() {
		return this.ready;
	}
	
	public MessageEmbed getEmbed(JDA jda, String state, int colour) {
		return this.getEmbed(jda, state, null, null, colour);
	}
	
	public MessageEmbed getEmbed(JDA jda, String state, CloseCode closeCode, OffsetDateTime disconnectTime, int colour) {
		ShardInfo shardInfo = jda.getShardInfo();
		
		EmbedBuilder builder = new EmbedBuilder()
			.setColor(colour)
			.setFooter(state)
			.setTimestamp(disconnectTime != null ? disconnectTime : Instant.now())
			.addField("Shard", (shardInfo.getShardId() + 1) + "/" + shardInfo.getShardTotal(), false)
			.setAuthor(jda.getSelfUser().getAsTag(), null, jda.getSelfUser().getEffectiveAvatarUrl());
		
		if (closeCode != null) {
			builder.addField("Reason", closeCode.getMeaning() + " [" + closeCode.getCode() + "]", false);
		}
		
		return builder.build();
	}

	public void onReady(ReadyEvent event) {
		JDA jda = event.getJDA();

		if (++this.readyEventsCalled == jda.getShardInfo().getShardTotal()) {
			ExceptionUtility.safeRun(this.bot.getReminderManager()::ensureReminders);
			ExceptionUtility.safeRun(this.bot.getMuteManager()::ensureMutes);
			ExceptionUtility.safeRun(this.bot.getTemporaryBanManager()::ensureBans);
			ExceptionUtility.safeRun(this.bot.getGiveawayManager()::ensureGiveaways);
			ExceptionUtility.safeRun(this.bot.getYouTubeManager()::ensureSubscriptions);
			ExceptionUtility.safeRun(this.bot.getFreeGameManager()::ensureAnnouncedGames);
			ExceptionUtility.safeRun(this.bot.getFreeGameManager()::ensureEpicFreeGames);
			ExceptionUtility.safeRun(this.bot.getFreeGameManager()::ensureSteamFreeGames);
			ExceptionUtility.safeRun(this.bot.getFreeGameManager()::ensureGOGFreeGames);
			ExceptionUtility.safeRun(this.bot.getEntitlementsHandler()::ensureEntitlements);

			if (this.bot.getConfig().isMain()) {
				ExceptionUtility.safeRun(this.bot.getPatreonManager()::ensurePatrons);
			}

			this.ready = true;
		}
		
		this.getWebhook(event.getJDA()).sendMessageEmbeds(this.getEmbed(jda, "Ready", this.bot.getConfig().getGreen())).queue();
	}
	
	public void onReconnected(SessionRecreateEvent event) {
		this.getWebhook(event.getJDA()).sendMessageEmbeds(this.getEmbed(event.getJDA(), "Reconnect", this.bot.getConfig().getGreen())).queue();
	}
	
	public void onResumed(SessionResumeEvent event) {
		this.getWebhook(event.getJDA()).sendMessageEmbeds(this.getEmbed(event.getJDA(), "Resume", this.bot.getConfig().getGreen())).queue();
	}
	
	public void onDisconnect(SessionDisconnectEvent event) {
		this.getWebhook(event.getJDA()).sendMessageEmbeds(this.getEmbed(event.getJDA(), "Disconnect", event.getCloseCode(), event.getTimeDisconnected(), this.bot.getConfig().getRed())).queue();
	}

	@Override
	public void onEvent(@NotNull GenericEvent event) {
		if (event instanceof ReadyEvent) {
			this.onReady((ReadyEvent) event);
		} else if (event instanceof SessionRecreateEvent) {
			this.onReconnected((SessionRecreateEvent) event);
		} else if (event instanceof SessionResumeEvent) {
			this.onResumed((SessionResumeEvent) event);
		} else if (event instanceof SessionDisconnectEvent) {
			this.onDisconnect((SessionDisconnectEvent) event);
		}
	}

}
