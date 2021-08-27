package com.sx4.bot.paged;

import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class PagedManager {
	
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	
	private final Map<Long, PagedResult<?>> pagedResults;
	private final Map<Long, ScheduledFuture<?>> executors;

	public PagedManager() {
		this.pagedResults = new HashMap<>();
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
	
	public PagedResult<?> getPagedResult(long messageId) {
		return this.pagedResults.get(messageId);
	}
	
	public void addPagedResult(PagedResult<?> pagedResult) {
		PagedResult<?> old = this.pagedResults.put(pagedResult.getMessageId(), pagedResult);
		if (old != null) {
			MessageChannel channel = old.getChannel();
			if (channel != null) {
				channel.deleteMessageById(old.getMessageId()).queue(null, ErrorResponseException.ignore(ErrorResponse.UNKNOWN_MESSAGE));
			}
		}
	}
	
	public void removePagedResult(long messageId) {
		this.pagedResults.remove(messageId);
	}
	
}
