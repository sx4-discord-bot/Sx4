package com.sx4.bot.commands.developer;

import com.jockie.bot.core.argument.Argument;
import com.mongodb.client.model.*;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.utility.ExceptionUtility;
import net.dv8tion.jda.api.entities.Member;
import org.bson.Document;

import java.util.List;

public class TransferTaxCommand extends Sx4Command {

	public TransferTaxCommand() {
		super("transfer tax", 439);

		super.setDescription("Transfers tax");
		super.setExamples("transfer tax @Shea#6653", "transfer tax Shea");
		super.setDeveloper(true);
		super.setCategoryAll(ModuleCategory.DEVELOPER);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="user", endless=true) Member member) {
		event.getMongo().withTransaction(session -> {
			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.include("economy.balance"));

			Document data = event.getMongo().getUsers().findOneAndUpdate(session, Filters.eq("_id", event.getSelfUser().getIdLong()), Updates.set("economy.balance", 0L), options);
			if (data == null) {
				event.replyFailure("There is no tax to give").queue();
				session.abortTransaction();
				return null;
			}

			long tax = data.getEmbedded(List.of("economy", "balance"), 0L);
			if (tax == 0) {
				event.replyFailure("There is no tax to give").queue();
				session.abortTransaction();
				return null;
			}

			event.getMongo().getUsers().updateOne(session, Filters.eq("_id", member.getIdLong()), Updates.inc("economy.balance", tax), new UpdateOptions().upsert(true));

			return tax;
		}).whenComplete((tax, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception) || tax == null) {
				return;
			}

			event.replyFormat("%s has received **$%,d** in tax %s", member.getUser().getAsTag(), tax, event.getConfig().getSuccessEmote()).queue();
		});
	}

}
