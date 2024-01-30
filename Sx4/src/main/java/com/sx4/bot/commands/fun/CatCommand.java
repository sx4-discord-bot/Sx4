package com.sx4.bot.commands.fun;

import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.http.HttpCallback;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import okhttp3.Request;
import org.bson.Document;
import org.json.JSONArray;
import org.json.JSONObject;

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
			.url("https://api.thecatapi.com/v1/images/search")
			.build();

		event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
			JSONArray array = new JSONArray(response.body().string());
			JSONObject data = array.getJSONObject(0);

			EmbedBuilder embed = new EmbedBuilder()
				.setDescription(":cat:")
				.setImage(data.getString("url"));

			event.reply(embed.build()).queue();
		});
	}

}
