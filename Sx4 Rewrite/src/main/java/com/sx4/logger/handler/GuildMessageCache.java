package com.sx4.logger.handler;

import java.util.Objects;

import gnu.trove.map.TLongObjectMap;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.events.Event;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.core.hooks.EventListener;
import net.dv8tion.jda.core.utils.MiscUtil;

public class GuildMessageCache implements EventListener {
	
	public static final GuildMessageCache INSTANCE = new GuildMessageCache();
	
	public static final int MAX_CACHED_MESSAGES = 200000;
	
	private GuildMessageCache() {}
	
	public TLongObjectMap<Message> messageCache = MiscUtil.newLongMap();
	public TLongObjectMap<Message> overloadCache = MiscUtil.newLongMap();
	
	public void putMessage(Message message) {
		message = Objects.requireNonNull(message);
		
		if(this.overloadCache.size() + 1 >= MAX_CACHED_MESSAGES/2) {
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
		
		if(this.messageCache.size() + 1 >= MAX_CACHED_MESSAGES/2) {
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