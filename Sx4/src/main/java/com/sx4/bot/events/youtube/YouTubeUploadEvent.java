package com.sx4.bot.events.youtube;

import com.sx4.bot.entities.youtube.YouTubeChannel;
import com.sx4.bot.entities.youtube.YouTubeVideo;

public class YouTubeUploadEvent extends YouTubeUpdateEvent {

	public YouTubeUploadEvent(YouTubeChannel channel, YouTubeVideo video) {
		super(channel, video);
	}
	
}
