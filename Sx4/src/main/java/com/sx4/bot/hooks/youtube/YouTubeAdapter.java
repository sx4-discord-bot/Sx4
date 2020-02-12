package com.sx4.bot.hooks.youtube;

import com.sx4.bot.events.youtube.YouTubeDeleteEvent;
import com.sx4.bot.events.youtube.YouTubeEvent;
import com.sx4.bot.events.youtube.YouTubeUpdateEvent;
import com.sx4.bot.events.youtube.YouTubeUpdateTitleEvent;
import com.sx4.bot.events.youtube.YouTubeUploadEvent;

public abstract class YouTubeAdapter implements YouTubeListener {
	
	public void onGenericYouTube(YouTubeEvent event) {}
	public void onYouTubeUpdate(YouTubeUpdateEvent event) {}
	public void onYouTubeUpdateTitle(YouTubeUpdateTitleEvent event) {}
	public void onYouTubeDelete(YouTubeDeleteEvent event) {}
	public void onYouTubeUpload(YouTubeUploadEvent event) {}

	public void onYouTube(YouTubeEvent event) {
		this.onGenericYouTube(event);
		
		if (event instanceof YouTubeUpdateEvent) {
			this.onYouTubeUpdate((YouTubeUpdateEvent) event);
		}
		
		if (event instanceof YouTubeUpdateTitleEvent) {
			this.onYouTubeUpdateTitle((YouTubeUpdateTitleEvent) event);
		} else if (event instanceof YouTubeUploadEvent) {
			this.onYouTubeUpload((YouTubeUploadEvent) event);
		}
		
		if (event instanceof YouTubeDeleteEvent) {
			this.onYouTubeDelete((YouTubeDeleteEvent) event);
		}
	}
	
}
