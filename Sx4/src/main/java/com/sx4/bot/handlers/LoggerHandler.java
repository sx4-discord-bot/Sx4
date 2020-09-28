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
import com.sx4.bot.utility.LoggerUtility;
import com.sx4.bot.utility.StringUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceJoinEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageDeleteEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.bson.Document;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class LoggerHandler extends ListenerAdapter {

	private static final int DELAY = 500;

	private final Map<Long, Map<Long, Integer>> disconnectCache = new HashMap<>();

	private final LoggerManager manager = LoggerManager.get();
	private final Database database = Database.get();
	private final Config config = Config.get();

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
					.filter(e -> Duration.between(e.getTimeCreated(), ZonedDateTime.now()).toSeconds() <= 5)
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
}
