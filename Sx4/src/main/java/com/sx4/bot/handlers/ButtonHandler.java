package com.sx4.bot.handlers;

import com.mongodb.client.model.*;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.economy.item.Axe;
import com.sx4.bot.entities.economy.item.Item;
import com.sx4.bot.entities.economy.item.Pickaxe;
import com.sx4.bot.entities.economy.item.Rod;
import com.sx4.bot.entities.games.GuessTheNumberGame;
import com.sx4.bot.entities.interaction.ButtonType;
import com.sx4.bot.entities.interaction.CustomButtonId;
import com.sx4.bot.entities.interaction.CustomModalId;
import com.sx4.bot.entities.interaction.ModalType;
import com.sx4.bot.utility.ButtonUtility;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.PermissionUtility;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Modal;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
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
		Message message = new MessageBuilder()
			.setContent(content)
			.build();

		return this.reply(event, message, function);
	}

	public RestAction<Message> reply(ButtonInteractionEvent event, Message message) {
		return this.reply(event, message, Function.identity());
	}

	public RestAction<Message> reply(ButtonInteractionEvent event, Message message, Function<ReplyCallbackAction, ReplyCallbackAction> function) {
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

			List<Bson> guildUpdate = List.of(Operators.set("premium.endAt", Operators.add(TimeUnit.DAYS.toSeconds(days), Operators.ifNull("$premium.endAt", Operators.nowEpochSecond()))));
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

		Message message = new MessageBuilder()
			.setContent("<@" + opponentId + "> and <@" + userId + "> click below to submit your number")
			.allowMentions(Message.MentionType.USER)
			.setActionRows(ActionRow.of(Button.primary(id, "Guess the number")))
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
		}
	}

}
