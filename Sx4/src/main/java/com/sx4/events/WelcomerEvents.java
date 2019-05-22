package com.sx4.events;

import static com.rethinkdb.RethinkDB.r;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.sx4.core.Sx4Bot;
import com.sx4.utils.WelcomerUtils;

import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Icon;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.entities.Webhook;
import net.dv8tion.jda.core.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberLeaveEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import net.dv8tion.jda.webhook.WebhookMessage;
import net.dv8tion.jda.webhook.WebhookMessageBuilder;

public class WelcomerEvents extends ListenerAdapter {
	
	private Map<String, Webhook> welcomerWebhooks = new HashMap<>();
	private Map<String, Webhook> leaverWebhooks = new HashMap<>();
	
	private void getWelcomerWebhook(Guild guild, Map<String, Object> data, Consumer<Webhook> webhook) throws MalformedURLException, IOException {
		TextChannel channel = guild.getTextChannelById((String) data.get("channel")); 
		User selfUser = guild.getSelfMember().getUser();
		if (this.welcomerWebhooks.containsKey(guild.getId())) {
			Webhook currentWebhook = this.welcomerWebhooks.get(guild.getId());
			if (!currentWebhook.getChannel().equals(channel)) {
				currentWebhook.delete().queue();
				channel.createWebhook(selfUser.getName() + " - Welcomer").setAvatar(Icon.from(new URL(selfUser.getEffectiveAvatarUrl()).openStream())).queue(newWebhook -> {
					this.welcomerWebhooks.put(guild.getId(), newWebhook);
					webhook.accept(newWebhook);
				});
			} else {
				webhook.accept(currentWebhook);
			}
		} else {
			channel.getWebhooks().queue(webhooks -> {
				for (Webhook channelWebhook : webhooks) {
					if (channelWebhook.getName().equals(selfUser.getName() + " - Welcomer")) {
						if (!channelWebhook.getChannel().equals(channel)) {
							channelWebhook.delete().queue();
							try {
								channel.createWebhook(selfUser.getName() + " - Welcomer").setAvatar(Icon.from(new URL(selfUser.getEffectiveAvatarUrl()).openStream())).queue(newWebhook -> {
									this.welcomerWebhooks.put(guild.getId(), newWebhook);
									webhook.accept(newWebhook);
								});
							} catch (IOException e) {}
						} else {
							this.welcomerWebhooks.put(guild.getId(), channelWebhook);
							webhook.accept(channelWebhook);
						}
						
						return;
					}
				}
				
				try {
					channel.createWebhook(selfUser.getName() + " - Welcomer").setAvatar(Icon.from(new URL(selfUser.getEffectiveAvatarUrl()).openStream())).queue(newWebhook -> {
						this.welcomerWebhooks.put(guild.getId(), newWebhook);
						webhook.accept(newWebhook);
					});
				} catch (IOException e) {}
			});
		}
	}
	
	private void getLeaverWebhook(Guild guild, Map<String, Object> data, Consumer<Webhook> webhook) throws MalformedURLException, IOException {
		TextChannel channel = guild.getTextChannelById((String) data.get("leavechannel")); 
		User selfUser = guild.getSelfMember().getUser();
		if (this.leaverWebhooks.containsKey(guild.getId())) {
			Webhook currentWebhook = this.leaverWebhooks.get(guild.getId());
			if (!currentWebhook.getChannel().equals(channel)) {
				currentWebhook.delete().queue();
				channel.createWebhook(selfUser.getName() + " - Leaver").setAvatar(Icon.from(new URL(selfUser.getEffectiveAvatarUrl()).openStream())).queue(newWebhook -> {
					this.leaverWebhooks.put(guild.getId(), newWebhook);
					webhook.accept(newWebhook);
				});
			} else {
				webhook.accept(currentWebhook);
			}
		} else {
			channel.getWebhooks().queue(webhooks -> {
				for (Webhook channelWebhook : webhooks) {
					if (channelWebhook.getName().equals(selfUser.getName() + " - Leaver")) {
						if (!channelWebhook.getChannel().equals(channel)) {
							channelWebhook.delete().queue();
							try {
								channel.createWebhook(selfUser.getName() + " - Leaver").setAvatar(Icon.from(new URL(selfUser.getEffectiveAvatarUrl()).openStream())).queue(newWebhook -> {
									this.leaverWebhooks.put(guild.getId(), newWebhook);
									webhook.accept(newWebhook);
								});
							} catch (IOException e) {}
						} else {
							this.leaverWebhooks.put(guild.getId(), channelWebhook);
							webhook.accept(channelWebhook);
						}
						
						return;
					}
				}
				
				try {
					channel.createWebhook(selfUser.getName() + " - Leaver").setAvatar(Icon.from(new URL(selfUser.getEffectiveAvatarUrl()).openStream())).queue(newWebhook -> {
						this.leaverWebhooks.put(guild.getId(), newWebhook);
						webhook.accept(newWebhook);
					});
				} catch (IOException e) {}
			});
		}
	}

	public void onGuildMemberJoin(GuildMemberJoinEvent event) {
		Map<String, Object> data = r.table("welcomer").get(event.getGuild().getId()).run(Sx4Bot.getConnection());
		if (data == null || (boolean) data.get("toggle") == false || data.get("channel") == null) {
			return;
		}
		
		try {
			this.getWelcomerWebhook(event.getGuild(), data, webhook -> {
				WelcomerUtils.getWelcomerMessage(event.getMember(), event.getGuild(), data, (message, response) -> {
					if (message.isEmpty() && response != null) {
						byte[] bytes;
						try {
							bytes = response.body().bytes();
						} catch (IOException e) {
							return;
						}
						
						try {
							webhook.newClient().build().send(bytes, "welcomer." + response.headers().get("Content-Type").split("/")[1]);
						} catch (IllegalArgumentException e) {}
					} else if (!message.isEmpty() && response == null) {
						webhook.newClient().build().send(message.build());
					} else {
						byte[] bytes;
						try {
							bytes = response.body().bytes();
						} catch (IOException e) {
							return;
						}
						
						try {
							WebhookMessage webhookMessage = new WebhookMessageBuilder(message.build()).addFile("welcomer." + response.headers().get("Content-Type").split("/")[1], bytes).build();
							webhook.newClient().build().send(webhookMessage);
						} catch (IllegalArgumentException e) {}
					}
				});
			});
		} catch (IOException e) {}
	}
	
	public void onGuildMemberLeave(GuildMemberLeaveEvent event) {
		Map<String, Object> data = r.table("welcomer").get(event.getGuild().getId()).run(Sx4Bot.getConnection());
		if (data == null || (boolean) data.get("leavetoggle") == false || data.get("leavechannel") == null) {
			return;
		}
		
		try {
			this.getLeaverWebhook(event.getGuild(), data, webhook -> {
				webhook.newClient().build().send(WelcomerUtils.getLeaver(event.getMember(), event.getGuild(), data).build());
			});
		} catch (IOException e) {}
	}
}
