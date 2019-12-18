package com.sx4.bot.youtube;

import java.time.ZonedDateTime;

public class YouTubeVideo {
	
	private final String id;
	private final String title;
	private final String url;
	
	private final ZonedDateTime updatedAt;
	private final ZonedDateTime publishedAt;
	private final ZonedDateTime deletedAt;

	public YouTubeVideo(String id, String title, String updatedAt, String publishedAt, String deletedAt) {
		this.title = title;
		this.id = id;
		this.url = "https://www.youtube.com/watch?v=" + id;
		
		this.updatedAt = updatedAt == null ? null : ZonedDateTime.parse(updatedAt);
		this.publishedAt = publishedAt == null ? null : ZonedDateTime.parse(publishedAt);
		this.deletedAt = deletedAt == null ? null : ZonedDateTime.parse(deletedAt);
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
	
	public boolean isDeleted() {
		return this.deletedAt != null;
	}
	
	public ZonedDateTime getTimeUpdatedAt() {
		return this.updatedAt;
	}
	
	public ZonedDateTime getTimePublishedAt() {
		return this.publishedAt;
	}
	
	public ZonedDateTime getTimeDeletedAt() {
		return this.deletedAt;
	}
	
}
