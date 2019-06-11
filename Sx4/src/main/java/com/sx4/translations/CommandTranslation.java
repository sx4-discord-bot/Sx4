package com.sx4.translations;

import java.util.HashMap;
import java.util.Map;

public class CommandTranslation {
	
	private String description = null;
	private TranslationType translationType = null;
	private Map<String, String> strings = new HashMap<>();

	public CommandTranslation(TranslationType translationType, String description, Map<String, String> strings) {
		this.translationType = translationType;
		this.description = description;
		this.strings = strings;
	}
	
	public String getDescription() {
		return this.description;
	}
	
	public CommandTranslation setDescription(String description) {
		this.description = description;
		
		return this;
	}
	
	public Map<String, String> getStrings() {
		return this.strings;
	}
	
	public CommandTranslation setStrings(Map<String, String> strings) {
		this.strings = strings;
		
		return this;
	}
	
	public TranslationType getTranslationType() {
		return this.translationType;
	}
	
}
