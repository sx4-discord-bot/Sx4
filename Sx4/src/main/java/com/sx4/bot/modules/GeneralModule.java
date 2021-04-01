package com.sx4.bot.modules;

import com.jockie.bot.core.JockieUtils;
import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.command.Command.*;
import com.jockie.bot.core.command.Context;
import com.jockie.bot.core.command.ICommand.ContentOverflowPolicy;
import com.jockie.bot.core.command.Initialize;
import com.jockie.bot.core.command.impl.CommandEvent;
import com.jockie.bot.core.command.impl.CommandImpl;
import com.jockie.bot.core.cooldown.ICooldown;
import com.jockie.bot.core.module.Module;
import com.jockie.bot.core.option.Option;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.model.*;
import com.sun.management.OperatingSystemMXBean;
import com.sx4.bot.cache.ChangesMessageCache;
import com.sx4.bot.categories.Categories;
import com.sx4.bot.core.Sx4Bot;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEventListener;
import com.sx4.bot.database.Database;
import com.sx4.bot.events.AwaitEvents;
import com.sx4.bot.events.ConnectionEvents;
import com.sx4.bot.events.ReminderEvents;
import com.sx4.bot.events.StatsEvents;
import com.sx4.bot.interfaces.Examples;
import com.sx4.bot.interfaces.Sx4Callback;
import com.sx4.bot.settings.Settings;
import com.sx4.bot.utils.*;
import com.sx4.bot.utils.PagedUtils.PagedResult;
import net.dv8tion.jda.api.*;
import net.dv8tion.jda.api.JDA.ShardInfo;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.Activity.ActivityType;
import net.dv8tion.jda.api.entities.Guild.ExplicitContentLevel;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.entities.User.UserFlag;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import okhttp3.Request;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.awt.*;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.zone.ZoneRulesException;
import java.util.List;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;

@Module
public class GeneralModule {
	
	private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd LLL yyyy HH:mm");
	
	@Command(value="voice link", aliases={"voicelink", "vclink", "vc link", "screenshare"}, description="Gives you a link which allows you to screenshare in the current or specified voice channel")
	@Examples({"voice link", "voice link #general", "voice link 344091594972069888", "voice link general"})
	public String voiceLink(CommandEvent event, @Argument(value="voice channel", nullDefault=true, endless=true) String voiceChannel) {
		VoiceChannel channel = null;
		if (voiceChannel == null) {
			channel = event.getMember().getVoiceState().getChannel();
			if (channel == null) {
				return "You are not in a voice channel, join one or provide a voice channel argument :no_entry:";
			}
		} else {
			channel = ArgumentUtils.getVoiceChannel(event.getGuild(), voiceChannel);
			if (channel == null) {
				return "I could not find that voice channel :no_entry:";
			}
		}
		
		return "<https://discordapp.com/channels/" + event.getGuild().getId() + "/" + channel.getId() + ">";
	}
	
	public class ReminderCommand extends Sx4Command {
		
		private final Pattern reminderTimeRegex = Pattern.compile("(.*) in (?: *|)((?:[0-9]+(?: |)(?:[a-zA-Z]+|){1}(?: |){1}){1,})");
		private final Pattern reminderDateRegex = Pattern.compile("(.*) at (" + TimeUtils.DATE_REGEX.pattern() + ")");
		
		public ReminderCommand() {
			super("reminder");
			
			super.setDescription("Create reminders to keep up to date with tasks");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
			super.setExamples("reminder add", "reminder remove", "reminder list");
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="add", description="Add a reminder so that the bot can remind you about it rather than you having to remember", argumentInfo="<reminder> in <time>\nreminder add <reminder> at <date>")
		@Examples({"reminder add Football game in 4 hours", "reminder add Party at 01/07/20 15:00 UTC+1", "reminder add Finish coursework at 12:00", "reminder add fish in 5 minutes --repeat"})
		public void add(CommandEvent event, @Context Database database, @Argument(value="reminder", endless=true) String reminder, @Option(value="repeat", /*aliases={"reoccur"},*/ description="Repeats your reminder so once it finishes it recreates one for you") boolean repeat) {
			Matcher timeRegex = this.reminderTimeRegex.matcher(reminder);
			Matcher dateRegex = this.reminderDateRegex.matcher(reminder);
			String reminderName;
			long duration, remindAt;
			long timestampNow = Clock.systemUTC().instant().getEpochSecond();
			if (timeRegex.matches()) {
				reminderName = timeRegex.group(1);
				String time = timeRegex.group(2).trim();
				
				duration = TimeUtils.convertToSeconds(time);			
				if (duration <= 0) {
					event.reply("Invalid time format, make sure it's formatted with a numerical value then a letter representing the time (d for days, h for hours, m for minutes, s for seconds) and make sure it's in order :no_entry:").queue();
					return;
				}
				
				remindAt = Clock.systemUTC().instant().getEpochSecond() + duration;
			} else if (dateRegex.matches()) {
				reminderName = dateRegex.group(1);
				String date = dateRegex.group(2).trim();
				
				try {
					duration = TimeUtils.dateTimeToDuration(date);
				} catch(ZoneRulesException e) {
					event.reply("You provided an invalid time zone :no_entry:").queue();
					return;
				} catch(IllegalArgumentException e) {
					event.reply(e.getMessage()).queue();
					return;
				}
				
				remindAt = timestampNow + duration;
				
				if (duration == 0) {
					event.reply("Invalid date format, make sure it's formated like so `dd/mm/yy hh:mm` :no_entry:").queue();
					return;
				}
				
			} else {
				event.reply("Invalid reminder format, make sure to format your argument like so <reminder> in <time> or <reminder> at <date> :no_entry:").queue();
				return;
			}
			
			if (duration < 60 && repeat) {
				event.reply("Repeated reminders must be at least 1 minute long :no_entry:").queue();
				return;
			}
			
			if (reminderName.length() > 1500) {
				event.reply("Your reminder can be no longer than 1500 characters :no_entry:").queue();
				return;
			}
			
			int reminderCount = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("reminder.amount")).getEmbedded(List.of("reminder", "amount"), 0);
			
			Document reminderData = new Document("id", reminderCount + 1)
					.append("reminder", reminderName)
					.append("remindAt", remindAt)
					.append("duration", duration)
					.append("repeat", repeat);
			
			Bson update = Updates.combine(
					Updates.push("reminder.reminders", reminderData),
					Updates.inc("reminder.amount", 1)
			);

