package com.sx4.bot.commands.games;

import com.jockie.bot.core.argument.Argument;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.games.GameState;
import com.sx4.bot.entities.games.GameType;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.StringUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

public class GameListCommand extends Sx4Command {

	public GameListCommand() {
		super("game list", 297);

		super.setDescription("Lists basic info on all games a user has played on Sx4");
		super.setExamples("game list", "game list @Shea#6653", "game list Shea");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.GAMES);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="user", endless=true, nullDefault=true) Member member) {
		User user = member == null ? event.getAuthor() : member.getUser();

		List<Document> games = event.getDatabase().getGames(Filters.eq("userId", user.getIdLong()), Projections.include("type", "state")).into(new ArrayList<>());
		if (games.isEmpty()) {
			event.replyFailure("That user has not played any games yet").queue();
			return;
		}

		PagedResult<Document> paged = new PagedResult<>(event.getBot(), games)
			.setAuthor("Game List", null, user.getEffectiveAvatarUrl())
			.setIndexed(false)
			.setPerPage(15)
			.setSelect()
			.setDisplayFunction(game -> "`" + GameType.fromId(game.getInteger("type")).getName() + "` - " + StringUtility.title(GameState.fromId(game.getInteger("state")).name()));

		paged.execute(event);
	}

}
