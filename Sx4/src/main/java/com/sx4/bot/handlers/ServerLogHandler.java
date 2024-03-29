package com.sx4.bot.handlers;

import com.sx4.bot.core.Sx4;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.utility.TimeUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.sharding.ShardManager;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.bson.Document;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ServerLogHandler implements EventListener {

	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	private WebhookClient<Message> webhook;

	private final Sx4 bot;

	public ServerLogHandler(Sx4 bot) {
		this.bot = bot;

		this.postGuildCount();
	}

	private WebhookClient<Message> getWebhook(JDA jda) {
		if (this.webhook == null) {
			this.webhook = WebhookClient.createClient(jda, Long.toString(this.bot.getConfig().getGuildsWebhookId()), this.bot.getConfig().getGuildsWebhookToken());
		}

		return this.webhook;
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
		}, 30, 30, TimeUnit.MINUTES);
	}

	public void onGuildJoin(GuildJoinEvent event) {
		ShardManager manager = event.getJDA().getShardManager();
		Member owner = event.getGuild().getOwner();

		EmbedBuilder embed = new EmbedBuilder()
			.setColor(this.bot.getConfig().getGreen())
			.setThumbnail(event.getGuild().getIconUrl())
			.setTimestamp(Instant.now())
			.setDescription(String.format("I am now in %,d servers and connected to %,d users", manager.getGuildCache().size(), manager.getUserCache().size()))
			.setAuthor("Joined Server!", null, event.getJDA().getSelfUser().getEffectiveAvatarUrl())
			.addField("Server Name", event.getGuild().getName(), true)
			.addField("Server ID", event.getGuild().getId(), true)
			.addField("Server Owner", (owner == null ? "Anonymous#0000" : owner.getUser().getAsTag()) + "\n" + event.getGuild().getOwnerId(), true)
			.addField("Server Members", String.format("%,d member%s", event.getGuild().getMemberCount(), event.getGuild().getMemberCount() == 1 ? "" : "s"), true);

		this.getWebhook(event.getJDA()).sendMessageEmbeds(embed.build()).queue();

		if (event.getGuild().getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			event.getGuild().retrieveAuditLogs().type(ActionType.BOT_ADD).queueAfter(500, TimeUnit.MILLISECONDS, audits -> {
				AuditLogEntry audit = audits.stream()
					.filter(auditLog -> auditLog.getTargetIdLong() == event.getJDA().getSelfUser().getIdLong())
					.filter(auditLog -> Duration.between(auditLog.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds() <= 5)
					.findFirst()
					.orElse(null);

				if (audit != null) {
					User bot = event.getJDA().getSelfUser();

					MessageEmbed joinEmbed = new EmbedBuilder()
						.setAuthor("Thank you for adding Sx4", null, bot.getEffectiveAvatarUrl())
						.addField("Information", "The default prefix for Sx4 is `s?` however if you have changed it and forgot your prefix use `@" + bot.getAsTag() + " prefix` to check your current prefixes.\n\nAll of Sx4's commands can be found in `s?help`", false)
						.addField("Need Extra Help?", "Check out and ask your question in the [support server](https://discord.gg/PqJNcfB)", false)
						.addField("Enjoying Sx4?", "Find yourself using Sx4 a lot, then maybe buying premium might be for you or just simply donating to support the hosting of Sx4, feel free to check out our [patreon](https://patreon.com/Sx4) which lists the perks you get for donating", false)
						.build();

					audit.getUser().openPrivateChannel()
						.flatMap(channel -> channel.sendMessageEmbeds(joinEmbed))
						.queue();
				}
			});
		}
	}

	public void onGuildLeave(GuildLeaveEvent event) {
		ShardManager manager = event.getJDA().getShardManager();
		Member owner = event.getGuild().getOwner();

		EmbedBuilder embed = new EmbedBuilder()
			.setColor(this.bot.getConfig().getRed())
			.setThumbnail(event.getGuild().getIconUrl())
			.setTimestamp(Instant.now())
			.setDescription(String.format("I am now in %,d servers and connected to %,d users", manager.getGuildCache().size(), manager.getUserCache().size()))
			.setAuthor("Left Server!", null, event.getJDA().getSelfUser().getEffectiveAvatarUrl())
			.addField("Server Name", event.getGuild().getName(), true)
			.addField("Server ID", event.getGuild().getId(), true)
			.addField("Server Owner", (owner == null ? "Anonymous#0000" : owner.getUser().getAsTag()) + "\n" + event.getGuild().getOwnerId(), true)
			.addField("Server Members", String.format("%,d member%s", event.getGuild().getMemberCount(), event.getGuild().getMemberCount() == 1 ? "" : "s"), true)
			.addField("Stayed for", TimeUtility.LONG_TIME_FORMATTER.parse(Duration.between(event.getGuild().getSelfMember().getTimeJoined(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds()), false);

		this.getWebhook(event.getJDA()).sendMessageEmbeds(embed.build()).queue();
	}

	@Override
	public void onEvent(@NotNull GenericEvent event) {
		if (event instanceof GuildJoinEvent) {
			this.onGuildJoin((GuildJoinEvent) event);
		} else if (event instanceof GuildLeaveEvent) {
			this.onGuildLeave((GuildLeaveEvent) event);
		}
	}
}
