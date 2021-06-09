package com.sx4.bot.commands.economy;

import com.mongodb.client.model.UpdateOptions;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.utility.ExceptionUtility;
import org.bson.conversions.Bson;

import java.util.List;

public class ClaimCommand extends Sx4Command {

	public ClaimCommand() {
		super("claim", 426);

		super.setDescription("Claim $1,000,000,000 on the economy if you're using Sx4 Canary");
		super.setExamples("claim");
		super.setCanaryCommand(true);
		super.setCategoryAll(ModuleCategory.ECONOMY);
	}

	public void onCommand(Sx4CommandEvent event) {
		List<Bson> update = List.of(
			Operators.set("economy.balance", Operators.cond(Operators.ifNull("$economy.claimed", false), "$economy.balance", Operators.add(Operators.ifNull("$economy.balance", 0L), 1_000_000_000L))),
			Operators.set("economy.claimed", true)
		);

		event.getMongo().updateUserById(event.getAuthor().getIdLong(), update, new UpdateOptions().upsert(true)).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getModifiedCount() == 0 && result.getUpsertedId() == null) {
				event.replyFailure("You have already claimed your free money").queue();
				return;
			}

			event.replySuccess("You just claimed your free **$1,000,000,000**").queue();
		});
	}

}
