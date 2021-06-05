package com.sx4.bot.database.mongo;

import com.jockie.bot.core.command.impl.CommandEvent;
import com.mongodb.MongoClientSettings;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.*;
import com.mongodb.client.model.*;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertManyResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import com.sx4.bot.handlers.MongoDatabaseHandler;
import com.sx4.bot.utility.ExceptionUtility;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;
import org.bson.types.ObjectId;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class MongoDatabase {
	
	public static final Document EMPTY_DOCUMENT = new Document();
	public static final JsonWriterSettings PRETTY_JSON = JsonWriterSettings.builder().indent(true).indentCharacters("    ").outputMode(JsonMode.RELAXED).build();

	private final ExecutorService executor = Executors.newCachedThreadPool();
	
	private final UpdateOptions updateOptions = new UpdateOptions().upsert(true);
	private final FindOneAndUpdateOptions findOneAndUpdateOptions = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER).upsert(true);
	
	private final MongoClient client;
	private final com.mongodb.client.MongoDatabase database;
	
	private final MongoCollection<Document> guilds;
	private final MongoCollection<Document> users;

	private final MongoCollection<Document> items;

	private final MongoCollection<Document> blacklists;

	private final MongoCollection<Document> warnings;
	private final MongoCollection<Document> mutes;
	private final MongoCollection<Document> temporaryBans;

	private final MongoCollection<Document> triggers;

	private final MongoCollection<Document> templates;

	private final MongoCollection<Document> starboards;
	private final MongoCollection<Document> stars;

	private final MongoCollection<Document> mediaChannels;

	private final MongoCollection<Document> serverStats;

	private final MongoCollection<Document> loggers;

	private final MongoCollection<Document> games;

	private final MongoCollection<Document> marriages;

	private final MongoCollection<Document> reminders;

	private final MongoCollection<Document> suggestions;

	private final MongoCollection<Document> reactionRoles;

	private final MongoCollection<Document> selfRoles;
	private final MongoCollection<Document> autoRoles;
	
	private final MongoCollection<Document> giveaways;

	private final MongoCollection<Document> redirects;
	
	private final MongoCollection<Document> auction;

	private final MongoCollection<Document> regexAttempts;
	private final MongoCollection<Document> regexTemplates;
	private final MongoCollection<Document> regexes;
	
	private final MongoCollection<Document> modLogs;
	private final MongoCollection<Document> commands;
	private final MongoCollection<Document> messages;

	private final MongoCollection<Document> youtubeNotifications;
	private final MongoCollection<Document> youtubeSubscriptions;
	private final MongoCollection<Document> youtubeNotificationLogs;
	
	private final MongoCollection<Document> offences;
	
	public MongoDatabase(String databaseName) {
		MongoDatabaseHandler handler = new MongoDatabaseHandler();

		MongoClientSettings settings = MongoClientSettings.builder()
			.addCommandListener(handler)
			.applyToClusterSettings(clusterSettings -> clusterSettings.addClusterListener(handler))
			.build();

		IndexOptions uniqueIndex = new IndexOptions().unique(true);
		
		this.client = MongoClients.create(settings);
		this.database = this.client.getDatabase(databaseName);
		
		this.users = this.database.getCollection("users");

		this.guilds = this.database.getCollection("guilds");

		this.items = this.database.getCollection("items");
		this.items.createIndex(Indexes.descending("userId", "item.id"));
		this.items.createIndex(Indexes.descending("userId", "item.type"));

		this.blacklists = this.database.getCollection("blacklists");
		this.blacklists.createIndex(Indexes.descending("guildId"));
		this.blacklists.createIndex(Indexes.descending("channelId"), uniqueIndex);

		this.mutes = this.database.getCollection("mutes");
		this.mutes.createIndex(Indexes.descending("userId", "guildId"), uniqueIndex);

		this.temporaryBans = this.database.getCollection("temporaryBans");
		this.temporaryBans.createIndex(Indexes.descending("userId", "guildId"), uniqueIndex);

		this.warnings = this.database.getCollection("warnings");
		this.warnings.createIndex(Indexes.descending("userId", "guildId"), uniqueIndex);

		this.triggers = this.database.getCollection("triggers");
		this.triggers.createIndex(Indexes.descending("trigger", "guildId"), uniqueIndex);

		this.templates = this.database.getCollection("templates");
		this.templates.createIndex(Indexes.descending("template", "guildId"), uniqueIndex);

		this.starboards = this.database.getCollection("starboards");
		this.starboards.createIndex(Indexes.descending("guildId"));
		this.starboards.createIndex(Indexes.descending("messageId"));
		this.starboards.createIndex(Indexes.descending("originalMessageId"));
		this.starboards.createIndex(Indexes.descending("count"));

		this.stars = this.database.getCollection("stars");
		this.stars.createIndex(Indexes.descending("messageId", "userId"), uniqueIndex);

		this.loggers = this.database.getCollection("loggers");
		this.loggers.createIndex(Indexes.descending("guildId"));
		this.loggers.createIndex(Indexes.descending("channelId"), uniqueIndex);

		this.mediaChannels = this.database.getCollection("mediaChannels");
		this.mediaChannels.createIndex(Indexes.descending("guildId"));
		this.mediaChannels.createIndex(Indexes.descending("channelId"), uniqueIndex);

		this.serverStats = this.database.getCollection("guildStats");
		this.serverStats.createIndex(Indexes.descending("guildId"));
		this.serverStats.createIndex(Indexes.descending("time"), new IndexOptions().expireAfter(608400L, TimeUnit.SECONDS));

		this.games = this.database.getCollection("games");
		this.games.createIndex(Indexes.descending("userId", "gameId"), uniqueIndex);
		this.games.createIndex(Indexes.descending("type"));

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
		this.reactionRoles.createIndex(Indexes.descending("messageId", "emote"), uniqueIndex);

		this.selfRoles = this.database.getCollection("selfRoles");
		this.selfRoles.createIndex(Indexes.descending("roleId"), uniqueIndex);
		this.selfRoles.createIndex(Indexes.descending("guildId"));

		this.autoRoles = this.database.getCollection("autoRoles");
		this.autoRoles.createIndex(Indexes.descending("roleId"), uniqueIndex);
		this.autoRoles.createIndex(Indexes.descending("guildId"));
		
		this.giveaways = this.database.getCollection("giveaways");
		this.giveaways.createIndex(Indexes.descending("guildId"));
		this.giveaways.createIndex(Indexes.descending("channelId"));
		this.giveaways.createIndex(Indexes.descending("winners"));
		this.giveaways.createIndex(Indexes.descending("messageId"));

		this.redirects = this.database.getCollection("redirects");
		this.redirects.createIndex(Indexes.descending("url"));
		
		this.auction = this.database.getCollection("auction");
		this.auction.createIndex(Indexes.descending("item.id"));
		this.auction.createIndex(Indexes.descending("expires"));
		this.auction.createIndex(Indexes.descending("ownerId"));
		
		this.regexTemplates = this.database.getCollection("regexTemplates");
		this.regexTemplates.createIndex(Indexes.descending("approved"));
		this.regexTemplates.createIndex(Indexes.descending("pattern"));

		this.regexes = this.database.getCollection("regexes");
		this.regexes.createIndex(Indexes.descending("guildId", "regexId"));

		this.regexAttempts = this.database.getCollection("regexAttempts");
		this.regexAttempts.createIndex(Indexes.descending("userId", "regexId"), uniqueIndex);
		this.regexAttempts.createIndex(Indexes.descending("guildId"));
		
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
		this.youtubeNotifications.createIndex(Indexes.descending("guildId"));

		this.youtubeSubscriptions = this.database.getCollection("youtubeSubscriptions");
		this.youtubeNotificationLogs = this.database.getCollection("youtubeNotificationLogs");
		
		this.offences = this.database.getCollection("offences");
		this.offences.createIndex(Indexes.descending("authorId"));
		this.offences.createIndex(Indexes.descending("guildId"));
	}
	
	public MongoClient getClient() {
		return this.client;
	}
	
	public com.mongodb.client.MongoDatabase getDatabase() {
		return this.database;
	}

	public CompletableFuture<Boolean> withTransaction(Consumer<ClientSession> consumer) {
		return CompletableFuture.supplyAsync(() -> {
			try (ClientSession session = this.client.startSession()) {
				session.startTransaction();
				consumer.accept(session);
				if (session.hasActiveTransaction()) {
					session.commitTransaction();
					return true;
				}

				return false;
			}
		}, this.executor);
	}

	public <Type> CompletableFuture<Type> withTransaction(Function<ClientSession, Type> function) {
		return CompletableFuture.supplyAsync(() -> {
			try (ClientSession session = this.client.startSession()) {
				session.startTransaction();
				Type value = function.apply(session);
				if (session.hasActiveTransaction()) {
					session.commitTransaction();
				}

				return value;
			}
		}, this.executor);
	}

	public MongoCollection<Document> getItems() {
		return this.items;
	}

	public FindIterable<Document> getItems(Bson filter, Bson projection) {
		return this.items.find(filter).projection(projection);
	}

	public Document getItem(Bson filter, Bson projection) {
		return this.getItems(filter, projection).first();
	}

	public CompletableFuture<AggregateIterable<Document>> aggregateItems(List<Bson> pipeline) {
		return CompletableFuture.supplyAsync(() -> this.items.aggregate(pipeline), this.executor);
	}

	public CompletableFuture<InsertOneResult> insertItem(Document data) {
		return CompletableFuture.supplyAsync(() -> this.items.insertOne(data));
	}

	public CompletableFuture<UpdateResult> updateItem(Bson filter, Bson update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.items.updateOne(filter, update, options), this.executor);
	}

	public CompletableFuture<UpdateResult> updateItem(Bson filter, List<Bson> update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.items.updateOne(filter, update, options), this.executor);
	}

	public CompletableFuture<DeleteResult> deleteItem(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.items.deleteOne(filter), this.executor);
	}

	public CompletableFuture<BulkWriteResult> bulkWriteItems(List<WriteModel<Document>> bulkData) {
		return CompletableFuture.supplyAsync(() -> this.items.bulkWrite(bulkData), this.executor);
	}

	public MongoCollection<Document> getMediaChannels() {
		return this.mediaChannels;
	}

	public FindIterable<Document> getMediaChannels(Bson filter, Bson projection) {
		return this.mediaChannels.find(filter).projection(projection);
	}

	public Document getMediaChannel(Bson filter, Bson projection) {
		return this.getMediaChannels(filter, projection).first();
	}

	public CompletableFuture<InsertOneResult> insertMediaChannel(Document data) {
		return CompletableFuture.supplyAsync(() -> this.mediaChannels.insertOne(data), this.executor);
	}

	public CompletableFuture<UpdateResult> updateMediaChannel(Bson filter, Bson update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.mediaChannels.updateOne(filter, update, options), this.executor);
	}

	public CompletableFuture<UpdateResult> updateMediaChannel(Bson filter, List<Bson> update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.mediaChannels.updateOne(filter, update, options), this.executor);
	}

	public CompletableFuture<DeleteResult> deleteMediaChannel(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.mediaChannels.deleteOne(filter), this.executor);
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

	public CompletableFuture<InsertOneResult> insertMute(Document data) {
		return CompletableFuture.supplyAsync(() -> this.mutes.insertOne(data), this.executor);
	}

	public CompletableFuture<UpdateResult> updateMute(Bson filter, Bson update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.mutes.updateOne(filter, update, options), this.executor);
	}

	public CompletableFuture<UpdateResult> updateMute(Bson filter, List<Bson> update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.mutes.updateOne(filter, update, options), this.executor);
	}

	public CompletableFuture<DeleteResult> deleteMute(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.mutes.deleteOne(filter), this.executor);
	}

	public CompletableFuture<BulkWriteResult> bulkWriteMutes(List<WriteModel<Document>> bulkData) {
		return CompletableFuture.supplyAsync(() -> this.mutes.bulkWrite(bulkData), this.executor);
	}

	public MongoCollection<Document> getWarnings() {
		return this.warnings;
	}

	public FindIterable<Document> getWarnings(Bson filter, Bson projection) {
		return this.warnings.find(filter).projection(projection);
	}

	public Document getWarning(Bson filter, Bson projection) {
		return this.getWarnings(filter, projection).first();
	}

	public CompletableFuture<InsertOneResult> insertWarning(Document data) {
		return CompletableFuture.supplyAsync(() -> this.warnings.insertOne(data), this.executor);
	}

	public CompletableFuture<UpdateResult> updateWarnings(Bson filter, Bson update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.warnings.updateOne(filter, update, options), this.executor);
	}

	public CompletableFuture<UpdateResult> updateWarnings(Bson filter, List<Bson> update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.warnings.updateOne(filter, update, options), this.executor);
	}

	public CompletableFuture<Document> findAndUpdateWarnings(Bson filter, List<Bson> update, FindOneAndUpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.warnings.findOneAndUpdate(filter, update, options), this.executor);
	}

	public CompletableFuture<DeleteResult> deleteWarning(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.warnings.deleteOne(filter), this.executor);
	}

	public CompletableFuture<BulkWriteResult> bulkWriteWarnings(List<WriteModel<Document>> bulkData) {
		return CompletableFuture.supplyAsync(() -> this.warnings.bulkWrite(bulkData), this.executor);
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

	public CompletableFuture<InsertOneResult> insertTemporaryBan(Document data) {
		return CompletableFuture.supplyAsync(() -> this.temporaryBans.insertOne(data), this.executor);
	}

	public CompletableFuture<UpdateResult> updateTemporaryBan(Bson filter, Bson update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.temporaryBans.updateOne(filter, update, options), this.executor);
	}

	public CompletableFuture<UpdateResult> updateTemporaryBan(Bson filter, List<Bson> update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.temporaryBans.updateOne(filter, update, options), this.executor);
	}

	public CompletableFuture<DeleteResult> deleteTemporaryBan(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.temporaryBans.deleteOne(filter), this.executor);
	}

	public CompletableFuture<BulkWriteResult> bulkWriteTemporaryBans(List<WriteModel<Document>> bulkData) {
		return CompletableFuture.supplyAsync(() -> this.temporaryBans.bulkWrite(bulkData), this.executor);
	}


	public MongoCollection<Document> getAutoRoles() {
		return this.autoRoles;
	}

	public FindIterable<Document> getAutoRoles(Bson filter, Bson projection) {
		return this.autoRoles.find(filter).projection(projection);
	}

	public Document getAutoRole(Bson filter, Bson projection) {
		return this.getAutoRoles(filter, projection).first();
	}

	public CompletableFuture<InsertOneResult> insertAutoRole(Document data) {
		return CompletableFuture.supplyAsync(() -> this.autoRoles.insertOne(data), this.executor);
	}

	public CompletableFuture<UpdateResult> updateAutoRole(Bson filter, List<Bson> update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.autoRoles.updateOne(filter, update, options), this.executor);
	}

	public CompletableFuture<UpdateResult> updateAutoRole(Bson filter, Bson update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.autoRoles.updateOne(filter, update, options), this.executor);
	}

	public CompletableFuture<Document> findAndUpdateAutoRole(Bson filter, List<Bson> update, FindOneAndUpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.autoRoles.findOneAndUpdate(filter, update, options), this.executor);
	}

	public CompletableFuture<Document> findAndUpdateAutoRole(Bson filter, Bson update, FindOneAndUpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.autoRoles.findOneAndUpdate(filter, update, options), this.executor);
	}

	public CompletableFuture<DeleteResult> deleteAutoRole(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.autoRoles.deleteOne(filter), this.executor);
	}

	public CompletableFuture<DeleteResult> deleteManyAutoRoles(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.autoRoles.deleteMany(filter), this.executor);
	}

	public MongoCollection<Document> getSelfRoles() {
		return this.selfRoles;
	}

	public FindIterable<Document> getSelfRoles(Bson filter, Bson projection) {
		return this.selfRoles.find(filter).projection(projection);
	}

	public Document getSelfRole(Bson filter, Bson projection) {
		return this.getSelfRoles(filter, projection).first();
	}

	public CompletableFuture<InsertOneResult> insertSelfRole(Document data) {
		return CompletableFuture.supplyAsync(() -> this.selfRoles.insertOne(data), this.executor);
	}

	public CompletableFuture<DeleteResult> deleteSelfRole(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.selfRoles.deleteOne(filter), this.executor);
	}

	public CompletableFuture<DeleteResult> deleteManySelfRoles(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.selfRoles.deleteMany(filter), this.executor);
	}

	public MongoCollection<Document> getServerStats() {
		return this.serverStats;
	}

	public FindIterable<Document> getServerStats(Bson filter, Bson projection) {
		return this.serverStats.find(filter).projection(projection);
	}

	public CompletableFuture<InsertManyResult> insertManyServerStats(List<Document> data) {
		return CompletableFuture.supplyAsync(() -> this.serverStats.insertMany(data), this.executor);
	}

	public MongoCollection<Document> getRegexAttempts() {
		return this.regexAttempts;
	}

	public FindIterable<Document> getRegexAttempts(Bson filter, Bson projection) {
		return this.regexAttempts.find(filter).projection(projection);
	}

	public Document getRegexAttempt(Bson filter, Bson projection) {
		return this.getRegexAttempts(filter, projection).first();
	}

	public CompletableFuture<UpdateResult> updateRegexAttempt(Bson filter, List<Bson> update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.regexAttempts.updateOne(filter, update, options), this.executor);
	}

	public CompletableFuture<UpdateResult> updateRegexAttempt(Bson filter, List<Bson> update) {
		return this.updateRegexAttempt(filter, update, this.updateOptions);
	}

	public CompletableFuture<UpdateResult> updateRegexAttempt(Bson filter, Bson update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.regexAttempts.updateOne(filter, update, options), this.executor);
	}

	public CompletableFuture<UpdateResult> updateRegexAttempt(Bson filter, Bson update) {
		return this.updateRegexAttempt(filter, update, this.updateOptions);
	}

	public CompletableFuture<Document> findAndUpdateRegexAttempt(Bson filter, Bson update, FindOneAndUpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.regexAttempts.findOneAndUpdate(filter, update, options), this.executor);
	}

	public CompletableFuture<Document> findAndUpdateRegexAttempt(Bson filter, List<Bson> update, FindOneAndUpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.regexAttempts.findOneAndUpdate(filter, update, options), this.executor);
	}

	public CompletableFuture<UpdateResult> updateRegexAttempt(UpdateOneModel<Document> model) {
		return CompletableFuture.supplyAsync(() -> this.regexAttempts.updateOne(model.getFilter(), model.getUpdate(), model.getOptions()), this.executor);
	}

	public CompletableFuture<DeleteResult> deleteRegexAttempt(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.regexAttempts.deleteOne(filter), this.executor);
	}

	public MongoCollection<Document> getGames() {
		return this.games;
	}

	public FindIterable<Document> getGames(Bson filter, Bson projection) {
		return this.games.find(filter).projection(projection);
	}

	public Document getGame(Bson filter, Bson projection) {
		return this.getGames(filter, projection).first();
	}

	public CompletableFuture<InsertOneResult> insertGame(Document data) {
		return CompletableFuture.supplyAsync(() -> this.games.insertOne(data), this.executor);
	}

	public CompletableFuture<InsertManyResult> insertManyGames(List<Document> data) {
		return CompletableFuture.supplyAsync(() -> this.games.insertMany(data), this.executor);
	}

	public MongoCollection<Document> getLoggers() {
		return this.loggers;
	}

	public FindIterable<Document> getLoggers(Bson filter, Bson projection) {
		return this.loggers.find(filter).projection(projection);
	}

	public Document getLogger(Bson filter, Bson projection) {
		return this.getLoggers(filter, projection).first();
	}

	public long countLoggers(Bson filter, CountOptions options) {
		return this.loggers.countDocuments(filter, options);
	}

	public CompletableFuture<AggregateIterable<Document>> aggregateLoggers(List<Bson> pipeline) {
		return CompletableFuture.supplyAsync(() -> this.loggers.aggregate(pipeline));
	}

	public CompletableFuture<InsertOneResult> insertLogger(Document data) {
		return CompletableFuture.supplyAsync(() -> this.loggers.insertOne(data), this.executor);
	}

	public CompletableFuture<DeleteResult> deleteLogger(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.loggers.deleteOne(filter), this.executor);
	}

	public CompletableFuture<DeleteResult> deleteManyLoggers(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.loggers.deleteMany(filter), this.executor);
	}

	public CompletableFuture<UpdateResult> updateLogger(Bson filter, Bson update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.loggers.updateOne(filter, update, options), this.executor);
	}

	public CompletableFuture<UpdateResult> updateLogger(Bson filter, Bson update) {
		return this.updateLogger(filter, update, this.updateOptions);
	}

	public CompletableFuture<UpdateResult> updateLogger(Bson filter, List<Bson> update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.loggers.updateOne(filter, update, options), this.executor);
	}

	public CompletableFuture<UpdateResult> updateLogger(Bson filter, List<Bson> update) {
		return this.updateLogger(filter, update, this.updateOptions);
	}

	public CompletableFuture<Document> findAndUpdateLogger(Bson filter, Bson update, FindOneAndUpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.loggers.findOneAndUpdate(filter, update, options), this.executor);
	}

	public CompletableFuture<Document> findAndUpdateLogger(Bson filter, List<Bson> update, FindOneAndUpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.loggers.findOneAndUpdate(filter, update, options), this.executor);
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
		return CompletableFuture.supplyAsync(() -> this.marriages.updateOne(filter, update, this.updateOptions), this.executor);
	}

	public CompletableFuture<DeleteResult> deleteMarriage(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.marriages.deleteOne(filter), this.executor);
	}

	public CompletableFuture<DeleteResult> deleteManyMarriages(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.marriages.deleteMany(filter), this.executor);
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
		return CompletableFuture.supplyAsync(() -> this.templates.insertOne(data), this.executor);
	}

	public CompletableFuture<DeleteResult> deleteTemplateById(ObjectId id) {
		return CompletableFuture.supplyAsync(() -> this.templates.deleteOne(Filters.eq("_id", id)), this.executor);
	}

	public CompletableFuture<DeleteResult> deleteManyTemplates(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.templates.deleteMany(filter), this.executor);
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
		return CompletableFuture.supplyAsync(() -> this.reactionRoles.updateOne(filter, update, options), this.executor);
	}

	public CompletableFuture<UpdateResult> updateReactionRole(Bson filter, List<Bson> update) {
		return this.updateReactionRole(filter, update, this.updateOptions);
	}

	public CompletableFuture<Document> findAndUpdateReactionRole(Bson filter, List<Bson> update, FindOneAndUpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.reactionRoles.findOneAndUpdate(filter, update, options), this.executor);
	}

	public CompletableFuture<UpdateResult> updateReactionRole(Bson filter, Bson update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.reactionRoles.updateOne(filter, update, options), this.executor);
	}

	public CompletableFuture<UpdateResult> updateReactionRole(Bson filter, Bson update) {
		return this.updateReactionRole(filter, update, this.updateOptions);
	}

	public CompletableFuture<UpdateResult> updateManyReactionRoles(Bson filter, List<Bson> update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.reactionRoles.updateMany(filter, update, options), this.executor);
	}

	public CompletableFuture<Document> findAndUpdateReactionRole(Bson filter, Bson update, FindOneAndUpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.reactionRoles.findOneAndUpdate(filter, update, options), this.executor);
	}

	public CompletableFuture<UpdateResult> updateManyReactionRoles(Bson filter, Bson update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.reactionRoles.updateMany(filter, update, options), this.executor);
	}

	public CompletableFuture<UpdateResult> updateManyReactionRoles(Bson filter, Bson update) {
		return this.updateManyReactionRoles(filter, update, this.updateOptions);
	}

	public CompletableFuture<DeleteResult> deleteReactionRole(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.reactionRoles.deleteOne(filter), this.executor);
	}

	public CompletableFuture<DeleteResult> deleteManyReactionRoles(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.reactionRoles.deleteMany(filter), this.executor);
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

	public CompletableFuture<AggregateIterable<Document>> aggregateRegexes(List<Bson> pipeline) {
		return CompletableFuture.supplyAsync(() -> this.regexes.aggregate(pipeline));
	}

	public Document getRegex(Bson filter, Bson projection) {
		return this.getRegexes(filter, projection).first();
	}

	public CompletableFuture<InsertOneResult> insertRegex(Document data) {
		return CompletableFuture.supplyAsync(() -> this.regexes.insertOne(data), this.executor);
	}

	public CompletableFuture<UpdateResult> updateRegex(Bson filter, Bson update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.regexes.updateOne(filter, update, options), this.executor);
	}

	public CompletableFuture<UpdateResult> updateRegex(Bson filter, Bson update) {
		return this.updateRegex(filter, update, new UpdateOptions());
	}

	public CompletableFuture<UpdateResult> updateRegex(Bson filter, List<Bson> update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.regexes.updateOne(filter, update, options), this.executor);
	}

	public CompletableFuture<UpdateResult> updateRegex(Bson filter, List<Bson> update) {
		return this.updateRegex(filter, update, this.updateOptions);
	}

	public CompletableFuture<Document> findAndUpdateRegex(Bson filter, List<Bson> update, FindOneAndUpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.regexes.findOneAndUpdate(filter, update, options), this.executor);
	}

	public CompletableFuture<DeleteResult> deleteRegex(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.regexes.deleteOne(filter), this.executor);
	}

	public CompletableFuture<Document> findAndDeleteRegex(Bson filter, FindOneAndDeleteOptions options) {
		return CompletableFuture.supplyAsync(() -> this.regexes.findOneAndDelete(filter, options), this.executor);
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
		return CompletableFuture.supplyAsync(() -> this.triggers.insertOne(data), this.executor);
	}

	public CompletableFuture<UpdateResult> updateTrigger(Bson filter, Bson update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.triggers.updateOne(filter, update, options), this.executor);
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
		return CompletableFuture.supplyAsync(() -> this.triggers.updateOne(filter, update, options), this.executor);
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
		return CompletableFuture.supplyAsync(() -> this.triggers.updateMany(filter, update, options), this.executor);
	}

	public CompletableFuture<UpdateResult> updateManyTriggers(Bson filter, List<Bson> update) {
		return CompletableFuture.supplyAsync(() -> this.triggers.updateMany(filter, update, this.updateOptions), this.executor);
	}

	public CompletableFuture<Document> findAndUpdateTrigger(Bson filter, List<Bson> update, FindOneAndUpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.triggers.findOneAndUpdate(filter, update, options), this.executor);
	}

	public CompletableFuture<Document> findAndUpdateTriggerById(ObjectId id, List<Bson> update, FindOneAndUpdateOptions options) {
		return this.findAndUpdateTrigger(Filters.eq("_id", id), update, options);
	}

	public CompletableFuture<DeleteResult> deleteTrigger(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.triggers.deleteOne(filter), this.executor);
	}

	public CompletableFuture<DeleteResult> deleteTriggerById(ObjectId id) {
		return this.deleteTrigger(Filters.eq("_id", id));
	}

	public CompletableFuture<DeleteResult> deleteManyTriggers(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.triggers.deleteMany(filter), this.executor);
	}

	public CompletableFuture<BulkWriteResult> bulkWriteTriggers(List<WriteModel<Document>> bulkData) {
		return CompletableFuture.supplyAsync(() -> this.triggers.bulkWrite(bulkData), this.executor);
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
		return CompletableFuture.supplyAsync(() -> this.stars.insertOne(data), this.executor);
	}

	public CompletableFuture<DeleteResult> deleteStar(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.stars.deleteOne(filter), this.executor);
	}

	public CompletableFuture<DeleteResult> deleteStarById(long userId, long messageId) {
		return this.deleteStar(Filters.and(Filters.eq("userId", userId), Filters.eq("messageId", messageId)));
	}

	public CompletableFuture<DeleteResult> deleteManyStars(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.stars.deleteMany(filter), this.executor);
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
		return CompletableFuture.supplyAsync(() -> this.starboards.insertOne(data), this.executor);
	}

	public CompletableFuture<UpdateResult> updateStarboard(Bson filter, Bson update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.starboards.updateOne(filter, update, options), this.executor);
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
		return CompletableFuture.supplyAsync(() -> this.starboards.updateOne(filter, update, options), this.executor);
	}

	public CompletableFuture<UpdateResult> updateStarboard(Bson filter, List<Bson> update) {
		return this.updateStarboard(filter, update, this.updateOptions);
	}

	public CompletableFuture<UpdateResult> updateStarboardById(ObjectId id, List<Bson> update, UpdateOptions options) {
		return this.updateStarboard(Filters.eq("_id", id), update, options);
	}

	public CompletableFuture<Document> findAndUpdateStarboard(Bson filter, Bson update, FindOneAndUpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.starboards.findOneAndUpdate(filter, update, options), this.executor);
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
		return CompletableFuture.supplyAsync(() -> this.starboards.findOneAndUpdate(filter, update, options), this.executor);
	}

	public CompletableFuture<Document> findAndUpdateStarboard(Bson filter, List<Bson> update) {
		return this.findAndUpdateStarboard(filter, update, this.findOneAndUpdateOptions);
	}

	public CompletableFuture<Document> findAndUpdateStarboardById(ObjectId id, List<Bson> update, FindOneAndUpdateOptions options) {
		return this.findAndUpdateStarboard(Filters.eq("_id", id), update, options);
	}

	public CompletableFuture<DeleteResult> deleteStarboard(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.starboards.deleteOne(filter), this.executor);
	}

	public CompletableFuture<DeleteResult> deleteStarboardById(ObjectId id) {
		return this.deleteStarboard(Filters.eq("_id", id));
	}

	public CompletableFuture<Document> findAndDeleteStarboard(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.starboards.findOneAndDelete(filter), this.executor);
	}

	public CompletableFuture<Document> findAndDeleteStarboardById(ObjectId id) {
		return this.findAndDeleteStarboard(Filters.eq("_id", id));
	}

	public CompletableFuture<DeleteResult> deleteManyStarboards(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.starboards.deleteMany(filter), this.executor);
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
		return CompletableFuture.supplyAsync(() -> this.suggestions.insertOne(data), this.executor);
	}

	public CompletableFuture<UpdateResult> updateSuggestion(Bson filter, Bson update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.suggestions.updateOne(filter, update, options), this.executor);
	}

	public CompletableFuture<UpdateResult> updateSuggestion(Bson filter, Bson update) {
		return this.updateSuggestion(filter, update, this.updateOptions);
	}

	public CompletableFuture<Document> findAndUpdateSuggestion(Bson filter, Bson update, FindOneAndUpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.suggestions.findOneAndUpdate(filter, update, options), this.executor);
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
		return CompletableFuture.supplyAsync(() -> this.suggestions.deleteOne(filter), this.executor);
	}

	public CompletableFuture<Document> findAndDeleteSuggestion(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.suggestions.findOneAndDelete(filter), this.executor);
	}

	public CompletableFuture<Document> findAndDeleteSuggestionById(ObjectId id) {
		return this.findAndDeleteSuggestion(Filters.eq("_id", id));
	}

	public CompletableFuture<DeleteResult> deleteManySuggestions(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.suggestions.deleteMany(filter), this.executor);
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
		return CompletableFuture.supplyAsync(() -> this.reminders.updateOne(filter, update, options), this.executor);
	}

	public CompletableFuture<UpdateResult> updateReminder(Bson filter, Bson update) {
		return this.updateReminder(filter, update, this.updateOptions);
	}

	public CompletableFuture<UpdateResult> updateReminder(Bson filter, List<Bson> update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.reminders.updateOne(filter, update, options), this.executor);
	}

	public CompletableFuture<UpdateResult> updateReminder(Bson filter, List<Bson> update) {
		return this.updateReminder(filter, update, this.updateOptions);
	}

	public CompletableFuture<UpdateResult> updateReminder(UpdateOneModel<Document> model) {
		return this.updateReminder(model.getFilter(), model.getUpdate(), model.getOptions());
	}

	public CompletableFuture<InsertOneResult> insertReminder(Document data) {
		return CompletableFuture.supplyAsync(() -> this.reminders.insertOne(data), this.executor);
	}

	public CompletableFuture<DeleteResult> deleteReminder(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.reminders.deleteOne(filter), this.executor);
	}

	public CompletableFuture<DeleteResult> deleteReminder(DeleteOneModel<Document> model) {
		return this.deleteReminder(model.getFilter());
	}

	public CompletableFuture<DeleteResult> deleteReminderById(ObjectId id) {
		return this.deleteReminder(Filters.eq("_id", id));
	}

	public CompletableFuture<BulkWriteResult> bulkWriteReminders(List<WriteModel<Document>> bulkData) {
		return CompletableFuture.supplyAsync(() -> this.reminders.bulkWrite(bulkData), this.executor);
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
		return CompletableFuture.supplyAsync(() -> this.youtubeNotifications.insertOne(data), this.executor);
	}

	public CompletableFuture<Document> findAndDeleteYouTubeNotificationById(ObjectId id, FindOneAndDeleteOptions options) {
		return CompletableFuture.supplyAsync(() -> this.youtubeNotifications.findOneAndDelete(Filters.eq("_id", id), options), this.executor);
	}

	public CompletableFuture<DeleteResult> deleteYouTubeNotificationById(ObjectId id) {
		return CompletableFuture.supplyAsync(() -> this.youtubeNotifications.deleteOne(Filters.eq("_id", id)), this.executor);
	}

	public CompletableFuture<DeleteResult> deleteManyYouTubeNotifications(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.youtubeNotifications.deleteMany(filter), this.executor);
	}

	public CompletableFuture<Document> findAndUpdateYouTubeNotification(Bson filter, Bson update, FindOneAndUpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.youtubeNotifications.findOneAndUpdate(filter, update, options), this.executor);
	}

	public CompletableFuture<Document> findAndUpdateYouTubeNotificationById(ObjectId id, Bson update, FindOneAndUpdateOptions options) {
		return this.findAndUpdateYouTubeNotification(Filters.eq("_id", id), update, options);
	}

	public CompletableFuture<UpdateResult> updateYouTubeNotification(Bson filter, Bson update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.youtubeNotifications.updateOne(filter, update, options), this.executor);
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
		return CompletableFuture.supplyAsync(() -> this.youtubeNotifications.updateMany(filter, update, options), this.executor);
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
		return CompletableFuture.supplyAsync(() -> this.redirects.findOneAndUpdate(Filters.eq("url", url), Updates.combine(Updates.setOnInsert("_id", id), Updates.setOnInsert("url", url)), this.findOneAndUpdateOptions), this.executor);
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
		return CompletableFuture.supplyAsync(() -> this.regexTemplates.insertOne(data), this.executor);
	}
	
	public CompletableFuture<UpdateResult> updateRegexTemplateById(ObjectId id, Bson update) {
		return CompletableFuture.supplyAsync(() -> this.regexTemplates.updateOne(Filters.eq("_id", id), update), this.executor);
	}
	
	public CompletableFuture<Document> findAndUpdateRegexTemplateById(ObjectId id, Bson update, FindOneAndUpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.regexTemplates.findOneAndUpdate(Filters.eq("_id", id), update, options), this.executor);
	}
	
	public CompletableFuture<DeleteResult> deleteRegexTemplateById(ObjectId id) {
		return CompletableFuture.supplyAsync(() -> this.regexTemplates.deleteOne(Filters.eq("_id", id)), this.executor);
	}
	
	public CompletableFuture<Document> findAndDeleteRegexTemplateById(ObjectId id, FindOneAndDeleteOptions options) {
		return CompletableFuture.supplyAsync(() -> this.regexTemplates.findOneAndDelete(Filters.eq("_id", id), options), this.executor);
	}

	public MongoCollection<Document> getBlacklists() {
		return this.blacklists;
	}

	public FindIterable<Document> getBlacklists(Bson filter, Bson projection) {
		return this.blacklists.find(filter).projection(projection);
	}

	public Document getBlacklist(Bson filter, Bson projection) {
		return this.getBlacklists(filter, projection).first();
	}

	public CompletableFuture<UpdateResult> updateBlacklist(Bson filter, List<? extends Bson> update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.blacklists.updateOne(filter, update, options), this.executor);
	}

	public CompletableFuture<UpdateResult> updateBlacklist(Bson filter, Bson update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.blacklists.updateOne(filter, update, options), this.executor);
	}

	public CompletableFuture<UpdateResult> updateBlacklist(UpdateOneModel<Document> update) {
		return this.updateBlacklist(update.getFilter(), update.getUpdate(), update.getOptions());
	}

	public CompletableFuture<Document> findAndUpdateBlacklist(Bson filter, Bson update, FindOneAndUpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.blacklists.findOneAndUpdate(filter, update, options), this.executor);
	}

	public CompletableFuture<Document> findAndUpdateBlacklist(Bson filter, List<Bson> update, FindOneAndUpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.blacklists.findOneAndUpdate(filter, update, options), this.executor);
	}

	public CompletableFuture<UpdateResult> updateManyBlacklists(Bson filter, List<Bson> update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.blacklists.updateMany(filter, update, options), this.executor);
	}

	public CompletableFuture<UpdateResult> updateManyBlacklists(Bson filter, List<Bson> update) {
		return this.updateManyBlacklists(filter, update, this.updateOptions);
	}

	public CompletableFuture<BulkWriteResult> bulkWriteBlacklists(List<? extends WriteModel<? extends Document>> bulkData) {
		return CompletableFuture.supplyAsync(() -> this.blacklists.bulkWrite(bulkData), this.executor);
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

		return data == null ? MongoDatabase.EMPTY_DOCUMENT : data;
	}
	
	public Document getUserById(long userId, Bson projection) {
		return this.getUserById(Filters.eq("_id", userId), projection);
	}

	public CompletableFuture<AggregateIterable<Document>> aggregateUsers(List<Bson> pipeline) {
		return CompletableFuture.supplyAsync(() -> this.users.aggregate(pipeline), this.executor);
	}

	public CompletableFuture<UpdateResult> updateUser(Bson filter, List<? extends Bson> update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.users.updateOne(filter, update, options), this.executor);
	}

	public CompletableFuture<UpdateResult> updateUserById(long userId, List<? extends Bson> update, UpdateOptions options) {
		return this.updateUser(Filters.eq("_id", userId), update, options);
	}

	public CompletableFuture<UpdateResult> updateUserById(long userId, List<? extends Bson> update) {
		return this.updateUserById(userId, update, this.updateOptions);
	}

	public CompletableFuture<UpdateResult> updateUser(Bson filter, Bson update, UpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.users.updateOne(filter, update, options), this.executor);
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
		return CompletableFuture.supplyAsync(() -> this.users.findOneAndUpdate(filter, update, options), this.executor);
	}

	public CompletableFuture<Document> findAndUpdateUserById(long userId, Bson update, FindOneAndUpdateOptions options) {
		return this.findAndUpdateUser(Filters.eq("_id", userId), update, options);
	}
	
	public CompletableFuture<Document> findAndUpdateUserById(long userId, Bson update) {
		return this.findAndUpdateUserById(userId, update, this.findOneAndUpdateOptions);
	}

	public CompletableFuture<Document> findAndUpdateUser(Bson filter, List<Bson> update, FindOneAndUpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.users.findOneAndUpdate(filter, update, options), this.executor);
	}

	public CompletableFuture<Document> findAndUpdateUserById(long userId, List<Bson> update, FindOneAndUpdateOptions options) {
		return this.findAndUpdateUser(Filters.eq("_id", userId), update, options);
	}

	public CompletableFuture<Document> findAndUpdateUserById(long userId, List<Bson> update) {
		return this.findAndUpdateUserById(userId, update, this.findOneAndUpdateOptions);
	}
	
	public CompletableFuture<BulkWriteResult> bulkWriteUsers(List<? extends WriteModel<? extends Document>> bulkData) {
		return CompletableFuture.supplyAsync(() -> this.users.bulkWrite(bulkData), this.executor);
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
		
		return data == null ? MongoDatabase.EMPTY_DOCUMENT : data;
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
		
		return CompletableFuture.supplyAsync(() -> this.guilds.updateOne(dbFilter, update, options), this.executor);
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
		
		return CompletableFuture.supplyAsync(() -> this.guilds.updateOne(dbFilter, update, options), this.executor);
	}
	
	public CompletableFuture<UpdateResult> updateGuildById(long guildId, Bson update, UpdateOptions options) {
		return this.updateGuildById(guildId, null, update, options);
	}
	
	public CompletableFuture<UpdateResult> updateGuildById(long guildId, Bson update) {
		return this.updateGuildById(guildId, update, this.updateOptions);
	}
	
	public CompletableFuture<UpdateResult> updateGuild(UpdateOneModel<Document> model) {
		return CompletableFuture.supplyAsync(() -> this.guilds.updateOne(model.getFilter(), model.getUpdate(), model.getOptions()), this.executor);
	}
	
	public CompletableFuture<Document> findAndUpdateGuildById(long guildId, Bson filter, Bson update, FindOneAndUpdateOptions options) {
		Bson dbFilter;
		if (filter == null) {
			dbFilter = Filters.eq("_id", guildId);
		} else {
			dbFilter = Filters.and(Filters.eq("_id", guildId), filter);
		}
		
		return CompletableFuture.supplyAsync(() -> this.guilds.findOneAndUpdate(dbFilter, update, options), this.executor);
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
		
		return CompletableFuture.supplyAsync(() -> this.guilds.findOneAndUpdate(dbFilter, update, options), this.executor);
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
		return CompletableFuture.supplyAsync(() -> this.guilds.bulkWrite(bulkData), this.executor);
	}
	
	public MongoCollection<Document> getGiveaways() {
		return this.giveaways;
	}
	
	public FindIterable<Document> getGiveaways(Bson filter) {
		return this.giveaways.find(filter);
	}
	
	public Document getGiveawayById(long messageId) {
		return this.giveaways.find(Filters.eq("messageId", messageId)).first();
	}
	
	public CompletableFuture<InsertOneResult> insertGiveaway(Document data) {
		return CompletableFuture.supplyAsync(() -> this.giveaways.insertOne(data), this.executor);
	}
	
	public CompletableFuture<UpdateResult> updateGiveaway(UpdateOneModel<Document> model) {
		return CompletableFuture.supplyAsync(() -> this.giveaways.updateOne(model.getFilter(), model.getUpdate(), model.getOptions()), this.executor);
	}
	
	public CompletableFuture<UpdateResult> updateGiveawayById(long messageId, Bson update) {
		return CompletableFuture.supplyAsync(() -> this.giveaways.updateOne(Filters.eq("messageId", messageId), update), this.executor);
	}
	
	public CompletableFuture<UpdateResult> updateGiveawayById(long messageId, List<? extends Bson> update) {
		return CompletableFuture.supplyAsync(() -> this.giveaways.updateOne(Filters.eq("messageId", messageId), update), this.executor);
	}
	
	public CompletableFuture<Document> findAndUpdateGiveawayById(long messageId, List<? extends Bson> update, FindOneAndUpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.giveaways.findOneAndUpdate(Filters.eq("messageId", messageId), update, options), this.executor);
	}
	
	public CompletableFuture<DeleteResult> deleteGiveawayById(long messageId) {
		return CompletableFuture.supplyAsync(() -> this.giveaways.deleteOne(Filters.eq("messageId", messageId)), this.executor);
	}
	
	public CompletableFuture<DeleteResult> deleteManyGiveaways(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.giveaways.deleteMany(filter), this.executor);
	}
	
	public CompletableFuture<BulkWriteResult> bulkWriteGiveaways(List<? extends WriteModel<? extends Document>> bulkData) {
		return CompletableFuture.supplyAsync(() -> this.giveaways.bulkWrite(bulkData), this.executor);
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
		return CompletableFuture.supplyAsync(() -> this.messages.insertOne(data), this.executor);
	}
	
	public CompletableFuture<UpdateResult> replaceMessage(Document data) {
		return CompletableFuture.supplyAsync(() -> this.messages.replaceOne(Filters.eq("_id", data.get("_id")), data), this.executor);
	}
	
	public CompletableFuture<DeleteResult> deleteMessageById(long messageId) {
		return CompletableFuture.supplyAsync(() -> this.messages.deleteOne(Filters.eq("_id", messageId)), this.executor);
	}

	public CompletableFuture<DeleteResult> deleteMessages(List<Long> messageIds) {
		return CompletableFuture.supplyAsync(() -> this.messages.deleteMany(Filters.in("_id", messageIds)), this.executor);
	}
	
	public CompletableFuture<BulkWriteResult> bulkWriteMessages(List<? extends WriteModel<? extends Document>> bulkData) {
		return CompletableFuture.supplyAsync(() -> this.messages.bulkWrite(bulkData), this.executor);
	}
	
	public MongoCollection<Document> getAuction() {
		return this.auction;
	}

	public CompletableFuture<AggregateIterable<Document>> aggregateAuction(List<Bson> pipeline) {
		return CompletableFuture.supplyAsync(() -> this.auction.aggregate(pipeline), this.executor);
	}
	
	public FindIterable<Document> getAuctions(Bson filter) {
		return this.auction.find(filter);
	}
	
	public Document getAuctionById(ObjectId id) {
		return this.auction.find(Filters.eq("_id", id)).first();
	}
	
	public CompletableFuture<DeleteResult> deleteAuctionById(ObjectId id) {
		return CompletableFuture.supplyAsync(() -> this.auction.deleteOne(Filters.eq("_id", id)), this.executor);
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
		return CompletableFuture.supplyAsync(() -> this.modLogs.insertOne(data), this.executor);
	}
	
	public CompletableFuture<UpdateResult> updateModLogById(ObjectId id, Bson update) {
		return CompletableFuture.supplyAsync(() -> this.modLogs.updateOne(Filters.eq("_id", id), update, this.updateOptions), this.executor);
	}

	public CompletableFuture<Document> findAndUpdateModLogById(ObjectId id, List<Bson> update, FindOneAndUpdateOptions options) {
		return CompletableFuture.supplyAsync(() -> this.modLogs.findOneAndUpdate(Filters.eq("_id", id), update, options), this.executor);
	}

	public CompletableFuture<UpdateResult> updateManyModLogs(List<Bson> update) {
		return CompletableFuture.supplyAsync(() -> this.modLogs.updateMany(MongoDatabase.EMPTY_DOCUMENT, update, this.updateOptions), this.executor);
	}

	public CompletableFuture<Document> findAndDeleteModLog(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.modLogs.findOneAndDelete(filter), this.executor);
	}

	public CompletableFuture<Document> findAndDeleteModLogById(ObjectId id) {
		return this.findAndDeleteModLog(Filters.eq("_id", id));
	}

	public CompletableFuture<DeleteResult> deleteModLog(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.modLogs.deleteOne(filter), this.executor);
	}

	public CompletableFuture<DeleteResult> deleteModLogById(ObjectId id) {
		return this.deleteModLog(Filters.eq("_id", id));
	}

	public CompletableFuture<DeleteResult> deleteManyModLogs(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.modLogs.deleteMany(filter), this.executor);
	}
	
	public MongoCollection<Document> getYouTubeSubscriptions() {
		return this.youtubeSubscriptions;
	}
	
	public long countYouTubeSubscriptionDocuments(Bson filter) {
		return this.youtubeSubscriptions.countDocuments(filter);
	}
	
	public CompletableFuture<DeleteResult> deleteYouTubeSubscription(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.youtubeSubscriptions.deleteOne(filter), this.executor);
	}
	
	public CompletableFuture<UpdateResult> updateYouTubeSubscriptionById(String channelId, Bson update) {
		return CompletableFuture.supplyAsync(() -> this.youtubeSubscriptions.updateOne(Filters.eq("_id", channelId), update, this.updateOptions), this.executor);
	}
	
	public CompletableFuture<BulkWriteResult> bulkWriteYouTubeSubscriptions(List<? extends WriteModel<? extends Document>> bulkData) {
		return CompletableFuture.supplyAsync(() -> this.youtubeSubscriptions.bulkWrite(bulkData), this.executor);
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
		return CompletableFuture.supplyAsync(() -> this.youtubeNotificationLogs.insertOne(data), this.executor);
	}
	
	public CompletableFuture<DeleteResult> deleteManyYouTubeNotificationLogs(String videoId) {
		return CompletableFuture.supplyAsync(() -> this.youtubeNotificationLogs.deleteMany(Filters.eq("videoId", videoId)), this.executor);
	}
	
	public MongoCollection<Document> getCommands() {
		return this.commands;
	}
	
	public CompletableFuture<FindIterable<Document>> getCommands(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.commands.find(filter), this.executor);
	}

	public CompletableFuture<InsertOneResult> insertCommand(Document data) {
		return CompletableFuture.supplyAsync(() -> this.commands.insertOne(data), this.executor);
	}
	
	public CompletableFuture<AggregateIterable<Document>> aggregateCommands(List<? extends Bson> pipeline) {
		return CompletableFuture.supplyAsync(() -> this.commands.aggregate(pipeline), this.executor);
	}
	
	public MongoCollection<Document> getOffences() {
		return this.offences;
	}
	
	public CompletableFuture<FindIterable<Document>> getOffences(Bson filter) {
		return CompletableFuture.supplyAsync(() -> this.offences.find(filter), this.executor);
	}

	public CompletableFuture<InsertOneResult> insertOffence(Document data) {
		return CompletableFuture.supplyAsync(() -> this.offences.insertOne(data), this.executor);
	}

	public static <Type> BiConsumer<Type, Throwable> exceptionally(ShardManager manager) {
		return ($, exception) -> ExceptionUtility.sendErrorMessage(manager, exception);
	}

	public static <Type> BiConsumer<Type, Throwable> exceptionally(CommandEvent event) {
		return ($, exception) -> ExceptionUtility.sendExceptionally(event, exception);
	}
	
}
