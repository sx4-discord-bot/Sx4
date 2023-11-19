package com.sx4.bot.formatter.output;

import com.sx4.bot.formatter.output.function.*;
import com.sx4.bot.utility.ColourUtility;
import com.sx4.bot.utility.StringUtility;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;

import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public abstract class Formatter<Type> {

	protected final FormatterManager manager;
	protected final Type object;

	public Formatter(Type object, FormatterManager manager) {
		this.object = object;
		this.manager = manager;
	}

	public Formatter(Type object) {
		this(object, FormatterManager.getDefaultManager());
	}

	public Formatter<Type> addVariable(Class<?> type, String name, Object argument) {
		this.manager.addVariable(name, type, $ -> argument);

		return this;
	}

	public Formatter<Type> addVariable(String name, Object argument) {
		this.manager.addVariable(name, argument);

		return this;
	}

	public Formatter<Type> user(User user) {
		if (user == null) {
			return this;
		}

		return this.addVariable("user", user);
	}

	public Formatter<Type> member(Member member) {
		if (member == null) {
			return this;
		}

		return this.addVariable("member", member);
	}

	public Formatter<Type> guild(Guild guild) {
		if (guild == null) {
			return this;
		}

		return this.addVariable("server", guild);
	}

	public Formatter<Type> channel(MessageChannel channel) {
		if (channel == null) {
			return this;
		}

		return this.addVariable("channel", channel);
	}

	public Formatter<Type> role(Role role) {
		if (role == null) {
			return this;
		}

		return this.addVariable("role", role);
	}

	public Formatter<Type> emoji(EmojiUnion emoji) {
		if (emoji == null) {
			return this;
		}

		return this.addVariable("emote", emoji);
	}

	public abstract Type parse();

	public static boolean isEscaped(String string, int index) {
		return index > 0 && string.charAt(index - 1) == '\\';
	}

	private static List<Object> getFunctionArguments(FormatterFunction<?> function, String text, Object value, Class<?> type, FormatterManager manager) {
		if (type != Void.class && value == null) {
			return null;
		}

		List<Object> functionArguments = new ArrayList<>();
		functionArguments.add(new FormatterEvent<>(value, manager));

		FormatterArgument[] arguments = function.getArguments();
		int nextIndex, lastIndex = -1, commaIndex = -1, index = -1;
		do {
			nextIndex = text.indexOf(',', commaIndex + 1);
			if (nextIndex != -1 && (lastIndex >= nextIndex || Formatter.isEscaped(text, nextIndex) || StringUtility.isNotEqual(text.substring(lastIndex + 1, nextIndex), '{', '}'))) {
				commaIndex = nextIndex;
				continue;
			}

			String argument;
			if (++index == arguments.length - 1) {
				argument = text.substring(lastIndex + 1);
				nextIndex = -1;
			} else {
				argument = text.substring(lastIndex + 1, (lastIndex = nextIndex) == -1 ? text.length() : nextIndex);
			}

			FormatterArgument formatterArgument = arguments[index];
			if (formatterArgument.isExcludeFormatting()) {
				functionArguments.add(argument);
				continue;
			}

			Object argumentValue = Formatter.toObject(argument, formatterArgument.isUsePrevious() ? type : formatterArgument.getType(), manager);
			if (argumentValue == null && !formatterArgument.isAcceptNull()) {
				return null;
			}

			functionArguments.add(formatterArgument.isOptional() ? Optional.ofNullable(argumentValue) : argumentValue);
		} while (nextIndex != -1);

		for (int i = functionArguments.size() - 1; i < arguments.length; i++) {
			FormatterArgument formatterArgument = arguments[i];
			if (formatterArgument.isOptional()) {
				functionArguments.add(Optional.empty());
			} else {
				return null;
			}
		}

		return functionArguments;
	}

	public static Object getValue(String formatter, FormatterManager manager) {
		Class<?> type = Void.class;
		Object value = null;

		int periodIndex = -1;
		while (type == Void.class || periodIndex != -1) {
			if (periodIndex != 0 && Formatter.isEscaped(formatter, periodIndex)) {
				continue;
			}

			int nextPeriodIndex = formatter.indexOf('.', periodIndex + 1);
			String name = formatter.substring(value == null ? 0 : periodIndex + 1, nextPeriodIndex == -1 ? formatter.length() : nextPeriodIndex);

			while (StringUtility.isNotEqual(name, '(', ')') && nextPeriodIndex != -1) {
				nextPeriodIndex = formatter.indexOf('.', nextPeriodIndex + 1);
				name = formatter.substring(value == null ? 0 : periodIndex + 1, nextPeriodIndex == -1 ? formatter.length() : nextPeriodIndex);
			}

			int bracketIndex = 0;
			while (Formatter.isEscaped(name, bracketIndex = name.indexOf('(', bracketIndex + 1)) && bracketIndex != -1);

			int endBracketIndex = name.length();
			while (Formatter.isEscaped(name, endBracketIndex = name.lastIndexOf(')', endBracketIndex - 1)) && endBracketIndex != -1);

			periodIndex = nextPeriodIndex;
			if (endBracketIndex <= bracketIndex || bracketIndex == -1) {
				FormatterVariable<?> variable = manager.getVariable(type, name);
				if (variable == null) {
					FormatterFunction<?> function = manager.getFunction(type, "get");
					if (function != null) {
						try {
							List<Object> arguments = Formatter.getFunctionArguments(function, name, value, type, manager);
							if (arguments == null) {
								continue;
							}

							value = function.parse(arguments);
						} catch (InvocationTargetException | IllegalAccessException | IllegalArgumentException exception) {
							exception.printStackTrace();
							value = null;
						}
					} else if (nextPeriodIndex == -1) {
						return null;
					}
				} else {
					value = variable.parse(value);
				}
			} else {
				String argument = name.substring(bracketIndex + 1, endBracketIndex);
				String functionName = name.substring(0, bracketIndex);

				FormatterFunction<?> function = manager.getFunction(type, functionName);
				if (function == null) {
					return null;
				}

				List<Object> functionArguments = Formatter.getFunctionArguments(function, argument, value, type, manager);
				if (functionArguments == null) {
					return null;
				}

				try {
					value = function.parse(functionArguments);
				} catch (InvocationTargetException | IllegalAccessException | IllegalArgumentException exception) {
					exception.printStackTrace();
					value = null;
				}
			}

			if (value == null) {
				continue;
			}

			type = value.getClass();
		}

		return value;
	}

	public static String format(String string, FormatterManager manager) {
		int index = -1;
		Open: while ((index = string.indexOf('{', index + 1)) != -1) {
			if (Formatter.isEscaped(string, index)) {
				continue;
			}

			int endIndex = index;
			while ((endIndex = string.indexOf('}', endIndex + 1)) != -1) {
				if (Formatter.isEscaped(string, endIndex)) {
					continue;
				}

				String formatter = string.substring(index + 1, endIndex);
				if (StringUtility.isNotEqual(formatter, '{', '}')) {
					continue;
				}

				Object value = Formatter.getValue(formatter, manager);
				if (value == null) {
					continue;
				}

				string = string.substring(0, index) + Formatter.toString(value) + string.substring(endIndex + 1);

				continue Open;
			}
		}

		return string;
	}

	public static String toString(Object object) {
		if (object == null) {
			return "null";
		} else if (object instanceof Color colour) {
			return ColourUtility.toHexString(colour.getRGB());
		} else if (object instanceof Double value && value % 1 == 0) {
			return Long.toString(value.longValue());
		}

		return object.toString();
	}

	public static Object toObject(String text, Class<?> type, FormatterManager manager) {
		return Formatter.toObject(text, type, manager, true);
	}

	public static Object toObject(String text, Class<?> type, FormatterManager manager, boolean useParser) {
		if (text.length() > 0 && text.charAt(0) == '{' && text.charAt(text.length() - 1) == '}' && text.charAt(text.length() - 2) != '\\') {
			String valueArgument = text.substring(1, text.length() - 1);
			if (!StringUtility.isNotEqual(valueArgument, '{', '}', true)) {
				Object value = Formatter.getValue(valueArgument, manager);
				if (value != null && type.isAssignableFrom(value.getClass())) {
					return value;
				}
			}
		}

		if (!useParser) {
			return Formatter.format(text, manager);
		}

		FormatterParser<?> parser = manager.getParser(type);
		if (parser == null) {
			return null;
		} else {
			return parser.parse(Formatter.format(text, manager));
		}
	}

}
