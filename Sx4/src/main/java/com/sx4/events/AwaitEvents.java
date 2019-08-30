package com.sx4.events;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bson.Document;

import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.sx4.core.Sx4Bot;
import com.sx4.database.Database;

import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.user.update.UserUpdateOnlineStatusEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.sharding.ShardManager;

public class AwaitEvents extends ListenerAdapter {
	
	private static List<Document> awaitData = new ArrayList<>();
	
	public static List<Document> getAwaitData() {
		return awaitData;
	}
	
	public static void addUsers(long authorId, List<Long> userIds) {
		for (Document userData : awaitData) {
			if (userData.getLong("_id") == authorId) {
				List<Long> usersData = userData.getEmbedded(List.of("await", "users"), new ArrayList<>());
				usersData.addAll(userIds);
				return;
			}
		}
		
		Document userData = new Document("_id", authorId)
				.append("await", new Document("users", userIds));
		
		awaitData.add(userData);
	}
	
	public static void addUsers(long authorId, Long... userIds) {
		AwaitEvents.addUsers(authorId, Arrays.asList(userIds));
	}
	
	public static void removeUser(long authorId, long userId) {
		for (Document userData : awaitData) {
			if (userData.getLong("_id") == authorId) {
				List<Long> usersData = userData.getEmbedded(List.of("await", "users"), new ArrayList<>());
				usersData.remove(userId);
				userData.put("users", usersData);
				return;
			}
		}
		
		throw new IllegalArgumentException("The author is not in the await data");
	}
	
	public static void ensureAwaitData() {
		awaitData = Database.get().getUsers().find().projection(Projections.include("await.users")).into(new ArrayList<>());
	}

	public void onUserUpdateOnlineStatus(UserUpdateOnlineStatusEvent event) {
		if (event.getOldOnlineStatus().equals(OnlineStatus.OFFLINE) && !event.getNewOnlineStatus().equals(OnlineStatus.OFFLINE)) {
			ShardManager shardManager = Sx4Bot.getShardManager();
			
			for (Document userData : AwaitEvents.getAwaitData()) {
				List<Long> users = userData.getEmbedded(List.of("await", "users"), new ArrayList<>());
				if (users.contains(event.getUser().getIdLong())) {
					AwaitEvents.removeUser(userData.getLong("_id"), event.getUser().getIdLong());
					
					Database.get().updateUserById(userData.getLong("_id"), Updates.pull("await.users", event.getUser().getIdLong()), (result, exception) -> {
						if (exception != null) {
							exception.printStackTrace();
						}
					});
					
					User notifiedUser = shardManager.getUserById(userData.getLong("_id"));
					if (notifiedUser != null) {
						notifiedUser.openPrivateChannel().queue(channel -> channel.sendMessage("**" + event.getUser().getAsTag() + "** is now online<:online:361440486998671381>").queue(), e -> {});
					}
					
					continue;
				}
			}
		}
	}
	
}
