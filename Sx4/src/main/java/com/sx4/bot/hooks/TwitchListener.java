package com.sx4.bot.hooks;

import com.sx4.bot.events.twitch.TwitchEvent;
import com.sx4.bot.events.twitch.TwitchStreamStartEvent;

public interface TwitchListener {

	default void onEvent(TwitchEvent event) {}

	default void onStreamStart(TwitchStreamStartEvent event) {}

}
