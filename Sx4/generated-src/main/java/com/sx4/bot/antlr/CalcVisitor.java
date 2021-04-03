// Generated from Calc.g4 by ANTLR 4.7.2
package com.sx4.bot.antlr;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link CalcParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface CalcVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link CalcParser#parse}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParse(CalcParser.ParseContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Return}
	 * labeled alternative in {@link CalcParser#stat}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitReturn(CalcParser.ReturnContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Assign}
	 * labeled alternative in {@link CalcParser#stat}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAssign(CalcParser.AssignContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Blank}
	 * labeled alternative in {@link CalcParser#stat}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBlank(CalcParser.BlankContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Add}
	 * labeled alternative in {@link CalcParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAdd(CalcParser.AddContext ctx);
	/**
	 * Visit a parse tree produced by the {@code ShiftRight}
	 * labeled alternative in {@link CalcParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitShiftRight(CalcParser.ShiftRightContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Floor}
	 * labeled alternative in {@link CalcParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFloor(CalcParser.FloorContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Sub}
	 * labeled alternative in {@link CalcParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSub(CalcParser.SubContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Or}
	 * labeled alternative in {@link CalcParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitOr(CalcParser.OrContext ctx);
	/**
	 * Visit a parse tree produced by the {@code ShiftLeft}
	 * labeled alternative in {@link CalcParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitShiftLeft(CalcParser.ShiftLeftContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Max}
	 * labeled alternative in {@link CalcParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMax(CalcParser.MaxContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Mul}
	 * labeled alternative in {@link CalcParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMul(CalcParser.MulContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Var}
	 * labeled alternative in {@link CalcParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitVar(CalcParser.VarContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Parens}
	 * labeled alternative in {@link CalcParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParens(CalcParser.ParensContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Sqrt}
	 * labeled alternative in {@link CalcParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSqrt(CalcParser.SqrtContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Sigma}
	 * labeled alternative in {@link CalcParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSigma(CalcParser.SigmaContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Double}
	 * labeled alternative in {@link CalcParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDouble(CalcParser.DoubleContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Div}
	 * labeled alternative in {@link CalcParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDiv(CalcParser.DivContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Not}
	 * labeled alternative in {@link CalcParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNot(CalcParser.NotContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Abs}
	 * labeled alternative in {@link CalcParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAbs(CalcParser.AbsContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Min}
	 * labeled alternative in {@link CalcParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMin(CalcParser.MinContext ctx);
	/**
	 * Visit a parse tree produced by the {@code And}
	 * labeled alternative in {@link CalcParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAnd(CalcParser.AndContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Pi}
	 * labeled alternative in {@link CalcParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPi(CalcParser.PiContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Round}
	 * labeled alternative in {@link CalcParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRound(CalcParser.RoundContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Pow}
	 * labeled alternative in {@link CalcParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPow(CalcParser.PowContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Ceil}
	 * labeled alternative in {@link CalcParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCeil(CalcParser.CeilContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Xor}
	 * labeled alternative in {@link CalcParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitXor(CalcParser.XorContext ctx);
}