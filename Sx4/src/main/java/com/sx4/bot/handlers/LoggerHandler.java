package com.sx4.bot.handlers;

import club.minnced.discord.webhook.send.WebhookEmbed;
import club.minnced.discord.webhook.send.WebhookEmbed.EmbedAuthor;
import club.minnced.discord.webhook.send.WebhookEmbed.EmbedField;
import club.minnced.discord.webhook.send.WebhookEmbed.EmbedFooter;
import club.minnced.discord.webhook.send.WebhookEmbedBuilder;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.sx4.bot.config.Config;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.Database;
import com.sx4.bot.entities.logger.LoggerContext;
import com.sx4.bot.entities.management.logger.LoggerEvent;
import com.sx4.bot.managers.LoggerManager;
import com.sx4.bot.utility.ColourUtility;
import com.sx4.bot.utility.LoggerUtility;
import com.sx4.bot.utility.StringUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogChange;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.audit.AuditLogKey;
import net.dv8tion.jda.api.entities.*;
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
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.events.guild.override.PermissionOverrideCreateEvent;
import net.dv8tion.jda.api.events.guild.override.PermissionOverrideDeleteEvent;
import net.dv8tion.jda.api.events.guild.override.PermissionOverrideUpdateEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceJoinEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageDeleteEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.api.events.role.RoleCreateEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.events.role.update.RoleUpdateColorEvent;
import net.dv8tion.jda.api.events.role.update.RoleUpdateNameEvent;
import net.dv8tion.jda.api.events.role.update.RoleUpdatePermissionsEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.bson.Document;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class LoggerHandler extends ListenerAdapter {

	private static final int DELAY = 500;

	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

	private final Map<Long, Map<Long, Integer>> disconnectCache = new HashMap<>();

	private final LoggerManager manager = LoggerManager.get();
	private final Database database = Database.get();
	private final Config config = Config.get();

	private void delay(Runnable runnable) {
		this.executor.schedule(runnable, LoggerHandler.DELAY, TimeUnit.MILLISECONDS);
	}

	private void onMessageDelete(TextChannel textChannel, List<Long> messageIds) {
		Guild guild = textChannel.getGuild();
		ShardManager shardManager = Sx4.get().getShardManager();

		LoggerEvent loggerEvent = LoggerEvent.MESSAGE_DELETE;

		List<Long> deletedLoggers = new ArrayList<>();

		List<Document> loggers = this.database.getGuildById(guild.getIdLong(), Projections.include("logger.loggers")).getEmbedded(List.of("logger", "loggers"), Collections.emptyList());
		for (Document logger : loggers) {
			if (!logger.get("enabled", true)) {
				continue;
			}

			if ((logger.get("events", LoggerEvent.ALL) & loggerEvent.getRaw()) != loggerEvent.getRaw()) {
				continue;
			}

			long channelId = logger.getLong("id");
			TextChannel channel = guild.getTextChannelById(channelId);
			if (channel == null) {
				deletedLoggers.add(channelId);
				continue;
			}

			List<Document> entities = logger.getEmbedded(List.of("blacklist", "entities"), Collections.emptyList());

			List<WebhookEmbed> embeds = new ArrayList<>();
			for (long messageId : messageIds) {
				WebhookEmbedBuilder embed = new WebhookEmbedBuilder()
					.setColor(this.config.getRed())
					.setTimestamp(Instant.now())
					.setFooter(new EmbedFooter(String.format("Message ID: %d", messageId), null));

				Document message = this.database.getMessageById(messageId);
				if (message == null) {
					embed.setDescription(String.format("A message sent in %s was deleted", textChannel.getAsMention()));
					embed.setAuthor(new EmbedAuthor(guild.getName(), guild.getIconUrl(), null));
				} else {
					long userId = message.getLong("authorId");
					User user = shardManager.getUserById(userId);

					if (!LoggerUtility.isWhitelisted(entities, loggerEvent, 0L, userId, textChannel.getIdLong(), 0L)) {
						continue;
					}

					embed.setDescription(String.format("The message sent by `%s` in %s was deleted", user == null ? userId : user.getName(), textChannel.getAsMention()));
					embed.setAuthor(new EmbedAuthor(user == null ? guild.getName() : user.getAsTag(), user == null ? guild.getIconUrl() : user.getEffectiveAvatarUrl(), null));

					String content = message.getString("content");
					if (!content.isBlank()) {
						embed.addField(new EmbedField(false, "Message", StringUtility.limit(content, MessageEmbed.VALUE_MAX_LENGTH, "...")));
					}
				}

				embeds.add(embed.build());
			}

			this.manager.queue(channel, logger, embeds);
		}

		if (!deletedLoggers.isEmpty()) {
			this.database.updateGuildById(guild.getIdLong(), Updates.pull("logger.loggers", Filters.in("id", deletedLoggers))).whenComplete(Database.exceptionally());
		}
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

		Document previousMessage = this.database.getMessageById(message.getIdLong());
		String oldContent = previousMessage == null ? null : previousMessage.getString("content");

		LoggerContext loggerContext = new LoggerContext(user, textChannel, null);
		LoggerEvent loggerEvent = LoggerEvent.MESSAGE_UPDATE;

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		if (member != null) {
			embed.setDescription(String.format("`%s` edited their [message](%s) in %s", member.getEffectiveName(), message.getJumpUrl(), textChannel.getAsMention()));
		} else {
			embed.setDescription(String.format("`%s` edited their [message](%s) in %s", user.getName(), message.getJumpUrl(), textChannel.getAsMention()));
		}

		embed.setAuthor(new EmbedAuthor(user.getAsTag(), user.getEffectiveAvatarUrl(), null));
		embed.setColor(this.config.getOrange());
		embed.setTimestamp(Instant.now());
		embed.setFooter(new EmbedFooter(String.format("Message ID: %s", message.getId()), null));

		if (oldContent != null && !oldContent.isBlank()) {
			embed.addField(new EmbedField(false, "Before", StringUtility.limit(oldContent, MessageEmbed.VALUE_MAX_LENGTH, "...")));
		}

		if (!message.getContentRaw().isBlank()) {
			embed.addField(new EmbedField(false, "After", StringUtility.limit(message.getContentRaw(), MessageEmbed.VALUE_MAX_LENGTH, String.format("[...](%s)", message.getJumpUrl()))));
		}

		List<Document> loggers = this.database.getGuildById(guild.getIdLong(), Projections.include("logger.loggers")).getEmbedded(List.of("logger", "loggers"), Collections.emptyList());
		this.manager.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
	}

	public void onGuildMemberJoin(GuildMemberJoinEvent event) {
		Guild guild = event.getGuild();
		Member member = event.getMember();
		User user = event.getUser();

		LoggerEvent loggerEvent = user.isBot() ? LoggerEvent.BOT_ADDED : LoggerEvent.MEMBER_JOIN;

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setColor(this.config.getGreen());
		embed.setTimestamp(Instant.now());
		embed.setAuthor(new EmbedAuthor(user.getAsTag(), user.getEffectiveAvatarUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("%s ID: %s", user.isBot() ? "Bot" : "User", member.getId()), null));

		List<Document> loggers = this.database.getGuildById(guild.getIdLong(), Projections.include("logger.loggers")).getEmbedded(List.of("logger", "loggers"), Collections.emptyList());

		if (user.isBot()) {
			StringBuilder description = new StringBuilder(String.format("`%s` was just added to the server", member.getEffectiveName()));

			if (guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
				guild.retrieveAuditLogs().type(ActionType.BOT_ADD).queueAfter(LoggerHandler.DELAY, TimeUnit.MILLISECONDS, logs -> {
					User moderator = logs.stream()
						.filter(e -> e.getTargetIdLong() == member.getIdLong())
						.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds() <= 5)
						.map(AuditLogEntry::getUser)
						.findFirst()
						.orElse(null);

					LoggerContext loggerContext = new LoggerContext(user, null, null, moderator);

					if (moderator != null) {
						description.append(" by **")
							.append(moderator.getAsTag())
							.append("**");
					}

					embed.setDescription(description.toString());

					this.manager.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
				});

				return;
			}

			embed.setDescription(description.toString());
		} else {
			embed.setDescription(String.format("`%s` just joined the server", member.getEffectiveName()));
		}

		this.manager.queue(guild, loggers, loggerEvent, new LoggerContext(user, null, null), embed.build());
	}

	public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
		Guild guild = event.getGuild();
		User user = event.getUser();

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("`%s` just left the server", user.getName()));
		embed.setColor(this.config.getRed());
		embed.setTimestamp(Instant.now());
		embed.setAuthor(new EmbedAuthor(user.getAsTag(), user.getEffectiveAvatarUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("User ID: %s", user.getId()), null));

		List<Document> loggers = this.database.getGuildById(guild.getIdLong(), Projections.include("logger.loggers")).getEmbedded(List.of("logger", "loggers"), Collections.emptyList());

		if (guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			guild.retrieveAuditLogs().type(ActionType.KICK).queueAfter(LoggerHandler.DELAY, TimeUnit.MILLISECONDS, logs -> {
				User moderator = logs.stream()
					.filter(e -> e.getTargetIdLong() == user.getIdLong())
					.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds() <= 5)
					.map(AuditLogEntry::getUser)
					.findFirst()
					.orElse(null);

				LoggerContext loggerContext = new LoggerContext(user, null, null, moderator);
				LoggerEvent loggerEvent = moderator == null ? LoggerEvent.MEMBER_LEAVE : LoggerEvent.MEMBER_KICKED;

				if (moderator != null) {
					embed.setDescription(String.format("`%s` has been kicked by **%s**", user.getName(), moderator.getAsTag()));
				}

				this.manager.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
			});

			return;
		}

		LoggerContext loggerContext = new LoggerContext(user, null, null);
		LoggerEvent loggerEvent = LoggerEvent.MEMBER_LEAVE;

		this.manager.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
	}

	public void onGuildBan(GuildBanEvent event) {
		Guild guild = event.getGuild();
		User user = event.getUser();

		LoggerEvent loggerEvent = LoggerEvent.MEMBER_BANNED;

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("`%s` has been banned", user.getName()));
		embed.setColor(this.config.getRed());
		embed.setTimestamp(Instant.now());
		embed.setAuthor(new EmbedAuthor(user.getAsTag(), user.getEffectiveAvatarUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("User ID: %s", user.getId()), null));

		List<Document> loggers = this.database.getGuildById(guild.getIdLong(), Projections.include("logger.loggers")).getEmbedded(List.of("logger", "loggers"), Collections.emptyList());

		if (guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			guild.retrieveAuditLogs().type(ActionType.BAN).queueAfter(LoggerHandler.DELAY, TimeUnit.MILLISECONDS, logs -> {
				User moderator = logs.stream()
					.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds() <= 5)
					.filter(e -> e.getTargetIdLong() == user.getIdLong())
					.map(AuditLogEntry::getUser)
					.findFirst()
					.orElse(null);

				LoggerContext loggerContext = new LoggerContext(user, null, null, moderator);

				if (moderator != null) {
					embed.setDescription(String.format("`%s` has been banned by **%s**", user.getName(), moderator.getAsTag()));
				}

				this.manager.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
			});
		} else {
			LoggerContext loggerContext = new LoggerContext(user, null, null, null);

			this.manager.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
		}
	}

	public void onGuildUnban(GuildUnbanEvent event) {
		Guild guild = event.getGuild();
		User user = event.getUser();

		LoggerEvent loggerEvent = LoggerEvent.MEMBER_UNBANNED;

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("`%s` has been unbanned", user.getName()));
		embed.setColor(this.config.getGreen());
		embed.setTimestamp(Instant.now());
		embed.setAuthor(new EmbedAuthor(user.getAsTag(), user.getEffectiveAvatarUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("User ID: %s", user.getId()), null));

		List<Document> loggers = this.database.getGuildById(guild.getIdLong(), Projections.include("logger.loggers")).getEmbedded(List.of("logger", "loggers"), Collections.emptyList());

		if (guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			guild.retrieveAuditLogs().type(ActionType.UNBAN).queueAfter(LoggerHandler.DELAY, TimeUnit.MILLISECONDS, logs -> {
				User moderator = logs.stream()
					.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds() <= 5)
					.filter(e -> e.getTargetIdLong() == user.getIdLong())
					.map(AuditLogEntry::getUser)
					.findFirst()
					.orElse(null);

				LoggerContext loggerContext = new LoggerContext(user, null, null, moderator);

				if (moderator != null) {
					embed.setDescription(String.format("`%s` has been unbanned by **%s**", user.getName(), moderator.getAsTag()));
				}

				this.manager.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
			});
		} else {
			LoggerContext loggerContext = new LoggerContext(user, null, null);

			this.manager.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
		}
	}

	public void onGuildVoiceJoin(GuildVoiceJoinEvent event) {
		Guild guild = event.getGuild();
		Member member = event.getMember();
		VoiceChannel channel = event.getChannelJoined();

		LoggerEvent loggerEvent = LoggerEvent.MEMBER_VOICE_JOIN;
		LoggerContext loggerContext = new LoggerContext(member.getUser(), channel, null);

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder()
			.setDescription(String.format("`%s` just joined the voice channel `%s`", member.getEffectiveName(), channel.getName()))
			.setColor(this.config.getGreen())
			.setTimestamp(Instant.now())
			.setFooter(new EmbedFooter(String.format("User ID: %s", member.getId()), null))
			.setAuthor(new EmbedAuthor(member.getUser().getAsTag(), member.getUser().getEffectiveAvatarUrl(), null));

		List<Document> loggers = this.database.getGuildById(guild.getIdLong(), Projections.include("logger.loggers")).getEmbedded(List.of("logger", "loggers"), Collections.emptyList());
		this.manager.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
	}

	public void onGuildVoiceLeave(GuildVoiceLeaveEvent event) {
		Guild guild = event.getGuild();
		Member member = event.getMember();
		User user = member.getUser();
		VoiceChannel channel = event.getChannelLeft();

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("`%s` just left the voice channel `%s`", member.getEffectiveName(), channel.getName()));
		embed.setColor(this.config.getRed());
		embed.setTimestamp(Instant.now());
		embed.setAuthor(new EmbedAuthor(user.getAsTag(), user.getEffectiveAvatarUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("User ID: %s", user.getId()), null));

		List<Document> loggers = this.database.getGuildById(guild.getIdLong(), Projections.include("logger.loggers")).getEmbedded(List.of("logger", "loggers"), Collections.emptyList());

		if (guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			event.getGuild().retrieveAuditLogs().type(ActionType.MEMBER_VOICE_KICK).queueAfter(LoggerHandler.DELAY, TimeUnit.MILLISECONDS, logs -> {
				Set<Long> ids = new HashSet<>();

				Map<Long, Integer> guildCache = this.disconnectCache.computeIfAbsent(guild.getIdLong(), key -> new HashMap<>());

				AuditLogEntry entry = logs.stream()
					.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toMinutes() <= 10)
					.filter(e -> {
						long id = e.getUser().getIdLong();

						int count = Integer.parseInt(e.getOptionByName("count"));
						int oldCount = guildCache.getOrDefault(id, 0);

						if (ids.contains(id)) {
							return false;
						}

						ids.add(id);

						return (count == 1 && count != oldCount) || count > oldCount;
					})
					.findFirst()
					.orElse(null);

				User moderator = entry == null ? null : entry.getUser();

				LoggerContext loggerContext = new LoggerContext(user, channel, null, moderator);
				LoggerEvent loggerEvent = moderator == null ? LoggerEvent.MEMBER_VOICE_LEAVE : LoggerEvent.MEMBER_VOICE_DISCONNECT;

				if (moderator != null) {
					guildCache.put(moderator.getIdLong(), Integer.parseInt(entry.getOptionByName("count")));

					embed.setDescription(String.format("`%s` was disconnected from the voice channel `%s` by **%s**", member.getEffectiveName(), channel.getName(), moderator.getAsTag()));
				}

				this.manager.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
			});

			return;
		}

		LoggerContext loggerContext = new LoggerContext(user, channel, null);
		LoggerEvent loggerEvent = LoggerEvent.MEMBER_VOICE_LEAVE;

		this.manager.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
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

		String message = LoggerUtility.getPermissionOverrideDifference(0L, Permission.ALL_PERMISSIONS, 0L, permissionOverride);

		StringBuilder description = new StringBuilder();
		description.append(String.format("The %s %s has had permission overrides created for %s", LoggerUtility.getChannelTypeReadable(channelType), channelType == ChannelType.TEXT ? ((TextChannel) channel).getAsMention() : "`" + channel.getName() + "`", event.isRoleOverride() ? event.getRole().getAsMention() : "`" + event.getMember().getEffectiveName() + "`"));

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setColor(this.config.getGreen());
		embed.setTimestamp(Instant.now());
		embed.setAuthor(new EmbedAuthor(guild.getName(), guild.getIconUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("%s ID: %s", event.isRoleOverride() ? "Role" : "User", permissionHolder.getIdLong()), null));

		List<Document> loggers = this.database.getGuildById(guild.getIdLong(), Projections.include("logger.loggers")).getEmbedded(List.of("logger", "loggers"), Collections.emptyList());

		if (guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			guild.retrieveAuditLogs().type(ActionType.CHANNEL_OVERRIDE_CREATE).queueAfter(LoggerHandler.DELAY, TimeUnit.MILLISECONDS, logs -> {
				User moderator = logs.stream()
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

				LoggerContext loggerContext = new LoggerContext(event.isMemberOverride() ? event.getMember().getUser() : null, channel, event.isRoleOverride() ? event.getRole() : null, moderator);

				if (moderator != null) {
					description.append(String.format(" by **%s**", moderator.getAsTag()));
				}

				description.append(message);
				embed.setDescription(description.toString());

				this.manager.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
			});
		} else {
			LoggerContext loggerContext = new LoggerContext(event.isMemberOverride() ? event.getMember().getUser() : null, channel, event.isRoleOverride() ? event.getRole() : null);

			description.append(message);
			embed.setDescription(description.toString());

			this.manager.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
		}
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

		String message = LoggerUtility.getPermissionOverrideDifference(event.getOldAllowRaw(), event.getOldInheritedRaw(), event.getOldDenyRaw(), permissionOverride);

		StringBuilder description = new StringBuilder(String.format("The %s %s has had permission overrides updated for %s", LoggerUtility.getChannelTypeReadable(channelType), channelType == ChannelType.TEXT ? ((TextChannel) channel).getAsMention() : "`" + channel.getName() + "`", event.isRoleOverride() ? event.getRole().getAsMention() : "`" + event.getMember().getEffectiveName() + "`"));

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setColor(this.config.getOrange());
		embed.setTimestamp(Instant.now());
		embed.setAuthor(new EmbedAuthor(guild.getName(), guild.getIconUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("%s ID: %s", event.isRoleOverride() ? "Role" : "User", permissionHolder.getIdLong()), null));

		List<Document> loggers = this.database.getGuildById(guild.getIdLong(), Projections.include("logger.loggers")).getEmbedded(List.of("logger", "loggers"), Collections.emptyList());

		if (guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			guild.retrieveAuditLogs().type(ActionType.CHANNEL_OVERRIDE_CREATE).queueAfter(LoggerHandler.DELAY, TimeUnit.MILLISECONDS, logs -> {
				User moderator = logs.stream()
					.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds() <= 5)
					.filter(e -> e.getTargetIdLong() == channel.getIdLong())
					.filter(e -> {
						AuditLogChange allow = e.getChangeByKey("allow"), deny = e.getChangeByKey("deny");

						int denyNew = deny == null ? (int) permissionOverride.getDeniedRaw() : deny.getNewValue(), denyOld = deny == null ? (int) event.getOldDenyRaw() : deny.getOldValue();
						int allowNew = allow == null ? (int) permissionOverride.getAllowedRaw() : allow.getNewValue(), allowOld = allow == null ? (int) event.getOldAllowRaw() : allow.getOldValue();

						return denyNew == permissionOverride.getDeniedRaw() && denyOld == event.getOldDenyRaw() && allowNew == permissionOverride.getAllowedRaw() && allowOld == event.getOldAllowRaw();
					})
					.map(AuditLogEntry::getUser)
					.findFirst()
					.orElse(null);

				LoggerContext loggerContext = new LoggerContext(event.isMemberOverride() ? event.getMember().getUser() : null, channel, event.isRoleOverride() ? event.getRole() : null, moderator);

				if (moderator != null) {
					description.append(String.format(" by **%s**", moderator.getAsTag()));
				}

				description.append(message);
				embed.setDescription(description.toString());

				this.manager.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
			});
		} else {
			LoggerContext loggerContext = new LoggerContext(event.isMemberOverride() ? event.getMember().getUser() : null, channel, event.isRoleOverride() ? event.getRole() : null);

			description.append(message);
			embed.setDescription(description.toString());

			this.manager.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
		}
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

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setColor(this.config.getRed());
		embed.setTimestamp(Instant.now());
		embed.setAuthor(new EmbedAuthor(guild.getName(), guild.getIconUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("%s ID: %s", roleOverride ? "Role" : "User", event.getPermissionHolder().getIdLong()), null));

		// wait for member leave or role delete event if needed
		this.delay(() -> {
			List<Document> loggers = this.database.getGuildById(guild.getIdLong(), Projections.include("logger.loggers")).getEmbedded(List.of("logger", "loggers"), Collections.emptyList());

			boolean deleted = (roleOverride ? event.getRole() : event.getMember()) == null;

			StringBuilder description = new StringBuilder(String.format("The %s %s has had permission overrides deleted for %s", LoggerUtility.getChannelTypeReadable(channelType), channelType == ChannelType.TEXT ? ((TextChannel) channel).getAsMention() : "`" + channel.getName() + "`", roleOverride ? (deleted ? "`" + role.getName() + "`" : role.getAsMention()) : "`" + member.getEffectiveName() + "`"));

			if (deleted) {
				description.append(String.format(" by **%s**", roleOverride ? "role deletion" : "member leave"));
			}

			if (!deleted && guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
				guild.retrieveAuditLogs().type(ActionType.CHANNEL_OVERRIDE_DELETE).queue(logs -> {
					User moderator = logs.stream()
						.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds() <= 5)
						.filter(e -> e.getTargetIdLong() == channel.getIdLong())
						.map(AuditLogEntry::getUser)
						.findFirst()
						.orElse(null);

					LoggerContext loggerContext = new LoggerContext(roleOverride ? null : member.getUser(), channel, roleOverride ? role : null, moderator);

					if (moderator != null) {
						description.append(String.format(" by **%s**", moderator.getAsTag()));
					}

					embed.setDescription(description.toString());

					this.manager.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
				});
			} else {
				LoggerContext loggerContext = new LoggerContext(roleOverride ? null : member.getUser(), channel, roleOverride ? role : null);

				embed.setDescription(description.toString());

				this.manager.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
			}
		});
	}

	public void onChannelDelete(GuildChannel channel, LoggerEvent loggerEvent) {
		Guild guild = channel.getGuild();
		String typeReadable = LoggerUtility.getChannelTypeReadable(channel.getType());

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("The %s `%s` has just been deleted", typeReadable, channel.getName()));
		embed.setColor(this.config.getRed());
		embed.setTimestamp(Instant.now());
		embed.setAuthor(new EmbedAuthor(guild.getName(), guild.getIconUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("%s ID: %s", channel.getType() == ChannelType.CATEGORY ? "Category" : "Channel", channel.getId()), null));

		List<Document> loggers = this.database.getGuildById(guild.getIdLong(), Projections.include("logger.loggers")).getEmbedded(List.of("logger", "loggers"), Collections.emptyList());

		if (guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			guild.retrieveAuditLogs().type(ActionType.CHANNEL_DELETE).queueAfter(LoggerHandler.DELAY, TimeUnit.MILLISECONDS, logs -> {
				User moderator = logs.stream()
					.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds() <= 5)
					.filter(e -> e.getTargetIdLong() == channel.getIdLong())
					.map(AuditLogEntry::getUser)
					.findFirst()
					.orElse(null);

				LoggerContext loggerContext = new LoggerContext(null, channel, null, moderator);

				if (moderator != null) {
					embed.setDescription(String.format("The %s `%s` has just been deleted by **%s**", typeReadable, channel.getName(),  moderator.getAsTag()));
				}

				this.manager.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
			});
		} else {
			LoggerContext loggerContext = new LoggerContext(null, channel, null);

			this.manager.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
		}
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

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("The %s `%s` has just been created", typeReadable, channel.getName()));
		embed.setColor(this.config.getGreen());
		embed.setTimestamp(Instant.now());
		embed.setAuthor(new EmbedAuthor(guild.getName(), guild.getIconUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("%s ID: %s", channel.getType() == ChannelType.CATEGORY ? "Category" : "Channel", channel.getId()), null));

		List<Document> loggers = this.database.getGuildById(guild.getIdLong(), Projections.include("logger.loggers")).getEmbedded(List.of("logger", "loggers"), Collections.emptyList());

		if (guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			guild.retrieveAuditLogs().type(ActionType.CHANNEL_CREATE).queueAfter(LoggerHandler.DELAY, TimeUnit.MILLISECONDS, logs -> {
				User moderator = logs.stream()
					.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds() <= 5)
					.filter(e -> e.getTargetIdLong() == channel.getIdLong())
					.map(AuditLogEntry::getUser)
					.findFirst()
					.orElse(null);

				LoggerContext loggerContext = new LoggerContext(null, channel, null, moderator);

				if (moderator != null) {
					embed.setDescription(String.format("The %s %s has just been created by **%s**", typeReadable, channelType == ChannelType.TEXT ? ((TextChannel) channel).getAsMention() : "`" + channel.getName() + "`",  moderator.getAsTag()));
				}

				this.manager.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
			});
		} else {
			LoggerContext loggerContext = new LoggerContext(null, channel, null);

			this.manager.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
		}
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

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("The %s `%s` has just been renamed", typeReadable, channel.getName()));
		embed.setColor(this.config.getOrange());
		embed.setTimestamp(Instant.now());
		embed.setAuthor(new EmbedAuthor(guild.getName(), guild.getIconUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("%s ID: %s", channel.getType() == ChannelType.CATEGORY ? "Category" : "Channel", channel.getId()), null));

		embed.addField(new EmbedField(false, "Before", String.format("`%s`", oldName)));
		embed.addField(new EmbedField(false, "After", String.format("`%s`", channel.getName())));

		List<Document> loggers = this.database.getGuildById(guild.getIdLong(), Projections.include("logger.loggers")).getEmbedded(List.of("logger", "loggers"), Collections.emptyList());

		if (guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			guild.retrieveAuditLogs().type(ActionType.CHANNEL_UPDATE).queueAfter(LoggerHandler.DELAY, TimeUnit.MILLISECONDS, logs -> {
				User moderator = logs.stream()
					.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds() <= 5)
					.filter(e -> e.getTargetIdLong() == channel.getIdLong())
					.filter(e -> e.getChangeByKey(AuditLogKey.CHANNEL_NAME) != null)
					.map(AuditLogEntry::getUser)
					.findFirst()
					.orElse(null);

				LoggerContext loggerContext = new LoggerContext(null, channel, null, moderator);

				if (moderator != null) {
					embed.setDescription(String.format("The %s %s has just been renamed by **%s**", typeReadable, channelType == ChannelType.TEXT ? ((TextChannel) channel).getAsMention() : "`" + channel.getName() + "`", moderator.getAsTag()));
				}

				this.manager.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
			});
		} else {
			LoggerContext loggerContext = new LoggerContext(null, channel, null);

			this.manager.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
		}
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

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("The role %s has been created", role.getAsMention()));
		embed.setColor(this.config.getGreen());
		embed.setTimestamp(Instant.now());
		embed.setAuthor(new EmbedAuthor(guild.getName(), guild.getIconUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("Role ID: %s", role.getId()), null));

		List<Document> loggers = this.database.getGuildById(guild.getIdLong(), Projections.include("logger.loggers")).getEmbedded(List.of("logger", "loggers"), Collections.emptyList());

		if (!role.isManaged() && guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			guild.retrieveAuditLogs().type(ActionType.ROLE_CREATE).queueAfter(LoggerHandler.DELAY, TimeUnit.MILLISECONDS, logs -> {
				User moderator = logs.stream()
					.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds() <= 5)
					.filter(e -> e.getTargetIdLong() == role.getIdLong())
					.map(AuditLogEntry::getUser)
					.findFirst()
					.orElse(null);

				LoggerContext loggerContext = new LoggerContext(null, null, role, moderator);

				if (moderator != null) {
					embed.setDescription(String.format("The role %s has been created by **%s**", role.getAsMention(), moderator.getAsTag()));
				}

				this.manager.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
			});
		} else {
			LoggerContext loggerContext = new LoggerContext(null, null, role);

			this.manager.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
		}
	}

	public void onRoleDelete(RoleDeleteEvent event) {
		Guild guild = event.getGuild();
		Role role = event.getRole();

		LoggerEvent loggerEvent = LoggerEvent.ROLE_DELETE;

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("The role `%s` has been deleted", role.getName()));
		embed.setColor(this.config.getRed());
		embed.setTimestamp(Instant.now());
		embed.setAuthor(new EmbedAuthor(guild.getName(), guild.getIconUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("Role ID: %s", role.getId()), null));

		List<Document> loggers = this.database.getGuildById(guild.getIdLong(), Projections.include("logger.loggers")).getEmbedded(List.of("logger", "loggers"), Collections.emptyList());

		if (!role.isManaged() && guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			guild.retrieveAuditLogs().type(ActionType.ROLE_DELETE).queueAfter(LoggerHandler.DELAY, TimeUnit.MILLISECONDS, logs -> {
				User moderator = logs.stream()
					.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds() <= 5)
					.filter(e -> e.getTargetIdLong() == role.getIdLong())
					.map(AuditLogEntry::getUser)
					.findFirst()
					.orElse(null);

				LoggerContext loggerContext = new LoggerContext(null, null, role, moderator);

				if (moderator != null) {
					embed.setDescription(String.format("The role `%s` has been deleted by **%s**", role.getName(), moderator.getAsTag()));
				}

				this.manager.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
			});
		} else {
			LoggerContext loggerContext = new LoggerContext(null, null, role);

			this.manager.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
		}
	}

	public void onRoleUpdateName(RoleUpdateNameEvent event) {
		Guild guild = event.getGuild();
		Role role = event.getRole();

		LoggerEvent loggerEvent = LoggerEvent.ROLE_NAME_UPDATE;

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("The role %s has been renamed", role.getAsMention()));
		embed.setColor(this.config.getOrange());
		embed.setTimestamp(Instant.now());
		embed.setAuthor(new EmbedAuthor(guild.getName(), guild.getIconUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("Role ID: %s", role.getId()), null));

		embed.addField(new EmbedField(false, "Before", String.format("`%s`", event.getOldName())));
		embed.addField(new EmbedField(false, "After", String.format("`%s`", event.getNewName())));

		List<Document> loggers = this.database.getGuildById(guild.getIdLong(), Projections.include("logger.loggers")).getEmbedded(List.of("logger", "loggers"), Collections.emptyList());

		if (guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			guild.retrieveAuditLogs().type(ActionType.ROLE_UPDATE).queueAfter(LoggerHandler.DELAY, TimeUnit.MILLISECONDS, logs -> {
				User moderator = logs.stream()
					.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds() <= 5)
					.filter(e -> e.getTargetIdLong() == role.getIdLong())
					.filter(e -> e.getChangeByKey(AuditLogKey.ROLE_NAME) != null)
					.map(AuditLogEntry::getUser)
					.findFirst()
					.orElse(null);

				LoggerContext loggerContext = new LoggerContext(null, null, role, moderator);

				if (moderator != null) {
					embed.setDescription(String.format("The role %s has been renamed by **%s**", role.getAsMention(), moderator.getAsTag()));
				}

				this.manager.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
			});
		} else {
			LoggerContext loggerContext = new LoggerContext(null, null, role);

			this.manager.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
		}
	}

	public void onRoleUpdateColor(RoleUpdateColorEvent event) {
		Guild guild = event.getGuild();
		Role role = event.getRole();

		LoggerEvent loggerEvent = LoggerEvent.ROLE_COLOUR_UPDATE;

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setDescription(String.format("The role %s has been given a new colour", role.getAsMention()));
		embed.setColor(this.config.getOrange());
		embed.setTimestamp(Instant.now());
		embed.setAuthor(new EmbedAuthor(guild.getName(), guild.getIconUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("Role ID: %s", role.getId()), null));

		int oldColour = event.getOldColorRaw(), newColour = event.getNewColorRaw();

		embed.addField(new EmbedField(false, "Before", String.format("Hex: [#%s](%3$s%1$s)\nRGB: [%2$s](%3$s%1$s)", ColourUtility.toHexString(oldColour), ColourUtility.toRGBString(oldColour), "https://image.sx4bot.co.uk/api/colour?w=1000&h=500&hex=")));
		embed.addField(new EmbedField(false, "After", String.format("Hex: [#%s](%3$s%1$s)\nRGB: [%2$s](%3$s%1$s)", ColourUtility.toHexString(newColour), ColourUtility.toRGBString(newColour), "https://image.sx4bot.co.uk/api/colour?w=1000&h=500&hex=")));

		List<Document> loggers = this.database.getGuildById(guild.getIdLong(), Projections.include("logger.loggers")).getEmbedded(List.of("logger", "loggers"), Collections.emptyList());

		if (guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			guild.retrieveAuditLogs().type(ActionType.ROLE_UPDATE).queueAfter(LoggerHandler.DELAY, TimeUnit.MILLISECONDS, logs -> {
				User moderator = logs.stream()
					.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds() <= 5)
					.filter(e -> e.getTargetIdLong() == role.getIdLong())
					.filter(e -> e.getChangeByKey(AuditLogKey.ROLE_COLOR) != null)
					.map(AuditLogEntry::getUser)
					.findFirst()
					.orElse(null);

				LoggerContext loggerContext = new LoggerContext(null, null, role, moderator);

				if (moderator != null) {
					embed.setDescription(String.format("The role %s has been given a new colour by **%s**", role.getAsMention(), moderator.getAsTag()));
				}

				this.manager.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
			});
		} else {
			LoggerContext loggerContext = new LoggerContext(null, null, role);

			this.manager.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
		}
	}

	public void onRoleUpdatePermissions(RoleUpdatePermissionsEvent event) {
		Guild guild = event.getGuild();
		Role role = event.getRole();

		LoggerEvent loggerEvent = LoggerEvent.ROLE_PERMISSION_UPDATE;

		String permissionMessage = LoggerUtility.getRolePermissionDifference(event.getOldPermissionsRaw(), event.getNewPermissionsRaw());
		if (permissionMessage.isEmpty()) {
			return;
		}

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setColor(this.config.getOrange());
		embed.setTimestamp(Instant.now());
		embed.setAuthor(new EmbedAuthor(guild.getName(), guild.getIconUrl(), null));
		embed.setFooter(new EmbedFooter(String.format("Role ID: %s", role.getId()), null));

		List<Document> loggers = this.database.getGuildById(guild.getIdLong(), Projections.include("logger.loggers")).getEmbedded(List.of("logger", "loggers"), Collections.emptyList());

		StringBuilder description = new StringBuilder(String.format("The role %s has had permission changes made", role.getAsMention()));
		if (guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			guild.retrieveAuditLogs().type(ActionType.ROLE_UPDATE).queueAfter(LoggerHandler.DELAY, TimeUnit.MILLISECONDS, logs -> {
				User moderator = logs.stream()
					.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds() <= 5)
					.filter(e -> e.getTargetIdLong() == role.getIdLong())
					.filter(e -> e.getChangeByKey(AuditLogKey.ROLE_PERMISSIONS) != null)
					.map(AuditLogEntry::getUser)
					.findFirst()
					.orElse(null);

				LoggerContext loggerContext = new LoggerContext(null, null, role, moderator);

				if (moderator != null) {
					description.append(String.format(" by **%s**", moderator.getAsTag()));
				}

				description.append(permissionMessage);
				embed.setDescription(description.toString());

				this.manager.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
			});
		} else {
			description.append(permissionMessage);
			embed.setDescription(description.toString());

			LoggerContext loggerContext = new LoggerContext(null, null, role);

			this.manager.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
		}
	}

	public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent event) {
		Guild guild = event.getGuild();
		Member member = event.getMember();
		User user = event.getUser();

		List<Role> roles = event.getRoles();
		Role firstRole = roles.get(0);

		LoggerEvent loggerEvent = LoggerEvent.MEMBER_ROLE_ADD;

		StringBuilder description = new StringBuilder();

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setColor(this.config.getGreen());
		embed.setTimestamp(Instant.now());
		embed.setAuthor(new EmbedAuthor(user.getAsTag(), user.getEffectiveAvatarUrl(), null));

		boolean multiple = roles.size() > 1;
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

			description.append(String.format("The roles %s have been added to `%s`", builder.toString(), member.getEffectiveName()));
		} else {
			description.append(String.format("The role %s has been added to `%s`", firstRole.getAsMention(), member.getEffectiveName()));
			embed.setFooter(new EmbedFooter(String.format("Role ID: %s", firstRole.getId()), null));
		}

		List<Document> loggers = this.database.getGuildById(guild.getIdLong(), Projections.include("logger.loggers")).getEmbedded(List.of("logger", "loggers"), Collections.emptyList());

		if (!firstRole.isManaged() && guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			guild.retrieveAuditLogs().type(ActionType.MEMBER_ROLE_UPDATE).queueAfter(LoggerHandler.DELAY, TimeUnit.MILLISECONDS, logs -> {
				User moderator = logs.stream()
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

				LoggerContext loggerContext = new LoggerContext(user, null, multiple ? null : firstRole, moderator);

				if (moderator != null) {
					description.append(String.format(" by **%s**", moderator.getAsTag()));
				}

				embed.setDescription(description.toString());

				this.manager.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
			});
		} else {
			embed.setDescription(description.toString());

			LoggerContext loggerContext = new LoggerContext(user, null, multiple ? null : firstRole);

			this.manager.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
		}
	}

	public void onGuildMemberRoleRemove(GuildMemberRoleRemoveEvent event) {
		Guild guild = event.getGuild();
		Member member = event.getMember();
		User user = event.getUser();

		List<Role> roles = event.getRoles();
		Role firstRole = roles.get(0);

		LoggerEvent loggerEvent = LoggerEvent.MEMBER_ROLE_REMOVE;

		// Ensure role delete event has been sent just in case
		this.delay(() -> {
			StringBuilder description = new StringBuilder();

			WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
			embed.setColor(this.config.getGreen());
			embed.setTimestamp(Instant.now());
			embed.setAuthor(new EmbedAuthor(user.getAsTag(), user.getEffectiveAvatarUrl(), null));

			boolean multiple = roles.size() > 1, deleted = false;
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

				description.append(String.format("The roles %s have been removed from `%s`", builder.toString(), member.getEffectiveName()));
			} else {
				deleted = guild.getRoleById(firstRole.getIdLong()) == null;

				description.append(String.format("The role %s has been removed from `%s`", deleted ? "`" + firstRole.getName() + "`" : firstRole.getAsMention(), member.getEffectiveName()));
				embed.setFooter(new EmbedFooter(String.format("Role ID: %s", firstRole.getId()), null));

				if (deleted) {
					description.append(" by **role deletion**");
				}
			}

			List<Document> loggers = this.database.getGuildById(guild.getIdLong(), Projections.include("logger.loggers")).getEmbedded(List.of("logger", "loggers"), Collections.emptyList());

			if (!deleted && !firstRole.isManaged() && guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
				guild.retrieveAuditLogs().type(ActionType.MEMBER_ROLE_UPDATE).queue(logs -> {
					User moderator = logs.stream()
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

					LoggerContext loggerContext = new LoggerContext(user, null, multiple ? null : firstRole, moderator);

					if (moderator != null) {
						description.append(String.format(" by **%s**", moderator.getAsTag()));
					}

					embed.setDescription(description.toString());

					this.manager.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
				});
			} else {
				embed.setDescription(description.toString());

				LoggerContext loggerContext = new LoggerContext(user, null, multiple ? null : firstRole);

				this.manager.queue(guild, loggers, loggerEvent, loggerContext, embed.build());
			}
		});
	}
}
