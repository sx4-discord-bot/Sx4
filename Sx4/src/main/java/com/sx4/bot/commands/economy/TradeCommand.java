package com.sx4.bot.commands.economy;

import com.jockie.bot.core.argument.Argument;
import com.mongodb.client.model.*;
import com.mongodb.client.result.UpdateResult;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.economy.item.*;
import com.sx4.bot.managers.EconomyManager;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.waiter.Waiter;
import com.sx4.bot.waiter.exception.CancelException;
import com.sx4.bot.waiter.exception.TimeoutException;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicLong;

public class TradeCommand extends Sx4Command {

	public TradeCommand() {
		super("trade", 409);

		super.setDescription("Trade items and money with another user");
		super.setExamples("trade @Shea#6653", "trade Shea", "trade 402557516728369153");
		super.setCooldownDuration(20);
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.ECONOMY);
	}

	public Map.Entry<Long, Map<Item, Long>> getTrade(EconomyManager manager, String query) {
		String[] parts = query.split(",");

		long money = 0;
		Map<Item, Long> items = new HashMap<>();
		for (String part : parts) {
			part = part.trim();
			try {
				money += Long.parseUnsignedLong(part);
			} catch (NumberFormatException e) {
				ItemStack<?> stack = ItemStack.parse(manager, part, Item.class);
				if (stack == null) {
					continue;
				}

				long amount = stack.getAmount();
				if (amount < 1) {
					continue;
				}

				items.compute(stack.getItem(), (key, value) -> value == null ? amount : value + amount);
			}
		}

		return Map.entry(money, items);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="user", endless=true) Member member) {
		User user = member.getUser();
		if (user.isBot()) {
			event.replyFailure("You can not trade with bots").queue();
			return;
		}

		if (user.getIdLong() == event.getAuthor().getIdLong()) {
			event.replyFailure("You can not trade with yourself").queue();
			return;
		}

		String prompt = "What %s the user? Make sure you put a space between every thing you want to offer, for example: `2 gold, 200, 5 titanium, 1 coal factory` would offer $200, 5 Titanium, 2 Gold and 1 Coal Factory (Respond Below)";

		AtomicLong userMoneyAtomic = new AtomicLong(0), authorMoneyAtomic = new AtomicLong(0);
		Map<Item, Long> userItems = new HashMap<>(), authorItems = new HashMap<>();

		event.reply(String.format(prompt, "are you offering to")).submit().thenCompose(message -> {
			return new Waiter<>(event.getBot(), MessageReceivedEvent.class)
				.setPredicate(e -> !e.getMessage().getContentRaw().equalsIgnoreCase("cancel"))
				.setCancelPredicate(e -> e.getMessage().getContentRaw().equalsIgnoreCase("cancel"))
				.setUnique(event.getAuthor().getIdLong(), event.getChannel().getIdLong())
				.setTimeout(60)
				.start();
		}).thenCompose(e -> {
			Map.Entry<Long, Map<Item, Long>> trade = this.getTrade(event.getBot().getEconomyManager(), e.getMessage().getContentRaw());

			Map<Item, Long> items = trade.getValue();
			long money = trade.getKey();
			if (items.isEmpty() && money == 0) {
				throw new IllegalArgumentException("No valid or tradeable items or money were supplied");
			}

			userItems.putAll(trade.getValue());
			userMoneyAtomic.set(trade.getKey());

			StringJoiner content = new StringJoiner("\n");
			items.forEach((key, value) -> content.add(value + " " + key.getName()));

			EmbedBuilder embed = new EmbedBuilder()
				.setTitle("What you are offering to " + user.getAsTag())
				.setDescription((money == 0 ? "" : String.format("$%,d", money)) + (items.isEmpty() ? "" : "\n") + content);

			MessageBuilder message = new MessageBuilder()
				.setEmbeds(embed.build())
				.setContent(String.format(prompt, "would you like from"));

			return event.reply(message.build()).submit();
		}).thenCompose(message -> {
			return new Waiter<>(event.getBot(), MessageReceivedEvent.class)
				.setPredicate(e -> !e.getMessage().getContentRaw().equalsIgnoreCase("cancel"))
				.setCancelPredicate(e -> e.getMessage().getContentRaw().equalsIgnoreCase("cancel"))
				.setUnique(event.getAuthor().getIdLong(), event.getChannel().getIdLong())
				.setTimeout(60)
				.start();
		}).thenCompose(e -> {
			Map.Entry<Long, Map<Item, Long>> trade = this.getTrade(event.getBot().getEconomyManager(), e.getMessage().getContentRaw());

			authorItems.putAll(trade.getValue());
			long authorMoney = trade.getKey();
			if (authorItems.isEmpty() && authorMoney == 0) {
				throw new IllegalArgumentException("No valid or tradeable items or money were supplied");
			}

			authorMoneyAtomic.set(authorMoney);

			long userMoney = userMoneyAtomic.get();

			StringJoiner authorContent = new StringJoiner("\n");
			authorItems.forEach((key, value) -> authorContent.add(value + " " + key.getName()));

			StringJoiner userContent = new StringJoiner("\n");
			userItems.forEach((key, value) -> userContent.add(value + " " + key.getName()));

			EmbedBuilder embed = new EmbedBuilder()
				.setTitle("Final Trade")
				.addField(user.getAsTag() + " Gets", (userMoney == 0 ? "" : String.format("$%,d", userMoney)) + (userItems.isEmpty() ? "" : "\n") + userContent, false)
				.addField(event.getAuthor().getAsTag() + " Gets", (authorMoney == 0 ? "" : String.format("$%,d", authorMoney)) + (authorItems.isEmpty() ? "" : "\n") + authorContent, false);

			MessageBuilder message = new MessageBuilder()
				.setEmbeds(embed.build())
				.setActionRows(ActionRow.of(List.of(Button.success("yes", "Yes"), Button.danger("no", "No"))))
				.setAllowedMentions(EnumSet.of(Message.MentionType.USER))
				.setContent(user.getAsMention() + ", do you accept this trade?");

			return event.reply(message.build()).submit();
		}).thenCompose(message -> {
			return new Waiter<>(event.getBot(), ButtonClickEvent.class)
				.setPredicate(e -> {
					Button button = e.getButton();
					return button != null && button.getId().equals("yes") && e.getMessageIdLong() == message.getIdLong() && e.getUser().getIdLong() == user.getIdLong();
				})
				.setCancelPredicate(e -> {
					Button button = e.getButton();
					return button != null && button.getId().equals("no") && e.getMessageIdLong() == message.getIdLong() && e.getUser().getIdLong() == user.getIdLong();
				})
				.setTimeout(60)
				.start();
		}).whenCompleteAsync((e, exception) -> {
			Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
			if (cause instanceof CancelException) {
				event.replySuccess("Cancelled").queue();
				return;
			} else if (cause instanceof TimeoutException) {
				event.reply("Timed out :stopwatch:").queue();
				return;
			} else if (cause instanceof IllegalArgumentException) {
				event.replyFailure(cause.getMessage()).queue();
				return;
			}

			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			event.getMongo().withTransaction(session -> {
				Map<String, Long> types = new HashMap<>();
				Map<Item, Long> combined = new HashMap<>();
				long authorMoney = authorMoneyAtomic.get(), userMoney = userMoneyAtomic.get();

				long totalAuthorWorth = authorMoney;
				for (Item item : authorItems.keySet()) {
					if (item instanceof Envelope) {
						event.replyFailure("You can not trade envelopes").queue();
						session.abortTransaction();
						return;
					} else if (item instanceof Tool) {
						event.replyFailure("You can not trade tools").queue();
						session.abortTransaction();
						return;
					}

					long amount = authorItems.get(item);

					totalAuthorWorth += item.getPrice() * amount;

					types.compute(item.getName(), (key, value) -> value == null ? amount : value + amount);
					combined.compute(item, (key, value) -> value == null ? -amount : value - amount);
				}

				long totalUserWorth = userMoney;
				for (Item item : userItems.keySet()) {
					if (item instanceof Envelope) {
						event.replyFailure("You can not trade envelopes").queue();
						session.abortTransaction();
						return;
					} else if (item instanceof Tool) {
						event.replyFailure("You can not trade tools").queue();
						session.abortTransaction();
						return;
					}

					long amount = userItems.get(item);

					totalUserWorth += item.getPrice() * amount;

					types.compute(item.getName(), (key, value) -> value == null ? amount : value + amount);
					combined.compute(item, (key, value) -> value == null ? amount : value + amount);
				}

				types.put("Money", authorMoney + userMoney);

				Map.Entry<String, Long> max = types.entrySet().stream()
					.max(Map.Entry.comparingByValue())
					.get();

				if ((double) max.getValue() / (totalUserWorth + totalAuthorWorth) >= 0.7D) {
					event.replyFailure(max.getKey() + " cannot make up more than 70% of the trades value").queue();
					session.abortTransaction();
					return;
				}

				if (totalUserWorth / totalAuthorWorth > 5 || totalAuthorWorth / totalUserWorth > 5) {
					event.replyFailure("You have to trade at least 20% the worth of the other persons trade").queue();
					session.abortTransaction();
					return;
				}

				if (userMoney - authorMoney != 0) {
					List<Bson> authorUpdate = List.of(Operators.set("economy.balance", Operators.let(new Document("balance", Operators.ifNull("$economy.balance", 0L)), Operators.let(new Document("newBalance", Operators.add("$$balance", authorMoney - userMoney)), Operators.cond(Operators.lt("$$newBalance", 0L), "$$balance", "$$newBalance")))));

					UpdateResult authorResult = event.getMongo().getUsers().updateOne(session, Filters.eq("_id", event.getAuthor().getIdLong()), authorUpdate);
					if (authorResult.getModifiedCount() == 0) {
						event.replyFormat("%s does not have **$%,d** %s", event.getAuthor().getAsTag(), userMoney - authorMoney, event.getConfig().getFailureEmote()).queue();
						session.abortTransaction();
						return;
					}

					List<Bson> userUpdate = List.of(Operators.set("economy.balance", Operators.let(new Document("balance", Operators.ifNull("$economy.balance", 0L)), Operators.let(new Document("newBalance", Operators.add("$$balance", userMoney - authorMoney)), Operators.cond(Operators.lt("$$newBalance", 0L), "$$balance", "$$newBalance")))));

					UpdateResult userResult = event.getMongo().getUsers().updateOne(session, Filters.eq("_id", user.getIdLong()), userUpdate);
					if (userResult.getModifiedCount() == 0) {
						event.replyFormat("%s does not have **$%,d** %s", event.getAuthor().getAsTag(), authorMoney - userMoney, event.getConfig().getFailureEmote()).queue();
						session.abortTransaction();
						return;
					}
				}

				for (Item item : combined.keySet()) {
					long signedAmount = combined.get(item);
					if (signedAmount == 0) {
						continue;
					}

					boolean author = signedAmount < 0;
					long amount = Math.abs(signedAmount);

					List<Bson> addUpdate = List.of(
						Operators.set("item", item.toData()),
						Operators.set("amount", Operators.add(Operators.ifNull("$amount", 0L), amount))
					);

					event.getMongo().getItems().updateOne(session, Filters.and(Filters.eq("userId", author ? event.getAuthor().getIdLong() : user.getIdLong()), Filters.eq("item.id", item.getId())), addUpdate, new UpdateOptions().upsert(true));

					List<Bson> removeUpdate = List.of(Operators.set("amount", Operators.let(new Document("amount", Operators.ifNull("$amount", 0L)), Operators.cond(Operators.lt(Operators.subtract("$$amount", Operators.sum(Operators.map(Operators.filter(Operators.ifNull("$resets", Collections.EMPTY_LIST), Operators.gt("$$this.time", Operators.nowEpochSecond())), "$$this.amount"))), amount), "$$amount", Operators.subtract("$$amount", amount)))));
					FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().projection(Projections.include("amount", "resets")).returnDocument(ReturnDocument.BEFORE);

					Document data = event.getMongo().getItems().findOneAndUpdate(session, Filters.and(Filters.eq("userId", author ? user.getIdLong() : event.getAuthor().getIdLong()), Filters.eq("item.id", item.getId())), removeUpdate, options);
					long userAmount = data == null ? 0L : data.get("amount", 0L);
					if (userAmount < amount) {
						event.replyFailure((author ? user.getAsTag() : event.getAuthor().getAsTag()) + " does not have `" + amount + " " + item.getName() + "`").queue();
						session.abortTransaction();
						return;
					}

					CooldownItemStack<Item> cooldownStack = new CooldownItemStack<>(item, data);

					long cooldownAmount = cooldownStack.getCooldownAmount();
					if (userAmount - cooldownAmount < amount) {
						event.replyFormat("%s has `%,d %s` but **%,d** %s on cooldown %s", author ? user.getAsTag() : event.getAuthor().getAsTag(), userAmount, item.getName(), cooldownAmount, cooldownAmount == 1 ? "is" : "are", event.getConfig().getFailureEmote()).queue();
						session.abortTransaction();
						return;
					}
				}
			}).whenComplete((updated, databaseException) -> {
				if (ExceptionUtility.sendExceptionally(event, databaseException) || !updated) {
					return;
				}

				event.replySuccess("All money and items have been traded").queue();
			});
		});
	}

}
