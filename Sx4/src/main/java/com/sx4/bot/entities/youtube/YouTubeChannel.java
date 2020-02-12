package com.sx4.bot.entities.youtube;

public class YouTubeChannel {

	private final String id;
	private final String name;
	private final String url;
	
	public YouTubeChannel(String channelId, String channelName) {
		this.id = channelId;
		this.name = channelName;
		this.url = "https://www.youtube.com/channel/" + channelId;
	}
	
	public String getId() {
		return this.id;
	}
	
	public String getName() {
		return this.name;
	}
	
	public String getUrl() {
		return this.url;
	}
	
}
