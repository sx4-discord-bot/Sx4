package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.cache.GoogleSearchCache.GoogleSearchResult;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.ExceptionUtility;
import jakarta.ws.rs.ForbiddenException;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.attribute.IAgeRestrictedChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
		MessageChannel channel = event.getChannel();
		boolean nsfw = channel instanceof IAgeRestrictedChannel && ((IAgeRestrictedChannel) channel).isNSFW();

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
				.setPerPage(4)
				.setCustomFunction(page -> {
					List<MessageEmbed> embeds = new ArrayList<>();
					page.forEach((result, index) -> {
						EmbedBuilder embed = new EmbedBuilder()
							.setTitle("Page " + page.getPage() + "/" + page.getMaxPage(), googleUrl)
							.setAuthor("Google", googleUrl, "http://i.imgur.com/G46fm8J.png")
							.setFooter(PagedResult.DEFAULT_FOOTER_TEXT)
							.setImage(result.getLink());

						embeds.add(embed.build());
					});

					return new MessageCreateBuilder().setEmbeds(embeds);
				});

			paged.execute(event);
		});
	}

}
