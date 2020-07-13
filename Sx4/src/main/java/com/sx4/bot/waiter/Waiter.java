package com.sx4.bot.waiter;

import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;

import java.util.concurrent.CompletableFuture;
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
	
	private final WaiterManager manager = WaiterManager.get();

	private long authorId = 0L;
	private long channelId = 0L;
	
	private long timeout = 0L;
	
	private final Class<Type> event;
	
	private Consumer<CancelType> onCancelled = null;
	private Runnable onTimeout = null;
	private final CompletableFuture<Type> future = new CompletableFuture<>();
	
	private Predicate<Type> predicate = $ -> true;
	private Predicate<Type> cancelPredicate = $ -> false;
	
	public Waiter(Class<Type> event) {
		this.event = event;
	}
	
	public Class<Type> getEvent() {
		return this.event;
	}
	
	public Waiter<Type> setUnique(long authorId, long channelId) {
		if (this.event != GuildMessageReceivedEvent.class) {
			throw new IllegalArgumentException("Unique waiters currently only support GuildMessageReceivedEvent");
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
	
	public Consumer<CancelType> getCancelRunnable() {
		return this.onCancelled;
	}
	
	public void onCancelled(Consumer<CancelType> onCancelled) {
		this.onCancelled = onCancelled;
	}
	
	public Runnable getTimeoutRunnable() {
		return this.onTimeout;
	}
	
	public void onTimeout(Runnable onTimeout) {
		this.onTimeout = onTimeout;
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
	
	public CompletableFuture<Type> future() {
		return this.future;
	}
	
	@SuppressWarnings("unchecked")
	public void execute(GenericEvent event) {
		this.future.complete((Type) event);
		
		this.delete();
	}
	
	public void cancel(CancelType type) {
		if (this.onCancelled != null) {
			this.onCancelled.accept(type);
		}
		
		this.delete();
	}
	
	public void timeout() {
		if (this.onTimeout != null) {
			this.onTimeout.run();
		}

		this.delete();
	}
	
	public void start() {
		this.manager.addWaiter(this);
		
		if (this.timeout != 0) {
			this.manager.setTimeout(this);
		}
	}
	
	public void delete() {
		this.manager.removeWaiter(this);
		this.manager.cancelTimeout(this);
	}
	
}
