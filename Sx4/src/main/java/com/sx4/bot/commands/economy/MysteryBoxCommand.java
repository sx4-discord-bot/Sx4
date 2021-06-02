package com.sx4.bot.commands.economy;

import com.jockie.bot.core.argument.Argument;
import com.mongodb.client.model.Projections;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.games.GameState;
import com.sx4.bot.entities.games.GameType;
import com.sx4.bot.entities.games.MysteryBoxGame;
import com.sx4.bot.managers.MysteryBoxManager;
import com.sx4.bot.utility.EconomyUtility;
import com.sx4.bot.utility.ExceptionUtility;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.interactions.components.Component;
import net.dv8tion.jda.api.requests.restaction.interactions.UpdateInteractionAction;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.*;

public class MysteryBoxCommand extends Sx4Command {

	private final String description = "You put down **$%,d**. Click the buttons to reveal what they hold, if you get :moneybag: you will get 0.20x your initial bet and it'll increase with the more successful clicks, if you get a :bomb: you will lose everything. Quit at anytime and leave with your earnings at any time by clicking the quit button.";

	public MysteryBoxCommand() {
		super("mystery box", 399);

		super.setDescription("Choose a box if it's a bag of money get 0.25x your original bet, you can do this as long as you want but if it's a bomb you lose everything");
		super.setExamples("mystery box 1000");
		super.setCategoryAll(ModuleCategory.ECONOMY);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="bet") long bet) {
		MysteryBoxManager manager = event.getBot().getMysteryBoxManager();
		if (manager.hasGame(event.getAuthor())) {
			event.replyFailure("You already have an active mystery box game").queue();
			return;
		}

		long balance = event.getMongo().getUserById(event.getAuthor().getIdLong(), Projections.include("economy.balance")).getEmbedded(List.of("economy", "balance"), 0L);
		if (balance < bet) {
			event.replyFormat("You do not have **$%,d** %s", bet, event.getConfig().getFailureEmote()).queue();
			return;
		}

		Map<Integer, Boolean> boxes = new HashMap<>();
		do {
			int x = event.getRandom().nextInt(24);

			boxes.putIfAbsent(x, true);
		} while (boxes.size() != 10);

		List<ActionRow> rows = new ArrayList<>();
		for (int x = 0; x < 5; x++) {
			List<Button> buttons = new ArrayList<>();
			for (int y = 0; y < 5; y++) {
				int index = (x * 5) + y;

				boxes.putIfAbsent(index, false);

				String id = String.valueOf(index);
				if (x == 4 && y == 4) {
					buttons.add(Button.primary(id, "Quit").asDisabled());
				} else {
					buttons.add(Button.secondary(id, " "));
				}
			}

			rows.add(ActionRow.of(buttons));
		}

		event.getTextChannel().sendMessageFormat(this.description, bet).setActionRows(rows).submit().thenCompose(message -> {
			MysteryBoxGame game = new MysteryBoxGame(message, bet, boxes);
			manager.addGame(event.getAuthor(), game);

			return game.getFuture();
		}).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			boolean won = result.isWon();
			long winnings = result.getWinnings();

			List<Bson> update = List.of(
				won ? Operators.set("economy.balance", Operators.add(Operators.ifNull("$economy.balance", 0L), winnings)) : EconomyUtility.decreaseBalanceUpdate(bet),
				won ? Operators.set("economy.winnings", Operators.add(Operators.ifNull("$economy.winnings", 0L), winnings)) : Operators.set("economy.winnings", Operators.let(new Document("winnings", Operators.ifNull("$economy.winnings", 0L)), Operators.cond(Operators.lt(Operators.ifNull("$economy.balance", 0L), bet), "$$winnings", Operators.subtract("$$winnings", bet))))
			);

			event.getMongo().updateUserById(event.getAuthor().getIdLong(), update).whenComplete((updateResult, updateException) -> {
				if (ExceptionUtility.sendExceptionally(event, updateException)) {
					return;
				}

				boolean canAfford = updateResult.getModifiedCount() != 0;

				ButtonClickEvent buttonEvent = result.getEvent();

				List<ActionRow> newRows = new ArrayList<>(buttonEvent.getMessage().getActionRows());
				for (ActionRow row : newRows) {
					List<Component> components = row.getComponents();
					for (ListIterator<Component> it = components.listIterator(); it.hasNext();) {
						Component component = it.next();
						if (!(component instanceof Button)) {
							continue;
						}

						Button button = (Button) component;
						String id = button.getId();

						if (!won && id.equals(buttonEvent.getComponentId())) {
							it.set(Button.danger(id, Emoji.fromUnicode("\uD83D\uDCA5")).asDisabled());
						} else if (button.getLabel().isBlank()) {
							it.set(Button.of(button.getStyle(), id, Emoji.fromUnicode(boxes.get(Integer.parseInt(id)) ? "\uD83D\uDCA3" : "\uD83D\uDCB0")).asDisabled());
						} else {
							it.set(button.asDisabled());
						}
					}
				}

				UpdateInteractionAction action = buttonEvent.editComponents(newRows);
				if (canAfford) {
					action.setContent(won ? String.format("Congratulations, you pulled out with **$%,d**", winnings + result.getBet()) : ":boom: You hit a bomb and lost everything!");
				} else {
					action.setContent(String.format("You do not have **$%,d** %s", bet, event.getConfig().getFailureEmote())).queue();
					return;
				}

				action.queue();

				Document gameData = new Document("userId", event.getAuthor().getIdLong())
					.append("clicks", result.getClicks())
					.append("bet", result.getBet())
					.append("winnings", won ? winnings - result.getBet() : -result.getBet())
					.append("state", won ? GameState.WIN.getId() : GameState.LOSS.getId())
					.append("gameId", ObjectId.get())
					.append("type", GameType.MYSTERY_BOX.getId());

				event.getMongo().insertGame(gameData).whenComplete(MongoDatabase.exceptionally(event));
			});
		});
	}

}
