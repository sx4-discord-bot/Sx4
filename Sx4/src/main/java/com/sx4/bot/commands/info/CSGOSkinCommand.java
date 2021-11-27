package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.option.Option;
import com.sx4.bot.annotations.argument.Lowercase;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.utility.TimeFormatter;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.managers.SkinPortManager;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.TimeUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import okhttp3.FormBody;
import okhttp3.Request;
import org.bson.Document;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CSGOSkinCommand extends Sx4Command {

	private final Map<String, Integer> phases = new HashMap<>() {{
		this.put("1", 2);
		this.put("2", 3);
		this.put("3", 4);
		this.put("4", 5);
		this.put("ruby", 7);
		this.put("sapphire", 6);
		this.put("emerald", 9);
		this.put("black pearl", 8);
		this.put("black_pearl", 8);
	}};

	public enum Sort {

		AGE("date"),
		DISCOUNT("percent"),
		DEAL("sale"),
		WEAR("wear"),
		PRICE("price"),
		POPULARITY("position");

		private final String identifier;

		private Sort(String identifier) {
			this.identifier = identifier;
		}

		public String getIdentifier() {
			return this.identifier;
		}

	}

	public enum Wear {

		FN(2),
		MW(4),
		FT(3),
		WW(5),
		BS(1);

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

		super.setDescription("Get price information on a csgo skin from SkinPort");
		super.setAliases("cs skin", "skin", "csskin", "csgoskin");
		super.setExamples("csgo skin asiimov", "csgo skin Tiger Tooth Butterfly --sort=cheapest --wear=fn");
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="skin name", endless=true, nullDefault=true) String query, @Option(value="sort", description="You can sort by `price`, `age`, `deal`, `popularity`, `wear or `discount`") Sort sort, @Option(value="reverse", description="Reverse the order of the sorting") boolean reverse, @Option(value="wear", description="What wear you would like to filter by, options are `fn`, `mw`, `ft`, `ww` and `bs`") Wear wear, @Option(value="phase", description="Filter by phase of a knife") @Lowercase String phase) {
		SkinPortManager manager = event.getBot().getSkinPortManager();
		String cookie = manager.getCSRFCookie();

		FormBody body = new FormBody.Builder()
			.add("prefix", query)
			.add("_csrf", manager.getCSRFToken())
			.build();

		Request suggestionRequest = new Request.Builder()
			.url("https://skinport.com/api/suggestions/730")
			.post(body)
			.addHeader("Content-Type", "application/x-www-form-urlencoded")
			.addHeader("Cookie", cookie)
			.build();

		event.getHttpClient().newCall(suggestionRequest).enqueue((HttpCallback) suggestionResponse -> {
			Document data = Document.parse(suggestionResponse.body().string());

			List<Document> variants = data.getList("suggestions", Document.class);
			if (variants.isEmpty()) {
				event.replyFailure("I could not find any skins from that query").queue();
				return;
			}

			PagedResult<Document> suggestions = new PagedResult<>(event.getBot(), variants)
				.setAuthor("SkinPort", null, "https://skinport.com/static/favicon-32x32.png")
				.setDisplayFunction(suggestion -> {
					String type = suggestion.getString("type");
					return (type == null ? "" : type + " | ") + suggestion.getString("item");
				})
				.setIndexed(true)
				.setAutoSelect(true);

			suggestions.onSelect(select -> {
				Document selected = select.getSelected();

				String type = selected.getString("type");
				StringBuilder url = new StringBuilder("https://skinport.com/api/browse/730?cat=" + URLEncoder.encode(selected.getString("category"), StandardCharsets.UTF_8) + (type != null ? "&type=" + URLEncoder.encode(type, StandardCharsets.UTF_8) : "") + "&item=" + URLEncoder.encode(selected.getString("item"), StandardCharsets.UTF_8));

				if (wear != null) {
					url.append("&exterior=").append(wear.getId());
				}

				if (sort != null) {
					url.append("&sort=").append(sort.getIdentifier()).append("&order=").append(reverse ? "desc" : "asc");
				}

				if (phase != null) {
					int phaseId = this.phases.getOrDefault(phase, -1);
					if (phaseId != -1) {
						url.append("&phase=").append(phaseId);
					}
				}

				Request request = new Request.Builder()
					.url(url.toString())
					.addHeader("Cookie", cookie)
					.build();

				event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
					Document skinData = Document.parse(response.body().string());

					List<Document> items = skinData.getList("items", Document.class);
					if (items.isEmpty()) {
						event.replyFailure("There are no skins listed with those filters").queue();
						return;
					}

					PagedResult<Document> skins = new PagedResult<>(event.getBot(), items)
						.setPerPage(1)
						.setSelect()
						.setCustomFunction(page -> {
							List<MessageEmbed> embeds = new ArrayList<>();

							EmbedBuilder embed = new EmbedBuilder();
							embed.setFooter("Skin " + page.getPage() + "/" + page.getMaxPage());

							page.forEach((d, index) -> {
								double steamPrice = d.getInteger("suggestedPrice") / 100D;
								double price = d.getInteger("salePrice") / 100D;
								double increase = steamPrice - price;

								embed.setTitle(d.getString("marketName"), "https://skinport.com/item/" + d.getString("url") + "/" + d.getInteger("saleId"));
								embed.setImage("https://community.cloudflare.steamstatic.com/economy/image/" + d.getString("image"));
								embed.addField("Price", String.format("~~£%,.2f~~ £%,.2f (%s%.2f%%)", steamPrice, price, increase > 0 ? "-" : "+", Math.abs((increase / steamPrice) * 100D)), true);

								String exterior = d.getString("exterior");
								if (exterior != null) {
									embed.addField("Wear", exterior, true);
									embed.addField("Float", String.format("%.3f", d.get("wear", Number.class).doubleValue()), true);
								}

								String lock = d.getString("lock");
								if (lock == null) {
									embed.addField("Trade Locked", "No", true);
								} else {
									embed.addField("Trade Locked", this.formatter.parse(Duration.between(OffsetDateTime.now(ZoneOffset.UTC), OffsetDateTime.parse(lock))), true);
								}

								embeds.add(embed.build());

								if (d.getBoolean("canHaveScreenshots")) {
									embed.setImage("https://cdn.skinport.com/images/screenshots/" + d.getInteger("assetId") + "/backside_512x384.png");
									embeds.add(embed.build());

									embed.setImage("https://cdn.skinport.com/images/screenshots/" + d.getInteger("assetId") + "/playside_512x384.png");
									embeds.add(embed.build());
								}
							});

							return new MessageBuilder().setEmbeds(embeds);
						});

					skins.execute(event);
				});
			});

			suggestions.execute(event);
		});
	}

	public void skinBaron(Sx4CommandEvent event, @Argument(value="skin name", endless=true, nullDefault=true) String query, @Option(value="sort", description="You can sort by `expensive`, `cheapest`, `best_deal`, `newest`, `rarest`, `best_float` or `popularity`") Sort sort, @Option(value="wear", description="What wear you would like to filter by, options are `fn`, `mw`, `ft`, `ww` and `bs`") Wear wear) {
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

				StringBuilder url = new StringBuilder("https://skinbaron.de/api/v2/Browsing/FilterOffers?appId=730&language=en&otherCurrency=GBP&variantId=" + selected.getInteger("id") + "&sort=" + (sort == null ? Sort.DEAL : sort).getIdentifier());

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
