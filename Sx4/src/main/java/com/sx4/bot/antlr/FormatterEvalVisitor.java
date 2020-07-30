package com.sx4.bot.antlr;

import java.util.Map;

public class FormatterEvalVisitor extends FormatterBaseVisitor<String> {

    private final Map<String, Object> map;

    public FormatterEvalVisitor(Map<String, Object> map) {
        this.map = map;
    }

    @Override
    public String visitTernary(FormatterParser.TernaryContext ctx) {
        FormatterParser.ParseContext parse = ctx.parse(this.visit(ctx.expr()).equals("true") ? 0 : 1);
        return parse == null ? "" : this.visit(parse);
    }

    @Override
    public String visitNotEqual(FormatterParser.NotEqualContext ctx) {
        String first = this.visit(ctx.expr(0)), second = this.visit(ctx.expr(1));
        try {
            return Boolean.toString(Integer.parseInt(first) != Integer.parseInt(second));
        } catch (NumberFormatException e) {
            return Boolean.toString(!first.equals(second));
        }
    }

    @Override
    public String visitEqual(FormatterParser.EqualContext ctx) {
        String first = this.visit(ctx.expr(0)), second = this.visit(ctx.expr(1));
        try {
            return Boolean.toString(Integer.parseInt(first) == Integer.parseInt(second));
        } catch (NumberFormatException e) {
            return Boolean.toString(first.equals(second));
        }
    }

    @Override
    public String visitMoreThan(FormatterParser.MoreThanContext ctx) {
        try {
            return Boolean.toString(Integer.parseInt(this.visit(ctx.expr(0))) > Integer.parseInt(this.visit(ctx.expr(1))));
        } catch (NumberFormatException e) {
            return "false";
        }
    }

    @Override
    public String visitMoreThanEqual(FormatterParser.MoreThanEqualContext ctx) {
        try {
            return Boolean.toString(Integer.parseInt(this.visit(ctx.expr(0))) >= Integer.parseInt(this.visit(ctx.expr(1))));
        } catch (NumberFormatException e) {
            return "false";
        }
    }

    @Override
    public String visitLessThan(FormatterParser.LessThanContext ctx) {
        try {
            return Boolean.toString(Integer.parseInt(this.visit(ctx.expr(0))) < Integer.parseInt(this.visit(ctx.expr(1))));
        } catch (NumberFormatException e) {
            return "false";
        }
    }

    @Override
    public String visitLessThanEqual(FormatterParser.LessThanEqualContext ctx) {
        try {
            return Boolean.toString(Integer.parseInt(this.visit(ctx.expr(0))) <= Integer.parseInt(this.visit(ctx.expr(1))));
        } catch (NumberFormatException e) {
            return "false";
        }
    }

    @Override
    public String visitFormat(FormatterParser.FormatContext ctx) {
        System.out.println(this.visit(ctx.expr()));
        return String.valueOf(this.map.get(this.visit(ctx.expr())));
    }

    @Override
    public String visitString(FormatterParser.StringContext ctx) {
        return ctx.STRING().getText();
    }

    @Override
    public String visitParse(FormatterParser.ParseContext ctx) {
        StringBuilder result = new StringBuilder();
        ctx.expr().forEach(expr -> result.append(this.visit(expr)));
        return result.toString();
    }

}
