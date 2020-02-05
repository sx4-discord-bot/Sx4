package com.sx4.bot.entities.mod;

import java.time.Instant;

import org.bson.types.ObjectId;

import com.sx4.bot.core.Sx4Bot;
import com.sx4.bot.entities.warn.WarnConfig;
import com.sx4.bot.utility.TimeUtility;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;

public class ModLog {
	
	private final ObjectId id;
	
	private long messageId;
	private final long channelId;
	private final long guildId;
	private final long targetId;
	private final long moderatorId;
	
	private final Action action;
	
	private final String reason;
	
	public ModLog(long channelId, long guildId, long targetId, long moderatorId, String reason, Action action) {
		this(ObjectId.get(), 0L, channelId, guildId, targetId, moderatorId, reason, action);
	}
	
	public ModLog(ObjectId id, long messageId, long channelId, long guildId, long targetId, long moderatorId, String reason, Action action) {
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
		return Sx4Bot.getShardManager().getGuildById(this.guildId);
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
		return Sx4Bot.getShardManager().getUserById(this.targetId);
	}
	
	public long getModeratorId() {
		return this.moderatorId;
	}
	
	public User getModerator() {
		return Sx4Bot.getShardManager().getUserById(this.moderatorId);
	}
	
	public String getReason() {
		return this.reason;
	}
	
	public Action getAction() {
		return this.action;
	}
	
	public MessageEmbed getEmbed(Member moderator, User target) {
		EmbedBuilder embed = new EmbedBuilder();

		if (this.action instanceof WarnAction) {
			WarnConfig warning = ((WarnAction) this.action).getWarning();
			
			embed.setTitle(warning.getAction().getName() + (warning.hasDuration() ? " (" + TimeUtility.getTimeString(warning.getDuration()) + ")" : ""));
		} else if (this.action instanceof TimeAction) {
			TimeAction action = (TimeAction) this.action;
			
			embed.setTitle(action.getModAction().getName() + (action.hasDuration() ? " (" + TimeUtility.getTimeString(action.getDuration()) + ")" : ""));
		} else {
			embed.setTitle(this.action.getModAction().getName());
		}
		
		embed.addField("Target", target.getAsTag() + " (" + target.getId() + ")", false);
		embed.addField("Moderator", moderator.getUser().getAsTag() + " (" + moderator.getId() + ")", false);
		embed.addField("Reason", this.reason, false);
		embed.setTimestamp(Instant.ofEpochSecond(this.getTimestamp()));
		embed.setFooter("ID: " + this.getHex());
		
		return embed.build();
	}
	
}
