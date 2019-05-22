package com.sx4.events;

import static com.rethinkdb.RethinkDB.r;

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

	@SuppressWarnings("unchecked")
	public void onUserUpdateOnlineStatus(UserUpdateOnlineStatusEvent event) {
		if (event.getOldOnlineStatus().equals(OnlineStatus.OFFLINE) && !event.getNewOnlineStatus().equals(OnlineStatus.OFFLINE)) {
			Connection connection = Sx4Bot.getConnection();
			ShardManager shardManager = Sx4Bot.getShardManager();
			
			Cursor<Map<String, Object>> cursor = r.table("await").run(connection);
			List<Map<String, Object>> data = cursor.toList();
			for (Map<String, Object> userData : data) {
				List<String> users = (List<String>) userData.get("users");
				if (users.contains(event.getUser().getId())) {
					r.table("await").get(userData.get("id")).update(row -> r.hashMap("users", row.g("users").filter(d -> d.ne(event.getUser().getId())))).runNoReply(connection);
					
					User notifiedUser = shardManager.getUserById((String) userData.get("id"));
					if (notifiedUser != null) {
						notifiedUser.openPrivateChannel().queue(channel -> channel.sendMessage("**" + event.getUser().getAsTag() + "** is now online<:online:361440486998671381>").queue(), e -> {});
					}
					
					break;
				}
			}
		}
	}
	
}
