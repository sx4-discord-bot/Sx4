package com.sx4.bot.utility;

import com.sx4.bot.database.model.Operators;
import org.bson.conversions.Bson;

public class EconomyUtility {
	
	public static Bson getBalanceUpdate(long amount) {
		return Operators.set("economy.balance", Operators.cond(Operators.or(Operators.extinct("$economy.balance"), Operators.lt("$economy.balance", amount)), "$economy.balance", Operators.subtract("$economy.balance", amount)));
	}
	
	// When the user gives a percentage as an argument eg: 50%
	public static Bson getBalanceUpdate(double decimal) {
		return Operators.set("economy.balance", Operators.subtract("$economy.balance", Operators.toLong(Operators.floor(Operators.multiply(decimal, "$economy.balance")))));
	}
	
}
