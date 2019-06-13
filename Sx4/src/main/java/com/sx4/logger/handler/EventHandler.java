package com.sx4.logger.handler;

import static com.rethinkdb.RethinkDB.r;

import java.awt.Color;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
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

import com.rethinkdb.net.Connection;
import com.sx4.cache.GuildMessageCache;
import com.sx4.core.Sx4Bot;
import com.sx4.logger.Statistics;
import com.sx4.logger.util.Utils;
import com.sx4.settings.Settings;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.audit.ActionType;
import net.dv8tion.jda.core.audit.AuditLogEntry;
import net.dv8tion.jda.core.audit.AuditLogKey;
import net.dv8tion.jda.core.entities.Channel;
import net.dv8tion.jda.core.entities.ChannelType;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.entities.Role;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.entities.VoiceChannel;
import net.dv8tion.jda.core.entities.Webhook;
import net.dv8tion.jda.core.events.channel.category.CategoryCreateEvent;
import net.dv8tion.jda.core.events.channel.category.CategoryDeleteEvent;
import net.dv8tion.jda.core.events.channel.category.update.CategoryUpdateNameEvent;
import net.dv8tion.jda.core.events.channel.text.TextChannelCreateEvent;
import net.dv8tion.jda.core.events.channel.text.TextChannelDeleteEvent;
import net.dv8tion.jda.core.events.channel.text.update.TextChannelUpdateNameEvent;
import net.dv8tion.jda.core.events.channel.voice.VoiceChannelCreateEvent;
import net.dv8tion.jda.core.events.channel.voice.VoiceChannelDeleteEvent;
import net.dv8tion.jda.core.events.channel.voice.update.VoiceChannelUpdateNameEvent;
import net.dv8tion.jda.core.events.guild.GuildBanEvent;
import net.dv8tion.jda.core.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.core.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberLeaveEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberNickChangeEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.core.events.guild.voice.GuildVoiceGuildDeafenEvent;
import net.dv8tion.jda.core.events.guild.voice.GuildVoiceGuildMuteEvent;
import net.dv8tion.jda.core.events.guild.voice.GuildVoiceJoinEvent;
import net.dv8tion.jda.core.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.core.events.guild.voice.GuildVoiceMoveEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageDeleteEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.core.events.role.RoleCreateEvent;
import net.dv8tion.jda.core.events.role.RoleDeleteEvent;
import net.dv8tion.jda.core.events.role.update.RoleUpdateNameEvent;
import net.dv8tion.jda.core.events.role.update.RoleUpdatePermissionsEvent;
import net.dv8tion.jda.core.exceptions.HttpException;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import net.dv8tion.jda.core.requests.restaction.AuditableRestAction.EmptyRestAction;
import net.dv8tion.jda.webhook.WebhookClient;
import net.dv8tion.jda.webhook.WebhookClientBuilder;
import net.dv8tion.jda.webhook.WebhookMessage;
import net.dv8tion.jda.webhook.WebhookMessageBuilder;
import okhttp3.OkHttpClient;;

public class EventHandler extends ListenerAdapter {
	
	private static final Color COLOR_GREEN = Settings.COLOR_GREEN;
	private static final Color COLOR_ORANGE = Settings.COLOR_ORANGE;
	private static final Color COLOR_RED = Settings.COLOR_RED;
	
	/* It can some times get stuck in an infinite loop and these are used to prevent it */
	private static final int MAX_ATTEMPTS = 3;
	private static final int ATTEMPTS_BEFORE_REFETCH = 2;
	
	/* Used to ensure that the audit-log has come through */
	private static final int AUDIT_LOG_DELAY = 500;
	
	public static final Request EMPTY_REQUEST = new Request(null, 0L, null, null);
	
	public static class Request {
		
		public final JDA bot;
		public final long guildId;
		public final Map<String, Object> data;
		public final List<MessageEmbed> embeds;
		
		public Request(JDA bot, long guildId, Map<String, Object> data, List<MessageEmbed> embeds) {
			this.bot = bot;
			this.guildId = guildId;
			this.data = data;
			this.embeds = embeds;
		}
		
