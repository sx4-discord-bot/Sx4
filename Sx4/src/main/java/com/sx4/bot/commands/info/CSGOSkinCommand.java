package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.option.Option;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.utility.TimeFormatter;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.TimeUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import okhttp3.Request;
import org.bson.Document;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

public class CSGOSkinCommand extends Sx4Command {

	public enum Sort {

		CHEAPEST("CF"),
		EXPENSIVE("EF"),
		BEST_DEAL("BP"),
		NEWEST("NF"),
		RAREST("BQ"),
		BEST_FLOAT("BE"),
		POPULARITY("MS");

		private final String identifier;

		private Sort(String identifier) {
			this.identifier = identifier;
		}

		public String getIdentifier() {
			return this.identifier;
		}

	}

	public enum Wear {

		FN(0),
		MW(1),
		FT(2),
		WW(3),
		BS(4);

		private final int id;

		private Wear(int id) {
			this.id = id;
		}

		public int getId() {
			return this.id;
		}

	}

	private final TimeFormatter formatter = TimeUtility.LONG_TIME_FORMATTER_BUILDER.setMaxUnits(2).build();

	public CSGOSkinCommand() {
		super("csgo skin", 470);

		super.setDescription("Get price information on a csgo skin from SkinBaron");
		super.setAliases("cs skin", "skin", "csskin", "csgoskin");
		super.setExamples("csgo skin asiimov", "csgo skin Tiger Tooth Butterfly --sort=cheapest --wear=fn");
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="skin name", endless=true, nullDefault=true) String query, @Option(value="sort", description="You can sort by `expensive`, `cheapest`, `best_deal`, `newest`, `rarest`, `best_float` or `popularity`") Sort sort, @Option(value="wear", description="What wear you would like to filter by, options are `fn`, `mw`, `ft`, `ww` and `bs`") Wear wear) {
		Request suggestionRequest = new Request.Builder()
			.url("https://skinbaron.de/api/v2/Browsing/QuickSearch?variantName=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&appId=730&language=en")
			.build();

		event.getHttpClient().newCall(suggestionRequest).enqueue((HttpCallback) suggestionResponse -> {
			Document data = Document.parse(suggestionResponse.body().string());

			List<Document> variants = data.getList("variants", Document.class);
			if (variants.isEmpty()) {
				event.replyFailure("I could not find any skins from that query").queue();
				return;
			}

			PagedResult<Document> suggestions = new PagedResult<>(event.getBot(), variants)
				.setAuthor("SkinBaron", null, "https://skinbaron.de/favicon.png")
				.setDisplayFunction(suggestion -> suggestion.getString("variantName"))
				.setIndexed(true)
				.setAutoSelect(true);

			suggestions.onSelect(select -> {
				Document selected = select.getSelected();

				StringBuilder url = new StringBuilder("https://skinbaron.de/api/v2/Browsing/FilterOffers?appId=730&language=en&otherCurrency=GBP&variantId=" + selected.getInteger("id") + "&sort=" + (sort == null ? Sort.BEST_DEAL : sort).getIdentifier());

				if (wear != null) {
					url.append("&wf=").append(wear.getId());
				}

				Request request = new Request.Builder()
					.url(url.toString())
					.build();

				event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
					Document skinData = Document.parse(response.body().string());

					List<Document> offers = skinData.getList("aggregatedMetaOffers", Document.class);
					if (offers.isEmpty()) {
						event.replyFailure("There are no skins listed with those filters").queue();
						return;
					}

					PagedResult<Document> skins = new PagedResult<>(event.getBot(), offers)
						.setPerPage(1)
						.setSelect()
						.setCustomFunction(page -> {
							EmbedBuilder embed = new EmbedBuilder();
							embed.setFooter("Skin " + page.getPage() + "/" + page.getMaxPage());

							page.forEach((d, index) -> {
								Document skin = d.containsKey("singleOffer") ? d.get("singleOffer", Document.class) : d.get("variant", Document.class);

								Number steamPrice = d.get("steamMarketPrice",Number.class);

								String priceString;
								if (skin.containsKey("itemPrice")) {
									double price = skin.get("itemPrice", Number.class).doubleValue();
									if (steamPrice == null) {
										priceString = String.format("£%,.2f", price);
									} else {
										double increase = price - steamPrice.doubleValue();

										priceString = String.format("~~£%,.2f~~ £%,.2f (%.2f%%)", steamPrice.doubleValue(), price, (increase / (increase > 0 ? steamPrice.doubleValue() : price)) * 100D);
									}
								} else {
									priceString = String.format("£%,.2f", steamPrice.doubleValue());
								}

								int tradeLockHours = skin.getInteger("tradeLockHoursLeft", 0);

								embed.setTitle(skin.getString("localizedName") + (skin.containsKey("statTrakString") ? " (StatTrak)" : ""), "https://skinbaron.de" + d.getString("offerLink"));
								embed.setImage(skin.getString("imageUrl"));
								embed.addField("Price", priceString, true);

								String wearName = skin.getString("localizedExteriorName");
								if (wearName != null) {
									embed.addField("Wear", wearName, true);
									embed.addField("Float", String.format("%.4f", skin.get("wearPercent", Number.class).doubleValue() / 100D), true);
								}

								embed.addField("Trade Locked", tradeLockHours == 0 ? "No" : "Yes (" + this.formatter.parse(Duration.ofHours(tradeLockHours)) + ")", true);
							});

							return new MessageBuilder().setEmbeds(embed.build());
						});

					skins.execute(event);
				});
			});

			suggestions.execute(event);
		});
	}

}
