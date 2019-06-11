package com.sx4.translations;

import java.util.HashMap;
import java.util.Map;

import com.sx4.core.Sx4Command;

public class CommandTranslation {
	
	private Sx4Command command = null;
	private String description = null;
	private Map<String, String> strings = new HashMap<>();

	public CommandTranslation(Sx4Command command, String description, Map<String, String> strings) {
		this.command = command;
		this.description = description;
		this.strings = strings;
	}
	
	public Sx4Command getCommand() {
		return this.command;
	}
	
	public String getDescription() {
		return this.description;
	}
	
	public Map<String, String> getStrings() {
		return this.strings;
	}
	
}
