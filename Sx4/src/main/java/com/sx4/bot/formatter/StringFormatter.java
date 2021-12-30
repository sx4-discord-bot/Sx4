package com.sx4.bot.formatter;

public class StringFormatter extends Formatter<String> {

    public StringFormatter(String string, FormatterManager manager) {
        super(string, manager);
    }

    public StringFormatter(String string) {
        super(string);
    }

    public String parse() {
        return Formatter.parse(this.object, this.manager);
    }

}
