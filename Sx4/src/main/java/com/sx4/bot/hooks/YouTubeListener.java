package com.sx4.bot.hooks;

import java.util.EventListener;

import com.sx4.bot.events.youtube.YouTubeDeleteEvent;
import com.sx4.bot.events.youtube.YouTubeEvent;
import com.sx4.bot.events.youtube.YouTubeUpdateEvent;
import com.sx4.bot.events.youtube.YouTubeUpdateTitleEvent;
import com.sx4.bot.events.youtube.YouTubeUploadEvent;

public interface YouTubeListener extends EventListener {

	default void onYouTube(YouTubeEvent event) {}
	
	default void onYouTubeUpdate(YouTubeUpdateEvent event) {}
	
	default void onYouTubeUpdateTitle(YouTubeUpdateTitleEvent event) {}
	
	default void onYouTubeDelete(YouTubeDeleteEvent event) {}
	
	default void onYouTubeUpload(YouTubeUploadEvent event) {}
	
}
