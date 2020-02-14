package com.sx4.bot.handlers;

import com.sx4.bot.database.Database;
import com.sx4.bot.events.youtube.YouTubeDeleteEvent;
import com.sx4.bot.events.youtube.YouTubeUploadEvent;
import com.sx4.bot.hooks.youtube.YouTubeAdapter;
import com.sx4.bot.utility.ExceptionUtility;

public class YouTubeHandler extends YouTubeAdapter {

	public void onYouTubeUpload(YouTubeUploadEvent event) {
		
	}
	
	public void onYouTubeDelete(YouTubeDeleteEvent event) {
		Database.get().deleteManyNotifications(event.getVideoId()).whenComplete((result, exception) -> {
			if (exception != null) {
				exception.printStackTrace();
				ExceptionUtility.sendErrorMessage(exception);
			}
		});
	}
	
}
