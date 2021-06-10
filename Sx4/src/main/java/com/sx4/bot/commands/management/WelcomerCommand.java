package com.sx4.bot.commands.management;

import club.minnced.discord.webhook.WebhookClient;
import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.command.Command.Async;
import com.jockie.bot.core.command.Command.Cooldown;
import com.jockie.bot.core.cooldown.ICooldown;
import com.jockie.bot.core.option.Option;
import com.mongodb.client.model.*;
import com.sx4.bot.annotations.argument.AdvancedMessage;
import com.sx4.bot.annotations.argument.Colour;
import com.sx4.bot.annotations.argument.ImageUrl;
import com.sx4.bot.annotations.argument.Options;
import com.sx4.bot.annotations.command.*;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.argument.Alternative;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.managers.WelcomerManager;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.MessageUtility;
import com.sx4.bot.utility.OperatorsUtility;
import com.sx4.bot.utility.WelcomerUtility;
import net.dv8tion.jda.api.EmbedBuilder;
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

	public WelcomerCommand() {
		super("welcomer", 91);

		super.setDescription("Set the bot to send welcome messages when a user joins the server");
		super.setExamples("welcomer toggle", "welcomer message", "welcomer channel");
		super.setCategoryAll(ModuleCategory.MANAGEMENT);
	}

	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}

	@Command(value="toggle", description="Toggle the state of welcomer")
	@CommandId(92)
	@Examples({"welcomer toggle"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void toggle(Sx4CommandEvent event) {
		List<Bson> update = List.of(Operators.set("welcomer.enabled", Operators.cond("$welcomer.enabled", Operators.REMOVE, true)));
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER).projection(Projections.include("welcomer.enabled")).upsert(true);

		event.getMongo().findAndUpdateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			event.replySuccess("Welcomer is now " + (data.getEmbedded(List.of("welcomer", "enabled"), false) ? "enabled" : "disabled")).queue();
		});
	}

	@Command(value="channel", description="Sets the channel where welcomer messages are sent to")
	@CommandId(93)
	@Examples({"welcomer channel", "welcomer channel #joins", "welcomer channel reset"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void channel(Sx4CommandEvent event, @Argument(value="channel | reset", endless=true, nullDefault=true) @Options("reset") Alternative<TextChannel> option) {
		TextChannel channel = option == null ? event.getTextChannel() : option.isAlternative() ? null : option.getValue();

		List<Bson> update = List.of(Operators.set("welcomer.channelId", channel == null ? Operators.REMOVE : channel.getIdLong()), Operators.unset("welcomer.webhook.id"), Operators.unset("welcomer.webhook.token"));
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().upsert(true).projection(Projections.include("welcomer.webhook.token", "welcomer.webhook.id", "welcomer.channelId")).returnDocument(ReturnDocument.BEFORE);

		event.getMongo().findAndUpdateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((data, exception) -> {
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
				WebhookClient oldWebhook = event.getBot().getWelcomerManager().removeWebhook(channelId);
				if (oldWebhook != null) {
					oldChannel.deleteWebhookById(String.valueOf(oldWebhook.getId())).queue();
				}
			}

			event.replySuccess("The welcomer channel has been " + (channel == null ? "unset" : "set to " + channel.getAsMention())).queue();
		});
	}

	@Command(value="message", description="Sets the message to be sent when welcoming a new user")
	@CommandId(94)
	@Examples({"welcomer message A new person has joined", "welcomer message Welcome {user.mention}!"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void message(Sx4CommandEvent event, @Argument(value="message", endless=true) String message) {
		event.getMongo().updateGuildById(event.getGuild().getIdLong(), Updates.set("welcomer.message", new Document("content", message))).whenComplete((result, exception) -> {
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
	@CommandId(95)
	@Examples({"welcomer advanced message {\"embed\": {\"description\": \"A new person has joined\"}}", "welcomer advanced message {\"embed\": {\"description\": \"Welcome {user.mention}!\"}}"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void advancedMessage(Sx4CommandEvent event, @Argument(value="json", endless=true) @AdvancedMessage Document message) {
		event.getMongo().updateGuildById(event.getGuild().getIdLong(), Updates.set("welcomer.message", message)).whenComplete((result, exception) -> {
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

	@Command(value="embed", description="Set your welcomer message to use a basic embed")
	@CommandId(434)
	@Examples({"welcomer embed A new person has joined", "welcomer message Welcome {user.mention}! --colour=#ffff00"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void embed(Sx4CommandEvent event, @Argument(value="message", endless=true) String message, @Option(value="image", description="Use this option if you want the image welcomer in the embed") boolean image, @Option(value="colour", description="Sets the embed colour for the message") @Colour Integer colour) {
		Document data = new Document("description", message).append("author", new Document("name", "{user.tag}").append("icon_url", "{user.avatar}"));
		if (colour != null) {
			data.append("color", colour);
		}

		if (image) {
			data.append("image", new Document("url", "{file.url}"));
		}

		event.getMongo().updateGuildById(event.getGuild().getIdLong(), Updates.set("welcomer.message", new Document("embed", data))).whenComplete((result, exception) -> {
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
	@CommandId(96)
	@Examples({"welcomer name Welcomer", "welcomer name Joins"})
	@Premium
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void name(Sx4CommandEvent event, @Argument(value="name", endless=true) String name) {
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().projection(Projections.include("welcomer.webhook.name", "premium.endAt")).returnDocument(ReturnDocument.BEFORE).upsert(true);
		event.getMongo().findAndUpdateGuildById(event.getGuild().getIdLong(), List.of(OperatorsUtility.setIfPremium("welcomer.webhook.name", name)), options).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			data = data == null ? MongoDatabase.EMPTY_DOCUMENT : data;

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
	@CommandId(97)
	@Examples({"welcomer avatar Shea#6653", "welcomer avatar https://i.imgur.com/i87lyNO.png"})
	@Premium
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void avatar(Sx4CommandEvent event, @Argument(value="avatar", endless=true, acceptEmpty=true) @ImageUrl String url) {
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().projection(Projections.include("welcomer.webhook.avatar", "premium.endAt")).returnDocument(ReturnDocument.BEFORE).upsert(true);
		event.getMongo().findAndUpdateGuildById(event.getGuild().getIdLong(), List.of(OperatorsUtility.setIfPremium("welcomer.webhook.avatar", url)), options).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			data = data == null ? MongoDatabase.EMPTY_DOCUMENT : data;

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

	@Command(value="dm toggle", aliases={"dm"}, description="Toggle the state of welcomer private messaging the user")
	@CommandId(98)
	@Examples({"welcomer dm toggle"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void dmToggle(Sx4CommandEvent event) {
		List<Bson> update = List.of(Operators.set("welcomer.dm", Operators.cond("$welcomer.dm", Operators.REMOVE, true)));
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER).projection(Projections.include("welcomer.dm")).upsert(true);

		event.getMongo().findAndUpdateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			event.replySuccess("Welcomer will " + (data.getEmbedded(List.of("welcomer", "dm"), false) ? "now" : "no longer") + " send in private messages").queue();
		});
	}

	@Command(value="preview", description="Preview your welcomer message")
	@CommandId(99)
	@Examples({"welcomer preview"})
	public void preview(Sx4CommandEvent event) {
		Document data = event.getMongo().getGuildById(event.getGuild().getIdLong(), Projections.include("welcomer.message", "welcomer.image", "welcomer.enabled", "premium.endAt"));

		Document welcomer = data.get("welcomer", MongoDatabase.EMPTY_DOCUMENT);
		Document image = welcomer.get("image", MongoDatabase.EMPTY_DOCUMENT);

		boolean messageEnabled = welcomer.get("enabled", false), imageEnabled = image.get("enabled", false);
		if (!messageEnabled && !imageEnabled) {
			event.replyFailure("Neither welcomer or image welcomer is enabled").queue();
			return;
		}

		boolean gif = data.getEmbedded(List.of("premium", "endAt"), 0L) >= Clock.systemUTC().instant().getEpochSecond();

		WelcomerUtility.getWelcomerMessage(event.getHttpClient(), messageEnabled ? welcomer.get("message", WelcomerManager.DEFAULT_MESSAGE) : null, image.getString("bannerId"), event.getMember(), event.getConfig().isCanary(), imageEnabled, gif, (builder, exception) -> {
			if (exception instanceof IllegalArgumentException) {
				event.replyFailure(exception.getMessage()).queue();
				return;
			}

			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			MessageUtility.fromWebhookMessage(event.getTextChannel(), builder.build()).queue();
		});
	}

	@Command(value="stats", aliases={"settings"}, description="View basic information about your welcomer configuration")
	@CommandId(435)
	@Examples({"welcomer stats"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void stats(Sx4CommandEvent event) {
		Document data = event.getMongo().getGuildById(event.getGuild().getIdLong(), Projections.include("welcomer")).get("welcomer", MongoDatabase.EMPTY_DOCUMENT);
		Document image = data.get("image", MongoDatabase.EMPTY_DOCUMENT);

		EmbedBuilder embed = new EmbedBuilder()
			.setAuthor("Welcomer Stats", null, event.getSelfUser().getEffectiveAvatarUrl())
			.addField("Message Status", data.get("enabled", false) ? "Enabled" : "Disabled", true)
			.addField("Channel", data.containsKey("channelId") ? "<#" + data.get("channelId") + ">" : "None", true)
			.addField("Image Status", image.getBoolean("enabled", false) ? "Enabled" : "Disabled", true)
			.addField("Private Message Status", data.getBoolean("dm", false) ? "Enabled" : "Disabled", true)
			.addField("Webhook Name", data.getEmbedded(List.of("webhook", "name"), "Sx4 - Welcomer"), true)
			.addField("Webhook Avatar", data.getEmbedded(List.of("webhook", "avatar"), event.getSelfUser().getEffectiveAvatarUrl()), true);

		event.reply(embed.build()).queue();
	}

	public static class ImageCommand extends Sx4Command {

		private final Set<String> types = Set.of("png", "jpeg", "jpg", "gif");

		public ImageCommand() {
			super("image", 100);

			super.setDescription("Setup the image section of the welcomer");
			super.setExamples("welcomer image toggle", "welcomer image banner");
			super.setCategoryAll(ModuleCategory.MANAGEMENT);
		}

		public void onCommand(Sx4CommandEvent event) {
			event.replyHelp().queue();
		}

		@Command(value="toggle", description="Toggle the status of having an image on the welcomer message")
		@CommandId(101)
		@Redirects({"image welcomer toggle", "img welcomer toggle"})
		@Examples({"welcomer image toggle"})
		@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
		public void toggle(Sx4CommandEvent event) {
			List<Bson> update = List.of(Operators.set("welcomer.image.enabled", Operators.cond("$welcomer.image.enabled", Operators.REMOVE, true)));
			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER).projection(Projections.include("welcomer.image.enabled")).upsert(true);

			event.getMongo().findAndUpdateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((data, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				event.replySuccess("Image welcomer is now " + (data.getEmbedded(List.of("welcomer", "image", "enabled"), false) ? "enabled" : "disabled")).queue();
			});
		}

		@Command(value="banner", description="Set the welcomer banner for image welcomer if the server is premium this can be a gif")
		@CommandId(102)
		@Cooldown(value=30, cooldownScope=ICooldown.Scope.GUILD)
		@Async
		@Examples({"welcomer banner https://i.imgur.com/i87lyNO.png", "welcomer banner https://example.com/image.png"})
		@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
		public void banner(Sx4CommandEvent event, @Argument(value="url") @ImageUrl String url) {
			Request request = new Request.Builder()
				.url(url)
				.build();

			event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
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

				byte[] bytes = response.body().bytes();
				if (bytes.length > 5_000_000) {
					event.replyFailure("Your welcomer banner cannot be more than 5MB").queue();
					return;
				}

				String bannerId = event.getGuild().getId() + "." + subType;
				try (FileOutputStream stream = new FileOutputStream("welcomer/banners/" + bannerId)) {
					stream.write(bytes);
				} catch (IOException e) {
					ExceptionUtility.sendExceptionally(event, e);
					return;
				}

				event.getMongo().updateGuildById(event.getGuild().getIdLong(), Updates.set("welcomer.image.bannerId", bannerId), new UpdateOptions().upsert(true)).whenComplete((result, exception) -> {
					if (ExceptionUtility.sendExceptionally(event, exception)) {
						return;
					}

					event.replySuccess("Your welcomer banner has been updated").queue();
				});
			});
		}

	}

}
