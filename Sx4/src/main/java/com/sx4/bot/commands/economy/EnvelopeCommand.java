package com.sx4.bot.commands.economy;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.client.model.*;
import com.mongodb.client.result.UpdateResult;
import com.sx4.bot.annotations.argument.AlternativeOptions;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.argument.Alternative;
import com.sx4.bot.entities.argument.AmountArgument;
import com.sx4.bot.entities.economy.item.Envelope;
import com.sx4.bot.entities.economy.item.ItemStack;
import com.sx4.bot.entities.economy.item.ItemType;
import com.sx4.bot.utility.EconomyUtility;
import com.sx4.bot.utility.ExceptionUtility;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;

public class EnvelopeCommand extends Sx4Command {

	public EnvelopeCommand() {
		super("envelope", 365);

		super.setDescription("Create or redeem envelopes");
		super.setExamples("envelope create", "envelope redeem");
		super.setCategoryAll(ModuleCategory.ECONOMY);
	}

	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}

	@Command(value="create", description="Creates the optimal amount of envelopes from a set amount of money")
	@CommandId(366)
	@Examples({"envelope create 5000", "envelope create all", "envelope create 50%"})
	public void create(Sx4CommandEvent event, @Argument(value="amount") AmountArgument amount) {
		event.getMongo().withTransaction(session -> {
			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.include("economy.balance"));
			Document data = event.getMongo().getUsers().findOneAndUpdate(session, Filters.eq("_id", event.getAuthor().getIdLong()), List.of(EconomyUtility.decreaseBalanceUpdate(amount)), options);
			if (data == null) {
				event.replyFailure("You do not have any money").queue();
				session.abortTransaction();
				return null;
			}

			long balance = data.getEmbedded(List.of("economy", "balance"), 0L);
			if (balance == 0L) {
				event.replyFailure("You do not have any money").queue();
				session.abortTransaction();
				return null;
			}

			long effectiveAmount = amount.getEffectiveAmount(balance);
			if (balance < effectiveAmount) {
				event.replyFormat("You do not have **$%,d** %s", effectiveAmount, event.getConfig().getFailureEmote()).queue();
				session.abortTransaction();
				return null;
			}

			for (ItemStack<Envelope> stack : Envelope.getOptimalEnvelopes(event.getBot().getEconomyManager(), effectiveAmount)) {
				Envelope envelope = stack.getItem();

				List<Bson> update = List.of(
					Operators.set("item", envelope.toData()),
					Operators.set("amount", Operators.add(Operators.ifNull("$amount", 0L), stack.getAmount()))
				);

				Bson filter = Filters.and(
					Filters.eq("userId", event.getAuthor().getIdLong()),
					Filters.eq("item.id", envelope.getId())
				);

				event.getMongo().getItems().updateOne(session, filter, update, new UpdateOptions().upsert(true));
			}

			return effectiveAmount;
		}).whenComplete((effectiveAmount, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (effectiveAmount != null) {
				event.replyFormat("You have been given **$%,d** worth of envelopes %s", effectiveAmount, event.getConfig().getSuccessEmote()).queue();
			}
		});
	}

	@Command(value="redeem", description="Redeems envelopes to the amount of money they give")
	@CommandId(367)
	@Examples({"envelope redeem 5 Coal Envelope", "envelope redeem 2 Shoe Envelope", "envelope redeem all"})
	public void redeem(Sx4CommandEvent event, @Argument(value="envelopes", endless=true) @AlternativeOptions("all") Alternative<ItemStack<Envelope>> option) {
		event.getMongo().withTransaction(session -> {
			long amount;
			if (option.isAlternative()) {
				Bson filter = Filters.and(
					Filters.eq("userId", event.getAuthor().getIdLong()),
					Filters.eq("item.type", ItemType.ENVELOPE.getId())
				);

				List<Document> envelopes = event.getMongo().getItems().find(session, filter).projection(Projections.include("item.price", "amount")).into(new ArrayList<>());

				amount = envelopes.stream().mapToLong(data -> data.getEmbedded(List.of("item", "price"), Long.class) * data.getLong("amount")).sum();

				if (amount == 0) {
					event.replyFailure("You do not have any envelopes").queue();
					session.abortTransaction();
					return amount;
				} else {
					event.getMongo().getItems().deleteMany(session, filter);
				}
			} else {
				ItemStack<Envelope> stack = option.getValue();

				Bson filter = Filters.and(
					Filters.eq("userId", event.getAuthor().getIdLong()),
					Filters.eq("item.id", option.getValue().getItem().getId())
				);

				List<Bson> update = List.of(Operators.set("amount", Operators.let(new Document("amount", Operators.ifNull("$amount", 0L)), Operators.cond(Operators.lte(stack.getAmount(), "$$amount"), Operators.subtract("$$amount", stack.getAmount()), "$$amount"))));

				UpdateResult result = event.getMongo().getItems().updateOne(session, filter, update);
				if (result.getModifiedCount() == 0) {
					event.replyFailure("You do not have `" + stack.getAmount() + " " + stack.getItem().getName() + "`").queue();
					session.abortTransaction();
					return 0L;
				}

				amount = stack.getTotalPrice();
			}

			event.getMongo().getUsers().updateOne(session, Filters.eq("_id", event.getAuthor().getIdLong()), Updates.inc("economy.balance", amount));

			return amount;
		}).whenComplete((amount, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (amount != 0L) {
				event.replyFormat("You redeemed those envelopes for **$%,d** %s", amount, event.getConfig().getSuccessEmote()).queue();
			}
		});
	}

}
