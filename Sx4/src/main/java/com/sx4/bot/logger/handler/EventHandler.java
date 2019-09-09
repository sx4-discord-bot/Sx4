package com.sx4.bot.logger.handler;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.sx4.bot.cache.GuildMessageCache;
import com.sx4.bot.core.Sx4Bot;
import com.sx4.bot.database.Database;
import com.sx4.bot.logger.Statistics;
import com.sx4.bot.logger.util.Utils;
import com.sx4.bot.settings.Settings;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.exception.HttpException;
import club.minnced.discord.webhook.send.WebhookEmbed;
import club.minnced.discord.webhook.send.WebhookEmbed.EmbedAuthor;
import club.minnced.discord.webhook.send.WebhookEmbed.EmbedField;
import club.minnced.discord.webhook.send.WebhookEmbed.EmbedFooter;
import club.minnced.discord.webhook.send.WebhookEmbedBuilder;
import club.minnced.discord.webhook.send.WebhookMessage;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.audit.AuditLogKey;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.events.channel.category.CategoryCreateEvent;
import net.dv8tion.jda.api.events.channel.category.CategoryDeleteEvent;
import net.dv8tion.jda.api.events.channel.category.update.CategoryUpdateNameEvent;
import net.dv8tion.jda.api.events.channel.text.TextChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.text.TextChannelDeleteEvent;
import net.dv8tion.jda.api.events.channel.text.update.TextChannelUpdateNameEvent;
import net.dv8tion.jda.api.events.channel.voice.VoiceChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.voice.VoiceChannelDeleteEvent;
import net.dv8tion.jda.api.events.channel.voice.update.VoiceChannelUpdateNameEvent;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberLeaveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceGuildDeafenEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceGuildMuteEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceJoinEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceMoveEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageDeleteEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.api.events.role.RoleCreateEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.events.role.update.RoleUpdateNameEvent;
import net.dv8tion.jda.api.events.role.update.RoleUpdatePermissionsEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.internal.requests.EmptyRestAction;
import okhttp3.OkHttpClient;;

public class EventHandler extends ListenerAdapter {
	
	private static final int COLOR_GREEN = Settings.COLOR_GREEN.hashCode();
	private static final int COLOR_ORANGE = Settings.COLOR_ORANGE.hashCode();
	private static final int COLOR_RED = Settings.COLOR_RED.hashCode();
	
	/* It can some times get stuck in an infinite loop and these are used to prevent it */
	private static final int MAX_ATTEMPTS = 3;
	private static final int ATTEMPTS_BEFORE_REFETCH = 2;
	
	/* Used to ensure that the audit-log has come through */
	private static final int AUDIT_LOG_DELAY = 500;
	
	public static final Request EMPTY_REQUEST = new Request(null, 0L, null, null);
	
	public static class Request {
		
		public final JDA bot;
		public final long guildId;
		public final Document data;
		public final List<WebhookEmbed> embeds;
		
		public Request(JDA bot, long guildId, Document data, List<WebhookEmbed> embeds) {
			this.bot = bot;
			this.guildId = guildId;
			this.data = data;
			this.embeds = embeds;
		}
		
		public Guild getGuild() {
			return this.bot.getGuildById(this.guildId);
		}
	}
	
	private Map<Long, WebhookClient> webhooks = new HashMap<>();
	
	private Map<Long, BlockingDeque<Request>> queue = new HashMap<>();
	
	private ExecutorService executor = Executors.newCachedThreadPool();
	private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
	
	private OkHttpClient client = new OkHttpClient.Builder().build();
	
	public EventHandler() {}
	
	public Collection<WebhookClient> getRegisteredWebhooks() {
		return this.webhooks.values();
	}
	
	public Map<Long, BlockingDeque<Request>> getQueue() {
		return this.queue;
	}
	
	public int getTotalRequestsQueued() {
		return this.queue.values().stream()
			.mapToInt(queue -> queue.size())
			.sum();
	}
	
	private void handleRequest(JDA bot, Guild guild, Document data, List<WebhookEmbed> requestEmbeds) {
		if(!this.queue.containsKey(guild.getIdLong())) {
			BlockingDeque<Request> blockingDeque = new LinkedBlockingDeque<>();
			this.queue.put(guild.getIdLong(), blockingDeque);
			
			this.executor.submit(() -> {
				try {
					List<WebhookEmbed> embeds = new ArrayList<>();
					int length = 0, requests = 0;
					
					Request request;
					while((request = blockingDeque.take()) != EMPTY_REQUEST) {
						int lengthToSend = request.embeds.stream()
							.mapToInt(embed -> Utils.getLength(embed))
							.sum();
						
						/* Bulk the requests if there is more than one queued up */
						boolean hasSpace = embeds.size() + request.embeds.size() <= 10 && length + lengthToSend <= MessageEmbed.EMBED_MAX_LENGTH_BOT;
						if(hasSpace) {
							embeds.addAll(request.embeds);
							
							length += lengthToSend;
							requests++;
						}else{
							blockingDeque.addFirst(request);
						}
						
						if(!hasSpace || embeds.size() == 10 || blockingDeque.isEmpty()) {
							this._send(request.bot, request.getGuild(), request.data, embeds, requests, 0);
							
							embeds.clear();
							requests = 0;
							length = 0;
						}
					}
				}catch(InterruptedException e) {}
			});
		}
		
		this.queue.get(guild.getIdLong()).offer(new Request(bot, guild.getIdLong(), data, requestEmbeds));
	}
	
	private void _send(JDA bot, Guild guild, Document data, List<WebhookEmbed> embeds, int requestAmount, int attempts) {
		if(attempts >= MAX_ATTEMPTS) {
			Statistics.increaseSkippedLogs();
			
			return;
		}
		
		if(attempts >= ATTEMPTS_BEFORE_REFETCH) {
			data = Database.get().getGuildById(guild.getIdLong(), null, Projections.include("logger")).get("logger", Database.EMPTY_DOCUMENT);
		}
		
		TextChannel channel = guild.getTextChannelById(data.getLong("channelId"));
		if (channel == null) {
			return;
		}
		
		WebhookClient client;
		if(data.getLong("webhookId") == null || data.getString("webhookToken") == null) {
			Webhook webhook = channel.createWebhook("Sx4 - Logs").complete();
			
			data.put("webhookId", webhook.getId());
			data.put("webhookToken", webhook.getToken());
			
			Bson update = Updates.combine(Updates.set("logger.webhookId", webhook.getId()), Updates.set("logger.webhookToken", webhook.getToken()));
			Database.get().updateGuildById(guild.getIdLong(), update, (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
				}
			});
			
			client = new WebhookClientBuilder(webhook.getUrl())
				.setExecutorService(this.scheduledExecutorService)
				.setHttpClient(this.client)
				.build();
			
			this.webhooks.put(client.getId(), client);
		}else{
			Long webhookId = data.getLong("webhookId");
			String webhookToken = data.getString("webhookToken");
			
			client = this.webhooks.computeIfAbsent(webhookId, ($) -> 
				new WebhookClientBuilder(webhookId, webhookToken)
					.setExecutorService(this.scheduledExecutorService)
					.setHttpClient(this.client)
					.build());
		}
		
