package com.sx4.bot.commands.management;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.*;
import com.sx4.bot.annotations.command.AuthorPermissions;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.management.LoggerCategory;
import com.sx4.bot.entities.management.LoggerEvent;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.LoggerUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.TextChannel;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class LoggerCommand extends Sx4Command {

    public LoggerCommand() {
        super("logger", 53);

        super.setAliases("logs", "log");
        super.setDescription("Logs server events which occur");
        super.setExamples("logger add", "logger remove", "logger list");
        super.setCategoryAll(ModuleCategory.MANAGEMENT);
    }

    public void onCommand(Sx4CommandEvent event) {
        event.replyHelp().queue();
    }

    @Command(value="add", description="Adds a logger to a certain channel")
    @CommandId(54)
    @AuthorPermissions(permissions={Permission.MANAGE_SERVER})
    @Examples({"logger add #logs", "logger add"})
    public void add(Sx4CommandEvent event, @Argument(value="channel", endless=true, nullDefault=true) TextChannel channel) {
        TextChannel effectiveChannel = channel == null ? event.getTextChannel() : channel;

        List<Bson> guildPipeline = List.of(
            Aggregates.project(Projections.fields(Projections.computed("premium", Operators.lt(Operators.nowEpochSecond(), Operators.ifNull("$premium.endAt", 0L))), Projections.computed("guildId", "$_id"))),
            Aggregates.match(Filters.eq("guildId", event.getGuild().getIdLong()))
        );

        List<Bson> pipeline = List.of(
            Aggregates.match(Filters.and(Filters.eq("guildId", event.getGuild().getIdLong()), Filters.exists("enabled", false))),
            Aggregates.group(null, Accumulators.sum("count", 1)),
            Aggregates.limit(3),
            Aggregates.unionWith("guilds", guildPipeline),
            Aggregates.group(null, Accumulators.max("count", "$count"), Accumulators.max("premium", "$premium")),
            Aggregates.project(Projections.fields(Projections.include("premium"), Projections.computed("count", Operators.ifNull("$count", 0))))
        );

        event.getMongo().aggregateLoggers(pipeline).thenCompose(iterable -> {
            Document counter = iterable.first();
            if (counter != null && counter.getInteger("count") == 3 && !counter.getBoolean("premium")) {
                event.replyFailure("You need to have Sx4 premium to have more than 3 enabled loggers, you can get premium at <https://www.patreon.com/Sx4>").queue();
                return CompletableFuture.completedFuture(null);
            }

            Document data = new Document("channelId", effectiveChannel.getIdLong())
                .append("guildId", event.getGuild().getIdLong());

            return event.getMongo().insertLogger(data);
        }).whenComplete((result, exception) -> {
            Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
            if (cause instanceof MongoWriteException && ((MongoWriteException) cause).getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
                event.replyFailure("You already have a logger setup in " + effectiveChannel.getAsMention()).queue();
                return;
            }

            if (ExceptionUtility.sendExceptionally(event, exception) || result == null) {
                return;
            }

            event.replySuccess("You now have a logger setup in " + effectiveChannel.getAsMention()).queue();
        });
    }

    @Command(value="remove", description="Removes a logger from a certain channel")
    @CommandId(55)
    @AuthorPermissions(permissions={Permission.MANAGE_SERVER})
    @Examples({"logger remove #logs", "logger remove"})
    public void remove(Sx4CommandEvent event, @Argument(value="channel", endless=true, nullDefault=true) TextChannel channel) {
        TextChannel effectiveChannel = channel == null ? event.getTextChannel() : channel;

        event.getMongo().deleteLogger(Filters.eq("channelId", effectiveChannel.getIdLong())).whenComplete((result, exception) -> {
            if (ExceptionUtility.sendExceptionally(event, exception)) {
                return;
            }

            if (result.getDeletedCount() == 0) {
                event.replyFailure("You don't have a logger in " + effectiveChannel.getAsMention()).queue();
                return;
            }

            event.replySuccess("You no longer have a logger setup in " + effectiveChannel.getAsMention()).queue();
        });
    }

    @Command(value="toggle", aliases={"enable", "disable"}, description="Toggles the state of a logger")
    @CommandId(56)
    @AuthorPermissions(permissions={Permission.MANAGE_SERVER})
    @Examples({"logger toggle #logs", "logger toggle"})
    public void toggle(Sx4CommandEvent event, @Argument(value="channel", endless=true, nullDefault=true) TextChannel channel) {
        TextChannel effectiveChannel = channel == null ? event.getTextChannel() : channel;

        Bson filter = Filters.and(
            Filters.eq("guildId", event.getGuild().getIdLong()),
            Filters.exists("enabled", false)
        );

        List<Bson> guildPipeline = List.of(
            Aggregates.project(Projections.fields(Projections.computed("premium", Operators.lt(Operators.nowEpochSecond(), Operators.ifNull("$premium.endAt", 0L))), Projections.computed("guildId", "$_id"))),
            Aggregates.match(Filters.eq("guildId", event.getGuild().getIdLong()))
        );

        List<Bson> pipeline = List.of(
            Aggregates.match(Filters.and(Filters.eq("guildId", event.getGuild().getIdLong()), Filters.exists("enabled", false))),
            Aggregates.project(Projections.include("channelId")),
            Aggregates.group(null, Accumulators.push("loggers", Operators.ROOT)),
            Aggregates.unionWith("guilds", guildPipeline),
            Aggregates.group(null, Accumulators.max("loggers", "$loggers"), Accumulators.max("premium", "$premium")),
            Aggregates.project(Projections.fields(Projections.include("premium"), Projections.computed("count", Operators.size(Operators.ifNull("$loggers", Collections.EMPTY_LIST))), Projections.computed("disabled", Operators.isEmpty(Operators.filter(Operators.ifNull("$loggers", Collections.EMPTY_LIST), Operators.eq("$$this.channelId", effectiveChannel.getIdLong()))))))
        );

        event.getMongo().aggregateLoggers(pipeline).thenCompose(iterable -> {
            Document data = iterable.first();
            if (data != null && data.getBoolean("disabled") && data.getInteger("count") >= 3 && !data.getBoolean("premium")) {
                event.replyFailure("You need to have Sx4 premium to have more than 3 enabled loggers, you can get premium at <https://www.patreon.com/Sx4>").queue();
                return CompletableFuture.completedFuture(null);
            }

            List<Bson> update = List.of(Operators.set("enabled", Operators.cond(Operators.exists("$enabled"), Operators.REMOVE, false)));
            FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER).projection(Projections.include("enabled"));

            return event.getMongo().findAndUpdateLogger(Filters.eq("channelId", effectiveChannel.getIdLong()), update, options);
        }).whenComplete((data, exception) -> {
            if (ExceptionUtility.sendExceptionally(event, exception) || data == null) {
                return;
            }

            event.replySuccess("The logger in " + effectiveChannel.getAsMention() + " is now **" + (data.get("enabled", true) ? "enabled" : "disabled") + "**").queue();
        });
    }

    public static class EventsCommand extends Sx4Command {

        public EventsCommand() {
            super("events", 57);

            super.setAliases("event");
            super.setDescription("Set what events you want to be sent with a logger");
            super.setExamples("logger events add", "logger events remove", "logger events set");
        }

        public void onCommand(Sx4CommandEvent event) {
            event.replyHelp().queue();
        }

        @Command(value="add", description="Adds an event the logger should send")
        @CommandId(58)
        @AuthorPermissions(permissions={Permission.MANAGE_SERVER})
        @Examples({"logger events add #logs MESSAGE_DELETE", "logger events add MESSAGE_DELETE MESSAGE_UPDATE"})
        public void add(Sx4CommandEvent event, @Argument(value="channel", nullDefault=true) TextChannel channel, @Argument(value="events") LoggerEvent... events) {
            TextChannel effectiveChannel = channel == null ? event.getTextChannel() : channel;

            long raw = LoggerEvent.getRaw(events);
            List<Bson> update = List.of(Operators.set("events", Operators.bitwiseAnd(Operators.ifNull("$events", LoggerEvent.ALL), raw)));

            event.getMongo().updateLogger(Filters.eq("channelId", effectiveChannel.getIdLong()), update, new UpdateOptions()).whenComplete((result, exception) -> {
                if (ExceptionUtility.sendExceptionally(event, exception)) {
                    return;
                }

                if (result.getMatchedCount() == 0) {
                    event.replyFailure("You do not have a logger in " + effectiveChannel.getAsMention()).queue();
                    return;
                }

                if (result.getModifiedCount() == 0) {
                    event.replyFailure("That logger already has those events").queue();
                    return;
                }

                event.replySuccess("That logger now has those events enabled").queue();
            });
        }

        @Command(value="remove", description="Removes an event from a logger")
        @CommandId(59)
        @AuthorPermissions(permissions={Permission.MANAGE_SERVER})
        @Examples({"logger events remove #logs MESSAGE_DELETE", "logger events remove MESSAGE_DELETE MESSAGE_UPDATE"})
        public void remove(Sx4CommandEvent event, @Argument(value="channel", nullDefault=true) TextChannel channel, @Argument(value="events") LoggerEvent... events) {
            TextChannel effectiveChannel = channel == null ? event.getTextChannel() : channel;

            long raw = LoggerEvent.getRaw(events);
            List<Bson> update = List.of(Operators.set("events", Operators.bitwiseAnd(Operators.ifNull("$events", LoggerEvent.ALL), ~raw)));

            event.getMongo().updateLogger(Filters.eq("channelId", effectiveChannel.getIdLong()), update, new UpdateOptions()).whenComplete((result, exception) -> {
                if (ExceptionUtility.sendExceptionally(event, exception)) {
                    return;
                }

                if (result.getMatchedCount() == 0) {
                    event.replyFailure("You do not have a logger in " + effectiveChannel.getAsMention()).queue();
                    return;
                }

                if (result.getModifiedCount() == 0) {
                    event.replyFailure("That logger doesn't have any of those events").queue();
                    return;
                }

                event.replySuccess("That logger now has those events disabled").queue();
            });
        }

        @Command(value="set", description="Sets the events a logger should use")
        @CommandId(60)
        @AuthorPermissions(permissions={Permission.MANAGE_SERVER})
        @Examples({"logger events set #logs MESSAGE_DELETE", "logger events set MESSAGE_DELETE MESSAGE_UPDATE"})
        public void set(Sx4CommandEvent event, @Argument(value="channel", nullDefault=true) TextChannel channel, @Argument(value="events") LoggerEvent... events) {
            TextChannel effectiveChannel = channel == null ? event.getTextChannel() : channel;

            event.getMongo().updateLogger(Filters.eq("channelId", effectiveChannel.getIdLong()), Updates.set("events", LoggerEvent.getRaw(events)), new UpdateOptions()).whenComplete((result, exception) -> {
                if (ExceptionUtility.sendExceptionally(event, exception)) {
                    return;
                }

                if (result.getMatchedCount() == 0) {
                    event.replyFailure("You don't have a logger in " + effectiveChannel.getAsMention()).queue();
                    return;
                }

                if (result.getModifiedCount() == 0) {
                    event.replyFailure("That logger already is already set to those events").queue();
                    return;
                }

                event.replySuccess("That logger will now only send those events").queue();
            });
        }

        @Command(value="list", description="Lists all the events you can use")
        @CommandId(61)
        @Examples({"logger events list"})
        public void list(Sx4CommandEvent event) {
            PagedResult<LoggerEvent> paged = new PagedResult<>(event.getBot(), Arrays.asList(LoggerEvent.values()))
                .setPerPage(15)
                .setAuthor("Events List", null, event.getGuild().getIconUrl())
                .setIndexed(false);

            paged.execute(event);
        }

    }

    public static class BlacklistCommand extends Sx4Command {

        public BlacklistCommand() {
            super("blacklist", 62);

            super.setDescription("Blacklist certain entities from being able to appear in logs");
            super.setExamples("logger blacklist set", "logger blacklist add", "logger blacklist remove");
        }

        public void onCommand(Sx4CommandEvent event) {
            event.replyHelp().queue();
        }

        @Command(value="set", description="Set what events a certain entity should be blacklisted from")
        @CommandId(63)
        @AuthorPermissions(permissions={Permission.MANAGE_SERVER})
        @Examples({"logger blacklist set #logs @Shea#6653 MESSAGE_DELETE", "logger blacklist set #logs @Members MESSAGE_UPDATE MESSAGE_DELETE", "logger blacklist set #logs #channel TEXT_CHANNEL_OVERRIDE_UPDATE"})
        public void set(Sx4CommandEvent event, @Argument(value="channel", nullDefault=true) TextChannel channel, @Argument(value="user | role | channel") String query, @Argument(value="events") LoggerEvent... events) {
            TextChannel effectiveChannel = channel == null ? event.getTextChannel() : channel;

            Set<LoggerCategory> common = LoggerUtility.getCommonCategories(events);
            if (common.isEmpty()) {
                event.replyFailure("All of those events don't have a blacklist type in common").queue();
                return;
            }

            PagedResult<LoggerCategory> paged = new PagedResult<>(event.getBot(), new ArrayList<>(common))
                .setAuthor("Conflicting Types", null, event.getGuild().getIconUrl())
                .setDisplayFunction(LoggerCategory::getName)
                .setTimeout(60)
                .setAutoSelect(true);

            paged.onSelect(select -> {
                LoggerCategory category = select.getSelected();

                long id = LoggerUtility.getEntityIdFromType(query, event.getGuild(), category);
                if (id == 0L) {
                    event.replyFailure("I could not find that " + category.getName().toLowerCase()).queue();
                    return;
                }

                long eventsRaw = LoggerEvent.getRaw(events);

                Bson entitiesMap = Operators.ifNull("$blacklist.entities", Collections.EMPTY_LIST);
                Bson entityFilter = Operators.filter(entitiesMap, Operators.eq("$$this.id", id));

                List<Bson> update = List.of(Operators.set("blacklist.entities", Operators.concatArrays(List.of(Operators.mergeObjects(Operators.ifNull(Operators.first(entityFilter), MongoDatabase.EMPTY_DOCUMENT), new Document("id", id).append("events", eventsRaw).append("type", category.getType()))), Operators.filter(entitiesMap, Operators.ne("$$this.id", id)))));

                event.getMongo().updateLogger(Filters.eq("channelId", effectiveChannel.getIdLong()), update, new UpdateOptions()).whenComplete((result, exception) -> {
                   if (ExceptionUtility.sendExceptionally(event, exception)) {
                       return;
                   }

                    if (result.getMatchedCount() == 0) {
                        event.replyFailure("You don't have a logger in " + effectiveChannel.getAsMention()).queue();
                        return;
                    }

                   if (result.getModifiedCount() == 0) {
                       event.replyFailure("That " + category.getName().toLowerCase() + " was already blacklisted from those events").queue();
                       return;
                   }

                   event.replySuccess("That " + category.getName().toLowerCase() + " is now blacklisted from appearing in those events for that logger").queue();
                });
            });

            paged.onTimeout(() -> event.reply("Timed out :stopwatch:").queue());

            paged.execute(event);
        }

        @Command(value="add", description="Add events to be blacklisted from a certain entity")
        @CommandId(64)
        @AuthorPermissions(permissions={Permission.MANAGE_SERVER})
        @Examples({"logger blacklist add #logs @Shea#6653 MESSAGE_DELETE", "logger blacklist add #logs @Members MESSAGE_UPDATE MESSAGE_DELETE", "logger blacklist add #logs #channel TEXT_CHANNEL_OVERRIDE_UPDATE"})
        public void add(Sx4CommandEvent event, @Argument(value="channel", nullDefault=true) TextChannel channel, @Argument(value="user | role | channel") String query, @Argument(value="events") LoggerEvent... events) {
            TextChannel effectiveChannel = channel == null ? event.getTextChannel() : channel;

            Set<LoggerCategory> common = LoggerUtility.getCommonCategories(events);
            if (common.isEmpty()) {
                event.replyFailure("All of those events don't have a blacklist type in common").queue();
                return;
            }

            PagedResult<LoggerCategory> paged = new PagedResult<>(event.getBot(), new ArrayList<>(common))
                .setAuthor("Conflicting Types", null, event.getGuild().getIconUrl())
                .setDisplayFunction(LoggerCategory::getName)
                .setTimeout(60)
                .setAutoSelect(true);

            paged.onSelect(select -> {
                LoggerCategory category = select.getSelected();

                long id = LoggerUtility.getEntityIdFromType(query, event.getGuild(), category);
                if (id == 0L) {
                    event.replyFailure("I could not find that " + category.getName().toLowerCase()).queue();
                    return;
                }

                long eventsRaw = LoggerEvent.getRaw(events);

                Bson entitiesMap = Operators.ifNull("$blacklist.entities", Collections.EMPTY_LIST);
                Bson entityFilter = Operators.filter(entitiesMap, Operators.eq("$$this.id", id));
                Bson currentEvents = Operators.ifNull(Operators.first(Operators.map(entityFilter, "$$this.events")), 0L);

                List<Bson> update = List.of(Operators.set("blacklist.entities", Operators.concatArrays(List.of(Operators.mergeObjects(Operators.ifNull(Operators.first(entityFilter), new Document("id", id).append("type", category.getType())), new Document("events", Operators.toLong(Operators.bitwiseOr(eventsRaw, currentEvents))))), Operators.filter(entitiesMap, Operators.ne("$$this.id", id)))));

                event.getMongo().updateLogger(Filters.eq("channelId", effectiveChannel.getIdLong()), update, new UpdateOptions()).whenComplete((result, exception) -> {
                    if (ExceptionUtility.sendExceptionally(event, exception)) {
                        return;
                    }

                    if (result.getMatchedCount() == 0) {
                        event.replyFailure("You don't have a logger in " + effectiveChannel.getAsMention()).queue();
                        return;
                    }

                    if (result.getModifiedCount() == 0) {
                        event.replyFailure("That " + category.getName().toLowerCase() + " already has that blacklist event configuration").queue();
                        return;
                    }

                    event.replySuccess("That " + category.getName().toLowerCase() + " is now blacklisted from appearing in those events for that logger").queue();
                });
            });

            paged.onTimeout(() -> event.reply("Timed out :stopwatch:").queue());

            paged.execute(event);
        }

    }

}
