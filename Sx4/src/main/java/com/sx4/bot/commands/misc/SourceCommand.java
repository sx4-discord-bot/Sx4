package com.sx4.bot.commands.misc;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.http.HttpCallback;
import okhttp3.Request;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class SourceCommand extends Sx4Command {

	public SourceCommand() {
		super("source", 213);

		super.setDescription("Gets the GitHub source code for a command");
		super.setExamples("source help", "source auto role", "source dictionary");
		super.setCategoryAll(ModuleCategory.MISC);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="command", endless=true) Sx4Command command) {
		String path = command.getCommandMethod().getDeclaringClass().getName();

		int lastIndex = path.lastIndexOf('.');

		String className = path.substring(lastIndex);
		if (className.contains("$")) {
			String[] classes = className.split("\\$");
			String lastClassName = classes[classes.length - 1];

			String fullPath = "Sx4/blob/rewrite/Sx4/src/main/java/" + path.replace(".", "/").substring(0, path.indexOf("$")) + ".java";

			Request request = new Request.Builder()
				.url(String.format("https://github.com/sx4-discord-bot/Sx4/find-definition?q=%s&blob_path=%s&ref=rewrite&language=Java", URLEncoder.encode(lastClassName, StandardCharsets.UTF_8), URLEncoder.encode(fullPath, StandardCharsets.UTF_8)))
				.build();

			event.getClient().newCall(request).enqueue((HttpCallback) response -> {
				Document document = Jsoup.parse(response.body().string());

				for (Element element : document.getElementsByClass("TagsearchPopover-item")) {
					String link = element.attr("href");
					if (link.contains(fullPath)) {
						event.reply("https://github.com" + link).queue();
						return;
					}
				}

				// should basically be impossible, unless GitHub has an outage
				event.reply("If you reach this message then GitHub didn't find something somehow").queue();
			});

			return;
		}

		event.reply("https://github.com/sx4-discord-bot/Sx4/blob/rewrite/Sx4/src/main/java/" + path.replace(".", "/") + ".java").queue();
	}

}
