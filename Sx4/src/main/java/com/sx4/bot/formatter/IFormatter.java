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
import java.util.Map;

public interface IFormatter<Type> {

	IFormatter<Type> addArgument(String key, Object object);

	default IFormatter<Type> user(User user) {
		if (user == null) {
			return this;
		}

		return this.addArgument("user", user);
	}

	default IFormatter<Type> member(Member member) {
		if (member == null) {
			return this;
		}

		return this.addArgument("member", member);
	}

	default IFormatter<Type> guild(Guild guild) {
		if (guild == null) {
			return this;
		}

		return this.addArgument("server", guild);
	}

	default IFormatter<Type> channel(TextChannel channel) {
		if (channel == null) {
			return this;
		}

		return this.addArgument("channel", channel);
	}

	default IFormatter<Type> role(Role role) {
		if (role == null) {
			return this;
		}

		return this.addArgument("role", role);
	}

	default IFormatter<Type> emote(ReactionEmote emote) {
		if (emote == null) {
			return this;
		}

		return this.addArgument("emote", emote);
	}

	Type parse();

	public static boolean escape(String string, int index) {
		if (index > 0 && string.charAt(index - 1) == '\\') {
			string = string.substring(0, index - 1) + string.substring(index);
			return true;
		}

		return false;
	}

	public static String ternary(String string, Map<String, Object> arguments, FormatterManager manager) {
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

	private static List<Object> getFunctionArguments(FormatterFunction<?> function, String text, Object value, Map<String, Object> arguments, FormatterManager manager) {
		List<Object> functionArguments = new ArrayList<>();
		functionArguments.add(new FormatterEvent(value, arguments, manager));

		Class<?>[] parameters = function.getMethod().getParameterTypes();
		if (parameters.length > 2) {
			int lastIndex = -1, i = 0;
			do {
				int nextIndex = text.indexOf(',', lastIndex + 1);
				if (IFormatter.escape(text, nextIndex)) {
					continue;
				}

				Object argumentValue = IFormatter.toObject(text.substring(lastIndex + 1, lastIndex = nextIndex == -1 ? text.length() : nextIndex), function.isUsePrevious() ? value.getClass() : parameters[i++ + 1], manager);
				if (argumentValue == null) {
					continue;
				}

				functionArguments.add(argumentValue);
			} while (lastIndex != text.length());

			if (functionArguments.size() < parameters.length - 1) {
				return null;
			}
		} else if (parameters.length == 2) {
			Object argumentValue = IFormatter.toObject(text, function.isUsePrevious() ? value.getClass() : parameters[1], manager);
			if (argumentValue == null) {
				return null;
			}

			functionArguments.add(argumentValue);
		}

		return functionArguments;
	}

	private static Object getValue(String formatter, Map<String, Object> arguments, FormatterManager manager) {
		String argument = formatter;
		int periodIndex = formatter.lastIndexOf('.');

		Object value;
		do {
			value = arguments.get(argument);
			if (value != null || periodIndex == -1) {
				break;
			}

			argument = formatter.substring(0, periodIndex);
		} while ((periodIndex = argument.lastIndexOf('.')) != -1);

		if (value == null) {
			int staticBracketIndex = 0;
			while (IFormatter.escape(argument, staticBracketIndex = argument.indexOf('(', staticBracketIndex + 1)) && staticBracketIndex != -1);

			int staticEndBracketIndex = argument.length();
			while (IFormatter.escape(argument, staticEndBracketIndex = argument.lastIndexOf(')', staticEndBracketIndex - 1)) && staticEndBracketIndex != -1);

			if (staticBracketIndex == -1 || staticEndBracketIndex == -1) {
				value = arguments.get(argument);
			} else {
				String argumentText = argument.substring(staticBracketIndex + 1, staticEndBracketIndex);
				String functionName = argument.substring(0, staticBracketIndex);

				FormatterFunction<?> function = manager.getStaticFunction(functionName);
				if (function == null) {
					return null;
				}

				List<Object> functionArguments = IFormatter.getFunctionArguments(function, argumentText, null, arguments, manager);
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
		}

		if (value == null) {
			return null;
		}

		periodIndex = argument.length();

		while (periodIndex != -1 && periodIndex != formatter.length()) {
			if (value == null) {
				return null;
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
				FormatterVariable<?> variable = manager.getVariable(value.getClass(), name);
				if (variable == null) {
					return null;
				}

				value = variable.parse(value);
				if (nextPeriodIndex != -1) {
					if (value == null) {
						continue;
					}
				}
			} else {
				String argumentText = name.substring(bracketIndex + 1, endBracketIndex);
				String functionName = name.substring(0, bracketIndex);

				FormatterFunction<?> function = manager.getFunction(value.getClass(), functionName);
				if (function == null) {
					return null;
				}

				List<Object> functionArguments = IFormatter.getFunctionArguments(function, argumentText, value, arguments, manager);
				if (functionArguments == null) {
					return null;
				}

				try {
					value = function.parse(functionArguments);
				} catch (InvocationTargetException | IllegalAccessException | IllegalArgumentException exception) {
					exception.printStackTrace();
					value = null;
				}

				if (value == null) {
					continue;
				}
			}

			periodIndex = nextPeriodIndex;
		}

		return value;
	}

	public static String format(String string, Map<String, Object> arguments, FormatterManager manager) {
		int index = string.length();
		Open: while ((index = string.lastIndexOf('{', index - 1)) != -1) {
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

				Object value = IFormatter.getValue(formatter, arguments, manager);
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
		FormatterParser<?> parser = manager.getParser(type);
		if (parser == null) {
			return null;
		} else {
			return parser.parse(text);
		}
	}

	default String parse(String string, Map<String, Object> arguments, FormatterManager manager) {
		return IFormatter.ternary(IFormatter.format(string, arguments, manager), arguments, manager);
	}

}
