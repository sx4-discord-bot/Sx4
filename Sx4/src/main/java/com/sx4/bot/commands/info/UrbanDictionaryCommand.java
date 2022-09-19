package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.paged.PagedResult;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import okhttp3.Request;
import org.bson.Document;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

public class UrbanDictionaryCommand extends Sx4Command {

	public UrbanDictionaryCommand() {
		super("urban dictionary", 211);

		super.setAliases("ud");
		super.setDescription("Search up a query on the urban dictionary");
		super.setExamples("urban dictionary hello", "urban dictionary shea");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	private String getUrbanText(String text, String readMore) {
		StringBuilder newText = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char character = text.charAt(i);
			BRACKET : if (character == '[') {
				int endIndex = text.indexOf(']', i + 1);
				if (endIndex == -1) {
					break BRACKET;
				}

				String word = text.substring(i + 1, endIndex);

				String hyperlink = String.format("[%s](https://www.urbandictionary.com/define.php?term=%s)", word, URLEncoder.encode(word, StandardCharsets.UTF_8));
				if (newText.length() + hyperlink.length() > MessageEmbed.VALUE_MAX_LENGTH - readMore.length()) {
					newText.append(readMore);
					break;
				} else {
					newText.append(hyperlink);
					i = endIndex;
					continue;
				}
			}

			if (newText.length() + 1 > MessageEmbed.VALUE_MAX_LENGTH - readMore.length()) {
				newText.append(readMore);
				break;
			}

			newText.append(character);
		}

		return newText.toString();
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="query", endless=true) String query) {
		Request request = new Request.Builder()
			.url("http://api.urbandictionary.com/v0/define?term=" + URLEncoder.encode(query, StandardCharsets.UTF_8))
			.build();

		event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
			Document json = Document.parse(response.body().string());

			List<Document> list = json.getList("list", Document.class, Collections.emptyList());
			if (list.isEmpty()) {
				event.replyFailure("I could not find any results for that query").queue();
				return;
			}

			PagedResult<Document> paged = new PagedResult<>(event.getBot(), list)
				.setPerPage(1)
				.setCustomFunction(page -> {
					EmbedBuilder embed = new EmbedBuilder();
					embed.setFooter(PagedResult.DEFAULT_FOOTER_TEXT, null);
					embed.setTitle("Page " + page.getPage() + "/" + page.getMaxPage());

					page.forEach((data, index) -> {
						String readMore = "... [Read more](" + data.getString("permalink") + ")";

						String definition = this.getUrbanText(data.getString("definition"), readMore);
						String example = this.getUrbanText(data.getString("example"), readMore);

						embed.setAuthor(data.getString("word"), data.getString("permalink"), null);
						embed.addField("Definition", definition, false);

						if (!example.isEmpty()) {
							embed.addField("Example", example, false);
						}

						embed.addField("Upvotes", data.getInteger("thumbs_up") + " 👍", true);
						embed.addField("Downvotes", data.getInteger("thumbs_down") + " 👎", true);
					});

					return new MessageCreateBuilder().setEmbeds(embed.build());
				});

			paged.execute(event);
		});
	}

}
