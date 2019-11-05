package com.sx4.bot.modules;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import org.bson.Document;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.command.Command.AuthorPermissions;
import com.jockie.bot.core.command.Command.BotPermissions;
import com.jockie.bot.core.command.Context;
import com.jockie.bot.core.command.Initialize;
import com.jockie.bot.core.command.impl.CommandEvent;
import com.jockie.bot.core.command.impl.CommandImpl;
import com.jockie.bot.core.command.ICommand.ContentOverflowPolicy;
import com.jockie.bot.core.module.Module;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.sx4.bot.categories.Categories;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEventListener;
import com.sx4.bot.database.Database;
import com.sx4.bot.events.GiveawayEvents;
import com.sx4.bot.utils.ArgumentUtils;
import com.sx4.bot.utils.GiveawayUtils;
import com.sx4.bot.utils.HelpUtils;
import com.sx4.bot.utils.PagedUtils;
import com.sx4.bot.utils.TimeUtils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;

@Module
public class GiveawayModule {

	public class GiveawayCommand extends Sx4Command {
		
		Pattern winnerRegex = Pattern.compile("\\*\\*(.*)\\*\\* has won \\*\\*(.*)\\*\\*");
		
		public GiveawayCommand() {
			super("giveaway");
			
			super.setDescription("Set up giveaways in a certain channel which will be decided randomly through reactions");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="end", description="End a giveaway while it is active", contentOverflowPolicy=ContentOverflowPolicy.IGNORE) 
		@AuthorPermissions({Permission.MANAGE_ROLES})
		@BotPermissions({Permission.MESSAGE_HISTORY})
		public void end(CommandEvent event, @Context Database database, @Argument(value="giveaway id") int giveawayId) {
			List<Document> giveaways = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("giveaway.giveaways")).getEmbedded(List.of("giveaway", "giveaways"), Collections.emptyList());
			if (giveaways.isEmpty()) {
				event.reply("There are currently no active giveaways :no_entry:").queue();
				return;
			}
			
			for (Document giveaway : giveaways) {
				if (giveaway.getInteger("id") == giveawayId) {
					TextChannel channel = event.getGuild().getTextChannelById(giveaway.getLong("channelId"));
					if (channel == null) {
						event.reply("The channel where that giveaway was hosted has been deleted :no_entry:").queue();
						return;
					}
					
					channel.retrieveMessageById(giveaway.getLong("messageId")).queue(message -> {
						for (MessageReaction reaction : message.getReactions()) {
							if (reaction.getReactionEmote().getName().equals("🎉")) {
								List<Member> members = new ArrayList<>();
								CompletableFuture<?> future = reaction.retrieveUsers().forEachAsync(user -> {
									Member reactionMember = event.getGuild().getMember(user);
									if (reactionMember != null && !members.contains(reactionMember) && reactionMember != event.getSelfMember()) {
										members.add(reactionMember);
									}
									
									return true;
								});
									
								future.thenRun(() -> {
									if (members.size() == 0) {
										channel.sendMessage("No one entered the giveaway, the giveaway has been deleted anyway :no_entry:").queue();
										message.delete().queue(null, e -> {});
									} else {
										Set<Member> winners = GiveawayUtils.getRandomSample(members, Math.min(members.size(), giveaway.getInteger("winnersAmount")));
										List<String> winnerMentions = new ArrayList<>(), winnerTags = new ArrayList<>();
										for (Member winner : winners) {
											winnerMentions.add(winner.getAsMention());
											winnerTags.add(winner.getUser().getAsTag());
										}
										
										channel.sendMessage(String.join(", ", winnerMentions) + ", Congratulations you have won the giveaway for **" + giveaway.getString("item") + "**").queue();
										
										EmbedBuilder embed = new EmbedBuilder();
										embed.setTitle("Giveaway");
										embed.setDescription("**" + String.join(", ", winnerTags) + "** has won **" + giveaway.getString("item") + "**");
										embed.setFooter("Giveaway Ended", null);
										message.editMessage(embed.build()).queue();
									}
									
									database.updateGuildById(event.getGuild().getIdLong(), Updates.pull("giveaway.giveaways", Filters.eq("id", giveawayId)), (result, exception) -> {
										if (exception != null) {
											exception.printStackTrace();
											event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
										} else {
											GiveawayEvents.cancelExecutor(event.getGuild().getIdLong(), giveawayId);
										}
									});
								});
							}
						}
					}, e -> {
						if (e instanceof ErrorResponseException) {
							ErrorResponseException exception = (ErrorResponseException) e;
							if (exception.getErrorCode() == 10008) {
								database.updateGuildById(event.getGuild().getIdLong(), Updates.pull("giveaway.giveaways", Filters.eq("id", giveawayId)), (removeResult, removeException) -> {
									if (removeException != null) {
										removeException.printStackTrace();
										event.reply(Sx4CommandEventListener.getUserErrorMessage(removeException)).queue();
									} else {
										event.reply("That giveaway message has been deleted :no_entry:").queue();
										GiveawayEvents.cancelExecutor(event.getGuild().getIdLong(), giveawayId);
									}
								});
							}
						}
					});
					
					return;
				}
			}
			
