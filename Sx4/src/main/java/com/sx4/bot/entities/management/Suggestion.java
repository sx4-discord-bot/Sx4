package com.sx4.bot.entities.management;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion;
import net.dv8tion.jda.api.sharding.ShardManager;
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
	private final String image;
	private final String state;

	public Suggestion(long channelId, long guildId, long authorId, String suggestion, String image, String state) {
		this(ObjectId.get(), 0L, channelId, guildId, authorId, suggestion, image, state);
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
		this.image = data.getString("image");
		this.state = data.getString("state");
	}

	public Suggestion(ObjectId id, long messageId, long channelId, long guildId, long authorId, String suggestion, String image, String state) {
		this.id = id;
		this.messageId = messageId;
		this.channelId = channelId;
		this.guildId = guildId;
		this.authorId = authorId;
		this.moderatorId = 0L;
		this.reason = null;
		this.suggestion = suggestion;
		this.image = image;
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

	public Guild getGuild(ShardManager manager) {
		return manager.getGuildById(this.guildId);
	}

	public long getChannelId() {
		return this.channelId;
	}

	public GuildMessageChannelUnion getChannel(ShardManager manager) {
		return this.getChannel(this.getGuild(manager));
	}

	public GuildMessageChannelUnion getChannel(Guild guild) {
		return guild == null ? null : guild.getChannelById(GuildMessageChannelUnion.class, this.channelId);
	}

	public long getAuthorId() {
		return this.authorId;
	}

	public User getAuthor(ShardManager manager) {
		return manager.getUserById(this.authorId);
	}

	public long getModeratorId() {
		return this.moderatorId;
	}

	public User getModerator(ShardManager manager) {
		if (this.moderatorId == 0L) {
			return null;
		}

		return manager.getUserById(this.moderatorId);
	}

	public String getReason() {
		return this.reason;
	}

	public String getSuggestion() {
		return this.suggestion;
	}

	public String getImage() {
		return this.image;
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
		return Suggestion.getEmbed(this.id, moderator, author, this.suggestion, this.image, this.reason, state);
	}

	public MessageEmbed getEmbed(ShardManager manager, SuggestionState state) {
		return this.getEmbed(this.getModerator(manager), this.getAuthor(manager), state);
	}

	public Document toData() {
		Document data = new Document("_id", this.id)
			.append("guildId", this.guildId)
			.append("channelId", this.channelId)
			.append("authorId", this.authorId)
			.append("suggestion", this.suggestion)
			.append("state", this.state);

		if (this.image != null) {
			data.append("image", this.image);
		}

		if (this.moderatorId != 0L) {
			data.append("moderatorId", this.moderatorId);
		}

		if (this.messageId != 0L) {
			data.append("messageId", this.messageId);
		}

		if (this.reason != null) {
			data.append("reason", this.reason);
		}

		return data;
	}

	public static Suggestion fromData(Document data) {
		return new Suggestion(data);
	}

	public static MessageEmbed getEmbed(ObjectId id, User moderator, User author, String suggestion, String image, String reason, SuggestionState state) {
		EmbedBuilder embed = new EmbedBuilder()
			.setAuthor(author == null ? "Anonymous#0000" : author.getAsTag(), null, author == null ? null : author.getEffectiveAvatarUrl())
			.setDescription(suggestion)
			.setFooter(String.format("%s | ID: %s", state.getName(), id.toHexString()))
			.setColor(state.getColour())
			.setTimestamp(Instant.ofEpochSecond(id.getTimestamp()));

		if (image != null) {
			embed.setImage(image);
		}

		if (moderator != null) {
			embed.addField("Moderator", moderator.getAsTag(), true);
		}

		if (reason != null) {
			embed.addField("Reason", reason, true);
		}

		return embed.build();
	}

}
