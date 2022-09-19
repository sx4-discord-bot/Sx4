package com.sx4.bot.commands.management;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.command.Command.Async;
import com.jockie.bot.core.command.Command.Cooldown;
import com.jockie.bot.core.cooldown.ICooldown;
import com.jockie.bot.core.option.Option;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import com.sx4.bot.annotations.argument.AdvancedMessage;
import com.sx4.bot.annotations.argument.AlternativeOptions;
import com.sx4.bot.annotations.argument.Colour;
import com.sx4.bot.annotations.argument.ImageUrl;
import com.sx4.bot.annotations.command.*;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.argument.Alternative;
import com.sx4.bot.entities.webhook.WebhookChannel;
import com.sx4.bot.formatter.FormatterManager;
import com.sx4.bot.formatter.function.FormatterVariable;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.managers.WelcomerManager;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.MessageUtility;
import com.sx4.bot.utility.RequestUtility;
import com.sx4.bot.utility.WelcomerUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.attribute.IWebhookContainer;
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import okhttp3.Request;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

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
	public void channel(Sx4CommandEvent event, @Argument(value="channel | reset", endless=true, nullDefault=true) @AlternativeOptions("reset") Alternative<WebhookChannel> option) {
		WebhookChannel channel = option.isAlternative() ? null : option.getValue();

		List<Bson> update = List.of(Operators.set("welcomer.channelId", channel == null ? Operators.REMOVE : channel.getIdLong()), Operators.unset("welcomer.webhook.id"), Operators.unset("welcomer.webhook.token"));
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().upsert(true).projection(Projections.include("welcomer.webhook.id", "welcomer.channelId")).returnDocument(ReturnDocument.BEFORE);

		event.getMongo().findAndUpdateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			long channelId = data == null ? 0L : data.getEmbedded(List.of("welcomer", "channelId"), 0L);
			event.getBot().getWelcomerManager().removeWebhook(channelId);

			if ((channel == null ? 0L : channel.getIdLong()) == channelId) {
				event.replyFailure("The welcomer channel is already " + (channel == null ? "unset" : "set to " + channel.getAsMention())).queue();
				return;
			}

			GuildMessageChannelUnion oldChannel = channelId == 0L ? null : event.getGuild().getChannelById(GuildMessageChannelUnion.class, channelId);
			long webhookId = data == null ? 0L : data.getEmbedded(List.of("welcomer", "webhook", "id"), 0L);

			if (oldChannel != null && webhookId != 0L) {
				((IWebhookContainer) oldChannel).deleteWebhookById(Long.toString(webhookId)).queue(null, ErrorResponseException.ignore(ErrorResponse.UNKNOWN_WEBHOOK));
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
	@Examples({"welcomer embed A new person has joined", "welcomer embed Welcome {user.mention}! --colour=#ffff00", "welcomer embed Welcome {user.mention}! --colour=#ffff00 --image"})
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

	@Command(value="screening", aliases={"member screening"}, description="Toggles whether the bot should send a welcomer message after or before a member has gone through member screening")
	@CommandId(461)
	@Examples({"welcomer screening"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void screening(Sx4CommandEvent event) {
		List<Bson> update = List.of(Operators.set("welcomer.screening", Operators.cond(Operators.exists("$welcomer.screening"), Operators.REMOVE, false)));
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER).projection(Projections.include("welcomer.screening")).upsert(true);

		event.getMongo().findAndUpdateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			event.replySuccess("Welcomer will now send a message " + (data.getEmbedded(List.of("welcomer", "screening"), true) ? "after" : "before") + " member screening").queue();
		});
	}

	@Command(value="name", description="Set the name of the webhook that sends welcomer messages")
	@CommandId(96)
	@Examples({"welcomer name Welcomer", "welcomer name Joins"})
	@Premium
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void name(Sx4CommandEvent event, @Argument(value="name", endless=true) String name) {
		event.getMongo().updateGuildById(event.getGuild().getIdLong(), Updates.set("welcomer.webhook.name", name)).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getModifiedCount() == 0 && result.getUpsertedId() == null) {
				event.replyFailure("Your welcomer webhook name was already set to that").queue();
				return;
			}

			event.replySuccess("Your welcomer webhook name has been updated, this only works with premium <https://patreon.com/Sx4>").queue();
		});
	}

	@Command(value="avatar", description="Set the avatar of the webhook that sends welcomer messages")
	@CommandId(97)
	@Examples({"welcomer avatar Shea#6653", "welcomer avatar https://i.imgur.com/i87lyNO.png"})
	@Premium
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void avatar(Sx4CommandEvent event, @Argument(value="avatar", endless=true, acceptEmpty=true) @ImageUrl String url) {
		event.getMongo().updateGuildById(event.getGuild().getIdLong(), Updates.set("welcomer.webhook.avatar", url)).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getModifiedCount() == 0 && result.getUpsertedId() == null) {
				event.replyFailure("Your welcomer webhook avatar was already set to that").queue();
				return;
			}

			event.replySuccess("Your welcomer webhook avatar has been updated, this only works with premium <https://patreon.com/Sx4>").queue();
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

			event.reply( MessageUtility.fromWebhookMessage(builder.build())).queue();
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

	@Command(value="formatters", aliases={"format", "formatting"}, description="Get all the formatters for welcomer you can use")
	@CommandId(441)
	@Examples({"welcomer formatters"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void formatters(Sx4CommandEvent event) {
		EmbedBuilder embed = new EmbedBuilder()
			.setAuthor("Welcomer Formatters", null, event.getSelfUser().getEffectiveAvatarUrl());

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

		embed.setDescription(content.toString());

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
		@Examples({"welcomer image banner https://i.imgur.com/i87lyNO.png", "welcomer image banner https://example.com/image.png", "welcomer image banner reset"})
		@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
		public void banner(Sx4CommandEvent event, @Argument(value="url", acceptEmpty=true) @ImageUrl @AlternativeOptions("reset") Alternative<String> option) {
			if (option.isAlternative()) {
				FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.include("welcomer.image.bannerId"));
				event.getMongo().findAndUpdateGuildById(event.getGuild().getIdLong(), Updates.unset("welcomer.image.bannerId"), options).whenComplete((data, exception) -> {
					if (ExceptionUtility.sendExceptionally(event, exception)) {
						return;
					}

					if (data == null) {
						event.replyFailure("You do not have a welcomer banner").queue();
						return;
					}

					String bannerId = data.getEmbedded(List.of("welcomer", "image", "bannerId"), String.class);
					if (bannerId == null) {
						event.replyFailure("You do not have a welcomer banner").queue();
						return;
					}

					File file = new File("welcomer/banners/" + bannerId);
					if (file.delete()) {
						event.replySuccess("Your welcomer banner has been unset").queue();
					} else {
						event.replyFailure("You do not have a welcomer banner").queue();
					}
				});

				return;
			}

			Request request = new Request.Builder()
				.url(RequestUtility.getWorkerUrl(option.getValue()))
				.build();

			event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
				if (response.code() == 403) {
					if ("error code: 1003".equals(response.body().string())) {
						event.replyFailure("That url is either not valid or not accepted").queue();
						return;
					}
				}

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

				FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.include("welcomer.image.bannerId")).upsert(true);
				event.getMongo().findAndUpdateGuildById(event.getGuild().getIdLong(), Updates.set("welcomer.image.bannerId", bannerId), options).whenComplete((data, exception) -> {
					if (ExceptionUtility.sendExceptionally(event, exception)) {
						return;
					}

					if (data != null)  {
						String banner = data.getEmbedded(List.of("welcomer", "image", "bannerId"), String.class);
						if (banner != null) {
							new File("welcomer/banners/" + bannerId).delete();
						}
					}

					try (FileOutputStream stream = new FileOutputStream("welcomer/banners/" + bannerId)) {
						stream.write(bytes);
					} catch (IOException e) {
						ExceptionUtility.sendExceptionally(event, e);
						return;
					}

					event.replySuccess("Your welcomer banner has been updated").queue();
				});
			});
		}

	}

}
