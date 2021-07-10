package com.sx4.bot.commands.management;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReturnDocument;
import com.sx4.bot.annotations.argument.AlternativeOptions;
import com.sx4.bot.annotations.argument.DefaultNumber;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.annotations.command.AuthorPermissions;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.argument.Alternative;
import com.sx4.bot.entities.argument.MessageArgument;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.NumberUtility;
import com.sx4.bot.utility.SearchUtility;
import com.sx4.bot.utility.TimeUtility;
import com.sx4.bot.waiter.Waiter;
import com.sx4.bot.waiter.exception.CancelException;
import com.sx4.bot.waiter.exception.TimeoutException;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class GiveawayCommand extends Sx4Command {

	public GiveawayCommand() {
		super("giveaway", 46);
		
		super.setDescription("Setup giveaways in a certain channel which will be decided randomly through reactions");
		super.setExamples("giveaway setup", "giveaway reroll", "giveaway restart");
		super.setCategoryAll(ModuleCategory.MANAGEMENT);
	}
	
	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}
	
	private MessageEmbed getEmbed(int winners, long seconds, String item) {
		EmbedBuilder embed = new EmbedBuilder()
			.setTitle("Giveaway")
			.setDescription(String.format("Enter by reacting with :tada:\n\nThis giveaway is for **%s**\nDuration: **%s**\nWinners: **%,d**", item, TimeUtility.getTimeString(seconds), winners))
			.setTimestamp(Instant.ofEpochSecond(Clock.systemUTC().instant().getEpochSecond() + seconds))
			.setFooter("Ends", null);
		
		return embed.build();
	}
	
	@Command(value="setup", description="Setup giveaways for users to react to")
	@CommandId(47)
	@Examples({"giveaway setup", "giveaway setup #giveaways 1 7d $10 Nitro"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void setup(Sx4CommandEvent event, @Argument(value="channel", nullDefault=true) TextChannel channel, @Argument(value="winners") @DefaultNumber(0) @Limit(min=1) int winners, @Argument(value="duration", nullDefault=true) Duration duration, @Argument(value="item", nullDefault=true, endless=true) String item) {
		if (channel != null && winners != 0 && duration != null && item != null) {
			long seconds = duration.toSeconds();
			if (seconds < 1) {
				event.replyFailure("The duration of a giveaway cannot be less than 1 second").queue();
				return;
			}
			
			channel.sendMessageEmbeds(this.getEmbed(winners, seconds, item)).queue(message -> {
				message.addReaction("🎉").queue();
				
				Document data = new Document("messageId", message.getIdLong())
					.append("channelId", channel.getIdLong())
					.append("guildId", event.getGuild().getIdLong())
					.append("winnersAmount", winners)
					.append("endAt", Clock.systemUTC().instant().getEpochSecond() + seconds)
					.append("duration", seconds)
					.append("item", item);
				
				event.getMongo().insertGiveaway(data).whenComplete((result, exception) -> {
					if (ExceptionUtility.sendExceptionally(event, exception)) {
						return;
					}
					
					event.getBot().getGiveawayManager().putGiveaway(data, seconds);
					
					event.reply("Your giveaway has been created in " + channel.getAsMention() + " :tada:").queue();
				});
			});
			
			return;
		}
		
		AtomicReference<TextChannel> atomicChannel = new AtomicReference<>();
		AtomicInteger atomicWinners = new AtomicInteger();
		AtomicReference<Duration> atomicDuration = new AtomicReference<>();
		AtomicReference<String> atomicItem = new AtomicReference<>();
		
		CompletableFuture.completedFuture(true).thenCompose($ -> {
			if (channel != null) {
				atomicChannel.set(channel);
				return CompletableFuture.completedFuture(true);
			}
			
			CompletableFuture<Boolean> future = new CompletableFuture<>();
			
			event.reply("What channel would you like to start the giveaway in? Type `cancel` at anytime to cancel the creation.").queue(message -> {
				Waiter<MessageReceivedEvent> waiter = new Waiter<>(event.getBot(), MessageReceivedEvent.class)
					.setUnique(event.getAuthor().getIdLong(), event.getChannel().getIdLong())
					.setCancelPredicate(e -> e.getMessage().getContentRaw().equalsIgnoreCase("cancel"))
					.setTimeout(30)
					.setPredicate(e -> {
						TextChannel textChannel = SearchUtility.getTextChannel(event.getGuild(), e.getMessage().getContentRaw());
						if (textChannel != null) {
							atomicChannel.set(textChannel);
							
							return true;
						}
						
						event.replyFailure("I could not find that channel").queue();
						
						return false;
					});
					
				waiter.onCancelled((type) -> {
					event.replySuccess("Cancelled").queue();
					future.complete(false);
				});
				
				waiter.onTimeout(() -> {
					event.reply("Response timed out :stopwatch:").queue();
					future.complete(false);
				});
				
				waiter.onSuccess(e -> future.complete(true));
				
				waiter.start();
			});
			
			return future;
		}).thenCompose(success -> {
			if (!success) {
				return CompletableFuture.completedFuture(false);
			}
			
			if (winners != 0) {
				atomicWinners.set(winners);
				return CompletableFuture.completedFuture(true);
			}
			
			CompletableFuture<Boolean> future = new CompletableFuture<>();
			
			event.reply("How many winners would you like the giveaway to have?").queue(message -> {
				Waiter<MessageReceivedEvent> waiter = new Waiter<>(event.getBot(), MessageReceivedEvent.class)
					.setCancelPredicate(e -> e.getMessage().getContentRaw().equalsIgnoreCase("cancel"))
					.setTimeout(30)
					.setUnique(event.getAuthor().getIdLong(), event.getChannel().getIdLong())
					.setPredicate(e -> {
						String content = e.getMessage().getContentRaw();
						if (NumberUtility.isNumberUnsigned(content)) {
							int number = Integer.parseInt(content);
							if (number < 1) {
								event.replyFailure("You have to have at least 1 winner").queue();
								
								return false;
							}
							
							atomicWinners.set(number);
							
							return true;
						}
						
						event.replyFailure("That is not a number").queue();
						
						return false;
					});
					
				waiter.onCancelled((type) -> {
					event.replySuccess("Cancelled").queue();
					future.complete(false);
				});
				
				waiter.onTimeout(() -> {
					event.reply("Response timed out :stopwatch:").queue();
					future.complete(false);
				});
				
				waiter.onSuccess(e -> future.complete(true));
				
				waiter.start();
			});
			
			return future;
		}).thenCompose(success -> {
			if (!success) {
				return CompletableFuture.completedFuture(false);
			}
			
			if (duration != null) {
				atomicDuration.set(duration);
				return CompletableFuture.completedFuture(true);
			}
			
			CompletableFuture<Boolean> future = new CompletableFuture<>();
			
			event.reply("How long would you like the giveaway to last?").queue(message -> {
				Waiter<MessageReceivedEvent> waiter = new Waiter<>(event.getBot(), MessageReceivedEvent.class)
					.setCancelPredicate(e -> e.getMessage().getContentRaw().equalsIgnoreCase("cancel"))
					.setTimeout(30)
					.setUnique(event.getAuthor().getIdLong(), event.getChannel().getIdLong())
					.setPredicate(e -> {
						Duration durationReply = TimeUtility.getDurationFromString(e.getMessage().getContentRaw());
						if (durationReply.toSeconds() < 1) {
							event.replyFailure("The duration of a giveaway cannot be less than 1 second").queue();

							return false;
						}

						atomicDuration.set(durationReply);

						return true;
					});
					
				waiter.onCancelled((type) -> {
					event.replySuccess("Cancelled").queue();
					future.complete(false);
				});
				
				waiter.onTimeout(() -> {
					event.reply("Response timed out :stopwatch:").queue();
					future.complete(false);
				});
				
				waiter.onSuccess(e -> future.complete(true));
				
				waiter.start();
			});
			
			return future;
		}).thenCompose(success -> {
			if (!success) {
				return CompletableFuture.completedFuture(false);
			}
			
			if (item != null) {
				atomicItem.set(item);
				return CompletableFuture.completedFuture(true);
			}
			
			CompletableFuture<Boolean> future = new CompletableFuture<>();
			
			event.reply("What would you like to giveaway?").queue(message -> {
				Waiter<MessageReceivedEvent> waiter = new Waiter<>(event.getBot(), MessageReceivedEvent.class)
					.setCancelPredicate(e -> e.getMessage().getContentRaw().equalsIgnoreCase("cancel"))
					.setTimeout(30)
					.setUnique(event.getAuthor().getIdLong(), event.getChannel().getIdLong())
					.setPredicate(e -> {
						String content = e.getMessage().getContentRaw();
						if (content.equalsIgnoreCase("cancel")) {
							return false;
						}
						
						atomicItem.set(content);
						
						return true;
					});
					
				waiter.onCancelled((type) -> {
					event.replySuccess("Cancelled").queue();
					future.complete(false);
				});
				
				waiter.onTimeout(() -> {
					event.reply("Response timed out :stopwatch:").queue();
					future.complete(false);
				});
				
				waiter.onSuccess(e -> future.complete(true));
				
				waiter.start();
			});
			
			return future;
		}).thenAccept(success -> {
			if (!success) {
				return;
			}
			
			TextChannel channelFuture = atomicChannel.get();
			int winnersFuture = atomicWinners.get();
			long durationFuture = atomicDuration.get().toSeconds();
			String itemFuture = atomicItem.get();
			
			channelFuture.sendMessageEmbeds(this.getEmbed(winnersFuture, durationFuture, itemFuture)).queue(message -> {
				message.addReaction("🎉").queue();
				
				Document data = new Document("messageId", message.getIdLong())
					.append("channelId", channelFuture.getIdLong())
					.append("guildId", event.getGuild().getIdLong())
					.append("winnersAmount", winnersFuture)
					.append("endAt", Clock.systemUTC().instant().getEpochSecond() + durationFuture)
					.append("duration", durationFuture)
					.append("item", itemFuture);
					
				event.getMongo().insertGiveaway(data).whenComplete((result, exception) -> {
					if (ExceptionUtility.sendExceptionally(event, exception)) {
						return;
					}
					
					event.getBot().getGiveawayManager().putGiveaway(data, durationFuture);
					
					event.reply("Your giveaway has been created in " + channelFuture.getAsMention() + " :tada:").queue();
				});
			});
		});
	}
	
	@Command(value="restart", description="Restarts a giveaway")
	@CommandId(48)
	@Examples({"giveaway restart 727224132202397726"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void restart(Sx4CommandEvent event, @Argument(value="message id") MessageArgument messageArgument, @Argument(value="duration", endless=true, nullDefault=true) Duration duration) {
		long timeNow = Clock.systemUTC().instant().getEpochSecond();
		
		List<Bson> update = List.of(
			Operators.set("endAt", Operators.cond(Operators.exists("$winners"), duration == null ? Operators.add(timeNow, "$duration") : duration.toSeconds() + timeNow, "$endAt")),
			Operators.set("duration", Operators.cond(Operators.exists("$winners"), duration == null ? "$duration" : duration.toSeconds(), "$duration")),
			Operators.unset("winners")
		);	
		
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.exclude("winners"));
		event.getMongo().findAndUpdateGiveawayById(messageArgument.getMessageId(), update, options).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}
			
			if (data == null) {
				event.replyFailure("There is no giveaway with that id").queue();
				return;
			}
			
			if (data.getLong("endAt") - timeNow > 0) {
				event.replyFailure("That giveaway has not ended yet").queue();
				return;
			}
			
			long seconds = duration == null ? data.getLong("duration") : duration.toSeconds();
			
			TextChannel channel = event.getGuild().getTextChannelById(data.getLong("channelId"));
			if (channel == null) {
				event.replyFailure("That giveaway no longer exists").queue();
				return;
			}
			
			channel.editMessageEmbedsById(data.getLong("messageId"), this.getEmbed(data.getInteger("winnersAmount"), seconds, data.getString("item"))).queue();
			
			event.getBot().getGiveawayManager().putGiveaway(data, seconds);
			
			event.replySuccess("That giveaway has been restarted").queue();
		});
	}
	
	@Command(value="reroll", aliases={"re roll"}, description="Rerolls the winners for an ended giveaway")
	@CommandId(49)
	@Examples({"giveaway reroll 727224132202397726"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void reroll(Sx4CommandEvent event, @Argument(value="message id") MessageArgument messageArgument) {
		Document data = event.getMongo().getGiveawayById(messageArgument.getMessageId());
		if (data == null) {
			event.replyFailure("There is no giveaway with that id").queue();
			return;
		}
		
		if (!data.containsKey("winners")) {
			event.replyFailure("That giveaway has not ended yet").queue();
			return;
		}
		
		event.getBot().getGiveawayManager().endGiveaway(data, true);
	}
	
	@Command(value="end", description="Ends an active giveaway early")
	@CommandId(50)
	@Examples({"giveaway end 727224132202397726"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void end(Sx4CommandEvent event, @Argument(value="message id") MessageArgument messageArgument) {
		Document data = event.getMongo().getGiveawayById(messageArgument.getMessageId());
		if (data == null) {
			event.replyFailure("There is no giveaway with that id").queue();
			return;
		}
		
		if (data.containsKey("winners")) {
			event.replyFailure("That giveaway has already ended").queue();
			return;
		}
		
		event.getBot().getGiveawayManager().endGiveaway(data, true);
	}
	
	@Command(value="delete", aliases={"remove"}, description="Deletes a giveaway")
	@CommandId(51)
	@Examples({"giveaway delete 727224132202397726", "giveaway delete all"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void delete(Sx4CommandEvent event, @Argument(value="message id | all") @AlternativeOptions("all") Alternative<MessageArgument> option) {
		if (option.isAlternative()) {
			List<Button> buttons = List.of(Button.success("yes", "Yes"), Button.danger("no", "No"));

			event.reply(event.getAuthor().getName() + ", are you sure you want to delete **all** giveaways in this server?").setActionRow(buttons).submit()
				.thenCompose(message -> {
					return new Waiter<>(event.getBot(), ButtonClickEvent.class)
						.setPredicate(e -> {
							Button button = e.getButton();
							return button != null && button.getId().equals("yes") && e.getMessageIdLong() == message.getIdLong() && e.getUser().getIdLong() == event.getAuthor().getIdLong();
						})
						.setCancelPredicate(e -> {
							Button button = e.getButton();
							return button != null && button.getId().equals("no") && e.getMessageIdLong() == message.getIdLong() && e.getUser().getIdLong() == event.getAuthor().getIdLong();
						})
						.setTimeout(60)
						.start();
				})
				.thenCompose(e -> event.getMongo().deleteManyGiveaways(Filters.eq("guildId", event.getGuild().getIdLong())))
				.whenComplete((result, exception) -> {
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
					
					if (result.getDeletedCount() == 0) {
						event.replyFailure("There are no giveaways in this server").queue();
						return;
					}
					
					event.replySuccess("All giveaways in this server have been deleted").queue();
				});
		} else {
			long messageId = option.getValue().getMessageId();
			event.getMongo().deleteGiveawayById(messageId).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}
				
				if (result.getDeletedCount() == 0) {
					event.replyFailure("There was no giveaway with that id").queue();
					return;
				}
				
				event.replySuccess("That giveaway has been deleted").queue();
			});
		}
	}
	
	@Command(value="list", description="Lists all the giveaways which have happened in the server")
	@CommandId(52)
	@Examples({"giveaway list"})
	public void list(Sx4CommandEvent event) {
		List<Document> giveaways = event.getMongo().getGiveaways(Filters.eq("guildId", event.getGuild().getIdLong())).into(new ArrayList<>());
		if (giveaways.isEmpty()) {
			event.replyFailure("No giveaways have been setup in this server").queue();
			return;
		}
		
		PagedResult<Document> paged = new PagedResult<>(event.getBot(), giveaways)
			.setAuthor("Giveaways", null, event.getGuild().getIconUrl())
			.setDisplayFunction(data -> {
				long endAt = data.getLong("endAt"), timeNow = Clock.systemUTC().instant().getEpochSecond();
				return data.getLong("messageId") + " - " + (endAt - timeNow < 0 ? "Ended" : TimeUtility.getTimeString(endAt - timeNow));
			});
		
		paged.onSelect(select -> {
			ShardManager shardManager = event.getShardManager();
			
			Document data = select.getSelected();
			
			List<Long> winners = data.getList("winners", Long.class, Collections.emptyList());
			String winnersString = winners.isEmpty() ? "None" : winners.stream()
				.map(shardManager::getUserById)
				.filter(Objects::nonNull)
				.map(User::getAsMention)
				.collect(Collectors.joining(", "));
			
			event.replyFormat("**Giveaway %d**\nItem: %s\nWinner%s: %s\nDuration: %s", data.getLong("messageId"), data.getString("item"), winners.size() == 1 ? "" : "s", winnersString, TimeUtility.getTimeString(data.getLong("duration"))).queue();
		});
		
		paged.execute(event);
	}
	
}
