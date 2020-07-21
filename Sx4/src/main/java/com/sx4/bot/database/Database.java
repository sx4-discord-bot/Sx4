package com.sx4.bot.database;

import com.mongodb.MongoClientSettings;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.*;
import com.mongodb.client.model.*;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import com.sx4.bot.config.Config;
import com.sx4.bot.handlers.DatabaseHandler;
import com.sx4.bot.utility.ExceptionUtility;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

public class Database {
	
	public static final BiConsumer<BulkWriteResult, Throwable> DEFAULT_BULK_HANDLER = (result, exception) -> ExceptionUtility.sendErrorMessage(exception);
	
	public static final BiConsumer<UpdateResult, Throwable> DEFAULT_UPDATE_HANDLER = (result, exception) -> ExceptionUtility.sendErrorMessage(exception);
	
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
	
	private final MongoCollection<Document> giveaways;
	
	private final MongoCollection<Document> patrons;
	
	private final MongoCollection<Document> auction;
	
	private final MongoCollection<Document> regexes;
	
	private final MongoCollection<Document> modLogs;
	private final MongoCollection<Document> commands;
	private final MongoCollection<Document> messages;
	
	private final MongoCollection<Document> resubscriptions;
	private final MongoCollection<Document> notifications;
	
	private final MongoCollection<Document> offences;
	
	public Database() {
		DatabaseHandler handler = new DatabaseHandler();
		
		MongoClientSettings settings = MongoClientSettings.builder()
			.addCommandListener(handler)
			.applyToClusterSettings(clusterSettings -> clusterSettings.addClusterListener(handler))
			.build();
		
		this.client = MongoClients.create(settings);
		this.database = this.client.getDatabase(Config.get().getDatabase());
		
		this.users = this.database.getCollection("users");
		this.guilds = this.database.getCollection("guilds");
		
		this.giveaways = this.database.getCollection("giveaways");
		this.giveaways.createIndex(Indexes.descending("guildId"));
		this.giveaways.createIndex(Indexes.descending("channelId"));
		this.giveaways.createIndex(Indexes.descending("winners"));
		
		this.patrons = this.database.getCollection("patrons");
		this.patrons.createIndex(Indexes.descending("discordId"));
		
		this.auction = this.database.getCollection("auction");
		this.auction.createIndex(Indexes.descending("item.name"));
		this.auction.createIndex(Indexes.descending("ownerId"));
		
		this.regexes = this.database.getCollection("regexes");
		this.regexes.createIndex(Indexes.descending("approved"));
		this.regexes.createIndex(Indexes.descending("pattern"));
		
		this.modLogs = this.database.getCollection("modLogs");
		this.modLogs.createIndex(Indexes.descending("action.type"));
		this.modLogs.createIndex(Indexes.descending("guildId"));
		this.modLogs.createIndex(Indexes.descending("moderatorId"));
		this.modLogs.createIndex(Indexes.descending("targetId"));
		
		this.commands = this.database.getCollection("commands");
		this.commands.createIndex(Indexes.descending("timestamp"));
		this.commands.createIndex(Indexes.descending("authorId"));
		this.commands.createIndex(Indexes.descending("guildId"));
		this.commands.createIndex(Indexes.descending("command"));
		this.commands.createIndex(Indexes.descending("channelId"));
		
		this.messages = this.database.getCollection("messages");
		this.messages.createIndex(Indexes.descending("updated"), new IndexOptions().expireAfter(7L, TimeUnit.DAYS));
		
		this.resubscriptions = this.database.getCollection("resubscriptions");
		this.notifications = this.database.getCollection("notifications");
		
		this.offences = this.database.getCollection("offences");
		this.offences.createIndex(Indexes.descending("authorId"));
	}
	
	public MongoClient getClient() {
		return this.client;
	}
	
	public MongoDatabase getDatabase() {
		return this.database;
	}
	
	public MongoCollection<Document> getPatrons() {
		return this.patrons;
	}
	
	public FindIterable<Document> getPatrons(Bson filter, Bson projection) {
		return this.patrons.find(filter).projection(projection);
	}
	
