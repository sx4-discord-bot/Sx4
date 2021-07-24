package com.sx4.bot.handlers;

import club.minnced.discord.webhook.send.WebhookEmbed;
import club.minnced.discord.webhook.send.WebhookEmbed.EmbedAuthor;
import club.minnced.discord.webhook.send.WebhookEmbed.EmbedField;
import club.minnced.discord.webhook.send.WebhookEmbed.EmbedFooter;
import club.minnced.discord.webhook.send.WebhookEmbedBuilder;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.management.LoggerContext;
import com.sx4.bot.entities.management.LoggerEvent;
import com.sx4.bot.managers.LoggerManager;
import com.sx4.bot.utility.ColourUtility;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.LoggerUtility;
import com.sx4.bot.utility.StringUtility;
import gnu.trove.map.TLongIntMap;
import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TLongIntHashMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogChange;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.audit.AuditLogKey;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.channel.category.CategoryCreateEvent;
import net.dv8tion.jda.api.events.channel.category.CategoryDeleteEvent;
import net.dv8tion.jda.api.events.channel.category.update.CategoryUpdateNameEvent;
import net.dv8tion.jda.api.events.channel.store.StoreChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.store.StoreChannelDeleteEvent;
import net.dv8tion.jda.api.events.channel.store.update.StoreChannelUpdateNameEvent;
import net.dv8tion.jda.api.events.channel.text.TextChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.text.TextChannelDeleteEvent;
import net.dv8tion.jda.api.events.channel.text.update.TextChannelUpdateNameEvent;
import net.dv8tion.jda.api.events.channel.voice.VoiceChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.voice.VoiceChannelDeleteEvent;
import net.dv8tion.jda.api.events.channel.voice.update.VoiceChannelUpdateNameEvent;
import net.dv8tion.jda.api.events.emote.EmoteAddedEvent;
import net.dv8tion.jda.api.events.emote.EmoteRemovedEvent;
import net.dv8tion.jda.api.events.emote.update.EmoteUpdateNameEvent;
import net.dv8tion.jda.api.events.emote.update.EmoteUpdateRolesEvent;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent;
import net.dv8tion.jda.api.events.guild.override.PermissionOverrideCreateEvent;
import net.dv8tion.jda.api.events.guild.override.PermissionOverrideDeleteEvent;
import net.dv8tion.jda.api.events.guild.override.PermissionOverrideUpdateEvent;
import net.dv8tion.jda.api.events.guild.voice.*;
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageDeleteEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.api.events.role.RoleCreateEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.events.role.update.RoleUpdateColorEvent;
import net.dv8tion.jda.api.events.role.update.RoleUpdateNameEvent;
import net.dv8tion.jda.api.events.role.update.RoleUpdatePermissionsEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import okhttp3.OkHttpClient;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class LoggerHandler implements EventListener {

	public static final int DELAY = 500;

	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	
	private final Map<Long, LoggerManager> managers;

	private final ExecutorService managerExecutor = Executors.newCachedThreadPool();
	private final ScheduledExecutorService webhookExecutor = Executors.newScheduledThreadPool(20);
	private final OkHttpClient webhookClient = new OkHttpClient.Builder()
		.callTimeout(3, TimeUnit.SECONDS)
		.build();

	private final TLongObjectMap<TLongIntMap> disconnectCache = new TLongObjectHashMap<>();
	private final TLongObjectMap<TLongIntMap> moveCache = new TLongObjectHashMap<>();

	private final Sx4 bot;
	
	public LoggerHandler(Sx4 bot) {
		this.bot = bot;
		this.managers = new HashMap<>();
	}

	private LoggerManager getManager(long channelId) {
		if (this.managers.containsKey(channelId)) {
			return this.managers.get(channelId);
		}

		LoggerManager manager = new LoggerManager(this.bot, this.managerExecutor, this.webhookClient, this.webhookExecutor);
		this.managers.put(channelId, manager);

		return manager;
	}

	public void queue(Guild guild, List<Document> loggers, LoggerEvent event, LoggerContext context, WebhookEmbed... embeds) {
		this.queue(guild, loggers, event, context, Arrays.asList(embeds));
	}

	public void queue(Guild guild, List<Document> loggers, LoggerEvent event, LoggerContext context, List<WebhookEmbed> embeds) {
		List<Long> deletedLoggers = new ArrayList<>();
		for (Document logger : loggers) {
			if (!LoggerUtility.canSend(logger, event, context)) {
				continue;
			}

			long channelId = logger.getLong("channelId");
			TextChannel channel = guild.getTextChannelById(channelId);
			if (channel == null) {
				deletedLoggers.add(channelId);
				continue;
			}

			this.getManager(channelId).queue(channel, logger, embeds);
		}

		if (!deletedLoggers.isEmpty()) {
			this.bot.getMongo().deleteManyLoggers(Filters.in("channelId", deletedLoggers)).whenComplete(MongoDatabase.exceptionally(this.bot.getShardManager()));
		}
	}

	private List<Bson> getPipeline(long guildId) {
		List<Bson> guildPipeline = List.of(
			Aggregates.project(Projections.fields(Projections.computed("premium", Operators.lt(Operators.nowEpochSecond(), Operators.ifNull("$premium.endAt", 0L))), Projections.computed("guildId", "$_id"))),
			Aggregates.match(Filters.eq("guildId", guildId))
		);

		return List.of(
			Aggregates.match(Filters.and(Filters.eq("guildId", guildId), Filters.exists("enabled", false))),
			Aggregates.group(null, Accumulators.push("loggers", Operators.ROOT)),
			Aggregates.unionWith("guilds", guildPipeline),
			Aggregates.group(null, Accumulators.max("premium", "$premium"), Accumulators.max("loggers", "$loggers")),
			Aggregates.project(Projections.computed("loggers", Operators.let(new Document("loggers", Operators.map(Operators.ifNull("$loggers", Collections.EMPTY_LIST), Operators.mergeObjects("$$this", new Document("premium", Operators.ifNull("$premium", false))))), Operators.cond(Operators.ifNull("$premium", false), "$$loggers", Operators.slice("$$loggers", 0, 3)))))
		);
	}

	private void delay(Runnable runnable) {
		this.executor.schedule(runnable, LoggerHandler.DELAY, TimeUnit.MILLISECONDS);
	}

	private void onMessageDelete(TextChannel textChannel, List<Long> messageIds) {
		Guild guild = textChannel.getGuild();

		LoggerEvent loggerEvent = LoggerEvent.MESSAGE_DELETE;

		List<Long> deletedLoggers = new ArrayList<>();

		this.bot.getMongo().aggregateLoggers(this.getPipeline(guild.getIdLong())).whenComplete((documents, exception) -> {
			if (ExceptionUtility.sendErrorMessage(exception)) {
				return;
			}

			if (documents.isEmpty()) {
				return;
			}

			Document data = documents.get(0);

			for (Document logger : data.getList("loggers", Document.class)) {
				if ((logger.get("events", LoggerEvent.ALL) & loggerEvent.getRaw()) != loggerEvent.getRaw()) {
					continue;
				}

				long channelId = logger.getLong("channelId");
				TextChannel channel = guild.getTextChannelById(channelId);
				if (channel == null) {
					deletedLoggers.add(channelId);
					continue;
				}

				List<Document> entities = logger.getEmbedded(List.of("blacklist", "entities"), Collections.emptyList());

				List<WebhookEmbed> embeds = new ArrayList<>();
				for (long messageId : messageIds) {
					WebhookEmbedBuilder embed = new WebhookEmbedBuilder()
						.setColor(this.bot.getConfig().getRed())
						.setTimestamp(Instant.now())
						.setFooter(new EmbedFooter(String.format("Message ID: %d", messageId), null));

					LoggerContext loggerContext = new LoggerContext()
						.setChannel(textChannel);

					Message message = this.bot.getMessageCache().getMessageById(messageId);
					if (message == null) {
						if (!LoggerUtility.isWhitelisted(entities, loggerEvent, loggerContext)) {
							continue;
						}

						embed.setDescription(String.format("A message sent in %s was deleted", textChannel.getAsMention()));
						embed.setAuthor(new EmbedAuthor(guild.getName(), guild.getIconUrl(), null));
					} else {
						User author = message.getAuthor();

						loggerContext.setUser(author.getIdLong());

						if (!LoggerUtility.isWhitelisted(entities, loggerEvent, loggerContext)) {
							continue;
						}

						embed.setDescription(String.format("The message sent by `%s` in %s was deleted", author.getName(), textChannel.getAsMention()));
						embed.setAuthor(new EmbedAuthor(author.getAsTag(), author.getEffectiveAvatarUrl(), null));

						String content = message.getContentRaw();
						if (!content.isBlank()) {
							embed.addField(new EmbedField(false, "Message", StringUtility.limit(content, MessageEmbed.VALUE_MAX_LENGTH, "...")));
						}
					}

					embeds.add(embed.build());
				}

				this.getManager(channelId).queue(channel, logger, embeds);
			}

			if (!deletedLoggers.isEmpty()) {
				this.bot.getMongo().deleteManyLoggers(Filters.in("channelId", deletedLoggers)).whenComplete(MongoDatabase.exceptionally(this.bot.getShardManager()));
			}
		});
	}

	public void onGuildMessageDelete(GuildMessageDeleteEvent event) {
		this.onMessageDelete(event.getChannel(), List.of(event.getMessageIdLong()));
	}

	public void onMessageBulkDelete(MessageBulkDeleteEvent event) {
		this.onMessageDelete(event.getChannel(), event.getMessageIds().stream().map(Long::parseLong).collect(Collectors.toList()));
	}

	public void onGuildMessageUpdate(GuildMessageUpdateEvent event) {
		Guild guild = event.getGuild();
		TextChannel textChannel = event.getChannel();
		Member member = event.getMember();
		User user = event.getAuthor();
		Message message = event.getMessage();

		if (message.getTimeEdited() == null) {
			return;
		}

		Message previousMessage = this.bot.getMessageCache().getMessageById(message.getIdLong());
		if (previousMessage != null && message.isPinned() != previousMessage.isPinned()) {
			return;
		}

		LoggerEvent loggerEvent = LoggerEvent.MESSAGE_UPDATE;
		LoggerContext loggerContext = new LoggerContext()
			.setUser(user)
			.setChannel(textChannel);

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		if (member != null) {
			embed.setDescription(String.format("`%s` edited their [message](%s) in %s", member.getEffectiveName(), message.getJumpUrl(), textChannel.getAsMention()));
		} else {
			embed.setDescription(String.format("`%s` edited their [message](%s) in %s", user.getName(), message.getJumpUrl(), textChannel.getAsMention()));
		}

		embed.setAuthor(new EmbedAuthor(user.getAsTag(), user.getEffectiveAvatarUrl(), null));
		embed.setColor(this.bot.getConfig().getOrange());
		embed.setTimestamp(Instant.now());
		embed.setFooter(new EmbedFooter(String.format("Message ID: %s", message.getId()), null));

		String oldContent = previousMessage == null ? null : previousMessage.getContentRaw();
		if (oldContent != null && !oldContent.isBlank()) {
			embed.addField(new EmbedField(false, "Before", StringUtility.limit(oldContent, MessageEmbed.VALUE_MAX_LENGTH, "...")));
		}

		if (!message.getContentRaw().isBlank()) {
			embed.addField(new EmbedField(false, "After", StringUtility.limit(message.getContentRaw(), MessageEmbed.VALUE_MAX_LENGTH, String.format("[...](%s)", message.getJumpUrl()))));
		}

		this.bot.getMongo().aggregateLoggers(this.getPipeline(guild.getIdLong())).whenComplete((documents, exception) -> {
			if (ExceptionUtility.sendErrorMessage(exception)) {
				return;
			}

			if (documents.isEmpty()) {
				return;
			}

			Document data = documents.get(0);

			this.queue(guild, data.getList("loggers", Document.class), loggerEvent, loggerContext, embed.build());
		});
	}

	public void onGuildMemberJoin(GuildMemberJoinEvent event) {
		Guild guild = event.getGuild();
		Member member = event.getMember();
		User user = event.getUser();

		LoggerEvent loggerEvent = user.isBot() ? LoggerEvent.BOT_ADDED : LoggerEvent.MEMBER_JOIN;
		LoggerContext loggerContext = new LoggerContext()
			.setUser(user);

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setColor(this.bot.getConfig().getGreen());
		embed.setTimestamp(Instant.now());
		embed.setAuthor(new EmbedAuthor(user.getAsTag(), user.getEffectiveAvatarUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("%s ID: %s", user.isBot() ? "Bot" : "User", member.getId()), null));

		this.bot.getMongo().aggregateLoggers(this.getPipeline(guild.getIdLong())).whenComplete((documents, exception) -> {
			if (ExceptionUtility.sendErrorMessage(exception)) {
				return;
			}

			if (documents.isEmpty()) {
				return;
			}

			Document data = documents.get(0);

			List<Document> loggers = LoggerUtility.getValidLoggers(data.getList("loggers", Document.class), loggerEvent, loggerContext);
			if (loggers.isEmpty()) {
				return;
			}

			if (user.isBot()) {
				StringBuilder description = new StringBuilder(String.format("`%s` was just added to the server", member.getEffectiveName()));

				if (guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
					guild.retrieveAuditLogs().type(ActionType.BOT_ADD).submitAfter(LoggerHandler.DELAY, TimeUnit.MILLISECONDS).whenComplete((logs, auditException) -> {
						User moderator = logs == null ? null : logs.stream()
							.filter(e -> e.getTargetIdLong() == member.getIdLong())
							.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds() <= 5)
							.map(AuditLogEntry::getUser)
							.findFirst()
							.orElse(null);

						if (moderator != null) {
							loggerContext.setModerator(moderator);

							description.append(" by **")
								.append(moderator.getAsTag())
								.append("**");
						}

						embed.setDescription(description.toString());

						this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
					});

					return;
				}

				embed.setDescription(description.toString());
			} else {
				embed.setDescription(String.format("`%s` just joined the server", member.getEffectiveName()));
			}

			this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
		});
	}

	public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
		Guild guild = event.getGuild();
		User user = event.getUser();

		LoggerContext loggerContext = new LoggerContext()
			.setUser(user);

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("`%s` just left the server", user.getName()));
		embed.setColor(this.bot.getConfig().getRed());
		embed.setTimestamp(Instant.now());
		embed.setAuthor(new EmbedAuthor(user.getAsTag(), user.getEffectiveAvatarUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("User ID: %s", user.getId()), null));

		this.bot.getMongo().aggregateLoggers(this.getPipeline(guild.getIdLong())).whenComplete((documents, exception) -> {
			if (ExceptionUtility.sendErrorMessage(exception)) {
				return;
			}

			if (documents.isEmpty()) {
				return;
			}

			Document data = documents.get(0);

			List<Document> loggers = data.getList("loggers", Document.class);
			if (loggers.isEmpty()) {
				return;
			}

			if (guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
				guild.retrieveAuditLogs().type(ActionType.KICK).submitAfter(LoggerHandler.DELAY, TimeUnit.MILLISECONDS).whenComplete((logs, auditException) -> {
					User moderator = logs == null ? null : logs.stream()
						.filter(e -> e.getTargetIdLong() == user.getIdLong())
						.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds() <= 5)
						.map(AuditLogEntry::getUser)
						.findFirst()
						.orElse(null);

					LoggerEvent loggerEvent = moderator == null ? LoggerEvent.MEMBER_LEAVE : LoggerEvent.MEMBER_KICKED;

					if (moderator != null) {
						loggerContext.setModerator(moderator);

						embed.setDescription(String.format("`%s` has been kicked by **%s**", user.getName(), moderator.getAsTag()));
					}

					this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
				});

				return;
			}

			LoggerEvent loggerEvent = LoggerEvent.MEMBER_LEAVE;

			this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
		});
	}

	public void onGuildBan(GuildBanEvent event) {
		Guild guild = event.getGuild();
		User user = event.getUser();

		LoggerEvent loggerEvent = LoggerEvent.MEMBER_BANNED;
		LoggerContext loggerContext = new LoggerContext()
			.setUser(user);

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("`%s` has been banned", user.getName()));
		embed.setColor(this.bot.getConfig().getRed());
		embed.setTimestamp(Instant.now());
		embed.setAuthor(new EmbedAuthor(user.getAsTag(), user.getEffectiveAvatarUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("User ID: %s", user.getId()), null));

		this.bot.getMongo().aggregateLoggers(this.getPipeline(guild.getIdLong())).whenComplete((documents, exception) -> {
			if (ExceptionUtility.sendErrorMessage(exception)) {
				return;
			}

			if (documents.isEmpty()) {
				return;
			}

			Document data = documents.get(0);

			List<Document> loggers = LoggerUtility.getValidLoggers(data.getList("loggers", Document.class), loggerEvent, loggerContext);
			if (loggers.isEmpty()) {
				return;
			}

			if (guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
				guild.retrieveAuditLogs().type(ActionType.BAN).submitAfter(LoggerHandler.DELAY, TimeUnit.MILLISECONDS).whenComplete((logs, auditException) -> {
					User moderator = logs == null ? null : logs.stream()
						.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds() <= 5)
						.filter(e -> e.getTargetIdLong() == user.getIdLong())
						.map(AuditLogEntry::getUser)
						.findFirst()
						.orElse(null);

					if (moderator != null) {
						loggerContext.setModerator(moderator);

						embed.setDescription(String.format("`%s` has been banned by **%s**", user.getName(), moderator.getAsTag()));
					}

					this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
				});
			} else {
				this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
			}
		});
	}

	public void onGuildUnban(GuildUnbanEvent event) {
		Guild guild = event.getGuild();
		User user = event.getUser();

		LoggerEvent loggerEvent = LoggerEvent.MEMBER_UNBANNED;
		LoggerContext loggerContext = new LoggerContext()
			.setUser(user);

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("`%s` has been unbanned", user.getName()));
		embed.setColor(this.bot.getConfig().getGreen());
		embed.setTimestamp(Instant.now());
		embed.setAuthor(new EmbedAuthor(user.getAsTag(), user.getEffectiveAvatarUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("User ID: %s", user.getId()), null));

		this.bot.getMongo().aggregateLoggers(this.getPipeline(guild.getIdLong())).whenComplete((documents, exception) -> {
			if (ExceptionUtility.sendErrorMessage(exception)) {
				return;
			}

			if (documents.isEmpty()) {
				return;
			}

			Document data = documents.get(0);

			List<Document> loggers = LoggerUtility.getValidLoggers(data.getList("loggers", Document.class), loggerEvent, loggerContext);
			if (loggers.isEmpty()) {
				return;
			}

			if (guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
				guild.retrieveAuditLogs().type(ActionType.UNBAN).submitAfter(LoggerHandler.DELAY, TimeUnit.MILLISECONDS).whenComplete((logs, auditException) -> {
					User moderator = logs == null ? null : logs.stream()
						.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds() <= 5)
						.filter(e -> e.getTargetIdLong() == user.getIdLong())
						.map(AuditLogEntry::getUser)
						.findFirst()
						.orElse(null);

					if (moderator != null) {
						loggerContext.setModerator(moderator);

						embed.setDescription(String.format("`%s` has been unbanned by **%s**", user.getName(), moderator.getAsTag()));
					}

					this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
				});
			} else {
				this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
			}
		});
	}

	public void onGuildVoiceJoin(GuildVoiceJoinEvent event) {
		Guild guild = event.getGuild();
		Member member = event.getMember();
		User user = member.getUser();
		VoiceChannel channel = event.getChannelJoined();

		LoggerEvent loggerEvent = LoggerEvent.MEMBER_VOICE_JOIN;
		LoggerContext loggerContext = new LoggerContext()
			.setUser(user)
			.setChannel(channel);

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder()
			.setDescription(String.format("`%s` just joined the voice channel %s", member.getEffectiveName(), channel.getAsMention()))
			.setColor(this.bot.getConfig().getGreen())
			.setTimestamp(Instant.now())
			.setFooter(new EmbedFooter(String.format("User ID: %s", member.getId()), null))
			.setAuthor(new EmbedAuthor(user.getAsTag(), user.getEffectiveAvatarUrl(), null));

		this.bot.getMongo().aggregateLoggers(this.getPipeline(guild.getIdLong())).whenComplete((documents, exception) -> {
			if (ExceptionUtility.sendErrorMessage(exception)) {
				return;
			}

			if (documents.isEmpty()) {
				return;
			}

			Document data = documents.get(0);

			this.queue(guild, data.getList("loggers", Document.class), loggerEvent, loggerContext, embed.build());
		});
	}

	public void onGuildVoiceLeave(GuildVoiceLeaveEvent event) {
		Guild guild = event.getGuild();
		Member member = event.getMember();
		User user = member.getUser();
		VoiceChannel channel = event.getChannelLeft();

		LoggerContext loggerContext = new LoggerContext()
			.setChannel(channel)
			.setUser(user);

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("`%s` just left the voice channel %s", member.getEffectiveName(), channel.getAsMention()));
		embed.setColor(this.bot.getConfig().getRed());
		embed.setTimestamp(Instant.now());
		embed.setAuthor(new EmbedAuthor(user.getAsTag(), user.getEffectiveAvatarUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("User ID: %s", user.getId()), null));

		this.bot.getMongo().aggregateLoggers(this.getPipeline(guild.getIdLong())).whenComplete((documents, exception) -> {
			if (ExceptionUtility.sendErrorMessage(exception)) {
				return;
			}

			if (documents.isEmpty()) {
				return;
			}

			Document data = documents.get(0);

			List<Document> loggers = data.getList("loggers", Document.class);
			if (loggers.isEmpty()) {
				return;
			}

			if (guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
				event.getGuild().retrieveAuditLogs().type(ActionType.MEMBER_VOICE_KICK).submitAfter(LoggerHandler.DELAY, TimeUnit.MILLISECONDS).whenComplete((logs, auditException) -> {
					this.disconnectCache.putIfAbsent(guild.getIdLong(), new TLongIntHashMap());
					TLongIntMap guildCache = this.disconnectCache.get(guild.getIdLong());

					AuditLogEntry entry = logs == null ? null : logs.stream()
						.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toMinutes() <= 10)
						.filter(e -> {
							int count = Integer.parseInt(e.getOptionByName("count"));
							int oldCount = guildCache.get(e.getIdLong());

							return (count == 1 && count != oldCount) || count > oldCount;
						})
						.findFirst()
						.orElse(null);

					User moderator = entry == null ? null : entry.getUser();

					LoggerEvent loggerEvent = moderator == null ? LoggerEvent.MEMBER_VOICE_LEAVE : LoggerEvent.MEMBER_VOICE_DISCONNECT;

					if (moderator != null) {
						guildCache.put(entry.getIdLong(), Integer.parseInt(entry.getOptionByName("count")));

						loggerContext.setModerator(moderator);

						embed.setDescription(String.format("`%s` was disconnected from the voice channel %s by **%s**", member.getEffectiveName(), channel.getAsMention(), moderator.getAsTag()));
					}

					this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
				});
			} else {
				LoggerEvent loggerEvent = LoggerEvent.MEMBER_VOICE_LEAVE;

				this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
			}
		});
	}

	public void onGuildVoiceMove(GuildVoiceMoveEvent event) {
		Guild guild = event.getGuild();
		Member member = event.getMember();
		User user = member.getUser();
		VoiceChannel joined = event.getChannelJoined(), left = event.getChannelLeft();

		LoggerEvent loggerEvent = LoggerEvent.MEMBER_VOICE_MOVE;
		LoggerContext loggerContext = new LoggerContext()
			.setChannel(joined)
			.setUser(user);

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("`%s` just changed voice channel", member.getEffectiveName()));
		embed.setColor(this.bot.getConfig().getOrange());
		embed.setTimestamp(Instant.now());
		embed.setAuthor(new EmbedAuthor(member.getUser().getAsTag(), member.getUser().getEffectiveAvatarUrl(), null));

		embed.addField(new EmbedField(false, "Before", String.format("`%s`", left.getName())));
		embed.addField(new EmbedField(false, "After", String.format("`%s`", joined.getName())));

		this.bot.getMongo().aggregateLoggers(this.getPipeline(guild.getIdLong())).whenComplete((documents, exception) -> {
			if (ExceptionUtility.sendErrorMessage(exception)) {
				return;
			}

			if (documents.isEmpty()) {
				return;
			}

			Document data = documents.get(0);

			List<Document> loggers = LoggerUtility.getValidLoggers(data.getList("loggers", Document.class), loggerEvent, loggerContext);
			if (loggers.isEmpty()) {
				return;
			}

			if (guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
				event.getGuild().retrieveAuditLogs().type(ActionType.MEMBER_VOICE_MOVE).submitAfter(LoggerHandler.DELAY, TimeUnit.MILLISECONDS).whenComplete((logs, auditException) -> {
					this.moveCache.putIfAbsent(joined.getIdLong(), new TLongIntHashMap());
					TLongIntMap channelCache = this.moveCache.get(joined.getIdLong());

					AuditLogEntry entry = logs == null ? null : logs.stream()
						.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toMinutes() <= 10)
						.filter(e -> Long.parseLong(e.getOptionByName("channel_id")) == joined.getIdLong())
						.filter(e -> {
							int count = Integer.parseInt(e.getOptionByName("count"));
							int oldCount = channelCache.get(e.getIdLong());

							return (count == 1 && count != oldCount) || count > oldCount;
						})
						.findFirst()
						.orElse(null);

					User moderator = entry == null ? null : entry.getUser();
					if (moderator != null) {
						channelCache.put(entry.getIdLong(), Integer.parseInt(entry.getOptionByName("count")));

						loggerContext.setModerator(moderator);

						embed.setDescription(String.format("`%s` was moved voice channel by **%s**", member.getEffectiveName(), moderator.getAsTag()));
					}

					this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
				});
			} else {
				this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
			}
		});
	}

	public void onGuildVoiceGuildMute(GuildVoiceGuildMuteEvent event) {
		Guild guild = event.getGuild();
		Member member = event.getMember();
		User user = member.getUser();
		GuildVoiceState voiceState = event.getVoiceState();
		VoiceChannel channel = voiceState.getChannel();

		boolean muted = voiceState.isGuildMuted();

		LoggerEvent loggerEvent = muted ? LoggerEvent.MEMBER_SERVER_VOICE_MUTE : LoggerEvent.MEMBER_SERVER_VOICE_UNMUTE;
		LoggerContext loggerContext = new LoggerContext()
			.setUser(user)
			.setChannel(channel == null ? 0L : channel.getIdLong());

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("`%s` has been %s", member.getEffectiveName(), muted ? "muted" : "unmuted"));
		embed.setTimestamp(Instant.now());
		embed.setAuthor(new EmbedAuthor(member.getUser().getAsTag(), member.getUser().getEffectiveAvatarUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("User ID: %s", user.getId()), null));
		embed.setColor(muted ? this.bot.getConfig().getRed() : this.bot.getConfig().getGreen());

		this.bot.getMongo().aggregateLoggers(this.getPipeline(guild.getIdLong())).whenComplete((documents, exception) -> {
			if (ExceptionUtility.sendErrorMessage(exception)) {
				return;
			}

			if (documents.isEmpty()) {
				return;
			}

			Document data = documents.get(0);

			List<Document> loggers = LoggerUtility.getValidLoggers(data.getList("loggers", Document.class), loggerEvent, loggerContext);
			if (loggers.isEmpty()) {
				return;
			}

			if (guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
				guild.retrieveAuditLogs().type(ActionType.MEMBER_UPDATE).submitAfter(LoggerHandler.DELAY, TimeUnit.MILLISECONDS).whenComplete((logs, auditException) -> {
					User moderator = logs == null ? null : logs.stream()
						.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds() <= 5)
						.filter(e -> e.getTargetIdLong() == member.getUser().getIdLong())
						.filter(e -> e.getChangeByKey(AuditLogKey.MEMBER_MUTE) != null)
						.map(AuditLogEntry::getUser)
						.findFirst()
						.orElse(null);

					if (moderator != null) {
						loggerContext.setModerator(moderator);

						embed.setDescription(String.format("`%s` has been %s by **%s**", member.getEffectiveName(), muted ? "muted" : "unmuted", moderator.getAsTag()));
					}

					this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
				});
			} else {
				this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
			}
		});
	}

	public void onGuildVoiceGuildDeafen(GuildVoiceGuildDeafenEvent event) {
		Guild guild = event.getGuild();
		Member member = event.getMember();
		User user = member.getUser();
		GuildVoiceState voiceState = event.getVoiceState();
		VoiceChannel channel = voiceState.getChannel();

		boolean deafened = voiceState.isGuildDeafened();

		LoggerEvent loggerEvent = deafened ? LoggerEvent.MEMBER_SERVER_VOICE_DEAFEN : LoggerEvent.MEMBER_SERVER_VOICE_UNDEAFEN;
		LoggerContext loggerContext = new LoggerContext()
			.setChannel(channel == null ? 0L : channel.getIdLong())
			.setUser(user);

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("`%s` has been %s", member.getEffectiveName(), deafened ? "deafened" : "undeafened"));
		embed.setTimestamp(Instant.now());
		embed.setAuthor(new EmbedAuthor(member.getUser().getAsTag(), member.getUser().getEffectiveAvatarUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("User ID: %s", user.getId()), null));
		embed.setColor(deafened ? this.bot.getConfig().getRed() : this.bot.getConfig().getGreen());

		this.bot.getMongo().aggregateLoggers(this.getPipeline(guild.getIdLong())).whenComplete((documents, exception) -> {
			if (ExceptionUtility.sendErrorMessage(exception)) {
				return;
			}

			if (documents.isEmpty()) {
				return;
			}

			Document data = documents.get(0);

			List<Document> loggers = LoggerUtility.getValidLoggers(data.getList("loggers", Document.class), loggerEvent, loggerContext);
			if (loggers.isEmpty()) {
				return;
			}

			if (guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
				guild.retrieveAuditLogs().type(ActionType.MEMBER_UPDATE).submitAfter(LoggerHandler.DELAY, TimeUnit.MILLISECONDS).whenComplete((logs, auditException) -> {
					User moderator = logs == null ? null : logs.stream()
						.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds() <= 5)
						.filter(e -> e.getTargetIdLong() == member.getUser().getIdLong())
						.filter(e -> e.getChangeByKey(AuditLogKey.MEMBER_DEAF) != null)
						.map(AuditLogEntry::getUser)
						.findFirst()
						.orElse(null);

					if (moderator != null) {
						loggerContext.setModerator(moderator);

						embed.setDescription(String.format("`%s` has been %s by **%s**", member.getEffectiveName(), deafened ? "deafened" : "undeafened", moderator.getAsTag()));
					}

					this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
				});
			} else {
				this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
			}
		});
	}

	public void onPermissionOverrideCreate(PermissionOverrideCreateEvent event) {
		Guild guild = event.getGuild();
		GuildChannel channel = event.getChannel();
		IPermissionHolder permissionHolder = event.getPermissionHolder();
		PermissionOverride permissionOverride = event.getPermissionOverride();
		ChannelType channelType = event.getChannelType();

		LoggerEvent loggerEvent = channelType == ChannelType.CATEGORY ? LoggerEvent.CATEGORY_OVERRIDE_CREATE :
			channelType == ChannelType.STORE ? LoggerEvent.STORE_CHANNEL_OVERRIDE_CREATE :
			channelType == ChannelType.VOICE ? LoggerEvent.VOICE_CHANNEL_OVERRIDE_CREATE :
			LoggerEvent.TEXT_CHANNEL_OVERRIDE_CREATE;

		LoggerContext loggerContext = new LoggerContext()
			.setUser(event.isMemberOverride() ? permissionHolder.getIdLong() : 0L)
			.setRole(event.isRoleOverride() ? permissionHolder.getIdLong() : 0L)
			.setChannel(channel);

		String message = LoggerUtility.getPermissionOverrideDifference(0L, Permission.ALL_PERMISSIONS, 0L, permissionOverride);

		StringBuilder description = new StringBuilder();
		description.append(String.format("The %s %s has had permission overrides created for %s", LoggerUtility.getChannelTypeReadable(channelType), channelType == ChannelType.CATEGORY ? "`" + channel.getName() + "`" : channel.getAsMention(), event.isRoleOverride() ? event.getRole().getAsMention() : "`" + event.getMember().getEffectiveName() + "`"));

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setColor(this.bot.getConfig().getGreen());
		embed.setTimestamp(Instant.now());
		embed.setAuthor(new EmbedAuthor(guild.getName(), guild.getIconUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("%s ID: %s", event.isRoleOverride() ? "Role" : "User", permissionHolder.getIdLong()), null));

		this.bot.getMongo().aggregateLoggers(this.getPipeline(guild.getIdLong())).whenComplete((documents, exception) -> {
			if (ExceptionUtility.sendErrorMessage(exception)) {
				return;
			}

			if (documents.isEmpty()) {
				return;
			}

			Document data = documents.get(0);

			List<Document> loggers = LoggerUtility.getValidLoggers(data.getList("loggers", Document.class), loggerEvent, loggerContext);
			if (loggers.isEmpty()) {
				return;
			}

			if (guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
				guild.retrieveAuditLogs().type(ActionType.CHANNEL_OVERRIDE_CREATE).submitAfter(LoggerHandler.DELAY, TimeUnit.MILLISECONDS).whenComplete((logs, auditException) -> {
					User moderator = logs == null ? null : logs.stream()
						.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds() <= 5)
						.filter(e -> e.getTargetIdLong() == channel.getIdLong())
						.filter(e -> {
							AuditLogChange allow = e.getChangeByKey("allow"), deny = e.getChangeByKey("deny");

							int denyNew = deny == null ? (int) permissionOverride.getDeniedRaw() : deny.getNewValue();
							int allowNew = allow == null ? (int) permissionOverride.getAllowedRaw() : allow.getNewValue();

							return denyNew == permissionOverride.getDeniedRaw() && allowNew == permissionOverride.getAllowedRaw();
						})
						.map(AuditLogEntry::getUser)
						.findFirst()
						.orElse(null);

					if (moderator != null) {
						loggerContext.setModerator(moderator);

						description.append(String.format(" by **%s**", moderator.getAsTag()));
					}

					description.append(message);
					embed.setDescription(description.toString());

					this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
				});
			} else {
				description.append(message);
				embed.setDescription(description.toString());

				this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
			}
		});
	}

	public void onPermissionOverrideUpdate(PermissionOverrideUpdateEvent event) {
		Guild guild = event.getGuild();
		GuildChannel channel = event.getChannel();
		IPermissionHolder permissionHolder = event.getPermissionHolder();
		PermissionOverride permissionOverride = event.getPermissionOverride();
		ChannelType channelType = event.getChannelType();

		LoggerEvent loggerEvent = channelType == ChannelType.CATEGORY ? LoggerEvent.CATEGORY_OVERRIDE_CREATE :
			channelType == ChannelType.STORE ? LoggerEvent.STORE_CHANNEL_OVERRIDE_CREATE :
			channelType == ChannelType.VOICE ? LoggerEvent.VOICE_CHANNEL_OVERRIDE_CREATE :
			LoggerEvent.TEXT_CHANNEL_OVERRIDE_CREATE;

		LoggerContext loggerContext = new LoggerContext()
			.setUser(event.isMemberOverride() ? permissionHolder.getIdLong() : 0L)
			.setRole(event.isRoleOverride() ? permissionHolder.getIdLong() : 0L)
			.setChannel(channel);

		String message = LoggerUtility.getPermissionOverrideDifference(event.getOldAllowRaw(), event.getOldInheritedRaw(), event.getOldDenyRaw(), permissionOverride);

		StringBuilder description = new StringBuilder(String.format("The %s %s has had permission overrides updated for %s", LoggerUtility.getChannelTypeReadable(channelType), channelType == ChannelType.CATEGORY ? "`" + channel.getName() + "`" : channel.getAsMention(), event.isRoleOverride() ? event.getRole().getAsMention() : "`" + event.getMember().getEffectiveName() + "`"));

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setColor(this.bot.getConfig().getOrange());
		embed.setTimestamp(Instant.now());
		embed.setAuthor(new EmbedAuthor(guild.getName(), guild.getIconUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("%s ID: %s", event.isRoleOverride() ? "Role" : "User", permissionHolder.getIdLong()), null));

		this.bot.getMongo().aggregateLoggers(this.getPipeline(guild.getIdLong())).whenComplete((documents, exception) -> {
			if (ExceptionUtility.sendErrorMessage(exception)) {
				return;
			}

			if (documents.isEmpty()) {
				return;
			}

			Document data = documents.get(0);

			List<Document> loggers = LoggerUtility.getValidLoggers(data.getList("loggers", Document.class), loggerEvent, loggerContext);
			if (loggers.isEmpty()) {
				return;
			}

			if (guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
				guild.retrieveAuditLogs().type(ActionType.CHANNEL_OVERRIDE_CREATE).submitAfter(LoggerHandler.DELAY, TimeUnit.MILLISECONDS).whenComplete((logs, auditException) -> {
					User moderator = logs == null ? null : logs.stream()
						.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds() <= 5)
						.filter(e -> e.getTargetIdLong() == channel.getIdLong())
						.filter(e -> {
							AuditLogChange allow = e.getChangeByKey("allow"), deny = e.getChangeByKey("deny");

							Integer denyNewValue = deny == null ? null : deny.getNewValue(), denyOldValue = deny == null ? null : deny.getOldValue();
							Integer allowNewValue = allow == null ? null : allow.getNewValue(), allowOldValue = allow == null ? null : allow.getOldValue();

							int denyNew = denyNewValue == null ? (int) permissionOverride.getDeniedRaw() : denyNewValue, denyOld = denyOldValue == null ? (int) event.getOldDenyRaw() : denyOldValue;
							int allowNew = allowNewValue == null ? (int) permissionOverride.getAllowedRaw() : allowNewValue, allowOld = allowOldValue == null ? (int) event.getOldAllowRaw() : allowOldValue;

							return denyNew == permissionOverride.getDeniedRaw() && denyOld == event.getOldDenyRaw() && allowNew == permissionOverride.getAllowedRaw() && allowOld == event.getOldAllowRaw();
						})
						.map(AuditLogEntry::getUser)
						.findFirst()
						.orElse(null);

					if (moderator != null) {
						loggerContext.setModerator(moderator);

						description.append(String.format(" by **%s**", moderator.getAsTag()));
					}

					description.append(message);
					embed.setDescription(description.toString());

					this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
				});
			} else {
				description.append(message);
				embed.setDescription(description.toString());

				this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
			}
		});
	}

	public void onPermissionOverrideDelete(PermissionOverrideDeleteEvent event) {
		Guild guild = event.getGuild();
		GuildChannel channel = event.getChannel();
		ChannelType channelType = event.getChannelType();
		Member member = event.getMember();
		Role role = event.getRole();

		boolean roleOverride = event.isRoleOverride();

		LoggerEvent loggerEvent = channelType == ChannelType.CATEGORY ? LoggerEvent.CATEGORY_OVERRIDE_DELETE :
			channelType == ChannelType.STORE ? LoggerEvent.STORE_CHANNEL_OVERRIDE_DELETE :
			channelType == ChannelType.VOICE ? LoggerEvent.VOICE_CHANNEL_OVERRIDE_DELETE :
			LoggerEvent.TEXT_CHANNEL_OVERRIDE_DELETE;

		LoggerContext loggerContext = new LoggerContext()
			.setUser(roleOverride ? 0L : member.getIdLong())
			.setRole(roleOverride ? role.getIdLong() : 0L)
			.setChannel(channel);

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setColor(this.bot.getConfig().getRed());
		embed.setTimestamp(Instant.now());
		embed.setAuthor(new EmbedAuthor(guild.getName(), guild.getIconUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("%s ID: %s", roleOverride ? "Role" : "User", event.getPermissionHolder().getIdLong()), null));

		// wait for member leave or role delete event if needed
		this.delay(() -> {
			this.bot.getMongo().aggregateLoggers(this.getPipeline(guild.getIdLong())).whenComplete((documents, exception) -> {
				if (ExceptionUtility.sendErrorMessage(exception)) {
					return;
				}

				if (documents.isEmpty()) {
					return;
				}

				Document data = documents.get(0);

				List<Document> loggers = LoggerUtility.getValidLoggers(data.getList("loggers", Document.class), loggerEvent, loggerContext);
				if (loggers.isEmpty()) {
					return;
				}

				boolean deleted = (roleOverride ? event.getRole() : event.getMember()) == null;

				StringBuilder description = new StringBuilder(String.format("The %s %s has had permission overrides deleted for %s", LoggerUtility.getChannelTypeReadable(channelType), channelType == ChannelType.CATEGORY ? "`" + channel.getName() + "`" : channel.getAsMention(), roleOverride ? (deleted ? "`" + role.getName() + "`" : role.getAsMention()) : "`" + member.getEffectiveName() + "`"));

				if (deleted) {
					description.append(String.format(" by **%s**", roleOverride ? "role deletion" : "member leave"));
				}

				if (!deleted && guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
					guild.retrieveAuditLogs().type(ActionType.CHANNEL_OVERRIDE_DELETE).submit().whenComplete((logs, auditException) -> {
						User moderator = logs == null ? null : logs.stream()
							.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds() <= 5)
							.filter(e -> e.getTargetIdLong() == channel.getIdLong())
							.map(AuditLogEntry::getUser)
							.findFirst()
							.orElse(null);

						if (moderator != null) {
							loggerContext.setModerator(moderator);

							description.append(String.format(" by **%s**", moderator.getAsTag()));
						}

						embed.setDescription(description.toString());

						this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
					});
				} else {
					embed.setDescription(description.toString());

					this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
				}
			});
		});
	}

	public void onChannelDelete(GuildChannel channel, LoggerEvent loggerEvent) {
		Guild guild = channel.getGuild();
		String typeReadable = LoggerUtility.getChannelTypeReadable(channel.getType());

		LoggerContext loggerContext = new LoggerContext()
			.setChannel(channel);

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("The %s `%s` has just been deleted", typeReadable, channel.getName()));
		embed.setColor(this.bot.getConfig().getRed());
		embed.setTimestamp(Instant.now());
		embed.setAuthor(new EmbedAuthor(guild.getName(), guild.getIconUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("%s ID: %s", channel.getType() == ChannelType.CATEGORY ? "Category" : "Channel", channel.getId()), null));

		this.bot.getMongo().aggregateLoggers(this.getPipeline(guild.getIdLong())).whenComplete((documents, exception) -> {
			if (ExceptionUtility.sendErrorMessage(exception)) {
				return;
			}

			if (documents.isEmpty()) {
				return;
			}

			Document data = documents.get(0);

			List<Document> loggers = LoggerUtility.getValidLoggers(data.getList("loggers", Document.class), loggerEvent, loggerContext);
			if (loggers.isEmpty()) {
				return;
			}

			if (guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
				guild.retrieveAuditLogs().type(ActionType.CHANNEL_DELETE).submitAfter(LoggerHandler.DELAY, TimeUnit.MILLISECONDS).whenComplete((logs, auditException) -> {
					User moderator = logs == null ? null : logs.stream()
						.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds() <= 5)
						.filter(e -> e.getTargetIdLong() == channel.getIdLong())
						.map(AuditLogEntry::getUser)
						.findFirst()
						.orElse(null);

					if (moderator != null) {
						loggerContext.setModerator(moderator);

						embed.setDescription(String.format("The %s `%s` has just been deleted by **%s**", typeReadable, channel.getName(), moderator.getAsTag()));
					}

					this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
				});
			} else {
				this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
			}
		});
	}

	public void onTextChannelDelete(TextChannelDeleteEvent event) {
		this.onChannelDelete(event.getChannel(), LoggerEvent.TEXT_CHANNEL_DELETE);
	}

	public void onVoiceChannelDelete(VoiceChannelDeleteEvent event) {
		this.onChannelDelete(event.getChannel(), LoggerEvent.VOICE_CHANNEL_DELETE);
	}

	public void onCategoryDelete(CategoryDeleteEvent event) {
		this.onChannelDelete(event.getCategory(), LoggerEvent.CATEGORY_DELETE);
	}

	public void onStoreChannelDelete(StoreChannelDeleteEvent event) {
		this.onChannelDelete(event.getChannel(), LoggerEvent.STORE_CHANNEL_DELETE);
	}

	public void onChannelCreate(GuildChannel channel, LoggerEvent loggerEvent) {
		Guild guild = channel.getGuild();
		ChannelType channelType = channel.getType();
		String typeReadable = LoggerUtility.getChannelTypeReadable(channelType);

		LoggerContext loggerContext = new LoggerContext()
			.setChannel(channel);

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("The %s `%s` has just been created", typeReadable, channel.getName()));
		embed.setColor(this.bot.getConfig().getGreen());
		embed.setTimestamp(Instant.now());
		embed.setAuthor(new EmbedAuthor(guild.getName(), guild.getIconUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("%s ID: %s", channel.getType() == ChannelType.CATEGORY ? "Category" : "Channel", channel.getId()), null));

		this.bot.getMongo().aggregateLoggers(this.getPipeline(guild.getIdLong())).whenComplete((documents, exception) -> {
			if (ExceptionUtility.sendErrorMessage(exception)) {
				return;
			}

			if (documents.isEmpty()) {
				return;
			}

			Document data = documents.get(0);

			List<Document> loggers = LoggerUtility.getValidLoggers(data.getList("loggers", Document.class), loggerEvent, loggerContext);
			if (loggers.isEmpty()) {
				return;
			}

			if (guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
				guild.retrieveAuditLogs().type(ActionType.CHANNEL_CREATE).submitAfter(LoggerHandler.DELAY, TimeUnit.MILLISECONDS).whenComplete((logs, auditException) -> {
					User moderator = logs == null ? null : logs.stream()
						.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds() <= 5)
						.filter(e -> e.getTargetIdLong() == channel.getIdLong())
						.map(AuditLogEntry::getUser)
						.findFirst()
						.orElse(null);

					if (moderator != null) {
						loggerContext.setModerator(moderator);

						embed.setDescription(String.format("The %s %s has just been created by **%s**", typeReadable, channelType == ChannelType.CATEGORY ? "`" + channel.getName() + "`" : channel.getAsMention(), moderator.getAsTag()));
					}

					this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
				});
			} else {
				this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
			}
		});
	}

	public void onTextChannelCreate(TextChannelCreateEvent event) {
		this.onChannelCreate(event.getChannel(), LoggerEvent.TEXT_CHANNEL_CREATE);
	}

	public void onVoiceChannelCreate(VoiceChannelCreateEvent event) {
		this.onChannelCreate(event.getChannel(), LoggerEvent.VOICE_CHANNEL_CREATE);
	}

	public void onCategoryCreate(CategoryCreateEvent event) {
		this.onChannelCreate(event.getCategory(), LoggerEvent.CATEGORY_CREATE);
	}

	public void onStoreChannelCreate(StoreChannelCreateEvent event) {
		this.onChannelCreate(event.getChannel(), LoggerEvent.STORE_CHANNEL_CREATE);
	}

	public void onChannelUpdateName(GuildChannel channel, String oldName, LoggerEvent loggerEvent) {
		Guild guild = channel.getGuild();
		ChannelType channelType = channel.getType();
		String typeReadable = LoggerUtility.getChannelTypeReadable(channelType);

		LoggerContext loggerContext = new LoggerContext()
			.setChannel(channel);

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("The %s `%s` has just been renamed", typeReadable, channel.getName()));
		embed.setColor(this.bot.getConfig().getOrange());
		embed.setTimestamp(Instant.now());
		embed.setAuthor(new EmbedAuthor(guild.getName(), guild.getIconUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("%s ID: %s", channel.getType() == ChannelType.CATEGORY ? "Category" : "Channel", channel.getId()), null));

		embed.addField(new EmbedField(false, "Before", String.format("`%s`", oldName)));
		embed.addField(new EmbedField(false, "After", String.format("`%s`", channel.getName())));

		this.bot.getMongo().aggregateLoggers(this.getPipeline(guild.getIdLong())).whenComplete((documents, exception) -> {
			if (ExceptionUtility.sendErrorMessage(exception)) {
				return;
			}

			if (documents.isEmpty()) {
				return;
			}

			Document data = documents.get(0);

			List<Document> loggers = LoggerUtility.getValidLoggers(data.getList("loggers", Document.class), loggerEvent, loggerContext);
			if (loggers.isEmpty()) {
				return;
			}

			if (guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
				guild.retrieveAuditLogs().type(ActionType.CHANNEL_UPDATE).submitAfter(LoggerHandler.DELAY, TimeUnit.MILLISECONDS).whenComplete((logs, auditException) -> {
					User moderator = logs == null ? null : logs.stream()
						.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds() <= 5)
						.filter(e -> e.getTargetIdLong() == channel.getIdLong())
						.filter(e -> e.getChangeByKey(AuditLogKey.CHANNEL_NAME) != null)
						.map(AuditLogEntry::getUser)
						.findFirst()
						.orElse(null);

					if (moderator != null) {
						loggerContext.setModerator(moderator);

						embed.setDescription(String.format("The %s %s has just been renamed by **%s**", typeReadable, channelType == ChannelType.CATEGORY ? "`" + channel.getName() + "`" : channel.getAsMention(), moderator.getAsTag()));
					}

					this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
				});
			} else {
				this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
			}
		});
	}

	public void onTextChannelUpdateName(TextChannelUpdateNameEvent event) {
		this.onChannelUpdateName(event.getEntity(), event.getOldName(), LoggerEvent.TEXT_CHANNEL_NAME_UPDATE);
	}

	public void onVoiceChannelUpdateName(VoiceChannelUpdateNameEvent event) {
		this.onChannelUpdateName(event.getEntity(), event.getOldName(), LoggerEvent.VOICE_CHANNEL_NAME_UPDATE);
	}

	public void onCategoryUpdateName(CategoryUpdateNameEvent event) {
		this.onChannelUpdateName(event.getEntity(), event.getOldName(), LoggerEvent.CATEGORY_NAME_UPDATE);
	}

	public void onStoreChannelUpdateName(StoreChannelUpdateNameEvent event) {
		this.onChannelUpdateName(event.getEntity(), event.getOldName(), LoggerEvent.STORE_CHANNEL_NAME_UPDATE);
	}

	public void onRoleCreate(RoleCreateEvent event) {
		Guild guild = event.getGuild();
		Role role = event.getRole();

		LoggerEvent loggerEvent = LoggerEvent.ROLE_CREATE;
		LoggerContext loggerContext = new LoggerContext()
			.setRole(role);

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("The role %s has been created", role.getAsMention()));
		embed.setColor(this.bot.getConfig().getGreen());
		embed.setTimestamp(Instant.now());
		embed.setAuthor(new EmbedAuthor(guild.getName(), guild.getIconUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("Role ID: %s", role.getId()), null));

		this.bot.getMongo().aggregateLoggers(this.getPipeline(guild.getIdLong())).whenComplete((documents, exception) -> {
			if (ExceptionUtility.sendErrorMessage(exception)) {
				return;
			}

			if (documents.isEmpty()) {
				return;
			}

			Document data = documents.get(0);

			List<Document> loggers = LoggerUtility.getValidLoggers(data.getList("loggers", Document.class), loggerEvent, loggerContext);
			if (loggers.isEmpty()) {
				return;
			}

			if (!role.isManaged() && guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
				guild.retrieveAuditLogs().type(ActionType.ROLE_CREATE).submitAfter(LoggerHandler.DELAY, TimeUnit.MILLISECONDS).whenComplete((logs, auditException) -> {
					User moderator = logs == null ? null : logs.stream()
						.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds() <= 5)
						.filter(e -> e.getTargetIdLong() == role.getIdLong())
						.map(AuditLogEntry::getUser)
						.findFirst()
						.orElse(null);

					if (moderator != null) {
						loggerContext.setModerator(moderator);

						embed.setDescription(String.format("The role %s has been created by **%s**", role.getAsMention(), moderator.getAsTag()));
					}

					this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
				});
			} else {
				this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
			}
		});
	}

	public void onRoleDelete(RoleDeleteEvent event) {
		Guild guild = event.getGuild();
		Role role = event.getRole();

		LoggerEvent loggerEvent = LoggerEvent.ROLE_DELETE;
		LoggerContext loggerContext = new LoggerContext()
			.setRole(role);

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("The role `%s` has been deleted", role.getName()));
		embed.setColor(this.bot.getConfig().getRed());
		embed.setTimestamp(Instant.now());
		embed.setAuthor(new EmbedAuthor(guild.getName(), guild.getIconUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("Role ID: %s", role.getId()), null));

		this.bot.getMongo().aggregateLoggers(this.getPipeline(guild.getIdLong())).whenComplete((documents, exception) -> {
			if (ExceptionUtility.sendErrorMessage(exception)) {
				return;
			}

			if (documents.isEmpty()) {
				return;
			}

			Document data = documents.get(0);

			List<Document> loggers = LoggerUtility.getValidLoggers(data.getList("loggers", Document.class), loggerEvent, loggerContext);
			if (loggers.isEmpty()) {
				return;
			}

			if (!role.isManaged() && guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
				guild.retrieveAuditLogs().type(ActionType.ROLE_DELETE).submitAfter(LoggerHandler.DELAY, TimeUnit.MILLISECONDS).whenComplete((logs, auditException) -> {
					User moderator = logs == null ? null : logs.stream()
						.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds() <= 5)
						.filter(e -> e.getTargetIdLong() == role.getIdLong())
						.map(AuditLogEntry::getUser)
						.findFirst()
						.orElse(null);

					if (moderator != null) {
						loggerContext.setModerator(moderator);

						embed.setDescription(String.format("The role `%s` has been deleted by **%s**", role.getName(), moderator.getAsTag()));
					}

					this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
				});
			} else {
				this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
			}
		});
	}

	public void onRoleUpdateName(RoleUpdateNameEvent event) {
		Guild guild = event.getGuild();
		Role role = event.getRole();

		LoggerEvent loggerEvent = LoggerEvent.ROLE_NAME_UPDATE;
		LoggerContext loggerContext = new LoggerContext()
			.setRole(role);

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("The role %s has been renamed", role.getAsMention()));
		embed.setColor(this.bot.getConfig().getOrange());
		embed.setTimestamp(Instant.now());
		embed.setAuthor(new EmbedAuthor(guild.getName(), guild.getIconUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("Role ID: %s", role.getId()), null));

		embed.addField(new EmbedField(false, "Before", String.format("`%s`", event.getOldName())));
		embed.addField(new EmbedField(false, "After", String.format("`%s`", event.getNewName())));

		this.bot.getMongo().aggregateLoggers(this.getPipeline(guild.getIdLong())).whenComplete((documents, exception) -> {
			if (ExceptionUtility.sendErrorMessage(exception)) {
				return;
			}

			if (documents.isEmpty()) {
				return;
			}

			Document data = documents.get(0);

			List<Document> loggers = LoggerUtility.getValidLoggers(data.getList("loggers", Document.class), loggerEvent, loggerContext);
			if (loggers.isEmpty()) {
				return;
			}

			if (guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
				guild.retrieveAuditLogs().type(ActionType.ROLE_UPDATE).submitAfter(LoggerHandler.DELAY, TimeUnit.MILLISECONDS).whenComplete((logs, auditException) -> {
					User moderator = logs == null ? null : logs.stream()
						.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds() <= 5)
						.filter(e -> e.getTargetIdLong() == role.getIdLong())
						.filter(e -> e.getChangeByKey(AuditLogKey.ROLE_NAME) != null)
						.map(AuditLogEntry::getUser)
						.findFirst()
						.orElse(null);

					if (moderator != null) {
						loggerContext.setModerator(moderator);

						embed.setDescription(String.format("The role %s has been renamed by **%s**", role.getAsMention(), moderator.getAsTag()));
					}

					this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
				});
			} else {
				this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
			}
		});
	}

	public void onRoleUpdateColor(RoleUpdateColorEvent event) {
		Guild guild = event.getGuild();
		Role role = event.getRole();

		LoggerEvent loggerEvent = LoggerEvent.ROLE_COLOUR_UPDATE;
		LoggerContext loggerContext = new LoggerContext()
			.setRole(role);

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("The role %s has been given a new colour", role.getAsMention()));
		embed.setColor(this.bot.getConfig().getOrange());
		embed.setTimestamp(Instant.now());
		embed.setAuthor(new EmbedAuthor(guild.getName(), guild.getIconUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("Role ID: %s", role.getId()), null));

		int oldColour = event.getOldColorRaw(), newColour = event.getNewColorRaw();

		embed.addField(new EmbedField(false, "Before", String.format("Hex: [#%s](%3$s%1$s)\nRGB: [%2$s](%3$s%1$s)", ColourUtility.toHexString(oldColour), ColourUtility.toRGBString(oldColour), "https://image.sx4bot.co.uk/api/colour?w=1000&h=500&hex=")));
		embed.addField(new EmbedField(false, "After", String.format("Hex: [#%s](%3$s%1$s)\nRGB: [%2$s](%3$s%1$s)", ColourUtility.toHexString(newColour), ColourUtility.toRGBString(newColour), "https://image.sx4bot.co.uk/api/colour?w=1000&h=500&hex=")));

		this.bot.getMongo().aggregateLoggers(this.getPipeline(guild.getIdLong())).whenComplete((documents, exception) -> {
			if (ExceptionUtility.sendErrorMessage(exception)) {
				return;
			}

			if (documents.isEmpty()) {
				return;
			}

			Document data = documents.get(0);

			List<Document> loggers = LoggerUtility.getValidLoggers(data.getList("loggers", Document.class), loggerEvent, loggerContext);
			if (loggers.isEmpty()) {
				return;
			}

			if (guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
				guild.retrieveAuditLogs().type(ActionType.ROLE_UPDATE).submitAfter(LoggerHandler.DELAY, TimeUnit.MILLISECONDS).whenComplete((logs, auditException) -> {
					User moderator = logs == null ? null : logs.stream()
						.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds() <= 5)
						.filter(e -> e.getTargetIdLong() == role.getIdLong())
						.filter(e -> e.getChangeByKey(AuditLogKey.ROLE_COLOR) != null)
						.map(AuditLogEntry::getUser)
						.findFirst()
						.orElse(null);

					if (moderator != null) {
						loggerContext.setModerator(moderator);

						embed.setDescription(String.format("The role %s has been given a new colour by **%s**", role.getAsMention(), moderator.getAsTag()));
					}

					this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
				});
			} else {
				this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
			}
		});
	}

	public void onRoleUpdatePermissions(RoleUpdatePermissionsEvent event) {
		Guild guild = event.getGuild();
		Role role = event.getRole();

		LoggerEvent loggerEvent = LoggerEvent.ROLE_PERMISSION_UPDATE;
		LoggerContext loggerContext = new LoggerContext()
			.setRole(role);

		String permissionMessage = LoggerUtility.getRolePermissionDifference(event.getOldPermissionsRaw(), event.getNewPermissionsRaw());
		if (permissionMessage.isEmpty()) {
			return;
		}

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setColor(this.bot.getConfig().getOrange());
		embed.setTimestamp(Instant.now());
		embed.setAuthor(new EmbedAuthor(guild.getName(), guild.getIconUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("Role ID: %s", role.getId()), null));

		this.bot.getMongo().aggregateLoggers(this.getPipeline(guild.getIdLong())).whenComplete((documents, exception) -> {
			if (ExceptionUtility.sendErrorMessage(exception)) {
				return;
			}

			if (documents.isEmpty()) {
				return;
			}

			Document data = documents.get(0);

			List<Document> loggers = LoggerUtility.getValidLoggers(data.getList("loggers", Document.class), loggerEvent, loggerContext);
			if (loggers.isEmpty()) {
				return;
			}

			StringBuilder description = new StringBuilder(String.format("The role %s has had permission changes made", role.getAsMention()));
			if (guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
				guild.retrieveAuditLogs().type(ActionType.ROLE_UPDATE).submitAfter(LoggerHandler.DELAY, TimeUnit.MILLISECONDS).whenComplete((logs, auditException) -> {
					User moderator = logs == null ? null : logs.stream()
						.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds() <= 5)
						.filter(e -> e.getTargetIdLong() == role.getIdLong())
						.filter(e -> e.getChangeByKey(AuditLogKey.ROLE_PERMISSIONS) != null)
						.map(AuditLogEntry::getUser)
						.findFirst()
						.orElse(null);

					if (moderator != null) {
						loggerContext.setModerator(moderator);

						description.append(String.format(" by **%s**", moderator.getAsTag()));
					}

					description.append(permissionMessage);
					embed.setDescription(description.toString());

					this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
				});
			} else {
				description.append(permissionMessage);
				embed.setDescription(description.toString());

				this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
			}
		});
	}

	public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent event) {
		Guild guild = event.getGuild();
		Member member = event.getMember();
		User user = event.getUser();

		List<Role> roles = event.getRoles();
		Role firstRole = roles.get(0);

		boolean multiple = roles.size() > 1;

		LoggerEvent loggerEvent = LoggerEvent.MEMBER_ROLE_ADD;
		LoggerContext loggerContext = new LoggerContext()
			.setRole(multiple ? 0L : firstRole.getIdLong())
			.setUser(user);

		StringBuilder description = new StringBuilder();

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setColor(this.bot.getConfig().getGreen());
		embed.setTimestamp(Instant.now());
		embed.setAuthor(new EmbedAuthor(user.getAsTag(), user.getEffectiveAvatarUrl(), null));

		if (multiple) {
			StringBuilder builder = new StringBuilder();

			/* Make sure there is always enough space to write all the components to it */
			int maxLength = MessageEmbed.TEXT_MAX_LENGTH
				- 32 /* Max nickname length */
				- 32 /* Result String overhead */
				- 15 /* " and x more" overhead */
				- 3 /* Max length of x */;

			for (int i = 0; i < roles.size(); i++) {
				Role role = roles.get(i);

				String entry = (i == roles.size() - 1 ? " and " : i != 0 ? ", " : "") + role.getAsMention();
				if (builder.length() + entry.length() < maxLength) {
					builder.append(entry);
				} else {
					builder.append(String.format(" and **%s** more", roles.size() - i));
					break;
				}
			}

			description.append(String.format("The roles %s have been added to `%s`", builder, member.getEffectiveName()));
		} else {
			description.append(String.format("The role %s has been added to `%s`", firstRole.getAsMention(), member.getEffectiveName()));
			embed.setFooter(new EmbedFooter(String.format("Role ID: %s", firstRole.getId()), null));
		}

		this.bot.getMongo().aggregateLoggers(this.getPipeline(guild.getIdLong())).whenComplete((documents, exception) -> {
			if (ExceptionUtility.sendErrorMessage(exception)) {
				return;
			}

			if (documents.isEmpty()) {
				return;
			}

			Document data = documents.get(0);

			List<Document> loggers = LoggerUtility.getValidLoggers(data.getList("loggers", Document.class), loggerEvent, loggerContext);
			if (loggers.isEmpty()) {
				return;
			}

			if (!firstRole.isManaged() && guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
				guild.retrieveAuditLogs().type(ActionType.MEMBER_ROLE_UPDATE).submitAfter(LoggerHandler.DELAY, TimeUnit.MILLISECONDS).whenComplete((logs, auditException) -> {
					User moderator = logs == null ? null : logs.stream()
						.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds() <= 5)
						.filter(e -> e.getTargetIdLong() == member.getUser().getIdLong())
						.filter(e -> {
							AuditLogChange change = e.getChangeByKey(AuditLogKey.MEMBER_ROLES_ADD);
							if (change == null) {
								return false;
							}

							List<Map<String, String>> roleEntries = change.getNewValue();
							List<String> roleIds = roleEntries.stream().map(roleEntry -> roleEntry.get("id")).collect(Collectors.toList());

							for (Role role : roles) {
								if (!roleIds.contains(role.getId())) {
									return false;
								}
							}

							return true;
						})
						.map(AuditLogEntry::getUser)
						.findFirst()
						.orElse(null);

					if (moderator != null) {
						loggerContext.setModerator(moderator);

						description.append(String.format(" by **%s**", moderator.getAsTag()));
					}

					embed.setDescription(description.toString());

					this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
				});
			} else {
				embed.setDescription(description.toString());

				this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
			}
		});
	}

	public void onGuildMemberRoleRemove(GuildMemberRoleRemoveEvent event) {
		Guild guild = event.getGuild();
		Member member = event.getMember();
		User user = event.getUser();

		List<Role> roles = event.getRoles();
		Role firstRole = roles.get(0);

		// Ensure role delete event has been sent just in case
		this.delay(() -> {
			StringBuilder description = new StringBuilder();

			boolean multiple = roles.size() > 1;

			LoggerEvent loggerEvent = LoggerEvent.MEMBER_ROLE_REMOVE;
			LoggerContext loggerContext = new LoggerContext()
				.setRole(multiple ? 0L : firstRole.getIdLong())
				.setUser(user);

			WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
			embed.setColor(this.bot.getConfig().getRed());
			embed.setTimestamp(Instant.now());
			embed.setAuthor(new EmbedAuthor(user.getAsTag(), user.getEffectiveAvatarUrl(), null));

			boolean deleted;
			if (multiple) {
				StringBuilder builder = new StringBuilder();

				/* Make sure there is always enough space to write all the components to it */
				int maxLength = MessageEmbed.TEXT_MAX_LENGTH
					- 32 /* Max nickname length */
					- 32 /* Result String overhead */
					- 16 /* " and x more" overhead */
					- 3 /* Max length of x */;

				for (int i = 0; i < roles.size(); i++) {
					Role role = roles.get(i);

					String entry = (i == roles.size() - 1 ? " and " : i != 0 ? ", " : "") + role.getAsMention();
					if (builder.length() + entry.length() < maxLength) {
						builder.append(entry);
					} else {
						builder.append(String.format(" and **%s** more", roles.size() - i));

						break;
					}
				}

				description.append(String.format("The roles %s have been removed from `%s`", builder, member.getEffectiveName()));

				deleted = false;
			} else {
				deleted = guild.getRoleById(firstRole.getIdLong()) == null;

				description.append(String.format("The role %s has been removed from `%s`", deleted ? "`" + firstRole.getName() + "`" : firstRole.getAsMention(), member.getEffectiveName()));
				embed.setFooter(new EmbedFooter(String.format("Role ID: %s", firstRole.getId()), null));

				if (deleted) {
					description.append(" by **role deletion**");
				}
			}

			this.bot.getMongo().aggregateLoggers(this.getPipeline(guild.getIdLong())).whenComplete((documents, exception) -> {
				if (ExceptionUtility.sendErrorMessage(exception)) {
					return;
				}

				if (documents.isEmpty()) {
					return;
				}

				Document data = documents.get(0);

				List<Document> loggers = LoggerUtility.getValidLoggers(data.getList("loggers", Document.class), loggerEvent, loggerContext);
				if (loggers.isEmpty()) {
					return;
				}

				if (!deleted && !firstRole.isManaged() && guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
					guild.retrieveAuditLogs().type(ActionType.MEMBER_ROLE_UPDATE).submit().whenComplete((logs, auditException) -> {
						User moderator = logs == null ? null : logs.stream()
							.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds() <= 5)
							.filter(e -> e.getTargetIdLong() == member.getUser().getIdLong())
							.filter(e -> {
								AuditLogChange change = e.getChangeByKey(AuditLogKey.MEMBER_ROLES_REMOVE);
								if (change == null) {
									return false;
								}

								List<Map<String, String>> roleEntries = change.getNewValue();
								List<String> roleIds = roleEntries.stream().map(roleEntry -> roleEntry.get("id")).collect(Collectors.toList());

								for (Role role : roles) {
									if (!roleIds.contains(role.getId())) {
										return false;
									}
								}

								return true;
							})
							.map(AuditLogEntry::getUser)
							.findFirst()
							.orElse(null);

						if (moderator != null) {
							loggerContext.setModerator(moderator);

							description.append(String.format(" by **%s**", moderator.getAsTag()));
						}

						embed.setDescription(description.toString());

						this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
					});
				} else {
					embed.setDescription(description.toString());

					this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
				}
			});
		});
	}

	public void onGuildMemberUpdateNickname(GuildMemberUpdateNicknameEvent event) {
		Guild guild = event.getGuild();
		Member member = event.getMember();
		User user = event.getUser();

		LoggerEvent loggerEvent = LoggerEvent.MEMBER_NICKNAME_UPDATE;
		LoggerContext loggerContext = new LoggerContext()
			.setUser(user);

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("`%s` has had their nickname changed", member.getEffectiveName()));
		embed.setColor(this.bot.getConfig().getOrange());
		embed.setTimestamp(Instant.now());
		embed.setAuthor(new EmbedAuthor(user.getAsTag(), user.getEffectiveAvatarUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("User ID: %s", user.getId()), null));

		embed.addField(new EmbedField(false, "Before", String.format("`%s`", event.getOldNickname() != null ? event.getOldNickname() : member.getUser().getName())));
		embed.addField(new EmbedField(false, "After", String.format("`%s`", event.getNewNickname() != null ? event.getNewNickname() : member.getUser().getName())));

		this.bot.getMongo().aggregateLoggers(this.getPipeline(guild.getIdLong())).whenComplete((documents, exception) -> {
			if (ExceptionUtility.sendErrorMessage(exception)) {
				return;
			}

			if (documents.isEmpty()) {
				return;
			}

			Document data = documents.get(0);

			List<Document> loggers = LoggerUtility.getValidLoggers(data.getList("loggers", Document.class), loggerEvent, loggerContext);
			if (loggers.isEmpty()) {
				return;
			}

			if (guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
				guild.retrieveAuditLogs().type(ActionType.MEMBER_UPDATE).submitAfter(LoggerHandler.DELAY, TimeUnit.MILLISECONDS).whenComplete((logs, auditException) -> {
					User moderator = logs == null ? null : logs.stream()
						.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds() <= 5)
						.filter(e -> e.getTargetIdLong() == user.getIdLong())
						.filter(e -> e.getChangeByKey(AuditLogKey.MEMBER_NICK) != null)
						.map(AuditLogEntry::getUser)
						.findFirst()
						.orElse(null);

					if (moderator != null) {
						loggerContext.setModerator(moderator);
						embed.setDescription(String.format("`%s` has had their nickname changed by **%s**", member.getEffectiveName(), moderator.getAsTag()));
					}

					this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
				});
			} else {
				this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
			}
		});
	}

	public void onEmoteAdded(EmoteAddedEvent event) {
		Guild guild = event.getGuild();
		Emote emote = event.getEmote();

		LoggerEvent loggerEvent = LoggerEvent.EMOTE_CREATE;
		LoggerContext loggerContext = new LoggerContext()
			.setEmote(emote);

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("The emote %s has been created", emote.getAsMention()));
		embed.setColor(this.bot.getConfig().getGreen());
		embed.setTimestamp(Instant.now());
		embed.setAuthor(new EmbedAuthor(guild.getName(), guild.getIconUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("Emote ID: %s", emote.getId()), null));

		this.bot.getMongo().aggregateLoggers(this.getPipeline(guild.getIdLong())).whenComplete((documents, exception) -> {
			if (ExceptionUtility.sendErrorMessage(exception)) {
				return;
			}

			if (documents.isEmpty()) {
				return;
			}

			Document data = documents.get(0);

			List<Document> loggers = LoggerUtility.getValidLoggers(data.getList("loggers", Document.class), loggerEvent, loggerContext);
			if (loggers.isEmpty()) {
				return;
			}

			if (!emote.isManaged() && guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
				guild.retrieveAuditLogs().type(ActionType.EMOTE_CREATE).submitAfter(LoggerHandler.DELAY, TimeUnit.MILLISECONDS).whenComplete((logs, auditException) -> {
					User moderator = logs == null ? null : logs.stream()
						.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds() <= 5)
						.filter(e -> e.getTargetIdLong() == emote.getIdLong())
						.map(AuditLogEntry::getUser)
						.findFirst()
						.orElse(null);

					if (moderator != null) {
						loggerContext.setModerator(moderator);

						embed.setDescription(String.format("The emote %s has been created by **%s**", emote.getAsMention(), moderator.getAsTag()));
					}

					this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
				});
			} else {
				this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
			}
		});
	}

	public void onEmoteRemoved(EmoteRemovedEvent event) {
		Guild guild = event.getGuild();
		Emote emote = event.getEmote();

		LoggerEvent loggerEvent = LoggerEvent.EMOTE_DELETE;
		LoggerContext loggerContext = new LoggerContext()
			.setEmote(emote);

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("The emote `%s` has been deleted", emote.getName()));
		embed.setColor(this.bot.getConfig().getRed());
		embed.setTimestamp(Instant.now());
		embed.setAuthor(new EmbedAuthor(guild.getName(), guild.getIconUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("Emote ID: %s", emote.getId()), null));

		this.bot.getMongo().aggregateLoggers(this.getPipeline(guild.getIdLong())).whenComplete((documents, exception) -> {
			if (ExceptionUtility.sendErrorMessage(exception)) {
				return;
			}

			if (documents.isEmpty()) {
				return;
			}

			Document data = documents.get(0);

			List<Document> loggers = LoggerUtility.getValidLoggers(data.getList("loggers", Document.class), loggerEvent, loggerContext);
			if (loggers.isEmpty()) {
				return;
			}

			if (!emote.isManaged() && guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
				guild.retrieveAuditLogs().type(ActionType.EMOTE_DELETE).submitAfter(LoggerHandler.DELAY, TimeUnit.MILLISECONDS).whenComplete((logs, auditException) -> {
					User moderator = logs == null ? null : logs.stream()
						.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds() <= 5)
						.filter(e -> e.getTargetIdLong() == emote.getIdLong())
						.map(AuditLogEntry::getUser)
						.findFirst()
						.orElse(null);

					if (moderator != null) {
						loggerContext.setModerator(moderator);

						embed.setDescription(String.format("The emote `%s` has been deleted by **%s**", emote.getName(), moderator.getAsTag()));
					}

					this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
				});
			} else {
				this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
			}
		});
	}

	public void onEmoteUpdateName(EmoteUpdateNameEvent event) {
		Guild guild = event.getGuild();
		Emote emote = event.getEmote();

		LoggerEvent loggerEvent = LoggerEvent.EMOTE_NAME_UPDATE;
		LoggerContext loggerContext = new LoggerContext()
			.setEmote(emote);

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("The emote %s has been renamed", emote.getAsMention()));
		embed.setColor(this.bot.getConfig().getOrange());
		embed.setTimestamp(Instant.now());
		embed.setAuthor(new EmbedAuthor(guild.getName(), guild.getIconUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("Emote ID: %s", emote.getId()), null));

		embed.addField(new EmbedField(false, "Before", String.format("`%s`", event.getOldName())));
		embed.addField(new EmbedField(false, "After", String.format("`%s`", event.getNewName())));

		this.bot.getMongo().aggregateLoggers(this.getPipeline(guild.getIdLong())).whenComplete((documents, exception) -> {
			if (ExceptionUtility.sendErrorMessage(exception)) {
				return;
			}

			if (documents.isEmpty()) {
				return;
			}

			Document data = documents.get(0);

			List<Document> loggers = LoggerUtility.getValidLoggers(data.getList("loggers", Document.class), loggerEvent, loggerContext);
			if (loggers.isEmpty()) {
				return;
			}

			if (guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
				guild.retrieveAuditLogs().type(ActionType.EMOTE_UPDATE).submitAfter(LoggerHandler.DELAY, TimeUnit.MILLISECONDS).whenComplete((logs, auditException) -> {
					User moderator = logs == null ? null : logs.stream()
						.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds() <= 5)
						.filter(e -> e.getTargetIdLong() == emote.getIdLong())
						.filter(e -> e.getChangeByKey(AuditLogKey.EMOTE_NAME) != null)
						.map(AuditLogEntry::getUser)
						.findFirst()
						.orElse(null);

					if (moderator != null) {
						loggerContext.setModerator(moderator);

						embed.setDescription(String.format("The emote %s has been renamed by **%s**", emote.getName(), moderator.getAsTag()));
					}

					this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
				});
			} else {
				this.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
			}
		});
	}

	public void onEmoteUpdateRoles(EmoteUpdateRolesEvent event) {
		Guild guild = event.getGuild();
		Emote emote = event.getEmote();
		List<Role> newRoles = event.getNewRoles(), oldRoles = event.getOldRoles();

		LoggerEvent loggerEvent = LoggerEvent.EMOTE_ROLES_UPDATE;
		LoggerContext loggerContext = new LoggerContext()
			.setEmote(emote);

		Pair<List<Role>, List<Role>> roles = LoggerUtility.getRoleDifference(newRoles, oldRoles);
		List<Role> rolesAdded = roles.getLeft(), rolesRemoved = roles.getRight();

		StringBuilder description = new StringBuilder(String.format("The emote %s has had its role whitelist updated", emote.getAsMention()));

		/* This event isn't sent when a role is deleted, I'll leave this here in case
		if (rolesAdded.size() == 0 && rolesRemoved.size() == 1 && guild.getRoleById(rolesRemoved.get(0).getIdLong()) == null) {
			description.append(" by **role deletion**");
		}*/

		description.append(LoggerUtility.getRoleDifferenceMessage(rolesRemoved, rolesAdded, description.length()));

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(description.toString());
		embed.setColor(this.bot.getConfig().getOrange());
		embed.setTimestamp(Instant.now());
		embed.setAuthor(new EmbedAuthor(guild.getName(), guild.getIconUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("Emote ID: %s", emote.getId()), null));

		this.bot.getMongo().aggregateLoggers(this.getPipeline(guild.getIdLong())).whenComplete((documents, exception) -> {
			if (ExceptionUtility.sendErrorMessage(exception)) {
				return;
			}

			if (documents.isEmpty()) {
				return;
			}

			Document data = documents.get(0);

			this.queue(guild, data.getList("loggers", Document.class), loggerEvent, loggerContext, embed.build());
		});
	}

	@Override
	public void onEvent(GenericEvent event) {
		if (event instanceof GuildMessageDeleteEvent) {
			this.onGuildMessageDelete((GuildMessageDeleteEvent) event);
		} else if (event instanceof MessageBulkDeleteEvent) {
			this.onMessageBulkDelete((MessageBulkDeleteEvent) event);
		} else if (event instanceof GuildMessageUpdateEvent) {
			this.onGuildMessageUpdate((GuildMessageUpdateEvent) event);
		} else if (event instanceof GuildMemberJoinEvent) {
			this.onGuildMemberJoin((GuildMemberJoinEvent) event);
		} else if (event instanceof GuildMemberRemoveEvent) {
			this.onGuildMemberRemove((GuildMemberRemoveEvent) event);
		} else if (event instanceof GuildBanEvent) {
			this.onGuildBan((GuildBanEvent) event);
		} else if (event instanceof GuildUnbanEvent) {
			this.onGuildUnban((GuildUnbanEvent) event);
		} else if (event instanceof GuildVoiceJoinEvent) {
			this.onGuildVoiceJoin((GuildVoiceJoinEvent) event);
		} else if (event instanceof GuildVoiceLeaveEvent) {
			this.onGuildVoiceLeave((GuildVoiceLeaveEvent) event);
		} else if (event instanceof GuildVoiceMoveEvent) {
			this.onGuildVoiceMove((GuildVoiceMoveEvent) event);
		} else if (event instanceof GuildVoiceGuildMuteEvent) {
			this.onGuildVoiceGuildMute((GuildVoiceGuildMuteEvent) event);
		} else if (event instanceof GuildVoiceGuildDeafenEvent) {
			this.onGuildVoiceGuildDeafen((GuildVoiceGuildDeafenEvent) event);
		} else if (event instanceof PermissionOverrideCreateEvent) {
			this.onPermissionOverrideCreate((PermissionOverrideCreateEvent) event);
		} else if (event instanceof PermissionOverrideUpdateEvent) {
			this.onPermissionOverrideUpdate((PermissionOverrideUpdateEvent) event);
		} else if (event instanceof PermissionOverrideDeleteEvent) {
			this.onPermissionOverrideDelete((PermissionOverrideDeleteEvent) event);
		} else if (event instanceof TextChannelDeleteEvent) {
			this.onTextChannelDelete((TextChannelDeleteEvent) event);
		} else if (event instanceof VoiceChannelDeleteEvent) {
			this.onVoiceChannelDelete((VoiceChannelDeleteEvent) event);
		} else if (event instanceof CategoryDeleteEvent) {
			this.onCategoryDelete((CategoryDeleteEvent) event);
		} else if (event instanceof StoreChannelDeleteEvent) {
			this.onStoreChannelDelete((StoreChannelDeleteEvent) event);
		} else if (event instanceof TextChannelCreateEvent) {
			this.onTextChannelCreate((TextChannelCreateEvent) event);
		} else if (event instanceof VoiceChannelCreateEvent) {
			this.onVoiceChannelCreate((VoiceChannelCreateEvent) event);
		} else if (event instanceof CategoryCreateEvent) {
			this.onCategoryCreate((CategoryCreateEvent) event);
		} else if (event instanceof StoreChannelCreateEvent) {
			this.onStoreChannelCreate((StoreChannelCreateEvent) event);
		} else if (event instanceof TextChannelUpdateNameEvent) {
			this.onTextChannelUpdateName((TextChannelUpdateNameEvent) event);
		} else if (event instanceof VoiceChannelUpdateNameEvent) {
			this.onVoiceChannelUpdateName((VoiceChannelUpdateNameEvent) event);
		} else if (event instanceof CategoryUpdateNameEvent) {
			this.onCategoryUpdateName((CategoryUpdateNameEvent) event);
		} else if (event instanceof StoreChannelUpdateNameEvent) {
			this.onStoreChannelUpdateName((StoreChannelUpdateNameEvent) event);
		} else if (event instanceof RoleCreateEvent) {
			this.onRoleCreate((RoleCreateEvent) event);
		} else if (event instanceof RoleDeleteEvent) {
			this.onRoleDelete((RoleDeleteEvent) event);
		} else if (event instanceof RoleUpdateNameEvent) {
			this.onRoleUpdateName((RoleUpdateNameEvent) event);
		} else if (event instanceof RoleUpdateColorEvent) {
			this.onRoleUpdateColor((RoleUpdateColorEvent) event);
		} else if (event instanceof RoleUpdatePermissionsEvent) {
			this.onRoleUpdatePermissions((RoleUpdatePermissionsEvent) event);
		} else if (event instanceof GuildMemberRoleAddEvent) {
			this.onGuildMemberRoleAdd((GuildMemberRoleAddEvent) event);
		} else if (event instanceof GuildMemberRoleRemoveEvent) {
			this.onGuildMemberRoleRemove((GuildMemberRoleRemoveEvent) event);
		} else if (event instanceof GuildMemberUpdateNicknameEvent) {
			this.onGuildMemberUpdateNickname((GuildMemberUpdateNicknameEvent) event);
		} else if (event instanceof EmoteAddedEvent) {
			this.onEmoteAdded((EmoteAddedEvent) event);
		} else if (event instanceof EmoteRemovedEvent) {
			this.onEmoteRemoved((EmoteRemovedEvent) event);
		} else if (event instanceof EmoteUpdateNameEvent) {
			this.onEmoteUpdateName((EmoteUpdateNameEvent) event);
		} else if (event instanceof EmoteUpdateRolesEvent) {
			this.onEmoteUpdateRoles((EmoteUpdateRolesEvent) event);
		}
	}

}
