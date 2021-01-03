package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.option.Option;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.info.IGDBFilter;
import com.sx4.bot.entities.info.IGDBParser;
import com.sx4.bot.entities.info.IGDBSort;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.StringUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.bson.Document;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

public class IGDBCommand extends Sx4Command {

	public IGDBCommand() {
		super("igdb", 31);

		super.setDescription("Search up any game on IGDB");
		super.setExamples("igdb human fall", "igdb uno --sort=release --reverse", "igdb grand theft auto --sort=rating");
		super.setCooldownDuration(10);
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="game", endless=true, nullDefault=true) String game, @Option(value="sort", description="Sort results by `name` (default), `rating` and `release`") IGDBSort sort, @Option(value="reverse", description="Reverses sorting order") boolean reverse) {
		IGDBParser parser = new IGDBParser()
			.setFilter("category = 0")
			.limit(500)
			.addFields("name", "total_rating", "total_rating_count", "first_release_date", "genres.name", "url", "summary", "cover.image_id", "platforms.name");

		if (game != null && sort == null && !reverse) {
			parser.search(String.format("\"%s\"", game));
		} else {
			parser.sort(sort == null ? "name" : sort.getName(), !reverse)
				.appendFilter(filter -> IGDBFilter.and(filter, (sort == null ? "name" : sort.getName()) + " != n"));

			if (game != null) {
				parser.appendFilter(filter -> IGDBFilter.and(filter, String.format("name ~ \"%s\"*", game)));
			}
		}

		Request request = new Request.Builder()
			.url("https://api.igdb.com/v4/games/")
			.post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), parser.parse()))
			.addHeader("Client-ID", this.config.getTwitchClientId())
			.addHeader("Authorization", "Bearer " + this.config.getTwitch())
			.build();

		event.getClient().newCall(request).enqueue((HttpCallback) response -> {
			String body = String.format("{\"data\":%s}", response.body().string());

			List<Document> results = Document.parse(body).getList("data", Document.class);
			if (results.isEmpty()) {
				event.replyFailure("I could not find any games with that filter").queue();
				return;
			}

			PagedResult<Document> paged = new PagedResult<>(results)
				.setAutoSelect(true)
				.setIncreasedIndex(true)
				.setAuthor("IGDB Search", null, "http://bit.ly/2NXGwMz")
				.setDisplayFunction(data -> data.getString("name"));

			paged.onSelect(select -> {
				Document data = select.getSelected();

				EmbedBuilder embed = new EmbedBuilder();
				embed.setAuthor(data.getString("name"), data.getString("url"), "http://bit.ly/2NXGwMz");
				embed.setDescription(StringUtility.limit(data.get("summary", "This game has no description :("), MessageEmbed.TEXT_MAX_LENGTH, "... [Read More](" + data.getString("url") + ")"));
				embed.setThumbnail(data.containsKey("cover") ? String.format("https://images.igdb.com/igdb/image/upload/t_thumb/%s.jpg", data.getEmbedded(List.of("cover", "image_id"), String.class)) : null);

				int ratings = data.get("total_rating_count", 0);
				embed.addField("Rating", data.containsKey("total_rating") ? String.format("%.2f out of %,d rating%s", data.getDouble("total_rating"), ratings, ratings == 1 ? "" : "s") : "Unknown", true);
				embed.addField("Release Date", data.containsKey("first_release_date") ? Instant.ofEpochSecond(data.getInteger("first_release_date")).atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("dd LLLL yyyy")) : "Unknown", true);
				embed.addField("Genres", data.containsKey("genres") ? data.getList("genres", Document.class).stream().map(genre -> genre.getString("name")).collect(Collectors.joining("\n")) : "None", true);
				embed.addField("Platforms", data.containsKey("platforms") ? data.getList("platforms", Document.class).stream().map(platform -> platform.getString("name")).collect(Collectors.joining("\n")) : "None", true);

				event.reply(embed.build()).queue();
			});

			paged.execute(event);
		});
	}

}
