package com.sx4.bot.commands.games;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.option.Option;
import com.sx4.bot.annotations.argument.DefaultNumber;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.interaction.ButtonType;
import com.sx4.bot.entities.interaction.CustomButtonId;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.util.EnumSet;
import java.util.List;

public class GuessTheNumberCommand extends Sx4Command {

	public GuessTheNumberCommand() {
		super("guess the number", 298);

		super.setDescription("2 players choose a number between a range of numbers whoever is closest to the random number wins");
		super.setAliases("gtn");
		super.setExamples("guess the number @Shea#6653", "guess the number Shea --max=1000", "guess the number 402557516728369153 --min=5 --max=100");
		super.setCategoryAll(ModuleCategory.GAMES);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="user", endless=true) Member member, @Option(value="min", description="Choose the minimum number of the range") @Limit(min=1) @DefaultNumber(1) int min, @Option(value="max", description="Choose the maximum number of the range") @Limit(min=2) @DefaultNumber(50) int max) {
		User opponent = member.getUser();
		if (opponent.isBot()) {
			event.replyFailure("You cannot play against bots").queue();
			return;
		}

		if (opponent.getIdLong() == event.getAuthor().getIdLong()) {
			event.replyFailure("You cannot play against yourself").queue();
			return;
		}

		String acceptId = new CustomButtonId.Builder()
			.setType(ButtonType.GUESS_THE_NUMBER_CONFIRM)
			.setOwners(opponent.getIdLong())
			.setArguments(event.getAuthor().getIdLong(), min, max)
			.getId();

		String rejectId = new CustomButtonId.Builder()
			.setType(ButtonType.GENERIC_REJECT)
			.setOwners(opponent.getIdLong())
			.setArguments(event.getAuthor().getIdLong(), min, max)
			.getId();

		List<Button> buttons = List.of(Button.success(acceptId, "Yes"), Button.danger(rejectId, "No"));

		event.reply(opponent.getAsMention() + ", do you want to play guess the number with **" + event.getAuthor().getName() + "**?")
			.allowedMentions(EnumSet.of(Message.MentionType.USER))
			.setActionRow(buttons)
			.queue();
	}

}
