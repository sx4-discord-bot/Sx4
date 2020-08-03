package com.sx4.bot.formatter;

public enum Condition {

    EQUAL("=="),
    NOT_EQUAL("!="),
    MORE_THAN_EQUAL(">="),
    MORE_THAN(">"),
    LESS_THAN_EQUAL("<="),
    LESS_THAN("<");

    private final String operator;

    private Condition(String operator) {
        this.operator = operator;
    }

    public String getOperator() {
        return this.operator;
    }

}
