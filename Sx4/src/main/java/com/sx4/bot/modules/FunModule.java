package com.sx4.bot.modules;

import java.awt.Color;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.ws.rs.ForbiddenException;

import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.Binary;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.command.Command.Async;
import com.jockie.bot.core.command.Command.AuthorPermissions;
import com.jockie.bot.core.command.Command.BotPermissions;
import com.jockie.bot.core.command.Command.Cooldown;
import com.jockie.bot.core.command.Context;
import com.jockie.bot.core.command.ICommand.ContentOverflowPolicy;
import com.jockie.bot.core.command.Initialize;
import com.jockie.bot.core.command.impl.CommandEvent;
import com.jockie.bot.core.command.impl.CommandImpl;
import com.jockie.bot.core.module.Module;
import com.jockie.bot.core.option.Option;
import com.mongodb.client.FindIterable;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import com.sx4.bot.cache.DictionaryCache;
import com.sx4.bot.cache.GoogleSearchCache;
import com.sx4.bot.cache.GoogleSearchCache.GoogleSearchResult;
import com.sx4.bot.cache.SteamCache;
import com.sx4.bot.categories.Categories;
import com.sx4.bot.core.Sx4Bot;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEventListener;
import com.sx4.bot.database.Database;
import com.sx4.bot.interfaces.Examples;
import com.sx4.bot.interfaces.Sx4Callback;
import com.sx4.bot.settings.Settings;
import com.sx4.bot.starboard.Starboard;
import com.sx4.bot.starboard.StarboardMessage;
import com.sx4.bot.utils.ArgumentUtils;
import com.sx4.bot.utils.EconomyUtils;
import com.sx4.bot.utils.FunUtils;
import com.sx4.bot.utils.GeneralUtils;
import com.sx4.bot.utils.HelpUtils;
import com.sx4.bot.utils.PagedUtils;
import com.sx4.bot.utils.PagedUtils.PagedResult;
import com.sx4.bot.utils.StarboardUtils;
import com.sx4.bot.utils.TimeUtils;
import com.sx4.bot.utils.TokenUtils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


@Module
public class FunModule {
	
	private static final Random RANDOM = new Random();
	
	private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd LLL yyyy HH:mm");
	
	public enum Direction {
		NORTH(0),
		NORTH_EAST(45),
		EAST(90),
		SOUTH_EAST(135),
		SOUTH(180),
		SOUTH_WEST(225),
		WEST(270),
		NORTH_WEST(315);
		
		private int degrees;
		
		private Direction(int degrees) {
			this.degrees = degrees;
		}
		
		public String getName() {
			return GeneralUtils.title(this.name()).replace("_", " ").trim();
		}

		public int getDegrees() {
			return this.degrees;
		}

		public static Direction getDirection(int degrees) {
			Direction closest = null;
			int closestInt = 360;
			for (Direction direction : Direction.values()) {
				int difference = Math.abs(direction.getDegrees() - degrees);
				if (closest == null) {
					closest = direction;
					closestInt = difference;
				} else {
					if (closestInt > difference) {
						closest = direction;
						closestInt = difference;
					}
				}
			}

			return closest;
		}
	}
	
	public class YouTubeNotificationCommand extends Sx4Command {
		
		public YouTubeNotificationCommand() {
			super("youtube notification");
			
			super.setDescription("Subscribe to a youtube channel so anytime it uploads it's sent in a channel of your choice");
			super.setAliases("yt notif", "yt notification", "youtube notif");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
			super.setExamples("youtube notification add", "youtube notification remove", "youtube notification list");
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="add", description="Add a youtube notification to be posted to a specific channel when the user uploads")
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void add(CommandEvent event, @Context Database database, @Argument(value="channel") String channelArgument, @Argument(value="youtube channel", endless=true) String youtubeChannelArgument) {
			TextChannel channel = ArgumentUtils.getTextChannel(event.getGuild(), channelArgument);
			if (channel == null) {
				event.reply("I could not find that channel :no_entry:").queue();
				return;
			}
			
			Request channelRequest = new Request.Builder()
					.url("https://www.googleapis.com/youtube/v3/search?key=" + TokenUtils.YOUTUBE + "&q=" + youtubeChannelArgument + "&part=id&maxResults=1")
					.build();
			
			Sx4Bot.client.newCall(channelRequest).enqueue((Sx4Callback) channelResponse -> {
				JSONObject data = new JSONObject(channelResponse.body().string());
				
				JSONArray items = data.getJSONArray("items");
				if (items.isEmpty()) {
					event.reply("I could not find that youtube channel :no_entry:").queue();
					return;
				}
				
				String channelId = items.getJSONObject(0).getJSONObject("id").getString("channelId");
				
				Document notificationData = new Document("uploaderId", channelId)
						.append("channelId", channel.getIdLong())
						.append("guildId", event.getGuild().getIdLong());
				
				List<Document> notifications = database.getNotifications(Filters.eq("uploaderId", channelId), Projections.include("channelId")).into(new ArrayList<>());
				if (notifications.isEmpty() && !Sx4Bot.getYouTubeManager().hasResubscription(channelId)) {
					RequestBody body = new MultipartBody.Builder()
							.addFormDataPart("hub.mode", "subscribe")
							.addFormDataPart("hub.topic", "https://www.youtube.com/xml/feeds/videos.xml?channel_id=" + channelId)
							.addFormDataPart("hub.callback", "http://" + Settings.DOMAIN + ":" + Settings.PORT + "/api/v1/youtube")
							.addFormDataPart("hub.verify", "sync")
							.addFormDataPart("hub.verify_token", TokenUtils.YOUTUBE)
							.setType(MultipartBody.FORM)
							.build();
					
					Request request = new Request.Builder()
							.url("https://pubsubhubbub.appspot.com/subscribe")
							.post(body)
							.build();
					
					Sx4Bot.client.newCall(request).enqueue((Sx4Callback) response -> {
						if (response.isSuccessful()) {
							database.insertNotification(notificationData, (result, exception) -> {
								if (exception != null) {
									exception.printStackTrace();
									event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
								} else {
									event.reply("Notifications will now be sent in " + channel.getAsMention() + " when that user uploads <:done:403285928233402378>").queue();
								}
							});
						} else {
							event.reply("Oops something went wrong there, try again. If this repeats report this to my developer (Code: " + response.code() + ") :no_entry:").queue();
						}
					});
				} else {
					for (Document notification : notifications) {
						if (notification.getLong("channelId") == channel.getIdLong()) {
							event.reply("You already have a notification for that user in that channel :no_entry:").queue();
							return;
						}
					}
					
					database.insertNotification(notificationData, (result, exception) -> {
						if (exception != null) {
							exception.printStackTrace();
							event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
						} else {
							event.reply("Notifications will now be sent in " + channel.getAsMention() + " when that user uploads <:done:403285928233402378>").queue();
						}
					});
				}
			});
		}
		
