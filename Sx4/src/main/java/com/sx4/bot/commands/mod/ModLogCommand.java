package com.sx4.bot.commands.mod;

import club.minnced.discord.webhook.WebhookClient;
import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReturnDocument;
import com.sx4.bot.annotations.command.AuthorPermissions;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.Database;
import com.sx4.bot.database.model.Operators;
import com.sx4.bot.entities.argument.All;
import com.sx4.bot.entities.argument.Range;
import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.entities.mod.action.Action;
import com.sx4.bot.entities.mod.ModLog;
import com.sx4.bot.managers.ModLogManager;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.waiter.Waiter;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;

public class ModLogCommand extends Sx4Command {

	private final ModLogManager manager = ModLogManager.get();

	public ModLogCommand() {
		super("modlog");
		
		super.setAliases("modlogs", "mod log", "mod logs");
		super.setDescription("Setup the mod log in your server to log mod actions which happen within the server");
		super.setExamples("modlog toggle", "modlog channel", "modlog case");
		super.setCategoryAll(ModuleCategory.MODERATION);
	}
	
	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}
	
	@Command(value="toggle", description="Turn mod logs on/off in your server")
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	@Examples({"modlog toggle"})
	public void toggle(Sx4CommandEvent event) {
		List<Bson> update = List.of(Operators.set("modLog.enabled", Operators.cond("$modLog.enabled", Operators.REMOVE, true)));
		this.database.findAndUpdateGuildById(event.getGuild().getIdLong(), Projections.include("modLog.enabled"), update).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}
			
			event.replySuccess("Mod logs are now **" + (data.getEmbedded(List.of("modLog", "enabled"), false) ? "enabled" : "disabled") + "**").queue();
		});
	}
	
	@Command(value="channel", description="Sets the channel which mod logs are sent to")
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	@Examples({"modlog channel #mod-logs", "modlog channel mod-logs", "modlog channel 432898619943813132"})
	public void channel(Sx4CommandEvent event, @Argument(value="channel", endless=true, nullDefault=true) TextChannel channel) {
		List<Bson> update = List.of(Operators.set("modLog.channelId", channel == null ? Operators.REMOVE : channel.getIdLong()), Operators.unset("modLog.webhook.id"), Operators.unset("modLog.webhook.token"));
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.include("modLog.channelId")).upsert(true);

		this.database.findAndUpdateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			long channelId = data == null ? 0L : data.getEmbedded(List.of("modLog", "channelId"), 0L);

			if ((channel == null && channelId == 0L) || (channel != null && channel.getIdLong() == channelId)) {
				event.replyFailure("The mod log channel is already " + (channel == null ? "unset" : "set to " + channel.getAsMention())).queue();
				return;
			}

			if (channel != null) {
				WebhookClient oldWebhook = this.manager.removeWebhook(channelId);
				if (oldWebhook != null) {
					channel.deleteWebhookById(String.valueOf(oldWebhook.getId())).queue();
				}
			}
			
			event.replySuccess("The mod log channel has been " + (channel == null ? "unset" : "set to " + channel.getAsMention())).queue();
		});
	}
	
	@Command(value="case", description="Edit the reason of a mod log case")
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

		List<Bson> update = List.of(Operators.set("reason", Operators.cond(Operators.and(Operators.eq("$moderatorId", authorId), Operators.or(or)), reason.getParsed(), "$reason")));
		this.database.updateManyModLogs(update).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			long modified = result.getModifiedCount();
			if (modified == 0) {
				event.replyFailure("You were unable to update any of those mod logs or you provided an invalid range").queue();
				return;
			}

			event.replyFormat("Updated **%d** case%s %s", modified, modified == 1 ? "" : "s", this.config.getSuccessEmote()).queue();
		});
	}

	@Command(value="remove", aliases={"delete"}, description="Deletes a mod log from the  server")
	@Examples({"modlog remove 5e45ce6d3688b30ee75201ae", "modlog remove all"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void remove(Sx4CommandEvent event, @Argument(value="id") All<ObjectId> all) {
		User author = event.getAuthor();

		if (all.isAll()) {
			event.reply(author.getName() + ", are you sure you want to delete **all** the suggestions in this server? (Yes or No)").queue(queryMessage -> {
				Waiter<GuildMessageReceivedEvent> waiter = new Waiter<>(GuildMessageReceivedEvent.class)
					.setPredicate(messageEvent -> messageEvent.getMessage().getContentRaw().equalsIgnoreCase("yes"))
					.setOppositeCancelPredicate()
					.setTimeout(30)
					.setUnique(author.getIdLong(), event.getChannel().getIdLong());

				waiter.onTimeout(() -> event.reply("Response timed out :stopwatch:").queue());

				waiter.onCancelled(type -> event.replySuccess("Cancelled").queue());

				waiter.future()
					.thenCompose(messageEvent -> this.database.deleteManyModLogs(Filters.eq("guildId", event.getGuild().getIdLong())))
					.whenComplete((result, exception) -> {
						if (ExceptionUtility.sendExceptionally(event, exception)) {
							return;
						}

						if (result.getDeletedCount() == 0) {
							event.replyFailure("There are no mod logs in this server").queue();
							return;
						}

						event.replySuccess("All your mod logs have been deleted").queue();
					});

				waiter.start();
			});
		} else {
			ObjectId id = all.getValue();

			this.database.findAndDeleteModLogById(id).whenComplete((data, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (data == null) {
					event.replyFailure("I could not find that mod log").queue();
					return;
				}

				Guild guild = event.getGuild();

				long channelId = data.getLong("channelId");
				TextChannel channel = guild.getTextChannelById(channelId);
				if (channel != null) {
					if (guild.getSelfMember().hasPermission(channel, Permission.MESSAGE_MANAGE)) {
						channel.deleteMessageById(data.getLong("messageId")).queue();
					} else {
						Document webhookData = this.database.getGuildById(guild.getIdLong(), Projections.include("modLog.webhook.token", "modLog.webhook.id")).getEmbedded(List.of("modLog", "webhook"), Database.EMPTY_DOCUMENT);

						this.manager.deleteModLog(data.getLong("messageId"), channelId, webhookData);
					}
				}

				event.replySuccess("That mod log has been deleted").queue();
			});
		}
	}
	
	@Command(value="view", aliases={"viewcase", "view case", "list"}, description="View a mod log case from the server")
	@Examples({"modlog view 5e45ce6d3688b30ee75201ae", "modlog view"})
	public void view(Sx4CommandEvent event, @Argument(value="id", nullDefault=true) ObjectId id) {
		Bson projection = Projections.include("moderatorId", "reason", "targetId", "action");
		if (id == null) {
			List<Document> allData = this.database.getModLogs(Filters.eq("guildId", event.getGuild().getIdLong()), projection).into(new ArrayList<>());
			if (allData.isEmpty()) {
				event.replyFailure("There are no mod logs in this server").queue();
				return;
			}

			PagedResult<Document> paged = new PagedResult<>(allData)
				.setDisplayFunction(data -> {
					long targetId = data.getLong("targetId");
					User target = event.getShardManager().getUserById(targetId);
					
					return Action.fromData(data.get("action", Document.class)).toString() + " to `" + (target == null ? targetId : target.getAsTag() + "`");
				})
				.setIncreasedIndex(true);
			
			paged.onSelect(select -> {
				event.reply(ModLog.fromData(select.getSelected()).getEmbed()).queue();
			});
			
			paged.execute(event);
		} else {
			Document data = this.database.getModLogById(Filters.and(Filters.eq("_id", id), Filters.eq("guildId", event.getGuild().getIdLong())), projection);
			if (data == null) {
				event.replyFailure("I could not find a mod log with that id").queue();
				return;
			}
			
			event.reply(ModLog.fromData(data).getEmbed()).queue();
		}
	}
	
}
