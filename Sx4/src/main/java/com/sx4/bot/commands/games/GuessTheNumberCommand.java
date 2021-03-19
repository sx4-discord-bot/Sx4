package com.sx4.bot.commands.games;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.option.Option;
import com.sx4.bot.annotations.argument.DefaultNumber;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.Database;
import com.sx4.bot.entities.games.GameState;
import com.sx4.bot.entities.games.GameType;
import com.sx4.bot.waiter.Waiter;
import com.sx4.bot.waiter.Waiter.CancelType;
import com.sx4.bot.waiter.exception.CancelException;
import com.sx4.bot.waiter.exception.TimeoutException;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class GuessTheNumberCommand extends Sx4Command {

	public GuessTheNumberCommand() {
		super("guess the number", 296);

		super.setDescription("2 players choose a number between a range of numbers whoever is closest to the random number wins");
		super.setAliases("gtn");
		super.setCooldownDuration(60);
		super.setExamples("guess the number @Shea#6653", "guess the number Shea", "guess the number 402557516728369153");
		super.setCategoryAll(ModuleCategory.GAMES);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="user", endless=true) Member member, @Option(value="min") @Limit(min=1) @DefaultNumber(1) int min, @Option(value="max") @Limit(min=2) @DefaultNumber(50) int max) {
		User opponent = member.getUser();

		event.reply(opponent.getAsMention() + ", do you want to play guess the number with **" + event.getAuthor().getName() + "**? (Yes or No)")
			.allowedMentions(EnumSet.of(Message.MentionType.USER))
			.submit()
			.thenCompose(message -> {
				return new Waiter<>(event.getBot(), MessageReceivedEvent.class)
					.setUnique(opponent.getIdLong(), event.getChannel().getIdLong())
					.setTimeout(30)
					.setPredicate(e -> e.getMessage().getContentRaw().equalsIgnoreCase("yes"))
					.setOppositeCancelPredicate()
					.start();
			}).whenComplete((confirmEvent, confirmException) -> {
				Throwable confirmCause = confirmException instanceof CompletionException ? confirmException.getCause() : confirmException;
				if (confirmCause instanceof CancelException) {
					event.replySuccess("Cancelled").queue();
					return;
				} else if (confirmCause instanceof TimeoutException) {
					event.reply("Timed out :stopwatch:").queue();
					return;
				}

				CompletableFuture<MessageReceivedEvent> authorFuture = event.getAuthor().openPrivateChannel().submit()
					.thenCompose(channel -> channel.sendMessage("Send a number between **" + min + "** and **" + max + "** or `cancel` to cancel").submit())
					.thenCompose(message -> {
						return new Waiter<>(event.getBot(), MessageReceivedEvent.class)
							.setUnique(event.getAuthor().getIdLong(), message.getChannel().getIdLong())
							.setTimeout(30)
							.setCancelPredicate(e -> e.getMessage().getContentRaw().equalsIgnoreCase("cancel"))
							.setPredicate(e -> {
								int number;
								try {
									number = Integer.parseInt(e.getMessage().getContentRaw());
								} catch (NumberFormatException exception) {
									return false;
								}

								return number >= min && number <= max;
							}).start();
					});

				CompletableFuture<MessageReceivedEvent> opponentFuture = opponent.openPrivateChannel().submit()
					.thenCompose(channel -> channel.sendMessage("Send a number between **" + min + "** and **" + max + "** or `cancel` to cancel").submit())
					.thenCompose(message -> {
						return new Waiter<>(event.getBot(), MessageReceivedEvent.class)
							.setUnique(opponent.getIdLong(), message.getChannel().getIdLong())
							.setTimeout(30)
							.setCancelPredicate(e -> e.getMessage().getContentRaw().equalsIgnoreCase("cancel"))
							.setPredicate(e -> {
								int number;
								try {
									number = Integer.parseInt(e.getMessage().getContentRaw());
								} catch (NumberFormatException exception) {
									return false;
								}

								return number >= min && number <= max;
							}).start();
					});

				authorFuture.whenComplete((messageEvent, exception) -> {
					Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
					if (cause instanceof CancelException) {
						opponentFuture.cancel(true);
						if (((CancelException) cause).getType() == CancelType.USER) {
							event.getAuthor().openPrivateChannel().flatMap(channel -> channel.sendMessage("Cancelled " + event.getConfig().getSuccessEmote())).queue();
						}

						event.replyFailure("**" + event.getAuthor().getAsTag() + "** cancelled their response").queue();
						return;
					} else if (cause instanceof TimeoutException) {
						opponentFuture.cancel(true);

						event.replyFailure("**" + event.getAuthor().getAsTag() + "** took too long to respond").queue();
						return;
					} else if (cause instanceof ErrorResponseException) {
						opponentFuture.cancel(true);

						event.replyFailure("I could not send a message to **" + event.getAuthor().getAsTag() + "**").queue();
						return;
					}

					messageEvent.getChannel().sendMessage("Your number has been received, results will be sent in " + event.getTextChannel().getAsMention()).queue();
				});

				opponentFuture.whenComplete((messageEvent, exception) -> {
					Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
					if (cause instanceof CancelException) {
						authorFuture.cancel(true);

						if (((CancelException) cause).getType() == CancelType.USER) {
							opponent.openPrivateChannel().flatMap(channel -> channel.sendMessage("Cancelled " + event.getConfig().getSuccessEmote())).queue();
						}

						event.replyFailure("**" + opponent.getAsTag() + "** cancelled their response").queue();
						return;
					} else if (cause instanceof TimeoutException) {
						authorFuture.cancel(true);

						event.replyFailure("**" + opponent.getAsTag() + "** took too long to respond").queue();
						return;
					} else if (cause instanceof ErrorResponseException) {
						authorFuture.cancel(true);

						event.replyFailure("I could not send a message to **" + opponent.getAsTag() + "**").queue();
						return;
					}

					messageEvent.getChannel().sendMessage("Your number has been received, results will be sent in " + event.getTextChannel().getAsMention()).queue();
				});

				CompletableFuture.allOf(authorFuture, opponentFuture).whenComplete((result, exception) -> {
					if (exception != null) {
						return;
					}

					MessageReceivedEvent authorEvent = authorFuture.join(), opponentEvent = opponentFuture.join();

					ObjectId gameId = ObjectId.get();

					int authorNumber = Integer.parseInt(authorEvent.getMessage().getContentRaw());
					int opponentNumber = Integer.parseInt(opponentEvent.getMessage().getContentRaw());
					int randomNumber = event.getRandom().nextInt(max) + 1;

					Document authorData = new Document("userId", event.getAuthor().getIdLong())
						.append("gameId", gameId)
						.append("type", GameType.GTN.getId())
						.append("choice", authorNumber)
						.append("answer", randomNumber);

					Document opponentData = new Document("userId", opponent.getIdLong())
						.append("gameId", gameId)
						.append("type", GameType.GTN.getId())
						.append("choice", authorNumber)
						.append("answer", randomNumber);

					int authorDifference = Math.abs(authorNumber - randomNumber), opponentDifference = Math.abs(opponentNumber - randomNumber);

					StringBuilder content = new StringBuilder("The random number was **" + randomNumber + "**\n" + opponent.getName() + "'s number was **" + opponentNumber + "**\n" + event.getAuthor().getName() + "'s number was **" + authorNumber + "**\n\n");
					if (authorDifference == opponentDifference) {
						content.append("You both guessed the same number, It was a draw!");

						authorData.append("state", GameState.DRAW.getId());
						opponentData.append("state", GameState.DRAW.getId());
					} else if (authorDifference > opponentDifference) {
						content.append(opponent.getName()).append(" won! They were the closest to ").append(randomNumber);

						authorData.append("state", GameState.LOSS.getId());
						opponentData.append("state", GameState.WIN.getId());
					} else {
						content.append(event.getAuthor().getName()).append(" won! They were the closest to ").append(randomNumber);

						authorData.append("state", GameState.WIN.getId());
						opponentData.append("state", GameState.LOSS.getId());
					}

					event.reply(content.toString()).queue();

					event.getDatabase().insertManyGames(List.of(authorData, opponentData)).whenComplete(Database.exceptionally(event.getShardManager()));
				});
			});
	}

}
