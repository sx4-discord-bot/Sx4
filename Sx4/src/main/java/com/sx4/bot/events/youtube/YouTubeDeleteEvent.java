package com.sx4.bot.events.youtube;

import java.time.OffsetDateTime;

import com.sx4.bot.entities.youtube.YouTubeChannel;

public class YouTubeDeleteEvent extends YouTubeEvent {
	
	private final OffsetDateTime deletedAt;

	public YouTubeDeleteEvent(YouTubeChannel channel, String videoId, String videoDeletedAt) {
		super(channel, videoId);
		
		this.deletedAt = OffsetDateTime.parse(videoDeletedAt);
	}
	
	public OffsetDateTime getDeletedAt() {
		return this.deletedAt;
	}
	
}
