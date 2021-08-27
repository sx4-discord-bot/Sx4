package com.sx4.bot.commands.settings;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.option.Option;
import com.mongodb.client.model.*;
import com.sx4.bot.annotations.argument.DefaultNumber;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.annotations.command.Premium;
import com.sx4.bot.annotations.command.Redirects;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.NumberUtility;
import com.sx4.bot.waiter.Waiter;
import com.sx4.bot.waiter.exception.CancelException;
import com.sx4.bot.waiter.exception.TimeoutException;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class PremiumCommand extends Sx4Command {

	private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d'%s' MMMM u 'at' kk:mm 'UTC'");

	public PremiumCommand() {
		super("premium", 176);
		
		super.setDescription("Make a server premium or remove a server from being premium, you can make a server premium for $5");
		super.setExamples("premium add", "premium remove", "premium perks");
		super.setCategoryAll(ModuleCategory.SETTINGS);
	}
	
	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}
	
	@Command(value="add", description="Make a server premium")
	@CommandId(177)
	@Examples({"premium add", "premium add 31", "premium add 20 Sx4 | Support Server"})
	@Premium
	public void add(Sx4CommandEvent event, @Argument(value="days") @DefaultNumber(31) @Limit(min=1, max=365) int days, @Argument(value="server", endless=true, nullDefault=true) Guild guild) {
		if (guild == null) {
			guild = event.getGuild();
		}
		
		long guildId = guild.getIdLong();
		String guildName = guild.getName();

		int monthPrice = event.getConfig().getPremiumPrice();
		int price = (int) Math.round((monthPrice / (double) event.getConfig().getPremiumDays()) * days);

		long endAtPrior = event.getMongo().getGuildById(guildId, Projections.include("premium.endAt")).getEmbedded(List.of("premium", "endAt"), 0L);
		boolean hasPremium = endAtPrior != 0;

		MessageEmbed embed = new EmbedBuilder()
			.setColor(event.getConfig().getOrange())
			.setAuthor("Premium", null, event.getAuthor().getEffectiveAvatarUrl())
			.setDescription(String.format("Buying %d day%s of premium will:\n\n• Make you unable to use this credit on the other version of the bot\n• Use **$%.2f** of your credit\n• %s %1$s day%2$s of premium to the server\n\n:warning: **This action cannot be reversed** :warning:", days, days == 1 ? "" : "s", price / 100D, hasPremium ? "Add an extra" : "Give"))
			.build();

		List<Button> buttons = List.of(Button.success("confirm", "Confirm"), Button.danger("cancel", "Cancel"));

		event.reply(embed).setActionRow(buttons).submit().thenCompose(message -> {
			return new Waiter<>(event.getBot(), ButtonClickEvent.class)
				.setPredicate(e -> {
					Button button = e.getButton();
					return button != null && button.getId().equals("confirm") && e.getMessageIdLong() == message.getIdLong() && e.getUser().getIdLong() == event.getAuthor().getIdLong();
				})
				.setCancelPredicate(e -> {
					Button button = e.getButton();
					return button != null && button.getId().equals("cancel") && e.getMessageIdLong() == message.getIdLong() && e.getUser().getIdLong() == event.getAuthor().getIdLong();
				})
				.onFailure(e -> e.reply("This is not your button to click " + event.getConfig().getFailureEmote()).setEphemeral(true).queue())
				.setTimeout(60)
				.start();
		}).whenComplete((e, exception) -> {
			Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
			if (cause instanceof CancelException) {
				GenericEvent cancelEvent = ((CancelException) cause).getEvent();
				if (cancelEvent != null) {
					((ButtonClickEvent) cancelEvent).reply("Cancelled " + event.getConfig().getSuccessEmote()).queue();
				}

				return;
			} else if (cause instanceof TimeoutException) {
				event.reply("Timed out :stopwatch:").queue();
				return;
			} else if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			List<Bson> update = List.of(Operators.set("premium.credit", Operators.cond(Operators.gt(price, Operators.ifNull("$premium.credit", 0)), "$premium.credit", Operators.subtract("$premium.credit", price))));
			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.include("premium.credit")).upsert(true);

			event.getMongoMain().findAndUpdateUserById(event.getAuthor().getIdLong(), update, options).thenCompose(data -> {
				int credit = data == null ? 0 : data.getEmbedded(List.of("premium", "credit"), 0);
				if (price > credit) {
					e.reply("You do not have enough credit to buy premium for that long " + event.getConfig().getFailureEmote()).queue();
					return CompletableFuture.completedFuture(MongoDatabase.EMPTY_DOCUMENT);
				}

				List<Bson> guildUpdate = List.of(Operators.set("premium.endAt", Operators.add(TimeUnit.DAYS.toSeconds(days), Operators.ifNull("$premium.endAt", Operators.nowEpochSecond()))));
				FindOneAndUpdateOptions guildOptions = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.include("premium.endAt")).upsert(true);

				return event.getMongo().findAndUpdateGuildById(guildId, guildUpdate, guildOptions);
			}).whenComplete((data, databaseException) -> {
				if (ExceptionUtility.sendExceptionally(event, databaseException) || (data != null && data.isEmpty())) {
					return;
				}

				long endAt = data == null ? 0L : data.getEmbedded(List.of("premium", "endAt"), 0L);

				e.replyFormat("**%s** now has premium for %s%d day%s %s", guildName, endAt == 0 ? "" : "another ", days, days == 1 ? "" : "s", event.getConfig().getSuccessEmote()).queue();
			});
		});
	}

	@Command(value="check", description="Checks when the current premium in the server expires")
	@CommandId(178)
	@Examples({"premium check"})
	public void check(Sx4CommandEvent event) {
		long endAt = event.getMongo().getGuildById(event.getGuild().getIdLong(), Projections.include("premium.endAt")).getEmbedded(List.of("premium", "endAt"), 0L);
		if (endAt == 0) {
			event.replyFailure("This server currently doesn't have premium, you can give it premium with credit <https://patreon.com/Sx4>").queue();
			return;
		}

		OffsetDateTime expire = OffsetDateTime.ofInstant(Instant.ofEpochSecond(endAt), ZoneOffset.UTC);
		if (expire.isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
			event.replyFailure("Premium for this server expired on the **" + String.format(expire.format(this.formatter), NumberUtility.getSuffix(expire.getDayOfMonth())) + "**, you can renew it with more credit <https://patreon.com/Sx4>").queue();
			return;
		}

		event.replyFormat("Premium for this server will expire on the **" +  String.format(expire.format(this.formatter), NumberUtility.getSuffix(expire.getDayOfMonth())) + "**").queue();
	}

	@Command(value="credit", description="Checks your current credit")
	@CommandId(179)
	@Examples({"premium credit"})
	public void credit(Sx4CommandEvent event) {
		Document premium = event.getMongoMain().getUserById(event.getAuthor().getIdLong(), Projections.include("premium.credit", "premium.endAt")).get("premium", MongoDatabase.EMPTY_DOCUMENT);

		int credit = premium.getInteger("credit", 0);

		long endAt = premium.get("endAt", -1L);
		OffsetDateTime expire = OffsetDateTime.ofInstant(Instant.ofEpochSecond(endAt), ZoneOffset.UTC);
		String format = String.format(expire.format(this.formatter), NumberUtility.getSuffix(expire.getDayOfMonth()));

		event.replyFormat("Your current credit is **$%,.2f**%s", credit / 100D, endAt == -1 ? "" : "\n\nYour personal premium " + (expire.isBefore(OffsetDateTime.now(ZoneOffset.UTC)) ? "expired on the **" + format + "**, you can renew it here <https://patreon.com/Sx4>" : "will expire on the **" + format + "**")).queue();
	}

	@Command(value="perks", description="View the perks you get when you or a server has premium")
	@CommandId(462)
	@Examples({"premium perks"})
	public void perks(Sx4CommandEvent event) {
		List<String> userPerks = event.getConfig().getPremiumUserPerks(), serverPerks = event.getConfig().getPremiumServerPerks();

		EmbedBuilder embed = new EmbedBuilder()
			.setAuthor("Premium Perks", null, event.getSelfUser().getEffectiveAvatarUrl())
			.addField("Personal Perks", "• " + String.join("\n• ", userPerks), false)
			.addField("Server Perks", "• " + String.join("\n• ", serverPerks), false);

		event.reply(embed.build()).queue();
	}

	@Command(value="leaderboard", aliases={"lb"}, description="Leaderboard for Sx4s biggest donors")
	@CommandId(446)
	@Redirects({"lb premium", "leaderboard premium"})
	@Examples({"premium leaderboard"})
	public void leaderboard(Sx4CommandEvent event, @Option(value="server", aliases={"guild"}, description="Filters the results to only people in the current server") boolean guild) {
		List<Bson> pipeline = List.of(
			Aggregates.project(Projections.computed("total", "$premium.total")),
			Aggregates.match(Filters.and(Filters.exists("total"), Filters.ne("total", 0))),
			Aggregates.sort(Sorts.descending("total"))
		);

		event.getMongoMain().aggregateUsers(pipeline).whenCompleteAsync((documents, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			List<Map.Entry<String, Integer>> users = new ArrayList<>();
			AtomicInteger userIndex = new AtomicInteger(-1);

			int i = 0;
			for (Document data : documents) {
				long id = data.getLong("_id");
				User user = event.getShardManager().getUserById(data.getLong("_id"));
				if ((user == null || !event.getGuild().isMember(user)) && guild) {
					continue;
				}

				i++;

				users.add(Map.entry(user == null ? "Anonymous#0000 (" + id + ")" : MarkdownSanitizer.escape(user.getAsTag()), data.getInteger("total")));

				if (user != null && user.getIdLong() == event.getAuthor().getIdLong()) {
					userIndex.set(i);
				}
			}

			if (users.isEmpty()) {
				event.replyFailure("There are no users which fit into this leaderboard").queue();
				return;
			}

			PagedResult<Map.Entry<String, Integer>> paged = new PagedResult<>(event.getBot(), users)
				.setPerPage(10)
				.setCustomFunction(page -> {
					int rank = userIndex.get();

					EmbedBuilder embed = new EmbedBuilder()
						.setTitle("Donors Leaderboard")
						.setFooter(event.getAuthor().getName() + "'s Rank: " + (rank == -1 ? "N/A" : NumberUtility.getSuffixed(rank)) + " | Page " + page.getPage() + "/" + page.getMaxPage(), event.getAuthor().getEffectiveAvatarUrl());

					page.forEach((entry, index) -> embed.appendDescription(String.format("%d. `%s` - $%,.2f\n", index + 1, entry.getKey(), entry.getValue() / 100D)));

					return new MessageBuilder().setEmbeds(embed.build());
				});

			paged.execute(event);
		});
	}
	
}
