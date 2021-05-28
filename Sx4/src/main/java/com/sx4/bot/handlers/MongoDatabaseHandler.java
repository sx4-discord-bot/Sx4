package com.sx4.bot.handlers;

import com.mongodb.event.*;

import java.util.concurrent.TimeUnit;

public class MongoDatabaseHandler implements CommandListener, ClusterListener {
	
	public void commandSucceeded(CommandSucceededEvent event) {
		System.out.println("Successfully ran mongo command with name " + event.getCommandName() + ", elapsed: " + event.getElapsedTime(TimeUnit.NANOSECONDS));
	}
	
	public void clusterOpening(ClusterOpeningEvent event) {
		System.out.println("Connected to the MongoDB server");
	}
	
	public void clusterClosed(ClusterClosedEvent event) {
		System.err.println("Disconnected from the MongoDB server");
	}
	
}
