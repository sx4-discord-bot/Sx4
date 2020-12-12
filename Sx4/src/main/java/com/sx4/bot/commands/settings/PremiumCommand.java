package com.sx4.bot.commands.settings;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReturnDocument;
import com.sx4.bot.annotations.argument.DefaultInt;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.annotations.command.Donator;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.Database;
import com.sx4.bot.database.model.Operators;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.waiter.Waiter;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import org.bson.conversions.Bson;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class PremiumCommand extends Sx4Command {

	DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d/MM/u k:m");

	public PremiumCommand() {
		super("premium");
		
		super.setDescription("Make a server premium or remove a server from being premium, you can make a server premium for $5");
		super.setExamples("premium add", "premium remove", "premium list");
		super.setCategoryAll(ModuleCategory.SETTINGS);
	}
	
	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}
	
	@Command(value="add", description="Make a server premium")
	@Examples({"premium add", "premium add 30", "premium add 20 Sx4 | Support Server"})
	@Donator
	public void add(Sx4CommandEvent event, @Argument(value="days") @Limit(min=1, max=365) @DefaultInt(30) int days, @Argument(value="server", endless=true, nullDefault=true) Guild guild) {
		if (guild == null) {
			guild = event.getGuild();
		}
		
		long guildId = guild.getIdLong();
		String guildName = guild.getName();

		int monthPrice = this.config.getPremiumPrice();
		int price = (int) Math.round((monthPrice / 30D) * days);

		long endsAtPrior = this.database.getGuildById(guildId, Projections.include("premium.endsAt")).getEmbedded(List.of("premium", "endsAt"), 0L);
		boolean hasPremium = endsAtPrior != 0;

		MessageEmbed embed = new EmbedBuilder()
			.setColor(this.config.getOrange())
			.setAuthor("Premium", null, event.getAuthor().getEffectiveAvatarUrl())
			.setDescription(String.format("Buying %d day%s of premium will:\n\n• Use **$%.2f** of your credit\n• %s %1$s day%2$s of premium to the server\n\n:warning: **This action cannot be reversed** :warning:", days, days == 1 ? "" : "s", price / 100D, hasPremium ? "Add an extra" : "Give"))
			.setFooter("Say yes to continue and cancel to cancel")
			.build();

		event.reply(embed).queue($ -> {
			Waiter<GuildMessageReceivedEvent> waiter = new Waiter<>(GuildMessageReceivedEvent.class)
				.setCancelPredicate(e -> e.getMessage().getContentRaw().equalsIgnoreCase("cancel"))
				.setTimeout(30)
				.setUnique(event.getAuthor().getIdLong(), event.getTextChannel().getIdLong())
				.setPredicate(e -> e.getMessage().getContentRaw().equalsIgnoreCase("yes"))
				.setOppositeCancelPredicate();

			waiter.onCancelled(type -> event.replyFailure("Cancelled").queue());

			waiter.onTimeout(() -> event.reply("Timed out :stopwatch:").queue());

			waiter.future().thenCompose(messageEvent -> {
				List<Bson> update = List.of(Operators.set("premium.credit", Operators.cond(Operators.gt(price, Operators.ifNull("$premium.credit", 0)), "$premium.credit", Operators.subtract("$premium.credit", price))));
				FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.include("premium.credit")).upsert(true);

				return this.database.findAndUpdateUserById(event.getAuthor().getIdLong(), update, options);
			}).thenCompose(data -> {
				int credit = data == null ? 0 : data.getEmbedded(List.of("premium", "credit"), 0);
				if (price > credit) {
					event.replyFailure("You do not have enough credit to buy premium for that long").queue();
					return CompletableFuture.completedFuture(Database.EMPTY_DOCUMENT);
				}

				List<Bson> update = List.of(Operators.set("premium.endAt", Operators.add(TimeUnit.DAYS.toSeconds(days), Operators.ifNull("$premium.endAt", Operators.nowEpochSecond()))));
				FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.include("premium.endAt")).upsert(true);

				return this.database.findAndUpdateGuildById(guildId, update, options);
			}).whenComplete((data, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception) || (data != null && data.isEmpty())) {
					return;
				}

				long endAt = data == null ? 0L : data.getEmbedded(List.of("premium", "endAt"), 0L);

				event.replyFormat("**%s** now has premium for %s%d day%s %s", guildName, endAt == 0 ? "" : "another ", days, days == 1 ? "" : "s", this.config.getSuccessEmote()).queue();
			});

			waiter.start();
		});
	}

	@Command(value="check", description="Checks when the current premium in the server expires")
	@Examples({"premium check"})
	public void check(Sx4CommandEvent event) {
		long endsAt = this.database.getGuildById(event.getGuild().getIdLong(), Projections.include("premium.endsAt")).getEmbedded(List.of("premium", "endsAt"), 0L);
		if (endsAt == 0) {
			event.replyFailure("This server currently doesn't have premium").queue();
			return;
		}

		ZonedDateTime expire = ZonedDateTime.ofInstant(Instant.ofEpochSecond(endsAt), ZoneOffset.UTC);
		event.replyFormat("Premium for this server will expire on **%s UTC**", expire.format(this.formatter)).queue();
	}

	@Command(value="credit", description="Checks your current credit")
	@Examples({"premium credit"})
	public void credit(Sx4CommandEvent event) {
		int credit = this.database.getUserById(event.getAuthor().getIdLong(), Projections.include("premium.credit")).getEmbedded(List.of("premium", "credit"), 0);

		event.replyFormat("Your current credit is **$%.2f**", credit / 100D).queue();
	}
	
}
