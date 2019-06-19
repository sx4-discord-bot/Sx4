package com.sx4.modules;

import static com.rethinkdb.RethinkDB.r;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

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
import com.rethinkdb.gen.ast.Get;
import com.rethinkdb.model.OptArgs;
import com.rethinkdb.net.Connection;
import com.sx4.categories.Categories;
import com.sx4.core.Sx4Command;
import com.sx4.events.GiveawayEvents;
import com.sx4.utils.ArgumentUtils;
import com.sx4.utils.GiveawayUtils;
import com.sx4.utils.HelpUtils;
import com.sx4.utils.PagedUtils;
import com.sx4.utils.TimeUtils;

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
		
		@SuppressWarnings("unchecked")
		@Command(value="end", description="End a giveaway while it is active", contentOverflowPolicy=ContentOverflowPolicy.IGNORE) 
		@AuthorPermissions({Permission.MANAGE_ROLES})
		@BotPermissions({Permission.MESSAGE_HISTORY})
		public void end(CommandEvent event, @Context Connection connection, @Argument(value="giveaway id") int giveawayId) {
			Get data = r.table("giveaway").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if (dataRan == null) {
				event.reply("There are currently no active giveaways :no_entry:").queue();
				return;
			}
			
			List<Map<String, Object>> giveaways = (List<Map<String, Object>>) dataRan.get("giveaways");
			if (giveaways.isEmpty()) {
				event.reply("There are currently no active giveaways :no_entry:").queue();
				return;
			}
			
			for (Map<String, Object> giveaway : giveaways) {
				if ((long) giveaway.get("id") == giveawayId) {
					TextChannel channel = event.getGuild().getTextChannelById((String) giveaway.get("channel"));
					if (channel == null) {
						event.reply("The channel where that giveaway was hosted has been deleted :no_entry:").queue();
						return;
					}
					
					channel.retrieveMessageById((String) giveaway.get("message")).queue(message -> {
						for (MessageReaction reaction : message.getReactions()) {
							if (reaction.getReactionEmote().getName().equals("🎉")) {
								List<Member> members = new ArrayList<>();
								CompletableFuture<?> future = reaction.retrieveUsers().forEachAsync((user) -> {
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
										Set<Member> winners = GiveawayUtils.getRandomSample(members, Math.min(members.size(), Math.toIntExact((long) giveaway.get("winners"))));
										List<String> winnerMentions = new ArrayList<>(), winnerTags = new ArrayList<>();
										for (Member winner : winners) {
											winnerMentions.add(winner.getAsMention());
											winnerTags.add(winner.getUser().getAsTag());
										}
										
										channel.sendMessage(String.join(", ", winnerMentions) + ", Congratulations you have won the giveaway for **" + ((String) giveaway.get("item")) + "**").queue();
										
										EmbedBuilder embed = new EmbedBuilder();
										embed.setTitle("Giveaway");
										embed.setDescription("**" + String.join(", ", winnerTags) + "** has won **" + ((String) giveaway.get("item")) + "**");
										embed.setFooter("Giveaway Ended", null);
										message.editMessage(embed.build()).queue();
									}
									
									data.update(row -> r.hashMap("giveaways", row.g("giveaways").filter(d -> d.g("id").ne(giveawayId)))).runNoReply(connection);
									GiveawayEvents.cancelExecutor(event.getGuild().getId(), giveawayId);
								});
							}
						}
					}, e -> {
						if (e instanceof ErrorResponseException) {
							ErrorResponseException exception = (ErrorResponseException) e;
							if (exception.getErrorCode() == 10008) {
								event.reply("That giveaway message has been deleted :no_entry:").queue();
								data.update(row -> r.hashMap("giveaways", row.g("giveaways").filter(d -> d.g("id").ne(giveawayId)))).runNoReply(connection);
								GiveawayEvents.cancelExecutor(event.getGuild().getId(), giveawayId);
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
		public void reroll(CommandEvent event, @Argument(value="message id") String messageId, @Argument(value="winners", nullDefault=true) Integer winnersAmountArgument) {
			int winnersAmount = winnersAmountArgument == null ? 1 : winnersAmountArgument;
			
			//very hacky
			try {
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
			} catch(IllegalArgumentException e) {
				event.reply("I could not find that message within this channel :no_entry:").queue();
			}
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="delete", description="Delete a specific giveaway which is currently running with its giveaway id", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@AuthorPermissions({Permission.MANAGE_ROLES})
		@BotPermissions({Permission.MESSAGE_HISTORY})
		public void delete(CommandEvent event, @Context Connection connection, @Argument(value="giveaway id") int giveawayId) {
			Get data = r.table("giveaway").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if (dataRan == null) {
				event.reply("There are currently no active giveaways :no_entry:").queue();
				return;
			}
			
			List<Map<String, Object>> giveaways = (List<Map<String, Object>>) dataRan.get("giveaways");
			if (giveaways.isEmpty()) {
				event.reply("There are currently no active giveaways :no_entry:").queue();
				return;
			}
			
			for (Map<String, Object> giveaway : giveaways) {
				if ((long) giveaway.get("id") == giveawayId) {
					event.reply("That giveaway has been deleted <:done:403285928233402378>").queue();
					
					String channelData = (String) giveaway.get("channel");
					TextChannel channel = event.getGuild().getTextChannelById(channelData);
					if (channel != null) {
						channel.retrieveMessageById((String) giveaway.get("message")).queue(message -> message.delete().queue(null, e -> {}), e -> {});
					}
					
					data.update(row -> r.hashMap("giveaways", row.g("giveaways").filter(d -> d.g("id").ne(giveawayId)))).runNoReply(connection);
					GiveawayEvents.cancelExecutor(event.getGuild().getId(), giveawayId);
					return;
				}
			}
			
			event.reply("I could not find that giveaway :no_entry:").queue();
		}
		
		@Command(value="setup", description="Set up a giveaway in the current server")
		@AuthorPermissions({Permission.MANAGE_ROLES})
		@BotPermissions({Permission.MESSAGE_HISTORY, Permission.MESSAGE_EMBED_LINKS, Permission.MESSAGE_ADD_REACTION})
		public void setup(CommandEvent event, @Context Connection connection, @Argument(value="channel", nullDefault=true) String channelArgument, @Argument(value="winners", nullDefault=true) Long winnersAmount,
				@Argument(value="duration", nullDefault=true) String duration, @Argument(value="item", endless=true, nullDefault=true) String giveawayItem) {
			r.table("giveaway").insert(r.hashMap("id", event.getGuild().getId()).with("giveaway#", 0).with("giveaways", new Object[0])).run(connection, OptArgs.of("durability", "soft"));
			Get data = r.table("giveaway").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			AtomicReference<String> channelString = new AtomicReference<>(), durationString = new AtomicReference<>(), itemString = new AtomicReference<>();
			AtomicReference<Long> winnersTotal = new AtomicReference<>();
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
					
					long id = ((long) dataRan.get("giveaway#")) + 1;
					event.reply("Your giveaway has been created in " + channel.getAsMention() + " :tada:\nGiveaway ID: `" + id + "`").queue();
					
					Map<String, Object> giveaway = new HashMap<>();
					giveaway.put("id", id);
					giveaway.put("message", message.getId());
					giveaway.put("endtime", endTime);
					giveaway.put("length", durationLength);
					giveaway.put("item", giveawayItem);
					giveaway.put("channel", channel.getId());
					giveaway.put("winners", winnersAmount);
					
					data.update(row -> r.hashMap("giveaways", row.g("giveaways").append(giveaway)).with("giveaway#", row.g("giveaway#").add(1))).runNoReply(connection);
					
					ScheduledFuture<?> executor = GiveawayEvents.scheduledExectuor.schedule(() -> GiveawayEvents.removeGiveaway(event.getGuild(), giveaway), durationLength, TimeUnit.SECONDS);
					GiveawayEvents.putExecutor(event.getGuild().getId(), id, executor);
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
									winnersTotal.set(Long.parseLong(winnersMessage.getContentRaw()));
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
						
						long id = ((long) dataRan.get("giveaway#")) + 1;
						event.reply("Your giveaway has been created in " + channel.getAsMention() + " :tada:\nGiveaway ID: `" + id + "`").queue();
						
						Map<String, Object> giveaway = new HashMap<>();
						giveaway.put("id", id);
						giveaway.put("message", message.getId());
						giveaway.put("endtime", endTime);
						giveaway.put("length", durationLength);
						giveaway.put("item", itemString.get());
						giveaway.put("channel", channel.getId());
						giveaway.put("winners", winnersTotal.get());
						
						data.update(row -> r.hashMap("giveaways", row.g("giveaways").append(giveaway)).with("giveaway#", row.g("giveaway#").add(1))).runNoReply(connection);
						
						ScheduledFuture<?> executor = GiveawayEvents.scheduledExectuor.schedule(() -> GiveawayEvents.removeGiveaway(event.getGuild(), giveaway), durationLength, TimeUnit.SECONDS);
						GiveawayEvents.putExecutor(event.getGuild().getId(), id, executor);
					});
				});
			}
		}
		
	}
	
	@Initialize(all=true)
	public void initialize(CommandImpl command) {
		command.setCategory(Categories.GIVEAWAY);
	}
	
}
