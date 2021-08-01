package com.sx4.bot.commands.fun;

import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.http.HttpCallback;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import okhttp3.Request;
import org.bson.Document;

public class CatCommand extends Sx4Command {

	public CatCommand() {
		super("cat", 127);

		super.setDescription("Gives a random image of a cat");
		super.setExamples("cat");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.FUN);
	}

	public void onCommand(Sx4CommandEvent event) {
		Request request = new Request.Builder()
			.url("http://aws.random.cat/meow")
			.build();

		event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
			Document data = Document.parse(response.body().string());

			EmbedBuilder embed = new EmbedBuilder()
				.setDescription(":cat:")
				.setImage(data.getString("file"));

			event.reply(embed.build()).queue();
		});
	}

}
