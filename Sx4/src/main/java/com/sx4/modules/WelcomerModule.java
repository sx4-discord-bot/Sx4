package com.sx4.modules;

import static com.rethinkdb.RethinkDB.r;

import java.awt.Color;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import com.jockie.bot.core.Context;
import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.command.Command.AuthorPermissions;
import com.jockie.bot.core.command.Command.BotPermissions;
import com.jockie.bot.core.command.ICommand.ContentOverflowPolicy;
import com.jockie.bot.core.command.Initialize;
import com.jockie.bot.core.command.impl.CommandEvent;
import com.jockie.bot.core.command.impl.CommandImpl;
import com.jockie.bot.core.module.Module;
import com.rethinkdb.gen.ast.Get;
import com.rethinkdb.model.OptArgs;
import com.rethinkdb.net.Connection;
import com.sx4.categories.Categories;
import com.sx4.core.Sx4Command;
import com.sx4.settings.Settings;
import com.sx4.utils.ArgumentUtils;
import com.sx4.utils.GeneralUtils;
import com.sx4.utils.HelpUtils;
import com.sx4.utils.WelcomerUtils;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Message.Attachment;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.entities.TextChannel;

@Module
public class WelcomerModule {
	
	public class WelcomerCommand extends Sx4Command {
		
		private static final String MESSAGE = "{user.mention}, Welcome to **{server}**. Enjoy your time here! The server now has {server.members} members.";
		
