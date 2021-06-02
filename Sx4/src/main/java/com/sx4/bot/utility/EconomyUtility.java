package com.sx4.bot.utility;

import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.argument.AmountArgument;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.Collections;
import java.util.List;

public class EconomyUtility {

	public static Bson getResetsUpdate(long amount, long cooldown) {
		return Operators.set("resets", Operators.let(new Document("resets", Operators.ifNull("$resets", Collections.EMPTY_LIST)).append("seconds", Operators.nowEpochSecond()), Operators.concatArrays(Operators.filter("$$resets", Operators.gt("$$this.time", "$$seconds")), List.of(new Document("time", Operators.add("$$seconds", cooldown)).append("amount", amount)))));
	}
	
	public static Bson decreaseBalanceUpdate(long amount) {
		return Operators.set("economy.balance", Operators.let(new Document("balance", Operators.ifNull("$economy.balance", 0)), Operators.cond(Operators.lt("$$balance", amount), "$$balance", Operators.subtract("$$balance", amount))));
	}

	public static Bson decreaseBalanceUpdate(double decimal) {
		return Operators.set("economy.balance", Operators.let(new Document("balance", Operators.ifNull("$economy.balance", 0)), Operators.subtract("$$balance", Operators.toLong(Operators.ceil(Operators.multiply(Operators.min(decimal, 1), "$$balance"))))));
	}

	public static Bson decreaseBalanceUpdate(AmountArgument amount) {
		if (amount.hasDecimal()) {
			return EconomyUtility.decreaseBalanceUpdate(amount.getDecimal());
		} else {
			return EconomyUtility.decreaseBalanceUpdate(amount.getAmount());
		}
	}
	
}
