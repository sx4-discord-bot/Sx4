package com.sx4.bot.entities.info;

public enum IGDBSort {

	NAME("name"),
	RELEASE("first_release_date"),
	RATING("total_rating");

	private final String name;

	private IGDBSort(String name) {
		this.name = name;
	}

	public String getName() {
		return this.name;
	}

}
