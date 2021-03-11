package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.option.Option;
import com.sx4.bot.annotations.argument.DefaultNumber;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.TimeUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import okhttp3.Request;
import org.bson.Document;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.List;

public class GoogleCommand extends Sx4Command {

	private final DecimalFormat decimalFormat = new DecimalFormat("0.##");

	public GoogleCommand() {
		super("google", 208);

		super.setDescription("Searches a query up on google");
		super.setExamples("google How to use Sx4", "google Sx4 discord bot");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="query", endless=true) String query, @Option(value="page", description="Sets the page to search on") @DefaultNumber(1) int page) {
		boolean nsfw = event.getTextChannel().isNSFW();

		Request request = new Request.Builder()
			.url(event.getConfig().getSearchWebserverUrl("google") + "?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&nsfw=" + nsfw + "&page=" + page + "&types=0,2,3,4,5,6,7,8,9,10")
			.build();

		event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
			Document document = Document.parse(response.body().string());
			if (!response.isSuccessful()) {
				StringBuilder builder = new StringBuilder("Command failed with status " + response.code());
				if (document.containsKey("message")) {
					builder.append(" with message `").append(document.getString("message")).append("`");
				}

				event.replyFailure(builder.toString()).queue();
				return;
			}

			List<Document> results = document.getList("results", Document.class);
			if (results.isEmpty()) {
				event.replyFailure("I could not find any results").queue();
				return;
			}

			String url = document.getString("url").replace("*", "%2A");
			String googleUrl = "https://google.com/";

			PagedResult<Document> paged = new PagedResult<>(event.getBot(), results)
				.setIndexed(false)
				.setPerPage(3)
				.setAuthor("Google", url, "http://i.imgur.com/G46fm8J.png")
				.setDisplayFunction(data -> {
					int type = data.getInteger("type");
					if (type == 0) {
						return "**[" + data.getString("title") + "](" + data.getString("url") + ")**\n" + (data.containsKey("answer") ? "**" + data.getString("answer") + "**\n" : "") + MarkdownSanitizer.escape(data.getString("description")) + "\n";
					} else if (type == 4) {
						Document input = data.get("input", Document.class);
						Document output = data.get("output", Document.class);

						String inputValue = this.decimalFormat.format(input.get("value", Number.class).doubleValue());
						String outputValue = this.decimalFormat.format(output.get("value", Number.class).doubleValue());

						return String.format("**[Conversion](%s)**\n**%s** %s \\➡ **%s** %s\n", googleUrl, inputValue, input.getString("unit"), outputValue, output.getString("unit"));
					} else if (type == 5) {
						return "**[Date Time](" + googleUrl + ")**\n" + (data.containsKey("time") ? "**" + data.getString("time") + "**\n" : "") + data.getString("date") + "\n";
					} else if (type == 6) {
						return "**[Calculator](" + googleUrl + ")**\nThe answer is: **" + this.decimalFormat.format(data.get("answer", Number.class).doubleValue()) + "**\n";
					} else if (type == 8) {
						return "**[Random Number between " + data.getInteger("min") + " and " + data.getInteger("max") + "](" + googleUrl + ")**\n**" + data.getInteger("value") + "**\n";
					} else if (type == 3) {
						return "**[Answer](" + googleUrl + ")**\n**" + data.getString("answer") + "**\n";
					} else if (type == 2) {
						Document definition = data.getList("definitions", Document.class).get(0);

						return "**[Definition of " + data.getString("word") + " (" + definition.getString("type") + ")](" + data.getString("url") + ")**\n**" + definition.getString("definition") + "**\n";
					} else if (type == 7) {
						Document output = data.get("output", Document.class);

						return "**[Translation to " + output.getEmbedded(List.of("language", "name"), String.class) + "](" + googleUrl + ")**\n**" + output.getString("text") + "**\n";
					} else if (type == 9) {
						Document route = data.getList("routes", Document.class).get(0);

						String directions;
						if (data.getString("method").equals("Public transport")) {
							directions = "Leave " + route.getString("station") + " at " + route.getEmbedded(List.of("depart", "station"), String.class);
						} else {
							Document distance = route.get("distance", Document.class);
							directions = distance.get("value", Number.class).doubleValue() + " " + distance.getString("unit") + " via " + route.getString("via") + " (" + TimeUtility.getTimeString(route.getInteger("duration")) + ")";
						}

						return "**[" + data.getString("method") + " from " + data.getString("from") + " to " + data.getString("to") + "](" + data.getString("url") + ")**\n**" + directions + "**\n";
					} else if (type == 10) {
						Document flight = data.getList("flights", Document.class).get(0);

						String info = flight.getString("type") + " flight with " + flight.getString("airline") + " for " + flight.getEmbedded(List.of("price", "formatted"), String.class) + " (" + TimeUtility.getTimeString(flight.getInteger("duration")) + ")";
						return "**[Flight from " + data.getString("departing") + " to " + data.getString("destination") + "](" + flight.getString("url") + ")**\n**" + info + "**\n";
					}

					return "";
				});

			paged.execute(event);
		});
	}

}
