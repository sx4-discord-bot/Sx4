package com.sx4.bot.entities.argument;

import java.util.Arrays;
import java.util.List;

public enum UpdateType {
	
	ENABLE(true, "enabled"),
	DISABLE(false, "disabled"),
	TOGGLE(null, "toggled");
	
	private static final List<UpdateType> LIST = Arrays.asList(UpdateType.values());
	
	private final Boolean value;
	private final String name;
	
	private UpdateType(Boolean value, String name) {
		this.value = value;
		this.name = name;
	}
	
	public Boolean getValue() {
		return this.value;
	}
	
	public String getName() {
		return this.name;
	}
	
	public static List<UpdateType> listValues() {
		return UpdateType.LIST;
	}
	
}
