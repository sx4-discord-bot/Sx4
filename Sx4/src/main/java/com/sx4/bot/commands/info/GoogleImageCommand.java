package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.cache.GoogleSearchCache.GoogleSearchResult;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.ExceptionUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;

import javax.ws.rs.ForbiddenException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionException;

public class GoogleImageCommand extends Sx4Command {

	public GoogleImageCommand() {
		super("google image", 209);

		super.setDescription("Search up a query on google images");
		super.setAliases("googleimage");
		super.setExamples("google image dog", "google image cat");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="query", endless=true) String query) {
		boolean nsfw = event.getTextChannel().isNSFW();

		event.getBot().getGoogleCache().retrieveResultsByQuery(query, true, nsfw).whenComplete((results, exception) -> {
			Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
			if (cause instanceof ForbiddenException) {
				event.replyFailure(cause.getMessage()).queue();
				return;
			}

			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			String googleUrl = "https://google.com/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&tbm=isch" + (nsfw ? "" : "&safe=active");

			PagedResult<GoogleSearchResult> paged = new PagedResult<>(event.getBot(), results)
				.setIndexed(false)
				.setPerPage(1)
				.setAuthor("Google Images", googleUrl, "http://i.imgur.com/G46fm8J.png")
				.setCustomFunction(page -> {
					EmbedBuilder embed = new EmbedBuilder()
						.setAuthor("Google", "https://www.google.co.uk/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&tbm=isch", "http://i.imgur.com/G46fm8J.png")
						.setTitle("Image " + page.getPage() + "/" + page.getMaxPage())
						.setFooter(PagedResult.DEFAULT_FOOTER_TEXT);

					page.forEach((result, index) -> embed.setImage(result.getLink()));

					return new MessageBuilder().setEmbed(embed.build()).build();
				});

			paged.execute(event);
		});
	}

}
