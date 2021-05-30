package com.sx4.bot.commands.fun;

import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.http.HttpCallback;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import okhttp3.Request;
import org.bson.Document;

public class DuckCommand extends Sx4Command {

	public DuckCommand() {
		super("duck", 375);

		super.setDescription("Gives a random image of a duck");
		super.setExamples("duck");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
	}

	public void onCommand(Sx4CommandEvent event) {
		Request request = new Request.Builder()
			.url("https://random-d.uk/api/v1/random")
			.build();

		event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
			Document data = Document.parse(response.body().string());

			EmbedBuilder embed = new EmbedBuilder()
				.setDescription(":duck:")
				.setImage(data.getString("url"));

			event.reply(embed.build()).queue();
		});
	}

}
