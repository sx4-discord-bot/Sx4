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
	
	public static Bson filter(String key, Object expression) {
		return new Document("$filter", new Document("input", key).append("cond", expression));
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
	
}