	public long countPatrons(Bson filter) {
		return this.patrons.countDocuments(filter);
	}
	
	public Document getPatronByFilter(Bson filter, Bson projection) {
		Document data = this.getPatrons(filter, projection).first();
		
		return data == null ? Database.EMPTY_DOCUMENT : data;
	}
	
	public Document getPatronById(String id, Bson filter, Bson projection) {
		if (filter == null) {
			filter = Filters.eq("_id", id);
		} else {
			filter = Filters.and(Filters.eq("_id", id), filter);
		}
		
		Document data = this.patrons.find(filter).projection(projection).first();
		
		return data == null ? Database.EMPTY_DOCUMENT : data;
	}
	
	public Document getPatronById(String id, Bson projection) {
		return this.getPatronById(id, null, projection);
	}
	
	public CompletableFuture<UpdateResult> updatePatronByFilter(Bson filter, Bson update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.patrons.updateOne(filter, update, options));
	}
	
	public CompletableFuture<UpdateResult> updatePatronByFilter(Bson filter, Bson update) {
		return this.updatePatronByFilter(filter, update, this.updateOptions);
	}
	
	public CompletableFuture<UpdateResult> updatePatronById(String id, Bson filter, Bson update, UpdateOptions options) {
		Bson dbFilter;
		if (filter == null) {
			dbFilter = Filters.eq("_id", id);
		} else {
			dbFilter = Filters.and(Filters.eq("_id", id), filter);
		}
		
		return this.updatePatronByFilter(dbFilter, update, options);
	}
	
	public CompletableFuture<UpdateResult> updatePatronById(String id, Bson update, UpdateOptions options) {
		return this.updatePatronById(id, null, update, options);
	}
	
	public CompletableFuture<UpdateResult> updatePatronById(String id, Bson update) {
		return this.updatePatronById(id, update, this.updateOptions);
	}
	
	public CompletableFuture<UpdateResult> updatePatronById(UpdateOneModel<Document> update) {
		return CompletableFuture.supplyAsync(() -> this.patrons.updateOne(update.getFilter(), update.getUpdate(), update.getOptions()));
	}
	
	public CompletableFuture<Document> findAndUpdatePatronById(Bson filter, Bson update, FindOneAndUpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.patrons.findOneAndUpdate(filter, update, options));
	}
	
	public CompletableFuture<Document> findAndUpdatePatronById(String id, Bson filter, Bson update, FindOneAndUpdateOptions options) {
		Bson dbFilter;
		if (filter == null) {
			dbFilter = Filters.eq("_id", id);
		} else {
			dbFilter = Filters.and(Filters.eq("_id", id), filter);
		}
		
		return this.findAndUpdatePatronById(dbFilter, update, options);
	}
	
	public CompletableFuture<Document> findAndUpdatePatronById(String id, Bson update, FindOneAndUpdateOptions options) {
		return this.findAndUpdatePatronById(id, null, update, options);
	}
	
	public CompletableFuture<Document> findAndUpdatePatronById(String id, Bson filter, Bson projection, Bson update) {
		return this.findAndUpdatePatronById(id, filter, update, this.findOneAndUpdateOptions.projection(projection));
	}
	
	public CompletableFuture<Document> findAndUpdatePatronById(String id, Bson projection, Bson update) {
		return this.findAndUpdatePatronById(id, null, update, this.findOneAndUpdateOptions.projection(projection));
	}
	
	public CompletableFuture<Document> findAndUpdatePatronById(Bson filter, List<? extends Bson> update, FindOneAndUpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.patrons.findOneAndUpdate(filter, update, options));
	}
	
	public CompletableFuture<Document> findAndUpdatePatronById(String id, Bson filter, List<? extends Bson> update, FindOneAndUpdateOptions options) {
		Bson dbFilter;
		if (filter == null) {
			dbFilter = Filters.eq("_id", id);
		} else {
			dbFilter = Filters.and(Filters.eq("_id", id), filter);
		}
		
		return this.findAndUpdatePatronById(dbFilter, update, options);
	}
	
	public CompletableFuture<Document> findAndUpdatePatronById(String id, Bson filter, Bson projection, List<? extends Bson> update) {
		return this.findAndUpdatePatronById(id, filter, update, this.findOneAndUpdateOptions.projection(projection));
	}
	
	public CompletableFuture<Document> findAndUpdatePatronById(String id, Bson projection, List<? extends Bson> update) {
		return this.findAndUpdatePatronById(id, null, update, this.findOneAndUpdateOptions.projection(projection));
	}
	
	public CompletableFuture<DeleteResult> deletePatronById(String id) {
		return CompletableFuture.supplyAsync(() -> this.patrons.deleteOne(Filters.eq("_id", id)));
	}
	
	public CompletableFuture<Document> findAndDeletePatronById(String id, Bson projection) {
		return CompletableFuture.supplyAsync(() -> this.patrons.findOneAndDelete(Filters.eq("_id", id), new FindOneAndDeleteOptions().projection(projection)));
	}
	
	public CompletableFuture<BulkWriteResult> bulkWritePatrons(List<? extends WriteModel<? extends Document>> bulkData) {
		return CompletableFuture.supplyAsync(() -> this.patrons.bulkWrite(bulkData));
	}
	
	public MongoCollection<Document> getRegexes() {
		return this.regexes;
	}
	
	public FindIterable<Document> getRegexes(Bson filter, Bson projection) {
		return this.regexes.find(filter).projection(projection);
	}
	
	public Document getRegexById(ObjectId id, Bson filter, Bson projection) {
		if (filter == null) {
			filter = Filters.eq("_id", id);
		} else {
			filter = Filters.and(Filters.eq("_id", id), filter);
		}
		
		Document data = this.regexes.find(filter).projection(projection).first();
		
		return data == null ? Database.EMPTY_DOCUMENT : data;
	}
	
	public Document getRegexById(ObjectId id, Bson projection) {
		return this.getRegexById(id, null, projection);
	}
	
	public CompletableFuture<InsertOneResult> insertRegex(Document data) {
		return CompletableFuture.supplyAsync(() -> this.regexes.insertOne(data));
	}
	
	public CompletableFuture<UpdateResult> updateRegexById(ObjectId id, Bson update) {
		return CompletableFuture.supplyAsync(() -> this.regexes.updateOne(Filters.eq("_id", id), update));
	}
	
	public CompletableFuture<Document> findAndUpdateRegexById(ObjectId id, Bson update, FindOneAndUpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.regexes.findOneAndUpdate(Filters.eq("_id", id), update, options));
	}
	
	public CompletableFuture<DeleteResult> deleteRegexById(ObjectId id) {
		return CompletableFuture.supplyAsync(() -> this.regexes.deleteOne(Filters.eq("_id", id)));
	}
	
	public CompletableFuture<Document> findAndDeleteRegexById(ObjectId id, FindOneAndDeleteOptions options) {
		return CompletableFuture.supplyAsync(() -> this.regexes.findOneAndDelete(Filters.eq("_id", id), options));
	}
	
	public MongoCollection<Document> getUsers() {
		return this.users;
	}
	
	public FindIterable<Document> getUsers(Bson filter, Bson projection) {
		return this.users.find(filter).projection(projection);
	}
	
	public long countUsers(Bson filter) {
		return this.users.countDocuments(filter);
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

	public CompletableFuture<UpdateResult> updateUserById(long userId, Bson filter, List<? extends Bson> update, UpdateOptions options) {
		Bson dbFilter;
		if (filter == null) {
			dbFilter = Filters.eq("_id", userId);
		} else {
			dbFilter = Filters.and(Filters.eq("_id", userId), filter);
		}

		return CompletableFuture.supplyAsync(() -> this.users.updateOne(dbFilter, update, options));
	}

	public CompletableFuture<UpdateResult> updateUserById(long userId, List<? extends Bson> update, UpdateOptions options) {
		return this.updateUserById(userId, null, update, options);
	}

	public CompletableFuture<UpdateResult> updateUserById(long userId, List<? extends Bson> update) {
		return this.updateUserById(userId, update, this.updateOptions);
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
	
	public CompletableFuture<UpdateResult> updateUser(UpdateOneModel<Document> update) {
		return CompletableFuture.supplyAsync(() -> this.users.updateOne(update.getFilter(), update.getUpdate(), update.getOptions()));
	}
	
	public CompletableFuture<Document> findAndUpdateUserById(long userId, Bson filter, Bson update, FindOneAndUpdateOptions options) {
		Bson dbFilter;
		if (filter == null) {
			dbFilter = Filters.eq("_id", userId);
		} else {
			dbFilter = Filters.and(Filters.eq("_id", userId), filter);
		}
		
		return CompletableFuture.supplyAsync(() -> this.users.findOneAndUpdate(dbFilter, update, options));
	}
	
	public CompletableFuture<Document> findAndUpdateUserById(long userId, Bson update, FindOneAndUpdateOptions options) {
		return this.findAndUpdateUserById(userId, null, update, options);
	}
	
	public CompletableFuture<Document> findAndUpdateUserById(long userId, Bson update) {
		return this.findAndUpdateUserById(userId, update, this.findOneAndUpdateOptions);
	}
	
	public CompletableFuture<Document> findAndUpdateUserById(long userId, Bson filter, Bson projection, Bson update) {
		return this.findAndUpdateUserById(userId, filter, update, this.findOneAndUpdateOptions.projection(projection));
	}
	
	public CompletableFuture<Document> findAndUpdateUserById(long userId, Bson projection, Bson update) {
		return this.findAndUpdateUserById(userId, null, update, this.findOneAndUpdateOptions.projection(projection));
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
	
	public CompletableFuture<Document> findAndUpdateUserById(long userId, List<? extends Bson> update, FindOneAndUpdateOptions options) {
		return this.findAndUpdateUserById(userId, null, update, options);
	}
	
	public CompletableFuture<Document> findAndUpdateUserById(long userId, List<? extends Bson> update) {
		return this.findAndUpdateUserById(userId, update, this.findOneAndUpdateOptions);
	}
	
	public CompletableFuture<Document> findAndUpdateUserById(long userId, Bson projection, List<? extends Bson> update) {
		return this.findAndUpdateUserById(userId, update, this.findOneAndUpdateOptions.projection(projection));
	}
	
	public CompletableFuture<BulkWriteResult> bulkWriteUsers(List<? extends WriteModel<? extends Document>> bulkData) {
		return CompletableFuture.supplyAsync(() -> this.users.bulkWrite(bulkData));
	}
	
	public MongoCollection<Document> getGuilds() {
		return this.guilds;
	}
	
	public FindIterable<Document> getGuilds(Bson filter, Bson projection) {
		return this.guilds.find(filter).projection(projection);
	}
	
	public long countGuilds(Bson filter) {
		return this.guilds.countDocuments(filter);
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
	
	public CompletableFuture<UpdateResult> updateGuildById(long guildId, Bson filter, List<? extends Bson> update, UpdateOptions options) {
		Bson dbFilter;
		if (filter == null) {
			dbFilter = Filters.eq("_id", guildId);
		} else {
			dbFilter = Filters.and(Filters.eq("_id", guildId), filter);
		}
		
		return CompletableFuture.supplyAsync(() -> this.guilds.updateOne(dbFilter, update, options));
	}
	
	public CompletableFuture<UpdateResult> updateGuildById(long guildId, List<? extends Bson> update, UpdateOptions options) {
		return this.updateGuildById(guildId, null, update, options);
	}
	
	public CompletableFuture<UpdateResult> updateGuildById(long guildId, List<? extends Bson> update) {
		return this.updateGuildById(guildId, update, this.updateOptions);
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
	
	public CompletableFuture<UpdateResult> updateGuild(UpdateOneModel<Document> model) {
		return CompletableFuture.supplyAsync(() -> this.guilds.updateOne(model.getFilter(), model.getUpdate(), model.getOptions()));
	}
	
	public CompletableFuture<Document> findAndUpdateGuildById(long guildId, Bson filter, Bson update, FindOneAndUpdateOptions options) {
		Bson dbFilter;
		if (filter == null) {
			dbFilter = Filters.eq("_id", guildId);
		} else {
			dbFilter = Filters.and(Filters.eq("_id", guildId), filter);
		}
		
		return CompletableFuture.supplyAsync(() -> this.guilds.findOneAndUpdate(dbFilter, update, options));
	}
	
	public CompletableFuture<Document> findAndUpdateGuildById(long guildId, Bson update, FindOneAndUpdateOptions options) {
		return this.findAndUpdateGuildById(guildId, null, update, options);
	}
	
	public CompletableFuture<Document> findAndUpdateGuildById(long guildId, Bson filter, Bson projection, Bson update) {
		return this.findAndUpdateGuildById(guildId, filter, update, this.findOneAndUpdateOptions.projection(projection));
	}
	
	public CompletableFuture<Document> findAndUpdateGuildById(long guildId, Bson projection, Bson update) {
		return this.findAndUpdateGuildById(guildId, null, update, this.findOneAndUpdateOptions.projection(projection));
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
	
	public CompletableFuture<Document> findAndUpdateGuildById(long guildId, List<? extends Bson> update, FindOneAndUpdateOptions options) {
		return this.findAndUpdateGuildById(guildId, null, update, options);
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
	
	public MongoCollection<Document> getGiveaways() {
		return this.giveaways;
	}
	
	public FindIterable<Document> getGiveaways(Bson filter) {
		return this.giveaways.find(filter);
	}
	
	public Document getGiveawayById(long messageId) {
		return this.giveaways.find(Filters.eq("_id", messageId)).first();
	}
	
	public CompletableFuture<InsertOneResult> insertGiveaway(Document data) {
		return CompletableFuture.supplyAsync(() -> this.giveaways.insertOne(data));
	}
	
	public CompletableFuture<UpdateResult> updateGiveaway(UpdateOneModel<Document> model) {
		return CompletableFuture.supplyAsync(() -> this.giveaways.updateOne(model.getFilter(), model.getUpdate(), model.getOptions()));
	}
	
	public CompletableFuture<UpdateResult> updateGiveawayById(long messageId, Bson update) {
		return CompletableFuture.supplyAsync(() -> this.giveaways.updateOne(Filters.eq("_id", messageId), update));
	}
	
	public CompletableFuture<UpdateResult> updateGiveawayById(long messageId, List<? extends Bson> update) {
		return CompletableFuture.supplyAsync(() -> this.giveaways.updateOne(Filters.eq("_id", messageId), update));
	}
	
	public CompletableFuture<Document> findAndUpdateGiveawayById(long messageId, List<? extends Bson> update, FindOneAndUpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.giveaways.findOneAndUpdate(Filters.eq("_id", messageId), update, options));
	}
	
	public CompletableFuture<DeleteResult> deleteGiveawayById(long messageId) {
		return CompletableFuture.supplyAsync(() -> this.giveaways.deleteOne(Filters.eq("_id", messageId)));
	}
	
	public CompletableFuture<DeleteResult> deleteManyGiveaways(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.giveaways.deleteMany(filter));
	}
	
	public CompletableFuture<BulkWriteResult> bulkWriteGiveaways(List<? extends WriteModel<? extends Document>> bulkData) {
		return CompletableFuture.supplyAsync(() -> this.giveaways.bulkWrite(bulkData));
	}
	
	public MongoCollection<Document> getMessages() {
		return this.messages;
	}
	
	public FindIterable<Document> getMessages(Bson filter) {
		return this.messages.find(filter);
	}
	
	public long countMessages(Bson filter) {
		return this.messages.countDocuments(filter);
	}
	
	public Document getMessageById(long messageId) {
		return this.messages.find(Filters.eq("_id", messageId)).first();
	}
	
	public CompletableFuture<InsertOneResult> insertMessage(Document data) {
		return CompletableFuture.supplyAsync(() -> this.messages.insertOne(data));
	}
	
	public CompletableFuture<UpdateResult> replaceMessage(Document data) {
		return CompletableFuture.supplyAsync(() -> this.messages.replaceOne(Filters.eq("_id", data.get("_id")), data));
	}
	
	public CompletableFuture<DeleteResult> deleteMessageById(long messageId) {
		return CompletableFuture.supplyAsync(() -> this.messages.deleteOne(Filters.eq("_id", messageId)));
	}

	public CompletableFuture<DeleteResult> deleteMessages(List<Long> messageIds) {
		return CompletableFuture.supplyAsync(() -> this.messages.deleteMany(Filters.in("_id", messageIds)));
	}
	
	public CompletableFuture<BulkWriteResult> bulkWriteMessages(List<? extends WriteModel<? extends Document>> bulkData) {
		return CompletableFuture.supplyAsync(() -> this.messages.bulkWrite(bulkData));
	}
	
	public MongoCollection<Document> getAuction() {
		return this.auction;
	}
	
	public FindIterable<Document> getAuctions(Bson filter) {
		return this.auction.find(filter);
	}
	
	public Document getAuctionById(ObjectId id) {
		return this.auction.find(Filters.eq("_id", id)).first();
	}
	
	public CompletableFuture<DeleteResult> deleteAuctionById(ObjectId id) {
		return CompletableFuture.supplyAsync(() -> this.auction.deleteOne(Filters.eq("_id", id)));
	}
 	
	public MongoCollection<Document> getModLogs() {
		return this.modLogs;
	}
	
	public FindIterable<Document> getModLogs(Bson filter, Bson projection) {
		return this.modLogs.find(filter).projection(projection).sort(Sorts.descending("_id"));
	}
	
	public Document getModLogById(Bson filter, Bson projection) {
		return this.modLogs.find(filter).projection(projection).first();
	}
	
	public CompletableFuture<InsertOneResult> insertModLog(Document data) {
		return CompletableFuture.supplyAsync(() -> this.modLogs.insertOne(data));
	}
	
	public CompletableFuture<UpdateResult> updateModLogById(ObjectId id, Bson update) {
		return CompletableFuture.supplyAsync(() -> this.modLogs.updateOne(Filters.eq("_id", id), update, this.updateOptions));
	}
	
	public MongoCollection<Document> getResubscriptions() {
		return this.resubscriptions;
	}
	
	public long countResubscriptionDocuments(Bson filter) {
		return this.resubscriptions.countDocuments(filter);
	}
	
	public CompletableFuture<DeleteResult> deleteResubscription(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.resubscriptions.deleteOne(filter));
	}
	
	public CompletableFuture<UpdateResult> updateResubscriptionById(String channelId, Bson update) {
		return CompletableFuture.supplyAsync(() -> this.resubscriptions.updateOne(Filters.eq("_id", channelId), update, this.updateOptions));
	}
	
	public CompletableFuture<BulkWriteResult> bulkWriteResubscriptions(List<? extends WriteModel<? extends Document>> bulkData) {
		return CompletableFuture.supplyAsync(() -> this.resubscriptions.bulkWrite(bulkData));
	}
	
	public FindIterable<Document> getNotifications(Bson filter, Bson projection) {
		return this.notifications.find(filter).projection(projection).sort(Sorts.descending("_id"));
	}
	
	public Document getNotification(Bson filter, Bson projection) {
		Document data = this.getNotifications(filter, projection).first();
		
		return data == null ? Database.EMPTY_DOCUMENT : data;
	}
	
	public CompletableFuture<InsertOneResult> insertNotification(Document data) {
		return CompletableFuture.supplyAsync(() -> this.notifications.insertOne(data));
	}
	
	public CompletableFuture<DeleteResult> deleteManyNotifications(String videoId) {
		return CompletableFuture.supplyAsync(() -> this.notifications.deleteMany(Filters.eq("videoId", videoId)));
	}
	
	public MongoCollection<Document> getCommands() {
		return this.commands;
	}
	
	public CompletableFuture<FindIterable<Document>> getCommands(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.commands.find(filter));
	}
	
	public CompletableFuture<AggregateIterable<Document>> aggregateCommands(List<? extends Bson> pipeline) {
		return CompletableFuture.supplyAsync(() -> this.commands.aggregate(pipeline));
	}
	
	public MongoCollection<Document> getOffences() {
		return this.offences;
	}
	
	public CompletableFuture<FindIterable<Document>> getOffences(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.offences.find(filter));
	}
	
}