			database.updateUserById(event.getAuthor().getIdLong(), update, (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply(String.format("I have added your reminder, you will be reminded about it in `%s` (Reminder ID: **%s**)", TimeUtils.toTimeString(duration, ChronoUnit.SECONDS), reminderCount + 1)).queue();
					ScheduledFuture<?> executor = ReminderEvents.scheduledExectuor.schedule(() -> ReminderEvents.removeUserReminder(event.getAuthor().getIdLong(), reminderCount + 1, reminderName, duration, repeat), duration, TimeUnit.SECONDS);
					ReminderEvents.putExecutor(event.getAuthor().getIdLong(), reminderCount + 1, executor);
				}
			});
		}
		
		@Command(value="remove", description="Remove a reminder you no longer need to be notified about")
		@Examples({"reminder remove 1", "reminder remove 10"})
		public void remove(CommandEvent event, @Context Database database, @Argument("reminder id") int reminderId) {
			List<Document> reminders = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("reminder.reminders")).getEmbedded(List.of("reminder", "reminders"), Collections.emptyList());
			if (reminders.isEmpty()) {
				event.reply("You do not have any active reminders :no_entry:").queue();
				return;
			}
			
			for (Document reminder : reminders) {
				if (reminder.getInteger("id") == reminderId) {
					database.updateUserById(event.getAuthor().getIdLong(), Updates.pull("reminder.reminders", Filters.eq("id", reminderId)), (result, exception) -> {
						if (exception != null) {
							exception.printStackTrace();
							event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
						} else {
							event.reply("I have removed that reminder, you will no longer be reminded about it <:done:403285928233402378>").queue();
							ReminderEvents.cancelExecutor(event.getAuthor().getIdLong(), reminderId);
						}
					});
					
					return;
				}
			}
			
			event.reply("I could not find that reminder :no_entry:").queue();
		}
		
		@Command(value="list", description="Lists all the current reminders you have and how long left till you'll be notified about them", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"reminder list"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void list(CommandEvent event, @Context Database database) {
			List<Document> reminders = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("reminder.reminders")).getEmbedded(List.of("reminder", "reminders"), Collections.emptyList());
			if (reminders.isEmpty()) {
				event.reply("You do not have any active reminders :no_entry:").queue();
				return;
			}
			
			reminders.sort((a, b) -> Integer.compare(a.getInteger("id"), b.getInteger("id")));

			PagedResult<Document> paged = new PagedResult<>(reminders)
					.setAuthor("Reminder List", null, event.getAuthor().getEffectiveAvatarUrl())
					.setFunction(data -> {
						String reminder = data.getString("reminder");
						if (reminder.length() > 100) {
							reminder = reminder.substring(0, 97) + "...";
						}
						
						String time = TimeUtils.toTimeString(data.getLong("remindAt") - Clock.systemUTC().instant().getEpochSecond(), ChronoUnit.SECONDS);
						return reminder + " in `" + time + "` (ID: " + data.getInteger("id") + ")";
					})
					.setSelectableByIndex(true)
					.setIncreasedIndex(true);
				
			PagedUtils.getPagedResult(event, paged, 300, returnResult -> {
				Document requestedReminder = returnResult.getData();
				String remindAt = TimeUtils.toTimeString(requestedReminder.getLong("remindAt") - Clock.systemUTC().instant().getEpochSecond(), ChronoUnit.SECONDS);
				event.reply("ID: `" + requestedReminder.getInteger("id") + "`\nReminder: `" + requestedReminder.getString("reminder") + "`\nRemind in: `" + remindAt + "`").queue();
			});
			
		}
		
	}
	
	@Command(value="suggest", description="If suggestions are set up in the current server send in a suggestion for the chance of it being implemented and get notified when it's accepted/declined")
	@Examples({"suggest Add the dog emote", "suggest Create a channel for people looking to play games"})
	@BotPermissions({Permission.MESSAGE_ADD_REACTION, Permission.MESSAGE_EMBED_LINKS})
	public void suggest(CommandEvent event, @Context Database database, @Argument(value="suggestion", endless=true) String suggestion) {
		Document data = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("suggestion.enabled", "suggestion.channelId")).get("suggestion", Database.EMPTY_DOCUMENT);
		Long channelId = data.getLong("channelId");
		
		if (channelId == null) {
			event.reply("Suggestions have not been setup in this server :no_entry:").queue();
			return;
		}
		
		if (!data.getBoolean("enabled", false)) {
			event.reply("Suggestions are disabled on this server :no_entry:").queue();
			return;
		}
		
		TextChannel channel = event.getGuild().getTextChannelById(channelId);
		if (channel == null) {
			event.reply("The set channel for suggestions no longer exists :no_entry:").queue();
			return;
		}
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setDescription(suggestion);
		embed.setAuthor(event.getAuthor().getAsTag(), null, event.getAuthor().getEffectiveAvatarUrl());
		embed.setFooter("This suggestion is currently pending", null);
		channel.sendMessage(embed.build()).queue(message -> {
			Document suggestionData = new Document("id", message.getIdLong())
					.append("userId", event.getAuthor().getIdLong())
					.append("accepted", null);
			
			database.updateGuildById(event.getGuild().getIdLong(), Updates.push("suggestion.suggestions", suggestionData), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("Your suggestion has been sent to " + channel.getAsMention()).queue();
					message.addReaction("✅").queue();
					message.addReaction("❌").queue();
				}
			});
		});	
	}
	
	public class SuggestionCommand extends Sx4Command {
		
		public SuggestionCommand() {
			super("suggestion");
			
			super.setDescription("Create a suggestion channel where suggestions can be sent in and voted on in your server");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
			super.setExamples("suggestion accept", "suggestion deny", "suggestion toggle");
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="toggle", aliases={"enable", "disable"}, description="Enables/disables suggestions in the server depending on its current state", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"suggestion toggle"})
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		public void toggle(CommandEvent event, @Context Database database) {
			boolean enabled = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("suggestion.enabled")).getEmbedded(List.of("suggestion", "enabled"), false);			
			database.updateGuildById(event.getGuild().getIdLong(), Updates.set("suggestion.enabled", !enabled), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("Suggestions are now " + (enabled ? "disabled" : "enabled") + " <:done:403285928233402378>").queue();
				}
			});
		}
		
		@Command(value="channel", description="Sets the suggestion channel for suggestions in your server")
		@Examples({"suggestion channel #suggestions", "suggestion channel 447080313944801291", "suggestion channel suggestions"})
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		public void channel(CommandEvent event, @Context Database database, @Argument(value="channel", endless=true) String channelArgument) {
			TextChannel channel = ArgumentUtils.getTextChannel(event.getGuild(), channelArgument);
			if (channel == null) {
				event.reply("I could not find that text channel :no_entry").queue();
				return;
			}
			
			long channelId = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("suggestion.channelId")).getEmbedded(List.of("suggestion", "channelId"), 0L);
			if (channelId == channel.getIdLong()) {
				event.reply(channel.getAsMention() + " is already the suggestion channel :no_entry:").queue();
				return;
			}
			
			database.updateGuildById(event.getGuild().getIdLong(), Updates.set("suggestion.channelId", channel.getIdLong()), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("The suggestion channel is now " + channel.getAsMention() + " <:done:403285928233402378>").queue();
				}
			});
		}
		
		@Command(value="accept", description="Accepts a suggestion that a user has created this lets the user know it's been accepted and shows it's accepted in the suggestion channel")
		@Examples({"suggestion accept 643130228465467413"})
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		@BotPermissions({Permission.MESSAGE_HISTORY})
		public void accept(CommandEvent event, @Context Database database, @Argument(value="message id") long messageId, @Argument(value="reason", endless=true, nullDefault=true) String reason) {
			Document data = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("suggestion.channelId", "suggestion.suggestions")).get("suggestion", Database.EMPTY_DOCUMENT);
			
			Long channelId = data.getLong("channelId");
			if (channelId == null) {
				event.reply("Suggestions have not been setup in this server :no_entry:").queue();
				return;
			}
			
			List<Document> suggestions = data.getList("suggestions", Document.class, Collections.emptyList());
			for (Document suggestion : suggestions) {
				if (messageId == suggestion.getLong("id")) {
					TextChannel channel = event.getGuild().getTextChannelById(channelId);
					if (channel == null) {
						database.updateGuildById(event.getGuild().getIdLong(), Updates.pull("suggestion.suggestions", Filters.eq("id", messageId)), (result, exception) -> {
							if (exception != null) {
								exception.printStackTrace();
								event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
							} else {
								event.reply("The suggestion channel set no longer exists :no_entry").queue();
							}
						});
						
						return;
					}
					
					channel.retrieveMessageById(messageId).queue(message -> {
						if (suggestion.getBoolean("accepted") != null) {
							event.reply("This suggestion already has a verdict :no_entry:").queue();
							return;
						}
							
						MessageEmbed oldEmbed = message.getEmbeds().get(0);
						EmbedBuilder embed = new EmbedBuilder();
						embed.setAuthor(oldEmbed.getAuthor().getName(), null, oldEmbed.getAuthor().getIconUrl());
						embed.setDescription(oldEmbed.getDescription());
						embed.addField("Moderator", event.getAuthor().getAsTag(), true);
						embed.addField("Reason", reason == null ? "Not given" : reason, true);
						embed.setFooter("Suggestion Accepted", null);
						embed.setColor(Color.decode("#5fe468"));
							
						UpdateOptions updateOptions = new UpdateOptions().arrayFilters(List.of(Filters.eq("suggestion.id", messageId)));
						database.updateGuildById(event.getGuild().getIdLong(), null, Updates.set("suggestion.suggestions.$[suggestion].accepted", true), updateOptions, (result, exception) -> {
							if (exception != null) {
								exception.printStackTrace();
								event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
							} else {
								message.editMessage(embed.build()).queue();
								
								event.reply("That suggestion has been accepted <:done:403285928233402378>").queue();
								
								Member member = event.getGuild().getMemberById(suggestion.getLong("userId"));
								
								if (member != null) {
									member.getUser().openPrivateChannel().queue(u -> u.sendMessage("Your suggestion below has been accepted\n" + message.getJumpUrl()).queue(), $ -> {});
								}
							}
						});

						return;
					}, e -> {
						if (e instanceof ErrorResponseException) {
							ErrorResponseException exception = (ErrorResponseException) e;
							if (exception.getErrorCode() == 10008) {
								database.updateGuildById(event.getGuild().getIdLong(), Updates.pull("suggestion.suggestions", Filters.eq("id", messageId)), (removeResult, removeException) -> {
									if (removeException != null) {
										removeException.printStackTrace();
										event.reply(Sx4CommandEventListener.getUserErrorMessage(removeException)).queue();
									} else {
										event.reply("I could not find that message :no_entry:").queue();
									}
								});

								return;
							}
						}
					});
					
					return;
				}
			}
			
			event.reply("That message is not a suggestion message :no_entry:").queue();			
		}
		
		@Command(value="deny", description="Denies a suggestion that a user has created this lets the user know it's been declined and shows it's declined in the suggestion channel")
		@Examples({"suggestion deny 643130228465467413"})
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		@BotPermissions({Permission.MESSAGE_HISTORY})
		public void deny(CommandEvent event, @Context Database database, @Argument(value="message id") long messageId, @Argument(value="reason", endless=true, nullDefault=true) String reason) {
			Document data = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("suggestion.channelId", "suggestion.suggestions")).get("suggestion", Database.EMPTY_DOCUMENT);
			
			Long channelId = data.getLong("channelId");
			if (channelId == null) {
				event.reply("Suggestions have not been setup in this server :no_entry:").queue();
				return;
			}
			
			List<Document> suggestions = data.getList("suggestions", Document.class, Collections.emptyList());
			for (Document suggestion : suggestions) {
				if (messageId == suggestion.getLong("id")) {
					TextChannel channel = event.getGuild().getTextChannelById(channelId);
					if (channel == null) {
						database.updateGuildById(event.getGuild().getIdLong(), Updates.pull("suggestion.suggestions", Filters.eq("id", messageId)), (result, exception) -> {
							if (exception != null) {
								exception.printStackTrace();
								event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
							} else {
								event.reply("The suggestion channel set no longer exists :no_entry").queue();
							}
						});
						
						return;
					}
					
					channel.retrieveMessageById(messageId).queue(message -> {
						if (suggestion.getBoolean("accepted") != null) {
							event.reply("This suggestion already has a verdict :no_entry:").queue();
							return;
						}
							
						MessageEmbed oldEmbed = message.getEmbeds().get(0);
						EmbedBuilder embed = new EmbedBuilder();
						embed.setAuthor(oldEmbed.getAuthor().getName(), null, oldEmbed.getAuthor().getIconUrl());
						embed.setDescription(oldEmbed.getDescription());
						embed.addField("Moderator", event.getAuthor().getAsTag(), true);
						embed.addField("Reason", reason == null ? "Not given" : reason, true);
						embed.setFooter("Suggestion Denied", null);
						embed.setColor(Color.decode("#f84b50"));
							
						UpdateOptions updateOptions = new UpdateOptions().arrayFilters(List.of(Filters.eq("suggestion.id", messageId)));
						database.updateGuildById(event.getGuild().getIdLong(), null, Updates.set("suggestion.suggestions.$[suggestion].accepted", false), updateOptions, (result, exception) -> {
							if (exception != null) {
								exception.printStackTrace();
								event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
							} else {
								message.editMessage(embed.build()).queue();
								
								event.reply("That suggestion has been denied <:done:403285928233402378>").queue();
								
								Member member = event.getGuild().getMemberById(suggestion.getLong("userId"));
								
								if (member != null) {
									member.getUser().openPrivateChannel().queue(u -> {
										u.sendMessage("Your suggestion below has been denied\n" + message.getJumpUrl()).queue();
									}, $ -> {});
								}
							}
						});
						
						return;
					}, e -> {
						if (e instanceof ErrorResponseException) {
							ErrorResponseException exception = (ErrorResponseException) e;
							if (exception.getErrorCode() == 10008) {
								database.updateGuildById(event.getGuild().getIdLong(), Updates.pull("suggestion.suggestions", Filters.eq("id", messageId)), (removeResult, removeException) -> {
									if (removeException != null) {
										removeException.printStackTrace();
										event.reply(Sx4CommandEventListener.getUserErrorMessage(removeException)).queue();
									} else {
										event.reply("I could not find that message :no_entry:").queue();
									}
								});
								
								return;
							}
						}
					});
					
					return;
				}
			}
			
			event.reply("That message is not a suggestion message :no_entry:").queue();			
		}
		
		@Command(value="undo", description="Undoes a decision made on a suggestion to put it back to it's pending state", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"suggestion undo 643130228465467413"})
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		@BotPermissions({Permission.MESSAGE_HISTORY})
		public void undo(CommandEvent event, @Context Database database, @Argument(value="message ID") long messageId) {
			Document data = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("suggestion.channelId", "suggestion.suggestions")).get("suggestion", Database.EMPTY_DOCUMENT);
			
			Long channelId = data.getLong("channelId");
			if (channelId == null) {
				event.reply("Suggestions have not been setup in this server :no_entry:").queue();
				return;
			}
			
			List<Document> suggestions = data.getList("suggestions", Document.class, Collections.emptyList());
			for (Document suggestion : suggestions) {
				if (messageId == suggestion.getLong("id")) {
					TextChannel channel = event.getGuild().getTextChannelById(channelId);
					if (channel == null) {
						database.updateGuildById(event.getGuild().getIdLong(), Updates.pull("suggestion.suggestions", Filters.eq("id", messageId)), (result, exception) -> {
							if (exception != null) {
								exception.printStackTrace();
								event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
							} else {
								event.reply("The suggestion channel set no longer exists :no_entry").queue();
							}
						});
						
						return;
					}
					
					channel.retrieveMessageById(messageId).queue(message -> {
						if (suggestion.getBoolean("accepted") == null) {
							event.reply("This suggestion is already pending :no_entry:").queue();
							return;
						}
							
						MessageEmbed oldEmbed = message.getEmbeds().get(0);
						EmbedBuilder embed = new EmbedBuilder();
						embed.setAuthor(oldEmbed.getAuthor().getName(), null, oldEmbed.getAuthor().getIconUrl());
						embed.setDescription(oldEmbed.getDescription());
						embed.setFooter("This suggestion is currently pending", null);
							
						UpdateOptions updateOptions = new UpdateOptions().arrayFilters(List.of(Filters.eq("suggestion.id", messageId)));
						database.updateGuildById(event.getGuild().getIdLong(), null, Updates.set("suggestion.suggestions.$[suggestion].accepted", null), updateOptions, (result, exception) -> {
							if (exception != null) {
								exception.printStackTrace();
								event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
							} else {
								message.editMessage(embed.build()).queue();
								
								event.reply("That suggestion is now pending <:done:403285928233402378>").queue();
								
								Member member = event.getGuild().getMemberById(suggestion.getLong("userId"));
								
								if (member != null) {
									member.getUser().openPrivateChannel().queue(u -> u.sendMessage("Your suggestion below has been undone this means it is back to its pending state\n" + message.getJumpUrl()).queue(), $ -> {});
								}
							}
						});
						
						return;
					}, e -> {
						if (e instanceof ErrorResponseException) {
							ErrorResponseException exception = (ErrorResponseException) e;
							if (exception.getErrorCode() == 10008) {
								database.updateGuildById(event.getGuild().getIdLong(), Updates.pull("suggestion.suggestions", Filters.eq("id", messageId)), (removeResult, removeException) -> {
									if (removeException != null) {
										removeException.printStackTrace();
										event.reply(Sx4CommandEventListener.getUserErrorMessage(removeException)).queue();
									} else {
										event.reply("I could not find that message :no_entry:").queue();
									}
								});
								
								return;
							}
						}
					});
					
					return;
				}
			}
			
			event.reply("That message is not a suggestion message :no_entry:").queue();			
		}
		
		@Command(value="remove", aliases={"delete"}, description="Deletes a suggestion from the suggestions list, also deletes the message if it can", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"suggestion remove 643130228465467413"})
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		@BotPermissions({Permission.MESSAGE_HISTORY})
		public void remove(CommandEvent event, @Context Database database, @Argument(value="message ID") long messageId) {
			Document data = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("suggestion.channelId", "suggestion.suggestions")).get("suggestion", Database.EMPTY_DOCUMENT);
			
			Long channelId = data.getLong("channelId");
			if (channelId == null) {
				event.reply("Suggestions have not been setup in this server :no_entry:").queue();
				return;
			}
			
			List<Document> suggestions = data.getList("suggestions", Document.class, Collections.emptyList());
			for (Document suggestion : suggestions) {
				if (messageId == suggestion.getLong("id")) {
					TextChannel channel = event.getGuild().getTextChannelById(channelId);
					if (channel == null) {
						database.updateGuildById(event.getGuild().getIdLong(), Updates.pull("suggestion.suggestions", Filters.eq("id", messageId)), (result, exception) -> {
							if (exception != null) {
								exception.printStackTrace();
								event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
							} else {
								event.reply("The suggestion channel set no longer exists :no_entry").queue();
							}
						});
						
						return;
					}
					
					channel.retrieveMessageById(messageId).queue(message -> {
						database.updateGuildById(event.getGuild().getIdLong(), Updates.pull("suggestion.suggestions", Filters.eq("id", messageId)), (result, exception) -> {
							if (exception != null) {
								exception.printStackTrace();
								event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
							} else {
								message.delete().queue();
								event.reply("That suggestion has been deleted <:done:403285928233402378>").queue();
							}
						});

						return;
					}, e -> {
						if (e instanceof ErrorResponseException) {
							ErrorResponseException exception = (ErrorResponseException) e;
							if (exception.getErrorCode() == 10008) {
								database.updateGuildById(event.getGuild().getIdLong(), Updates.pull("suggestion.suggestions", Filters.eq("id", messageId)), (removeResult, removeException) -> {
									if (removeException != null) {
										removeException.printStackTrace();
										event.reply(Sx4CommandEventListener.getUserErrorMessage(removeException)).queue();
									} else {
										event.reply("I could not find that message :no_entry:").queue();
									}
								});
								
								return;
							}
						}
					});	
					
					return;
				}
			}
			
			event.reply("That message is not a suggestion message :no_entry:").queue();		  
		}
		
		@Command(value="reset", aliases={"wipe"}, description="Wipes all of the suggestions in the server", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"suggestion reset"})
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		public void reset(CommandEvent event, @Context Database database) {
			List<Document> suggestions = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("suggestion.suggestions")).getEmbedded(List.of("suggestion", "suggestions"), Collections.emptyList());
			if (suggestions.isEmpty()) {
				event.reply("There are no suggestions in this server :no_entry:").queue();
				return;
			}
			
			event.reply("**" + event.getAuthor().getName() + "**, are you sure you want to delete all the suggestions in the server?").queue(message -> {
				PagedUtils.getConfirmation(event, 30, event.getAuthor(), confirmation -> {
					if (confirmation) {
						database.updateGuildById(event.getGuild().getIdLong(), Updates.unset("suggestion.suggestions"), (result, exception) -> {
							if (exception != null) {
								exception.printStackTrace();
								event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
							} else {
								event.reply("All suggestions have been deleted <:done:403285928233402378>").queue();
							}
						});
					} else {
						event.reply("Cancelled <:done:403285928233402378>").queue();
					}
					
					message.delete().queue(null, e -> {});
				});
			});
		}
		
		@Command(value="list", description="View all the suggestions which have been sent in, shows whether they have been declined/accepted and provides a jump link", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"suggestion list"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void list(CommandEvent event, @Context Database database) {
			Document data = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("suggestion.channelId", "suggestion.suggestions")).get("suggestion", Database.EMPTY_DOCUMENT);
			
			Long channelId = data.getLong("channelId");
			if (channelId == null) {
				event.reply("Suggestions have not been setup in this server :no_entry:").queue();
				return;
			}
			
			List<Document> suggestions = data.getList("suggestions", Document.class, Collections.emptyList());
			if (suggestions.isEmpty()) {
				event.reply("No suggestions have been sent yet :no_entry:").queue();
				return;
			}
			
			PagedResult<Document> paged = new PagedResult<>(suggestions)
					.setIndexed(false)
					.setAuthor("Suggestions", null, event.getGuild().getIconUrl())
					.setFunction(d -> {
						String accepted;
						if (d.getBoolean("accepted") == null) {
							accepted = "Pending";
						} else if (d.getBoolean("accepted")) {
							accepted = "Accepted";
						} else {
							accepted = "Denied";
						}
						
						User user = event.getShardManager().getUserById(d.getLong("userId"));
						return "[" + (user != null ? user.getName() + "'s" : d.getLong("userId")) + " Suggestion - " + accepted + "](https://discordapp.com/channels/" + 
								event.getGuild().getId() + "/" + channelId + "/" + d.getLong("id") + ")";
					});
			
			PagedUtils.getPagedResult(event, paged, 300, null);
		}
		
		@Command(value="stats", aliases={"settings", "setting"}, description="View the settings for suggestions in the current server", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"suggestion stats"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void stats(CommandEvent event, @Context Database database) {
			Document data = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("suggestion.channelId", "suggestion.suggestions", "suggestion.enabled")).get("suggestion", Database.EMPTY_DOCUMENT);
			
			List<Document> suggestions = data.getList("suggestions", Document.class, Collections.emptyList());
			Long channelId = data.getLong("channelId");
			TextChannel channel = channelId == null ? null : event.getGuild().getTextChannelById(channelId);
			
			EmbedBuilder embed = new EmbedBuilder()
					.setAuthor("Suggestion Settings", null, event.getGuild().getIconUrl())
					.setColor(Settings.EMBED_COLOUR)
					.addField("Status", data.getBoolean("enabled", false) ? "Enabled" : "Disabled", true)
					.addField("Channel", channel == null ? "Not Set" : channel.getAsMention(), true)
					.addField("Suggestions", String.valueOf(suggestions.size()), true);
			
			event.reply(embed.build()).queue();
			
		}
	}
	
	public class ImageModeCommand extends Sx4Command {
		
		private List<String> nullStrings = List.of("off", "none", "null");
		
		public ImageModeCommand() {
			super("image mode");
			
			super.setAliases("imagemode", "imgmode", "img mode");
			super.setDescription("Set up image mode in a channel so that only images can be sent in that channel anything else will be deleted");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
			super.setExamples("image mode channel", "image mode slowmode", "image mode stats");
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="channel", aliases={"toggle"}, description="Toggle image mode on/off in a certain channel")
		@Examples({"image mode channel", "image mode channel #images", "image mode channel 344091594972069888", "image mode channel images"})
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		public void channel(CommandEvent event, @Context Database database, @Argument(value="channel", endless=true, nullDefault=true) String channelArgument) {
			TextChannel channel;
			if (channelArgument == null) {
				channel = event.getTextChannel();
			} else {
				channel = ArgumentUtils.getTextChannel(event.getGuild(), channelArgument);
				if (channel == null) {
					event.reply("I could not find that text channel :no_entry:").queue();
					return;
				}
			}
			
			
			List<Document> channels = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("imageMode.channels")).getEmbedded(List.of("imageMode", "channels"), Collections.emptyList());
			for (Document channelData : channels) {
				if (channelData.getLong("id") == channel.getIdLong()) {
					database.updateGuildById(event.getGuild().getIdLong(), Updates.pull("imageMode.channels", Filters.eq("id", channel.getIdLong())), (result, exception) -> {
						if (exception != null) {
							exception.printStackTrace();
							event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
						} else {
							event.reply("Image mode in " + channel.getAsMention() + " is now disabled <:done:403285928233402378>").queue();
						}
					});
					
					return;
				}
			}
			
			Document imageMode = new Document("id", channel.getIdLong())
					.append("slowmode", 0L)
					.append("users", Collections.emptyList());
			
			database.updateGuildById(event.getGuild().getIdLong(), Updates.push("imageMode.channels", imageMode), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("Image mode in " + channel.getAsMention() + " is now enabled <:done:403285928233402378>").queue();
				}
			});
		}
		
		@Command(value="slowmode", aliases={"slow mode", "sm"}, description="Add a slowmode to the current channel (Providing image mode is turned on in the current channel) so users can only send an image every however long you choose")
		@Examples({"image mode slowmode 10 minutes", "image mode slowmode none"})
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		public void slowmode(CommandEvent event, @Context Database database, @Argument(value="time", endless=true) String timeString) {
			long slowmode = 0;
			if (!nullStrings.contains(timeString)) {
				try {
					slowmode = TimeUtils.convertToSeconds(timeString);
				} catch(NumberFormatException e) {
					event.reply("Invalid time format, make sure it's formatted with a numerical value then a letter representing the time (d for days, h for hours, m for minutes, s for seconds) :no_entry:").queue();
					return;
				}
			}
			
			List<Document> channels = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("imageMode.channels")).getEmbedded(List.of("imageMode", "channels"), Collections.emptyList());
			for (Document channelData : channels) {
				if (event.getChannel().getIdLong() == channelData.getLong("id")) {
					String reply = "Slowmode has been " + (slowmode == 0 ? "turned off" : "set to " + TimeUtils.toTimeString(slowmode, ChronoUnit.SECONDS)) + " for image mode in this channel <:done:403285928233402378>";
					
					UpdateOptions updateOptions = new UpdateOptions().arrayFilters(List.of(Filters.eq("channel.id", event.getChannel().getIdLong())));
					database.updateGuildById(event.getGuild().getIdLong(), null, Updates.set("imageMode.channels.$[channel].slowmode", slowmode), updateOptions, (result, exception) -> {
						if (exception != null) {
							exception.printStackTrace();
							event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
						} else {
							event.reply(reply).queue();
						}
					});

					return;
				}
			}
			
			event.reply("Image mode needs to be enabled in this channel to be able to change the slowmode :no_entry:").queue();		
		}
		
		@Command(value="stats", aliases={"settings", "setting"}, description="View settings for image mode in a specific channel")
		@Examples({"image mode stats", "image mode stats #images", "image mode stats 344091594972069888", "image mode stats images"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void stats(CommandEvent event, @Context Database database, @Argument(value="channel", endless=true, nullDefault=true) String channelArgument) {
			TextChannel channel;
			if (channelArgument == null) {
				channel = event.getTextChannel();
			} else {
				channel = ArgumentUtils.getTextChannel(event.getGuild(), channelArgument);
				if (channel == null) {
					event.reply("I could not find that text channel :no_entry:").queue();
					return;
				}
			}
			
			List<Document> channels = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("imageMode.channels")).getEmbedded(List.of("imageMode", "channels"), Collections.emptyList());
			for (Document channelData : channels) {
				if (channel.getIdLong() == channelData.getLong("id")) {
					long slowmode = channelData.getLong("slowmode");
					EmbedBuilder embed = new EmbedBuilder();
					embed.setColor(Settings.EMBED_COLOUR);
					embed.setAuthor("Image Mode Settings", null, event.getGuild().getIconUrl());
					embed.setFooter("Settings shown are for the text channel " + event.getChannel().getName(), null);
					embed.addField("Status", "Enabled", true);
					embed.addField("Slowmode", slowmode == 0 ? "Not Set" : TimeUtils.toTimeString(slowmode, ChronoUnit.SECONDS), true);
					event.reply(embed.build()).queue();
					return;
				}
			}
			
			event.reply("Image mode is not set up in " + (event.getTextChannel().equals(channel) ? "this" : "that") + " channel :no_entry:").queue();
		}
		
	}
	
	@Command(value="usage", description="Shows you how much a specific command has been used on Sx4")
	@Examples({"usage fish", "usage ship --server=330399610273136641", "usage ban --channel=#general", "usage userinfo --server=330399610273136641 --user=Shea#6653"})
	public void usage(CommandEvent event, @Context Database database, @Argument(value="command name", endless=true) String commandName, @Option(value="server", /*aliases={"guild"},*/ description="Provide a server name or id to filter the usage by") String guildArgument, @Option(value="user", description="Provide a user name, tag, mention or id to filter the usage by") String userArgument, @Option(value="channel", description="Provide a channel name, mention or id to filter the usage by") String channelArgument) {
		Sx4Command command = ArgumentUtils.getCommand(commandName);
		if (command == null) {
			event.reply("I could not find that command :no_entry:").queue();
			return;
		}
		
		List<Bson> filters = new ArrayList<>();
		filters.add(Filters.eq("command", command.getCommandTrigger()));
		if (guildArgument != null) {
			Guild guild = ArgumentUtils.getGuild(guildArgument);
			if (guild != null) {
				filters.add(Filters.eq("guildId", guild.getIdLong()));
			}
		} 
		
		if (userArgument != null) {
			User user = ArgumentUtils.getUser(userArgument);
			if (user != null) {
				filters.add(Filters.eq("authorId", user.getIdLong()));
			}
		}
		
		if (channelArgument != null) {
			TextChannel channel = ArgumentUtils.getTextChannel(event.getGuild(), channelArgument);
			if (channel != null) {
				filters.add(Filters.eq("channelId", channel.getIdLong()));
			}
		}
		
		long used = database.getCommandLogs().countDocuments(Filters.and(filters));
		event.reply("`" + command.getCommandTrigger() + "` has been used **" + used + "** time" + (used == 1 ? "" : "s")).queue();
	}
	
	public class CommandStatsCommand extends Sx4Command {
		
		public CommandStatsCommand() {
			super("command stats");
			
			super.setAliases("commandstats");
			super.setDescription("Shows some stats about command usage on the bot");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
			super.setExamples("command stats all", "command stats servers", "command stats users");
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="all", description="View the top used commands on Sx4", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"command stats all", "command stats all --server=330399610273136641", "command stats all --channel=#general", "command stats all --server=330399610273136641 --user=Shea#6653"})
		@Async
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void all(CommandEvent event, @Context Database database, @Option(value="server", /*aliases={"guild"},*/ description="Provide a server name or id to filter the usage by") String guildArgument, @Option(value="user", description="Provide a user name, tag, mention or id to filter the usage by") String userArgument, @Option(value="channel", description="Provide a channel name, mention or id to filter the usage by") String channelArgument, @Option(value="from", description="Choose when the data should start at in epoch seconds") String from, @Option(value="to", description="Choose when the data should end at in epoch seconds") String to) {
			List<Bson> filters = new ArrayList<>(), projections = new ArrayList<>();
			if (guildArgument != null) {
				Guild guild = ArgumentUtils.getGuild(guildArgument);
				if (guild != null) {
					filters.add(Filters.eq("guildId", guild.getIdLong()));
				}
			} 
			
			if (userArgument != null) {
				User user = ArgumentUtils.getUser(userArgument);
				if (user != null) {
					projections.add(Projections.include("authorId"));
					filters.add(Filters.eq("authorId", user.getIdLong()));
				}
			}
			
			if (channelArgument != null) {
				TextChannel channel = ArgumentUtils.getTextChannel(event.getGuild(), channelArgument);
				if (channel != null) {
					projections.add(Projections.include("channelId"));
					filters.add(Filters.eq("channelId", channel.getIdLong()));
				}
			}
			
			if (from != null) {
				try {
					filters.add(Filters.gte("timestamp", Long.parseLong(from)));
				} catch (NumberFormatException e) {}
			}
			
			if (to != null) {
				try {
					filters.add(Filters.lte("timestamp", Long.parseLong(to)));
				} catch (NumberFormatException e) {}
			}
			
			projections.add(Projections.include("command", "executionDuration", "timestamp", "guildId"));
			projections.add(Projections.exclude("_id"));
			
			List<BsonField> accumulators = List.of(
					Accumulators.sum("count", 1L), 
					Accumulators.avg("executionAverage", "$executionDuration"),
					Accumulators.max("executionMaximum", "$executionDuration"),
					Accumulators.min("executionMinimum", "$executionDuration"),
					Accumulators.max("lastUsed", "$timestamp")
			);
			
			List<Bson> aggregates;
			if (filters.isEmpty()) {
				aggregates = List.of(
						Aggregates.project(Projections.fields(projections)),
						Aggregates.group("$command", accumulators),
						Aggregates.sort(Sorts.descending("count"))
				);
			} else {
				aggregates = List.of(
						Aggregates.project(Projections.fields(projections)),
						Aggregates.match(Filters.and(filters)),
						Aggregates.group("$command", accumulators),
						Aggregates.sort(Sorts.descending("count"))
				);
			}

			AggregateIterable<Document> commands = database.getCommandLogs().aggregate(aggregates);
			
			List<Document> commandCounter = commands.into(new ArrayList<>());
			if (commandCounter.isEmpty()) {
				event.reply("No commands have been used with those set filters :no_entry:").queue();
				return;
			}
			
			PagedResult<Document> paged = new PagedResult<>(commandCounter)
					.setFunction(data -> String.format("`%s` - %,d %s", data.getString("_id"), data.getLong("count"), data.getLong("count") == 1 ? "use" : "uses"))
					.setAuthor("Top Commands", null, event.getSelfUser().getEffectiveAvatarUrl())
					.setIncreasedIndex(true)
					.setDeleteMessage(false);
			
			PagedUtils.getPagedResult(event, paged, 300, null);
		}
		
		@Command(value="servers", aliases= {"guilds"}, description="Shows a list of servers in order of command usage")
		@Examples({"command stats servers", "command stats servers --command=ship", "command stats servers --command=fish --user=Shea#6653"})
		@Async
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void guilds(CommandEvent event, @Context Database database, @Option(value="command", description="Provide a command name to filter the usage by") String commandArgument, @Option(value="user", description="Provide a user name, tag, mention or id to filter the usage by") String userArgument, @Option(value="from", description="Choose when the data should start at in epoch seconds") String from, @Option(value="to", description="Choose when the data should end at in epoch seconds") String to) {
			List<Bson> filters = new ArrayList<>(), projections = new ArrayList<>();
			if (commandArgument != null) {
				Sx4Command command = ArgumentUtils.getCommand(commandArgument, false, true, true);
				if (command != null) {
					projections.add(Projections.include("command"));
					filters.add(Filters.eq("command", command.getCommandTrigger()));
				}
			} 
			
			if (userArgument != null) {
				User user = ArgumentUtils.getUser(userArgument);
				if (user != null) {
					projections.add(Projections.include("authorId"));
					filters.add(Filters.eq("authorId", user.getIdLong()));
				}
			}
			
			if (from != null) {
				try {
					filters.add(Filters.gte("timestamp", Long.parseLong(from)));
				} catch (NumberFormatException e) {}
			}
			
			if (to != null) {
				try {
					filters.add(Filters.lte("timestamp", Long.parseLong(to)));
				} catch (NumberFormatException e) {}
			}
			
			projections.add(Projections.include("guildId"));
			projections.add(Projections.exclude("_id"));
			
			List<Bson> aggregates;
			if (filters.isEmpty()) {
				aggregates = List.of(
						Aggregates.project(Projections.fields(projections)),
						Aggregates.group("$guildId", Accumulators.sum("count", 1L)),
						Aggregates.sort(Sorts.descending("count"))
				);
			} else {
				aggregates = List.of(
						Aggregates.project(Projections.fields(projections)),
						Aggregates.match(Filters.and(filters)),
						Aggregates.group("$guildId", Accumulators.sum("count", 1L)),
						Aggregates.sort(Sorts.descending("count"))
				);
			}
			
			AggregateIterable<Document> commands = database.getCommandLogs().aggregate(aggregates);

			List<Document> commandCounter = commands.into(new ArrayList<>());
			if (commandCounter.isEmpty()) {
				event.reply("No commands have been used with those set filters :no_entry:").queue();
				return;
			}
			
			PagedResult<Document> paged = new PagedResult<>(commandCounter)
					.setFunction(data -> {
						Guild guild = event.getShardManager().getGuildById(data.getLong("_id"));
						return String.format("`%s` - %,d command%s used", guild == null ? "Unknown guild (" + data.getLong("_id") + ")" : guild.getName(), data.getLong("count"), data.getLong("count") == 1 ? "" : "s");
					})
					.setAuthor("Top Commands", null, event.getSelfUser().getEffectiveAvatarUrl())
					.setIncreasedIndex(true)
					.setDeleteMessage(false);
			
			PagedUtils.getPagedResult(event, paged, 300, null);
		}
		
		@Command(value="users", description="Shows a list of users in order of command usage")
		@Examples({"command stats users", "command stats users --command=ship", "command stats users --channel=#general", "command stats users --command=fish --server=330399610273136641"})
		@Async
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void users(CommandEvent event, @Context Database database, @Option(value="command", description="Provide a command name to filter the usage by") String commandArgument, @Option(value="server", /*aliases={"guild"},*/ description="Provide a server name or id to filter the usage by") String guildArgument, @Option(value="channel", description="Provide a channel name, mention or id to filter the usage by") String channelArgument, @Option(value="from", description="Choose when the data should start at in epoch seconds") String from, @Option(value="to", description="Choose when the data should end at in epoch seconds") String to) {
			List<Bson> filters = new ArrayList<>(), projections = new ArrayList<>();
			if (commandArgument != null) {
				Sx4Command command = ArgumentUtils.getCommand(commandArgument, false, true, true);
				if (command != null) {
					projections.add(Projections.include("command"));
					filters.add(Filters.eq("command", command.getCommandTrigger()));
				}
			} 
			
			if (guildArgument != null) {
				Guild guild = ArgumentUtils.getGuild(guildArgument);
				if (guild != null) {
					projections.add(Projections.include("guildId"));
					filters.add(Filters.eq("guildId", guild.getIdLong()));
				}
			} 
			
			if (channelArgument != null) {
				TextChannel channel = ArgumentUtils.getTextChannel(event.getGuild(), channelArgument);
				if (channel != null) {
					projections.add(Projections.include("channelId"));
					filters.add(Filters.eq("channelId", channel.getIdLong()));
				}
			}
			
			if (from != null) {
				try {
					filters.add(Filters.gte("timestamp", Long.parseLong(from)));
				} catch (NumberFormatException e) {}
			}
			
			if (to != null) {
				try {
					filters.add(Filters.lte("timestamp", Long.parseLong(to)));
				} catch (NumberFormatException e) {}
			}
			
			projections.add(Projections.include("authorId"));
			projections.add(Projections.exclude("_id"));
			
			List<Bson> aggregates;
			if (filters.isEmpty()) {
				aggregates = List.of(
						Aggregates.project(Projections.fields(projections)),
						Aggregates.group("$authorId", Accumulators.sum("count", 1L)),
						Aggregates.sort(Sorts.descending("count"))
				);
			} else {
				aggregates = List.of(
						Aggregates.project(Projections.fields(projections)),
						Aggregates.match(Filters.and(filters)),
						Aggregates.group("$authorId", Accumulators.sum("count", 1L)),
						Aggregates.sort(Sorts.descending("count"))
				);
			}
			
			AggregateIterable<Document> commands = database.getCommandLogs().aggregate(aggregates);

			List<Document> commandCounter = commands.into(new ArrayList<>());
			if (commandCounter.isEmpty()) {
				event.reply("No commands have been used with those set filters :no_entry:").queue();
				return;
			}
			
			PagedResult<Document> paged = new PagedResult<>(commandCounter)
					.setFunction(data -> {
						User user = event.getShardManager().getUserById(data.getLong("_id"));
						return String.format("`%s` - %,d command%s used", user == null ? "Unknown user (" + data.getLong("_id") + ")" : user.getAsTag(), data.getLong("count"), data.getLong("count") == 1 ? "" : "s");
					})
					.setAuthor("Top Commands", null, event.getSelfUser().getEffectiveAvatarUrl())
					.setIncreasedIndex(true)
					.setDeleteMessage(false);
			
			PagedUtils.getPagedResult(event, paged, 300, null);
		}
		
	}
	
	@Command(value="decode", description="Decode any text files into discord markdown", argumentInfo="<attachment>", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"decode"})
	public void decode(CommandEvent event) {
		List<Attachment> attachments = event.getMessage().getAttachments();
		if (attachments.isEmpty()) {
			event.reply("You need to supply an attachment when using this command :no_entry:").queue();
			return;
		}
		
		for (Attachment attachment : attachments) {
			if (!attachment.isImage()) {
				int indexOfPeriod = attachment.getUrl().lastIndexOf(".");
				String fileType = attachment.getUrl().substring(indexOfPeriod + 1);
				attachment.retrieveInputStream().thenAcceptAsync(file -> {
					String fileContent;
					try {
						fileContent = new String(file.readAllBytes());
						event.reply(("```" + fileType + "\n" + fileContent).substring(0, Math.min(1997, fileContent.length())) + "```").queue();
					} catch (IOException e) {
						event.reply("Oops, something went wrong there, try again :no_entry:").queue();
					}	
				});
				
				return;
			}
		}
		
		event.reply("The attachment" + (attachments.size() == 1 ? "" : "s") + " sent " + (attachments.size() == 1 ? "is" : "are") +  " not decodable :no_entry:").queue();
	}
	
	@Command(value="channelinfo", aliases={"ci", "channel info", "cinfo", "c info"}, description="Gives you information about a specific text channel/category or voice channels")
	@Examples({"channelinfo", "channelinfo #general", "channelinfo 344091594972069888", "channelinfo general"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void channelInfo(CommandEvent event, @Argument(value="text channel | category | voice channel", endless=true, nullDefault=true) String channelArgument) {
		GuildChannel channel = null;
		if (channelArgument == null) {
			channel = event.getTextChannel();
		} else {
			channel = ArgumentUtils.getTextChannel(event.getGuild(), channelArgument);
			if (channel == null) {
				channel = ArgumentUtils.getVoiceChannel(event.getGuild(), channelArgument);
				if (channel == null) {
					channel = ArgumentUtils.getCategory(event.getGuild(), channelArgument);
				}
			}
		}
		
		if (channel == null) {
			event.reply("I could not find that channel/category :no_entry:").queue();
			return;
		}
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setColor(event.getMember().getColor());
		embed.setAuthor(channel.getName(), null, event.getGuild().getIconUrl());
		embed.addField("Created", channel.getTimeCreated().format(this.formatter), true);
		embed.addField("Channel ID", channel.getId(), true);
		embed.addField("Channel Position", String.valueOf(channel.getPosition() + 1), true);
		embed.addField("Members", String.valueOf(channel.getMembers().size()), true);
		
		if (channel instanceof TextChannel) {
			TextChannel textChannel = (TextChannel) channel;
			embed.addField("NSFW Channel", textChannel.isNSFW() == true ? "Yes" : "No", true);
			embed.addField("Slowmode", textChannel.getSlowmode() != 0 ? TimeUtils.toTimeString(textChannel.getSlowmode(), ChronoUnit.SECONDS) : "No Slowmode Set", true);
			embed.addField("Channel Category", textChannel.getParent() == null ? "Not in a Category" : textChannel.getParent().getName(), true);
		} else if (channel instanceof VoiceChannel) {
			VoiceChannel voiceChannel = (VoiceChannel) channel;
			embed.addField("Channel Category", voiceChannel.getParent() == null ? "Not in a Category" : voiceChannel.getParent().getName(), true);
			embed.addField("User Limit", voiceChannel.getUserLimit() == 0 ? "Unlimited" : String.valueOf(voiceChannel.getUserLimit()), true);
			embed.addField("Bitrate", voiceChannel.getBitrate()/1000 + " kbps", true);
		} else {
			Category category = (Category) channel;
			
			List<String> channels = new ArrayList<>();
			for (GuildChannel guildChannel : category.getChannels()) {
				if (guildChannel.getType() == ChannelType.VOICE) {
					channels.add(guildChannel.getName());
				} else if (guildChannel.getType() == ChannelType.TEXT) {
					channels.add("<#" + guildChannel.getId() + ">");
				}
			}
			
			embed.addField("Category Channels", channels.isEmpty() ? "No Channels are in this Category" : String.join("\n", channels), false);
		}
		
		event.reply(embed.build()).queue();
		
	}
	
	@Command(value="changes", aliases={"changelog", "change log", "updates"}, description="Allows you to view recent changes which have occured on the bot", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"changes", "changes 2.2.0", "changes latest"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void changes(CommandEvent event, @Argument(value="version", nullDefault=true) String version) {
		List<Pair<Long, String>> messages = ChangesMessageCache.getMessages();
		if (version == null) {
			messages = messages.stream().filter(message -> message.getRight().startsWith("Version: ")).collect(Collectors.toList());
			if (messages.isEmpty()) {
				event.reply("I could not find any change logs :no_entry:").queue();
				return;
			}
			
			PagedResult<Pair<Long, String>> paged = new PagedResult<>(messages)
					.setIncreasedIndex(true)
					.setSelectableByIndex(true)
					.setFunction(changeLogMessage -> {
						return "v" + changeLogMessage.getRight().split("\n")[0].substring(9);
					});
			
			PagedUtils.getPagedResult(event, paged, 60, onReturn -> {
				event.reply(onReturn.getData().getRight()).queue();
			});
		} else {
			if (version.toLowerCase().equals("latest")) {
				event.reply(messages.get(0).getRight()).queue();
			} else {
				for (Pair<Long, String> message : messages) {
					String messageVersion = message.getRight().split("\n")[0];
					if (messageVersion.equals("Version: " + version)) {
						event.reply(message.getRight()).queue();
						return;
					}
				}
				
				event.reply("I could not find that change log :no_entry:").queue();
			}
		}
	}
	
	@Command(value="reaction", description="Test your reaction speed using this command")
	@Examples({"reaction"})
	@Cooldown(value=70)
	public void reaction(CommandEvent event) {
		event.reply("In the next 2-10 seconds i'm going to send a message this is when you type whatever you want in the chat from there i will work out the time between me sending the message and you sending your message and that'll be your reaction time :stopwatch:").queue();
		event.reply("**GO!**").queueAfter(GeneralUtils.getRandomNumber(2, 10), TimeUnit.SECONDS, message -> {
			long beforeResponse = System.currentTimeMillis();
			
			Sx4Bot.waiter.waitForEvent(MessageReceivedEvent.class, e -> {
				return e.getChannel().equals(event.getChannel()) && e.getAuthor().equals(event.getAuthor());
			}, e -> {
				long afterResponse = System.currentTimeMillis();
				long responseTime = (afterResponse - beforeResponse - event.getJDA().getGatewayPing());
				event.reply("It took you **" + responseTime + "ms** to respond.").queue();
				event.removeCooldown();
			}, 60, TimeUnit.SECONDS, () -> event.reply("Response timed out :stopwatch:").queue());	
		});
	}
	
	@Command(value="invites", aliases={"inv"}, description="View how many invites a user has in a server and where they are ranked in the whole server", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"invites", "invites @Shea#6653", "invites 402557516728369153", "invites Shea"})
	@BotPermissions({Permission.MANAGE_SERVER})
	public void invites(CommandEvent event, @Argument(value="user", nullDefault=true, endless=true) String userArgument) {
		User user;
		if (userArgument == null) {
			user = event.getAuthor();
		} else {
			user = ArgumentUtils.getUser(userArgument);
			if (user == null) {
				event.reply("I could not find that user :no_entry:").queue();
				return;
			}
		}
		
		event.getGuild().retrieveInvites().queue(invites -> {
			if (invites.isEmpty()) {
				event.reply("No invites have been created in this server :no_entry:").queue();
				return;
			}
			
			Map<Long, Integer> entries = new HashMap<>();
			int totalInvites = 0;
			for (Invite invite : invites) {
				if (invite.getInviter() != null) {
					entries.compute(invite.getInviter().getIdLong(), (key, value) -> value != null ? value + invite.getUses() : invite.getUses());
				} 
				
				totalInvites += invite.getUses();
			}

			if (!entries.containsKey(user.getIdLong())) {
				event.reply("**" + user.getAsTag() + "** has no invites :no_entry:").queue();
				return;
			}
			
			List<Entry<Long, Integer>> sortedEntries = new ArrayList<>(entries.entrySet());
			sortedEntries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
			
			int percentInvited = Math.round((((float) entries.get(user.getIdLong()) / totalInvites) * 100));
			String percent = percentInvited >= 1 ? String.valueOf(percentInvited) : "<1";
			for (int i = 0; i < sortedEntries.size(); i++) {
				Entry<Long, Integer> entry = sortedEntries.get(i);
				if (entry.getKey() == user.getIdLong()) {
					event.reply(String.format("%s has **%d** invites which means they have the **%s** most invites. They have invited **%s%%** of all users.",
							user.getAsTag(), entries.get(user.getIdLong()), GeneralUtils.getNumberSuffix(i + 1), percent)).queue();
					
					return;
				}
			}
		});
	}
	
	@Command(value="leaderboard invites", aliases={"lb invites", "invites lb", "inviteslb", "lbinvites", "invites leaderboard"}, description="View a leaderboard of users with the most invites", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"leaderboard invites"})
	@BotPermissions({Permission.MANAGE_SERVER, Permission.MESSAGE_EMBED_LINKS})
	public void leaderboardInvites(CommandEvent event) {
		event.getGuild().retrieveInvites().queue(invites -> {
			int totalInvites = 0;
			Map<Long, Integer> entries = new HashMap<>();
			for (Invite invite : invites) {
				if (invite.getInviter() != null) {
					entries.compute(invite.getInviter().getIdLong(), (key, value) -> value != null ? value + invite.getUses() : invite.getUses());
				}
				
				totalInvites += invite.getUses();
			}
			
			int newTotalInvites = totalInvites;
			
			List<Entry<Long, Integer>> entriesList = new ArrayList<>(entries.entrySet());
			entriesList.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
			PagedResult<Entry<Long, Integer>> paged = new PagedResult<>(entriesList)
					.setIncreasedIndex(true)
					.setAuthor("Invites Leaderboard", null, event.getGuild().getIconUrl())
					.setDeleteMessage(false)
					.setFunction(data -> {
						int percentInvited = Math.round(((float) data.getValue() / newTotalInvites) * 100);
						String percent = percentInvited >= 1 ? String.valueOf(percentInvited) : "<1";
						Member member = event.getGuild().getMemberById(data.getKey());
						String memberString = member == null ? String.valueOf(data.getKey()) : member.getUser().getAsTag();
						return String.format("`%s` - %,d %s (%s%%)", memberString, data.getValue(), data.getValue() == 1 ? "invite" : "invites", percent);
					});
			
			PagedUtils.getPagedResult(event, paged, 300, null);
		});
	}
	
	@Command(value="await", description="Notifies you when a user comes online")
	@Examples({"await @Shea#6653", "await Shea Joakim", "await 440996323156819968"})
	public void await(CommandEvent event, @Context Database database, @Argument(value="user(s)") String[] users) {
		List<Long> memberIds = new ArrayList<>();
		List<String> memberTags = new ArrayList<>();
		for (String user : users) {
			Member member = ArgumentUtils.getMember(event.getGuild(), user);
			if (member != null) {
				if (!member.getOnlineStatus().equals(OnlineStatus.OFFLINE)) {
					event.reply("**" + member.getUser().getAsTag() + "** is already online :no_entry:").queue();
					return;
				} else {
					if (!memberIds.contains(member.getIdLong())) {
						memberIds.add(member.getUser().getIdLong());
						memberTags.add(member.getUser().getAsTag());
					}
				}
			}
		}
		
		if (memberIds.isEmpty()) {
			event.reply("I could not find any of the users you provided :no_entry:").queue();
			return;
		}
		
		List<Long> dataUsers = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("await.users")).getEmbedded(List.of("await", "users"), Collections.emptyList());
		for (long memberId : dataUsers) {
			if (memberIds.contains(memberId)) {
				event.reply(event.getGuild().getMemberById(memberId).getUser().getAsTag() + " is already awaited :no_entry:").queue();
				return;
			}
		}
		
		database.updateUserById(event.getAuthor().getIdLong(), Updates.addEachToSet("await.users", memberIds), (result, exception) -> {
			if (exception != null) {
				exception.printStackTrace();
				event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
			} else {
				AwaitEvents.addUsers(event.getAuthor().getIdLong(), memberIds);
				event.reply("You will be notified when `" + String.join(", ", memberTags) + "` comes online <:done:403285928233402378>").queue();
			}
		});
	}
	
	@Command(value="join position", aliases={"joinposition"}, description="Shows you when a user joined/what user joined in a certain position")
	@Examples({"join position", "join position 5", "join position @Shea#6653"})
	public void joinPosition(CommandEvent event, @Argument(value="user | join position", endless=true, nullDefault=true) String argument) {
		Member member;
		Integer joinPosition = null;	
		List<Member> guildMembers = new ArrayList<>(event.getGuild().getMembers());

		if (argument == null) {
			member = event.getMember();
		} else {
			member = ArgumentUtils.getMember(event.getGuild(), argument);
			if (member == null) {
				try {
					joinPosition = Integer.parseInt(argument);
					if (joinPosition > guildMembers.size() || joinPosition < 1) {
						event.reply("The join position can not be more than the member count of the server or less than 1 :no_entry:").queue();
						return;
					}
				} catch(NumberFormatException e) {
					event.reply("I could not find that user :no_entry:").queue();
					return;
				}
			} 
		}
		
		guildMembers.sort((a, b) -> a.getTimeJoined().compareTo(b.getTimeJoined()));
		if (joinPosition != null) {
			member = guildMembers.get(joinPosition - 1);
			
			event.reply(String.format("**%s** was the %s user to join %s", member.getUser().getAsTag(), GeneralUtils.getNumberSuffix(joinPosition), event.getGuild().getName())).queue();
		} else {
			joinPosition = guildMembers.indexOf(member) + 1;
			
			event.reply(String.format("%s was the **%s** user to join %s", member.getUser().getAsTag(), GeneralUtils.getNumberSuffix(joinPosition), event.getGuild().getName())).queue();
		}
	}
	
	@Command(value="emote info", aliases={"emote", "emoji", "emoteinfo", "emoji info", "emojiinfo"}, description="Search up any emote that the bot can see and it'll return information on the desired emote", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"emote info doggo", "emote info :doggo:"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void emoteInfo(CommandEvent event, @Argument(value="emote") String argument) {
		Emote emote = ArgumentUtils.getEmote(event.getGuild(), argument);
		if (emote == null) {
			event.reply("I could not find that emote :no_entry:").queue();
			return;
		}
		
		EmbedBuilder embed = new EmbedBuilder()
				.setAuthor(emote.getName(), emote.getImageUrl(), emote.getImageUrl())
				.setThumbnail(emote.getImageUrl())
				.setTimestamp(emote.getTimeCreated())
				.setFooter("Created", null)
				.addField("ID", emote.getId(), false)
				.addField("Server", emote.getGuild().getName() + " (" + emote.getGuild().getId() + ")", false);
		
		if (event.getSelfMember().hasPermission(Permission.MANAGE_EMOTES)) {
			emote.getGuild().retrieveEmote(emote).queue(e -> {
				if (e.hasUser()) {
					embed.addField("Uploader", e.getUser().getAsTag(), false);
				}
				
				event.reply(embed.build()).queue();
			});
		} else {
			event.reply(embed.build()).queue();
		}
	}
	
	@Command(value="server emotes", aliases={"guild emotes", "serveremojis", "guildemojis", "guild emojis", "server emojis", "emote list", "emoji list", "emotelist", "emojilist"}, 
			description="View all the emotes within the current server", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"server emotes"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void serverEmotes(CommandEvent event) {
		List<Emote> emotes = new ArrayList<Emote>(event.getGuild().getEmotes());
		Collections.sort(emotes, (a, b) -> Boolean.compare(a.isAnimated(), b.isAnimated()));
		PagedResult<Emote> paged = new PagedResult<>(emotes)
				.setAuthor("Server Emotes", null, event.getGuild().getIconUrl())
				.setPerPage(15)
				.setIndexed(false)
				.setDeleteMessage(false)
				.setFunction(emote -> {
					return emote.getAsMention() + " - " + emote.getName();
				});
		
		PagedUtils.getPagedResult(event, paged, 300, null);
	}
	
	@Command(value="dbl search", aliases={"dbl", "dblsearch"}, description="Search up any bot on [discord bot list](https://discordbots.org)")
	@Examples({"dbl search Sx4", "dbl search 440996323156819968", "dbl search @Sx4#1617"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void discordBotList(CommandEvent event, @Argument(value="bot", endless=true, nullDefault=true) String argument) {
		String url;
		if (argument != null) {
			Matcher mentionMatch = ArgumentUtils.USER_MENTION_REGEX.matcher(argument);
			Matcher tagMatch = ArgumentUtils.USER_TAG_REGEX.matcher(argument);
			if (argument.matches("\\d+")) {
				url = "https://discordbots.org/api/bots?search=id:" + argument + "&sort=points";
			} else if (mentionMatch.matches()) {
				url = "https://discordbots.org/api/bots?search=id:" + mentionMatch.group(1) + "&sort=points";
			} else if (tagMatch.matches()) {
				url = "https://discordbots.org/api/bots?search=username:" + tagMatch.group(1) + "&discriminator:" + tagMatch.group(2) + "&sort=points";
			} else if (argument.length() <= 32) {
				url = "https://discordbots.org/api/bots?search=username:" + argument + "&sort=points";
			} else {
				event.reply("I could not find that bot :no_entry:").queue();
				return;
			}
		} else {
			url = "https://discordbots.org/api/bots?search=id:" + event.getSelfUser().getId();
		}
		
		Request request = new Request.Builder()
				.url(url)
				.addHeader("Authorization", TokenUtils.TOP_GG)
				.build();
		
		Sx4Bot.client.newCall(request).enqueue((Sx4Callback) response -> {
			JSONObject json = new JSONObject(response.body().string());
			
			JSONArray results = json.getJSONArray("results");
			if (results.length() == 0) {
				event.reply("I could not find that bot :no_entry:").queue();
				return;
			}
			
			JSONObject botData = results.getJSONObject(0);
			String botAvatarUrl = "https://cdn.discordapp.com/avatars/" + botData.getString("id") + "/" + botData.getString("avatar");
			String botInviteUrl = botData.getString("invite").contains("https://") ? botData.getString("invite") : "https://discord.com/oauth2/authorize?client_id=" + botData.getString("id") + "&scope=bot";
			String guildCount = botData.has("server_count") ? String.format("%,d", botData.getInt("server_count")) : "N/A";
			User owner = event.getShardManager().getUserById(botData.getJSONArray("owners").getString(0));
			EmbedBuilder embed = new EmbedBuilder()
					.setAuthor(botData.getString("username") + "#" + botData.getString("discriminator"), "https://discordbots.org/bot/" + botData.getString("id"), botAvatarUrl)
					.setThumbnail(botAvatarUrl)
					.setDescription((botData.getBoolean("certifiedBot") == true ? "<:certified:438392214545235978> | " : "") + botData.getString("shortdesc"))
					.addField("Guilds", guildCount, true)
					.addField("Prefix", botData.getString("prefix"), true)
					.addField("Library", botData.getString("lib"), true)
					.addField("Approval Date", ZonedDateTime.parse(botData.getString("date")).format(DateTimeFormatter.ofPattern("dd/MM/yy")), true)
					.addField("Monthly Votes", String.format("%,d :thumbsup:", botData.getInt("monthlyPoints")), true)
					.addField("Total Votes", String.format("%,d :thumbsup:", botData.getInt("points")), true)
					.addField("Invite", "**[Invite " + botData.getString("username") + " to your server](" + botInviteUrl + ")**", true);
			
			if (owner == null) {
				embed.setFooter("Primary Owner ID: " + botData.getJSONArray("owners").getString(0), null);
			} else {
				embed.setFooter("Primary Owner: " + owner.getAsTag(), owner.getEffectiveAvatarUrl());
			}
			
			event.reply(embed.build()).queue();
		});
	}
	
	@SuppressWarnings("unchecked")
	@Command(value="bot list", aliases={"botlist", "dbl bot list", "dblbotlist"}, description="Returns a list of bots in order of server count from [discord bot list](https://discordbots.org)", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"bot list", "bot list 5"})
	@Cooldown(value=5)
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void botList(CommandEvent event, @Argument(value="page", nullDefault=true) Integer pageNumber) {
		pageNumber = pageNumber == null ? 1 : pageNumber > 50 ? 50 : pageNumber < 1 ? 1 : pageNumber;
		int page = pageNumber;
		
		Request request = new Request.Builder()
				.url("https://discordbots.org/api/bots?sort=server_count&limit=500&fields=username,server_count,id")
				.addHeader("Authorization", TokenUtils.TOP_GG)
				.build();
		
		Sx4Bot.client.newCall(request).enqueue((Sx4Callback) response -> {
			JSONObject json;
			try {
				json = new JSONObject(response.body().string());
			} catch (JSONException | IOException e) {
				event.reply("Oops something went wrong there, try again :no_entry:").queue();
				return;
			}
			
			List<Object> results = json.getJSONArray("results").toList();
			PagedResult<Object> paged = new PagedResult<>(results)
					.setIncreasedIndex(true)
					.setPage(page)
					.setDeleteMessage(false)
					.setFunction(data -> {
						Map<String, Object> bot = (Map<String, Object>) data;
						return String.format("[%s](https://discordbots.org/bot/%s) - **%,d** servers", bot.get("username"), bot.get("id"), bot.get("server_count"));
					});
			
			PagedUtils.getPagedResult(event, paged, 300, null);
		});
	}
	
	@Command(value="ping", description="Shows the bots heartbeat and message response times", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"ping"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void ping(CommandEvent event) {
		event.getJDA().getRestPing().queue(time -> {
			long gatewayPing = event.getJDA().getGatewayPing();
			long lastGatewayPing = ConnectionEvents.getLastGatewayPing();
			long difference = gatewayPing - lastGatewayPing;
			event.reply(String.format("Pong! :ping_pong:\n\n:stopwatch: **%dms**\n:heartbeat: **%dms**", time, gatewayPing) + (lastGatewayPing == -1 ? "" : String.format(" (%s%dms)", difference < 0 ? "" : "+", difference))).queue();
		});
	}
	
	@Command(value="bots", aliases={"server bots", "guild bots"}, description="View all the bots in the current server", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"bots"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void bots(CommandEvent event) {
		List<Member> bots = new ArrayList<Member>();
		for (Member member : event.getGuild().getMembers()) {
			if (member.getUser().isBot()) {
				bots.add(member);
			}
		}
		
		Collections.sort(bots, (a, b) -> a.getUser().getName().compareTo(b.getUser().getName()));
		PagedResult<Member> paged = new PagedResult<>(bots)
				.setDeleteMessage(false)
				.setIndexed(false)
				.setPerPage(15)
				.setAuthor("Bot List (" + bots.size() + ")", null, event.getGuild().getIconUrl())
				.setFunction(bot -> {
					return bot.getUser().getAsTag();
				});
		
		PagedUtils.getPagedResult(event, paged, 300, null);
	}
	
	@Command(value="donate", description="Get Sx4s donation link", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"donate"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void donate(CommandEvent event) {
		EmbedBuilder embed = new EmbedBuilder()
				.setDescription("[Invite](https://discord.com/oauth2/authorize?client_id=" + event.getJDA().getSelfUser().getId() + "&permissions=8&scope=bot%20applications.commands)\n" +
						"[Support Server](https://discord.gg/PqJNcfB)\n[PayPal](https://www.paypal.me/SheaCartwright)\n[Patreon](https://www.patreon.com/Sx4)")
				.setAuthor("Donate!", null, event.getSelfUser().getEffectiveAvatarUrl())
				.setColor(Settings.EMBED_COLOUR);
		
		event.reply(embed.build()).queue();
	}
	
	@Command(value="support", aliases={"support server", "support guild"}, description="Get Sx4s support server invite link", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"support"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void support(CommandEvent event) {
		EmbedBuilder embed = new EmbedBuilder()
				.setDescription("[Invite](https://discord.com/oauth2/authorize?client_id=" + event.getJDA().getSelfUser().getId() + "&permissions=8&scope=bot%20applications.commands)\n" +
						"[Support Server](https://discord.gg/PqJNcfB)\n[PayPal](https://www.paypal.me/SheaCartwright)\n[Patreon](https://www.patreon.com/Sx4)")
				.setAuthor("Support!", null, event.getSelfUser().getEffectiveAvatarUrl())
				.setColor(Settings.EMBED_COLOUR);
		
		event.reply(embed.build()).queue();
	}
	
	@Command(value="invite", aliases={"inv"}, description="Get Sx4s invite link", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"invite"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void invite(CommandEvent event) {
		EmbedBuilder embed = new EmbedBuilder()
				.setDescription("[Invite](https://discord.com/oauth2/authorize?client_id=" + event.getJDA().getSelfUser().getId() + "&permissions=8&scope=bot%20applications.commands)\n" +
						"[Support Server](https://discord.gg/PqJNcfB)\n[PayPal](https://www.paypal.me/SheaCartwright)\n[Patreon](https://www.patreon.com/Sx4)")
				.setAuthor("Invite!", null, event.getSelfUser().getEffectiveAvatarUrl())
				.setColor(Settings.EMBED_COLOUR);
		
		event.reply(embed.build()).queue();
	}
	
	@Command(value="info", description="View some info about Sx4 and how it has become what it is now", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"info"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void info(CommandEvent event) {
		String description = String.format("Sx4 is a multipurpose all in one bot made to make your discord experience easier yet fun. Its features include Moderation, Utility, Economy, Music, Welcomer, and Logs.\r\n" + 
				"\r\n" + 
				"Sx4 began as a RED bot for the use in a single server. The users in the server were interested in the various features Sx4 offered and eventually requested if they could use Sx4 in their server as well. So, it was made public due to the demand.\r\n" + 
				"\r\n" + 
				"As a result, Sx4 was rewritten from scratch. All the modules and the base of the bot was rewritten in Python using Discord.py. \r\n" + 
				"\r\n" + 
				"As the bot grew, the older code took a toll on Sx4 and caused it to become slow and eat a lot of resources, so steps were taken make the image manipulation section of the bot into Java and improve certain sections of the general code.\r\n" + 
				"\r\n" + 
				"Since there was a lot of poor code in the Python version of Sx4, it was eventually fully rewritten into Java using JDA in order to make every command more efficient and eat fewer resources. Sx4 is currently 100%% Java and is in over %,d servers.",
				event.getShardManager().getGuilds().size());
		
		List<String> developers = new ArrayList<String>();
		for (long developerId : event.getCommandListener().getDevelopers()) {
			User developer = event.getShardManager().getUserById(developerId);
			if (developer != null) {
				if (developer.getId().equals("190551803669118976")) {
					developers.add("[" + developer.getAsTag() + "](https://github.com/21Joakim)");
				} else if (developer.getId().equals("402557516728369153")) {
					developers.add("[" + developer.getAsTag() + "](https://github.com/sx4-discord-bot)");
				} else {
					developers.add(developer.getAsTag());
				}
			}
		}
		
		EmbedBuilder embed = new EmbedBuilder()
				.setDescription(description)
				.setAuthor("Info!", null, event.getSelfUser().getEffectiveAvatarUrl())
				.setColor(Settings.EMBED_COLOUR)
				.addField("Stats", String.format("Ping: %dms\nServers: %,d\nUsers: %,d\nCommands: %d", event.getJDA().getGatewayPing(), event.getShardManager().getGuilds().size(), event.getShardManager().getUsers().size(), event.getCommandListener().getAllCommands().size()), true)
				.addField("Credits", "[Taiitoo#7419 (Host)](https://taiitoo.tk)\n[Victor#6359 (Ex Host)](https://vjserver.ddns.net)\n[ETLegacy](https://discord.gg/MqQsmF7)\n[Nexus](https://discord.gg/BEdrSaW)\n[MongoDB]"
						+ "(https://www.mongodb.com/)\n[JDA](https://github.com/DV8FromTheWorld/JDA)\n[Jockie Utils](https://github.com/21Joakim/Jockie-Utils)", true)
				.addField("Sx4", "Developers: " + String.join(", ", developers) + "\nInvite: [Click Here](https://discord.com/oauth2/authorize?client_id=440996323156819968&permissions=8&scope=bot%20applications.commands)\nSupport: "
						+ "[Click Here](https://discord.gg/PqJNcfB)\nDonate: [PayPal](https://paypal.me/SheaCartwright), [Patreon](https://www.patreon.com/Sx4)", true);
				
		event.reply(embed.build()).queue();
	}
	
	@Command(value="shard info", aliases={"shards", "shardinfo"}, description="Views Sx4s shards and some basic stats on them", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"shard info"})
	@Cooldown(value=7)
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void shardInfo(CommandEvent event) {
		long totalGuilds = event.getShardManager().getGuildCache().size(), totalUsers = event.getShardManager().getUserCache().size();
		
		List<JDA> shards = new ArrayList<>(event.getShardManager().getShards());
		shards.sort((a, b) -> Integer.compare(a.getShardInfo().getShardId(), b.getShardInfo().getShardId()));
		
		ShardInfo shardInfo = event.getJDA().getShardInfo();
		PagedResult<JDA> paged = new PagedResult<>(shards)
				.setPerPage(9)
				.setDeleteMessage(false)
				.setCustomFunction(page -> {
					EmbedBuilder embed = new EmbedBuilder();
					embed.setDescription(String.format("```prolog\nTotal Shards: %d\nTotal Servers: %,d\nTotal Users: %,d\nAverage Ping: %.0fms```", shardInfo.getShardTotal(), totalGuilds, totalUsers, event.getShardManager().getAverageGatewayPing()));
					embed.setAuthor("Shard Info!", null, event.getSelfUser().getEffectiveAvatarUrl());
					embed.setFooter("next | previous | go to <page> | cancel");
					embed.setColor(Settings.EMBED_COLOUR);
					
					List<JDA> pageShards = page.getArray();
					for (int i = page.getCurrentPage() * page.getPerPage() - page.getPerPage(); i < (page.getMaxPage() == page.getCurrentPage() ? pageShards.size() : page.getCurrentPage() * page.getPerPage()); i++) {
						JDA shard = pageShards.get(i);
						String currentShard = shardInfo.getShardId() == i ? "\\> " : "";
						embed.addField(currentShard + "Shard " + (i + 1), String.format("%,d servers\n%,d users\n%dms\n%s", shard.getGuilds().size(), shard.getUsers().size(), shard.getGatewayPing(), shard.getStatus().toString()), true);
					}
					
					return embed.build();
				});
		
		PagedUtils.getPagedResult(event, paged, 300, null);
	}
	
	@Command(value="mutual servers", aliases={"mutual guilds", "shared servers", "shared guilds", "sharedguilds", "sharedservers", "mutualguilds", "mutualservers"}, description="View the mutual guilds you have with Sx4")
	@Examples({"mutual servers", "mutual servers @Shea#6653", "mutual servers 402557516728369153", "mutual servers Shea"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void mutualGuilds(CommandEvent event, @Argument(value="user", endless=true, nullDefault=true) String argument) {
		User user;
		if (argument == null) {
			user = event.getAuthor();
		} else {
			Member member = ArgumentUtils.getMember(event.getGuild(), argument);
			if (member == null) {
				event.reply("I could not find that user :no_entry:").queue();
				return;
			} else {
				user = member.getUser();
			}
		}
		
		List<Guild> mutualGuilds = new ArrayList<>(event.getShardManager().getMutualGuilds(user));
		mutualGuilds.sort((a, b) -> a.getName().compareTo(b.getName()));
		PagedResult<Guild> paged = new PagedResult<>(mutualGuilds)
				.setDeleteMessage(false)
				.setAuthor("Mutual Guilds", null, user.getEffectiveAvatarUrl())
				.setIndexed(false)
				.setFunction(guild -> guild.getName());
		
		PagedUtils.getPagedResult(event, paged, 300, null);
	}
	
	@Command(value="servers", aliases={"guilds"}, description="View all the guilds Sx4 is in", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"servers"})
	@Developer
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void guilds(CommandEvent event) {
		List<Guild> guilds = new ArrayList<>(event.getShardManager().getGuilds());
		guilds.sort((a, b) -> Integer.compare(b.getMembers().size(), a.getMembers().size()));
		PagedResult<Guild> paged = new PagedResult<>(guilds)
				.setDeleteMessage(false)
				.setIndexed(false)
				.setEmbedColour(Settings.EMBED_COLOUR)
				.setAuthor("Servers (" + guilds.size() + ")", null, event.getSelfUser().getEffectiveAvatarUrl())
				.setFunction(guild -> String.format("`%s` - %,d members", guild.getName(), guild.getMembers().size()));
		
		PagedUtils.getPagedResult(event, paged, 300, null);
	}
	
	@Command(value="permissions", aliases={"perms"}, description="Gets the permissions of a role or user in the current server")
	@Examples({"permissions @Members", "permissions @Shea#6653"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void permissions(CommandEvent event, @Argument(value="role | user", endless=true, nullDefault=true) String argument) {
		IPermissionHolder permissionHolder = null;
		if (argument == null) {
			permissionHolder = event.getMember();
		} else {
			permissionHolder = ArgumentUtils.getMember(event.getGuild(), argument);
			if (permissionHolder == null) {
				permissionHolder = ArgumentUtils.getRole(event.getGuild(), argument);
			}
		}
		
		if (permissionHolder == null) {
			event.reply("I could not find that user/role :no_entry:").queue();
			return;
		}
		
		if (permissionHolder.getPermissions().isEmpty()) {
			event.reply("That user/role doesn't have any permissions :no_entry:").queue();
			return;
		}
		
		EmbedBuilder embed = new EmbedBuilder();
		if (permissionHolder instanceof Member) {
			Member member = (Member) permissionHolder;
			
			embed.setColor(member.getColor());
			embed.setAuthor(member.getUser().getName() + "'s Permissions", null, member.getUser().getEffectiveAvatarUrl());
		} else {
			Role role = (Role) permissionHolder;
			
			embed.setColor(role.getColor());
			embed.setAuthor(role.getName() + "'s Permissions", null, event.getGuild().getIconUrl());
		}
		
		embed.setDescription(String.join("\n", permissionHolder.getPermissions().stream().map(Permission::getName).collect(Collectors.toList())));
		
		event.reply(embed.build()).queue();
	}
	
	@Command(value="in role", aliases={"inrole"}, description="Shows a list of members in a specified role")
	@Examples({"in role Nitro Boosters", "in role @Nitro Boosters", "in role 611710855335247893"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void inRole(CommandEvent event, @Argument(value="role", endless=true) String argument) {
		Role role = ArgumentUtils.getRole(event.getGuild(), argument);
		if (role == null) {
			event.reply("I could not find that role :no_entry:").queue();
			return;
		}
		
		List<Member> members = event.getGuild().getMembersWithRoles(role);
		
		if (members.isEmpty()) {
			event.reply("There is no one in that role :no_entry:").queue();
			return;
		}
		
		Collections.sort(members, (a, b) -> a.getUser().getName().compareTo(b.getUser().getName()));
		PagedResult<Member> paged = new PagedResult<>(members)
				.setIndexed(false)
				.setPerPage(15)
				.setEmbedColour(role.getColor())
				.setAuthor("Members In " + role.getName() + " (" + members.size() + ")", null, event.getGuild().getIconUrl())
				.setDeleteMessage(false)
				.setFunction(member -> member.getUser().getAsTag());
		
		PagedUtils.getPagedResult(event, paged, 300, null);
	}
	
	@Command(value="member count", aliases={"membercount", "members", "mc"}, description="View statistics of different member counts depending on members statuses or if they're a bot or not", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"member count"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void memberCount(CommandEvent event) {
		Collector<Member, ?, List<Member>> toList = Collectors.toList();
		List<Member> members = event.getGuild().getMembers().stream().filter(member -> !member.getUser().isBot()).collect(toList);
		List<Member> bots = event.getGuild().getMembers().stream().filter(member -> member.getUser().isBot()).collect(toList);
		
		long onlineMembers = members.stream().filter(member -> !member.getOnlineStatus().equals(OnlineStatus.OFFLINE)).count();
		long onlineBots = bots.stream().filter(member -> !member.getOnlineStatus().equals(OnlineStatus.OFFLINE)).count();
		long onlineStatusMembers = members.stream().filter(member -> member.getOnlineStatus().equals(OnlineStatus.ONLINE)).count();
		long onlineStatusBots = bots.stream().filter(member -> member.getOnlineStatus().equals(OnlineStatus.ONLINE)).count();
		long idleStatusMembers = members.stream().filter(member -> member.getOnlineStatus().equals(OnlineStatus.IDLE)).count();
		long idleStatusBots = bots.stream().filter(member -> member.getOnlineStatus().equals(OnlineStatus.IDLE)).count();
		long dndStatusMembers = members.stream().filter(member -> member.getOnlineStatus().equals(OnlineStatus.DO_NOT_DISTURB)).count();
		long dndStatusBots = bots.stream().filter(member -> member.getOnlineStatus().equals(OnlineStatus.DO_NOT_DISTURB)).count();
		long offlineStatusMembers = members.stream().filter(member -> member.getOnlineStatus().equals(OnlineStatus.OFFLINE)).count();
		long offlineStatusBots = bots.stream().filter(member -> member.getOnlineStatus().equals(OnlineStatus.OFFLINE)).count();
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setThumbnail(event.getGuild().getIconUrl());
		embed.setAuthor(event.getGuild().getName() + "'s Member Count", null, event.getGuild().getIconUrl());
		embed.addField("Total Members (" + (members.size() + bots.size()) + ")", members.size() + " members\n" + bots.size() + " bots", true);
		embed.addField("Total Online Members (" + (onlineMembers + onlineBots) + ")", onlineMembers + " member" + (onlineMembers == 1 ? "" : "s") + "\n" + onlineBots + " bot" + (onlineBots == 1 ? "" : "s"), true);
		embed.addField("Members Online<:online:361440486998671381> (" + (onlineStatusMembers + onlineStatusBots) + ")", onlineStatusMembers + " member" + (onlineStatusMembers == 1 ? "" : "s") + "\n" + onlineStatusBots + " bot" + (onlineStatusBots == 1 ? "" : "s"), true);
		embed.addField("Members Idle<:idle:361440487233814528> (" + (idleStatusMembers + idleStatusBots) + ")", idleStatusMembers + " member" + (idleStatusMembers == 1 ? "" : "s") + "\n" + idleStatusBots + " bot" + (idleStatusBots == 1 ? "" : "s"), true);
		embed.addField("Members DND<:dnd:361440487179157505> (" + (dndStatusMembers + dndStatusBots) + ")", dndStatusMembers + " member" + (dndStatusMembers == 1 ? "" : "s") + "\n" + dndStatusBots + " bot" + (dndStatusBots == 1 ? "" : "s"), true);
		embed.addField("Members Offline<:offline:361445086275567626> (" + (offlineStatusMembers + offlineStatusBots) + ")", offlineStatusMembers + " member" + (offlineStatusMembers == 1 ? "" : "s") + "\n" + offlineStatusBots + " bot" + (offlineStatusBots == 1 ? "" : "s"), true);
		event.reply(embed.build()).queue();
	}
	
	@Command(value="role info", aliases={"roleinfo", "ri", "rinfo"}, description="Returns info on a specified role in the current server")
	@Examples({"role info @Members", "role info Members", "role info 345718366373150720"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void roleInfo(CommandEvent event, @Argument(value="role", endless=true) String argument) {
		Role role = ArgumentUtils.getRole(event.getGuild(), argument);
		if (role == null) {
			event.reply("I could not find that role :no_entry:").queue();
			return;
		}
		
		EmbedBuilder embed = new EmbedBuilder()
				.setAuthor(role.getName(), null, event.getGuild().getIconUrl())
				.setColor(role.getColor())
				.setThumbnail(event.getGuild().getIconUrl())
				.addField("Created", role.getTimeCreated().format(this.formatter), true)
				.addField("Role Colour", String.format("Hex: #%s\nRGB: %s", GeneralUtils.getHex(role.getColorRaw()), GeneralUtils.getRGB(role.getColorRaw())), true)
				.addField("Role Position", String.format("%s (Bottom to Top)\n%s (Top to Bottom)", GeneralUtils.getNumberSuffix(role.getPosition() + 2), GeneralUtils.getNumberSuffix(event.getGuild().getRoles().size() - 1 - role.getPosition())), true)
				.addField("Members In Role", String.valueOf(event.getGuild().getMembersWithRoles(role).size()), true)
				.addField("Hoisted Role", role.isHoisted() ? "Yes" : "No", true)
				.addField("Mentionable Role", role.isMentionable() ? "Yes" : "No", true)
				.addField("Managed Role", role.isManaged() ? "Yes" : "No", true)
				.addField("Role ID", role.getId(), true)
				.addField("Role Permissions", role.getPermissions().isEmpty() ? "None" : String.join("\n", role.getPermissions().stream().map(Permission::getName).collect(Collectors.toList())), false);
		
		event.reply(embed.build()).queue();
	}
	
	@Command(value="discriminator", aliases={"discrim"}, description="Search through all the users Sx4 can see by discriminator", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"discriminator", "discriminator 6653", "discriminator 0001"})
	@Cooldown(value=10, cooldownScope=ICooldown.Scope.GUILD)
	@Async
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void discriminator(CommandEvent event, @Argument(value="discriminator", nullDefault=true) String discriminator) {
		if (discriminator == null) {
			discriminator = event.getAuthor().getDiscriminator();
		} else {
			if (discriminator.startsWith("#")) {
				discriminator = discriminator.substring(1);
			}
			if (!discriminator.matches("\\d{4}")) {
				event.reply("That is not a valid discriminator, discriminators are the 4 numbers at the end of a users name :no_entry:").queue();
				return;
			}
		}
		
		List<User> users = new ArrayList<User>();
		for (User user : event.getShardManager().getUsers()) {
			if (discriminator.equals(user.getDiscriminator())) {
				users.add(user);
			}
		}
		
		if (users.isEmpty()) {
			event.reply("There are no users in that discriminator :no_entry:").queue();
			return;
		}
		
		users.sort((a, b) -> a.getName().compareTo(b.getName()));
		PagedResult<User> paged = new PagedResult<>(users)
				.setIndexed(false)
				.setDeleteMessage(false)
				.setPerPage(15)
				.setAuthor("Users In The Discriminator " + discriminator, null, event.getAuthor().getEffectiveAvatarUrl())
				.setFunction(user -> user.getAsTag());
		
		PagedUtils.getPagedResult(event, paged, 300, null);
	}
	
	@Command(value="avatar", aliases={"av"}, description="Gives you a specified users avatar")
	@Examples({"avatar", "avatar @Shea#6653", "avatar 402557516728369153", "avatar Shea"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void avatar(CommandEvent event, @Argument(value="user", endless=true, nullDefault=true) String argument) {
		Member member;
		if (argument == null) {
			member = event.getMember();
		} else {
			member = ArgumentUtils.getMember(event.getGuild(), argument);
			if (member == null) {
				event.reply("I could not find that user :no_entry:").queue();
				return;
			}
		}
		
		EmbedBuilder embed = new EmbedBuilder()
				.setColor(member.getColor())
				.setImage(member.getUser().getEffectiveAvatarUrl() + "?size=1024")
				.setAuthor(member.getUser().getAsTag(), member.getUser().getEffectiveAvatarUrl() + "?size=1024", member.getUser().getEffectiveAvatarUrl() + "?size=1024");
				
		event.reply(embed.build()).queue();
	}
	
	@Command(value="server avatar", aliases={"server av", "guild av", "guild avatar", "guildav", "serverav", "guildavatar", "guildicon", "guild icon", "servericon", "server icon", "sav", "gav"}, description="View the current servers icon", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"server avatar"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void serverIcon(CommandEvent event) {
		String url = event.getGuild().getIconUrl() == null ? null : event.getGuild().getIconUrl() + "?size=1024";
		
		EmbedBuilder embed = new EmbedBuilder()
				.setImage(url)
				.setAuthor(event.getGuild().getName(), url, url);
				
		event.reply(embed.build()).queue();
	}
	
	public class TriggerCommand extends Sx4Command {
		
		public TriggerCommand() {
			super("trigger");
			
			super.setDescription("Triggers make it so you can say a certain word and/or phrase and the bot will repeat something back of your choice");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
			super.setExamples("trigger toggle", "trigger case", "trigger stats");
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="formatting", aliases={"format", "formats"}, description="View the formats you are able to use to customize your triggers", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"trigger formatting"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void formatting(CommandEvent event) {
			String example = String.format("{user} - The users name + discriminator which executed the trigger (Shea#6653)\n"
					+ "{user.name} - The users name which executed the trigger (Shea)\n"
					+ "{user.mention} - The users mention which executed the trigger (@Shea#6653)\n"
					+ "{channel.name} - The current channels name\n"
					+ "{channel.mention} - The current channels mention\n\n"
					+ "Make sure to keep the **{}** brackets when using the formatting\n"
					+ "Example: `%strigger add hello Hello {user.name}!`", event.getPrefix());
			
			EmbedBuilder embed = new EmbedBuilder()
					.setAuthor("Trigger Formatting", null, event.getGuild().getIconUrl())
					.setDescription(example);
			
			event.reply(embed.build()).queue();
		}
		
		@Command(value="toggle", aliases={"enable", "disable"}, description="Toggle triggers on/off for the current server", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"trigger toggle"})
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		public void toggle(CommandEvent event, @Context Database database) {
			boolean enabled = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("trigger.enabled")).getEmbedded(List.of("trigger", "enabled"), true);
			database.updateGuildById(event.getGuild().getIdLong(), Updates.set("trigger.enabled", !enabled), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("Triggers are now " + (enabled ? "disabled" : "enabled") + " in this server <:done:403285928233402378>").queue();
				}
			});
		}
		
		@Command(value="case", aliases={"case sensitive", "case toggle", "casesensitive", "casetoggle"}, description="Toggles whether you want your triggers in the server to be case sensitive or not", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"trigger case"})
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		public void caseSensitive(CommandEvent event, @Context Database database) {
			boolean caseSensitive = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("trigger.case")).getEmbedded(List.of("trigger", "case"), true);
			database.updateGuildById(event.getGuild().getIdLong(), Updates.set("trigger.case", !caseSensitive), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("Triggers are " + (caseSensitive ? "no longer" : "now") + " case sensitive in this server <:done:403285928233402378>").queue();
				}
			});
		}
		
		@Command(value="list", description="Shows a list of all the triggers which are in the server", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"trigger list"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void list(CommandEvent event, @Context Database database) {
			List<Document> triggers = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("trigger.triggers")).getEmbedded(List.of("trigger", "triggers"),  Collections.emptyList());
			if (triggers.isEmpty()) {
				event.reply("This server has no triggers :no_entry:").queue();
				return;
			}
			
			PagedResult<Document> paged = new PagedResult<>(triggers)
					.setIndexed(false)
					.setPerPage(5)
					.setDeleteMessage(false)
					.setAuthor(event.getGuild().getName() + "'s Triggers", null, event.getGuild().getIconUrl())
					.setFunction(trigger -> {  
						int extraChars = 9 //'Trigger: ' length
								+ 10 //'Response: ' length
								+ 3; //3 new lines
						
						String triggerText = trigger.getString("trigger");
						String responseText = trigger.getString("response");
						int maxResponseLength = (MessageEmbed.TEXT_MAX_LENGTH/5) - extraChars - triggerText.length();
						return String.format("Trigger: %s\nResponse: %s\n", triggerText, responseText.substring(0, Math.min(maxResponseLength, responseText.length())));
					});
			
			PagedUtils.getPagedResult(event, paged, 300, null);
		}
		
		@Command(value="add", description="Add a trigger to the server")
		@Examples({"trigger add hi Hello there!", "trigger add bye Goodbye **{user.name}** :wave:"})
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		public void add(CommandEvent event, @Context Database database, @Argument(value="trigger") String triggerText, @Argument(value="response", endless=true) String responseText) {
			if (triggerText.toLowerCase().equals(responseText.toLowerCase())) {
				event.reply("You can't have a trigger with the same content as the response :no_entry:").queue();
				return;
			}
			
			List<Document> triggers = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("trigger.triggers")).getEmbedded(List.of("trigger", "triggers"),  Collections.emptyList());
			for (Document triggerData : triggers) {
				if (triggerData.getString("trigger").toLowerCase().equals(triggerText.toLowerCase())) {
					event.reply("There is already a trigger with that name :no_entry:").queue();
					return;
				}
			}
			
			Document triggerDocument = new Document("trigger", triggerText)
					.append("response", responseText);
			
			database.updateGuildById(event.getGuild().getIdLong(), Updates.push("trigger.triggers", triggerDocument), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("The trigger **" + triggerText + "** has been created <:done:403285928233402378>").queue();
				}
			});
		}
		
		@Command(value="edit", description="Edit a trigger on the server")
		@Examples({"trigger edit hi Hello there {user.name}!", "trigger edit bye Goodbye **{user.name}** have a good day :wave:"})
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		public void edit(CommandEvent event, @Context Database database, @Argument(value="trigger") String trigger, @Argument(value="response", endless=true) String response) {
			List<Document> triggers = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("trigger.triggers")).getEmbedded(List.of("trigger", "triggers"),  Collections.emptyList());
			for (Document triggerData : triggers) {
				String triggerText = triggerData.getString("trigger");
				if (trigger.toLowerCase().equals(triggerText.toLowerCase())) {
					UpdateOptions updateOptions = new UpdateOptions().arrayFilters(List.of(Filters.eq("trigger.trigger", triggerText)));
					database.updateGuildById(event.getGuild().getIdLong(), null, Updates.set("trigger.triggers.$[trigger].response", response), updateOptions, (result, exception) -> {
						if (exception != null) {
							exception.printStackTrace();
							event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
						} else {
							event.reply("The trigger **" + triggerText + "** has been edited <:done:403285928233402378>").queue();
						}
					});
					
					return;
				}
			}
			
			event.reply("I could not find that trigger :no_entry:").queue();
		}
		
		@Command(value="remove", description="Remove a trigger from the server")
		@Examples({"trigger remove hi", "trigger remove bye"})
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		public void remove(CommandEvent event, @Context Database database, @Argument(value="trigger", endless=true) String trigger) {
			List<Document> triggers = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("trigger.triggers")).getEmbedded(List.of("trigger", "triggers"),  Collections.emptyList());
			for (Document triggerData : triggers) {
				String triggerText = triggerData.getString("trigger");
				if (trigger.toLowerCase().equals(triggerText.toLowerCase())) {
					database.updateGuildById(event.getGuild().getIdLong(), Updates.pull("trigger.triggers", Filters.eq("trigger", triggerText)), (result, exception) -> {
						if (exception != null) {
							exception.printStackTrace();
							event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
						} else {
							event.reply("The trigger **" + triggerText + "** has been removed <:done:403285928233402378>").queue();
						}
					});
					
					return;
				}
			}
			
			event.reply("I could not find that trigger :no_entry:").queue();
		}
		
	}
	
	@Command(value="server roles", aliases={"serverroles", "guild roles", "guildroles", "roles"}, description="Shows a list of all of the current servers roles", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"server roles"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void serverRoles(CommandEvent event) {
		PagedResult<Role> paged = new PagedResult<>(event.getGuild().getRoles())
				.setAuthor("Server Roles", null, event.getGuild().getIconUrl())
				.setDeleteMessage(false)
				.setIncreasedIndex(true)
				.setPerPage(15)
				.setFunction(role -> role.getAsMention());
		
		PagedUtils.getPagedResult(event, paged, 300, null);
	}
	
	private final Map<OnlineStatus, String> statuses = new HashMap<>();
	{
		statuses.put(OnlineStatus.ONLINE, "Online<:online:361440486998671381>");
		statuses.put(OnlineStatus.IDLE, "Idle<:idle:361440487233814528>");
		statuses.put(OnlineStatus.DO_NOT_DISTURB, "DND<:dnd:361440487179157505>");
		statuses.put(OnlineStatus.OFFLINE, "Offline<:offline:361445086275567626>");
	}
	
	private String getBadges(EnumSet<UserFlag> flags) {
		StringBuilder description = new StringBuilder();
		
		int i = 0;
		for (UserFlag flag : flags) {
			switch (flag) {
				case HYPESQUAD_BRILLIANCE:
					description.append("<:brilliance:585763004495298575>");
					break;
				case HYPESQUAD_BRAVERY:
					description.append("<:bravery:585763004218343426>");
					break;
				case HYPESQUAD_BALANCE:
					description.append("<:balance:585763004574859273>");
					break;
				case VERIFIED_DEVELOPER:
					description.append("<:verified:712714152287928333>");
					break;
				case EARLY_SUPPORTER:
					description.append("<:supporter:585763690868113455>");
					break;
				case STAFF:
					description.append("<:staff_badge:712717245058646146>");
					break;
				case HYPESQUAD:
					description.append("<:hypesquad_events:585765895939424258>");
					break;
				case PARTNER:
					description.append("<:partner:314068430556758017>");
					break;
				case BUG_HUNTER_LEVEL_1:
					description.append("<:bughunter:585765206769139723>");
					break;
				default:
					break;
			}
			
			if (i != flags.size() - 1) {
				description.append(" ");
			}
			
			i++;
		}
		
		return description.toString();
	}
	
	@Command(value="user info", aliases={"userinfo", "ui", "uinfo"}, description="Returns info about a specified user")
	@Examples({"user info", "user info @Shea#6653", "user info 402557516728369153", "user info Shea"})
	@Async
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void userInfo(CommandEvent event, @Argument(value="user", endless=true, nullDefault=true) String argument) {
		Member member = null;
		EmbedBuilder embed = new EmbedBuilder();
		if (argument == null) {
			member = event.getMember();
		} else {
			member = ArgumentUtils.getMemberInfo(event.getGuild(), argument);
			if (member == null) {
				ArgumentUtils.getUserInfo(argument, user -> {
					if (user == null) {
						event.reply("I could not find that user :no_entry:").queue();
						return;
					}
					
					embed.setDescription(this.getBadges(user.getFlags()));
					embed.setAuthor(user.getAsTag(), null, user.getEffectiveAvatarUrl());
					embed.setThumbnail(user.getEffectiveAvatarUrl());
					embed.addField("User ID", user.getId(), true);
					embed.addField("Joined Discord", user.getTimeCreated().format(this.formatter), true);
					embed.addField("Bot", user.isBot() ? "Yes" : "No", true);
					event.reply(embed.build()).queue();
				});
				
				return;
			}
		}

		if (member != null) {
			StringBuilder description = new StringBuilder();
			for (Activity activity : member.getActivities()) {
				boolean emptyCustomStatus = activity.getName().equals("Custom Status");
				if (activity.getEmoji() != null && emptyCustomStatus) {
					description.append(activity.getEmoji().getAsMention() + " ");
					continue;
				}
				
				if (activity.getType() == ActivityType.CUSTOM_STATUS && !emptyCustomStatus) {
					description.append((activity.getEmoji() != null ? activity.getEmoji().getAsMention() : "") + " " + activity.getName());																		
				} else if (activity.isRich()) {
					RichPresence richPresence = activity.asRichPresence();
					if (activity.getName().equals("Spotify")) {
						String currentTime = TimeUtils.getTimeFormat(activity.getTimestamps().getElapsedTime(ChronoUnit.SECONDS));
						String totalTime = TimeUtils.getTimeFormat((activity.getTimestamps().getEnd()/1000) - (activity.getTimestamps().getStart()/1000));
						description.append(String.format("Listening to [%s by %s](https://open.spotify.com/track/%s) `[%s/%s]`", richPresence.getDetails(), richPresence.getState().split(";")[0], richPresence.getSyncId(), currentTime, totalTime));
					} else if (activity.getType() == ActivityType.STREAMING) {
						description.append(String.format("Streaming [%s](%s)", richPresence.getState(), richPresence.getUrl()));
					} else {
						description.append(GeneralUtils.title(activity.getType() == ActivityType.DEFAULT ? "Playing" : activity.getType().toString()) + " " + activity.getName() + (activity.getTimestamps() != null ? " for " + 
								TimeUtils.toTimeString(Clock.systemUTC().instant().getEpochSecond() - (activity.getTimestamps().getStart()/1000), ChronoUnit.SECONDS) : ""));  
					}
				} else {
					description.append(GeneralUtils.title(activity.getType().equals(ActivityType.DEFAULT) ? "Playing" : activity.getType().toString()) + " " + activity.getName() + (activity.getTimestamps() != null ? " for " + 
							TimeUtils.toTimeString(Clock.systemUTC().instant().getEpochSecond() - (activity.getTimestamps().getStart()/1000), ChronoUnit.SECONDS) : ""));	
				}
				
				break;
			}
			
			EnumSet<UserFlag> flags = member.getUser().getFlags();
			if (!flags.isEmpty()) {
				description.append("\n" + this.getBadges(flags));
			}
			
			StringBuilder onlineOn = new StringBuilder();
			for (ClientType client : member.getActiveClients()) {
				if (client.equals(ClientType.MOBILE)) {
					onlineOn.append("📱");
				} else if (client.equals(ClientType.DESKTOP)) {
					onlineOn.append("💻");
				} else if (client.equals(ClientType.WEB)) {
					onlineOn.append("🌐");
				}
			}
			
			embed.setAuthor(String.format("%s %s", member.getUser().getAsTag(), onlineOn.length() == 0 ? "" : onlineOn.toString()), null, member.getUser().getEffectiveAvatarUrl());
			embed.setThumbnail(member.getUser().getEffectiveAvatarUrl());
			
			if (description.length() != 0) {
				embed.setDescription(description.toString());
			}
			
			if (!event.getGuild().getMembers().contains(member)) {
				embed.addField("User ID", member.getUser().getId(), true);
				embed.addField("Joined Discord", member.getUser().getTimeCreated().format(this.formatter), true);
				embed.addField("Status", statuses.get(member.getOnlineStatus()), true);
				embed.addField("Bot", member.getUser().isBot() ? "Yes" : "No", true);
			} else { 
				List<Member> guildMembers = new ArrayList<>(event.getGuild().getMembers());
				guildMembers.sort((a, b) -> a.getTimeJoined().compareTo(b.getTimeJoined()));
				int joinPosition = guildMembers.indexOf(member) + 1;
				
				List<String> memberRoles = new ArrayList<>();
				for (Role role : member.getRoles()) {
					memberRoles.add(role.getAsMention());
				}
				
				embed.setColor(member.getColor());
				embed.setFooter("Join Position: " + GeneralUtils.getNumberSuffix(joinPosition), null);
				embed.addField("Joined Discord", member.getUser().getTimeCreated().format(this.formatter), true);
				embed.addField("Joined Server", member.getTimeJoined().format(this.formatter), true);
				embed.addField("Boosting Since", event.getGuild().getBoosters().contains(member) ? member.getTimeBoosted().format(this.formatter) : "Not Boosting", true);
				embed.addField("Nickname", member.getNickname() == null ? "None" : member.getNickname(), true);
				embed.addField("Discriminator", member.getUser().getDiscriminator(), true);
				embed.addField("Bot", member.getUser().isBot() ? "Yes" : "No", true);
				embed.addField("Status", statuses.get(member.getOnlineStatus()), true);
				embed.addField("User Colour", "#" + GeneralUtils.getHex(member.getColorRaw()), true);
				embed.addField("User ID", member.getUser().getId(), true);
				embed.addField("Highest Role", member.getRoles().isEmpty() ? event.getGuild().getPublicRole().getAsMention() : member.getRoles().get(0).getAsMention(), true);
				embed.addField("Number of Roles", String.valueOf(member.getRoles().size()), true);
				if (!member.getRoles().isEmpty()) {
					if (member.getRoles().size() <= 10) {
						embed.addField("Roles", String.join(", ", memberRoles), false);
					} else {
						int extraRoles = member.getRoles().size() - 10;
						embed.addField("Roles", String.join(", ", memberRoles.subList(0, 10)) + " and " + (extraRoles) + " more role" + (extraRoles == 1 ? "" : "s"), false);
					}
				} else {
					embed.addField("Roles", "No Roles", false);
				}
			}
		}
		
		event.reply(embed.build()).queue();
	}
	
	@Command(value="server info", aliases={"serverinfo", "si", "sinfo"}, description="Returns info about the current server", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"server info"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void serverInfo(CommandEvent event) {
		Collector<Member, ?, List<Member>> toList = Collectors.toList();
		List<Member> members = event.getGuild().getMembers().stream().filter(member -> !member.getUser().isBot()).collect(toList);
		List<Member> bots = event.getGuild().getMembers().stream().filter(member -> member.getUser().isBot()).collect(toList);
		List<Member> onlineMembers = members.stream().filter(member -> !member.getOnlineStatus().equals(OnlineStatus.OFFLINE)).collect(toList);
		List<Member> onlineBots = bots.stream().filter(member -> !member.getOnlineStatus().equals(OnlineStatus.OFFLINE)).collect(toList);
		int totalMembersSize = members.size() + bots.size();
		
		String contentFilter;
		if (event.getGuild().getExplicitContentLevel().equals(ExplicitContentLevel.ALL)) {
			contentFilter = "All Members";
		} else {
			contentFilter = GeneralUtils.title(event.getGuild().getExplicitContentLevel().name().replace("_", " "));
		}
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setAuthor(event.getGuild().getName(), null, event.getGuild().getIconUrl());
		embed.setThumbnail(event.getGuild().getIconUrl());
		embed.setDescription(event.getGuild().getName() + " was created on " + event.getGuild().getTimeCreated().format(this.formatter));
		embed.addField("Region", event.getGuild().getRegion().getName() + " " + (event.getGuild().getRegion().getEmoji() == null ? "" : event.getGuild().getRegion().getEmoji()), true);
		embed.addField("Total users/bots",  totalMembersSize + " user" + (totalMembersSize == 1 ? "" : "s") + "/bot" + (totalMembersSize == 1 ? "" : "s"), true);
		embed.addField("Users", members.size() + " user" + (members.size() == 1 ? "" : "s") + " (" + onlineMembers.size() + " Online)", true);
		embed.addField("Bots", bots.size() + " bot" + (bots.size() == 1 ? "" : "s") + " (" + onlineBots.size() + " Online)", true);
		embed.addField("Boosts", event.getGuild().getBoostCount() + " booster" + (event.getGuild().getBoostCount() == 1 ? "" : "s") + " (Tier " + event.getGuild().getBoostTier().getKey() + ")", true);
		embed.addField("Text Channels", String.valueOf(event.getGuild().getTextChannels().size()), true);
		embed.addField("Voice Channels", String.valueOf(event.getGuild().getVoiceChannels().size()), true);
		embed.addField("Categories", String.valueOf(event.getGuild().getCategories().size()), true);
		embed.addField("Verification Level", GeneralUtils.title(event.getGuild().getVerificationLevel().name()), true);
		embed.addField("AFK Timeout", TimeUtils.toTimeString(event.getGuild().getAfkTimeout().getSeconds(), ChronoUnit.SECONDS), true);
		embed.addField("AFK Channel", event.getGuild().getAfkChannel() == null ? "None" : event.getGuild().getAfkChannel().getName(), true);
		embed.addField("Explicit Content Filter", contentFilter, true);
		embed.addField("Roles", String.valueOf(event.getGuild().getRoles().size()), true);
		embed.addField("Owner", event.getGuild().getOwner().getUser().getAsTag(), true);
		embed.addField("Server ID", event.getGuild().getId(), true);
		
		event.reply(embed.build()).queue();
	}
	
	@Command(value="server stats", aliases={"serverstats", "guildstats", "guild stats"}, description="View the stats of the current server, includes member joined and messages sent in the past 24h", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"server stats"})
	public void serverStats(CommandEvent event, @Context Database database) {
		Document data = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("stats")).get("stats", Database.EMPTY_DOCUMENT);
		EmbedBuilder embed = new EmbedBuilder()
				.setAuthor(event.getGuild().getName() + " Stats", null, event.getGuild().getIconUrl())
				.addField("Users Joined Today", String.format("%,d", data.get("members", 0)), true)
				.addField("Messages Sent Today", String.format("%,d", data.get("messages", 0)), true);
		
		event.reply(embed.build()).queue();
	}
	
	private final long  megabyte = 1024L * 1024L;
	private final long gigabyte = this.megabyte * 1024L;
	
	@Command(value="stats", description="Shows you Sx4s current stats", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"stats"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	@Cooldown(value=20)
	@Async
	public void stats(CommandEvent event, @Context Database database) {
		long timestampNow = Clock.systemUTC().instant().getEpochSecond();
		Bson timeFilter = Filters.gte("timestamp", timestampNow - StatsEvents.DAY_IN_SECONDS);
		long commandsUsed = database.getCommandLogs().countDocuments(timeFilter);
		int guildsGained = database.getGuildsGained(timeFilter);
		
		List<Member> members = ArgumentUtils.getAllUniqueMembers();
		long onlineMembers = members.stream().filter(m -> !m.getOnlineStatus().equals(OnlineStatus.OFFLINE)).count();
		
		Runtime runtime = Runtime.getRuntime();
		double cpuUsage = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
		long totalMemory = ((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalPhysicalMemorySize();
		long memoryUsed = runtime.totalMemory() - runtime.freeMemory();
		StringBuilder memoryString = new StringBuilder();
		if (memoryUsed >= this.gigabyte) {
			double memoryUsedGb = (double) memoryUsed / this.gigabyte;
			memoryString.append(String.format("%.2fGiB/", memoryUsedGb));
		} else {
			double memoryUsedMb = (double) memoryUsed / this.megabyte;
			memoryString.append(String.format("%.0fMiB/", memoryUsedMb));
		}
		
		if (totalMemory >= this.gigabyte) {
			double totalMemoryGb = (double) totalMemory / this.gigabyte;
			memoryString.append(String.format("%.2fGiB ", totalMemoryGb));
		} else {
			double totalMemoryMb = (double) totalMemory / this.megabyte;
			memoryString.append(String.format("%.0fMiB ", totalMemoryMb));
		}
		
		memoryString.append(String.format("(%.1f%%)", ((double) memoryUsed / totalMemory) * 100));
		
		Document mongoData = database.getDatabase().runCommand(new Document("serverStatus", 1));
		double mongoUptime = mongoData.getDouble("uptime");
		Document latencies = mongoData.get("opLatencies", Document.class);
		Document reads = latencies.get("reads", Document.class), writes = latencies.get("writes", Document.class);
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setDescription("Bot ID: " + event.getSelfUser().getId());
		embed.setThumbnail(event.getSelfUser().getEffectiveAvatarUrl());
		embed.setAuthor(event.getSelfUser().getName() + " Stats", null, event.getSelfUser().getEffectiveAvatarUrl());
		embed.setFooter("Uptime: " + TimeUtils.toTimeString(ManagementFactory.getRuntimeMXBean().getUptime(), ChronoUnit.MILLIS) + " | Java " + System.getProperty("java.version"), null);
		embed.addField("Library", "JDA " + JDAInfo.VERSION + "\nJockie Utils " + JockieUtils.VERSION, true);
		embed.addField("Memory Usage", memoryString.toString(), true);
		embed.addField("CPU Usage", String.format("%.1f%%", cpuUsage), true);
		embed.addField("Threads", String.valueOf(Thread.activeCount()), true);
		embed.addField("Text Channels", String.valueOf(event.getShardManager().getTextChannelCache().size()), true);
		embed.addField("Voice Channels", String.valueOf(event.getShardManager().getVoiceChannelCache().size()), true);
		embed.addField("Servers Joined (Last 24h)", String.valueOf(guildsGained), true);
		embed.addField("Commands Used (Last 24h)", String.valueOf(commandsUsed), true);
		embed.addField("Average Execution Time", String.format("%.2fms", (double) Sx4CommandEventListener.getAverageExecutionTime() / 1000000), true);
		embed.addField("Database Queries", String.format("Average Reads Per Second: %,.2f\nAverage Writes Per Second: %,.2f", reads.getLong("ops") / mongoUptime, writes.getLong("ops") / mongoUptime), true);
		embed.addField("Servers", String.format("%,d", event.getShardManager().getGuildCache().size()), true);
		embed.addField(String.format("Users (%,d total)", members.size()), String.format("%,d Online\n%,d Offline", onlineMembers, members.size() - onlineMembers), true);
		
		event.reply(embed.build()).queue();
	}

	@Initialize(all=true, subCommands=true, recursive=true)
	public void initialize(CommandImpl command) {
		command.setCategory(Categories.GENERAL);
	}

}
