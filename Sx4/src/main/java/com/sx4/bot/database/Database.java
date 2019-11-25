package com.sx4.bot.database;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import com.sx4.bot.settings.Settings;

public class Database {

	public static final Database INSTANCE = new Database();
	
	public static final Document EMPTY_DOCUMENT = new Document();
	
	public static Database get() {
		return Database.INSTANCE;
	}
	
	private ExecutorService queryExecutor = Executors.newCachedThreadPool();
	
	private MongoClient client;
	
	private MongoDatabase otherDatabase;
	
	private MongoCollection<Document> otherGuilds;
	private MongoCollection<Document> otherUsers;
	
	private MongoDatabase database;
	
	private MongoCollection<Document> guilds;
	private MongoCollection<Document> users;
	private MongoCollection<Document> auction;
	
	private MongoCollection<Document> commandLogs;
	private MongoCollection<Document> guildLogs;
	private MongoCollection<Document> modLogs;
	
	private final UpdateOptions defaultUpdateOptions = new UpdateOptions().upsert(true);
	private final FindOneAndUpdateOptions defaultFindOneAndUpdateOptions = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER).upsert(true);
	
	private Database() {
		this.client = MongoClients.create();
		
		this.otherDatabase = this.client.getDatabase(Settings.CANARY ? Settings.MAIN_DATABASE_NAME : Settings.CANARY_DATABASE_NAME);
		
		this.otherGuilds = this.otherDatabase.getCollection("guilds");
		this.otherUsers = this.otherDatabase.getCollection("users");
		
		this.database = this.client.getDatabase(Settings.DATABASE_NAME);
		
		this.guilds = this.database.getCollection("guilds");
		this.users = this.database.getCollection("users");
		
		this.auction = this.database.getCollection("auction");
		this.auction.createIndex(Indexes.descending("ownerId"));
		this.auction.createIndex(Indexes.descending("item"));
		
		this.modLogs = this.database.getCollection("modLogs");
		this.modLogs.createIndex(Indexes.descending("guildId"));
		this.modLogs.createIndex(Indexes.descending("id"));
		
		this.commandLogs = this.database.getCollection("commandLogs");
		this.commandLogs.createIndex(Indexes.descending("guildId"));
		this.commandLogs.createIndex(Indexes.descending("authorId"));
		this.commandLogs.createIndex(Indexes.descending("command"));
		this.commandLogs.createIndex(Indexes.descending("module"));
		this.commandLogs.createIndex(Indexes.descending("channelId"));
		this.commandLogs.createIndex(Indexes.descending("shard"));
		this.commandLogs.createIndex(Indexes.descending("timestamp"));
		
		this.guildLogs = this.database.getCollection("guildLogs");
		this.guildLogs.createIndex(Indexes.descending("guildId"));
		this.guildLogs.createIndex(Indexes.descending("timestamp"));
		
		System.out.println("Connecting to MongoDB...");
		
		try {
			this.client.listDatabaseNames().first();		
			System.out.println("Connected to MongoDB");
		} catch(Exception e) {
			throw new RuntimeException("MongoDB failed to connect");
		}
	}
	
	public MongoClient getClient() {
		return this.client;
	}
	
	public MongoDatabase getOtherDatabase() {
		return this.otherDatabase;
	}
	
	public MongoCollection<Document> getOtherGuilds() {
		return this.otherGuilds;
	}
	
	public MongoCollection<Document> getOtherUsers() {
		return this.otherUsers;
	}
	
	public MongoDatabase getDatabase() {
		return this.database;
	}
	
	public MongoCollection<Document> getGuilds() {
		return this.guilds;
	}
	
	public MongoCollection<Document> getUsers() {
		return this.users;
	}
	
	public MongoCollection<Document> getAuction() {
		return this.auction;
	}
	
	public MongoCollection<Document> getCommandLogs() {
		return this.commandLogs;
	}
	
	public MongoCollection<Document> getModLogs() {
		return this.modLogs;
	}
	
	public MongoCollection<Document> getGuildLogs() {
		return this.guildLogs;
	}
	
	public int getGuildsGained(Bson filter) {
		FindIterable<Document> guildLogs = this.guildLogs.find(filter).projection(Projections.include("joined"));
		
		int guildsGained = 0;
		for (Document guildLog : guildLogs) {
			boolean joined = guildLog.getBoolean("joined");
			if (joined) {
				guildsGained++;
			} else {
				guildsGained--;
			}
		}
		
		return guildsGained;
	}
	
	public void insertAuction(long ownerId, long price, Document rawItem, DatabaseCallback<Void> callback) {
		this.queryExecutor.submit(() -> {
			try {
				Document auctionItem = new Document("item", rawItem)
						.append("price", price)
						.append("ownerId", ownerId);
				
				this.auction.insertOne(auctionItem);
				
				callback.onResult(null, null);
			} catch(Throwable e) {
				callback.onResult(null, e);
			}
		});
	}
	
	public void removeAuction(ObjectId id, DatabaseCallback<Void> callback) {
		this.queryExecutor.submit(() -> {
			try {
				this.auction.deleteOne(Filters.eq("_id", id));
				
				callback.onResult(null, null);
			} catch(Throwable e) {
				callback.onResult(null, e);
			}
		});
	}
	
	public void insertGuildLog(Document document, DatabaseCallback<Void> callback) {
		this.queryExecutor.submit(() -> {
			try {
				this.guildLogs.insertOne(document);
				
				callback.onResult(null, null);
			} catch(Throwable e) {
				callback.onResult(null, e);
			}
		});
	}
	
	public void insertModLogCase(Document document, DatabaseCallback<Void> callback) {
		this.queryExecutor.submit(() -> {
			try {
				this.modLogs.insertOne(document);
				
				callback.onResult(null, null);
			} catch(Throwable e) {
				callback.onResult(null, e);
			}
		});
	}
	
	public void updateModLogCases(List<WriteModel<Document>> bulkData, DatabaseCallback<BulkWriteResult> callback) {
		this.queryExecutor.submit(() -> {
			try {
				callback.onResult(this.modLogs.bulkWrite(bulkData), null);
			} catch(Throwable e) {
				callback.onResult(null, e);
			}
		});
	}
	
	public void deleteModLogCases(Bson filter, DatabaseCallback<DeleteResult> callback) {
		this.queryExecutor.submit(() -> {
			try {
				callback.onResult(this.modLogs.deleteMany(filter), null);
			} catch(Throwable e) {
				callback.onResult(null, e);
			}
		});
	}
	
	public void insertCommandData(Document document, DatabaseCallback<Void> callback) {
		this.queryExecutor.submit(() -> {
			try {
				this.commandLogs.insertOne(document);
				
				callback.onResult(null, null);
			} catch(Throwable e) {
				callback.onResult(null, e);
			}
		});
	}
	
	public Document getGuildById(long guildId, Bson filters, Bson projection) {
		Document document;
		if (filters != null) {
			document = this.guilds.find(Filters.and(Filters.eq("_id", guildId), filters)).projection(projection).first();
		} else {
			document = this.guilds.find(Filters.eq("_id", guildId)).projection(projection).first();
		}
		
		return document == null ? Database.EMPTY_DOCUMENT : document;
	}
	
	public Document getGuildById(long guildId) {
		return this.getGuildById(guildId, null, null);
	}
	
	public UpdateResult updateGuildById(Bson filters, Bson update, UpdateOptions updateOptions) {
		return this.guilds.updateOne(filters, update, updateOptions == null ? this.defaultUpdateOptions : updateOptions);
	}
	
	public UpdateResult updateGuildById(long guildId, Bson filters, Bson update, UpdateOptions updateOptions) {
		Bson filter;
		if (filters != null) {
			filter = Filters.and(Filters.eq("_id", guildId), filters);
		} else {
			filter = Filters.eq("_id", guildId);
		}
		
		return this.updateGuildById(filter, update, updateOptions == null ? this.defaultUpdateOptions : updateOptions);
	}
	
	public UpdateResult updateGuildById(UpdateOneModel<Document> updateModel) {
		return this.updateGuildById(updateModel.getFilter(), updateModel.getUpdate(), updateModel.getOptions());
	}
	
	public UpdateResult updateGuildById(long guildId, Bson update) {
		return this.updateGuildById(guildId, null, update, null);
	}
	
	public void updateGuildById(Bson filters, Bson update, UpdateOptions updateOptions, DatabaseCallback<UpdateResult> callback) {
		this.queryExecutor.submit(() -> {
			try {
				callback.onResult(this.updateGuildById(filters, update, updateOptions), null);
			} catch(Throwable e) {
				callback.onResult(null, e);
			}
		});
	}
	
	public void updateGuildById(long guildId, Bson filters, Bson update, UpdateOptions updateOptions, DatabaseCallback<UpdateResult> callback) {
		this.queryExecutor.submit(() -> {
			try {
				callback.onResult(this.updateGuildById(guildId, filters, update, updateOptions), null);
			} catch(Throwable e) {
				callback.onResult(null, e);
			}
		});
	}
	
	public void updateGuildById(UpdateOneModel<Document> updateModel, DatabaseCallback<UpdateResult> callback) {
		this.updateGuildById(updateModel.getFilter(), updateModel.getUpdate(), updateModel.getOptions(), callback);
	}
	
	public void updateGuildById(long guildId, Bson update, DatabaseCallback<UpdateResult> callback) {
		this.updateGuildById(guildId, null, update, null, callback);
	}
	
	public Document getGuildByIdAndUpdate(long guildId, Bson filters, Bson update, FindOneAndUpdateOptions findOneAndUpdateOptions) {
		Bson filter;
		if (filters != null) {
			filter = Filters.and(filters, Filters.eq("_id", guildId));
		} else {
			filter = Filters.eq("_id", guildId);
		}
		
		return this.guilds.findOneAndUpdate(filter, update, findOneAndUpdateOptions == null ? this.defaultFindOneAndUpdateOptions : findOneAndUpdateOptions);
	}
	
	public Document getGuildByIdAndUpdate(long guildId, Bson update, Bson projection) {
		return this.getGuildByIdAndUpdate(guildId, null, update, this.defaultFindOneAndUpdateOptions.projection(projection));
	}
	
	public void getGuildByIdAndUpdate(long guildId, Bson filters, Bson update, FindOneAndUpdateOptions findOneAndUpdateOptions, DatabaseCallback<Document> callback) {
		this.queryExecutor.submit(() -> {
			try {
				callback.onResult(this.getGuildByIdAndUpdate(guildId, filters, update, findOneAndUpdateOptions), null);
			} catch(Throwable e) {
				callback.onResult(null, e);
			}
		});
	}
	
	public void getGuildByIdAndUpdate(long guildId, Bson update, Bson projection, DatabaseCallback<Document> callback) {
		this.getGuildByIdAndUpdate(guildId, null, update, this.defaultFindOneAndUpdateOptions.projection(projection), callback);
	}

	public UpdateResult updateManyGuilds(Bson filter, Bson update) {
		return this.guilds.updateMany(filter, update);
	}
	
	public UpdateResult updateManyGuilds(Bson update) {
		return this.updateManyGuilds(Database.EMPTY_DOCUMENT, update);
	}
	
	public void updateManyGuilds(Bson filter, Bson update, DatabaseCallback<UpdateResult> callback) {
		this.queryExecutor.submit(() -> {
			try {
				callback.onResult(this.updateManyGuilds(filter, update), null);
			} catch(Throwable e) {
				callback.onResult(null, e);
			}
		});
	}
	
	public void updateManyGuilds(Bson update, DatabaseCallback<UpdateResult> callback) {
		this.queryExecutor.submit(() -> {
			try {
				callback.onResult(this.updateManyGuilds(update), null);
			} catch(Throwable e) {
				callback.onResult(null, e);
			}
		});
	}
	
	public BulkWriteResult bulkWriteGuilds(List<WriteModel<Document>> bulkData) {
		return this.guilds.bulkWrite(bulkData);
	}
	
	public void bulkWriteGuilds(List<WriteModel<Document>> bulkData, DatabaseCallback<BulkWriteResult> callback) {
		this.queryExecutor.submit(() -> {
			try {
				callback.onResult(this.bulkWriteGuilds(bulkData), null);
			} catch(Throwable e) {
				callback.onResult(null, e);
			}
		});
	}
	
	public Document getUserById(long userId, Bson filters, Bson projection) {
		Document document;
		if (filters != null) {
			document = this.users.find(Filters.and(Filters.eq("_id", userId), filters)).projection(projection).first();
		} else {
			document = this.users.find(Filters.eq("_id", userId)).projection(projection).first();
		}

		return document == null ? Database.EMPTY_DOCUMENT : document;
	}
	
	public Document getUserById(long userId) {
		return this.getUserById(userId, null, null);
	}
	
	public UpdateResult updateUserById(Bson filters, Bson update, UpdateOptions updateOptions) {
		return this.users.updateOne(filters, update, updateOptions == null ? this.defaultUpdateOptions : updateOptions);
	}
	
	public UpdateResult updateUserById(long userId, Bson filters, Bson update, UpdateOptions updateOptions) {
		Bson filter;
		if (filters != null) {
			filter = Filters.and(Filters.eq("_id", userId), filters);
		} else {
			filter = Filters.eq("_id", userId);
		}
		
		return this.updateUserById(filter, update, updateOptions == null ? this.defaultUpdateOptions : updateOptions);
	}
	
	public UpdateResult updateUserById(UpdateOneModel<Document> updateModel) {
		return this.updateGuildById(updateModel.getFilter(), updateModel.getUpdate(), updateModel.getOptions());
	}
	
	public UpdateResult updateUserById(long userId, Bson update) {
		return this.updateUserById(userId, null, update, null);
	}
	
	public void updateUserById(Bson filters, Bson update, UpdateOptions updateOptions, DatabaseCallback<UpdateResult> callback) {
		this.queryExecutor.submit(() -> {
			try {
				callback.onResult(this.updateUserById(filters, update, updateOptions), null);
			} catch(Throwable e) {
				callback.onResult(null, e);
			}
		});
	}
	
	public void updateUserById(long userId, Bson filters, Bson update, UpdateOptions updateOptions, DatabaseCallback<UpdateResult> callback) {
		this.queryExecutor.submit(() -> {
			try {
				callback.onResult(this.updateUserById(userId, filters, update, updateOptions), null);
			} catch(Throwable e) {
				callback.onResult(null, e);
			}
		});
	}
	
	public void updateUserById(UpdateOneModel<Document> updateModel, DatabaseCallback<UpdateResult> callback) {
		this.updateUserById(updateModel.getFilter(), updateModel.getUpdate(), updateModel.getOptions(), callback);
	}
	
	public void updateUserById(long userId, Bson update, DatabaseCallback<UpdateResult> callback) {
		this.updateUserById(userId, null, update, null, callback);
	}
	
	public Document getUserByIdAndUpdate(long userId, Bson filters, Bson update, FindOneAndUpdateOptions findOneAndUpdateOptions) {
		Bson filter;
		if (filters != null) {
			filter = Filters.and(filters, Filters.eq("_id", userId));
		} else {
			filter = Filters.eq("_id", userId);
		}
		
		return this.users.findOneAndUpdate(filter, update, findOneAndUpdateOptions == null ? this.defaultFindOneAndUpdateOptions : findOneAndUpdateOptions);
	}
	
	public Document getUserByIdAndUpdate(long userId, Bson update, Bson projection) {
		return this.getUserByIdAndUpdate(userId, null, update, this.defaultFindOneAndUpdateOptions.projection(projection));
	}
	
	public void getUserByIdAndUpdate(long userId, Bson filters, Bson update, FindOneAndUpdateOptions findOneAndUpdateOptions, DatabaseCallback<Document> callback) {
		this.queryExecutor.submit(() -> {
			try {
				callback.onResult(this.getUserByIdAndUpdate(userId, filters, update, findOneAndUpdateOptions), null);
			} catch(Throwable e) {
				callback.onResult(null, e);
			}
		});
	}
	
	public void getUserByIdAndUpdate(long userId, Bson update, Bson projection, DatabaseCallback<Document> callback) {
		this.getUserByIdAndUpdate(userId, null, update, this.defaultFindOneAndUpdateOptions.projection(projection), callback);
	}
	
	public UpdateResult updateManyUsers(Bson filter, Bson update) {
		return this.users.updateMany(filter, update);
	}
	
	public UpdateResult updateManyUsers(Bson update) {
		return this.updateManyUsers(Database.EMPTY_DOCUMENT, update);
	}
	
	public void updateManyUsers(Bson filter, Bson update, DatabaseCallback<UpdateResult> callback) {
		this.queryExecutor.submit(() -> {
			try {
				callback.onResult(this.updateManyUsers(filter, update), null);
			} catch(Throwable e) {
				callback.onResult(null, e);
			}
		});
	}
	
	public void updateManyUsers(Bson update, DatabaseCallback<UpdateResult> callback) {
		this.queryExecutor.submit(() -> {
			try {
				callback.onResult(this.updateManyUsers(update), null);
			} catch(Throwable e) {
				callback.onResult(null, e);
			}
		});
	}
	
	public BulkWriteResult bulkWriteUsers(List<WriteModel<Document>> bulkData) {
		return this.users.bulkWrite(bulkData);
	}
	
	public void bulkWriteUsers(List<WriteModel<Document>> bulkData, DatabaseCallback<BulkWriteResult> callback) {
		this.queryExecutor.submit(() -> {
			try {
				callback.onResult(this.bulkWriteUsers(bulkData), null);
			} catch(Throwable e) {
				callback.onResult(null, e);
			}
		});
	}
	
}
