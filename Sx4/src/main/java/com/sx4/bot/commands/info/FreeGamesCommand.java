package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.*;
import com.sx4.bot.annotations.argument.AdvancedMessage;
import com.sx4.bot.annotations.argument.ImageUrl;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.annotations.command.AuthorPermissions;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.annotations.command.Premium;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.info.game.EpicFreeGame;
import com.sx4.bot.entities.info.game.FreeGame;
import com.sx4.bot.entities.info.game.FreeGameType;
import com.sx4.bot.entities.webhook.WebhookChannel;
import com.sx4.bot.formatter.Formatter;
import com.sx4.bot.formatter.FormatterManager;
import com.sx4.bot.formatter.JsonFormatter;
import com.sx4.bot.formatter.function.FormatterVariable;
import com.sx4.bot.managers.FreeGameManager;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.FreeGameUtility;
import com.sx4.bot.utility.MessageUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.utils.TimeFormat;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

public class FreeGamesCommand extends Sx4Command {

	private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM HH:mm");

	public FreeGamesCommand() {
		super("free games", 472);

		super.setDescription("View or add a channel to get free games from Epic Games");
		super.setExamples("free games list");
		super.setAliases("freegames", "freegame", "free game");
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public EmbedBuilder getGameEmbed(FreeGame<?> game) {
		return this.setGameEmbed(new EmbedBuilder(), game);
	}

	public EmbedBuilder setGameEmbed(EmbedBuilder embed, FreeGame<?> game) {
		double originalPrice = game.getOriginalPriceDecimal();

		embed.setTitle(game.getTitle(), game.getUrl());
		embed.setDescription(game.getDescription());
		embed.setThumbnail(game.getType().getIconUrl());
		embed.setImage(game.getImage());
		embed.addField("Price", game.getDiscountPriceDecimal() == originalPrice ? "Free" : String.format("~~£%.2f~~ Free", originalPrice), true);
		embed.addField("Publisher", game.getPublisher(), true);
		embed.addField("Promotion Start", TimeFormat.DATE_TIME_SHORT.format(game.getPromotionStart()), false);
		embed.addField("Promotion End", TimeFormat.DATE_TIME_SHORT.format(game.getPromotionEnd()), false);

		return embed;
	}

	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}

	@Command(value="list", description="Lists the current free games")
	@CommandId(473)
	@Examples({"free games list"})
	public void list(Sx4CommandEvent event) {
		List<Document> games = event.getMongo().getAnnouncedGames(Filters.gte("promotion.end", Instant.now().getEpochSecond()), new Document()).into(new ArrayList<>());
		if (games.isEmpty()) {
			event.replyFailure("There are currently no free games").queue();
			return;
		}

		List<FreeGame<?>> freeGames = games.stream().map(FreeGameUtility::getFreeGame).collect(Collectors.toList());

		PagedResult<FreeGame<?>> paged = new PagedResult<>(event.getBot(), freeGames)
			.setSelect()
			.setPerPage(1)
			.setCustomFunction(page -> {
				EmbedBuilder embed = new EmbedBuilder();
				embed.setFooter("Game " + page.getPage() + "/" + page.getMaxPage());

				page.forEach((game, index) -> this.setGameEmbed(embed, game));

				return new MessageCreateBuilder().setEmbeds(embed.build());
			});

		paged.execute(event);
	}

	@Command(value="upcoming", description="Lists the free games on Epic Games coming in the future")
	@CommandId(482)
	@Examples({"free games upcoming"})
	public void upcoming(Sx4CommandEvent event) {
		FreeGameUtility.retrieveUpcomingFreeGames(event.getHttpClient(), freeGames -> {
			if (freeGames.isEmpty()) {
				event.replyFailure("There are currently no upcoming free games").queue();
				return;
			}

			PagedResult<EpicFreeGame> paged = new PagedResult<>(event.getBot(), freeGames)
				.setSelect()
				.setPerPage(1)
				.setCustomFunction(page -> {
					EmbedBuilder embed = new EmbedBuilder();
					embed.setFooter("Game " + page.getPage() + "/" + page.getMaxPage());

					page.forEach((game, index) -> this.setGameEmbed(embed, game));

					return new MessageCreateBuilder().setEmbeds(embed.build());
				});

			paged.execute(event);
		});
	}

	@Command(value="history", description="View the history of free games announced by Sx4")
	@CommandId(489)
	@Examples({"free games history"})
	public void history(Sx4CommandEvent event) {
		List<Document> games = event.getMongo().getAnnouncedGames().find().sort(Sorts.descending("promotion.start")).into(new ArrayList<>());

		PagedResult<Document> paged = new PagedResult<>(event.getBot(), games)
			.setPerPage(15)
			.setAuthor("Free Games History", null, null)
			.setSelectFunction(data -> data.getString("title"))
			.setDisplayFunction(data -> {
				FreeGame<?> game = FreeGameUtility.getFreeGame(data);

				return "[" + game.getTitle() + "](" + game.getUrl() + ") - " + TimeFormat.DATE_SHORT.format(game.getPromotionStart()) + " to " + TimeFormat.DATE_SHORT.format(game.getPromotionEnd());
			});

		paged.onSelect(select -> {
			FreeGame<?> game = FreeGameUtility.getFreeGame(select.getSelected());

			event.reply(this.getGameEmbed(game).build()).queue();
		});

		paged.execute(event);
	}

