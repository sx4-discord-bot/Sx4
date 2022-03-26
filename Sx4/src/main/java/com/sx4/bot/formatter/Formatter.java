package com.sx4.bot.formatter;

import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterFunction;
import com.sx4.bot.formatter.function.FormatterParser;
import com.sx4.bot.formatter.function.FormatterVariable;
import com.sx4.bot.utility.ColourUtility;
import com.sx4.bot.utility.StringUtility;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.MessageReaction.ReactionEmote;

import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

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

	public Formatter<Type> channel(TextChannel channel) {
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

	public Formatter<Type> emote(ReactionEmote emote) {
		if (emote == null) {
			return this;
		}

		return this.addVariable("emote", emote);
	}

	public abstract Type parse();

	public static boolean isEscaped(String string, int index) {
		return index > 0 && string.charAt(index - 1) == '\\';
	}

	private static List<Object> getFunctionArguments(FormatterFunction<?> function, String text, Object value, Class<?> type, FormatterManager manager) {
		List<Object> functionArguments = new ArrayList<>();
		functionArguments.add(new FormatterEvent<>(value, manager));

		Class<?>[] parameters = function.getMethod().getParameterTypes();
		if (parameters.length > 2) {
			int lastIndex = -1, i = 0;
			do {
				int nextIndex = text.indexOf(',', lastIndex + 1);
				if (Formatter.isEscaped(text, nextIndex)) {
					continue;
				}

				String argument;
				if (++i == parameters.length - 1) {
					argument = text.substring(lastIndex + 1);
					lastIndex = -1;
				} else {
					argument = text.substring(lastIndex + 1, (lastIndex = nextIndex) == -1 ? text.length() : nextIndex);
				}

				Object argumentValue = Formatter.toObject(argument, function.isUsePrevious() ? type : parameters[i], manager);
				if (argumentValue == null) {
					return null;
				}

				functionArguments.add(argumentValue);
			} while (lastIndex != -1);

			if (functionArguments.size() < parameters.length - 1) {
				return null;
			}
		} else if (parameters.length == 2) {
			Object argumentValue = Formatter.toObject(text, function.isUsePrevious() ? type : parameters[1], manager);
			if (argumentValue == null) {
				return null;
			}

			functionArguments.add(argumentValue);
		}

		return functionArguments;
	}

	private static Object getValue(String formatter, FormatterManager manager) {
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
			if (endBracketIndex <= bracketIndex) {
				FormatterVariable<?> variable = manager.getVariable(type, name);
				if (variable == null && nextPeriodIndex == -1) {
					return null;
				} else if (variable == null) {
					continue;
				}

				value = variable.parse(value);
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
		} else if (object instanceof Double value) {
			if (value % 1 == 0) {
				return Long.toString(value.longValue());
			}
		}

		return object.toString();
	}

	public static Object toObject(String text, Class<?> type, FormatterManager manager) {
		if (text.length() > 0 && text.charAt(0) == '{' && text.charAt(text.length() - 1) == '}' && text.charAt(text.length() - 2) != '\\') {
			Object value = Formatter.getValue(text.substring(1, text.length() - 1), manager);
			if (value != null && type.isAssignableFrom(value.getClass())) {
				return value;
			}
		}

		FormatterParser<?> parser = manager.getParser(type);
		if (parser == null) {
			return null;
		} else {
			return parser.parse(Formatter.format(text, manager));
		}
	}

}