		try {
			WebhookMessage message = new WebhookMessageBuilder()
				.setAvatarUrl(bot.getSelfUser().getEffectiveAvatarUrl())
				.setUsername("Sx4 - Logs")
				.addEmbeds(embeds)
				.build();
			
			client.send(message).get();
			
			Statistics.increaseSuccessfulLogs(requestAmount);
		}catch(InterruptedException | ExecutionException e) {
			Statistics.increaseFailedLogs();
			
			if(e instanceof ExecutionException) {
				if(e.getCause() instanceof HttpException) {
					/* Ugly catch, blame JDA */
					if(e.getCause().getMessage().startsWith("Request returned failure 404")) {
						data.put("webhookId", null);
						data.put("wehookToken", null);
						
						/* 
						 * Calling close would close the scheduled executor service we are using,
						 * causing all logs to stop.
						 * 
						 * this.webhooks.remove(client.getId()).close(); 
						 */
						
						this.webhooks.remove(client.getId());
						
						Bson update = Updates.combine(Updates.set("logger.webhookId", null), Updates.set("logger.webhookToken", null));
						Database.get().updateGuildById(guild.getIdLong(), update, (result, exception) -> {
							if (exception != null) {
								exception.printStackTrace();
							}
						});
						
						this._send(bot, guild, data, embeds, requestAmount, attempts + 1);
						
						return;
					}
				}
			}
			
			System.err.println("[" + LocalDateTime.now().format(Sx4Bot.getTimeFormatter()) + "] [_send]");
			e.printStackTrace();
		}
	}
	
	public void send(JDA bot, Guild guild, Document data, List<WebhookEmbed> embeds) {
		this.executor.submit(() -> {
			this.handleRequest(bot, guild, data, embeds);
		});
	}
	
	public void send(JDA bot, Guild guild, Document data, WebhookEmbed... embeds) {
		this.send(bot, guild, data, List.of(embeds));
	}
	
	public void onGuildLeave(GuildLeaveEvent event) {
		BlockingDeque<Request> queue = this.queue.get(event.getGuild().getIdLong());
		if(queue != null) {
			queue.clear();
			
			/* Tell the thread that it is time to stop blocking */
			queue.offer(EMPTY_REQUEST);
		}
		
		this.queue.remove(event.getGuild().getIdLong());
	}
	
	public void onGuildMemberJoin(GuildMemberJoinEvent event) {
		Guild guild = event.getGuild();
		Member member = event.getMember();
		
		Document data = Database.get().getGuildById(guild.getIdLong(), null, Projections.include("logger")).get("logger", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false) || data.getLong("channelId") == null) {
			return;
		}
		
		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("`%s` just joined the server", member.getEffectiveName()));
		embed.setColor(COLOR_GREEN);
		embed.setTimestamp(ZonedDateTime.now());
		embed.setAuthor(new EmbedAuthor(member.getUser().getAsTag(), member.getUser().getEffectiveAvatarUrl(), null));
		
		embed.setFooter(new EmbedFooter(String.format("User ID: %s", member.getUser().getId()), null));
		
		this.send(event.getJDA(), guild, data, embed.build());		
	}
	
	public void onGuildMemberLeave(GuildMemberLeaveEvent event) {
		Guild guild = event.getGuild();
		Member member = event.getMember();
		
		Document data = Database.get().getGuildById(guild.getIdLong(), null, Projections.include("logger")).get("logger", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false) || data.getLong("channelId") == null) {
			return;
		}
	
		List<WebhookEmbed> embeds = new ArrayList<>();
	
		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("`%s` just left the server", member.getEffectiveName()));
		embed.setColor(COLOR_RED);
		embed.setTimestamp(ZonedDateTime.now());
		embed.setAuthor(new EmbedAuthor(member.getUser().getAsTag(), member.getUser().getEffectiveAvatarUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("User ID: %s", member.getUser().getId()), null));
		
		embeds.add(embed.build());
		
		if(guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			guild.retrieveAuditLogs().type(ActionType.KICK).queueAfter(AUDIT_LOG_DELAY, TimeUnit.MILLISECONDS, logs -> {
				AuditLogEntry entry = logs.stream()
					.filter(e -> e.getTargetIdLong() == member.getUser().getIdLong())
					.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now()).toSeconds() < 10)
					.findFirst()
					.orElse(null);
				
				if(entry != null) {
					embed.setDescription(String.format("`%s` has been kicked by **%s**", member.getEffectiveName(), entry.getUser().getAsTag()));
					
					embeds.add(embed.build());
				}
				
				this.send(event.getJDA(), guild, data, embeds);
			});
		}else{
			this.send(event.getJDA(), guild, data, embeds);
		}
	}
	
	public void onGuildBan(GuildBanEvent event) {
		Guild guild = event.getGuild();
		User user = event.getUser();
		
		Document data = Database.get().getGuildById(guild.getIdLong(), null, Projections.include("logger")).get("logger", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false) || data.getLong("channelId") == null) {
			return;
		}
	
		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("`%s` has been banned", user.getName()));
		embed.setColor(COLOR_RED);
		embed.setTimestamp(ZonedDateTime.now());
		embed.setAuthor(new EmbedAuthor(user.getAsTag(), user.getEffectiveAvatarUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("User ID: %s", user.getId()), null));
		
		if(guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			guild.retrieveAuditLogs().type(ActionType.BAN).queueAfter(AUDIT_LOG_DELAY, TimeUnit.MILLISECONDS, logs -> {
				AuditLogEntry entry = logs.stream()
					.filter(e -> e.getTargetIdLong() == user.getIdLong())
					.findFirst()
					.orElse(null);
				
				if(entry != null) {
					Statistics.increaseSuccessfulAuditLogs();
					
					embed.setDescription(String.format("`%s` has been banned by **%s**", user.getName(), entry.getUser().getAsTag()));
				}else{
					Statistics.increaseFailedAuditLogs();
					
					System.err.println(String.format("[onGuildBan] Could not find audit log for %s (%s) %s (%s)", guild.getName(), guild.getId(), user.getAsTag(), user.getId()));
				}
				
				this.send(event.getJDA(), guild, data, embed.build());
			});
		}else{
			this.send(event.getJDA(), guild, data, embed.build());
		}
	}
	
	public void onGuildUnban(GuildUnbanEvent event) {
		Guild guild = event.getGuild();
		User user = event.getUser();
		
		Document data = Database.get().getGuildById(guild.getIdLong(), null, Projections.include("logger")).get("logger", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false) || data.getLong("channelId") == null) {
			return;
		}
	
		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("`%s` has been unbanned", user.getName()));
		embed.setColor(COLOR_GREEN);
		embed.setTimestamp(ZonedDateTime.now());
		embed.setAuthor(new EmbedAuthor(user.getAsTag(), user.getEffectiveAvatarUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("User ID: %s", user.getId()), null));
		
		if(guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			guild.retrieveAuditLogs().type(ActionType.UNBAN).queueAfter(AUDIT_LOG_DELAY, TimeUnit.MILLISECONDS, logs -> {
				AuditLogEntry entry = logs.stream()
					.filter(e -> e.getTargetIdLong() == user.getIdLong())
					.findFirst()
					.orElse(null);
				
				if(entry != null) {
					Statistics.increaseSuccessfulAuditLogs();
					
					embed.setDescription(String.format("`%s` has been unbanned by **%s**", user.getName(), entry.getUser().getAsTag()));
				}else{
					Statistics.increaseFailedAuditLogs();
					
					System.err.println(String.format("[onGuildUnban] Could not find audit log for %s (%s) %s (%s)", guild.getName(), guild.getId(), user.getAsTag(), user.getId()));
				}
				
				this.send(event.getJDA(), guild, data, embed.build());
			});
		}else{
			this.send(event.getJDA(), guild, data, embed.build());
		}
	}
	
	public void onGuildMessageUpdate(GuildMessageUpdateEvent event) {
		Guild guild = event.getGuild();
		TextChannel channel = event.getChannel();
		Message message = event.getMessage(), previousMessage = GuildMessageCache.INSTANCE.getMessageById(message.getIdLong());
		
		Document data = Database.get().getGuildById(guild.getIdLong(), null, Projections.include("logger")).get("logger", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false) || data.getLong("channelId") == null) {
			return;
		}

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		if(message.getMember() != null) {
			Member member = event.getMember();
			
			embed.setDescription(String.format("`%s` edited their [message](%s) in %s", member.getEffectiveName(), message.getJumpUrl(), channel.getAsMention()));
			embed.setAuthor(new EmbedAuthor(member.getUser().getAsTag(), member.getUser().getEffectiveAvatarUrl(), null));
		}else{
			User user = event.getAuthor();
			
			embed.setDescription(String.format("`%s` edited their [message](%s) in %s", user.getName(), message.getJumpUrl(), channel.getAsMention()));
			embed.setAuthor(new EmbedAuthor(user.getAsTag(), user.getEffectiveAvatarUrl(), null));
		}
		
		embed.setColor(COLOR_ORANGE);
		embed.setTimestamp(ZonedDateTime.now());
		embed.setFooter(new EmbedFooter(String.format("Message ID: %s", message.getId()), null));		
		
		if(previousMessage != null && previousMessage.getContentRaw().length() > 0) {
			embed.addField(new EmbedField(false, "Before", Utils.limitField(previousMessage.getContentRaw())));
		}
		
		if(message.getContentRaw().length() > 0) {
			embed.addField(new EmbedField(false, "After", Utils.limitField(message.getContentRaw())));
		}
		
		this.send(event.getJDA(), guild, data, embed.build());
	}
	
	public void onGuildMessageDelete(GuildMessageDeleteEvent event) {
		Guild guild = event.getGuild();
		TextChannel channel = event.getChannel();
		
		Document data = Database.get().getGuildById(guild.getIdLong(), null, Projections.include("logger")).get("logger", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false) || data.getLong("channelId") == null) {
			return;
		}

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setColor(COLOR_RED);
		embed.setTimestamp(ZonedDateTime.now());
		embed.setFooter(new EmbedFooter(String.format("Message ID: %s", event.getMessageId()), null));
		
		Message message = GuildMessageCache.INSTANCE.getMessageById(event.getMessageIdLong());
		if(message != null) {
			if(message.getContentRaw().length() == 0 && message.getAttachments().isEmpty()) {
				return;
			}
			
			if(message.getMember() != null) {
				Member member = message.getMember();
				
				embed.setDescription(String.format("The message sent by `%s` in %s was deleted", member.getEffectiveName(), channel.getAsMention()));
				embed.setAuthor(new EmbedAuthor(member.getUser().getAsTag(), member.getUser().getEffectiveAvatarUrl(), null));
			}else{
				User user = message.getAuthor();
				
				embed.setDescription(String.format("The message sent by `%s` in %s was deleted", user.getName(), channel.getAsMention()));
				embed.setAuthor(new EmbedAuthor(user.getAsTag(), user.getEffectiveAvatarUrl(), null));
			}
			
			if (message.getContentRaw().length() != 0) {
				embed.addField(new EmbedField(false, "Message", Utils.limitField(message.getContentRaw())));
			}
			
			this.send(event.getJDA(), guild, data, embed.build());
		}else{
			embed.setDescription(String.format("A message sent in %s was deleted", channel.getAsMention()));
			embed.setAuthor(new EmbedAuthor(guild.getName(), guild.getIconUrl(), null));
			
			this.send(event.getJDA(), guild, data, embed.build());
		}
	}
	
	public void onChannelDelete(GuildChannel channel) {
		Guild guild = channel.getGuild();
		
		Document data = Database.get().getGuildById(guild.getIdLong(), null, Projections.include("logger")).get("logger", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false) || data.getLong("channelId") == null) {
			return;
		}

		String type = Utils.getChannelTypeReadable(channel);
		if(type == null) {
			return;
		}
		
		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("The %s `%s` has just been deleted", type, channel.getName()));
		embed.setColor(COLOR_RED);
		embed.setTimestamp(ZonedDateTime.now());
		embed.setAuthor(new EmbedAuthor(guild.getName(), guild.getIconUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("%s ID: %s", channel.getType().equals(ChannelType.CATEGORY) ? "Category" : "Channel", channel.getId()), null));
		
		if(guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			guild.retrieveAuditLogs().type(ActionType.CHANNEL_DELETE).limit(100).queueAfter(AUDIT_LOG_DELAY, TimeUnit.MILLISECONDS, logs -> {
				AuditLogEntry entry = logs.stream()
					.filter(e -> e.getTargetIdLong() == channel.getIdLong())
					.findFirst()
					.orElse(null);
				
				if(entry != null) {
					Statistics.increaseSuccessfulAuditLogs();
					
					embed.setDescription(String.format("The %s `%s` has just been deleted by **%s**", type, channel.getName(), entry.getUser().getAsTag()));
				}else{
					Statistics.increaseFailedAuditLogs();
					
					System.err.println(String.format("[onChannelDelete] Could not find audit log for %s (%s) %s (%s)", guild.getName(), guild.getId(), channel.getName(), channel.getId()));
				}
				
				this.send(channel.getJDA(), guild, data, embed.build());
			});
		}else{
			this.send(channel.getJDA(), guild, data, embed.build());
		}
	}
	
	public void onTextChannelDelete(TextChannelDeleteEvent event) {
		onChannelDelete(event.getChannel());
	}
	
	public void onVoiceChannelDelete(VoiceChannelDeleteEvent event) {
		onChannelDelete(event.getChannel());
	}
	
	public void onCategoryDelete(CategoryDeleteEvent event) {
		onChannelDelete(event.getCategory());
	}
	
	public void onChannelCreate(GuildChannel channel) {
		Guild guild = channel.getGuild();
		
		Document data = Database.get().getGuildById(guild.getIdLong(), null, Projections.include("logger")).get("logger", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false) || data.getLong("channelId") == null) {
			return;
		}

		String type = Utils.getChannelTypeReadable(channel);
		if(type == null) {
			return;
		}
		
		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("The %s %s has just been created", type, channel.getType().equals(ChannelType.TEXT) ? ((TextChannel) channel).getAsMention() : "`" + channel.getName() + "`"));
		embed.setColor(COLOR_GREEN);
		embed.setTimestamp(ZonedDateTime.now());
		embed.setAuthor(new EmbedAuthor(guild.getName(), guild.getIconUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("%s ID: %s", channel.getType().equals(ChannelType.CATEGORY) ? "Category" : "Channel", channel.getId()), null));
		
		if(guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			guild.retrieveAuditLogs().type(ActionType.CHANNEL_CREATE).limit(100).queueAfter(AUDIT_LOG_DELAY, TimeUnit.MILLISECONDS, logs -> {
				AuditLogEntry entry = logs.stream()
					.filter(e -> e.getTargetIdLong() == channel.getIdLong())
					.findFirst()
					.orElse(null);
				
				if(entry != null) {
					Statistics.increaseSuccessfulAuditLogs();
					
					embed.setDescription(String.format("The %s %s has just been created by **%s**", type, channel.getType().equals(ChannelType.TEXT) ? ((TextChannel) channel).getAsMention() : "`" + channel.getName() + "`", entry.getUser().getAsTag()));
				}else{
					Statistics.increaseFailedAuditLogs();
					
					System.err.println(String.format("[onChannelCreate] Could not find audit log for %s (%s) %s (%s)", guild.getName(), guild.getId(), channel.getName(), channel.getId()));
				}
				
				this.send(channel.getJDA(), guild, data, embed.build());
			});
		}else{
			this.send(channel.getJDA(), guild, data, embed.build());
		}
	}
	
	public void onTextChannelCreate(TextChannelCreateEvent event) {
		onChannelCreate(event.getChannel());
	}
	
	public void onVoiceChannelCreate(VoiceChannelCreateEvent event) {
		onChannelCreate(event.getChannel());
	}
	
	public void onCategoryCreate(CategoryCreateEvent event) {
		onChannelCreate(event.getCategory());
	}
	
	public void onChannelUpdateName(GuildChannel channel, String previous, String current) {
		Guild guild = channel.getGuild();
		
		Document data = Database.get().getGuildById(guild.getIdLong(), null, Projections.include("logger")).get("logger", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false) || data.getLong("channelId") == null) {
			return;
		}

		String type = Utils.getChannelTypeReadable(channel);
		if(type == null) {
			return;
		}
		
		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("The %s **%s** has been renamed", type, channel.getType().equals(ChannelType.TEXT) ? ((TextChannel) channel).getAsMention() : "`" + channel.getName() + "`"));
		embed.setColor(COLOR_ORANGE);
		embed.setTimestamp(ZonedDateTime.now());
		embed.setAuthor(new EmbedAuthor(guild.getName(), guild.getIconUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("%s ID: %s", channel.getType().equals(ChannelType.CATEGORY) ? "Category" : "Channel", channel.getId()), null));
		
		embed.addField(new EmbedField(false, "Before", String.format("`%s`", previous)));
		embed.addField(new EmbedField(false, "After", String.format("`%s`", current)));
		
		if(guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			guild.retrieveAuditLogs().type(ActionType.CHANNEL_UPDATE).limit(100).queueAfter(AUDIT_LOG_DELAY, TimeUnit.MILLISECONDS, logs -> {
				AuditLogEntry entry = logs.stream()
					.filter(e -> e.getTargetIdLong() == channel.getIdLong())
					.filter(e -> e.getChangeByKey(AuditLogKey.CHANNEL_NAME) != null)
					.findFirst()
					.orElse(null);
				
				if(entry != null) {
					Statistics.increaseSuccessfulAuditLogs();
					
					embed.setDescription(String.format("The %s **%s** has been renamed by **%s**", type, channel.getType().equals(ChannelType.TEXT) ? ((TextChannel) channel).getAsMention() : "`" + channel.getName() + "`", entry.getUser().getAsTag()));
				}else{
					Statistics.increaseFailedAuditLogs();
					
					System.err.println(String.format("[onChannelUpdateName] Could not find audit log for %s (%s) %s (%s)", guild.getName(), guild.getId(), channel.getName(), channel.getId()));
				}
				
				this.send(channel.getJDA(), guild, data, embed.build());
			});
		}else{
			this.send(channel.getJDA(), guild, data, embed.build());
		}
	}
	
	public void onTextChannelUpdateName(TextChannelUpdateNameEvent event) {
		onChannelUpdateName(event.getEntity(), event.getOldName(), event.getNewName());
	}
	
	public void onVoiceChannelUpdateName(VoiceChannelUpdateNameEvent event) {
		onChannelUpdateName(event.getEntity(), event.getOldName(), event.getNewName());
	}
	
	public void onCategoryUpdateName(CategoryUpdateNameEvent event) {
		onChannelUpdateName(event.getEntity(), event.getOldName(), event.getNewName());
	}
	
	public void onRoleCreate(RoleCreateEvent event) {
		Guild guild = event.getGuild();
		Role role = event.getRole();
		
		Document data = Database.get().getGuildById(guild.getIdLong(), null, Projections.include("logger")).get("logger", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false) || data.getLong("channelId") == null) {
			return;
		}

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("The role %s has been created", role.getAsMention()));
		embed.setColor(COLOR_GREEN);
		embed.setTimestamp(ZonedDateTime.now());
		embed.setAuthor(new EmbedAuthor(guild.getName(), guild.getIconUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("Role ID: %s", role.getId()), null));
		
		if(!role.isManaged() && guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			guild.retrieveAuditLogs().type(ActionType.ROLE_CREATE).limit(100).queueAfter(AUDIT_LOG_DELAY, TimeUnit.MILLISECONDS, logs -> {
				AuditLogEntry entry = logs.stream()
					.filter(e -> e.getTargetIdLong() == event.getRole().getIdLong())
					.findFirst()
					.orElse(null);
				
				if(entry != null) {
					Statistics.increaseSuccessfulAuditLogs();
					
					embed.setDescription(String.format("The role %s has been created by **%s**", role.getAsMention(), entry.getUser().getAsTag()));
				}else{
					Statistics.increaseFailedAuditLogs();
					
					System.err.println(String.format("[onRoleCreate] Could not find audit log for %s (%s) %s (%s)", guild.getName(), guild.getId(), role.getName(), role.getId()));
				}
				
				this.send(event.getJDA(), guild, data, embed.build());
			});
		}else{
			this.send(event.getJDA(), guild, data, embed.build());
		}
	}
	
	public void onRoleDelete(RoleDeleteEvent event) {
		Guild guild = event.getGuild();
		Role role = event.getRole();
		
		Document data = Database.get().getGuildById(guild.getIdLong(), null, Projections.include("logger")).get("logger", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false) || data.getLong("channelId") == null) {
			return;
		}

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("The role `%s` has been deleted", role.getName()));
		embed.setColor(COLOR_RED);
		embed.setTimestamp(ZonedDateTime.now());
		embed.setAuthor(new EmbedAuthor(guild.getName(), guild.getIconUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("Role ID: %s", role.getId()), null));
		
		if(!role.isManaged() && guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			guild.retrieveAuditLogs().type(ActionType.ROLE_DELETE).limit(100).queueAfter(AUDIT_LOG_DELAY, TimeUnit.MILLISECONDS, logs -> {
				AuditLogEntry entry = logs.stream()
					.filter(e -> e.getTargetIdLong() == event.getRole().getIdLong())
					.findFirst()
					.orElse(null);
				
				if(entry != null) {
					Statistics.increaseSuccessfulAuditLogs();
					
					embed.setDescription(String.format("The role `%s` has been deleted by **%s**", role.getName(), entry.getUser().getAsTag()));
				}else{
					Statistics.increaseFailedAuditLogs();
					
					System.err.println(String.format("[onRoleCreate] Could not find audit log for %s (%s) %s (%s)", guild.getName(), guild.getId(), role.getName(), role.getId()));
				}
				
				this.send(event.getJDA(), guild, data, embed.build());
			});
		}else{
			this.send(event.getJDA(), guild, data, embed.build());
		}
	}
	
	public void onRoleUpdateName(RoleUpdateNameEvent event) {
		Guild guild = event.getGuild();
		Role role = event.getRole();
		
		Document data = Database.get().getGuildById(guild.getIdLong(), null, Projections.include("logger")).get("logger", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false) || data.getLong("channelId") == null) {
			return;
		}

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("The role %s has been renamed", role.getAsMention()));
		embed.setColor(COLOR_ORANGE);
		embed.setTimestamp(ZonedDateTime.now());
		embed.setAuthor(new EmbedAuthor(guild.getName(), guild.getIconUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("Role ID: %s", role.getId()), null));
		
		embed.addField(new EmbedField(false, "Before", String.format("`%s`", event.getOldName())));
		embed.addField(new EmbedField(false, "After", String.format("`%s`", event.getNewName())));
		
		if(guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			guild.retrieveAuditLogs().type(ActionType.ROLE_UPDATE).limit(100).queueAfter(AUDIT_LOG_DELAY, TimeUnit.MILLISECONDS, logs -> {
				AuditLogEntry entry = logs.stream()
					.filter(e -> e.getTargetIdLong() == event.getRole().getIdLong())
					.filter(e -> e.getChangeByKey(AuditLogKey.ROLE_NAME) != null)
					.findFirst()
					.orElse(null);
				
				if(entry != null) {
					Statistics.increaseSuccessfulAuditLogs();
					
					embed.setDescription(String.format("The role %s has been renamed by **%s**", role.getAsMention(), entry.getUser().getAsTag()));
				}else{
					Statistics.increaseFailedAuditLogs();
					
					System.err.println(String.format("[onRoleUpdateName] Could not find audit log for %s (%s) %s (%s)", guild.getName(), guild.getId(), role.getName(), role.getId()));
				}
				
				this.send(event.getJDA(), guild, data, embed.build());
			});
		}else{
			this.send(event.getJDA(), guild, data, embed.build());
		}
	}
	
	private String getPermissionDifference(long permissionsBefore, long permissionsAfter) {
		StringBuilder builder = new StringBuilder();
		
		long permissions = permissionsBefore ^ permissionsAfter;
		
		EnumSet<Permission> permissionsAdded = Permission.getPermissions(permissionsAfter & permissions);
		EnumSet<Permission> permissionsRemoved = Permission.getPermissions(permissionsBefore & permissions);
		
		if(permissionsAdded.size() + permissionsRemoved.size() != 0) {
			builder.append("\n```diff");
			
			for(Permission permissionAdded : permissionsAdded) {
				builder.append("\n+ " + permissionAdded.getName());
			}
			
			for(Permission permissionRemoved : permissionsRemoved) {
				builder.append("\n- " + permissionRemoved.getName());
			}
			
			builder.append("```");
		}
		
		return builder.toString();
	}
	
	public void onRoleUpdatePermissions(RoleUpdatePermissionsEvent event) {
		Guild guild = event.getGuild();
		Role role = event.getRole();
		
		Document data = Database.get().getGuildById(guild.getIdLong(), null, Projections.include("logger")).get("logger", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false) || data.getLong("channelId") == null) {
			return;
		}

		String message = this.getPermissionDifference(event.getOldPermissionsRaw(), event.getNewPermissionsRaw());
		if(message.length() > 0) {
			StringBuilder embedDescription = new StringBuilder();
			embedDescription.append(String.format("The role %s has had permission changes made", role.getAsMention()));
			
			WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
			embed.setColor(COLOR_ORANGE);
			embed.setTimestamp(ZonedDateTime.now());
			embed.setAuthor(new EmbedAuthor(guild.getName(), guild.getIconUrl(), null));
			embed.setFooter(new EmbedFooter(String.format("Role ID: %s", role.getId()), null));
			
			if(guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
				guild.retrieveAuditLogs().type(ActionType.ROLE_UPDATE).limit(100).queueAfter(AUDIT_LOG_DELAY, TimeUnit.MILLISECONDS, logs -> {
					AuditLogEntry entry = logs.stream()
						.filter(e -> e.getTargetIdLong() == event.getRole().getIdLong())
						.filter(e -> e.getChangeByKey(AuditLogKey.ROLE_PERMISSIONS) != null)
						.findFirst()
						.orElse(null);
					
					if(entry != null) {
						Statistics.increaseSuccessfulAuditLogs();
						
						embedDescription.append(String.format(" by **%s**", entry.getUser().getAsTag()));
					}else{
						Statistics.increaseFailedAuditLogs();
						
						System.err.println(String.format("[onRoleUpdatePermissions] Could not find audit log for %s (%s) %s (%s)", guild.getName(), guild.getId(), role.getName(), role.getId()));
					}
					
					embedDescription.append(message);
					
					embed.setDescription(embedDescription.toString());
					
					this.send(event.getJDA(), guild, data, embed.build());
				});
			}else{
				embedDescription.append(message);
				
				embed.setDescription(embedDescription.toString());
				
				this.send(event.getJDA(), guild, data, embed.build());
			}
		}
	}
	
	public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent event) {
		Guild guild = event.getGuild();
		Member member = event.getMember();
		
		List<Role> roles = event.getRoles();
		Role firstRole = roles.get(0);
		
		Document data = Database.get().getGuildById(guild.getIdLong(), null, Projections.include("logger")).get("logger", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false) || data.getLong("channelId") == null) {
			return;
		}

		StringBuilder embedDescription = new StringBuilder();
		
		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setColor(COLOR_GREEN);
		embed.setTimestamp(ZonedDateTime.now());
		embed.setAuthor(new EmbedAuthor(member.getUser().getAsTag(), member.getUser().getEffectiveAvatarUrl(), null));
		
		if(roles.size() > 1) {
			StringBuilder builder = new StringBuilder();
			
			/* Make sure there is always enough space to write all the components to it */
			int maxLength = MessageEmbed.TEXT_MAX_LENGTH 
				- 32 /* Max nickname length */ 
				- 32 /* Result String overhead */ 
				- 16 /* " and x more" overhead */
				- 3 /* Max length of x */;
			
			for(int i = 0; i < roles.size(); i++) {
				Role role = roles.get(i);
				
				String entry = (i == roles.size() - 1 ? " and " : i != 0 ? ", " : "") + role.getAsMention();
				if(builder.length() + entry.length() < maxLength) {
					builder.append(entry);
				}else{
					builder.append(String.format(" and **%s** more", roles.size() - i));
					
					break;
				}
			}
			
			embedDescription.append(String.format("The roles %s have been added to `%s`", builder.toString(), member.getEffectiveName()));
		}else{
			embedDescription.append(String.format("The role %s has been added to `%s`", firstRole.getAsMention(), member.getEffectiveName()));
			embed.setFooter(new EmbedFooter(String.format("Role ID: %s", firstRole.getId()), null));
		}
		
		if(!firstRole.isManaged() && guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			guild.retrieveAuditLogs().type(ActionType.MEMBER_ROLE_UPDATE).limit(100).queueAfter(AUDIT_LOG_DELAY, TimeUnit.MILLISECONDS, logs -> {
				AuditLogEntry entry = logs.stream()
					.filter(e -> e.getTargetIdLong() == member.getUser().getIdLong())
					.filter(e -> e.getChangeByKey(AuditLogKey.MEMBER_ROLES_ADD) != null)
					.filter(e -> {
						List<Map<String, String>> roleEntries = e.getChangeByKey(AuditLogKey.MEMBER_ROLES_ADD).getNewValue();
						List<String> roleIds = roleEntries.stream().map(roleEntry -> roleEntry.get("id")).collect(Collectors.toList());
						
						for(Role role : roles) {
							if(!roleIds.contains(role.getId())) {
								return false;
							}
						}
						
						return true;
					})
					.findFirst()
					.orElse(null);
				
				if(entry != null) {
					Statistics.increaseSuccessfulAuditLogs();
					
					embedDescription.append(String.format(" by **%s**", entry.getUser().getAsTag()));
				}else{
					Statistics.increaseFailedAuditLogs();
					
					System.err.println(String.format("[onGuildMemberRoleAdd] Could not find audit log for %s (%s) (%s) (%s)", guild.getName(), guild.getId(), member, roles));
				}
				
				embed.setDescription(embedDescription.toString());
				
				this.send(event.getJDA(), guild, data, embed.build());
			});
		}else{
			embed.setDescription(embedDescription.toString());
			
			this.send(event.getJDA(), guild, data, embed.build());
		}
	}
	
	public void onGuildMemberRoleRemove(GuildMemberRoleRemoveEvent event) {		
		Guild guild = event.getGuild();
		Member member = event.getMember();
		
		List<Role> roles = event.getRoles();
		Role firstRole = roles.get(0);
		
		Document data = Database.get().getGuildById(guild.getIdLong(), null, Projections.include("logger")).get("logger", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false) || data.getLong("channelId") == null) {
			return;
		}

		/* Wait AUDIT_LOG_DELAY milliseconds to ensure that the role-deletion event has come through */
		new EmptyRestAction<Void>(event.getJDA()).queueAfter(AUDIT_LOG_DELAY, TimeUnit.MILLISECONDS, ($) -> {
			StringBuilder embedDescription = new StringBuilder();
			
			WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
			embed.setColor(COLOR_RED);
			embed.setTimestamp(ZonedDateTime.now());
			embed.setAuthor(new EmbedAuthor(member.getUser().getAsTag(), member.getUser().getEffectiveAvatarUrl(), null));
			
			if(roles.size() == 1 && guild.getRoleById(firstRole.getIdLong()) == null) {
				embed.setDescription(String.format("The role `%s` has been removed from `%s` by **role deletion**", firstRole.getName(), member.getEffectiveName()));
				embed.setFooter(new EmbedFooter(String.format("Role ID: %s", firstRole.getId()), null));
				
				this.send(event.getJDA(), guild, data, embed.build());
			}else{
				if(roles.size() > 1) {
					StringBuilder builder = new StringBuilder();
					
					/* Make sure there is always enough space to write all the components to it */
					int maxLength = MessageEmbed.TEXT_MAX_LENGTH 
						- 32 /* Max nickname length */ 
						- 36 /* Result String overhead */ 
						- 16 /* " and x more" overhead */
						- 3 /* Max length of x */;
					
					for(int i = 0; i < roles.size(); i++) {
						Role role = roles.get(i);
						
						String entry = (i == roles.size() - 1 ? " and " : i != 0 ? ", " : "") + role.getAsMention();
						if(builder.length() + entry.length() < maxLength) {
							builder.append(entry);
						}else{
							builder.append(String.format(" and **%s** more", roles.size() - i));
							
							break;
						}
					}
					
					embedDescription.append(String.format("The roles %s have been removed from `%s`", builder, member.getEffectiveName()));
				}else{
					embedDescription.append(String.format("The role %s has been removed from `%s`", firstRole.getAsMention(), member.getEffectiveName()));
					embed.setFooter(new EmbedFooter(String.format("Role ID: %s", firstRole.getId()), null));
				}
				
				if(!firstRole.isManaged() && guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
					guild.retrieveAuditLogs().type(ActionType.MEMBER_ROLE_UPDATE).limit(100).queue(logs -> {
						AuditLogEntry entry = logs.stream()
							.filter(e -> e.getTargetIdLong() == member.getUser().getIdLong())
							.filter(e -> e.getChangeByKey(AuditLogKey.MEMBER_ROLES_REMOVE) != null)
							.filter(e -> {
								List<Map<String, String>> roleEntries = e.getChangeByKey(AuditLogKey.MEMBER_ROLES_REMOVE).getNewValue();
								List<String> roleIds = roleEntries.stream().map(roleEntry -> roleEntry.get("id")).collect(Collectors.toList());
								
								for(Role role : roles) {
									if(!roleIds.contains(role.getId())) {
										return false;
									}
								}
								
								return true;
							})
							.findFirst()
							.orElse(null);
						
						if(entry != null) {
							Statistics.increaseSuccessfulAuditLogs();
							
							embedDescription.append(String.format(" by **%s**", entry.getUser().getAsTag()));
						}else{
							Statistics.increaseFailedAuditLogs();
							
							System.err.println(String.format("[onGuildMemberRoleRemove] Could not find audit log for %s (%s) (%s) (%s)", guild.getName(), guild.getId(), member, roles));
						}
						
						embed.setDescription(embedDescription.toString());
						
						this.send(event.getJDA(), guild, data, embed.build());
					});
				}else{
					embed.setDescription(embedDescription.toString());
					
					this.send(event.getJDA(), guild, data, embed.build());
				}
			}
		});
	}
	
	public void onGuildMemberUpdateNickname(GuildMemberUpdateNicknameEvent event) {
		Guild guild = event.getGuild();
		Member member = event.getMember();
		
		Document data = Database.get().getGuildById(guild.getIdLong(), null, Projections.include("logger")).get("logger", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false) || data.getLong("channelId") == null) {
			return;
		}

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("`%s` has had their nickname changed", member.getEffectiveName()));
		embed.setColor(COLOR_ORANGE);
		embed.setTimestamp(ZonedDateTime.now());
		embed.setAuthor(new EmbedAuthor(member.getUser().getAsTag(), member.getUser().getEffectiveAvatarUrl(), null));
		
		embed.addField(new EmbedField(false, "Before", String.format("`%s`", event.getOldNickname() != null ? event.getOldNickname() : member.getUser().getName())));
		embed.addField(new EmbedField(false, "After", String.format("`%s`", event.getNewNickname() != null ? event.getNewNickname() : member.getUser().getName())));
		
		if(guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			guild.retrieveAuditLogs().type(ActionType.MEMBER_UPDATE).limit(100).queueAfter(AUDIT_LOG_DELAY, TimeUnit.MILLISECONDS, logs -> {
				AuditLogEntry entry = logs.stream()
					.filter(e -> e.getTargetIdLong() == member.getUser().getIdLong())
					.filter(e -> e.getChangeByKey(AuditLogKey.MEMBER_NICK) != null)
					.findFirst()
					.orElse(null);
				
				if(entry != null) {
					Statistics.increaseSuccessfulAuditLogs();
					
					embed.setDescription(String.format("`%s` has had their nickname changed by **%s**", member.getEffectiveName(), entry.getUser().getAsTag()));
				}else{
					Statistics.increaseFailedAuditLogs();
					
					System.err.println(String.format("[onGuildMemberNickChange] Could not find audit log for %s (%s) %s (%s)", guild.getName(), guild.getId(), member.getUser().getName(), member.getUser().getId()));
				}
				
				this.send(event.getJDA(), guild, data, embed.build());
			});
		}else{
			this.send(event.getJDA(), guild, data, embed.build());
		}
	}
	
	public void onGuildVoiceGuildMute(GuildVoiceGuildMuteEvent event) {
		Guild guild = event.getGuild();
		Member member = event.getMember();
		
		Document data = Database.get().getGuildById(guild.getIdLong(), null, Projections.include("logger")).get("logger", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false) || data.getLong("channelId") == null) {
			return;
		}

		boolean muted = event.getVoiceState().isGuildMuted();
		
		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("`%s` has been %s", member.getEffectiveName(), muted ? "muted" : "unmuted"));
		embed.setTimestamp(ZonedDateTime.now());
		embed.setAuthor(new EmbedAuthor(member.getUser().getAsTag(), member.getUser().getEffectiveAvatarUrl(), null));
		
		if(muted) {
			embed.setColor(COLOR_RED);
		}else{
			embed.setColor(COLOR_GREEN);
		}
		
		if(guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			guild.retrieveAuditLogs().type(ActionType.MEMBER_UPDATE).limit(100).queueAfter(AUDIT_LOG_DELAY, TimeUnit.MILLISECONDS, logs -> {
				AuditLogEntry entry = logs.stream()
					.filter(e -> e.getTargetIdLong() == member.getUser().getIdLong())
					.filter(e -> e.getChangeByKey(AuditLogKey.MEMBER_MUTE) != null)
					.findFirst()
					.orElse(null);
				
				if(entry != null) {
					Statistics.increaseSuccessfulAuditLogs();
					
					embed.setDescription(String.format("`%s` has been %s by **%s**", member.getEffectiveName(), muted ? "muted" : "unmuted", entry.getUser().getAsTag()));
				}else{
					Statistics.increaseFailedAuditLogs();
					
					System.err.println(String.format("[onGuildVoiceGuildMute] Could not find audit log for %s (%s) %s (%s)", guild.getName(), guild.getId(), member.getUser().getAsTag(), member.getUser().getId()));
				}
				
				this.send(event.getJDA(), guild, data, embed.build());
			});
		}else{
			this.send(event.getJDA(), guild, data, embed.build());
		}
	}
	
	public void onGuildVoiceGuildDeafen(GuildVoiceGuildDeafenEvent event) {
		Guild guild = event.getGuild();
		Member member = event.getMember();
		
		Document data = Database.get().getGuildById(guild.getIdLong(), null, Projections.include("logger")).get("logger", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false) || data.getLong("channelId") == null) {
			return;
		}

		boolean deafened = event.getVoiceState().isGuildDeafened();
		
		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("`%s` has been %s", member.getEffectiveName(), deafened ? "deafened" : "undefeaned"));		
		embed.setTimestamp(ZonedDateTime.now());
		embed.setAuthor(new EmbedAuthor(member.getUser().getAsTag(), member.getUser().getEffectiveAvatarUrl(), null));
		
		if(deafened) {
			embed.setColor(COLOR_RED);
		}else{
			embed.setColor(COLOR_GREEN);
		}
		
		if(guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			guild.retrieveAuditLogs().type(ActionType.MEMBER_UPDATE).limit(100).queueAfter(AUDIT_LOG_DELAY, TimeUnit.MILLISECONDS, logs -> {
				AuditLogEntry entry = logs.stream()
					.filter(e -> e.getTargetIdLong() == member.getUser().getIdLong())
					.filter(e -> e.getChangeByKey(AuditLogKey.MEMBER_DEAF) != null)
					.findFirst()
					.orElse(null);
				
				if(entry != null) {
					Statistics.increaseSuccessfulAuditLogs();
					
					embed.setDescription(String.format("`%s` has been %s by **%s**", member.getEffectiveName(), deafened ? "deafened" : "undefeaned", entry.getUser().getAsTag()));
				}else{
					Statistics.increaseFailedAuditLogs();
					
					System.err.println(String.format("[onGuildVoiceGuildDeafen] Could not find audit log for %s (%s) %s (%s)", guild.getName(), guild.getId(), member.getUser().getAsTag(), member.getUser().getId()));
				}
				
				this.send(event.getJDA(), guild, data, embed.build());
			});
		}else{
			this.send(event.getJDA(), guild, data, embed.build());
		}
	}
	
	public void onGuildVoiceJoin(GuildVoiceJoinEvent event) {
		Guild guild = event.getGuild();
		Member member = event.getMember();
		VoiceChannel channel = event.getChannelJoined();
		
		Document data = Database.get().getGuildById(guild.getIdLong(), null, Projections.include("logger")).get("logger", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false) || data.getLong("channelId") == null) {
			return;
		}

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("`%s` just joined the voice channel `%s`", member.getEffectiveName(), channel.getName()));
		embed.setColor(COLOR_GREEN);
		embed.setTimestamp(ZonedDateTime.now());
		embed.setAuthor(new EmbedAuthor(member.getUser().getAsTag(), member.getUser().getEffectiveAvatarUrl(), null));
		
		this.send(event.getJDA(), guild, data, embed.build());
	}
	
	public void onGuildVoiceLeave(GuildVoiceLeaveEvent event) {
		Guild guild = event.getGuild();
		Member member = event.getMember();
		VoiceChannel channel = event.getChannelLeft();
		
		Document data = Database.get().getGuildById(guild.getIdLong(), null, Projections.include("logger")).get("logger", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false) || data.getLong("channelId") == null) {
			return;
		}

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("`%s` just left the voice channel `%s`", member.getEffectiveName(), channel.getName()));
		embed.setColor(COLOR_RED);
		embed.setTimestamp(ZonedDateTime.now());
		embed.setAuthor(new EmbedAuthor(member.getUser().getAsTag(), member.getUser().getEffectiveAvatarUrl(), null));
		
		this.send(event.getJDA(), guild, data, embed.build());
	}
	
	public void onGuildVoiceMove(GuildVoiceMoveEvent event) {
		Guild guild = event.getGuild();
		Member member = event.getMember();
		
		VoiceChannel left = event.getChannelLeft(), joined = event.getChannelJoined();
		
		Document data = Database.get().getGuildById(guild.getIdLong(), null, Projections.include("logger")).get("logger", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false) || data.getLong("channelId") == null) {
			return;
		}

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("`%s` just changed voice channel", member.getEffectiveName()));
		embed.setColor(COLOR_ORANGE);
		embed.setTimestamp(ZonedDateTime.now());
		embed.setAuthor(new EmbedAuthor(member.getUser().getAsTag(), member.getUser().getEffectiveAvatarUrl(), null));
		
		embed.addField(new EmbedField(false, "Before", String.format("`%s`", left.getName())));
		embed.addField(new EmbedField(false, "After", String.format("`%s`", joined.getName())));
		
		this.send(event.getJDA(), guild, data, embed.build());
	}
}