package com.sx4.core;

import static com.rethinkdb.RethinkDB.r;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jockie.bot.core.command.ICommand;
import com.jockie.bot.core.command.impl.CommandEvent;
import com.jockie.bot.core.command.impl.CommandEventListener;
import com.rethinkdb.gen.ast.Get;
import com.sx4.pair.CustomPair;
import com.sx4.settings.Settings;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.Message.Attachment;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.webhook.WebhookClient;
import net.dv8tion.jda.webhook.WebhookClientBuilder;

public class Sx4CommandEventListener extends CommandEventListener {
	
	private WebhookClient commandWebhook = new WebhookClientBuilder(Long.valueOf(Settings.COMMANDS_WEBHOOK_ID), Settings.COMMANDS_WEBHOOK_TOKEN).build();
	private List<MessageEmbed> commandStore = new ArrayList<>();
	private static CustomPair<Long, Long> averageExecutionTime = new CustomPair<>(0L, 0L);
	
	public static long getAverageExecutionTime() {
		return averageExecutionTime.getValue() == 0 ? 0 : averageExecutionTime.getKey() / averageExecutionTime.getValue();
	}
	
	public static void sendErrorMessage(TextChannel channel, Throwable throwable, Object[] arguments) {
		List<String> messages = new ArrayList<>();
		
		StringBuilder message = new StringBuilder();
		message.append("```diff\n- ").append(throwable.toString());
		
		if (arguments.length != 0) {
			message.append(" with arguments " + Arrays.deepToString(arguments));
		}
		
		for (StackTraceElement element : throwable.getStackTrace()) {
			String toAppend = "\n-      at " + element.toString();
			
			if (message.length() + toAppend.length() > 1997) {
				messages.add(message.append("```").toString());
				
				message = new StringBuilder("```diff");
			} else {
				message.append(toAppend);
			}
		}
		
		if (message.length() > 7) {
			messages.add(message.append("```").toString());
		}
		
		for (String messageContent : messages) {
			channel.sendMessage(messageContent).queue();
		}
	}

	@SuppressWarnings("unchecked")
	public void onCommandExecuted(ICommand command, CommandEvent event) {
		if (event.isDeveloper()) {
			event.removeCooldown();
		}
		
		averageExecutionTime.setPair(averageExecutionTime.getKey() + event.getTimeSinceStarted(), averageExecutionTime.getValue() + 1);
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setTimestamp(Instant.now());
		embed.addField("Message", String.format("Content: %s\nID: %s", event.getMessage().getContentRaw(), event.getMessage().getId()), false);
		embed.addField("Channel", String.format("Name: %s\nID: %s", event.getChannel().getName(), event.getChannel().getId()), false);
		embed.addField("Guild", String.format("Name: %s\nID: %s\nShard: %d\nMember Count: %,d", event.getGuild().getName(), event.getGuild().getId(), event.getJDA().getShardInfo().getShardId(), event.getGuild().getMembers().size()), false);
		embed.addField("Author", String.format("Tag: %s\nID: %s", event.getAuthor().getAsTag(), event.getAuthor().getId()), false);
		embed.addField("Command", String.format("Prefix: %s\nCommand: %s\nUnparsed Argument: %s", event.getPrefix(), command.getCommandTrigger(), event.getMessage().getContentRaw().substring(event.getPrefix().length() + event.getCommandTrigger().length())), false);

		List<String> attachments = new ArrayList<>();
		for (Attachment attachment : event.getMessage().getAttachments()) {
			attachments.add(attachment.getUrl());
		}
		
		embed.addField("Attachments", attachments.isEmpty() ? "None" : String.join("\n", attachments), false);
		
		if (this.commandStore.size() == 10) {
			this.commandWebhook.send(this.commandStore);
			this.commandStore.clear();
		} else {
			this.commandStore.add(embed.build());
		}
		
		Get data = r.table("botstats").get("stats");
		Map<String, Object> dataRan = data.run(Sx4Bot.getConnection());
		
		List<String> users = (List<String>) dataRan.get("users");
		if (!users.contains(event.getAuthor().getId())) {
			users.add(event.getAuthor().getId());
		}
		
		List<Map<String, Object>> commandCounter = (List<Map<String, Object>>) dataRan.get("commandcounter");
		boolean updated = false;
		for (Map<String, Object> commandData : commandCounter) {
			if (event.getCommand().getCommandTrigger().equals(commandData.get("name"))) {
				commandCounter.remove(commandData);
				commandData.put("amount", ((long) commandData.get("amount")) + 1);
				commandCounter.add(commandData);
				updated = true;
				break;
			}
		}
		
		if (updated == false) {
			Map<String, Object> commandData = new HashMap<>();
			commandData.put("amount", 1);
			commandData.put("name", event.getCommand().getCommandTrigger());
			commandCounter.add(commandData);
		}
		
		data.update(row -> r.hashMap("commands", row.g("commands").add(1)).with("users", users).with("commandcounter", commandCounter)).runNoReply(Sx4Bot.getConnection());
	}
	
	public void onCommandExecutionException(ICommand command, CommandEvent event, Throwable throwable) {
		Sx4CommandEventListener.sendErrorMessage(event.getShardManager().getGuildById(Settings.SUPPORT_SERVER_ID).getTextChannelById(Settings.ERRORS_CHANNEL_ID), throwable, event.getArguments());
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setTitle("Error");
		embed.setDescription("You have come across an error! [Support Server](https://discord.gg/PqJNcfB)\n```diff\n- " + throwable.toString());
		
		for (int i = 0; i < throwable.getStackTrace().length; i++) {
			StackTraceElement element = throwable.getStackTrace()[i];
			if (element.toString().contains("sx4")) {
				embed.appendDescription("\n- " + element.toString() + "```");
				break;
			}
		}
		
		event.reply(embed.build()).queue();
	}
}
