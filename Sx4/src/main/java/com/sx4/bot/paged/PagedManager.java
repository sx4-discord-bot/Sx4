package com.sx4.bot.paged;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class PagedManager {
	
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	
	private final Map<Long, Map<Long, PagedResult<?>>> pagedResults;
	private final Set<Long> messages;

	private final Map<Long, ScheduledFuture<?>> executors;

	public PagedManager() {
		this.pagedResults = new HashMap<>();
		this.messages = new HashSet<>();
		this.executors = new HashMap<>();
	}
	
	public void cancelTimeout(long messageId) {
		ScheduledFuture<?> executor = this.executors.remove(messageId);
		if (executor != null && !executor.isDone()) {
			executor.cancel(true);
		}
	}
	
	public void setTimeout(PagedResult<?> pagedResult) {
		if (pagedResult.getTimeout() != 0) {
			ScheduledFuture<?> old = this.executors.put(pagedResult.getMessageId(), this.executor.schedule(pagedResult::timeout, pagedResult.getTimeout(), TimeUnit.SECONDS));
			if (old != null && !old.isDone()) {
				old.cancel(true);
			}
		}
	}

	public boolean isPagedResult(long messageId) {
		return this.messages.contains(messageId);
	}

	public PagedResult<?> getPagedResult(long channelId, long ownerId) {
		Map<Long, PagedResult<?>> users = this.pagedResults.get(channelId);
		return users == null ? null : users.get(ownerId);
	}

	public void addPagedResult(MessageChannel channel, User owner, PagedResult<?> pagedResult) {
		Map<Long, PagedResult<?>> users = this.pagedResults.get(channel.getIdLong());
		if (users == null) {
			users = new HashMap<>();
			users.put(owner.getIdLong(), pagedResult);

			this.pagedResults.put(channel.getIdLong(), users);
		} else {
			if (users.containsKey(owner.getIdLong())) {
				channel.deleteMessageById(users.get(owner.getIdLong()).getMessageId()).queue(null, ErrorResponseException.ignore(ErrorResponse.UNKNOWN_MESSAGE));
			}

			users.put(owner.getIdLong(), pagedResult);
		}

		this.messages.add(pagedResult.getMessageId());
	}

	public void removePagedResult(PagedResult<?> pagedResult) {
		Map<Long, PagedResult<?>> users = this.pagedResults.get(pagedResult.getChannelId());
		if (users != null) {
			users.remove(pagedResult.getOwnerId());
		}

		this.messages.remove(pagedResult.getMessageId());
	}
	
}
