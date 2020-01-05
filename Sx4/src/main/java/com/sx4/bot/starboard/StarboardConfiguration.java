package com.sx4.bot.starboard;

import java.util.List;
import java.util.stream.Collectors;

import org.bson.Document;

public class StarboardConfiguration {
	
	private final int id;
	private final String message;

	public StarboardConfiguration(Document configuration) {
		this.id = configuration.getInteger("id");
		this.message = configuration.getString("message");
	}
	
	public int getId() {
		return this.id;
	}
	
	public String getMessage() {
		return this.message;
	}
	
	public static List<StarboardConfiguration> fromRaw(List<Document> configuration) {
		return configuration.stream().map(StarboardConfiguration::new).collect(Collectors.toList());
	}
	
}
