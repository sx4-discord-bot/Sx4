package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.paged.PagedResult;
import net.dv8tion.jda.api.Permission;
import okhttp3.Request;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class GoogleCommand extends Sx4Command {

	public GoogleCommand() {
		super("google", 208);

		super.setDescription("Searches a query up on google");
		super.setExamples("google How to use Sx4", "google Sx4 discord bot");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="query", endless=true) String query) {
		boolean nsfw = event.getTextChannel().isNSFW();
		String url ="https://google.com/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + (nsfw ? "" : "&safe=active");

		Request request = new Request.Builder()
			.url(url)
			.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.141 Safari/537.3")
			.build();

		event.getClient().newCall(request).enqueue((HttpCallback) response -> {
			Document document = Jsoup.parse(response.body().string());

			List<String> elements = new ArrayList<>();
			for (Element element : document.select("div.rc")) {
				Element titleElement = element.getElementsByTag("a").get(0);

				String webUrl = titleElement.attr("href");
				String title = titleElement.getElementsByTag("h3").get(0).text();
				String description = element.getElementsByTag("div").get(8).text();

				elements.add(String.format("**[%s](%s)**\n%s\n", title, webUrl, description));
			}

			if (elements.isEmpty()) {
				event.replyFailure("I could not find any results for that query").queue();
				return;
			}

			PagedResult<String> paged = new PagedResult<>(elements)
				.setIndexed(false)
				.setPerPage(3)
				.setAuthor("Google", url, "http://i.imgur.com/G46fm8J.png");

			paged.execute(event);
		});
	}

}
