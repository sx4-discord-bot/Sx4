package com.sx4.bot.events;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.sx4.bot.database.Database;
import com.sx4.bot.utils.WelcomerUtils;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.send.WebhookMessage;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import okhttp3.OkHttpClient;

public class WelcomerEvents extends ListenerAdapter {
	
	private OkHttpClient client = new OkHttpClient.Builder().build();
	
	private ScheduledExecutorService scheduledExectuor = Executors.newSingleThreadScheduledExecutor();
	
	private Map<Long, WebhookClient> welcomerWebhooks = new HashMap<>();
	private Map<Long, WebhookClient> leaverWebhooks = new HashMap<>();
	
	private void createNewWelcomerWebhook(Guild guild, TextChannel channel, User selfUser, Consumer<WebhookClient> webhook) {
		channel.createWebhook(selfUser.getName() + " - Welcomer").queue(newWebhook -> {
			WebhookClient newWebhookClient = new WebhookClientBuilder(newWebhook.getUrl())
					.setHttpClient(this.client)
					.setExecutorService(this.scheduledExectuor)
					.build();
			
			this.welcomerWebhooks.put(guild.getIdLong(), newWebhookClient);
			
			Bson update = Updates.combine(Updates.set("welcomer.webhookId", newWebhook.getIdLong()), Updates.set("welcomer.webhookToken", newWebhook.getToken()));
			Database.get().updateGuildById(guild.getIdLong(), update, (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
				}
			});
			
			webhook.accept(newWebhookClient);
		});
	}
	
	private void createNewLeaverWebhook(Guild guild, TextChannel channel, User selfUser, Consumer<WebhookClient> webhook) {
		channel.createWebhook(selfUser.getName() + " - Leaver").queue(newWebhook -> {
			WebhookClient newWebhookClient = new WebhookClientBuilder(newWebhook.getUrl())
					.setHttpClient(this.client)
					.setExecutorService(this.scheduledExectuor)
					.build();
			
			this.leaverWebhooks.put(guild.getIdLong(), newWebhookClient);
			
			Bson update = Updates.combine(Updates.set("leaver.webhookId", newWebhook.getIdLong()), Updates.set("leaver.webhookToken", newWebhook.getToken()));
			Database.get().updateGuildById(guild.getIdLong(), update, (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
				}
			});
			
			webhook.accept(newWebhookClient);
		});
	}
	
	private void getWelcomerWebhook(Guild guild, Document data, Consumer<WebhookClient> webhook) throws MalformedURLException, IOException {
		TextChannel channel = guild.getTextChannelById(data.getLong("channelId"));
		if (channel == null) {
			return;
		}
		
		if (!guild.getSelfMember().hasPermission(Permission.MANAGE_WEBHOOKS)) {
			return;
		}
		
		User selfUser = guild.getSelfMember().getUser();
		if (this.welcomerWebhooks.containsKey(guild.getIdLong())) {
			WebhookClient webhookClient = this.welcomerWebhooks.get(guild.getIdLong());
			guild.retrieveWebhooks().queue(webhooks -> {
				Webhook currentWebhook = null;
				for (Webhook guildWebhook : webhooks) {
					if (guildWebhook.getIdLong() == webhookClient.getId()) {
						currentWebhook = guildWebhook;
					}
				}
				
				if (currentWebhook != null) {
					if (!currentWebhook.getChannel().equals(channel)) {
						currentWebhook.delete().queue();
						
						this.createNewWelcomerWebhook(guild, channel, selfUser, webhook);
					} else {
						webhook.accept(webhookClient);
					}
				} else {
					this.createNewWelcomerWebhook(guild, channel, selfUser, webhook);
				}
			});
		} else {
			Long webhookId = data.getLong("webhookId");
			String webhookToken = data.getString("webhookToken");
			
			if (webhookToken == null || webhookId == null) {
				this.createNewWelcomerWebhook(guild, channel, selfUser, webhook);
			} else {
				guild.retrieveWebhooks().queue(webhooks -> {
					Webhook currentWebhook = null;
					for (Webhook guildWebhook : webhooks) {
						if (guildWebhook.getIdLong() == webhookId && guildWebhook.getToken().equals(webhookToken)) {
							currentWebhook = guildWebhook;
						}
					}
					
					WebhookClient webhookClient = new WebhookClientBuilder(webhookId, webhookToken)
							.setHttpClient(this.client)
							.setExecutorService(this.scheduledExectuor)
							.build();
					
					if (currentWebhook != null) {
						if (!currentWebhook.getChannel().equals(channel)) {
							currentWebhook.delete().queue();
							
							this.createNewWelcomerWebhook(guild, channel, selfUser, webhook);
						} else {
							this.welcomerWebhooks.put(guild.getIdLong(), webhookClient);
							webhook.accept(webhookClient);
						}
					} else {
						this.createNewWelcomerWebhook(guild, channel, selfUser, webhook);
					}
				});
			}
		}
	}
	
	private void getLeaverWebhook(Guild guild, Document data, Consumer<WebhookClient> webhook) throws MalformedURLException, IOException {
		TextChannel channel = guild.getTextChannelById(data.getLong("channelId"));
		if (channel == null) {
			return;
		}
		
		if (!guild.getSelfMember().hasPermission(Permission.MANAGE_WEBHOOKS)) {
			return;
		}
		
		User selfUser = guild.getSelfMember().getUser();
		if (this.leaverWebhooks.containsKey(guild.getIdLong())) {
			WebhookClient webhookClient = this.leaverWebhooks.get(guild.getIdLong());
			guild.retrieveWebhooks().queue(webhooks -> {
				Webhook currentWebhook = null;
				for (Webhook guildWebhook : webhooks) {
					if (guildWebhook.getIdLong() == webhookClient.getId()) {
						currentWebhook = guildWebhook;
					}
				}
				
				if (currentWebhook != null) {
					if (!currentWebhook.getChannel().equals(channel)) {
						currentWebhook.delete().queue();
						
						this.createNewLeaverWebhook(guild, channel, selfUser, webhook);
					} else {
						webhook.accept(webhookClient);
					}
				} else {
					this.createNewLeaverWebhook(guild, channel, selfUser, webhook);
				}
			});
		} else {
			Long webhookId = data.getLong("webhookId");
			String webhookToken = data.getString("webhookToken");
			
			if (webhookToken == null || webhookId == null) {
				this.createNewLeaverWebhook(guild, channel, selfUser, webhook);
			} else {
				guild.retrieveWebhooks().queue(webhooks -> {
					Webhook currentWebhook = null;
					for (Webhook guildWebhook : webhooks) {
						if (guildWebhook.getIdLong() == webhookId && guildWebhook.getToken().equals(webhookToken)) {
							currentWebhook = guildWebhook;
						}
					}
					
					WebhookClient webhookClient = new WebhookClientBuilder(webhookId, webhookToken)
							.setHttpClient(this.client)
							.setExecutorService(this.scheduledExectuor)
							.build();
					
					if (currentWebhook != null) {
						if (!currentWebhook.getChannel().equals(channel)) {
							currentWebhook.delete().queue();
							
							this.createNewLeaverWebhook(guild, channel, selfUser, webhook);
						} else {
							this.leaverWebhooks.put(guild.getIdLong(), webhookClient);
							webhook.accept(webhookClient);
						}
					} else {
						this.createNewLeaverWebhook(guild, channel, selfUser, webhook);
					}
				});
			}
		}
	}

	public void onGuildMemberJoin(GuildMemberJoinEvent event) {
		Bson projection = Projections.include("welcomer.enabled", "welcomer.channelId", "welcomer.webhookId", "welcomer.webhookToken", "welcomer.message", "imageWelcomer.gif", "imageWelcomer.enabled", "imageWelcomer.banner", "welcomer.embed", "welcomer.dm");
		Document data = Database.get().getGuildById(event.getGuild().getIdLong(), null, projection);
		Document welcomerData = data.get("welcomer", Database.EMPTY_DOCUMENT), imageWelcomerData = data.get("imageWelcomer", Database.EMPTY_DOCUMENT);
		if ((!welcomerData.getBoolean("enabled", false) && !imageWelcomerData.getBoolean("enabled", false)) || welcomerData.getLong("channelId") == null) {
			return;
		}
		
		if (welcomerData.getBoolean("dm", false) && !event.getUser().isBot()) {
			WelcomerUtils.getWelcomerPreview(event.getMember(), event.getGuild(), data, imageWelcomerData.getBoolean("gif", false), (message, response) -> {
				if (!message.isEmpty() && response == null) {
					event.getUser().openPrivateChannel().queue(channel -> channel.sendMessage(message.build()).queue(), e -> {});
				} else {
					byte[] bytes;
					try {
						bytes = response.body().bytes();
					} catch (IOException e) {
						return;
					}
					
					try {
						if (message.isEmpty()) {
							event.getUser().openPrivateChannel().queue(channel -> channel.sendFile(bytes, "welcomer." + response.headers().get("Content-Type").split("/")[1]).queue(), e -> {});
						} else {
							event.getUser().openPrivateChannel().queue(channel -> channel.sendMessage(message.build()).addFile(bytes, "welcomer." + response.headers().get("Content-Type").split("/")[1]).queue(), e -> {});
						}
					} catch (IllegalArgumentException e) {}
				}
			});
		} else {	
			try {
				this.getWelcomerWebhook(event.getGuild(), welcomerData, webhook -> {
					WelcomerUtils.getWelcomerMessage(event.getMember(), event.getGuild(), data, imageWelcomerData.getBoolean("gif", false), message -> {
						webhook.send(message.setAvatarUrl(event.getJDA().getSelfUser().getEffectiveAvatarUrl()).build());
					});
				});
			} catch (IOException e) {}
		}
	}
	
	public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
		Bson projection = Projections.include("leaver.enabled", "leaver.channelId", "leaver.embed", "leaver.webhookId", "leaver.webhookToken", "leaver.message");
		Document data = Database.get().getGuildById(event.getGuild().getIdLong(), null, projection).get("leaver", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false) || data.getLong("channelId") == null) {
			return;
		}

		try {
			this.getLeaverWebhook(event.getGuild(), data, webhook -> {
				WebhookMessage message = WelcomerUtils.getLeaver(event.getMember(), event.getGuild(), data)
						.setAvatarUrl(event.getJDA().getSelfUser().getEffectiveAvatarUrl())
						.build();
				
				webhook.send(message);
			});
		} catch (IOException e) {}
	}
}
