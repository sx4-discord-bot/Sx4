package com.sx4.bot.database.model;

import java.util.Arrays;
import java.util.List;

import org.bson.BsonUndefined;
import org.bson.Document;
import org.bson.conversions.Bson;

public class Operators {
	
	public static final String REMOVE = "$$REMOVE";

	public static Bson not(Object cond) {
		return new Document("$not", cond);
	}
	
	public static Bson cond(Object ifCond, Object thenCond, Object elseCond) {
		return new Document("$cond", List.of(ifCond, thenCond, elseCond));
	}
	
	public static Bson and(Object... expressions) {
		return new Document("$and", Arrays.asList(expressions));
	}
	
	public static Bson or(Object... expressions) {
		return new Document("$or", Arrays.asList(expressions));
	}
	
	public static Bson eq(Object expression, Object expression2) {
		return new Document("$eq", List.of(expression, expression2));
	}
	
	public static Bson ne(Object expression, Object expression2) {
		return new Document("$ne", List.of(expression, expression2));
	}
	
	public static Bson extinct(String key) {
		return Operators.eq(key, new BsonUndefined());
	}
	
	public static Bson exists(String key) {
		return Operators.ne(key, new BsonUndefined());
	}
	
	public static Bson filter(Object listExpression, Object expression) {
		return new Document("$filter", new Document("input", listExpression).append("cond", expression));
	}
	
	public static Bson map(Object listExpression, Object expression) {
		return new Document("$map", new Document("input", listExpression).append("in", expression));
	}
	
	public static Bson set(String key, Object expression) {
		return new Document("$set", new Document(key, expression));
	}
	
	public static Bson concatArrays(Object... expressions) {
		return new Document("$concatArrays", Arrays.asList(expressions));
	}
	
	public static Bson size(Object expression) {
		return new Document("$size", expression);
	}
	
	public static Bson isEmpty(Object expression) {
		return Operators.eq(Operators.size(expression), 0);
	}
	
	public static Bson divide(Object expression, Object expression2) {
		return new Document("$divide", List.of(expression, expression2));
	}
	
	public static Bson multiply(Object... expressions) {
		return new Document("$multiply", Arrays.asList(expressions));
	}
	
	public static Bson add(Object... expressions) {
		return new Document("$add", Arrays.asList(expressions));
	}
	
	public static Bson subtract(Object expression, Object expression2) {
		return new Document("$subtract", List.of(expression, expression2));
	}
	
	public static Bson floor(Object expression) {
		return new Document("$floor", expression);
	}
	
	public static Bson ceil(Object expression) {
		return new Document("$ceil", expression);
	}
	
	public static Bson mod(Object expression, Object expression2) {
		return new Document("$mod", List.of(expression, expression2));
	}
	
	public static Bson sum(Object listExpression) {
		return new Document("$sum", listExpression);
	}
	
	public static Bson sum(List<Object> objects) {
		return new Document("$sum", objects);
	}
	
	public static Bson range(Object start, Object end) {
		return new Document("$range", List.of(start, end));
	}
	
	public static Bson sigma(Object start, Object end, Object expression) {
		return Operators.sum(Operators.map(Operators.range(start, Operators.add(end, 1)), expression));
	}
	
	public static Bson pow(Object expression, Object powerExpression) {
		return new Document("$pow", List.of(expression, powerExpression));
	}
	
	public static Bson log(Object expression, Object baseExpression) {
		return new Document("$log", List.of(expression, baseExpression));
	}
	
	public static Bson toLong(Object expression) {
		return new Document("$toLong", expression);
	}
	
	private static Bson bitwiseOrUnchecked(Object x, Object y) {
		return Operators.sigma(0, Operators.floor(Operators.log(x, 2)), Operators.multiply(Operators.pow(2, "$$this"), Operators.mod(Operators.add(Operators.mod(Operators.floor(Operators.divide(x, Operators.pow(2, "$$this"))), 2), Operators.mod(Operators.floor(Operators.divide(y, Operators.pow(2, "$$this"))), 2), Operators.multiply(Operators.mod(Operators.floor(Operators.divide(x, Operators.pow(2, "$$this"))), 2), Operators.mod(Operators.floor(Operators.divide(y, Operators.pow(2, "$$this"))), 2))), 2)));
	}
	
	public static Bson bitwiseOr(Object x, Object y) {
		return Operators.cond(Operators.lt(x, y), Operators.bitwiseOrUnchecked(y, x), Operators.bitwiseOrUnchecked(x, y));
	}
	
	public static Bson lt(Object expression, Object expression2) {
		return new Document("$lt", List.of(expression, expression2));
	}
	
	public static Bson in(Object expression, Object arrayExpression) {
		return new Document("$in", List.of(expression, arrayExpression));
	}
	
	public static Bson abs(Object expression) {
		return new Document("$abs", expression);
	}
	
	public static Bson arrayElemAt(Object expression, int index) {
		return new Document("$arrayElemAt", List.of(expression, index));
	}
	
	public static Bson first(Object expression) {
		return Operators.arrayElemAt(expression, 0);
	}
	
	public static Bson last(Object expression) {
		return Operators.arrayElemAt(expression, -1);
	}
	
	public static Bson ifNull(Object expression, Object defaultExpression) {
		return new Document("$ifNull", List.of(expression, defaultExpression));
	}
	
}
