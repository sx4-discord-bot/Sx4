package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.option.Option;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.NumberUtility;
import com.sx4.bot.utility.StringUtility;
import com.sx4.bot.utility.TimeUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import okhttp3.Request;
import org.bson.Document;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

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

	private String getUrlFromId(Document id) {
		String kind = id.getString("kind");
		if (kind.equals("youtube#channel")) {
			return "https://www.youtube.com/channel/" + id.getString("channelId");
		} else if (kind.equals("youtube#video")) {
			return "https://www.youtube.com/watch?v=" + id.getString("videoId");
		} else {
			return "https://www.youtube.com/playlist?list=" + id.getString("playlistId");
		}
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="query", endless=true) String query, @Option(value="channel", description="Only return a channel") boolean channel, @Option(value="playlist", description="Only return a playlist") boolean playlist, @Option(value="video", description="Only return a video") boolean video) {
		String type = channel ? "channel" : video ? "video" : playlist ? "playlist" : null;

		Request request = new Request.Builder()
			.url(String.format("https://www.googleapis.com/youtube/v3/search?key=%s&part=snippet&maxResults=50&safeSearch=none&q=%s%s", event.getConfig().getYoutube(), URLEncoder.encode(query, StandardCharsets.UTF_8), type == null ? "" : "&type=" + type))
			.build();

		event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
			Document json = Document.parse(response.body().string());

			List<Document> items = json.getList("items", Document.class);
			if (items.isEmpty()) {
				event.replyFailure("I could not find any results").queue();
				return;
			}

			PagedResult<Document> paged = new PagedResult<>(event.getBot(), items)
				.setAuthor("YouTube Results", null, "https://media-thumbs.golden.com/4hBhjfnhOC6J3uJZglZG0quRsPU=/200x200/smart/golden-media.s3.amazonaws.com%2Ftopic_images%2F6c3fdb0966b049eba2b9c2331da224f0.png")
				.setAutoSelect(true)
				.setDisplayFunction(data -> {
					Document id = data.get("id", Document.class);
					String kind = id.getString("kind");
					return "[" + data.getEmbedded(List.of("snippet", "title"), String.class) + "](" + this.getUrlFromId(id) + ")" + " (" + StringUtility.title(kind.substring(kind.indexOf('#') + 1)) + ")";
				});

			paged.onSelect(select -> {
				Document result = select.getSelected();
				Document id = result.get("id", Document.class);
				Document snippet = result.get("snippet", Document.class);
				Document thumbnails = snippet.get("thumbnails", Document.class);

				ZonedDateTime uploadedAt = ZonedDateTime.parse(snippet.getString("publishedAt"));

				EmbedBuilder embed = new EmbedBuilder();
				embed.setColor(16711680);
				embed.setDescription(snippet.getString("description"));
				embed.setImage(thumbnails.getEmbedded(List.of("high", "url"), String.class));
				embed.setTitle(snippet.getString("title"), this.getUrlFromId(id));

				String kind = id.getString("kind");
				if (kind.equals("youtube#channel")) {
					embed.addField("Created At", uploadedAt.format(this.formatter), true);
				} else if (kind.equals("youtube#video")) {
					String state = snippet.getString("liveBroadcastContent");
					embed.addField("Uploaded By", "[" + snippet.getString("channelTitle") + "](https://www.youtube.com/channel/" + snippet.getString("channelId") + ")", true);
					embed.addField("Uploaded At", uploadedAt.format(this.formatter), true);
					embed.addField("State", state.equals("live") ? "Live Now" : state.equals("upcoming") ? "Scheduled" : "Uploaded", true);

					Request metaDataRequest = new Request.Builder()
						.url("https://api.jockiemusic.com/v1/youtube/videos/" + id.getString("videoId") + "/metadata")
						.build();

					event.reply(embed.build()).queue(message -> {
						event.getHttpClient().newCall(metaDataRequest).enqueue((HttpCallback) metaDataResponse -> {
							if (metaDataResponse.code() == 404) {
								return;
							}

							Document data = Document.parse(metaDataResponse.body().string()).get("data", Document.class);
							Document metaData = data.get("metadata", Document.class);
							Document rating = metaData.get("rating", Document.class);

							long likes = rating.get("likes", Number.class).longValue(), dislikes = rating.get("dislikes", Number.class).longValue();
							double ratingPercent = ((double) likes / (likes + dislikes)) * 100D;

							embed.addField("Duration", TimeUtility.getMusicTimeString(metaData.get("length", Number.class).longValue(), TimeUnit.MILLISECONDS), true);
							embed.addField("Views", String.format("%,d", metaData.get("views", Number.class).longValue()), true);
							embed.addField("Likes/Dislikes", String.format("%,d \\\uD83D\uDC4D\n%,d \\\uD83D\uDC4E", likes, dislikes), true);
							embed.addField("Rating", NumberUtility.DEFAULT_DECIMAL_FORMAT.format(ratingPercent) + "%", true);
							embed.setFooter("Views and Ratings last updated");
							embed.setTimestamp(Instant.ofEpochSecond(data.get("lastUpdated", Number.class).longValue()));

							message.editMessageEmbeds(embed.build()).queue();
						});
					});

					return;
				} else {
					embed.addField("Uploaded By", "[" + snippet.getString("channelTitle") + "](https://www.youtube.com/channel/" + snippet.getString("channelId") + ")", true);
					embed.addField("Uploaded At", uploadedAt.format(this.formatter), true);
				}

				event.reply(embed.build()).queue();
			});

			paged.execute(event);
		});
	}

}
