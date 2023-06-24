package com.sx4.bot.database.mongo.model;

import org.bson.BsonUndefined;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Operators {
	
	public static final String REMOVE = "$$REMOVE";
	public static final String CURRENT = "$$CURRENT";
	public static final String NOW = "$$NOW";
	public static final String ROOT = "$$ROOT";

	public static Bson nowEpochMilli() {
		return Operators.toLong(Operators.NOW);
	}

	public static Bson nowEpochSecond() {
		return Operators.toLong(Operators.divide(Operators.nowEpochMilli(), 1000));
	}

	public static Bson literal(Object expression) {
		return new Document("$literal", expression);
	}

	public static Bson expr(Object expression) {
		return new Document("$expr", expression);
	}

	public static Bson function(String code, List<Object> args) {
		return new Document("$function", new Document("body", code).append("args", args).append("lang", "js"));
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

	public static Bson allElementsTrue(Object expression) {
		return new Document("$allElementsTrue", List.of(expression));
	}

	public static Bson anyElementTrue(Object expression) {
		return new Document("$anyElementTrue", List.of(expression));
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

	public static Bson replaceWith(Object expression) {
		return new Document("$replaceWith", expression);
	}
	
	public static Bson set(String key, Object expression) {
		return new Document("$set", new Document(key, expression));
	}

	public static Bson setOnInsert(String key, Object expression) {
		return Operators.set(key, Operators.cond(Operators.exists("$" + key), "$" + key, expression));
	}

	public static Bson unset(String key) {
		return new Document("$unset", key);
	}

	public static Bson slice(Object array, Object start, Object end) {
		return new Document("$slice", List.of(array, start, end));
	}

	public static Bson concat(List<?> expressions) {
		return new Document("$concat", expressions);
	}

	public static Bson concat(Object... expressions) {
		return Operators.concat(Arrays.asList(expressions));
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

	// Mongo Version 5
	public static Bson getField(String field) {
		return new Document("$getField", field);
	}

	// Mongo Version 5
	public static Bson getField(String field, Object expression) {
		return new Document("$getField", new Document("field", field).append("input", expression));
	}

	// Mongo Version 5
	public static Bson unsetField(String field) {
		return Operators.unsetField(field, Operators.ROOT);
	}

	// Mongo Version 5
	public static Bson unsetField(String field, Object expression) {
		return new Document("$unsetField", new Document("field", field).append("input", expression));
	}

	// Mongo Version 5
	public static Bson setField(String field, Object value) {
		return new Document("$setField", new Document("field", field).append("input", Operators.ROOT).append("value", value));
	}

	// Mongo Version 5
	public static Bson setField(String field, Object expression, Object value) {
		return new Document("$setField", new Document("field", field).append("input", expression).append("value", value));
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

	public static Bson length(Object expression) {
		return new Document("$strLenCP", expression);
	}

	public static Bson split(Object expression, String delimiter) {
		return new Document("$split", List.of(expression, delimiter));
	}

	public static Bson toCharArray(Object expression) {
		return Operators.split(expression, "");
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

	public static Bson max(Object... expressions) {
		return new Document("$max", Arrays.asList(expressions));
	}

	public static Bson min(Object... expressions) {
		return new Document("$min", Arrays.asList(expressions));
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

	public static Bson round(Object expression) {
		return new Document("$round", expression);
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
	
	public static Bson range(Object start, Object end, Object increment) {
		return new Document("$range", List.of(start, end, increment));
	}

	public static Bson range(Object start, Object end) {
		return Operators.range(start, end, 1);
	}
	
	public static Bson sigma(Object start, Object end, Object expression) {
		return Operators.reduce(Operators.range(start, Operators.add(end, 1)), 0, Operators.add("$$value", expression));
	}
	
	public static Bson pow(Object expression, Object powerExpression) {
		return Operators.cond(Operators.eq(powerExpression, 63), Long.MIN_VALUE, new Document("$pow", List.of(expression, powerExpression)));
	}
	
	public static Bson log(Object expression, Object baseExpression) {
		return new Document("$log", List.of(expression, baseExpression));
	}

	public static Bson objectIdToEpochSecond(Object expression) {
		return Operators.toLong(Operators.divide(Operators.toLong(Operators.toDate(expression)), 1000));
	}

	public static Bson toDate(Object expression) {
		return new Document("$toDate", expression);
	}

	public static Bson toInt(Object expression) {
		return new Document("$toInt",  expression);
	}

	public static Bson toLong(Object expression) {
		return new Document("$toLong", expression);
	}

	public static Bson toString(Object expression) {
		return new Document("$toString", expression);
	}

	private static Bson toLongValue(Object low, Object high) {
		return Operators.toLong(Operators.let(new Document("low", low).append("high", high), Operators.add(Operators.cond(Operators.eq("$$high", 2147483648L), Long.MIN_VALUE, Operators.toLong(Operators.multiply("$$high", 0x100000000L))), Operators.cond(Operators.eq("$$low", Integer.MIN_VALUE), 2147483648L, "$$low"))));
	}
	
	public static Bson bitwiseXor(Object x, Object y) {
		return Operators.cond(Operators.eq(x, y), 0L, Operators.let(new Document("array", Operators.function("function(x, y){return [x.bottom ^ y.bottom, x.top ^ y.top]}", List.of(x, y))), Operators.toLongValue(Operators.arrayElemAt("$$array", 0), Operators.arrayElemAt("$$array", 1))));
	}
	
	public static Bson bitwiseNot(Object expression) {
		return Operators.cond(Operators.eq(expression, Long.MIN_VALUE), Long.MAX_VALUE, Operators.subtract(Operators.multiply(expression, -1), 1));
	}

	public static Bson bitwiseAnd(Object x, Object y) {
		return Operators.cond(Operators.eq(x, y), x, Operators.let(new Document("array", Operators.function("function(x, y){return [x.bottom & y.bottom, x.top & y.top]}", List.of(x, y))), Operators.toLongValue(Operators.arrayElemAt("$$array", 0), Operators.arrayElemAt("$$array", 1))));
	}

	public static Bson bitwiseAndNot(Object x, Object y) {
		return Operators.let(new Document("result", Operators.bitwiseAnd(x, y)), Operators.cond(Operators.eq("$$result", 0), x, Operators.subtract(x, "$$result")));
	}
	
	public static Bson bitwiseOr(Object x, Object y) {
		return Operators.cond(Operators.eq(x, y), x, Operators.let(new Document("array", Operators.function("function(x, y){return [x.bottom | y.bottom, x.top | y.top]}", List.of(x, y))), Operators.toLongValue(Operators.arrayElemAt("$$array", 0), Operators.arrayElemAt("$$array", 1))));
	}

	public static Bson shiftLeft(Object x, Object y) {
		return Operators.multiply(x, Operators.pow(2, y));
	}

	public static Bson shiftRight(Object x, Object y) {
		return Operators.floor(Operators.divide(x, Operators.pow(2, y)));
	}

	public static Bson bitSetOr(Object longArray, Object longArray2) {
		Bson wordsInCommon = Operators.min(Operators.size(longArray), Operators.size(longArray2));
		Bson largerWords = Operators.cond(Operators.gt(Operators.size(longArray), Operators.size(longArray2)), longArray, longArray2);
		Bson difference = Operators.subtract(Operators.size(largerWords), wordsInCommon);

		return Operators.concatArrays(Operators.reduce(Operators.range(0, wordsInCommon), Collections.EMPTY_LIST, Operators.let(new Document("index", "$$this"), Operators.concatArrays("$$value", List.of(Operators.toLong(Operators.bitwiseOr(Operators.arrayElemAt(longArray, "$$index"), Operators.arrayElemAt(longArray2, "$$index"))))))), Operators.cond(Operators.eq(difference, 0), Collections.EMPTY_LIST, Operators.slice(largerWords, wordsInCommon, difference)));
	}

	public static Bson bitSetAnd(Object longArray, Object longArray2) {
		Bson wordsInCommon = Operators.min(Operators.size(longArray), Operators.size(longArray2));

		return Operators.reduce(Operators.range(0, wordsInCommon), Collections.EMPTY_LIST, Operators.let(new Document("index", "$$this"), Operators.concatArrays("$$value", List.of(Operators.toLong(Operators.bitwiseAnd(Operators.arrayElemAt(longArray, "$$index"), Operators.arrayElemAt(longArray2, "$$index")))))));
	}

	public static Bson bitSetAndNot(Object longArray, Object clearLongArray) {
		Bson wordsInCommon = Operators.min(Operators.size(longArray), Operators.size(clearLongArray));
		Bson difference = Operators.subtract(Operators.size(longArray), wordsInCommon);

		return Operators.concatArrays(Operators.reduce(Operators.range(0, wordsInCommon), Collections.EMPTY_LIST, Operators.let(new Document("index", "$$this"), Operators.concatArrays("$$value", List.of(Operators.toLong(Operators.bitwiseAndNot(Operators.arrayElemAt(longArray, "$$index"), Operators.arrayElemAt(clearLongArray, "$$index"))))))), Operators.cond(Operators.lte(difference, 0), Collections.EMPTY_LIST, Operators.slice(longArray, wordsInCommon, difference)));
	}

	public static Bson bitSetIsEmpty(Object longArray) {
		return Operators.isEmpty(Operators.filter(longArray, Operators.ne("$$this", 0L)));
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

	public static Bson reduce(Object list, Object initialValue, Object expression) {
		return new Document("$reduce", new Document("input", list).append("initialValue", initialValue).append("in", expression));
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
