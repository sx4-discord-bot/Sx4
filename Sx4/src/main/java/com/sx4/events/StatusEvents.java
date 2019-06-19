package com.sx4.events;

import java.util.concurrent.TimeUnit;

import com.sx4.core.Sx4Bot;

import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Activity.ActivityType;
import net.dv8tion.jda.api.sharding.ShardManager;

public class StatusEvents {
	
	public static void initialize() {}

	private static boolean servers = true;
	private static ShardManager shardManager = Sx4Bot.getShardManager();
	
	static {
		
		Sx4Bot.scheduledExectuor.scheduleAtFixedRate(() -> {
			if (servers) {
				shardManager.setGame(Activity.of(ActivityType.WATCHING, String.format("%,d servers", shardManager.getGuilds().size())));
				servers = false;
			} else {
				shardManager.setGame(Activity.of(ActivityType.WATCHING, String.format("%,d users", shardManager.getUsers().size())));
				servers = true;
			}
		}, 0, 5, TimeUnit.MINUTES);
		
	}
	
}
