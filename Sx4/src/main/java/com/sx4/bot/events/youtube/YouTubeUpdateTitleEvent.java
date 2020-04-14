package com.sx4.bot.events.youtube;

import com.sx4.bot.entities.youtube.YouTubeChannel;
import com.sx4.bot.entities.youtube.YouTubeVideo;

public class YouTubeUpdateTitleEvent extends YouTubeUpdateEvent {

	private final String oldTitle;
	
	public YouTubeUpdateTitleEvent(YouTubeChannel channel, YouTubeVideo video, String oldTitle) {
		super(channel, video);
		
		this.oldTitle = oldTitle;
	}
	
	public String getNewTitle() {
		return this.getVideo().getTitle();
	}
	
	public String getOldTitle() {
		return this.oldTitle;
	}
	
}
