package com.sx4.bot.commands.developer;

import com.jockie.bot.core.JockieUtils;
import com.sun.management.OperatingSystemMXBean;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.utility.NumberUtility;
import com.sx4.bot.utility.TimeUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDAInfo;
import net.dv8tion.jda.api.Permission;
import org.bson.Document;

import java.lang.management.ManagementFactory;
import java.util.concurrent.TimeUnit;

public class StatsCommand extends Sx4Command {

	public StatsCommand() {
		super("stats", 463);

		super.setDescription("View statistics of Sx4");
		super.setAliases("statistics");
		super.setExamples("stats");
		super.setExecuteAsync(true);
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.DEVELOPER);
	}

	public void onCommand(Sx4CommandEvent event) {
		long commands = event.getMongo().getCommands().estimatedDocumentCount();

		Runtime runtime = Runtime.getRuntime();
		long totalMemory = ((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalMemorySize();
		long memoryUsed = runtime.totalMemory() - runtime.freeMemory();

		Document mongoData = event.getMongo().getDatabase().runCommand(new Document("serverStatus", 1));
		double mongoUptime = mongoData.getDouble("uptime");
		Document latencies = mongoData.get("opLatencies", Document.class);
		Document reads = latencies.get("reads", Document.class), writes = latencies.get("writes", Document.class);

		EmbedBuilder embed = new EmbedBuilder();
		embed.setDescription("Bot ID: " + event.getSelfUser().getId());
		embed.setThumbnail(event.getSelfUser().getEffectiveAvatarUrl());
		embed.setAuthor(event.getSelfUser().getName() + " Stats", null, event.getSelfUser().getEffectiveAvatarUrl());
		embed.setFooter("Uptime: " + TimeUtility.getTimeString(ManagementFactory.getRuntimeMXBean().getUptime(), TimeUnit.MILLISECONDS) + " | Java " + System.getProperty("java.version"), null);
		embed.addField("Library", "JDA " + JDAInfo.VERSION + "\nJockie Utils " + JockieUtils.VERSION, true);
		embed.addField("Memory Usage", NumberUtility.getBytesReadable(memoryUsed) + "/" + NumberUtility.getBytesReadable(totalMemory), true);
		embed.addField("CPU Usage", String.format("%.1f%%", ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage()), true);
		embed.addField("Database Queries", String.format("Reads: %,.2f/s\nWrites: %,.2f/s", reads.getLong("ops") / mongoUptime, writes.getLong("ops") / mongoUptime), true);
		embed.addField("Database Uptime", TimeUtility.getTimeString((long) mongoUptime, TimeUtility.SHORT_SUFFIXES), true);
		embed.addField("Commands Used", String.format("%,d", commands), true);
		embed.addField("Threads", String.format("%,d", Thread.activeCount()), true);
		embed.addField("Text Channels", String.format("%,d", event.getShardManager().getTextChannelCache().size()), true);
		embed.addField("Voice Channels", String.format("%,d", event.getShardManager().getVoiceChannelCache().size()), true);
		embed.addField("Servers", String.format("%,d", event.getShardManager().getGuildCache().size()), true);
		embed.addField("Users", String.format("%,d", event.getShardManager().getUserCache().size()), true);

		event.reply(embed.build()).queue();
	}

}
