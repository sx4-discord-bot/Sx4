package com.sx4.bot.database.mongo.model;

import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.BsonField;
import org.bson.conversions.Bson;

public class AggregateOperators {

	public static Bson mergeFields(String... fields) {
		BsonField[] accumulators = new BsonField[fields.length];
		for (int i = 0; i < fields.length; i++) {
			String field = fields[i];
			accumulators[i] = Accumulators.max(field, "$" + field);
		}

		return Aggregates.group(null, accumulators);
	}

}
