package com.sx4.bot.commands.management;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.sx4.bot.annotations.command.AuthorPermissions;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.management.MediaType;
import com.sx4.bot.paged.MessagePagedResult;
import com.sx4.bot.utility.ExceptionUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;

public class MediaModeCommand extends Sx4Command {

	public MediaModeCommand() {
		super("media mode", 349);

		super.setDescription("Set a channel up where only media can be sent in them");
		super.setAliases("image mode");
		super.setExamples("media mode add", "media mode remove", "media mode types");
		super.setCategoryAll(ModuleCategory.MANAGEMENT);
	}

	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}

	@Command(value="add", description="Add a channel as a media only channel")
	@CommandId(350)
	@Examples({"media mode add", "media mode add #media"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void add(Sx4CommandEvent event, @Argument(value="channel", endless=true, nullDefault=true) GuildMessageChannel channel) {
		Document data = new Document("channelId", channel.getIdLong())
			.append("guildId", event.getGuild().getIdLong());

		event.getMongo().insertMediaChannel(data).whenComplete((result, exception) -> {
			Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
			if (cause instanceof MongoWriteException && ((MongoWriteException) cause).getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
				event.replyFailure("That channel is already a media only channel").queue();
				return;
			}

			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			event.replySuccess(channel.getAsMention() + " is now a media only channel").queue();
		});
	}

	@Command(value="remove", aliases={"delete"}, description="Removes a channel as media only channel")
	@CommandId(351)
	@Examples({"media mode remove", "media mode remove #media"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void remove(Sx4CommandEvent event, @Argument(value="channel", endless=true, nullDefault=true) GuildMessageChannel channel) {
		event.getMongo().deleteMediaChannel(Filters.eq("channelId", channel.getIdLong())).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getDeletedCount() == 0) {
				event.replyFailure("That channel is not a media only channel").queue();
				return;
			}

			event.replySuccess(channel.getAsMention() + " is no longer a media only channel").queue();
		});
	}

	@Command(value="types", description="Sets what types are allowed to be sent in the channel")
	@CommandId(352)
	@Examples({"media mode types PNG", "media mode types #media JPG PNG", "media mode types GIF MP4"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void types(Sx4CommandEvent event, @Argument(value="channel", nullDefault=true) GuildMessageChannel channel, @Argument(value="types") MediaType... types) {
		event.getMongo().updateMediaChannel(Filters.eq("channelId", channel.getIdLong()), Updates.set("types", MediaType.getRaw(types)), new UpdateOptions()).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getMatchedCount() == 0) {
				event.replyFailure("Media mode has not been setup in that channel").queue();
				return;
			}

			if (result.getModifiedCount() == 0) {
				event.replyFailure("That media only channel already had those types set").queue();
				return;
			}

			event.replySuccess("Types for that media only channel have been updated").queue();
		});
	}

	@Command(value="list", description="Lists all the media only channels in the server")
	@CommandId(353)
	@Examples({"media mode list"})
	@AuthorPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void list(Sx4CommandEvent event) {
		List<Document> mediaChannels = event.getMongo().getMediaChannels(Filters.eq("guildId", event.getGuild().getIdLong()), Projections.include("channelId")).into(new ArrayList<>());
		if (mediaChannels.isEmpty()) {
			event.replyFailure("There are no media channels setup in this server").queue();
			return;
		}

		MessagePagedResult<Document> paged = new MessagePagedResult.Builder<>(event.getBot(), mediaChannels)
			.setAuthor("Media Only Channels", null, event.getGuild().getIconUrl())
			.setIndexed(false)
			.setSelect()
			.setDisplayFunction(data -> "<#" + data.getLong("channelId") + ">")
			.build();

		paged.execute(event);
	}

}
