package com.sx4.bot.formatter;

import java.util.HashMap;
import java.util.Map;

public class StringFormatter implements FormatterImpl<String> {

    private final Map<String, Object> map;
    private final String string;

    public StringFormatter(String string) {
        this(string, new HashMap<>());
    }

    private StringFormatter(String string, Map<String, Object> map) {
        this.string = string;
        this.map = map;
    }

    public StringFormatter append(String key, Object replace) {
        this.map.put(key, replace);

        return this;
    }

    public String parse() {
        return this.parse(this.string, this.map);
    }

    public static StringFormatter of(String string, Map<String, Object> map) {
        return new StringFormatter(string, map);
    }

}
