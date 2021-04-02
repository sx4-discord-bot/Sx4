package com.sx4.bot.commands.fun;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.command.Command.Cooldown;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.sx4.bot.annotations.argument.Options;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.CooldownMessage;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.annotations.command.Redirects;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.argument.Alternative;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.waiter.Waiter;
import com.sx4.bot.waiter.exception.CancelException;
import com.sx4.bot.waiter.exception.TimeoutException;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.CompletionException;

public class MarriageCommand extends Sx4Command {

	private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMMM uuuu HH:mm");

	public MarriageCommand() {
		super("marriage", 267);

		super.setDescription("Marry up to 5 users");
		super.setExamples("marriage add", "marriage remove", "marriage list");
		super.setCategoryAll(ModuleCategory.FUN);
	}

	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}

	@Command(value="add", description="Propose to a user and marry them if they accept")
	@CommandId(268)
	@Redirects({"marry"})
	@Cooldown(60)
	@CooldownMessage("You already have a pending marriage :no_entry:")
	@Examples({"marriage add @Shea#6653", "marriage add Shea", "marriage add 402557516728369153"})
	public void add(Sx4CommandEvent event, @Argument(value="user", endless=true) Member member) {
		User author = event.getAuthor();
		if (member.getUser().isBot()) {
			event.replyFailure("You cannot marry bots").queue();
			return;
		}

		Bson checkFilter = Filters.or(
			Filters.eq("proposerId", author.getIdLong()),
			Filters.eq("partnerId", author.getIdLong()),
			Filters.eq("proposerId", member.getIdLong()),
			Filters.eq("partnerId", member.getIdLong())
		);

		List<Document> marriages = event.getDatabase().getMarriages(checkFilter, Projections.include("partnerId", "proposerId")).into(new ArrayList<>());

		long userCount = marriages.stream().filter(d -> d.getLong("proposerId") == author.getIdLong() || d.getLong("partnerId") == author.getIdLong()).count();
		if (userCount >= 5) {
			event.removeCooldown();
			event.replyFailure("You cannot marry more than 5 users").queue();
			return;
		}

		long memberCount = marriages.stream().filter(d -> d.getLong("proposerId") == member.getIdLong() || d.getLong("partnerId") == member.getIdLong()).count();
		if (memberCount >= 5) {
			event.removeCooldown();
			event.replyFailure("That user is already married to 5 users").queue();
			return;
		}

		event.reply(member.getAsMention() + ", **" + author.getName() + "** would like to marry you! Do you accept? (Yes or No)")
			.allowedMentions(EnumSet.of(Message.MentionType.USER))
			.submit()
			.thenCompose(message -> {
				return new Waiter<>(event.getBot(), MessageReceivedEvent.class)
					.setPredicate(e -> e.getMessage().getContentRaw().equalsIgnoreCase("yes"))
					.setOppositeCancelPredicate()
					.setTimeout(60)
					.setUnique(member.getIdLong(), event.getChannel().getIdLong())
					.start();
			}).thenCompose(e -> {
				Bson filter = Filters.or(Filters.and(Filters.eq("proposerId", member.getIdLong()), Filters.eq("partnerId", author.getIdLong())), Filters.and(Filters.eq("proposerId", author.getIdLong()), Filters.eq("partnerId", member.getIdLong())));

				return event.getDatabase().updateMarriage(filter, Updates.combine(Updates.setOnInsert("proposerId", author.getIdLong()), Updates.setOnInsert("partnerId", member.getIdLong())));
			}).whenComplete((result, exception) -> {
				event.removeCooldown();
				Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
				if (cause instanceof CancelException) {
					event.replySuccess("Cancelled").queue();
					return;
				} else if (cause instanceof TimeoutException) {
					event.reply("Timed out :stopwatch:").queue();
					return;
				} else if (ExceptionUtility.sendExceptionally(event, cause)) {
					return;
				}

				if (result.getMatchedCount() != 0) {
					event.replyFailure("You're already married to that user").queue();
					return;
				}

				event.reply("You're now married to " + member.getAsMention() + " :tada: :heart:").queue();
			});
	}

	@Command(value="remove", description="Divorce someone you are currently married to")
	@CommandId(269)
	@Redirects({"divorce"})
	@Examples({"marriage remove @Shea#6653", "marriage remove Shea", "marriage remove all"})
	public void remove(Sx4CommandEvent event, @Argument(value="user | all", endless=true) @Options("all") Alternative<Member> option) {
		User author = event.getAuthor();
		if (option.isAlternative()) {
			event.reply(author.getName() + ", are you sure you want to divorce everyone you are currently married to? (Yes or No)").submit()
				.thenCompose(message -> {
					return new Waiter<>(event.getBot(), MessageReceivedEvent.class)
						.setPredicate(messageEvent -> messageEvent.getMessage().getContentRaw().equalsIgnoreCase("yes"))
						.setOppositeCancelPredicate()
						.setTimeout(30)
						.setUnique(author.getIdLong(), event.getChannel().getIdLong())
						.start();
				}).thenCompose(e -> {
					Bson filter = Filters.or(Filters.eq("proposerId", author.getIdLong()), Filters.eq("partnerId", author.getIdLong()));

					return event.getDatabase().deleteManyMarriages(filter);
				}).whenComplete((result, exception) -> {
					Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
					if (cause instanceof CancelException) {
						return;
					} else if (cause instanceof TimeoutException) {
						return;
					} else if (ExceptionUtility.sendExceptionally(event, cause)) {
						return;
					}

					if (result.getDeletedCount() == 0) {
						event.replyFailure("You are not married to anyone").queue();
						return;
					}

					event.replySuccess("You are no longer married to anyone").queue();
				});
		} else {
			Member member = option.getValue();

			Bson filter = Filters.or(Filters.and(Filters.eq("proposerId", member.getIdLong()), Filters.eq("partnerId", author.getIdLong())), Filters.and(Filters.eq("proposerId", author.getIdLong()), Filters.eq("partnerId", member.getIdLong())));
			event.getDatabase().deleteMarriage(filter).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (result.getDeletedCount() == 0) {
					event.replyFailure("You are not married to that user").queue();
					return;
				}

				event.replySuccess("You are no longer married to **" + member.getUser().getAsTag() + "**").queue();
			});
		}
	}

	@Command(value="list", description="Lists a users marriages")
	@CommandId(270)
	@Redirects({"married"})
	@Examples({"marriage list", "marriage list @Shea#6653", "marriage list Shea"})
	public void list(Sx4CommandEvent event, @Argument(value="user", endless=true, nullDefault=true) Member member) {
		member = member == null ? event.getMember() : member;
		User user = member.getUser();

		Bson filter = Filters.or(Filters.eq("proposerId", user.getIdLong()), Filters.eq("partnerId", user.getIdLong()));

		List<Document> marriages = event.getDatabase().getMarriages(filter, Projections.include("proposerId", "partnerId")).into(new ArrayList<>());
		if (marriages.isEmpty()) {
			event.replyFailure("That user is not married to anyone").queue();
			return;
		}

		StringJoiner joiner = new StringJoiner("\n");
		for (Document marriage : marriages) {
			long partnerId = marriage.getLong("partnerId");
			long otherId = partnerId == user.getIdLong() ? marriage.getLong("proposerId") : partnerId;

			User other = event.getShardManager().getUserById(otherId);
			joiner.add((other == null ? "Anonymous#0000" : other.getAsTag()) + " (" + otherId + ")");
		}

		EmbedBuilder embed = new EmbedBuilder()
			.setAuthor(user.getName() + "'s Partners", null, user.getEffectiveAvatarUrl())
			.setDescription(joiner.toString())
			.setColor(member.getColorRaw());

		event.reply(embed.build()).queue();
	}

	@Command(value="info", description="Get info on a marriage with one of your partners")
	@CommandId(272)
	@Examples({"marriage info @Shea#6653", "marriage info Shea", "marriage info 402557516728369153"})
	public void info(Sx4CommandEvent event, @Argument(value="user", endless=true) Member member) {
		User author = event.getAuthor();

		Bson filter = Filters.or(Filters.and(Filters.eq("proposerId", member.getIdLong()), Filters.eq("partnerId", author.getIdLong())), Filters.and(Filters.eq("proposerId", author.getIdLong()), Filters.eq("partnerId", member.getIdLong())));

		Document marriage = event.getDatabase().getMarriage(filter, Projections.include("proposerId"));
		if (marriage == null) {
			event.replyFailure("You are not married to that user").queue();
			return;
		}

		EmbedBuilder embed = new EmbedBuilder()
			.setAuthor(author.getName() + " \u2764\uFE0F " + member.getUser().getName(), null, author.getEffectiveAvatarUrl())
			.addField("Proposer", author.getIdLong() == marriage.getLong("proposerId") ? author.getAsTag() : member.getUser().getAsTag(), false)
			.addField("Marriage Time", OffsetDateTime.ofInstant(Instant.ofEpochSecond(marriage.getObjectId("_id").getTimestamp()), ZoneOffset.UTC).format(this.formatter), false);

		event.reply(embed.build()).queue();
	}

}
