package com.sx4.bot.logger.handler;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingDeque;
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
import com.sx4.bot.core.Sx4CommandEventListener;
import com.sx4.bot.database.Database;
import com.sx4.bot.logger.Event;
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
import net.dv8tion.jda.api.events.channel.store.StoreChannelDeleteEvent;
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
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent;
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
	
	private static final Bson DEFAULT_PROJECTION = Projections.include("logger");
	
	public static final Request EMPTY_REQUEST = new Request(null, 0L, null, 0L, null);
	
	public static class Request {
		
		public final JDA bot;
		public final long guildId;
		public final Document data;
		public final long timestamp;
		public final List<WebhookEmbed> embeds;
		
		public Request(JDA bot, long guildId, Document data, long timestamp, List<WebhookEmbed> embeds) {
			this.bot = bot;
			this.guildId = guildId;
			this.data = data;
			this.timestamp = timestamp;
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
		long guildId = guild.getIdLong();
		if(!this.queue.containsKey(guildId)) {
			BlockingDeque<Request> blockingDeque = new LinkedBlockingDeque<>();
			this.queue.put(guildId, blockingDeque);
			
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
				}catch(Throwable e) {
					Sx4CommandEventListener.sendErrorMessage(Sx4Bot.getShardManager().getGuildById(Settings.SUPPORT_SERVER_ID).getTextChannelById(Settings.ERRORS_CHANNEL_ID), e, new Object[0]);
					this.queue.remove(guildId);
				}
			});
		}
		
		int requests = (int) Math.ceil((double) requestEmbeds.size() / 10);
		for (int i = 1; i <= requests; i++) {
			List<WebhookEmbed> embedsSplit = i == requests ? requestEmbeds.subList(i * 10 - 10, requestEmbeds.size()) : requestEmbeds.subList(i * 10 - 10, i * 10);
			this.queue.get(guildId).offer(new Request(bot, guildId, data, Clock.systemUTC().instant().getEpochSecond(), embedsSplit));
		}
	}
	
	private void _send(JDA bot, Guild guild, Document rawData, List<WebhookEmbed> embeds, int requestAmount, int attempts) {
		if(attempts >= MAX_ATTEMPTS) {
			Statistics.increaseSkippedLogs();
			
			return;
		}
		
		if(attempts >= ATTEMPTS_BEFORE_REFETCH) {
			rawData = Database.get().getGuildById(guild.getIdLong(), null, DEFAULT_PROJECTION).get("logger", Database.EMPTY_DOCUMENT);
		}
		
		Document data = rawData;
		
		TextChannel channel = guild.getTextChannelById(data.getLong("channelId"));
		if (channel == null) {
			return;
		}
		
		WebhookClient client;
		if(data.getLong("webhookId") == null || data.getString("webhookToken") == null) {
			Webhook webhook;
			if (guild.getSelfMember().hasPermission(Permission.MANAGE_WEBHOOKS)) {
				webhook = channel.createWebhook("Sx4 - Logs").complete();
			} else {
				Statistics.increaseSkippedLogs();
				
				return;
			}
			
			data.put("webhookId", webhook.getIdLong());
			data.put("webhookToken", webhook.getToken());
			
			Bson update = Updates.combine(Updates.set("logger.webhookId", webhook.getIdLong()), Updates.set("logger.webhookToken", webhook.getToken()));
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
		
		WebhookMessage message = new WebhookMessageBuilder()
			.setAvatarUrl(bot.getSelfUser().getEffectiveAvatarUrl())
			.setUsername("Sx4 - Logs")
			.addEmbeds(embeds)
			.build();
		
		client.send(message).whenCompleteAsync((finalMessage, e) -> {
			if (e != null) {
				Statistics.increaseFailedLogs();
				
				if(e instanceof HttpException) {
					/* Ugly catch, blame JDA */
					if(e.getMessage().startsWith("Request returned failure 404")) {
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
				
				System.err.println("[" + LocalDateTime.now().format(Sx4Bot.getTimeFormatter()) + "] [_send]");
				e.printStackTrace();
			} else {
				Statistics.increaseSuccessfulLogs(requestAmount);
			}
		});
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
		
		Document data = Database.get().getGuildById(guild.getIdLong(), null, DEFAULT_PROJECTION).get("logger", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false) || data.getLong("channelId") == null) {
			return;
		}
		
		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setColor(COLOR_GREEN);
		embed.setTimestamp(ZonedDateTime.now());
		embed.setAuthor(new EmbedAuthor(member.getUser().getAsTag(), member.getUser().getEffectiveAvatarUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("%s ID: %s", event.getUser().isBot() ? "Bot" : "User", member.getId()), null));
		
		EnumSet<Event> events = Event.getEvents(data.get("events", Event.ALL_EVENTS));
		if (event.getUser().isBot()) {
			if (!events.contains(Event.BOT_ADDED)) {
				return;
			}
			
			List<Document> users = data.getEmbedded(List.of("blacklisted", "users"), Collections.emptyList());
			for (Document userBlacklist : users) {
				if (userBlacklist.getLong("id") == member.getIdLong()) {
					if ((userBlacklist.getLong("events") & Event.BOT_ADDED.getRaw()) == Event.BOT_ADDED.getRaw()) {
						return;
					}
					
					break;
				}
			}
			
			StringBuilder description = new StringBuilder(String.format("`%s` was just added to the server", member.getEffectiveName()));
			
			if (guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
				guild.retrieveAuditLogs().type(ActionType.BOT_ADD).queueAfter(AUDIT_LOG_DELAY, TimeUnit.MILLISECONDS, logs -> {
					AuditLogEntry entry = logs.stream()
							.filter(e -> e.getTargetIdLong() == member.getIdLong())
							.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds() <= 5)
							.findFirst()
							.orElse(null);
					
					if (entry != null) {
						description.append(" by **" + entry.getUser().getAsTag() + "**");
					}
					
					embed.setDescription(description.toString());
					this.send(event.getJDA(), guild, data, embed.build());
				});
				
				return;
			}
			
			embed.setDescription(description.toString());
		} else {
			if (!events.contains(Event.MEMBER_JOIN)) {
				return;
			}
			
			List<Document> users = data.getEmbedded(List.of("blacklisted", "users"), Collections.emptyList());
			for (Document userBlacklist : users) {
				if (userBlacklist.getLong("id") == member.getIdLong()) {
					if ((userBlacklist.getLong("events") & Event.MEMBER_JOIN.getRaw()) == Event.MEMBER_JOIN.getRaw()) {
						return;
					}
					
					break;
				}
			}
		
			embed.setDescription(String.format("`%s` just joined the server", member.getEffectiveName()));
		}
			
		this.send(event.getJDA(), guild, data, embed.build());		
	}
	
	public void onGuildMemberLeave(GuildMemberLeaveEvent event) {
		Guild guild = event.getGuild();
		Member member = event.getMember();
		
		Document data = Database.get().getGuildById(guild.getIdLong(), null, DEFAULT_PROJECTION).get("logger", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false) || data.getLong("channelId") == null) {
			return;
		}
		
		EnumSet<Event> events = Event.getEvents(data.get("events", Event.ALL_EVENTS));
		if (!events.contains(Event.MEMBER_LEAVE)) {
			return;
		}
		
		List<Document> users = data.getEmbedded(List.of("blacklisted", "users"), Collections.emptyList());
		for (Document userBlacklist : users) {
			if (userBlacklist.getLong("id") == member.getIdLong()) {
				if ((userBlacklist.getLong("events") & Event.MEMBER_LEAVE.getRaw()) == Event.MEMBER_LEAVE.getRaw()) {
					return;
				}
				
				break;
			}
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
		
		Document data = Database.get().getGuildById(guild.getIdLong(), null, DEFAULT_PROJECTION).get("logger", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false) || data.getLong("channelId") == null) {
			return;
		}
		
		EnumSet<Event> events = Event.getEvents(data.get("events", Event.ALL_EVENTS));
		if (!events.contains(Event.MEMBER_BANNED)) {
			return;
		}
		
		List<Document> users = data.getEmbedded(List.of("blacklisted", "users"), Collections.emptyList());
		for (Document userBlacklist : users) {
			if (userBlacklist.getLong("id") == user.getIdLong()) {
				if ((userBlacklist.getLong("events") & Event.MEMBER_BANNED.getRaw()) == Event.MEMBER_BANNED.getRaw()) {
					return;
				}
				
				break;
			}
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
		
		Document data = Database.get().getGuildById(guild.getIdLong(), null, DEFAULT_PROJECTION).get("logger", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false) || data.getLong("channelId") == null) {
			return;
		}
		
		EnumSet<Event> events = Event.getEvents(data.get("events", Event.ALL_EVENTS));
		if (!events.contains(Event.MEMBER_UNBANNED)) {
			return;
		}
		
		List<Document> users = data.getEmbedded(List.of("blacklisted", "users"), Collections.emptyList());
		for (Document userBlacklist : users) {
			if (userBlacklist.getLong("id") == event.getUser().getIdLong()) {
				if ((userBlacklist.getLong("events") & Event.MEMBER_UNBANNED.getRaw()) == Event.MEMBER_UNBANNED.getRaw()) {
					return;
				}
				
				break;
			}
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
		
		if (previousMessage != null && message.getContentRaw().equals(previousMessage.getContentRaw())) {
			return;
		}
		
		Document data = Database.get().getGuildById(guild.getIdLong(), null, DEFAULT_PROJECTION).get("logger", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false) || data.getLong("channelId") == null) {
			return;
		}
		
		EnumSet<Event> events = Event.getEvents(data.get("events", Event.ALL_EVENTS));
		if (!events.contains(Event.MESSAGE_UPDATE)) {
			return;
		}
		
		Document blacklisted = data.get("blacklisted", Database.EMPTY_DOCUMENT);
		
		List<Document> users = blacklisted.getList("users", Document.class, Collections.emptyList());
		for (Document userBlacklist : users) {
			if (userBlacklist.getLong("id") == event.getAuthor().getIdLong()) {
				if ((userBlacklist.getLong("events") & Event.MESSAGE_UPDATE.getRaw()) == Event.MESSAGE_UPDATE.getRaw()) {
					return;
				}
				
				break;
			}
		}
		
		List<Document> channels = blacklisted.getList("channels", Document.class, Collections.emptyList());
		for (Document channelBlacklist : channels) {
			if (channelBlacklist.getLong("id") == channel.getIdLong() || (channel.getParent() != null && channelBlacklist.getLong("id") == channel.getParent().getIdLong())) {
				if ((channelBlacklist.getLong("events") & Event.MESSAGE_UPDATE.getRaw()) == Event.MESSAGE_UPDATE.getRaw()) {
					return;
				}
				
				break;
			}
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
	
	public void onMessageDelete(TextChannel channel, List<String> messages) {
		Guild guild = channel.getGuild();
		
		Document data = Database.get().getGuildById(guild.getIdLong(), null, DEFAULT_PROJECTION).get("logger", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false) || data.getLong("channelId") == null) {
			return;
		}
		
		EnumSet<Event> events = Event.getEvents(data.get("events", Event.ALL_EVENTS));
		if (!events.contains(Event.MESSAGE_DELETE)) {
			return;
		}
		
		Document blacklisted = data.get("blacklisted", Database.EMPTY_DOCUMENT);
		
		List<WebhookEmbed> embeds = new ArrayList<>();
		MessageTask : for (String messageId : messages) {
			Message message = GuildMessageCache.INSTANCE.getMessageById(messageId);
			
			if (message != null) {
				List<Document> users = blacklisted.getList("users", Document.class, Collections.emptyList());
				for (Document userBlacklist : users) {
					if (userBlacklist.getLong("id") == message.getAuthor().getIdLong()) {
						if ((userBlacklist.getLong("events") & Event.MESSAGE_DELETE.getRaw()) == Event.MESSAGE_DELETE.getRaw()) {
							continue MessageTask;
						}
						
						break;
					}
				}
			}
			
			List<Document> channels = blacklisted.getList("channels", Document.class, Collections.emptyList());
			for (Document channelBlacklist : channels) {
				if (channelBlacklist.getLong("id") == channel.getIdLong() || (channel.getParent() != null && channelBlacklist.getLong("id") == channel.getParent().getIdLong())) {
					if ((channelBlacklist.getLong("events") & Event.MESSAGE_DELETE.getRaw()) == Event.MESSAGE_DELETE.getRaw()) {
						continue MessageTask;
					}
					
					break;
				}
			}
			
			WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
			embed.setColor(COLOR_RED);
			embed.setTimestamp(ZonedDateTime.now());
			embed.setFooter(new EmbedFooter(String.format("Message ID: %s", messageId), null));
			
			if(message != null) {
				if(message.getContentRaw().length() == 0 && message.getAttachments().isEmpty() && message.getEmbeds().isEmpty()) {
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
				
				embeds.add(embed.build());
			}else{
				embed.setDescription(String.format("A message sent in %s was deleted", channel.getAsMention()));
				embed.setAuthor(new EmbedAuthor(guild.getName(), guild.getIconUrl(), null));
				
				embeds.add(embed.build());
			}
		}
		
		if (!embeds.isEmpty()) {
			this.send(channel.getJDA(), guild, data, embeds);
		}
	}
	
	public void onMessageBulkDelete(MessageBulkDeleteEvent event) {
		this.onMessageDelete(event.getChannel(), event.getMessageIds());
	}
	
	public void onGuildMessageDelete(GuildMessageDeleteEvent event) {
		this.onMessageDelete(event.getChannel(), List.of(event.getMessageId()));
	}
	
	public void onChannelDelete(GuildChannel channel) {
		Guild guild = channel.getGuild();
		
		Document data = Database.get().getGuildById(guild.getIdLong(), null, DEFAULT_PROJECTION).get("logger", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false) || data.getLong("channelId") == null) {
			return;
		}
		
		Event eventType = channel.getType().equals(ChannelType.TEXT) ? Event.TEXT_CHANNEL_DELETE : 
			channel.getType().equals(ChannelType.VOICE) ? Event.VOICE_CHANNEL_DELETE : 
			channel.getType().equals(ChannelType.STORE) ? Event.STORE_CHANNEL_DELETE : Event.CATEGORY_DELETE;
		
		EnumSet<Event> events = Event.getEvents(data.get("events", Event.ALL_EVENTS));
		if (!events.contains(eventType)) {
			return;
		}
		
		List<Document> channels = data.getEmbedded(List.of("blacklisted", "channels"), Collections.emptyList());
		for (Document channelBlacklist : channels) {
			if (channelBlacklist.getLong("id") == channel.getIdLong()) {
				if ((channelBlacklist.getLong("events") & eventType.getRaw()) == eventType.getRaw()) {
					return;
				}
				
				break;
			}
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
	
	public void onStoreChannelDelete(StoreChannelDeleteEvent event) {
		onChannelDelete(event.getChannel());
	}
	
	public void onChannelCreate(GuildChannel channel) {
		Guild guild = channel.getGuild();
		
		Document data = Database.get().getGuildById(guild.getIdLong(), null, DEFAULT_PROJECTION).get("logger", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false) || data.getLong("channelId") == null) {
			return;
		}
		
		Event eventType = channel.getType().equals(ChannelType.TEXT) ? Event.TEXT_CHANNEL_CREATE : 
			channel.getType().equals(ChannelType.VOICE) ? Event.VOICE_CHANNEL_CREATE : 
			channel.getType().equals(ChannelType.STORE) ? Event.STORE_CHANNEL_CREATE : Event.CATEGORY_CREATE;
		
		EnumSet<Event> events = Event.getEvents(data.get("events", Event.ALL_EVENTS));
		if (!events.contains(eventType)) {
			return;
		}
		
		List<Document> channels = data.getEmbedded(List.of("blacklisted", "channels"), Collections.emptyList());
		for (Document channelBlacklist : channels) {
			if (channelBlacklist.getLong("id") == channel.getIdLong()) {
				if ((channelBlacklist.getLong("events") & eventType.getRaw()) == eventType.getRaw()) {
					return;
				}
				
				break;
			}
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
		
		Document data = Database.get().getGuildById(guild.getIdLong(), null, DEFAULT_PROJECTION).get("logger", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false) || data.getLong("channelId") == null) {
			return;
		}
		
		Event eventType = channel.getType().equals(ChannelType.TEXT) ? Event.TEXT_CHANNEL_NAME_UPDATE : 
			channel.getType().equals(ChannelType.VOICE) ? Event.VOICE_CHANNEL_NAME_UPDATE : 
			channel.getType().equals(ChannelType.STORE) ? Event.STORE_CHANNEL_NAME_UPDATE : Event.CATEGORY_NAME_UPDATE;
		
		EnumSet<Event> events = Event.getEvents(data.get("events", Event.ALL_EVENTS));
		if (!events.contains(eventType)) {
			return;
		}
		
		List<Document> channels = data.getEmbedded(List.of("blacklisted", "channels"), Collections.emptyList());
		for (Document channelBlacklist : channels) {
			if (channelBlacklist.getLong("id") == channel.getIdLong()) {
				if ((channelBlacklist.getLong("events") & eventType.getRaw()) == eventType.getRaw()) {
					return;
				}
				
				break;
			}
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
		
		Document data = Database.get().getGuildById(guild.getIdLong(), null, DEFAULT_PROJECTION).get("logger", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false) || data.getLong("channelId") == null) {
			return;
		}
		
		EnumSet<Event> events = Event.getEvents(data.get("events", Event.ALL_EVENTS));
		if (!events.contains(Event.ROLE_CREATE)) {
			return;
		}
		
		List<Document> roles = data.getEmbedded(List.of("blacklisted", "roles"), Collections.emptyList());
		for (Document roleBlacklist : roles) {
			if (roleBlacklist.getLong("id") == role.getIdLong()) {
				if ((roleBlacklist.getLong("events") & Event.ROLE_CREATE.getRaw()) == Event.ROLE_CREATE.getRaw()) {
					return;
				}
				
				break;
			}
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
		
		Document data = Database.get().getGuildById(guild.getIdLong(), null, DEFAULT_PROJECTION).get("logger", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false) || data.getLong("channelId") == null) {
			return;
		}
		
		EnumSet<Event> events = Event.getEvents(data.get("events", Event.ALL_EVENTS));
		if (!events.contains(Event.ROLE_DELETE)) {
			return;
		}
		
		List<Document> roles = data.getEmbedded(List.of("blacklisted", "roles"), Collections.emptyList());
		for (Document roleBlacklist : roles) {
			if (roleBlacklist.getLong("id") == role.getIdLong()) {
				if ((roleBlacklist.getLong("events") & Event.ROLE_DELETE.getRaw()) == Event.ROLE_DELETE.getRaw()) {
					return;
				}
				
				break;
			}
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
		
		Document data = Database.get().getGuildById(guild.getIdLong(), null, DEFAULT_PROJECTION).get("logger", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false) || data.getLong("channelId") == null) {
			return;
		}
		
		EnumSet<Event> events = Event.getEvents(data.get("events", Event.ALL_EVENTS));
		if (!events.contains(Event.ROLE_NAME_UPDATE)) {
			return;
		}
		
		List<Document> roles = data.getEmbedded(List.of("blacklisted", "roles"), Collections.emptyList());
		for (Document roleBlacklist : roles) {
			if (roleBlacklist.getLong("id") == role.getIdLong()) {
				if ((roleBlacklist.getLong("events") & Event.ROLE_NAME_UPDATE.getRaw()) == Event.ROLE_NAME_UPDATE.getRaw()) {
					return;
				}
				
				break;
			}
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
		
		Document data = Database.get().getGuildById(guild.getIdLong(), null, DEFAULT_PROJECTION).get("logger", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false) || data.getLong("channelId") == null) {
			return;
		}
		
		EnumSet<Event> events = Event.getEvents(data.get("events", Event.ALL_EVENTS));
		if (!events.contains(Event.ROLE_PERMISSION_UPDATE)) {
			return;
		}
		
		List<Document> roles = data.getEmbedded(List.of("blacklisted", "roles"), Collections.emptyList());
		for (Document roleBlacklist : roles) {
			if (roleBlacklist.getLong("id") == role.getIdLong()) {
				if ((roleBlacklist.getLong("events") & Event.ROLE_PERMISSION_UPDATE.getRaw()) == Event.ROLE_PERMISSION_UPDATE.getRaw()) {
					return;
				}
				
				break;
			}
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
		
		Document data = Database.get().getGuildById(guild.getIdLong(), null, DEFAULT_PROJECTION).get("logger", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false) || data.getLong("channelId") == null) {
			return;
		}
		
		EnumSet<Event> events = Event.getEvents(data.get("events", Event.ALL_EVENTS));
		if (!events.contains(Event.MEMBER_ROLE_ADD)) {
			return;
		}
		
		Document blacklisted = data.get("blacklisted", Database.EMPTY_DOCUMENT);
		
		List<Document> users = blacklisted.getList("users", Document.class, Collections.emptyList());
		for (Document userBlacklist : users) {
			if (userBlacklist.getLong("id") == member.getIdLong()) {
				if ((userBlacklist.getLong("events") & Event.MEMBER_ROLE_ADD.getRaw()) == Event.MEMBER_ROLE_ADD.getRaw()) {
					return;
				}
				
				break;
			}
		}
		
		List<Document> blacklistedRoles = blacklisted.getList("roles", Document.class, Collections.emptyList());
		for (Document roleBlacklist : blacklistedRoles) {
			for (Role role : roles) {
				if (roleBlacklist.getLong("id") == role.getIdLong()) {
					if ((roleBlacklist.getLong("events") & Event.MEMBER_ROLE_ADD.getRaw()) == Event.MEMBER_ROLE_ADD.getRaw()) {
						return;
					}
					
					break;
				}
			}
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
		
		Document data = Database.get().getGuildById(guild.getIdLong(), null, DEFAULT_PROJECTION).get("logger", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false) || data.getLong("channelId") == null) {
			return;
		}
		
		EnumSet<Event> events = Event.getEvents(data.get("events", Event.ALL_EVENTS));
		if (!events.contains(Event.MEMBER_ROLE_REMOVE)) {
			return;
		}
		
		Document blacklisted = data.get("blacklisted", Database.EMPTY_DOCUMENT);
		
		List<Document> users = blacklisted.getList("users", Document.class, Collections.emptyList());
		for (Document userBlacklist : users) {
			if (userBlacklist.getLong("id") == member.getIdLong()) {
				if ((userBlacklist.getLong("events") & Event.MEMBER_ROLE_REMOVE.getRaw()) == Event.MEMBER_ROLE_REMOVE.getRaw()) {
					return;
				}
				
				break;
			}
		}
		
		List<Document> blacklistedRoles = blacklisted.getList("roles", Document.class, Collections.emptyList());
		for (Document roleBlacklist : blacklistedRoles) {
			for (Role role : roles) {
				if (roleBlacklist.getLong("id") == role.getIdLong()) {
					if ((roleBlacklist.getLong("events") & Event.MEMBER_ROLE_REMOVE.getRaw()) == Event.MEMBER_ROLE_REMOVE.getRaw()) {
						return;
					}
					
					break;
				}
			}
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
		
		Document data = Database.get().getGuildById(guild.getIdLong(), null, DEFAULT_PROJECTION).get("logger", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false) || data.getLong("channelId") == null) {
			return;
		}
		
		EnumSet<Event> events = Event.getEvents(data.get("events", Event.ALL_EVENTS));
		if (!events.contains(Event.MEMBER_NICKNAME_UPDATE)) {
			return;
		}
		
		List<Document> users = data.getEmbedded(List.of("blacklisted", "users"), Collections.emptyList());
		for (Document userBlacklist : users) {
			if (userBlacklist.getLong("id") == member.getIdLong()) {
				if ((userBlacklist.getLong("events") & Event.MEMBER_NICKNAME_UPDATE.getRaw()) == Event.MEMBER_NICKNAME_UPDATE.getRaw()) {
					return;
				}
				
				break;
			}
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
		VoiceChannel channel = event.getVoiceState().getChannel();
		
		Document data = Database.get().getGuildById(guild.getIdLong(), null, DEFAULT_PROJECTION).get("logger", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false) || data.getLong("channelId") == null) {
			return;
		}
		
		EnumSet<Event> events = Event.getEvents(data.get("events", Event.ALL_EVENTS));
		if (!events.contains(Event.MEMBER_SERVER_VOICE_MUTE)) {
			return;
		}
		
		Document blacklisted = data.get("blacklisted", Database.EMPTY_DOCUMENT);
		
		List<Document> users = blacklisted.getList("users", Document.class, Collections.emptyList());
		for (Document userBlacklist : users) {
			if (userBlacklist.getLong("id") == member.getIdLong()) {
				if ((userBlacklist.getLong("events") & Event.MEMBER_SERVER_VOICE_MUTE.getRaw()) == Event.MEMBER_SERVER_VOICE_MUTE.getRaw()) {
					return;
				}
				
				break;
			}
		}
		
		List<Document> channels = blacklisted.getList("channels", Document.class, Collections.emptyList());
		for (Document channelBlacklist : channels) {
			if (channelBlacklist.getLong("id") == channel.getIdLong() || (channel.getParent() != null && channelBlacklist.getLong("id") == channel.getParent().getIdLong())) {
				if ((channelBlacklist.getLong("events") & Event.MEMBER_SERVER_VOICE_MUTE.getRaw()) == Event.MEMBER_SERVER_VOICE_MUTE.getRaw()) {
					return;
				}
				
				break;
			}
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
		VoiceChannel channel = event.getVoiceState().getChannel();
		
		Document data = Database.get().getGuildById(guild.getIdLong(), null, DEFAULT_PROJECTION).get("logger", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false) || data.getLong("channelId") == null) {
			return;
		}
		
		EnumSet<Event> events = Event.getEvents(data.get("events", Event.ALL_EVENTS));
		if (!events.contains(Event.MEMBER_SERVER_VOICE_DEAFEN)) {
			return;
		}
		
		Document blacklisted = data.get("blacklisted", Database.EMPTY_DOCUMENT);
		
		List<Document> users = blacklisted.getList("users", Document.class, Collections.emptyList());
		for (Document userBlacklist : users) {
			if (userBlacklist.getLong("id") == member.getIdLong()) {
				if ((userBlacklist.getLong("events") & Event.MEMBER_SERVER_VOICE_DEAFEN.getRaw()) == Event.MEMBER_SERVER_VOICE_DEAFEN.getRaw()) {
					return;
				}
				
				break;
			}
		}
		
		List<Document> channels = blacklisted.getList("channels", Document.class, Collections.emptyList());
		for (Document channelBlacklist : channels) {
			if (channelBlacklist.getLong("id") == channel.getIdLong() || (channel.getParent() != null && channelBlacklist.getLong("id") == channel.getParent().getIdLong())) {
				if ((channelBlacklist.getLong("events") & Event.MEMBER_SERVER_VOICE_DEAFEN.getRaw()) == Event.MEMBER_SERVER_VOICE_DEAFEN.getRaw()) {
					return;
				}
				
				break;
			}
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
		
		Document data = Database.get().getGuildById(guild.getIdLong(), null, DEFAULT_PROJECTION).get("logger", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false) || data.getLong("channelId") == null) {
			return;
		}
		
		EnumSet<Event> events = Event.getEvents(data.get("events", Event.ALL_EVENTS));
		if (!events.contains(Event.MEMBER_VOICE_JOIN)) {
			return;
		}
		
		Document blacklisted = data.get("blacklisted", Database.EMPTY_DOCUMENT);
		
		List<Document> users = blacklisted.getList("users", Document.class, Collections.emptyList());
		for (Document userBlacklist : users) {
			if (userBlacklist.getLong("id") == member.getIdLong()) {
				if ((userBlacklist.getLong("events") & Event.MEMBER_VOICE_JOIN.getRaw()) == Event.MEMBER_VOICE_JOIN.getRaw()) {
					return;
				}
				
				break;
			}
		}
		
		List<Document> channels = blacklisted.getList("channels", Document.class, Collections.emptyList());
		for (Document channelBlacklist : channels) {
			if (channelBlacklist.getLong("id") == channel.getIdLong() || (channel.getParent() != null && channelBlacklist.getLong("id") == channel.getParent().getIdLong())) {
				if ((channelBlacklist.getLong("events") & Event.MEMBER_VOICE_JOIN.getRaw()) == Event.MEMBER_VOICE_JOIN.getRaw()) {
					return;
				}
				
				break;
			}
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
		
		Document data = Database.get().getGuildById(guild.getIdLong(), null, DEFAULT_PROJECTION).get("logger", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false) || data.getLong("channelId") == null) {
			return;
		}
		
		EnumSet<Event> events = Event.getEvents(data.get("events", Event.ALL_EVENTS));
		if (!events.contains(Event.MEMBER_VOICE_LEAVE)) {
			return;
		}
		
		Document blacklisted = data.get("blacklisted", Database.EMPTY_DOCUMENT);
		
		List<Document> users = blacklisted.getList("users", Document.class, Collections.emptyList());
		for (Document userBlacklist : users) {
			if (userBlacklist.getLong("id") == member.getIdLong()) {
				if ((userBlacklist.getLong("events") & Event.MEMBER_VOICE_LEAVE.getRaw()) == Event.MEMBER_VOICE_LEAVE.getRaw()) {
					return;
				}
				
				break;
			}
		}
		
		List<Document> channels = blacklisted.getList("channels", Document.class, Collections.emptyList());
		for (Document channelBlacklist : channels) {
			if (channelBlacklist.getLong("id") == channel.getIdLong() || (channel.getParent() != null && channelBlacklist.getLong("id") == channel.getParent().getIdLong())) {
				if ((channelBlacklist.getLong("events") & Event.MEMBER_VOICE_LEAVE.getRaw()) == Event.MEMBER_VOICE_LEAVE.getRaw()) {
					return;
				}
				
				break;
			}
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
		
		Document data = Database.get().getGuildById(guild.getIdLong(), null, DEFAULT_PROJECTION).get("logger", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false) || data.getLong("channelId") == null) {
			return;
		}
		
		EnumSet<Event> events = Event.getEvents(data.get("events", Event.ALL_EVENTS));
		if (!events.contains(Event.MEMBER_VOICE_MOVE)) {
			return;
		}
		
		List<Document> users = data.getEmbedded(List.of("blacklisted", "users"), Collections.emptyList());
		for (Document userBlacklist : users) {
			if (userBlacklist.getLong("id") == member.getIdLong()) {
				if ((userBlacklist.getLong("events") & Event.MEMBER_VOICE_MOVE.getRaw()) == Event.MEMBER_VOICE_MOVE.getRaw()) {
					return;
				}
				
				break;
			}
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