		@Command(value="remove", description="Removes a notification from a channel you had setup prior for a youtube channel")
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void remove(CommandEvent event, @Context Database database, @Argument(value="channel") String channelArgument, @Argument(value="youtube channel", endless=true) String youtubeChannelArgument) {
			TextChannel channel = ArgumentUtils.getTextChannel(event.getGuild(), channelArgument);
			if (channel == null) {
				event.reply("I could not find that channel :no_entry:").queue();
				return;
			}
			
			Request channelRequest = new Request.Builder()
					.url("https://www.googleapis.com/youtube/v3/search?key=" + TokenUtils.YOUTUBE + "&q=" + youtubeChannelArgument + "&part=id&maxResults=1")
					.build();
			
			Sx4Bot.client.newCall(channelRequest).enqueue((Sx4Callback) channelResponse -> {
				JSONObject data = new JSONObject(channelResponse.body().string());
				
				JSONArray items = data.getJSONArray("items");
				if (items.isEmpty()) {
					event.reply("I could not find that youtube channel :no_entry:").queue();
					return;
				}
				
				String channelId = items.getJSONObject(0).getJSONObject("id").getString("channelId");
				
				database.deleteNotification(Filters.and(Filters.eq("uploaderId", channelId), Filters.eq("channelId", channel.getIdLong())), (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						if (result.getDeletedCount() == 1) {
							event.reply("Notifications will no longer be sent in " + channel.getAsMention() + " when that user uploads <:done:403285928233402378>").queue();
						} else {
							event.reply("You do not have a notification for that user in " + channel.getAsMention() + " :no_entry:").queue();
						}
					}
				});
			});
		}
		
	}
	
	public class StarboardCommand extends Sx4Command {
		
		public StarboardCommand() {
			super("starboard");
			
			super.setDescription("Set up a starboard in your server so you can star eachothers messages");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
			super.setExamples("starboard toggle", "starboard channel", "starboard top");
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="toggle", aliases={"enable", "disable"}, description="enable/disable the starboard in the current server")
		@Examples({"starboard toggle"})
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void toggle(CommandEvent event, @Context Database database) {
			boolean enabled = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("starboard.enabled")).getEmbedded(List.of("starboard", "enabled"), false);
			database.updateGuildById(event.getGuild().getIdLong(), Updates.set("starboard.enabled", !enabled), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("Starboard is now " + (enabled ? "disabled" : "enabled") + " in this server <:done:403285928233402378>").queue();
				}
			});
		}
		
		@Command(value="channel", description="Set the channel for the starboard messages to be sent to")
		@Examples({"starboard channel", "starboard channel #general", "starboard channel general", "starboard channel 344091594972069888"})
		@AuthorPermissions(Permission.MANAGE_SERVER)
		public void channel(CommandEvent event, @Context Database database, @Argument(value="channel", endless=true, nullDefault=true) String channelArgument) {
			TextChannel channel;
			if (channelArgument == null) {
				channel = event.getTextChannel();
			} else {
				channel = ArgumentUtils.getTextChannel(event.getGuild(), channelArgument);
				if (channel == null) {
					event.reply("I could not find that channel :no_entry:").queue();
					return;
				}
			}
			
			Long channelId = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("starboard.channelId")).getEmbedded(List.of("starboard", "channelId"), Long.class);
			if (channelId != null && channelId == channel.getIdLong()) {
				event.reply("The starboard channel is already set to " + channel.getAsMention() + " :no_entry:").queue();
				return;
			}
			
			database.updateGuildById(event.getGuild().getIdLong(), Updates.set("starboard.channelId", channel.getIdLong()), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("The starboard channel has been set to " + channel.getAsMention() + " <:done:403285928233402378>").queue();
				}
			});
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="set", description="Set a specific message to be changed to when a message reaches a certain amount of stars", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"starboard set 5 **{star}** :fireworks:", "starboard set 1 We reached **{star}** :star:, we only need **{stars.next.until}** more :star: until our next milestone"})
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void set(CommandEvent event, @Context Database database, @Argument(value="stars") int stars, @Argument(value="message", endless=true) String message) {
			Bson update = null;
			List<Bson> arrayFilters = null;
			
			List<Document> configuration = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("starboard.configuration")).getEmbedded(List.of("starboard", "configuration"), List.class);
			if (configuration == null) {
				boolean updated = false;
				Document newData = new Document("id", stars).append("message", message);
				
				List<Document> newConfiguration = new ArrayList<>(StarboardUtils.DEFAULT_STARBOARD_CONFIGURATION);
				for (Document star : newConfiguration) {
					if (star.getInteger("id") == stars) {
						if (message.equals(star.getString("message"))) {
							event.reply("The message for " + stars + " star" + (stars == 1 ? "" : "s") + " is already set to that message :no_entry:").queue();
							return;
						}
						
						newConfiguration.remove(star);
						newConfiguration.add(newData);
						updated = true;
						
						break;
					}
				}
				
				if (!updated) {
					newConfiguration.add(newData);
				}
				
				update = Updates.set("starboard.configuration", newConfiguration);
			} else {
				for (Document star : configuration) {
					if (star.getInteger("id") == stars) {
						if (message.equals(star.getString("message"))) {
							event.reply("The message for " + stars + " star" + (stars == 1 ? "" : "s") + " is already set to that message :no_entry:").queue();
							return;
						}
						
						update = Updates.set("starboard.configuration.$[star].message", message);
						arrayFilters = List.of(Filters.eq("star.id", stars));
					}
				}
				
				if (update == null) {
					update = Updates.push("starboard.configuration", new Document("id", stars).append("message", message));
				}
			}
			
			UpdateOptions updateOptions = new UpdateOptions().arrayFilters(arrayFilters).upsert(true);
			database.updateGuildById(event.getGuild().getIdLong(), null, update, updateOptions, (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("After a message gets **" + stars + "** star" + (stars == 1 ? "" : "s") + " it will now display that message <:done:403285928233402378>").queue();
				}
			});
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="remove", description="Removes a starboard message when you reaching a sepecific amount of stars", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"starboard remove 5", "starboard remove 1"})
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void remove(CommandEvent event, @Context Database database, @Argument(value="stars") int stars) {
			Bson update = null;
			
			List<Document> configuration = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("starboard.configuration")).getEmbedded(List.of("starboard", "configuration"), List.class);
			if (configuration == null) {
				boolean updated = false;
				
				List<Document> newConfiguration = new ArrayList<>(StarboardUtils.DEFAULT_STARBOARD_CONFIGURATION);
				for (Document star : newConfiguration) {
					if (star.getInteger("id") == stars) {
						newConfiguration.remove(star);
						updated = true;
						break;
					}
				}
				
				if (!updated) {
					event.reply("There is no message set when a message reaches that amount of stars :no_entry:").queue();
					return;
				}
				
				update = Updates.set("starboard.configuration", newConfiguration);
			} else {
				for (Document star : configuration) {
					if (star.getInteger("id") == stars) { 
						if (configuration.size() == 1) {
							event.reply("This is the last message you have setup for the starboard, if you want to go back to the default one use `" + event.getPrefix() + "starboard reset` :no_entry:").queue();
							return;
						}
						
						update = Updates.pull("starboard.configuration", Filters.eq("id", stars));
					}
				}
				
				if (update == null) {
					event.reply("There is no message set when a message reaches that amount of stars :no_entry:").queue();
					return;
				}
			}
			
			database.updateGuildById(event.getGuild().getIdLong(), update, (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("After a message gets **" + stars + "** star" + (stars == 1 ? "" : "s") + " it will no longer display that message <:done:403285928233402378>").queue();
				}
			});
		}
		
		@Command(value="reset", description="Resets your configuration for the starboard messages and puts them back to the default", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"starboard reset"})
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void reset(CommandEvent event, @Context Database database) {
			event.reply(event.getAuthor().getName() + ", are you sure you want to reset your starboard configuration back to the default?").queue($ -> {
				PagedUtils.getConfirmation(event, 60, event.getAuthor(), confirmation -> {
					if (confirmation) {
						List<Document> configuration = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("starboard.configuration")).getEmbedded(List.of("starboard", "configuration"), Collections.emptyList());
						if (configuration.isEmpty()) {
							event.reply("Your starboard messages are already set to the default :no_entry:").queue();
							return;
						} else {
							database.updateGuildById(event.getGuild().getIdLong(), Updates.unset("starboard.configuration"), (result, exception) -> {
								if (exception != null) {
									exception.printStackTrace();
									event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
								} else {
									event.reply("Your starboard configuration has been reset <:done:403285928233402378>").queue();
								}
							});
						}
					} else {
						event.reply("Cancelled <:done:403285928233402378>").queue();
					}
				});
			});
		}
		
		@Command(value="delete", description="Deletes a starboard message and blacklists it from reappearing as a starred message", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"starboard delete 643110427487633418"})
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void delete(CommandEvent event, @Context Database database, @Argument(value="message id") long messageId) {
			Document data = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("starboard.deleted", "starboard.messages")).get("starboard", Database.EMPTY_DOCUMENT);
			Starboard starboard = new Starboard(data);

			if (starboard.isDeletedMessage(messageId)) {
				event.reply("That message has already been deleted and blacklisted :no_entry:").queue();
				return;
			}
			
			StarboardMessage message = starboard.getMessageById(messageId);
			if (message != null) {
				database.updateGuildById(event.getGuild().getIdLong(), Updates.combine(Updates.pull("starboard.messages", Filters.eq("id", message.getMessageId())), Updates.addToSet("starboard.deleted", message.getMessageId())), (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						if (message.hasStarboard()) {
							TextChannel channel = message.getChannel(event.getGuild());
							if (channel != null) {
								channel.deleteMessageById(message.getStarboardId()).queue(null, e -> {});
							}
						}
						
						event.reply("That message has been deleted and blacklisted <:done:403285928233402378>").queue();
					}
				});
			} else {
				event.reply("That message has not been starred :no_entry:").queue();
			}
		}
		
		@Command(value="top", description="View the top starred messages in this server", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"starboard top"})
		public void top(CommandEvent event, @Context Database database) {
			List<Document> messages = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("starboard.messages")).getEmbedded(List.of("starboard", "messages"), Collections.emptyList());
			if (messages.isEmpty()) {
				event.reply("No messages have been starred in this server :no_entry:").queue();
				return;
			}
			
			messages.sort((a, b) -> -Integer.compare(a.getList("stars", Long.class).size(), b.getList("stars", Long.class).size()));
			PagedResult<Document> paged = new PagedResult<>(messages)
					.setDeleteMessage(false)
					.setEmbedColour(StarboardUtils.STARBOARD_COLOUR)
					.setAuthor("Top Stars", null, event.getGuild().getIconUrl())
					.setFunction(data -> {
						List<Long> stars = data.getList("stars", Long.class);
						User author = event.getShardManager().getUserById(data.getLong("authorId"));
						return String.format("[%s - **%d :star:**](https://discordapp.com/channels/%s/%d/%d)", author == null ? data.getLong("authorId") : author.getAsTag(), stars.size(), event.getGuild().getId(), data.getLong("channelId"), data.getLong("id"));
					});
			
			PagedUtils.getPagedResult(event, paged, 300, null);
		}
		
		@Command(value="list", description="Lists your starboard messages you have per star milestone", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"starboard list"})
		public void list(CommandEvent event, @Context Database database) {
			List<Document> configuration = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("starboard.configuration")).getEmbedded(List.of("starboard", "configuration"), StarboardUtils.DEFAULT_STARBOARD_CONFIGURATION);
			configuration.sort((a, b) -> Integer.compare(a.getInteger("id"), b.getInteger("id")));
			
			PagedResult<Document> paged = new PagedResult<>(configuration)
					.setAuthor("Starboard Configuration", null, event.getGuild().getIconUrl())
					.setDeleteMessage(false)
					.setIndexed(false)
					.setPerPage(5)
					.setFunction(data -> "Star #" + data.getInteger("id") + ": `" + data.getString("message") + "`");
			
			PagedUtils.getPagedResult(event, paged, 300, null);
		}
		
		@Command(value="formatting", aliases={"format", "formats"}, description="View the formats you can use for your your starboard messages", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"starboard formatting"})
		public void formatting(CommandEvent event) {
			EmbedBuilder embed = new EmbedBuilder();
			embed.setAuthor("Starboard Formatting", null, event.getGuild().getIconUrl());
			embed.setColor(Settings.EMBED_COLOUR);
			embed.setDescription(
					"{stars} = The amount of stars the message currently has\n" +
					"{stars.string} = \"star\" if {stars} is 1 else \"stars\"\n" +
					"{stars.suffix} = The suffixed version of {stars} for example 2nd or 5th\n" +
					"{stars.next} = The amount of stars of the next milestone, 0 if there isn't one\n" +
					"{stars.next.string} = \"star\" if {stars.next} is 1 else \"stars\"\n" +
					"{stars.next.suffix} = The suffixed version of {stars.next} for example 2nd or 5th\n" +
					"{stars.next.until} = The amount of stars needed till the next milestone\n" +
					"{stars.next.until.string} = \"star\" if {stars.next.until} is 1 else \"stars\"\n" +
					"{stars.next.until.suffix} = The suffixed version of {stars.next.until} for example 2nd or 5th\n" +
					"{channel.name} = The name of the channel the message is from\n" +
					"{channel.mention} = The mention of the channel the message is from\n" +
					"{message.id} = The id of the message being starred\n" +
					"{user} = The user tag of the latest user which reacted to the message\n" +
					"{user.name} = The user name of the latest user which reacted to the message\n" +
					"{user.mention} = The user mention of the latest user which reacted to the message\n" +
					"**Make sure you keep the {} brackets in the message**\n\n" +
					"Example: `" + event.getPrefix() + "starboard set 1 We reached **{star}** :star:, we only need **{stars.next.until}** more :star: until our next milestone.`"
			);
			
			event.reply(embed.build()).queue();
		}
		
	}
	
	/*public class UnoCommand extends Sx4Command {
		
		private List<UnoSession> unoSessions = new ArrayList<>();
		
		public UnoSession getUnoSession(TextChannel channel) {
			for (UnoSession unoSession : this.unoSessions) {
				if (unoSession.getChannelId() == channel.getIdLong()) {
					return unoSession;
				}
			}
			
			return null;
		}
		
		public UnoCommand() {
			super("uno");
			
			super.setDescription("Play uno against your friends, players: 2-4");
			super.setContentOverflowPolicy(ContentOverflowPolicy.IGNORE);
		}
		
		public void onCommand(CommandEvent event, @Argument(value="invite only", nullDefault=true) Boolean inviteOnly) {
			UnoSession currentUnoSession = this.getUnoSession(event.getTextChannel());
			if (currentUnoSession == null) {
				UnoSession unoSession = new UnoSession(event.getTextChannel(), event.getMember(), inviteOnly == null ? false : inviteOnly);
				unoSessions.add(unoSession);
				
				StringBuilder reply = new StringBuilder("I have created your uno game <:done:403285928233402378>\n\n");
				if (inviteOnly != null && inviteOnly) {
					reply.append("Invite friends to your uno game with `" + event.getPrefix() + "uno invite <user | role>` then they can use `" + event.getPrefix() + "uno join` to join.");
				} else {
					reply.append("Anyone can use `" + event.getPrefix() + "uno join` to join.");
				}
				
				event.reply(reply.toString()).queue();
			} else {
				event.reply("An uno game is already set up in this channel :no_entry:").queue();
			}
		}
		
		@Command(value="invite", description="Invite a user/role to your uno game")
		public void invite(CommandEvent event, @Argument(value="user | role", endless=true) String argument) {
			UnoSession unoSession = this.getUnoSession(event.getTextChannel());
			if (unoSession == null) {
				event.reply("An uno game isn't set up in this channel :no_entry:").queue();
				return;
			}
			
			if (unoSession.getOwnerId() != event.getAuthor().getIdLong()) {
				event.reply("You are not the owner of the uno game in this channel :no_entry:").queue();
				return;
			}
			
			if (!unoSession.isInviteOnly()) {
				event.reply("The uno game is not invite only :no_entry:").queue();
				return;
			}
			
			Role role = ArgumentUtils.getRole(event.getGuild(), argument);
			Member member = ArgumentUtils.getMember(event.getGuild(), argument);
			
			if (role != null) {
				unoSession.invite(role);
				event.reply("I have invited every user within the role `" + role.getName() + "` <:done:403285928233402378>").queue();
			} else if (member != null) {
				unoSession.invite(member);
				event.reply("I have invited **" + member.getUser().getAsTag() + "** <:done:403285928233402378>").queue();
			} else {
				event.reply("I could not find that user or role :no_entry:").queue();
			}
		}
		
		@Command(value="join", description="Join an uno game which has not started in the current channel", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		public void join(CommandEvent event) {
			UnoSession unoSession = this.getUnoSession(event.getTextChannel());
			if (unoSession == null) {
				event.reply("An uno game isn't set up in this channel :no_entry:").queue();
				return;
			}
			
			if (unoSession.isInviteOnly() && !unoSession.isInvited(event.getMember())) {
				event.reply("You have not been invited to the uno game in this channel :no_entry:").queue();
				return;
			}
			
			if (unoSession.getPlayers().size() == 4) {
				event.reply("The uno game in this channel is currently full :no_entry:").queue();
				return;
			}
			
			unoSession.addPlayer(event.getMember());
			event.reply("You have joined the uno game in this channel, wait for the owner to start the game <:done:403285928233402378>").queue();
		}
		
		@Command(value="start", description="Start the uno game in the current channel", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		public void start(CommandEvent event) {
			UnoSession unoSession = this.getUnoSession(event.getTextChannel());
			if (unoSession == null) {
				event.reply("An uno game isn't set up in this channel :no_entry:").queue();
				return;
			}
			
			if (unoSession.getOwnerId() != event.getAuthor().getIdLong()) {
				event.reply("You do not own the uno game in the current channel :no_entry:").queue();
				return;
			}
			
			if (unoSession.getPlayers().size() < 2) {
				event.reply("You need at least 2 players to start an uno game :no_entry:").queue();
				return;
			}
			
			event.reply("The uno game will now start, everyone will be messaged with their starting cards. Good luck!").queue(message -> {
				try {
					unoSession.start(event);
				} catch(IllegalArgumentException e) {
					e.printStackTrace();
					event.reply("Too many players left, the game has been cancelled :no_entry:").queue();
					this.unoSessions.remove(unoSession);
				}
			});
		}
		
	}*/
	
	@Command(value="profile", description="View a users or your profile")
	@Examples({"profile", "profile @Shea#6653", "profile 402557516728369153", "profile Shea"})
	@BotPermissions({Permission.MESSAGE_ATTACH_FILES})
	public void profile(CommandEvent event, @Context Database database, @Argument(value="user", endless=true, nullDefault=true) String userArgument) {
		Member member;
		if (userArgument == null) {
			member = event.getMember();
		} else {
			member = ArgumentUtils.getMember(event.getGuild(), userArgument);
			if (member == null) {
				event.reply("I could not find that user :no_entry:").queue();
				return;
			}
		}
		
		Bson projection = Projections.include("economy.balance", "profile", "reputation.amount");
		Document data = database.getUserById(member.getIdLong(), null, projection);
		
		Document profile = data.get("profile", Database.EMPTY_DOCUMENT);
		
		int reputation = data.getEmbedded(List.of("reputation", "amount"), 0);
		long balance = data.getEmbedded(List.of("economy", "balance"), 0L);
		
		List<Long> marriedUsers = profile.getList("marriedUsers", Long.class, Collections.emptyList());
		List<String> marriedUserTags = new ArrayList<>();
		for (long userId : marriedUsers) {
			User user = event.getShardManager().getUserById(userId);
			if (user != null) {
				marriedUserTags.add(user.getAsTag());
			}
		}
		
		String colour = profile.getString("colour");
		String birthday = profile.getString("birthday");
		String height = profile.getString("height");
		String description = profile.getString("description");
		
		Binary bytes = profile.get("banner", Binary.class);
		
		List<String> badges = FunUtils.getMemberBadges(member);
		
		if (colour != null || birthday != null || height != null || description != null) {
			badges.add("profile_editor.png");
		}
		
		if (!marriedUsers.isEmpty()) {
			badges.add("married.png");
		}
		
		String body = new JSONObject()
				.put("user_name", member.getUser().getAsTag())
				.put("background", bytes == null ? null : bytes.getData())
				.put("colour", colour == null ? "#ffffff" : colour)
				.put("balance", String.format("%,d", balance))
				.put("reputation", reputation)
				.put("description", description == null ? "Not set" : description)
				.put("birthday", birthday == null ? "Not set" : birthday)
				.put("height", height == null ? "Not set" : height)
				.put("badges", badges)
				.put("married_users", marriedUserTags)
				.put("user_avatar_url", member.getUser().getEffectiveAvatarUrl())
				.toString();
		
		Request request = new Request.Builder()
				.post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), body))
				.url("http://" + Settings.LOCAL_HOST + ":8443/api/profile")
				.build();
		
		event.getTextChannel().sendTyping().queue($ -> {
			ImageModule.client.newCall(request).enqueue((Sx4Callback) response -> {
				if (response.code() == 200) {
					event.replyFile(response.body().bytes(), "profile.png").queue();
				} else if (response.code() == 400) {
					event.reply(response.body().string()).queue();
				} else {
					event.reply("Oops something went wrong there! Status code: " + response.code() +  " :no_entry:\n```java\n" + response.body().string() + "```").queue();
				}
			});
		});
	}
	
	@Command(value="badges", description="Shows you all the badges you can get on your profile", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"badges"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void badges(CommandEvent event) {
		EmbedBuilder embed = new EmbedBuilder();
		embed.setTitle("Badges");
		embed.setDescription("<:server_owner:441255213450526730> - Be an owner of a server in which Sx4 is in\n" +
		"<:developer:441255213068845056> - Be a developer of Sx4\n<:helper:441255213131628554> - You have at some point contributed to the bot\n" +
		"<:donator:441255213224034325> - Donate to Sx4 either through PayPal or Patreon\n<:profile_editor:441255213207126016> - Edit your profile" +
		"\n<:married:441255213106593803> - Be married to someone on the bot\n<:playing:441255213513572358> - Have a playing status\n<:streaming:441255213106724865> - Have a streaming status" +
		"\n<:insx4server:472895584856965132> - Be in the Sx4 Support Server");
		
		event.reply(embed.build()).queue();
	}
	
	public class SetCommand extends Sx4Command {
		
		public SetCommand() {
			super("set");
			
			super.setDescription("Set aspects of your profile to display on your profile");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
			super.setExamples("set description", "set height", "set birthday");
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="birthday", description="Set your birthday for your profile (EU Format)", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"set birthday 01/07", "set birthday 01/07/2002"})
		public void birthday(CommandEvent event, @Context Database database, @Argument(value="birth date") String birthDateString) {
			LocalDate birthDateTime;
			boolean displayYear = true;
			try {
				birthDateTime = LocalDate.parse(birthDateString, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
			} catch(DateTimeParseException e) {
				try {
					birthDateTime = LocalDate.parse(birthDateString + "/1970", DateTimeFormatter.ofPattern("dd/MM/yyyy"));
					displayYear = false;
				} catch(DateTimeParseException ex) {
					event.reply("Invalid birth date format, follow the format of `dd/mm/yyyy` or `dd/mm` :no_entry:").queue();
					return;
				}
			}
			
			if (birthDateTime.compareTo(LocalDate.now(ZoneOffset.UTC)) > 0) {
				event.reply("You cannot set a birthday in the future :no_entry:").queue();
				return;
			}
			
			String birthday = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("profile.birthday")).getEmbedded(List.of("profile", "birthday"), String.class);
			if (birthday != null && birthday.equals(birthDateString)) {
				event.reply("Your birth date is already set to that :no_entry:").queue();
				return;
			}
			
			DateTimeFormatter format;
			if (displayYear) {
				format = DateTimeFormatter.ofPattern("dd/MM/yyyy"); 
			} else {
				format = DateTimeFormatter.ofPattern("dd/MM");
			}
			
			String birthdayString = GeneralUtils.getNumberSuffix(birthDateTime.getDayOfMonth()) + " " + birthDateTime.getMonth().getDisplayName(TextStyle.FULL, Locale.UK);
			database.updateUserById(event.getAuthor().getIdLong(), Updates.set("profile.birthday", birthDateTime.format(format)), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("Your birth date has been updated to the **" + birthdayString + "** <:done:403285928233402378>").queue();
				}
			});
		}
		
		@Command(value="description", aliases={"bio"}, description="Set your description for your profile")
		@Examples({"set description Hello", "set description Breif description about myself"})
		public void description(CommandEvent event, @Context Database database, @Argument(value="description", endless=true) String description) {
			if (description.length() > 300) {
				event.reply("Your description can be no longer than 300 characters :no_entry:").queue();
				return;
			}
			
			String currentDescription = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("profile.description")).getEmbedded(List.of("profile", "description"), String.class);
			if (currentDescription != null && currentDescription.equals(description)) {
				event.reply("Your description is already set to that :no_entry:").queue();
				return;
			}
			
			database.updateUserById(event.getAuthor().getIdLong(), Updates.set("profile.description", description), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("Your description has been updated <:done:403285928233402378>").queue();
				} 
			});
		}
		
		@Command(value="height", description="Set your height for your profile", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"set height 175", "set height 5'9", "set height 6'"})
		public void height(CommandEvent event, @Context Database database, @Argument(value="feet and inches | centimetres") String height) { 
			int centimetres, feet, inches;
			if (GeneralUtils.isNumber(height)) {
				centimetres = Integer.parseInt(height);			
				feet = (int) Math.floor(centimetres / 30.48);
				inches = (int) Math.round(((centimetres / 30.48) - feet) * 12);
			} else {
				String[] splitHeight;
				if (height.contains("\"")) {
					splitHeight = height.split("\"", 2);
				} else if (height.contains("'")) {
					splitHeight = height.split("'", 2);
				} else {
					event.reply("Invalid height format, make sure to supply a number in centimetres or feet and inches formatted like `f'i` :no_entry:").queue();
					return;
				}
				
				if (!GeneralUtils.isNumber(splitHeight[0]) || !GeneralUtils.isNumber(splitHeight[1])) {
					event.reply("Make sure that both the feet and inches you provided are numbers :no_entry:").queue();
					return;
				}
				
				feet = Integer.parseInt(splitHeight[0]);
				inches = splitHeight[1].equals("") ? 0 : Integer.parseInt(splitHeight[1]);
				centimetres = (int) Math.round((feet * 30.48) + (inches * 2.54));
				
				if (inches < 0 || inches > 12) {
					event.reply("Inches cannot be any less than 0 or more than 12 :no_entry:").queue();
					return;
				}
			}
			
			if (centimetres < 1) {
				event.reply("You have to be at least a centimetre tall :no_entry:").queue();
				return;
			}
			
			if (centimetres > 272) {
				event.reply("You are not taller than the tallest man ever :no_entry:").queue();
				return;
			}
			
			String heightDisplay = feet + "'" + inches + " (" + centimetres + "cm)";
			
			String currentHeight = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("profile.height")).getEmbedded(List.of("profile", "height"), String.class);
			if (currentHeight != null && currentHeight.equals(heightDisplay)) {
				event.reply("Your height is already set to " + heightDisplay + " :no_entry:").queue();
				return;
			}
			
			database.updateUserById(event.getAuthor().getIdLong(), Updates.set("profile.height", heightDisplay), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("Your height has been set to " + heightDisplay + " <:done:403285928233402378>").queue();
				}
			});
		}
		
		@Command(value="colour", aliases={"color"}, description="Set the colour accenting for you profile")
		@Examples({"set colour 255, 0, 0", "set colour #ff0000", "set colour ff0000"})
		public void colour(CommandEvent event, @Context Database database, @Argument(value="hex | rgb", endless=true) String colourArgument) {
			Color colour;
			if (colourArgument.toLowerCase().equals("reset") || colourArgument.toLowerCase().equals("default")) {
				colour = null;
			} else {
				colour = ArgumentUtils.getColourFromString(colourArgument);
				if (colour == null) {
					event.reply("Invalid hex or RGB value :no_entry:").queue();
					return;
				}
			}
			
			String currentColour = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("profile.colour")).getEmbedded(List.of("profile", "colour"), String.class);
			if (colour == null) {
				if (currentColour == null) {
					event.reply("You don't have a colour set for your profile :no_entry:").queue();
					return;
				}
				
				database.updateUserById(event.getAuthor().getIdLong(), Updates.unset("profile.colour"), (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						event.reply("The colour for your profile has been reset <:done:403285928233402378>").queue();
					}
				});
			} else {
				String hex = "#" + Integer.toHexString(colour.hashCode()).substring(2);
				if (currentColour != null && currentColour.equals(hex)) {
					event.reply("The colour for your profile is already set to **" + hex.toUpperCase() + "** :no_entry:").queue();
					return;
				}
				
				database.updateUserById(event.getAuthor().getIdLong(), Updates.set("profile.colour", hex), (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						event.reply("The colour for your profile has been updated to **" + hex.toUpperCase() + "** <:done:403285928233402378>").queue();
					}
				});
			}
		}
		
		@Command(value="banner", aliases={"background"}, description="Set your background for you profile", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"set banner", "set banner https://i.imgur.com/EqaQ9wo.png"})
		@Async
		@Cooldown(value=30)
		public void banner(CommandEvent event, @Context Database database, @Argument(value="banner", nullDefault=true) String banner) {
			URL url = null;
			if (banner == null && !event.getMessage().getAttachments().isEmpty()) {
				for (Attachment attachment : event.getMessage().getAttachments()) {
					if (attachment.isImage()) {
						try {
							url = new URL(attachment.getUrl());
						} catch (MalformedURLException e) {}
					}
				}
				
				if (url == null) {
					event.reply("None of the attachments you attached were images :no_entry:").queue();
					return;
				}
			} else if (banner == null && event.getMessage().getAttachments().isEmpty()) {
				event.reply("You need to supply a banner url or an attachment :no_entry:").queue();
				return;
			} else {
				if (!banner.toLowerCase().equals("reset")) {
					if (!event.getMessage().getEmbeds().isEmpty()) {
						MessageEmbed imageEmbed = event.getMessage().getEmbeds().get(0);
						if (imageEmbed.getThumbnail() != null) {
							try {
								url = new URL(imageEmbed.getThumbnail().getUrl());
							} catch (MalformedURLException e) {}
						} else {
							event.reply("That is not an image :no_entry:").queue();
							return;
						}
					} else {
						try {
							url = new URL(banner);
						} catch (MalformedURLException e) {
							event.reply("The banner you provided is not a URL :no_entry:").queue();
							return;
						}
					}
				}
			}
			
			Binary currentBytes = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("profile.banner")).getEmbedded(List.of("profile", "banner"), Binary.class);
			if (url == null) {
				if (currentBytes == null) {
					event.reply("You do not have a profile background set :no_entry:").queue();
					return;
				}
				
				database.updateUserById(event.getAuthor().getIdLong(), Updates.unset("profile.banner"), (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						event.reply("Your profile background has been updated <:done:403285928233402378>").queue();
					}
				});
			} else {
				if (url.getHost().contains("giphy")) {
					try {
						url = new URL("https://i.giphy.com/" + url.getPath().split("/")[2] + ".gif");
					} catch (MalformedURLException e) {}
				}
				
				try {
					url = new URL("http://" + Settings.LOCAL_HOST + ":8443/api/resize?image=" + url.toString() + "&width=2560&height=1440");
				} catch (MalformedURLException e1) {}
				
				Binary bytes;
				try {
					bytes = new Binary(url.openStream().readAllBytes());
				} catch (IOException e) {
					event.reply("Oops something went wrong there, try again :no_entry:").queue();
					return;
				}
				
				if (bytes.equals(currentBytes)) {
					event.reply("Your profile background is already set to that :no_entry:").queue();
					return;
				}
				
				database.updateUserById(event.getAuthor().getIdLong(), Updates.set("profile.banner", bytes), (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						event.reply("Your profile background has been updated <:done:403285928233402378>").queue();
					}
				});
			}
		}	
	}
	
	@Command(value="birthdays", description="View all the birthdays which are upcoming in the next 30 days (Set your birthday with `set birthday`)", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"birthdays", "birthdays --server"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void birthdays(CommandEvent event, @Context Database database, @Option(value="server", aliases={"guild"}, description="Filters all birthdays so only peoples birthdays from the current server are shown") boolean guild) {
		FindIterable<Document> data = database.getUsers().find(Filters.exists("profile.birthday")).projection(Projections.include("profile.birthday"));
		
		LocalDate now = LocalDate.now(ZoneOffset.UTC);
		
		List<Pair<User, LocalDate>> birthDates = new ArrayList<>();
		for (Document userData : data) {
			User user;
			if (guild) {
				Member member = event.getGuild().getMemberById(userData.getLong("_id"));
				user = member == null ? null : member.getUser();
			} else {
				user = event.getShardManager().getUserById(userData.getLong("_id"));
			}
			
			if (user != null) {
				String[] birthdaySplit = userData.getEmbedded(List.of("profile", "birthday"), String.class).split("/");
				int day = Integer.parseInt(birthdaySplit[0]);
				int month = Integer.parseInt(birthdaySplit[1]);
				if (month == 2 && day == 29 && now.getYear() % 4 != 0) {
					continue;
				}
				
				LocalDate birthday = LocalDate.of(now.getYear(), month, day);
				if (birthday.compareTo(now) < 0) {
					birthday = birthday.plusYears(1);
				}
				
				int daysApart = TimeUtils.getActualDaysApart(birthday.getDayOfYear() - now.getDayOfYear());
				if (daysApart >= 0 && daysApart < 31) {
					birthDates.add(Pair.of(user, birthday));
				}
			}
		}
		
		if (birthDates.isEmpty()) {
			event.reply("There are no upcoming birthdays :no_entry:").queue();
			return;
		}
		
		birthDates.sort((a, b) -> Integer.compare(TimeUtils.getActualDaysApart(a.getRight().getDayOfYear() - now.getDayOfYear()), TimeUtils.getActualDaysApart(b.getRight().getDayOfYear() - now.getDayOfYear())));
		PagedResult<Pair<User, LocalDate>> paged = new PagedResult<>(birthDates)
				.setDeleteMessage(false)
				.setIndexed(false)
				.setPerPage(20)
				.setEmbedColour(Settings.EMBED_COLOUR)
				.setAuthor("Upcoming Birthdays 🎂", null, null)
				.setFunction(userBirthday -> {
					LocalDate birthday = userBirthday.getRight();
					return userBirthday.getLeft().getAsTag() + " - " + GeneralUtils.getNumberSuffix(birthday.getDayOfMonth()) + " " + birthday.getMonth().getDisplayName(TextStyle.FULL, Locale.UK) + (birthday.equals(now) ? " :cake:" : "");
				});
		
		PagedUtils.getPagedResult(event, paged, 300, null);
	}
	
	private final Map<Long, List<Long>> marriages = new HashMap<>();
	
	@Command(value="marry", description="Marry other users or yourself, you can have up to 5 partners")
	@Examples({"marry @Shea#6653", "marry 402557516728369153", "marry Shea"})
	public void marry(CommandEvent event, @Context Database database, @Argument(value="user", endless=true) String userArgument) {
		Member member = ArgumentUtils.getMember(event.getGuild(), userArgument);
		if (member == null) {
			event.reply("I could not find that user :no_entry:").queue();
			return;
		}
		
		if (member.getUser().isBot()) {
			event.reply("You cannot marry bots :no_entry:").queue();
			return;
		}
		
		boolean isAuthor = event.getMember().equals(member);
		
		List<Long> authorMarriedUsers = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("profile.marriedUsers")).getEmbedded(List.of("profile", "marriedUsers"), Collections.emptyList());
		List<Long> userMarriedUsers;
		if (!isAuthor) {
			userMarriedUsers = database.getUserById(member.getIdLong(), null, Projections.include("profile.marriedUsers")).getEmbedded(List.of("profile", "marriedUsers"), Collections.emptyList());
		} else {
			userMarriedUsers = authorMarriedUsers;
		}
		
		if (userMarriedUsers.contains(event.getAuthor().getIdLong()) && authorMarriedUsers.contains(member.getIdLong())) {
			event.reply("You are already married to " + (isAuthor ? "yourself" : "that user") + " :no_entry:").queue();
			return;
		}
		
		if (authorMarriedUsers.size() >= 5) {
			event.reply("You are already married to the max amount of people (5) :no_entry:").queue();
			return;
		}
		
		if (userMarriedUsers.size() >= 5) {
			event.reply("**" + member.getUser().getAsTag() + "** is already married to the max amount of people (5) :no_entry:").queue();
			return;
		}
		
		List<Long> pendingMarriages = this.marriages.get(event.getAuthor().getIdLong());
		if (pendingMarriages != null && pendingMarriages.contains(member.getIdLong())) {
			event.reply("You already have a pending marriage with this user :no_entry:").queue();
			return;
		} else {
			if (pendingMarriages == null) {
				List<Long> users = new ArrayList<>();
				users.add(member.getIdLong());
				this.marriages.put(event.getAuthor().getIdLong(), users);
			} else {
				pendingMarriages.add(member.getIdLong());
			}
		}
		
		event.reply(member.getAsMention() + ", **" + event.getAuthor().getName() + "** would like to marry you!\n**Do you accept?**\nType **yes** or **no** to choose.").queue(message -> {
			PagedUtils.getConfirmation(event, 60, member.getUser(), confirmation -> {
				List<Long> newPendingMarriages = this.marriages.get(event.getAuthor().getIdLong());
				newPendingMarriages.remove(member.getIdLong());
				
				if (confirmation) {
					List<WriteModel<Document>> bulkData = new ArrayList<>();
					
					List<Long> authorMarriedUsersUpdated = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("profile.marriedUsers")).getEmbedded(List.of("profile", "marriedUsers"), Collections.emptyList());
					List<Long> userMarriedUsersUpdated;
					if (!isAuthor) {
						userMarriedUsersUpdated = database.getUserById(member.getIdLong(), null, Projections.include("profile.marriedUsers")).getEmbedded(List.of("profile", "marriedUsers"), Collections.emptyList());
					} else {
						userMarriedUsersUpdated = authorMarriedUsers;
					}
					
					if (userMarriedUsersUpdated.contains(event.getAuthor().getIdLong()) && authorMarriedUsersUpdated.contains(member.getIdLong())) {
						event.reply("You are already married to " + (isAuthor ? "yourself" : "that user") + " :no_entry:").queue();
						return;
					}
					
					if (authorMarriedUsersUpdated.size() >= 5) {
						event.reply("You are already married to the max amount of people (5) :no_entry:").queue();
						return;
					}
					
					if (userMarriedUsersUpdated.size() >= 5) {
						event.reply("**" + member.getUser().getAsTag() + "** is already married to the max amount of people (5) :no_entry:").queue();
						return;
					}
					
					UpdateOptions updateOptions = new UpdateOptions().upsert(true);
					bulkData.add(new UpdateOneModel<>(Filters.eq("_id", event.getAuthor().getIdLong()), Updates.addToSet("profile.marriedUsers", member.getIdLong()), updateOptions));
					if (!isAuthor) {
						bulkData.add(new UpdateOneModel<>(Filters.eq("_id", member.getIdLong()), Updates.addToSet("profile.marriedUsers", event.getAuthor().getIdLong()), updateOptions));
					}
					
					database.bulkWriteUsers(bulkData, (result, exception) -> {
						if (exception != null) {
							exception.printStackTrace();
							event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
						} else {
							message.delete().queue();
							event.reply("Congratulations " + (isAuthor ? "on marrying yourself" : "**" + event.getAuthor().getName() + "** and **" + member.getUser().getName() + "**") + " :heart: :tada:").queue();
						}
					});
				} else {
					message.delete().queue();
					event.reply("**" + event.getAuthor().getName() + "**, you can always try someone else.").queue();
				}
			}, () -> {
				event.reply("Response timed out :stopwatch:").queue();
				
				List<Long> newPendingMarriages = this.marriages.get(event.getAuthor().getIdLong());
				newPendingMarriages.remove(member.getIdLong());
			});
		});
	}
	
	@Command(value="divorce", description="Divorce a user you are married to or put an argument of all to divorce everyone")
	@Examples({"divorce all", "divorce @Shea#6653", "divorce 402557516728369153", "divorce Shea"})
	@Cooldown(value=5)
	public void divorce(CommandEvent event, @Context Database database, @Argument(value="user | all", endless=true) String userArgument) {
		List<Long> marriedUsers = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("profile.marriedUsers")).getEmbedded(List.of("profile", "marriedUsers"), Collections.emptyList());
		if (marriedUsers.isEmpty()) {
			event.reply("You are not married to anyone :no_entry:").queue();
			return;
		}
		
		if (userArgument.toLowerCase().equals("all")) {
			event.reply(event.getAuthor().getName() + ", are you sure you want to divorce everyone you are currently married to? (Yes or No)").queue(message -> {
				PagedUtils.getConfirmation(event, 60, event.getAuthor(), confirmation -> {
					if (confirmation) {
						List<Long> marriedUsersUpdated = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("profile.marriedUsers")).getEmbedded(List.of("profile", "marriedUsers"), Collections.emptyList());
						
						List<WriteModel<Document>> bulkData = new ArrayList<>();
						for (long userId : marriedUsersUpdated) {
							bulkData.add(new UpdateOneModel<>(Filters.eq("_id", userId), Updates.pull("profile.marriedUsers", event.getAuthor().getIdLong())));
						}
						
						bulkData.add(new UpdateOneModel<>(Filters.eq("_id", event.getAuthor().getIdLong()), Updates.unset("profile.marriedUsers")));
						database.bulkWriteUsers(bulkData, (result, exception) -> {
							if (exception != null) {
								exception.printStackTrace();
								event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
							} else {
								event.reply("You are no longer married to anyone <:done:403285928233402378>").queue();
							}
						});
					} else {
						event.reply("Cancelled <:done:403285928233402378>").queue();
						return;
					}
				});
			});
		} else {
			User user = ArgumentUtils.getUser(userArgument);
			List<WriteModel<Document>> bulkData = new ArrayList<>();
			if (user == null) {
				try { 
					long userId = Long.parseUnsignedLong(userArgument);
					if (marriedUsers.contains(userId)) {
						bulkData.add(new UpdateOneModel<>(Filters.eq("_id", event.getAuthor().getIdLong()), Updates.pull("profile.marriedUsers", userId)));
						bulkData.add(new UpdateOneModel<>(Filters.eq("_id", userId), Updates.pull("profile.marriedUsers", event.getAuthor().getIdLong())));
					} else {
						event.reply("You are not married to that user :no_entry:").queue();
						return;
					}
				} catch (NumberFormatException e) {
					event.reply("I could not find that user, provide their ID displayed in `" + event.getPrefix() + "married` if you are married to them :no_entry:").queue();
					return;
				}
			} else {
				if (marriedUsers.contains(user.getIdLong())) {
					bulkData.add(new UpdateOneModel<>(Filters.eq("_id", event.getAuthor().getIdLong()), Updates.pull("profile.marriedUsers", user.getIdLong())));
					bulkData.add(new UpdateOneModel<>(Filters.eq("_id", user.getIdLong()), Updates.pull("profile.marriedUsers", event.getAuthor().getIdLong())));
				} else {
					event.reply("You are not married to that user :no_entry:").queue();
					return;
				}
			}
			
			database.bulkWriteUsers(bulkData, (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("You are no longer married to " + (user == null ? "the user with the ID of **" + userArgument + "**" : "**" + user.getAsTag() + "**") + " <:done:403285928233402378>").queue();
				}
			});
		}
	}
	
	@Command(value="married", aliases={"married list", "marriedlist"}, description="View the users you or another user are married to")
	@Examples({"married", "married @Shea#6653", "married 402557516728369153", "married Shea"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void married(CommandEvent event, @Context Database database, @Argument(value="user", endless=true, nullDefault=true) String userArgument) {
		Member member;
		if (userArgument == null) {
			member = event.getMember();
		} else {
			member = ArgumentUtils.getMember(event.getGuild(), userArgument);
			if (member == null) {
				event.reply("I could not find that user :no_entry:").queue();
				return;
			}
		}
		
		List<Long> marriedUsers = database.getUserById(member.getIdLong(), null, Projections.include("profile.marriedUsers")).getEmbedded(List.of("profile", "marriedUsers"), Collections.emptyList());
		if (marriedUsers.isEmpty()) {
			event.reply("You are not married to anyone :no_entry:").queue();
			return;
		}
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setAuthor(member.getUser().getName() + "'s Partners", null, member.getUser().getEffectiveAvatarUrl());
		embed.setColor(member.getColor());
		
		for (long userId : marriedUsers) {
			User user = event.getShardManager().getUserById(userId);
			if (user == null) {
				embed.appendDescription("Unknown user (" + userId + ")\n");
			} else {
				embed.appendDescription(user.getAsTag() + " (" + userId + ")\n");
			}
		}
		
		event.reply(embed.build()).queue();
	}
	
	@Command(value="tts", aliases={"text to speech", "texttospeech"}, description="Give some text to be returned as text to speech in an mp3 file")
	@Examples({"tts Hello there", "tts Robot voices are cool"})
	@Cooldown(value=3)
	@BotPermissions({Permission.MESSAGE_ATTACH_FILES})
	public void tts(CommandEvent event, @Argument(value="text", endless=true) String text) {
		if (text.length() > 200) {
			event.reply("Text for text to speech can be no longer than 200 characters :no_entry:").queue();
		}
		
		URL url;
		try {
			url = new URL("https://translate.google.com/translate_tts?ie=UTF-8&client=tw-ob&tl=en-gb&q=" + text);
		} catch (MalformedURLException e) {
			event.reply("Oops something went wrong there, try again :no_entry:").queue();
			return;
		}
		
		Request request = new Request.Builder().url(url).build();
		
		Sx4Bot.client.newCall(request).enqueue((Sx4Callback) response -> {
			event.getTextChannel().sendFile(response.body().bytes(), text + ".mp3").queue();
		});
	}
	
	@Command(value="weather", description="Gives you weather information about a specified city")
	@Examples({"weather gb london", "weather se stockholm"})
	@Cooldown(value=3)
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void weather(CommandEvent event, @Argument(value="country code") String country, @Argument(value="city", endless=true) String city) {
		URL url;
		try {
			url = new URL(URLDecoder.decode("https://api.openweathermap.org/data/2.5/weather?q=" + city.toLowerCase() + "," + country.toLowerCase() + "&APPID=" + TokenUtils.OPEN_WEATHER + "&units=metric", StandardCharsets.UTF_8));
		} catch (MalformedURLException e) {
			event.reply("Oops something went wrong there, try again :no_entry:").queue();
			return;
		}
		
		Request request = new Request.Builder().url(url).build();
		
		Sx4Bot.client.newCall(request).enqueue((Sx4Callback) response -> {
			JSONObject json;
			try {
				json = new JSONObject(response.body().string());
			} catch (JSONException | IOException e) {
				event.reply("Oops something went wrong there, try again :no_entry:").queue();
				return;
			}
			
			if (json.has("message")) {
				event.reply(json.getString("message").substring(0, 1).toUpperCase() + json.getString("message").substring(1) + " :no_entry:").queue();
				return;
			}
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setTitle(json.getString("name") + " (" + json.getJSONObject("sys").getString("country") + ")");
			embed.setThumbnail("http://openweathermap.org/img/w/" + json.getJSONArray("weather").getJSONObject(0).getString("icon") + ".png");
			embed.addField("Temperature", "Minimum: " + json.getJSONObject("main").getInt("temp_min") + "°C\nCurrent: " +
					json.getJSONObject("main").getInt("temp") + "°C\nMaximum: " + json.getJSONObject("main").getInt("temp_max") + "°C", true);
			embed.addField("Humidity", json.getJSONObject("main").getInt("humidity") + "%", true);
			embed.addField("Wind", "Speed: " + json.getJSONObject("wind").getDouble("speed") + "m/s\nDirection: " + (json.getJSONObject("wind").has("deg") ? json.getJSONObject("wind").getInt("deg") + "° (" + Direction.getDirection(json.getJSONObject("wind").getInt("deg")).getName() + ")" : ""), true);
			
			event.reply(embed.build()).queue();
		});
	}
	
	@Command(value="random steam game", aliases={"randomsteamgame", "randomsteam", "random steam", "randomgame", "random game"}, description="Gives you a random game from steam", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"random steam game"})
	@Cooldown(value=5)
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void randomSteamGame(CommandEvent event) {
		List<Map<String, Object>> steamGames = SteamCache.getGames();
		if (steamGames.isEmpty()) {
			event.reply("The steam cache is currently empty, try again in 1 hour :no_entry:").queue();
			return;
		}
		
		AtomicInteger attempts = new AtomicInteger(0);
		Map<String, Object> steamGame = steamGames.get(RANDOM.nextInt(steamGames.size()));
		
		Request request = new Request.Builder().url("https://store.steampowered.com/api/appdetails?appids=" + (int) steamGame.get("appid")).addHeader("Accept-Language", "en-GB").build();
		
		Sx4Callback sx4Callback = new Sx4Callback() {
			public void onResponse(Response response) throws IOException {
				JSONObject json;
				try {
					json = new JSONObject(response.body().string()).getJSONObject(String.valueOf((int) steamGame.get("appid")));
				} catch (JSONException | IOException e) {
					event.reply("Oops something went wrong there, try again :no_entry:").queue();
					return;
				}
				
				if (json.getBoolean("success") == false) {
					attempts.set(attempts.get() + 1);
					if (attempts.get() == 3) {
						event.reply("Steam failed to get data for a random steam game, try again :no_entry:").queue();
					} else {
						Map<String, Object> newSteamGame = steamGames.get(RANDOM.nextInt(steamGames.size()));
						
						Sx4Bot.client.newCall(request.newBuilder().url("https://store.steampowered.com/api/appdetails?appids=" + (int) newSteamGame.get("appid")).build()).enqueue(this);
					}
					
					return;
				}
				
				json = json.getJSONObject("data");
				
				EmbedBuilder embed = new EmbedBuilder();
				embed.setDescription(json.getString("short_description").replace("&quot;", "\""));
				embed.setImage(json.getString("header_image"));
				embed.setAuthor(json.getString("name"), "https://store.steampowered.com/app/" + (int) steamGame.get("appid"), "https://upload.wikimedia.org/wikipedia/commons/thumb/8/83/Steam_icon_logo.svg/2000px-Steam_icon_logo.svg.png");
				embed.addField("Price", json.has("price_overview") ? json.getJSONObject("price_overview").getString("final_formatted") : json.getBoolean("is_free") ? "Free" : "Unknown", true);
				if (!json.getJSONObject("release_date").getString("date").trim().equals("") || json.getJSONObject("release_date").getBoolean("coming_soon") == true) {
					embed.addField("Release Date", json.getJSONObject("release_date").getString("date") + (json.getJSONObject("release_date").getBoolean("coming_soon") ? " (Coming Soon)" : ""), true);
				}
				embed.addField("Required Age", json.getInt("required_age") == 0 ? "No Age Restriction" : String.valueOf(json.getInt("required_age")), true);
				embed.addField("Recommendations", json.has("recommendations") ? String.format("%,d", json.getJSONObject("recommendations").getInt("total")) : "Unknown/None", true);
				embed.addField("Supported Languages", json.has("supported_languages") ? json.getString("supported_languages").replace("<br>", "\n").replace("</br>", "\n").replace("<strong>", "").replace("</strong>", "").replace("*", "\\*") : "Unknown", true);
				
				if (json.has("genres")) {
					List<String> genres = new ArrayList<>();
					for (Object genre : json.getJSONArray("genres")) {
						genres.add(((JSONObject) genre).getString("description"));
					}
					embed.addField("Genres", String.join("\n", genres), true);
				} else {
					embed.addField("Genres", "None", true);
				}
				
				embed.setFooter("Developed by " + (json.has("developers") ? String.join(", ", json.getJSONArray("developers").toList().toArray(new String[0])) : "Unknown"), null);
				event.reply(embed.build()).queue();
			}
		};
		
		Sx4Bot.client.newCall(request).enqueue(sx4Callback);
	}
	
	@Command(value="steam search", aliases={"steamsearch", "gamesearch", "game search", "steamgame", "steam game"}, description="Look up any game on steam")
	@Examples({"steam search grand theft auto", "steam search uno", "steam search human fall flat"})
	@Cooldown(value=5)
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void steamSearch(CommandEvent event, @Argument(value="game", endless=true, nullDefault=true) String gameName) {
		List<Map<String, Object>> steamGames = SteamCache.getGames();
		if (steamGames.isEmpty()) {
			event.reply("The steam cache is currently empty, try again in 1 hour :no_entry:").queue();
			return;
		}
		
		List<Map<String, Object>> games = new ArrayList<>();
		for (Map<String, Object> game : steamGames) {
			if (gameName != null) {
				if (((String) game.get("name")).toLowerCase().equals(gameName.toLowerCase()) || ((String) game.get("name")).toLowerCase().contains(gameName.toLowerCase())) {
					games.add(game);
				}
			} else {
				games.add(game);
			}
		}
		
		if (games.isEmpty()) {
			event.reply("I could not find that game :no_entry:").queue();
			return;
		}
		
		games.sort((a, b) -> ((String) a.get("name")).compareTo((String) b.get("name")));
		
		PagedResult<Map<String, Object>> paged = new PagedResult<>(games)
				.setAuthor("Steam Search", null, "https://upload.wikimedia.org/wikipedia/commons/thumb/8/83/Steam_icon_logo.svg/2000px-Steam_icon_logo.svg.png")
				.setIncreasedIndex(true)
				.setSelectableByIndex(true)
				.setAutoSelect(true)
				.setFunction(game -> (String) game.get("name"));
		
		PagedUtils.getPagedResult(event, paged, 60, returnedGame -> {
			Map<String, Object> steamGame = returnedGame.getData();
			
			Request request = new Request.Builder().url("https://store.steampowered.com/api/appdetails?appids=" + (int) steamGame.get("appid")).addHeader("Accept-Language", "en-GB").build();
			
			Sx4Bot.client.newCall(request).enqueue((Sx4Callback) response -> {
				JSONObject json;
				try {
					json = new JSONObject(response.body().string()).getJSONObject(String.valueOf((int) steamGame.get("appid")));
				} catch (JSONException | IOException e) {
					event.reply("Oops something went wrong there, try again :no_entry:").queue();
					return;
				}
				
				if (json.getBoolean("success") == false) {
					event.reply("Steam failed to get data for that game :no_entry:").queue();
					return;
				}
				
				json = json.getJSONObject("data");
				
				EmbedBuilder embed = new EmbedBuilder();
				embed.setDescription(json.getString("short_description").replace("&quot;", "\""));
				embed.setImage(json.getString("header_image"));
				embed.setAuthor(json.getString("name"), "https://store.steampowered.com/app/" + (int) steamGame.get("appid"), "https://upload.wikimedia.org/wikipedia/commons/thumb/8/83/Steam_icon_logo.svg/2000px-Steam_icon_logo.svg.png");
				embed.addField("Price", json.has("price_overview") ? json.getJSONObject("price_overview").getString("final_formatted") : json.getBoolean("is_free") ? "Free" : "Unknown", true);
				if (!json.getJSONObject("release_date").getString("date").trim().equals("") || json.getJSONObject("release_date").getBoolean("coming_soon") == true) {
					embed.addField("Release Date", json.getJSONObject("release_date").getString("date") + (json.getJSONObject("release_date").getBoolean("coming_soon") ? " (Coming Soon)" : ""), true);
				}
				embed.addField("Required Age", json.getInt("required_age") == 0 ? "No Age Restriction" : String.valueOf(json.getInt("required_age")), true);
				embed.addField("Recommendations", json.has("recommendations") ? String.format("%,d", json.getJSONObject("recommendations").getInt("total")) : "Unknown/None", true);
				embed.addField("Supported Languages", json.has("supported_languages") ? json.getString("supported_languages").replace("<br>", "\n").replace("</br>", "\n").replace("<strong>", "").replace("</strong>", "").replace("*", "\\*") : "Unknown", true);
				
				if (json.has("genres")) {
					List<String> genres = new ArrayList<>();
					for (Object genre : json.getJSONArray("genres")) {
						genres.add(((JSONObject) genre).getString("description"));
					}
					embed.addField("Genres", String.join("\n", genres), true);
				} else {
					embed.addField("Genres", "None", true);
				}
				
				embed.setFooter("Developed by " + (json.has("developers") ? String.join(", ", json.getJSONArray("developers").toList().toArray(new String[0])) : "Unknown"), null);
				event.reply(embed.build()).queue();
			});
		});
	}
	
	public enum Minesweeper {
		ZERO(0, ":zero:"),
		ONE(1, ":one:"),
		TWO(2, ":two:"),
		THREE(3, ":three:"),
		FOUR(4, ":four:"),
		FIVE(5, ":five:"),
		SIX(6, ":six:"),
		SEVEN(7, ":seven:"),
		EIGHT(8, ":eight:"),
		NINE(9, ":nine:"),
		BOMB(10, ":bomb:");
		
		private int number;
		private String emote;
		
		private Minesweeper(int number, String emote) {
			this.number = number;
			this.emote = emote;
		}
		
		public int getNumber() {
			return this.number;
		}
		
		public String getEmote() {
			return this.emote;
		}
		
		public static String getEmoteFromNumber(int number) {
			for (Minesweeper minesweeper : Minesweeper.values()) {
				if (minesweeper.getNumber() == number) {
					return minesweeper.getEmote();
				}
			}
			
			return null;
		}
	}
	
	@Command(value="minesweeper", description="The bot will generate a minesweeper grid for you to play with discords spoilers", contentOverflowPolicy=ContentOverflowPolicy.IGNORE) 
	@Examples({"minesweeper", "minesweeper 10", "minesweeper 5 5x5"})
	public void minesweeper(CommandEvent event, @Argument(value="bombs", nullDefault=true) Integer bombAmount, @Argument(value="grid size", nullDefault=true) String gridSize) {
		int gridX, gridY;
		if (gridSize != null) {
			if (gridSize.matches("\\d+x\\d+")) {
				gridX = Integer.parseInt(gridSize.split("x")[0]);
				gridY = Integer.parseInt(gridSize.split("x")[1]);
			} else {
				event.reply("Invalid grid format make sure it is formated like <x>x<y> an example would be 6x6 :no_entry:").queue();
				return;
			}
		} else {
			gridX = 10;
			gridY = 10;
		}
		
		if (bombAmount == null) {
			bombAmount = 10;
		}
		
		if (gridX < 2 && gridY < 2) {
			event.reply("The grid has to be at least 2x2 in size :no_entry:").queue();
			return;
		}
		
		if (bombAmount > gridX * gridY - 1) {
			event.reply("**" + (gridX * gridY - 1) + "** is the max amount of bombs you can have in this grid :no_entry:").queue();
			return;
		}
		
		if (bombAmount < 1) {
			event.reply("You have to have at least 1 bomb in your grid :no_entry:").queue();
			return;
		}
		
		List<List<Integer>> bombs = new ArrayList<>();
		for (int bomb = 0; bomb < bombAmount; bomb++) {
			List<Integer> bombPosition = List.of(RANDOM.nextInt(gridX), RANDOM.nextInt(gridY), 10);
			while (bombs.contains(bombPosition)) {
				bombPosition = List.of(RANDOM.nextInt(gridX), RANDOM.nextInt(gridY), 10);
			}
			
			bombs.add(bombPosition);
		}
		
		int number = 0;
		List<List<Integer>> grid = new ArrayList<>();
		for (int x = 0; x < gridX; x++) {
			for (int y = 0; y < gridY; y++) {
				if (!bombs.contains(List.of(x, y, 10))) {
					for (int aroundX = -1; aroundX < 2; aroundX++) {
						for (int aroundY = -1; aroundY < 2; aroundY++) {
							if (bombs.contains(List.of(x + aroundX, y + aroundY, 10))) {
								number += 1;
							}
						}
					}
					
					grid.add(List.of(x, y, number));
					number = 0;
				}
			}
		}
		
		grid.addAll(bombs);
		grid.sort((a, b) -> Integer.compare(a.get(1), b.get(1)));
		grid.sort((a, b) -> Integer.compare(a.get(0), b.get(0)));
		
		String content = "";
		int i = 0;
		for (List<Integer> gridSpot : grid) {
			if (i == gridX) {
				content += "\n";
				i = 0;
			} 
			
			content += "||" + Minesweeper.getEmoteFromNumber(gridSpot.get(2)) + "||";
			i += 1;
		}
		
		if (content.length() > 2000) {
			event.reply("That grid size is too big to display :no_entry:").queue();
			return;
		}
		
		event.reply(content).queue();
	}
	
	@Command(value="convert", description="Convert one currency to another", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"convert 10 gbp usd", "convert 5 eur usd", "convert 500 sek gbp"})
	@Cooldown(value=3)
	public void convert(CommandEvent event, @Argument(value="amount") double amount, @Argument(value="currency from") String currencyFrom, @Argument(value="currency to") String currencyTo) {
		String from = currencyFrom.toUpperCase(), to = currencyTo.toUpperCase();
		Request request = new Request.Builder().url("https://free.currencyconverterapi.com/api/v6/convert?q=" + currencyFrom + "_" + currencyTo + "&apiKey=" + TokenUtils.CURRENCY_CONVERTOR).build();
		
		Sx4Bot.client.newCall(request).enqueue((Sx4Callback) response -> {
			JSONObject json;
			try {
				json = new JSONObject(response.body().string());
			} catch (JSONException | IOException e) {
				event.reply("Oops something went wrong there, try again :no_entry:").queue();
				return;
			}
			
			if (json.getJSONObject("query").getInt("count") == 0) {
				event.reply("I could not find one of the currencies :no_entry:").queue();
				return;
			}
			
			JSONObject result = json.getJSONObject("results").getJSONObject(from + "_" + to);
			event.reply("**" + String.format("%,.2f", amount) + "** " + from + " \\➡ **" + String.format("%,.2f", amount * result.getDouble("val")) + "** " + to).queue();
		});
	}
	
	@SuppressWarnings("unchecked")
	@Command(value="teams", description="Split users/users of a role into a specified amount of teams")
	@Examples({"teams 5 @everyone", "teams 2 @Shea#6653 190551803669118976 Adriaan οοοοοο#7518", "teams 3 dog cat fox bear hamster monkey"})
	public void teams(CommandEvent event, @Argument(value="amount of teams") int teams, @Argument(value="players") String[] players) {
		if (teams < 2) {
			event.reply("There has to be at least 2 teams :no_entry:").queue();
			return;
		}
		
		List<String> newPlayers = new ArrayList<>();  
		for (int i = 0; i < players.length; i++) {
			Role role = ArgumentUtils.getRole(event.getGuild(), players[i]);
			Member member = ArgumentUtils.getMember(event.getGuild(), players[i]);
			if (role != null) {
				for (Member roleMember : event.getGuild().getMembersWithRoles(role)) {
					if (!newPlayers.contains(roleMember.getUser().getName())) {
						newPlayers.add(roleMember.getUser().getName());
					}
				}
			} else if (member != null) {
				if (!newPlayers.contains(member.getUser().getName())) {
					newPlayers.add(member.getUser().getName());
				}
			} else {
				if (!newPlayers.contains(players[i])) {
					newPlayers.add(players[i]);
				}
			}
		}
		
		if (newPlayers.size() < teams) {
			event.reply("There are more teams than players :no_entry:").queue();
			return;
		}
		
		List<String>[] team = new ArrayList[teams];
		for (int i = 0; i < teams; i++) {
			team[i] = new ArrayList<String>();
		}
		
		int teamNumber = 0;
		while (!newPlayers.isEmpty()) {
			String chosenPlayer = newPlayers.get(RANDOM.nextInt(newPlayers.size()));
			newPlayers.remove(chosenPlayer);
			team[teamNumber].add(chosenPlayer);
			if (teamNumber == teams - 1) {
				teamNumber = 0;
			} else {
				teamNumber += 1;
			}
		}
		
		String content = "";
		for (int i = 0; i < team.length; i++) {
			content += "**Team " + (i + 1) + "**" + "\n\n" + String.join(", ", team[i]) + "\n\n";
		}
		
		if (content.length() > 2000) {
			event.reply("I cannot show that many players within 2000 characters :no_entry:").queue();
			return;
		}
		
		event.reply(content).queue();
	}
	
	@Command(value="guess the number", aliases={"guessthenumber", "gtn"}, description="You and another user will have to guess a number between 1 and 50 whoever is closest wins, winner gets the other users bet if one is placed", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"guess the number @Shea#6653", "guess the number Shea 5000", "guess the number 402557516728369153 all", "guess the number Shea#6653 10%"})
	@Cooldown(value=120)
	public void guessTheNumber(CommandEvent event, @Context Database database, @Argument(value="user") String userArgument, @Argument(value="bet", nullDefault=true) String betArgument) {
		Member member = ArgumentUtils.getMember(event.getGuild(), userArgument);
		if (member == null) {
			event.reply("I could not find that user :no_entry:").queue();
			event.removeCooldown();
			return;
		}
		
		if (member == event.getMember()) {
			event.reply("You cannot play against yourself :no_entry:").queue();
			event.removeCooldown();
			return;
		}
		
		long authorBalance = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.balance")).getEmbedded(List.of("economy", "balance"), 0L), moneyArgument = 0;
		if (betArgument != null) {
			try {
				moneyArgument = EconomyUtils.convertMoneyArgument(authorBalance, betArgument);
			} catch(IllegalArgumentException e) {
				event.reply(e.getMessage()).queue();
				return;
			}
		}
		
		long bet = betArgument == null ? 0 : moneyArgument;
		if (bet != 0) {
			if (bet < 1) {
				event.reply("The bet must be at least **$1** :no_entry:").queue();
				event.removeCooldown();
				return;
			}
			
			if (authorBalance < bet) {
				event.reply("**" + event.getAuthor().getAsTag() + "** doesn't have enough money :no_entry:").queue();
				event.removeCooldown();
				return;
			}
			
			long userBalance = database.getUserById(member.getIdLong(), null, Projections.include("economy.balance")).getEmbedded(List.of("economy", "balance"), 0L);
			if (userBalance < bet) {
				event.reply("**" + member.getUser().getAsTag() + "** doesn't have enough money :no_entry:").queue();
				event.removeCooldown();
				return;
			}
		}
		
		event.reply(member.getUser().getName() + ", Type `accept` or `yes` if you would like to play guess the number" + (bet == 0 ? "." : String.format(" for **$%,d**", bet))).queue($ -> {
			PagedUtils.getConfirmation(event, 60, member.getUser(), confirmed -> {
				if (confirmed) {
					event.reply("I will send a message to **" + member.getUser().getAsTag() + "**, once they've responded I will send a message to **" + event.getAuthor().getAsTag() + "**").queue();
					
					member.getUser().openPrivateChannel().queue(userChannel -> {
						userChannel.sendMessage("I am thinking of a number between 1 and 50, try and guess my number. Whoever is closest wins, good luck!").queue(message -> {
							PagedUtils.getResponse(event, 20, (e) -> {
								if (e.getChannel().equals(userChannel)) {
									int memberNumber;
									try {
										memberNumber = Integer.parseInt(e.getMessage().getContentRaw());
									} catch(NumberFormatException ex) {
										return false;
									}
									
									return memberNumber > 0 && memberNumber < 51; 
								} else {
									return false;
								}
							}, () -> {event.reply("Response timed out for " + member.getUser().getAsTag() + " :stopwatch:").queue(); event.removeCooldown(); return;}, userMessage -> {
								int userNumber = Integer.parseInt(userMessage.getContentRaw());
								userChannel.sendMessage("Your answer has been locked in! Waiting on a response from **" + event.getAuthor().getAsTag() + "**. Results will be sent in " + event.getTextChannel().getAsMention()).queue();
								
								event.getAuthor().openPrivateChannel().queue(authorChannel -> {
									authorChannel.sendMessage("I am thinking of a number between 1 and 50, try and guess my number. Whoever is closest wins, good luck!").queue(m -> {
										PagedUtils.getResponse(event, 20, (e) -> {
											if (e.getChannel().equals(authorChannel)) {
												int memberNumber;
												try {
													memberNumber = Integer.parseInt(e.getMessage().getContentRaw());
												} catch(NumberFormatException ex) {
													return false;
												}
												
												return memberNumber > 0 && memberNumber < 51; 
											} else {
												return false;
											}
										}, () -> {event.reply("Response timed out for " + event.getAuthor().getAsTag() + " :stopwatch:").queue(); event.removeCooldown(); return;}, authorMessage -> {
											authorChannel.sendMessage("Your answer has been locked in! Results have been sent to " + event.getTextChannel().getAsMention()).queue();
											
											int authorNumber = Integer.parseInt(authorMessage.getContentRaw());
											int myNumber = GeneralUtils.getRandomNumber(1, 50);
											int authorDifference = Math.abs(authorNumber - myNumber);
											int userDifference = Math.abs(userNumber - myNumber);
											Member winner = null;
											StringBuilder content = new StringBuilder("My number was **" + myNumber + "**\n" + member.getUser().getName() + "s number was **" + userNumber + "**\n" + event.getAuthor().getName() + "s number was **" + authorNumber + "**\n\n");
											if (userDifference == authorDifference) {
												content.append("You both guessed the same number, It was a draw!");
											} else if (userDifference < authorDifference) {
												content.append(member.getUser().getName() + " won! They were the closest to " + myNumber);
												winner = member;
											} else {
												content.append(event.getAuthor().getName() + " won! They were the closest to " + myNumber);
												winner = event.getMember();
											}
											
											if (winner != null && betArgument != null) {
												long authorBalanceUpdated = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.balance")).getEmbedded(List.of("economy", "balance"), 0L);
												long userBalanceUpdated = database.getUserById(member.getIdLong(), null, Projections.include("economy.balance")).getEmbedded(List.of("economy", "balance"), 0L);
												if (authorBalanceUpdated < bet) {
													content.append("\n**" + event.getAuthor().getAsTag() + "** no longer has enough money, bet has been cancelled.");
												} else if (userBalanceUpdated < bet) {
													content.append("\n**" + member.getUser().getAsTag() + "** no longer has enough money, bet has been cancelled.");
												} else {
													content.append(String.format("\nThey have been rewarded **$%,d**", bet * 2));
													
													List<WriteModel<Document>> bulkData = new ArrayList<>();
													bulkData.add(new UpdateOneModel<>(Filters.eq("_id", event.getAuthor().getIdLong()), Updates.inc("economy.balance", winner.equals(event.getMember()) ? bet : -bet)));
													bulkData.add(new UpdateOneModel<>(Filters.eq("_id", member.getIdLong()), Updates.inc("economy.balance", winner.equals(member) ? bet : -bet)));
													
													database.bulkWriteUsers(bulkData, (result, exception) -> {
														if (exception != null) {
															exception.printStackTrace();
															event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
														} else {
															event.reply(content.toString()).queue();
														}
													});
													
													return;
												}
											}
											
											event.reply(content.toString()).queue();
											event.removeCooldown();
										});
									});
								}, e -> {
									event.reply("I was unable to send a message to **" + event.getAuthor().getAsTag() + "** :no_entry:").queue();
									event.removeCooldown();
									return;
								});
							});
						});
					}, e -> {
						event.reply("I was unable to send a message to **" + member.getUser().getAsTag() + "** :no_entry:").queue();
						event.removeCooldown();
						return;
					});
				} else {
					event.reply("Cancelled <:done:403285928233402378>").queue();
					event.removeCooldown();
				}
			});
		});
	}
	
	@Command(value="translate", aliases={"tr"}, description="Translate the text provided into a specified language")
	@Examples({"translate english bonjour", "translate de hello there"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void translate(CommandEvent event, @Argument(value="language") String languageName, @Argument(value="text", endless=true) String text) {
		if (text.length() > MessageEmbed.VALUE_MAX_LENGTH) {
			event.reply("The text to translate cannot be any longer than 1024 characters :no_entry:").queue();
			return;
		}
		
		languageName = languageName.toLowerCase();
		Locale language = null;
		for (Locale localeLanguage : Locale.getAvailableLocales()) {
			if (languageName.equals(localeLanguage.getDisplayLanguage().toLowerCase())) {
				language = localeLanguage;
				break;
			} else if (languageName.equals(localeLanguage.getLanguage().toLowerCase())) {
				language = localeLanguage;
				break;
			} else if (languageName.equals(localeLanguage.getISO3Language())) {
				language = localeLanguage;
				break;
			}
		}
		
		if (language == null) { 
			event.reply("I could not find that language :no_entry:").queue();
			return;
		}
		
		String languageISO;
		if (language.getLanguage().equals("zh")) {
			languageISO = language.getLanguage() + "-CN";
		} else {
			languageISO = language.getLanguage();
		}
		
		String displayLanguage = language.getDisplayLanguage();
		if (GeneralUtils.isNumberUnsigned(text)) {
			event.getTextChannel().retrieveMessageById(text).queue(message -> {
				Request request;
				try {
					request = new Request.Builder().url(new URL("http://" + Settings.LOCAL_HOST + ":8080/translate/" + languageISO + "?q=" + message.getContentRaw())).build();
				} catch (MalformedURLException e) {
					event.reply("Oops something went wrong there, try again :no_entry:").queue();
					return;
				}
				
				Sx4Bot.client.newCall(request).enqueue((Sx4Callback) response -> {
					JSONObject json;
					try {
						json = new JSONObject(response.body().string());
					} catch (JSONException | IOException e) {
						event.reply("Oops something went wrong there, try again :no_entry:").queue();
						return;
					}
					
					if (json.getBoolean("success") == false) {
						if (json.has("response")) {
							event.reply(json.getJSONObject("response").getString("message").replace("'", "`") + " :no_entry:").queue();
							return;
						}
					}
					
					String inputText = json.getJSONObject("from").getJSONObject("language").getString("iso");
					if (inputText.contains("-")) {
						inputText = inputText.split("-")[0];
					}
					
					Locale inputLanguage = null;
					for (Locale localeLanguage : Locale.getAvailableLocales()) {
						if (inputText.equals(localeLanguage.getLanguage().toLowerCase())) {
							inputLanguage = localeLanguage;
							break;
						} else if (inputText.equals(localeLanguage.getISO3Language())) {
							inputLanguage = localeLanguage;
							break;
						}
					}
					
					EmbedBuilder embed = new EmbedBuilder();
					embed.setColor(0x4285f4);
					embed.setAuthor("Google Translate", null, "https://upload.wikimedia.org/wikipedia/commons/d/db/Google_Translate_Icon.png");
					embed.addField("Input Text (" + (inputLanguage == null ? "Unknown" : inputLanguage.getDisplayLanguage()) + ")", json.getJSONObject("from").getJSONObject("text").getString("value").equals("") ? message.getContentRaw() : json.getJSONObject("from").getJSONObject("text").getString("value"), false);
					embed.addField("Output Text (" + displayLanguage + ")", json.getString("text"), false);
					event.reply(embed.build()).queue();
				});
			}, e -> {
				event.reply("I could not find that message :no_entry:").queue();
			});
		} else {
			Request request;
			try {
				request = new Request.Builder().url(new URL("http://" + Settings.LOCAL_HOST + ":8080/translate/" + languageISO + "?q=" + text)).build();
			} catch (MalformedURLException e) {
				event.reply("Oops something went wrong there, try again :no_entry:").queue();
				return;
			}
			
			Sx4Bot.client.newCall(request).enqueue((Sx4Callback) response -> {
				JSONObject json;
				try {
					json = new JSONObject(response.body().string());
				} catch (JSONException | IOException e) {
					event.reply("Oops something went wrong there, try again :no_entry:").queue();
					return;
				}
				
				if (json.getBoolean("success") == false) {
					if (json.has("response")) {
						event.reply(json.getJSONObject("response").getString("message").replace("'", "`") + " :no_entry:").queue();
						return;
					}
				}
				
				String inputText = json.getJSONObject("from").getJSONObject("language").getString("iso");
				if (inputText.contains("-")) {
					inputText = inputText.split("-")[0];
				}
				
				Locale inputLanguage = null;
				for (Locale localeLanguage : Locale.getAvailableLocales()) {
					if (inputText.equals(localeLanguage.getLanguage().toLowerCase())) {
						inputLanguage = localeLanguage;
						break;
					} else if (inputText.equals(localeLanguage.getISO3Language())) {
						inputLanguage = localeLanguage;
						break;
					}
				}
				
				EmbedBuilder embed = new EmbedBuilder();
				embed.setColor(0x4285f4);
				embed.setAuthor("Google Translate", null, "https://upload.wikimedia.org/wikipedia/commons/d/db/Google_Translate_Icon.png");
				embed.addField("Input Text (" + (inputLanguage == null ? "Unknown" : inputLanguage.getDisplayLanguage()) + ")", json.getJSONObject("from").getJSONObject("text").getString("value").equals("") ? text : json.getJSONObject("from").getJSONObject("text").getString("value"), false);
				embed.addField("Output Text (" + displayLanguage + ")", json.getString("text"), false);
				event.reply(embed.build()).queue();
			});
		}
	}
	
	@Command(value="calculator", aliases={"calc"}, description="Calculate any equation with general mathmatic symbols")
	@Examples({"calculator (10 * 10) / 5", "calculator 1 + 1"})
	public void calculator(CommandEvent event, @Argument(value="equation", endless=true) String equation) {
		String eq;
		try {
			eq = new String(Runtime.getRuntime().exec("./calc " + equation.strip().replace("(", "l").replace(")", "r")).getInputStream().readAllBytes());
		} catch (IOException e) {
			event.reply("Oops something went wrong there, try again :no_entry:").queue();
			return;
		}
		
		if (eq.isEmpty()) {
			event.reply("Invalid equation :no_entry:").queue();
			return;
		}
		
		event.reply(eq).queue();
	}
	
	private final List<String> regions = List.of("cn", "na", "eu", "sa", "ea", "sg");
	
	@Command(value="vain glory", aliases={"vain", "vg", "vainglory"}, description="Get statitstics about a specified player in the game vain glory", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"vain glory dog", "vain glory cat eu"})
	@Cooldown(value=5)
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void vainGlory(CommandEvent event, @Argument(value="player name") String playerName, @Argument(value="region", nullDefault=true) String region) {
		if (region == null) {
			region = "na";
		}
		
		if (!this.regions.contains(region)) {
			event.reply("I could not find that region, valid regions are `" + String.join("`, `", this.regions) + "` :no_entry:").queue();
			return;
		}
		
		Request request;
		try {
			request = new Request.Builder()
					.url(new URL("https://api.dc01.gamelockerapp.com/shards/" + region + "/players?filter[playerNames]=" + playerName))
					.addHeader("Authorization", TokenUtils.VAIN_GLORY)
					.addHeader("Accept", "application/vnd.api+json")
					.build();
		} catch (MalformedURLException e) {
			event.reply("Oops something went wrong there, try again :no_entry:").queue();
			return;
		}
		
		Sx4Bot.client.newCall(request).enqueue((Sx4Callback) response -> {
			JSONObject json;
			try {
				json = new JSONObject(response.body().string());
			} catch(JSONException e) {
				event.reply("The vain glory api is currently down :no_entry:").queue();
				return;
			}
			
			if (json.has("errors")) {
				event.reply("I could not find that player :no_entry:").queue();
				return;
			}
			
			JSONObject userData = json.getJSONArray("data").getJSONObject(0).getJSONObject("attributes");
			EmbedBuilder embed = new EmbedBuilder();
			embed.setColor(event.getMember().getColor());
			embed.setAuthor(playerName, null, event.getAuthor().getEffectiveAvatarUrl());
			embed.setTimestamp(ZonedDateTime.parse(userData.getString("createdAt")));
			embed.setFooter("ID: " + json.getJSONArray("data").getJSONObject(0).getString("id") + " | Created", null);
			
			String gamesPlayed = "";
			Iterator<String> keysGamesPlayed = userData.getJSONObject("stats").getJSONObject("gamesPlayed").keys();
			while (keysGamesPlayed.hasNext()) {
				String keyGamesPlayed = keysGamesPlayed.next();
				if (userData.getJSONObject("stats").getJSONObject("gamesPlayed").get(keyGamesPlayed) instanceof Integer) {
					gamesPlayed += GeneralUtils.title(keyGamesPlayed.replace("_", " ")) + String.format(": %,d\n", userData.getJSONObject("stats").getJSONObject("gamesPlayed").getInt(keyGamesPlayed));
				}
			}
			
			embed.addField("Games Played", String.format("%sWins: %,d", gamesPlayed, userData.getJSONObject("stats").getInt("wins")), true);
			
			String eloEarned = "";
			Iterator<String> eloKeys = userData.getJSONObject("stats").keys();
			while (eloKeys.hasNext()) {
				String eloKey = eloKeys.next();
				if (eloKey.contains("season")) {
					eloEarned += String.format("%s: %,d\n", GeneralUtils.title(eloKey.split("_", 3)[2].replace("_", " ")), userData.getJSONObject("stats").getInt(eloKey));
				}
			}
			
			embed.addField("ELO Earned", eloEarned, true);
			embed.addField("Levels", String.format("Level %,d\n%,d XP\nKarma Level: %,d", userData.getJSONObject("stats").getInt("level"), userData.getJSONObject("stats").getInt("xp"), userData.getJSONObject("stats").getInt("karmaLevel")), true);
			embed.addField("Streaks", String.format("Win Streak: %,d\nLoss Streak: %,d", userData.getJSONObject("stats").getInt("winStreak"), userData.getJSONObject("stats").getInt("lossStreak")), true);
			
			String rankedPoints = "";
			Iterator<String> rankedKeys = userData.getJSONObject("stats").getJSONObject("rankPoints").keys();
			while (rankedKeys.hasNext()) {
				String rankedKey = rankedKeys.next();
				rankedPoints += String.format("%s: %,d\n", GeneralUtils.title(rankedKey.replace("_", " ")), userData.getJSONObject("stats").getJSONObject("rankPoints").getInt(rankedKey));
			}
			
			embed.addField("Ranked Points", rankedPoints, true);
			embed.addField("Skill Tier", String.format("%,d", userData.getJSONObject("stats").getInt("skillTier")), true);
			
			event.reply(embed.build()).queue();
		});
	}
	
	@Command(value="quote", description="Gives you a random quote", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"quote"})
	@Cooldown(value=5)
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void quote(CommandEvent event) {
		Request request = new Request.Builder()
				.post(RequestBody.create(null, new byte[0]))
				.url("https://andruxnet-random-famous-quotes.p.mashape.com")
				.addHeader("X-Mashape-Key", TokenUtils.MASHAPE)
				.addHeader("Content-Type", "application/x-www-form-urlencoded")
				.addHeader("Accept", "application/json")
				.build();
		
		Sx4Bot.client.newCall(request).enqueue((Sx4Callback) response -> {
			JSONObject json;
			try {
				json = new JSONArray(response.body().string()).getJSONObject(0);
			} catch (JSONException | IOException e) {
				event.reply("Oops something went wrong there, try again :no_entry:").queue();
				return;
			}
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setColor(event.getMember().getColor());
			embed.setAuthor(json.getString("author"), null, event.getAuthor().getEffectiveAvatarUrl());
			embed.setDescription(json.getString("quote"));
			
			event.reply(embed.build()).queue();
		});
	}
	
	@Command(value="youtube", aliases={"yt"}, description="Search up any video/channel/playlist on youtube")
	@Examples({"youtube pewdiepie", "youtube youtube rewind 2018", "youtube top charts"})
	@Cooldown(value=3)
	public void youtube(CommandEvent event, @Argument(value="query", endless=true) String query) {
		Request request = new Request.Builder()
				.url("https://www.googleapis.com/youtube/v3/search?key=" + TokenUtils.YOUTUBE + "&part=snippet&safeSearch=none&maxResults=1&q=" + query)
				.build();
		
		Sx4Bot.client.newCall(request).enqueue((Sx4Callback) response -> {
			JSONObject json;
			try {
				json = new JSONObject(response.body().string());
			} catch (JSONException | IOException e) {
				event.reply("Oops something went wrong there, try again :no_entry:").queue();
				return;
			}
			
			JSONArray items = json.getJSONArray("items");
			if (items.isEmpty()) {
				event.reply("I could not find any results :no_entry:").queue();
				return;
			}
			
			JSONObject result = items.getJSONObject(0);
			JSONObject id = result.getJSONObject("id");
			JSONObject snippet = result.getJSONObject("snippet");
			JSONObject thumbnails = snippet.getJSONObject("thumbnails");
			
			ZonedDateTime uploadedAt = ZonedDateTime.parse(snippet.getString("publishedAt"));
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setColor(16711680);
			embed.setDescription(snippet.getString("description"));
			embed.setImage(thumbnails.getJSONObject("high").getString("url"));
			
			if (id.getString("kind").equals("youtube#channel")) {
				embed.setTitle(snippet.getString("title"), "https://www.youtube.com/channel/" + id.getString("channelId"));
				embed.addField("Created At", uploadedAt.format(this.formatter), true);
			} else if (id.getString("kind").equals("youtube#video")) {
				embed.setTitle(snippet.getString("title"), "https://www.youtube.com/watch?v=" + id.getString("videoId"));
				
				String state = snippet.getString("liveBroadcastContent");
				embed.addField("Uploaded By", "[" + snippet.getString("channelTitle") + "](https://www.youtube.com/channel/" + snippet.getString("channelId") + ")", true);
				embed.addField("Uploaded At", uploadedAt.format(this.formatter), true);
				embed.addField("State", state.equals("live") ? "Live Now" : state.equals("upcoming") ? "Scheduled" : "Uploaded", true);
			} else {
				embed.setTitle(snippet.getString("title"), "https://www.youtube.com/playlist?list=" + id.getString("playlistId"));
				embed.addField("Uploaded By", "[" + snippet.getString("channelTitle") + "](https://www.youtube.com/channel/" + snippet.getString("channelId") + ")", true);
				embed.addField("Uploaded At", uploadedAt.format(this.formatter), true);
			}
			
			event.reply(embed.build()).queue();
		});
	}
	
	@Command(value="shorten", description="Shortens a specified url")
	@Examples({"shorten https://patreon.com/Sx4", "shorten https://youtube.com/user/PewDiePie"})
	@Cooldown(value=10)
	public void shorten(CommandEvent event, @Argument(value="url", endless=true) String url) {
		Request request = new Request.Builder()
				.url("https://api-ssl.bitly.com/v4/bitlinks")
				.post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), "{\"long_url\":\"" + url + "\"}"))
				.addHeader("Authorization", "Bearer " + TokenUtils.BITLY)
				.addHeader("Content-Type", "application/json")
				.build();
		
		Sx4Bot.client.newCall(request).enqueue((Sx4Callback) response -> {
			JSONObject json;
			try {
				json = new JSONObject(response.body().string());
			} catch (JSONException | IOException e) {
				event.reply("Oops something went wrong there, try again :no_entry:").queue();
				return;
			}
			
			if (json.has("message")) {
				event.reply("The url provided was invalid :no_entry:").queue();
				return;
			}
			
			event.reply("<" + json.getString("link") + ">").queue();
		});
	}
	
	@Command(value="meme", description="Gives you a random meme from reddit", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"meme"})
	@Cooldown(value=7)
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void meme(CommandEvent event) {
		String[] urls = {"https://www.reddit.com/r/dankmemes.json?sort=new&limit=100", "https://www.reddit.com/r/memeeconomy.json?sort=new&limit=100"};
		String url = urls[RANDOM.nextInt(urls.length)];
		
		Request request = new Request.Builder().url(url).build();
		
		Sx4Bot.client.newCall(request).enqueue((Sx4Callback) response -> {
			JSONObject json;
			try {
				json = new JSONObject(response.body().string());
			} catch (JSONException | IOException e) {
				event.reply("Oops something went wrong there, try again :no_entry:").queue();
				return;
			}
			
			JSONObject data = json.getJSONObject("data").getJSONArray("children").getJSONObject(RANDOM.nextInt(100)).getJSONObject("data");
			EmbedBuilder embed = new EmbedBuilder();
			embed.setAuthor(data.getString("title"), "https://www.reddit.com" + data.getString("permalink"), "http://i.imgur.com/sdO8tAw.png");
			embed.setImage(data.getString("url"));
			embed.setFooter("Score: " + data.getInt("score"), null);
			
			event.reply(embed.build()).queue();
		});
	}
	
	@Command(value="discord meme", aliases={"discordmeme"}, description="Gives you a random discord meme", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"discord meme"})
	@Cooldown(value=3)
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void discordMeme(CommandEvent event) {
		Request request = new Request.Builder()
				.url("https://api.weeb.sh/images/random?type=discord_memes")
				.addHeader("Authorization", "Wolke " + TokenUtils.WEEB_SH)
				.addHeader("User-Agent", "Mozilla/5.0")
				.build();
		
		Sx4Bot.client.newCall(request).enqueue((Sx4Callback) response -> {
			JSONObject json;
			try {
				json = new JSONObject(response.body().string());
			} catch (JSONException | IOException e) {
				event.reply("Oops something went wrong there, try again :no_entry:").queue();
				return;
			}
			
			EmbedBuilder embed = new EmbedBuilder()
					.setImage(json.getString("url"))
					.setFooter("Powered by weeb.sh", null);
			
			event.reply(embed.build()).queue();
		});
	}
	
	@Command(value="google", description="Returns the first 5 google search results from your query")
	@Examples({"google Sx4 discord bot", "google youtube"})
	@Cooldown(value=3)
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void google(CommandEvent event, @Argument(value="query", endless=true) String query) {
		GoogleSearchCache.INSTANCE.retrieveResultsByQuery(query.toLowerCase(), event.getTextChannel().isNSFW(), (results, exception) -> {
			if (exception != null) {
				if (exception instanceof ForbiddenException) {
					event.reply(exception.getMessage() + " :no_entry:").queue();
				} else {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				}
			} else {
				if (results.isEmpty()) {
					event.reply("I could not find any results :no_entry:").queue();
					return;
				}
				
				PagedResult<GoogleSearchResult> paged = new PagedResult<>(results)
						.setDeleteMessage(false)
						.setIndexed(false)
						.setPerPage(5)
						.setAuthor("Google", "https://www.google.co.uk/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8), "http://i.imgur.com/G46fm8J.png")
						.setFunction(result -> {
							return String.format("**[%s](%s)**\n%s\n", result.getTitle(), result.getLink(), result.getSnippet());
						});
				
				PagedUtils.getPagedResult(event, paged, 300, null);
			}
		});
	}
	
	@Command(value="google image", aliases={"googleimage"}, description="Returns an image from google based on your query")
	@Examples({"google image car", "google image doggo", "google image cat"})
	@Cooldown(value=3)
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void googleImage(CommandEvent event, @Argument(value="query", endless=true) String query) {
		GoogleSearchCache.INSTANCE.retrieveResultsByQuery(query.toLowerCase(), true, event.getTextChannel().isNSFW(), (results, exception) -> {
			if (exception != null) {
				if (exception instanceof ForbiddenException) {
					event.reply(exception.getMessage() + " :no_entry:").queue();
				} else {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				}
			} else {
				if (results.isEmpty()) {
					event.reply("I could not find any results :no_entry:").queue();
					return;
				}
				
				PagedResult<GoogleSearchResult> paged = new PagedResult<>(results)
						.setDeleteMessage(false)
						.setPerPage(1)
						.setCustomFunction(page -> {
							EmbedBuilder embed = new EmbedBuilder();
							embed.setAuthor("Google", "https://www.google.co.uk/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&tbm=isch", "http://i.imgur.com/G46fm8J.png");
							embed.setTitle("Image " + page.getCurrentPage() + "/" + page.getMaxPage());
							embed.setFooter("next | previous | go to <page_number> | cancel", null);
							
							List<GoogleSearchResult> data = page.getArray();
							for (int i = page.getCurrentPage() * page.getPerPage() - page.getPerPage(); i < page.getCurrentPage() * page.getPerPage(); i++) {
								try {
									embed.setImage(data.get(i).getLink());
								} catch (IndexOutOfBoundsException e) {
									break;
								}
							}
							
							return embed.build();
						});
				
				PagedUtils.getPagedResult(event, paged, 300, null);
			}
		});
	}
	
	@SuppressWarnings("unchecked")
	@Command(value="igdb", description="Search up any game on IGDB")
	@Examples({"igdb human fall", "igdb uno --sort=release --reverse", "igdb grand theft auto --sort=rating --rating=>80"})
	@Cooldown(value=3)
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void igdb(CommandEvent event, @Argument(value="game", endless=true, nullDefault=true) String game, @Option(value="rating", description="Filters results based on their rating") String rating, @Option(value="sort", description="Sort results by `name` (default), `rating`, `popularity` and `release`") String sort, @Option(value="reverse", description="Reverses sorting order") boolean reverse) {
		StringBuilder filters = new StringBuilder(";where category = 0");
		StringBuilder query = new StringBuilder("fields name,total_rating,total_rating_count,first_release_date,genres.name,url,summary,cover.image_id,platforms.name,popularity;limit 500");
		if (game != null && sort == null && !reverse) {
			query.append(";search \"" + game + "\"");
		} else {
			sort = sort == null ? "name" : sort;
			String sortingOrder = reverse ? ":asc" : ":desc";
			switch (sort) {
				case "release":
					query.append(";sort first_release_date" + sortingOrder);
					filters.append("& first_release_date != n");
					break;
				case "rating":
					query.append(";sort total_rating" + sortingOrder);
					filters.append("& total_rating != n");
					break;
				case "popularity":
					query.append(";sort popularity" + sortingOrder);
					break;
				default:
					query.append(";sort name" + sortingOrder);
					break;
			}
			
			if (game != null) {
				filters.append("& name ~ \"" + game + "\"*");
			}
		}
		
		if (rating != null) {
			if (rating.contains("-")) {
				String[] ratingSplit = rating.split("-", 2);
				boolean reverseRating = rating.startsWith("!");
				try {
					int lowerBound = Integer.parseInt(reverseRating ? ratingSplit[0].substring(1) : ratingSplit[0]);
					int upperBound = Integer.parseInt(ratingSplit[1]);
					if (lowerBound >= 0 && upperBound <= 100 && upperBound >= lowerBound) {
						filters.append("& (total_rating " + (reverseRating ? "<" : ">=") + lowerBound + (reverseRating ? "|" : "&") + " total_rating " + (reverseRating ? ">" : "<") + upperBound + ")");
					}
				} catch (NumberFormatException e) {}
			} else if (GeneralUtils.isNumberUnsigned(rating)) {
				filters.append("& total_rating = " + rating);
			} else {
				int index = 0;
				StringBuilder prefix = new StringBuilder();
				while (!Character.isDigit(rating.charAt(index)) && index != rating.length() - 1) {
					prefix.append(rating.charAt(index++));
				}
				
				if (index != rating.length() - 1) {
					try {
						int bound = Integer.parseInt(rating.substring(index));
						switch (prefix.toString()) {
							case "<":
								filters.append("& total_rating < " + bound);
								break;
							case "<=":
								filters.append("& total_rating <= " + bound);
								break;
							case ">":
								filters.append("& total_rating > " + bound);
								break;
							case ">=":
								filters.append("& total_rating >= " + bound);
								break;
							case "=":
								filters.append("& total_rating = " + bound);
								break;
							case "==":
								filters.append("& total_rating = " + bound);
								break;
							case "!=":
								filters.append("& total_rating != " + bound);
								break;
							case "!":
								filters.append("& total_rating != " + bound);
								break;
						}
					} catch(NumberFormatException e) {}
				}
			}
		}
		
		query.append(filters.toString());
		query.append(";");
		
		Request request = new Request.Builder()
				.url("https://api-v3.igdb.com/games/")
				.post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), query.toString()))
				.addHeader("user-key", TokenUtils.IGDB)
				.build();
		
		Sx4Bot.client.newCall(request).enqueue((Sx4Callback) response -> {
			JSONArray results = new JSONArray(response.body().string());
			if (results.isEmpty()) {
				event.reply("I could not find any games :no_entry:").queue();
				return;
			}
			
			PagedResult<Object> paged = new PagedResult<>(results.toList())
					.setAutoSelect(true)
					.setIncreasedIndex(true)
					.setSelectableByIndex(true)
					.setAuthor("IGDB Search", null, "http://bit.ly/2NXGwMz")
					.setFunction(data -> (String) ((Map<String, Object>) data).get("name"));
			
			PagedUtils.getPagedResult(event, paged, 300, onReturn -> {
				JSONObject data = new JSONObject((Map<String, Object>) onReturn.getData());
				
				EmbedBuilder embed = new EmbedBuilder();
				embed.setAuthor(data.getString("name"), data.getString("url"), "http://bit.ly/2NXGwMz");
				embed.setDescription(GeneralUtils.limitString(data.optString("summary", "This game has no description :("), MessageEmbed.TEXT_MAX_LENGTH, "... [Read More](" + data.getString("url") + ")"));
				embed.setThumbnail(data.has("cover") ? String.format("https://images.igdb.com/igdb/image/upload/t_thumb/%s.jpg", data.getJSONObject("cover").getString("image_id")) : null);
				
				int ratings = data.optInt("total_rating_count");
				embed.addField("Rating", data.has("total_rating") ? String.format("%.2f out of %,d rating%s", data.getDouble("total_rating"), ratings, ratings == 1 ? "" : "s") : "Unknown", true);
				embed.addField("Release Date", data.has("first_release_date") ? LocalDateTime.ofEpochSecond(data.getLong("first_release_date"), 0, ZoneOffset.UTC).format(this.formatter) : "Unknown", true);
				embed.addField("Popularity", String.format("%.2f", data.optDouble("popularity")), true);
				embed.addField("Genres", data.has("genres") ? String.join("\n", data.getJSONArray("genres").toList().stream().map(genre -> (String) ((Map<String, Object>) genre).get("name")).collect(Collectors.toList())) : "None", true);
				embed.addField("Platforms", data.has("platforms") ? String.join("\n", data.getJSONArray("platforms").toList().stream().map(platform -> (String) ((Map<String, Object>) platform).get("name")).collect(Collectors.toList())) : "None", true);
				
				event.reply(embed.build()).queue();
			});
		});
	}
	
	@Command(value="dictionary", aliases={"define"}, description="Look up any word to see its definition")
	@Examples({"dictionary phenomenal", "dictionary antidisestablishmentarianism"})
	@Cooldown(value=5)
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void dictionary(CommandEvent event, @Argument(value="word", endless=true) String word) {
		DictionaryCache.INSTANCE.retrieveResultByQuery(word.toLowerCase(), (result, exception) -> {
			if (exception != null) {
				if (exception instanceof ForbiddenException) {
					event.reply(exception.getMessage() + " :no_entry:").queue();
				} else {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				}
			} else {
				if (result == null || !result.hasDefinition()) {
					event.reply("I could not find a definition for that word :no_entry:").queue();
					return;
				}
				
				EmbedBuilder embed = new EmbedBuilder();
				embed.setAuthor(GeneralUtils.title(result.getId()), "https://en.oxforddictionaries.com/definition/" + URLEncoder.encode(word.toLowerCase(), StandardCharsets.UTF_8));
				embed.addField("Definition", result.getDefinition() + (result.hasExample() ? "\n\n*" + result.getExample() + "*" : ""), false);
				
				if (result.hasAudioFile()) {
					embed.addField("Pronunciation", "[Download Here](" + result.getAudioFile() + ")", false);
				}
				
				event.reply(embed.build()).queue();
			}
		});
	}
	
	@Command(value="steam", aliases={"steam profile", "steamprofile"}, description="Look up any users steam profile", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"steam dog", "steam https://steamcommunity.com/id/dog"})
	@Cooldown(value=10)
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void steam(CommandEvent event, @Argument(value="vanity url", endless=true) String vanityUrl) {
		vanityUrl = vanityUrl.replace("https://steamcommunity.com/id/", "");
		String profileUrl = vanityUrl;
		Request requestForId = new Request.Builder().url("http://api.steampowered.com/ISteamUser/ResolveVanityURL/v0001/?key=" + TokenUtils.STEAM + "&vanityurl=" + vanityUrl).build();
		
		Sx4Bot.client.newCall(requestForId).enqueue((Sx4Callback) responseForId -> {
			JSONObject jsonForId;
			try {
				jsonForId = new JSONObject(responseForId.body().string());
			} catch (JSONException | IOException e) {
				event.reply("Oops something went wrong there, try again :no_entry:").queue();
				return;
			}
			
			String id;
			if (jsonForId.getJSONObject("response").has("message")) {
				id = profileUrl;
			} else {
				id = jsonForId.getJSONObject("response").getString("steamid");
			}
			
			Request request = new Request.Builder().url("http://api.steampowered.com/ISteamUser/GetPlayerSummaries/v0002/?key=" + TokenUtils.STEAM + "&steamids=" + id).build();
			
			Sx4Bot.client.newCall(request).enqueue((Sx4Callback) response -> {
				JSONObject json;
				try {
					json = new JSONObject(response.body().string());
				} catch (JSONException | IOException e) {
					event.reply("Oops something went wrong there, try again :no_entry:").queue();
					return;
				}
				
				JSONArray players = json.getJSONObject("response").getJSONArray("players");
				if (players.toList().isEmpty()) {
					event.reply("I could not find any results :no_entry:").queue();
					return;
				}
				
				JSONObject player = players.getJSONObject(0);
				String lastLoggedOn = LocalDateTime.ofEpochSecond(player.getLong("lastlogoff"), 0, ZoneOffset.UTC).format(this.formatter);
				
				String status;
				if (player.getInt("personastate") == 0) {
					if (player.getInt("communityvisibilitystate") == 1) {
						status = "Private Account";
					} else {
						status = "Offline";
					}
				} else if (player.getInt("personastate") == 1 || player.getInt("personastate") == 5 || player.getInt("personastate") == 6) {
					status = "Online";
				} else if (player.getInt("personastate") == 2) {
					status = "Busy";
				} else if (player.getInt("personastate") == 3) {
					status = "Away";
				} else if (player.getInt("personastate") == 4) {
					status = "Snooze";
				} else {
					status = "Unknown";
				}
				
				EmbedBuilder embed = new EmbedBuilder();
				embed.setDescription("Steam Profile <:steam:530830699821793281>");
				embed.setAuthor(player.getString("personaname"), player.getString("profileurl"), player.getString("avatarfull"));
				embed.setThumbnail(player.getString("avatarfull"));
				embed.addField("Status", status, true);
				
				if (status.equals("Offline")) {
					embed.addField("Last Time Logged In", lastLoggedOn, true);
				} else {
					embed.addField("Last Time Logged In", "Currently Online", true);
				}
				
				if (player.getInt("communityvisibilitystate") != 1) {
					if (player.has("realname")) {
						embed.addField("Real Name", player.getString("realname"), true);
					}
					
					if (player.has("gameextrainfo")) {
						embed.addField("Currently Playing", player.getString("gameextrainfo"), true);
					}
				}
				
				event.reply(embed.build()).queue();
			});
		});
	}
	
	Pattern hyperLink = Pattern.compile("\\[(.*?)\\]");
	
	@Command(value="urban dictionary", aliases={"urban", "urbandictionary", "ud"}, description="Look up definitions on the urban dictionary")
	@Examples({"urban dictionary hello", "urban dictionary dog"})
	@Cooldown(value=3)
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void urbanDictionary(CommandEvent event, @Argument(value="word", endless=true) String word) {
		if (!event.getTextChannel().isNSFW()) {
			event.reply("You can not use this command in a non-nsfw channel :no_entry:").queue();
			return;
		}
		
		Request request;
		try {
			request = new Request.Builder().url(new URL("http://api.urbandictionary.com/v0/define?term=" + word)).build();
		} catch (MalformedURLException e) {
			event.reply("Oops something went wrong there, try again :no_entry:").queue();
			return;
		}
		
		Sx4Bot.client.newCall(request).enqueue((Sx4Callback) response -> {
			JSONObject json;
			try {
				json = new JSONObject(response.body().string());
			} catch (JSONException | IOException e) {
				event.reply("Oops something went wrong there, try again :no_entry:").queue();
				return;
			}
			
			if (json.getJSONArray("list").toList().isEmpty()) {
				event.reply("I could not find any results :no_entry:").queue();
				return;
			}
			
			PagedResult<Object> paged = new PagedResult<>(json.getJSONArray("list").toList())
					.setPerPage(1)
					.setDeleteMessage(false)
					.setCustomFunction(page -> {
						JSONObject data = new JSONArray(page.getArray()).getJSONObject(page.getCurrentPage() - 1); 
						
						Matcher hyperLinkMatchDefinition = hyperLink.matcher(data.getString("definition"));
						String definition = data.getString("definition");
						List<String> matchesDefinition = new ArrayList<>();
						while (hyperLinkMatchDefinition.find()) {
							matchesDefinition.add(hyperLinkMatchDefinition.group());
						}
						
						for (String matchDefinition : matchesDefinition) {
							definition = definition.replace(matchDefinition, matchDefinition + "(https://www.urbandictionary.com/define.php?term=" + URLEncoder.encode(matchDefinition.replace("[", "").replace("]", ""), StandardCharsets.UTF_8) + ")");
						}
						
						Matcher hyperLinkMatchExample = hyperLink.matcher(data.getString("example"));
						String example = data.getString("example");
						Set<String> matchesExample = new HashSet<>();
						while (hyperLinkMatchExample.find()) {
							matchesExample.add(hyperLinkMatchExample.group());
						}
						
						for (String matchExample : matchesExample) {
							example = example.replace(matchExample, matchExample + "(https://www.urbandictionary.com/define.php?term=" + URLEncoder.encode(matchExample.replace("[", "").replace("]", ""), StandardCharsets.UTF_8) + ")");
						}
						
						EmbedBuilder embed = new EmbedBuilder();
						embed.setColor(event.getMember().getColor());
						embed.setFooter("next | previous | go to <page_number> | cancel", null);
						embed.setTitle("Page " + page.getCurrentPage() + "/" + page.getMaxPage());
						embed.setAuthor(data.getString("word"), data.getString("permalink"), null);
						embed.addField("Definition", definition.length() > 950 ? definition.substring(0, 950) + "... [Read more](" + data.getString("permalink") + ")" : definition, false);
						if (!data.getString("example").equals("")) {
							embed.addField("Example", example.length() > 950 ? example.substring(0, 950) + "... [Read more](" + data.getString("permalink") + ")" : example, false);
						}
						
						embed.addField("Upvotes", data.getInt("thumbs_up") + " 👍", true);
						embed.addField("Downvotes", data.getInt("thumbs_down") + " 👎", true);
						
						return embed.build();
					});
			
			PagedUtils.getPagedResult(event, paged, 300, null);
		});
	}
	
	@Command(value="say", description="The bot will repeat whatever you say")
	@Examples({"say hello there"})
	public void say(CommandEvent event, @Argument(value="text", endless=true) String text) {
		text = FunUtils.escapeMentions(event.getGuild(), text);
		event.reply(text.substring(0, Math.min(text.length(), 2000))).queue();
	}
	
	@Command(value="spoilerfy", description="The bot will return your specified text into a spoiler per character")
	@Examples({"spoilerfy hidden message"})
	public void spoilerfy(CommandEvent event, @Argument(value="text", endless=true) String text) {
		String content = "";
		for (char character : text.toCharArray()) {
			if (character != ' ') {
				content += "||" + character + "||";
			} else {
				content += " ";
			}
		}
		
		text = FunUtils.escapeMentions(event.getGuild(), content);
		event.reply(text.substring(0, Math.min(text.length(), 2000))).queue();
	}
	
	@Command(value="embed", aliases={"esay"}, description="The bot will repeat whatever you say in an embed")
	@Examples({"embed hello there"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void embed(CommandEvent event, @Argument(value="text", endless=true) String text) {
		EmbedBuilder embed = new EmbedBuilder();
		embed.setColor(event.getMember().getColor());
		embed.setAuthor(event.getAuthor().getAsTag(), null, event.getAuthor().getEffectiveAvatarUrl());
		embed.setDescription(text.substring(0, Math.min(text.length(), 2000)));
		
		event.reply(embed.build()).queue();
	}
	
	@Command(value="developer embed", aliases={"developerembed", "dev embed", "devembed"}, description="Create more in depth embeds if you know the syntax of json")
	@Examples({
		"developer embed {\"title\": \"My title\", \"description\": \"My description\"}", 
		"developer embed {\"author\": {\"name\": \"My author name\", \"icon_url\": \"url here\"}, \"description\": \"My description\"}", 
		"developer embed {\"fields\": [{\"name\": \"My field name\", \"value\": \"My field value\"}, {\"name\": \"My field name\", \"value\": \"My field value\", \"inline\": false}]}"
	})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void developerEmbed(CommandEvent event, @Argument(value="json", endless=true) JSONObject json) {
		EmbedBuilder embed = new EmbedBuilder();
		if (json.has("title")) {
			if (json.getString("title").length() > 256) {
				event.reply("Embed titles cannot be longer than 256 characters :no_entry:").queue();
				return;
			}
			
			embed.setTitle(json.getString("title"));
		}
		
		if (json.has("description")) {
			if (json.getString("description").length() > 2048) {
				event.reply("Embed descriptions cannot be longer than 2048 characters :no_entry:").queue();
				return;
			}
			
			embed.setDescription(json.getString("description"));
		}
		
		if (json.has("colour")) {
			Object colour = json.get("colour");
			Matcher hexMatch = ArgumentUtils.HEX_REGEX.matcher(String.valueOf(colour));
			if (hexMatch.matches()) {
				embed.setColor(Color.decode("#" + hexMatch.group(1)));
			} else {
				try {
					int colourInt = (int) colour;
					embed.setColor(new Color(colourInt));
				} catch(ClassCastException e) {
					event.reply("Invalid hex was given for the embed colour :no_entry:").queue();
					return;
				}
			}
		}
		
		if (json.has("fields")) {
			JSONArray fields = json.getJSONArray("fields");
			if (fields.toList().size() > 25) {
				event.reply("You cannot have more than 25 fields in an embed :no_entry:").queue();
				return;
			}
			
			for (int i = 0; i < fields.toList().size(); i++) {
				JSONObject field = fields.getJSONObject(i);
				if (field.has("name") && field.has("value")) {
					boolean inline = true;
					if (field.has("inline")) {
						inline = field.getBoolean("inline");
					}
					
					embed.addField(field.getString("name"), field.getString("value"), inline);
				} else {
					event.reply("`name` and `value` are required parameters for a field in an embed (Field at index " + i + ") :no_entry:").queue();
					return;
				}
			}
		}
		
		if (json.has("image")) {
			embed.setImage(json.getString("image"));
		}
		
		if (json.has("thumbnail")) {
			embed.setThumbnail(json.getString("thumbnail"));
		}
		
		if (json.has("footer")) {
			JSONObject footer = json.getJSONObject("footer");
			if (footer.has("text")) {
				if (footer.getString("text").length() > 2048) {
					event.reply("Embed footers cannot be longer than 2048 characters :no_entry:").queue();
					return;
				}
				
				embed.setFooter(footer.getString("text"), footer.has("icon_url") ? footer.getString("icon_url") : null);
			} else {
				event.reply("`text` is a required paramater for a footer in an embed :no_entry:").queue();
				return;
			}
		}
		
		if (json.has("author")) {
			JSONObject author = json.getJSONObject("author");
			if (author.has("name")) {
				if (author.getString("name").length() > 256) {
					event.reply("Names in authors cannot be longer than 256 characters in an embed :no_entry:").queue();
					return;
				}
				
				embed.setAuthor(author.getString("name"), author.has("url") ? author.getString("url") : null, author.has("icon_url") ? author.getString("icon_url") : null);
			} else {
				event.reply("`name` is a required parameter for an author in an embed :no_entry:").queue();
				return;
			}
		}
		
		if (embed.isEmpty()) {
			event.reply("The embed has to contain at least something :no_entry:").queue();
			return;
		}
		
		event.reply(embed.build()).queue();
	}
	
	@Command(value="clapify", description="The bot will return your text with a clap emote in between each word")
	@Examples({"clapify some angry message"})
	public void clapify(CommandEvent event, @Argument(value="text", endless=true) String text) {
		text = FunUtils.escapeMentions(event.getGuild(), text.replace(" ", " :clap: "));
		event.reply(text.substring(0, Math.min(text.length(), 2000))).queue();
	}
	
	@Command(value="ascend", description="The bot will return your text with a space in between each character")
	@Examples({"ascend hello there"})
	public void ascend(CommandEvent event, @Argument(value="text", endless=true) String text) {
		text = FunUtils.escapeMentions(event.getGuild(), text.replace("", " "));
		event.reply(text.substring(0, Math.min(text.length(), 2000))).queue();
	}
	
	@Command(value="backwards", aliases={"reverse"}, description="The bot will return your text reversed")
	@Examples({"backwards hello there"})
	public void backwards(CommandEvent event, @Argument(value="text", endless=true) String text) {
		String content = "";
		for (int i = text.length() - 1; i >= 0; i--) {
			content += text.charAt(i);
		}
		
		text = FunUtils.escapeMentions(event.getGuild(), content);
		event.reply(text.substring(0, Math.min(text.length(), 2000))).queue();
	}
	
	@Command(value="random caps", aliases={"randomcaps", "rand caps", "randcaps"}, description="The bot will return your text with the characters randomly being lower case or upper case")
	@Examples({"random caps hello there"})
	public void randomCaps(CommandEvent event, @Argument(value="text", endless=true) String text) {
		String content = "";
		for (char character : text.toCharArray()) {
			int number = RANDOM.nextInt(2);
			if (number == 1) {
				content += Character.toUpperCase(character);
			} else if (number == 0) {
				content += Character.toLowerCase(character);
			}
		}
		
		text = FunUtils.escapeMentions(event.getGuild(), content);
		event.reply(content.substring(0, Math.min(text.length(), 2000))).queue();
	}
	
	@Command(value="alternate caps", aliases={"alt caps", "altcaps", "alternatecaps"}, description="The bot will return your text with the characters alternating between being lower case or upper case")
	@Examples({"alternate caps hello there"})
	public void alternateCaps(CommandEvent event, @Argument(value="text", endless=true) String text) {
		String content = "";
		for (int i = 0; i < text.length(); i++) {
			if (i % 2 == 0) {
				content += Character.toUpperCase(text.charAt(i));
			} else {
				content += Character.toLowerCase(text.charAt(i));
			}
		}
		
		text = FunUtils.escapeMentions(event.getGuild(), content);
		event.reply(text.substring(0, Math.min(text.length(), 2000))).queue();
	}
	
	private final List<String> rockChoices = List.of("r", "rock");
	private final List<String> paperChoices = List.of("p", "paper");
	private final List<String> scissorsChoices = List.of("s", "scissors", "scissor");
	private final List<String> rpsEmotes = List.of("Rock :moyai:", "Paper :page_facing_up:", "Scissors :scissors:");
	
	@Command(value="rps", aliases={"rock paper scissors", "rockpaperscissors"}, description="Play rock paper scissors again the bot")
	@Examples({"rps rock", "rps paper", "rps scissors"})
	public void rps(CommandEvent event, @Context Database database, @Argument(value="rock | paper | scissors") String choice) {
		int choiceInt;
		if (this.rockChoices.contains(choice)) {
			choiceInt = 0;
		} else if (this.paperChoices.contains(choice)) {
			choiceInt = 1;
		} else if (this.scissorsChoices.contains(choice)) {
			choiceInt = 2;
		} else {
			event.reply("Your choice has to be `rock`, `paper` or `scissors` :no_entry:").queue();
			return;
		}
		
		int botChoice = RANDOM.nextInt(3);
		Bson update = new BsonDocument(), projection = Projections.include("rps");
		String key;
		StringBuilder outcome = new StringBuilder(event.getAuthor().getName() + ": " + this.rpsEmotes.get(choiceInt) + "\n" + event.getSelfUser().getName() + ": " + this.rpsEmotes.get(botChoice));
		if (choiceInt == botChoice) {
			update = Updates.inc("rps.draws", 1);
			key = "draws";
			outcome.append("\n\nDraw, let's go again! Your **%s** draw!");
		} else if ((botChoice == 2 && choiceInt == 0) || choiceInt - 1 == botChoice) {
			update = Updates.inc("rps.wins", 1);
			key = "wins";
			outcome.append("\n\nYou win, Your **%s** win! :trophy:");
		} else {
			update = Updates.inc("rps.losses", 1);
			key = "losses";
			outcome.append("\n\nYou lose, better luck next time. Your **%s** loss!");
		}
	
		database.getUserByIdAndUpdate(event.getAuthor().getIdLong(), update, projection, (result, exception) -> {
			if (exception != null) {
				exception.printStackTrace();
				event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
			} else {
				String amount = GeneralUtils.getNumberSuffix(result.getEmbedded(List.of("rps", key), Integer.class));
				event.replyFormat(outcome.toString(), amount).queue();
			}
		});
	}
	
	@Command(value="rps stats", aliases={"rpsstats", "rpss", "rock paper scissors stats", "rockpaperscissorsstats"}, description="View your rock paper scissors stats")
	@Examples({"rps stats", "rps stats @Shea#6653", "rps stats 402557516728369153", "rps stats Shea"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void rpsStats(CommandEvent event, @Context Database database, @Argument(value="user", endless=true, nullDefault=true) String userArgument) {
		Member member;
		if (userArgument == null) {
			member = event.getMember();
		} else {
			member = ArgumentUtils.getMember(event.getGuild(), userArgument);
			if (member == null) {
				event.reply("I could not find that user :no_entry:").queue();
				return;
			}
		}
		
		Document data = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("rps")).get("rps", Database.EMPTY_DOCUMENT);
		int losses = data.getInteger("losses", 0), draws = data.getInteger("draws", 0), wins = data.getInteger("wins", 0);
		int allGames = losses + wins + draws;
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setColor(member.getColor());
		embed.setAuthor(member.getUser().getAsTag(), null, member.getUser().getEffectiveAvatarUrl());
		embed.setDescription(String.format("Wins: %,d\nDraws: %,d\nLosses: %,d\n\nWin Percentage: %.2f%%", wins, draws, losses, allGames == 0 ? 0 : (((float) wins / allGames) * 100)));
		
		event.reply(embed.build()).queue();
	}

	@Initialize(all=true, subCommands=true, recursive=true)
	public void initialize(CommandImpl command) {
		command.setCategory(Categories.FUN);
	}
	
}
