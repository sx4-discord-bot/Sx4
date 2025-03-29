package com.sx4.bot.commands.economy;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.option.Option;
import com.mongodb.client.model.*;
import com.sx4.bot.annotations.command.BotPermissions;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.economy.item.Item;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.paged.MessagePagedResult;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.NumberUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import okhttp3.Request;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class LeaderboardCommand extends Sx4Command {

	public LeaderboardCommand() {
		super("leaderboard", 368);

		super.setDescription("View the leaderboards for multiple different aspects in the economy");
		super.setAliases("lb");
		super.setExamples("leaderboard balance", "leaderboard items", "leaderboard winnings");
		super.setCategoryAll(ModuleCategory.ECONOMY);
	}

	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}

	@Command(value="balance", aliases={"bank", "money"}, description="View the leaderboard for the balance of users")
	@CommandId(369)
	@Examples({"leaderboard balance", "leaderboard balance --server"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void balance(Sx4CommandEvent event, @Option(value="server", aliases={"guild"}, description="View the leaderboard with a server filter") boolean guild) {
		List<Bson> pipeline = List.of(
			Aggregates.project(Projections.computed("balance", "$economy.balance")),
			Aggregates.match(Filters.and(Filters.ne("_id", event.getJDA().getSelfUser().getIdLong()), Filters.exists("balance"), Filters.ne("balance", 0))),
			Aggregates.sort(Sorts.descending("balance"))
		);

		event.getMongo().aggregateUsers(pipeline).whenCompleteAsync((documents, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			List<Map.Entry<User, Long>> users = new ArrayList<>();
			AtomicInteger userIndex = new AtomicInteger(-1);

			int i = 0;
			for (Document data : documents) {
				User user = event.getShardManager().getUserById(data.getLong("_id"));
				if (user == null) {
					continue;
				}

				if (!event.getGuild().isMember(user) && guild) {
					continue;
				}

				i++;

				users.add(Map.entry(user, data.getLong("balance")));

				if (user.getIdLong() == event.getAuthor().getIdLong()) {
					userIndex.set(i);
				}
			}

			if (users.isEmpty()) {
				event.replyFailure("There are no users which fit into this leaderboard").queue();
				return;
			}

			MessagePagedResult<Map.Entry<User, Long>> paged = new MessagePagedResult.Builder<>(event.getBot(), users)
				.setPerPage(10)
				.setSelect()
				.setCustomFunction(page -> {
					int rank = userIndex.get();

					EmbedBuilder embed = new EmbedBuilder()
						.setTitle("Balance Leaderboard")
						.setFooter(event.getAuthor().getName() + "'s Rank: " + (rank == -1 ? "N/A" : NumberUtility.getSuffixed(rank)) + " | Page " + page.getPage() + "/" + page.getMaxPage(), event.getAuthor().getEffectiveAvatarUrl());

					page.forEach((entry, index) -> embed.appendDescription(String.format("%d. `%s` - $%,d\n", index + 1, MarkdownSanitizer.escape(entry.getKey().getAsTag()), entry.getValue())));

					return new MessageCreateBuilder().setEmbeds(embed.build());
				}).build();

			paged.execute(event);
		});
	}

	@Command(value="networth", description="View the leaderboard for the networth of users")
	@CommandId(370)
	@Examples({"leaderboard networth", "leaderboard networth --server"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void networth(Sx4CommandEvent event, @Option(value="server", aliases={"guild"}, description="View the leaderboard with a server filter") boolean guild) {
		List<Bson> userPipeline = List.of(
			Aggregates.project(Projections.computed("total", "$economy.balance")),
			Aggregates.match(Filters.exists("total"))
		);

		List<Bson> pipeline = List.of(
			Aggregates.project(Projections.fields(Projections.computed("_id", "$userId"), Projections.computed("total", Operators.cond(Operators.exists("$item.durability"), Operators.toDecimal(Operators.multiply(Operators.divide("$item.price", "$item.maxDurability"), "$item.durability")), Operators.multiply("$item.price", "$amount"))))),
			Aggregates.unionWith("users", userPipeline),
			Aggregates.group("$_id", Accumulators.sum("total", "$total")),
			Aggregates.match(Filters.and(Filters.ne("_id", event.getJDA().getSelfUser().getIdLong()), Filters.ne("total", 0))),
			Aggregates.project(Projections.computed("total", Operators.toString("$total"))),
			Aggregates.sort(Sorts.descending("total"))
		);

		event.getMongo().aggregateItems(pipeline).whenCompleteAsync((documents, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			List<Map.Entry<User, BigInteger>> users = new ArrayList<>();
			AtomicInteger userIndex = new AtomicInteger(-1);

			int i = 0;
			for (Document data : documents) {
				User user = event.getShardManager().getUserById(data.getLong("_id"));
				if (user == null) {
					continue;
				}

				if (!event.getGuild().isMember(user) && guild) {
					continue;
				}

				i++;

				users.add(Map.entry(user, new BigDecimal(data.getString("total")).toBigInteger()));

				if (user.getIdLong() == event.getAuthor().getIdLong()) {
					userIndex.set(i);
				}
			}

			if (users.isEmpty()) {
				event.replyFailure("There are no users which fit into this leaderboard").queue();
				return;
			}

			MessagePagedResult<Map.Entry<User, BigInteger>> paged = new MessagePagedResult.Builder<>(event.getBot(), users)
				.setPerPage(10)
				.setSelect()
				.setCustomFunction(page -> {
					int rank = userIndex.get();

					EmbedBuilder embed = new EmbedBuilder()
						.setTitle("Networth Leaderboard")
						.setFooter(event.getAuthor().getName() + "'s Rank: " + (rank == -1 ? "N/A" : NumberUtility.getSuffixed(rank)) + " | Page " + page.getPage() + "/" + page.getMaxPage(), event.getAuthor().getEffectiveAvatarUrl());

					page.forEach((entry, index) -> embed.appendDescription(String.format("%d. `%s` - $%,d\n", index + 1, MarkdownSanitizer.escape(entry.getKey().getAsTag()), entry.getValue())));

					return new MessageCreateBuilder().setEmbeds(embed.build());
				}).build();

			paged.execute(event);
		});
	}

	@Command(value="winnings", description="View the leaderboard for the winnings of users")
	@CommandId(371)
	@Examples({"leaderboard winnings", "leaderboard winnings --server"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void winnings(Sx4CommandEvent event, @Option(value="server", aliases={"guild"}, description="View the leaderboard with a server filter") boolean guild) {
		List<Bson> pipeline = List.of(
			Aggregates.project(Projections.computed("winnings", "$economy.winnings")),
			Aggregates.match(Filters.and(Filters.ne("_id", event.getJDA().getSelfUser().getIdLong()), Filters.exists("winnings"), Filters.ne("winnings", 0))),
			Aggregates.sort(Sorts.descending("winnings"))
		);

		event.getMongo().aggregateUsers(pipeline).whenCompleteAsync((documents, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			List<Map.Entry<User, Long>> users = new ArrayList<>();
			AtomicInteger userIndex = new AtomicInteger(-1);

			int i = 0;
			for (Document data : documents) {
				User user = event.getShardManager().getUserById(data.getLong("_id"));
				if (user == null) {
					continue;
				}

				if (!event.getGuild().isMember(user) && guild) {
					continue;
				}

				i++;

				users.add(Map.entry(user, data.getLong("winnings")));

				if (user.getIdLong() == event.getAuthor().getIdLong()) {
					userIndex.set(i);
				}
			}

			if (users.isEmpty()) {
				event.replyFailure("There are no users which fit into this leaderboard").queue();
				return;
			}

			MessagePagedResult<Map.Entry<User, Long>> paged = new MessagePagedResult.Builder<>(event.getBot(), users)
				.setPerPage(10)
				.setSelect()
				.setCustomFunction(page -> {
					int rank = userIndex.get();

					EmbedBuilder embed = new EmbedBuilder()
						.setTitle("Winnings Leaderboard")
						.setFooter(event.getAuthor().getName() + "'s Rank: " + (rank == -1 ? "N/A" : NumberUtility.getSuffixed(rank)) + " | Page " + page.getPage() + "/" + page.getMaxPage(), event.getAuthor().getEffectiveAvatarUrl());

					page.forEach((entry, index) -> embed.appendDescription(String.format("%d. `%s` - $%,d\n", index + 1, MarkdownSanitizer.escape(entry.getKey().getAsTag()), entry.getValue())));

					return new MessageCreateBuilder().setEmbeds(embed.build());
				}).build();

			paged.execute(event);
		});
	}

	@Command(value="items", description="View the leaderboard for a specific items count of users")
	@CommandId(372)
	@Examples({"leaderboard items", "leaderboard items Shoe", "leaderboard items Diamond --server"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void items(Sx4CommandEvent event, @Argument(value="item", endless=true, nullDefault=true) Item item, @Option(value="server", aliases={"guild"}, description="View the leaderboard with a server filter") boolean guild) {
		Bson filter = Filters.and(Filters.ne("_id", event.getJDA().getSelfUser().getIdLong()), Filters.ne("amount", 0));
		if (item != null) {
			filter = Filters.and(filter, Filters.eq("item.id", item.getId()));
		}

		List<Bson> pipeline = List.of(
			Aggregates.project(Projections.include("amount", "userId", "item.id")),
			Aggregates.match(filter),
			Aggregates.group("$userId", Accumulators.sum("amount", "$amount")),
			Aggregates.sort(Sorts.descending("amount"))
		);

		event.getMongo().aggregateItems(pipeline).whenCompleteAsync((documents, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			List<Map.Entry<User, Long>> users = new ArrayList<>();
			AtomicInteger userIndex = new AtomicInteger(-1);

			int i = 0;
			for (Document data : documents) {
				User user = event.getShardManager().getUserById(data.getLong("_id"));
				if (user == null) {
					continue;
				}

				if (!event.getGuild().isMember(user) && guild) {
					continue;
				}

				i++;

				users.add(Map.entry(user, data.getLong("amount")));

				if (user.getIdLong() == event.getAuthor().getIdLong()) {
					userIndex.set(i);
				}
			}

			if (users.isEmpty()) {
				event.replyFailure("There are no users which fit into this leaderboard").queue();
				return;
			}

			MessagePagedResult<Map.Entry<User, Long>> paged = new MessagePagedResult.Builder<>(event.getBot(), users)
				.setPerPage(10)
				.setSelect()
				.setCustomFunction(page -> {
					int rank = userIndex.get();

					EmbedBuilder embed = new EmbedBuilder()
						.setTitle((item == null ? "All Items" : item.getName()) + " Leaderboard")
						.setFooter(event.getAuthor().getName() + "'s Rank: " + (rank == -1 ? "N/A" : NumberUtility.getSuffixed(rank)) + " | Page " + page.getPage() + "/" + page.getMaxPage(), event.getAuthor().getEffectiveAvatarUrl());

					page.forEach((entry, index) -> embed.appendDescription(String.format("%d. `%s` - %,d %s\n", index + 1, MarkdownSanitizer.escape(entry.getKey().getAsTag()), entry.getValue(), item == null ? "Item" + (entry.getValue() == 1 ? "" : "s") : item.getName())));

					return new MessageCreateBuilder().setEmbeds(embed.build());
				}).build();

			paged.execute(event);
		});
	}

	@Command(value="streak", description="View the leaderboard for the streaks of users")
	@CommandId(373)
	@Examples({"leaderboard streak", "leaderboard streak --server"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void streak(Sx4CommandEvent event, @Option(value="server", aliases={"guild"}, description="View the leaderboard with a server filter") boolean guild) {
		List<Bson> pipeline = List.of(
			Aggregates.project(Projections.computed("streak", "$economy.streak")),
			Aggregates.match(Filters.and(Filters.ne("_id", event.getJDA().getSelfUser().getIdLong()), Filters.exists("streak"), Filters.ne("streak", 0))),
			Aggregates.sort(Sorts.descending("streak"))
		);

		event.getMongo().aggregateUsers(pipeline).whenCompleteAsync((documents, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			List<Map.Entry<User, Integer>> users = new ArrayList<>();
			AtomicInteger userIndex = new AtomicInteger(-1);

			int i = 0;
			for (Document data : documents) {
				User user = event.getShardManager().getUserById(data.getLong("_id"));
				if (user == null) {
					continue;
				}

				if (!event.getGuild().isMember(user) && guild) {
					continue;
				}

				i++;

				users.add(Map.entry(user, data.getInteger("streak")));

				if (user.getIdLong() == event.getAuthor().getIdLong()) {
					userIndex.set(i);
				}
			}

			if (users.isEmpty()) {
				event.replyFailure("There are no users which fit into this leaderboard").queue();
				return;
			}

			MessagePagedResult<Map.Entry<User, Integer>> paged = new MessagePagedResult.Builder<>(event.getBot(), users)
				.setPerPage(10)
				.setSelect()
				.setCustomFunction(page -> {
					int rank = userIndex.get();

					EmbedBuilder embed = new EmbedBuilder()
						.setTitle("Streak Leaderboard")
						.setFooter(event.getAuthor().getName() + "'s Rank: " + (rank == -1 ? "N/A" : NumberUtility.getSuffixed(rank)) + " | Page " + page.getPage() + "/" + page.getMaxPage(), event.getAuthor().getEffectiveAvatarUrl());

					page.forEach((entry, index) -> embed.appendDescription(String.format("%d. `%s` - %,d day streak\n", index + 1, MarkdownSanitizer.escape(entry.getKey().getAsTag()), entry.getValue())));

					return new MessageCreateBuilder().setEmbeds(embed.build());
				}).build();

			paged.execute(event);
		});
	}

	@Command(value="votes", description="View the leaderboard for the votes of users")
	@CommandId(374)
	@Examples({"leaderboard votes", "leaderboard votes December", "leaderboard votes July --server"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void votes(Sx4CommandEvent event, @Argument(value="month", nullDefault=true) Month month, @Option(value="server", aliases={"guild"}, description="View the leaderboard with a server filter") boolean guild) {
		StringBuilder url = new StringBuilder(event.getConfig().getVoteWebserverUrl("votesCount"));

		int year;
		if (month != null) {
			OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

			year = month.getValue() > now.getMonthValue() ? now.getYear() - 1 : now.getYear();
			OffsetDateTime monthStart = OffsetDateTime.of(year, month.getValue(), 1, 0, 0, 0, 0, ZoneOffset.UTC);

			url.append("?after=").append(monthStart.toInstant().getEpochSecond()).append("&before=").append(monthStart.plusMonths(1).toInstant().getEpochSecond());
		} else {
			year = 0;
		}

		Request request = new Request.Builder()
			.url(url.toString())
			.build();

		event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
			Document data = Document.parse(response.body().string());

			List<Document> votes = data.getList("votes", Document.class);
			List<Map.Entry<User, Integer>> users = new ArrayList<>();
			AtomicInteger userIndex = new AtomicInteger(-1);

			int i = 0;
			for (Document vote : votes) {
				User user = event.getShardManager().getUserById(vote.getString("id"));
				if (user == null) {
					continue;
				}

				if (!event.getGuild().isMember(user) && guild) {
					continue;
				}

				i++;

				users.add(Map.entry(user, vote.getInteger("count")));

				if (user.getIdLong() == event.getAuthor().getIdLong()) {
					userIndex.set(i);
				}
			}

			MessagePagedResult<Map.Entry<User, Integer>> paged = new MessagePagedResult.Builder<>(event.getBot(), users)
				.setPerPage(10)
				.setSelect()
				.setCustomFunction(page -> {
					int rank = userIndex.get();

					EmbedBuilder embed = new EmbedBuilder()
						.setTitle("Votes Leaderboard" + (month == null ? "" : " for " + month.getDisplayName(TextStyle.FULL, Locale.UK) + " " + year))
						.setFooter(event.getAuthor().getName() + "'s Rank: " + (rank == -1 ? "N/A" : NumberUtility.getSuffixed(rank)) + " | Page " + page.getPage() + "/" + page.getMaxPage(), event.getAuthor().getEffectiveAvatarUrl());

					page.forEach((entry, index) -> embed.appendDescription(String.format("%d. `%s` - %,d vote%s\n", index + 1, MarkdownSanitizer.escape(entry.getKey().getAsTag()), entry.getValue(), entry.getValue() == 1 ? "" : "s")));

					return new MessageCreateBuilder().setEmbeds(embed.build());
				}).build();

			paged.execute(event);
		});
	}

}
