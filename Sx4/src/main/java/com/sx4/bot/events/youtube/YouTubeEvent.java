package com.sx4.bot.events.youtube;

import com.sx4.bot.entities.youtube.YouTubeChannel;

public class YouTubeEvent {

	private final String videoId;
	private final YouTubeChannel channel;

	public YouTubeEvent(YouTubeChannel channel, String videoId) {
		this.channel = channel;
		this.videoId = videoId;
	}
	
	public YouTubeChannel getChannel() {
		return this.channel;
	}
	
	public String getVideoId() {
		return this.videoId;
	}
	
}
