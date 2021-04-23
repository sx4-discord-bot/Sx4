package com.sx4.bot.formatter;

import java.util.HashMap;
import java.util.Map;

public class StringFormatter implements IFormatter<String> {

    private final Map<String, Object> arguments;
    private final FormatterManager manager;

    private final String string;

    public StringFormatter(String string) {
        this(string, new HashMap<>());
    }

    private StringFormatter(String string, Map<String, Object> arguments) {
        this.string = string;
        this.arguments = arguments;
        this.manager = FormatterManager.getDefaultManager();
    }

    public StringFormatter addArgument(String name, Object argument) {
        this.arguments.put(name, argument);

        return this;
    }

    public String getString() {
        return this.string;
    }

    public String parse() {
        return this.parse(this.string, this.arguments, this.manager);
    }

    public static StringFormatter of(String string, Map<String, Object> arguments) {
        return new StringFormatter(string, arguments);
    }

}
