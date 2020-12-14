package com.sx4.bot.database;

import com.jockie.bot.core.command.impl.CommandEvent;
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
	
	public static final Document EMPTY_DOCUMENT = new Document();

	private static final Database INSTANCE = new Database();
	
	public static Database get() {
		return Database.INSTANCE;
	}

	private final UpdateOptions updateOptions = new UpdateOptions().upsert(true);
	private final FindOneAndUpdateOptions findOneAndUpdateOptions = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER).upsert(true);
	
	private final MongoClient client;
	private final MongoDatabase database;
	
	private final MongoCollection<Document> guilds;
	private final MongoCollection<Document> users;

	private final MongoCollection<Document> reminders;

	private final MongoCollection<Document> suggestions;

	private final MongoCollection<Document> warns;
	private final MongoCollection<Document> mutes;
	private final MongoCollection<Document> temporaryBans;
	
	private final MongoCollection<Document> giveaways;

	private final MongoCollection<Document> redirects;
	
	private final MongoCollection<Document> patrons;
	
	private final MongoCollection<Document> auction;
	
	private final MongoCollection<Document> regexes;
	
	private final MongoCollection<Document> modLogs;
	private final MongoCollection<Document> commands;
	private final MongoCollection<Document> messages;

	private final MongoCollection<Document> youtubeNotifications;
	private final MongoCollection<Document> youtubeSubscriptions;
	private final MongoCollection<Document> youtubeNotificationLogs;
	
	private final MongoCollection<Document> offences;
	
	public Database() {
		DatabaseHandler handler = new DatabaseHandler();
		
		MongoClientSettings settings = MongoClientSettings.builder()
			.addCommandListener(handler)
			.applyToClusterSettings(clusterSettings -> clusterSettings.addClusterListener(handler))
			.build();

		IndexOptions uniqueIndex = new IndexOptions().unique(true);
		
		this.client = MongoClients.create(settings);
		this.database = this.client.getDatabase(Config.get().getDatabase());
		
		this.users = this.database.getCollection("users");
		this.guilds = this.database.getCollection("guilds");

		Bson guildId = Indexes.descending("guildId"), userId = Indexes.descending("userId");

		this.reminders = this.database.getCollection("reminders");
		this.reminders.createIndex(userId);

		this.suggestions = this.database.getCollection("suggestions");
		this.suggestions.createIndex(guildId);
		this.suggestions.createIndex(Indexes.descending("messageId"));

		this.warns = this.database.getCollection("warns");
		this.warns.createIndex(Indexes.compoundIndex(guildId, userId), uniqueIndex);
		this.warns.createIndex(guildId);
		this.warns.createIndex(userId);

		this.mutes = this.database.getCollection("mutes");
		this.mutes.createIndex(Indexes.compoundIndex(guildId, userId), uniqueIndex);
		this.mutes.createIndex(guildId);
		this.mutes.createIndex(userId);

		this.temporaryBans = this.database.getCollection("temporaryBans");
		this.temporaryBans.createIndex(Indexes.compoundIndex(guildId, userId), uniqueIndex);
		this.temporaryBans.createIndex(guildId);
		this.temporaryBans.createIndex(userId);
		
		this.giveaways = this.database.getCollection("giveaways");
		this.giveaways.createIndex(Indexes.descending("guildId"));
		this.giveaways.createIndex(Indexes.descending("channelId"));
		this.giveaways.createIndex(Indexes.descending("winners"));
		
		this.patrons = this.database.getCollection("patrons");
		this.patrons.createIndex(Indexes.descending("discordId"));

		this.redirects = this.database.getCollection("redirects");
		this.redirects.createIndex(Indexes.descending("url"));
		
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
		this.commands.createIndex(Indexes.descending("authorId"));
		this.commands.createIndex(Indexes.descending("guildId"));
		this.commands.createIndex(Indexes.descending("command"));
		this.commands.createIndex(Indexes.descending("channelId"));
		
		this.messages = this.database.getCollection("messages");
		this.messages.createIndex(Indexes.descending("updated"), new IndexOptions().expireAfter(14L, TimeUnit.DAYS));

		this.youtubeNotifications = this.database.getCollection("youtubeNotifications");

		this.youtubeNotifications.createIndex(Indexes.descending("channelId", "uploaderId"), uniqueIndex);
		this.youtubeNotifications.createIndex(Indexes.descending("channelId"));
		this.youtubeNotifications.createIndex(Indexes.descending("uploaderId"));
		this.youtubeNotifications.createIndex(Indexes.descending("guildId"));

		this.youtubeSubscriptions = this.database.getCollection("youtubeSubscriptions");
		this.youtubeNotificationLogs = this.database.getCollection("youtubeNotificationLogs");
		
		this.offences = this.database.getCollection("offences");
		this.offences.createIndex(Indexes.descending("authorId"));
	}
	
	public MongoClient getClient() {
		return this.client;
	}
	
	public MongoDatabase getDatabase() {
		return this.database;
	}

	public MongoCollection<Document> getSuggestions() {
		return this.suggestions;
	}

	public FindIterable<Document> getSuggestions(Bson filter, Bson projection) {
		return this.suggestions.find(filter).projection(projection);
	}

	public Document getSuggestion(Bson filter, Bson projection) {
		return this.getSuggestions(filter, projection).first();
	}

	public CompletableFuture<InsertOneResult> insertSuggestion(Document data) {
		return CompletableFuture.supplyAsync(() -> this.suggestions.insertOne(data));
	}

	public CompletableFuture<UpdateResult> updateSuggestion(Bson filter, Bson update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.suggestions.updateOne(filter, update, options));
	}

	public CompletableFuture<UpdateResult> updateSuggestion(Bson filter, Bson update) {
		return this.updateSuggestion(filter, update, this.updateOptions);
	}

	public CompletableFuture<Document> findAndUpdateSuggestion(Bson filter, Bson update, FindOneAndUpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.suggestions.findOneAndUpdate(filter, update, options));
	}

	public CompletableFuture<Document> findAndUpdateSuggestion(Bson filter, Bson update) {
		return this.findAndUpdateSuggestion(filter, update, this.findOneAndUpdateOptions);
	}

	public CompletableFuture<DeleteResult> deleteSuggestion(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.suggestions.deleteOne(filter));
	}

	public CompletableFuture<Document> findAndDeleteSuggestion(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.suggestions.findOneAndDelete(filter));
	}

	public CompletableFuture<DeleteResult> deleteManySuggestions(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.suggestions.deleteMany(filter));
	}

	public MongoCollection<Document> getReminders() {
		return this.reminders;
	}

	public FindIterable<Document> getReminders(Bson filter, Bson projection) {
		return this.reminders.find(filter).projection(projection);
	}

	public Document getReminder(Bson filter, Bson projection) {
		return this.getReminders(filter, projection).first();
	}

	public CompletableFuture<UpdateResult> updateReminder(Bson filter, Bson update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.reminders.updateOne(filter, update, options));
	}

	public CompletableFuture<UpdateResult> updateReminder(Bson filter, Bson update) {
		return this.updateReminder(filter, update, this.updateOptions);
	}

	public CompletableFuture<UpdateResult> updateReminder(Bson filter, List<Bson> update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.reminders.updateOne(filter, update, options));
	}

	public CompletableFuture<UpdateResult> updateReminder(Bson filter, List<Bson> update) {
		return this.updateReminder(filter, update, this.updateOptions);
	}

	public CompletableFuture<UpdateResult> updateReminder(UpdateOneModel<Document> model) {
		return this.updateReminder(model.getFilter(), model.getUpdate(), model.getOptions());
	}

	public CompletableFuture<InsertOneResult> insertReminder(Document data) {
		return CompletableFuture.supplyAsync(() -> this.reminders.insertOne(data));
	}

	public CompletableFuture<DeleteResult> deleteReminder(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.reminders.deleteOne(filter));
	}

	public CompletableFuture<DeleteResult> deleteReminder(DeleteOneModel<Document> model) {
		return this.deleteReminder(model.getFilter());
	}

	public CompletableFuture<DeleteResult> deleteReminderById(ObjectId id) {
		return this.deleteReminder(Filters.eq("_id", id));
	}

	public CompletableFuture<BulkWriteResult> bulkWriteReminders(List<WriteModel<Document>> bulkData) {
		return CompletableFuture.supplyAsync(() -> this.reminders.bulkWrite(bulkData));
	}

	public MongoCollection<Document> getTemporaryBans() {
		return this.temporaryBans;
	}

	public FindIterable<Document> getTemporaryBans(Bson filter, Bson projection) {
		return this.temporaryBans.find(filter).projection(projection);
	}

	public Document getTemporaryBan(Bson filter, Bson projection) {
		return this.getTemporaryBans(filter, projection).first();
	}

	public CompletableFuture<UpdateResult> updateTemporaryBan(Bson filter, Bson update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.temporaryBans.updateOne(filter, update, options));
	}

	public CompletableFuture<UpdateResult> updateTemporaryBan(Bson filter, Bson update) {
		return this.updateTemporaryBan(filter, update, this.updateOptions);
	}

	public CompletableFuture<UpdateResult> updateTemporaryBan(Bson filter, List<Bson> update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.temporaryBans.updateOne(filter, update, options));
	}

	public CompletableFuture<UpdateResult> updateTemporaryBan(Bson filter, List<Bson> update) {
		return this.updateTemporaryBan(filter, update, this.updateOptions);
	}

	public CompletableFuture<DeleteResult> deleteTemporaryBan(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.temporaryBans.deleteOne(filter));
	}

	public CompletableFuture<DeleteResult> deleteTemporaryBan(DeleteOneModel<Document> model) {
		return this.deleteTemporaryBan(model.getFilter());
	}

	public CompletableFuture<BulkWriteResult> bulkWriteTemporaryBans(List<WriteModel<Document>> bulkData) {
		return CompletableFuture.supplyAsync(() -> this.temporaryBans.bulkWrite(bulkData));
	}

	public MongoCollection<Document> getWarns() {
		return this.warns;
	}

	public FindIterable<Document> getWarns(Bson filter, Bson projection) {
		return this.warns.find(filter).projection(projection);
	}

	public Document getWarn(Bson filter, Bson projection) {
		return this.getWarns(filter, projection).first();
	}

	public CompletableFuture<UpdateResult> updateWarn(Bson filter, List<Bson> update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.warns.updateOne(filter, update, options));
	}

	public CompletableFuture<UpdateResult> updateWarn(Bson filter, List<Bson> update) {
		return this.updateWarn(filter, update, this.updateOptions);
	}

	public CompletableFuture<Document> findAndUpdateWarn(Bson filter, List<Bson> update, FindOneAndUpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.warns.findOneAndUpdate(filter, update, options));
	}

	public CompletableFuture<Document> findAndUpdateWarn(Bson filter, List<Bson> update) {
		return this.findAndUpdateWarn(filter, update, this.findOneAndUpdateOptions);
	}

	public MongoCollection<Document> getMutes() {
		return this.mutes;
	}

	public FindIterable<Document> getMutes(Bson filter, Bson projection) {
		return this.mutes.find(filter).projection(projection);
	}

	public Document getMute(Bson filter, Bson projection) {
		return this.getMutes(filter, projection).first();
	}

	public CompletableFuture<BulkWriteResult> bulkWriteMutes(List<WriteModel<Document>> bulkData) {
		return CompletableFuture.supplyAsync(() -> this.mutes.bulkWrite(bulkData));
	}

	public CompletableFuture<DeleteResult> deleteMute(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.mutes.deleteOne(filter));
	}

	public CompletableFuture<DeleteResult> deleteMute(DeleteOneModel<Document> model) {
		return this.deleteMute(model.getFilter());
	}

	public CompletableFuture<InsertOneResult> insertMute(Document data) {
		return CompletableFuture.supplyAsync(() -> this.mutes.insertOne(data));
	}

	public CompletableFuture<UpdateResult> updateMute(Bson filter, Bson update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.mutes.updateOne(filter, update, options));
	}

	public CompletableFuture<UpdateResult> updateMute(Bson filter, Bson update) {
		return this.updateMute(filter, update, this.updateOptions);
	}

	public CompletableFuture<UpdateResult> updateMute(Bson filter, List<Bson> update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.mutes.updateOne(filter, update, options));
	}

	public CompletableFuture<UpdateResult> updateMute(Bson filter, List<Bson> update) {
		return this.updateMute(filter, update, this.updateOptions);
	}

	public CompletableFuture<Document> findAndUpdateMute(Bson filter, List<Bson> update, FindOneAndUpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.mutes.findOneAndUpdate(filter, update, options));
	}

	public MongoCollection<Document> getYouTubeNotifications() {
		return this.youtubeNotifications;
	}

	public FindIterable<Document> getYouTubeNotifications(Bson filter, Bson projection) {
		return this.youtubeNotifications.find(filter).projection(projection);
	}

	public long countYouTubeNotifications(Bson filter, CountOptions options) {
		return this.youtubeNotifications.countDocuments(filter, options);
	}

	public Document getYouTubeNotification(Bson filter, Bson projection) {
		return this.getYouTubeNotifications(filter, projection).first();
	}

	public CompletableFuture<InsertOneResult> insertYouTubeNotification(Document data) {
		return CompletableFuture.supplyAsync(() -> this.youtubeNotifications.insertOne(data));
	}

	public CompletableFuture<Document> findAndDeleteYouTubeNotificationById(ObjectId id, FindOneAndDeleteOptions options) {
		return CompletableFuture.supplyAsync(() -> this.youtubeNotifications.findOneAndDelete(Filters.eq("_id", id), options));
	}

	public CompletableFuture<DeleteResult> deleteYouTubeNotificationById(ObjectId id) {
		return CompletableFuture.supplyAsync(() -> this.youtubeNotifications.deleteOne(Filters.eq("_id", id)));
	}

	public CompletableFuture<DeleteResult> deleteManyYouTubeNotifications(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.youtubeNotifications.deleteMany(filter));
	}

	public CompletableFuture<Document> findAndUpdateYouTubeNotification(Bson filter, Bson update, FindOneAndUpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.youtubeNotifications.findOneAndUpdate(filter, update, options));
	}

	public CompletableFuture<Document> findAndUpdateYouTubeNotificationById(ObjectId id, Bson update, FindOneAndUpdateOptions options) {
		return this.findAndUpdateYouTubeNotification(Filters.eq("_id", id), update, options);
	}

	public CompletableFuture<UpdateResult> updateYouTubeNotification(Bson filter, Bson update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.youtubeNotifications.updateOne(filter, update, options));
	}

	public CompletableFuture<UpdateResult> updateYouTubeNotificationById(ObjectId id, Bson update, UpdateOptions options) {
		return this.updateYouTubeNotification(Filters.eq("_id", id), update, options);
	}

	public CompletableFuture<UpdateResult> updateYouTubeNotificationById(ObjectId id, Bson update) {
		return this.updateYouTubeNotification(Filters.eq("_id", id), update, this.updateOptions);
	}

	public CompletableFuture<UpdateResult> updateYouTubeNotification(Bson filter, Bson update) {
		return this.updateYouTubeNotification(filter, update, this.updateOptions);
	}

	public CompletableFuture<UpdateResult> updateManyYouTubeNotifications(Bson filter, Bson update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.youtubeNotifications.updateMany(filter, update, options));
	}

	public CompletableFuture<UpdateResult> updateManyYouTubeNotifications(Bson filter, Bson update) {
		return this.updateManyYouTubeNotifications(filter, update, this.updateOptions);
	}

	public MongoCollection<Document> getRedirects() {
		return this.redirects;
	}

	public Document getRedirectById(String id) {
		return this.redirects.find(Filters.eq("_id", id)).first();
	}

	public CompletableFuture<Document> insertRedirect(String id, String url) {
		return CompletableFuture.supplyAsync(() -> this.redirects.findOneAndUpdate(Filters.eq("url", url), Updates.combine(Updates.setOnInsert("_id", id), Updates.setOnInsert("url", url)), this.findOneAndUpdateOptions));
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

	public CompletableFuture<Document> findAndUpdatePatronByFilter(Bson filter, List<? extends Bson> update, FindOneAndUpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.patrons.findOneAndUpdate(filter, update, options));
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

	public CompletableFuture<Document> findAndUpdateModLogById(ObjectId id, List<Bson> update, FindOneAndUpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.modLogs.findOneAndUpdate(Filters.eq("_id", id), update, options));
	}

	public CompletableFuture<UpdateResult> updateManyModLogs(List<Bson> update) {
		return CompletableFuture.supplyAsync(() -> this.modLogs.updateMany(Database.EMPTY_DOCUMENT, update, this.updateOptions));
	}

	public CompletableFuture<Document> findAndDeleteModLog(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.modLogs.findOneAndDelete(filter));
	}

	public CompletableFuture<Document> findAndDeleteModLogById(ObjectId id) {
		return this.findAndDeleteModLog(Filters.eq("_id", id));
	}

	public CompletableFuture<DeleteResult> deleteModLog(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.modLogs.deleteOne(filter));
	}

	public CompletableFuture<DeleteResult> deleteModLogById(ObjectId id) {
		return this.deleteModLog(Filters.eq("_id", id));
	}

	public CompletableFuture<DeleteResult> deleteManyModLogs(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.modLogs.deleteMany(filter));
	}
	
	public MongoCollection<Document> getYouTubeSubscriptions() {
		return this.youtubeSubscriptions;
	}
	
	public long countYouTubeSubscriptionDocuments(Bson filter) {
		return this.youtubeSubscriptions.countDocuments(filter);
	}
	
	public CompletableFuture<DeleteResult> deleteYouTubeSubscription(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.youtubeSubscriptions.deleteOne(filter));
	}
	
	public CompletableFuture<UpdateResult> updateYouTubeSubscriptionById(String channelId, Bson update) {
		return CompletableFuture.supplyAsync(() -> this.youtubeSubscriptions.updateOne(Filters.eq("_id", channelId), update, this.updateOptions));
	}
	
	public CompletableFuture<BulkWriteResult> bulkWriteYouTubeSubscriptions(List<? extends WriteModel<? extends Document>> bulkData) {
		return CompletableFuture.supplyAsync(() -> this.youtubeSubscriptions.bulkWrite(bulkData));
	}

	public MongoCollection<Document> getYoutubeNotificationLogs() {
		return this.youtubeNotificationLogs;
	}
	
	public FindIterable<Document> getYouTubeNotificationLogs(Bson filter, Bson projection) {
		return this.youtubeNotificationLogs.find(filter).projection(projection);
	}
	
	public Document getYouTubeNotificationLog(Bson filter, Bson projection) {
		Document data = this.getYouTubeNotificationLogs(filter, projection).first();
		
		return data == null ? Database.EMPTY_DOCUMENT : data;
	}
	
	public CompletableFuture<InsertOneResult> insertYouTubeNotificationLog(Document data) {
		return CompletableFuture.supplyAsync(() -> this.youtubeNotificationLogs.insertOne(data));
	}
	
	public CompletableFuture<DeleteResult> deleteManyYouTubeNotificationLogs(String videoId) {
		return CompletableFuture.supplyAsync(() -> this.youtubeNotificationLogs.deleteMany(Filters.eq("videoId", videoId)));
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

	public static <Type> BiConsumer<Type, Throwable> exceptionally() {
		return ($, exception) -> ExceptionUtility.sendErrorMessage(exception);
	}

	public static <Type> BiConsumer<Type, Throwable> exceptionally(CommandEvent event) {
		return ($, exception) -> ExceptionUtility.sendExceptionally(event, exception);
	}
	
}
