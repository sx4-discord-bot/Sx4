package com.sx4.bot.youtube;

public enum YouTubeType {

	UPLOAD(0),
	TITLE(1),
	DESCRIPTION(2);
	
	private final int raw;
	
	private YouTubeType(int raw) {
		this.raw = raw;
	}
	
	public int getRaw() {
		return this.raw;
	}
	
	public static YouTubeType getType(int raw) {
		for (YouTubeType type : YouTubeType.values()) {
			if (type.getRaw() == raw) {
				return type;
			}
		}
		
		return null;
	}
	
}
