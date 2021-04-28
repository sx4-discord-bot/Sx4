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

public interface IFormatter<Type> {

	IFormatter<Type> addVariable(String key, Object object);

	default IFormatter<Type> user(User user) {
		if (user == null) {
			return this;
		}

		return this.addVariable("user", user);
	}

	default IFormatter<Type> member(Member member) {
		if (member == null) {
			return this;
		}

		return this.addVariable("member", member);
	}

	default IFormatter<Type> guild(Guild guild) {
		if (guild == null) {
			return this;
		}

		return this.addVariable("server", guild);
	}

	default IFormatter<Type> channel(TextChannel channel) {
		if (channel == null) {
			return this;
		}

		return this.addVariable("channel", channel);
	}

	default IFormatter<Type> role(Role role) {
		if (role == null) {
			return this;
		}

		return this.addVariable("role", role);
	}

	default IFormatter<Type> emote(ReactionEmote emote) {
		if (emote == null) {
			return this;
		}

		return this.addVariable("emote", emote);
	}

	Type parse();

	public static boolean escape(String string, int index) {
		if (index > 0 && string.charAt(index - 1) == '\\') {
			string = string.substring(0, index - 1) + string.substring(index);
			return true;
		}

		return false;
	}

	public static String ternary(String string, FormatterManager manager) {
		int index = string.length();
		Brackets: while ((index = string.lastIndexOf('(', index - 1)) != -1) {
			if (IFormatter.escape(string, index)) {
				continue;
			}

			int endIndex = index;
			while ((endIndex = string.indexOf(')', endIndex + 1)) != -1) {
				if (IFormatter.escape(string, endIndex)) {
					continue;
				}
				
				if (StringUtility.isNotEqual(string.substring(index + 1, endIndex), '(', ')')) {
					continue;
				}

				int condIndex = index;
				while ((condIndex = string.indexOf('?', condIndex + 1)) != -1) {
					if (IFormatter.escape(string, condIndex)) {
						continue;
					}

					int endCondIndex = condIndex;
					while ((endCondIndex = string.indexOf(':', endCondIndex + 1)) != -1) {
						if (IFormatter.escape(string, endCondIndex)) {
							continue;
						}

						String ifFormatter = string.substring(condIndex + 1, endCondIndex);
						if (StringUtility.isNotEqual(ifFormatter, '?', ':')) {
							continue;
						}

						String elseFormatter = string.substring(endCondIndex + 1, endIndex);

						Object condition = IFormatter.toObject(string.substring(index + 1, condIndex), Boolean.class, manager);
						if (condition == null) {
							condition = false;
						}

						string = string.substring(0, index) + ((boolean) condition ? ifFormatter : elseFormatter) + string.substring(endIndex + 1);

						continue Brackets;
					}
				}
			}
		}

		return string;
	}

	private static List<Object> getFunctionArguments(FormatterFunction<?> function, String text, Object value, Class<?> type, FormatterManager manager) {
		List<Object> functionArguments = new ArrayList<>();
		functionArguments.add(new FormatterEvent(value, manager));

		Class<?>[] parameters = function.getMethod().getParameterTypes();
		if (parameters.length > 2) {
			int lastIndex = -1, i = 0;
			do {
				int nextIndex = text.indexOf(',', lastIndex + 1);
				if (IFormatter.escape(text, nextIndex)) {
					continue;
				}

				Object argumentValue = IFormatter.toObject(text.substring(lastIndex + 1, lastIndex = nextIndex == -1 ? text.length() : nextIndex), function.isUsePrevious() ? type : parameters[i++ + 1], manager);
				if (argumentValue == null) {
					return null;
				}

				functionArguments.add(argumentValue);
			} while (lastIndex != text.length());

			if (functionArguments.size() < parameters.length - 1) {
				return null;
			}
		} else if (parameters.length == 2) {
			Object argumentValue = IFormatter.toObject(text, function.isUsePrevious() ? type : parameters[1], manager);
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
			if (periodIndex != 0 && IFormatter.escape(formatter, periodIndex)) {
				continue;
			}

			int nextPeriodIndex = formatter.indexOf('.', periodIndex + 1);
			String name = formatter.substring(periodIndex + 1, nextPeriodIndex == -1 ? formatter.length() : nextPeriodIndex);

			while (StringUtility.isNotEqual(name, '(', ')') && nextPeriodIndex != -1) {
				nextPeriodIndex = formatter.indexOf('.', nextPeriodIndex + 1);
				name = formatter.substring(periodIndex + 1, nextPeriodIndex == -1 ? formatter.length() : nextPeriodIndex);
			}

			int bracketIndex = 0;
			while (IFormatter.escape(name, bracketIndex = name.indexOf('(', bracketIndex + 1)) && bracketIndex != -1);

			int endBracketIndex = name.length();
			while (IFormatter.escape(name, endBracketIndex = name.lastIndexOf(')', endBracketIndex - 1)) && endBracketIndex != -1);

			if (bracketIndex == -1 || endBracketIndex == -1) {
				FormatterVariable<?> variable = manager.getVariable(type, name);
				if (variable == null) {
					return null;
				}

				value = variable.parse(value);
			} else {
				String argument = name.substring(bracketIndex + 1, endBracketIndex);
				String functionName = name.substring(0, bracketIndex);

				FormatterFunction<?> function = manager.getFunction(type, functionName);
				if (function == null) {
					return null;
				}

				List<Object> functionArguments = IFormatter.getFunctionArguments(function, argument, value, type, manager);
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

			periodIndex = nextPeriodIndex;
		}

		return value;
	}

	public static String format(String string, FormatterManager manager) {
		int index = -1;
		Open: while ((index = string.indexOf('{', index + 1)) != -1) {
			if (IFormatter.escape(string, index)) {
				continue;
			}

			int endIndex = index;
			while ((endIndex = string.indexOf('}', endIndex + 1)) != -1) {
				if (IFormatter.escape(string, endIndex)) {
					continue;
				}

				String formatter = string.substring(index + 1, endIndex);
				if (StringUtility.isNotEqual(formatter, '{', '}')) {
					continue;
				}

				Object value = IFormatter.getValue(formatter, manager);
				if (value == null) {
					continue;
				}

				string = string.substring(0, index) + IFormatter.toString(value) + string.substring(endIndex + 1);

				continue Open;
			}
		}

		return string;
	}

	private static String toString(Object object) {
		if (object == null) {
			return "null";
		} else if (object instanceof Color) {
			return ColourUtility.toHexString(((Color) object).getRGB());
		} else if (object instanceof Double) {
			Double value = ((Double) object);
			if (value % 1 == 0) {
				return String.valueOf(value.longValue());
			}
		}

		return object.toString();
	}

	public static Object toObject(String text, Class<?> type, FormatterManager manager) {
		if (text.charAt(0) == '{' && text.charAt(text.length() - 1) == '}' && text.charAt(text.length() - 2) != '\\') {
			Object value = IFormatter.getValue(text.substring(1, text.length() - 1), manager);
			if (value != null && type.isAssignableFrom(value.getClass())) {
				return value;
			}
		}

		FormatterParser<?> parser = manager.getParser(type);
		if (parser == null) {
			return null;
		} else {
			return parser.parse(IFormatter.format(text, manager));
		}
	}

	default String parse(String string, FormatterManager manager) {
		return IFormatter.ternary(IFormatter.format(string, manager), manager);
	}

}
