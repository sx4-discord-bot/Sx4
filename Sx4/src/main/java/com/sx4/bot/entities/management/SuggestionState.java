package com.sx4.bot.entities.management;

import com.sx4.bot.config.Config;
import net.dv8tion.jda.api.entities.Role;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

public class SuggestionState {
	
	public static final SuggestionState PENDING = new SuggestionState("Pending", "PENDING", Role.DEFAULT_COLOR_RAW);
	public static final SuggestionState ACCEPTED = new SuggestionState("Accepted", "ACCEPTED", Config.get().getGreen());
	public static final SuggestionState DENIED = new SuggestionState("Denied", "DENIED", Config.get().getRed());
	
	public static final List<Document> DEFAULT_STATES = List.of(
		SuggestionState.PENDING.toData(),
		SuggestionState.ACCEPTED.toData(),
		SuggestionState.DENIED.toData()
	);

	private final int colour;
	private final String name;
	private final String dataName;
	
	public SuggestionState(Document data) {
		this(data.getString("name"), data.getString("dataName"), data.getInteger("colour"));
	}
	
	public SuggestionState(String name, int colour) {
		this(name, name.toUpperCase().replace(" ", "_"), colour);
	}
	
	public SuggestionState(String name, String dataName, int colour) {
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
		return new ArrayList<>(SuggestionState.DEFAULT_STATES);
	}
	
}
