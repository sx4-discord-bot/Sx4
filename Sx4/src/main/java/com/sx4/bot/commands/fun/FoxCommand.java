package com.sx4.bot.commands.fun;

import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.http.HttpCallback;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import okhttp3.Request;
import org.bson.Document;

public class FoxCommand extends Sx4Command {

	public FoxCommand() {
		super("fox", 377);

		super.setDescription("Gives a random image of a fox");
		super.setExamples("fox");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.FUN);
	}

	public void onCommand(Sx4CommandEvent event) {
		Request request = new Request.Builder()
			.url("https://randomfox.ca/floof/")
			.build();

		event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
			Document data = Document.parse(response.body().string());

			EmbedBuilder embed = new EmbedBuilder()
				.setDescription(":fox:")
				.setImage(data.getString("image"));

			event.reply(embed.build()).queue();
		});
	}

}
