package com.sx4.bot.entities.youtube;

import java.time.OffsetDateTime;

public class YouTubeVideo {

	private final String id;
	private final String title;
	private final String url;
	private final String thumbnail;
	
	private final OffsetDateTime updatedAt;
	private final OffsetDateTime publishedAt;
	
	public YouTubeVideo(String videoId, String videoTitle, String videoUpdatedAt, String videoPublishedAt) {
		this.id = videoId;
		this.title = videoTitle;
		this.url = "https://www.youtube.com/watch?v=" + videoId;
		this.thumbnail = "https://img.youtube.com/vi/" + videoId + "/0.jpg";
		this.updatedAt = OffsetDateTime.parse(videoUpdatedAt);
		this.publishedAt = OffsetDateTime.parse(videoPublishedAt);
	}
	
	public String getId() {
		return this.id;
	}
	
	public String getTitle() {
		return this.title;
	}
	
	public String getUrl() {
		return this.url;
	}

	public String getThumbnail() {
		return this.thumbnail;
	}
	
	public OffsetDateTime getUpdatedAt() {
		return this.updatedAt;
	}
	
	public OffsetDateTime getPublishedAt() {
		return this.publishedAt;
	}
	
}
