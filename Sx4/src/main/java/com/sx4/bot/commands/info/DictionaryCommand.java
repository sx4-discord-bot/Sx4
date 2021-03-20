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
import net.dv8tion.jda.api.Permission;
import okhttp3.Request;
import org.bson.Document;

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
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="query", endless=true) String query) {
		Request request = new Request.Builder()
			.url(event.getConfig().getSearchWebserverUrl("dictionary") + "?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8))
			.build();

		event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
			Document document = Document.parse(response.body().string());
			if (!response.isSuccessful()) {
				StringBuilder builder = new StringBuilder("Command failed with status " + response.code());
				if (document.containsKey("message")) {
					builder.append(" with message `").append(document.getString("message")).append("`");
				}

				event.replyFailure(builder.toString()).queue();
				return;
			}

			List<Document> definitions = document.getList("definitions", Document.class);
			if (definitions.isEmpty()) {
				event.replyFailure("I could not find a definition for that word").queue();
				return;
			}

			PagedResult<Document> paged = new PagedResult<>(event.getBot(), definitions)
				.setPerPage(1)
				.setCustomFunction(page -> {
					EmbedBuilder embed = new EmbedBuilder();
					embed.setAuthor(StringUtility.title(query) + " (" + document.getString("type") + ")", document.getString("url"), null);
					embed.setTitle("Page " + page.getPage() + "/" + page.getMaxPage());
					embed.setFooter(PagedResult.DEFAULT_FOOTER_TEXT);

					page.forEach((data, index) -> {
						StringBuilder definition = new StringBuilder();
						for (Document node : data.getList("nodes", Document.class)) {
							if (node.containsKey("url")) {
								definition.append("[").append(node.getString("text")).append("](").append(node.getString("url")).append(")");
							} else {
								definition.append(node.getString("text"));
							}
						}

						List<String> examples = data.getList("examples", String.class);

						embed.addField("Definition", definition.toString() + (examples.isEmpty() ? "" : "\n\n*" + String.join("*\n*", examples.subList(0, Math.min(3, examples.size()))) + "*"), false);
					});

					if (document.containsKey("pronunciation")) {
						embed.addField("Pronunciation", String.format("[Listen Here](%s)", document.getString("pronunciation")), false);
					}

					return new MessageBuilder().setEmbed(embed.build()).build();
				});

			paged.execute(event);
		});
	}

}
