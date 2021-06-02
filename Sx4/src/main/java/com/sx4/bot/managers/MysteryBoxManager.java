package com.sx4.bot.managers;

import com.sx4.bot.entities.games.MysteryBoxGame;
import net.dv8tion.jda.api.entities.User;

import java.util.HashMap;
import java.util.Map;

public class MysteryBoxManager {

	private final Map<Long, MysteryBoxGame> games;

	public MysteryBoxManager() {
		this.games = new HashMap<>();
	}

	public MysteryBoxGame getGame(User user) {
		return this.games.get(user.getIdLong());
	}

	public boolean hasGame(User user) {
		return this.games.containsKey(user.getIdLong());
	}

	public void addGame(User user, MysteryBoxGame game) {
		this.games.put(user.getIdLong(), game);
	}

	public void removeGame(User user) {
		this.games.remove(user.getIdLong());
	}

}
