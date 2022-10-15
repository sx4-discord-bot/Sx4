package com.sx4.bot.commands.games;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.argument.Endless;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.option.Option;
import com.mongodb.client.model.*;
import com.sx4.bot.annotations.command.BotPermissions;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Redirects;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.games.GameState;
import com.sx4.bot.entities.games.GameType;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.NumberUtility;
import com.sx4.bot.utility.StringUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class GameCommand extends Sx4Command {

	public GameCommand() {
		super("game", 456);

		super.setDescription("Get information about games played on Sx4");
		super.setExamples("game list", "game leaderboard");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.GAMES);
	}

	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}

	@Command(value="leaderboard", aliases={"lb"}, description="View the users who have played/won/lost/drawn the most games")
	@CommandId(457)
	@Redirects({"lb games", "leaderboard games", "lb game", "leaderboard game"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void leaderboard(Sx4CommandEvent event, @Argument(value="games") @Endless(minArguments=0) GameType[] gameTypes, @Option(value="server", aliases={"guild"}, description="Filters by only users in the current server") boolean guild, @Option(value="state", description="Filters whether the leaderboard should be for wins, played, losses or draws") GameState state) {
		List<Bson> filters = new ArrayList<>();
		if (gameTypes.length > 0) {
			filters.add(Filters.in("type", Arrays.stream(gameTypes).map(GameType::getId).collect(Collectors.toList())));
		}

		if (state != null) {
			filters.add(Filters.eq("state", state.getId()));
		}

		List<Bson> pipeline = List.of(
			Aggregates.project(Projections.include("state", "type", "userId")),
			Aggregates.match(filters.isEmpty() ? Filters.empty() : Filters.and(filters)),
			Aggregates.group("$userId", Accumulators.sum("count", 1L)),
			Aggregates.sort(Sorts.descending("count"))
		);

		event.getMongo().aggregateGames(pipeline).whenComplete((games, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			List<Map.Entry<User, Long>> users = new ArrayList<>();
			AtomicInteger userIndex = new AtomicInteger(-1);

			int i = 0;
			for (Document game : games) {
				User user = event.getShardManager().getUserById(game.getLong("_id"));
				if (user == null) {
					continue;
				}

				if (!event.getGuild().isMember(user) && guild) {
					continue;
				}

				i++;

				users.add(Map.entry(user, game.getLong("count")));

				if (user.getIdLong() == event.getAuthor().getIdLong()) {
					userIndex.set(i);
				}
			}

			if (users.isEmpty()) {
				event.replyFailure("There are no users which fit into this leaderboard").queue();
				return;
			}

			PagedResult<Map.Entry<User, Long>> paged = new PagedResult<>(event.getBot(), users)
				.setPerPage(10)
				.setSelect()
				.setCustomFunction(page -> {
					int rank = userIndex.get();

					EmbedBuilder embed = new EmbedBuilder()
						.setTitle("Games Leaderboard")
						.setFooter(event.getAuthor().getName() + "'s Rank: " + (rank == -1 ? "N/A" : NumberUtility.getSuffixed(rank)) + " | Page " + page.getPage() + "/" + page.getMaxPage(), event.getAuthor().getEffectiveAvatarUrl());

					page.forEach((entry, index) -> embed.appendDescription(String.format("%d. `%s` - %,d game%s\n", index + 1, MarkdownSanitizer.escape(entry.getKey().getAsTag()), entry.getValue(), entry.getValue() == 1 ? "" : "s")));

					return new MessageCreateBuilder().setEmbeds(embed.build());
				});

			paged.execute(event);
		});
	}

	@Command(value="list", aliases={"game list", "game list @Shea#6653", "game list Shea"}, description="Lists basic info on all games a user has played on Sx4")
	@CommandId(297)
	@Redirects({"games"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void list(Sx4CommandEvent event, @Argument(value="user", endless=true, nullDefault=true) Member member) {
		User user = member == null ? event.getAuthor() : member.getUser();

		List<Document> games = event.getMongo().getGames(Filters.eq("userId", user.getIdLong()), Projections.include("type", "state")).into(new ArrayList<>());
		if (games.isEmpty()) {
			event.replyFailure("That user has not played any games yet").queue();
			return;
		}

		PagedResult<Document> paged = new PagedResult<>(event.getBot(), games)
			.setAuthor("Game List", null, user.getEffectiveAvatarUrl())
			.setIndexed(false)
			.setPerPage(15)
			.setSelect()
			.setDisplayFunction(game -> "`" + GameType.fromId(game.getInteger("type")).getName() + "` - " + StringUtility.title(GameState.fromId(game.getInteger("state")).name()));

		paged.execute(event);
	}

}
