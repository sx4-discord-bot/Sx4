package com.sx4.bot.antlr;

import com.sx4.bot.utility.StringUtility;

import java.util.List;
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
    public String visitTernaryPy(FormatterParser.TernaryPyContext ctx) {
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
    public String visitCond(FormatterParser.CondContext ctx) {
        List<FormatterParser.ConditionContext> conditions = ctx.condition();

        boolean result = false;
        for (int i = 0; i < conditions.size(); i++) {
            FormatterParser.ConditionContext condition = conditions.get(i);

            int type = condition.op.getType(), previousType = conditions.get(Math.max(i - 1, 0)).op.getType();
            if (type == FormatterParser.AND) {
                boolean first = Boolean.parseBoolean(this.visit(condition.expr(0)));
                FormatterParser.ExprContext second = condition.expr(1);

                boolean andResult = second == null ? first : first && Boolean.parseBoolean(this.visit(second));
                result = i == 0 ? andResult : previousType == FormatterParser.AND || second == null ? result && andResult : result || andResult;
            } else {
                boolean first = Boolean.parseBoolean(this.visit(condition.expr(0)));
                FormatterParser.ExprContext second = condition.expr(1);

                boolean orResult = second == null ? first : first || Boolean.parseBoolean(this.visit(second));
                result = i == 0 ? orResult : previousType == FormatterParser.AND && second != null ? result && orResult : result || orResult;
            }
        }

        return Boolean.toString(result);
    }

    @Override
    public String visitUpper(FormatterParser.UpperContext ctx) {
        return this.visit(ctx.parse()).toUpperCase();
    }

    @Override
    public String visitLower(FormatterParser.LowerContext ctx) {
        return this.visit(ctx.parse()).toLowerCase();
    }

    @Override
    public String visitTitle(FormatterParser.TitleContext ctx) {
        return StringUtility.title(this.visit(ctx.parse()));
    }

    @Override
    public String visitFormat(FormatterParser.FormatContext ctx) {
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