		public WelcomerCommand() {
			super("welcomer");
			
			super.setAliases("welcome");
			super.setDescription("Set up a welcomer to send welcome messages everytime someone joins the server");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="toggle", aliases={"enable", "disable"}, description="Enable/disable the welcomer for the current server", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		public void toggle(CommandEvent event, @Context Connection connection) {
			WelcomerUtils.insertData(event.getGuild()).run(connection, OptArgs.of("durability", "soft"));
			Get data = r.table("welcomer").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if ((boolean) dataRan.get("toggle") == true) {
				event.reply("Welcomer is now disabled <:done:403285928233402378>").queue();
				data.update(r.hashMap("toggle", false)).runNoReply(connection);
			} else {
				event.reply("Welcomer is now enabled <:done:403285928233402378>").queue();
				data.update(r.hashMap("toggle", true)).runNoReply(connection);
			}
		}
		
		@Command(value="channel", description="Set the channel where you would want the welcomer messages to be sent")
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		public void channel(CommandEvent event, @Context Connection connection, @Argument(value="channel", endless=true, nullDefault=true) String channelArgument) {
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
			
			WelcomerUtils.insertData(event.getGuild()).run(connection, OptArgs.of("durability", "soft"));
			Get data = r.table("welcomer").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if (dataRan.get("channel") != null && dataRan.get("channel").equals(channel.getId())) {
				event.reply("The welcomer channel is already set to " + channel.getAsMention() + " :no_entry:").queue();
				return;
			}
			
			event.reply("The welcomer channel has been set to " + channel.getAsMention() + " <:done:403285928233402378>").queue();
			data.update(r.hashMap("channel", channel.getId())).runNoReply(connection);
		}
		
		@Command(value="embed", aliases={"toggle embed", "toggleembed", "embedtoggle", "embed toggle"}, description="Enable/disable whether the welcomer messages should be embedded", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		public void embed(CommandEvent event, @Context Connection connection) {
			WelcomerUtils.insertData(event.getGuild()).run(connection, OptArgs.of("durability", "soft"));
			Get data = r.table("welcomer").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if ((boolean) dataRan.get("embed") == true) {
				event.reply("Welcomer messages are no longer embedded <:done:403285928233402378>").queue();
				data.update(r.hashMap("embed", false)).runNoReply(connection);
			} else {
				event.reply("Welcomer messages are now embedded <:done:403285928233402378>").queue();
				data.update(r.hashMap("embed", true)).runNoReply(connection);
			}
		}
		
		@Command(value="embed colour", aliases={"embedcolour", "embed color", "embedcolor"}, description="Set the embed colour for your welcomer, this only works when the welcomer message is set to an embed")
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		public void embedColour(CommandEvent event, @Context Connection connection, @Argument(value="hex | rgb", endless=true) String colourArgument) {
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
			
			WelcomerUtils.insertData(event.getGuild()).run(connection, OptArgs.of("durability", "soft"));
			Get data = r.table("welcomer").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if (colour != null && dataRan.get("embedcolour") != null && (long) dataRan.get("embedcolour") == colour.hashCode()) {
				event.reply("The welcomer embed colour is already set to **#" + Integer.toHexString(colour.hashCode()).substring(2).toUpperCase() + "** :no_entry:").queue();
				return;
			}
			
			if (colour == null) {
				event.reply("The welcomer embed colour has been reset <:done:403285928233402378>").queue();
			} else {
				event.reply("The welcomer embed colour has been set to **#" + Integer.toHexString(colour.hashCode()).substring(2).toUpperCase() + "** <:done:403285928233402378>").queue();
			}
			
			data.update(r.hashMap("embedcolour", colour == null ? null : colour.hashCode())).runNoReply(connection);
		}
		
		@Command(value="dm toggle", aliases={"dm", "dmtoggle", "toggle dm", "toggledm"}, description="Enable/disable whether the welcomer message should be sent straight to the users dms when they join", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		public void dmToggle(CommandEvent event, @Context Connection connection) {
			WelcomerUtils.insertData(event.getGuild()).run(connection, OptArgs.of("durability", "soft"));
			Get data = r.table("welcomer").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if ((boolean) dataRan.get("dm") == true) {
				event.reply("Welcomer messages will no longer be sent in dms <:done:403285928233402378>").queue();
				data.update(r.hashMap("dm", false)).runNoReply(connection);
			} else {
				event.reply("Welcomer messages will now be sent in dms <:done:403285928233402378>").queue();
				data.update(r.hashMap("dm", true)).runNoReply(connection);
			}
		}
		
		@Command(value="message", aliases={"join message", "joinmessage", "join msg", "joinmsg", "msg"}, description="Set the message which will be sent for your welcomer, you can use different variables which can be found in `welcomer format`")
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		public void message(CommandEvent event, @Context Connection connection, @Argument(value="message", endless=true) String message) {
			if (message.length() > MessageEmbed.VALUE_MAX_LENGTH) {
				event.reply("The leaver message cannot be any longer than " + MessageEmbed.VALUE_MAX_LENGTH + " characters :no_entry:").queue();
				return;
			}
			
			WelcomerUtils.insertData(event.getGuild()).run(connection, OptArgs.of("durability", "soft"));
			Get data = r.table("welcomer").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if (dataRan.get("message").equals(message)) {
				event.reply("The welcomer message is already set to that :no_entry:").queue();
				return;
			}
			
			event.reply("The welcomer message has been updated <:done:403285928233402378>").queue();
			data.update(r.hashMap("message", message)).runNoReply(connection);
		}
		
		@Command(value="format", aliases={"formatting", "formats", "variable", "variables"}, description="View the variables you can use when settings your welcomer message", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void format(CommandEvent event) {
			EmbedBuilder embed = new EmbedBuilder();
			embed.setAuthor("Welcomer Formatting", null, event.getSelfUser().getEffectiveAvatarUrl());
			embed.setColor(Settings.EMBED_COLOUR);
			embed.setDescription("{server} = The current servers name\r\n" + 
					"{user.mention} = The mention of the user which joins\r\n" + 
					"{user.name} = The name of the user which joins\r\n" + 
					"{user} = The name and discriminator (tag) of the user which joins\r\n" + 
					"{server.members} = The current servers member count\r\n" + 
					"{server.members.prefix} = The current servers member count but suffixed (522nd)\r\n" + 
					"{user.created.length} = The amount of time since the creation date of the users account (1 year 2 months 5 days)\r\n" + 
					"**Make sure you keep the {} brackets in the message**\r\n" + 
					"\r\n" + 
					"Example: `s?welcomer message {user.mention}, Welcome to **{server}**. We now have **{server.members}** members :tada:`");
			
			event.reply(embed.build()).queue();
		}
		
		@Command(value="preview", description="View a preview of what your welcomer message will look like, the bot will also warn you about any possible issues", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS, Permission.MESSAGE_ATTACH_FILES})
		public void preview(CommandEvent event, @Context Connection connection) {
			Map<String, Object> data = r.table("welcomer").get(event.getGuild().getId()).run(connection);
			
			StringBuilder warningMessage = new StringBuilder();
			
			if (!event.getSelfMember().hasPermission(Permission.MANAGE_WEBHOOKS)) {
				warningMessage.append(":warning: I do not have the `Manage Webhooks` permission");
			}
			
			if (data == null) {
				warningMessage.append(":warning: The welcomer channel is not set, use `" + event.getPrefix() + "welcomer channel <channel>` to enable it\n");
				warningMessage.append(":warning: Welcomer is currently disabled, use `" + event.getPrefix() + "welcomer toggle` to enable it\n");
			} else {
				TextChannel channel;
				if (data.get("channel") == null) {
					channel = null;
				} else {
					channel = event.getGuild().getTextChannelById((String) data.get("channel"));
				}
				
				if (channel == null) {
					warningMessage.append(":warning: The welcomer channel is not set, use `" + event.getPrefix() + "welcomer channel <channel>` to enable it\n");
				}
				
				if ((boolean) data.get("toggle") == false && (boolean) data.get("imgwelcomertog") == false) {
					warningMessage.append(":warning: Welcomer is currently disabled, use `" + event.getPrefix() + "welcomer toggle` to enable it\n");
				}
			}
			
			WelcomerUtils.getWelcomerMessage(event.getMember(), event.getGuild(), data, (message, response) -> {
				if (message.isEmpty() && response != null) {
					byte[] bytes;
					try {
						bytes = response.body().bytes();
					} catch (IOException e) {
						event.reply("Oops something went wrong there, try again :no_entry:").queue();
						return;
					}
					
					try {
						event.replyFile(bytes, "welcomer." + response.headers().get("Content-Type").split("/")[1]).queue();
					} catch (IllegalArgumentException e) {
						event.reply(String.format("The banner you set is too large (File size: %.2fMiB) :no_entry:", (double) bytes.length / 1049000)).queue();
					}
				} else if (!message.isEmpty() && response == null) {
					event.reply(message.build()).queue();
				} else {
					byte[] bytes;
					try {
						bytes = response.body().bytes();
					} catch (IOException e) {
						event.reply("Oops something went wrong there, try again :no_entry:").queue();
						return;
					}
					
					try {
						event.reply(message.build()).addFile(bytes, "welcomer." + response.headers().get("Content-Type").split("/")[1]).queue();	
					} catch (IllegalArgumentException e) {
						event.reply(String.format("The banner you set is too large (File size: %.2fMiB) :no_entry:", (double) bytes.length / 1049000)).queue();
					}
				}
			});
			
			if (warningMessage.length() != 0) {
				event.reply(warningMessage.toString()).queue();
			}
		}
		
		@Command(value="stats", aliases={"settings", "setting"}, description="View the welcomer settings for the current server", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void stats(CommandEvent event, @Context Connection connection) {
			Map<String, Object> data = r.table("welcomer").get(event.getGuild().getId()).run(connection);
			
			TextChannel channel;
			if (data == null || data.get("channel") == null) {
				channel = null;
			} else {
				channel = event.getGuild().getTextChannelById((String) data.get("channel"));
			}
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setAuthor("Welcomer Settings", null, event.getSelfUser().getEffectiveAvatarUrl());
			embed.setColor(Settings.EMBED_COLOUR);
			embed.addField("Status", data == null ? "Disabled" : (boolean) data.get("toggle") == false ? "Disabled" : "Enabled", true);
			embed.addField("Channel", channel == null ? "Not Set" : channel.getAsMention(), true);
			embed.addField("DM Welcomer", data == null ? "Disabled" : (boolean) data.get("dm") == false ? "Disabled" : "Enabled", true);
			embed.addField("Image Welcomer", data == null ? "Disabled" : (boolean) data.get("imgwelcomertog") == false ? "Disabled" : "Enabled", true);
			embed.addField("Embed", String.format("Message: %s\nColour: %s", data == null ? "Disabled" : (boolean) data.get("embed") == false ? "Disabled" : "Enabled", data == null ? "Default" : data.get("embedcolour") == null ? "Default" : "#" + Integer.toHexString((int) ((long) data.get("embedcolour"))).substring(2).toUpperCase()), true);
			embed.addField("Message", String.format("`%s`", data == null ? MESSAGE : (String) data.get("message")), false);
			
			event.reply(embed.build()).queue();
		}
		
	}
	
	public class LeaverCommand extends Sx4Command {
		
		private static final String MESSAGE = "**{user.name}** has just left **{server}**. Bye **{user.name}**!";
		
		public LeaverCommand() {
			super("leaver");
			
			super.setAliases("leave");
			super.setDescription("Set up a leaver to send leave messages everytime someone leaves the server");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="toggle", aliases={"enable", "disable"}, description="Enable/disable the leaver for the current server", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		public void toggle(CommandEvent event, @Context Connection connection) {
			WelcomerUtils.insertData(event.getGuild()).run(connection, OptArgs.of("durability", "soft"));
			Get data = r.table("welcomer").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if ((boolean) dataRan.get("leavetoggle") == true) {
				event.reply("Leaver is now disabled <:done:403285928233402378>").queue();
				data.update(r.hashMap("leavetoggle", false)).runNoReply(connection);
			} else {
				event.reply("Leaver is now enabled <:done:403285928233402378>").queue();
				data.update(r.hashMap("leavetoggle", true)).runNoReply(connection);
			}
		}
		
		@Command(value="channel", description="Set the channel where you would want the leaver messages to be sent")
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		public void channel(CommandEvent event, @Context Connection connection, @Argument(value="channel", endless=true, nullDefault=true) String channelArgument) {
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
			
			WelcomerUtils.insertData(event.getGuild()).run(connection, OptArgs.of("durability", "soft"));
			Get data = r.table("welcomer").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if (dataRan.get("leavechannel") != null && dataRan.get("leavechannel").equals(channel.getId())) {
				event.reply("The leaver channel is already set to " + channel.getAsMention() + " :no_entry:").queue();
				return;
			}
			
			event.reply("The leaver channel has been set to " + channel.getAsMention() + " <:done:403285928233402378>").queue();
			data.update(r.hashMap("leavechannel", channel.getId())).runNoReply(connection);
		}
		
		@Command(value="embed", aliases={"toggle embed", "toggleembed", "embedtoggle", "embed toggle"}, description="Enable/disable whether the leaver messages should be embedded", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		public void embed(CommandEvent event, @Context Connection connection) {
			WelcomerUtils.insertData(event.getGuild()).run(connection, OptArgs.of("durability", "soft"));
			Get data = r.table("welcomer").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if ((boolean) dataRan.get("leaveembed") == true) {
				event.reply("Leaver messages are no longer embedded <:done:403285928233402378>").queue();
				data.update(r.hashMap("leaveembed", false)).runNoReply(connection);
			} else {
				event.reply("Leaver messages are now embedded <:done:403285928233402378>").queue();
				data.update(r.hashMap("leaveembed", true)).runNoReply(connection);
			}
		}
		
		@Command(value="embed colour", aliases={"embedcolour", "embed color", "embedcolor"}, description="Set the embed colour for your leaver, this only works when the leaver message is set to an embed")
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		public void embedColour(CommandEvent event, @Context Connection connection, @Argument(value="hex | rgb", endless=true) String colourArgument) {
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
			
			WelcomerUtils.insertData(event.getGuild()).run(connection, OptArgs.of("durability", "soft"));
			Get data = r.table("welcomer").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if (colour != null && dataRan.get("leaveembedcolour") != null && (long) dataRan.get("leaveembedcolour") == colour.hashCode()) {
				event.reply("The leaver embed colour is already set to **#" + Integer.toHexString(colour.hashCode()).substring(2).toUpperCase() + "** :no_entry:").queue();
				return;
			}
			
			if (colour == null) {
				event.reply("The leaver embed colour has been reset <:done:403285928233402378>").queue();
			} else {
				event.reply("The leaver embed colour has been set to **#" + Integer.toHexString(colour.hashCode()).substring(2).toUpperCase() + "** <:done:403285928233402378>").queue();
			}
			
			data.update(r.hashMap("leaveembedcolour", colour == null ? null : colour.hashCode())).runNoReply(connection);
		}
		
		@Command(value="message", aliases={"leave message", "leavemessage", "leave msg", "leavemsg", "msg"}, description="Set the message which will be sent for the leaver, you can use different variables which can be found in `leaver format`")
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		public void message(CommandEvent event, @Context Connection connection, @Argument(value="message", endless=true) String message) {
			if (message.length() > MessageEmbed.VALUE_MAX_LENGTH) {
				event.reply("The leaver message cannot be any longer than " + MessageEmbed.VALUE_MAX_LENGTH + " characters :no_entry:").queue();
				return;
			}
			
			WelcomerUtils.insertData(event.getGuild()).run(connection, OptArgs.of("durability", "soft"));
			Get data = r.table("welcomer").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if (dataRan.get("leave-message").equals(message)) {
				event.reply("The leaver message is already set to that :no_entry:").queue();
				return;
			}
			
			event.reply("The leaver message has been updated <:done:403285928233402378>").queue();
			data.update(r.hashMap("leave-message", message)).runNoReply(connection);
		}
		
		@Command(value="format", aliases={"formatting", "formats", "variable", "variables"}, description="View the variables you can use when settings your welcomer message", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void format(CommandEvent event) {
			EmbedBuilder embed = new EmbedBuilder();
			embed.setAuthor("Welcomer Formatting", null, event.getSelfUser().getEffectiveAvatarUrl());
			embed.setColor(Settings.EMBED_COLOUR);
			embed.setDescription("{server} = The current servers name\r\n" + 
					"{user.mention} = The mention of the user which joins\r\n" + 
					"{user.name} = The name of the user which joins\r\n" + 
					"{user} = The name and discriminator (tag) of the user which joins\r\n" + 
					"{server.members} = The current servers member count\r\n" + 
					"{server.members.prefix} = The current servers member count but suffixed (522nd)\r\n" + 
					"{user.stayed.length} = The amount of time the user stayed in the server before leaving (1 year 2 months 5 days)\r\n" + 
					"**Make sure you keep the {} brackets in the message**\r\n" + 
					"\r\n" + 
					"Example: `s?leaver message {user}, Goodbye! You stayed here for {user.stayed.length} before leaving.`");
			
			event.reply(embed.build()).queue();
		}
		
		@Command(value="preview", description="View a preview of what your leaver message will look like, the bot will also warn you about any possible issues", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS, Permission.MESSAGE_ATTACH_FILES})
		public void preview(CommandEvent event, @Context Connection connection) {
			Map<String, Object> data = r.table("welcomer").get(event.getGuild().getId()).run(connection);
			
			StringBuilder warningMessage = new StringBuilder();
			
			if (!event.getSelfMember().hasPermission(Permission.MANAGE_WEBHOOKS)) {
				warningMessage.append(":warning: I do not have the `Manage Webhooks` permission");
			}
			
			if (data == null) {
				warningMessage.append(":warning: The leaver channel is not set, use `" + event.getPrefix() + "leaver channel <channel>` to enable it\n");
				warningMessage.append(":warning: Leaver is currently disabled, use `" + event.getPrefix() + "leaver toggle` to enable it\n");
			} else {
				TextChannel channel;
				if (data.get("leavechannel") == null) {
					channel = null;
				} else {
					channel = event.getGuild().getTextChannelById((String) data.get("leavechannel"));
				}
				
				if (channel == null) {
					warningMessage.append(":warning: The leaver channel is not set, use `" + event.getPrefix() + "leaver channel <channel>` to enable it\n");
				}
				
				if ((boolean) data.get("leavetoggle") == false) {
					warningMessage.append(":warning: Leaver is currently disabled, use `" + event.getPrefix() + "leaver toggle` to enable it\n");
				}
			}
			
			event.reply(WelcomerUtils.getLeaver(event.getMember(), event.getGuild(), data).build()).queue();
			
			if (warningMessage.length() != 0) {
				event.reply(warningMessage.toString()).queue();
			}
		}
		
		@Command(value="stats", aliases={"settings", "setting"}, description="View the leaver settings for the current server", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void stats(CommandEvent event, @Context Connection connection) {
			Map<String, Object> data = r.table("welcomer").get(event.getGuild().getId()).run(connection);
			
			TextChannel channel;
			if (data == null || data.get("leavechannel") == null) {
				channel = null;
			} else {
				channel = event.getGuild().getTextChannelById((String) data.get("leavechannel"));
			}
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setAuthor("Leaver Settings", null, event.getSelfUser().getEffectiveAvatarUrl());
			embed.setColor(Settings.EMBED_COLOUR);
			embed.addField("Status", data == null ? "Disabled" : (boolean) data.get("leavetoggle") == false ? "Disabled" : "Enabled", true);
			embed.addField("Channel", channel == null ? "Not Set" : channel.getAsMention(), true);
			embed.addField("Embed", String.format("Message: %s\nColour: %s", data == null ? "Disabled" : (boolean) data.get("leaveembed") == false ? "Disabled" : "Enabled", data == null ? "Default" : data.get("leaveembedcolour") == null ? "Default" : "#" + Integer.toHexString((int) ((long) data.get("leaveembedcolour"))).substring(2).toUpperCase()), true);
			embed.addField("Message", String.format("`%s`", data == null ? MESSAGE : (String) data.get("leave-message")), false);
			
			event.reply(embed.build()).queue();
		}
		
	}
	
	public class ImageWelcomerCommand extends Sx4Command {
		
		List<String> supportedTypes = List.of("WEBP", "PNG", "GIF", "JPG", "JPEG");
		
		public ImageWelcomerCommand() {
			super("image welcomer");
			
			super.setAliases("imagewelcomer", "imgwelcomer", "img welcomer", "imgwelcome", "img welcome");
			super.setDescription("Set up an image welcomer to send an image with a greeting and the users name everytime someone joins the server");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="toggle", aliases={"enable", "disable"}, description="Enable/disable the image welcomer for the current server", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		public void toggle(CommandEvent event, @Context Connection connection) {
			WelcomerUtils.insertData(event.getGuild()).run(connection, OptArgs.of("durability", "soft"));
			Get data = r.table("welcomer").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if ((boolean) dataRan.get("imgwelcomertog") == true) {
				event.reply("Image welcomer is now disabled <:done:403285928233402378>").queue();
				data.update(r.hashMap("imgwelcomertog", false)).runNoReply(connection);
			} else {
				event.reply("Image welcomer is now enabled <:done:403285928233402378>").queue();
				data.update(r.hashMap("imgwelcomertog", true)).runNoReply(connection);
			}
		}
		
		@Command(value="banner", aliases={"background"}, description="Set or reset the banner for your image welcomer", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		public void banner(CommandEvent event, @Context Connection connection, @Argument(value="banner", nullDefault=true) String banner) {
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
					try {
						url = new URL(banner);
					} catch (MalformedURLException e) {
						event.reply("The banner you provided is not a URL :no_entry:").queue();
						return;
					}
					
					String urlString = url.toString();
					int periodIndex = urlString.lastIndexOf(".");
					
					if (!supportedTypes.contains(urlString.substring(periodIndex + 1).toUpperCase())) {
						if (event.getMessage().getEmbeds().isEmpty()) {
							event.reply("That image type is not supported, the supported types are " + GeneralUtils.joinGrammatical(supportedTypes) + " :no_entry:").queue();
							return;
						} else {
							MessageEmbed imageEmbed = event.getMessage().getEmbeds().get(0);
							if (imageEmbed.getThumbnail() != null) {
								String embedUrl = imageEmbed.getThumbnail().getUrl();
								int periodIndexEmbed = embedUrl.lastIndexOf(".");
								
								if (!supportedTypes.contains(embedUrl.substring(periodIndexEmbed + 1).toUpperCase())) {
									event.reply("That image type is not supported, the supported types are " + GeneralUtils.joinGrammatical(supportedTypes) + " :no_entry:").queue();
									return;
								} else {
									try {
										url = new URL(embedUrl);
									} catch (MalformedURLException e) {}
								}
							} else {
								event.reply("That image type is not supported, the supported types are " + GeneralUtils.joinGrammatical(supportedTypes) + " :no_entry:").queue();
								return;
							}
						}
					}
				}
			}
			
			WelcomerUtils.insertData(event.getGuild()).run(connection, OptArgs.of("durability", "soft"));
			Get data = r.table("welcomer").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if (url == null) {
				if (dataRan.get("banner") == null) {
					event.reply("You don't have a banner set for your image welcomer :no_entry:").queue();
					return;
				} else {
					event.reply("Your image welcomer banner has been reset <:done:403285928233402378>").queue();
					data.update(r.hashMap("banner", null)).runNoReply(connection);
				}
			} else {
				String urlString;
				
				//hacky solution to support giphy gifs
				if (url.getHost().contains("giphy")) {
					urlString = "https://i.giphy.com/" + url.getPath().split("/")[2] + ".gif";
				} else {
					urlString = url.toString();
				}
				
				if (urlString.equals(dataRan.get("banner"))) {
					event.reply("Your image welcomer banner is already set to that :no_entry:").queue();
					return;
				} else {
					event.reply("Your image welcomer banner has been updated <:done:403285928233402378>").queue();
					data.update(r.hashMap("banner", urlString)).runNoReply(connection);
				}
			}
		}
		
	}

	@Initialize(all=true)
	public void initialize(CommandImpl command) {
		command.setCategory(Categories.WELCOMER);
	}
	
}
