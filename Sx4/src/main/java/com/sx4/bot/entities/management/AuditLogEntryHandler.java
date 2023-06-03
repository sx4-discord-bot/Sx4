package com.sx4.bot.entities.management;

import net.dv8tion.jda.api.audit.AuditLogEntry;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class AuditLogEntryHandler {

	private final Predicate<AuditLogEntry> predicate;
	private final Consumer<AuditLogEntry> consumer;
	private final ScheduledFuture<?> task;

	private AuditLogEntryHandler(ScheduledExecutorService executor, Predicate<AuditLogEntry> predicate, Consumer<AuditLogEntry> consumer) {
		this.predicate = predicate;
		this.consumer = consumer;

		this.task = executor.schedule(() -> consumer.accept(null), 500, TimeUnit.MILLISECONDS);
	}

	public boolean handle(AuditLogEntry entry) {
		// Found audit log too late, in this case skip it
		if (this.task.isDone()) {
			return true;
		}

		if (this.predicate.test(entry)) {
			this.consumer.accept(entry);
			this.task.cancel(true);
			return true;
		}

		return false;
	}

	public static AuditLogEntryHandler from(ScheduledExecutorService executor, Predicate<AuditLogEntry> predicate, Consumer<AuditLogEntry> consumer) {
		return new AuditLogEntryHandler(executor, predicate, consumer);
	}

	public static AuditLogEntryHandler from(ScheduledExecutorService executor, long targetId, Consumer<AuditLogEntry> consumer) {
		return AuditLogEntryHandler.from(executor, getDefaultPredicate(targetId), consumer);
	}

	public static AuditLogEntryHandler from(ScheduledExecutorService executor, long targetId, Predicate<AuditLogEntry> predicate, Consumer<AuditLogEntry> consumer) {
		return new AuditLogEntryHandler(executor, getDefaultPredicate(targetId, predicate), consumer);
	}

	private static Predicate<AuditLogEntry> getDefaultPredicate(long targetId) {
		return getDefaultPredicate(targetId, null);
	}

	private static Predicate<AuditLogEntry> getDefaultPredicate(long targetId, Predicate<AuditLogEntry> predicate) {
		Predicate<AuditLogEntry> combined = entry ->
			Duration.between(entry.getTimeCreated(), ZonedDateTime.now(ZoneOffset.UTC)).toSeconds() <= 5 &&
			entry.getTargetIdLong() == targetId;

		if (predicate != null) {
			combined = combined.and(predicate);
		}

		return combined;
	}

}
