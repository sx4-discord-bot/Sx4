package com.sx4.bot.entities.twitch;

import java.time.OffsetDateTime;

public class TwitchStream {

	public final String id, preview, title, game;
	public final OffsetDateTime start;
	public final TwitchStreamType type;

	public TwitchStream(String id, TwitchStreamType type, String preview, String title, String game, OffsetDateTime start) {
		this.id = id;
		this.preview = preview;
		this.title = title;
		this.game = game;
		this.type = type;
		this.start = start;
	}

	public String getId() {
		return this.id;
	}

	public String getPreviewUrl() {
		return this.preview;
	}

	public String getTitle() {
		return this.title;
	}

	public String getGame() {
		return this.game;
	}

	public TwitchStreamType getType() {
		return this.type;
	}

	public OffsetDateTime getStartTime() {
		return this.start;
	}

}
