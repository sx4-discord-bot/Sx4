package com.sx4.bot.entities.mod.modlog;

import com.sx4.bot.core.Sx4;
import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.entities.mod.action.Action;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
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
	
	private final Action action;
	
	private final Reason reason;
	
	public ModLog(long channelId, long guildId, long targetId, long moderatorId, Reason reason, Action action) {
		this(ObjectId.get(), 0L, channelId, guildId, targetId, moderatorId, reason, action);
	}
	
	public ModLog(Document data) {
		this.id = data.getObjectId("_id");
		this.messageId = data.get("messageId", 0L);
		this.channelId = data.get("channelId", 0L);
		this.guildId = data.get("guildId", 0L);
		this.targetId = data.get("targetId", 0L);
		this.moderatorId = data.get("moderatorId", 0L);
		
		String reason = data.getString("reason");
		this.reason = reason == null ? null : new Reason(reason);
		
		this.action = Action.fromData(data);
	}
	
	public ModLog(ObjectId id, long messageId, long channelId, long guildId, long targetId, long moderatorId, Reason reason, Action action) {
		this.id = id;
		this.messageId = messageId;
		this.channelId = channelId;
		this.guildId = guildId;
		this.targetId = targetId;
		this.moderatorId = moderatorId;
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
	
	public boolean hasMessageId() {
		return this.messageId != 0L;
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
	
	public Guild getGuild() {
		return Sx4.get().getShardManager().getGuildById(this.guildId);
	}
	
	public long getChannelId() {
		return this.channelId;
	}
	
	public TextChannel getChannel() {
		Guild guild = this.getGuild();
		
		return guild == null ? null : guild.getTextChannelById(this.channelId);
	}
	
	public long getTargetId() {
		return this.targetId;
	}
	
	public User getTarget() {
		return Sx4.get().getShardManager().getUserById(this.targetId);
	}
	
	public long getModeratorId() {
		return this.moderatorId;
	}
	
	public User getModerator() {
		return Sx4.get().getShardManager().getUserById(this.moderatorId);
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
	
	public MessageEmbed getEmbed() {
		return this.getEmbed(this.getModerator(), this.getTarget());
	}
	
	public Document toData() {
		return this.action.toData()
			.append("_id", this.id)
			.append("guildId", this.guildId)
			.append("channelId", this.channelId)
			.append("targetId", this.targetId)
			.append("messageId", this.messageId)
			.append("moderatorId", this.moderatorId)
			.append("reason", this.reason == null ? null : this.reason.getParsed());
	}
	
}
