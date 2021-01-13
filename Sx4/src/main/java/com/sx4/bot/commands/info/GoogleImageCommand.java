package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.paged.PagedResult;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import okhttp3.Request;
import org.jsoup.Jsoup;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GoogleImageCommand extends Sx4Command {

	private final Pattern pattern = Pattern.compile("]\n,\\[\"(https?://(www\\.)?[-a-zA-Z0-9@:%._+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_+.~#?&/=]*))\\S*\",\\d+,\\d+]");

	public GoogleImageCommand() {
		super("google image", 209);

		super.setDescription("Search up a query on google images");
		super.setExamples("google image dog", "google image cat");
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="query", endless=true) String query) {
		boolean nsfw = event.getTextChannel().isNSFW();
		String url = String.format("https://google.com/search?q=%s&tbm=isch%s", URLEncoder.encode(query, StandardCharsets.UTF_8), nsfw ? "" : "&safe=active");

		Request request = new Request.Builder()
			.url(url)
			.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.141 Safari/537.3")
			.build();

		event.getClient().newCall(request).enqueue((HttpCallback) response -> {
			Document document = Jsoup.parse(response.body().string());

			Element element = document.getElementsByTag("script").get(41);

			List<String> urls = new ArrayList<>();
			for (DataNode node : element.dataNodes()) {
				Matcher matcher = this.pattern.matcher(node.getWholeData());
				while (matcher.find()) {
					urls.add(matcher.group(1));
				}
			}

			if (urls.isEmpty()) {
				event.replyFailure("I could not find any images for that query").queue();
				return;
			}

			PagedResult<String> paged = new PagedResult<>(urls)
				.setPerPage(1)
				.setCustomFunction(page -> {
					EmbedBuilder embed = new EmbedBuilder();
					embed.setAuthor("Google", url, "http://i.imgur.com/G46fm8J.png");
					embed.setFooter("next | previous | go to <page_number> | cancel");
					embed.setTitle("Page " + page.getPage() + "/" + page.getMaxPage());

					page.forEach((imageUrl, index) -> {
						embed.setImage(imageUrl);
					});

					return new MessageBuilder().setEmbed(embed.build()).build();
				});

			paged.execute(event);
		});
	}

}
