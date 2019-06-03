package com.sx4.events;

import static com.rethinkdb.RethinkDB.r;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.rethinkdb.net.Connection;
import com.rethinkdb.net.Cursor;
import com.sx4.core.Sx4Bot;

import net.dv8tion.jda.bot.sharding.ShardManager;
import net.dv8tion.jda.core.OnlineStatus;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.user.update.UserUpdateOnlineStatusEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

public class AwaitEvents extends ListenerAdapter {
	
	private static List<Map<String, Object>> awaitData = new ArrayList<>();
	
	public static List<Map<String, Object>> getAwaitData() {
		return awaitData;
	}
	
	@SuppressWarnings("unchecked")
	public static void addUsers(String authorId, List<String> userIds) {
		for (Map<String, Object> userData : awaitData) {
			if (userData.get("id").equals(authorId)) {
				List<String> usersData = (List<String>) userData.get("users");
				usersData.addAll(userIds);
				
				awaitData.remove(userData);
				userData.put("users", usersData);
				awaitData.add(userData);
				
				return;
			}
		}
		
		Map<String, Object> userData = new HashMap<>();
		userData.put("id", authorId);
		userData.put("users", userIds);
		awaitData.add(userData);
	}
	
	public static void addUsers(String authorId, String... userIds) {
		AwaitEvents.addUsers(authorId, List.of(userIds));
	}
	
	@SuppressWarnings("unchecked")
	public static void removeUser(String authorId, String userId) {
		for (Map<String, Object> userData : awaitData) {
			if (userData.get("id").equals(authorId)) {
				List<String> usersData = (List<String>) userData.get("users");
				usersData.remove(userId);
				
				awaitData.remove(userData);
				userData.put("users", usersData);
				awaitData.add(userData);
				
				return;
			}
		}
		
		throw new IllegalArgumentException("The author is not in the await data");
	}
	
	public static void ensureAwaitData() {
		try (Cursor<Map<String, Object>> cursor = r.table("await").run(Sx4Bot.getConnection())) {
			awaitData = cursor.toList();
		}
	}

	@SuppressWarnings("unchecked")
	public void onUserUpdateOnlineStatus(UserUpdateOnlineStatusEvent event) {
		if (event.getOldOnlineStatus().equals(OnlineStatus.OFFLINE) && !event.getNewOnlineStatus().equals(OnlineStatus.OFFLINE)) {
			Connection connection = Sx4Bot.getConnection();
			ShardManager shardManager = Sx4Bot.getShardManager();
			
			List<Map<String, Object>> data = awaitData;
			for (Map<String, Object> userData : data) {
				List<String> users = (List<String>) userData.get("users");
				if (users.contains(event.getUser().getId())) {
					AwaitEvents.removeUser((String) userData.get("id"), event.getUser().getId());
					r.table("await").get(userData.get("id")).update(row -> r.hashMap("users", row.g("users").filter(d -> d.ne(event.getUser().getId())))).runNoReply(connection);
					
					User notifiedUser = shardManager.getUserById((String) userData.get("id"));
					if (notifiedUser != null) {
						notifiedUser.openPrivateChannel().queue(channel -> channel.sendMessage("**" + event.getUser().getAsTag() + "** is now online<:online:361440486998671381>").queue(), e -> {});
					}
					
					continue;
				}
			}
		}
	}
	
}
