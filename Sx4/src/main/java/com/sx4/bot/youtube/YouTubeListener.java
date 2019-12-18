package com.sx4.bot.youtube;

public interface YouTubeListener {

	void onVideoUpload(YouTubeEvent event);
	
	void onVideoDelete(YouTubeEvent event);
	
}
