package com.sx4.bot.entities.mod;

import club.minnced.discord.webhook.send.WebhookEmbed;
import club.minnced.discord.webhook.send.WebhookEmbed.EmbedField;
import club.minnced.discord.webhook.send.WebhookEmbed.EmbedFooter;
import club.minnced.discord.webhook.send.WebhookEmbed.EmbedTitle;
import club.minnced.discord.webhook.send.WebhookEmbedBuilder;
import com.sx4.bot.entities.mod.action.Action;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.time.Instant;

public class ModLog {
	
	private final ObjectId id;
	
	private long messageId;
	private final long channelId;
	private final long guildId;
	private final long targetId;
	private final long moderatorId;

	private long webhookId;
	private String webhookToken;
	
	private final Action action;
	
	private final Reason reason;
	
	public ModLog(long channelId, long guildId, long targetId, long moderatorId, Reason reason, Action action) {
		this(ObjectId.get(), 0L, channelId, guildId, targetId, moderatorId, 0L, null, reason, action);
	}
	
	private ModLog(Document data) {
		this.id = data.getObjectId("_id");
		this.messageId = data.get("messageId", 0L);
		this.channelId = data.get("channelId", 0L);
		this.guildId = data.get("guildId",0L);
		this.targetId = data.getLong("targetId");
		this.moderatorId = data.getLong("moderatorId");

		Document webhook = data.get("webhook", Document.class);
		this.webhookId = webhook.getLong("id");
		this.webhookToken = webhook.getString("token");
		
		String reason = data.getString("reason");
		this.reason = reason == null ? null : new Reason(reason);
		
		this.action = Action.fromData(data.get("action", Document.class));
	}
	
	public ModLog(ObjectId id, long messageId, long channelId, long guildId, long targetId, long moderatorId, long webhookId, String webhookToken,  Reason reason, Action action) {
		this.id = id;
		this.messageId = messageId;
		this.channelId = channelId;
		this.guildId = guildId;
		this.targetId = targetId;
		this.moderatorId = moderatorId;
		this.webhookId = webhookId;
		this.webhookToken = webhookToken;
		this.reason = reason;
		this.action = action;
	}
	
	public ObjectId getId() {
		return this.id;
	}
	
	public String getHex() {
		return this.id.toHexString();
	}
	
	public long getTimestamp() {
		return this.id.getTimestamp();
	}

	public long getWebhookId() {
		return this.webhookId;
	}

	public ModLog setWebhookId(long webhookId) {
		this.webhookId = webhookId;

		return this;
	}

	public String getWebhookToken() {
		return this.webhookToken;
	}

	public ModLog setWebhookToken(String webhookToken) {
		this.webhookToken = webhookToken;

		return this;
	}
	
	public long getMessageId() {
		return this.messageId;
	}
	
	public ModLog setMessageId(long messageId) {
		this.messageId = messageId;
		
		return this;
	}
	
	public long getGuildId() {
		return this.guildId;
	}
	
	public Guild getGuild(ShardManager manager) {
		if (this.guildId == 0L) {
			return null;
		}

		return manager.getGuildById(this.guildId);
	}
	
	public long getChannelId() {
		return this.channelId;
	}
	
	public GuildMessageChannel getChannel(ShardManager manager) {
		if (this.channelId == 0L) {
			return null;
		}

		Guild guild = this.getGuild(manager);
		
		return guild == null ? null : guild.getChannelById(GuildMessageChannel.class, this.channelId);
	}
	
	public long getTargetId() {
		return this.targetId;
	}
	
	public User getTarget(ShardManager manager) {
		return manager.getUserById(this.targetId);
	}
	
	public long getModeratorId() {
		return this.moderatorId;
	}
	
	public User getModerator(ShardManager manager) {
		return manager.getUserById(this.moderatorId);
	}
	
	public Reason getReason() {
		return this.reason;
	}
	
	public Action getAction() {
		return this.action;
	}
	
	public MessageEmbed getEmbed(User moderator, User target) {
		EmbedBuilder embed = new EmbedBuilder();
		embed.setTitle(this.action.toString());
		embed.addField("Target", (target == null ? "Unknown User" : target.getAsTag()) + " (" + this.getTargetId() + ")", false);
		embed.addField("Moderator", (moderator == null ? "Unknown User" : moderator.getAsTag()) + " (" + this.getModeratorId() + ")", false);
		embed.addField("Reason", this.reason == null ? "None Given" : this.reason.getParsed(), false);
		embed.setTimestamp(Instant.ofEpochSecond(this.getTimestamp()));
		embed.setFooter("ID: " + this.getHex());
		
		return embed.build();
	}
	
	public MessageEmbed getEmbed(ShardManager manager) {
		return this.getEmbed(this.getModerator(manager), this.getTarget(manager));
	}

	public WebhookEmbed getWebhookEmbed(User moderator, User target) {
		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setTitle(new EmbedTitle(this.action.toString(), null));
		embed.addField(new EmbedField(false, "Target", (target == null || target.getClass() == UserReference.class ? "Anonymous#0000" : target.getAsTag()) + " (" + this.getTargetId() + ")"));
		embed.addField(new EmbedField(false, "Moderator", (moderator == null || moderator.getClass() == UserReference.class ? "Anonymous#0000" : moderator.getAsTag()) + " (" + this.getModeratorId() + ")"));
		embed.addField(new EmbedField(false, "Reason", this.reason == null ? "None Given" : this.reason.getParsed()));
		embed.setTimestamp(Instant.ofEpochSecond(this.getTimestamp()));
		embed.setFooter(new EmbedFooter("ID: " + this.getHex(), null));

		return embed.build();
	}

	public WebhookEmbed getWebhookEmbed(ShardManager manager) {
		return this.getWebhookEmbed(this.getModerator(manager), this.getTarget(manager));
	}

	public Document toData() {
		return new Document("_id", this.id)
			.append("guildId", this.guildId)
			.append("channelId", this.channelId)
			.append("targetId", this.targetId)
			.append("messageId", this.messageId)
			.append("moderatorId", this.moderatorId)
			.append("webhook", new Document("id", this.webhookId).append("token", this.webhookToken))
			.append("reason", this.reason == null ? null : this.reason.getParsed())
			.append("action", this.action.toData());
	}

	public static ModLog fromData(Document data) {
		return new ModLog(data);
	}
	
}
