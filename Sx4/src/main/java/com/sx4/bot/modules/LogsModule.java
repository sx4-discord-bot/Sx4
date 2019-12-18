package com.sx4.bot.modules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.command.Command.AuthorPermissions;
import com.jockie.bot.core.command.Command.BotPermissions;
import com.jockie.bot.core.command.Context;
import com.jockie.bot.core.command.Initialize;
import com.jockie.bot.core.command.impl.CommandEvent;
import com.jockie.bot.core.command.impl.CommandImpl;
import com.jockie.bot.core.module.Module;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.sx4.bot.categories.Categories;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEventListener;
import com.sx4.bot.database.Database;
import com.sx4.bot.interfaces.Examples;
import com.sx4.bot.logger.Category;
import com.sx4.bot.logger.Event;
import com.sx4.bot.logger.util.Utils;
import com.sx4.bot.utils.ArgumentUtils;
import com.sx4.bot.utils.GeneralUtils;
import com.sx4.bot.utils.HelpUtils;
import com.sx4.bot.utils.PagedUtils;
import com.sx4.bot.utils.PagedUtils.PagedResult;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.Webhook;

@Module
public class LogsModule {

	public class LogsCommand extends Sx4Command {
		
		public LogsCommand() {
			super("logs");
			
			super.setAliases("log", "logger");
			super.setDescription("Set up logs in your server to log a variety of actions which happen within the server");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
			super.setExamples("logs toggle", "logs events", "logs stats");
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="toggle", aliases={"enable", "disable"}, description="Enable/disable the logs in the current server", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"logs toggle"})
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void toggle(CommandEvent event, @Context Database database) {
			boolean enabled = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("logger.enabled")).getEmbedded(List.of("logger", "enabled"), false);
			database.updateGuildById(event.getGuild().getIdLong(), Updates.set("logger.enabled", !enabled), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("Logs are now " + (enabled ? "disabled" : "enabled") + " <:done:403285928233402378>").queue();
				}
			});
		}
		
		@Command(value="channel", description="Set the channel for all the logs to be sent to")
		@Examples({"logs channel", "logs channel #general", "logs channel reset"})
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void channel(CommandEvent event, @Context Database database, @Argument(value="channel", endless=true, nullDefault=true) String channelArgument) {
			Document data = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("logger.webhookId", "logger.webhookToken", "logger.channelId")).get("logger", Database.EMPTY_DOCUMENT);
			
			TextChannel channel;
			if (channelArgument == null) {
				channel = event.getTextChannel();
			} else if (channelArgument.equals("reset")) {
				channel = null;
			} else {
				channel = ArgumentUtils.getTextChannel(event.getGuild(), channelArgument);
				if (channel == null) {
					event.reply("I could not find that text channel :no_entry:").queue();
					return;
				}
			}
			
			Long channelId = data.getLong("channelId");
			if (channel == null) {
				if (channelId == null) {
					event.reply("The logs channel is already not set :no_entry:").queue();
					return;
				}
			} else {
				if (channelId != null && channelId == channel.getIdLong()) {
					event.reply("The logs channel is already set to " + channel.getAsMention() + " :no_entry:").queue();
					return;
				}
			}
			
			Bson update = Updates.combine(Updates.set("logger.webhookId", null), Updates.set("logger.webhookToken", null), Updates.set("logger.channelId", channel == null ? channel : channel.getIdLong()));
			
			TextChannel oldChannel = channelId == null ? null : event.getGuild().getTextChannelById(channelId);
			if (oldChannel != null) {
				Long webhookId = data.getLong("webhookId");
				String webhookToken = data.getString("webhookToken");
				if (webhookId != null && webhookToken != null) {
					oldChannel.retrieveWebhooks().queue(webhooks -> {
						for (Webhook webhook : webhooks) {
							if (webhook.getIdLong() == webhookId && webhook.getToken().equals(webhookToken)) {
								webhook.delete().queue();
							}
						}
						
						
						database.updateGuildById(event.getGuild().getIdLong(), update, (result, exception) -> {
							if (exception != null) {
								exception.printStackTrace();
								event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
							} else {
								event.reply("The logs channel has been " + (channel == null ? "reset" : "set to " + channel.getAsMention()) + " <:done:403285928233402378>").queue();
							}
						});
					});		
				} else {
					database.updateGuildById(event.getGuild().getIdLong(), Updates.set("logger.channelId", channel == null ? channel : channel.getIdLong()), (result, exception) -> {
						if (exception != null) {
							exception.printStackTrace();
							event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
						} else {
							event.reply("The logs channel has been " + (channel == null ? "reset" : "set to " + channel.getAsMention()) + " <:done:403285928233402378>").queue();
						}
					});
				}
			} else {
				database.updateGuildById(event.getGuild().getIdLong(), update, (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						event.reply("The logs channel has been " + (channel == null ? "reset" : "set to " + channel.getAsMention()) + " <:done:403285928233402378>").queue();
					}
				});
			}
		}
		
		@Command(value="stats", aliases={"settings", "setting"}, description="View the current setup of logs in this server", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"logs stats"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void stats(CommandEvent event, @Context Database database) {
			Document data = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("logger.enabled", "logger.channelId", "logger.events")).get("logger", Database.EMPTY_DOCUMENT);
			
			Long channelId = data.getLong("channelId");
			TextChannel channel = channelId == null ? null : event.getGuild().getTextChannelById(channelId);
			
			EnumSet<Event> events = Event.getEvents(data.get("events", Event.ALL_EVENTS));
			
			StringBuilder eventList = new StringBuilder();
			for (Event loggerEvent : events) {
				eventList.append(loggerEvent.toString() + "\n");
			}
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setAuthor("Logs Settings", null, event.getGuild().getIconUrl());
			embed.addField("Status", data.getBoolean("enabled", false) ? "Enabled" : "Disabled", true);
			embed.addField("Channel", channel == null ? "Not Set" : channel.getAsMention(), true);
			embed.addField("Enabled Events", events.isEmpty() ? "None" : eventList.toString(), false);
			event.reply(embed.build()).queue();
		}
		
		public class BlacklistCommand extends Sx4Command {
			
			public BlacklistCommand() {
				super("blacklist");
				
				super.setDescription("Blacklists a user, channel or role from being logged in the logger");
				super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
				super.setExamples("logs blacklist set", "logs blacklist remove", "logs blacklist list");
			}
			
			public void onCommand(CommandEvent event) {
				event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
			}
			
			@Command(value="set", description="Sets the blacklist for a specific user, channel or role on events")
			@Examples({"logs blacklist set #general MESSAGE_UPDATE", "logs blacklist set @Shea#6653 MESSAGE_UPDATE MESSAGE_DELETE", "logs blacklist set @Owners MEMBER_ROLE_ADD"})
			@AuthorPermissions({Permission.MANAGE_SERVER})
			public void set(CommandEvent event, @Context Database database, @Argument(value="user | channel | role") String argument, @Argument(value="events", nullDefault=true) String[] eventsArgument) {
				Member member = ArgumentUtils.getMember(event.getGuild(), argument);
				GuildChannel channel = ArgumentUtils.getGuildChannel(event.getGuild(), argument);
				Role role = ArgumentUtils.getRole(event.getGuild(), argument);
				
				if (role == null && channel == null && member == null) {
					event.reply("I could not find that user/role/channel :no_entry:").queue();
					return;
				}
				
				Category category = role != null ? Category.ROLE : member != null ? Category.MEMBER : Category.CHANNEL;
				String type = role != null ? "roles" : member != null ? "users" : "channels";
				long id = role != null ? role.getIdLong() : member != null ? member.getIdLong() : channel.getIdLong();
				
				Document blacklisted = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("logger.blacklisted")).getEmbedded(List.of("logger", "blacklisted"), Database.EMPTY_DOCUMENT);
				
				long currentEventsRaw = 0L;
				List<Document> blacklists = blacklisted.getList(type, Document.class, Collections.emptyList());
				for (Document blacklist : blacklists) {
					if (blacklist.getLong("id") == id) {
						currentEventsRaw = blacklist.getLong("events");
					}
				}
				
				long eventsRaw = 0L;
				if (eventsArgument.length == 1 && eventsArgument[0].toLowerCase().equals("all")) {
					eventsRaw = role != null ? Event.ALL_ROLE_EVENTS : member != null ? Event.ALL_MEMBER_EVENTS : Event.ALL_CHANNEL_EVENTS;
				} else {
					for (String stringEvent : eventsArgument) {
						try {
							Event newEvent = Event.valueOf(stringEvent.toUpperCase());
							if (!newEvent.containsCategory(category)) {
								event.reply("You cannot blacklist a " + category.toString().toLowerCase() + " from the `" + newEvent.toString() + "` event :no_entry:").queue();
								return;
							}
							
							eventsRaw |= newEvent.getRaw();
						} catch(IllegalArgumentException e) {
							event.reply("`" + stringEvent.toUpperCase() + "` is not a valid event :no_entry:").queue();
							return;
						}
					}
				}
				
				Bson update;
				List<Bson> arrayFilters = null;
				if (currentEventsRaw == 0) {
					Document blacklist = new Document("id", id).append("events", eventsRaw);
					update = Updates.push("logger.blacklisted." + type, blacklist);
				} else {
					update = Updates.set("logger.blacklisted." + type + ".$[blacklist].events", eventsRaw);
					arrayFilters = List.of(Filters.eq("blacklist.id", id));
				}
				
				UpdateOptions updateOptions = new UpdateOptions().arrayFilters(arrayFilters).upsert(true);
				database.updateGuildById(event.getGuild().getIdLong(), null, update, updateOptions, (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						event.reply("That " + category.toString().toLowerCase() + " is now blacklisted from appearing in only those events <:done:403285928233402378>").queue();
					}
				});
			}
			
			@Command(value="add", description="Adds a blacklist for a specific user, channel or role on events")
			@Examples({"logs blacklist add #general MESSAGE_UPDATE", "logs blacklist add @Shea#6653 MESSAGE_UPDATE MESSAGE_DELETE", "logs blacklist add @Owners MEMBER_ROLE_ADD"})
			@AuthorPermissions({Permission.MANAGE_SERVER})
			public void add(CommandEvent event, @Context Database database, @Argument(value="user | channel | role") String argument, @Argument(value="events", nullDefault=true) String[] eventsArgument) {
				Member member = ArgumentUtils.getMember(event.getGuild(), argument);
				GuildChannel channel = ArgumentUtils.getGuildChannel(event.getGuild(), argument);
				Role role = ArgumentUtils.getRole(event.getGuild(), argument);
				
				if (role == null && channel == null && member == null) {
					event.reply("I could not find that user/role/channel :no_entry:").queue();
					return;
				}
				
				Category category = role != null ? Category.ROLE : member != null ? Category.MEMBER : Category.CHANNEL;
				String type = role != null ? "roles" : member != null ? "users" : "channels";
				long id = role != null ? role.getIdLong() : member != null ? member.getIdLong() : channel.getIdLong();
				
				Document blacklisted = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("logger.blacklisted")).getEmbedded(List.of("logger", "blacklisted"), Database.EMPTY_DOCUMENT);
				
				long currentEventsRaw = 0L;
				List<Document> blacklists = blacklisted.getList(type, Document.class, Collections.emptyList());
				for (Document blacklist : blacklists) {
					if (blacklist.getLong("id") == id) {
						currentEventsRaw = blacklist.getLong("events");
					}
				}
				
				long eventsRaw = 0L;
				for (String stringEvent : eventsArgument) {
					try {
						Event newEvent = Event.valueOf(stringEvent.toUpperCase());
						if (!newEvent.containsCategory(category)) {
							event.reply("You cannot blacklist a " + category.toString().toLowerCase() + " from the `" + newEvent.toString() + "` event :no_entry:").queue();
							return;
						}
						
						if ((currentEventsRaw & newEvent.getRaw()) == newEvent.getRaw()) {
							event.reply("That " + category.toString().toLowerCase() + " is already blacklisted from the event `" + newEvent.toString() + "` :no_entry:").queue();
							return;
						}
						
						eventsRaw |= newEvent.getRaw();
					} catch(IllegalArgumentException e) {
						event.reply("`" + stringEvent.toUpperCase() + "` is not a valid event :no_entry:").queue();
						return;
					}
				}
				
				Bson update;
				List<Bson> arrayFilters = null;
				if (currentEventsRaw == 0) {
					Document blacklist = new Document("id", id).append("events", eventsRaw);
					update = Updates.push("logger.blacklisted." + type, blacklist);
				} else {
					update = Updates.bitwiseOr("logger.blacklisted." + type + ".$[blacklist].events", eventsRaw);
					arrayFilters = List.of(Filters.eq("blacklist.id", id));
				}
				
				UpdateOptions updateOptions = new UpdateOptions().arrayFilters(arrayFilters).upsert(true);
				database.updateGuildById(event.getGuild().getIdLong(), null, update, updateOptions, (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						event.reply("That " + category.toString().toLowerCase() + " is now blacklisted from appearing in those events <:done:403285928233402378>").queue();
					}
				});
			}
			
			@Command(value="remove", description="Removes a blacklist for a specific user, channel or role on events")
			@Examples({"logs blacklist remove #general MESSAGE_UPDATE", "logs blacklist remove @Shea#6653 MESSAGE_UPDATE MESSAGE_DELETE", "logs blacklist remove @Owners MEMBER_ROLE_ADD"})
			@AuthorPermissions({Permission.MANAGE_SERVER})
			public void remove(CommandEvent event, @Context Database database, @Argument(value="user | channel | role") String argument, @Argument(value="events", nullDefault=true) String[] eventsArgument) {
				Member member = ArgumentUtils.getMember(event.getGuild(), argument);
				GuildChannel channel = ArgumentUtils.getGuildChannel(event.getGuild(), argument);
				Role role = ArgumentUtils.getRole(event.getGuild(), argument);
				
				if (role == null && channel == null && member == null) {
					event.reply("I could not find that user/role/channel :no_entry:").queue();
					return;
				}
				
				Category category = role != null ? Category.ROLE : member != null ? Category.MEMBER : Category.CHANNEL;
				String type = role != null ? "roles" : member != null ? "users" : "channels";
				long id = role != null ? role.getIdLong() : member != null ? member.getIdLong() : channel.getIdLong();
				
				Document blacklisted = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("logger.blacklisted")).getEmbedded(List.of("logger", "blacklisted"), Database.EMPTY_DOCUMENT);
				
				long currentEventsRaw = 0L;
				List<Document> blacklists = blacklisted.getList(type, Document.class, Collections.emptyList());
				for (Document blacklist : blacklists) {
					if (blacklist.getLong("id") == id) {
						currentEventsRaw = blacklist.getLong("events");
					}
				}
				
				for (String stringEvent : eventsArgument) {
					try {
						Event newEvent = Event.valueOf(stringEvent.toUpperCase());
						if ((currentEventsRaw & newEvent.getRaw()) == newEvent.getRaw()) {
							currentEventsRaw -= newEvent.getRaw();
						} else {
							event.reply("That " + category.toString().toLowerCase() + " is not blacklisted from the event `" + newEvent.toString() + "` :no_entry:").queue();
							return;
						}
					} catch(IllegalArgumentException e) {
						event.reply("`" + stringEvent.toUpperCase() + "` is not a valid event :no_entry:").queue();
						return;
					}
				}
				
				Bson update;
				List<Bson> arrayFilters = null;
				if (currentEventsRaw == 0) {
					update = Updates.pull("logger.blacklisted." + type, Filters.eq("id", id));
				} else {
					update = Updates.set("logger.blacklisted." + type + ".$[blacklist].events", currentEventsRaw);
					arrayFilters = List.of(Filters.eq("blacklist.id", id));
				}
				
				UpdateOptions updateOptions = new UpdateOptions().arrayFilters(arrayFilters).upsert(true);
				database.updateGuildById(event.getGuild().getIdLong(), null, update, updateOptions, (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						event.reply("That " + category.toString().toLowerCase() + " is no longer blacklisted from appearing in those events <:done:403285928233402378>").queue();
					}
				});
			}
			
			@Command(value="list", description="Lists what users, channels and roles which are blacklisted from a specific event")
			@Examples({"logs blacklist list"})
			@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
			public void list(CommandEvent event, @Context Database database, @Argument(value="event") String eventArgument) {
				Event loggerEvent;
				try {
					loggerEvent = Event.valueOf(eventArgument.toUpperCase());
				} catch (IllegalArgumentException e) {
					event.reply("I could not find that event :no_entry:").queue();
					return;
				}
				
				Document blacklisted = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("logger.blacklisted")).getEmbedded(List.of("logger", "blacklisted"), Database.EMPTY_DOCUMENT);
				
				List<String> display = new ArrayList<>();
				
				List<Document> roles = blacklisted.getList("roles", Document.class, Collections.emptyList());
				for (Document role : roles) {
					if ((role.getLong("events") & loggerEvent.getRaw()) == loggerEvent.getRaw()) {
						Role guildRole = event.getGuild().getRoleById(role.getLong("id"));
						if (guildRole != null) {
							display.add(guildRole.getName() + " (Role)");
						}
					}
				}
				
				List<Document> users = blacklisted.getList("users", Document.class, Collections.emptyList());
				for (Document user : users) {
					if ((user.getLong("events") & loggerEvent.getRaw()) == loggerEvent.getRaw()) {
						Member member = event.getGuild().getMemberById(user.getLong("id"));
						if (member != null) {
							display.add(member.getUser().getAsTag() + " (Member)");
						}
					}
				}
				
				List<Document> channels = blacklisted.getList("channels", Document.class, Collections.emptyList());
				for (Document channel : channels) {
					if ((channel.getLong("events") & loggerEvent.getRaw()) == loggerEvent.getRaw()) {
						GuildChannel guildChannel = event.getGuild().getGuildChannelById(channel.getLong("id"));
						if (guildChannel != null) {
							display.add(guildChannel.getName() + " (" + GeneralUtils.title(Utils.getChannelTypeReadable(guildChannel)) + ")");
						}
					}
				}
				
				if (display.isEmpty()) {
					event.reply("Nothing is blacklisted from that event :no_entry:").queue();
					return;
				}
				
				PagedResult<String> paged = new PagedResult<>(display)
						.setDeleteMessage(false)
						.setIndexed(false);
				
				PagedUtils.getPagedResult(event, paged, 300, null);
			}
			
		}
		
		public class EventsCommand extends Sx4Command {
			
			public EventsCommand() {
				super("events");
				
				super.setAliases("event");
				super.setDescription("Enable/disable specific events in the logger");
				super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
				super.setExamples("logs events set", "logs events remove", "logs events list");
			}
			
			public void onCommand(CommandEvent event) {
				event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
			}
			
			@Command(value="set", description="Set what events you want the logger to use, events are listed in `logs events list`")
			@Examples({"logs events set MESSAGE_UPDATE", "logs events set MEMBER_ROLE_ADD MEMBER_ROLE_REMOVE"})
			@AuthorPermissions({Permission.MANAGE_SERVER})
			public void set(CommandEvent event, @Context Database database, @Argument(value="events") String[] eventsArgument) {
				long currentEventsRaw = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("logger.events")).getEmbedded(List.of("logger", "events"), Event.ALL_EVENTS);
				
				long eventsRaw = 0L;
				if (eventsArgument.length == 1) {
					switch (eventsArgument[0].toLowerCase()) {
						case "all":
							eventsRaw = Event.ALL_EVENTS;
							break;
						case "channel":
							eventsRaw = Event.ALL_CHANNEL_EVENTS;
							break;
						case "role": 
							eventsRaw = Event.ALL_ROLE_EVENTS;
							break;
						case "message":
							eventsRaw = Event.ALL_MESSAGE_EVENTS;
							break;
						case "member":
							eventsRaw = Event.ALL_MEMBER_EVENTS;
							break;
					}
				} else {
					for (String stringEvent : eventsArgument) {
						try {
							Event newEvent = Event.valueOf(stringEvent.toUpperCase());
							eventsRaw |= newEvent.getRaw();
						} catch(IllegalArgumentException e) {
							event.reply("`" + stringEvent.toUpperCase() + "` is not a valid event :no_entry:").queue();
							return;
						}
					}
				}
				
				if (currentEventsRaw == eventsRaw) {
					event.reply("The logger already has that event configuration :no_entry:").queue();
					return;
				}
				
				database.updateGuildById(event.getGuild().getIdLong(), Updates.set("logger.events", eventsRaw), (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						event.reply("The logger will now only send logs for those events <:done:403285928233402378>").queue();
					}
				});
			}
			
			@Command(value="add", description="Add events to the current events set for the logger to use, events are listed in `logs events list`")
			@Examples({"logs events add MESSAGE_UPDATE", "logs events add MEMBER_ROLE_ADD MEMBER_ROLE_REMOVE"})
			@AuthorPermissions({Permission.MANAGE_SERVER})
			public void add(CommandEvent event, @Context Database database, @Argument(value="events") String[] eventsArgument) {
				long currentEventsRaw = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("logger.events")).getEmbedded(List.of("logger", "events"), Event.ALL_EVENTS);
				
				long eventsRaw = 0L;
				for (String stringEvent : eventsArgument) {
					try {
						Event newEvent = Event.valueOf(stringEvent.toUpperCase());
						if ((currentEventsRaw & newEvent.getRaw()) == newEvent.getRaw()) {
							event.reply("`" + newEvent.toString() + "` is already enabled :no_entry:").queue();
							return;
						} else {
							eventsRaw |= newEvent.getRaw();
						}
					} catch(IllegalArgumentException e) {
						event.reply("`" + stringEvent.toUpperCase() + "` is not a valid event :no_entry:").queue();
						return;
					}
				}
				
				database.updateGuildById(event.getGuild().getIdLong(), Updates.bitwiseOr("logger.events", eventsRaw), (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						event.reply("The logger will now send logs for those events <:done:403285928233402378>").queue();
					}
				});
			}
			
			@Command(value="remove", description="Remove events to the current events set for the logger to use, events are listed in `logs events list`")
			@Examples({"logs events remove MESSAGE_UPDATE", "logs events remove MEMBER_ROLE_ADD MEMBER_ROLE_REMOVE"})
			@AuthorPermissions({Permission.MANAGE_SERVER})
			public void remove(CommandEvent event, @Context Database database, @Argument(value="events") String[] eventsArgument) {
				long eventsRaw = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("logger.events")).getEmbedded(List.of("logger", "events"), Event.ALL_EVENTS);
				
				for (String stringEvent : eventsArgument) {
					try {
						Event newEvent = Event.valueOf(stringEvent.toUpperCase());
						if ((eventsRaw & newEvent.getRaw()) == newEvent.getRaw()) {
							eventsRaw -= newEvent.getRaw();
						} else {
							event.reply("`" + newEvent.toString() + "` is not enabled :no_entry:").queue();
							return;
						}
					} catch(IllegalArgumentException e) {
						event.reply("`" + stringEvent.toUpperCase() + "` is not a valid event :no_entry:").queue();
						return;
					}
				}
				
				database.updateGuildById(event.getGuild().getIdLong(), Updates.set("logger.events", eventsRaw), (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						event.reply("The logger will no longer send logs for those events <:done:403285928233402378>").queue();
					}
				});
			}
			
			@Command(value="list", description="Lists all the events the logger has")
			@Examples({"logs events list"})
			@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
			public void list(CommandEvent event) {
				StringBuilder eventList = new StringBuilder();
				for (Event loggerEvent : Event.values()) {
					eventList.append(loggerEvent.toString() + "\n");
				}
				
				EmbedBuilder embed = new EmbedBuilder();
				embed.setAuthor("Event List", null, event.getGuild().getIconUrl());
				embed.setDescription(eventList.toString());	
				event.reply(embed.build()).queue();
			}
			
		}
		
	}
	
	@Initialize(all=true, subCommands=true, recursive=true)
	public void initialize(CommandImpl command) {
		command.setCategory(Categories.LOGS);
	}
	
}
