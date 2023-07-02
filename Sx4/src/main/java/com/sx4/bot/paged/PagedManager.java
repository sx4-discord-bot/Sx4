package com.sx4.bot.paged;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class PagedManager {
	
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	
	private final Map<Long, Map<Long, PagedResult<?, ?>>> pagedResults;
	private final Map<Long, PagedResult<?, ?>> messages;

	private final Map<Long, ScheduledFuture<?>> executors;

	public PagedManager() {
		this.pagedResults = new HashMap<>();
		this.messages = new HashMap<>();
		this.executors = new HashMap<>();
	}
	
	public void setTimeout(PagedResult<?, ?> pagedResult) {
		if (pagedResult.getTimeout() == 0) {
			return;
		}

		ScheduledFuture<?> old = this.executors.put(pagedResult.getMessageId(), this.executor.schedule(pagedResult::timeout, pagedResult.getTimeout(), TimeUnit.SECONDS));
		if (old != null && !old.isDone()) {
			old.cancel(true);
		}
	}

	public PagedResult<?, ?> getPagedResult(long messageId) {
		return this.messages.get(messageId);
	}

	public PagedResult<?, ?> getPagedResult(long channelId, long ownerId) {
		Map<Long, PagedResult<?, ?>> users = this.pagedResults.get(channelId);
		return users == null ? null : users.get(ownerId);
	}

	public void createPagedResult(PagedResult<?, ?> pagedResult) {
		Map<Long, PagedResult<?, ?>> users = this.pagedResults.get(pagedResult.getChannelId());
		if (users == null) {
			users = new HashMap<>();
			users.put(pagedResult.getOwnerId(), pagedResult);

			this.pagedResults.put(pagedResult.getChannelId(), users);
		} else {
			users.put(pagedResult.getOwnerId(), pagedResult);
		}

		this.messages.put(pagedResult.getMessageId(), pagedResult);
	}

	public void deletePagedResult(PagedResult<?, ?> pagedResult) {
		Map<Long, PagedResult<?, ?>> users = this.pagedResults.get(pagedResult.getChannelId());
		if (users != null) {
			users.remove(pagedResult.getOwnerId());
		}

		this.messages.remove(pagedResult.getMessageId());

		ScheduledFuture<?> executor = this.executors.remove(pagedResult.getMessageId());
		if (executor != null && !executor.isDone()) {
			executor.cancel(true);
		}
	}
	
}
