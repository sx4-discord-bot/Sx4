package com.sx4.bot.waiter;

import com.sx4.bot.core.Sx4;
import com.sx4.bot.waiter.exception.CancelException;
import com.sx4.bot.waiter.exception.TimeoutException;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class Waiter<Type extends GenericEvent> {
	
	public enum CancelType {
		USER("Cancelled due to user input"),
		UNIQUE("Cancelled due to a new user waiter being created");
		
		private final String reason;
		
		private CancelType(String reason) {
			this.reason = reason;
		}
		
		public String getReason() {
			return this.reason;
		}
		
	}

	private long authorId = 0L;
	private long channelId = 0L;
	
	private long timeout = 0L;
	
	private final Class<Type> event;

	private final CompletableFuture<Type> future = new CompletableFuture<>();
	
	private Predicate<Type> predicate = $ -> true;
	private Predicate<Type> cancelPredicate = $ -> false;

	private final Sx4 bot;
	
	public Waiter(Sx4 bot, Class<Type> event) {
		this.bot = bot;
		this.event = event;
	}
	
	public Class<Type> getEvent() {
		return this.event;
	}
	
	public Waiter<Type> setUnique(long authorId, long channelId) {
		if (!MessageReceivedEvent.class.isAssignableFrom(this.event)) {
			throw new IllegalArgumentException("Unique waiters currently only support instances of MessageReceivedEvent");
		}
		
		this.authorId = authorId;
		this.channelId = channelId;
		
		return this;
	}
	
	public boolean isUnique() {
		return this.authorId != 0L && this.channelId != 0L;
	}
	
	public long getChannelId() {
		return this.channelId;
	}
	
	public long getAuthorId() {
		return this.authorId;
	}

	public void onCancelled(Consumer<CancelType> onCancelled) {
		this.future.whenComplete((result, exception) -> {
			Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
			if (cause instanceof CancelException) {
				onCancelled.accept(((CancelException) cause).getType());
			}
		});
	}

	public void onTimeout(Runnable onTimeout) {
		this.future.whenComplete((result, exception) -> {
			Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
			if (cause instanceof TimeoutException) {
				onTimeout.run();
			}
		});
	}
	
	public long getTimeout() {
		return this.timeout;
	}
	
	public Waiter<Type> setTimeout(long seconds) {
		return this.setTimeout(seconds, TimeUnit.SECONDS);
	}
	
	public Waiter<Type> setTimeout(long timeout, TimeUnit timeUnit) {
		this.timeout = timeUnit.toSeconds(timeout);
		
		return this;
	}
	
	@SuppressWarnings("unchecked")
	public boolean testCancelPredicate(GenericEvent event) {
		return this.cancelPredicate.test((Type) event);
	}
	
	public Waiter<Type> setCancelPredicate(Predicate<Type> predicate) {
		this.cancelPredicate = predicate;
		
		return this;
	}
	
	public Waiter<Type> setOppositeCancelPredicate() {
		this.cancelPredicate = Predicate.not(this.predicate);
		
		return this;
	}
	
	@SuppressWarnings("unchecked")
	public boolean testPredicate(GenericEvent event) {
		return this.predicate.test((Type) event);
	}
	
	public Waiter<Type> setPredicate(Predicate<Type> predicate) {
		this.predicate = predicate;
		
		return this;
	}
	
	public void onSuccess(Consumer<Type> onSuccess) {
		this.future.thenAccept(onSuccess);
	}
	
	@SuppressWarnings("unchecked")
	public void execute(GenericEvent event) {
		this.future.complete((Type) event);
		
		this.delete();
	}
	
	public void cancel(CancelType type) {
		this.future.completeExceptionally(new CancelException(type));

		this.delete();
	}
	
	public void timeout() {
		this.future.completeExceptionally(new TimeoutException());

		this.delete();
	}
	
	public CompletableFuture<Type> start() {
		this.bot.getWaiterManager().addWaiter(this);
		
		if (this.timeout != 0) {
			this.bot.getWaiterManager().setTimeout(this);
		}

		return this.future;
	}
	
	public void delete() {
		this.bot.getWaiterManager().removeWaiter(this);
		this.bot.getWaiterManager().cancelTimeout(this);
		this.future.cancel(true);
	}
	
}
