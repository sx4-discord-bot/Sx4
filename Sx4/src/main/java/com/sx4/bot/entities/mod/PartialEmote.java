package com.sx4.bot.entities.mod;

import net.dv8tion.jda.api.entities.Emote;

public class PartialEmote {
	
	protected final long id;
	protected final Boolean animated;
	protected final String name;
	protected final String url;
	
	public PartialEmote(Emote emote) {
		this.name = emote.getName();
		this.id = emote.getIdLong();
		this.animated = emote.isAnimated();
		this.url = emote.getImageUrl();
	}
	
	public PartialEmote(String url, String name, Boolean animated) {
		this.name = name;
		this.id = 0L;
		this.animated = animated;
		this.url = url;
	}
	
	public PartialEmote(long id, String name, Boolean animated) {
		this.name = name;
		this.id = id;
		this.animated = animated;
		this.url = String.format(Emote.ICON_URL, id, animated == null || animated ? "gif" : "png");
	}
	
	public long getId() {
		return this.id;
	}
	
	public boolean isUnknown() {
		return this.animated == null;
	}
	
	public Boolean isAnimated() {
		return this.animated;
	}
	
	public String getName() {
		return this.name;
	}
	
	public boolean hasName() {
		return this.name != null;
	}
	
	public String getUrl() {
		return this.url;
	}

}
