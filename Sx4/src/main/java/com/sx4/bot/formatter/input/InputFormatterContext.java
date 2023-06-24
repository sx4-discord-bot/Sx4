package com.sx4.bot.formatter.input;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;

import java.util.HashMap;
import java.util.Map;

public class InputFormatterContext {

	private final Map<String, Object> variables;
	private final Message message;

	public InputFormatterContext(Message message) {
		this.variables = new HashMap<>();
		this.message = message;
	}

	public Message getMessage() {
		return this.message;
	}

	public Guild getGuild() {
		return this.message.getGuild();
	}

	@SuppressWarnings({"unchecked"})
	public <T> T getVariable(String key, Class<T> clazz) {
		return (T) this.variables.get(key);
	}

	@SuppressWarnings({"unchecked"})
	public <T> T getVariable(String key, T defaultValue) {
		T value = (T) this.variables.get(key);
		return value == null ? defaultValue : value;
	}

	public InputFormatterContext setVariable(String key, Object object) {
		this.variables.put(key, object);

		return this;
	}

}
