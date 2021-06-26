package com.sx4.bot.cache;

import com.sx4.bot.entities.cache.GuildMessage;
import gnu.trove.impl.sync.TSynchronizedLongObjectMap;
import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.api.hooks.EventListener;

import java.util.Objects;

public class GuildMessageCache implements EventListener {

	public static final int MAX_CACHED_MESSAGES = 400000;

	private static final int _MAX_CACHED_MESSAGES = MAX_CACHED_MESSAGES / 2;

	private TLongObjectMap<GuildMessage> createCache() {
		return new TSynchronizedLongObjectMap<>(new TLongObjectHashMap<>(_MAX_CACHED_MESSAGES), new Object());
	}

	public TLongObjectMap<GuildMessage> messageCache = this.createCache();
	public TLongObjectMap<GuildMessage> overloadCache = this.createCache();

	public synchronized void putMessage(Message message) {
		Objects.requireNonNull(message);

		if (this.overloadCache.size() + 1 > _MAX_CACHED_MESSAGES) {
			TLongObjectMap<GuildMessage> temp = this.messageCache;

			this.messageCache = this.overloadCache;
			this.overloadCache = temp;

			this.overloadCache.clear();
		}

		GuildMessage newMessage = new GuildMessage(message.getAuthor().getIdLong(), message.isPinned(), message.getContentRaw());
		if (this.messageCache.containsKey(message.getIdLong())) {
			this.messageCache.put(message.getIdLong(), newMessage);
		} else if (this.overloadCache.containsKey(message.getIdLong())) {
			this.overloadCache.put(message.getIdLong(), newMessage);
		}

		if (this.messageCache.size() + 1 > _MAX_CACHED_MESSAGES) {
			this.overloadCache.put(message.getIdLong(), newMessage);
		} else {
			this.messageCache.put(message.getIdLong(), newMessage);
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
		if (event instanceof GuildMessageReceivedEvent) {
			Message message = ((GuildMessageReceivedEvent) event).getMessage();

			this.putMessage(message);
		} else if (event instanceof GuildMessageUpdateEvent) {
			Message message = ((GuildMessageUpdateEvent) event).getMessage();

			this.putMessage(message);
		}
	}

}
