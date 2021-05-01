package com.sx4.bot.utility;

import com.sx4.bot.database.model.Operators;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.Collections;
import java.util.List;

public class EconomyUtility {

	public static Bson getResetsUpdate(long amount, long cooldown) {
		return Operators.set("resets", Operators.let(new Document("resets", Operators.ifNull("$resets", Collections.EMPTY_LIST)).append("seconds", Operators.nowEpochSecond()), Operators.concatArrays(Operators.filter("$$resets", Operators.gt("$$this.time", "$$seconds")), List.of(new Document("time", Operators.add("$$seconds", cooldown)).append("amount", amount)))));
	}
	
	public static Bson getBalanceUpdate(long amount) {
		return Operators.set("economy.balance", Operators.cond(Operators.or(Operators.extinct("$economy.balance"), Operators.lt("$economy.balance", amount)), "$economy.balance", Operators.subtract("$economy.balance", amount)));
	}
	
	// When the user gives a percentage as an argument eg: 50%
	public static Bson getBalanceUpdate(double decimal) {
		return Operators.set("economy.balance", Operators.subtract("$economy.balance", Operators.toLong(Operators.floor(Operators.multiply(decimal, "$economy.balance")))));
	}
	
}
