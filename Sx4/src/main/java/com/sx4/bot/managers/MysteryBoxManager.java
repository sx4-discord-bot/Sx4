package com.sx4.bot.managers;

import com.sx4.bot.entities.games.MysteryBoxGame;
import net.dv8tion.jda.api.entities.User;

import java.util.HashMap;
import java.util.Map;

public class MysteryBoxManager {

	public static final int BOMB_COUNT = 8;
	public static final int MONEY_COUNT = 16;

	private final Map<Long, MysteryBoxGame> messages;
	private final Map<Long, MysteryBoxGame> users;

	public MysteryBoxManager() {
		this.users = new HashMap<>();
		this.messages = new HashMap<>();
	}

	public MysteryBoxGame getGame(long messageId) {
		return this.messages.get(messageId);
	}

	public boolean hasGame(long messageId) {
		return this.messages.containsKey(messageId);
	}

	public void removeGame(long messageId) {
		MysteryBoxGame game = this.messages.remove(messageId);
		if (game != null) {
			this.users.remove(game.getUserId());
		}
	}

	public MysteryBoxGame getGame(User user) {
		return this.users.get(user.getIdLong());
	}

	public boolean hasGame(User user) {
		return this.users.containsKey(user.getIdLong());
	}

	public void addGame(User user, MysteryBoxGame game) {
		this.users.put(user.getIdLong(), game);
		this.messages.put(game.getMessageId(), game);
	}

}
