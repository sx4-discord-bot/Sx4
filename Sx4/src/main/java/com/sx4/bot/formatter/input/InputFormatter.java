package com.sx4.bot.formatter.input;

import com.sx4.bot.formatter.exception.InvalidFormatterSyntax;
import com.sx4.bot.formatter.input.InputFormatterNode.TextNode;

import java.util.*;
import java.util.function.Function;

public class InputFormatter {

	private final String formatter;
	private final InputFormatterManager manager;

	public InputFormatter(String formatter, InputFormatterManager manager) {
		this.formatter = formatter;
		this.manager = manager;
	}

	public InputFormatter(String formatter) {
		this(formatter, InputFormatterManager.getDefaultManager());
	}

	private Map.Entry<String, Object> parseOption(String name, String option) {
		StringBuilder key = new StringBuilder();
		StringBuilder value = new StringBuilder();
		boolean keyDone = false;

		for (int i = 0; i < option.length(); i++) {
			char character = option.charAt(i);
			if (character == '=' && (i == 0 || option.charAt(i - 1) != '\\')) {
				if (keyDone) {
					throw new InvalidFormatterSyntax("Multiple '=' signs given, if you want to use an `=` in the key or value escape it like so \\\\=");
				}

				keyDone = true;
			} else {
				(keyDone ? value : key).append(character);
			}
		}

		if (!keyDone) {
			throw new InvalidFormatterSyntax("No `key=value` format given");
		}

		return Map.entry(key.toString(), this.manager.getMapping(name, key.toString()).apply(value.toString()));
	}

	private InputFormatterArgument parseInputFormatter(String formatter) {
		StringBuilder name = new StringBuilder();
		StringBuilder option = new StringBuilder();
		boolean nameDone = false;

		Map<String, Object> options = new HashMap<>();
		for (int i = 0; i < formatter.length(); i++) {
			char character = formatter.charAt(i);
			if (character == ':' && i == 0) {
				throw new InvalidFormatterSyntax("Type name not given for the input formatter");
			}

			if (character == ':' && formatter.charAt(i - 1) != '\\') {
				if (nameDone) {
					Map.Entry<String, Object> entry = this.parseOption(name.toString(), option.toString());
					options.put(entry.getKey(), entry.getValue());
					option.setLength(0);
				} else {
					if (name.length() == 0) {
						throw new InvalidFormatterSyntax("Type name for input formatter not given");
					}

					nameDone = true;
				}
			} else {
				(nameDone ? option : name).append(character);
			}

			if (i == formatter.length() - 1 && nameDone) {
				Map.Entry<String, Object> entry = this.parseOption(name.toString(), option.toString());
				options.put(entry.getKey(), entry.getValue());
			}
		}

		if (name.length() == 0) {
			throw new InvalidFormatterSyntax("Type name for input formatter not given");
		}

		return new InputFormatterArgument(name.toString(), options);
	}

	public List<InputFormatterNode<?>> getNodes() {
		List<InputFormatterNode<?>> nodes = new ArrayList<>();

		StringBuilder text = new StringBuilder();
		StringBuilder inputFormatter = new StringBuilder();
		boolean formatter = false;

		for (int i = 0; i < this.formatter.length(); i++) {
			char character = this.formatter.charAt(i);
			if (i == 0 && character == '}') {
				throw new InvalidFormatterSyntax("`}` given with no starting brace, use `\\}` if you want to use an escaped version");
			}

			char characterBefore = i == 0 ? 0 : this.formatter.charAt(i - 1);
			if (character == '{') {
				if (i != 0 && characterBefore == '\\') {
					text.setLength(text.length() - 1);
					text.append(character);
				} else {
					if (text.length() == 0 && i != 0) {
						throw new InvalidFormatterSyntax("There must be text to separate 2 input formatters");
					}

					if (text.length() > 0) {
						nodes.add(InputFormatterNode.ofText(text.toString()));
						text.setLength(0);
					}

					formatter = true;
				}
			} else if (character == '}') {
				if (characterBefore == '\\') {
					text.setLength(text.length() - 1);
					text.append(character);
				} else {
					if (!formatter) {
						throw new InvalidFormatterSyntax("`}` given with no starting brace, use `\\}` if you want to use an escaped version");
					}

					nodes.add(InputFormatterNode.ofArgument(this.parseInputFormatter(inputFormatter.toString())));
					inputFormatter.setLength(0);

					formatter = false;
				}
			} else {
				(formatter ? inputFormatter : text).append(character);
			}

			if (i == this.formatter.length() - 1) {
				if (formatter) {
					throw new InvalidFormatterSyntax("`{` starting brace used with no ending brace, use `\\{` if you want to use an escaped version");
				}

				if (text.length() > 0) {
					nodes.add(InputFormatterNode.ofText(text.toString()));
				}
			}
		}

		return nodes;
	}

