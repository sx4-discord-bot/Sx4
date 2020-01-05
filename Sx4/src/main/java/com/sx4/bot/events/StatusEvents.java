package com.sx4.bot.events;

import java.util.concurrent.TimeUnit;

import com.sx4.bot.core.Sx4Bot;

import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Activity.ActivityType;
import net.dv8tion.jda.api.sharding.ShardManager;

public class StatusEvents {

	private static boolean servers = true;
	
	public static void initialize() {
		Sx4Bot.scheduledExectuor.scheduleAtFixedRate(() -> {
			ShardManager shardManager = Sx4Bot.getShardManager();
			if (servers) {
				shardManager.setActivity(Activity.of(ActivityType.WATCHING, String.format("%,d servers", shardManager.getGuilds().size())));
				servers = false;
			} else {
				shardManager.setActivity(Activity.of(ActivityType.WATCHING, String.format("%,d users", shardManager.getUsers().size())));
				servers = true;
			}
		}, 0, 5, TimeUnit.MINUTES);
	}
	
}