	@Command(value="add", description="Add a channel to get free game notifications from Epic Games")
	@CommandId(474)
	@Examples({"free games add", "free games add #channel"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void add(Sx4CommandEvent event, @Argument(value="channel", nullDefault=true, endless=true) WebhookChannel channel) {
		Document data = new Document("channelId", channel.getIdLong())
			.append("guildId", event.getGuild().getIdLong());

		event.getMongo().insertFreeGameChannel(data).whenComplete((result, exception) -> {
			Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
			if (cause instanceof MongoWriteException && ((MongoWriteException) cause).getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
				event.replyFailure("You already have a free games channel in " + channel.getAsMention()).queue();
				return;
			}

			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			event.replySuccess("Free game notifications will now be sent in " + channel.getAsMention()).queue();
		});
	}

	@Command(value="remove", description="Remove a channel from getting free game notifications from Epic Games")
	@CommandId(475)
	@Examples({"free games remove", "free games remove #channel"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void remove(Sx4CommandEvent event, @Argument(value="channel", nullDefault=true, endless=true) WebhookChannel channel) {
		FindOneAndDeleteOptions options = new FindOneAndDeleteOptions().projection(Projections.include("webhook"));
		event.getMongo().findAndDeleteFreeGameChannel(Filters.eq("channelId", channel.getIdLong()), options).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			event.getBot().getFreeGameManager().removeWebhook(channel.getIdLong());

			Document webhook = data.get("webhook", Document.class);
			if (webhook != null) {
				channel.deleteWebhookById(Long.toString(webhook.getLong("id"))).queue(null, ErrorResponseException.ignore(ErrorResponse.UNKNOWN_WEBHOOK));
			}

			event.replySuccess("Free game notifications will no longer be sent in " + channel.getAsMention()).queue();
		});
	}

	@Command(value="toggle", description="Enables/disables a free game channel")
	@CommandId(484)
	@Examples({"free games toggle", "free games toggle #channel"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void toggle(Sx4CommandEvent event, @Argument(value="channel", nullDefault=true, endless=true) WebhookChannel channel) {
		List<Bson> update = List.of(Operators.set("enabled", Operators.cond(Operators.exists("$enabled"), Operators.REMOVE, false)));
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().projection(Projections.include("enabled")).returnDocument(ReturnDocument.AFTER);

		event.getMongo().findAndUpdateFreeGameChannel(Filters.eq("channelId", channel.getIdLong()), update, options).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (data == null) {
				event.replyFailure("There is not a free game channel setup in " + channel.getAsMention()).queue();
				return;
			}

			event.replySuccess("The free game channel in " + channel.getAsMention() + " is now **" + (data.get("enabled", true) ? "enabled" : "disabled") + "**").queue();
		});
	}

	@Command(value="platforms", description="Select what platforms you want notifications from")
	@CommandId(491)
	@Examples({"free games platforms #channel STEAM", "free games platforms STEAM EPIC_GAMES"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void platforms(Sx4CommandEvent event, @Argument(value="channel", nullDefault=true) WebhookChannel channel, @Argument(value="platforms") FreeGameType... types) {

		long raw = FreeGameType.getRaw(types);
		event.getMongo().updateFreeGameChannel(Filters.eq("channelId", channel.getIdLong()), Updates.set("platforms", raw), new UpdateOptions()).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getMatchedCount() == 0) {
				event.replyFailure("You do not have a free game channel in " + channel.getAsMention()).queue();
				return;
			}

			if (result.getModifiedCount() == 0) {
				event.replyFailure("That free game channel already uses those platforms").queue();
				return;
			}

			event.replySuccess("That free game channel will now send notifications from those platforms").queue();
		});
	}

	@Command(value="formatters", aliases={"formatting", "format"}, description="View the formatters you can use for a custom message")
	@CommandId(476)
	@Examples({"free games formatters"})
	public void formatters(Sx4CommandEvent event) {
		EmbedBuilder embed = new EmbedBuilder()
			.setAuthor("Free Game Formatters", null, event.getSelfUser().getEffectiveAvatarUrl());

		FormatterManager manager = FormatterManager.getDefaultManager();

		StringJoiner content = new StringJoiner("\n");
		for (FormatterVariable<?> variable : manager.getVariables(EpicFreeGame.class)) {
			content.add("`{game." + variable.getName() + "}` - " + variable.getDescription());
		}

		embed.setDescription(content.toString());

		event.reply(embed.build()).queue();
	}

	@Command(value="message", description="Set the message for the free game notifications")
	@CommandId(477)
	@Examples({"free games message {game.title} is now free!", "free games message {game.title} {game.original_price.equals(0).then().else(was £{game.original_price.format(,##0.00)} and)} is now free!"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void message(Sx4CommandEvent event, @Argument(value="channel", nullDefault=true) WebhookChannel channel, @Argument(value="message", endless=true) @Limit(max=2000) String message) {
		event.getMongo().updateFreeGameChannel(Filters.eq("channelId", channel.getIdLong()), Updates.set("message.content", message), new UpdateOptions()).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getMatchedCount() == 0) {
				event.replyFailure("You don't have a free game channel setup").queue();
				return;
			}

			if (result.getModifiedCount() == 0) {
				event.replyFailure("Your message for free game notifications is already set to that").queue();
				return;
			}

			event.replySuccess("Your message for free game notifications has been updated").queue();
		});
	}

