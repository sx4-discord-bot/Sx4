package com.sx4.bot.cache;

import gnu.trove.impl.sync.TSynchronizedLongObjectMap;
import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.EventListener;

import java.util.Objects;

public class MessageCache implements EventListener {

	public static final int MAX_CACHED_MESSAGES = 750_000;

	private static final int _MAX_CACHED_MESSAGES = MAX_CACHED_MESSAGES / 2;

	private TLongObjectMap<Message> createCache() {
		return new TSynchronizedLongObjectMap<>(new TLongObjectHashMap<>(_MAX_CACHED_MESSAGES), new Object());
	}

	public TLongObjectMap<Message> messageCache = this.createCache();
	public TLongObjectMap<Message> overloadCache = this.createCache();

	public synchronized void putMessage(Message message) {
		Objects.requireNonNull(message);

		if (this.overloadCache.size() + 1 > _MAX_CACHED_MESSAGES) {
			TLongObjectMap<Message> temp = this.messageCache;

			this.messageCache = this.overloadCache;
			this.overloadCache = temp;

			this.overloadCache.clear();
		}

		if (this.messageCache.containsKey(message.getIdLong())) {
			this.messageCache.put(message.getIdLong(), message);
		} else if (this.overloadCache.containsKey(message.getIdLong())) {
			this.overloadCache.put(message.getIdLong(), message);
		}

		if (this.messageCache.size() + 1 > _MAX_CACHED_MESSAGES) {
			this.overloadCache.put(message.getIdLong(), message);
		} else {
			this.messageCache.put(message.getIdLong(), message);
		}
	}

	public Message getMessageById(String id) {
		return this.getMessageById(Long.parseLong(id));
	}

	public Message getMessageById(long id) {
		Message message = this.messageCache.get(id);
		if (message == null) {
			message = this.overloadCache.get(id);
		}

		return message;
	}

	@Override
	public void onEvent(GenericEvent event) {
		if (event instanceof MessageReceivedEvent) {
			this.putMessage(((MessageReceivedEvent) event).getMessage());
		} else if (event instanceof MessageUpdateEvent) {
			this.putMessage(((MessageUpdateEvent) event).getMessage());
		}
	}

}
