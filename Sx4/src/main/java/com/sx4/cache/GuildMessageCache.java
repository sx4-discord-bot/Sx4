package com.sx4.cache;

import java.util.EventListener;
import java.util.Objects;

import gnu.trove.impl.sync.TSynchronizedLongObjectMap;
import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.Event;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageUpdateEvent;

public class GuildMessageCache implements EventListener {
	
	public static final GuildMessageCache INSTANCE = new GuildMessageCache();
	
	public static final int MAX_CACHED_MESSAGES = 200000;
	
	private static final int _MAX_CACHED_MESSAGES = MAX_CACHED_MESSAGES/2;
	
	private GuildMessageCache() {}
	
	private TLongObjectMap<Message> createCache() {
		return new TSynchronizedLongObjectMap<>(new TLongObjectHashMap<>(_MAX_CACHED_MESSAGES), new Object());
	}
	
	public TLongObjectMap<Message> messageCache = this.createCache();
	public TLongObjectMap<Message> overloadCache = this.createCache();
	
	public synchronized void putMessage(Message message) {
		message = Objects.requireNonNull(message);
		
		if(this.overloadCache.size() + 1 > _MAX_CACHED_MESSAGES) {
			TLongObjectMap<Message> temp = this.messageCache;
			
			this.messageCache = this.overloadCache;
			this.overloadCache = temp;
			
			this.overloadCache.clear();
		}
		
		if(this.messageCache.containsKey(message.getIdLong())) {
			this.messageCache.put(message.getIdLong(), message);
		}else if(this.overloadCache.containsKey(message.getIdLong())) {
			this.overloadCache.put(message.getIdLong(), message);
		}
		
		if(this.messageCache.size() + 1 > _MAX_CACHED_MESSAGES) {
			this.overloadCache.put(message.getIdLong(), message);
		}else{
			this.messageCache.put(message.getIdLong(), message);
		}
	}
	
	public Message getMessageById(String id) {
		return this.getMessageById(Long.valueOf(id));
	}
	
	public Message getMessageById(long id) {
		Message message = this.messageCache.get(id);
		if(message == null) {
			message = this.overloadCache.get(id);
		}
		
		return message;
	}
	
	public void onEvent(Event event) {
		if(event instanceof GuildMessageReceivedEvent) {
			Message message = ((GuildMessageReceivedEvent) event).getMessage();
			
			this.putMessage(message);
		}else if(event instanceof GuildMessageUpdateEvent) {
			Message message = ((GuildMessageUpdateEvent) event).getMessage();
			
			this.putMessage(message);
		}
	}
}