	@Command(value="advanced message", description="Set the message for the free game notifications using json")
	@CommandId(478)
	@Examples({"free games advanced message {\"content\": \"{game.title} is now free!\"}", "free games advanced message {\"embed\": {\"title\": \"{game.title}\", \"url\": \"{game.url}\", \"description\": \"{game.description\"}}"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void advancedMessage(Sx4CommandEvent event, @Argument(value="channel", nullDefault=true) WebhookChannel channel, @Argument(value="message", endless=true) @AdvancedMessage Document message) {
		event.getMongo().updateFreeGameChannel(Filters.eq("channelId", channel.getIdLong()), Updates.set("message", message), new UpdateOptions()).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getMatchedCount() == 0) {
				event.replyFailure("You don't have a free game channel setup").queue();
				return;
			}

			if (result.getModifiedCount() == 0) {
				event.replyFailure("Your message for free game notifications is already set to that").queue();
				return;
			}

			event.replySuccess("Your message for free game notifications has been updated").queue();
		});
	}

	@Command(value="name", description="Set the name of the webhook that sends free game notifications")
	@CommandId(479)
	@Examples({"free games name Epic Games", "free games name Free Games"})
	@Premium
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void name(Sx4CommandEvent event, @Argument(value="channel", nullDefault=true) WebhookChannel channel, @Argument(value="name", endless=true) String name) {
		event.getMongo().updateFreeGameChannel(Filters.eq("channelId", channel.getIdLong()), Updates.set("webhook.name", name), new UpdateOptions()).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getMatchedCount() == 0) {
				event.replyFailure("You don't have a free game channel setup").queue();
				return;
			}

			if (result.getModifiedCount() == 0) {
				event.replyFailure("Your webhook name for free game notifications was already set to that").queue();
				return;
			}

			event.replySuccess("Your webhook name has been updated for free game notifications, this only works with premium <https://patreon.com/Sx4>").queue();
		});
	}

	@Command(value="avatar", description="Set the avatar of the webhook that sends free game notifications")
	@CommandId(480)
	@Examples({"free games avatar Shea#6653", "free games avatar https://i.imgur.com/i87lyNO.png"})
	@Premium
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void avatar(Sx4CommandEvent event, @Argument(value="channel", nullDefault=true) WebhookChannel channel, @Argument(value="avatar", endless=true, acceptEmpty=true) @ImageUrl String url) {
		event.getMongo().updateFreeGameChannel(Filters.eq("channelId", channel.getIdLong()), Updates.set("webhook.avatar", url), new UpdateOptions()).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getMatchedCount() == 0) {
				event.replyFailure("You don't have a free game channel setup").queue();
				return;
			}

			if (result.getModifiedCount() == 0) {
				event.replyFailure("Your webhook avatar for free game notifications was already set to that").queue();
				return;
			}

			event.replySuccess("Your webhook avatar has been updated for that free game notifications, this only works with premium <https://patreon.com/Sx4>").queue();
		});
	}

	@Command(value="preview", description="Preview your free game notification message")
	@CommandId(481)
	@Examples({"free games preview"})
	public void preview(Sx4CommandEvent event, @Argument(value="channel", nullDefault=true) WebhookChannel channel) {
		Document data = event.getMongo().getFreeGameChannel(Filters.eq("channelId", channel.getIdLong()), Projections.include("message"));
		if (data == null) {
			event.replyFailure("You don't have a free game channel setup").queue();
			return;
		}

		FreeGameUtility.retrieveFreeGames(event.getHttpClient(), freeGames -> {
			Formatter<Document> formatter = new JsonFormatter(data.get("message", FreeGameManager.DEFAULT_MESSAGE))
				.addVariable("game", freeGames.get(0));

			try {
				event.reply(MessageUtility.fromWebhookMessage(MessageUtility.fromJson(formatter.parse(), true).build())).queue();
			} catch (IllegalArgumentException e) {
				event.replyFailure(e.getMessage()).queue();
			}
		});
	}

}
