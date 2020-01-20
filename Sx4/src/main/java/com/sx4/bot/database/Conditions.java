package com.sx4.bot.database;

import java.util.List;

import org.bson.BsonUndefined;
import org.bson.Document;
import org.bson.conversions.Bson;

public class Conditions {

	public static Bson not(Object cond) {
		return new Document("$not", cond);
	}
	
	public static Bson cond(Object ifCond, Object thenCond, Object elseCond) {
		return new Document("$cond", List.of(ifCond, thenCond, elseCond));
	}
	
	public static Bson eq(String key, Object value) {
		return new Document("$eq", List.of(key, value));
	}
	
	public static Bson ne(String key, Object value) {
		return new Document("$ne", List.of(key, value));
	}
	
	public static Bson exists(String key) {
		return Conditions.ne(key, new BsonUndefined());
	}
	
}