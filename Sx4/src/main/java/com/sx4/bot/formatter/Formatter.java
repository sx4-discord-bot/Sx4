package com.sx4.bot.formatter;

import com.sx4.bot.formatter.parser.FormatterTimeParser;
import com.sx4.bot.utility.ColourUtility;
import com.sx4.bot.utility.NumberUtility;
import com.sx4.bot.utility.StringUtility;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.MessageReaction.ReactionEmote;

import java.util.Map;
import java.util.function.Function;

public interface Formatter<Type> {

	class Variable {

		private final String name;
		private final String tag;

		private Variable(String name) {
			this(name, null);
		}

		private Variable(String name, String tag) {
			this.name = name;
			this.tag = tag;
		}

		public String getName() {
			return this.name;
		}

		public boolean hasTag() {
			return this.tag != null;
		}

		public String getTag() {
			return this.tag;
		}

	}

	default Formatter<Type> append(String key, Object replace) {
		return this.appendFunction(key, (variable) -> replace);
	}

	Formatter<Type> appendFunction(String key, Function<Variable, Object> function);

	default Formatter<Type> user(User user) {
		if (user == null) {
			return this;
		}

		return this.append("user.mention", user.getAsMention())
			.append("user.name", user.getName())
			.append("user.id", user.getId())
			.append("user.discriminator", user.getDiscriminator())
			.append("user.tag", user.getAsTag())
			.append("user.avatar", user.getEffectiveAvatarUrl())
			.appendFunction("user.created", new FormatterTimeParser(user.getTimeCreated()));
	}

	default Formatter<Type> member(Member member) {
		if (member == null) {
			return this;
		}

		return this.user(member.getUser())
			.append("user.joined", member.getTimeJoined())
			.append("user.colour.raw", member.getColorRaw())
			.append("user.colour", "#" + ColourUtility.toHexString(member.getColorRaw()));
	}

	default Formatter<Type> guild(Guild guild) {
		if (guild == null) {
			return this;
		}

		return this.append("server.id", guild.getId())
			.append("server.name", guild.getName())
			.append("server.avatar", guild.getIconUrl())
			.append("server.members", guild.getMemberCount())
			.append("server.members.suffix", NumberUtility.getSuffixed(guild.getMemberCount()))
			.appendFunction("server.created", new FormatterTimeParser(guild.getTimeCreated()));
	}

	default Formatter<Type> channel(TextChannel channel) {
		if (channel == null) {
			return this;
		}

		return this.append("channel.mention", channel.getAsMention())
			.append("channel.name", channel.getName())
			.append("channel.id", channel.getId())
			.appendFunction("channel.created", new FormatterTimeParser(channel.getTimeCreated()));
	}

	default Formatter<Type> role(Role role) {
		if (role == null) {
			return this;
		}

		return this.append("role.mention", role.getAsMention())
			.append("role.name", role.getName())
			.append("role.id", role.getId())
			.append("role.colour", "#" + ColourUtility.toHexString(role.getColorRaw()))
			.append("role.colour.raw", role.getColorRaw())
			.appendFunction("role.created", new FormatterTimeParser(role.getTimeCreated()));
	}

	default Formatter<Type> emote(ReactionEmote reactionEmote) {
		if (reactionEmote == null) {
			return this;
		}

		boolean emoji = reactionEmote.isEmoji();
		Emote emote = emoji ? null : reactionEmote.getEmote();

		return this.append("emote.id", emoji ? "0" : reactionEmote.getId())
			.append("emote.mention", emoji ? reactionEmote.getEmoji() : emote.getAsMention())
			.append("emote.name", emoji ? reactionEmote.getEmoji() : emote.getName())
			.append("emote.emoji", emoji)
			.appendFunction("emote.created", emoji ? null : new FormatterTimeParser(emote.getTimeCreated()));
	}

	Type parse();

	private boolean escape(String string, int index) {
		if (index > 0 && string.charAt(index - 1) == '\\') {
			string = string.substring(0, index - 1) + string.substring(index);
			return true;
		}

		return false;
	}

