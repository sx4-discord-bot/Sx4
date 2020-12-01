package com.sx4.bot.commands.mod;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.sx4.bot.annotations.command.AuthorPermissions;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.model.Operators;
import com.sx4.bot.entities.argument.Range;
import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.entities.mod.action.Action;
import com.sx4.bot.entities.mod.modlog.ModLog;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.ExceptionUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;

public class ModLogCommand extends Sx4Command {

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
		List<Bson> update = List.of(Operators.set("modLog.channelId", channel == null ? Operators.REMOVE : channel.getIdLong()));
		this.database.updateGuildById(event.getGuild().getIdLong(), update).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}
			
			if (result.getModifiedCount() == 0) {
				event.replyFailure("The mod log channel is already " + (channel == null ? "unset" : "set to " + channel.getAsMention())).queue();
				return;
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
	
	@Command(value="view case", aliases={"viewcase"}, description="View a modlog case from the server")
	@Examples({"modlog view case 5e45ce6d3688b30ee75201ae", "modlog view case"})
	public void viewCase(Sx4CommandEvent event, @Argument(value="id", nullDefault=true) ObjectId id) {
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
				System.out.println(select.getSelected());
				event.reply(new ModLog(select.getSelected()).getEmbed()).queue();
			});
			
			paged.execute(event);
		} else {
			Document data = this.database.getModLogById(Filters.and(Filters.eq("_id", id), Filters.eq("guildId", event.getGuild().getIdLong())), projection);
			if (data == null) {
				event.replyFailure("I could not find a mod log with that id").queue();
				return;
			}
			
			event.reply(new ModLog(data).getEmbed()).queue();
		}
	}
	
}
