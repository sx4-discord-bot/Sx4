package com.sx4.bot.entities.interaction;

import java.util.function.Function;

public abstract class CustomInteractionId {

	protected final int type;
	protected final String[] arguments;

	protected CustomInteractionId(int type, String[] arguments) {
		this.type = type;
		this.arguments = arguments;
	}

	public int getType() {
		return this.type;
	}

	public String[] getArguments() {
		return this.arguments;
	}

	public <Type> Type getArgument(int index, Function<String, Type> function) {
		return function.apply(this.arguments[index]);
	}

	public String getArgument(int index) {
		return this.getArgument(index, Function.identity());
	}

	public long getArgumentLong(int index) {
		return this.getArgument(index, Long::parseLong);
	}

	public int getArgumentInt(int index) {
		return this.getArgument(index, Integer::parseInt);
	}

	public abstract String getId();

	public static abstract class Builder {

		protected int type;
		protected String[] arguments;

		public CustomInteractionId.Builder setType(int type) {
			this.type = type;

			return this;
		}

		public CustomInteractionId.Builder setArguments(Object... arguments) {
			String[] newArguments = new String[arguments.length];
			for (int i = 0; i < arguments.length; i++) {
				newArguments[i] = arguments[i].toString();
			}

			this.arguments = newArguments;

			return this;
		}

		public abstract CustomInteractionId build();

		public String getId() {
			return this.build().getId();
		}

	}

}