package com.sx4.events;

import java.util.concurrent.TimeUnit;

import com.sx4.core.Sx4Bot;

import net.dv8tion.jda.bot.sharding.ShardManager;
import net.dv8tion.jda.core.entities.Game;
import net.dv8tion.jda.core.entities.Game.GameType;

public class StatusEvents {
	
	public static void initialize() {}

	private static boolean servers = true;
	private static ShardManager shardManager = Sx4Bot.getShardManager();
	
	static {
		
		Sx4Bot.scheduledExectuor.scheduleAtFixedRate(() -> {
			if (servers) {
				shardManager.setGame(Game.of(GameType.WATCHING, String.format("%,d servers", shardManager.getGuilds().size())));
				servers = false;
			} else {
				shardManager.setGame(Game.of(GameType.WATCHING, String.format("%,d users", shardManager.getUsers().size())));
				servers = true;
			}
		}, 0, 5, TimeUnit.MINUTES);
		
	}
	
}
