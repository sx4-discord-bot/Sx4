package com.sx4.bot.commands.management;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import com.sx4.bot.annotations.argument.AdvancedMessage;
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
import com.sx4.bot.managers.LeaverManager;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.LeaverUtility;
import com.sx4.bot.utility.MessageUtility;
import com.sx4.bot.utility.OperatorsUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.TextChannel;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Clock;
import java.util.List;

public class LeaverCommand extends Sx4Command {

	private final LeaverManager manager = LeaverManager.get();

	public LeaverCommand() {
		super("leaver", 188);

		super.setDescription("Set the bot to send welcome messages when a user joins the server");
		super.setExamples("welcomer toggle", "welcomer message", "welcomer channel");
		super.setCategoryAll(ModuleCategory.MANAGEMENT);
	}

	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}

	@Command(value="toggle", description="Toggle the state of leaver")
	@CommandId(189)
	@Examples({"leaver toggle"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void toggle(Sx4CommandEvent event) {
		List<Bson> update = List.of(Operators.set("leaver.enabled", Operators.cond("$leaver.enabled", Operators.REMOVE, true)));
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER).projection(Projections.include("leaver.enabled")).upsert(true);

		this.database.findAndUpdateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			event.replySuccess("Leaver is now " + (data.getEmbedded(List.of("leaver", "enabled"), false) ? "enabled" : "disabled")).queue();
		});
	}

	@Command(value="channel", description="Sets the channel where leaver messages are sent to")
	@CommandId(190)
	@Examples({"leaver channel", "leaver channel #leaves", "leaver channel reset"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void channel(Sx4CommandEvent event, @Argument(value="channel", endless=true, nullDefault=true) @Options("reset") Alternative<TextChannel> option) {
		TextChannel channel = option == null ? event.getTextChannel() : option.isAlternative() ? null : option.getValue();

		List<Bson> update = List.of(Operators.set("leaver.channelId", channel == null ? Operators.REMOVE : channel.getIdLong()), Operators.unset("leaver.webhook.id"), Operators.unset("leaver.webhook.token"));
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().upsert(true).projection(Projections.include("leaver.webhook.token", "leaver.webhook.id", "leaver.channelId")).returnDocument(ReturnDocument.BEFORE);

		this.database.findAndUpdateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			long channelId = data == null ? 0L : data.getEmbedded(List.of("leaver", "channelId"), 0L);

			if ((channel == null ? 0L : channel.getIdLong()) == channelId) {
				event.replyFailure("The leaver channel is already " + (channel == null ? "unset" : "set to " + channel.getAsMention())).queue();
				return;
			}

			TextChannel oldChannel = channelId == 0L ? null : event.getGuild().getTextChannelById(channelId);
			if (oldChannel != null) {
				WebhookClient oldWebhook = this.manager.removeWebhook(channelId);
				if (oldWebhook != null) {
					oldChannel.deleteWebhookById(String.valueOf(oldWebhook.getId())).queue();
				}
			}

			event.replySuccess("The leaver channel has been " + (channel == null ? "unset" : "set to " + channel.getAsMention())).queue();
		});
	}

	@Command(value="message", description="Sets the message to be sent when a user leaves")
	@CommandId(191)
	@Examples({"leaver message Someone has left", "leaver message Goodbye {user.tag}!"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void message(Sx4CommandEvent event, @Argument(value="message", endless=true) String message) {
		this.database.updateGuildById(event.getGuild().getIdLong(), Updates.set("leaver.message.content", message)).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getModifiedCount() == 0) {
				event.replyFailure("Your leaver message is already set to that").queue();
				return;
			}

			event.replySuccess("Your leaver message has been updated").queue();
		});
	}

	@Command(value="advanced message", description="Same as `leaver message` but takes json for more advanced options")
	@CommandId(192)
	@Examples({"leaver advanced message {\"embed\": {\"description\": \"Someone has left\"}}", "leaver advanced message {\"embed\": {\"description\": \"Goodbye {user.tag}!\"}}"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void advancedMessage(Sx4CommandEvent event, @Argument(value="json", endless=true) @AdvancedMessage Document message) {
		this.database.updateGuildById(event.getGuild().getIdLong(), Updates.set("leaver.message", message)).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getModifiedCount() == 0) {
				event.replyFailure("Your leaver message is already set to that").queue();
				return;
			}

			event.replySuccess("Your leaver message has been updated").queue();
		});
	}

	@Command(value="name", description="Set the name of the webhook that sends leaver messages")
	@CommandId(193)
	@Examples({"leaver name Leaver", "leaver name Leaves"})
	@Premium
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void name(Sx4CommandEvent event, @Argument(value="name", endless=true) String name) {
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().projection(Projections.include("leaver.webhook.name", "premium.endAt")).returnDocument(ReturnDocument.BEFORE).upsert(true);
		this.database.findAndUpdateGuildById(event.getGuild().getIdLong(), List.of(OperatorsUtility.setIfPremium("leaver.webhook.name", name)), options).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			data = data == null ? Database.EMPTY_DOCUMENT : data;

			if (data.getEmbedded(List.of("premium", "endAt"), 0L) < Clock.systemUTC().instant().getEpochSecond()) {
				event.replyFailure("This server needs premium to use this command").queue();
				return;
			}

			String oldName = data.getEmbedded(List.of("leaver", "webhook", "name"), String.class);
			if (oldName != null && oldName.equals(name)) {
				event.replyFailure("Your leaver webhook name was already set to that").queue();
				return;
			}

			event.replySuccess("Your leaver webhook name has been updated").queue();
		});
	}

	@Command(value="avatar", description="Set the avatar of the webhook that sends leaver messages")
	@CommandId(194)
	@Examples({"leaver avatar Shea#6653", "leaver avatar https://i.imgur.com/i87lyNO.png"})
	@Premium
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void avatar(Sx4CommandEvent event, @Argument(value="avatar", endless=true, acceptEmpty=true) @ImageUrl String url) {
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().projection(Projections.include("leaver.webhook.avatar", "premium.endAt")).returnDocument(ReturnDocument.BEFORE).upsert(true);
		this.database.findAndUpdateGuildById(event.getGuild().getIdLong(), List.of(OperatorsUtility.setIfPremium("leaver.webhook.avatar", url)), options).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			data = data == null ? Database.EMPTY_DOCUMENT : data;

			if (data.getEmbedded(List.of("premium", "endAt"), 0L) < Clock.systemUTC().instant().getEpochSecond()) {
				event.replyFailure("This server needs premium to use this command").queue();
				return;
			}

			String oldUrl = data.getEmbedded(List.of("leaver", "webhook", "avatar"), String.class);
			if (oldUrl != null && oldUrl.equals(url)) {
				event.replyFailure("Your leaver webhook avatar was already set to that").queue();
				return;
			}

			event.replySuccess("Your leaver webhook avatar has been updated").queue();
		});
	}

	@Command(value="preview", description="Preview your leaver message")
	@CommandId(195)
	@Examples({"leaver preview"})
	public void preview(Sx4CommandEvent event) {
		Document data = this.database.getGuildById(event.getGuild().getIdLong(), Projections.include("leaver.message", "leaver.enabled"));

		Document leaver = data.get("leaver", Database.EMPTY_DOCUMENT);
		if (!leaver.get("enabled", false)) {
			event.replyFailure("Leaver is not enabled").queue();
			return;
		}

		WebhookMessageBuilder builder;
		try {
			builder = LeaverUtility.getLeaverMessage(leaver.get("message", LeaverManager.DEFAULT_MESSAGE), event.getMember());
		} catch (IllegalArgumentException e) {
			event.replyFailure(e.getMessage()).queue();
			return;
		}

		MessageUtility.fromWebhookMessage(event.getTextChannel(), builder.build()).queue();
	}

}
