package com.sx4.bot.formatter.input;

public class InputFormatterNode<Type> {

	private final Type value;

	public InputFormatterNode(Type value) {
		this.value = value;
	}

	public Type getValue() {
		return this.value;
	}

	public static TextNode ofText(String text) {
		return new TextNode(text);
	}

	public static ArgumentNode ofArgument(InputFormatterArgument argument) {
		return new ArgumentNode(argument);
	}

	public static class TextNode extends InputFormatterNode<String> {

		private TextNode(String text) {
			super(text);
		}

	}

	public static class ArgumentNode extends InputFormatterNode<InputFormatterArgument> {

		private ArgumentNode(InputFormatterArgument argument) {
			super(argument);
		}

	}

}
