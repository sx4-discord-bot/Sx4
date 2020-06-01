package com.sx4.bot.entities.mod;

import net.dv8tion.jda.api.entities.Emote;

public class PartialEmote {
	
	private final String url;
	private final String name;
	
	public PartialEmote(Emote emote) {
		this.name = emote.getName();
		this.url = emote.getImageUrl();
	}
	
	public PartialEmote(String url, String name) {
		this.name = name;
		this.url = url;
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
