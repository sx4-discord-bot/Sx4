package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.StringUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import okhttp3.Request;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class DictionaryCommand extends Sx4Command {

	private static class DictionaryResult {

		private final String definition;
		private final List<String> examples;

		private DictionaryResult(String definition) {
			this.definition = definition;
			this.examples = new ArrayList<>();
		}

		public void addExample(String example) {
			this.examples.add(example);
		}

		public String getDefinition() {
			return this.definition;
		}

		public List<String> getExamples() {
			return this.examples;
		}

	}

	public DictionaryCommand() {
		super("dictionary", 210);

		super.setAliases("define");
		super.setDescription("Look up the definition for a specific word");
		super.setExamples("dictionary hello", "dictionary placebo");
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="query", endless=true) String query) {
		Request request = new Request.Builder()
			.url("https://www.oxfordlearnersdictionaries.com/definition/english/" + URLEncoder.encode(query, StandardCharsets.UTF_8))
			.build();

		event.getClient().newCall(request).enqueue((HttpCallback) response -> {
			Document document = Jsoup.parse(response.body().string());

			Elements elements = document.getElementsByClass("sense");
			if (elements.isEmpty()) {
				event.replyFailure("I could not find a definition for that query").queue();
			}

			Element pronunciationElement = document.getElementsByClass("sound audio_play_button pron-uk icon-audio").first();
			String pronunciation = pronunciationElement == null ? null : pronunciationElement.attr("data-src-mp3");

			List<DictionaryResult> results = new ArrayList<>();
			for (Element element : elements) {
				Element definitionElement = element.getElementsByClass("def").first();

				Elements referenceElements = definitionElement.getElementsByClass("Ref");
				List<TextNode> nodes = definitionElement.textNodes();

				StringBuilder definition = new StringBuilder();
				for (int i = 0; i < nodes.size(); i++) {
					definition.append(nodes.get(i).text());

					if (i < referenceElements.size()) {
						Element reference = referenceElements.get(i);
						definition.append(String.format("[%s](%s)", reference.getElementsByClass("ndv").first().text(), reference.attr("href")));
					}
				}

				DictionaryResult result = new DictionaryResult(definition.toString());

				Element exampleElement = element.getElementsByClass("examples").first();
				if (exampleElement != null) {
					Elements examples = exampleElement.getElementsByTag("li");
					for (Element example : examples.subList(0, Math.min(examples.size(), 3))) {
						result.addExample(example.text());
					}
				}

				results.add(result);
			}

			PagedResult<DictionaryResult> paged = new PagedResult<>(results)
				.setPerPage(1)
				.setCustomFunction(page -> {
					EmbedBuilder embed = new EmbedBuilder();
					embed.setAuthor(StringUtility.title(query), null, null);
					embed.setTitle("Page " + page.getPage() + "/" + page.getMaxPage());
					embed.setFooter("next | previous | go to <page_number> | cancel");

					page.forEach((result, index) -> {
						embed.addField("Definition", String.format("%s%s", result.getDefinition(), result.getExamples().isEmpty() ? "" : "\n\n*" + String.join("*\n*", result.getExamples()) + "*"), false);
					});

					if (pronunciation != null) {
						embed.addField("Pronunciation", String.format("[Listen Here](%s)", pronunciation), false);
					}

					return new MessageBuilder().setEmbed(embed.build()).build();
				});

			paged.execute(event);
		});
	}

}
