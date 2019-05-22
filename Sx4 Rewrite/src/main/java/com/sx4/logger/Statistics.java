package com.sx4.logger;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.sx4.core.Sx4Bot;
import com.sx4.logger.util.Utils;

public class Statistics {
	
	private static final String AUDIT_MESSAGE = "Successful audit logs %s/%s (Unsuccessful: %s, %.2f%%)";
	private static final String LOGS_MESSAGE = "Successful logs %s/%s (Skipped: %s, %.2f%%) (Unsuccessful: %s) (Bulked: %s)";
	
	private static final String WEBHOOK_MESSAGE = "Total webhooks registered %s";
	private static final String QUEUED_LOGS_MESSAGE = "Total queued logs %s";
	
	private static final AtomicInteger failedAuditLogs = new AtomicInteger();
	private static final AtomicInteger successfulAuditLogs = new AtomicInteger();
	
	private static final AtomicInteger successfulLogs = new AtomicInteger();
	private static final AtomicInteger bulkedLogs = new AtomicInteger();
	private static final AtomicInteger failedLogs = new AtomicInteger();
	private static final AtomicInteger skippedLogs = new AtomicInteger();
	
	public static void increaseSuccessfulAuditLogs() {
		Statistics.successfulAuditLogs.incrementAndGet();
	}
	
	public static void increaseFailedAuditLogs() {
		Statistics.failedAuditLogs.incrementAndGet();
	}
	
	public static void increaseSuccessfulLogs(int amount) {
		Statistics.successfulLogs.addAndGet(amount);
		
		if(amount > 1) {
			Statistics.bulkedLogs.addAndGet(amount);
		}
	}
	
	public static void increaseFailedLogs() {
		Statistics.failedLogs.incrementAndGet();
	}
	
	public static void increaseSkippedLogs() {
		Statistics.skippedLogs.incrementAndGet();
	}
	
	public static void printStatistics() {
		StringBuilder message = new StringBuilder();
		
		{
			int successful = Statistics.successfulAuditLogs.get();
			int failed = Statistics.failedAuditLogs.get();
			
			int total = successful + failed;
			
			message.append('\n').append(String.format(AUDIT_MESSAGE, successful, total, failed, total != 0 ? ((double) failed/total) * 100 : 0));
		}
		
		{
			int successful = Statistics.successfulLogs.get();
			int bulked = Statistics.bulkedLogs.get();
			int skipped = Statistics.skippedLogs.get();
			int failed = Statistics.failedLogs.get();
			
			int total = successful + skipped;
			
			message.append('\n').append(String.format(LOGS_MESSAGE, successful, total, skipped, total != 0 ? ((double) skipped/total) * 100 : 0, failed, bulked));
		}
		
		message.append('\n').append(String.format(WEBHOOK_MESSAGE, Sx4Bot.getEventHandler().getRegisteredWebhooks().size()));
		message.append('\n').append(String.format(QUEUED_LOGS_MESSAGE, Sx4Bot.getEventHandler().getTotalRequestsQueued()));
		
		System.out.println(Utils.getMessageSeperated(message));
	}
	
	static {
		new Thread(() -> {
			while(true) {
				Statistics.printStatistics();
				
				try {
					Thread.sleep(TimeUnit.MINUTES.toMillis(5));
				}catch(InterruptedException e) {}
			}
		}).start();
	}
}