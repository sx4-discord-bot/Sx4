package com.sx4.bot.events.youtube;

import com.sx4.bot.entities.youtube.YouTubeChannel;
import com.sx4.bot.entities.youtube.YouTubeVideo;

public class YouTubeUpdateEvent extends YouTubeEvent {
	
	private final YouTubeVideo video;

	public YouTubeUpdateEvent(YouTubeChannel channel, YouTubeVideo video) {
		super(channel, video.getId());
		
		this.video = video;
	}
	
	public YouTubeVideo getVideo() {
		return this.video;
	}
	
}