			event.reply("I could not find that giveaway :no_entry:").queue();
		}
		
		
		@Command(value="reroll", description="Reroll a giveaway which has ended", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@AuthorPermissions({Permission.MANAGE_ROLES})
		@BotPermissions({Permission.MESSAGE_HISTORY})
		public void reroll(CommandEvent event, @Argument(value="message id") long messageId, @Argument(value="winners", nullDefault=true) Integer winnersAmountArgument) {
			int winnersAmount = winnersAmountArgument == null ? 1 : winnersAmountArgument;
			
			event.getTextChannel().retrieveMessageById(messageId).queue(message -> {
				if (!message.getAuthor().equals(event.getSelfUser())) {
					event.reply("That message is not a giveaway message :no_entry:").queue();
					return;
				}
				
				if (message.getEmbeds().isEmpty()) {
					event.reply("That message is not a giveaway message :no_entry:").queue();
					return;
				}
				
				MessageEmbed embed = message.getEmbeds().get(0);	
				if (embed.getFooter() == null) {
					event.reply("That message is not a giveaway message :no_entry:").queue();
					return;
				} else {
					if (embed.getFooter().getText() == null) {
						event.reply("That message is not a giveaway message :no_entry:").queue();
						return;
					} else {
						if (embed.getFooter().getText().equals("Giveaway Ended")) {
							if (embed.getDescription() == null) {
								event.reply("That message is not a giveaway message :no_entry:").queue();
								return;
							} else {
								if (winnerRegex.matcher(embed.getDescription()).matches()) {
									String[] winners = embed.getDescription().split("\\*\\*")[1].split(", ");
									Set<Member> members = new HashSet<>();
									for (String winner : winners) {
										Member member = event.getGuild().getMembersByName(winner.split("#")[0], false).stream().filter(it -> it.getUser().getDiscriminator().equals(winner.split("#")[1])).findFirst().orElse(null);
										members.add(member);
									}
										
									List<Member> possibleWinners = new ArrayList<>();
									for (MessageReaction reaction : message.getReactions()) {
										if (reaction.getReactionEmote().getName().equals("🎉")) {
											CompletableFuture<?> future = reaction.retrieveUsers().forEachAsync((user) -> {
												Member reactionMember = event.getGuild().getMember(user);
												if (!members.contains(reactionMember) && reactionMember != event.getSelfMember()) {
													possibleWinners.add(reactionMember);
												}
												
												return true;
											});

											future.thenRun(() -> {
												if (possibleWinners.size() == 0) {
													event.reply("No one has reacted to that giveaway :no_entry:").queue();
													return;
												}
												
												Set<Member> actualWinners = GiveawayUtils.getRandomSample(possibleWinners, Math.min(winnersAmount, possibleWinners.size()));
												List<String> winnerMentions = new ArrayList<>();
												for (Member member : actualWinners) {
													winnerMentions.add(member.getAsMention());
												}
												
												event.reply("The new " + (actualWinners.size() == 1 ? "winner is" : "winners are") + " " + String.join(", ", winnerMentions) + ", congratulations :tada:").queue();
											});
										}
									}
								} else {
									event.reply("That message is not a giveaway message :no_entry:").queue();
									return;
								}
							}
						} else {
							event.reply("That giveaway has not ended yet :no_entry:").queue();
							return;
						}
					}
				}
			}, e -> {
				if (e instanceof ErrorResponseException) {
					ErrorResponseException exception = (ErrorResponseException) e;
					if (exception.getErrorCode() == 10008) {
						event.reply("I could not find that message within this channel :no_entry:").queue();
					}
				}
			});
		}
		
		@Command(value="delete", description="Delete a specific giveaway which is currently running with its giveaway id", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@AuthorPermissions({Permission.MANAGE_ROLES})
		@BotPermissions({Permission.MESSAGE_HISTORY})
		public void delete(CommandEvent event, @Context Database database, @Argument(value="giveaway id") int giveawayId) {
			List<Document> giveaways = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("giveaway.giveaways")).getEmbedded(List.of("giveaway", "giveaways"), Collections.emptyList());
			if (giveaways.isEmpty()) {
				event.reply("There are currently no active giveaways :no_entry:").queue();
				return;
			}
			
			for (Document giveaway : giveaways) {
				if (giveaway.getInteger("id") == giveawayId) {
					long channelId = giveaway.getLong("channelId");
					TextChannel channel = event.getGuild().getTextChannelById(channelId);
					if (channel != null) {
						channel.retrieveMessageById(giveaway.getLong("messageId")).queue(message -> message.delete().queue(null, e -> {}), e -> {});
					}
					
					database.updateGuildById(event.getGuild().getIdLong(), Updates.pull("giveaway.giveaways", Filters.eq("id", giveawayId)), (result, exception) -> {
						if (exception != null) {
							exception.printStackTrace();
							event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
						} else {
							event.reply("That giveaway has been deleted <:done:403285928233402378>").queue();
							GiveawayEvents.cancelExecutor(event.getGuild().getIdLong(), giveawayId);
						}
					});

					return;
				}
			}
			
			event.reply("I could not find that giveaway :no_entry:").queue();
		}
		
		@Command(value="setup", description="Set up a giveaway in the current server")
		@AuthorPermissions({Permission.MANAGE_ROLES})
		@BotPermissions({Permission.MESSAGE_HISTORY, Permission.MESSAGE_EMBED_LINKS, Permission.MESSAGE_ADD_REACTION})
		public void setup(CommandEvent event, @Context Database database, @Argument(value="channel", nullDefault=true) String channelArgument, @Argument(value="winners", nullDefault=true) Integer winnersAmount, @Argument(value="duration", nullDefault=true) String duration, @Argument(value="item", endless=true, nullDefault=true) String giveawayItem) {
			int id = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("giveaway.giveawayAmount")).getEmbedded(List.of("giveaway", "giveawayAmount"), 0) + 1;
			
			AtomicReference<String> channelString = new AtomicReference<>(), durationString = new AtomicReference<>(), itemString = new AtomicReference<>();
			AtomicReference<Integer> winnersTotal = new AtomicReference<>();
			if (channelArgument != null && winnersAmount != null && duration != null && giveawayItem != null) {
				TextChannel channel = ArgumentUtils.getTextChannel(event.getGuild(), channelArgument);
				if (channel == null) {
					event.reply("I could not find that channel :no_entry:").queue();
					return;
				}
				
				long durationLength = TimeUtils.convertToSeconds(duration);
				if (durationLength <= 0) {
					event.reply("Invalid time format, make sure it's formatted with a numerical value then a letter representing the time (d for days, h for hours, m for minutes, s for seconds) and make sure it's in order :no_entry:").queue();
					return;
				}
				
				long endTime = Clock.systemUTC().instant().getEpochSecond() + durationLength;
				
				EmbedBuilder embed = new EmbedBuilder();
				embed.setTitle("Giveaway");
				embed.setDescription("Enter by reacting with :tada:\n\nThis giveaway is for **" + giveawayItem + "**\nDuration: **" + TimeUtils.toTimeString(durationLength, ChronoUnit.SECONDS) + "**\nWinners: **" + winnersAmount + "**");
				embed.setTimestamp(Instant.ofEpochSecond(endTime));
				embed.setFooter("Ends", null);
				channel.sendMessage(embed.build()).queue(message -> {
					message.addReaction("🎉").queue();
					
					Document giveaway = new Document().append("id", id)
							.append("messageId", message.getIdLong())
							.append("endTimestamp", endTime)
							.append("duration", durationLength)
							.append("item", giveawayItem)
							.append("channelId", channel.getIdLong())
							.append("winnersAmount", winnersAmount);
					
					database.updateGuildById(event.getGuild().getIdLong(), Updates.combine(Updates.inc("giveaway.giveawayAmount", 1), Updates.push("giveaway.giveaways", giveaway)), (result, exception) -> {
						if (exception != null) {
							exception.printStackTrace();
							event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
						} else {
							event.reply("Your giveaway has been created in " + channel.getAsMention() + " :tada:\nGiveaway ID: `" + id + "`").queue();
							
							ScheduledFuture<?> executor = GiveawayEvents.scheduledExectuor.schedule(() -> GiveawayEvents.removeGiveaway(event.getGuild().getIdLong(), giveaway), durationLength, TimeUnit.SECONDS);
							GiveawayEvents.putExecutor(event.getGuild().getIdLong(), id, executor);
						}
					});
				});
			} else {
				CompletableFuture.completedFuture(true).thenCompose(($) -> {
					CompletableFuture<Boolean> future = new CompletableFuture<>();
					
					if (channelArgument == null) {
						event.reply("What channel would you like me to start this giveaway in? Type \"cancel\" at anytime to cancel the creation (Respond below)").queue(message -> {
							PagedUtils.getResponse(event, 60, (e) -> {
								if (e.getTextChannel().equals(event.getTextChannel()) && e.getAuthor().equals(event.getAuthor())) {
									if (e.getMessage().getContentRaw().toLowerCase().equals("cancel")) {
										return true;
									} else {
										TextChannel providedChannel = ArgumentUtils.getTextChannel(event.getGuild(), e.getMessage().getContentRaw());
										if (providedChannel != null) {
											return true;
										}
									}
								}
								
								return false;
							}, () -> event.reply("Response timed out :stopwatch:").queue(a -> future.complete(false)), channelMessage -> {
								if (channelMessage.getContentRaw().toLowerCase().equals("cancel")) {
									event.reply("Cancelled <:done:403285928233402378>").queue(a -> future.complete(false));
								} else {
									channelString.set(channelMessage.getContentRaw());
									future.complete(true);
								}
							});	   					   
						});
					} else {
						channelString.set(channelArgument);
						future.complete(true);
					}
					
					return future;
				}).thenCompose((success) -> {
					if (!success) {
						return CompletableFuture.completedFuture(success);
					}
					
					CompletableFuture<Boolean> future = new CompletableFuture<>();
					
					if (winnersAmount == null) {
						event.reply("How many winners would you like? (Respond below)").queue(message -> {
							PagedUtils.getResponse(event, 60, (e) -> {
								if (e.getTextChannel().equals(event.getTextChannel()) && e.getAuthor().equals(event.getAuthor())) {
									if (e.getMessage().getContentRaw().toLowerCase().equals("cancel")) {
										return true;
									} else {
										try {
											Integer.parseInt(e.getMessage().getContentRaw());
											return true;
										} catch(NumberFormatException ex) {
											return false;
										}
									}
								}
								
								return false;
							}, () -> event.reply("Response timed out :stopwatch:").queue(a -> future.complete(false)), winnersMessage -> {
								if (winnersMessage.getContentRaw().toLowerCase().equals("cancel")) {
									event.reply("Cancelled <:done:403285928233402378>").queue(a -> future.complete(false));
								} else {
									winnersTotal.set(Integer.parseInt(winnersMessage.getContentRaw()));
									future.complete(true);
								}
							});	   					   
						});
					} else {
						winnersTotal.set(winnersAmount);
						future.complete(true);
					}
					
					return future;
				}).thenCompose((success) -> {
					if (!success) {
						return CompletableFuture.completedFuture(success);
					}
					
					CompletableFuture<Boolean> future = new CompletableFuture<>();
					
					if (duration == null) {
						event.reply("How long do you want your giveaway to last? (After the numerical value add 'd' for days, 'h' for hours, 'm' for minutes, 's' for seconds), Respond below.").queue(message -> {
							PagedUtils.getResponse(event, 60, (e) -> {
								if (e.getTextChannel().equals(event.getTextChannel()) && e.getAuthor().equals(event.getAuthor())) {
									if (e.getMessage().getContentRaw().toLowerCase().equals("cancel")) {
										return true;
									} else {
										if (TimeUtils.convertToSeconds(e.getMessage().getContentRaw()) > 0) {
											return true;
										} else {
											return false;
										}
									}
								}
								
								return false;
							}, () -> event.reply("Response timed out :stopwatch:").queue(a -> future.complete(false)), durationMessage -> {
								if (durationMessage.getContentRaw().toLowerCase().equals("cancel")) {
									event.reply("Cancelled <:done:403285928233402378>").queue(a -> future.complete(false));
								} else {
									durationString.set(durationMessage.getContentRaw());
									future.complete(true);
								}
							});	   					   
						});
					} else {
						durationString.set(duration);
						future.complete(true);
					}
					
					return future;
				}).thenCompose((success) -> {
					if (!success) {
						return CompletableFuture.completedFuture(success);
					}
					
					CompletableFuture<Boolean> future = new CompletableFuture<>();
					
					if (giveawayItem == null) {
						event.reply("What are you giving away? (Respond below)").queue(message -> {
							PagedUtils.getResponse(event, 60, (e) -> {
								return e.getTextChannel().equals(event.getTextChannel()) && e.getAuthor().equals(event.getAuthor());
							}, () -> event.reply("Response timed out :stopwatch:").queue(a -> future.complete(false)), durationMessage -> {
								if (durationMessage.getContentRaw().toLowerCase().equals("cancel")) {
									event.reply("Cancelled <:done:403285928233402378>").queue(a -> future.complete(false));
								} else {
									itemString.set(durationMessage.getContentRaw());
									future.complete(true);
								}
							});	   					   
						});
					} else {
						itemString.set(giveawayItem);
						future.complete(true);
					}
					
					return future;
				}).thenAccept((success) -> {
					TextChannel channel = ArgumentUtils.getTextChannel(event.getGuild(), channelString.get());
					if (channel == null) {
						event.reply("I could not find that channel :no_entry:").queue();
						return;
					}
					
					long durationLength = TimeUtils.convertToSeconds(durationString.get());
					if (durationLength <= 0) {
						event.reply("Invalid time format, make sure it's formatted with a numerical value then a letter representing the time (d for days, h for hours, m for minutes, s for seconds) and make sure it's in order :no_entry:").queue();
						return;
					}
					
					long endTime = Clock.systemUTC().instant().getEpochSecond() + durationLength;
					
					EmbedBuilder embed = new EmbedBuilder();
					embed.setTitle("Giveaway");
					embed.setDescription("Enter by reacting with :tada:\n\nThis giveaway is for **" + itemString.get() + "**\nDuration: **" + TimeUtils.toTimeString(durationLength, ChronoUnit.SECONDS) + "**\nWinners: **" + winnersTotal.get() + "**");
					embed.setTimestamp(Instant.ofEpochSecond(endTime));
					embed.setFooter("Ends", null);
					channel.sendMessage(embed.build()).queue(message -> {
						message.addReaction("🎉").queue();
						
						Document giveaway = new Document().append("id", id)
								.append("messageId", message.getIdLong())
								.append("endTimestamp", endTime)
								.append("duration", durationLength)
								.append("item", itemString.get())
								.append("channelId", channel.getIdLong())
								.append("winnersAmount", winnersTotal.get());
						
						database.updateGuildById(event.getGuild().getIdLong(), Updates.combine(Updates.inc("giveaway.giveawayAmount", 1), Updates.push("giveaway.giveaways", giveaway)), (result, exception) -> {
							if (exception != null) {
								exception.printStackTrace();
								event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
							} else {
								event.reply("Your giveaway has been created in " + channel.getAsMention() + " :tada:\nGiveaway ID: `" + id + "`").queue();
								
								ScheduledFuture<?> executor = GiveawayEvents.scheduledExectuor.schedule(() -> GiveawayEvents.removeGiveaway(event.getGuild().getIdLong(), giveaway), durationLength, TimeUnit.SECONDS);
								GiveawayEvents.putExecutor(event.getGuild().getIdLong(), id, executor);
							}
						});
					});
				});
			}
		}
		
	}
	
	@Initialize(all=true, subCommands=true, recursive=true)
	public void initialize(CommandImpl command) {
		command.setCategory(Categories.GIVEAWAY);
	}
	
}
