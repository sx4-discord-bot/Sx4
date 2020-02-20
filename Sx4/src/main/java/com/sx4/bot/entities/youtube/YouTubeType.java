package com.sx4.bot.entities.youtube;

public enum YouTubeType {

	UPLOAD(0),
	TITLE(1);
	
	private final int type;
	
	private YouTubeType(int raw) {
		this.type = raw;
	}
	
	public int getRaw() {
		return this.type;
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
