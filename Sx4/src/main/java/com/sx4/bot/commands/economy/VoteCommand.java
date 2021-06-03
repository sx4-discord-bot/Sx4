package com.sx4.bot.commands.economy;

import com.mongodb.client.model.*;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.TimeUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import okhttp3.Request;
import org.bson.Document;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

public class VoteCommand extends Sx4Command {

	public static final long COOLDOWN = 43200L;

	public VoteCommand() {
		super("vote", 405);

		super.setDescription("Vote for the bot on top.gg for some extra money");
		super.setExamples("vote");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.ECONOMY);
	}

	public void onCommand(Sx4CommandEvent event) {
		Request request = new Request.Builder()
			.url(event.getConfig().getVoteWebserverUrl("440996323156819968/votes/user/" + event.getAuthor().getId() + "/unused/use"))
			.addHeader("Authorization", event.getConfig().getVoteApi(true))
			.build();

		event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
			Document data = Document.parse(response.body().string());
			if (!data.getBoolean("success")) {
				Request latest = new Request.Builder()
					.url(event.getConfig().getVoteWebserverUrl("440996323156819968/votes/user/" + event.getAuthor().getId() + "/latest"))
					.addHeader("Authorization", event.getConfig().getVoteApi(true))
					.build();

				event.getHttpClient().newCall(latest).enqueue((HttpCallback) latestResponse -> {
					Document latestVote = Document.parse(latestResponse.body().string());

					OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
					boolean weekend = now.getDayOfWeek() == DayOfWeek.FRIDAY || now.getDayOfWeek() == DayOfWeek.SATURDAY || now.getDayOfWeek() == DayOfWeek.SUNDAY;

					EmbedBuilder embed = new EmbedBuilder()
						.setAuthor("Vote Bonus", null, event.getAuthor().getEffectiveAvatarUrl());

					long timeRemaining = 0;

					Document vote = latestVote.get("vote", Document.class);
					if (vote != null && latestVote.getBoolean("success")) {
						timeRemaining = vote.get("time", Number.class).longValue() - Clock.systemUTC().instant().getEpochSecond() + VoteCommand.COOLDOWN;
					}

					if (timeRemaining > 0) {
						embed.addField("Sx4", "**[You have voted recently you can vote for the bot again in " + TimeUtility.getTimeString(timeRemaining) + "](https://top.gg/bot/440996323156819968/vote)**", false);
					} else {
						embed.addField("Sx4", "**[You can vote for Sx4 for an extra $" + (weekend ? 1600 : 800) + "](https://top.gg/bot/440996323156819968/vote)**", false);
					}

					event.reply(embed.build()).queue();
				});

				return;
			}

			List<Document> votes = data.getList("votes", Document.class, Collections.emptyList());
			Map<User, Long> referrers = new HashMap<>();

			long money = 0L;
			for (Document vote : votes) {
				boolean weekend = vote.getBoolean("weekend");
				money += weekend ? 1600 : 800;

				Document query = vote.get("query", Document.class);
				if (query == null) {
					continue;
				}

				Object referral = query.get("referral");
				if (referral == null) {
					continue;
				}

				String id = referral instanceof List ? (String) ((List<?>) referral).get(0) : (String) referral;

				User user;
				try {
					user = event.getShardManager().getUserById(id);
				} catch (NumberFormatException e) {
					continue;
				}

				if (user == null) {
					continue;
				}

				long amount = weekend ? 500 : 250;
				referrers.compute(user, (key, value) -> value == null ? amount : value + amount);
			}

			List<WriteModel<Document>> bulkData = new ArrayList<>();
			UpdateOptions options = new UpdateOptions().upsert(true);

			StringJoiner content = new StringJoiner(", ");
			Set<User> keys = referrers.keySet();
			for (User user : keys) {
				content.add(String.format("%s (**$%,d**)", user.getAsTag(), referrers.get(user)));

				bulkData.add(new UpdateOneModel<>(Filters.eq("_id", user.getIdLong()), Updates.inc("economy.balance", money), options));
			}

			bulkData.add(new UpdateOneModel<>(Filters.eq("_id", event.getAuthor().getIdLong()), Updates.inc("economy.balance", money), options));

			String message = String.format("You have voted for the bot **%,d** time%s since you last used the command gathering you a total of **$%,d**, Vote for the bots again in 12 hours for more money.%s", votes.size(), votes.size() == 1 ? "" : "s", money, content.length() == 0 ? "" : "Referred users: " + content.toString());
			event.getMongo().bulkWriteUsers(bulkData).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				event.reply(message).queue();
			});
		});
	}

}
