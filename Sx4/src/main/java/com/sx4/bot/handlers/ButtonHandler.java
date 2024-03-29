package com.sx4.bot.handlers;

import com.mongodb.client.model.*;
import com.sx4.bot.commands.image.ShipCommand;
import com.sx4.bot.commands.info.ServerStatsCommand;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.economy.item.Axe;
import com.sx4.bot.entities.economy.item.Item;
import com.sx4.bot.entities.economy.item.Pickaxe;
import com.sx4.bot.entities.economy.item.Rod;
import com.sx4.bot.entities.games.GuessTheNumberGame;
import com.sx4.bot.entities.image.ImageRequest;
import com.sx4.bot.entities.interaction.ButtonType;
import com.sx4.bot.entities.interaction.CustomButtonId;
import com.sx4.bot.entities.interaction.CustomModalId;
import com.sx4.bot.entities.interaction.ModalType;
import com.sx4.bot.entities.trigger.TriggerActionType;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.paged.InteractionPagedResult;
import com.sx4.bot.utility.*;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import okhttp3.Request;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.jetbrains.annotations.NotNull;

import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ButtonHandler implements EventListener {

	private final Sx4 bot;

	public ButtonHandler(Sx4 bot) {
		this.bot = bot;
	}

	public RestAction<Message> reply(ButtonInteractionEvent event, String content, Object... arguments) {
		return this.reply(event, String.format(content, arguments));
	}

	public RestAction<Message> reply(ButtonInteractionEvent event, String content) {
		return this.reply(event, content, Function.identity());
	}

	public RestAction<Message> reply(ButtonInteractionEvent event, String content, Function<ReplyCallbackAction, ReplyCallbackAction> function) {
		MessageCreateData message = new MessageCreateBuilder()
			.setContent(content)
			.build();

		return this.reply(event, message, function);
	}

	public RestAction<Message> reply(ButtonInteractionEvent event, MessageCreateData message) {
		return this.reply(event, message, Function.identity());
	}

	public RestAction<Message> reply(ButtonInteractionEvent event, MessageCreateData message, Function<ReplyCallbackAction, ReplyCallbackAction> function) {
		return function.apply(event.reply(message)).flatMap(hook -> event.getMessage().editMessageComponents(event.getMessage().getActionRows().stream().map(ActionRow::asDisabled).collect(Collectors.toList())));
	}

	public void handleChannelDeleteConfirm(ButtonInteractionEvent event) {
		Permission permission = event.getChannelType().isThread() ? Permission.MANAGE_THREADS : Permission.MANAGE_CHANNEL;
		if (!event.getMember().hasPermission(permission)) {
			this.reply(event, PermissionUtility.formatMissingPermissions(EnumSet.of(permission)) + " " + this.bot.getConfig().getFailureEmote(), action -> action.setEphemeral(true)).queue();
			return;
		}

		if (!event.getGuild().getSelfMember().hasPermission(permission)) {
			this.reply(event, PermissionUtility.formatMissingPermissions(EnumSet.of(permission), "I am") + " " + this.bot.getConfig().getFailureEmote(), action -> action.setEphemeral(true)).queue();
			return;
		}

		event.deferEdit().queue();
		event.getChannel().delete().queue();
	}

	public void handleModLogDeleteConfirm(ButtonInteractionEvent event) {
		this.bot.getMongo().deleteManyModLogs(Filters.eq("guildId", event.getGuild().getIdLong())).whenComplete((result, databaseException) -> {
			if (ExceptionUtility.sendExceptionally(event.getMessageChannel(), databaseException)) {
				return;
			}

			if (result.getDeletedCount() == 0) {
				this.reply(event, "There are no mod logs in this server " + this.bot.getConfig().getFailureEmote()).queue();
				return;
			}

			this.reply(event, "All your mod logs have been deleted " + this.bot.getConfig().getSuccessEmote()).queue();
		});
	}

	public void handleReactionRoleDeleteConfirm(ButtonInteractionEvent event) {
		this.bot.getMongo().deleteManyReactionRoles(Filters.eq("guildId", event.getGuild().getIdLong())).whenComplete((result, databaseException) -> {
			if (ExceptionUtility.sendExceptionally(event.getMessageChannel(), databaseException)) {
				return;
			}

			if (result.getDeletedCount() == 0) {
				this.reply(event, "There are no reaction roles in this server " + this.bot.getConfig().getFailureEmote()).queue();
				return;
			}

			this.reply(event, "All reaction role data has been deleted in this server " + this.bot.getConfig().getSuccessEmote()).queue();
		});
	}

	public void handleSuggestionDeleteConfirm(ButtonInteractionEvent event) {
		this.bot.getMongo().deleteManySuggestions(Filters.eq("guildId", event.getGuild().getIdLong())).whenComplete((result, databaseException) -> {
			if (ExceptionUtility.sendExceptionally(event.getMessageChannel(), databaseException)) {
				return;
			}

			if (result.getDeletedCount() == 0) {
				this.reply(event, "This server has no suggestions " + this.bot.getConfig().getFailureEmote()).queue();
				return;
			}

			this.reply(event, "All suggestions have been deleted in this server " + this.bot.getConfig().getSuccessEmote()).queue();
		});
	}

	public void handleFakePermissionsDeleteConfirm(ButtonInteractionEvent event) {
		this.bot.getMongo().updateGuildById(event.getGuild().getIdLong(), Updates.unset("fakePermissions.holders")).whenComplete((result, databaseException) -> {
			if (ExceptionUtility.sendExceptionally(event.getMessageChannel(), databaseException)) {
				return;
			}

			this.reply(event, "All fake permission data has been deleted in this server " + this.bot.getConfig().getSuccessEmote()).queue();
		});
	}

	public void handleStarboardDeleteConfirm(ButtonInteractionEvent event) {
		this.bot.getMongo().deleteManyStarboards(Filters.eq("guildId", event.getGuild().getIdLong()))
			.thenCompose(result -> this.bot.getMongo().deleteManyStars(Filters.eq("guildId", event.getGuild().getIdLong())))
			.whenComplete((result, databaseException) -> {
				if (ExceptionUtility.sendExceptionally(event.getMessageChannel(), databaseException)) {
					return;
				}

				if (result.getDeletedCount() == 0) {
					this.reply(event, "There are no starboards in this server " + this.bot.getConfig().getFailureEmote()).queue();
					return;
				}

				this.reply(event, "All starboards have been deleted in this server " + this.bot.getConfig().getSuccessEmote()).queue();
			});
	}

	public void handleTriggerDeleteConfirm(ButtonInteractionEvent event) {
		this.bot.getMongo().deleteManyTriggers(Filters.eq("guildId", event.getGuild().getIdLong())).whenComplete((result, databaseException) -> {
			if (ExceptionUtility.sendExceptionally(event.getMessageChannel(), databaseException)) {
				return;
			}

			if (result.getDeletedCount() == 0) {
				this.reply(event, "There are no triggers in this server " + this.bot.getConfig().getFailureEmote()).queue();
				return;
			}

			this.reply(event, "All triggers have been deleted in this server " + this.bot.getConfig().getSuccessEmote()).queue();
		});
	}

	public void handlePremiumConfirm(ButtonInteractionEvent event, CustomButtonId buttonId) {
		long userId = buttonId.getFirstOwnerId();
		long guildId = buttonId.getArgumentLong(0);
		int days = buttonId.getArgumentInt(1);

		Guild guild = this.bot.getShardManager().getGuildById(guildId);
		if (guild == null) {
			this.reply(event, "The bot is no longer in that server " + this.bot.getConfig().getFailureEmote()).queue();
			return;
		}

		int monthPrice = this.bot.getConfig().getPremiumPrice();
		int price = (int) Math.round((monthPrice / (double) this.bot.getConfig().getPremiumDays()) * days);

		List<Bson> update = List.of(Operators.set("premium.credit", Operators.cond(Operators.gt(price, Operators.ifNull("$premium.credit", 0)), "$premium.credit", Operators.subtract("$premium.credit", price))));
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.include("premium.credit")).upsert(true);

		this.bot.getMongoMain().findAndUpdateUserById(userId, update, options).thenCompose(data -> {
			int credit = data == null ? 0 : data.getEmbedded(List.of("premium", "credit"), 0);
			if (price > credit) {
				this.reply(event, "You do not have enough credit to buy premium for that long " + this.bot.getConfig().getFailureEmote()).queue();
				return CompletableFuture.completedFuture(MongoDatabase.EMPTY_DOCUMENT);
			}

			List<Bson> guildUpdate = List.of(Operators.set("premium.endAt", Operators.add(TimeUnit.DAYS.toSeconds(days), Operators.cond(Operators.or(Operators.extinct("$premium.endAt"), Operators.lt("$premium.endAt", Operators.nowEpochSecond())), Operators.nowEpochSecond(), "$premium.endAt"))));
			FindOneAndUpdateOptions guildOptions = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.include("premium.endAt")).upsert(true);

			return this.bot.getMongo().findAndUpdateGuildById(guildId, guildUpdate, guildOptions);
		}).whenComplete((data, databaseException) -> {
			if (ExceptionUtility.sendExceptionally(event.getMessageChannel(), databaseException) || (data != null && data.isEmpty())) {
				return;
			}

			long endAt = data == null ? 0L : data.getEmbedded(List.of("premium", "endAt"), 0L);

			this.reply(event, "**%s** now has premium for %s%d day%s %s", guild.getName(), endAt == 0 ? "" : "another ", days, days == 1 ? "" : "s", this.bot.getConfig().getSuccessEmote()).queue();
		});
	}

	public void handleAutoRoleDeleteConfirm(ButtonInteractionEvent event) {
		this.bot.getMongo().deleteManyAutoRoles(Filters.eq("guildId", event.getGuild().getIdLong())).whenComplete((result, databaseException) -> {
			if (ExceptionUtility.sendExceptionally(event.getMessageChannel(), databaseException)) {
				return;
			}

			if (result.getDeletedCount() == 0) {
				this.reply(event, "There are no auto roles in this server " + this.bot.getConfig().getFailureEmote()).queue();
				return;
			}

			this.reply(event, "All auto roles have been removed " + this.bot.getConfig().getSuccessEmote()).queue();
		});
	}

	public void handleAxeConfirm(ButtonInteractionEvent event, CustomButtonId buttonId) {
		long userId = buttonId.getFirstOwnerId();
		int itemId = buttonId.getArgumentInt(0);
		int axeId = buttonId.getArgumentInt(1);
		int currentDurability = buttonId.getArgumentInt(2);
		int durability = buttonId.getArgumentInt(3);

		Item item = this.bot.getEconomyManager().getItemById(itemId);
		Axe axe = this.bot.getEconomyManager().getItemById(axeId, Axe.class);

		int itemCount = (int) Math.ceil((((double) axe.getPrice() / item.getPrice()) / axe.getMaxDurability()) * durability);

		List<Bson> update = List.of(Operators.set("amount", Operators.let(new Document("amount", Operators.ifNull("$amount", 0L)), Operators.cond(Operators.lte(itemCount, "$$amount"), Operators.subtract("$$amount", itemCount), "$$amount"))));

		this.bot.getMongo().updateItem(Filters.and(Filters.eq("item.id", itemId), Filters.eq("userId", userId)), update, new UpdateOptions()).thenCompose(result -> {
			if (result.getMatchedCount() == 0 || result.getModifiedCount() == 0) {
				this.reply(event, "You do not have `" + itemCount + " " + item.getName() + "` " + this.bot.getConfig().getFailureEmote()).queue();
				return CompletableFuture.completedFuture(null);
			}

			List<Bson> itemUpdate = List.of(Operators.set("item.durability", Operators.cond(Operators.eq("$item.durability", currentDurability), Operators.add("$item.durability", durability), "$item.durability")));

			return this.bot.getMongo().updateItem(Filters.and(Filters.eq("item.id", axeId), Filters.eq("userId", userId)), itemUpdate, new UpdateOptions());
		}).whenComplete((result, databaseException) -> {
			if (ExceptionUtility.sendExceptionally(event.getMessageChannel(), databaseException) || result == null) {
				return;
			}

			if (result.getMatchedCount() == 0) {
				this.reply(event, "You no longer have that axe " + this.bot.getConfig().getFailureEmote()).queue();
				return;
			}

			if (result.getMatchedCount() == 0) {
				this.reply(event, "The durability of your axe has changed " + this.bot.getConfig().getFailureEmote()).queue();
				return;
			}

			this.reply(event, "You just repaired your axe by **" + durability + "** durability " + this.bot.getConfig().getSuccessEmote()).queue();
		});
	}

	public void handlePickaxeConfirm(ButtonInteractionEvent event, CustomButtonId buttonId) {
		long userId = buttonId.getFirstOwnerId();
		int itemId = buttonId.getArgumentInt(0);
		int pickaxeId = buttonId.getArgumentInt(1);
		int currentDurability = buttonId.getArgumentInt(2);
		int durability = buttonId.getArgumentInt(3);

		Item item = this.bot.getEconomyManager().getItemById(itemId);
		Pickaxe pickaxe = this.bot.getEconomyManager().getItemById(pickaxeId, Pickaxe.class);

		int itemCount = (int) Math.ceil((((double) pickaxe.getPrice() / item.getPrice()) / pickaxe.getMaxDurability()) * durability);

		List<Bson> update = List.of(Operators.set("amount", Operators.let(new Document("amount", Operators.ifNull("$amount", 0L)), Operators.cond(Operators.lte(itemCount, "$$amount"), Operators.subtract("$$amount", itemCount), "$$amount"))));

		this.bot.getMongo().updateItem(Filters.and(Filters.eq("item.id", itemId), Filters.eq("userId", userId)), update, new UpdateOptions()).thenCompose(result -> {
			if (result.getMatchedCount() == 0 || result.getModifiedCount() == 0) {
				this.reply(event, "You do not have `" + itemCount + " " + item.getName() + "` " + this.bot.getConfig().getFailureEmote()).queue();
				return CompletableFuture.completedFuture(null);
			}

			List<Bson> itemUpdate = List.of(Operators.set("item.durability", Operators.cond(Operators.eq("$item.durability", currentDurability), Operators.add("$item.durability", durability), "$item.durability")));

			return this.bot.getMongo().updateItem(Filters.and(Filters.eq("item.id", pickaxeId), Filters.eq("userId", userId)), itemUpdate, new UpdateOptions());
		}).whenComplete((result, databaseException) -> {
			if (ExceptionUtility.sendExceptionally(event.getMessageChannel(), databaseException) || result == null) {
				return;
			}

			if (result.getMatchedCount() == 0) {
				this.reply(event, "You no longer have that pickaxe " + this.bot.getConfig().getFailureEmote()).queue();
				return;
			}

			if (result.getMatchedCount() == 0) {
				this.reply(event, "The durability of your pickaxe has changed " + this.bot.getConfig().getFailureEmote()).queue();
				return;
			}

			this.reply(event, "You just repaired your pickaxe by **" + durability + "** durability " + this.bot.getConfig().getSuccessEmote()).queue();
		});
	}

	public void handleFishingRodConfirm(ButtonInteractionEvent event, CustomButtonId buttonId) {
		long userId = buttonId.getFirstOwnerId();
		int itemId = buttonId.getArgumentInt(0);
		int rodId = buttonId.getArgumentInt(1);
		int currentDurability = buttonId.getArgumentInt(2);
		int durability = buttonId.getArgumentInt(3);

		Item item = this.bot.getEconomyManager().getItemById(itemId);
		Rod rod = this.bot.getEconomyManager().getItemById(rodId, Rod.class);

		int itemCount = (int) Math.ceil((((double) rod.getPrice() / item.getPrice()) / rod.getMaxDurability()) * durability);

		List<Bson> update = List.of(Operators.set("amount", Operators.let(new Document("amount", Operators.ifNull("$amount", 0L)), Operators.cond(Operators.lte(itemCount, "$$amount"), Operators.subtract("$$amount", itemCount), "$$amount"))));

		this.bot.getMongo().updateItem(Filters.and(Filters.eq("item.id", itemId), Filters.eq("userId", userId)), update, new UpdateOptions()).thenCompose(result -> {
			if (result.getMatchedCount() == 0 || result.getModifiedCount() == 0) {
				this.reply(event, "You do not have `" + itemCount + " " + item.getName() + "` " + this.bot.getConfig().getFailureEmote()).queue();
				return CompletableFuture.completedFuture(null);
			}

			List<Bson> itemUpdate = List.of(Operators.set("item.durability", Operators.cond(Operators.eq("$item.durability", currentDurability), Operators.add("$item.durability", durability), "$item.durability")));

			return this.bot.getMongo().updateItem(Filters.and(Filters.eq("item.id", rodId), Filters.eq("userId", userId)), itemUpdate, new UpdateOptions());
		}).whenComplete((result, databaseException) -> {
			if (ExceptionUtility.sendExceptionally(event.getMessageChannel(), databaseException) || result == null) {
				return;
			}

			if (result.getMatchedCount() == 0) {
				this.reply(event, "You no longer have that fishing rod " + this.bot.getConfig().getFailureEmote()).queue();
				return;
			}

			if (result.getMatchedCount() == 0) {
				this.reply(event, "The durability of your fishing rod has changed " + this.bot.getConfig().getFailureEmote()).queue();
				return;
			}

			this.reply(event, "You just repaired your fishing rod by **" + durability + "** durability " + this.bot.getConfig().getSuccessEmote()).queue();
		});
	}

	public void handleSelfRoleDeleteConfirm(ButtonInteractionEvent event) {
		this.bot.getMongo().deleteManySelfRoles(Filters.eq("guildId", event.getGuild().getIdLong())).whenComplete((result, databaseException) -> {
			if (ExceptionUtility.sendExceptionally(event.getMessageChannel(), databaseException)) {
				return;
			}

			if (result.getDeletedCount() == 0) {
				this.reply(event, "There are no self roles in this server " + this.bot.getConfig().getFailureEmote()).queue();
				return;
			}

			this.reply(event, "All self roles have been deleted " + this.bot.getConfig().getSuccessEmote()).queue();
		});
	}

	public void handleTemplateDeleteConfirm(ButtonInteractionEvent event) {
		this.bot.getMongo().deleteManyTemplates(Filters.eq("guildId", event.getGuild().getIdLong())).whenComplete((result, databaseException) -> {
			if (ExceptionUtility.sendExceptionally(event.getMessageChannel(), databaseException)) {
				return;
			}

			if (result.getDeletedCount() == 0) {
				this.reply(event, "There are no templates in this server " + this.bot.getConfig().getFailureEmote()).queue();
				return;
			}

			this.reply(event, "All templates have been deleted in this server " + this.bot.getConfig().getSuccessEmote()).queue();
		});
	}
	
	public void handleGiveawayDeleteConfirm(ButtonInteractionEvent event) {
		this.bot.getMongo().deleteManyGiveaways(Filters.eq("guildId", event.getGuild().getIdLong())).whenComplete((result, databaseException) -> {
			if (ExceptionUtility.sendExceptionally(event.getMessageChannel(), databaseException)) {
				return;
			}

			if (result.getDeletedCount() == 0) {
				this.reply(event, "There are no giveaways in this server " + this.bot.getConfig().getFailureEmote()).queue();
				return;
			}

			this.reply(event, "All giveaways in this server have been deleted " + this.bot.getConfig().getSuccessEmote()).queue();
		});
	}

	public void handleGuessTheNumberModal(ButtonInteractionEvent event, CustomButtonId buttonId) {
		int min = buttonId.getArgumentInt(0), max = buttonId.getArgumentInt(1);

		String id = new CustomModalId.Builder()
			.setType(ModalType.GUESS_THE_NUMBER)
			.setArguments(event.getMessageIdLong())
			.getId();

		Modal modal = Modal.create(id, "Guess The Number")
			.addActionRow(TextInput.create("number", "Choose a number (" + min + "-" + max + ")", TextInputStyle.SHORT).build())
			.build();

		event.replyModal(modal).queue();
	}

	public void handleGuessTheNumberConfirm(ButtonInteractionEvent event, CustomButtonId buttonId) {
		long opponentId = buttonId.getFirstOwnerId();
		long userId = buttonId.getArgumentLong(0);
		int min = buttonId.getArgumentInt(1), max = buttonId.getArgumentInt(2);

		if (this.bot.getGuessTheNumberManager().hasGame(opponentId, userId)) {
			this.reply(event, "You already have an active game with this person " + this.bot.getConfig().getFailureEmote(), action -> action.setEphemeral(true)).queue();
			return;
		}

		String id = new CustomButtonId.Builder()
			.setType(ButtonType.GUESS_THE_NUMBER_MODAL)
			.setTimeout(60)
			.setOwners(userId, opponentId)
			.setArguments(min, max)
			.getId();

		MessageCreateData message = new MessageCreateBuilder()
			.setContent("<@" + opponentId + "> and <@" + userId + "> click below to submit your number")
			.setAllowedMentions(List.of(Message.MentionType.USER))
			.setComponents(ActionRow.of(Button.primary(id, "Guess the number")))
			.build();

		ButtonUtility.disableButtons(event).flatMap(hook -> hook.sendMessage(message)).queue(sentMessage -> {
			GuessTheNumberGame game = new GuessTheNumberGame(this.bot, sentMessage.getIdLong(), opponentId, userId, min, max);
			this.bot.getGuessTheNumberManager().addGame(game);
		});
	}

	public void handleDivorceAllConfirm(ButtonInteractionEvent event, CustomButtonId buttonId) {
		long userId = buttonId.getFirstOwnerId();

		Bson filter = Filters.or(Filters.eq("proposerId", userId), Filters.eq("partnerId", userId));

		this.bot.getMongo().deleteManyMarriages(filter).whenComplete((result, databaseException) -> {
			if (ExceptionUtility.sendExceptionally(event.getChannel(), databaseException)) {
				return;
			}

			if (result.getDeletedCount() == 0) {
				this.reply(event, "You are not married to anyone " + this.bot.getConfig().getFailureEmote()).queue();
				return;
			}

			this.reply(event, "You are no longer married to anyone " + this.bot.getConfig().getSuccessEmote()).queue();
		});
	}

	public void handleGenericReject(ButtonInteractionEvent event) {
		this.reply(event, "Cancelled " + this.bot.getConfig().getSuccessEmote()).queue();
	}

	public void handleMarriageConfirm(ButtonInteractionEvent event, CustomButtonId buttonId) {
		long proposerId = buttonId.getArgumentLong(0);
		long partnerId = buttonId.getFirstOwnerId();

		User user = this.bot.getShardManager().getUserById(partnerId);

		Bson filter = Filters.or(Filters.and(Filters.eq("proposerId", proposerId), Filters.eq("partnerId", partnerId)), Filters.and(Filters.eq("proposerId", proposerId), Filters.eq("partnerId", partnerId)));

		this.bot.getMongo().updateMarriage(filter, Updates.combine(Updates.setOnInsert("proposerId", proposerId), Updates.setOnInsert("partnerId", partnerId))).whenComplete((result, databaseException) -> {
			if (ExceptionUtility.sendExceptionally(event.getChannel(), databaseException)) {
				return;
			}

			if (result.getMatchedCount() != 0) {
				this.reply(event, "You're already married to that user " + this.bot.getConfig().getFailureEmote()).queue();
				return;
			}

			this.reply(event, "You're now married to " + (user == null ? partnerId : user.getAsMention()) + " :tada: :heart:").queue();
		});
	}

	public void handleMarriageReject(ButtonInteractionEvent event, CustomButtonId buttonId) {
		long userId = buttonId.getArgumentLong(0);
		User user = this.bot.getShardManager().getUserById(userId);

		this.reply(event, "Better luck next time " + (user == null ? userId : user.getName()) + " :broken_heart:").queue();
	}

	public void handleShipSwipeLeft(ButtonInteractionEvent event, CustomButtonId buttonId) {
		Member firstMember = event.getGuild().getMemberById(buttonId.getArgumentLong(0));
		if (firstMember == null) {
			this.reply(event, "Could no longer find the first user").queue();
			return;
		}

		User firstUser = firstMember.getUser();

		Random random = new Random();

		List<Member> members = event.getGuild().getMembers();
		User secondUser = members.get(random.nextInt(members.size())).getUser();

		random.setSeed(firstUser.getIdLong() + secondUser.getIdLong());
		int percent = random.nextInt(100) + 1;

		String firstName = firstUser.getName(), secondName = secondUser.getName();
		String shipName = firstName.substring(0, (int) Math.ceil((double) firstName.length() / 2)) + secondName.substring((int) Math.ceil((double) secondName.length() / 2));

		String message = String.format("Ship Name: **%s**\nLove Percentage: **%d%%**", shipName, percent);

		Request request = new ImageRequest(this.bot.getConfig().getImageWebserverUrl("ship"))
			.addQuery("first_image", firstUser.getEffectiveAvatarUrl())
			.addQuery("second_image", secondUser.getEffectiveAvatarUrl())
			.addQuery("percent", percent)
			.build(this.bot.getConfig().getImageWebserver());

		if (event.getGuild().getSelfMember().hasPermission(event.getGuildChannel(), Permission.MESSAGE_ATTACH_FILES)) {
			this.bot.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
				MessageCreateBuilder builder = ImageUtility.getImageMessage(response);
				if (response.isSuccessful()) {
					builder.setContent(message).setComponents(ActionRow.of(ShipCommand.getShipButtons(event.getUser().getIdLong(), firstUser, secondUser)));
				}

				event.editMessage(MessageEditData.fromCreateData(builder.build())).queue();
			});
		} else {
			event.reply(message).queue();
		}
	}

	public void handleShipSwipeRight(ButtonInteractionEvent event, CustomButtonId buttonId) {
		long proposerId = buttonId.getFirstOwnerId();
		long partnerId = buttonId.getArgumentLong(0);

		Member member = event.getGuild().getMemberById(partnerId);
		if (member == null) {
			event.reply("That user is no longer in the server " + this.bot.getConfig().getFailureEmote()).setEphemeral(true)
				.flatMap(hook -> event.getMessage().editMessageComponents(ButtonUtility.disableButtons(event.getMessage().getActionRows(), buttonId.getId())))
				.queue();

			return;
		}

		Bson checkFilter = Filters.or(
			Filters.eq("proposerId", proposerId),
			Filters.eq("partnerId", proposerId),
			Filters.eq("proposerId", partnerId),
			Filters.eq("partnerId", partnerId)
		);

		List<Document> marriages = this.bot.getMongo().getMarriages(checkFilter, Projections.include("partnerId", "proposerId")).into(new ArrayList<>());

		long proposerCount = marriages.stream().filter(d -> d.getLong("proposerId") == proposerId || d.getLong("partnerId") == proposerId).count();
		if (proposerCount >= 5) {
			event.reply("You are already married to 5 users " + this.bot.getConfig().getFailureEmote()).setEphemeral(true).queue();
			return;
		}

		long partnerCount = marriages.stream().filter(d -> d.getLong("proposerId") == partnerId || d.getLong("partnerId") == partnerId).count();
		if (partnerCount >= 5) {
			event.reply("That user is already married to 5 users " + this.bot.getConfig().getFailureEmote()).setEphemeral(true).queue();
			return;
		}

		String acceptId = new CustomButtonId.Builder()
			.setType(ButtonType.MARRIAGE_CONFIRM)
			.setTimeout(60)
			.setOwners(partnerId)
			.setArguments(proposerId)
			.getId();

		String rejectId = new CustomButtonId.Builder()
			.setType(ButtonType.MARRIAGE_REJECT)
			.setTimeout(60)
			.setOwners(partnerId)
			.setArguments(proposerId)
			.getId();

		List<Button> buttons = List.of(Button.success(acceptId, "Yes"), Button.danger(rejectId, "No"));

		event.reply(member.getAsMention() + ", **" + event.getUser().getName() + "** would like to marry you! Do you accept?")
			.setAllowedMentions(EnumSet.of(Message.MentionType.USER))
			.setActionRow(buttons)
			.queue();
	}

	public void handleTriggerVariablePurgeConfirm(ButtonInteractionEvent event, CustomButtonId buttonId) {
		ObjectId id = buttonId.getArgument(0, ObjectId::new);
		long guildId = buttonId.getArgumentLong(1);

		this.bot.getMongo().updateTrigger(Filters.and(Filters.eq("_id", id), Filters.eq("guildId", guildId)), Updates.unset("variables"), new UpdateOptions()).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event.getMessageChannel(), exception)) {
				return;
			}

			if (result.getMatchedCount() == 0) {
				this.reply(event, "I could not find that trigger " + this.bot.getConfig().getFailureEmote()).queue();
				return;
			}

			if (result.getModifiedCount() == 0) {
				this.reply(event, "There were no variables saved on that trigger " + this.bot.getConfig().getFailureEmote()).queue();
				return;
			}

			this.reply(event, "All variables have been removed from that trigger " + this.bot.getConfig().getSuccessEmote()).queue();
		});
	}

	public void handleTriggerUpdateView(ButtonInteractionEvent event, CustomButtonId buttonId) {
		Document template = this.bot.getMongo().getTriggerTemplate(Filters.eq("_id", buttonId.getArgument(0, ObjectId::new)), MongoDatabase.EMPTY_DOCUMENT);
		if (template == null) {
			this.reply(event, "The template connected to that trigger has been deleted " + this.bot.getConfig().getFailureEmote(), action -> action.setEphemeral(true)).queue();
			return;
		}

		Document trigger = template.get("data", Document.class);

		List<Document> actions = trigger.getList("actions", Document.class);
		actions.sort(Comparator.comparingInt(action -> action.getInteger("order", -1)));

		InteractionPagedResult<Document> pagedActions = new InteractionPagedResult.Builder<>(this.bot, actions)
			.setSelect()
			.setEphemeral(true)
			.setPerPage(3)
			.setCustomFunction(page -> {
				EmbedBuilder embed = new EmbedBuilder();
				embed.setTitle(template.getString("name") + " (" + template.getObjectId("_id").toHexString() + ")");
				embed.setDescription("This trigger will be executed when someone sends a message matching `" + trigger.getString("trigger") + "`");

				int uses = template.getInteger("uses");
				embed.setFooter("Used " + uses + " time" + (uses == 1 ? "" : "s") + " | Version " + template.getInteger("version"));

				AtomicInteger actionIndex = new AtomicInteger(1);
				page.forEach((action, index) -> {
					String type = TriggerActionType.fromId(action.getInteger("type")).toString();
					action.append("type", type);

					int order = action.getInteger("order", -1);
					String orderText = (order == -1 ? "Unordered" : NumberUtility.getSuffixed(actionIndex.getAndIncrement())) + " Action";

					embed.addField(type + " (" + orderText + ")", "```json\n" + action.toJson(MongoDatabase.PRETTY_JSON) + "```", false);
				});

				return new MessageCreateBuilder().setEmbeds(embed.build());
			}).build();

		pagedActions.execute(event);
	}

	public void handleTriggerUpdateConfirm(ButtonInteractionEvent event, CustomButtonId buttonId) {
		Document template = this.bot.getMongo().getTriggerTemplate(Filters.eq("_id", buttonId.getArgument(0, ObjectId::new)), Projections.include("data", "version"));
		if (template == null) {
			this.reply(event, "The template connected to that trigger has been deleted " + this.bot.getConfig().getFailureEmote()).queue();
			return;
		}

		Bson update = Updates.set("template.version", template.getInteger("version"));

		Document data = template.get("data", Document.class);
		for (String key : data.keySet()) {
			update = Updates.combine(update, Updates.set(key, data.get(key)));
		}

		this.bot.getMongo().updateTrigger(Filters.eq("_id", buttonId.getArgument(1, ObjectId::new)), update, new UpdateOptions()).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event.getMessageChannel(), exception)) {
				return;
			}

			this.reply(event, "The trigger has been updated to the latest template version " + this.bot.getConfig().getSuccessEmote()).queue();
		});
	}

	public void handleServerStatsGraph(ButtonInteractionEvent event, CustomButtonId buttonId) {
		List<Document> data = this.bot.getMongo().getServerStats(Filters.eq("guildId", event.getGuild().getIdLong()), MongoDatabase.EMPTY_DOCUMENT).into(new ArrayList<>());
		if (data.isEmpty()) {
			this.reply(event, "There is not server stats data on this server").queue();
			return;
		}

		boolean joins = buttonId.getType() == ButtonType.SHOW_SERVER_STATS_JOIN_GRAPH.getId();

		ImageRequest request = new ImageRequest(this.bot.getConfig().getImageWebserverUrl("line-graph"))
			.addField("x_header", "Time")
			.addField("y_header", joins ? "Members Joined" : "Messages Sent");

		List<Document> graphData = data.stream().map(d -> {
			String time = ServerStatsCommand.GRAPH_FORMATTER.format(d.getDate("time").toInstant().atOffset(ZoneOffset.UTC));
			return new Document("value", d.getInteger(joins ? "joins" : "messages", 0)).append("name", time);
		}).collect(Collectors.toList());

		request.addField("data", graphData);

		EmbedBuilder builder = new EmbedBuilder(event.getMessage().getEmbeds().get(0));

		CustomButtonId newButtonId = new CustomButtonId.Builder()
			.setType(joins ? ButtonType.SHOW_SERVER_STATS_MESSAGES_GRAPH : ButtonType.SHOW_SERVER_STATS_JOIN_GRAPH)
			.build();

		Button button = newButtonId.asButton(ButtonStyle.SECONDARY, "View " + (joins ? "Messages" : "Joins") + " Graph").withEmoji(Emoji.fromUnicode("\uD83D\uDCC8"));

		this.bot.getHttpClient().newCall(request.build(null)).enqueue((HttpCallback) response -> {
			if (!response.isSuccessful()) {
				event.reply(ImageUtility.getErrorMessage(response.code(), response.body().string(), null).build()).setEphemeral(true).queue();
				return;
			}

			byte[] image = response.body().bytes();
			builder.setImage("attachment://graph.png");

			event.editMessageEmbeds(builder.build()).setFiles(FileUpload.fromData(image, "graph.png")).setActionRow(button).queue();
		});
	}

	@Override
	public void onEvent(@NotNull GenericEvent genericEvent) {
		if (!(genericEvent instanceof ButtonInteractionEvent event)) {
			return;
		}

		Button button = event.getButton();
		String buttonId = button.getId();
		if (button.isDisabled() || !buttonId.contains(":")) {
			return;
		}

		CustomButtonId customId = CustomButtonId.fromId(buttonId);
		if (customId == null) {
			return;
		}

		if (customId.isExpired()) {
			this.reply(event, "This button has expired :stopwatch:", action -> action.setEphemeral(true)).queue();
			return;
		}

		if (!customId.isOwner(event.getUser().getIdLong())) {
			event.reply("This is not your button " + this.bot.getConfig().getFailureEmote())
				.setEphemeral(true)
				.queue();

			return;
		}

		ButtonType type = ButtonType.fromId(customId.getType());
		switch (type) {
			case MARRIAGE_CONFIRM -> this.handleMarriageConfirm(event, customId);
			case MARRIAGE_REJECT -> this.handleMarriageReject(event, customId);
			case DIVORCE_ALL_CONFIRM -> this.handleDivorceAllConfirm(event, customId);
			case GENERIC_REJECT -> this.handleGenericReject(event);
			case GUESS_THE_NUMBER_CONFIRM -> this.handleGuessTheNumberConfirm(event, customId);
			case GUESS_THE_NUMBER_MODAL -> this.handleGuessTheNumberModal(event, customId);
			case GIVEAWAY_DELETE_CONFIRM -> this.handleGiveawayDeleteConfirm(event);
			case TEMPLATE_DELETE_CONFIRM -> this.handleTemplateDeleteConfirm(event);
			case SELF_ROLE_DELETE_CONFIRM -> this.handleSelfRoleDeleteConfirm(event);
			case FISHING_ROD_REPAIR_CONFIRM -> this.handleFishingRodConfirm(event, customId);
			case PICKAXE_REPAIR_CONFIRM -> this.handlePickaxeConfirm(event, customId);
			case AXE_REPAIR_CONFIRM -> this.handleAxeConfirm(event, customId);
			case AUTO_ROLE_DELETE_CONFIRM -> this.handleAutoRoleDeleteConfirm(event);
			case PREMIUM_CONFIRM -> this.handlePremiumConfirm(event, customId);
			case FAKE_PERMISSIONS_DELETE_CONFIRM -> this.handleFakePermissionsDeleteConfirm(event);
			case TRIGGER_DELETE_CONFIRM -> this.handleTriggerDeleteConfirm(event);
			case STARBOARD_DELETE_CONFIRM -> this.handleStarboardDeleteConfirm(event);
			case SUGGESTION_DELETE_CONFIRM -> this.handleSuggestionDeleteConfirm(event);
			case REACTION_ROLE_DELETE_CONFIRM -> this.handleReactionRoleDeleteConfirm(event);
			case MOD_LOG_DELETE_CONFIRM -> this.handleModLogDeleteConfirm(event);
			case CHANNEL_DELETE_CONFIRM -> this.handleChannelDeleteConfirm(event);
			case SHIP_SWIPE_LEFT -> this.handleShipSwipeLeft(event, customId);
			case TRIGGER_VARIABLE_PURGE_CONFIRM -> this.handleTriggerVariablePurgeConfirm(event, customId);
			case SHIP_SWIPE_RIGHT -> this.handleShipSwipeRight(event, customId);
			case TRIGGER_UPDATE_VIEW -> this.handleTriggerUpdateView(event, customId);
			case TRIGGER_UPDATE_CONFIRM -> this.handleTriggerUpdateConfirm(event, customId);
			case SHOW_SERVER_STATS_MESSAGES_GRAPH, SHOW_SERVER_STATS_JOIN_GRAPH -> this.handleServerStatsGraph(event, customId);
		}
	}

}
