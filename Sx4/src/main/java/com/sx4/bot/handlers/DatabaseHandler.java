package com.sx4.bot.handlers;

import java.util.concurrent.TimeUnit;

import com.mongodb.event.ClusterClosedEvent;
import com.mongodb.event.ClusterListener;
import com.mongodb.event.ClusterOpeningEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandSucceededEvent;

public class DatabaseHandler implements CommandListener, ClusterListener {
	
	public void commandSucceeded(CommandSucceededEvent event) {
		System.out.println("Succesfully ran database commmand with name " + event.getCommandName() + ", elapsed: " + event.getElapsedTime(TimeUnit.NANOSECONDS));
	}
	
	public void clusterOpening(ClusterOpeningEvent event) {
		System.out.println("Connected to the MongoDB server");
	}
	
	public void clusterClosed(ClusterClosedEvent event) {
		System.err.println("Disconnected from the MongoDB server");
	}
	
}
