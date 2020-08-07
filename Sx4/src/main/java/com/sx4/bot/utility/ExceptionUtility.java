package com.sx4.bot.utility;

import com.jockie.bot.core.command.impl.CommandEvent;
import com.sx4.bot.config.Config;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.requests.RestAction;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ExceptionUtility {

	public static boolean sendErrorMessage(Throwable throwable) {
		if (throwable == null) {
			return false;
		}
		
		List<String> messages = new ArrayList<>();
		
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
	    try (PrintStream stream = new PrintStream(outputStream)) {
	        throwable.printStackTrace(stream);
	    }
	    
	    String[] errorLines = new String(outputStream.toByteArray(), StandardCharsets.UTF_8).split("\n");
		
		StringBuilder message = new StringBuilder("```diff\n");
		
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
		
		TextChannel channel = Config.get().getErrorsChannel();
		if (channel != null) {
			messages.stream().map(channel::sendMessage).forEach(RestAction::queue);
		}
		
		return true;
	}
	
	public static MessageEmbed getSimpleErrorMessage(Throwable throwable) {
		EmbedBuilder embed = new EmbedBuilder();
		embed.setTitle("Error");
		embed.setColor(Config.get().getRed());
		embed.setDescription("You have come across an error! [Support Server](https://discord.gg/PqJNcfB)\n```diff\n- " + throwable.toString());
		
		StackTraceElement[] stackTrace = throwable.getStackTrace();
		for (int i = 0; i < stackTrace.length; i++) {
			StackTraceElement element = stackTrace[i];
			if (element.toString().contains("com.sx4.bot")) {
				embed.appendDescription("\n- " + element.toString() + "```");
				break;
			}
			
			if (i == stackTrace.length - 1) {
				embed.appendDescription("```");
			}
		}
		
		return embed.build();
	}
	
	public static boolean sendExceptionally(CommandEvent event, Throwable throwable) {
		boolean exception = throwable != null;
		if (exception) {
			event.reply(ExceptionUtility.getSimpleErrorMessage(throwable)).queue();

			ExceptionUtility.sendErrorMessage(throwable);

			throwable.printStackTrace();
		}
		
		return exception;
	}
	
}
