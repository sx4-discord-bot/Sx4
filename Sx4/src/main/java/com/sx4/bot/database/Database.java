package com.sx4.bot.database;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.mongodb.MongoClientSettings;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import com.sx4.bot.config.Config;
import com.sx4.bot.entities.mod.Action;
import com.sx4.bot.entities.mod.ModLog;
import com.sx4.bot.entities.mod.TimeAction;
import com.sx4.bot.entities.mod.WarnAction;
import com.sx4.bot.entities.warn.WarnConfig;
import com.sx4.bot.handlers.DatabaseHandler;

public class Database {
	
	public static final Document EMPTY_DOCUMENT = new Document();

	public static final Database INSTANCE = new Database();
	
	public static Database get() {
		return Database.INSTANCE;
	}
	
	private final UpdateOptions updateOptions = new UpdateOptions().upsert(true);
	private final FindOneAndUpdateOptions findOneAndUpdateOptions = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER).upsert(true);
	
	private final MongoClient client;
	private final MongoDatabase database;
	
	private final MongoCollection<Document> guilds;
	private final MongoCollection<Document> users;
	
	private final MongoCollection<Document> modLogs;
	private final MongoCollection<Document> commandLogs;
	
	private final MongoCollection<Document> offences;
	
	public Database() {
		DatabaseHandler handler = new DatabaseHandler();
		
		MongoClientSettings settings = MongoClientSettings.builder()
			.addCommandListener(handler)
			.applyToClusterSettings(block -> block.addClusterListener(handler))
			.build();
		
		this.client = MongoClients.create(settings);
		this.database = this.client.getDatabase(Config.get().getDatabase());
		
		this.users = this.database.getCollection("users");
		this.guilds = this.database.getCollection("guilds");
		
		this.modLogs = this.database.getCollection("modLogs");
		this.modLogs.createIndex(Indexes.descending("timestamp"));
		
		this.commandLogs = this.database.getCollection("commandLogs");
		this.commandLogs.createIndex(Indexes.descending("timestamp"));
		this.commandLogs.createIndex(Indexes.descending("authorId"));
		this.commandLogs.createIndex(Indexes.descending("guildId"));
		this.commandLogs.createIndex(Indexes.descending("command"));
		this.commandLogs.createIndex(Indexes.descending("channelId"));
		
		this.offences = this.database.getCollection("offences");
		this.offences.createIndex(Indexes.descending("type"));
		this.offences.createIndex(Indexes.descending("authorId"));
	}
	
	public MongoClient getClient() {
		return this.client;
	}
	
	public MongoDatabase getDatabase() {
		return this.database;
	}
	
	public MongoCollection<Document> getUsers() {
		return this.users;
	}
	
	public Document getUserById(long userId, Bson filter, Bson projection) {
		if (filter == null) {
			filter = Filters.eq("_id", userId);
		} else {
			filter = Filters.and(Filters.eq("_id", userId), filter);
		}
		
		Document data = this.users.find(filter).projection(projection).first();
		
		return data == null ? Database.EMPTY_DOCUMENT : data;
	}
	
	public Document getUserById(long userId, Bson projection) {
		return this.getUserById(userId, null, projection);
	}
	
	public CompletableFuture<UpdateResult> updateUserById(long userId, Bson filter, Bson update, UpdateOptions options) {
		Bson dbFilter;
		if (filter == null) {
			dbFilter = Filters.eq("_id", userId);
		} else {
			dbFilter = Filters.and(Filters.eq("_id", userId), filter);
		}
		
		return CompletableFuture.supplyAsync(() -> this.users.updateOne(dbFilter, update, options));
	}
	
	public CompletableFuture<UpdateResult> updateUserById(long userId, Bson update, UpdateOptions options) {
		return this.updateUserById(userId, null, update, options);
	}
	
	public CompletableFuture<UpdateResult> updateUserById(long userId, Bson update) {
		return this.updateUserById(userId, update, this.updateOptions);
	}
	
	public CompletableFuture<Document> findAndUpdateUserById(long userId, Bson filter, List<? extends Bson> update, FindOneAndUpdateOptions options) {
		Bson dbFilter;
		if (filter == null) {
			dbFilter = Filters.eq("_id", userId);
		} else {
			dbFilter = Filters.and(Filters.eq("_id", userId), filter);
		}
		
		return CompletableFuture.supplyAsync(() -> this.users.findOneAndUpdate(dbFilter, update, options));
	}
	
	public CompletableFuture<Document> findAndUpdateUserById(long userId, Bson filter, Bson projection, List<? extends Bson> update) {
		return this.findAndUpdateUserById(userId, filter, update, this.findOneAndUpdateOptions.projection(projection));
	}
	
	public CompletableFuture<Document> findAndUpdateUserById(long userId, Bson projection, List<? extends Bson> update) {
		return this.findAndUpdateUserById(userId, null, update, this.findOneAndUpdateOptions.projection(projection));
	}
	
	public CompletableFuture<BulkWriteResult> bulkWriteUsers(List<? extends WriteModel<? extends Document>> bulkData) {
		return CompletableFuture.supplyAsync(() -> this.users.bulkWrite(bulkData));
	}
	
	public MongoCollection<Document> getGuilds() {
		return this.guilds;
	}
	
	public Document getGuildById(long guildId, Bson filter, Bson projection) {
		if (filter == null) {
			filter = Filters.eq("_id", guildId);
		} else {
			filter = Filters.and(Filters.eq("_id", guildId), filter);
		}
		
		Document data = this.guilds.find(filter).projection(projection).first();
		
		return data == null ? Database.EMPTY_DOCUMENT : data;
	}
	
	public Document getGuildById(long guildId, Bson projection) {
		return this.getGuildById(guildId, null, projection);
	}
	
	public CompletableFuture<UpdateResult> updateGuildById(long guildId, Bson filter, Bson update, UpdateOptions options) {
		Bson dbFilter;
		if (filter == null) {
			dbFilter = Filters.eq("_id", guildId);
		} else {
			dbFilter = Filters.and(Filters.eq("_id", guildId), filter);
		}
		
		return CompletableFuture.supplyAsync(() -> this.guilds.updateOne(dbFilter, update, options));
	}
	
	public CompletableFuture<UpdateResult> updateGuildById(long guildId, Bson update, UpdateOptions options) {
		return this.updateGuildById(guildId, null, update, options);
	}
	
	public CompletableFuture<UpdateResult> updateGuildById(long guildId, Bson update) {
		return this.updateGuildById(guildId, update, this.updateOptions);
	}
	
	public CompletableFuture<Document> findAndUpdateGuildById(long guildId, Bson filter, List<? extends Bson> update, FindOneAndUpdateOptions options) {
		Bson dbFilter;
		if (filter == null) {
			dbFilter = Filters.eq("_id", guildId);
		} else {
			dbFilter = Filters.and(Filters.eq("_id", guildId), filter);
		}
		
		return CompletableFuture.supplyAsync(() -> this.guilds.findOneAndUpdate(dbFilter, update, options));
	}
	
	public CompletableFuture<Document> findAndUpdateGuildById(long guildId, Bson filter, Bson projection, List<? extends Bson> update) {
		return this.findAndUpdateGuildById(guildId, filter, update, this.findOneAndUpdateOptions.projection(projection));
	}
	
	public CompletableFuture<Document> findAndUpdateGuildById(long guildId, Bson projection, List<? extends Bson> update) {
		return this.findAndUpdateGuildById(guildId, null, update, this.findOneAndUpdateOptions.projection(projection));
	}
	
	public CompletableFuture<BulkWriteResult> bulkWriteGuilds(List<? extends WriteModel<? extends Document>> bulkData) {
		return CompletableFuture.supplyAsync(() -> this.guilds.bulkWrite(bulkData));
	}
	
	public MongoCollection<Document> getModLogs() {
		return this.modLogs;
	}
	
	public Document getModLog(ObjectId id) {
		return this.modLogs.find(Filters.eq("_id", id)).first();
	}
	
	public CompletableFuture<InsertOneResult> insertModLog(ModLog modLog) {
		Document data = new Document("_id", modLog.getId())
				.append("guildId", modLog.getGuildId())
				.append("channelId", modLog.getChannelId())
				.append("targetId", modLog.getTargetId())
				.append("moderatorId", modLog.getModeratorId())
				.append("reason", modLog.getReason());
		
		Action action = modLog.getAction();
		Document actionData = new Document("type", action.getModAction().getType());
		
		if (action instanceof TimeAction) {
			TimeAction timeAction = (TimeAction) action;
			
			if (timeAction.hasDuration()) {
				actionData.append("duration", timeAction.getDuration());
			}
		} else if (action instanceof WarnAction) {
			WarnConfig warning = ((WarnAction) action).getWarning();
			
			Document warnData = new Document("number", warning.getNumber())
					.append("type", warning.getAction().getType());
			
			if (warning.hasDuration()) {
				warnData.append("duration", warning.getDuration());
			}
			
			actionData.append("warning", warnData);
		}
		
		data.append("action", actionData);
		
		return CompletableFuture.supplyAsync(() -> this.modLogs.insertOne(data));
	}
	
	public MongoCollection<Document> getCommandLogs() {
		return this.commandLogs;
	}
	
	public CompletableFuture<FindIterable<Document>> getCommandLogs(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.commandLogs.find(filter));
	}
	
	public CompletableFuture<AggregateIterable<Document>> aggregateCommandLogs(List<? extends Bson> pipeline) {
		return CompletableFuture.supplyAsync(() -> this.commandLogs.aggregate(pipeline));
	}
	
	public MongoCollection<Document> getOffences() {
		return this.offences;
	}
	
	public CompletableFuture<FindIterable<Document>> getOffences(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.offences.find(filter));
	}
	
}
