package com.sx4.bot.youtube;

public class YouTubeEvent {
	
	private final YouTubeChannel channel;
	private final YouTubeVideo video;

	public YouTubeEvent(String channelId, String channelName, String videoId, String videoTitle, String videoUpdatedAt, String videoPublishedAt, String videoDeletedAt) {
		this.channel = new YouTubeChannel(channelId, channelName);
		this.video = new YouTubeVideo(videoId, videoTitle, videoUpdatedAt, videoPublishedAt, videoDeletedAt);
	}
	
	public YouTubeChannel getChannel() {
		return this.channel;
	}
	
	public YouTubeVideo getVideo() {
		return this.video;
	}
	
}
