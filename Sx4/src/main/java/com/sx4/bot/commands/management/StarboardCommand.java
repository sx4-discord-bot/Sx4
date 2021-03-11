package com.sx4.bot.commands.management;

import club.minnced.discord.webhook.WebhookClient;
import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.client.model.*;
import com.sx4.bot.annotations.argument.ImageUrl;
import com.sx4.bot.annotations.argument.Limit;
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
import com.sx4.bot.managers.StarboardManager;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.OperatorsUtility;
import com.sx4.bot.waiter.Waiter;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageReaction.ReactionEmote;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class StarboardCommand extends Sx4Command {

	private final StarboardManager manager = StarboardManager.get();

	public StarboardCommand() {
		super("starboard", 196);

		super.setDescription("Setup starboard in your server to favourite messages");
		super.setExamples("starboard toggle", "starboard channel", "starboard top");
		super.setCategoryAll(ModuleCategory.MANAGEMENT);
	}

	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}

	@Command(value="toggle", description="Toggle the state of starboard in the server")
	@CommandId(197)
	@Examples({"starboard toggle"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void toggle(Sx4CommandEvent event) {
		List<Bson> update = List.of(Operators.set("starboard.enabled", Operators.cond("$starboard.enabled", Operators.REMOVE, true)));
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().projection(Projections.include("starboard.enabled")).returnDocument(ReturnDocument.AFTER).upsert(true);
		this.database.findAndUpdateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			event.replySuccess("Starboard is now " + (data.getEmbedded(List.of("starboard", "enabled"), false) ? "enabled" : "disabled")).queue();
		});
	}

	@Command(value="channel", description="Sets the channel for starboard messages to be sent in")
	@CommandId(198)
	@Examples({"starboard channel", "starboard channel #starboard", "starboard channel reset"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void channel(Sx4CommandEvent event, @Argument(value="channel | reset", endless=true, nullDefault=true) @Options("reset") Alternative<TextChannel> option) {
		TextChannel channel = option == null ? event.getTextChannel() : option.getValue();

		List<Bson> update = List.of(Operators.set("starboard.channelId", channel == null ? Operators.REMOVE : channel.getIdLong()), Operators.unset("starboard.webhook.id"), Operators.unset("starboard.webhook.token"));
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.include("starboard.channelId")).upsert(true);

		this.database.findAndUpdateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			long channelId = data == null ? 0L : data.getEmbedded(List.of("starboard", "channelId"), 0L);

			if ((channel == null ? 0L : channel.getIdLong()) == channelId) {
				event.replyFailure("The starboard channel is already " + (channel == null ? "unset" : "set to " + channel.getAsMention())).queue();
				return;
			}

			TextChannel oldChannel = channelId == 0L ? null : event.getGuild().getTextChannelById(channelId);
			if (oldChannel != null) {
				WebhookClient oldWebhook = this.manager.removeWebhook(channelId);
				if (oldWebhook != null) {
					oldChannel.deleteWebhookById(String.valueOf(oldWebhook.getId())).queue();
				}
			}

			event.replySuccess("The starboard channel has been " + (channel == null ? "unset" : "set to " + channel.getAsMention())).queue();
		});
	}

	@Command(value="emote", aliases={"emoji"}, description="Sets the emote/emoji to be used for starboard")
	@CommandId(199)
	@Examples({"starboard emote ☝️", "starboard emote <:upvote:761345612079693865>", "starboard emote reset"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void emote(Sx4CommandEvent event, @Argument(value="emote | reset", endless=true) @Options("reset") Alternative<ReactionEmote> option) {
		ReactionEmote emote = option.getValue();
		boolean emoji = emote != null && emote.isEmoji();

		List<Bson> update = emote == null ? List.of(Operators.unset("starboard.emote")) : List.of(Operators.set("starboard.emote." + (emoji ? "name" : "id"), emoji ? emote.getEmoji() : emote.getIdLong()), Operators.unset("starboard.emote." + (emoji ? "id" : "name")));

		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).upsert(true).projection(Projections.include("starboard.emote"));
		this.database.findAndUpdateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			Document emoteData = data == null ? null : data.getEmbedded(List.of("starboard", "emote"), Document.class);
			if ((emote == null && emoteData == null) || (emote != null && emoteData != null && (emoji ? emote.getEmoji().equals(emoteData.getString("name")) : emoteData.getLong("id") == emote.getIdLong()))) {
				event.replyFailure("Your starboard emote was already " + (emote == null ? "unset" : "set to that")).queue();
				return;
			}

			event.replySuccess("Your starboard emote has been " + (emote == null ? "unset" : "updated")).queue();
		});
	}

	@Command(value="delete", aliases={"remove"}, description="Deletes a starboard")
	@CommandId(204)
	@Examples({"starboard delete 5ff636647f93247aeb2ac429", "starboard delete all"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void delete(Sx4CommandEvent event, @Argument(value="id | all") @Options("all") Alternative<ObjectId> option) {
		if (option.isAlternative()) {
			event.reply(event.getAuthor().getName() + ", are you sure you want to delete **all** starboards in this server? (Yes or No)").submit()
				.thenCompose(message -> {
					Waiter<GuildMessageReceivedEvent> waiter = new Waiter<>(GuildMessageReceivedEvent.class)
						.setPredicate(messageEvent -> messageEvent.getMessage().getContentRaw().equalsIgnoreCase("yes"))
						.setOppositeCancelPredicate()
						.setTimeout(30)
						.setUnique(event.getAuthor().getIdLong(), event.getChannel().getIdLong());

					waiter.onTimeout(() -> event.reply("Response timed out :stopwatch:").queue());

					waiter.onCancelled(type -> event.replySuccess("Cancelled").queue());

					waiter.start();

					return waiter.future();
				})
				.thenCompose(messageEvent -> this.database.deleteManyStarboards(Filters.eq("guildId", event.getGuild().getIdLong())))
				.thenCompose(result -> this.database.deleteManyStars(Filters.eq("guildId", event.getGuild().getIdLong())))
				.whenComplete((result, exception) -> {
					if (ExceptionUtility.sendExceptionally(event, exception)) {
						return;
					}

					if (result.getDeletedCount() == 0) {
						event.replySuccess("There are no starboards in this server").queue();
						return;
					}

					event.replySuccess("All starboards have been deleted in this server").queue();
				});
		} else {
			ObjectId id = option.getValue();

			AtomicReference<Document> atomicData = new AtomicReference<>();
			this.database.findAndDeleteStarboardById(id).thenCompose(data -> {
				if (data == null) {
					return CompletableFuture.completedFuture(null);
				}

				atomicData.set(data);

				return this.database.deleteManyStars(Filters.eq("messageId", data.getLong("originalMessageId")));
			}).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (result == null) {
					event.replyFailure("I could not find that starboard").queue();
					return;
				}

				Document data = atomicData.get();

				WebhookClient webhook = this.manager.getWebhook(data.getLong("channelId"));
				if (webhook != null) {
					webhook.delete(data.getLong("messageId"));
				}

				event.replySuccess("That starboard has been deleted").queue();
			});
		}
	}

	@Command(value="name", description="Set the name of the webhook that sends starboard messages")
	@CommandId(206)
	@Examples({"starboard name Starboard", "starboard name Stars"})
	@Premium
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void name(Sx4CommandEvent event, @Argument(value="name", endless=true) String name) {
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().projection(Projections.include("starboard.webhook.name", "premium.endAt")).returnDocument(ReturnDocument.BEFORE).upsert(true);
		this.database.findAndUpdateGuildById(event.getGuild().getIdLong(), List.of(OperatorsUtility.setIfPremium("starboard.webhook.name", name)), options).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			data = data == null ? Database.EMPTY_DOCUMENT : data;

			if (data.getEmbedded(List.of("premium", "endAt"), 0L) < Clock.systemUTC().instant().getEpochSecond()) {
				event.replyFailure("This server needs premium to use this command").queue();
				return;
			}

			String oldName = data.getEmbedded(List.of("starboard", "webhook", "name"), String.class);
			if (oldName != null && oldName.equals(name)) {
				event.replyFailure("Your starboard webhook name was already set to that").queue();
				return;
			}

			event.replySuccess("Your starboard webhook name has been updated").queue();
		});
	}

	@Command(value="avatar", description="Set the avatar of the webhook that sends starboard messages")
	@CommandId(207)
	@Examples({"starboard avatar Shea#6653", "starboard avatar https://i.imgur.com/i87lyNO.png"})
	@Premium
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void avatar(Sx4CommandEvent event, @Argument(value="avatar", endless=true, acceptEmpty=true) @ImageUrl String url) {
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().projection(Projections.include("starboard.webhook.avatar", "premium.endAt")).returnDocument(ReturnDocument.BEFORE).upsert(true);
		this.database.findAndUpdateGuildById(event.getGuild().getIdLong(), List.of(OperatorsUtility.setIfPremium("starboard.webhook.avatar", url)), options).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			data = data == null ? Database.EMPTY_DOCUMENT : data;

			if (data.getEmbedded(List.of("premium", "endAt"), 0L) < Clock.systemUTC().instant().getEpochSecond()) {
				event.replyFailure("This server needs premium to use this command").queue();
				return;
			}

			String oldUrl = data.getEmbedded(List.of("starboard", "webhook", "avatar"), String.class);
			if (oldUrl != null && oldUrl.equals(url)) {
				event.replyFailure("Your starboard webhook avatar was already set to that").queue();
				return;
			}

			event.replySuccess("Your starboard webhook avatar has been updated").queue();
		});
	}

	@Command(value="top", aliases={"list"}, description="View the top starred messages in the server")
	@CommandId(205)
	@Examples({"starboard top"})
	public void top(Sx4CommandEvent event) {
		Guild guild = event.getGuild();

		List<Document> starboards = this.database.getStarboards(Filters.eq("guildId", guild.getIdLong()), Projections.include("count", "channelId", "messageId")).sort(Sorts.descending("count")).into(new ArrayList<>());

		PagedResult<Document> paged = new PagedResult<>(starboards)
			.setIncreasedIndex(true)
			.setAuthor("Top Starboards", null, guild.getIconUrl())
			.setDisplayFunction(data ->
				String.format(
					"[%s](https://discord.com/channels/%d/%d/%d) - **%d**",
					data.getObjectId("_id").toHexString(),
					guild.getIdLong(),
					data.getLong("channelId"),
					data.getLong("messageId"),
					data.getInteger("count")
				)
			);

		paged.execute(event);
	}

	public class MessagesCommand extends Sx4Command {

		public MessagesCommand() {
			super("messages", 200);

			super.setDescription("Set the configuration of messages depending on the amount of stars");
			super.setExamples("starboard messages set", "starboard messages remove", "starboard messages list");
		}

		public void onCommand(Sx4CommandEvent event) {
			event.replyHelp().queue();
		}

		@Command(value="set", description="Sets the message for a certain amount of stars")
		@CommandId(201)
		@Examples({"starboard messages set 1 Our first star!", "starboard messages set 20 We reached **{stars}** stars"})
		@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
		public void set(Sx4CommandEvent event, @Argument(value="stars") @Limit(min=1) int stars, @Argument(value="message", endless=true) String message) {
			Document config = new Document("stars", stars)
				.append("message", new Document("content", message));

			List<Bson> update = List.of(Operators.set("starboard.messages", Operators.let(new Document("config", Operators.ifNull("$starboard.messages", StarboardManager.DEFAULT_CONFIGURATION)), Operators.cond(Operators.gte(Operators.size("$$config"), 50), "$$config", Operators.concatArrays(Operators.filter("$$config", Operators.ne("$$this.stars", stars)), List.of(config))))));
			this.database.updateGuildById(event.getGuild().getIdLong(), update).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (result.getModifiedCount() == 0) {
					event.replyFailure("You can have no more than 50 different messages").queue();
					return;
				}

				event.replySuccess("When a starboard reaches **" + stars + "** stars it will now show that message").queue();
			});
		}

		@Command(value="remove", description="Removes a starboard message")
		@CommandId(202)
		@Examples({"starboard messages remove 1", "starboard messages remove 20"})
		@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
		public void remove(Sx4CommandEvent event, @Argument(value="stars") int stars) {
			List<Bson> update = List.of(Operators.set("starboard.messages", Operators.let(new Document("config", Operators.ifNull("$starboard.messages", StarboardManager.DEFAULT_CONFIGURATION)), Operators.cond(Operators.eq(Operators.size("$$config"), 1), "$$config", Operators.let(new Document("updatedConfig", Operators.filter("$$config", Operators.ne("$$this.stars", stars))), Operators.cond(Operators.eq(StarboardManager.DEFAULT_CONFIGURATION, "$$updatedConfig"), Operators.REMOVE, "$$updatedConfig"))))));
			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().projection(Projections.include("starboard.messages")).returnDocument(ReturnDocument.BEFORE).upsert(true);
			this.database.findAndUpdateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((data, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				List<Document> config = data == null ? StarboardManager.DEFAULT_CONFIGURATION : data.getEmbedded(List.of("starboard", "messages"), StarboardManager.DEFAULT_CONFIGURATION);
				if (config.size() == 1) {
					event.replyFailure("You have to have at least 1 starboard message").queue();
					return;
				}

				Document star = config.stream()
					.filter(d -> d.getInteger("stars") == stars)
					.findFirst()
					.orElse(null);

				if (star == null) {
					event.replyFailure("You don't have a starboard message for that amount of stars").queue();
					return;
				}

				event.replySuccess("You no longer have a starboard message for **" + stars + "** stars").queue();
			});
		}

		@Command(value="reset", description="Resets the configuration of your messages to the default")
		@CommandId(203)
		@Examples({"starboard configuration reset"})
		@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
		public void reset(Sx4CommandEvent event) {
			this.database.updateGuildById(event.getGuild().getIdLong(), Updates.unset("starboard.messages")).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (result.getModifiedCount() == 0) {
					event.replyFailure("Your messages where already set to the default").queue();
					return;
				}

				event.replySuccess("Your starboard messages are now back to the default").queue();
			});
		}

	}

}