		public Guild getGuild() {
			return this.bot.getGuildById(this.guildId);
		}
	}
	
	private Connection connection;
	
	private Map<String, WebhookClient> webhooks = new HashMap<>();
	
	private Map<Long, BlockingDeque<Request>> queue = new HashMap<>();
	
	private ExecutorService executor = Executors.newCachedThreadPool();
	private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
	
	private OkHttpClient client = new OkHttpClient.Builder().build();
	
	public EventHandler(Connection connection) {
		this.connection = connection;
	}
	
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
	
	private void handleRequest(JDA bot, Guild guild, Map<String, Object> data, List<MessageEmbed> requestEmbeds) {
		if(!this.queue.containsKey(guild.getIdLong())) {
			BlockingDeque<Request> blockingDeque = new LinkedBlockingDeque<>();
			this.queue.put(guild.getIdLong(), blockingDeque);
			
			this.executor.submit(() -> {
				try {
					List<MessageEmbed> embeds = new ArrayList<>();
					int length = 0, requests = 0;
					
					Request request;
					while((request = blockingDeque.take()) != EMPTY_REQUEST) {
						int lengthToSend = request.embeds.stream()
							.mapToInt(MessageEmbed::getLength)
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
	
	private void _send(JDA bot, Guild guild, Map<String, Object> data, List<MessageEmbed> embeds, int requestAmount, int attempts) {
		if(attempts >= MAX_ATTEMPTS) {
			Statistics.increaseSkippedLogs();
			
			return;
		}
		
		if(attempts >= ATTEMPTS_BEFORE_REFETCH) {
			data = r.table("logs").get(guild.getId()).run(this.connection);
		}
		
		TextChannel channel = guild.getTextChannelById((String) data.get("channel"));
		if(channel == null) {
			return;
		}
		
		WebhookClient client;
		if(data.get("webhook_id") == null || data.get("webhook_token") == null) {
			Webhook webhook = channel.createWebhook("Sx4 - Logs").complete();
			
			data.put("webhook_id", webhook.getId());
			data.put("webhook_token", webhook.getToken());
			
			r.table("logs")
				.get(guild.getId())
				.update(r.hashMap()
					.with("webhook_id", webhook.getId())
					.with("webhook_token", webhook.getToken()))
				.runNoReply(this.connection);
			
			client = webhook.newClient()
				.setExecutorService(this.scheduledExecutorService)
				.setHttpClient(this.client)
				.build();
			
			this.webhooks.put(client.getId(), client);
		}else{
			String webhookId = (String) data.get("webhook_id");
			String webhookToken = (String) data.get("webhook_token");
			
			client = this.webhooks.computeIfAbsent(webhookId, ($) -> 
				new WebhookClientBuilder(Long.valueOf(webhookId), webhookToken)
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
						data.put("webhook_id", null);
						data.put("wehook_token", null);
						
						/* 
						 * Calling close would close the scheduled executor service we are using,
						 * causing all logs to stop.
						 * 
						 * this.webhooks.remove(client.getId()).close(); 
						 */
						
						this.webhooks.remove(client.getId());
						
						r.table("logs")
							.get(guild.getId())
							.update(r.hashMap()
								.with("webhook_id", null)
								.with("webhook_token", null))
							.runNoReply(this.connection);
						
						this._send(bot, guild, data, embeds, requestAmount, attempts + 1);
						
						return;
					}
				}
			}
			
			System.err.println("[" + LocalDateTime.now().format(Sx4Bot.getTimeFormatter()) + "] [_send]");
			e.printStackTrace();
		}
	}
	
	public void send(JDA bot, Guild guild, Map<String, Object> data, List<MessageEmbed> embeds) {
		this.executor.submit(() -> {
			this.handleRequest(bot, guild, data, embeds);
		});
	}
	
	public void send(JDA bot, Guild guild, Map<String, Object> data, MessageEmbed... embeds) {
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
		
		Map<String, Object> data = r.table("logs").get(guild.getId()).run(this.connection);
		if(data == null || !((boolean) data.getOrDefault("toggle", false)) || data.get("channel") == null) {
			return;
		}
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setDescription(String.format("`%s` just joined the server", member.getEffectiveName()));
		embed.setColor(COLOR_GREEN);
		embed.setTimestamp(ZonedDateTime.now());
		embed.setAuthor(member.getUser().getAsTag(), null, member.getUser().getEffectiveAvatarUrl());
		
		embed.setFooter(String.format("User ID: %s", member.getUser().getId()), null);
		
		this.send(event.getJDA(), guild, data, embed.build());
	}
	
	public void onGuildMemberLeave(GuildMemberLeaveEvent event) {
		Guild guild = event.getGuild();
		Member member = event.getMember();
		
		Map<String, Object> data = r.table("logs").get(guild.getId()).run(this.connection);
		if(data == null || !((boolean) data.getOrDefault("toggle", false)) || data.get("channel") == null) {
			return;
		}
		
		List<MessageEmbed> embeds = new ArrayList<>();
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setDescription(String.format("`%s` just left the server", member.getEffectiveName()));
		embed.setColor(COLOR_RED);
		embed.setTimestamp(ZonedDateTime.now());
		embed.setAuthor(member.getUser().getAsTag(), null, member.getUser().getEffectiveAvatarUrl());
		embed.setFooter(String.format("User ID: %s", member.getUser().getId()), null);
		
		embeds.add(embed.build());
		
		if(guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			guild.getAuditLogs().type(ActionType.KICK).queueAfter(AUDIT_LOG_DELAY, TimeUnit.MILLISECONDS, logs -> {
				AuditLogEntry entry = logs.stream()
					.filter(e -> e.getTargetIdLong() == member.getUser().getIdLong())
					.filter(e -> Duration.between(e.getCreationTime(), ZonedDateTime.now()).toSeconds() < 10)
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
		
		Map<String, Object> data = r.table("logs").get(guild.getId()).run(this.connection);
		if(data == null || !((boolean) data.getOrDefault("toggle", false)) || data.get("channel") == null) {
			return;
		}
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setDescription(String.format("`%s` has been banned", user.getName()));
		embed.setColor(COLOR_RED);
		embed.setTimestamp(ZonedDateTime.now());
		embed.setAuthor(user.getAsTag(), null, user.getEffectiveAvatarUrl());
		embed.setFooter(String.format("User ID: %s", user.getId()), null);
		
		if(guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			guild.getAuditLogs().type(ActionType.BAN).queueAfter(AUDIT_LOG_DELAY, TimeUnit.MILLISECONDS, logs -> {
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
		
		Map<String, Object> data = r.table("logs").get(guild.getId()).run(this.connection);
		if(data == null || !((boolean) data.getOrDefault("toggle", false)) || data.get("channel") == null) {
			return;
		}
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setDescription(String.format("`%s` has been unbanned", user.getName()));
		embed.setColor(COLOR_GREEN);
		embed.setTimestamp(ZonedDateTime.now());
		embed.setAuthor(user.getAsTag(), null, user.getEffectiveAvatarUrl());
		embed.setFooter(String.format("User ID: %s", user.getId()), null);
		
		if(guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			guild.getAuditLogs().type(ActionType.UNBAN).queueAfter(AUDIT_LOG_DELAY, TimeUnit.MILLISECONDS, logs -> {
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
		
		Map<String, Object> data = r.table("logs").get(guild.getId()).run(this.connection);
		if(data == null || !((boolean) data.getOrDefault("toggle", false)) || data.get("channel") == null) {
			return;
		}
		
		EmbedBuilder embed = new EmbedBuilder();
		if(message.getMember() != null) {
			Member member = event.getMember();
			
			embed.setDescription(String.format("`%s` edited their [message](%s) in %s", member.getEffectiveName(), message.getJumpUrl(), channel.getAsMention()));
			embed.setAuthor(member.getUser().getAsTag(), null, member.getUser().getEffectiveAvatarUrl());
		}else{
			User user = event.getAuthor();
			
			embed.setDescription(String.format("`%s` edited their [message](%s) in %s", user.getName(), message.getJumpUrl(), channel.getAsMention()));
			embed.setAuthor(user.getAsTag(), null, user.getEffectiveAvatarUrl());
		}
		
		embed.setColor(COLOR_ORANGE);
		embed.setTimestamp(ZonedDateTime.now());
		embed.setFooter(String.format("Message ID: %s", message.getId()), null);		
		
		if(previousMessage != null && previousMessage.getContentRaw().length() > 0) {
			embed.addField("Before", Utils.limitField(previousMessage.getContentRaw()), false);
		}
		
		if(message.getContentRaw().length() > 0) {
			embed.addField("After", Utils.limitField(message.getContentRaw()), false);
		}
		
		this.send(event.getJDA(), guild, data, embed.build());
	}
	
	public void onGuildMessageDelete(GuildMessageDeleteEvent event) {
		Guild guild = event.getGuild();
		TextChannel channel = event.getChannel();
		
		Map<String, Object> data = r.table("logs").get(guild.getId()).run(this.connection);
		if(data == null || !((boolean) data.getOrDefault("toggle", false)) || data.get("channel") == null) {
			return;
		}
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setColor(COLOR_RED);
		embed.setTimestamp(ZonedDateTime.now());
		embed.setFooter(String.format("Message ID: %s", event.getMessageId()), null);
		
		Message message = GuildMessageCache.INSTANCE.getMessageById(event.getMessageIdLong());
		if(message != null) {
			if(message.getContentRaw().length() == 0) {
				return;
			}
			
			if(message.getMember() != null) {
				Member member = message.getMember();
				
				embed.setDescription(String.format("The message sent by `%s` in %s was deleted", member.getEffectiveName(), channel.getAsMention()));
				embed.setAuthor(member.getUser().getAsTag(), null, member.getUser().getEffectiveAvatarUrl());
			}else{
				User user = message.getAuthor();
				
				embed.setDescription(String.format("The message sent by `%s` in %s was deleted", user.getName(), channel.getAsMention()));
				embed.setAuthor(user.getAsTag(), null, user.getEffectiveAvatarUrl());
			}
			
			embed.addField("Message", Utils.limitField(message.getContentRaw()), false);
			
			this.send(event.getJDA(), guild, data, embed.build());
		}else{
			embed.setDescription(String.format("A message sent in %s was deleted", channel.getAsMention()));
			embed.setAuthor(guild.getName(), null, guild.getIconUrl());
			
			this.send(event.getJDA(), guild, data, embed.build());
		}
	}
	
	public void onChannelDelete(Channel channel) {
		Guild guild = channel.getGuild();
		
		Map<String, Object> data = r.table("logs").get(guild.getId()).run(this.connection);
		if(data == null || !((boolean) data.getOrDefault("toggle", false)) || data.get("channel") == null) {
			return;
		}
		
		String type = Utils.getChannelTypeReadable(channel);
		if(type == null) {
			return;
		}
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setDescription(String.format("The %s `%s` has just been deleted", type, channel.getName()));
		embed.setColor(COLOR_RED);
		embed.setTimestamp(ZonedDateTime.now());
		embed.setAuthor(guild.getName(), null, guild.getIconUrl());
		embed.setFooter(String.format("%s ID: %s", channel.getType().equals(ChannelType.CATEGORY) ? "Category" : "Channel", channel.getId()), null);
		
		if(guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			guild.getAuditLogs().type(ActionType.CHANNEL_DELETE).limit(100).queueAfter(AUDIT_LOG_DELAY, TimeUnit.MILLISECONDS, logs -> {
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
	
	public void onChannelCreate(Channel channel) {
		Guild guild = channel.getGuild();
		
		Map<String, Object> data = r.table("logs").get(guild.getId()).run(this.connection);
		if(data == null || !((boolean) data.getOrDefault("toggle", false)) || data.get("channel") == null) {
			return;
		}
		
		String type = Utils.getChannelTypeReadable(channel);
		if(type == null) {
			return;
		}
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setDescription(String.format("The %s %s has just been created", type, channel.getType().equals(ChannelType.TEXT) ? ((TextChannel) channel).getAsMention() : "`" + channel.getName() + "`"));
		embed.setColor(COLOR_GREEN);
		embed.setTimestamp(ZonedDateTime.now());
		embed.setAuthor(guild.getName(), null, guild.getIconUrl());
		embed.setFooter(String.format("%s ID: %s", channel.getType().equals(ChannelType.CATEGORY) ? "Category" : "Channel", channel.getId()), null);
		
		if(guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			guild.getAuditLogs().type(ActionType.CHANNEL_CREATE).limit(100).queueAfter(AUDIT_LOG_DELAY, TimeUnit.MILLISECONDS, logs -> {
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
	
	public void onChannelUpdateName(Channel channel, String previous, String current) {
		Guild guild = channel.getGuild();
		
		Map<String, Object> data = r.table("logs").get(guild.getId()).run(this.connection);
		if(data == null || !((boolean) data.getOrDefault("toggle", false)) || data.get("channel") == null) {
			return;
		}
		
		String type = Utils.getChannelTypeReadable(channel);
		if(type == null) {
			return;
		}
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setDescription(String.format("The %s **%s** has been renamed", type, channel.getType().equals(ChannelType.TEXT) ? ((TextChannel) channel).getAsMention() : "`" + channel.getName() + "`"));
		embed.setColor(COLOR_ORANGE);
		embed.setTimestamp(ZonedDateTime.now());
		embed.setAuthor(guild.getName(), null, guild.getIconUrl());
		embed.setFooter(String.format("%s ID: %s", channel.getType().equals(ChannelType.CATEGORY) ? "Category" : "Channel", channel.getId()), null);
		
		embed.addField("Before", String.format("`%s`", previous), false);
		embed.addField("After", String.format("`%s`", current), false);
		
		if(guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			guild.getAuditLogs().type(ActionType.CHANNEL_UPDATE).limit(100).queueAfter(AUDIT_LOG_DELAY, TimeUnit.MILLISECONDS, logs -> {
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
		
		Map<String, Object> data = r.table("logs").get(guild.getId()).run(this.connection);
		if(data == null || !((boolean) data.getOrDefault("toggle", false)) || data.get("channel") == null) {
			return;
		}
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setDescription(String.format("The role %s has been created", role.getAsMention()));
		embed.setColor(COLOR_GREEN);
		embed.setTimestamp(ZonedDateTime.now());
		embed.setAuthor(guild.getName(), null, guild.getIconUrl());
		embed.setFooter(String.format("Role ID: %s", role.getId()), null);
		
		if(!role.isManaged() && guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			guild.getAuditLogs().type(ActionType.ROLE_CREATE).limit(100).queueAfter(AUDIT_LOG_DELAY, TimeUnit.MILLISECONDS, logs -> {
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
		
		Map<String, Object> data = r.table("logs").get(guild.getId()).run(this.connection);
		if(data == null || !((boolean) data.getOrDefault("toggle", false)) || data.get("channel") == null) {
			return;
		}
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setDescription(String.format("The role `%s` has been deleted", role.getName()));
		embed.setColor(COLOR_RED);
		embed.setTimestamp(ZonedDateTime.now());
		embed.setAuthor(guild.getName(), null, guild.getIconUrl());
		embed.setFooter(String.format("Role ID: %s", role.getId()), null);
		
		if(!role.isManaged() && guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			guild.getAuditLogs().type(ActionType.ROLE_DELETE).limit(100).queueAfter(AUDIT_LOG_DELAY, TimeUnit.MILLISECONDS, logs -> {
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
		
		Map<String, Object> data = r.table("logs").get(guild.getId()).run(this.connection);
		if(data == null || !((boolean) data.getOrDefault("toggle", false)) || data.get("channel") == null) {
			return;
		}
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setDescription(String.format("The role %s has been renamed", role.getAsMention()));
		embed.setColor(COLOR_ORANGE);
		embed.setTimestamp(ZonedDateTime.now());
		embed.setAuthor(guild.getName(), null, guild.getIconUrl());
		embed.setFooter(String.format("Role ID: %s", role.getId()), null);
		
		embed.addField("Before", String.format("`%s`", event.getOldName()), false);
		embed.addField("After", String.format("`%s`", event.getNewName()), false);
		
		if(guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			guild.getAuditLogs().type(ActionType.ROLE_UPDATE).limit(100).queueAfter(AUDIT_LOG_DELAY, TimeUnit.MILLISECONDS, logs -> {
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
		
		List<Permission> permissionsAdded = Permission.getPermissions(permissionsAfter & permissions);
		List<Permission> permissionsRemoved = Permission.getPermissions(permissionsBefore & permissions);
		
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
		
		Map<String, Object> data = r.table("logs").get(guild.getId()).run(this.connection);
		if(data == null || !((boolean) data.getOrDefault("toggle", false)) || data.get("channel") == null) {
			return;
		}
		
		String message = this.getPermissionDifference(event.getOldPermissionsRaw(), event.getNewPermissionsRaw());
		if(message.length() > 0) {
			EmbedBuilder embed = new EmbedBuilder();
			embed.setDescription(String.format("The role %s has had permission changes made", role.getAsMention()));
			embed.setColor(COLOR_ORANGE);
			embed.setTimestamp(ZonedDateTime.now());
			embed.setAuthor(guild.getName(), null, guild.getIconUrl());
			embed.setFooter(String.format("Role ID: %s", role.getId()), null);
			
			if(guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
				guild.getAuditLogs().type(ActionType.ROLE_UPDATE).limit(100).queueAfter(AUDIT_LOG_DELAY, TimeUnit.MILLISECONDS, logs -> {
					AuditLogEntry entry = logs.stream()
						.filter(e -> e.getTargetIdLong() == event.getRole().getIdLong())
						.filter(e -> e.getChangeByKey(AuditLogKey.ROLE_PERMISSIONS) != null)
						.findFirst()
						.orElse(null);
					
					if(entry != null) {
						Statistics.increaseSuccessfulAuditLogs();
						
						embed.setDescription(String.format("The role %s has had permission changes made by **%s**", role.getAsMention(), entry.getUser().getAsTag()));
					}else{
						Statistics.increaseFailedAuditLogs();
						
						System.err.println(String.format("[onRoleUpdatePermissions] Could not find audit log for %s (%s) %s (%s)", guild.getName(), guild.getId(), role.getName(), role.getId()));
					}
					
					embed.appendDescription(message);
					
					this.send(event.getJDA(), guild, data, embed.build());
				});
			}else{
				embed.appendDescription(message);
				
				this.send(event.getJDA(), guild, data, embed.build());
			}
		}
	}
	
	public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent event) {
		Guild guild = event.getGuild();
		Member member = event.getMember();
		
		List<Role> roles = event.getRoles();
		Role firstRole = roles.get(0);
		
		Map<String, Object> data = r.table("logs").get(guild.getId()).run(this.connection);
		if(data == null || !((boolean) data.getOrDefault("toggle", false)) || data.get("channel") == null) {
			return;
		}
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setColor(COLOR_GREEN);
		embed.setTimestamp(ZonedDateTime.now());
		embed.setAuthor(member.getUser().getAsTag(), null, member.getUser().getEffectiveAvatarUrl());
		
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
			
			embed.setDescription(String.format("The roles %s have been added to `%s`", builder.toString(), member.getEffectiveName()));
		}else{
			embed.setDescription(String.format("The role %s has been added to `%s`", firstRole.getAsMention(), member.getEffectiveName()));
			embed.setFooter(String.format("Role ID: %s", firstRole.getId()), null);
		}
		
		if(!firstRole.isManaged() && guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			guild.getAuditLogs().type(ActionType.MEMBER_ROLE_UPDATE).limit(100).queueAfter(AUDIT_LOG_DELAY, TimeUnit.MILLISECONDS, logs -> {
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
					
					embed.appendDescription(String.format(" by **%s**", entry.getUser().getAsTag()));
				}else{
					Statistics.increaseFailedAuditLogs();
					
					System.err.println(String.format("[onGuildMemberRoleAdd] Could not find audit log for %s (%s) (%s) (%s)", guild.getName(), guild.getId(), member, roles));
				}
				
				this.send(event.getJDA(), guild, data, embed.build());
			});
		}else{
			this.send(event.getJDA(), guild, data, embed.build());
		}
	}
	
	public void onGuildMemberRoleRemove(GuildMemberRoleRemoveEvent event) {		
		Guild guild = event.getGuild();
		Member member = event.getMember();
		
		List<Role> roles = event.getRoles();
		Role firstRole = roles.get(0);
		
		Map<String, Object> data = r.table("logs").get(guild.getId()).run(this.connection);
		if(data == null || !((boolean) data.getOrDefault("toggle", false)) || data.get("channel") == null) {
			return;
		}
		
		/* Wait AUDIT_LOG_DELAY milliseconds to ensure that the role-deletion event has come through */
		new EmptyRestAction<Void>(event.getJDA()).queueAfter(AUDIT_LOG_DELAY, TimeUnit.MILLISECONDS, ($) -> {
			EmbedBuilder embed = new EmbedBuilder();
			embed.setColor(COLOR_RED);
			embed.setTimestamp(ZonedDateTime.now());
			embed.setAuthor(member.getUser().getAsTag(), null, member.getUser().getEffectiveAvatarUrl());
			
			if(roles.size() == 1 && guild.getRoleById(firstRole.getIdLong()) == null) {
				embed.setDescription(String.format("The role `%s` has been removed from `%s` by **role deletion**", firstRole.getName(), member.getEffectiveName()));
				embed.setFooter(String.format("Role ID: %s", firstRole.getId()), null);
				
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
					
					embed.setDescription(String.format("The roles %s have been removed from `%s`", builder, member.getEffectiveName()));
				}else{
					embed.setDescription(String.format("The role %s has been removed from `%s`", firstRole.getAsMention(), member.getEffectiveName()));
					embed.setFooter(String.format("Role ID: %s", firstRole.getId()), null);
				}
				
				if(!firstRole.isManaged() && guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
					guild.getAuditLogs().type(ActionType.MEMBER_ROLE_UPDATE).limit(100).queue(logs -> {
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
							
							embed.appendDescription(String.format(" by **%s**", entry.getUser().getAsTag()));
						}else{
							Statistics.increaseFailedAuditLogs();
							
							System.err.println(String.format("[onGuildMemberRoleRemove] Could not find audit log for %s (%s) (%s) (%s)", guild.getName(), guild.getId(), member, roles));
						}
						
						this.send(event.getJDA(), guild, data, embed.build());
					});
				}else{
					this.send(event.getJDA(), guild, data, embed.build());
				}
			}
		});
	}
	
	public void onGuildMemberNickChange(GuildMemberNickChangeEvent event) {
		Guild guild = event.getGuild();
		Member member = event.getMember();
		
		Map<String, Object> data = r.table("logs").get(guild.getId()).run(this.connection);
		if(data == null || !((boolean) data.getOrDefault("toggle", false)) || data.get("channel") == null) {
			return;
		}
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setDescription(String.format("`%s` has had their nickname changed", member.getEffectiveName()));
		embed.setColor(COLOR_ORANGE);
		embed.setTimestamp(ZonedDateTime.now());
		embed.setAuthor(member.getUser().getAsTag(), null, member.getUser().getEffectiveAvatarUrl());
		
		embed.addField("Before", String.format("`%s`", event.getPrevNick() != null ? event.getPrevNick() : member.getUser().getName()), false);
		embed.addField("After", String.format("`%s`", event.getNewNick() != null ? event.getNewNick() : member.getUser().getName()), false);
		
		if(guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			guild.getAuditLogs().type(ActionType.MEMBER_UPDATE).limit(100).queueAfter(AUDIT_LOG_DELAY, TimeUnit.MILLISECONDS, logs -> {
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
		
		Map<String, Object> data = r.table("logs").get(guild.getId()).run(this.connection);
		if(data == null || !((boolean) data.getOrDefault("toggle", false)) || data.get("channel") == null) {
			return;
		}
		
		boolean muted = event.getVoiceState().isGuildMuted();
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setDescription(String.format("`%s` has been %s", member.getEffectiveName(), muted ? "muted" : "unmuted"));
		embed.setTimestamp(ZonedDateTime.now());
		embed.setAuthor(member.getUser().getAsTag(), null, member.getUser().getEffectiveAvatarUrl());
		
		if(muted) {
			embed.setColor(COLOR_RED);
		}else{
			embed.setColor(COLOR_GREEN);
		}
		
		if(guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			guild.getAuditLogs().type(ActionType.MEMBER_UPDATE).limit(100).queueAfter(AUDIT_LOG_DELAY, TimeUnit.MILLISECONDS, logs -> {
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
		
		Map<String, Object> data = r.table("logs").get(guild.getId()).run(this.connection);
		if(data == null || !((boolean) data.getOrDefault("toggle", false)) || data.get("channel") == null) {
			return;
		}
		
		boolean deafened = event.getVoiceState().isGuildDeafened();
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setDescription(String.format("`%s` has been %s", member.getEffectiveName(), deafened ? "deafened" : "undefeaned"));		
		embed.setTimestamp(ZonedDateTime.now());
		embed.setAuthor(member.getUser().getAsTag(), null, member.getUser().getEffectiveAvatarUrl());
		
		if(deafened) {
			embed.setColor(COLOR_RED);
		}else{
			embed.setColor(COLOR_GREEN);
		}
		
		if(guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			guild.getAuditLogs().type(ActionType.MEMBER_UPDATE).limit(100).queueAfter(AUDIT_LOG_DELAY, TimeUnit.MILLISECONDS, logs -> {
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
		
		Map<String, Object> data = r.table("logs").get(guild.getId()).run(this.connection);
		if(data == null || !((boolean) data.getOrDefault("toggle", false)) || data.get("channel") == null) {
			return;
		}
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setDescription(String.format("`%s` just joined the voice channel `%s`", member.getEffectiveName(), channel.getName()));
		embed.setColor(COLOR_GREEN);
		embed.setTimestamp(ZonedDateTime.now());
		embed.setAuthor(member.getUser().getAsTag(), null, member.getUser().getEffectiveAvatarUrl());
		
		this.send(event.getJDA(), guild, data, embed.build());
	}
	
	public void onGuildVoiceLeave(GuildVoiceLeaveEvent event) {
		Guild guild = event.getGuild();
		Member member = event.getMember();
		VoiceChannel channel = event.getChannelLeft();
		
		Map<String, Object> data = r.table("logs").get(guild.getId()).run(this.connection);
		if(data == null || !((boolean) data.getOrDefault("toggle", false)) || data.get("channel") == null) {
			return;
		}
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setDescription(String.format("`%s` just left the voice channel `%s`", member.getEffectiveName(), channel.getName()));
		embed.setColor(COLOR_RED);
		embed.setTimestamp(ZonedDateTime.now());
		embed.setAuthor(member.getUser().getAsTag(), null, member.getUser().getEffectiveAvatarUrl());
		
		this.send(event.getJDA(), guild, data, embed.build());
	}
	
	public void onGuildVoiceMove(GuildVoiceMoveEvent event) {
		Guild guild = event.getGuild();
		Member member = event.getMember();
		
		VoiceChannel left = event.getChannelLeft(), joined = event.getChannelJoined();
		
		Map<String, Object> data = r.table("logs").get(guild.getId()).run(this.connection);
		if(data == null || !((boolean) data.getOrDefault("toggle", false)) || data.get("channel") == null) {
			return;
		}
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setDescription(String.format("`%s` just changed voice channel", member.getEffectiveName()));
		embed.setColor(COLOR_ORANGE);
		embed.setTimestamp(ZonedDateTime.now());
		embed.setAuthor(member.getUser().getAsTag(), null, member.getUser().getEffectiveAvatarUrl());
		
		embed.addField("Before", String.format("`%s`", left.getName()), false);
		embed.addField("After", String.format("`%s`", joined.getName()), false);
		
		this.send(event.getJDA(), guild, data, embed.build());
	}
}