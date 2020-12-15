package com.sx4.bot.entities.management;

import club.minnced.discord.webhook.send.WebhookEmbed;
import club.minnced.discord.webhook.send.WebhookEmbed.EmbedAuthor;
import club.minnced.discord.webhook.send.WebhookEmbed.EmbedField;
import club.minnced.discord.webhook.send.WebhookEmbed.EmbedFooter;
import club.minnced.discord.webhook.send.WebhookEmbedBuilder;
import com.sx4.bot.core.Sx4;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.List;

public class Suggestion {

	private final ObjectId id;

	private long messageId;
	private final long channelId;
	private final long guildId;
	private final long authorId;
	private final long moderatorId;

	private final String reason;
	private final String suggestion;
	private final String state;

	public Suggestion(long channelId, long guildId, long authorId, String suggestion, String state) {
		this(ObjectId.get(), 0L, channelId, guildId, authorId, suggestion, state);
	}

	private Suggestion(Document data) {
		this.id = data.getObjectId("_id");
		this.messageId = data.get("messageId", 0L);
		this.channelId = data.get("channelId", 0L);
		this.guildId = data.get("guildId", 0L);
		this.authorId = data.get("authorId", 0L);
		this.moderatorId = data.get("moderatorId", 0L);
		this.reason = data.getString("reason");
		this.suggestion = data.getString("suggestion");
		this.state = data.getString("state");
	}

	public Suggestion(ObjectId id, long messageId, long channelId, long guildId, long authorId, String suggestion, String state) {
		this.id = id;
		this.messageId = messageId;
		this.channelId = channelId;
		this.guildId = guildId;
		this.authorId = authorId;
		this.moderatorId = 0L;
		this.reason = null;
		this.suggestion = suggestion;
		this.state = state;
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

	public boolean hasMessageId() {
		return this.messageId != 0L;
	}

	public long getMessageId() {
		return this.messageId;
	}

	public Suggestion setMessageId(long messageId) {
		this.messageId = messageId;

		return this;
	}

	public long getGuildId() {
		return this.guildId;
	}

	public Guild getGuild() {
		return Sx4.get().getShardManager().getGuildById(this.guildId);
	}

	public long getChannelId() {
		return this.channelId;
	}

	public TextChannel getChannel() {
		return this.getChannel(this.getGuild());
	}

	public TextChannel getChannel(Guild guild) {
		return guild == null ? null : guild.getTextChannelById(this.channelId);
	}

	public long getAuthorId() {
		return this.authorId;
	}

	public User getAuthor() {
		return Sx4.get().getShardManager().getUserById(this.authorId);
	}

	public long getModeratorId() {
		return this.moderatorId;
	}

	public User getModerator() {
		if (this.moderatorId == 0L) {
			return null;
		}

		return Sx4.get().getShardManager().getUserById(this.moderatorId);
	}

	public String getReason() {
		return this.reason;
	}

	public String getSuggestion() {
		return this.suggestion;
	}

	public String getState() {
		return this.state;
	}

	public SuggestionState getFullState(List<Document> states) {
		Document state = states.stream()
			.filter(d -> d.getString("dataName").equals(this.state))
			.findFirst()
			.orElse(null);

		if (state == null) {
			return null;
		}

		return new SuggestionState(state);
	}

	public MessageEmbed getEmbed(User moderator, User author, SuggestionState state) {
		EmbedBuilder embed = new EmbedBuilder()
			.setAuthor(author == null ? "Anonymous#0000" : author.getAsTag(), null, author == null ? null : author.getEffectiveAvatarUrl())
			.setDescription(this.suggestion)
			.setFooter(String.format("%s | ID: %s", state.getName(), this.getHex()))
			.setColor(state.getColour())
			.setTimestamp(Instant.ofEpochSecond(this.getTimestamp()));

		if (moderator != null) {
			embed.addField("Moderator", moderator.getAsTag(), true);
		}

		if (this.reason != null) {
			embed.addField("Reason", this.reason, true);
		}

		return embed.build();
	}

	public MessageEmbed getEmbed(SuggestionState state) {
		return this.getEmbed(this.getModerator(), this.getAuthor(), state);
	}

	public WebhookEmbed getWebhookEmbed(User moderator, User author, SuggestionState state) {
		WebhookEmbedBuilder embed = new WebhookEmbedBuilder()
			.setAuthor(new EmbedAuthor(author == null ? "Anonymous#0000" : author.getAsTag(), author == null ? null : author.getEffectiveAvatarUrl(), null))
			.setDescription(this.suggestion)
			.setFooter(new EmbedFooter(String.format("%s | ID: %s", state.getName(), this.getHex()), null))
			.setColor(state.getColour())
			.setTimestamp(Instant.ofEpochSecond(this.getTimestamp()));

		if (moderator != null) {
			embed.addField(new EmbedField(true, "Moderator", moderator.getAsTag()));
		}

		if (this.reason != null) {
			embed.addField(new EmbedField(true, "Reason", this.reason));
		}

		return embed.build();
	}

	public WebhookEmbed getWebhookEmbed(SuggestionState state) {
		return this.getWebhookEmbed(this.getModerator(), this.getAuthor(), state);
	}

	public Document toData() {
		return new Document("_id", this.id)
			.append("guildId", this.guildId)
			.append("channelId", this.channelId)
			.append("authorId", this.authorId)
			.append("messageId", this.messageId)
			.append("moderatorId", this.moderatorId)
			.append("reason", this.reason)
			.append("suggestion", this.suggestion)
			.append("state", this.state);
	}

	public static Suggestion fromData(Document data) {
		return new Suggestion(data);
	}

}
