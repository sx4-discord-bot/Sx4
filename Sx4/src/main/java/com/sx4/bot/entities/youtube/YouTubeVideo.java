package com.sx4.bot.entities.youtube;

import java.time.OffsetDateTime;

public class YouTubeVideo {

	private final String id;
	private final String title;
	private final String url;
	
	private final OffsetDateTime updatedAt;
	private final OffsetDateTime publishedAt;
	
	public YouTubeVideo(String videoId, String videoTitle, String videoUpdatedAt, String videoPublishedAt) {
		this.id = videoId;
		this.title = videoTitle;
		this.url = "https://www.youtube.com/watch?v=" + videoId;
		this.updatedAt = videoUpdatedAt == null ? null : OffsetDateTime.parse(videoUpdatedAt);
		this.publishedAt = videoPublishedAt == null ? null : OffsetDateTime.parse(videoPublishedAt);
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
	
	public OffsetDateTime getUpdatedAt() {
		return this.updatedAt;
	}
	
	public OffsetDateTime getPublishedAt() {
		return this.publishedAt;
	}
	
}
