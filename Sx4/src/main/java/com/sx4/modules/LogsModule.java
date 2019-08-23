package com.sx4.modules;

import java.util.List;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.command.Command.AuthorPermissions;
import com.jockie.bot.core.command.Command.BotPermissions;
import com.jockie.bot.core.command.Context;
import com.jockie.bot.core.command.ICommand.ContentOverflowPolicy;
import com.jockie.bot.core.command.Initialize;
import com.jockie.bot.core.command.impl.CommandEvent;
import com.jockie.bot.core.command.impl.CommandImpl;
import com.jockie.bot.core.module.Module;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.sx4.categories.Categories;
import com.sx4.core.Sx4Command;
import com.sx4.core.Sx4CommandEventListener;
import com.sx4.database.Database;
import com.sx4.utils.ArgumentUtils;
import com.sx4.utils.HelpUtils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
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
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="toggle", aliases={"enable", "disable"}, description="Enable/disable the logs in the current server", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
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
				
					return;
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
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void stats(CommandEvent event, @Context Database database) {
			Document data = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("logger.enabled", "logger.channelId")).get("logger", Database.EMPTY_DOCUMENT);
			
			Long channelId = data.getLong("channelId");
			TextChannel channel = channelId == null ? null : event.getGuild().getTextChannelById(channelId);
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setAuthor("Logs Settings", null, event.getGuild().getIconUrl());
			embed.addField("Status", data.getBoolean("enabled", false) ? "Enabled" : "Disabled", true);
			embed.addField("Channel", channel == null ? "Not Set" : channel.getAsMention(), true);
			event.reply(embed.build()).queue();
		}
		
	}
	
	@Initialize(all=true)
	public void initialize(CommandImpl command) {
		command.setCategory(Categories.LOGS);
	}
	
}
