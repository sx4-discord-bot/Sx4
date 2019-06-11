package com.sx4.translations;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.sx4.core.Sx4Command;
import com.sx4.utils.ArgumentUtils;

public class Translation {
	
	private TranslationType translationType = null;
	private List<CommandTranslation> commandTranslations = new ArrayList<>();

	@SuppressWarnings("unchecked")
	public Translation(TranslationType translationType, Map<String, Map<String, Object>> data) {
		this.translationType = translationType;
		
		for (String key : data.keySet()) {
			Sx4Command command = ArgumentUtils.getCommand(key);
			Map<String, Object> commandData = data.get(key);
			
			String description = (String) commandData.get("description");
			Map<String, String> strings = (Map<String, String>) commandData.get("strings");
			
			this.commandTranslations.add(new CommandTranslation(command, description, strings));
		}
	}
	
	public TranslationType getTranslationType() {
		return this.translationType;
	}
	
	public List<CommandTranslation> getCommandTranslations() {
		return this.commandTranslations;
	}
	
}
