package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.http.HttpCallback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.bson.Document;

public class ShortenCommand extends Sx4Command {

	public ShortenCommand() {
		super("shorten", 33);

		super.setDescription("Shortens a url into a bit.ly url");
		super.setExamples("shorten https://github.com/sponsors/Shea4", "shorten https://patreon.com/Sx4");
		super.setCooldownDuration(5);
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="url") String url) {
		Request request = new Request.Builder()
			.url(String.format("%s/api/shorten", event.getConfig().getDomain()))
			.post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), new Document("url", url).toJson()))
			//.addHeader("Authorization", "Bearer " + event.getConfig().getBitly())
			//.addHeader("Content-Type", "application/json")
			.build();

		event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
			Document json = Document.parse(response.body().string());

			event.replyFormat("<" + event.getConfig().getDomain() + "/" + json.getString("_id") + ">").queue();
		});
	}

}
