package com.sx4.bot.commands.fun;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.http.HttpCallback;
import net.dv8tion.jda.api.Permission;
import okhttp3.Request;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class TTSCommand extends Sx4Command {

	public TTSCommand() {
		super("tts", 271);

		super.setDescription("Get text to speech of a phrase");
		super.setAliases("text to speech", "texttospeech");
		super.setExamples("tts hello", "tts Robot voices are cool");
		super.setBotDiscordPermissions(Permission.MESSAGE_ATTACH_FILES);
		super.setCategoryAll(ModuleCategory.FUN);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="query", endless=true) @Limit(max=200) String query) {
		Request request = new Request.Builder()
			.url("https://translate.google.com/translate_tts?ie=UTF-8&client=tw-ob&tl=en-gb&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8))
			.build();

		event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
			event.replyFile(response.body().bytes(), query + ".mp3").queue();
		});
	}

}
