package com.sx4.bot.database.model;

import org.bson.BsonUndefined;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Operators {
	
	public static final String REMOVE = "$$REMOVE";
	public static final String NOW = "$$NOW";

	public static Bson nowEpochMilli() {
		return Operators.toLong(Operators.NOW);
	}

	public static Bson nowEpochSecond() {
		return Operators.toLong(Operators.divide(Operators.nowEpochMilli(), 1000));
	}

	public static Bson not(Object cond) {
		return new Document("$not", cond);
	}
	
	public static Bson cond(Object ifCond, Object thenCond, Object elseCond) {
		return new Document("$cond", List.of(ifCond, thenCond, elseCond));
	}
	
	public static Bson and(List<?> expressions) {
		return new Document("$and", expressions);
	}
	
	public static Bson and(Object... expressions) {
		return Operators.and(Arrays.asList(expressions));
	}
	
	public static Bson or(List<?> expressions) {
		return new Document("$or", expressions);
	}
	
	public static Bson or(Object... expressions) {
		return Operators.or(Arrays.asList(expressions));
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
	
	public static Bson map(Object listExpression, Object expression, String name) {
		return new Document("$map", new Document("input", listExpression).append("in", expression).append("as", name));
	}

	public static Bson map(Object listExpression, Object expression) {
		return new Document("$map", new Document("input", listExpression).append("in", expression));
	}

	public static Bson get(Object documentExpression, String key) {
		return Operators.first(Operators.map(List.of(documentExpression), "$$this." + key));
	}
	
	public static Bson set(String key, Object expression) {
		return new Document("$set", new Document(key, expression));
	}

	public static Bson slice(Object array, Object start, Object end) {
		return new Document("$slice", List.of(array, start, end));
	}
	
	public static Bson concatArrays(List<?> expressions) {
		return new Document("$concatArrays", expressions);
	}
	
	public static Bson concatArrays(Object... expressions) {
		return Operators.concatArrays(Arrays.asList(expressions));
	}

	public static Bson mergeObjects(Object... expressions) {
		return new Document("$mergeObjects", Arrays.asList(expressions));
	}

	public static Bson removeObject(Object expression, Object key) {
		return Operators.arrayToObject(Operators.filter(Operators.objectToArray(expression), Operators.ne("$$this.k", key)));
	}

	public static Bson objectToArray(Object expression) {
		return new Document("$objectToArray", expression);
	}

	public static Bson arrayToObject(Object expression) {
		return new Document("$arrayToObject", expression);
	}

	public static Bson indexOfArray(Object array, Object object) {
		return new Document("$indexOfArray", List.of(array, object));
	}
	
	public static Bson size(Object expression) {
		return new Document("$size", List.of(expression));
	}

	public static Bson notEmpty(Object expression) {
		return Operators.ne(Operators.size(expression), 0);
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

	public static Bson min(Object expression, Object expression2) {
		return new Document("$min", List.of(expression, expression2));
	}
	
	public static Bson sum(Object listExpression) {
		return new Document("$sum", listExpression);
	}
	
	public static Bson range(Object start, Object end) {
		return new Document("$range", List.of(start, end));
	}
	
	public static Bson sigma(Object start, Object end, Object expression) {
		return Operators.reduce(Operators.range(start, Operators.add(end, 1)), 0, Operators.add("$$value", expression));
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
	
	private static Bson bitwiseXorUnchecked(Object x, Object y) {
		return Operators.abs(Operators.sigma(0, Operators.floor(Operators.log(x, 2)), Operators.multiply(Operators.pow(2, "$$this"), Operators.mod(Operators.add(Operators.floor(Operators.divide(x, Operators.pow(2, "$$this"))), Operators.floor(Operators.divide(y, Operators.pow(2, "$$this")))), 2))));
	}
	
	public static Bson bitwiseXor(Object x, Object y) {
		return Operators.cond(Operators.lt(x, y), Operators.bitwiseXorUnchecked(y, x), Operators.bitwiseXorUnchecked(x, y));
	}
	
	public static Bson bitwiseNot(Object x) {
		return Operators.subtract(Operators.multiply(x, -1), 1);
	}

	// Doesn't work with long min value
	private static Bson bitwiseAndUnchecked(Object x, Object y) {
		return Operators.abs(Operators.sigma(0, Operators.floor(Operators.log(x, 2)), Operators.multiply(Operators.pow(2, "$$this"), Operators.mod(Operators.floor(Operators.divide(x, Operators.pow(2, "$$this"))), 2), Operators.mod(Operators.floor(Operators.divide(y, Operators.pow(2, "$$this"))), 2))));
	}
	
	public static Bson bitwiseAnd(Object x, Object y) {
		return Operators.cond(Operators.and(Operators.lt(x, 0), Operators.lt(y, 0)), Operators.let(new Document("value", Operators.cond(Operators.lt(x, y), Operators.bitwiseAndUnchecked(Operators.abs(x), y), Operators.bitwiseAndUnchecked(Operators.abs(y), x))), Operators.subtract("$$value", Operators.multiply("$$value", 2))), Operators.cond(Operators.lt(x, y), Operators.bitwiseAndUnchecked(y, x), Operators.bitwiseAndUnchecked(x, y)));
	}
	
	private static Bson bitwiseOrUnchecked(Object x, Object y) {
		return Operators.abs(Operators.sigma(0, Operators.floor(Operators.log(x, 2)), Operators.multiply(Operators.pow(2, "$$this"), Operators.mod(Operators.add(Operators.mod(Operators.floor(Operators.divide(x, Operators.pow(2, "$$this"))), 2), Operators.mod(Operators.floor(Operators.divide(y, Operators.pow(2, "$$this"))), 2), Operators.multiply(Operators.mod(Operators.floor(Operators.divide(x, Operators.pow(2, "$$this"))), 2), Operators.mod(Operators.floor(Operators.divide(y, Operators.pow(2, "$$this"))), 2))), 2))));
	}
	
	public static Bson bitwiseOr(Object x, Object y) {
		return Operators.cond(Operators.or(Operators.lt(x, 0), Operators.lt(y, 0)), Operators.let(new Document("value", Operators.cond(Operators.lt(x, y), Operators.bitwiseOrUnchecked(Operators.abs(y), x), Operators.bitwiseOrUnchecked(Operators.abs(x), y))), Operators.subtract("$$value", Operators.multiply("$$value", 2))), Operators.cond(Operators.lt(x, y), Operators.bitwiseOrUnchecked(y, x), Operators.bitwiseOrUnchecked(x, y)));
	}

	public static Bson shiftLeft(Object x, Object y) {
		return Operators.multiply(x, Operators.pow(2, y));
	}

	public static Bson shiftRight(Object x, Object y) {
		return Operators.floor(Operators.divide(x, Operators.pow(2, y)));
	}

	public static Bson wordsOr(Object words, Object words2) {
		Bson wordsInCommon = Operators.min(Operators.size(words), Operators.size(words2));
		Bson largerWords = Operators.cond(Operators.gt(Operators.size(words), Operators.size(words2)), words, words2);
		Bson difference = Operators.subtract(Operators.size(largerWords), wordsInCommon);

		return Operators.concatArrays(Operators.reduce(Operators.range(0, wordsInCommon), Collections.EMPTY_LIST, Operators.let(new Document("index", "$$this"), Operators.concatArrays("$$value", List.of(Operators.toLong(Operators.bitwiseOr(Operators.arrayElemAt(words, "$$index"), Operators.arrayElemAt(words2, "$$index"))))))), Operators.cond(Operators.eq(difference, 0), Collections.EMPTY_LIST, Operators.slice(largerWords, wordsInCommon, difference)));
	}

	public static Bson wordsAnd(Object words, Object words2) {
		Bson wordsInCommon = Operators.min(Operators.size(words), Operators.size(words2));
		Bson largerWords = Operators.cond(Operators.gt(Operators.size(words), Operators.size(words2)), words, words2);
		Bson difference = Operators.subtract(Operators.size(largerWords), wordsInCommon);

		return Operators.reduce(Operators.range(0, wordsInCommon), Collections.EMPTY_LIST, Operators.let(new Document("index", "$$this"), Operators.concatArrays("$$value", List.of(Operators.toLong(Operators.bitwiseAnd(Operators.arrayElemAt(words, "$$index"), Operators.arrayElemAt(words2, "$$index")))))));
	}

	public static Bson wordsIsEmpty(Object words) {
		return Operators.isEmpty(Operators.filter(words, Operators.ne("$$this", 0L)));
	}

	public static Bson let(Object variables, Object expression) {
		return new Document("$let", new Document("vars", variables).append("in", expression));
	}

	public static Bson gt(Object expression, Object expression2) {
		return new Document("$gt", List.of(expression, expression2));
	}

	public static Bson gte(Object expression, Object expression2) {
		return new Document("$gte", List.of(expression, expression2));
	}
	
	public static Bson lt(Object expression, Object expression2) {
		return new Document("$lt", List.of(expression, expression2));
	}

	public static Bson lte(Object expression, Object expression2) {
		return new Document("$lte", List.of(expression, expression2));
	}
	
	public static Bson in(Object expression, Object arrayExpression) {
		return new Document("$in", List.of(expression, arrayExpression));
	}
	
	public static Bson abs(Object expression) {
		return new Document("$abs", expression);
	}

	public static Bson reduce(Object listExpression, Object initialValue, Object expression) {
		return new Document("$reduce", new Document("input", listExpression).append("initialValue", initialValue).append("in", expression));
	}
	
	public static Bson arrayElemAt(Object expression, Object index) {
		return new Document("$arrayElemAt", List.of(expression, index));
	}
	
	public static Bson first(Object expression) {
		return Operators.arrayElemAt(expression, 0);
	}
	
	public static Bson last(Object expression) {
		return Operators.arrayElemAt(expression, -1);
	}

	public static Bson type(Object expression) {
		return new Document("$type", expression);
	}
	
	public static Bson ifNull(Object expression, Object defaultExpression) {
		return new Document("$ifNull", List.of(expression, defaultExpression));
	}

	// Operators.eq(expression, null) would be ideal but List.of does not take null values
	// Check for missing even though it's an undocumented type and I don't think it should be possible to get missing but have been able to get the missing type before
	public static Bson isNull(Object expression) {
		return Operators.or(Operators.eq(Operators.type(expression), "null"), Operators.eq(Operators.type(expression), "missing"));
	}

	public static Bson nonNull(Object expression) {
		return Operators.and(Operators.ne(Operators.type(expression), "null"), Operators.ne(Operators.type(expression), "missing"));
	}
	
}
