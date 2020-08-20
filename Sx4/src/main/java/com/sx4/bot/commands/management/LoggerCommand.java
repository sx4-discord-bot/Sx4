package com.sx4.bot.commands.management;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReturnDocument;
import com.sx4.bot.annotations.command.AuthorPermissions;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.Category;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.Database;
import com.sx4.bot.database.model.Operators;
import com.sx4.bot.entities.management.logger.LoggerEvent;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.ExceptionUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.TextChannel;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class LoggerCommand extends Sx4Command {

    public LoggerCommand() {
        super("logger");

        super.setAliases("logs", "log");
        super.setDescription("Logs server events which occur");
        super.setExamples("logger add", "logger remove", "logger list");
        super.setCategoryAll(Category.MANAGEMENT);
    }

    public void onCommand(Sx4CommandEvent event) {
        event.replyHelp().queue();
    }

    @Command(value="add", description="Adds a logger to a certain channel")
    @AuthorPermissions(permissions={Permission.MANAGE_SERVER})
    @Examples({"logger add #logs", "logger add"})
    public void add(Sx4CommandEvent event, @Argument(value="channel", endless=true, nullDefault=true) TextChannel channel) {
        channel = channel == null ? event.getTextChannel() : channel;
        String mention = channel.getAsMention();

        Bson loggers = Operators.ifNull("$logger.loggers", Collections.EMPTY_LIST);
        Bson filter = Operators.filter(loggers, Operators.eq("$$this.id", channel.getIdLong()));
        List<Bson> update = List.of(Operators.set("logger.loggers", Operators.cond(Operators.isEmpty(filter), Operators.concatArrays(List.of(new Document("id", channel.getIdLong())), Operators.filter(loggers, Operators.ne("$$this.id", channel.getIdLong()))), "$logger.loggers")));
        this.database.updateGuildById(event.getGuild().getIdLong(), update).whenComplete((result, exception) -> {
            if (ExceptionUtility.sendExceptionally(event, exception)) {
                return;
            }

            if (result.getModifiedCount() == 0) {
                event.reply("You already have a logger setup in " + mention + " " + this.config.getFailureEmote()).queue();
                return;
            }

            event.reply("You now have a logger setup in " + mention + " " + this.config.getSuccessEmote()).queue();
        });
    }

    @Command(value="remove", description="Removes a logger from a certain channel")
    @AuthorPermissions(permissions={Permission.MANAGE_SERVER})
    @Examples({"logger remove #logs", "logger remove"})
    public void remove(Sx4CommandEvent event, @Argument(value="channel", endless=true, nullDefault=true) TextChannel channel) {
        channel = channel == null ? event.getTextChannel() : channel;
        String mention = channel.getAsMention();

        List<Bson> update = List.of(Operators.set("logger.loggers", Operators.filter(Operators.ifNull("$logger.loggers", Collections.EMPTY_LIST), Operators.ne("$$this.id", channel.getIdLong()))));
        this.database.updateGuildById(event.getGuild().getIdLong(), update).whenComplete((result, exception) -> {
            if (ExceptionUtility.sendExceptionally(event, exception)) {
                return;
            }

            if (result.getModifiedCount() == 0) {
                event.reply("You don't have a logger in " + mention + " " + this.config.getFailureEmote()).queue();
                return;
            }

            event.reply("You no longer have a logger setup in " + mention + " " + this.config.getSuccessEmote()).queue();
        });
    }

    @Command(value="toggle", aliases={"enable", "disable"}, description="Toggles whether a logger should be enabled or disabled")
    @AuthorPermissions(permissions={Permission.MANAGE_SERVER})
    @Examples({"logger toggle #logs", "logger toggle"})
    public void toggle(Sx4CommandEvent event, @Argument(value="channel", endless=true, nullDefault=true) TextChannel channelArgument) {
        TextChannel channel = channelArgument == null ? event.getTextChannel() : channelArgument;

        Bson currentLoggers = Operators.ifNull("$logger.loggers", Collections.EMPTY_LIST);
        Bson filter = Operators.filter(currentLoggers, Operators.eq("$$this.id", channel.getIdLong()));
        List<Bson> update = List.of(Operators.set("logger.loggers", Operators.cond(Operators.isEmpty(filter), "$logger.loggers", Operators.concatArrays(Operators.cond(Operators.ifNull(Operators.first(Operators.map(filter, "$$this.enabled")), true), List.of(Operators.mergeObjects(Operators.first(filter), new Document("enabled", false))), List.of(Operators.removeObject(Operators.first(filter), "enabled"))), Operators.filter(filter, Operators.ne("$$this.id", channel.getIdLong()))))));

        FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER).projection(Projections.include("logger.loggers"));
        this.database.findAndUpdateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((data, exception) -> {
            if (ExceptionUtility.sendExceptionally(event, exception)) {
                return;
            }

            data = data == null ? Database.EMPTY_DOCUMENT : data;

            List<Document> loggers = data.getEmbedded(List.of("logger", "loggers"), Collections.emptyList());
            Document logger = loggers.stream()
                .filter(d -> d.getLong("id") == channel.getIdLong())
                .findFirst()
                .orElse(null);

            if (logger == null) {
                event.reply("You don't have a logger in " + channel.getAsMention() + " " + this.config.getFailureEmote()).queue();
                return;
            }

            event.replyFormat("The logger in %s is now **%s** %s", channel.getAsMention(), logger.get("enabled", true) ? "enabled" : "disabled", this.config.getSuccessEmote()).queue();
        });
    }

    public class EventsCommand extends Sx4Command {

        public EventsCommand() {
            super("events");

            super.setAliases("event");
            super.setDescription("Set what events you want to be sent with a logger");
            super.setExamples("logger events add", "logger events remove", "logger events set");
        }

        public void onCommand(Sx4CommandEvent event) {
            event.replyHelp().queue();
        }

        @Command(value="add", description="Adds an event the logger should send")
        @AuthorPermissions(permissions={Permission.MANAGE_SERVER})
        @Examples({"logger events add #logs MESSAGE_DELETE", "logger events add MESSAGE_DELETE MESSAGE_UPDATE"})
        public void add(Sx4CommandEvent event, @Argument(value="channel", nullDefault=true) TextChannel channelArgument, @Argument(value="events") LoggerEvent... events) {
            TextChannel channel = channelArgument == null ? event.getTextChannel() : channelArgument;

            long raw = LoggerEvent.getRaw(Arrays.asList(events));

            Bson currentLoggers = Operators.ifNull("$logger.loggers", Collections.EMPTY_LIST);
            Bson filter = Operators.filter(currentLoggers, Operators.eq("$$this.id", channel.getIdLong()));
            Bson currentEvents = Operators.ifNull(Operators.first(Operators.map(filter, "$$this.events")), LoggerEvent.ALL);
            List<Bson> update = List.of(Operators.set("logger.loggers", Operators.cond(Operators.or(Operators.isEmpty(filter), Operators.eq(Operators.bitwiseAnd(currentEvents, raw), raw)), "$logger.loggers", Operators.concatArrays(List.of(Operators.mergeObjects(Operators.first(filter), new Document("events", Operators.toLong(Operators.bitwiseOr(raw, currentEvents))))), Operators.filter(currentLoggers, Operators.ne("$$this.id", channel.getIdLong()))))));

            FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().projection(Projections.include("logger.loggers")).returnDocument(ReturnDocument.BEFORE);
            this.database.findAndUpdateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((data, exception) -> {
                if (ExceptionUtility.sendExceptionally(event, exception)) {
                    return;
                }

                data = data == null ? Database.EMPTY_DOCUMENT : data;

                List<Document> loggers = data.getEmbedded(List.of("logger", "loggers"), Collections.emptyList());
                Document logger = loggers.stream()
                    .filter(d -> d.getLong("id") == channel.getIdLong())
                    .findFirst()
                    .orElse(null);

                if (logger == null) {
                    event.reply("You don't have a logger in " + channel.getAsMention() + " " + this.config.getFailureEmote()).queue();
                    return;
                }

                if ((logger.get("events", LoggerEvent.ALL) & raw) == raw) {
                    event.reply("That logger already has those events " + this.config.getFailureEmote()).queue();
                    return;
                }

                event.reply("That logger now has those events enabled " + this.config.getSuccessEmote()).queue();
            });
        }

        @Command(value="remove", description="Removes an event from a logger")
        @AuthorPermissions(permissions={Permission.MANAGE_SERVER})
        @Examples({"logger events remove #logs MESSAGE_DELETE", "logger events remove MESSAGE_DELETE MESSAGE_UPDATE"})
        public void remove(Sx4CommandEvent event, @Argument(value="channel", nullDefault=true) TextChannel channelArgument, @Argument(value="events") LoggerEvent... events) {
            TextChannel channel = channelArgument == null ? event.getTextChannel() : channelArgument;

            long raw = LoggerEvent.getRaw(Arrays.asList(events));

            Bson currentLoggers = Operators.ifNull("$logger.loggers", Collections.EMPTY_LIST);
            Bson filter = Operators.filter(currentLoggers, Operators.eq("$$this.id", channel.getIdLong()));
            Bson currentEvents = Operators.ifNull(Operators.first(Operators.map(filter, "$$this.events")), LoggerEvent.ALL);
            Bson newEvents = Operators.toLong(Operators.bitwiseAnd(currentEvents, ~raw));
            List<Bson> update = List.of(Operators.set("logger.loggers", Operators.cond(Operators.or(Operators.isEmpty(filter), Operators.eq(newEvents, currentEvents)), "$logger.loggers", Operators.concatArrays(List.of(Operators.mergeObjects(Operators.first(filter), new Document("events", newEvents))), Operators.filter(currentLoggers, Operators.ne("$$this.id", channel.getIdLong()))))));

            FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().projection(Projections.include("logger.loggers")).returnDocument(ReturnDocument.BEFORE);
            this.database.findAndUpdateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((data, exception) -> {
                if (ExceptionUtility.sendExceptionally(event, exception)) {
                    return;
                }

                data = data == null ? Database.EMPTY_DOCUMENT : data;

                List<Document> loggers = data.getEmbedded(List.of("logger", "loggers"), Collections.emptyList());
                Document logger = loggers.stream()
                    .filter(d -> d.getLong("id") == channel.getIdLong())
                    .findFirst()
                    .orElse(null);

                if (logger == null) {
                    event.reply("You don't have a logger in " + channel.getAsMention() + " " + this.config.getFailureEmote()).queue();
                    return;
                }

                long eventsData = logger.get("events", LoggerEvent.ALL);
                if ((eventsData & ~raw) == eventsData) {
                    event.reply("That logger doesn't have any of those events " + this.config.getFailureEmote()).queue();
                    return;
                }

                event.reply("That logger now has those events disabled " + this.config.getSuccessEmote()).queue();
            });
        }

        @Command(value="set", description="Sets the events a logger should use")
        @AuthorPermissions(permissions={Permission.MANAGE_SERVER})
        @Examples({"logger events set #logs MESSAGE_DELETE", "logger events set MESSAGE_DELETE MESSAGE_UPDATE"})
        public void set(Sx4CommandEvent event, @Argument(value="channel", nullDefault=true) TextChannel channel, @Argument(value="events") LoggerEvent... events) {
            channel = channel == null ? event.getTextChannel() : channel;
            String mention = channel.getAsMention();

            Bson currentLoggers = Operators.ifNull("$logger.loggers", Collections.EMPTY_LIST);
            Bson filter = Operators.filter(currentLoggers, Operators.eq("$$this.id", channel.getIdLong()));
            List<Bson> update = List.of(Operators.set("logger.loggers", Operators.cond(Operators.isEmpty(filter), "$logger.loggers", Operators.concatArrays(List.of(Operators.mergeObjects(Operators.first(filter), new Document("events", LoggerEvent.getRaw(Arrays.asList(events))))), Operators.filter(currentLoggers, Operators.ne("$$this.id", channel.getIdLong()))))));
            this.database.updateGuildById(event.getGuild().getIdLong(), update).whenComplete((result, exception) -> {
                if (ExceptionUtility.sendExceptionally(event, exception)) {
                    return;
                }

                if (result.getModifiedCount() == 0) {
                    event.reply("You don't have a logger in " + mention + " " + this.config.getFailureEmote()).queue();
                    return;
                }

                event.reply("That logger will now only send those events " + this.config.getSuccessEmote()).queue();
            });
        }

        @Command(value="list", description="Lists all the events you can use")
        @Examples({"logger events list"})
        public void list(Sx4CommandEvent event) {
            PagedResult<LoggerEvent> paged = new PagedResult<>(Arrays.asList(LoggerEvent.values()))
                .setPerPage(15)
                .setAuthor("Events List", null, event.getGuild().getIconUrl())
                .setIndexed(false);

            paged.execute(event);
        }

    }

    public class BlacklistCommand extends Sx4Command {

        public BlacklistCommand() {
            super("blacklist");

            super.setDescription("Blacklist certain entities from being able to appear in logs");
            super.setExamples("logger blacklist set", "logger blacklist add", "logger blacklist remove");
        }

        public void onCommand(Sx4CommandEvent event) {
            event.replyHelp().queue();
        }

        @Command(value="set", description="Set what events a certain entity should be blacklisted from")
        @AuthorPermissions(permissions={Permission.MANAGE_SERVER})
        @Examples({"logger blacklist set "})
        public void set(Sx4CommandEvent event) {
            
        }

    }

}
