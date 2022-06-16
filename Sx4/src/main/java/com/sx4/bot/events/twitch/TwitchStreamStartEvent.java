package com.sx4.bot.events.twitch;

import com.sx4.bot.entities.twitch.TwitchStream;
import com.sx4.bot.entities.twitch.TwitchStreamer;

public class TwitchStreamStartEvent extends TwitchEvent {

	private final TwitchStream stream;
	private final TwitchStreamer streamer;

	public TwitchStreamStartEvent(TwitchStream stream, TwitchStreamer streamer) {
		this.stream = stream;
		this.streamer = streamer;
	}

	public TwitchStream getStream() {
		return this.stream;
	}

	public TwitchStreamer getStreamer() {
		return this.streamer;
	}

}
