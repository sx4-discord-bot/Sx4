package com.sx4.modules;

import static com.rethinkdb.RethinkDB.r;

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
import com.sx4.utils.ArgumentUtils;
import com.sx4.utils.HelpUtils;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.Webhook;

@Module
public class LogsModule {

	public class LogsCommand extends Sx4Command {
		
		public LogsCommand() {
			super("logs");
			
			super.setAliases("log");
			super.setDescription("Set up logs in your server to log a variety of actions which happen within the server");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="toggle", aliases={"enable", "disable"}, description="Enable/disable the logs in the current server", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void toggle(CommandEvent event, @Context Connection connection) {
			r.table("logs").insert(r.hashMap("id", event.getGuild().getId()).with("toggle", false).with("channel", null).with("webhook_id", null).with("webhook_token", null)).run(connection, OptArgs.of("durability", "soft"));
			Get data = r.table("logs").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if ((boolean) dataRan.get("toggle") == false) {
				event.reply("Logs are now enabled <:done:403285928233402378>").queue();
				data.update(r.hashMap("toggle", true)).runNoReply(connection);
			} else if ((boolean) dataRan.get("toggle") == true) {
				event.reply("Logs are now disabled <:done:403285928233402378>").queue();
				data.update(r.hashMap("toggle", false)).runNoReply(connection);
			}
		}
		
		@Command(value="channel", description="Set the channel for all the logs to be sent to")
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void channel(CommandEvent event, @Context Connection connection, @Argument(value="channel", endless=true, nullDefault=true) String channelArgument) {
			r.table("logs").insert(r.hashMap("id", event.getGuild().getId()).with("toggle", false).with("channel", null).with("webhook_id", null).with("webhook_token", null)).run(connection, OptArgs.of("durability", "soft"));
			Get data = r.table("logs").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
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
			
			String dataChannel = (String) dataRan.get("channel");
			if (channel == null) {
				if ((String) dataRan.get("channel") == null) {
					event.reply("The logs channel is already not set :no_entry:").queue();
					return;
				}
				
				event.reply("The logs channel has been reset <:done:403285928233402378>").queue();
			} else {
				if (dataChannel != null) {
					if (dataChannel.equals(channel.getId())) {
						event.reply("The logs channel is already set to " + channel.getAsMention() + " :no_entry:").queue();
						return;
					}
				}
				
				event.reply("The logs channel has been set to " + channel.getAsMention() + " <:done:403285928233402378>").queue();
			}
			
			TextChannel oldChannel = dataChannel == null ? null : event.getGuild().getTextChannelById(dataChannel);
			if (oldChannel != null) {
				if (dataRan.containsKey("webhook_id") && dataRan.containsKey("webhook_token")) {
					if (dataRan.get("webhook_id") != null && dataRan.get("webhook_token") != null) {
						oldChannel.getWebhooks().queue(webhooks -> {
							for (Webhook webhook : webhooks) {
								if (webhook.getId().equals((String) dataRan.get("webhook_id")) && webhook.getToken().equals((String) dataRan.get("webhook_token"))) {
									webhook.delete().queue();
								}
							}
							
							data.update(r.hashMap("channel", channel == null ? channel : channel.getId()).with("webhook_id", null).with("webhook_token", null)).runNoReply(connection);
						});		
					
						return;
					}
				} 
			}
			
			data.update(r.hashMap("channel", channel == null ? channel : channel.getId()).with("webhook_id", null).with("webhook_token", null)).runNoReply(connection);
		}
		
		@Command(value="stats", aliases={"settings", "setting"}, description="View the current setup of logs in this server", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void stats(CommandEvent event, @Context Connection connection) {
			Map<String, Object> data = r.table("logs").get(event.getGuild().getId()).run(connection);
			
			TextChannel channel;
			if (data == null) {
				channel = null;
			} else {
				String dataChannel = (String) data.get("channel");
				if (dataChannel == null) {
					channel = null;
				} else {
					channel = event.getGuild().getTextChannelById(dataChannel);
				}
			}
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setAuthor("Logs Settings", null, event.getGuild().getIconUrl());
			embed.addField("Status", data == null ? "Disabled" : (boolean) data.get("toggle") == true ? "Enabled" : "Disabled", true);
			embed.addField("Channel", channel == null ? "Not Set" : channel.getAsMention(), true);
			event.reply(embed.build()).queue();
		}
		
	}
	
	@Initialize(all=true)
	public void initialize(CommandImpl command) {
		command.setCategory(Categories.LOGS);
	}
	
}
