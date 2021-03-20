package com.sx4.bot.commands.fun;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.StringUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.util.*;

public class TeamsCommand extends Sx4Command {

	public TeamsCommand() {
		super("teams", 299);

		super.setDescription("Create multiple teams from a list of text");
		super.setExamples("teams 2 Shea Joakim Lucas Adrian", "teams 3 dog cat fox bear hamster monkey");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.FUN);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="amount") @Limit(min=2) int amount, @Argument(value="items") String... itemsArray) {
		if (itemsArray.length < amount) {
			event.replyFailure("You cannot have more items then the amount of teams").queue();
			return;
		}

		List<String> items = new ArrayList<>(Arrays.asList(itemsArray));

		int perTeam = (int) Math.ceil(itemsArray.length / (double) amount);
		if (perTeam > 50) {
			event.replyFailure("Only 50 items can be on each team").queue();
			return;
		}

		int remainder = itemsArray.length % perTeam;

		List<List<String>> teams = new ArrayList<>(amount);
		for (int i = 0; i < amount; i++) {
			int added = 0;

			List<String> team = new ArrayList<>(perTeam);
			while (added++ != (i == amount - 1 && remainder != 0 ? remainder : perTeam)) {
				String item = items.remove(event.getRandom().nextInt(items.size()));
				team.add(item);
			}

			teams.add(team);
		}

		PagedResult<List<String>> paged = new PagedResult<>(event.getBot(), teams)
			.setPerPage(1)
			.setSelect()
			.setCustomFunction(page -> {
				EmbedBuilder embed = new EmbedBuilder()
					.setTitle("Team " + page.getPage() + "/" + page.getMaxPage())
					.setFooter("next | previous | go to <page_number> | cancel", null);

				page.forEach((list, index) -> {
					for (String item : list) {
						embed.appendDescription(StringUtility.limit(item, (int) Math.floor(MessageEmbed.TEXT_MAX_LENGTH / (double) perTeam), "...") + "\n");
					}
				});

				return new MessageBuilder().setEmbed(embed.build()).build();
			});

		paged.execute(event);
	}

}
