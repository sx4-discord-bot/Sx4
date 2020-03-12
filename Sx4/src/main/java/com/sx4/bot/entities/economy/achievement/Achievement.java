package com.sx4.bot.entities.economy.achievement;

public class Achievement {

	private final int id;
	
	private final String title;
	private final String description;
	private final boolean secret;
	
	private final Reward reward;
	
	public Achievement(int id, String title, String description, Reward reward, boolean secret) {
		this.id = id;
		this.title = title;
		this.description = description;
		this.reward = reward;
		this.secret = secret;
	}
	
	public int getId() {
		return this.id;
	}
	
	public String getTitle() {
		return this.title;
	}
	
	public String getDescription() {
		return this.description;
	}
	
	public Reward getReward() {
		return this.reward;
	}
	
	public boolean isSecret() {
		return this.secret;
	}
	
}
