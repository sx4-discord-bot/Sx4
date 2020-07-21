package com.sx4.bot.formatter;

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

                if (endIndex != -1) {
                    if (string.charAt(endIndex - 1) == '\\') {
                        string = string.substring(0, endIndex - 1) + string.substring(endIndex);
                    } else {
                        String formatter = string.substring(index + 1, endIndex);

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
                                    formatter = formatter.substring(0, elseIndex - 1) + formatter.substring(elseIndex);
                                    continue;
                                }

                                elseFormatter = this.format(formatter.substring(elseIndex + 1), this.map);
                                ifFormatter = this.format(formatter.substring(ifIndex + 1, elseIndex), this.map);
                                formatter = formatter.substring(0, ifIndex);

                                break Condition;
                            }

                            ifFormatter = this.format(formatter.substring(ifIndex + 1), this.map);
                            formatter = formatter.substring(0, ifIndex);

                            break;
                        }

                        Object replace = this.map.get(formatter);
                        if (replace instanceof Boolean) {
                            string = string.substring(0, index) + (ifFormatter == null ? replace : (((Boolean) replace) ? ifFormatter : elseFormatter)) + string.substring(endIndex + 1);
                        } else {
                            string = string.substring(0, index) + replace + string.substring(endIndex + 1);
                        }
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
