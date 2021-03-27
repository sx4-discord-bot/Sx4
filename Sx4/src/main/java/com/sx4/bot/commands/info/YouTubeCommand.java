package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.option.Option;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.http.HttpCallback;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import okhttp3.Request;
import org.bson.Document;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class YouTubeCommand extends Sx4Command {

	private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd LLLL uuuu HH:mm");

	public YouTubeCommand() {
		super("youtube", 36);

		super.setDescription("Looks up a channel, video or playist on YouTube from a query");
		super.setExamples("youtube PewDiePie", "youtube YouTube Rewind", "youtube Music Playlist");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCooldownDuration(3);
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="query", endless=true) String query, @Option(value="channel", description="Only return a channel") boolean channel, @Option(value="playlist", description="Only return a playlist") boolean playlist, @Option(value="video", description="Only return a video") boolean video) {
		String type = channel ? "channel" : video ? "video" : playlist ? "playlist" : null;

		Request request = new Request.Builder()
			.url(String.format("https://www.googleapis.com/youtube/v3/search?key=%s&part=snippet&safeSearch=none&maxResults=1&q=%s%s", event.getConfig().getYoutube(), URLEncoder.encode(query, StandardCharsets.UTF_8), type == null ? "" : "&type=" + type))
			.build();

		event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
			Document json = Document.parse(response.body().string());

			List<Document> items = json.getList("items", Document.class);
			if (items.isEmpty()) {
				event.reply("I could not find any results :no_entry:").queue();
				return;
			}

			Document result = items.get(0);
			Document id = result.get("id", Document.class);
			Document snippet = result.get("snippet", Document.class);
			Document thumbnails = snippet.get("thumbnails", Document.class);

			ZonedDateTime uploadedAt = ZonedDateTime.parse(snippet.getString("publishedAt"));

			EmbedBuilder embed = new EmbedBuilder();
			embed.setColor(16711680);
			embed.setDescription(snippet.getString("description"));
			embed.setImage(thumbnails.getEmbedded(List.of("high", "url"), String.class));

			String kind = id.getString("kind");
			if (kind.equals("youtube#channel")) {
				embed.setTitle(snippet.getString("title"), "https://www.youtube.com/channel/" + id.getString("channelId"));
				embed.addField("Created At", uploadedAt.format(this.formatter), true);
			} else if (kind.equals("youtube#video")) {
				embed.setTitle(snippet.getString("title"), "https://www.youtube.com/watch?v=" + id.getString("videoId"));

				String state = snippet.getString("liveBroadcastContent");
				embed.addField("Uploaded By", "[" + snippet.getString("channelTitle") + "](https://www.youtube.com/channel/" + snippet.getString("channelId") + ")", true);
				embed.addField("Uploaded At", uploadedAt.format(this.formatter), true);
				embed.addField("State", state.equals("live") ? "Live Now" : state.equals("upcoming") ? "Scheduled" : "Uploaded", true);
			} else {
				embed.setTitle(snippet.getString("title"), "https://www.youtube.com/playlist?list=" + id.getString("playlistId"));
				embed.addField("Uploaded By", "[" + snippet.getString("channelTitle") + "](https://www.youtube.com/channel/" + snippet.getString("channelId") + ")", true);
				embed.addField("Uploaded At", uploadedAt.format(this.formatter), true);
			}

			event.reply(embed.build()).queue();
		});
	}

}
