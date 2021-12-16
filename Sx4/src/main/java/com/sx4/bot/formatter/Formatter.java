package com.sx4.bot.formatter;

public class Formatter implements IFormatter<String> {

    private final FormatterManager manager;

    private final String string;

    public Formatter(String string, FormatterManager manager) {
        this.string = string;
        this.manager = manager;
    }

    public Formatter(String string) {
        this(string, FormatterManager.getDefaultManager());
    }

    public Formatter addVariable(Class<?> type, String name, Object argument) {
        this.manager.addVariable(name, type, $ -> argument);

        return this;
    }

    public Formatter addVariable(String name, Object argument) {
        this.manager.addVariable(name, Void.class, $ -> argument);

        return this;
    }

    public String parse() {
        return this.parse(this.string, this.manager);
    }

}
