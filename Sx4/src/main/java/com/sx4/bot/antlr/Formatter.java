package com.sx4.bot.antlr;

import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;

import java.util.HashMap;
import java.util.Map;

public class Formatter {

    private final Map<String, Object> map;
    private String string;

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

    public String format() {
        return this.format(this.string, this.map);
    }
    
    private String format(String string, Map<String, Object> map) {
        int index = -1;
        while ((index = string.indexOf('{', index + 1)) != -1) {
            if (index > 0 && string.charAt(index - 1) == '\\') {
                string = string.substring(0, index - 1) + string.substring(index);
                continue;
            }

            int endIndex = index;
            while ((endIndex = string.indexOf('}', endIndex + 1)) != -1) {
                if (endIndex > 0 && string.charAt(endIndex - 1) == '\\') {
                    continue;
                }

                int leftBraces = 0, rightBraces = 0;
                for (int i = index + 1; i < endIndex; i++) {
                    char character = string.charAt(i), characterBefore = string.charAt(i - 1);
                    if (character == '{' && characterBefore != '\\') {
                        leftBraces++;
                    } else if (character == '}' && characterBefore != '\\') {
                        rightBraces++;
                    }
                }

                if (leftBraces != rightBraces) {
                    continue;
                }

                if (string.charAt(endIndex - 1) == '\\') {
                    string = string.substring(0, endIndex - 1) + string.substring(endIndex);
                } else {
                    String formatter = string.substring(index + 1, endIndex);
                    Object replace = null;

                    String ifFormatter = null, elseFormatter = "";
                    int ifIndex = -1;
                    Condition : while ((ifIndex = formatter.indexOf('?', ifIndex + 1)) != -1) {
                        if (ifIndex > 0 && formatter.charAt(ifIndex - 1) == '\\') {
                            formatter = formatter.substring(0, ifIndex - 1) + formatter.substring(ifIndex);
                            continue;
                        }

                        int elseIndex = ifIndex;
                        while ((elseIndex = formatter.indexOf(':', elseIndex + 1)) != -1) {
                            if (elseIndex > 0 && formatter.charAt(elseIndex - 1) == '\\') {
                                continue;
                            }

                            int ifBraces = 0, elseBraces = 0;
                            for (int i = ifIndex + 2; i < elseIndex + 1; i++) {
                                char character = string.charAt(i), characterBefore = string.charAt(i - 1);
                                if (character == '?' && characterBefore != '\\') {
                                    ifBraces++;
                                } else if (character == ':' && characterBefore != '\\') {
                                    elseBraces++;
                                }
                            }

                            if (ifBraces != elseBraces) {
                                continue;
                            }

                            elseFormatter = this.format(formatter.substring(elseIndex + 1), this.map);
                            ifFormatter = this.format(formatter.substring(ifIndex + 1, elseIndex), this.map);

                            break;
                        }

                        if (ifFormatter == null) {
                            ifFormatter = this.format(formatter.substring(ifIndex + 1), this.map);
                        }

                        String tempFormatter = formatter.substring(0, ifIndex);

                        Object tempReplace = this.map.get(tempFormatter);
                        if (tempReplace instanceof Boolean) {
                            replace = tempReplace;
                        }

                        break;
                    }

                    if (replace == null) {
                        replace = this.map.get(formatter);
                    }

                    if (replace instanceof Boolean && ifFormatter != null) {
                        string = string.substring(0, index) + (((Boolean) replace) ? ifFormatter : elseFormatter) + string.substring(endIndex + 1);
                    } else {
                        string = string.substring(0, index) + replace + string.substring(endIndex + 1);
                    }
                }
            }
        }

        return string;
    }

    public static Formatter of(String string, Map<String, Object> map) {
        return new Formatter(string, map);
    }

}