	private String condition(String string) {
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
						return Boolean.toString(Integer.parseInt(first) == Integer.parseInt(second));
					} catch (NumberFormatException e) {
						return Boolean.toString(first.equals(second));
					}
				case NOT_EQUAL:
					try {
						return Boolean.toString(Integer.parseInt(first) != Integer.parseInt(second));
					} catch (NumberFormatException e) {
						return Boolean.toString(!first.equals(second));
					}
				case MORE_THAN:
					try {
						return Boolean.toString(Integer.parseInt(first) > Integer.parseInt(second));
					} catch (NumberFormatException e) {
						return "false";
					}
				case MORE_THAN_EQUAL:
					try {
						return Boolean.toString(Integer.parseInt(first) >= Integer.parseInt(second));
					} catch (NumberFormatException e) {
						return "false";
					}
				case LESS_THAN:
					try {
						return Boolean.toString(Integer.parseInt(first) < Integer.parseInt(second));
					} catch (NumberFormatException e) {
						return "false";
					}
				case LESS_THAN_EQUAL:
					try {
						return Boolean.toString(Integer.parseInt(first) <= Integer.parseInt(second));
					} catch (NumberFormatException e) {
						return "false";
					}
			}
		}

		return string;
	}

	private String ternary(String string) {
		int index = -1;
		Brackets: while ((index = string.indexOf('(', index + 1)) != -1) {
			if (this.escape(string, index)) {
				continue;
			}

			int endIndex = index;
			while ((endIndex = string.indexOf(')', endIndex + 1)) != -1) {
				if (this.escape(string, endIndex)) {
					continue;
				}

				if (StringUtility.isEqual(string.substring(index + 1, endIndex), '(', ')')) {
					continue;
				}

				int condIndex = index;
				while ((condIndex = string.indexOf('?', condIndex + 1)) != -1) {
					if (this.escape(string, condIndex)) {
						continue;
					}

					int endCondIndex = condIndex;
					while ((endCondIndex = string.indexOf(':', endCondIndex + 1)) != -1) {
						if (this.escape(string, endCondIndex)) {
							continue;
						}

						String ifFormatter = string.substring(condIndex + 1, endCondIndex);
						if (StringUtility.isEqual(ifFormatter, '?', ':')) {
							continue;
						}

						String elseFormatter = string.substring(endCondIndex + 1, endIndex);

						String conditionString = this.condition(string.substring(index + 1, condIndex));
						boolean condition = conditionString.equals("true");

						string = string.substring(0, index) + this.ternary(condition ? ifFormatter : elseFormatter) + string.substring(endIndex + 1);

						continue Brackets;
					}
				}
			}
		}

		return string;
	}

	private String format(String string, Map<String, Function<Variable, Object>> map) {
		int index = -1;
		Open: while ((index = string.indexOf('{', index + 1)) != -1) {
			if (this.escape(string, index)) {
				continue;
			}

			int endIndex = index;
			while ((endIndex = string.indexOf('}', endIndex + 1)) != -1) {
				if (this.escape(string, endIndex)) {
					continue;
				}

				String formatter = string.substring(index + 1, endIndex);
				if (StringUtility.isEqual(formatter, '{', '}')) {
					continue;
				}

				int colonIndex = formatter.indexOf(':');

				Variable variable;
				if (colonIndex == -1 || formatter.charAt(colonIndex - 1) == '\\') {
					variable = new Variable(formatter);
				} else {
					variable = new Variable(formatter.substring(0, colonIndex), this.format(formatter.substring(colonIndex + 1), map));
				}

				if (!map.containsKey(variable.getName())) {
					continue;
				}

				Object formatted = map.get(variable.getName()).apply(variable);
				if (formatted instanceof String) {
					formatted = this.format((String) formatted, map);
				}

				string = string.substring(0, index) + formatted.toString() + string.substring(endIndex + 1);

				continue Open;
			}
		}

		return string;
	}

	default String parse(String string, Map<String, Function<Variable, Object>> map) {
		return this.ternary(this.format(string, map));
	}

}
