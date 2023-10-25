package com.sx4.bot.formatter.output.function;

public class FormatterSwitch {

	protected final Object value;
	private Object defaultValue = null;

	public FormatterSwitch(Object match) {
		this.value = match;
	}

	public FormatterSwitch checkCase(Object query, Object value) {
		if (query.equals(this.value)) {
			return new CompletedFormatterSwitch(value);
		} else {
			return this;
		}
	}

	public FormatterSwitch addDefault(Object defaultValue) {
		this.defaultValue = defaultValue;

		return this;
	}

	public String toString() {
		return this.defaultValue == null ? "" : this.defaultValue.toString();
	}

	public static class CompletedFormatterSwitch extends FormatterSwitch {

		public CompletedFormatterSwitch(Object value) {
			super(value);
		}

		@Override
		public CompletedFormatterSwitch checkCase(Object query, Object returnObject) {
			return this;
		}

		@Override
		public CompletedFormatterSwitch addDefault(Object defaultValue) {
			return this;
		}

		public String toString() {
			return this.value.toString();
		}

	}


}
