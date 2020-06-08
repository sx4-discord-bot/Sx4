package com.sx4.bot.entities.management;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;

import com.sx4.bot.config.Config;

import net.dv8tion.jda.api.entities.Role;

public class State {
	
	public static final State PENDING = new State("Pending", "PENDING", Role.DEFAULT_COLOR_RAW);
	public static final State ACCEPTED = new State("Accepted", "ACCEPTED", Config.get().getGreen());
	public static final State DENIED = new State("Denied", "DENIED", Config.get().getRed());
	
	public static final List<Document> DEFAULT_STATES = List.of(
		State.PENDING.toData(),
		State.ACCEPTED.toData(),
		State.DENIED.toData()
	);

	private final int colour;
	private final String name;
	private final String dataName;
	
	public State(Document data) {
		this(data.getString("name"), data.getString("dataName"), data.getInteger("colour"));
	}
	
	public State(String name, int colour) {
		this(name, name.toUpperCase().replace(" ", "_"), colour);
	}
	
	public State(String name, String dataName, int colour) {
		this.name = name;
		this.dataName = dataName;
		this.colour = colour;
	}
	
	public String getName() {
		return this.name;
	}
	
	public String getDataName() {
		return this.dataName;
	}
	
	public int getColour() {
		return this.colour;
	}
	
	public Document toData() {
		return new Document("name", this.name)
			.append("dataName", this.dataName)
			.append("colour", this.colour);
	}
	
	public static List<Document> getDefaultStates() {
		return new ArrayList<>(State.DEFAULT_STATES);
	}
	
}
