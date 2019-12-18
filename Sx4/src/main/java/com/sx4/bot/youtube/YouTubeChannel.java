package com.sx4.bot.youtube;

public class YouTubeChannel {
	
	private final String id;
	private final String name;
	private final String url;

	public YouTubeChannel(String id, String name) {
		this.id = id;
		this.name = name;
		this.url = "https://www.youtube.com/channel/" + id;
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
