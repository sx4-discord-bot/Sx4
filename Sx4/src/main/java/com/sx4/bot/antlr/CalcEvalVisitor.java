package com.sx4.bot.antlr;

import org.antlr.v4.runtime.tree.ParseTree;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

public class CalcEvalVisitor extends CalcBaseVisitor<Double> {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final Map<String, Double> variables;

    public CalcEvalVisitor() {
        this.variables = new HashMap<>();
    }

    @Override
    public Double visitAbs(CalcParser.AbsContext ctx) {
        return Math.abs(this.visit(ctx.expr()));
    }

    @Override
    public Double visitMax(CalcParser.MaxContext ctx) {
        return Math.max(this.visit(ctx.expr(0)), this.visit(ctx.expr(1)));
    }

    @Override
    public Double visitMin(CalcParser.MinContext ctx) {
        return Math.min(this.visit(ctx.expr(0)), this.visit(ctx.expr(1)));
    }

    @Override
    public Double visitPow(CalcParser.PowContext ctx) {
        return Math.pow(this.visit(ctx.expr(0)), this.visit(ctx.expr(1)));
    }

    @Override
    public Double visitSqrt(CalcParser.SqrtContext ctx) {
        return Math.sqrt(this.visit(ctx.expr()));
    }

    @Override
    public Double visitMul(CalcParser.MulContext ctx) {
        return this.visit(ctx.expr(0)) * this.visit(ctx.expr(1));
    }

    @Override
    public Double visitDiv(CalcParser.DivContext ctx) {
        return this.visit(ctx.expr(0)) / this.visit(ctx.expr(1));
    }

    @Override
    public Double visitAdd(CalcParser.AddContext ctx) {
        return this.visit(ctx.expr(0)) + this.visit(ctx.expr(1));
    }

    @Override
    public Double visitSub(CalcParser.SubContext ctx) {
        return this.visit(ctx.expr(0)) - this.visit(ctx.expr(1));
    }

    @Override
    public Double visitVar(CalcParser.VarContext ctx) {
        return this.variables.getOrDefault(ctx.VAR().getText(), 0D);
    }

    @Override
    public Double visitAssign(CalcParser.AssignContext ctx) {
        double value = this.visit(ctx.expr());
        this.variables.put(ctx.VAR().getText(), value);
        return value;
    }

    @Override
    public Double visitCeil(CalcParser.CeilContext ctx) {
        return Math.ceil(this.visit(ctx.expr()));
    }

    @Override
    public Double visitFloor(CalcParser.FloorContext ctx) {
        return Math.floor(this.visit(ctx.expr()));
    }

    @Override
    public Double visitRound(CalcParser.RoundContext ctx) {
        return (double) Math.round(this.visit(ctx.expr()));
    }

    @Override
    public Double visitParens(CalcParser.ParensContext ctx) {
        return this.visit(ctx.expr());
    }

    @Override
    public Double visitDouble(CalcParser.DoubleContext ctx) {
        return Double.parseDouble(ctx.DOUBLE().getText());
    }

    @Override
    public Double visitAnd(CalcParser.AndContext ctx) {
        return (double) (this.visit(ctx.expr(0)).longValue() & this.visit(ctx.expr(1)).longValue());
    }

    @Override
    public Double visitOr(CalcParser.OrContext ctx) {
        return (double) (this.visit(ctx.expr(0)).longValue() | this.visit(ctx.expr(1)).longValue());
    }

    @Override
    public Double visitXor(CalcParser.XorContext ctx) {
        return (double) (this.visit(ctx.expr(0)).longValue() ^ this.visit(ctx.expr(1)).longValue());
    }

    @Override
    public Double visitShiftLeft(CalcParser.ShiftLeftContext ctx) {
        return (double) (this.visit(ctx.expr(0)).longValue() << this.visit(ctx.expr(1)).longValue());
    }

    @Override
    public Double visitShiftRight(CalcParser.ShiftRightContext ctx) {
        return (double) (this.visit(ctx.expr(0)).longValue() >> this.visit(ctx.expr(1)).longValue());
    }

    @Override
    public Double visitNot(CalcParser.NotContext ctx) {
        return (double) (~this.visit(ctx.expr()).longValue());
    }

    @Override
    public Double visitPi(CalcParser.PiContext ctx) {
        return Math.PI;
    }

    public CompletableFuture<Double> parse(ParseTree tree) {
        return CompletableFuture.supplyAsync(() -> super.visit(tree), this.executor);
    }

}
