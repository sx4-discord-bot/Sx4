package com.sx4.bot.commands.mod;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.sx4.bot.annotations.argument.AlternativeOptions;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.annotations.command.AuthorPermissions;
import com.sx4.bot.annotations.command.BotPermissions;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.argument.Alternative;
import com.sx4.bot.entities.interaction.ButtonType;
import com.sx4.bot.entities.interaction.CustomButtonId;
import com.sx4.bot.paged.MessagePagedResult;
import com.sx4.bot.utility.ExceptionUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;

public class TemplateCommand extends Sx4Command {

	public TemplateCommand() {
		super("template", 254);

		super.setDescription("Setup templates to be used as shortcuts in moderation reasons");
		super.setAliases("templates");
		super.setExamples("template add", "template remove", "template list");
		super.setCategoryAll(ModuleCategory.MODERATION);
	}

	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}

	@Command(value="add", description="Add a template in the current server")
	@CommandId(255)
	@Examples({"template add tos Broke ToS", "template add spam Spamming excessively"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void add(Sx4CommandEvent event, @Argument(value="template") @Limit(max=100) String template, @Argument(value="reason", endless=true) String reason) {
		Document data = new Document("template", template)
			.append("reason", reason)
			.append("guildId", event.getGuild().getIdLong());

		event.getMongo().insertTemplate(data).whenComplete((result, exception) -> {
			Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
			if (cause instanceof MongoWriteException && ((MongoWriteException) cause).getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
				event.replyFailure("You already have a template with that name").queue();
				return;
			}

			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			event.replySuccess("That template has been added with id `" + result.getInsertedId().asObjectId().getValue().toHexString() + "`").queue();
		});
	}

	@Command(value="delete", aliases={"remove"}, description="Deletes a template from the current server")
	@CommandId(256)
	@Examples({"template delete 6006ff6b94c9ed0f764ada83", "template delete all"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void delete(Sx4CommandEvent event, @Argument(value="id | all") @AlternativeOptions("all") Alternative<ObjectId> option) {
		if (option.isAlternative()) {
			String acceptId = new CustomButtonId.Builder()
				.setType(ButtonType.TEMPLATE_DELETE_CONFIRM)
				.setTimeout(60)
				.setOwners(event.getAuthor().getIdLong())
				.getId();

			String rejectId = new CustomButtonId.Builder()
				.setType(ButtonType.GENERIC_REJECT)
				.setTimeout(60)
				.setOwners(event.getAuthor().getIdLong())
				.getId();

			List<Button> buttons = List.of(Button.success(acceptId, "Yes"), Button.danger(rejectId, "No"));

			event.reply(event.getAuthor().getName() + ", are you sure you want to delete **all** the templates in this server?")
				.setActionRow(buttons)
				.queue();
		} else {
			event.getMongo().deleteTemplateById(option.getValue()).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (result.getDeletedCount() == 0) {
					event.replyFailure("I could not find that template").queue();
					return;
				}

				event.replySuccess("That template has been deleted").queue();
			});
		}
	}

	@Command(value="list", description="Lists all the templates in the current server")
	@CommandId(257)
	@Examples({"template list"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void list(Sx4CommandEvent event) {
		List<Document> triggers = event.getMongo().getTemplates(Filters.eq("guildId", event.getGuild().getIdLong()), Projections.include("template", "reason")).into(new ArrayList<>());
		if (triggers.isEmpty()) {
			event.replyFailure("There are no templates setup in this server").queue();
			return;
		}

		MessagePagedResult<Document> paged = new MessagePagedResult.Builder<>(event.getBot(), triggers)
			.setAuthor("Templates", null, event.getGuild().getIconUrl())
			.setDisplayFunction(data -> "`" + data.getObjectId("_id").toHexString() + "` - " + data.getString("template"))
			.build();

		paged.onSelect(select -> event.reply(select.getSelected().getString("reason")).queue());

		paged.execute(event);
	}

}
