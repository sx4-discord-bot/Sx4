package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
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
import com.sx4.bot.entities.info.FreeGame;
import com.sx4.bot.formatter.FormatterManager;
import com.sx4.bot.formatter.function.FormatterVariable;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.FreeGameUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.TextChannel;
import org.bson.Document;

import java.time.format.DateTimeFormatter;
import java.util.Collections;
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

	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}

	@Command(value="list", description="Lists the free games on Epic Games for this week")
	@CommandId(473)
	@Examples({"free games list"})
	public void list(Sx4CommandEvent event) {
		event.getHttpClient().newCall(FreeGameUtility.REQUEST).enqueue((HttpCallback) response -> {
			Document document = Document.parse(response.body().string());

			List<Document> elements = document.getEmbedded(List.of("data", "Catalog", "searchStore", "elements"), Collections.emptyList());

			List<Document> freeGames = elements.stream()
				.filter(game -> {
					Document offer = FreeGameUtility.getPromotionalOffer(game);
					return offer != null && offer.getEmbedded(List.of("discountSetting", "discountPercentage"), Integer.class) == 0;
				})
				.collect(Collectors.toList());

			PagedResult<Document> paged = new PagedResult<>(event.getBot(), freeGames)
				.setSelect()
				.setPerPage(1)
				.setCustomFunction(page -> {
					EmbedBuilder embed = new EmbedBuilder();
					embed.setFooter("Game " + page.getPage() + "/" + page.getMaxPage());

					page.forEach((data, index) -> {
						FreeGame game = FreeGame.fromData(data, true);

						embed.setTitle(game.getTitle(), game.getUrl());
						embed.setDescription(game.getDescription());
						embed.setImage(game.getImage());

						double originalPrice = game.getOriginalPrice();

						embed.addField("Price", game.getPrice() == originalPrice ? "Free" : String.format("~~£%.2f~~ Free", originalPrice), true);
						embed.addField("Publisher", game.getPublisher(), true);
						embed.addField("Promotion Duration", game.getStart().format(this.formatter) + " - " + game.getEnd().format(this.formatter), false);
					});

					return new MessageBuilder().setEmbeds(embed.build());
				});

			paged.execute(event);
		});
	}

	@Command(value="add", description="Add a channel to get free game notifications from Epic Games")
	@CommandId(474)
	@Examples({"free games add", "free games add #channel"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void add(Sx4CommandEvent event, @Argument(value="channel", nullDefault=true, endless=true) TextChannel channel) {
		TextChannel effectiveChannel = channel == null ? event.getTextChannel() : channel;

		Document data = new Document("channelId", effectiveChannel.getIdLong())
			.append("guildId", event.getGuild().getIdLong());

		event.getMongo().insertFreeGameChannel(data).whenComplete((result, exception) -> {
			Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
			if (cause instanceof MongoWriteException && ((MongoWriteException) cause).getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
				event.replyFailure("You already have a free games channel setup").queue();
				return;
			}

			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			event.replySuccess("Free game notifications will now be sent in " + effectiveChannel.getAsMention()).queue();
		});
	}

	@Command(value="remove", description="Remove a channel from getting free game notifications from Epic Games")
	@CommandId(475)
	@Examples({"free games remove", "free games remove #channel"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void remove(Sx4CommandEvent event, @Argument(value="channel", nullDefault=true, endless=true) TextChannel channel) {
		TextChannel effectiveChannel = channel == null ? event.getTextChannel() : channel;

		event.getMongo().deleteFreeGameChannel(Filters.eq("channelId", effectiveChannel.getIdLong())).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			event.replySuccess("Free game notifications will no longer be sent in " + effectiveChannel.getAsMention()).queue();
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
		for (FormatterVariable<?> variable : manager.getVariables(FreeGame.class)) {
			content.add("`{game." + variable.getName() + "}` - " + variable.getDescription());
		}

		embed.setDescription(content.toString());

		event.reply(embed.build()).queue();
	}

	@Command(value="message", description="Set the message for the free game notifications")
	@CommandId(477)
	@Examples({"free games message {game.title} is now free!", "free games message {game.title} {game.original_price.equals(0).then().else(was £{game.original_price.format(,##0.00)} and)} is now free!"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void message(Sx4CommandEvent event, @Argument(value="message", endless=true) @Limit(max=2000) String message) {
		event.getMongo().updateFreeGameChannel(Filters.eq("guildId", event.getGuild().getIdLong()), Updates.set("message.content", message), new UpdateOptions()).whenComplete((result, exception) -> {
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
	public void advancedMessage(Sx4CommandEvent event, @Argument(value="message", endless=true) @AdvancedMessage Document message) {
		event.getMongo().updateFreeGameChannel(Filters.eq("guildId", event.getGuild().getIdLong()), Updates.set("message", message), new UpdateOptions()).whenComplete((result, exception) -> {
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
	public void name(Sx4CommandEvent event, @Argument(value="name", endless=true) String name) {
		event.getMongo().updateFreeGameChannel(Filters.eq("guildId", event.getGuild().getIdLong()), Updates.set("webhook.name", name), new UpdateOptions()).whenComplete((result, exception) -> {
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
	public void avatar(Sx4CommandEvent event, @Argument(value="avatar", endless=true, acceptEmpty=true) @ImageUrl String url) {
		event.getMongo().updateFreeGameChannel(Filters.eq("guildId", event.getGuild().getIdLong()), Updates.set("webhook.avatar", url), new UpdateOptions()).whenComplete((result, exception) -> {
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

}
