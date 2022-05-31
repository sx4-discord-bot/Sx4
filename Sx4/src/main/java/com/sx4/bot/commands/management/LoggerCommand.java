package com.sx4.bot.commands.management;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.*;
import com.sx4.bot.annotations.argument.ImageUrl;
import com.sx4.bot.annotations.command.*;
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
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.BaseGuildMessageChannel;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
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
    public void add(Sx4CommandEvent event, @Argument(value="channel", endless=true, nullDefault=true) BaseGuildMessageChannel channel) {
        MessageChannel messageChannel = event.getChannel();
        if (channel == null && !(messageChannel instanceof BaseGuildMessageChannel)) {
            event.replyFailure("You cannot use this channel type").queue();
            return;
        }

        BaseGuildMessageChannel effectiveChannel = channel == null ? (BaseGuildMessageChannel) messageChannel : channel;

        List<Bson> guildPipeline = List.of(
            Aggregates.project(Projections.fields(Projections.computed("premium", Operators.lt(Operators.nowEpochSecond(), Operators.ifNull("$premium.endAt", 0L))), Projections.computed("guildId", "$_id"))),
            Aggregates.match(Filters.eq("guildId", event.getGuild().getIdLong()))
        );

        List<Bson> pipeline = List.of(
            Aggregates.match(Filters.and(Filters.eq("guildId", event.getGuild().getIdLong()), Filters.exists("enabled", false))),
            Aggregates.limit(25),
            Aggregates.group(null, Accumulators.sum("count", 1)),
            Aggregates.unionWith("guilds", guildPipeline),
            Aggregates.group(null, Accumulators.max("count", "$count"), Accumulators.max("premium", "$premium")),
            Aggregates.project(Projections.fields(Projections.computed("premium", Operators.ifNull("$premium", false)), Projections.computed("count", Operators.ifNull("$count", 0))))
        );

        event.getMongo().aggregateLoggers(pipeline).thenCompose(documents -> {
            Document counter = documents.isEmpty() ? null : documents.get(0);

            int count = counter == null ? 0 : counter.getInteger("count");
            if (counter != null && count >= 3 && !counter.getBoolean("premium")) {
                event.replyFailure("You need to have Sx4 premium to have more than 3 enabled loggers, you can get premium at <https://www.patreon.com/Sx4>").queue();
                return CompletableFuture.completedFuture(null);
            }

            if (count == 25) {
                event.replyFailure("You can not have any more than 25 loggers").queue();
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
    public void remove(Sx4CommandEvent event, @Argument(value="channel", endless=true, nullDefault=true) BaseGuildMessageChannel channel) {
        MessageChannel messageChannel = event.getChannel();
        if (channel == null && !(messageChannel instanceof BaseGuildMessageChannel)) {
            event.replyFailure("You cannot use this channel type").queue();
            return;
        }

        BaseGuildMessageChannel effectiveChannel = channel == null ? (BaseGuildMessageChannel) messageChannel : channel;

        FindOneAndDeleteOptions options = new FindOneAndDeleteOptions().projection(Projections.include("webhook.id"));
        event.getMongo().findAndDeleteLogger(Filters.eq("channelId", effectiveChannel.getIdLong()), options).whenComplete((data, exception) -> {
            if (ExceptionUtility.sendExceptionally(event, exception)) {
                return;
            }

            if (data == null) {
                event.replyFailure("You don't have a logger in " + effectiveChannel.getAsMention()).queue();
                return;
            }

            event.getBot().getLoggerHandler().removeManager(effectiveChannel.getIdLong());

            Document webhook = data.get("webhook", Document.class);
            if (webhook != null) {
                effectiveChannel.deleteWebhookById(Long.toString(webhook.getLong("id"))).queue(null, ErrorResponseException.ignore(ErrorResponse.UNKNOWN_WEBHOOK));
            }

            event.replySuccess("You no longer have a logger setup in " + effectiveChannel.getAsMention()).queue();
        });
    }

    @Command(value="toggle", aliases={"enable", "disable"}, description="Toggles the state of a logger")
    @CommandId(56)
    @AuthorPermissions(permissions={Permission.MANAGE_SERVER})
    @Examples({"logger toggle #logs", "logger toggle"})
    public void toggle(Sx4CommandEvent event, @Argument(value="channel", endless=true, nullDefault=true) BaseGuildMessageChannel channel) {
        MessageChannel messageChannel = event.getChannel();
        if (channel == null && !(messageChannel instanceof BaseGuildMessageChannel)) {
            event.replyFailure("You cannot use this channel type").queue();
            return;
        }

        BaseGuildMessageChannel effectiveChannel = channel == null ? (BaseGuildMessageChannel) messageChannel : channel;

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
            Aggregates.project(Projections.fields(Projections.computed("premium", Operators.ifNull("$premium", false)), Projections.computed("count", Operators.size(Operators.ifNull("$loggers", Collections.EMPTY_LIST))), Projections.computed("disabled", Operators.isEmpty(Operators.filter(Operators.ifNull("$loggers", Collections.EMPTY_LIST), Operators.eq("$$this.channelId", effectiveChannel.getIdLong()))))))
        );

        event.getMongo().aggregateLoggers(pipeline).thenCompose(documents -> {
            Document data = documents.isEmpty() ? null : documents.get(0);

            boolean disabled = data == null || data.getBoolean("disabled");
            int count = data == null ? 0 : data.getInteger("count");
            if (data != null && disabled && count >= 3 && !data.getBoolean("premium")) {
                throw new IllegalArgumentException("You need to have Sx4 premium to have more than 3 enabled loggers, you can get premium at <https://www.patreon.com/Sx4>");
            }

            if (count >= 25) {
                throw new IllegalArgumentException("You can not have any more than 25 enabled loggers");
            }

            List<Bson> update = List.of(Operators.set("enabled", Operators.cond(Operators.exists("$enabled"), Operators.REMOVE, false)));
            FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER).projection(Projections.include("enabled"));

            return event.getMongo().findAndUpdateLogger(Filters.eq("channelId", effectiveChannel.getIdLong()), update, options);
        }).whenComplete((data, exception) -> {
            Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
            if (cause instanceof IllegalArgumentException) {
                event.replyFailure(cause.getMessage()).queue();
                return;
            }

            if (ExceptionUtility.sendExceptionally(event, exception)) {
                return;
            }

            if (data == null) {
                event.replyFailure("There is not a logger in that channel").queue();
                return;
            }

            event.replySuccess("The logger in " + effectiveChannel.getAsMention() + " is now **" + (data.get("enabled", true) ? "enabled" : "disabled") + "**").queue();
        });
    }

    @Command(value="name", description="Set the name of the webhook that sends logs")
    @CommandId(423)
    @Examples({"logger name #logs Logs", "logger name Logger"})
    @Premium
    @AuthorPermissions(permissions={Permission.MANAGE_SERVER})
    public void name(Sx4CommandEvent event, @Argument(value="channel", nullDefault=true) BaseGuildMessageChannel channel, @Argument(value="name", endless=true) String name) {
        MessageChannel messageChannel = event.getChannel();
        if (channel == null && !(messageChannel instanceof BaseGuildMessageChannel)) {
            event.replyFailure("You cannot use this channel type").queue();
            return;
        }

        BaseGuildMessageChannel effectiveChannel = channel == null ? (BaseGuildMessageChannel) messageChannel : channel;

        event.getMongo().updateLogger(Filters.eq("channelId", effectiveChannel.getIdLong()), Updates.set("webhook.name", name)).whenComplete((result, exception) -> {
            if (ExceptionUtility.sendExceptionally(event, exception)) {
                return;
            }

            if (result.getModifiedCount() == 0) {
                event.replyFailure("Your webhook name for that logger was already set to that").queue();
                return;
            }

            event.replySuccess("Your webhook name has been updated for that logger, this only works with premium <https://patreon.com/Sx4>").queue();
        });
    }

    @Command(value="avatar", description="Set the avatar of the webhook that sends logs")
    @CommandId(424)
    @Examples({"logger avatar #logs Shea#6653", "logger avatar https://i.imgur.com/i87lyNO.png"})
    @Premium
    @AuthorPermissions(permissions={Permission.MANAGE_SERVER})
    public void avatar(Sx4CommandEvent event, @Argument(value="channel", nullDefault=true) BaseGuildMessageChannel channel, @Argument(value="avatar", endless=true, acceptEmpty=true) @ImageUrl String url) {
        MessageChannel messageChannel = event.getChannel();
        if (channel == null && !(messageChannel instanceof BaseGuildMessageChannel)) {
            event.replyFailure("You cannot use this channel type").queue();
            return;
        }

        BaseGuildMessageChannel effectiveChannel = channel == null ? (BaseGuildMessageChannel) messageChannel : channel;

        event.getMongo().updateLogger(Filters.eq("channelId", effectiveChannel.getIdLong()), Updates.set("webhook.avatar", url)).whenComplete((result, exception) -> {
            if (ExceptionUtility.sendExceptionally(event, exception)) {
                return;
            }

            if (result.getModifiedCount() == 0) {
                event.replyFailure("Your webhook avatar for that logger was already set to that").queue();
                return;
            }

            event.replySuccess("Your webhook avatar has been updated for that logger, this only works with premium <https://patreon.com/Sx4>").queue();
        });
    }

    @Command(value="list", description="List and get info about loggers in the current server")
    @CommandId(458)
    @Examples({"logger list"})
    @BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
    public void list(Sx4CommandEvent event, @Argument(value="channel", endless=true, nullDefault=true) BaseGuildMessageChannel channel) {
        Bson filter = channel == null ? Filters.eq("guildId", event.getGuild().getIdLong()) : Filters.eq("channelId", channel.getIdLong());

        List<Document> loggers = event.getMongo().getLoggers(filter, MongoDatabase.EMPTY_DOCUMENT).into(new ArrayList<>());
        if (loggers.isEmpty()) {
            event.replyFailure(channel == null ? "There are not any loggers setup" : "There is not a logger setup in " + channel.getAsMention()).queue();
            return;
        }

        PagedResult<Document> paged = new PagedResult<>(event.getBot(), loggers)
            .setAuthor("Loggers", null, event.getGuild().getIconUrl())
            .setAutoSelect(true)
            .setDisplayFunction(data -> "<#" + data.getLong("channelId") + ">");

        paged.onSelect(select -> {
            Document data = select.getSelected();

            EnumSet<LoggerEvent> events = LoggerEvent.getEvents(data.get("events", LoggerEvent.ALL));

            PagedResult<LoggerEvent> loggerPaged = new PagedResult<>(event.getBot(), new ArrayList<>(events))
                .setSelect()
                .setPerPage(20)
                .setCustomFunction(page -> {
                   EmbedBuilder embed = new EmbedBuilder()
                       .setAuthor("Logger Settings", null, event.getGuild().getIconUrl())
                       .setTitle("Page " + page.getPage() + "/" + page.getMaxPage())
                       .setFooter(PagedResult.DEFAULT_FOOTER_TEXT)
                       .addField("Status", data.getBoolean("enabled", true) ? "Enabled" : "Disabled", true)
                       .addField("Channel", "<#" + data.getLong("channelId") + ">", true);

                   StringJoiner content = new StringJoiner("\n");
                   page.forEach((loggerEvent, index) -> content.add(loggerEvent.name()));

                   embed.addField("Enabled Events", content.toString(), false);

                   return new MessageBuilder().setEmbeds(embed.build());
                });

            loggerPaged.execute(event);
        });

        paged.execute(event);
    }

    public static class EventsCommand extends Sx4Command {

        public EventsCommand() {
            super("events", 57);

            super.setAliases("event");
            super.setDescription("Set what events you want to be sent with a logger");
            super.setExamples("logger events add", "logger events remove", "logger events set");
            super.setCategoryAll(ModuleCategory.MANAGEMENT);
        }

        public void onCommand(Sx4CommandEvent event) {
            event.replyHelp().queue();
        }

        @Command(value="add", description="Adds an event the logger should send")
        @CommandId(58)
        @AuthorPermissions(permissions={Permission.MANAGE_SERVER})
        @Examples({"logger events add #logs MESSAGE_DELETE", "logger events add MESSAGE_DELETE MESSAGE_UPDATE"})
        public void add(Sx4CommandEvent event, @Argument(value="channel", nullDefault=true) BaseGuildMessageChannel channel, @Argument(value="events") LoggerEvent... events) {
            MessageChannel messageChannel = event.getChannel();
            if (channel == null && !(messageChannel instanceof BaseGuildMessageChannel)) {
                event.replyFailure("You cannot use this channel type").queue();
                return;
            }

            BaseGuildMessageChannel effectiveChannel = channel == null ? (BaseGuildMessageChannel) messageChannel : channel;

            long raw = LoggerEvent.getRaw(events);
            List<Bson> update = List.of(Operators.set("events", Operators.bitwiseOr(Operators.ifNull("$events", LoggerEvent.ALL), raw)));

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
        public void remove(Sx4CommandEvent event, @Argument(value="channel", nullDefault=true) BaseGuildMessageChannel channel, @Argument(value="events") LoggerEvent... events) {
            MessageChannel messageChannel = event.getChannel();
            if (channel == null && !(messageChannel instanceof BaseGuildMessageChannel)) {
                event.replyFailure("You cannot use this channel type").queue();
                return;
            }

            BaseGuildMessageChannel effectiveChannel = channel == null ? (BaseGuildMessageChannel) messageChannel : channel;

            long raw = LoggerEvent.getRaw(events);
            List<Bson> update = List.of(Operators.set("events", Operators.bitwiseAndNot(Operators.ifNull("$events", LoggerEvent.ALL), raw)));

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
        public void set(Sx4CommandEvent event, @Argument(value="channel", nullDefault=true) BaseGuildMessageChannel channel, @Argument(value="events") LoggerEvent... events) {
            MessageChannel messageChannel = event.getChannel();
            if (channel == null && !(messageChannel instanceof BaseGuildMessageChannel)) {
                event.replyFailure("You cannot use this channel type").queue();
                return;
            }

            BaseGuildMessageChannel effectiveChannel = channel == null ? (BaseGuildMessageChannel) messageChannel : channel;

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
            super.setCategoryAll(ModuleCategory.MANAGEMENT);
        }

        public void onCommand(Sx4CommandEvent event) {
            event.replyHelp().queue();
        }

        @Command(value="set", description="Set what events a certain entity should be blacklisted from")
        @CommandId(63)
        @AuthorPermissions(permissions={Permission.MANAGE_SERVER})
        @Examples({"logger blacklist set #logs @Shea#6653 MESSAGE_DELETE", "logger blacklist set #logs @Members MESSAGE_UPDATE MESSAGE_DELETE", "logger blacklist set #logs #channel TEXT_CHANNEL_OVERRIDE_UPDATE"})
        public void set(Sx4CommandEvent event, @Argument(value="channel", nullDefault=true) BaseGuildMessageChannel channel, @Argument(value="user | role | channel") String query, @Argument(value="events") LoggerEvent... events) {
            MessageChannel messageChannel = event.getChannel();
            if (channel == null && !(messageChannel instanceof BaseGuildMessageChannel)) {
                event.replyFailure("You cannot use this channel type").queue();
                return;
            }

            BaseGuildMessageChannel effectiveChannel = channel == null ? (BaseGuildMessageChannel) messageChannel : channel;

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
        public void add(Sx4CommandEvent event, @Argument(value="channel", nullDefault=true) BaseGuildMessageChannel channel, @Argument(value="user | role | channel") String query, @Argument(value="events") LoggerEvent... events) {
            MessageChannel messageChannel = event.getChannel();
            if (channel == null && !(messageChannel instanceof BaseGuildMessageChannel)) {
                event.replyFailure("You cannot use this channel type").queue();
                return;
            }

            BaseGuildMessageChannel effectiveChannel = channel == null ? (BaseGuildMessageChannel) messageChannel : channel;

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

        @Command(value="remove", description="Removes events from being blacklisted from a certain entity")
        @CommandId(467)
        @AuthorPermissions(permissions={Permission.MANAGE_SERVER})
        @Examples({"logger blacklist remove #logs @Shea#6653 MESSAGE_DELETE", "logger blacklist remove #logs @Members MESSAGE_UPDATE MESSAGE_DELETE", "logger blacklist remove #logs #channel TEXT_CHANNEL_OVERRIDE_UPDATE"})
        public void remove(Sx4CommandEvent event, @Argument(value="channel", nullDefault=true) BaseGuildMessageChannel channel, @Argument(value="user | role | channel") String query, @Argument(value="events") LoggerEvent... events) {
            MessageChannel messageChannel = event.getChannel();
            if (channel == null && !(messageChannel instanceof BaseGuildMessageChannel)) {
                event.replyFailure("You cannot use this channel type").queue();
                return;
            }

            BaseGuildMessageChannel effectiveChannel = channel == null ? (BaseGuildMessageChannel) messageChannel : channel;

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

                List<Bson> update = List.of(Operators.set("blacklist.entities", Operators.let(new Document("newEvents", Operators.toLong(Operators.bitwiseAndNot(currentEvents, eventsRaw))), Operators.concatArrays(Operators.cond(Operators.eq("$$newEvents", 0L), Collections.EMPTY_LIST, List.of(Operators.mergeObjects(Operators.ifNull(Operators.first(entityFilter), new Document("id", id).append("type", category.getType())), new Document("events", "$$newEvents")))), Operators.filter(entitiesMap, Operators.ne("$$this.id", id))))));

                event.getMongo().updateLogger(Filters.eq("channelId", effectiveChannel.getIdLong()), update, new UpdateOptions()).whenComplete((result, exception) -> {
                    if (ExceptionUtility.sendExceptionally(event, exception)) {
                        return;
                    }

                    if (result.getMatchedCount() == 0) {
                        event.replyFailure("You don't have a logger in " + effectiveChannel.getAsMention()).queue();
                        return;
                    }

                    if (result.getModifiedCount() == 0) {
                        event.replyFailure("That " + category.getName().toLowerCase() + " doesn't have any of those events blacklisted").queue();
                        return;
                    }

                    event.replySuccess("That " + category.getName().toLowerCase() + " is no longer blacklisted from appearing in those events for that logger").queue();
                });
            });

            paged.onTimeout(() -> event.reply("Timed out :stopwatch:").queue());

            paged.execute(event);
        }

    }

}
