package com.sx4.bot.entities.argument;

public class Or<FirstType, SecondType> {

	private final FirstType first;
	private final SecondType second;
	
	public Or(FirstType first, SecondType second) {
		this.first = first;
		this.second = second;
	}
	
	public FirstType getFirst() {
		return this.first;
	}
	
	public boolean hasFirst() {
		return this.first != null;
	}
	
	public SecondType getSecond() {
		return this.second;
	}
	
	public boolean hasSecond() {
		return this.second != null;
	}
	
}
