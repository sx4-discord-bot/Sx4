// Generated from Formatter.g4 by ANTLR 4.7.2
package com.sx4.bot.antlr;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link FormatterParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface FormatterVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link FormatterParser#parse}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParse(FormatterParser.ParseContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Upper}
	 * labeled alternative in {@link FormatterParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUpper(FormatterParser.UpperContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Cond}
	 * labeled alternative in {@link FormatterParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCond(FormatterParser.CondContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Lower}
	 * labeled alternative in {@link FormatterParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLower(FormatterParser.LowerContext ctx);
	/**
	 * Visit a parse tree produced by the {@code NotEqual}
	 * labeled alternative in {@link FormatterParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNotEqual(FormatterParser.NotEqualContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Ternary}
	 * labeled alternative in {@link FormatterParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTernary(FormatterParser.TernaryContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Title}
	 * labeled alternative in {@link FormatterParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTitle(FormatterParser.TitleContext ctx);
	/**
	 * Visit a parse tree produced by the {@code String}
	 * labeled alternative in {@link FormatterParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitString(FormatterParser.StringContext ctx);
	/**
	 * Visit a parse tree produced by the {@code LessThanEqual}
	 * labeled alternative in {@link FormatterParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLessThanEqual(FormatterParser.LessThanEqualContext ctx);
	/**
	 * Visit a parse tree produced by the {@code LessThan}
	 * labeled alternative in {@link FormatterParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLessThan(FormatterParser.LessThanContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Format}
	 * labeled alternative in {@link FormatterParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFormat(FormatterParser.FormatContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Equal}
	 * labeled alternative in {@link FormatterParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitEqual(FormatterParser.EqualContext ctx);
	/**
	 * Visit a parse tree produced by the {@code TernaryPy}
	 * labeled alternative in {@link FormatterParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTernaryPy(FormatterParser.TernaryPyContext ctx);
	/**
	 * Visit a parse tree produced by the {@code ID}
	 * labeled alternative in {@link FormatterParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitID(FormatterParser.IDContext ctx);
	/**
	 * Visit a parse tree produced by the {@code MoreThan}
	 * labeled alternative in {@link FormatterParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMoreThan(FormatterParser.MoreThanContext ctx);
	/**
	 * Visit a parse tree produced by the {@code MoreThanEqual}
	 * labeled alternative in {@link FormatterParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMoreThanEqual(FormatterParser.MoreThanEqualContext ctx);
	/**
	 * Visit a parse tree produced by {@link FormatterParser#condition}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCondition(FormatterParser.ConditionContext ctx);
}