package com.sx4.bot.commands.management;

import club.minnced.discord.webhook.WebhookClient;
import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReturnDocument;
import com.sx4.bot.annotations.argument.ImageUrl;
import com.sx4.bot.annotations.argument.Options;
import com.sx4.bot.annotations.command.AuthorPermissions;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.annotations.command.Premium;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.Database;
import com.sx4.bot.database.model.Operators;
import com.sx4.bot.entities.argument.Alternative;
import com.sx4.bot.entities.argument.Range;
import com.sx4.bot.entities.mod.ModLog;
import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.entities.mod.action.Action;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.OperatorsUtility;
import com.sx4.bot.waiter.Waiter;
import com.sx4.bot.waiter.exception.CancelException;
import com.sx4.bot.waiter.exception.TimeoutException;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;

public class ModLogCommand extends Sx4Command {

	public ModLogCommand() {
		super("modlog", 65);
		
		super.setAliases("modlogs", "mod log", "mod logs");
		super.setDescription("Setup the mod log in your server to log mod actions which happen within the server");
		super.setExamples("modlog toggle", "modlog channel", "modlog case");
		super.setCategoryAll(ModuleCategory.MANAGEMENT);
	}
	
	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}
	
	@Command(value="toggle", description="Turn mod logs on/off in your server")
	@CommandId(66)
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	@Examples({"modlog toggle"})
	public void toggle(Sx4CommandEvent event) {
		List<Bson> update = List.of(Operators.set("modLog.enabled", Operators.cond("$modLog.enabled", Operators.REMOVE, true)));
		event.getDatabase().findAndUpdateGuildById(event.getGuild().getIdLong(), Projections.include("modLog.enabled"), update).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}
			
			event.replySuccess("Mod logs are now **" + (data.getEmbedded(List.of("modLog", "enabled"), false) ? "enabled" : "disabled") + "**").queue();
		});
	}
	
	@Command(value="channel", description="Sets the channel which mod logs are sent to")
	@CommandId(67)
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	@Examples({"modlog channel", "modlog channel #mod-logs", "modlog channel reset"})
	public void channel(Sx4CommandEvent event, @Argument(value="channel | reset", endless=true, nullDefault=true) @Options("reset") Alternative<TextChannel> option) {
		TextChannel channel = option == null ? event.getTextChannel() : option.isAlternative() ? null : option.getValue();

		List<Bson> update = List.of(Operators.set("modLog.channelId", channel == null ? Operators.REMOVE : channel.getIdLong()), Operators.unset("modLog.webhook.id"), Operators.unset("modLog.webhook.token"));
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.include("modLog.channelId")).upsert(true);

		event.getDatabase().findAndUpdateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			long channelId = data == null ? 0L : data.getEmbedded(List.of("modLog", "channelId"), 0L);

			if ((channel == null ? 0L : channel.getIdLong()) == channelId) {
				event.replyFailure("The mod log channel is already " + (channel == null ? "unset" : "set to " + channel.getAsMention())).queue();
				return;
			}

			TextChannel oldChannel = channelId == 0L ? null : event.getGuild().getTextChannelById(channelId);
			if (oldChannel != null) {
				WebhookClient oldWebhook = event.getBot().getModLogManager().removeWebhook(channelId);
				if (oldWebhook != null) {
					oldChannel.deleteWebhookById(String.valueOf(oldWebhook.getId())).queue();
				}
			}
			
			event.replySuccess("The mod log channel has been " + (channel == null ? "unset" : "set to " + channel.getAsMention())).queue();
		});
	}
	
	@Command(value="case", description="Edit the reason of a mod log case")
	@CommandId(68)
	@Examples({"modlog case 5e45ce6d3688b30ee75201ae Spamming", "modlog case 5fc24ea34854845b7c74e7f4-5fc24ea64854845b7c74e7f6 template:tos", "modlog case 5e45ce6d3688b30ee75201ae,5e45ce6d3688b30ee75201ab t:tos and Spamming"})
	public void case_(Sx4CommandEvent event, @Argument(value="id(s)") Range<ObjectId> range, @Argument(value="reason", endless=true) Reason reason) {
		List<Bson> or = new ArrayList<>();
		for (Pair<ObjectId, ObjectId> r : range.getRanges()) {
			or.add(Operators.and(Operators.gte(Operators.objectIdToEpochSecond("$_id"), r.getLeft().getTimestamp()), Operators.lte(Operators.objectIdToEpochSecond("$_id"), r.getRight().getTimestamp())));
		}

		for (ObjectId r : range.getObjects()) {
			or.add(Operators.eq("$_id", r));
		}

		long authorId = event.getAuthor().getIdLong();

		List<Bson> update = List.of(Operators.set("reason", Operators.cond(Operators.and(Operators.or(Operators.eq("$moderatorId", authorId), event.getMember().hasPermission(Permission.ADMINISTRATOR)), Operators.or(or)), reason.getParsed(), "$reason")));
		event.getDatabase().updateManyModLogs(update).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			long modified = result.getModifiedCount();
			if (modified == 0) {
				event.replyFailure("You were unable to update any of those mod logs or you provided an invalid range").queue();
				return;
			}

			event.replyFormat("Updated **%d** case%s %s", modified, modified == 1 ? "" : "s", event.getConfig().getSuccessEmote()).queue();
		});
	}

	@Command(value="delete", aliases={"remove"}, description="Deletes a mod log from the  server")
	@CommandId(69)
	@Examples({"modlog delete 5e45ce6d3688b30ee75201ae", "modlog delete all"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void remove(Sx4CommandEvent event, @Argument(value="id | all") @Options("all") Alternative<ObjectId> option) {
		User author = event.getAuthor();

		if (option.isAlternative()) {
			event.reply(author.getName() + ", are you sure you want to delete **all** the suggestions in this server? (Yes or No)").submit().thenCompose($ -> {
				return new Waiter<>(event.getBot(), MessageReceivedEvent.class)
					.setPredicate(messageEvent -> messageEvent.getMessage().getContentRaw().equalsIgnoreCase("yes"))
					.setOppositeCancelPredicate()
					.setTimeout(30)
					.setUnique(author.getIdLong(), event.getChannel().getIdLong())
					.start();
			})
			.thenCompose(messageEvent -> event.getDatabase().deleteManyModLogs(Filters.eq("guildId", event.getGuild().getIdLong())))
			.whenComplete((result, exception) -> {
				Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
				if (cause instanceof CancelException) {
					event.replySuccess("Cancelled").queue();
					return;
				} else if (cause instanceof TimeoutException) {
					event.reply("Timed out :stopwatch:").queue();
					return;
				} else if (ExceptionUtility.sendExceptionally(event, cause)) {
					return;
				}

				if (result.getDeletedCount() == 0) {
					event.replyFailure("There are no mod logs in this server").queue();
					return;
				}

				event.replySuccess("All your mod logs have been deleted").queue();
			});
		} else {
			ObjectId id = option.getValue();

			event.getDatabase().findAndDeleteModLogById(id).whenComplete((data, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (data == null) {
					event.replyFailure("I could not find that mod log").queue();
					return;
				}

				WebhookClient webhook = event.getBot().getModLogManager().getWebhook(data.getLong("channelId"));
				if (webhook != null) {
					webhook.delete(data.getLong("messageId"));
				}

				event.replySuccess("That mod log has been deleted").queue();
			});
		}
	}
	
	@Command(value="view", aliases={"viewcase", "view case", "list"}, description="View a mod log case from the server")
	@CommandId(70)
	@Examples({"modlog view 5e45ce6d3688b30ee75201ae", "modlog view"})
	public void view(Sx4CommandEvent event, @Argument(value="id", nullDefault=true) ObjectId id) {
		Bson projection = Projections.include("moderatorId", "reason", "targetId", "action");
		if (id == null) {
			List<Document> allData = event.getDatabase().getModLogs(Filters.eq("guildId", event.getGuild().getIdLong()), projection).into(new ArrayList<>());
			if (allData.isEmpty()) {
				event.replyFailure("There are no mod logs in this server").queue();
				return;
			}

			PagedResult<Document> paged = new PagedResult<>(event.getBot(), allData)
				.setDisplayFunction(data -> {
					long targetId = data.getLong("targetId");
					User target = event.getShardManager().getUserById(targetId);
					
					return Action.fromData(data.get("action", Document.class)).toString() + " to `" + (target == null ? targetId : target.getAsTag() + "`");
				})
				.setIncreasedIndex(true);
			
			paged.onSelect(select -> event.reply(ModLog.fromData(select.getSelected()).getEmbed(event.getShardManager())).queue());
			
			paged.execute(event);
		} else {
			Document data = event.getDatabase().getModLogById(Filters.and(Filters.eq("_id", id), Filters.eq("guildId", event.getGuild().getIdLong())), projection);
			if (data == null) {
				event.replyFailure("I could not find a mod log with that id").queue();
				return;
			}
			
			event.reply(ModLog.fromData(data).getEmbed(event.getShardManager())).queue();
		}
	}

	@Command(value="name", description="Set the name of the webhook that sends mod log messages")
	@CommandId(346)
	@Examples({"modlog name Mod Actions", "modlog name My Servers Mod Logs"})
	@Premium
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void name(Sx4CommandEvent event, @Argument(value="name", endless=true) String name) {
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().projection(Projections.include("modLog.webhook.name", "premium.endAt")).returnDocument(ReturnDocument.BEFORE).upsert(true);
		event.getDatabase().findAndUpdateGuildById(event.getGuild().getIdLong(), List.of(OperatorsUtility.setIfPremium("modLog.webhook.name", name)), options).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			data = data == null ? Database.EMPTY_DOCUMENT : data;

			if (data.getEmbedded(List.of("premium", "endAt"), 0L) < Clock.systemUTC().instant().getEpochSecond()) {
				event.replyFailure("This server needs premium to use this command").queue();
				return;
			}

			String oldName = data.getEmbedded(List.of("modLog", "webhook", "name"), String.class);
			if (oldName != null && oldName.equals(name)) {
				event.replyFailure("Your mod log webhook name was already set to that").queue();
				return;
			}

			event.replySuccess("Your starboard webhook name has been updated").queue();
		});
	}

	@Command(value="avatar", description="Set the avatar of the webhook that sends mod log messages")
	@CommandId(347)
	@Examples({"modlog avatar Shea#6653", "modlog avatar https://i.imgur.com/i87lyNO.png"})
	@Premium
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void avatar(Sx4CommandEvent event, @Argument(value="avatar", endless=true, acceptEmpty=true) @ImageUrl String url) {
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().projection(Projections.include("modLog.webhook.avatar", "premium.endAt")).returnDocument(ReturnDocument.BEFORE).upsert(true);
		event.getDatabase().findAndUpdateGuildById(event.getGuild().getIdLong(), List.of(OperatorsUtility.setIfPremium("modLog.webhook.avatar", url)), options).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			data = data == null ? Database.EMPTY_DOCUMENT : data;

			if (data.getEmbedded(List.of("premium", "endAt"), 0L) < Clock.systemUTC().instant().getEpochSecond()) {
				event.replyFailure("This server needs premium to use this command").queue();
				return;
			}

			String oldUrl = data.getEmbedded(List.of("modLog", "webhook", "avatar"), String.class);
			if (oldUrl != null && oldUrl.equals(url)) {
				event.replyFailure("Your mod log webhook avatar was already set to that").queue();
				return;
			}

			event.replySuccess("Your mod log webhook avatar has been updated").queue();
		});
	}
	
}
