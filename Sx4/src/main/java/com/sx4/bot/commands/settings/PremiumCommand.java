package com.sx4.bot.commands.settings;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReturnDocument;
import com.sx4.bot.annotations.argument.DefaultNumber;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.annotations.command.Premium;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.Database;
import com.sx4.bot.database.model.Operators;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.NumberUtility;
import com.sx4.bot.waiter.Waiter;
import com.sx4.bot.waiter.exception.CancelException;
import com.sx4.bot.waiter.exception.TimeoutException;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.bson.conversions.Bson;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

public class PremiumCommand extends Sx4Command {

	private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d'%s' MMMM u 'at' k:m 'UTC'");

	public PremiumCommand() {
		super("premium", 176);
		
		super.setDescription("Make a server premium or remove a server from being premium, you can make a server premium for $5");
		super.setExamples("premium add", "premium remove", "premium list");
		super.setCategoryAll(ModuleCategory.SETTINGS);
	}
	
	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}
	
	@Command(value="add", description="Make a server premium")
	@CommandId(177)
	@Examples({"premium add", "premium add 30", "premium add 20 Sx4 | Support Server"})
	@Premium
	public void add(Sx4CommandEvent event, @Argument(value="days") @DefaultNumber(30) @Limit(min=1, max=365) int days, @Argument(value="server", endless=true, nullDefault=true) Guild guild) {
		if (guild == null) {
			guild = event.getGuild();
		}
		
		long guildId = guild.getIdLong();
		String guildName = guild.getName();

		int monthPrice = event.getConfig().getPremiumPrice();
		int price = (int) Math.round((monthPrice / 30D) * days);

		long endAtPrior = event.getDatabase().getGuildById(guildId, Projections.include("premium.endAt")).getEmbedded(List.of("premium", "endAt"), 0L);
		boolean hasPremium = endAtPrior != 0;

		MessageEmbed embed = new EmbedBuilder()
			.setColor(event.getConfig().getOrange())
			.setAuthor("Premium", null, event.getAuthor().getEffectiveAvatarUrl())
			.setDescription(String.format("Buying %d day%s of premium will:\n\n• Make you unable to use this credit on the other version of the bot\n• Use **$%.2f** of your credit\n• %s %1$s day%2$s of premium to the server\n\n:warning: **This action cannot be reversed** :warning:", days, days == 1 ? "" : "s", price / 100D, hasPremium ? "Add an extra" : "Give"))
			.setFooter("Say yes to continue and cancel to cancel")
			.build();

		event.reply(embed).submit().thenCompose($ -> {
			return new Waiter<>(event.getBot(), MessageReceivedEvent.class)
				.setTimeout(30)
				.setUnique(event.getAuthor().getIdLong(), event.getTextChannel().getIdLong())
				.setPredicate(e -> e.getMessage().getContentRaw().equalsIgnoreCase("yes"))
				.setOppositeCancelPredicate()
				.start();
		}).thenCompose(messageEvent -> {
			List<Bson> update = List.of(Operators.set("premium.credit", Operators.cond(Operators.gt(price, Operators.ifNull("$premium.credit", 0)), "$premium.credit", Operators.subtract("$premium.credit", price))));
			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.include("premium.credit")).upsert(true);

			return event.getMainDatabase().findAndUpdateUserById(event.getAuthor().getIdLong(), update, options);
		}).thenCompose(data -> {
			int credit = data == null ? 0 : data.getEmbedded(List.of("premium", "credit"), 0);
			if (price > credit) {
				event.replyFailure("You do not have enough credit to buy premium for that long").queue();
				return CompletableFuture.completedFuture(Database.EMPTY_DOCUMENT);
			}

			List<Bson> update = List.of(Operators.set("premium.endAt", Operators.add(TimeUnit.DAYS.toSeconds(days), Operators.ifNull("$premium.endAt", Operators.nowEpochSecond()))));
			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.include("premium.endAt")).upsert(true);

			return event.getDatabase().findAndUpdateGuildById(guildId, update, options);
		}).whenComplete((data, exception) -> {
			Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
			if (cause instanceof CancelException) {
				event.replySuccess("Cancelled").queue();
				return;
			} else if (cause instanceof TimeoutException) {
				event.reply("Timed out :stopwatch:").queue();
				return;
			} else if (ExceptionUtility.sendExceptionally(event, cause) || (data != null && data.isEmpty())) {
				return;
			}

			long endAt = data == null ? 0L : data.getEmbedded(List.of("premium", "endAt"), 0L);

			event.replyFormat("**%s** now has premium for %s%d day%s %s", guildName, endAt == 0 ? "" : "another ", days, days == 1 ? "" : "s", event.getConfig().getSuccessEmote()).queue();
		});
	}

	@Command(value="check", description="Checks when the current premium in the server expires")
	@CommandId(178)
	@Examples({"premium check"})
	public void check(Sx4CommandEvent event) {
		long endAt = event.getDatabase().getGuildById(event.getGuild().getIdLong(), Projections.include("premium.endAt")).getEmbedded(List.of("premium", "endAt"), 0L);
		if (endAt == 0) {
			event.replyFailure("This server currently doesn't have premium").queue();
			return;
		}

		OffsetDateTime expire = OffsetDateTime.ofInstant(Instant.ofEpochSecond(endAt), ZoneOffset.UTC);
		event.replyFormat("Premium for this server will expire on **%s**", String.format(expire.format(this.formatter), NumberUtility.getSuffix(expire.getDayOfMonth()))).queue();
	}

	@Command(value="credit", description="Checks your current credit")
	@CommandId(179)
	@Examples({"premium credit"})
	public void credit(Sx4CommandEvent event) {
		int credit = event.getMainDatabase().getUserById(event.getAuthor().getIdLong(), Projections.include("premium.credit")).getEmbedded(List.of("premium", "credit"), 0);

		event.replyFormat("Your current credit is **$%,.2f**", credit / 100D).queue();
	}
	
}
