package com.sx4.bot.commands.management;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReturnDocument;
import com.sx4.bot.annotations.argument.DefaultInt;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.annotations.command.AuthorPermissions;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.Category;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.model.Operators;
import com.sx4.bot.entities.argument.All;
import com.sx4.bot.entities.argument.MessageArgument;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.NumberUtility;
import com.sx4.bot.utility.SearchUtility;
import com.sx4.bot.utility.TimeUtility;
import com.sx4.bot.waiter.Waiter;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.sharding.ShardManager;

public class GiveawayCommand extends Sx4Command {

	public GiveawayCommand() {
		super("giveaway");
		
		super.setDescription("Setup giveaways in a certain channel which will be decided randomly through reactions");
		super.setExamples("giveaway setup", "giveaway reroll", "giveaway reaction");
		super.setCategory(Category.MANAGEMENT);
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
	@Examples({"giveaway setup", "giveaway setup #giveaways 1 7d $10 Nitro"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void setup(Sx4CommandEvent event, @Argument(value="channel", nullDefault=true) TextChannel channel, @Argument(value="winners") @DefaultInt(-1) @Limit(min=1) int winners, @Argument(value="duration", nullDefault=true) Duration duration, @Argument(value="item", nullDefault=true, endless=true) String item) {
		if (channel != null && winners != -1 && duration != null && item != null) {
			long seconds = duration.toSeconds();
			
			channel.sendMessage(this.getEmbed(winners, seconds, item)).queue(message -> {
				message.addReaction("🎉").queue();
				
				Document data = new Document("_id", message.getIdLong())
					.append("channelId", channel.getIdLong())
					.append("guildId", event.getGuild().getIdLong())
					.append("winnersAmount", winners)
					.append("endAt", Clock.systemUTC().instant().getEpochSecond() + seconds)
					.append("duration", seconds)
					.append("item", item);
				
				this.database.insertGiveaway(data).whenComplete((result, exception) -> {
					if (ExceptionUtility.sendExceptionally(event, exception)) {
						return;
					}
					
					this.giveawayManager.putGiveaway(data, seconds);
					
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
				Waiter<GuildMessageReceivedEvent> waiter = new Waiter<>(GuildMessageReceivedEvent.class)
					.setCancelPredicate(e -> e.getMessage().getContentRaw().equalsIgnoreCase("cancel"))
					.setTimeout(30)
					.setUnique(event.getAuthor().getIdLong(), event.getChannel().getIdLong())
					.setPredicate(e -> {
						TextChannel textChannel = SearchUtility.getTextChannel(event.getGuild(), e.getMessage().getContentRaw());
						if (textChannel != null) {
							atomicChannel.set(textChannel);
							
							return true;
						}
						
						event.reply("I could not find that channel " + this.config.getFailureEmote()).queue();
						
						return false;
					});
					
				waiter.onCancelled((type) -> {
					event.reply("Cancelled " + this.config.getSuccessEmote()).queue();
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
				atomicWinners.set(winners);
				return CompletableFuture.completedFuture(false);
			}
			
			if (winners != -1) {
				return CompletableFuture.completedFuture(true);
			}
			
			CompletableFuture<Boolean> future = new CompletableFuture<>();
			
			event.reply("How many winners would you like the giveaway to have?").queue(message -> {
				Waiter<GuildMessageReceivedEvent> waiter = new Waiter<>(GuildMessageReceivedEvent.class)
					.setCancelPredicate(e -> e.getMessage().getContentRaw().equalsIgnoreCase("cancel"))
					.setTimeout(30)
					.setUnique(event.getAuthor().getIdLong(), event.getChannel().getIdLong())
					.setPredicate(e -> {
						String content = e.getMessage().getContentRaw();
						if (NumberUtility.isNumberUnsigned(content)) {
							int number = Integer.parseInt(content);
							if (number < 1) {
								event.reply("You have to have at least 1 winner :no_entry:").queue();
								
								return false;
							}
							
							atomicWinners.set(number);
							
							return true;
						}
						
						event.reply("That is not a number " + this.config.getFailureEmote()).queue();
						
						return false;
					});
					
				waiter.onCancelled((type) -> {
					event.reply("Cancelled " + this.config.getSuccessEmote()).queue();
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
				Waiter<GuildMessageReceivedEvent> waiter = new Waiter<>(GuildMessageReceivedEvent.class)
					.setCancelPredicate(e -> e.getMessage().getContentRaw().equalsIgnoreCase("cancel"))
					.setTimeout(30)
					.setUnique(event.getAuthor().getIdLong(), event.getChannel().getIdLong())
					.setPredicate(e -> {
						Duration durationReply = TimeUtility.getDurationFromString(e.getMessage().getContentRaw());
						if (durationReply != null) {
							atomicDuration.set(durationReply);
							
							return true;
						}
						
						event.reply("That is not a valid duration " + this.config.getFailureEmote()).queue();
						
						return false;
					});
					
				waiter.onCancelled((type) -> {
					event.reply("Cancelled " + this.config.getSuccessEmote()).queue();
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
				Waiter<GuildMessageReceivedEvent> waiter = new Waiter<>(GuildMessageReceivedEvent.class)
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
					event.reply("Cancelled " + this.config.getSuccessEmote()).queue();
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
			
			channelFuture.sendMessage(this.getEmbed(winnersFuture, durationFuture, itemFuture)).queue(message -> {
				message.addReaction("🎉").queue();
				
				Document data = new Document("_id", message.getIdLong())
					.append("channelId", channelFuture.getIdLong())
					.append("guildId", event.getGuild().getIdLong())
					.append("winnersAmount", winnersFuture)
					.append("endAt", Clock.systemUTC().instant().getEpochSecond() + durationFuture)
					.append("duration", durationFuture)
					.append("item", itemFuture);
					
				this.database.insertGiveaway(data).whenComplete((result, exception) -> {
					if (ExceptionUtility.sendExceptionally(event, exception)) {
						return;
					}
					
					this.giveawayManager.putGiveaway(data, durationFuture);
					
					event.reply("Your giveaway has been created in " + channelFuture.getAsMention() + " :tada:").queue();
				});
			});
		});
	}
	
	@Command(value="restart", description="Restarts a giveaway")
	@Examples({"giveaway restart 727224132202397726"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void restart(Sx4CommandEvent event, @Argument(value="message id") MessageArgument messageArgument, @Argument(value="duration", endless=true, nullDefault=true) Duration duration) {
		long timeNow = Clock.systemUTC().instant().getEpochSecond();
		
		List<Bson> update = List.of(
			Operators.set("endAt", Operators.cond(Operators.exists("$winners"), duration == null ? Operators.add(timeNow, "$duration") : duration.toSeconds() + timeNow, "$endAt")),
			Operators.set("duration", Operators.cond(Operators.exists("$winners"), duration == null ? "$duration" : duration.toSeconds(), "$duration")),
			Operators.set("winners", Operators.REMOVE)
		);	
		
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.exclude("winners"));
		this.database.findAndUpdateGiveawayById(messageArgument.getMessageId(), update, options).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}
			
			if (data == null) {
				event.reply("There is no giveaway with that id " + this.config.getFailureEmote()).queue();
				return;
			}
			
			if (data.get("endAt", 0L) - timeNow > 0) {
				event.reply("That giveaway has not ended yet " + this.config.getFailureEmote()).queue();
				return;
			}
			
			long seconds = duration == null ? data.get("duration", 0L) : duration.toSeconds();
			
			TextChannel channel = event.getGuild().getTextChannelById(data.get("channelId", 0L));
			if (channel == null) {
				event.reply("That giveaway no longer exists " + this.config.getFailureEmote()).queue();
				return;
			}
			
			channel.editMessageById(data.get("_id", 0L), this.getEmbed(data.get("winnersAmount", 0), seconds, data.getString("item"))).queue();
			
			this.giveawayManager.putGiveaway(data, seconds);
			
			event.reply("That giveaway has been restarted " + this.config.getSuccessEmote()).queue();
		});
	}
	
	@Command(value="reroll", aliases={"re roll"}, description="Rerolls the winners for an ended giveaway")
	@Examples({"giveaway reroll 727224132202397726"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void reroll(Sx4CommandEvent event, @Argument(value="message id") MessageArgument messageArgument) {
		Document data = this.database.getGiveawayById(messageArgument.getMessageId());
		if (data == null) {
			event.reply("There is no giveaway with that id " + this.config.getFailureEmote()).queue();
			return;
		}
		
		if (!data.containsKey("winners")) {
			event.reply("That giveaway has not ended yet " + this.config.getFailureEmote()).queue();
			return;
		}
		
		this.giveawayManager.endGiveaway(data);
	}
	
	@Command(value="delete", description="Deletes a giveaway")
	@Examples({"giveaway delete 727224132202397726", "giveaway delete all"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void delete(Sx4CommandEvent event, @Argument(value="message id | all") All<MessageArgument> all) {
		if (all.isAll()) {
			event.reply(event.getAuthor().getName() + ", are you sure you want to delete **all** giveaways in this server? (Yes or No)").queue(message -> {
				Waiter<GuildMessageReceivedEvent> waiter = new Waiter<>(GuildMessageReceivedEvent.class)
					.setPredicate(e -> e.getMessage().getContentRaw().equalsIgnoreCase("yes"))
					.setOppositeCancelPredicate()
					.setUnique(event.getAuthor().getIdLong(), event.getChannel().getIdLong())
					.setTimeout(30);
				
				waiter.onTimeout(() -> event.reply("Response timed out :stopwatch:"));
				
				waiter.onCancelled(type -> event.reply("Cancelled " + this.config.getSuccessEmote()));
				
				waiter.future()
					.thenCompose(e -> this.database.deleteManyGiveaways(Filters.eq("guildId", event.getGuild().getIdLong())))
					.whenComplete((result, exception) -> {
						if (ExceptionUtility.sendExceptionally(event, exception)) {
							return;
						}
						
						if (result.getDeletedCount() == 0) {
							event.reply("There are no giveaways in this server " + this.config.getFailureEmote()).queue();
							return;
						}
						
						event.reply("All giveaways in this server have been deleted " + this.config.getSuccessEmote()).queue();
					});
				
				waiter.start();
			});
		} else {
			long messageId = all.getValue().getMessageId();
			this.database.deleteGiveawayById(messageId).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}
				
				if (result.getDeletedCount() == 0) {
					event.reply("There was no giveaway with that id " + this.config.getFailureEmote()).queue();
					return;
				}
				
				event.reply("That giveaway has been deleted " + this.config.getSuccessEmote()).queue();
			});
		}
	}
	
	@Command(value="list", description="Lists all the giveaways which have happened in the server")
	@Examples({"giveaway list"})
	public void list(Sx4CommandEvent event) {
		List<Document> giveaways = this.database.getGiveaways(Filters.eq("guildId", event.getGuild().getIdLong())).into(new ArrayList<>());
		if (giveaways.isEmpty()) {
			event.reply("No giveaways have been setup in this server " + this.config.getFailureEmote()).queue();
			return;
		}
		
		PagedResult<Document> paged = new PagedResult<>(giveaways)
			.setAuthor("Giveaways", null, event.getGuild().getIconUrl())
			.setDisplayFunction(data -> {
				long endAt = data.get("endAt", 0L), timeNow = Clock.systemUTC().instant().getEpochSecond();
				if (endAt - timeNow < 0) {
					return data.get("_id", 0L) + " - Ended";
				} else {
					return data.get("_id", 0L) + " - " + TimeUtility.getTimeString(endAt - timeNow);
				}
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
			
			event.replyFormat("**Giveaway %d**\nItem: %s\nWinner%s: %s\nDuration: %s", data.get("_id", 0L), data.getString("item"), winners.size() == 1 ? "" : "s", winnersString, TimeUtility.getTimeString(data.get("duration", 0L))).queue();
		});
		
		paged.execute(event);
	}
	
}
