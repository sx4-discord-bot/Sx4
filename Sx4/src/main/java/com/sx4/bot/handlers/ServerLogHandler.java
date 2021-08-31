package com.sx4.bot.handlers;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.send.WebhookEmbed;
import club.minnced.discord.webhook.send.WebhookEmbedBuilder;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.utility.TimeUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.sharding.ShardManager;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.bson.Document;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ServerLogHandler implements EventListener {

	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	private final WebhookClient webhook;

	private final Sx4 bot;

	public ServerLogHandler(Sx4 bot) {
		this.bot = bot;

		this.webhook = new WebhookClientBuilder(this.bot.getConfig().getGuildsWebhookId(), this.bot.getConfig().getGuildsWebhookToken())
			.setHttpClient(this.bot.getHttpClient())
			.build();

		this.postGuildCount();
	}

	public void postGuildCount() {
		if (this.bot.getConfig().isCanary()) {
			return;
		}

		this.executor.scheduleAtFixedRate(() -> {
			ShardManager manager = this.bot.getShardManager();

			long guildCount = manager.getGuildCache().size();
			int shardCount = manager.getShardsTotal();

			Document topGGData = new Document()
				.append("server_count", guildCount)
				.append("shard_count", shardCount);

			Request topGGRequest = new Request.Builder()
				.post(RequestBody.create(MediaType.parse("application/json"), topGGData.toJson()))
				.url("https://top.gg/api/bots/440996323156819968/stats")
				.addHeader("Authorization", this.bot.getConfig().getTopGG())
				.addHeader("Content-Type", "application/json")
				.build();

			this.bot.getHttpClient().newCall(topGGRequest).enqueue((HttpCallback) response -> {
				System.out.println("Posted guild count to top.gg");
				response.close();
			});

			Document discordListData = new Document()
				.append("serverCount", guildCount);

			Request discordListRequest = new Request.Builder()
				.post(RequestBody.create(MediaType.parse("application/json"), discordListData.toJson()))
				.url("https://api.discordlist.space/v2/bots/440996323156819968")
				.addHeader("Authorization", "Bot " + this.bot.getConfig().getDiscordListSpace())
				.addHeader("Content-Type", "application/json")
				.build();

			this.bot.getHttpClient().newCall(discordListRequest).enqueue((HttpCallback) response -> {
				System.out.println("Posted guild count to discordlist.space");
				response.close();
			});
		}, 30, 30, TimeUnit.MINUTES);
	}

	public void onGuildJoin(GuildJoinEvent event) {
		ShardManager manager = event.getJDA().getShardManager();
		Member owner = event.getGuild().getOwner();

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder()
			.setColor(this.bot.getConfig().getGreen())
			.setThumbnailUrl(event.getGuild().getIconUrl())
			.setTimestamp(Instant.now())
			.setDescription(String.format("I am now in %,d servers and connected to %,d users", manager.getGuildCache().size(), manager.getUserCache().size()))
			.setAuthor(new WebhookEmbed.EmbedAuthor("Joined Server!", event.getJDA().getSelfUser().getEffectiveAvatarUrl(), null))
			.addField(new WebhookEmbed.EmbedField(true, "Server Name", event.getGuild().getName()))
			.addField(new WebhookEmbed.EmbedField(true, "Server ID", event.getGuild().getId()))
			.addField(new WebhookEmbed.EmbedField(true, "Server Owner", (owner == null ? "Anonymous#0000" : owner.getUser().getAsTag()) + "\n" + event.getGuild().getOwnerId()))
			.addField(new WebhookEmbed.EmbedField(true, "Server Members", String.format("%,d member%s", event.getGuild().getMemberCount(), event.getGuild().getMemberCount() == 1 ? "" : "s")));

		this.webhook.send(embed.build());

		if (event.getGuild().getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			event.getGuild().retrieveAuditLogs().type(ActionType.BOT_ADD).queueAfter(500, TimeUnit.MILLISECONDS, audits -> {
				AuditLogEntry audit = audits.stream()
					.filter(auditLog -> auditLog.getTargetIdLong() == event.getJDA().getSelfUser().getIdLong())
					.filter(auditLog -> Duration.between(auditLog.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds() <= 5)
					.findFirst()
					.orElse(null);

				if (audit != null) {
					audit.getUser().openPrivateChannel()
						.flatMap(channel -> channel.sendMessage("Thanks for adding me to your server!\nThe default prefix is `s?` however if that doesn't work use `@Sx4#1617 prefix` to check your current prefixes\nAll my info and commands can be found in `s?help`\nIf you need any help feel free to join the support server: https://discord.gg/PqJNcfB"))
						.queue();
				}
			});
		}
	}

	public void onGuildLeave(GuildLeaveEvent event) {
		ShardManager manager = event.getJDA().getShardManager();
		Member owner = event.getGuild().getOwner();

		WebhookEmbedBuilder embed = new WebhookEmbedBuilder()
			.setColor(this.bot.getConfig().getRed())
			.setThumbnailUrl(event.getGuild().getIconUrl())
			.setTimestamp(Instant.now())
			.setDescription(String.format("I am now in %,d servers and connected to %,d users", manager.getGuildCache().size(), manager.getUserCache().size()))
			.setAuthor(new WebhookEmbed.EmbedAuthor("Left Server!", event.getJDA().getSelfUser().getEffectiveAvatarUrl(), null))
			.addField(new WebhookEmbed.EmbedField(true, "Server Name", event.getGuild().getName()))
			.addField(new WebhookEmbed.EmbedField(true, "Server ID", event.getGuild().getId()))
			.addField(new WebhookEmbed.EmbedField(true, "Server Owner", (owner == null ? "Anonymous#0000" : owner.getUser().getAsTag()) + "\n" + event.getGuild().getOwnerId()))
			.addField(new WebhookEmbed.EmbedField(true, "Server Members", String.format("%,d member%s", event.getGuild().getMemberCount(), event.getGuild().getMemberCount() == 1 ? "" : "s")))
			.addField(new WebhookEmbed.EmbedField(false, "Stayed for", TimeUtility.LONG_TIME_FORMATTER.parse(Duration.between(event.getGuild().getSelfMember().getTimeJoined(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds())));

		this.webhook.send(embed.build());
	}

	@Override
	public void onEvent(GenericEvent event) {
		if (event instanceof GuildJoinEvent) {
			this.onGuildJoin((GuildJoinEvent) event);
		} else if (event instanceof GuildLeaveEvent) {
			this.onGuildLeave((GuildLeaveEvent) event);
		}
	}
}
