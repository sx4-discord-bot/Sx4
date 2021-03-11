package com.sx4.bot.database;

import com.jockie.bot.core.command.impl.CommandEvent;
import com.mongodb.MongoClientSettings;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.*;
import com.mongodb.client.model.*;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import com.sx4.bot.handlers.DatabaseHandler;
import com.sx4.bot.utility.ExceptionUtility;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

public class Database {
	
	public static final Document EMPTY_DOCUMENT = new Document();

	private final UpdateOptions updateOptions = new UpdateOptions().upsert(true);
	private final FindOneAndUpdateOptions findOneAndUpdateOptions = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER).upsert(true);
	
	private final MongoClient client;
	private final MongoDatabase database;
	
	private final MongoCollection<Document> guilds;
	private final MongoCollection<Document> users;
	private final MongoCollection<Document> channels;
	private final MongoCollection<Document> members;

	private final MongoCollection<Document> triggers;

	private final MongoCollection<Document> templates;

	private final MongoCollection<Document> starboards;
	private final MongoCollection<Document> stars;

	private final MongoCollection<Document> marriages;

	private final MongoCollection<Document> reminders;

	private final MongoCollection<Document> suggestions;

	private final MongoCollection<Document> reactionRoles;
	
	private final MongoCollection<Document> giveaways;

	private final MongoCollection<Document> redirects;
	
	private final MongoCollection<Document> auction;
	
	private final MongoCollection<Document> regexTemplates;
	private final MongoCollection<Document> regexes;
	
	private final MongoCollection<Document> modLogs;
	private final MongoCollection<Document> commands;
	private final MongoCollection<Document> messages;

	private final MongoCollection<Document> youtubeNotifications;
	private final MongoCollection<Document> youtubeSubscriptions;
	private final MongoCollection<Document> youtubeNotificationLogs;
	
	private final MongoCollection<Document> offences;
	
	public Database(String databaseName) {
		DatabaseHandler handler = new DatabaseHandler();

		MongoClientSettings settings = MongoClientSettings.builder()
			.addCommandListener(handler)
			.applyToClusterSettings(clusterSettings -> clusterSettings.addClusterListener(handler))
			.build();

		IndexOptions uniqueIndex = new IndexOptions().unique(true);
		
		this.client = MongoClients.create(settings);
		this.database = this.client.getDatabase(databaseName);
		
		this.users = this.database.getCollection("users");

		this.guilds = this.database.getCollection("guilds");

		Bson guildId = Indexes.descending("guildId"), userId = Indexes.descending("userId");

		this.channels = this.database.getCollection("channels");
		this.channels.createIndex(Indexes.descending("guildId"));

		this.members = this.database.getCollection("members");
		this.members.createIndex(Indexes.compoundIndex(guildId, userId), uniqueIndex);
		this.members.createIndex(guildId);
		this.members.createIndex(userId);

		this.triggers = this.database.getCollection("triggers");
		this.triggers.createIndex(Indexes.descending("trigger", "guildId"), uniqueIndex);
		this.triggers.createIndex(guildId);

		this.templates = this.database.getCollection("templates");
		this.templates.createIndex(Indexes.descending("template", "guildId"), uniqueIndex);
		this.templates.createIndex(guildId);

		this.starboards = this.database.getCollection("starboards");
		this.starboards.createIndex(guildId);
		this.starboards.createIndex(Indexes.descending("messageId"));
		this.starboards.createIndex(Indexes.descending("originalMessageId"));

		this.stars = this.database.getCollection("stars");
		this.stars.createIndex(Indexes.descending("messageId", "userId"), uniqueIndex);
		this.stars.createIndex(userId);

		this.reminders = this.database.getCollection("reminders");
		this.reminders.createIndex(Indexes.descending("userId"));

		this.marriages = this.database.getCollection("marriages");
		this.marriages.createIndex(Indexes.descending("proposerId"));
		this.marriages.createIndex(Indexes.descending("partnerId"));

		this.suggestions = this.database.getCollection("suggestions");
		this.suggestions.createIndex(Indexes.descending("guildId"));
		this.suggestions.createIndex(Indexes.descending("messageId"));

		this.reactionRoles = this.database.getCollection("reactionRoles");
		this.reactionRoles.createIndex(Indexes.descending("guildId"));
		this.reactionRoles.createIndex(Indexes.descending("messageId"));
		
		this.giveaways = this.database.getCollection("giveaways");
		this.giveaways.createIndex(Indexes.descending("guildId"));
		this.giveaways.createIndex(Indexes.descending("channelId"));
		this.giveaways.createIndex(Indexes.descending("winners"));

		this.redirects = this.database.getCollection("redirects");
		this.redirects.createIndex(Indexes.descending("url"));
		
		this.auction = this.database.getCollection("auction");
		this.auction.createIndex(Indexes.descending("item.name"));
		this.auction.createIndex(Indexes.descending("ownerId"));
		
		this.regexTemplates = this.database.getCollection("regexTemplates");
		this.regexTemplates.createIndex(Indexes.descending("approved"));
		this.regexTemplates.createIndex(Indexes.descending("pattern"));

		this.regexes = this.database.getCollection("regexes");
		this.regexes.createIndex(guildId);
		this.regexes.createIndex(Indexes.descending("regexId", "guildId"), uniqueIndex);
		
		this.modLogs = this.database.getCollection("modLogs");
		this.modLogs.createIndex(Indexes.descending("action.type"));
		this.modLogs.createIndex(Indexes.descending("guildId"));
		this.modLogs.createIndex(Indexes.descending("moderatorId"));
		this.modLogs.createIndex(Indexes.descending("targetId"));
		
		this.commands = this.database.getCollection("commands");
		this.commands.createIndex(Indexes.descending("authorId"));
		this.commands.createIndex(Indexes.descending("guildId"));
		this.commands.createIndex(Indexes.descending("command.id"));
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

	public MongoCollection<Document> getMarriages() {
		return this.marriages;
	}

	public FindIterable<Document> getMarriages(Bson filter, Bson projection) {
		return this.marriages.find(filter).projection(projection);
	}

	public Document getMarriage(Bson filter, Bson projection) {
		return this.getMarriages(filter, projection).first();
	}

	public long countMarriages(Bson filter) {
		return this.marriages.countDocuments(filter);
	}

	public CompletableFuture<UpdateResult> updateMarriage(Bson filter, Bson update) {
		return CompletableFuture.supplyAsync(() -> this.marriages.updateOne(filter, update, this.updateOptions));
	}

	public CompletableFuture<DeleteResult> deleteMarriage(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.marriages.deleteOne(filter));
	}

	public CompletableFuture<DeleteResult> deleteManyMarriages(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.marriages.deleteMany(filter));
	}

	public MongoCollection<Document> getTemplates() {
		return this.templates;
	}

	public FindIterable<Document> getTemplates(Bson filter, Bson projection) {
		return this.templates.find(filter).projection(projection);
	}

	public Document getTemplate(Bson filter, Bson projection) {
		return this.getTemplates(filter, projection).first();
	}

	public CompletableFuture<InsertOneResult> insertTemplate(Document data) {
		return CompletableFuture.supplyAsync(() -> this.templates.insertOne(data));
	}

	public CompletableFuture<DeleteResult> deleteTemplateById(ObjectId id) {
		return CompletableFuture.supplyAsync(() -> this.templates.deleteOne(Filters.eq("_id", id)));
	}

	public CompletableFuture<DeleteResult> deleteManyTemplates(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.templates.deleteMany(filter));
	}

	public MongoCollection<Document> getReactionRoles() {
		return this.reactionRoles;
	}

	public FindIterable<Document> getReactionRoles(Bson filter, Bson projection) {
		return this.reactionRoles.find(filter).projection(projection);
	}

	public Document getReactionRole(Bson filter, Bson projection) {
		return this.getReactionRoles(filter, projection).first();
	}

	public CompletableFuture<UpdateResult> updateReactionRole(Bson filter, List<Bson> update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.reactionRoles.updateOne(filter, update, options));
	}

	public CompletableFuture<UpdateResult> updateReactionRole(Bson filter, List<Bson> update) {
		return this.updateReactionRole(filter, update, this.updateOptions);
	}

	public CompletableFuture<Document> findAndUpdateReactionRole(Bson filter, List<Bson> update, FindOneAndUpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.reactionRoles.findOneAndUpdate(filter, update, options));
	}

	public CompletableFuture<UpdateResult> updateReactionRole(Bson filter, Bson update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.reactionRoles.updateOne(filter, update, options));
	}

	public CompletableFuture<UpdateResult> updateReactionRole(Bson filter, Bson update) {
		return this.updateReactionRole(filter, update, this.updateOptions);
	}

	public CompletableFuture<UpdateResult> updateManyReactionRoles(Bson filter, List<Bson> update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.reactionRoles.updateMany(filter, update, options));
	}

	public CompletableFuture<Document> findAndUpdateReactionRole(Bson filter, Bson update, FindOneAndUpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.reactionRoles.findOneAndUpdate(filter, update, options));
	}

	public CompletableFuture<UpdateResult> updateManyReactionRoles(Bson filter, Bson update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.reactionRoles.updateMany(filter, update, options));
	}

	public CompletableFuture<UpdateResult> updateManyReactionRoles(Bson filter, Bson update) {
		return this.updateManyReactionRoles(filter, update, this.updateOptions);
	}

	public CompletableFuture<DeleteResult> deleteReactionRole(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.reactionRoles.deleteOne(filter));
	}

	public CompletableFuture<DeleteResult> deleteManyReactionRoles(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.reactionRoles.deleteMany(filter));
	}

	public MongoCollection<Document> getRegexes() {
		return this.regexes;
	}

	public FindIterable<Document> getRegexes(Bson filter, Bson projection) {
		return this.regexes.find(filter).projection(projection);
	}

	public long countRegexes(Bson filter, CountOptions options) {
		return this.regexes.countDocuments(filter, options);
	}

	public Document getRegex(Bson filter, Bson projection) {
		return this.getRegexes(filter, projection).first();
	}

	public Document getRegexById(long guildId, ObjectId regexId, Bson projection) {
		return this.getRegex(Filters.and(Filters.eq("guildId", guildId), Filters.eq("regexId", regexId)), projection);
	}

	public CompletableFuture<InsertOneResult> insertRegex(Document data) {
		return CompletableFuture.supplyAsync(() -> this.regexes.insertOne(data));
	}

	public CompletableFuture<UpdateResult> updateRegex(Bson filter, Bson update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.regexes.updateOne(filter, update, options));
	}

	public CompletableFuture<UpdateResult> updateRegex(Bson filter, Bson update) {
		return this.updateRegex(filter, update, this.updateOptions);
	}

	public CompletableFuture<UpdateResult> updateRegexById(long guildId, ObjectId regexId, Bson update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.regexes.updateOne(Filters.and(Filters.eq("guildId", guildId), Filters.eq("regexId", regexId)), update, options));
	}

	public CompletableFuture<UpdateResult> updateRegexById(long guildId, ObjectId regexId, Bson update) {
		return this.updateRegexById(guildId, regexId, update, this.updateOptions);
	}

	public CompletableFuture<UpdateResult> updateRegex(Bson filter, List<Bson> update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.regexes.updateOne(filter, update, options));
	}

	public CompletableFuture<UpdateResult> updateRegex(Bson filter, List<Bson> update) {
		return this.updateRegex(filter, update, this.updateOptions);
	}

	public CompletableFuture<UpdateResult> updateRegexById(long guildId, ObjectId regexId, List<Bson> update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.regexes.updateOne(Filters.and(Filters.eq("guildId", guildId), Filters.eq("regexId", regexId)), update, options));
	}

	public CompletableFuture<UpdateResult> updateRegexById(long guildId, ObjectId regexId, List<Bson> update) {
		return this.updateRegexById(guildId, regexId, update, this.updateOptions);
	}

	public CompletableFuture<DeleteResult> deleteRegex(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.regexes.deleteOne(filter));
	}

	public CompletableFuture<DeleteResult> deleteRegexById(long guildId, ObjectId regexId) {
		return this.deleteRegex(Filters.and(Filters.eq("guildId", guildId), Filters.eq("regexId", regexId)));
	}

	public MongoCollection<Document> getTriggers() {
		return this.triggers;
	}

	public FindIterable<Document> getTriggers(Bson filter, Bson projection) {
		return this.triggers.find(filter).projection(projection);
	}

	public Document getTrigger(Bson filter, Bson projection) {
		return this.getTriggers(filter, projection).first();
	}

	public Document getTriggerById(ObjectId id, Bson projection) {
		return this.getTrigger(Filters.eq("_id", id), projection);
	}

	public CompletableFuture<InsertOneResult> insertTrigger(Document data) {
		return CompletableFuture.supplyAsync(() -> this.triggers.insertOne(data));
	}

	public CompletableFuture<UpdateResult> updateTrigger(Bson filter, Bson update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.triggers.updateOne(filter, update, options));
	}

	public CompletableFuture<UpdateResult> updateTrigger(Bson filter, Bson update) {
		return this.updateTrigger(filter, update, this.updateOptions);
	}

	public CompletableFuture<UpdateResult> updateTriggerById(Object id, Bson update, UpdateOptions options) {
		return this.updateTrigger(Filters.eq("_id", id), update, options);
	}

	public CompletableFuture<UpdateResult> updateTriggerById(ObjectId id, Bson update) {
		return this.updateTriggerById(id, update, this.updateOptions);
	}

	public CompletableFuture<UpdateResult> updateTrigger(Bson filter, List<Bson> update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.triggers.updateOne(filter, update, options));
	}

	public CompletableFuture<UpdateResult> updateTrigger(Bson filter, List<Bson> update) {
		return this.updateTrigger(filter, update, this.updateOptions);
	}

	public CompletableFuture<UpdateResult> updateTriggerById(Object id, List<Bson> update, UpdateOptions options) {
		return this.updateTrigger(Filters.eq("_id", id), update, options);
	}

	public CompletableFuture<UpdateResult> updateTriggerById(ObjectId id, List<Bson> update) {
		return this.updateTriggerById(id, update, this.updateOptions);
	}

	public CompletableFuture<UpdateResult> updateManyTriggers(Bson filter, List<Bson> update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.triggers.updateMany(filter, update, options));
	}

	public CompletableFuture<UpdateResult> updateManyTriggers(Bson filter, List<Bson> update) {
		return CompletableFuture.supplyAsync(() -> this.triggers.updateMany(filter, update, this.updateOptions));
	}

	public CompletableFuture<Document> findAndUpdateTrigger(Bson filter, List<Bson> update, FindOneAndUpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.triggers.findOneAndUpdate(filter, update, options));
	}

	public CompletableFuture<Document> findAndUpdateTriggerById(ObjectId id, List<Bson> update, FindOneAndUpdateOptions options) {
		return this.findAndUpdateTrigger(Filters.eq("_id", id), update, options);
	}

	public CompletableFuture<DeleteResult> deleteTrigger(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.triggers.deleteOne(filter));
	}

	public CompletableFuture<DeleteResult> deleteTriggerById(ObjectId id) {
		return this.deleteTrigger(Filters.eq("_id", id));
	}

	public CompletableFuture<DeleteResult> deleteManyTriggers(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.triggers.deleteMany(filter));
	}

	public CompletableFuture<BulkWriteResult> bulkWriteTriggers(List<WriteModel<Document>> bulkData) {
		return CompletableFuture.supplyAsync(() -> this.triggers.bulkWrite(bulkData));
	}

	public MongoCollection<Document> getStars() {
		return this.stars;
	}

	public FindIterable<Document> getStars(Bson filter, Bson projection) {
		return this.stars.find(filter).projection(projection);
	}

	public Document getStar(Bson filter, Bson projection) {
		return this.getStars(filter, projection).first();
	}

	public Document getStarById(long userId, long messageId, Bson projection) {
		return this.getStar(Filters.and(Filters.eq("userId", userId), Filters.eq("messageId", messageId)), projection);
	}

	public CompletableFuture<InsertOneResult> insertStar(Document data) {
		return CompletableFuture.supplyAsync(() -> this.stars.insertOne(data));
	}

	public CompletableFuture<DeleteResult> deleteStar(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.stars.deleteOne(filter));
	}

	public CompletableFuture<DeleteResult> deleteStarById(long userId, long messageId) {
		return this.deleteStar(Filters.and(Filters.eq("userId", userId), Filters.eq("messageId", messageId)));
	}

	public CompletableFuture<DeleteResult> deleteManyStars(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.stars.deleteMany(filter));
	}

	public MongoCollection<Document> getStarboards() {
		return this.starboards;
	}

	public FindIterable<Document> getStarboards(Bson filter, Bson projection) {
		return this.starboards.find(filter).projection(projection);
	}

	public Document getStarboard(Bson filter, Bson projection) {
		return this.getStarboards(filter, projection).first();
	}

	public Document getStarboardById(ObjectId id, Bson projection) {
		return this.getStarboard(Filters.eq("_id", id), projection);
	}

	public CompletableFuture<InsertOneResult> insertStarboard(Document data) {
		return CompletableFuture.supplyAsync(() -> this.starboards.insertOne(data));
	}

	public CompletableFuture<UpdateResult> updateStarboard(Bson filter, Bson update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.starboards.updateOne(filter, update, options));
	}

	public CompletableFuture<UpdateResult> updateStarboard(Bson filter, Bson update) {
		return this.updateStarboard(filter, update, this.updateOptions);
	}

	public CompletableFuture<UpdateResult> updateStarboardById(ObjectId id, Bson update, UpdateOptions options) {
		return this.updateStarboard(Filters.eq("_id", id), update, options);
	}

	public CompletableFuture<UpdateResult> updateStarboardById(ObjectId id, Bson update) {
		return this.updateStarboardById(id, update, this.updateOptions);
	}

	public CompletableFuture<UpdateResult> updateStarboardById(ObjectId id, List<Bson> update) {
		return this.updateStarboardById(id, update, this.updateOptions);
	}

	public CompletableFuture<UpdateResult> updateStarboard(Bson filter, List<Bson> update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.starboards.updateOne(filter, update, options));
	}

	public CompletableFuture<UpdateResult> updateStarboard(Bson filter, List<Bson> update) {
		return this.updateStarboard(filter, update, this.updateOptions);
	}

	public CompletableFuture<UpdateResult> updateStarboardById(ObjectId id, List<Bson> update, UpdateOptions options) {
		return this.updateStarboard(Filters.eq("_id", id), update, options);
	}

	public CompletableFuture<Document> findAndUpdateStarboard(Bson filter, Bson update, FindOneAndUpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.starboards.findOneAndUpdate(filter, update, options));
	}

	public CompletableFuture<Document> findAndUpdateStarboard(Bson filter, Bson update) {
		return this.findAndUpdateStarboard(filter, update, this.findOneAndUpdateOptions);
	}

	public CompletableFuture<Document> findAndUpdateStarboardById(ObjectId id, Bson update, FindOneAndUpdateOptions options) {
		return this.findAndUpdateStarboard(Filters.eq("_id", id), update, options);
	}

	public CompletableFuture<Document> findAndUpdateStarboardById(ObjectId id, Bson update) {
		return this.findAndUpdateStarboardById(id, update, this.findOneAndUpdateOptions);
	}

	public CompletableFuture<Document> findAndUpdateStarboardById(ObjectId id, List<Bson> update) {
		return this.findAndUpdateStarboardById(id, update, this.findOneAndUpdateOptions);
	}

	public CompletableFuture<Document> findAndUpdateStarboard(Bson filter, List<Bson> update, FindOneAndUpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.starboards.findOneAndUpdate(filter, update, options));
	}

	public CompletableFuture<Document> findAndUpdateStarboard(Bson filter, List<Bson> update) {
		return this.findAndUpdateStarboard(filter, update, this.findOneAndUpdateOptions);
	}

	public CompletableFuture<Document> findAndUpdateStarboardById(ObjectId id, List<Bson> update, FindOneAndUpdateOptions options) {
		return this.findAndUpdateStarboard(Filters.eq("_id", id), update, options);
	}

	public CompletableFuture<DeleteResult> deleteStarboard(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.starboards.deleteOne(filter));
	}

	public CompletableFuture<DeleteResult> deleteStarboardById(ObjectId id) {
		return this.deleteStarboard(Filters.eq("_id", id));
	}

	public CompletableFuture<Document> findAndDeleteStarboard(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.starboards.findOneAndDelete(filter));
	}

	public CompletableFuture<Document> findAndDeleteStarboardById(ObjectId id) {
		return this.findAndDeleteStarboard(Filters.eq("_id", id));
	}

	public CompletableFuture<DeleteResult> deleteManyStarboards(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.starboards.deleteMany(filter));
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

	public Document getSuggestionById(ObjectId id, Bson projection) {
		return this.getSuggestion(Filters.eq("_id", id), projection);
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

	public CompletableFuture<Document> findAndUpdateSuggestionById(ObjectId id, Bson update, FindOneAndUpdateOptions options) {
		return this.findAndUpdateSuggestion(Filters.eq("_id", id), update, options);
	}

	public CompletableFuture<Document> findAndUpdateSuggestionById(ObjectId id, Bson update) {
		return this.findAndUpdateSuggestionById(id, update, this.findOneAndUpdateOptions);
	}

	public CompletableFuture<DeleteResult> deleteSuggestion(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.suggestions.deleteOne(filter));
	}

	public CompletableFuture<Document> findAndDeleteSuggestion(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.suggestions.findOneAndDelete(filter));
	}

	public CompletableFuture<Document> findAndDeleteSuggestionById(ObjectId id) {
		return this.findAndDeleteSuggestion(Filters.eq("_id", id));
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
	
	public MongoCollection<Document> getRegexTemplates() {
		return this.regexTemplates;
	}
	
	public FindIterable<Document> getRegexTemplates(Bson filter, Bson projection) {
		return this.regexTemplates.find(filter).projection(projection);
	}
	
	public Document getRegexTemplate(Bson filter, Bson projection) {
		return this.regexTemplates.find(filter).projection(projection).first();
	}
	
	public Document getRegexTemplateById(ObjectId id, Bson projection) {
		return this.getRegexTemplate(Filters.eq("_id", id), projection);
	}
	
	public CompletableFuture<InsertOneResult> insertRegexTemplate(Document data) {
		return CompletableFuture.supplyAsync(() -> this.regexTemplates.insertOne(data));
	}
	
	public CompletableFuture<UpdateResult> updateRegexTemplateById(ObjectId id, Bson update) {
		return CompletableFuture.supplyAsync(() -> this.regexTemplates.updateOne(Filters.eq("_id", id), update));
	}
	
	public CompletableFuture<Document> findAndUpdateRegexTemplateById(ObjectId id, Bson update, FindOneAndUpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.regexTemplates.findOneAndUpdate(Filters.eq("_id", id), update, options));
	}
	
	public CompletableFuture<DeleteResult> deleteRegexTemplateById(ObjectId id) {
		return CompletableFuture.supplyAsync(() -> this.regexTemplates.deleteOne(Filters.eq("_id", id)));
	}
	
	public CompletableFuture<Document> findAndDeleteRegexTemplateById(ObjectId id, FindOneAndDeleteOptions options) {
		return CompletableFuture.supplyAsync(() -> this.regexTemplates.findOneAndDelete(Filters.eq("_id", id), options));
	}

	public MongoCollection<Document> getMembers() {
		return this.members;
	}

	public FindIterable<Document> getMembers(Bson filter, Bson projection) {
		return this.members.find(filter).projection(projection);
	}

	public Document getMember(Bson filter, Bson projection) {
		Document data = this.getMembers(filter, projection).first();

		return data == null ? Database.EMPTY_DOCUMENT : data;
	}

	public Document getMemberById(long userId, long guildId, Bson projection) {
		return this.getMember(Filters.and(Filters.eq("userId", userId), Filters.eq("guildId", guildId)), projection);
	}

	public CompletableFuture<UpdateResult> updateMember(Bson filter, List<? extends Bson> update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.members.updateOne(filter, update, options));
	}

	public CompletableFuture<UpdateResult> updateMemberById(long userId, long guildId, List<? extends Bson> update, UpdateOptions options) {
		return this.updateMember(Filters.and(Filters.eq("userId", userId), Filters.eq("guildId", guildId)), update, options);
	}

	public CompletableFuture<UpdateResult> updateMemberById(long userId, long guildId, List<? extends Bson> update) {
		return this.updateMemberById(userId, guildId, update, this.updateOptions);
	}

	public CompletableFuture<UpdateResult> updateMember(Bson filter, Bson update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.members.updateOne(filter, update, options));
	}

	public CompletableFuture<UpdateResult> updateMemberById(long userId, long guildId, Bson update, UpdateOptions options) {
		return this.updateMember(Filters.and(Filters.eq("userId", userId), Filters.eq("guildId", guildId)), update, options);
	}

	public CompletableFuture<UpdateResult> updateMemberById(long userId, long guildId, Bson update) {
		return this.updateMemberById(userId, guildId, update, this.updateOptions);
	}

	public CompletableFuture<UpdateResult> updateMember(UpdateOneModel<Document> update) {
		return this.updateMember(update.getFilter(), update.getUpdate(), update.getOptions());
	}

	public CompletableFuture<Document> findAndUpdateMember(Bson filter, Bson update, FindOneAndUpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.members.findOneAndUpdate(filter, update, options));
	}

	public CompletableFuture<Document> findAndUpdateMemberById(long userId, long guildId, Bson update, FindOneAndUpdateOptions options) {
		return this.findAndUpdateMember(Filters.and(Filters.eq("userId", userId), Filters.eq("guildId", guildId)), update, options);
	}

	public CompletableFuture<Document> findAndUpdateMemberById(long userId, long guildId, Bson update) {
		return this.findAndUpdateMemberById(userId, guildId, update, this.findOneAndUpdateOptions);
	}

	public CompletableFuture<Document> findAndUpdateMember(Bson filter, List<Bson> update, FindOneAndUpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.members.findOneAndUpdate(filter, update, options));
	}

	public CompletableFuture<Document> findAndUpdateMemberById(long userId, long guildId, List<Bson> update, FindOneAndUpdateOptions options) {
		return this.findAndUpdateMember(Filters.and(Filters.eq("userId", userId), Filters.eq("guildId", guildId)), update, options);
	}

	public CompletableFuture<Document> findAndUpdateMemberById(long userId, long guildId, List<Bson> update) {
		return this.findAndUpdateMemberById(userId, guildId, update, this.findOneAndUpdateOptions);
	}

	public CompletableFuture<BulkWriteResult> bulkWriteMembers(List<? extends WriteModel<? extends Document>> bulkData) {
		return CompletableFuture.supplyAsync(() -> this.members.bulkWrite(bulkData));
	}

	public MongoCollection<Document> getChannels() {
		return this.channels;
	}

	public FindIterable<Document> getChannels(Bson filter, Bson projection) {
		return this.channels.find(filter).projection(projection);
	}

	public Document getChannel(Bson filter, Bson projection) {
		Document data = this.getChannels(filter, projection).first();

		return data == null ? Database.EMPTY_DOCUMENT : data;
	}

	public Document getChannelById(long channelId, Bson projection) {
		return this.getChannel(Filters.eq("_id", channelId), projection);
	}

	public CompletableFuture<UpdateResult> updateChannel(Bson filter, List<? extends Bson> update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.channels.updateOne(filter, update, options));
	}

	public CompletableFuture<UpdateResult> updateChannelById(long channelId, List<? extends Bson> update, UpdateOptions options) {
		return this.updateChannel(Filters.eq("_id", channelId), update, options);
	}

	public CompletableFuture<UpdateResult> updateChannelById(long channelId, List<? extends Bson> update) {
		return this.updateChannelById(channelId, update, this.updateOptions);
	}

	public CompletableFuture<UpdateResult> updateChannel(Bson filter, Bson update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.channels.updateOne(filter, update, options));
	}

	public CompletableFuture<UpdateResult> updateChannelById(long channelId, Bson update, UpdateOptions options) {
		return this.updateChannel(Filters.eq("_id", channelId), update, options);
	}

	public CompletableFuture<UpdateResult> updateChannelById(long channelId, Bson update) {
		return this.updateChannelById(channelId, update, this.updateOptions);
	}

	public CompletableFuture<UpdateResult> updateChannel(UpdateOneModel<Document> update) {
		return this.updateChannel(update.getFilter(), update.getUpdate(), update.getOptions());
	}

	public CompletableFuture<Document> findAndUpdateChannel(Bson filter, Bson update, FindOneAndUpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.channels.findOneAndUpdate(filter, update, options));
	}

	public CompletableFuture<Document> findAndUpdateChannelById(long channelId, Bson update, FindOneAndUpdateOptions options) {
		return this.findAndUpdateChannel(Filters.eq("_id", channelId), update, options);
	}

	public CompletableFuture<Document> findAndUpdateChannelById(long channelId, Bson update) {
		return this.findAndUpdateChannelById(channelId, update, this.findOneAndUpdateOptions);
	}

	public CompletableFuture<Document> findAndUpdateChannel(Bson filter, List<Bson> update, FindOneAndUpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.channels.findOneAndUpdate(filter, update, options));
	}

	public CompletableFuture<Document> findAndUpdateChannelById(long channelId, List<Bson> update, FindOneAndUpdateOptions options) {
		return this.findAndUpdateChannel(Filters.eq("_id", channelId), update, options);
	}

	public CompletableFuture<Document> findAndUpdateChannelById(long channelId, List<Bson> update) {
		return this.findAndUpdateChannelById(channelId, update, this.findOneAndUpdateOptions);
	}

	public CompletableFuture<UpdateResult> updateManyChannels(Bson filter, List<Bson> update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.channels.updateMany(filter, update, options));
	}

	public CompletableFuture<UpdateResult> updateManyChannels(Bson filter, List<Bson> update) {
		return this.updateManyChannels(filter, update, this.updateOptions);
	}

	public CompletableFuture<BulkWriteResult> bulkWriteChannels(List<? extends WriteModel<? extends Document>> bulkData) {
		return CompletableFuture.supplyAsync(() -> this.channels.bulkWrite(bulkData));
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
	
	public Document getUserById(Bson filter, Bson projection) {
		Document data = this.getUsers(filter, projection).first();

		return data == null ? Database.EMPTY_DOCUMENT : data;
	}
	
	public Document getUserById(long userId, Bson projection) {
		return this.getUserById(Filters.eq("_id", userId), projection);
	}

	public CompletableFuture<UpdateResult> updateUser(Bson filter, List<? extends Bson> update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.users.updateOne(filter, update, options));
	}

	public CompletableFuture<UpdateResult> updateUserById(long userId, List<? extends Bson> update, UpdateOptions options) {
		return this.updateUser(Filters.eq("_id", userId), update, options);
	}

	public CompletableFuture<UpdateResult> updateUserById(long userId, List<? extends Bson> update) {
		return this.updateUserById(userId, update, this.updateOptions);
	}

	public CompletableFuture<UpdateResult> updateUser(Bson filter, Bson update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.users.updateOne(filter, update, options));
	}
	
	public CompletableFuture<UpdateResult> updateUserById(long userId, Bson update, UpdateOptions options) {
		return this.updateUser(Filters.eq("_id", userId), update, options);
	}
	
	public CompletableFuture<UpdateResult> updateUserById(long userId, Bson update) {
		return this.updateUserById(userId, update, this.updateOptions);
	}
	
	public CompletableFuture<UpdateResult> updateUser(UpdateOneModel<Document> update) {
		return this.updateUser(update.getFilter(), update.getUpdate(), update.getOptions());
	}
	
	public CompletableFuture<Document> findAndUpdateUser(Bson filter, Bson update, FindOneAndUpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.users.findOneAndUpdate(filter, update, options));
	}

	public CompletableFuture<Document> findAndUpdateUserById(long userId, Bson update, FindOneAndUpdateOptions options) {
		return this.findAndUpdateUser(Filters.eq("_id", userId), update, options);
	}
	
	public CompletableFuture<Document> findAndUpdateUserById(long userId, Bson update) {
		return this.findAndUpdateUserById(userId, update, this.findOneAndUpdateOptions);
	}

	public CompletableFuture<Document> findAndUpdateUser(Bson filter, List<Bson> update, FindOneAndUpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.users.findOneAndUpdate(filter, update, options));
	}

	public CompletableFuture<Document> findAndUpdateUserById(long userId, List<Bson> update, FindOneAndUpdateOptions options) {
		return this.findAndUpdateUser(Filters.eq("_id", userId), update, options);
	}

	public CompletableFuture<Document> findAndUpdateUserById(long userId, List<Bson> update) {
		return this.findAndUpdateUserById(userId, update, this.findOneAndUpdateOptions);
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
		return this.getYouTubeNotificationLogs(filter, projection).first();
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

	public CompletableFuture<InsertOneResult> insertCommand(Document data) {
		return CompletableFuture.supplyAsync(() -> this.commands.insertOne(data));
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

	public static <Type> BiConsumer<Type, Throwable> exceptionally(ShardManager manager) {
		return ($, exception) -> ExceptionUtility.sendErrorMessage(manager, exception);
	}

	public static <Type> BiConsumer<Type, Throwable> exceptionally(CommandEvent event) {
		return ($, exception) -> ExceptionUtility.sendExceptionally(event, exception);
	}
	
}
