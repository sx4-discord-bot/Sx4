package com.sx4.bot.commands.management;

import club.minnced.discord.webhook.WebhookClient;
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
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.annotations.command.Premium;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.Database;
import com.sx4.bot.database.model.Operators;
import com.sx4.bot.entities.argument.Option;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.managers.WelcomerManager;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.OperatorsUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.TextChannel;
import okhttp3.Request;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Set;

public class WelcomerCommand extends Sx4Command {

	private final WelcomerManager manager = WelcomerManager.get();

	public WelcomerCommand() {
		super("welcomer");

		super.setDescription("Set the bot to send welcome messages when a user joins the server");
		super.setExamples("welcomer toggle", "welcomer message", "welcomer channel");
		super.setCategoryAll(ModuleCategory.MANAGEMENT);
	}

	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}

	@Command(value="toggle", description="Toggle the state of welcomer")
	@Examples({"welcomer toggle"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void toggle(Sx4CommandEvent event) {
		List<Bson> update = List.of(Operators.set("welcomer.enabled", Operators.cond("$welcomer.enabled", Operators.REMOVE, true)));
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER).projection(Projections.include("welcomer.enabled")).upsert(true);

		this.database.findAndUpdateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			event.replySuccess("Welcomer is now " + (data.getEmbedded(List.of("welcomer", "enabled"), false) ? "enabled" : "disabled")).queue();
		});
	}

	@Command(value="channel", description="Sets the channel where welcomer messages are sent to")
	@Examples({"welcomer channel", "welcomer channel #joins", "welcomer channel reset"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void channel(Sx4CommandEvent event, @Argument(value="channel", endless=true, nullDefault=true) @Options("reset") Option<TextChannel> option) {
		TextChannel channel = option == null ? event.getTextChannel() : option.isAlternative() ? null : option.getValue();

		List<Bson> update = List.of(Operators.set("welcomer.channelId", channel == null ? Operators.REMOVE : channel.getIdLong()), Operators.unset("welcomer.webhook.id"), Operators.unset("welcomer.webhook.token"));
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().upsert(true).projection(Projections.include("welcomer.webhook.token", "welcomer.webhook.id")).returnDocument(ReturnDocument.BEFORE);

		this.database.findAndUpdateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			long channelId = data == null ? 0L : data.getEmbedded(List.of("welcomer", "channelId"), 0L);

			if ((channel == null ? 0L : channel.getIdLong()) == channelId) {
				event.replyFailure("The welcomer channel is already " + (channel == null ? "unset" : "set to " + channel.getAsMention())).queue();
				return;
			}

			TextChannel oldChannel = channelId == 0L ? null : event.getGuild().getTextChannelById(channelId);
			if (oldChannel != null) {
				WebhookClient oldWebhook = this.manager.removeWebhook(channelId);
				if (oldWebhook != null) {
					oldChannel.deleteWebhookById(String.valueOf(oldWebhook.getId())).queue();
				}
			}

			event.replySuccess("The welcomer channel has been " + (channel == null ? "unset" : "set to " + channel.getAsMention())).queue();
		});
	}

	@Command(value="message", description="Sets the message to be sent when welcoming a new user")
	@Examples({"welcomer message A new person has joined", "welcomer message Welcome {user.mention}!"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void message(Sx4CommandEvent event, @Argument(value="message", endless=true) String message) {
		this.database.updateGuildById(event.getGuild().getIdLong(), Updates.set("welcomer.message.content", message)).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getModifiedCount() == 0) {
				event.replyFailure("Your welcomer message is already set to that").queue();
				return;
			}

			event.replySuccess("Your welcomer message has been updated").queue();
		});
	}

	@Command(value="advanced message", description="Same as `welcomer message` but takes json for more advanced options")
	@Examples({"welcomer message {\"embed\": {\"description\": \"A new person has joined\"}}", "welcomer message {\"embed\": {\"description\": \"Welcome {user.mention}!\"}}"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void advancedMessage(Sx4CommandEvent event, @Argument(value="json", endless=true) @AdvancedMessage Document message) {
		this.database.updateGuildById(event.getGuild().getIdLong(), Updates.set("welcomer.message", message)).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getModifiedCount() == 0) {
				event.replyFailure("Your welcomer message is already set to that").queue();
				return;
			}

			event.replySuccess("Your welcomer message has been updated").queue();
		});
	}

	@Command(value="name", description="Set the name of the webhook that sends welcomer messages")
	@Examples({"welcomer name Welcomer", "welcomer name Joins"})
	@Premium
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void name(Sx4CommandEvent event, @Argument(value="name", endless=true) String name) {
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().projection(Projections.include("welcomer.webhook.name", "premium.endAt")).returnDocument(ReturnDocument.BEFORE).upsert(true);
		this.database.findAndUpdateGuildById(event.getGuild().getIdLong(), List.of(OperatorsUtility.setIfPremium("welcomer.webhook.name", name)), options).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			data = data == null ? Database.EMPTY_DOCUMENT : data;

			if (data.getEmbedded(List.of("premium", "endAt"), 0L) < Clock.systemUTC().instant().getEpochSecond()) {
				event.replyFailure("This server needs premium to use this command").queue();
				return;
			}

			String oldName = data.getEmbedded(List.of("welcomer", "webhook", "name"), String.class);
			if (oldName != null && oldName.equals(name)) {
				event.replyFailure("Your welcomer webhook name was already set to that").queue();
				return;
			}

			event.replySuccess("Your welcomer webhook name has been updated").queue();
		});
	}

	@Command(value="avatar", description="Set the avatar of the webhook that sends welcomer messages")
	@Examples({"welcomer avatar Shea#6653", "welcomer avatar https://i.imgur.com/i87lyNO.png"})
	@Premium
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void avatar(Sx4CommandEvent event, @Argument(value="avatar", endless=true, acceptEmpty=true) @ImageUrl String url) {
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().projection(Projections.include("welcomer.webhook.name", "premium.endAt")).returnDocument(ReturnDocument.BEFORE).upsert(true);
		this.database.findAndUpdateGuildById(event.getGuild().getIdLong(), List.of(OperatorsUtility.setIfPremium("welcomer.webhook.avatar", url)), options).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			data = data == null ? Database.EMPTY_DOCUMENT : data;

			if (data.getEmbedded(List.of("premium", "endAt"), 0L) < Clock.systemUTC().instant().getEpochSecond()) {
				event.replyFailure("This server needs premium to use this command").queue();
				return;
			}

			String oldUrl = data.getEmbedded(List.of("welcomer", "webhook", "avatar"), String.class);
			if (oldUrl != null && oldUrl.equals(url)) {
				event.replyFailure("Your welcomer webhook avatar was already set to that").queue();
				return;
			}

			event.replySuccess("Your welcomer webhook avatar has been updated").queue();
		});
	}

	public class ImageCommand extends Sx4Command {

		private final Set<String> types = Set.of("png", "jpeg", "jpg", "gif");

		public ImageCommand() {
			super("image");

			super.setDescription("Setup the image section of the welcomer");
			super.setExamples("welcomer image toggle", "welcomer image banner");
		}

		public void onCommand(Sx4CommandEvent event) {
			event.replyHelp().queue();
		}

		@Command(value="toggle", description="Toggle the status of having an image on the welcomer message")
		@Examples({"welcomer image toggle"})
		@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
		public void toggle(Sx4CommandEvent event) {
			List<Bson> update = List.of(Operators.set("welcomer.image.enabled", Operators.cond("$welcomer.image.enabled", Operators.REMOVE, true)));
			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER).projection(Projections.include("welcomer.image.enabled")).upsert(true);

			this.database.findAndUpdateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((data, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				event.replySuccess("Image welcomer is now " + (data.getEmbedded(List.of("welcomer", "image", "enabled"), false) ? "enabled" : "disabled")).queue();
			});
		}

		@Command(value="banner", description="Set the welcomer banner for image welcomer if the server is premium this can be a gif")
		@Examples({"welcomer banner https://i.imgur.com/i87lyNO.png", "welcomer banner https://example.com/image.png"})
		@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
		public void banner(Sx4CommandEvent event, @Argument(value="url") @ImageUrl String url) {
			Request request = new Request.Builder()
				.url(url)
				.build();

			event.getClient().newCall(request).enqueue((HttpCallback) response -> {
				String contentType = response.header("Content-Type");
				if (contentType == null) {
					event.replyFailure("That url does not return a content type").queue();
					return;
				}

				String[] contentTypeSplit = contentType.split("/");

				String type = contentTypeSplit[0], subType = contentType.contains("/") ? contentTypeSplit[1] : "png";
				if (!type.equals("image")) {
					event.replyFailure("That url is not an image").queue();
					return;
				}

				if (!this.types.contains(subType)) {
					event.replyFailure("That image is not a supported image type").queue();
					return;
				}

				try (FileOutputStream stream = new FileOutputStream(String.format("welcomer/banners/%s.%s", event.getGuild().getId(), subType))) {
					stream.write(response.body().bytes());
				} catch (IOException e) {
					ExceptionUtility.sendExceptionally(event, e);
					return;
				}

				event.replySuccess("Your welcomer banner has been updated").queue();
			});
		}

	}

}
