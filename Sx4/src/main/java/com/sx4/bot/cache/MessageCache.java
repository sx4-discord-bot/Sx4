package com.sx4.bot.cache;

import com.sx4.bot.entities.cache.GuildMessage;
import gnu.trove.impl.sync.TSynchronizedLongObjectMap;
import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.EventListener;

import java.util.Objects;

public class MessageCache implements EventListener {

	public static final int MAX_CACHED_MESSAGES = 750_000;

	private static final int _MAX_CACHED_MESSAGES = MAX_CACHED_MESSAGES / 2;

	private TLongObjectMap<GuildMessage> createCache() {
		return new TSynchronizedLongObjectMap<>(new TLongObjectHashMap<>(_MAX_CACHED_MESSAGES), new Object());
	}

	public TLongObjectMap<GuildMessage> messageCache = this.createCache();
	public TLongObjectMap<GuildMessage> overloadCache = this.createCache();

	public synchronized void putMessage(GuildMessage message) {
		Objects.requireNonNull(message);

		if (this.overloadCache.size() + 1 > _MAX_CACHED_MESSAGES) {
			TLongObjectMap<GuildMessage> temp = this.messageCache;

			this.messageCache = this.overloadCache;
			this.overloadCache = temp;

			this.overloadCache.clear();
		}

		if (this.messageCache.containsKey(message.getId())) {
			this.messageCache.put(message.getId(), message);
		} else if (this.overloadCache.containsKey(message.getId())) {
			this.overloadCache.put(message.getId(), message);
		}

		if (this.messageCache.size() + 1 > _MAX_CACHED_MESSAGES) {
			this.overloadCache.put(message.getId(), message);
		} else {
			this.messageCache.put(message.getId(), message);
		}
	}

	public GuildMessage getMessageById(String id) {
		return this.getMessageById(Long.parseLong(id));
	}

	public GuildMessage getMessageById(long id) {
		GuildMessage message = this.messageCache.get(id);
		if (message == null) {
			message = this.overloadCache.get(id);
		}

		return message;
	}

	@Override
	public void onEvent(GenericEvent event) {
		if (event instanceof MessageReceivedEvent) {
			this.putMessage(GuildMessage.fromMessage(((MessageReceivedEvent) event).getMessage()));
		} else if (event instanceof MessageUpdateEvent) {
			this.putMessage(GuildMessage.fromMessage(((MessageUpdateEvent) event).getMessage()));
		}
	}

}
