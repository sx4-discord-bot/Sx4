package com.sx4.bot.config;

public class TwitchConfig extends GenericConfig {

	public TwitchConfig() {
		super("../twitch.json");
	}

	public String getToken() {
		return this.get("token");
	}

	public long getExpiresAt() {
		return this.get("expiresAt", Number.class).longValue();
	}

}
