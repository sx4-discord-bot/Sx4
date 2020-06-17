package com.sx4.bot.entities.argument;

import java.time.Duration;

public class TimedArgument<Type> {

	private final Duration duration;
	private final Type argument;
	
	public TimedArgument(Duration duration, Type argument) {
		this.duration = duration;
		this.argument = argument;
	}
	
	public boolean hasDuration() {
		return this.duration != null;
	}
	
	public Duration getDuration() {
		return this.duration;
	}
	
	public long getSeconds() {
		return this.duration.toSeconds();
	}
	
	public Type getArgument() {
		return this.argument;
	}
	
}
