package com.sx4.bot.utility;

import com.sx4.bot.database.model.Operators;
import org.bson.conversions.Bson;

public class OperatorsUtility {

	public static Bson setIfPremium(String field, Object expression) {
		return Operators.set(field, Operators.cond(Operators.gte(Operators.ifNull("$premium.endAt", 0L), Operators.nowEpochSecond()), expression, "$" + field));
	}

}
