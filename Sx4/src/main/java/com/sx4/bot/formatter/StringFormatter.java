package com.sx4.bot.formatter;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class StringFormatter implements Formatter<String> {

    private final Map<String, Function<Variable, Object>> map;
    private final String string;

    public StringFormatter(String string) {
        this(string, new HashMap<>());
    }

    private StringFormatter(String string, Map<String, Function<Variable, Object>> map) {
        this.string = string;
        this.map = map;
    }

    public StringFormatter appendFunction(String key, Function<Variable, Object> function) {
        this.map.put(key, function);

        return this;
    }

    public String parse() {
        return this.parse(this.string, this.map);
    }

    public static StringFormatter of(String string, Map<String, Function<Variable, Object>> map) {
        return new StringFormatter(string, map);
    }

}
