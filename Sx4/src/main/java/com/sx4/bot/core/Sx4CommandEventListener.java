package com.sx4.bot.core;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bson.Document;

import com.jockie.bot.core.command.ICommand;
import com.jockie.bot.core.command.impl.CommandEvent;
import com.jockie.bot.core.command.impl.CommandEventListener;
import com.sx4.bot.database.Database;
import com.sx4.bot.settings.Settings;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.send.WebhookEmbed;
import club.minnced.discord.webhook.send.WebhookEmbed.EmbedField;
import club.minnced.discord.webhook.send.WebhookEmbedBuilder;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.internal.utils.tuple.Pair;

public class Sx4CommandEventListener extends CommandEventListener {
	
	private WebhookClient commandWebhook = new WebhookClientBuilder(Long.valueOf(Settings.COMMANDS_WEBHOOK_ID), Settings.COMMANDS_WEBHOOK_TOKEN).build();
	private List<WebhookEmbed> commandStore = new ArrayList<>();
	
	private long lastCommandExecuted = -1;
	
	private static Pair<Long, Long> averageExecutionTime = Pair.of(0L, 0L);
	
	public static long getAverageExecutionTime() {
		return averageExecutionTime.getRight() == 0 ? 0 : averageExecutionTime.getLeft() / averageExecutionTime.getRight();
	}
	
	public static MessageEmbed getUserErrorMessage(Throwable throwable) {
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
		
		return embed.build();
	}
	
	public static List<String> getErrorMessage(Throwable throwable, Object[] arguments) {
		List<String> messages = new ArrayList<>();
		
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
	    try (PrintStream stream = new PrintStream(outputStream)) {
	        throwable.printStackTrace(stream);
	    }
	    
	    String[] errorLines = new String(outputStream.toByteArray(), StandardCharsets.UTF_8).split("\n");
		
		StringBuilder message = new StringBuilder("```diff\n");
		
		if (arguments.length != 0) {
			message.append("+ Arguments: " + Arrays.deepToString(arguments));
		}
		
		for (String errorLine : errorLines) {
			String toAppend = "\n-      " + errorLine;
			
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
		
		return messages;
	}
	
	public static void sendErrorMessage(TextChannel channel, Throwable throwable, Object[] arguments) {
		List<String> messages = Sx4CommandEventListener.getErrorMessage(throwable, arguments);
		
		for (String messageContent : messages) {
			channel.sendMessage(messageContent).queue();
		}
	}

	public void onCommandExecuted(ICommand command, CommandEvent event) {
		if (event.isAuthorDeveloper()) {
			event.removeCooldown();
		}
		
		if (this.lastCommandExecuted == event.getMessage().getIdLong()) {
			return;
		} else {
			this.lastCommandExecuted = event.getMessage().getIdLong();
		}
		
		averageExecutionTime = Pair.of(averageExecutionTime.getLeft() + event.getTimeSinceStarted(), averageExecutionTime.getRight() + 1);
		
		WebhookEmbedBuilder embed = new WebhookEmbedBuilder();
		embed.setTimestamp(Instant.now());
		embed.addField(new EmbedField(false, "Message", String.format("Content: %s\nID: %s", event.getMessage().getContentRaw(), event.getMessage().getId())));
		embed.addField(new EmbedField(false, "Channel", String.format("Name: %s\nID: %s", event.getChannel().getName(), event.getChannel().getId())));
		embed.addField(new EmbedField(false, "Guild", String.format("Name: %s\nID: %s\nShard: %d\nMember Count: %,d", event.getGuild().getName(), event.getGuild().getId(), event.getJDA().getShardInfo().getShardId(), event.getGuild().getMembers().size())));
		embed.addField(new EmbedField(false, "Author", String.format("Tag: %s\nID: %s", event.getAuthor().getAsTag(), event.getAuthor().getId())));
		embed.addField(new EmbedField(false, "Command", String.format("Prefix: %s\nCommand: %s\nUnparsed Argument: %s", event.getPrefix(), command.getCommandTrigger(), event.getMessage().getContentRaw().substring(event.getPrefix().length() + event.getCommandTrigger().length()))));

		List<String> attachments = new ArrayList<>();
		for (Attachment attachment : event.getMessage().getAttachments()) {
			attachments.add(attachment.getUrl());
		}
		
		embed.addField(new EmbedField(false, "Attachments", attachments.isEmpty() ? "None" : String.join("\n", attachments)));
		
		if (this.commandStore.size() == 10) {
			this.commandWebhook.send(this.commandStore);
			this.commandStore.clear();
		} else {
			this.commandStore.add(embed.build());
		}
		
		Document commandData = new Document("_id", event.getMessage().getIdLong())
				.append("content", event.getMessage().getContentRaw())
				.append("command", command.getCommandTrigger())
				.append("module", command.getCategory() == null ? null : command.getCategory().getName())
				.append("aliasUsed", event.getCommandTrigger())
				.append("authorId", event.getAuthor().getIdLong())
				.append("channelId", event.getChannel().getIdLong())
				.append("guildId", event.getGuild().getIdLong())
				.append("arguments", Arrays.asList(event.getRawArguments()))
				.append("prefix", event.getPrefix())
				.append("attachments", attachments)
				.append("shard", event.getJDA().getShardInfo().getShardId())
				.append("executionDuration", event.getTimeSinceStarted())
				.append("timestamp", Clock.systemUTC().instant().getEpochSecond());
		
		Database.get().insertCommandData(commandData, (result, exception) -> {
			if (exception != null) {
				exception.printStackTrace();
			}
		});
	}
	
	public void onCommandExecutionException(ICommand command, CommandEvent event, Throwable throwable) {
		Sx4CommandEventListener.sendErrorMessage(event.getShardManager().getGuildById(Settings.SUPPORT_SERVER_ID).getTextChannelById(Settings.ERRORS_CHANNEL_ID), throwable, event.getArguments());
		
		event.reply(Sx4CommandEventListener.getUserErrorMessage(throwable)).queue();
	}
}
