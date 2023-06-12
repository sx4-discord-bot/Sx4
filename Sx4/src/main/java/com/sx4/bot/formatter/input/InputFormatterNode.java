package com.sx4.bot.formatter.input;

public class InputFormatterNode {

	private final InputFormatterArgument argument;
	private final String text;

	public InputFormatterNode(InputFormatterArgument argument) {
		this.argument = argument;
		this.text = null;
	}

	public InputFormatterNode(String text) {
		this.argument = null;
		this.text = text;
	}

	public String getText() {
		return this.text;
	}

	public boolean isText() {
		return this.text != null;
	}

	public InputFormatterArgument getArgument() {
		return this.argument;
	}

	public String toString() {
		return this.isText() ? this.getText() : "Argument(" + this.getArgument().getName() + ")";
	}

}
