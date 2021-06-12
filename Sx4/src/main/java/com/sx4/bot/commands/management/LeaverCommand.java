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
import com.sx4.bot.annotations.command.*;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.argument.Alternative;
import com.sx4.bot.formatter.FormatterManager;
import com.sx4.bot.formatter.function.FormatterVariable;
import com.sx4.bot.managers.LeaverManager;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.LeaverUtility;
import com.sx4.bot.utility.MessageUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.StringJoiner;

public class LeaverCommand extends Sx4Command {

	public LeaverCommand() {
		super("leaver", 188);

		super.setDescription("Set the bot to send welcome messages when a user joins the server");
		super.setExamples("leaver toggle", "leaver message", "leaver channel");
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

		event.getMongo().findAndUpdateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((data, exception) -> {
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
	public void channel(Sx4CommandEvent event, @Argument(value="channel | reset", endless=true, nullDefault=true) @Options("reset") Alternative<TextChannel> option) {
		TextChannel channel = option == null ? event.getTextChannel() : option.isAlternative() ? null : option.getValue();

		List<Bson> update = List.of(Operators.set("leaver.channelId", channel == null ? Operators.REMOVE : channel.getIdLong()), Operators.unset("leaver.webhook.id"), Operators.unset("leaver.webhook.token"));
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().upsert(true).projection(Projections.include("leaver.webhook.token", "leaver.webhook.id", "leaver.channelId")).returnDocument(ReturnDocument.BEFORE);

		event.getMongo().findAndUpdateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((data, exception) -> {
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
				WebhookClient oldWebhook = event.getBot().getLeaverManager().removeWebhook(channelId);
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
		event.getMongo().updateGuildById(event.getGuild().getIdLong(), Updates.set("leaver.message", new Document("content", message))).whenComplete((result, exception) -> {
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
		event.getMongo().updateGuildById(event.getGuild().getIdLong(), Updates.set("leaver.message", message)).whenComplete((result, exception) -> {
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
		event.getMongo().updateGuildById(event.getGuild().getIdLong(), Updates.set("leaver.webhook.name", name)).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getModifiedCount() == 0 && result.getUpsertedId() == null) {
				event.replyFailure("Your leaver webhook name was already set to that").queue();
				return;
			}

			event.replySuccess("Your leaver webhook name has been updated, this only works with premium <https://patreon.com/Sx4>").queue();
		});
	}

	@Command(value="avatar", description="Set the avatar of the webhook that sends leaver messages")
	@CommandId(194)
	@Examples({"leaver avatar Shea#6653", "leaver avatar https://i.imgur.com/i87lyNO.png"})
	@Premium
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void avatar(Sx4CommandEvent event, @Argument(value="avatar", endless=true, acceptEmpty=true) @ImageUrl String url) {
		event.getMongo().updateGuildById(event.getGuild().getIdLong(), Updates.set("leaver.webhook.avatar", url)).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getModifiedCount() == 0 && result.getUpsertedId() == null) {
				event.replyFailure("Your leaver webhook avatar was already set to that").queue();
				return;
			}

			event.replySuccess("Your leaver webhook avatar has been updated, this only works with premium <https://patreon.com/Sx4>").queue();
		});
	}

	@Command(value="stats", aliases={"settings"}, description="View basic information about your leaver configuration")
	@CommandId(440)
	@Examples({"leaver stats"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void stats(Sx4CommandEvent event) {
		Document data = event.getMongo().getGuildById(event.getGuild().getIdLong(), Projections.include("leaver")).get("leaver", MongoDatabase.EMPTY_DOCUMENT);

		EmbedBuilder embed = new EmbedBuilder()
			.setAuthor("Leaver Stats", null, event.getSelfUser().getEffectiveAvatarUrl())
			.addField("Message Status", data.get("enabled", false) ? "Enabled" : "Disabled", true)
			.addField("Channel", data.containsKey("channelId") ? "<#" + data.get("channelId") + ">" : "None", true)
			.addField("Private Message Status", data.getBoolean("dm", false) ? "Enabled" : "Disabled", true)
			.addField("Webhook Name", data.getEmbedded(List.of("webhook", "name"), "Sx4 - Leaver"), true)
			.addField("Webhook Avatar", data.getEmbedded(List.of("webhook", "avatar"), event.getSelfUser().getEffectiveAvatarUrl()), true);

		event.reply(embed.build()).queue();
	}

	@Command(value="formatters", aliases={"format", "formatting"}, description="Get all the formatters for leaver you can use")
	@CommandId(442)
	@Examples({"leaver formatters"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void formatters(Sx4CommandEvent event) {
		EmbedBuilder embed = new EmbedBuilder()
			.setAuthor("Leaver Formatters", null, event.getSelfUser().getEffectiveAvatarUrl());

		FormatterManager manager = FormatterManager.getDefaultManager();

		StringJoiner content = new StringJoiner("\n");
		for (FormatterVariable<?> variable : manager.getVariables(User.class)) {
			content.add("`{user." + variable.getName() + "}` - " + variable.getDescription());
		}

		for (FormatterVariable<?> variable : manager.getVariables(Member.class)) {
			content.add("`{member." + variable.getName() + "}` - " + variable.getDescription());
		}

		for (FormatterVariable<?> variable : manager.getVariables(Guild.class)) {
			content.add("`{server." + variable.getName() + "}` - " + variable.getDescription());
		}

		for (FormatterVariable<?> variable : manager.getVariables(OffsetDateTime.class)) {
			content.add("`{now." + variable.getName() + "}` - " + variable.getDescription());
		}

		content.add("`{user.age}` - Gets the age of a user as a string");
		content.add("`{member.age}` - Gets the age of a member as a string");

		embed.setDescription(content.toString());

		event.reply(embed.build()).queue();
	}

	@Command(value="preview", description="Preview your leaver message")
	@CommandId(195)
	@Examples({"leaver preview"})
	public void preview(Sx4CommandEvent event) {
		Document data = event.getMongo().getGuildById(event.getGuild().getIdLong(), Projections.include("leaver.message", "leaver.enabled"));

		Document leaver = data.get("leaver", MongoDatabase.EMPTY_DOCUMENT);
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
