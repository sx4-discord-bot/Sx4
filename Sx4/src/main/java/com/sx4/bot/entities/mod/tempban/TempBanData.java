package com.sx4.bot.entities.mod.tempban;

import java.time.Clock;
import java.util.Collections;
import java.util.List;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;

public class TempBanData {

	private final List<TempBanUser> users;
	private final long defaultTime;
	
	public TempBanData(Document data) {
		List<Document> users = data.getList("users", Document.class, Collections.emptyList());
		
		this.users = TempBanUser.fromData(users);
		this.defaultTime = data.get("defaultTime", 86400L);
	}
	
	public TempBanData(List<TempBanUser> users, long defaultTime) {
		this.users = users;
		this.defaultTime = defaultTime;
	}
	
	public List<TempBanUser> getUsers() {
		return this.users;
	}
	
	public TempBanUser getUserById(long userId) {
		return this.users.stream()
			.filter(user -> user.getId() == userId)
			.findFirst()
			.orElse(null);
	}
	
	public long getDefaultTime() {
		return this.defaultTime;
	}
	
	public UpdateOneModel<Document> getUpdate(long guildId, long userId, long seconds) {
		TempBanUser user = this.getUserById(userId);
		
		Bson update;
		List<Bson> arrayFilters = null;
		if (user == null) {
			Document rawData = new Document("id", userId)
					.append("unbanAt", Clock.systemUTC().instant().getEpochSecond() + seconds);
			
			update = Updates.push("tempBan.users", rawData);
		} else {
			arrayFilters = List.of(Filters.eq("user.id", user.getId()));
			
			update = Updates.set("tempBan.users.$[user].unbanAt", Clock.systemUTC().instant().getEpochSecond() + seconds);
		}
		
		return new UpdateOneModel<>(Filters.eq("_id", guildId), update, new UpdateOptions().arrayFilters(arrayFilters).upsert(true));
	}
	
}
