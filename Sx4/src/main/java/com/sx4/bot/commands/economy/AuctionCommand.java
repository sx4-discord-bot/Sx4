package com.sx4.bot.commands.economy;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.option.Option;
import com.mongodb.client.model.*;
import com.mongodb.client.result.UpdateResult;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.annotations.command.BotPermissions;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.economy.auction.Auction;
import com.sx4.bot.entities.economy.item.*;
import com.sx4.bot.entities.utility.TimeFormatter;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.EconomyUtility;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.TimeUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Clock;
import java.util.List;

public class AuctionCommand extends Sx4Command {

	private final TimeFormatter formatter = TimeUtility.LONG_TIME_FORMATTER_BUILDER.setMaxUnits(1).build();

	public AuctionCommand() {
		super("auction", 414);

		super.setDescription("Buy and sell items on the auction from other users");
		super.setExamples("auction list", "auction buy", "auction sell");
		super.setCategoryAll(ModuleCategory.ECONOMY);
	}

	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}

	@Command(value="list", description="View items on the auction house")
	@CommandId(415)
	@Examples({"auction list", "auction list Gold", "auction list Platinum --sort=price", "auction list --sort=name --reverse"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void list(Sx4CommandEvent event, @Argument(value="item", endless=true, nullDefault=true) Item item, @Option(value="sort", description="Sort by the `name`, `amount`, `price`, `price-per-item` or `expires` (default)") String sort, @Option(value="reverse", description="Reverses the order of the items") boolean reverse) {
		sort = sort == null ? "expires" : sort.equals("name") ? "item.name" : sort;

		Bson filter = Filters.gt("expires", Clock.systemUTC().instant().getEpochSecond());
		if (item != null) {
			filter = Filters.and(filter, Filters.eq("item.id", item.getId()));
		}

		List<Bson> pipeline = List.of(
			Aggregates.project(Projections.fields(Projections.include("amount", "price", "item", "expires"), Projections.computed("price-per-item", Operators.divide("$price", "$amount")))),
			Aggregates.match(filter),
			Aggregates.sort(reverse ? Sorts.ascending(sort) : Sorts.descending(sort))
		);

		event.getMongo().aggregateAuction(pipeline).whenComplete((items, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (items.isEmpty()) {
				event.replyFailure("There are no items on the auction with that filter").queue();
				return;
			}

			PagedResult<Document> paged = new PagedResult<>(event.getBot(), items)
				.setPerPage(6)
				.setSelect()
				.setCustomFunction(page -> {
					EmbedBuilder embed = new EmbedBuilder()
						.setAuthor("Auction List", null, event.getSelfUser().getEffectiveAvatarUrl())
						.setTitle("Page " + page.getPage() + "/" + page.getMaxPage())
						.setFooter(PagedResult.DEFAULT_FOOTER_TEXT);

					page.forEach((data, index) -> {
						Auction<?> auction = new Auction<>(event.getBot().getEconomyManager(), data);
						ItemStack<?> stack = auction.getItemStack();
						Item auctionItem = stack.getItem();

						StringBuilder content = new StringBuilder(String.format("Expires In: %s\nPrice: $%,d\nPrice Per Item: $%,.2f\nAmount: %,d", this.formatter.parse(auction.getExpiresAt() - Clock.systemUTC().instant().getEpochSecond()), auction.getPrice(), auction.getPricePerItem(), stack.getAmount()));

						if (auctionItem instanceof Tool tool) {
							content.append(String.format("\nDurability: %,d\nMax Durability: %,d", tool.getDurability(), tool.getMaxDurability()));
						}

						if (auctionItem instanceof Pickaxe pickaxe) {
							content.append(String.format("\nMultiplier: %,.2f\nYield: $%,d to $%,d", pickaxe.getMultiplier(), pickaxe.getMinYield(), pickaxe.getMaxYield()));
						} else if (auctionItem instanceof Axe axe) {
							content.append(String.format("\nMultiplier: %,.2f", axe.getMultiplier()));
						} else if (auctionItem instanceof Rod rod) {
							content.append(String.format("\nYield: $%,d to $%,d", rod.getMinYield(), rod.getMaxYield()));
						}

						embed.addField(auctionItem.getName(), content.toString(), true);
					});

					return new MessageBuilder().setEmbeds(embed.build());
				});

			paged.execute(event);
		});
	}

	@Command(value="sell", description="Sell an item on to the auction house")
	@CommandId(416)
	@Examples({"auction sell 10 5 Shoe", "auction sell 55 Coal", "auction sell 150 Wooden Pickaxe"})
	public void sell(Sx4CommandEvent event, @Argument(value="price") @Limit(min=1) long price, @Argument(value="items", endless=true) ItemStack<Item> stack) {
		long amount = stack.getAmount();
		if (amount < 1) {
			event.replyFailure("You need to sell at least 1 item").queue();
			return;
		}

		Item item = stack.getItem();
		if (item instanceof Envelope) {
			event.replyFailure("You cannot sell envelopes on the auction");
		}

		long totalPrice = stack.getTotalPrice();
		double pricePercent = (double) totalPrice / price;
		if (pricePercent < 0.1D) {
			event.replyFormat("You cannot sell an item for more than 500%% its worth (**$%,.0f**) %s", totalPrice * 5D, event.getConfig().getFailureEmote()).queue();
			return;
		}

		if (pricePercent > 20D) {
			event.replyFormat("You cannot sell an item for less than 5%% its worth (**$%,.0f**) %s", totalPrice / 20D, event.getConfig().getFailureEmote()).queue();
			return;
		}

		event.getMongo().withTransaction(session -> {
			Document data;
			if (item instanceof Tool) {
				FindOneAndDeleteOptions options = new FindOneAndDeleteOptions().projection(Projections.include("item", "amount", "resets"));

				data = event.getMongo().getItems().findOneAndDelete(session, Filters.and(Filters.eq("userId", event.getAuthor().getIdLong()), Filters.eq("item.type", item.getType().getId())), options);
			} else {
				FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.include("item", "amount"));
				List<Bson> update = List.of(Operators.set("amount", Operators.let(new Document("amount", Operators.ifNull("$amount", 0L)), Operators.cond(Operators.lt("$$amount", amount), "$$amount", Operators.subtract("$$amount", amount)))));

				data = event.getMongo().getItems().findOneAndUpdate(session, Filters.and(Filters.eq("userId", event.getAuthor().getIdLong()), Filters.eq("item.id", item.getId())), update, options);
			}

			long actualAmount = data == null ? 0L : data.getLong("amount");
			if (actualAmount < amount) {
				event.replyFormat("You do not have `%,d %s` %s", amount, item.getName(), event.getConfig().getFailureEmote()).queue();
				session.abortTransaction();
				return;
			}

			CooldownItemStack<?> cooldownStack = new CooldownItemStack<>(event.getBot().getEconomyManager(), data);

			long cooldownAmount = cooldownStack.getCooldownAmount();
			if (actualAmount - cooldownAmount < amount) {
				event.replyFormat("You have `%,d %s` but **%,d** %s on cooldown %s", actualAmount, item.getName(), cooldownAmount, cooldownAmount == 1 ? "is" : "are", event.getConfig().getFailureEmote()).queue();
				session.abortTransaction();
				return;
			}

			Document auctionData = new Document("expires", Clock.systemUTC().instant().getEpochSecond() + 604800L)
				.append("ownerId", event.getAuthor().getIdLong())
				.append("amount", amount)
				.append("price", price)
				.append("item", data.get("item", Document.class));

			event.getMongo().getAuction().insertOne(session, auctionData);
		}).whenComplete((updated, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception) || !updated) {
				return;
			}

			event.replyFormat("Your `%,d %s` has been put on the auction house for **$%,d** %s", amount, item.getName(), price, event.getConfig().getSuccessEmote()).queue();
		});
	}

	@Command(value="buy", description="Buy a item from the auction house")
	@CommandId(417)
	@Examples({"auction buy", "auction buy Gold", "auction buy Platinum --sort=price", "auction buy --sort=name --reverse"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void buy(Sx4CommandEvent event, @Argument(value="item", endless=true, nullDefault=true) Item item, @Option(value="sort", description="Sort by the `name`, `amount`, `price`, `price-per-item` or `expires` (default)") String sort, @Option(value="reverse", description="Reverses the order of the items") boolean reverse) {
		sort = sort == null ? "expires" : sort.equals("name") ? "item.name" : sort;

		Bson filter = Filters.and(Filters.gt("expires", Clock.systemUTC().instant().getEpochSecond()), Filters.ne("ownerId", event.getAuthor().getIdLong()));
		if (item != null) {
			filter = Filters.and(filter, Filters.eq("item.id", item.getId()));
		}

		List<Bson> pipeline = List.of(
			Aggregates.project(Projections.fields(Projections.include("item", "amount", "price", "expires", "ownerId"), Projections.computed("price-per-item", Operators.divide("$price", "$amount")))),
			Aggregates.match(filter),
			Aggregates.sort(reverse ? Sorts.ascending(sort) : Sorts.descending(sort))
		);

		event.getMongo().aggregateAuction(pipeline).whenComplete((items, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (items.isEmpty()) {
				event.replyFailure("There are no items on the auction to buy with that filter").queue();
				return;
			}

			PagedResult<Document> paged = new PagedResult<>(event.getBot(), items)
				.setPerPage(6)
				.setIncreasedIndex(true)
				.setTimeout(30)
				.setCustomFunction(page -> {
					EmbedBuilder embed = new EmbedBuilder()
						.setAuthor("Auction List", null, event.getSelfUser().getEffectiveAvatarUrl())
						.setTitle("Page " + page.getPage() + "/" + page.getMaxPage())
						.setFooter(PagedResult.DEFAULT_FOOTER_TEXT);

					page.forEach((data, index) -> {
						Auction<?> auction = new Auction<>(event.getBot().getEconomyManager(), data);
						ItemStack<?> stack = auction.getItemStack();
						Item auctionItem = stack.getItem();

						StringBuilder content = new StringBuilder(String.format("Expires In: %s\nPrice: $%,d\nPrice Per Item: $%,.2f\nAmount: %,d", this.formatter.parse(auction.getExpiresAt() - Clock.systemUTC().instant().getEpochSecond()), auction.getPrice(), auction.getPricePerItem(), stack.getAmount()));

						if (auctionItem instanceof Tool tool) {
							content.append(String.format("\nDurability: %,d\nMax Durability: %,d", tool.getDurability(), tool.getMaxDurability()));
						}

						if (auctionItem instanceof Pickaxe pickaxe) {
							content.append(String.format("\nMultiplier: %,.2f\nYield: $%,d to $%,d", pickaxe.getMultiplier(), pickaxe.getMinYield(), pickaxe.getMaxYield()));
						} else if (auctionItem instanceof Axe axe) {
							content.append(String.format("\nMultiplier: %,.2f", axe.getMultiplier()));
						} else if (auctionItem instanceof Rod rod) {
							content.append(String.format("\nYield: $%,d to $%,d", rod.getMinYield(), rod.getMaxYield()));
						}

						embed.addField((index + 1) + ". " + auctionItem.getName(), content.toString(), true);
					});

					return new MessageBuilder().setEmbeds(embed.build());
				});

			paged.onTimeout(() -> event.reply("Timed out :stopwatch:"));

			paged.onSelect(select -> {
				Auction<?> auction = new Auction<>(event.getBot().getEconomyManager(), select.getSelected());
				if (Clock.systemUTC().instant().getEpochSecond() > auction.getExpiresAt()) {
					event.replyFailure("That auction listing has expired").queue();
					return;
				}

				ItemStack<?> stack = auction.getItemStack();

				long amount = stack.getAmount(), price = auction.getPrice();
				Item auctionItem = stack.getItem();

				event.getMongo().withTransaction(session -> {
					if (auctionItem instanceof Tool) {
						ItemType type = auctionItem.getType();

						long count = event.getMongo().getItems().countDocuments(session, Filters.and(Filters.eq("userId", event.getAuthor().getIdLong()), Filters.eq("item.type", type.getId())));
						if (count != 0) {
							event.replyFailure("You already have a " + type.getName().toLowerCase()).queue();
							session.abortTransaction();
							return;
						}
					}

					event.getMongo().getAuction().deleteOne(session, Filters.eq("_id", auction.getId()));

					UpdateResult result = event.getMongo().getUsers().updateOne(session, Filters.eq("_id", event.getAuthor().getIdLong()), List.of(EconomyUtility.decreaseBalanceUpdate(price)));
					if (result.getModifiedCount() == 0) {
						event.replyFormat("You do not have **$%,d** %s", price, event.getConfig().getFailureEmote()).queue();
						session.abortTransaction();
						return;
					}

					event.getMongo().getUsers().updateOne(session, Filters.eq("_id", auction.getOwnerId()), Updates.inc("economy.balance", price), new UpdateOptions().upsert(true));

					List<Bson> update = List.of(
						Operators.set("item", auctionItem.toData()),
						Operators.set("amount", Operators.add(Operators.ifNull("$amount", 0L), amount))
					);

					event.getMongo().getItems().updateOne(session, Filters.and(Filters.eq("userId", event.getAuthor().getIdLong()), Filters.eq("item.id", auctionItem.getId())), update, new UpdateOptions().upsert(true));
				}).whenComplete((updated, databaseException) -> {
					if (ExceptionUtility.sendExceptionally(event, databaseException) || !updated) {
						return;
					}

					event.replyFormat("You just bought `%,d %s` for **$%,d** %s", amount, auctionItem.getName(), price, event.getConfig().getSuccessEmote()).queue();

					event.getJDA().openPrivateChannelById(auction.getOwnerId())
						.flatMap(channel -> channel.sendMessageFormat("Your `%,d %s` was sold for **$%,d** :tada:", amount, auctionItem.getName(), price))
						.queue(null, ErrorResponseException.ignore(ErrorResponse.CANNOT_SEND_TO_USER));
				});
			});

			paged.execute(event);
		});
	}

	@Command(value="refund", description="Refund an item you put on the auction house which has expired")
	@CommandId(418)
	@Examples({"auction refund", "auction refund Gold", "auction refund Platinum --sort=price", "auction refund --sort=name --reverse"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void refund(Sx4CommandEvent event, @Argument(value="item", endless=true, nullDefault=true) Item item, @Option(value="sort", description="Sort by the `name`, `amount`, `price`, `price-per-item` or `expires` (default)") String sort, @Option(value="reverse", description="Reverses the order of the items") boolean reverse) {
		sort = sort == null ? "expires" : sort.equals("name") ? "item.name" : sort;

		Bson filter = Filters.and(Filters.lte("expires", Clock.systemUTC().instant().getEpochSecond()), Filters.eq("ownerId", event.getAuthor().getIdLong()));
		if (item != null) {
			filter = Filters.and(filter, Filters.eq("item.id", item.getId()));
		}

		List<Bson> pipeline = List.of(
			Aggregates.project(Projections.fields(Projections.include("item", "amount", "price", "expires", "ownerId"), Projections.computed("price-per-item", Operators.divide("$price", "$amount")))),
			Aggregates.match(filter),
			Aggregates.sort(reverse ? Sorts.ascending(sort) : Sorts.descending(sort))
		);

		event.getMongo().aggregateAuction(pipeline).whenComplete((items, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (items.isEmpty()) {
				event.replyFailure("You have no items on the auction which have expired with that filter").queue();
				return;
			}

			PagedResult<Document> paged = new PagedResult<>(event.getBot(), items)
				.setPerPage(6)
				.setTimeout(30)
				.setCustomFunction(page -> {
					EmbedBuilder embed = new EmbedBuilder()
						.setAuthor("Auction List", null, event.getSelfUser().getEffectiveAvatarUrl())
						.setTitle("Page " + page.getPage() + "/" + page.getMaxPage())
						.setFooter(PagedResult.DEFAULT_FOOTER_TEXT);

					page.forEach((data, index) -> {
						Auction<?> auction = new Auction<>(event.getBot().getEconomyManager(), data);
						ItemStack<?> stack = auction.getItemStack();
						Item auctionItem = stack.getItem();

						StringBuilder content = new StringBuilder(String.format("Price: $%,d\nPrice Per Item: $%,.2f\nAmount: %,d", auction.getPrice(), auction.getPricePerItem(), stack.getAmount()));

						if (auctionItem instanceof Tool tool) {
							content.append(String.format("\nDurability: %,d\nMax Durability: %,d", tool.getDurability(), tool.getMaxDurability()));
						}

						if (auctionItem instanceof Pickaxe pickaxe) {
							content.append(String.format("\nMultiplier: %,.2f\nYield: $%,d to $%,d", pickaxe.getMultiplier(), pickaxe.getMinYield(), pickaxe.getMaxYield()));
						} else if (auctionItem instanceof Axe axe) {
							content.append(String.format("\nMultiplier: %,.2f", axe.getMultiplier()));
						} else if (auctionItem instanceof Rod rod) {
							content.append(String.format("\nYield: $%,d to $%,d", rod.getMinYield(), rod.getMaxYield()));
						}

						embed.addField((index + 1) + ". " + auctionItem.getName(), content.toString(), true);
					});

					return new MessageBuilder().setEmbeds(embed.build());
				});

			paged.onTimeout(() -> event.reply("Timed out :stopwatch:"));

			paged.onSelect(select -> {
				Auction<?> auction = new Auction<>(event.getBot().getEconomyManager(), select.getSelected());
				ItemStack<?> stack = auction.getItemStack();

				long amount = stack.getAmount();
				Item auctionItem = stack.getItem();

				event.getMongo().withTransaction(session -> {
					if (auctionItem instanceof Tool) {
						ItemType type = auctionItem.getType();

						long count = event.getMongo().getItems().countDocuments(session, Filters.and(Filters.eq("userId", event.getAuthor().getIdLong()), Filters.eq("item.type", type.getId())));
						if (count != 0) {
							event.replyFailure("You already have a " + type.getName().toLowerCase()).queue();
							session.abortTransaction();
							return;
						}
					}

					event.getMongo().getAuction().deleteOne(session, Filters.eq("_id", auction.getId()));

					List<Bson> update = List.of(
						Operators.set("item", auctionItem.toData()),
						Operators.set("amount", Operators.add(Operators.ifNull("$amount", 0L), amount))
					);

					event.getMongo().getItems().updateOne(session, Filters.and(Filters.eq("userId", event.getAuthor().getIdLong()), Filters.eq("item.id", auctionItem.getId())), update, new UpdateOptions().upsert(true));
				}).whenComplete((updated, databaseException) -> {
					if (ExceptionUtility.sendExceptionally(event, databaseException) || !updated) {
						return;
					}

					event.replyFormat("You just refunded your `%,d %s` %s", amount, auctionItem.getName(), event.getConfig().getSuccessEmote()).queue();
				});
			});

			paged.execute(event);
		});
	}


}
