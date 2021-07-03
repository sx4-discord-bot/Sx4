package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.annotations.argument.DefaultNumber;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.paged.PagedResult;
import net.dv8tion.jda.api.Permission;
import okhttp3.Request;
import org.bson.Document;

import java.util.List;

public class BotListCommand extends Sx4Command {

	public BotListCommand() {
		super("bot list", 337);

		super.setDescription("View the top 500 bots on top.gg sorted by server count");
		super.setAliases("botlist");
		super.setExamples("bot list", "bot list 5");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="page") @DefaultNumber(1) @Limit(min=1, max=50) int page) {
		Request request = new Request.Builder()
			.url("https://top.gg/api/bots?sort=server_count&limit=500&fields=username,server_count,id")
			.addHeader("Authorization", event.getConfig().getTopGG())
			.build();

		event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
			Document data = Document.parse(response.body().string());

			List<Document> results = data.getList("results", Document.class);
			PagedResult<Document> paged = new PagedResult<>(event.getBot(), results)
				.setPage(page)
				.setAuthor("Bot List", null, "https://imgur.com/HlfRQ3g.png")
				.setIncreasedIndex(true)
				.setSelect()
				.setDisplayFunction(bot -> String.format("[%s](https://top.gg/bot/%s) - **%,d** servers", bot.getString("username"), bot.getString("id"), bot.getInteger("server_count")));

			paged.execute(event);
		});
	}

}
