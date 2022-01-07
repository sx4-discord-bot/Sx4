package com.sx4.bot.utility;

import com.jockie.bot.core.command.impl.CommandEvent;
import com.sx4.bot.config.Config;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;

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
	    
	    String[] errorLines = outputStream.toString(StandardCharsets.UTF_8).split("\n");
		
		StringBuilder message = new StringBuilder("```ansi\n");
		
		for (String errorLine : errorLines) {
			String toAppend;
			int bracketIndex = errorLine.lastIndexOf('(');
			if (bracketIndex != -1) {
				toAppend = "\u001B[0;31m" + errorLine.substring(0, bracketIndex + 1) + (errorLine.contains("com.sx4.bot") ? "\u001B[0;34m" : "\u001B[0;30m") + errorLine.substring(bracketIndex + 1, errorLine.length() - 2) + "\u001B[0;31m)\u001B[0;0m\n";
			} else {
				toAppend = "\u001B[0;31m" + errorLine + "\u001B[0;0m";
			}
			
			if (message.length() + toAppend.length() > 1997) {
				messages.add(message.append("```").toString());
				
				message = new StringBuilder("```ansi\n");
			} else {
				message.append(toAppend);
			}
		}
		
		if (message.length() > 7) {
			messages.add(message.append("```").toString());
		}

		messages.forEach(Config.get().getErrorsWebhook()::send);

		throwable.printStackTrace();
		
		return true;
	}
	
	public static MessageEmbed getSimpleErrorMessage(Throwable throwable) {
		StringBuilder builder = new StringBuilder("- " + throwable.toString());

		StackTraceElement[] stackTrace = throwable.getStackTrace();
		for (StackTraceElement element : stackTrace) {
			if (element.toString().contains("com.sx4.bot")) {
				builder.append("\n- ").append(element);
				break;
			}
		}
		
		return ExceptionUtility.getSimpleErrorMessage(builder.toString(), "diff");
	}

	public static MessageEmbed getSimpleErrorMessage(String message, String markdown) {
		message = StringUtility.limit(message, Message.MAX_CONTENT_LENGTH, "...");

		return new EmbedBuilder()
			.setTitle("Error")
			.setColor(Config.get().getRed())
			.setDescription(String.format("You have come across an error! [Support Server](%s)\n```%s\n%s```", Config.get().getSupportGuildInvite(), markdown, StringUtility.limit(message, 1750)))
			.build();
	}
	
	public static boolean sendExceptionally(MessageChannel channel, Throwable throwable) {
		if (throwable == null) {
			return false;
		}

		channel.sendMessageEmbeds(ExceptionUtility.getSimpleErrorMessage(throwable)).queue();

		ExceptionUtility.sendErrorMessage(throwable);
		
		return true;
	}

	public static boolean sendExceptionally(CommandEvent event, Throwable throwable) {
		return ExceptionUtility.sendExceptionally(event.getChannel(), throwable);
	}

	public static void safeRun(Runnable runnable) {
		try {
			runnable.run();
		} catch (Throwable e) {
			ExceptionUtility.sendErrorMessage(e);
		}
	}
	
}
