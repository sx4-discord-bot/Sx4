package com.sx4.bot.modules;

import java.awt.Color;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
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
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.sx4.bot.categories.Categories;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEventListener;
import com.sx4.bot.database.Database;
import com.sx4.bot.interfaces.Examples;
import com.sx4.bot.settings.Settings;
import com.sx4.bot.utils.ArgumentUtils;
import com.sx4.bot.utils.GeneralUtils;
import com.sx4.bot.utils.HelpUtils;
import com.sx4.bot.utils.WelcomerUtils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;

@Module
public class WelcomerModule {
	
	public class WelcomerCommand extends Sx4Command {
		
		public static final String DEFAULT_MESSAGE = "{user.mention}, Welcome to **{server}**. Enjoy your time here! The server now has {server.members} members.";
		
		public WelcomerCommand() {
			super("welcomer");
			
			super.setAliases("welcome");
			super.setDescription("Set up a welcomer to send welcome messages everytime someone joins the server");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
			super.setExamples("welcomer toggle", "welcomer channel", "welcomer stats");
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="toggle", aliases={"enable", "disable"}, description="Enable/disable the welcomer for the current server", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"welcomer toggle"})
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		public void toggle(CommandEvent event, @Context Database database) {
			boolean enabled = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("welcomer.enabled")).getEmbedded(List.of("welcomer", "enabled"), false);
			database.updateGuildById(event.getGuild().getIdLong(), Updates.set("welcomer.enabled", !enabled), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("Welcomer is now " + (enabled ? "disabled" : "enabled") + " <:done:403285928233402378>").queue();
				}
			});
		}
		
		@Command(value="channel", description="Set the channel where you would want the welcomer messages to be sent")
		@Examples({"welcomer channel", "welcomer channel #welcome", "welcomer channel welcome", "welcomer channel 478916836940054538"})
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
			
			Long channelId = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("welcomer.channelId")).getEmbedded(List.of("welcomer", "channelId"), Long.class);
			if (channelId != null && channelId == channel.getIdLong()) {
				event.reply("The welcomer channel is already set to " + channel.getAsMention() + " :no_entry:").queue();
				return;
			}
			
			database.updateGuildById(event.getGuild().getIdLong(), Updates.set("welcomer.channelId", channel.getIdLong()), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("The welcomer channel has been set to " + channel.getAsMention() + " <:done:403285928233402378>").queue();
				}
			});
		}
		
		@Command(value="embed", aliases={"toggle embed", "toggleembed", "embedtoggle", "embed toggle"}, description="Enable/disable whether the welcomer messages should be embedded", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"welcomer embed"})
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		public void embed(CommandEvent event, @Context Database database) {
			boolean embedded = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("welcomer.embed.enabled")).getEmbedded(List.of("welcomer", "embed", "enabled"), false);
			database.updateGuildById(event.getGuild().getIdLong(), Updates.set("welcomer.embed.enabled", !embedded), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("Welcomer messages are " + (embedded ? "no longer" : "now") + " embedded <:done:403285928233402378>").queue();
				}
			});
		}
		
		@Command(value="embed colour", aliases={"embedcolour", "embed color", "embedcolor"}, description="Set the embed colour for your welcomer, this only works when the welcomer message is set to an embed")
		@Examples({"welcomer embed colour reset", "welcomer embed colour #ffff00", "welcomer embed colour ffff00", "welcomer embed colour 255, 255, 0"})
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		public void embedColour(CommandEvent event, @Context Database database, @Argument(value="hex | rgb", endless=true) String colourArgument) {
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
			
			Integer currentColour = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("welcomer.embed.colour")).getEmbedded(List.of("welcomer", "embed", "colour"), Integer.class);
			if (currentColour != null && colour != null && currentColour == colour.hashCode()) {
				event.reply("The welcomer embed colour is already set to **#" + GeneralUtils.getHex(currentColour) + "** :no_entry:").queue();
				return;
			}
			
			if (currentColour == null && colour == null) {
				event.reply("The welcomer embed colour is already not set :no_entry:").queue();
				return;
			}
			
			Bson update = colour == null ? Updates.unset("welcomer.embed.colour") : Updates.set("welcomer.embed.colour", colour.hashCode());
			database.updateGuildById(event.getGuild().getIdLong(), update, (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("The welcomer embed colour has been " + (colour == null ? "reset" : "set to **#" + GeneralUtils.getHex(colour.hashCode()) + "**") + " <:done:403285928233402378>").queue();
				}
			});
		}
		
		@Command(value="dm toggle", aliases={"dm", "dmtoggle", "toggle dm", "toggledm"}, description="Enable/disable whether the welcomer message should be sent straight to the users dms when they join", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"welcomer dm toggle"})
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		public void dmToggle(CommandEvent event, @Context Database database) {
			boolean dm = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("welcomer.dm")).getEmbedded(List.of("welcomer", "dm"), false);
			database.updateGuildById(event.getGuild().getIdLong(), Updates.set("welcomer.dm", !dm), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("Welcomer messages will " + (dm ? "no longer" : "now")  + " be sent in dms <:done:403285928233402378>").queue();
				}
			});
		}
		
		@Command(value="message", aliases={"join message", "joinmessage", "join msg", "joinmsg", "msg"}, description="Set the message which will be sent for your welcomer, you can use different variables which can be found in `welcomer format`")
		@Examples({"welcomer message A new person has joined", "welcomer message Welcome {user.mention}!"})
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		public void message(CommandEvent event, @Context Database database, @Argument(value="message", endless=true) String message) {
			if (message.length() > MessageEmbed.VALUE_MAX_LENGTH) {
				event.reply("The leaver message cannot be any longer than " + MessageEmbed.VALUE_MAX_LENGTH + " characters :no_entry:").queue();
				return;
			}
			
			String currentMessage = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("welcomer.message")).getEmbedded(List.of("welcomer", "message"), String.class);
			if (currentMessage != null && currentMessage.equals(message)) {
				event.reply("The welcomer message is already set to that :no_entry:").queue();
				return;
			}
			
			database.updateGuildById(event.getGuild().getIdLong(), Updates.set("welcomer.message", message), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("The welcomer message has been updated <:done:403285928233402378>").queue();
				}
			});
		}
		
		@Command(value="format", aliases={"formatting", "formats", "variable", "variables"}, description="View the variables you can use when settings your welcomer message", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"welcomer format"})
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
		@Examples({"welcomer preview"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS, Permission.MESSAGE_ATTACH_FILES})
		public void preview(CommandEvent event, @Context Database database) {
			Bson projection = Projections.include("welcomer.embed", "welcomer.message", "welcomer.enabled", "welcomer.channelId", "imageWelcomer.enabled", "imageWelcomer.banner", "imageWelcomer.gif");
			Document data = database.getGuildById(event.getGuild().getIdLong(), null, projection);
			
			StringBuilder warningMessage = new StringBuilder();
			
			if (!event.getSelfMember().hasPermission(Permission.MANAGE_WEBHOOKS)) {
				warningMessage.append(":warning: I do not have the `Manage Webhooks` permission");
			}
			

			Document welcomerData = data.get("welcomer", Database.EMPTY_DOCUMENT), imageWelcomerData = data.get("imageWelcomer", Database.EMPTY_DOCUMENT);
			
			Long channelId = welcomerData.getLong("channelId");
			TextChannel channel = channelId == null ? null : event.getGuild().getTextChannelById(channelId);
			
			if (channel == null) {
				warningMessage.append(":warning: The welcomer channel is not set, use `" + event.getPrefix() + "welcomer channel <channel>` to enable it\n");
			}
			
			if (!welcomerData.getBoolean("enabled", false) && !imageWelcomerData.getBoolean("enabled", false)) {
				warningMessage.append(":warning: Welcomer is currently disabled, use `" + event.getPrefix() + "welcomer toggle` to enable it\n");
			}
			
			WelcomerUtils.getWelcomerPreview(event.getMember(), event.getGuild(), data, imageWelcomerData.getBoolean("gif", false), (message, response) -> {
				if (!message.isEmpty() && response == null) {
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
						if (message.isEmpty()) {
							event.replyFile(bytes, "welcomer." + response.headers().get("Content-Type").split("/")[1]).queue();
						} else {
							event.reply(message.build()).addFile(bytes, "welcomer." + response.headers().get("Content-Type").split("/")[1]).queue();
						}
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
		@Examples({"welcomer stats"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void stats(CommandEvent event, @Context Database database) {
			Document data = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("imageWelcomer.enabled", "welcomer.channelId", "welcomer.message", "welcomer.enabled", "welcomer.dm", "welcomer.embed"));
			Document welcomerData = data.get("welcomer", Database.EMPTY_DOCUMENT), imageWelcomerData = data.get("imageWelcomer", Database.EMPTY_DOCUMENT);
			Document embedData = welcomerData.get("embed", Database.EMPTY_DOCUMENT);
			
			Long channelId = welcomerData.getLong("channelId");
			TextChannel channel = channelId == null ? null : event.getGuild().getTextChannelById(channelId);
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setAuthor("Welcomer Settings", null, event.getSelfUser().getEffectiveAvatarUrl());
			embed.setColor(Settings.EMBED_COLOUR);
			embed.addField("Status", welcomerData.getBoolean("enabled", false) ? "Enabled" : "Disabled", true);
			embed.addField("Channel", channel == null ? "Not Set" : channel.getAsMention(), true);
			embed.addField("DM Welcomer", welcomerData.getBoolean("dm", false) ? "Enabled" : "Disabled", true);
			embed.addField("Image Welcomer", imageWelcomerData.getBoolean("enabled", false) ? "Enabled" : "Disabled", true);
			embed.addField("Embed", String.format("Message: %s\nColour: #%s", embedData.getBoolean("enabled", false) ? "Enabled" : "Disabled", GeneralUtils.getHex(embedData.getInteger("colour", Role.DEFAULT_COLOR_RAW))), true);
			embed.addField("Message", String.format("`%s`", welcomerData.get("message", WelcomerCommand.DEFAULT_MESSAGE)), false);
			
			event.reply(embed.build()).queue();
		}
		
	}
	
	public class LeaverCommand extends Sx4Command {
		
		public static final String DEFAULT_MESSAGE = "**{user.name}** has just left **{server}**. Bye **{user.name}**!";
		
		public LeaverCommand() {
			super("leaver");
			
			super.setAliases("leave");
			super.setDescription("Set up a leaver to send leave messages everytime someone leaves the server");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
			super.setExamples("leaver toggle", "leaver channel", "leaver stats");
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="toggle", aliases={"enable", "disable"}, description="Enable/disable the leaver for the current server", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"leaver toggle"})
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		public void toggle(CommandEvent event, @Context Database database) {
			boolean enabled = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("leaver.enabled")).getEmbedded(List.of("leaver", "enabled"), false);
			database.updateGuildById(event.getGuild().getIdLong(), Updates.set("leaver.enabled", !enabled), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("Leaver is now " + (enabled ? "disabled" : "enabled") + " <:done:403285928233402378>").queue();
				}
			});
		}
		
		@Command(value="channel", description="Set the channel where you would want the leaver messages to be sent")
		@Examples({"leaver channel", "leaver channel #goodbye", "leaver channel goodbye", "leaver channel 439745234285625355"})
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
			
			Long channelId = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("leaver.channelId")).getEmbedded(List.of("leaver", "channelId"), Long.class);
			if (channelId != null && channelId == channel.getIdLong()) {
				event.reply("The leaver channel is already set to " + channel.getAsMention() + " :no_entry:").queue();
				return;
			}
			
			database.updateGuildById(event.getGuild().getIdLong(), Updates.set("leaver.channelId", channel.getIdLong()), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("The leaver channel has been set to " + channel.getAsMention() + " <:done:403285928233402378>").queue();
				}
			});
		}
		
		@Command(value="embed", aliases={"toggle embed", "toggleembed", "embedtoggle", "embed toggle"}, description="Enable/disable whether the leaver messages should be embedded", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"leaver embed"})
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		public void embed(CommandEvent event, @Context Database database) {
			boolean embedded = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("leaver.embed.enabled")).getEmbedded(List.of("leaver", "embed", "enabled"), false);
			database.updateGuildById(event.getGuild().getIdLong(), Updates.set("leaver.embed.enabled", !embedded), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("Leaver messages are " + (embedded ? "no longer" : "now") + " embedded <:done:403285928233402378>").queue();
				}
			});
		}
		
		@Command(value="embed colour", aliases={"embedcolour", "embed color", "embedcolor"}, description="Set the embed colour for your leaver, this only works when the leaver message is set to an embed")
		@Examples({"leaver embed colour reset", "leaver embed colour #ffff00", "leaver embed colour ffff00", "leaver embed colour 255, 255, 0"})
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		public void embedColour(CommandEvent event, @Context Database database, @Argument(value="hex | rgb", endless=true) String colourArgument) {
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
			
			Integer currentColour = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("leaver.embed.colour")).getEmbedded(List.of("leaver", "embed", "colour"), Integer.class);
			if (currentColour != null && colour != null && currentColour == colour.hashCode()) {
				event.reply("The leaver embed colour is already set to **#" + GeneralUtils.getHex(currentColour) + "** :no_entry:").queue();
				return;
			}
			
			if (currentColour == null && colour == null) {
				event.reply("The leaver embed colour is already not set :no_entry:").queue();
				return;
			}
			
			Bson update = colour == null ? Updates.unset("leaver.embed.colour") : Updates.set("leaver.embed.colour", colour.hashCode());
			database.updateGuildById(event.getGuild().getIdLong(), update, (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("The leaver embed colour has been " + (colour == null ? "reset" : "set to **#" + GeneralUtils.getHex(colour.hashCode()) + "**") + " <:done:403285928233402378>").queue();
				}
			});
		}
		
		@Command(value="message", aliases={"leave message", "leavemessage", "leave msg", "leavemsg", "msg"}, description="Set the message which will be sent for the leaver, you can use different variables which can be found in `leaver format`")
		@Examples({"leaver message Someone has left, goodbye", "leaver message Goodbye **{user.name}** 👋"})
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		public void message(CommandEvent event, @Context Database database, @Argument(value="message", endless=true) String message) {
			if (message.length() > MessageEmbed.VALUE_MAX_LENGTH) {
				event.reply("The leaver message cannot be any longer than " + MessageEmbed.VALUE_MAX_LENGTH + " characters :no_entry:").queue();
				return;
			}
			
			String currentMessage = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("leaver.message")).getEmbedded(List.of("leaver", "message"), String.class);
			if (currentMessage != null && currentMessage.equals(message)) {
				event.reply("The leaver message is already set to that :no_entry:").queue();
				return;
			}
			
			database.updateGuildById(event.getGuild().getIdLong(), Updates.set("leaver.message", message), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("The leaver message has been updated <:done:403285928233402378>").queue();
				}
			});
		}
		
		@Command(value="format", aliases={"formatting", "formats", "variable", "variables"}, description="View the variables you can use when settings your leaver message", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"leaver format"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void format(CommandEvent event) {
			EmbedBuilder embed = new EmbedBuilder();
			embed.setAuthor("Leaver Formatting", null, event.getSelfUser().getEffectiveAvatarUrl());
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
		@Examples({"leaver preview"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS, Permission.MESSAGE_ATTACH_FILES})
		public void preview(CommandEvent event, @Context Database database) {
			Document data = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("leaver.channelId", "leaver.enabled", "leaver.message", "leaver.embed")).get("leaver", Database.EMPTY_DOCUMENT);
			
			StringBuilder warningMessage = new StringBuilder();
			
			if (!event.getSelfMember().hasPermission(Permission.MANAGE_WEBHOOKS)) {
				warningMessage.append(":warning: I do not have the `Manage Webhooks` permission");
			}
			
			Long channelId = data.getLong("channelId");
			TextChannel channel = channelId == null ? null : event.getGuild().getTextChannelById(channelId);
			if (channel == null) {
				warningMessage.append(":warning: The leaver channel is not set, use `" + event.getPrefix() + "leaver channel <channel>` to enable it\n");
			}
			
			if (!data.getBoolean("enabled", false)) {
				warningMessage.append(":warning: Leaver is currently disabled, use `" + event.getPrefix() + "leaver toggle` to enable it\n");
			}
			
			event.reply(WelcomerUtils.getLeaverPreview(event.getMember(), event.getGuild(), data).build()).queue();
			
			if (warningMessage.length() != 0) {
				event.reply(warningMessage.toString()).queue();
			}
		}
		
		@Command(value="stats", aliases={"settings", "setting"}, description="View the leaver settings for the current server", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"leaver stats"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void stats(CommandEvent event, @Context Database database) {
			Document data = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("leaver.channelId", "leaver.enabled", "leaver.message", "leaver.embed")).get("leaver", Database.EMPTY_DOCUMENT);
			Document embedData = data.get("embed", Database.EMPTY_DOCUMENT);
			
			Long channelId = data.getLong("channelId");
			TextChannel channel = channelId == null ? null : event.getGuild().getTextChannelById(channelId);
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setAuthor("Leaver Settings", null, event.getSelfUser().getEffectiveAvatarUrl());
			embed.setColor(Settings.EMBED_COLOUR);
			embed.addField("Status", data.getBoolean("enabled", false) ? "Enabled" : "Disabled", true);
			embed.addField("Channel", channel == null ? "Not Set" : channel.getAsMention(), true);
			embed.addField("Embed", String.format("Message: %s\nColour: #%s", embedData.getBoolean("enabled", false) ? "Enabled" : "Disabled", GeneralUtils.getHex(embedData.getInteger("colour", Role.DEFAULT_COLOR_RAW))), true);
			embed.addField("Message", String.format("`%s`", data.get("message", LeaverCommand.DEFAULT_MESSAGE)), false);
			
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
			super.setExamples("image welcomer toggle", "image welcomer banner");
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="toggle", aliases={"enable", "disable"}, description="Enable/disable the image welcomer for the current server", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"image welcomer toggle"})
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		public void toggle(CommandEvent event, @Context Database database) {
			boolean enabled = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("imageWelcomer.enabled")).getEmbedded(List.of("imageWelcomer", "enabled"), false);
			database.updateGuildById(event.getGuild().getIdLong(), Updates.set("imageWelcomer.enabled", !enabled), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("Image Welcomer is now " + (enabled ? "disabled" : "enabled") + " <:done:403285928233402378>").queue();
				}
			});
		}
		
		@Command(value="banner", aliases={"background"}, description="Set or reset the banner for your image welcomer", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"image welcomer banner", "image welcomer banner https://i.imgur.com/i87lyNO.png"})
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
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
					try {
						url = new URL(banner);
					} catch (MalformedURLException e) {
						event.reply("The banner you provided is not a URL :no_entry:").queue();
						return;
					}
					
					String urlString = url.toString();
					int periodIndex = urlString.lastIndexOf(".");
					
					if (!this.supportedTypes.contains(urlString.substring(periodIndex + 1).toUpperCase())) {
						if (event.getMessage().getEmbeds().isEmpty()) {
							event.reply("That image type is not supported, the supported types are " + GeneralUtils.joinGrammatical(this.supportedTypes) + " :no_entry:").queue();
							return;
						} else {
							MessageEmbed imageEmbed = event.getMessage().getEmbeds().get(0);
							if (imageEmbed.getThumbnail() != null) {
								String embedUrl = imageEmbed.getThumbnail().getUrl();
								int periodIndexEmbed = embedUrl.lastIndexOf(".");
								
								if (!this.supportedTypes.contains(embedUrl.substring(periodIndexEmbed + 1).toUpperCase())) {
									event.reply("That image type is not supported, the supported types are " + GeneralUtils.joinGrammatical(this.supportedTypes) + " :no_entry:").queue();
									return;
								} else {
									try {
										url = new URL(embedUrl);
									} catch (MalformedURLException e) {}
								}
							} else {
								event.reply("That image type is not supported, the supported types are " + GeneralUtils.joinGrammatical(this.supportedTypes) + " :no_entry:").queue();
								return;
							}
						}
					}
				}
			}
			
			String currentBanner = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("imageWelcomer.banner")).getEmbedded(List.of("imageWelcomer", "banner"), String.class);
			if (url == null && currentBanner == null) {
				event.reply("You don't have a banner set for your image welcomer :no_entry:").queue();
				return;
			}
				
			String urlString = url == null ? null : url.getHost().contains("giphy") ? "https://i.giphy.com/" + url.getPath().split("/")[2] + ".gif" : url.toString();
			if (urlString != null && urlString.equals(currentBanner)) {
				event.reply("Your image welcomer banner is already set to that :no_entry:").queue();
				return;
			}
			
			Bson update = urlString == null ? Updates.unset("imageWelcomer.banner") : Updates.set("imageWelcomer.banner", urlString);
			database.updateGuildById(event.getGuild().getIdLong(), update, (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("Your image welcomer banner has been " + (urlString == null ? "reset" : "updated") + " <:done:403285928233402378>").queue();
				}
			});
		}
		
	}

	@Initialize(all=true, subCommands=true, recursive=true)
	public void initialize(CommandImpl command) {
		command.setCategory(Categories.WELCOMER);
	}
	
}
