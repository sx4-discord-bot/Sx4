package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.option.Option;
import com.sx4.bot.annotations.argument.*;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.StringUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import okhttp3.Request;
import org.bson.Document;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class RedditCommand extends Sx4Command {

	public RedditCommand() {
		super("reddit", 301);

		super.setDescription("Gets up to 100 posts from a subreddit with certain filters");
		super.setExamples("reddit doggos", "reddit doggos --sort=new --limit=100", "reddit doggos --sort=top --time=all");
		super.setCooldownDuration(10);
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="subreddit") String subreddit, @Option(value="sort", description="What to sort the subreddit by") @Options({"new", "hot", "top"}) @DefaultString("hot") @Lowercase String sort, @Option(value="time", description="If sort is top will choose the top posts after the set amount of time") @Options({"day", "week", "month", "year", "all"}) @DefaultString("day") @Lowercase String time, @Option(value="limit", description="How many posts to show") @DefaultNumber(25) @Limit(min=1, max=100) int limit) {
		Request request = new Request.Builder()
			.url("https://reddit.com/r/" + subreddit + "/" + sort + ".json?t=" + time + "&limit=" + limit)
			.build();

		event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
			if (response.code() == 302 || response.code() == 404) {
				event.replyFailure("I could not find that subreddit").queue();
				return;
			}

			List<Document> posts = Document.parse(response.body().string()).getEmbedded(List.of("data", "children"), Collections.emptyList());

			posts =	posts.stream()
				.filter(post -> {
					if (!event.getTextChannel().isNSFW()) {
						return !post.getEmbedded(List.of("data", "over_18"), false);
					}

					return true;
				})
				.map(post -> post.get("data", Document.class))
				.collect(Collectors.toList());

			if (posts.isEmpty()) {
				event.replyFailure("I could not find any posts with those filters").queue();
				return;
			}

			PagedResult<Document> paged = new PagedResult<>(event.getBot(), posts)
				.setPerPage(1)
				.setSelect()
				.setCustomFunction(page -> {
					MessageBuilder builder = new MessageBuilder();
					EmbedBuilder embed = new EmbedBuilder().setTitle("Page " + page.getPage() + "/" + page.getMaxPage());

					page.forEach((data, index) -> {
						String url = "https://reddit.com" + data.getString("permalink");

						embed.setFooter(data.getString("subreddit_name_prefixed") + " | Upvotes: " + data.getInteger("ups"));
						embed.setAuthor(StringUtility.limit(data.getString("title"), 50, "..."), url, "http://i.imgur.com/sdO8tAw.png");

						String postHint = data.getString("post_hint");
						if (postHint != null && postHint.equals("image")) {
							embed.setImage(data.getString("url"));
						} else if (postHint != null && postHint.equals("hosted:video")) {
							embed.setImage(data.getString("thumbnail"));
							builder.setContent("<" + data.getEmbedded(List.of("media", "reddit_video", "fallback_url"), String.class) + ">");
						} else if (postHint != null && postHint.equals("rich:video")) {
							embed.setImage(data.getEmbedded(List.of("media", "oembed", "thumbnail"), String.class));
							builder.setContent("<" + data.getEmbedded(List.of("secure_media_embed", "media_domain_url"), String.class) + ">");
						}

						if (data.containsKey("selftext")) {
							embed.setDescription(StringUtility.limit(data.getString("selftext"), MessageEmbed.DESCRIPTION_MAX_LENGTH, "... [Read more](" + url + ")"));
						}
					});

					return builder.setEmbeds(embed.build());
				});

			paged.execute(event);
		});
	}

}
