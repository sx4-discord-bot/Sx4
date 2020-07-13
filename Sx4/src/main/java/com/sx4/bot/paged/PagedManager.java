package com.sx4.bot.paged;

import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class PagedManager {
	
	private static final PagedManager INSTANCE = new PagedManager();
	
	public static PagedManager get() {
		return PagedManager.INSTANCE;
	}
	
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	
	private final Map<Long, Map<Long, PagedResult<?>>> pagedResults;
	private final Map<Long, Map<Long, ScheduledFuture<?>>> executors;

	public PagedManager() {
		this.pagedResults = new HashMap<>();
		this.executors = new HashMap<>();
	}
	
	public void cancelTimeout(long channelId, long ownerId) {
		Map<Long, ScheduledFuture<?>> executors = this.executors.get(channelId);
		if (executors != null) {
			ScheduledFuture<?> executor = executors.remove(ownerId);
			if (executor != null && !executor.isDone()) {
				executor.cancel(true);
			}
		}
	}
	
	public void setTimeout(PagedResult<?> pagedResult) {
		if (pagedResult.getTimeout() != 0) {
			if (this.executors.containsKey(pagedResult.getChannelId())) {
				Map<Long, ScheduledFuture<?>> executors = this.executors.get(pagedResult.getChannelId());
				if (executors.containsKey(pagedResult.getOwnerId())) {
					executors.get(pagedResult.getOwnerId()).cancel(true);
				}
				
				executors.put(pagedResult.getOwnerId(), this.executor.schedule(pagedResult::timeout, pagedResult.getTimeout(), TimeUnit.SECONDS));
			} else {
				Map<Long, ScheduledFuture<?>> executors = new HashMap<>();
				executors.put(pagedResult.getOwnerId(), this.executor.schedule(pagedResult::timeout, pagedResult.getTimeout(), TimeUnit.SECONDS));
				
				this.executors.put(pagedResult.getChannelId(), executors);
			}
		}
	}
	
	public PagedResult<?> getPagedResult(long channelId, long ownerId) {
		Map<Long, PagedResult<?>> users = this.pagedResults.get(channelId);
		return users == null ? null : users.get(ownerId);
	}
	
	public void addPagedResult(MessageChannel channel, User owner, PagedResult<?> pagedResult) {
		if (this.pagedResults.containsKey(channel.getIdLong())) {
			Map<Long, PagedResult<?>> users = this.pagedResults.get(channel.getIdLong());
			if (users.containsKey(owner.getIdLong())) {
				channel.deleteMessageById(users.get(owner.getIdLong()).getMessageId()).queue(null, ErrorResponseException.ignore(ErrorResponse.UNKNOWN_MESSAGE));
			}
			
			users.put(owner.getIdLong(), pagedResult);
		} else {
			Map<Long, PagedResult<?>> users = new HashMap<>();
			users.put(owner.getIdLong(), pagedResult);
			
			this.pagedResults.put(channel.getIdLong(), users);
		}
	}
	
	public void removePagedResult(long channelId, long ownerId) {
		Map<Long, PagedResult<?>> users = this.pagedResults.get(channelId);
		if (users != null) {
			users.remove(ownerId);
		}
	}
	
}