	public List<Object> parse(String query, boolean caseSensitive) {
		String newQuery = caseSensitive ? query : query.toLowerCase(Locale.ROOT);

		List<InputFormatterNode<?>> nodes = this.getNodes();
		List<Object> arguments = new ArrayList<>();

		int index = 0, previousIndex;
		for (int i = 0; i < nodes.size();) {
			InputFormatterNode<?> firstNode = nodes.get(i);

			if (firstNode instanceof TextNode textNode) {
				String text = textNode.getValue();
				if (nodes.size() == 1 && query.length() > text.length()) {
					return null;
				}

				index = newQuery.indexOf(caseSensitive ? text : text.toLowerCase(Locale.ROOT), index);
				if (index != 0) {
					return null;
				}

				index += text.length();
				i++;

				continue;
			}

			previousIndex = index;

			boolean quote = false;

			int quoteIndex = newQuery.indexOf("\"", index);
			while (quoteIndex != -1 && (quoteIndex == 0 || newQuery.charAt(quoteIndex - 1) == '\\')) {
				quoteIndex = newQuery.indexOf("\"", quoteIndex + 1);
			}

			if (quoteIndex != -1) {
				int endQuoteIndex = newQuery.indexOf("\"", quoteIndex + 1);
				while (endQuoteIndex != -1 && (endQuoteIndex == 0 || newQuery.charAt(endQuoteIndex - 1) == '\\')) {
					endQuoteIndex = newQuery.indexOf("\"", endQuoteIndex + 1);
				}

				if (endQuoteIndex != -1) {
					previousIndex += 1;
					index = endQuoteIndex;
					quote = true;
				}
			}

			String text = null;
			if (i + 1 >= nodes.size()) {
				index = newQuery.length();
			} else {
				text = (String) nodes.get(i + 1).getValue();
				if (quote) {
					index += 1;

					if (!query.startsWith(text, index)) {
						return null;
					}
				} else {
					index = newQuery.indexOf(caseSensitive ? text : text.toLowerCase(Locale.ROOT), index);
					if (index == -1) {
						return null;
					}
				}
			}

			InputFormatterArgument argument = (InputFormatterArgument) firstNode.getValue();
			InputFormatterParser<?> parser = this.manager.getParser(argument.getName());
			if (parser == null) {
				return null;
			}

			String argumentNode = query.substring(previousIndex, index - (quote ? 1 : 0));
			index += text == null ? 0 : text.length();

			Object object = parser.parse(argumentNode, argument.getOptions());
			if (object == null) {
				return null;
			}

			arguments.add(object);

			i += 2;
		}

		return arguments;
	}

	public static Function<String, Number> parseNumber(String key, Function<String, Number> mapping) {
		return text -> {
			try {
				return mapping.apply(text);
			} catch (NumberFormatException e) {
				throw new InvalidFormatterSyntax("`" + key + "` key is not an unsigned integer");
			}
		};
	}

}
