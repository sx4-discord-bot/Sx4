package com.sx4.bot.formatter;

import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterFunction;
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

	public static boolean condition(String string) {
		for (Condition condition : Condition.values()) {
			String operator = condition.getOperator();
			int index = string.indexOf(operator);
			if (index == -1) {
				continue;
			}

			String first = string.substring(0, index), second = string.substring(index + operator.length());
			switch (condition) {
				case EQUAL:
					try {
						return Double.parseDouble(first) == Double.parseDouble(second);
					} catch (NumberFormatException e) {
						return first.equals(second);
					}
				case NOT_EQUAL:
					try {
						return Double.parseDouble(first) != Double.parseDouble(second);
					} catch (NumberFormatException e) {
						return !first.equals(second);
					}
				case MORE_THAN:
					try {
						return Double.parseDouble(first) > Double.parseDouble(second);
					} catch (NumberFormatException e) {
						return false;
					}
				case MORE_THAN_EQUAL:
					try {
						return Double.parseDouble(first) >= Double.parseDouble(second);
					} catch (NumberFormatException e) {
						return false;
					}
				case LESS_THAN:
					try {
						return Double.parseDouble(first) < Double.parseDouble(second);
					} catch (NumberFormatException e) {
						return false;
					}
				case LESS_THAN_EQUAL:
					try {
						return Double.parseDouble(first) <= Double.parseDouble(second);
					} catch (NumberFormatException e) {
						return false;
					}
			}
		}

		return string.equals("true");
	}

	public static String ternary(String string) {
		int index = -1;
		Brackets: while ((index = string.indexOf('(', index + 1)) != -1) {
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

						boolean condition = IFormatter.condition(string.substring(index + 1, condIndex));

						string = string.substring(0, index) + IFormatter.ternary(condition ? ifFormatter : elseFormatter) + string.substring(endIndex + 1);

						continue Brackets;
					}
				}
			}
		}

		return string;
	}

	public static String format(String string, Map<String, Object> arguments, FormatterManager manager) {
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

				String argument = formatter;
				int periodIndex = formatter.lastIndexOf('.');

				Object value;
				do {
					value = arguments.get(argument);
					if (value != null) {
						break;
					}

					argument = formatter.substring(0, periodIndex);
				} while ((periodIndex = argument.lastIndexOf('.')) != -1);

				value = value == null ? arguments.get(argument) : value;
				if (value == null) {
					continue;
				}

				periodIndex = argument.length();

				Class<?> returnClass = value.getClass();
				while (periodIndex != -1 && periodIndex != formatter.length()) {
					if (value == null) {
						continue Open;
					}

					int nextPeriodIndex = formatter.indexOf('.', periodIndex + 1);
					String name = formatter.substring(periodIndex + 1, nextPeriodIndex == -1 ? formatter.length() : nextPeriodIndex);

					while (StringUtility.isNotEqual(name, '(', ')') && nextPeriodIndex != -1) {
						nextPeriodIndex = formatter.indexOf('.', nextPeriodIndex + 1);
						name = formatter.substring(periodIndex + 1, nextPeriodIndex == -1 ? formatter.length() : nextPeriodIndex);
					}

					int bracketIndex = 0;
					while (IFormatter.escape(name, bracketIndex = name.indexOf('(', bracketIndex + 1)) && bracketIndex != -1) ;

					int endBracketIndex = name.length();
					while (IFormatter.escape(name, endBracketIndex = name.lastIndexOf(')', endBracketIndex - 1)) && endBracketIndex != -1) ;

					if (bracketIndex == -1 || endBracketIndex == -1) {
						FormatterVariable<?> variable = manager.getVariable(returnClass, name);
						if (variable == null) {
							continue Open;
						}

						value = variable.parse(value);
						if (nextPeriodIndex != -1) {
							if (value == null) {
								continue;
							}

							returnClass = value.getClass();
						}
					} else {
						String argumentText = name.substring(bracketIndex + 1, endBracketIndex);
						String functionName = name.substring(0, bracketIndex);

						FormatterFunction<?> function = manager.getFunction(returnClass, functionName);
						if (function == null) {
							continue Open;
						}

						List<Object> objectArguments = new ArrayList<>();
						objectArguments.add(new FormatterEvent(value, arguments, manager));

						Class<?>[] parameters = function.getMethod().getParameterTypes();
						if (parameters.length > 2) {
							List<String> functionArguments = new ArrayList<>();

							int lastIndex = -1;
							do {
								int nextIndex = argumentText.indexOf(',', lastIndex + 1);
								if (IFormatter.escape(argumentText, nextIndex)) {
									continue;
								}

								functionArguments.add(IFormatter.format(argumentText.substring(lastIndex + 1, lastIndex = nextIndex == -1 ? argumentText.length() : nextIndex), arguments, manager));
							} while (lastIndex != argumentText.length());

							if (functionArguments.size() < parameters.length - 1) {
								continue Open;
							}

							for (int i = 0; i < parameters.length; i++) {
								Class<?> parameter = parameters[i];
								if (parameter != FormatterEvent.class) {
									objectArguments.add(IFormatter.toObject(functionArguments.get(i - 1), parameter));
								}
							}
						} else if (parameters.length == 2) {
							objectArguments.add(IFormatter.toObject(IFormatter.format(argumentText, arguments, manager), parameters[1]));
						}

						try {
							value = function.parse(objectArguments);
						} catch (InvocationTargetException | IllegalAccessException | IllegalArgumentException exception) {
							exception.printStackTrace();
							continue Open;
						}

						if (value == null) {
							continue;
						}

						returnClass = value.getClass();
					}

					periodIndex = nextPeriodIndex;
				}

				if (value instanceof String) {
					value = IFormatter.format((String) value, arguments, manager);
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

	private static Object toObject(String text, Class<?> type) {
		if (type == Boolean.class) {
			return text.equals("true");
		} else if (type == Integer.class) {
			try {
				return Integer.parseInt(text.trim());
			} catch (NumberFormatException e) {
				return 0;
			}
		} else if (type == Double.class) {
			try {
				return Double.parseDouble(text.trim());
			} catch (NumberFormatException e) {
				return 0D;
			}
		} else if (type == String.class) {
			return text;
		} else if (type == Long.class) {
			try {
				return Long.parseLong(text.trim());
			} catch (NumberFormatException e) {
				return 0L;
			}
		} else if (type == Number.class) {
			return text.contains(".") ? IFormatter.toObject(text, Double.class) : IFormatter.toObject(text, Long.class);
		}

		return null;
	}

	default String parse(String string, Map<String, Object> arguments, FormatterManager manager) {
		return IFormatter.ternary(IFormatter.format(string, arguments, manager));
	}

}
