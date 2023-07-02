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

	public <T> T getArgument(int index, Function<String, T> function) {
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

	@SuppressWarnings({"unchecked"})
	public static abstract class Builder<Type extends CustomInteractionId, Builder extends CustomInteractionId.Builder<Type, Builder>> {

		protected int type;
		protected String[] arguments;

		public Builder setType(int type) {
			this.type = type;

			return (Builder) this;
		}

		public Builder setArguments(Object... arguments) {
			String[] newArguments = new String[arguments.length];
			for (int i = 0; i < arguments.length; i++) {
				newArguments[i] = arguments[i].toString();
			}

			this.arguments = newArguments;

			return (Builder) this;
		}

		public abstract Type build();

		public String getId() {
			return this.build().getId();
		}

	}

}
