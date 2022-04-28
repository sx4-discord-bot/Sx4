package com.sx4.bot.entities.twitch;

public class TwitchStreamer {

	private final String id, name, login;

	public TwitchStreamer(String id, String name, String login) {
		this.id = id;
		this.name = name;
		this.login = login;
	}

	public String getId() {
		return this.id;
	}

	public String getLogin() {
		return this.login;
	}

	public String getUrl() {
		return "https://twitch.tv/" + this.login;
	}

	public String getName() {
		return this.name;
	}

}
