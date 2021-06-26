package com.sx4.bot.commands.fun;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.option.Option;
import com.mongodb.client.model.*;
import com.sx4.bot.annotations.command.BotPermissions;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.annotations.command.Redirects;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.NumberUtility;
import com.sx4.bot.utility.TimeUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ReputationCommand extends Sx4Command {

	public static final long COOLDOWN = 86400L;

	public ReputationCommand() {
		super("reputation", 419);

		super.setDescription("Give reputation to another user");
		super.setExamples("reputation @Shea#6653", "reputation leaderboard", "reputation amount");
		super.setAliases("rep");
		super.setCategoryAll(ModuleCategory.FUN);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="user", endless=true) Member member) {
		User user = member.getUser();
		if (user.getIdLong() == event.getAuthor().getIdLong()) {
			event.replyFailure("You can not give reputation to yourself").queue();
			return;
		}

		if (user.isBot()) {
			event.replyFailure("You can not give reputation to bots").queue();
			return;
		}

		event.getMongo().withTransaction(session -> {
			List<Bson> update = List.of(Operators.set("reputation.resets", Operators.let(new Document("resets", Operators.ifNull("$reputation.resets", 0L)), Operators.cond(Operators.lt(Operators.nowEpochSecond(), "$$resets"), "$$resets", Operators.add(Operators.nowEpochSecond(), ReputationCommand.COOLDOWN)))));
			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().projection(Projections.include("reputation.resets")).returnDocument(ReturnDocument.BEFORE).upsert(true);

			Document data = event.getMongo().getUsers().findOneAndUpdate(session, Filters.eq("_id", event.getAuthor().getIdLong()), update, options);

			long now = Clock.systemUTC().instant().getEpochSecond(), resets = data == null ? 0L : data.getEmbedded(List.of("reputation", "resets"), 0L);
			if (now < resets) {
				event.reply("Slow down! You can give out reputation in " + TimeUtility.getTimeString(resets - now) + " :stopwatch:").queue();
				session.abortTransaction();
				return;
			}

			event.getMongo().getUsers().updateOne(session, Filters.eq("_id", user.getIdLong()), Updates.inc("reputation.amount", 1), new UpdateOptions().upsert(true));
		}).whenComplete((updated, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception) || !updated) {
				return;
			}

			event.replySuccess("**+1**, " + user.getName() + " has gained reputation").queue();
		});
	}

	@Command(value="amount", description="View the amount of reputation a user has")
	@CommandId(420)
	@Examples({"reputation amount", "reputation amount @Shea#6653", "reputation amount Shea"})
	public void amount(Sx4CommandEvent event, @Argument(value="user", endless=true, nullDefault=true) Member member) {
		User user = member == null ? event.getAuthor() : member.getUser();
		int amount = event.getMongo().getUserById(user.getIdLong(), Projections.include("reputation.amount")).getEmbedded(List.of("reputation", "amount"), 0);

		event.replyFormat("%s has **%,d** reputation", user.getAsTag(), amount).queue();
	}

	@Command(value="leaderboard", aliases={"lb"}, description="View the leaderboard for reputation across the bot")
	@CommandId(421)
	@Redirects({"leaderboard reputation", "lb rep", "lb reputation", "leaderboard rep"})
	@Examples({"reputation leaderboard", "reputation leaderboard --server"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void leaderboard(Sx4CommandEvent event, @Option(value="server", aliases={"guild"}, description="View the leaderboard with a server filter") boolean guild) {
		List<Bson> pipeline = List.of(
			Aggregates.project(Projections.computed("reputation", "$reputation.amount")),
			Aggregates.match(Filters.exists("reputation")),
			Aggregates.sort(Sorts.descending("reputation"))
		);

		event.getMongo().aggregateUsers(pipeline).whenComplete((iterable, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			List<Document> documents = iterable.into(new ArrayList<>());
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

				users.add(Map.entry(user, data.getInteger("reputation")));

				if (user.getIdLong() == event.getAuthor().getIdLong()) {
					userIndex.set(i);
				}
			}

			if (users.isEmpty()) {
				event.replyFailure("There are no users which fit into this leaderboard").queue();
				return;
			}

			PagedResult<Map.Entry<User, Integer>> paged = new PagedResult<>(event.getBot(), users)
				.setPerPage(10)
				.setCustomFunction(page -> {
					int rank = userIndex.get();

					EmbedBuilder embed = new EmbedBuilder()
						.setTitle("Reputation Leaderboard")
						.setFooter(event.getAuthor().getName() + "'s Rank: " + (rank == -1 ? "N/A" : NumberUtility.getSuffixed(rank)) + " | Page " + page.getPage() + "/" + page.getMaxPage(), event.getAuthor().getEffectiveAvatarUrl());

					page.forEach((entry, index) -> embed.appendDescription(String.format("%d. `%s` - %,d reputation\n", index + 1, MarkdownSanitizer.escape(entry.getKey().getAsTag()), entry.getValue())));

					return new MessageBuilder().setEmbed(embed.build()).build();
				});

			paged.execute(event);
		});
	}

}
