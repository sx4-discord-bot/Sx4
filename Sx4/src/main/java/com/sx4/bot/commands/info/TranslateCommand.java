package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.option.Option;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.argument.MessageArgument;
import com.sx4.bot.entities.argument.Or;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.utility.SearchUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import okhttp3.FormBody;
import okhttp3.Request;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;

public class TranslateCommand extends Sx4Command {

	public TranslateCommand() {
		super("translate", 300);

		super.setDescription("Translate some text from one language to another");
		super.setAliases("tr");
		super.setExamples("translate fr hello", "translate bonjour", "translate en hej --from=sv");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public CompletableFuture<String> getQuery(Or<MessageArgument, String> option) {
		if (option.hasFirst()) {
			return option.getFirst().retrieveMessage().submit().thenApply(Message::getContentRaw);
		} else {
			return CompletableFuture.completedFuture(option.getSecond());
		}
	}

	public MessageEmbed getEmbed(String body, String query, Locale from, Locale to) {
		JSONArray array = new JSONArray(new JSONArray(body.substring(4)).getJSONArray(0).getString(2));
		JSONArray inputArray = array.getJSONArray(0), outputArray = array.getJSONArray(1);

		Locale inputLocale = from == null ? SearchUtility.getLocaleFromTag(inputArray.getString(2)) : from;

		Object correctionObject = inputArray.get(1);

		String inputText;
		if (correctionObject == JSONObject.NULL) {
			inputText = query;
		} else {
			Object object = ((JSONArray) correctionObject).get(0);
			if (object == JSONObject.NULL) {
				inputText = query;
			} else {
				Document document = Jsoup.parse(((JSONArray) object).getJSONArray(0).getString(1));
				for (Element element : document.getElementsByTag("i")) {
					element.replaceWith(new TextNode("**" + element.text() + "**"));
				}

				inputText = document.text();
			}
		}

		JSONArray segments = outputArray.getJSONArray(0)
			.getJSONArray(0)
			.getJSONArray(5);

		StringJoiner output = new StringJoiner(" ");
		for (int i = 0; i < segments.length(); i++) {
			output.add(segments.getJSONArray(i).getString(0));
		}

		EmbedBuilder embed = new EmbedBuilder()
			.setColor(0x4285f4)
			.setAuthor("Google Translate", null, "https://upload.wikimedia.org/wikipedia/commons/d/db/Google_Translate_Icon.png")
			.addField("Input Text (" + (inputLocale == null ? "Unknown" : inputLocale.getDisplayLanguage()) + ")", inputText, false)
			.addField("Output Text (" + to.getDisplayLanguage() + ")", output.toString(), false);

		return embed.build();
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="to") Locale to, @Argument(value="query | message id", endless=true, acceptEmpty=true) Or<MessageArgument, String> option, @Option(value="from", description="Choose what language to translate from") Locale from) {
		String toTag = to.getLanguage(), fromTag = from == null ? "auto" : from.getLanguage();

		this.getQuery(option).whenComplete((query, exception) -> {
			if (query.length() > 1000) {
				event.replyFailure("Query length cannot be any more than **1000** characters").queue();
				return;
			}

			String value = "%5B%5B%5B%22MkEWBc%22%2C%22%5B%5B%5C%22" + URLEncoder.encode(query.replace("\\", "\\\\\\\\").replace("\"", "\\\\\\\""), StandardCharsets.UTF_8).replace("%0A", "\n") + "%5C%22%2C%5C%22" + fromTag + "%5C%22%2C%5C%22" + toTag + "%5C%22%2Ctrue%5D%2C%5Bnull%5D%5D%22%2Cnull%2C%22generic%22%5D%5D%5D";

			FormBody requestBody = new FormBody.Builder()
				.addEncoded("f.req", value)
				.build();

			Request request = new Request.Builder()
				.url("https://translate.google.com/_/TranslateWebserverUi/data/batchexecute")
				.post(requestBody)
				.build();

			event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> event.reply(this.getEmbed(response.body().string(), query, from, to)).queue());
		});
	}

}
