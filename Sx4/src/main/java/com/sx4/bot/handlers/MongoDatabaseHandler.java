package com.sx4.bot.handlers;

import com.mongodb.event.ClusterClosedEvent;
import com.mongodb.event.ClusterListener;
import com.mongodb.event.ClusterOpeningEvent;

public class MongoDatabaseHandler implements ClusterListener {
	
	public void clusterOpening(ClusterOpeningEvent event) {
		System.out.println("Connected to the MongoDB server");
	}
	
	public void clusterClosed(ClusterClosedEvent event) {
		System.err.println("Disconnected from the MongoDB server");
	}
	
}
