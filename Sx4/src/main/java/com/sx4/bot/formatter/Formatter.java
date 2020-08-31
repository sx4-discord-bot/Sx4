package com.sx4.bot.formatter;

import com.sx4.bot.utility.ColourUtility;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;

import java.util.HashMap;
import java.util.Map;

public class Formatter {

    private final Map<String, Object> map;
    private final String string;

    public Formatter(String string) {
        this(string, new HashMap<>());
    }

    private Formatter(String string, Map<String, Object> map) {
        this.string = string;
        this.map = map;
    }

    public Formatter append(String key, Object replace) {
        this.map.put(key, replace);

        return this;
    }

    public Formatter user(User user) {
        return this.append("user.mention", user.getAsMention())
            .append("user.name", user.getName())
            .append("user.id", user.getId())
            .append("user.discriminator", user.getDiscriminator())
            .append("user.tag", user.getAsTag());
    }

    public Formatter channel(TextChannel channel) {
        return this.append("channel.mention", channel.getAsMention())
            .append("channel.name", channel.getName())
            .append("channel.id", channel.getId());
    }

    public Formatter role(Role role) {
        return this.append("role.mention", role.getAsMention())
            .append("role.name", role.getName())
            .append("role.id", role.getId())
            .append("role.colour", "#" + ColourUtility.toHexString(role.getColorRaw()));
    }

    public String parse() {
        return this.parse(this.string, this.map);
    }

    private boolean notEqual(String string, char firstChar, char secondChar) {
        int first = 0, second = 0;
        for (int i = 0; i < string.length(); i++) {
            char character = string.charAt(i), characterBefore = string.charAt(Math.max(0, i - 1));
            if (character == firstChar && characterBefore != '\\') {
                first++;
            } else if (character == secondChar && characterBefore != '\\') {
                second++;
            }
        }

        return first != second;
    }

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

                if (this.notEqual(string.substring(index + 1, endIndex), '(', ')')) {
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
                        if (this.notEqual(ifFormatter, '?', ':')) {
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
    
    private String format(String string, Map<String, Object> map) {
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
                if (this.notEqual(formatter, '{', '}')) {
                    continue;
                }

                if (!map.containsKey(formatter)) {
                    continue;
                }

                string = string.substring(0, index) + map.get(formatter) + string.substring(endIndex + 1);

                continue Open;
            }
        }

        return string;
    }

    private String parse(String string, Map<String, Object> map) {
        return this.ternary(this.format(string, map));
    }

    public static Formatter of(String string, Map<String, Object> map) {
        return new Formatter(string, map);
    }

}
