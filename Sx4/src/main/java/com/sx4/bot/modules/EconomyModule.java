package com.sx4.bot.modules;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.command.Command.Async;
import com.jockie.bot.core.command.Command.BotPermissions;
import com.jockie.bot.core.command.Command.Cooldown;
import com.jockie.bot.core.command.Context;
import com.jockie.bot.core.command.ICommand.ContentOverflowPolicy;
import com.jockie.bot.core.command.Initialize;
import com.jockie.bot.core.command.impl.CommandEvent;
import com.jockie.bot.core.command.impl.CommandImpl;
import com.jockie.bot.core.module.Module;
import com.jockie.bot.core.option.Option;
import com.mongodb.client.FindIterable;
import com.mongodb.client.model.*;
import com.sx4.bot.categories.Categories;
import com.sx4.bot.core.Sx4Bot;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEventListener;
import com.sx4.bot.database.Database;
import com.sx4.bot.economy.AuctionItem;
import com.sx4.bot.economy.Item;
import com.sx4.bot.economy.ItemStack;
import com.sx4.bot.economy.items.*;
import com.sx4.bot.economy.materials.Material;
import com.sx4.bot.economy.materials.Wood;
import com.sx4.bot.economy.tools.Axe;
import com.sx4.bot.economy.tools.Pickaxe;
import com.sx4.bot.economy.tools.Rod;
import com.sx4.bot.economy.tools.Tool;
import com.sx4.bot.economy.upgrades.AxeUpgrade;
import com.sx4.bot.economy.upgrades.PickaxeUpgrade;
import com.sx4.bot.economy.upgrades.RodUpgrade;
import com.sx4.bot.interfaces.Canary;
import com.sx4.bot.interfaces.Examples;
import com.sx4.bot.interfaces.Sx4Callback;
import com.sx4.bot.settings.Settings;
import com.sx4.bot.utils.*;
import com.sx4.bot.utils.EconomyUtils.Slot;
import com.sx4.bot.utils.PagedUtils.PagedResult;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.utils.cache.SnowflakeCacheView;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import okhttp3.Request;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigInteger;
import java.time.*;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

@Module
public class EconomyModule {
	
	private final Random random = new Random();
	
	@Command(value="advent calendar", aliases={"adventcalendar", "advent", "calendar"}, description="Open your advent calendar to get a random item every day up to the 24th")
	@Examples({"advent calendar"})
	public void adventCalander(CommandEvent event, @Context Database database) {
		ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
		int day = now.getDayOfMonth();
		if (now.getMonthValue() != 12 || day > 24) {
			event.replyFormat("There's no advent calander box for the %s %s :no_entry:", GeneralUtils.getNumberSuffix(day), now.getMonth().getDisplayName(TextStyle.FULL, Locale.UK)).queue();
			return;
		}
		
		Document data = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.opened", "economy.items")).get("economy", Database.EMPTY_DOCUMENT);
		List<Document> items = data.getList("items", Document.class, new ArrayList<>());
		
		List<Integer> opened = data.getList("opened", Integer.class, Collections.emptyList());
		if (opened.contains(day)) {
			long secondsTillTomorrow = now.toLocalDate().atStartOfDay(ZoneOffset.UTC).plusDays(1).toEpochSecond() - now.toEpochSecond();
			event.replyFormat("You've already opened todays box on your advent calendar%s :no_entry:", day != 24 ? ", you can open tomorrows in **" + TimeUtils.toTimeString(secondsTillTomorrow, ChronoUnit.SECONDS) + "**" : "").queue();
		} else {
			List<Item> winnableItems = new ArrayList<>(EconomyUtils.WINNABLE_ITEMS);
			winnableItems.sort((a, b) -> Long.compare(b.getPrice(), a.getPrice()));
			for (int i = 0; i < winnableItems.size(); i++) {
				Item item = winnableItems.get(i);
				
				int equation = (int) Math.ceil(item.getPrice() / Math.pow(day * 3, 2));
				if (random.nextInt(equation + 1) == 0 || i == winnableItems.size() - 1) {
					if (opened.size() == 23) {
						EconomyUtils.addItem(items, Crate.PRESENT, 1);
					}
					
					EconomyUtils.addItem(items, item, 1);
					
					database.updateUserById(event.getAuthor().getIdLong(), Updates.combine(Updates.addToSet("economy.opened", day), Updates.set("economy.items", items)), (result, exception) -> {
						if (exception != null) {
							exception.printStackTrace();
							event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
						} else {
							event.replyFormat("You opened your advent calendar for the %s and got **%s**%s :christmas_tree:", GeneralUtils.getNumberSuffix(day), item.getName(), opened.size() == 23 ? " and a **Present Crate**" : "").queue();
						}
					});
					
					return;
				}
			}
		}
	}
	
	@Command(value="claim", description="Claim your free 1 billion dollars to play with (Only works on Canary version)")
	@Examples({"claim"})
	@Canary
	public void claim(CommandEvent event, @Context Database database) {
		boolean claimed = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.claimed")).getEmbedded(List.of("economy", "claimed"), false);
		if (claimed) {
			event.reply("You have already claimed your free money :no_entry:").queue();
		} else {
			database.updateUserById(event.getAuthor().getIdLong(), Updates.combine(Updates.inc("economy.balance", 1_000_000_000L), Updates.set("economy.claimed", true)), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.replyFormat("You have been given your free **$%,d** :tada:", 1_000_000_000).queue();
				}
			});
		}
	}

	public class CrateCommand extends Sx4Command {

		private final Set<Long> pendingCrates = new HashSet<>();
		
		public CrateCommand() {
			super("crate");
			
			super.setAliases("crates");
			super.setDescription("Open crates to get random items in the economy");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
			super.setExamples("crate open", "crate buy", "crate shop");
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="shop", aliases={"list"}, description="View all the crates you can buy", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"crate shop"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void shop(CommandEvent event, @Context Database database) {
			long balance = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.balance")).getEmbedded(List.of("economy", "balance"), 0L);
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setColor(Settings.EMBED_COLOUR);
			embed.setAuthor("Crate Shop", null, event.getSelfUser().getEffectiveAvatarUrl());
			embed.setDescription("Crates give you a random item, the better the crate the better the chance of a better item");
			embed.setFooter(String.format("Use %scrate buy <crate> to buy a crate | Balance: $%,d", event.getPrefix(), balance), event.getAuthor().getEffectiveAvatarUrl());
			
			for (Crate crate : Crate.ALL) {
				if (crate.isBuyable()) {
					embed.addField(crate.getName(), String.format("Price: $%,d", crate.getPrice()), true);
				}
			}
			
			event.reply(embed.build()).queue();
		}
		
		@Command(value="buy", description="Buy a crate displayed in the crate shop")
		@Examples({"crate buy Shoe Crate 50", "crate buy Platinum Crate", "crate buy Gold 2"})
		public void buy(CommandEvent event, @Context Database database, @Argument(value="crate name", endless=true) String crateArgument) {
			Document data = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.balance", "economy.items")).get("economy", Database.EMPTY_DOCUMENT);
			List<Document> items = data.getList("items", Document.class, new ArrayList<>());
			long balance = data.get("balance", 0L);
			
			Pair<String, BigInteger> cratePair = EconomyUtils.getItemAndAmount(crateArgument);
			String crateName = cratePair.getLeft();
			BigInteger crateAmount = cratePair.getRight();
			
			if (crateAmount.compareTo(BigInteger.ONE) == -1) {
				event.reply("You have to buy at least one crate :no_entry:").queue();
				return;
			}
			
			Crate crate = Crate.getCrateByName(crateName);
			if (crate == null) {
				event.reply("I could not find that crate :no_entry:").queue();
				return;
			}
			
			if (!crate.isBuyable()) {
				event.reply("That crate is not buyable :no_entry:").queue();
				return;
			}
			
			if (this.pendingCrates.contains(event.getAuthor().getIdLong())) {
				event.reply("You cannot buy crates while opening crates :no_entry:").queue();
				return;
			}
			
			BigInteger price = BigInteger.valueOf(crate.getPrice()).multiply(crateAmount);
			if (BigInteger.valueOf(balance).compareTo(price) != -1) {
				EconomyUtils.addItem(items, crate, crateAmount.longValue());
				
				Bson update = Updates.combine(
					Updates.set("economy.items", items),
					Updates.inc("economy.balance", -price.longValue())
				);
				
				database.updateUserById(event.getAuthor().getIdLong(), update, (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						event.reply(String.format("You just bought `%,d %s` for **$%,d** :ok_hand:", crateAmount, crate.getName(), price)).queue();
					}
				});
			} else {
				event.reply(String.format("You do not have enough money to purchase `%,d %s` :no_entry:", crateAmount, crate.getName())).queue();
			}
		}
		
		@Command(value="open", description="Open a crate you have in your items")
		@Examples({"crate open Shoe Crate 50", "crate open Platinum Crate", "crate open all"})
		@Async
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void open(CommandEvent event, @Context Database database, @Argument(value="crate name", endless=true) String crateArgument) {
			List<Document> items = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.items")).getEmbedded(List.of("economy", "items"), new ArrayList<>());

			long totalCrates = 0;
			List<ItemStack<Crate>> crates = new ArrayList<>();
			if (crateArgument.toLowerCase().equals("all")) {
				for (Document itemData : items) {
					Item item = Item.getItemByName(itemData.getString("name"));
					if (item instanceof Crate) {
						Crate crate = (Crate) item;
						if (crate.isOpenable()) {
							long amount = itemData.getLong("amount");
							
							crates.add(new ItemStack<>(crate, amount));
							totalCrates += amount;
						}
					}
				}
				
				if (crates.isEmpty()) {
					event.reply("You do not have any crates :no_entry:").queue();
					return;
				}
			} else {
				Pair<String, BigInteger> cratePair = EconomyUtils.getItemAndAmount(crateArgument);
				String crateName = cratePair.getLeft();
				BigInteger crateAmount = cratePair.getRight();
				
				if (crateAmount.compareTo(BigInteger.ONE) == -1) {
					event.reply("You have to open at least 1 crate :no_entry:").queue();
					return;
				}
				
				Crate crate = Crate.getCrateByName(crateName);
				if (crate == null) {
					event.reply("I could not find that crate :no_entry:").queue();
					return;
				}
				
				if (!crate.isOpenable()) {
					event.reply("That crate is currently not openable :no_entry:").queue();
					return;
				}
				
				ItemStack<Item> userItem = EconomyUtils.getUserItem(items, crate);
				if (BigInteger.valueOf(userItem.getAmount()).compareTo(crateAmount) == -1) {
					event.reply(String.format("You do not have `%,d %s` :no_entry:", crateAmount, crate.getName())).queue();
					return;
				}
				
				crates.add(new ItemStack<>(crate, crateAmount.longValue()));
				totalCrates = crateAmount.longValue();
			}

			if (totalCrates > 1_000_000L) {
				event.reply("You can only open **1,000,000** crates at once :no_entry:").queue();
				return;
			}
			
			if (this.pendingCrates.contains(event.getAuthor().getIdLong())) {
				event.reply("You are already opening some crates, wait for them to open before opening more :no_entry:").queue();
				return;
			}
			
			this.pendingCrates.add(event.getAuthor().getIdLong());
			
			Map<Item, Long> finalItems = new HashMap<>();
			for (ItemStack<Crate> crateStack : crates) {
				Crate crate = crateStack.getItem();
				
				List<Item> winnableItems = new ArrayList<>(EconomyUtils.WINNABLE_ITEMS);
				winnableItems.remove(crate);
				winnableItems.sort(Comparator.comparing(Item::getPrice).reversed());
				
				for (int i = 0; i < crateStack.getAmount(); i++) { 
					for (Item item : winnableItems) {
						int equation = (int) Math.ceil((double) (38 * item.getPrice()) / Math.pow(crate.getChance(), 1.4));
						if (random.nextInt(equation + 1) == 0) {
							finalItems.compute(item, (key, value) -> value != null ? value + 1L : 1L);
							break;
						}
					}
				}
			}

			List<Document> itemsAfter = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.items")).getEmbedded(List.of("economy", "items"), new ArrayList<>());
			for (ItemStack<Crate> crate : crates) {
				ItemStack<Item> userCrate = EconomyUtils.getUserItem(itemsAfter, crate.getItem());
				if (userCrate.getAmount() < crate.getAmount()) {
					event.reply("You no longer have the sufficient amount of crates to perform this command :no_entry:").queue();
					this.pendingCrates.remove(event.getAuthor().getIdLong());
					return;
				}
			}
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setAuthor(event.getAuthor().getName(), null, event.getAuthor().getEffectiveAvatarUrl());
			embed.setColor(event.getMember().getColor());
			if (finalItems.isEmpty()) {
				if (crates.size() == 1) {
					ItemStack<Crate> crateStack = crates.get(0);
					embed.setDescription(String.format("You opened `%,d %s` and got scammed, there was nothing in the crate.", crateStack.getAmount(), crateStack.getItem().getName()));
				} else {
					embed.setDescription(String.format("You opened all your crates (%,d) and got scammed, there was nothing in the crate.", totalCrates));
				}
			} else {
				StringBuilder content = new StringBuilder();
				
				List<Entry<Item, Long>> newItems = new ArrayList<>(finalItems.entrySet());
				newItems.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
				
				long total = 0;
				for (Entry<Item, Long> item : newItems) {
					content.append(String.format("• %,d %s\n", item.getValue(), item.getKey().getName()));
					
					total += item.getValue();
					EconomyUtils.addItem(itemsAfter, item.getKey(), item.getValue());
				}
				
				if (crates.size() == 1) {
					ItemStack<Crate> crateStack = crates.get(0);
					content.insert(0, String.format("You opened `%,d %s` and got **%,d** item%s\n\n", crateStack.getAmount(), crateStack.getItem().getName(), total, total == 1 ? "" : "s"));
				} else {
					content.insert(0, String.format("You opened all your crates (%,d) and got **%,d** item%s\n\n", totalCrates, total, total == 1 ? "" : "s"));
				}
				
				embed.setDescription(content.toString());
			}

			EconomyUtils.removeItems(itemsAfter, crates);
			
			database.updateUserById(event.getAuthor().getIdLong(), Updates.set("economy.items", itemsAfter), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply(embed.build()).queue();
					
					this.pendingCrates.remove(event.getAuthor().getIdLong());
				}
			});
		}
		
	}
	
	public class EnvelopeCommand extends Sx4Command {
		
		public EnvelopeCommand() {
			super("envelope");
			
			super.setDescription("Create and redeem envelopes");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
			super.setExamples("envelope redeem", "envelope create");
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="create", description="Creates envelopes from a specified amount of money", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"envelope create 55000", "envelope create all", "envelopes create 25%"})
		public void create(CommandEvent event, @Context Database database, @Argument(value="amount") String moneyArgument) {
			Document data = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.balance", "economy.items")).get("economy", Database.EMPTY_DOCUMENT);
			List<Document> items = data.getList("items", Document.class, new ArrayList<>());
			long balance = data.get("balance", 0L);
			
			long amount;
			try {
				amount = EconomyUtils.convertMoneyArgument(balance, moneyArgument);
			} catch(IllegalArgumentException e) {
				event.reply(e.getMessage()).queue();
				return;
			}
			
			if (amount > balance) {
				event.replyFormat("You do not have **$%,d** :no_entry:", amount).queue();
				return;
			}
			
			EconomyUtils.addItems(items, Envelope.getOptimalEnvelopes(amount));
			database.updateUserById(event.getAuthor().getIdLong(), Updates.combine(Updates.inc("economy.balance", -amount), Updates.set("economy.items", items)), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.replyFormat("You have been given **$%,d** worth of envelopes :ok_hand:", amount).queue();
				}
			});
		}
		
		@Command(value="redeem", description="Trade in envelopes for actual money")
		@Examples({"envelope redeem all", "envelope redeem Coal Envelope 5", "envelope redeem Diamond"})
		public void redeem(CommandEvent event, @Context Database database, @Argument(value="envelope", endless=true) String envelopeArgument) {
			List<Document> items = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.items")).getEmbedded(List.of("economy", "items"), Collections.emptyList());
			if (items.isEmpty()) {
				event.reply("You do not have any envelopes :no_entry:").queue();
				return;
			}
			
			Bson update;
			String reply;
			if (envelopeArgument.toLowerCase().equals("all")) {
				long amount = 0;
				for (Document itemData : new ArrayList<>(items)) {
					Item item = Item.getItemByName(itemData.getString("name"));
					if (item instanceof Envelope) {
						long itemAmount = itemData.getLong("amount");

						EconomyUtils.removeItem(items, item, itemAmount);
						amount += item.getPrice() * itemAmount;
					}
				}
				
				if (amount == 0) {
					event.reply("You do not have any envelopes :no_entry:").queue();
					return;
				}
				
				update = Updates.combine(Updates.inc("economy.balance", amount), Updates.set("economy.items", items));
				reply = String.format("You redeemed all your envelopes for **$%,d** :ok_hand:", amount);
			} else {
				Pair<String, BigInteger> itemAndAmount = EconomyUtils.getItemAndAmount(envelopeArgument);
				String itemName = itemAndAmount.getLeft();
				BigInteger amount = itemAndAmount.getRight();
				
				Envelope envelope = Envelope.getEnvelopeByName(itemName);
				if (envelope == null) {
					event.reply("I could not find that envelope :no_entry:").queue();
					return;
				}
	
				ItemStack<Item> userEnvelope = EconomyUtils.getUserItem(items, envelope);
				if (BigInteger.valueOf(userEnvelope.getAmount()).compareTo(amount) == -1) {
					event.replyFormat("You do not have `%,d %s` :no_entry:", amount, envelope.getName()).queue();
					return;
				} else {
					EconomyUtils.removeItem(items, envelope, amount.longValue());
					
					update = Updates.combine(Updates.inc("economy.balance", envelope.getPrice() * amount.longValue()), Updates.set("economy.items", items));
					reply = String.format("You redeemed `%,d %s` for **$%,d** :ok_hand:", amount, envelope.getName(), envelope.getPrice() * amount.longValue());
				}
			}
			
			
			database.updateUserById(event.getAuthor().getIdLong(), update, (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply(reply).queue();
				}
			});
		}
		
	}
	
	@Command(value="tax", description="View the amount of tax the bot currently has (This is given away every friday in the support server)", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"tax"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void tax(CommandEvent event, @Context Database database) {
		long tax = database.getUserById(event.getSelfUser().getIdLong(), null, Projections.include("economy.balance")).getEmbedded(List.of("economy", "balance"), 0L);
		EmbedBuilder embed = new EmbedBuilder();
		embed.setAuthor(event.getSelfUser().getAsTag(), null, event.getSelfUser().getEffectiveAvatarUrl());
		embed.setDescription(String.format("Their balance: **$%,d**", tax));
		embed.setColor(Settings.EMBED_COLOUR);
		
		event.reply(embed.build()).queue();
	}
	
	@Command(value="trade", description="Trade items and money with another user")
	@Examples({"trade @Shea#6653", "trade 402557516728369153", "trade Shea"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	@Cooldown(value=10)
	public void trade(CommandEvent event, @Context Database database, @Argument(value="user", endless=true) String userArgument) {
		Member member = ArgumentUtils.getMember(event.getGuild(), userArgument);
		if (member == null) {
			event.reply("I could not find that user :no_entry:").queue();
			return;
		}
		
		if (member.equals(event.getMember())) {
			event.reply("You cannot trade with yourself :no_entry:").queue();
			return;
		}
		
		if (member.getUser().isBot()) {
			event.reply("You cannot trade with bots :no_entry:").queue();
			return;
		}
		
		Bson projection = Projections.include("economy.balance", "economy.items");
		Document authorData = database.getUserById(event.getAuthor().getIdLong(), null, projection).get("economy", Database.EMPTY_DOCUMENT);
		Document userData = database.getUserById(member.getIdLong(), null, projection).get("economy", Database.EMPTY_DOCUMENT);
		
		if (authorData.isEmpty()) {
			event.reply("You do not have have anything to trade :no_entry:").queue();
			return;
		}
		
		if (userData.isEmpty()) {
			event.reply("**" + member.getUser().getAsTag() + "** does not have anything to trade :no_entry:").queue();
			return;
		}
		
		String authorPrompt = "What are you offering to the user? Make sure you put a space between every thing you want to offer, for example: "
				+ "`2 gold, 200, 5 titanium, 1 coal factory` would offer $200, 5 Titanium, 2 Gold and 1 Coal Factory (Respond Below)";
		String userPrompt = "What would you like from the user? Make sure you put a comma between every thing you want to offer, for example: "
				+ "`2 gold, 200, 5 titanium, 1 coal factory` would offer $200, 5 Titanium, 2 Gold and 1 Coal Factory (Respond Below)";
		
		event.reply(authorPrompt).queue($ -> {
			PagedUtils.getResponse(event, 60, e -> e.getAuthor().equals(event.getAuthor()) && e.getChannel().equals(event.getChannel()), () -> event.reply("Response timed out :stopwatch:").queue(), authorTradeMessage -> {
				if (authorTradeMessage.getContentRaw().toLowerCase().equals("cancel")) {
					event.reply("Cancelled <:done:403285928233402378>").queue();
					return;
				}
				
				Pair<Long, List<ItemStack<Item>>> authorTrade;
				try {
					authorTrade = EconomyUtils.getTrade(authorTradeMessage.getContentRaw());
				} catch(IllegalArgumentException e) {
					event.reply(e.getMessage()).queue();
					return;
				}
				
				long authorMoney = authorTrade.getLeft();
				List<ItemStack<Item>> authorItems = authorTrade.getRight();
				
				StringBuilder authorItemsContent = new StringBuilder();
				for (ItemStack<Item> itemStack : authorItems) {
					authorItemsContent.append(String.format("%s x%,d\n", itemStack.getItem().getName(), itemStack.getAmount()));
				}
				
				EmbedBuilder embedAuthor = new EmbedBuilder();
				embedAuthor.setTitle("What you are offering to " + member.getUser().getAsTag());
				embedAuthor.setDescription((authorMoney != 0 ? String.format("$%,d\n", authorMoney) : "") + authorItemsContent);
				
				event.reply(new MessageBuilder().setContent(userPrompt).setEmbed(embedAuthor.build()).build()).queue(£ -> {
					PagedUtils.getResponse(event, 60, e -> e.getAuthor().equals(event.getAuthor()) && e.getChannel().equals(event.getChannel()), () -> event.reply("Response timed out :stopwatch:").queue(), userTradeMessage -> {
						if (userTradeMessage.getContentRaw().toLowerCase().equals("cancel")) {
							event.reply("Cancelled <:done:403285928233402378>").queue();
							return;
						}
						
						Pair<Long, List<ItemStack<Item>>> userTrade;
						try {
							userTrade = EconomyUtils.getTrade(userTradeMessage.getContentRaw());
						} catch(IllegalArgumentException e) {
							event.reply(e.getMessage()).queue();
							return;
						}
						
						long userMoney = userTrade.getLeft();
						List<ItemStack<Item>> userItems = userTrade.getRight();
						
						StringBuilder userItemsContent = new StringBuilder();
						for (ItemStack<Item> itemStack : userItems) {
							userItemsContent.append(String.format("%s x%,d\n", itemStack.getItem().getName(), itemStack.getAmount()));
						}
						
						EmbedBuilder embed = new EmbedBuilder();
						embed.setTitle("Final Trade");
						embed.addField(member.getUser().getAsTag() + " Gets", embedAuthor.getDescriptionBuilder().toString(), false);
						embed.addField(event.getAuthor().getAsTag() + " Gets", (userMoney != 0 ? String.format("$%,d\n", userMoney) : "") + userItemsContent, false);
						embed.setFooter(member.getUser().getAsTag() + " needs to type accept or yes to accept the trade", null);
						
						event.reply(embed.build()).queue(€ -> {
							PagedUtils.getConfirmation(event, 60, member.getUser(), confirmation -> {
								if (confirmation) {
									Document newAuthorData = database.getUserById(event.getAuthor().getIdLong(), null, projection).get("economy", Database.EMPTY_DOCUMENT);
									Document newUserData = database.getUserById(member.getIdLong(), null, projection).get("economy", Database.EMPTY_DOCUMENT);
									
									long totalAuthorWorth = authorMoney;
									long totalUserWorth = userMoney;
									
									if (newAuthorData.get("balance", 0L) < authorMoney) {
										event.reply("**" + event.getAuthor().getAsTag() + "** does not have $" + authorMoney + " :no_entry:").queue();
										return;
									}
									
									if (newUserData.get("balance", 0L) < userMoney) {
										event.reply("**" + member.getUser().getAsTag() + "** does not have $" + userMoney + " :no_entry:").queue();
										return;
									}

									Map<String, Long> types = new HashMap<>();
									
									List<Document> authorItemsData = newAuthorData.getList("items", Document.class, new ArrayList<>());
									List<Document> userItemsData = newUserData.getList("items", Document.class, new ArrayList<>());									
									for (ItemStack<Item> itemStack : authorItems) {
										ItemStack<Item> authorItem = EconomyUtils.getUserItem(authorItemsData, itemStack.getItem());
										if (authorItem.getAmount() < itemStack.getAmount()) {
											event.reply(String.format("**%s** does not have `%,d %s` :no_entry:", event.getAuthor().getAsTag(), itemStack.getAmount(), itemStack.getItem().getName())).queue();
											return;
										}

										if (itemStack.getItem() instanceof Envelope) {
											event.reply("You cannot trade envelopes, use money instead :no_entry:").queue();
											return;
										}

										long price = itemStack.getItem().getPrice() * itemStack.getAmount();

										types.compute(itemStack.getItem().getName(), (key, value) -> value != null ? value + price : price);
										
										totalAuthorWorth += !itemStack.getItem().isBuyable() ? 0 : price;
										
										EconomyUtils.removeItem(authorItemsData, itemStack);
										EconomyUtils.addItem(userItemsData, itemStack);
									}
									
									for (ItemStack<Item> itemStack : userItems) {
										ItemStack<Item> userItem = EconomyUtils.getUserItem(userItemsData, itemStack.getItem());
										if (userItem.getAmount() < itemStack.getAmount()) {
											event.reply(String.format("**%s** does not have `%,d %s` :no_entry:", member.getUser().getAsTag(), itemStack.getAmount(), itemStack.getItem().getName())).queue();
											return;
										}

										if (itemStack.getItem() instanceof Envelope) {
											event.reply("You cannot trade envelopes, use money instead :no_entry:").queue();
											return;
										}

										long price = itemStack.getItem().getPrice() * itemStack.getAmount();

										types.compute(itemStack.getItem().getName(), (key, value) -> value != null ? value + price : price);
										
										totalUserWorth += !itemStack.getItem().isBuyable() ? 0 : price;
										
										EconomyUtils.addItem(authorItemsData, itemStack);
										EconomyUtils.removeItem(userItemsData, itemStack);
									}

									types.put("Money", userMoney + authorMoney);

									Entry<String, Long> max = types.entrySet().stream()
										.max(Entry.comparingByValue())
										.get();

									if ((double) max.getValue() / (totalUserWorth + totalAuthorWorth) >= 0.7D) {
										event.reply(max.getKey() + " cannot make up more than 70% of the trades value :no_entry:").queue();
										return;
									}
									
									if (totalUserWorth / totalAuthorWorth > 5 || totalAuthorWorth / totalUserWorth > 5) {
										event.reply("You have to trade at least 20% the worth of the other persons trade :no_entry:").queue();
										return;
									}
									
									Bson authorUpdate = Updates.combine(Updates.inc("economy.balance", userMoney - authorMoney), Updates.set("economy.items", authorItemsData));
									Bson userUpdate = Updates.combine(Updates.inc("economy.balance", authorMoney - userMoney), Updates.set("economy.items", userItemsData));
									
									UpdateOptions updateOptions = new UpdateOptions().upsert(true);
									List<WriteModel<Document>> bulkData = List.of(
											new UpdateOneModel<>(Filters.eq("_id", event.getAuthor().getIdLong()), authorUpdate, updateOptions),
											new UpdateOneModel<>(Filters.eq("_id", member.getIdLong()), userUpdate, updateOptions)
									);

									database.bulkWriteUsers(bulkData, (result, exception) -> {
										if (exception != null) {
											exception.printStackTrace();
											event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
										} else {
											event.reply("All items and money have been transferred <:done:403285928233402378>").queue();
										}
									});
								} else {
									event.reply("Trade declined <:done:403285928233402378>").queue();
									return;
								}
							});
						});
					});
				});
			});
		});
		
	}
	
	public class BoosterCommand extends Sx4Command {
		
		public BoosterCommand() {
			super("booster");
			
			super.setAliases("boosters");
			super.setDescription("Buy boosters to be given an advantage at a cost");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
			super.setExamples("booster shop", "booster buy", "booster activate");
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="shop", aliases={"list"}, description="View all the boosters in the economy system", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"booster shop"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void shop(CommandEvent event, @Context Database database) {
			long balance = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.balance")).getEmbedded(List.of("economy", "balance"), 0L);
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setDescription("Buy boosters to be given benefits at a cost");
			embed.setColor(Settings.EMBED_COLOUR);
			embed.setAuthor("Booster Shop", null, event.getSelfUser().getEffectiveAvatarUrl());
			embed.setFooter(String.format("Use %sbooster buy <booster> to buy a booster | Balance: $%,d", event.getPrefix(), balance, event.getAuthor().getEffectiveAvatarUrl()));
			
			for (Booster booster : Booster.ALL) {
				embed.addField(booster.getName(), String.format("Price: $%,d\nDescription: %s %s", booster.getPrice(), booster.getDescription(), booster.isActivatable() ? "[Activatable]" : ""), false);
			}
			
			event.reply(embed.build()).queue();
		}
		
		@Command(value="buy", description="Buy a booster listed in the booster shop")
		@Examples({"booster buy Lended Pickaxe", "booster buy Lended"})
		public void buy(CommandEvent event, @Context Database database, @Argument(value="booster name", endless=true) String boosterArgument) {
			Document data = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.balance", "economy.items")).get("economy", Database.EMPTY_DOCUMENT);
			List<Document> items = data.getList("items", Document.class, new ArrayList<>());
			long balance = data.get("balance", 0L);
			
			Pair<String, BigInteger> boosterPair = EconomyUtils.getItemAndAmount(boosterArgument);
			String boosterName = boosterPair.getLeft();
			BigInteger boosterAmount = boosterPair.getRight();
			
			if (boosterAmount.compareTo(BigInteger.ONE) == -1) {
				event.reply("You have to at least buy one booster :no_entry:").queue();
				return;
			}
			
			Booster booster = Booster.getBoosterByName(boosterName);
			if (booster == null) {
				event.reply("I could not find that booster :no_entry:").queue();
				return;
			}
			
			BigInteger price = BigInteger.valueOf(booster.getPrice()).multiply(boosterAmount);
			if (BigInteger.valueOf(balance).compareTo(price) != -1) {
				EconomyUtils.addItem(items, booster, boosterAmount.longValue());
				
				Bson update = Updates.combine(
						Updates.set("economy.items", items),
						Updates.inc("economy.balance", -price.longValue())
				);
				
				database.updateUserById(event.getAuthor().getIdLong(), update, (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						event.reply(String.format("You just bought `%,d %s` for **$%,d** :ok_hand:", boosterAmount, booster.getName(), price.longValue())).queue();
					}
				});
			} else {
				event.reply(String.format("You do not have enough money to purchase `%,d %s` :no_entry:", boosterAmount, booster.getName())).queue();
			}
		}
		
		@Command(value="activate", description="Activates a booster which is activatable")
		@Examples({"booster activate Lended Pickaxe", "booster activate Lended"})
		public void activate(CommandEvent event, @Context Database database, @Argument(value="booster name", endless=true) String boosterName) {
			Document data = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.items", "economy.mineCooldown")).get("economy", Database.EMPTY_DOCUMENT);
			
			Booster booster = Booster.getBoosterByName(boosterName);
			if (booster == null) {
				event.reply("I could not find that booster :no_entry:").queue();
				return;
			}
			
			if (!booster.isActivatable()) {
				event.reply("That booster is not activatable :no_entry:").queue();
				return;
			}
			
			List<Document> userItems = data.getList("items", Document.class, new ArrayList<>());
			if (booster.equals(Booster.LENDED_PICKAXE)) {
				ItemStack<Item> userBooster = EconomyUtils.getUserItem(userItems, booster);
				if (userBooster.getAmount() == 0) {
					event.reply("You do not own any `" + booster.getName() + "` :no_entry:").queue();
					return;
				}
				
				Pickaxe userPickaxe = EconomyUtils.getUserPickaxe(userItems);
				if (userPickaxe == null) {
					event.reply("You do not own a pickaxe :no_entry:").queue();
					return;
				}
				
				Long mineCooldown = data.getLong("mineCooldown");
				if (mineCooldown == null || Clock.systemUTC().instant().getEpochSecond() - mineCooldown >= EconomyUtils.MINE_COOLDOWN) {
					event.reply("You currently do not have a cooldown on your mine :no_entry:").queue();
					return;
				}
				
				EconomyUtils.removeItem(userItems, booster, 1);
				database.updateUserById(event.getAuthor().getIdLong(), Updates.combine(Updates.set("economy.mineCooldown", null), Updates.set("economy.items", userItems)), (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						event.reply("Your booster `" + booster.getName() + "` has been activated :ok_hand:").queue();
					}
				});
			}
		}
		
	}
	
	@Command(value="referral", aliases={"referral link", "referrallink"}, description="Gives you a users referral links these can be used to give the user bonus money when you vote")
	@Examples({"referral", "referral @Shea#6653", "referral Shea", "referral 402557516728369153"})
	public void referral(CommandEvent event, @Argument(value="user", endless=true, nullDefault=true) String userArgument) {
		Member member;
		if (userArgument == null) {
			member = event.getMember();
		} else {
			member = ArgumentUtils.getMember(event.getGuild(), userArgument);
			if (member == null) {
				event.reply("I could not find that user :no_entry:").queue();
				return;
			}
		}
		
		event.reply(String.format("**Referral Links for %s**\n\nSx4: <https://discordbots.org/bot/440996323156819968/vote?referral=%s>",
				member.getUser().getAsTag(), member.getUser().getId(), member.getUser().getId())).queue();
	}
	
	@Command(value="vote", aliases={"vote bonus", "votebonus", "upvote"}, description="Upvote the bot on discord bot list to get some free money in the economy", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"vote"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void vote(CommandEvent event, @Context Database database) {
		LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
		boolean weekend = now.getDayOfWeek().equals(DayOfWeek.FRIDAY) || now.getDayOfWeek().equals(DayOfWeek.SATURDAY) || now.getDayOfWeek().equals(DayOfWeek.SUNDAY);
		
		Request request = new Request.Builder()
			.url("http://" + Settings.LOCAL_HOST + ":8080/440996323156819968/votes/user/" + event.getAuthor().getId() + "/unused/use")
			.addHeader("Authorization", TokenUtils.VOTE_API_SX4)
			.build();
		
		Sx4Bot.client.newCall(request).enqueue((Sx4Callback) response -> {
			JSONObject json = new JSONObject(response.body().string());
			if (json.getBoolean("success")) {
				JSONArray votes = new JSONArray();
				long money = 0;
				if (json.has("votes")) {
					votes = json.getJSONArray("votes");
				}

				Map<User, Integer> referredUsers = new HashMap<>();
				for (Object voteObject : votes) {
					JSONObject vote = (JSONObject) voteObject;

					money += vote.getBoolean("weekend") ? 1600 : 800;

					if (vote.getJSONObject("query").has("referral")) {
						User referredUser;
						if (vote.getJSONObject("query").get("referral") instanceof String[]) {
							referredUser = event.getShardManager().getUserById(vote.getJSONObject("query").getJSONArray("referral").getString(0));
						} else {
							referredUser = event.getShardManager().getUserById(vote.getJSONObject("query").getString("referral"));
						}

						if (referredUser != null) {
							if (referredUsers.containsKey(referredUser)) {
								referredUsers.put(referredUser, referredUsers.get(referredUser) + (vote.getBoolean("weekend") ? 500 : 250));
							} else {
								referredUsers.put(referredUser, vote.getBoolean("weekend") ? 500 : 250);
							}
						}
					}

				}

				UpdateOptions updateOptions = new UpdateOptions().upsert(true);
				List<WriteModel<Document>> updates = new ArrayList<>();
				StringBuilder referredBuilder = new StringBuilder();
				for (User key : referredUsers.keySet()) {
					updates.add(new UpdateOneModel<>(Filters.eq("_id", key.getIdLong()), Updates.inc("economy.balance", referredUsers.get(key)), updateOptions));
					referredBuilder.append(key.getAsTag() + " (**$" + referredUsers.get(key) + "**), ");
				}

				String referredContent = referredBuilder.length() != 0 ? referredBuilder.substring(0, referredBuilder.length() - 2) : "No one";
				int totalVotes = votes.length();
				String message = String.format("You have voted for the bot **%,d** time%s since you last used the command gathering you a total of **$%,d**, Vote for the bots again in 12 hours for more money."
						+ " Referred users: %s", totalVotes, totalVotes == 1 ? "" : "s", money, referredContent);

				updates.add(new UpdateOneModel<>(Filters.eq("_id", event.getAuthor().getIdLong()), Updates.inc("economy.balance", money), updateOptions));
				database.bulkWriteUsers(updates, (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						event.reply(message).queue();
					}
				});
			} else {
				if (json.getString("error").equals("This user has no unused votes")) {
					Request latest = new Request.Builder()
						.url("http://" + Settings.LOCAL_HOST + ":8080/440996323156819968/votes/user/" + event.getAuthor().getId() + "/latest")
						.addHeader("Authorization", TokenUtils.VOTE_API_SX4)
						.build();

					Sx4Bot.client.newCall(latest).enqueue((Sx4Callback) latestSx4Response -> {
						JSONObject latestJson = new JSONObject(latestSx4Response.body().string());

						EmbedBuilder embed = new EmbedBuilder();
						embed.setAuthor("Vote Bonus", null, event.getAuthor().getEffectiveAvatarUrl());

						long timestamp = Clock.systemUTC().instant().getEpochSecond();

						long timestampSx4 = 0;
						String timeSx4 = null;
						if (latestJson.has("vote")) {
							timestampSx4 = latestJson.getJSONObject("vote").getLong("time") - timestamp + EconomyUtils.VOTE_COOLDOWN;
							timeSx4 = latestJson.getBoolean("success") ? TimeUtils.toTimeString(timestampSx4, ChronoUnit.SECONDS) : null;
						}

						if (timeSx4 != null && timestampSx4 >= 0) {
							embed.addField("Sx4", "**[You have voted recently you can vote for the bot again in " + timeSx4 + "](https://discordbots.org/bot/440996323156819968/vote)**", false);
						} else {
							embed.addField("Sx4", "**[You can vote for Sx4 for an extra $" + (weekend ? 1600 : 800) + "](https://discordbots.org/bot/440996323156819968/vote)**", false);
						}

						event.reply(embed.build()).queue();
					});
				} else {
					event.reply("Oops something went wrong there, try again :no_entry:").queue();
				}
			}
		});
	}
	
	@Command(value="daily", aliases={"pd", "payday"}, description="Collect your daily money, repeatedly collect it everyday to get streaks the higher your streaks the better chance of getting a higher tier crate", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"daily"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void daily(CommandEvent event, @Context Database database) {
		Document data = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.dailyCooldown", "economy.streak", "economy.items")).get("economy", Database.EMPTY_DOCUMENT);
		
		long money;
		long timestampNow = Clock.systemUTC().instant().getEpochSecond();
		Long streakTime = data.getLong("dailyCooldown");
		
		EmbedBuilder embed = new EmbedBuilder();
		if (streakTime != null && timestampNow - streakTime <= EconomyUtils.DAILY_COOLDOWN) {
			event.reply("Slow down! You can collect your daily in " + TimeUtils.toTimeString(streakTime - timestampNow + EconomyUtils.DAILY_COOLDOWN, ChronoUnit.SECONDS) + " :stopwatch:").queue();
		} else if (streakTime != null && timestampNow - streakTime <= EconomyUtils.DAILY_COOLDOWN * 2) {
			int currentStreak = data.getInteger("streak") + 1;
			money = currentStreak >= 5 ? 250 : currentStreak == 4 ? 200 : currentStreak == 3 ? 170 : currentStreak == 2 ? 145 : currentStreak == 1 ? 120 : 100;
			
			List<Crate> crates = new ArrayList<>();
			for (Crate crate : Crate.ALL) {
				if (crate.isBuyable()) {
					if (random.nextInt((int) Math.ceil((crate.getChance() / currentStreak) * 4) + 1) == 0) {
						crates.add(crate);
					}
				}
			}
			
			crates.sort((a, b) -> Long.compare(b.getPrice(), a.getPrice()));
			
			Crate crateWon = crates.isEmpty() ? null : crates.get(0);
			String crateContent = crateWon == null ? "" : String.format("You also received a `%s` (**$%,d**), it has been added to your items.", crateWon.getName(), crateWon.getPrice());
			
			embed.setAuthor(event.getAuthor().getName(), null, event.getAuthor().getEffectiveAvatarUrl());
			embed.setColor(event.getMember().getColor());
			embed.setDescription("You have collected your daily money! (**+$" + money + "**)\nYou had a bonus of $" + (money - 100) + " for having a " + currentStreak + " day streak\n\n" + crateContent);

			Bson update = Updates.combine(
					Updates.set("economy.dailyCooldown", timestampNow),
					Updates.inc("economy.balance", money),
					Updates.inc("economy.streak", 1)
			);
			
			if (crateWon != null) {
				List<Document> items = data.getList("items", Document.class, new ArrayList<>());
				EconomyUtils.addItem(items, crateWon, 1);
				
				update = Updates.combine(update, Updates.set("economy.items", items));
			}
			
			database.updateUserById(event.getAuthor().getIdLong(), update, (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply(embed.build()).queue();
				}
			});
		} else {
			money = 100;
			
			embed.setAuthor(event.getAuthor().getName(), null, event.getAuthor().getEffectiveAvatarUrl());
			embed.setDescription("You have collected your daily money! (**+$" + money + "**)" + (data.getInteger("streak", 0) != 0 ? "\n\nIt has been over 2 days since you last used the command, your streak has been reset" : ""));
			embed.setColor(event.getMember().getColor());
			
			Bson update = Updates.combine(
					Updates.set("economy.dailyCooldown", timestampNow),
					Updates.inc("economy.balance", money),
					Updates.set("economy.streak", 0)
			);
			
			database.updateUserById(event.getAuthor().getIdLong(), update, (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply(embed.build()).queue();
				}
			});
		}
	}
	
	@Command(value="balance", aliases={"bal"}, description="Check the amount of money a user currently has")
	@Examples({"balance", "balance @Shea#6653", "balance 402557516728369153", "balance Shea"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void balance(CommandEvent event, @Context Database database, @Argument(value="user", endless=true, nullDefault=true) String userArgument) {
		Member member;
		if (userArgument == null) {
			member = event.getMember();
		} else {
			member = ArgumentUtils.getMember(event.getGuild(), userArgument);
			if (member == null) {
				event.reply("I could not find that user :no_entry:").queue();
				return;
			}
		}
		
		long balance = database.getUserById(member.getIdLong(), null, Projections.include("economy.balance")).getEmbedded(List.of("economy", "balance"), 0L);
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setColor(member.getColor());
		embed.setAuthor(member.getUser().getName(), null, member.getUser().getEffectiveAvatarUrl());
		embed.setDescription((member.equals(event.getMember()) ? "Your" : "Their") + " balance: " + String.format("**$%,d**", balance));
		event.reply(embed.build()).queue();
	}
	
	@Command(value="winnings", description="Check the amount of money a user has won/lost through betting")
	@Examples({"winnings", "winnings @Shea#6653", "winnings 402557516728369153", "winnings Shea"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void winnings(CommandEvent event, @Context Database database, @Argument(value="user", endless=true, nullDefault=true) String userArgument) {
		Member member;
		if (userArgument == null) {
			member = event.getMember();
		} else {
			member = ArgumentUtils.getMember(event.getGuild(), userArgument);
			if (member == null) {
				event.reply("I could not find that user :no_entry:").queue();
				return;
			}
		}
		
		long winnings = database.getUserById(member.getIdLong(), null, Projections.include("economy.winnings")).getEmbedded(List.of("economy", "winnings"), 0L);
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setColor(member.getColor());
		embed.setAuthor(member.getUser().getName(), null, member.getUser().getEffectiveAvatarUrl());
		embed.setDescription((member.equals(event.getMember()) ? "Your" : "Their") + " winnings: " + String.format("**$%,d**", winnings));
		event.reply(embed.build()).queue();
	}
	
	@Command(value="networth", description="Check the networth of a user")
	@Examples({"networth", "networth @Shea#6653", "networth 402557516728369153", "networth Shea"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void networth(CommandEvent event, @Context Database database, @Argument(value="user", endless=true, nullDefault=true) String userArgument) {
		Member member;
		if (userArgument == null) {
			member = event.getMember();
		} else {
			member = ArgumentUtils.getMember(event.getGuild(), userArgument);
			if (member == null) {
				event.reply("I could not find that user :no_entry:").queue();
				return;
			}
		}
		
		Document data = database.getUserById(member.getIdLong(), null, Projections.include("economy.balance", "economy.items")).get("economy", Database.EMPTY_DOCUMENT);
		long networth = EconomyUtils.getUserNetworth(data);
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setColor(member.getColor());
		embed.setAuthor(member.getUser().getName(), null, member.getUser().getEffectiveAvatarUrl());
		embed.setDescription((member.equals(event.getMember()) ? "Your" : "Their") + " networth: " + String.format("**$%,d**", networth));
		event.reply(embed.build()).queue();
	}
	
	@Command(value="rep", description="Give another user some reputation")
	@Examples({"rep Shea", "rep @Shea#6653 --amount", "rep --amount"})
	public void reputation(CommandEvent event, @Context Database database, @Argument(value="user", endless=true, nullDefault=true) String userArgument, @Option(value="amount", description="Shows the amount of reputation the user has rather than giving them reputation") boolean amountOption) {
		Member member;
		if (userArgument == null) {
			member = event.getMember();
		} else {
			member = ArgumentUtils.getMember(event.getGuild(), userArgument);
			if (member == null) {
				event.reply("I could not find that user :no_entry:").queue();
				return;
			}
		}
		
		if (amountOption) {
			int reputation = database.getUserById(member.getIdLong(), null, Projections.include("reputation.amount")).getEmbedded(List.of("reputation", "amount"), 0);
			event.reply(String.format("%s currently %s **%,d** repuatation", member.equals(event.getMember()) ? "You" : member.getUser().getAsTag(), member.equals(event.getMember()) ? "have" : "has", reputation)).queue();
		} else {
			if (member.equals(event.getMember())) {
				event.reply("You cannot give reputation yourself :no_entry:").queue();
				return;
			}
			
			if (member.getUser().isBot()) {
				event.reply("You cannot give repuation to bots :no_entry:").queue();
				return;
			}
			
			Long reputationCooldown = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("reputation.cooldown")).getEmbedded(List.of("reputation", "cooldown"), Long.class);
			long timestampNow = Clock.systemUTC().instant().getEpochSecond();
			
			if (reputationCooldown != null && timestampNow - reputationCooldown <= EconomyUtils.REPUTATION_COOLDOWN) {
				event.reply("Slow down! You can give out reputation in " + TimeUtils.toTimeString(reputationCooldown - timestampNow + EconomyUtils.REPUTATION_COOLDOWN, ChronoUnit.SECONDS) + " :stopwatch:").queue();
			} else {
				UpdateOptions updateOptions = new UpdateOptions().upsert(true);
				List<WriteModel<Document>> bulkData = List.of(
					new UpdateOneModel<>(Filters.eq("_id", event.getAuthor().getIdLong()), Updates.set("reputation.cooldown", timestampNow), updateOptions),
					new UpdateOneModel<>(Filters.eq("_id", member.getIdLong()), Updates.inc("reputation.amount", 1), updateOptions)
				);
				
				database.bulkWriteUsers(bulkData, (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						event.reply("**+1**, " + member.getUser().getName() + " has gained reputation").queue();
					}
				});
			}
		}
	}
	
	@Command(value="double or nothing", aliases={"don", "doubleornothing", "allin", "all in", "dn"}, description="Risk it all in the hope of doubling your money or losing it all", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"double or nothing"})
	@Cooldown(value=40)
	public void doubleOrNothing(CommandEvent event, @Context Database database) {
		Document data = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.balance", "economy.items")).get("economy", Database.EMPTY_DOCUMENT);
		
		long balance = data.get("balance", 0L);
		if (balance < 1) {
			event.reply("You do not have any money to bet :no_entry:").queue();
			event.removeCooldown();
			return;
		}
		
		List<Document> items = data.getList("items", Document.class, Collections.emptyList());
		for (Document itemData : items) {
			Item item = Item.getItemByName(itemData.getString("name"));
			if (item instanceof Envelope) {
				event.reply("You cannot have envelopes in your inventory when using double or nothing :no_entry:").queue();
				event.removeCooldown();
				return;
			}
		}

		event.reply(String.format(event.getAuthor().getName() + ", this will bet **$%,d** are you sure you want to bet this (Yes or No)", balance)).queue(originalMessage -> {
			PagedUtils.getConfirmation(event, 30, event.getAuthor(), confirmation -> {
				long balanceUpdated = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.balance")).getEmbedded(List.of("economy", "balance"), 0L);
				if (balanceUpdated < 1) {
					event.reply("You do not have any money to bet :no_entry:").queue();
					event.removeCooldown();
					return;
				} else {
					if (confirmation) {
						originalMessage.delete().queue(null, e -> {});
						event.reply(String.format("You just put **$%,d** on the line and...", balanceUpdated)).queue(message -> {
							Bson update;
							String messageString;
							if (random.nextBoolean()) {
								update = Updates.combine(
									Updates.mul("economy.balance", 2),
									Updates.inc("economy.winnings", balanceUpdated)
								);
								
								messageString = String.format("You double your money! **+$%,d**", balanceUpdated);
							} else {
								update = Updates.combine(
										Updates.set("economy.balance", 0L),
										Updates.inc("economy.winnings", -balanceUpdated)
								);
									
								messageString = String.format("You lost it all! **-$%,d**", balanceUpdated);
							}
							
							database.updateUserById(event.getAuthor().getIdLong(), update, (result, exception) -> {
								if (exception != null) {
									exception.printStackTrace();
									event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
								} else {
									message.editMessage(messageString).queueAfter(2, TimeUnit.SECONDS);
								}
							});
								
							event.removeCooldown();
						});
					} else {
						event.reply("Cancelled <:done:403285928233402378>").queue();
						event.removeCooldown();
					}
				}
			});
		});
	}
	
	public class MinerCommand extends Sx4Command {
		
		public MinerCommand() {
			super("miner");
			
			super.setDescription("You can buy miners so that you can gain recources every 2 hours");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
			super.setExamples("miner shop", "miner buy", "miner collect");
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="shop", aliases={"list"}, description="View all the miners you can buy", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"miner shop"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void shop(CommandEvent event, @Context Database database) {
			long balance = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.balance")).getEmbedded(List.of("economy", "balance"), 0L);
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setAuthor("Miner Shop", null, event.getSelfUser().getEffectiveAvatarUrl());
			embed.setDescription("Miners are a good way to easily gather materials");
			embed.setColor(Settings.EMBED_COLOUR);
			embed.setFooter(String.format("Use %sminer buy <miner> to buy a miner | Balance: $%,d", event.getPrefix(), balance), event.getAuthor().getEffectiveAvatarUrl());
			
			for (Miner miner : Miner.ALL) {
				if (miner.isBuyable()) {
					embed.addField(miner.getName(), String.format("Price: $%,d", miner.getPrice()), true);
				}
			}
			
			event.reply(embed.build()).queue();
		}
		
		@Command(value="buy", description="Buy a miner which is displayed in the miner shop")
		@Examples({"miner buy Gold Miner 10", "miner buy Platinum Miner", "miner buy Iron 5"})
		public void buy(CommandEvent event, @Context Database database, @Argument(value="miner name", endless=true) String minerArgument) {
			Document data = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.balance", "economy.items")).get("economy", Database.EMPTY_DOCUMENT);
			List<Document> items = data.getList("items", Document.class, new ArrayList<>());
			long balance = data.get("balance", 0L);
			
			Pair<String, BigInteger> minerPair = EconomyUtils.getItemAndAmount(minerArgument);
			String minerName = minerPair.getLeft();
			BigInteger minerAmount = minerPair.getRight();
			
			if (minerAmount.compareTo(BigInteger.ONE) == -1) {
				event.reply("You need to buy at least one miner :no_entry:").queue();
				return;
			}
			
			Miner miner = Miner.getMinerByName(minerName);
			if (miner == null) {
				event.reply("I could not find that miner :no_entry:").queue();
				return;
			}
			
			BigInteger price = BigInteger.valueOf(miner.getPrice()).multiply(minerAmount);
			if (BigInteger.valueOf(balance).compareTo(price) != -1) {
				EconomyUtils.addItem(items, miner, minerAmount.longValue());
				
				Bson update = Updates.combine(
					Updates.inc("economy.balance", -price.longValue()),
					Updates.set("economy.items", items)
				);
				
				database.updateUserById(event.getAuthor().getIdLong(), update, (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						event.reply(String.format("You just bought `%,d %s` for **$%,d** :ok_hand:", minerAmount, miner.getName(), price.longValue())).queue();
					}
				});
			} else {
				event.reply(String.format("You do not have enough money to purchase `%,d %s` :no_entry:", minerAmount, miner.getName())).queue();
			}
		}
		
		@Command(value="collect", description="Collect your materials from all the miners you own", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"miner collect"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void collect(CommandEvent event, @Context Database database) {
			Document data = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.items", "economy.minerCooldown")).get("economy", Database.EMPTY_DOCUMENT);
			
			List<Document> items = data.getList("items", Document.class, new ArrayList<>());
			Map<Miner, Long> userMiners = new HashMap<>();
			for (Document item : items) {
				for (Miner miner : Miner.ALL) {
					if (miner.getName().equals(item.getString("name"))) {
						userMiners.put(miner, item.getLong("amount"));
					}
				}
			}
			
			if (userMiners.isEmpty()) {
				event.reply("You do not own any miners :no_entry:").queue();
				return;
			}
			
			long timestampNow = Clock.systemUTC().instant().getEpochSecond();
			Long minerTime = data.getLong("minerCooldown");
			
			EmbedBuilder embed = new EmbedBuilder();
			if (minerTime != null && timestampNow - minerTime <= EconomyUtils.MINER_COOLDOWN) {
				event.reply("Slow down! You can collect from your miner in " + TimeUtils.toTimeString(minerTime - timestampNow + EconomyUtils.MINER_COOLDOWN, ChronoUnit.SECONDS) + " :stopwatch:").queue();
			} else {
				Map<Material, Long> materials = new HashMap<>();
				for (Miner userMiner : userMiners.keySet()) {
					for (Material material : Material.ALL) {
						if (!material.isHidden()) {
							double randomFloat = 0.85D + Math.random() * (1D - 0.85D);
							long materialAmount = (long) Math.round((userMiners.get(userMiner) / Math.ceil(material.getChance() * userMiner.getMultiplier())) * userMiner.getMaximumMaterials() * randomFloat);
							if (materialAmount != 0) {
								materials.compute(material, (key, value) -> value != null ? value + materialAmount : materialAmount);
							} else {
								if (random.nextInt((int) Math.ceil(material.getChance() * userMiner.getMultiplier()) + 1) == 0) {
									materials.compute(material, (key, value) -> value != null ? value + 1 : 1L);
								}
							}
						}
					}
				}
				
				long totalItems = 0;
				StringBuilder contentBuilder = new StringBuilder();
				if (!materials.isEmpty()) {
					List<Material> materialKeys = new ArrayList<>(materials.keySet());
					materialKeys.sort((a, b) -> Long.compare(materials.get(b), materials.get(a)));
					for (int i = 0; i < materialKeys.size(); i++) {
						Material key = materialKeys.get(i);
						long value = materials.get(key);
						totalItems += value;
						
						EconomyUtils.addItem(items, key, value);
						
						contentBuilder.append(String.format("• %,d %s %s", value, key.getName(), key.getEmote()));
						if (i != materialKeys.size() - 1) {
							contentBuilder.append("\n");
						}
					}
				}
				
				embed.setAuthor(event.getAuthor().getName(), null, event.getAuthor().getEffectiveAvatarUrl());
				embed.setDescription(String.format("You used your miners and gathered **%,d** material%s\n\n%s", totalItems, totalItems == 1 ? "" : "s", contentBuilder.length() == 0 ? "": contentBuilder.toString()));
				embed.setColor(event.getMember().getColor());
				
				Bson update = Updates.combine(Updates.set("economy.minerCooldown", timestampNow), Updates.set("economy.items", items));
				database.updateUserById(event.getAuthor().getIdLong(), update, (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						event.reply(embed.build()).queue();
					}
				});
			}
		}
		
	}
	
	public class PickaxeCommand extends Sx4Command {
		
		public PickaxeCommand() {
			super("pickaxe");
			
			super.setAliases("pick");
			super.setDescription("Pickaxes allow you to gain some extra money and gain some materials");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
			super.setExamples("pickaxe shop", "pickaxe buy", "pickaxe info");
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="shop", aliases={"list"}, description="View all the pickaxes you can buy/craft", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"pickaxe shop"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void shop(CommandEvent event, @Context Database database) {
			long balance = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.balance")).getEmbedded(List.of("economy", "balance"), 0L);
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setAuthor("Pickaxe Shop", null, event.getSelfUser().getEffectiveAvatarUrl());
			embed.setColor(Settings.EMBED_COLOUR);
			embed.setDescription("Pickaxes are a good way to gain some extra money aswell as some materials");
			embed.setFooter(String.format("Use %spickaxe buy <pickaxe> to buy a pickaxe | Balance: $%,d", event.getPrefix(), balance), event.getAuthor().getEffectiveAvatarUrl());
			
			for (Pickaxe pickaxe : Pickaxe.ALL) {
				if (pickaxe.isBuyable() || pickaxe.isCraftable()) {
					StringBuilder craftContent = new StringBuilder();
					if (pickaxe.isCraftable()) {
						List<ItemStack<Material>> craftingItems = pickaxe.getCraftingRecipe().getCraftingMaterials();
						for (int i = 0; i < craftingItems.size(); i++) {
							ItemStack<Material> itemStack = craftingItems.get(i);
							craftContent.append(itemStack.getAmount() + " " + itemStack.getItem().getName());
							if (i != craftingItems.size() - 1) {
								craftContent.append("\n");
							}
						}
					}
					
					if (craftContent.length() == 0) {
						craftContent = new StringBuilder("Not Craftable");
					}
					
					embed.addField(pickaxe.getName(), "Price: " + (pickaxe.isBuyable() ? String.format("$%,d", pickaxe.getPrice()) : "Not Buyable") + "\nCraft: " + craftContent.toString() + "\nDurability: " + pickaxe.getDurability(), true);
				}
			}
			
			event.reply(embed.build()).queue();
		}
		
		@Command(value="buy", description="Buy a pickaxe which is listed in the pickaxe shop")
		@Examples({"pickaxe buy Sx4 Pickaxe", "pickaxe buy Platinum"})
		public void buy(CommandEvent event, @Context Database database, @Argument(value="pickaxe name", endless=true) String pickaxeName) {
			Document data = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.balance", "economy.items")).get("economy", Database.EMPTY_DOCUMENT);
			
			Pickaxe pickaxe = Pickaxe.getPickaxeByName(pickaxeName);
			if (pickaxe == null) {
				event.reply("I could not find that pickaxe :no_entry:").queue();
				return;
			}
			
			if (!pickaxe.isBuyable()) {
				event.reply("That pickaxe is not buyable :no_entry:").queue();
				return;
			}
			
			List<Document> userItems = data.getList("items", Document.class, new ArrayList<>());
			if (EconomyUtils.hasPickaxe(userItems)) {
				event.reply("You already own a pickaxe :no_entry:").queue();
				return;
			}
			
			long balance = data.get("balance", 0L);
			if (balance >= pickaxe.getPrice()) {
				EconomyUtils.addItem(userItems, pickaxe, 1, new Document("currentDurability", pickaxe.getDurability()));
				
				Bson update = Updates.combine(
						Updates.set("economy.items", userItems),
						Updates.inc("economy.balance", -pickaxe.getPrice())
				);
				
				database.updateUserById(event.getAuthor().getIdLong(), update, (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						event.reply("You just bought a `" + pickaxe.getName() + "` for " + String.format("**$%,d**", pickaxe.getPrice()) + " :ok_hand:").queue();
					}
				});
			} else {
				event.reply("You do not have enough money to purchase a `" + pickaxe.getName() + "` :no_entry:").queue();
			}
		}
		
		@Command(value="info", description="Gives info of yours or another users pickaxe")
		@Examples({"pickaxe info", "pickaxe info @Shea#6653", "pickaxe info 402557516728369153", "pickaxe info Shea"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void info(CommandEvent event, @Context Database database, @Argument(value="user", endless=true, nullDefault=true) String userArgument) {
			Member member;
			if (userArgument == null) {
				member = event.getMember();
			} else {
				member = ArgumentUtils.getMember(event.getGuild(), userArgument);
				if (member == null) {
					event.reply("I could not find that user :no_entry:").queue();
					return;
				}
			}
			
			
			List<Document> items = database.getUserById(member.getIdLong(), null, Projections.include("economy.items")).getEmbedded(List.of("economy", "items"), Collections.emptyList());
			Pickaxe pickaxe = EconomyUtils.getUserPickaxe(items);
			if (pickaxe == null) {
				event.reply((member.equals(event.getMember()) ? "You do" : "That user does") + " not have a pickaxe :no_entry:").queue();
				return;
			}
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setAuthor(member.getUser().getName() + "'s " + pickaxe.getName(), null, member.getUser().getEffectiveAvatarUrl());
			embed.setColor(member.getColor());
			embed.setThumbnail("https://emojipedia-us.s3.amazonaws.com/thumbs/120/twitter/131/pick_26cf.png");
			embed.addField("Durability", pickaxe.getCurrentDurability() + "/" + pickaxe.getDurability(), false);
			if (pickaxe.isBuyable()) {
				embed.addField("Current Price", String.format("$%,d", Math.round(((double) pickaxe.getPrice() / pickaxe.getDurability()) * pickaxe.getCurrentDurability())), false);
				embed.addField("Price", String.format("$%,d", pickaxe.getPrice()), false);
			}
			embed.addField("Upgrades", String.valueOf(pickaxe.getUpgrades()), false);
			
			event.reply(embed.build()).queue();
		}
		
		@Command(value="craft", description="Craft a pickaxe which is in the pickaxe shop aslong as it displays as craftable")
		@Examples({"pickaxe craft Platinum Pickaxe", "pickaxe craft Gold"})
		public void craft(CommandEvent event, @Context Database database, @Argument(value="pickaxe name", endless=true) String pickaxeName) {
			Pickaxe pickaxe = Pickaxe.getPickaxeByName(pickaxeName);
			if (pickaxe == null) {
				event.reply("I could not find that pickaxe :no_entry:").queue();
				return;
			}
			
			if (!pickaxe.isCraftable()) {
				event.reply("That pickaxe is not craftable :no_entry:").queue();
				return;
			}
			
			List<Document> userItems = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.items")).getEmbedded(List.of("economy", "items"), new ArrayList<>());
			if (EconomyUtils.hasPickaxe(userItems)) {
				event.reply("You already own a pickaxe :no_entry:").queue();
				return;
			}
			
			for (ItemStack<Material> craftItem : pickaxe.getCraftingRecipe().getCraftingMaterials()) {
				ItemStack<Item> userItem = EconomyUtils.getUserItem(userItems, craftItem.getItem());
				if (userItem.getAmount() < craftItem.getAmount()) {
					event.reply(String.format("You do not have `%,d %s` :no_entry:", craftItem.getAmount(), craftItem.getItem().getName())).queue();
					return;
				}
				
				EconomyUtils.removeItem(userItems, craftItem);
			}
			
			EconomyUtils.addItem(userItems, pickaxe, 1, new Document("currentDurability", pickaxe.getDurability()));
			
			database.updateUserById(event.getAuthor().getIdLong(), Updates.set("economy.items", userItems), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("You just crafted a `" + pickaxe.getName() + "` with " + GeneralUtils.joinGrammatical(pickaxe.getCraftingRecipe().getCraftingMaterials()) + " :ok_hand:").queue();
				}
			});
		}
		
		@Command(value="upgrade", description="Upgrade your current pickaxe, you can view the upgrades in `pickaxe upgrades`")
		@Examples({"pickaxe upgrade money", "pickaxe upgrade durability", "pickaxe upgrade multiplier"})
		public void upgrade(CommandEvent event, @Context Database database, @Argument(value="upgrade name") String upgradeName, @Argument(value="upgrades", nullDefault=true) Integer upgradesArgument) {
			Document data = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.balance", "economy.items")).get("economy", Database.EMPTY_DOCUMENT);
			
			int upgrades = upgradesArgument == null ? 1 : upgradesArgument;
			if (upgrades < 1) {
				event.reply("You need to buy the upgrade at least once :no_entry:").queue();
				return;
			}
			
			if (upgrades > 1000) {
				event.reply("You cannot buy anymore than 1000 upgrades at one time :no_entry:").queue();
				return;
			}
			
			List<Document> items = data.getList("items", Document.class, Collections.emptyList());
			if (!EconomyUtils.hasPickaxe(items)) {
				event.reply("You do not own a pickaxe :no_entry:").queue();
				return;
			}
			
			PickaxeUpgrade upgrade = PickaxeUpgrade.getPickaxeUpgradeByName(upgradeName);
			if (upgrade == null) {
				event.reply("I could not find that upgrade, the upgrades are " + GeneralUtils.joinGrammatical(Arrays.asList(PickaxeUpgrade.ALL)) + " :no_entry:").queue();
				return;
			}
			
			Pickaxe pickaxe = EconomyUtils.getUserPickaxe(items);
			Pickaxe defaultPickaxe = pickaxe.getDefaultPickaxe();
			
			if (!pickaxe.isBuyable()) {
				event.reply("You cannot upgrade this pickaxe :no_entry:").queue();
				return;
			}
			
			long balance = data.get("balance", 0L);
			
			int currentUpgrades = pickaxe.getUpgrades();
			long price = 0;
			for (int i = 0; i < upgrades; i++) {
			    price += Math.round(0.015D * defaultPickaxe.getPrice() * currentUpgrades++ + 0.025D * defaultPickaxe.getPrice());
			}
			
			if (balance >= price) {
				Bson update = Updates.combine(
						Updates.inc("economy.items.$[pickaxe].upgrades", upgrades),
						Updates.set("economy.items.$[pickaxe].price", pickaxe.getPrice() + (Math.round(defaultPickaxe.getPrice() * 0.015D) * upgrades)),
						Updates.inc("economy.balance", -price)
				);
				
				if (upgrade.equals(PickaxeUpgrade.MONEY)) {
					int increase = (int) Math.round(defaultPickaxe.getMinimumYield() * upgrade.getIncreasePerUpgrade()) * upgrades;
					update = Updates.combine(
							update,
							Updates.set("economy.items.$[pickaxe].minimumYield", pickaxe.getMinimumYield() + increase),
							Updates.set("economy.items.$[pickaxe].maximumYield", pickaxe.getMaximumYield() + increase)
					);
				} else if (upgrade.equals(PickaxeUpgrade.DURABILITY)) {
					update = Updates.combine(
							update, 
							Updates.set("economy.items.$[pickaxe].maximumDurability", (int) (pickaxe.getDurability() + (upgrade.getIncreasePerUpgrade() * upgrades))),
							Updates.inc("economy.items.$[pickaxe].currentDurability", (int) (upgrade.getIncreasePerUpgrade() * upgrades))
					);
				} else if (upgrade.equals(PickaxeUpgrade.MULTIPLIER)) {
					update = Updates.combine(
							update,
							Updates.set("economy.items.$[pickaxe].multiplier", pickaxe.getMultiplier() * Math.pow(upgrade.getIncreasePerUpgrade(), upgrades))
					);
				}
				
				String message = String.format("You just upgraded %s %d time%s for your `%s` for **$%,d** :ok_hand:", upgrade.getName().toLowerCase(), upgrades, (upgrades == 1 ? "" : "s"), pickaxe.getName(), price);
				
				UpdateOptions updateOptions = new UpdateOptions().arrayFilters(List.of(Filters.eq("pickaxe.name", pickaxe.getName()))).upsert(true);
				database.updateUserById(event.getAuthor().getIdLong(), null, update, updateOptions, (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						event.reply(message).queue();
					}
				});
			} else {
				event.reply("You cannot afford " + upgrades + " for your pickaxe it will cost you " + String.format("**$%,d**", price) + " :no_entry:").queue();
			}
		}
		
		@Command(value="upgrades", description="View all the upgrades you can put on your pickaxe and their current cost", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"pickaxe upgrades"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void upgrades(CommandEvent event, @Context Database database) {
			Document data = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.items", "economy.balance")).get("economy", Database.EMPTY_DOCUMENT);
			long balance = data.get("balance", 0L);
			List<Document> items = data.getList("items", Document.class, Collections.emptyList());
			
			Pickaxe defaultPickaxe = null;
			Pickaxe pickaxe = EconomyUtils.getUserPickaxe(items);
			if (pickaxe != null) {
				defaultPickaxe = pickaxe.getDefaultPickaxe();
			}
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setFooter("Use pickaxe upgrade <upgrade> to apply an upgrade to your pickaxe | Balance: " + String.format("$%,d", balance), event.getAuthor().getEffectiveAvatarUrl());
			embed.setAuthor("Pickaxe Upgrades", null, event.getSelfUser().getEffectiveAvatarUrl());
			embed.setColor(event.getMember().getColor());
			
			for (PickaxeUpgrade upgrade : PickaxeUpgrade.ALL) {
				embed.addField(upgrade.getName(), "Description: " + upgrade.getDescription() + (pickaxe != null ? "\nPrice: " + String.format("$%,d", Math.round((defaultPickaxe.getPrice() * 0.025D) + (pickaxe.getUpgrades() * (defaultPickaxe.getPrice() * 0.015D)))) : ""), false);
			}
			
			event.reply(embed.build()).queue();
		}
		
		@Command(value="repair", description="Repair your current pickaxe with its deticated material if it has one", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"pickaxe repair 50", "pickaxe repair"})
		public void repair(CommandEvent event, @Context Database database, @Argument(value="durability", nullDefault=true) Integer durabilityAmount) {
			List<Document> items = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.items")).getEmbedded(List.of("economy", "items"), Collections.emptyList());
			
			Pickaxe pickaxe = EconomyUtils.getUserPickaxe(items);
			if (pickaxe == null) {
				event.reply("You do not own a pickaxe :no_entry:").queue();
				return;
			}
			
			if (!pickaxe.isRepairable()) {
				event.reply("Your pickaxe is not repairable :no_entry:").queue();
				return;
			}
			
			if (pickaxe.getDurability() <= pickaxe.getCurrentDurability()) {
				event.reply("Your pickaxe is already at full durability :no_entry:").queue();
				return;
			}
			
			int maxDurability = pickaxe.getDurability() - pickaxe.getCurrentDurability();
			
			int durabilityNeeded;
			if (durabilityAmount == null) {
				durabilityNeeded = maxDurability;
			} else {
				if (durabilityAmount > maxDurability) {
					event.reply("You can only repair your pickaxe by **" + maxDurability + "** durability :no_entry:").queue();
					return;
				}
				
				durabilityNeeded = durabilityAmount;
			}
			
			Item repairItem = pickaxe.getRepairItem();
			ItemStack<Item> userItem = EconomyUtils.getUserItem(items, repairItem);
			int cost = pickaxe.getAmountOfMaterialsForRepair(durabilityNeeded);
			if (userItem.getAmount() < cost) {
				long fixBy = pickaxe.getEstimateOfDurability(userItem.getAmount());
				event.reply("You do not have enough materials to fix your pickaxe by **" + durabilityNeeded + "** durability, you would need `" + cost + " " + repairItem.getName() + "`. You can fix your pickaxe by **" + fixBy + "** durability with your current amount of `" + repairItem.getName() + "` :no_entry:").queue();
				return;
			}
			
			event.reply("It will cost you `" + cost + " " + repairItem.getName() + "` to repair your pickaxe by **" + durabilityNeeded + "** durability, are you sure you want to repair it? (Yes or No)").queue(message -> {
				PagedUtils.getConfirmation(event, 60, event.getAuthor(), confirmation -> {
					if (confirmation) {
						message.delete().queue();
						
						List<Document> itemsNew = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.items")).getEmbedded(List.of("economy", "items"), new ArrayList<>());
						
						Pickaxe pickaxeNew = EconomyUtils.getUserPickaxe(itemsNew);
						if (pickaxeNew == null) {
							event.reply("You no longer own a pickaxe :no_entry:").queue();
						}
						
						if (!pickaxeNew.getName().equals(pickaxe.getName())) {
							event.reply("You have changed pickaxe since you answered :no_entry:").queue();
						}
						
						if (pickaxeNew.getCurrentDurability() != pickaxe.getCurrentDurability()) {
							event.reply("Your pickaxe durability has changed since answering :no_entry:").queue();
							return;
						}
						
						ItemStack<Item> userItemNew = EconomyUtils.getUserItem(itemsNew, repairItem);
						if (userItemNew.getAmount() < cost) {
							long fixBy = pickaxe.getEstimateOfDurability(userItemNew.getAmount());
							event.reply("You do not have enough materials to fix your pickaxe by **" + durabilityNeeded + "** durability, you would need `" + cost + " " + repairItem.getName() + "`. You can fix your pickaxe by **" + fixBy + "** durability with your current amount of `" + repairItem.getName() + "` :no_entry:").queue();
							return;
						}
					
						EconomyUtils.removeItem(itemsNew, repairItem, cost);
						EconomyUtils.editItem(itemsNew, pickaxeNew, "currentDurability", pickaxeNew.getCurrentDurability() + durabilityNeeded);
						
						database.updateUserById(event.getAuthor().getIdLong(), Updates.set("economy.items", itemsNew), (result, exception) -> {
							if (exception != null) {
								exception.printStackTrace();
								event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
							} else {
								event.reply("You just repaired your pickaxe by **" + durabilityNeeded + "** durability :ok_hand:").queue();
							}
						});
					} else {
						event.reply("Cancelled <:done:403285928233402378>").queue();
					}
				});
			});
		}
	}
	
	public class FishingRodCommand extends Sx4Command {
		
		public FishingRodCommand() {
			super("fishing rod");
			
			super.setAliases("fishingrod", "rod");
			super.setDescription("Fishing rods increase your yield of money per fish");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
			super.setExamples("fishing rod shop", "fishing rod buy", "fishing rod info");
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="shop", aliases={"list"}, description="View all the rods you can buy/craft")
		@Examples({"fishing rod shop"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void shop(CommandEvent event, @Context Database database) {
			long balance = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.balance")).getEmbedded(List.of("economy", "balance"), 0L);
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setAuthor("Fishing Rod Shop", null, event.getSelfUser().getEffectiveAvatarUrl());
			embed.setColor(Settings.EMBED_COLOUR);
			embed.setDescription("Fishing rods are a good way to gain some extra money from each fish");
			embed.setFooter(String.format("Use %sfishing rod buy <fishing rod> to buy a fishing rod | Balance: $%,d", event.getPrefix(), balance), event.getAuthor().getEffectiveAvatarUrl());
			
			for (Rod rod : Rod.ALL) {
				if (rod.isBuyable() || rod.isCraftable()) {
					StringBuilder craftContent = new StringBuilder();
					if (rod.isCraftable()) {
						List<ItemStack<Material>> craftingItems = rod.getCraftingRecipe().getCraftingMaterials();
						for (int i = 0; i < craftingItems.size(); i++) {
							ItemStack<Material> itemStack = craftingItems.get(i);
							craftContent.append(itemStack.getAmount() + " " + itemStack.getItem().getName());
							if (i != craftingItems.size() - 1) {
								craftContent.append("\n");
							}
						}
					}
					
					if (craftContent.length() == 0) {
						craftContent = new StringBuilder("Not Craftable");
					}
					
					embed.addField(rod.getName(), "Price: " + (rod.isBuyable() ? String.format("$%,d", rod.getPrice()) : "Not Buyable") + "\nCraft: " + craftContent.toString() + "\nDurability: " + rod.getDurability(), true);
				}
			}
			
			event.reply(embed.build()).queue();
		}
		
		@Command(value="buy", description="Buy a fishing rod which is listed in the fishing rod shop")
		@Examples({"fishing rod buy Platinum Rod", "fishing rod buy Gold"})
		public void buy(CommandEvent event, @Context Database database, @Argument(value="fishing rod name", endless=true) String rodName) {
			Document data = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.balance", "economy.items")).get("economy", Database.EMPTY_DOCUMENT);
			
			Rod rod = Rod.getRodByName(rodName);
			if (rod == null) {
				event.reply("I could not find that fishing rod :no_entry:").queue();
				return;
			}
			
			if (!rod.isBuyable()) {
				event.reply("That fishing rod is not buyable :no_entry:").queue();
				return;
			}
			
			List<Document> userItems = data.getList("items", Document.class, new ArrayList<>());
			if (EconomyUtils.hasRod(userItems)) {
				event.reply("You already own a fishing rod :no_entry:").queue();
				return;
			}
			
			long balance = data.get("balance", 0L);
			if (balance >= rod.getPrice()) {
				EconomyUtils.addItem(userItems, rod, 1, new Document("currentDurability", rod.getDurability()));

				Bson update = Updates.combine(
						Updates.set("economy.items", userItems),
						Updates.inc("economy.balance", -rod.getPrice())
				);
	
				database.updateUserById(event.getAuthor().getIdLong(), update, (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						event.reply("You just bought a `" + rod.getName() + "` for " + String.format("**$%,d**", rod.getPrice()) + " :ok_hand:").queue();
					}
				});
			} else {
				event.reply("You do not have enough money to purchase a `" + rod.getName() + "` :no_entry:").queue();
			}
		}
		
		@Command(value="info", description="Gives info of yours or another users fishing rod")
		@Examples({"fishing rod info", "fishing rod info @Shea#6653", "fishing rod info 402557516728369153", "fishing rod info Shea"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void info(CommandEvent event, @Context Database database, @Argument(value="user", endless=true, nullDefault=true) String userArgument) {
			Member member;
			if (userArgument == null) {
				member = event.getMember();
			} else {
				member = ArgumentUtils.getMember(event.getGuild(), userArgument);
				if (member == null) {
					event.reply("I could not find that user :no_entry:").queue();
					return;
				}
			}
			
			List<Document> items = database.getUserById(member.getIdLong(), null, Projections.include("economy.items")).getEmbedded(List.of("economy", "items"), Collections.emptyList());
			Rod rod = EconomyUtils.getUserRod(items);
			if (rod == null) {
				event.reply((member.equals(event.getMember()) ? "You do" : "That user does") + " not have a fishing rod :no_entry:").queue();
				return;
			}
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setAuthor(member.getUser().getName() + "'s " + rod.getName(), null, member.getUser().getEffectiveAvatarUrl());
			embed.setColor(member.getColor());
			embed.setThumbnail("https://emojipedia-us.s3.amazonaws.com/thumbs/120/twitter/147/fishing-pole-and-fish_1f3a3.png");
			embed.addField("Durability", rod.getCurrentDurability() + "/" + rod.getDurability(), false);
			if (rod.isBuyable()) {
				embed.addField("Current Price", String.format("$%,d", Math.round(((double) rod.getPrice() / rod.getDurability()) * rod.getCurrentDurability())), false);
				embed.addField("Price", String.format("$%,d", rod.getPrice()), false);
			}
			embed.addField("Upgrades", String.valueOf(rod.getUpgrades()), false);
			
			event.reply(embed.build()).queue();
		}
		
		@Command(value="craft", description="Craft a fishing rod which is in the fishing rod shop aslong as it displays as craftable")
		@Examples({"fishing rod craft Platinum Rod", "fishing rod craft Gold"})
		public void craft(CommandEvent event, @Context Database database, @Argument(value="fishing rod name", endless=true) String rodName) {
			Rod rod = Rod.getRodByName(rodName);
			if (rod == null) {
				event.reply("I could not find that fishing rod :no_entry:").queue();
				return;
			}
			
			if (!rod.isCraftable()) {
				event.reply("That fishing rod is not craftable :no_entry:").queue();
				return;
			}
			
			List<Document> userItems = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.items")).getEmbedded(List.of("economy", "items"), new ArrayList<>());
			if (EconomyUtils.hasRod(userItems)) {
				event.reply("You already own a fishing rod :no_entry:").queue();
				return;
			}
			
			for (ItemStack<Material> craftItem : rod.getCraftingRecipe().getCraftingMaterials()) {
				ItemStack<Item> userItem = EconomyUtils.getUserItem(userItems, craftItem.getItem());
				if (userItem.getAmount() < craftItem.getAmount()) {
					event.reply(String.format("You do not have `%,d %s` :no_entry:", craftItem.getAmount(), craftItem.getItem().getName())).queue();
					return;
				}
				
				EconomyUtils.removeItem(userItems, craftItem);
			}
			
			EconomyUtils.addItem(userItems, rod, 1, new Document("currentDurability", rod.getDurability()));
			
			database.updateUserById(event.getAuthor().getIdLong(), Updates.set("economy.items", userItems), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("You just crafted a `" + rod.getName() + "` with " + GeneralUtils.joinGrammatical(rod.getCraftingRecipe().getCraftingMaterials()) + " :ok_hand:").queue();
				}
			});
		}
		
		@Command(value="upgrade", description="Upgrade your current fishing rod, you can view the upgrades in `fishing rod upgrades`")
		@Examples({"fishing rod upgrade money", "fishing rod upgrade durability"})
		public void upgrade(CommandEvent event, @Context Database database, @Argument(value="upgrade name") String upgradeName, @Argument(value="upgrades", nullDefault=true) Integer upgradesArgument) {
			Document data = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.items", "economy.balance")).get("economy", Database.EMPTY_DOCUMENT);
			
			int upgrades = upgradesArgument == null ? 1 : upgradesArgument;
			if (upgrades < 1) {
				event.reply("You need to buy the upgrade at least once :no_entry:").queue();
				return;
			}
			
			if (upgrades > 1000) {
				event.reply("You cannot buy anymore than 1000 upgrades at one time :no_entry:").queue();
				return;
			}
			
			List<Document> items = data.getList("items", Document.class, Collections.emptyList());
			if (!EconomyUtils.hasRod(items)) {
				event.reply("You do not own a fishing rod :no_entry:").queue();
				return;
			}
			
			RodUpgrade upgrade = RodUpgrade.getRodUpgradeByName(upgradeName);
			if (upgrade == null) {
				event.reply("I could not find that upgrade, the upgrades are " + GeneralUtils.joinGrammatical(Arrays.asList(RodUpgrade.ALL)) + " :no_entry:").queue();
				return;
			}
			
			Rod rod = EconomyUtils.getUserRod(items);
			Rod defaultRod = rod.getDefaultRod();
			
			if (!rod.isBuyable()) {
				event.reply("You cannot upgrade this fishing rod :no_entry:").queue();
				return;
			}
			
			long balance = data.get("balance", 0L);
			
			int currentUpgrades = rod.getUpgrades();
			long price = 0;
			for (int i = 0; i < upgrades; i++) {
			    price += Math.round(0.015D * defaultRod.getPrice() * currentUpgrades++ + 0.025D * defaultRod.getPrice());
			}
			
			if (balance >= price) {
				Bson update = Updates.combine(
						Updates.inc("economy.items.$[rod].upgrades", upgrades),
						Updates.set("economy.items.$[rod].price", rod.getPrice() + (Math.round(defaultRod.getPrice() * 0.015D) * upgrades)),
						Updates.inc("economy.balance", -price)
				);

				if (upgrade.equals(RodUpgrade.MONEY)) {
					int increase = (int) Math.round(defaultRod.getMinimumYield() * upgrade.getIncreasePerUpgrade()) * upgrades;
					update = Updates.combine(
							update,
							Updates.set("economy.items.$[rod].minimumYield", rod.getMinimumYield() + increase),
							Updates.set("economy.items.$[rod].maximumYield", rod.getMaximumYield() + increase)
					);
				} else if (upgrade.equals(RodUpgrade.DURABILITY)) {
					update = Updates.combine(
							update, 
							Updates.set("economy.items.$[rod].maximumDurability", (int) (rod.getDurability() + (upgrade.getIncreasePerUpgrade() * upgrades))),
							Updates.inc("economy.items.$[rod].currentDurability", (int) (upgrade.getIncreasePerUpgrade() * upgrades))
					);
				}
				
				String message = String.format("You just upgraded %s %d time%s for your `%s` for **$%,d** :ok_hand:", upgrade.getName().toLowerCase(), upgrades, (upgrades == 1 ? "" : "s"), rod.getName(), price);
				
				UpdateOptions updateOptions = new UpdateOptions().arrayFilters(List.of(Filters.eq("rod.name", rod.getName()))).upsert(true);
				database.updateUserById(event.getAuthor().getIdLong(), null, update, updateOptions, (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						event.reply(message).queue();
					}
				});
			} else {
				event.reply("You cannot afford " + upgrades + " for your pickaxe it will cost you " + String.format("**$%,d**", price) + " :no_entry:").queue();
			}
		}
		
		@Command(value="upgrades", description="View all the upgrades you can put on your fishing rod and their current cost", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"fishing rod upgrades"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void upgrades(CommandEvent event, @Context Database database) {
			Document data = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.items", "economy.balance")).get("economy", Database.EMPTY_DOCUMENT);
			List<Document> items = data.getList("items", Document.class, Collections.emptyList());
			long balance = data.get("balance", 0L);
			
			Rod defaultRod = null;
			Rod rod = EconomyUtils.getUserRod(items);
			if (rod != null) {
				defaultRod = rod.getDefaultRod();
			}
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setFooter("Use fishing rod upgrade <upgrade> to apply an upgrade to your fishing rod | Balance: " + String.format("$%,d", balance), event.getAuthor().getEffectiveAvatarUrl());
			embed.setAuthor("Fishing Rod Upgrades", null, event.getSelfUser().getEffectiveAvatarUrl());
			embed.setColor(event.getMember().getColor());
			
			for (RodUpgrade upgrade : RodUpgrade.ALL) {
				embed.addField(upgrade.getName(), "Description: " + upgrade.getDescription() + (rod != null ? "\nPrice: " + String.format("$%,d", Math.round((defaultRod.getPrice() * 0.025D) + (rod.getUpgrades() * (defaultRod.getPrice() * 0.015D)))) : ""), false);
			}
			
			event.reply(embed.build()).queue();
		}
		
		@Command(value="repair", description="Repair your current fishing rod with its deticated material if it has one", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"fishing rod repair 50", "fishing rod repair"})
		public void repair(CommandEvent event, @Context Database database, @Argument(value="durability", nullDefault=true) Integer durabilityAmount) {
			List<Document> items = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.items")).getEmbedded(List.of("economy", "items"), Collections.emptyList());
			
			Rod rod = EconomyUtils.getUserRod(items);
			if (rod == null) {
				event.reply("You do not own a fishing rod :no_entry:").queue();
				return;
			}
			
			if (!rod.isRepairable()) {
				event.reply("Your fishing rod is not repairable :no_entry:").queue();
				return;
			}
			
			if (rod.getDurability() <= rod.getCurrentDurability()) {
				event.reply("Your fishing rod is already at full durability :no_entry:").queue();
				return;
			}
			
			int maxDurability = rod.getDurability() - rod.getCurrentDurability();
			
			int durabilityNeeded;
			if (durabilityAmount == null) {
				durabilityNeeded = maxDurability;
			} else {
				if (durabilityAmount > maxDurability) {
					event.reply("You can only repair your fishing rod by **" + maxDurability + "** durability :no_entry:").queue();
					return;
				}
				
				durabilityNeeded = durabilityAmount;
			}
			
			Item repairItem = rod.getRepairItem();
			ItemStack<Item> userItem = EconomyUtils.getUserItem(items, repairItem);
			int cost = rod.getAmountOfMaterialsForRepair(durabilityNeeded);
			if (userItem.getAmount() < cost) {
				long fixBy = rod.getEstimateOfDurability(userItem.getAmount());
				event.reply("You do not have enough materials to fix your fishing rod by **" + durabilityNeeded + "** durability, you would need `" + cost + " " + repairItem.getName() + "`. You can fix your fishing rod by **" + fixBy + "** durability with your current amount of `" + repairItem.getName() + "` :no_entry:").queue();
				return;
			}
			
			event.reply("It will cost you `" + cost + " " + repairItem.getName() + "` to repair your fishing rod by **" + durabilityNeeded + "** durability, are you sure you want to repair it? (Yes or No)").queue(message -> {
				PagedUtils.getConfirmation(event, 60, event.getAuthor(), confirmation -> {
					if (confirmation) {
						message.delete().queue();
						
						List<Document> itemsNew = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.items")).getEmbedded(List.of("economy", "items"), new ArrayList<>());
						
						Rod rodNew = EconomyUtils.getUserRod(itemsNew);
						if (rodNew == null) {
							event.reply("You no longer own a fishing rod :no_entry:").queue();
						}
						
						if (!rodNew.getName().equals(rod.getName())) {
							event.reply("You have changed fishing rod since you answered :no_entry:").queue();
						}
						
						if (rodNew.getCurrentDurability() != rod.getCurrentDurability()) {
							event.reply("Your fishing rod durability has changed since answering :no_entry:").queue();
							return;
						}
						
						ItemStack<Item> userItemNew = EconomyUtils.getUserItem(itemsNew, repairItem);
						if (userItemNew.getAmount() < cost) {
							long fixBy = rod.getEstimateOfDurability(userItemNew.getAmount());
							event.reply("You do not have enough materials to fix your fishing rod by **" + durabilityNeeded + "** durability, you would need `" + cost + " " + repairItem.getName() + "`. You can fix your fishing rod by **" + fixBy + "** durability with your current amount of `" + repairItem.getName() + "` :no_entry:").queue();
							return;
						}
					
						EconomyUtils.removeItem(itemsNew, repairItem, cost);
						EconomyUtils.editItem(itemsNew, rodNew, "currentDurability", rodNew.getCurrentDurability() + durabilityNeeded);

						database.updateUserById(event.getAuthor().getIdLong(), Updates.set("economy.items", itemsNew), (result, exception) -> {
							if (exception != null) {
								exception.printStackTrace();
								event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
							} else {
								event.reply("You just repaired your fishing rod by **" + durabilityNeeded + "** durability :ok_hand:").queue();
							}
						});
					} else {
						event.reply("Cancelled <:done:403285928233402378>").queue();
					}
				});
			});
		}
	}
	
	public class AxeCommand extends Sx4Command {
		
		public AxeCommand() {
			super("axe");
			
			super.setDescription("Axes allow you to gain wood by using the chop command");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
			super.setExamples("axe shop", "axe buy", "axe info");
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="shop", aliases={"list"}, description="View all the axes you can buy/craft", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"axe shop"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void shop(CommandEvent event, @Context Database database) {
			long balance = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.balance")).getEmbedded(List.of("economy", "balance"), 0L);
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setAuthor("Axe Shop", null, event.getSelfUser().getEffectiveAvatarUrl());
			embed.setColor(Settings.EMBED_COLOUR);
			embed.setDescription("Axes are a quick and easy way to gain some wood so you can craft");
			embed.setFooter(String.format("Use %saxe buy <axe> to buy a axe | Balance: $%,d", event.getPrefix(), balance), event.getAuthor().getEffectiveAvatarUrl());
			
			for (Axe axe : Axe.ALL) {
				if (axe.isBuyable() || axe.isCraftable()) {
					StringBuilder craftContent = new StringBuilder();
					if (axe.isCraftable()) {
						List<ItemStack<Material>> craftingItems = axe.getCraftingRecipe().getCraftingMaterials();
						for (int i = 0; i < craftingItems.size(); i++) {
							ItemStack<Material> itemStack = craftingItems.get(i);
							craftContent.append(itemStack.getAmount() + " " + itemStack.getItem().getName());
							if (i != craftingItems.size() - 1) {
								craftContent.append("\n");
							}
						}
					}
					
					if (craftContent.length() == 0) {
						craftContent = new StringBuilder("Not Craftable");
					}
					
					embed.addField(axe.getName(), "Price: " + (axe.isBuyable() ? String.format("$%,d", axe.getPrice()) : "Not Buyable") + "\nCraft: " + craftContent.toString() + "\nDurability: " + axe.getDurability(), true);
				}
			}
			
			event.reply(embed.build()).queue();
		}
		
		@Command(value="buy", description="Buy a axe which is listed in the axe shop")
		@Examples({"axe buy Platinum Axe", "axe buy Gold"})
		public void buy(CommandEvent event, @Context Database database, @Argument(value="axe name", endless=true) String axeName) {
			Document data = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.balance", "economy.items")).get("economy", Database.EMPTY_DOCUMENT);
			
			Axe axe = Axe.getAxeByName(axeName);
			if (axe == null) {
				event.reply("I could not find that axe :no_entry:").queue();
				return;
			}
			
			if (!axe.isBuyable()) {
				event.reply("That axe is not buyable :no_entry:").queue();
				return;
			}
			
			List<Document> userItems = data.getList("items", Document.class, new ArrayList<>());
			if (EconomyUtils.hasAxe(userItems)) {
				event.reply("You already own an axe :no_entry:").queue();
				return;
			}
			
			long balance = data.get("balance", 0L);
			if (balance >= axe.getPrice()) {
				EconomyUtils.addItem(userItems, axe, 1, new Document("currentDurability", axe.getDurability()));

				Bson update = Updates.combine(
						Updates.set("economy.items", userItems),
						Updates.inc("economy.balance", -axe.getPrice())
				);
				
				database.updateUserById(event.getAuthor().getIdLong(), update, (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						event.reply("You just bought a `" + axe.getName() + "` for " + String.format("**$%,d**", axe.getPrice()) + " :ok_hand:").queue();
					}
				});
			} else {
				event.reply("You do not have enough money to purchase a `" + axe.getName() + "` :no_entry:").queue();
			}
		}
		
		@Command(value="info", description="Gives info of yours or another users axe")
		@Examples({"axe info", "axe info @Shea#6653", "axe info 402557516728369153", "axe info Shea"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void info(CommandEvent event, @Context Database database, @Argument(value="user", endless=true, nullDefault=true) String userArgument) {
			Member member;
			if (userArgument == null) {
				member = event.getMember();
			} else {
				member = ArgumentUtils.getMember(event.getGuild(), userArgument);
				if (member == null) {
					event.reply("I could not find that user :no_entry:").queue();
					return;
				}
			}
			
			List<Document> items = database.getUserById(member.getIdLong(), null, Projections.include("economy.items")).getEmbedded(List.of("economy", "items"), Collections.emptyList());
			
			Axe axe = EconomyUtils.getUserAxe(items);
			if (axe == null) {
				event.reply((member.equals(event.getMember()) ? "You do" : "That user does") + " not have an axe :no_entry:").queue();
				return;
			}
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setAuthor(member.getUser().getName() + "'s " + axe.getName(), null, member.getUser().getEffectiveAvatarUrl());
			embed.setColor(member.getColor());
			embed.setThumbnail("https://www.shareicon.net/data/2016/09/02/823994_ax_512x512.png");
			embed.addField("Durability", axe.getCurrentDurability() + "/" + axe.getDurability(), false);
			if (axe.isBuyable()) {
				embed.addField("Current Price", String.format("$%,d", Math.round(((double) axe.getPrice() / axe.getDurability()) * axe.getCurrentDurability())), false);
				embed.addField("Price", String.format("$%,d", axe.getPrice()), false);
			}
			embed.addField("Upgrades", String.valueOf(axe.getUpgrades()), false);
			
			event.reply(embed.build()).queue();
		}
		
		@Command(value="craft", description="Craft a axe which is in the axe shop aslong as it displays as craftable")
		@Examples({"axe craft Platinum Axe", "axe craft Gold"})
		public void craft(CommandEvent event, @Context Database database, @Argument(value="axe name", endless=true) String axeName) {	
			Axe axe = Axe.getAxeByName(axeName);
			if (axe == null) {
				event.reply("I could not find that axe :no_entry:").queue();
				return;
			}
			
			if (!axe.isCraftable()) {
				event.reply("That axe is not craftable :no_entry:").queue();
				return;
			}
			
			List<Document> items = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.items")).getEmbedded(List.of("economy", "items"), new ArrayList<>());
			if (EconomyUtils.hasAxe(items)) {
				event.reply("You already own an axe :no_entry:").queue();
				return;
			}
			
			for (ItemStack<Material> craftItem : axe.getCraftingRecipe().getCraftingMaterials()) {
				ItemStack<Item> userItem = EconomyUtils.getUserItem(items, craftItem.getItem());
				if (userItem.getAmount() < craftItem.getAmount()) {
					event.reply(String.format("You do not have `%,d %s` :no_entry:", craftItem.getAmount(), craftItem.getItem().getName())).queue();
					return;
				}
				
				EconomyUtils.removeItem(items, craftItem);
			}
			
			EconomyUtils.addItem(items, axe, 1, new Document("currentDurability", axe.getDurability()));
			
			database.updateUserById(event.getAuthor().getIdLong(), Updates.set("economy.items", items), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("You just crafted a `" + axe.getName() + "` with " + GeneralUtils.joinGrammatical(axe.getCraftingRecipe().getCraftingMaterials()) + " :ok_hand:").queue();
				}
			});
		}
		
		@Command(value="upgrade", description="Upgrade your current axe, you can view the upgrades in `axe upgrades`")
		@Examples({"axe upgrade multiplier", "axe upgrade durability"})
		public void upgrade(CommandEvent event, @Context Database database, @Argument(value="upgrade name") String upgradeName, @Argument(value="upgrades", nullDefault=true) Integer upgradesArgument) {
			Document data = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.balance", "economy.items")).get("economy", Database.EMPTY_DOCUMENT);
			
			int upgrades = upgradesArgument == null ? 1 : upgradesArgument;
			if (upgrades < 1) {
				event.reply("You need to buy the upgrade at least once :no_entry:").queue();
				return;
			}
			
			if (upgrades > 1000) {
				event.reply("You cannot buy anymore than 1000 upgrades at one time :no_entry:").queue();
				return;
			}
			
			List<Document> items = data.getList("items", Document.class, Collections.emptyList());
			if (!EconomyUtils.hasAxe(items)) {
				event.reply("You do not own an axe :no_entry:").queue();
				return;
			}
			
			AxeUpgrade upgrade = AxeUpgrade.getAxeUpgradeByName(upgradeName);
			if (upgrade == null) {
				event.reply("I could not find that upgrade, the upgrades are " + GeneralUtils.joinGrammatical(Arrays.asList(AxeUpgrade.ALL)) + " :no_entry:").queue();
				return;
			}
			
			Axe axe = EconomyUtils.getUserAxe(items);
			Axe defaultAxe = axe.getDefaultAxe();
			
			if (!axe.isBuyable()) {
				event.reply("You cannot upgrade this axe :no_entry:").queue();
				return;
			}
			
			long balance = data.get("balance", 0L);
			
			int currentUpgrades = axe.getUpgrades();
			long price = 0;
			for (int i = 0; i < upgrades; i++) {
			    price += Math.round(0.015D * defaultAxe.getPrice() * currentUpgrades++ + 0.025D * defaultAxe.getPrice());
			}
			
			if (balance >= price) {
				Bson update = Updates.combine(
						Updates.inc("economy.items.$[axe].upgrades", upgrades),
						Updates.set("economy.items.$[axe].price", axe.getPrice() + (Math.round(defaultAxe.getPrice() * 0.015D) * upgrades)),
						Updates.inc("economy.balance", -price)
				);
				
				if (upgrade.equals(AxeUpgrade.DURABILITY)) {
					update = Updates.combine(
							update, 
							Updates.set("economy.items.$[axe].maximumDurability", (int) (axe.getDurability() + (upgrade.getIncreasePerUpgrade() * upgrades))),
							Updates.inc("economy.items.$[axe].currentDurability", (int) (upgrade.getIncreasePerUpgrade() * upgrades))
					);
				} else if (upgrade.equals(AxeUpgrade.MULTIPLIER)) {
					update = Updates.combine(
							update,
							Updates.set("economy.items.$[axe].multiplier", axe.getMultiplier() * Math.pow(upgrade.getIncreasePerUpgrade(), upgrades))
					);
				}
				
				String message = String.format("You just upgraded %s %d time%s for your `%s` for **$%,d** :ok_hand:", upgrade.getName().toLowerCase(), upgrades, (upgrades == 1 ? "" : "s"), axe.getName(), price);
				
				UpdateOptions updateOptions = new UpdateOptions().arrayFilters(List.of(Filters.eq("axe.name", axe.getName()))).upsert(true);
				database.updateUserById(event.getAuthor().getIdLong(), null, update, updateOptions, (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						event.reply(message).queue();
					}
				});
			} else {
				event.reply("You cannot afford " + upgrades + " for your pickaxe it will cost you " + String.format("**$%,d**", price) + " :no_entry:").queue();
			}
		}
		
		@Command(value="upgrades", description="View all the upgrades you can put on your axe and their current cost", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"axe upgrades"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void upgrades(CommandEvent event, @Context Database database) {
			Document data = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.items", "economy.balance")).get("economy", Database.EMPTY_DOCUMENT);
			long balance = data.get("balance", 0L);
			
			List<Document> items = data.getList("items", Document.class, Collections.emptyList());
			Axe axe = EconomyUtils.getUserAxe(items), defaultAxe = null;
			if (axe != null) {
				defaultAxe = axe.getDefaultAxe();
			}
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setFooter("Use axe upgrade <upgrade> to apply an upgrade to your axe | Balance: " + String.format("$%,d", balance), event.getAuthor().getEffectiveAvatarUrl());
			embed.setAuthor("Axe Upgrades", null, event.getSelfUser().getEffectiveAvatarUrl());
			embed.setColor(event.getMember().getColor());
			
			for (AxeUpgrade upgrade : AxeUpgrade.ALL) {
				embed.addField(upgrade.getName(), "Description: " + upgrade.getDescription() + (axe != null ? "\nPrice: " + String.format("$%,d", Math.round((defaultAxe.getPrice() * 0.025D) + (axe.getUpgrades() * (defaultAxe.getPrice() * 0.015D)))) : ""), false);
			}
			
			event.reply(embed.build()).queue();
		}
		
		@Command(value="repair", description="Repair your current axe with its deticated material if it has one", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"axe repair 50", "axe repair"})
		public void repair(CommandEvent event, @Context Database database, @Argument(value="durability", nullDefault=true) Integer durabilityAmount) {
			List<Document> items = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.items")).getEmbedded(List.of("economy", "items"), Collections.emptyList());
			Axe axe = EconomyUtils.getUserAxe(items);
			if (axe == null) {
				event.reply("You do not own an axe :no_entry:").queue();
				return;
			}
			
			if (!axe.isRepairable()) {
				event.reply("Your axe is not repairable :no_entry:").queue();
				return;
			}
			
			if (axe.getDurability() <= axe.getCurrentDurability()) {
				event.reply("Your axe is already at full durability :no_entry:").queue();
				return;
			}
			
			int maxDurability = axe.getDurability() - axe.getCurrentDurability();
			
			int durabilityNeeded;
			if (durabilityAmount == null) {
				durabilityNeeded = maxDurability;
			} else {
				if (durabilityAmount > maxDurability) {
					event.reply("You can only repair your axe by **" + maxDurability + "** durability :no_entry:").queue();
					return;
				}
				
				durabilityNeeded = durabilityAmount;
			}
			
			Item repairItem = axe.getRepairItem();
			ItemStack<Item> userItem = EconomyUtils.getUserItem(items, repairItem);
			int cost = axe.getAmountOfMaterialsForRepair(durabilityNeeded);
			if (userItem.getAmount() < cost) {
				long fixBy = axe.getEstimateOfDurability(userItem.getAmount());
				event.reply("You do not have enough materials to fix your axe by **" + durabilityNeeded + "** durability, you would need `" + cost + " " + repairItem.getName() + "`. You can fix your axe by **" + fixBy + "** durability with your current amount of `" + repairItem.getName() + "` :no_entry:").queue();
				return;
			}
			
			event.reply("It will cost you `" + cost + " " + repairItem.getName() + "` to repair your axe by **" + durabilityNeeded + "** durability, are you sure you want to repair it? (Yes or No)").queue(message -> {
				PagedUtils.getConfirmation(event, 60, event.getAuthor(), confirmation -> {
					if (confirmation) {
						message.delete().queue();
						
						List<Document> itemsNew = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.items")).getEmbedded(List.of("economy", "items"), new ArrayList<>());
						
						Axe axeNew = EconomyUtils.getUserAxe(itemsNew);
						if (axeNew == null) {
							event.reply("You no longer own a axe :no_entry:").queue();
						}
						
						if (!axeNew.getName().equals(axe.getName())) {
							event.reply("You have changed axe since you answered :no_entry:").queue();
						}
						
						if (axeNew.getCurrentDurability() != axe.getCurrentDurability()) {
							event.reply("Your axe durability has changed since answering :no_entry:").queue();
							return;
						}
						
						ItemStack<Item> userItemNew = EconomyUtils.getUserItem(itemsNew, repairItem);
						if (userItemNew.getAmount() < cost) {
							long fixBy = axe.getEstimateOfDurability(userItemNew.getAmount());
							event.reply("You do not have enough materials to fix your axe by **" + durabilityNeeded + "** durability, you would need `" + cost + " " + repairItem.getName() + "`. You can fix your axe by **" + fixBy + "** durability with your current amount of `" + repairItem.getName() + "` :no_entry:").queue();
							return;
						}
					
						EconomyUtils.removeItem(itemsNew, repairItem, cost);
						EconomyUtils.editItem(itemsNew, axeNew, "currentDurability", axeNew.getCurrentDurability() + durabilityNeeded);
						
						database.updateUserById(event.getAuthor().getIdLong(), Updates.set("economy.items", itemsNew),(result, exception) -> {
							if (exception != null) {
								exception.printStackTrace();
								event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
							} else {
								event.reply("You just repaired your axe by **" + durabilityNeeded + "** durability :ok_hand:").queue();
							}
						});
					} else {
						event.reply("Cancelled <:done:403285928233402378>").queue();
					}
				});
			});
		}
	}
	
	@Command(value="give", aliases={"gift"}, description="Give money to others users, there is a 5% tax per transaction")
	@Examples({"give @Shea#6653 50000", "give Shea all", "give 402557516728369153 23%"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void give(CommandEvent event, @Context Database database, @Argument(value="user") String userArgument, @Argument(value="amount") String amountArgument) {
		Member member = ArgumentUtils.getMember(event.getGuild(), userArgument);
		if (member == null) {
			event.reply("I could not find that user :no_entry:").queue();
			return;
		}
		
		boolean taxBot = member.equals(event.getSelfMember());
		
		if (member.getUser().isBot() && !taxBot) {
			event.reply("You cannot give money to bots :no_entry:").queue();
			return;
		}
		
		if (member.equals(event.getMember())) {
			event.reply("You cannot give money to yourself :no_entry:").queue();
			return;
		}
		
		Bson projection = Projections.include("economy.balance");
		long authorBalance = database.getUserById(event.getAuthor().getIdLong(), null, projection).getEmbedded(List.of("economy", "balance"), 0L);
		long userBalance = database.getUserById(member.getIdLong(), null, projection).getEmbedded(List.of("economy", "balance"), 0L);
		long fullAmount, tax, amount;
		try {
			fullAmount = EconomyUtils.convertMoneyArgument(authorBalance, amountArgument);
		} catch(IllegalArgumentException e) {
			event.reply(e.getMessage()).queue();
			return;
		}
		
		if (fullAmount > authorBalance) {
			event.reply("You do not have enough to money give " + String.format("**$%,d**", fullAmount) + " to " + member.getUser().getAsTag() + " :no_entry:").queue();
			return;
		}
		
		if (taxBot) {
			tax = fullAmount;
			amount = fullAmount;
		} else {
			tax = (long) Math.ceil(fullAmount * 0.05D);
			amount = (long) (fullAmount * 0.95D);
		}
		
		long newAuthorBalance = authorBalance - fullAmount;
		long newUserBalance = userBalance + amount;
		EmbedBuilder embed = new EmbedBuilder();
		embed.setAuthor(event.getAuthor().getName() + " → " + member.getUser().getName(), null, "https://cdn0.iconfinder.com/data/icons/social-messaging-ui-color-shapes/128/money-circle-green-3-512.png");
		embed.setColor(event.getMember().getColor());
		embed.setDescription(String.format("You have gifted **$%,d** to **%s**\n\n%s's new balance: **$%,d**\n%s's new balance: **$%,d**", amount, member.getUser().getName(), event.getAuthor().getName(), newAuthorBalance, member.getUser().getName(), newUserBalance));
		embed.setFooter(String.format("$%,d (%d%%) tax was taken", tax, Math.round((double) tax / fullAmount * 100)), null);
		
		UpdateOptions updateOptions = new UpdateOptions().upsert(true);
		List<WriteModel<Document>> bulkData = new ArrayList<>();
		bulkData.add(new UpdateOneModel<>(Filters.eq("_id", event.getAuthor().getIdLong()), Updates.inc("economy.balance", -fullAmount), updateOptions));
		bulkData.add(new UpdateOneModel<>(Filters.eq("_id", member.getIdLong()), Updates.inc("economy.balance", amount), updateOptions));
		if (!taxBot) {
			bulkData.add(new UpdateOneModel<>(Filters.eq("_id", event.getSelfUser().getIdLong()), Updates.inc("economy.balance", tax), updateOptions));
		}
		
		database.bulkWriteUsers(bulkData, (result, exception) -> {
			if (exception != null) {
				exception.printStackTrace();
				event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
			} else {
				event.reply(embed.build()).queue();
			}
		});
	}
	
	@Command(value="give materials", aliases={"givematerials", "give mats", "givemats"}, description="Give another user some materials you have, 5% the price of the materials will be taxed")
	@Examples({"give materials @Shea#6653 Coal 10", "give materials Shea Platinum"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void giveMaterials(CommandEvent event, @Context Database database, @Argument(value="user") String userArgument, @Argument(value="item", endless=true) String itemArgument) {
		Member member = ArgumentUtils.getMember(event.getGuild(), userArgument);
		if (member == null) {
			event.reply("I could not find that user :no_entry:").queue();
			return;
		}
		
		if (member.getUser().isBot()) {
			event.reply("You cannot give materials to bots :no_entry:").queue();
			return;
		}
		
		if (member.equals(event.getMember())) {
			event.reply("You cannot give materials to yourself :no_entry:").queue();
			return;
		}

		Document data = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.items", "economy.balance")).get("economy", Database.EMPTY_DOCUMENT);
		
		Pair<String, BigInteger> itemPair = EconomyUtils.getItemAndAmount(itemArgument);
		String itemName = itemPair.getLeft();
		BigInteger itemAmount = itemPair.getRight();
		
		if (itemAmount.compareTo(BigInteger.ONE) == -1) {
			event.reply("You have to give at least one item :no_entry:").queue();
			return;
		}
		
		Item item = EconomyUtils.getTradeableItem(itemName);
		if (item == null) {
			event.reply("I could not find that item :no_entry:").queue();
			return;
		}
		
		List<Document> authorItems = data.getList("items", Document.class, new ArrayList<>());
		String itemString = String.format("%,d %s", itemAmount, item.getName());
		ItemStack<Item> authorItem = EconomyUtils.getUserItem(authorItems, item);
		if (BigInteger.valueOf(authorItem.getAmount()).compareTo(itemAmount) != -1) {
			List<Document> userItems = database.getUserById(member.getIdLong(), null, Projections.include("economy.items")).getEmbedded(List.of("economy", "items"), new ArrayList<>());
			
			long itemAmountLong = itemAmount.longValue();
			ItemStack<Item> userItem = EconomyUtils.getUserItem(userItems, item);
			
			long fullPrice = item.isBuyable() ? item.getPrice() * itemAmountLong : 0;
			long tax = item.isBuyable() ? (long) (fullPrice * 0.05D) : 0;
			if (data.get("balance", 0L) < tax) {
				event.replyFormat("You cannot afford the tax for giving `%s`, you need **$%,d** :no_entry:", itemString, tax).queue();
				return;
			}
			
			long newAuthorAmount = authorItem.getAmount() - itemAmountLong;
			long newUserAmount = userItem.getAmount() + itemAmountLong;
			EmbedBuilder embed = new EmbedBuilder();
			embed.setColor(event.getMember().getColor());
			embed.setAuthor(event.getAuthor().getName() + " → " + member.getUser().getName(), null, "https://cdn0.iconfinder.com/data/icons/social-messaging-ui-color-shapes/128/money-circle-green-3-512.png");
			embed.setDescription(String.format("You have gifted **%s** to **%s**\n\n%s's new %s amount: **%,d %s**\n%s's new %s amount: **%,d %s**", itemString, member.getUser().getName(), event.getAuthor().getName(), item.getName(), newAuthorAmount, item.getName(), member.getUser().getName(), item.getName(), newUserAmount, item.getName()));
			embed.setFooter(String.format("$%,d (%d%%) tax was taken", tax, Math.round((double) tax / fullPrice * 100)), null);
			
			EconomyUtils.addItem(userItems, item, itemAmountLong);
			EconomyUtils.removeItem(authorItems, item, itemAmountLong);
			
			Bson authorUpdate = Updates.combine(Updates.set("economy.items", authorItems), Updates.inc("economy.balance", -tax));
			
			UpdateOptions updateOptions = new UpdateOptions().upsert(true);
			List<WriteModel<Document>> bulkData = List.of(
					new UpdateOneModel<>(Filters.eq("_id", event.getAuthor().getIdLong()), authorUpdate, updateOptions),
					new UpdateOneModel<>(Filters.eq("_id", member.getIdLong()), Updates.set("economy.items", userItems), updateOptions),
					new UpdateOneModel<>(Filters.eq("_id", event.getSelfUser().getIdLong()), Updates.inc("economy.balance", tax), updateOptions)
			);
			
			database.bulkWriteUsers(bulkData, (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply(embed.build()).queue();
				}
			});
		} else {
			event.reply("You do not have `" + itemString + "` to give to " + member.getUser().getAsTag() + " :no_entry:").queue();
		}
	}
	
	@Command(value="russian roulette", aliases={"rusr", "russianroulette", "roulette"}, description="Put a gun to your head and choose how many bullets go in the chamber if you're shot you lose your bet if you win you gain your winnings and cartain amount depending on how many bullets you put in the chanmber")
	@Examples({"russian roulette 3 5000", "russian roulette 2 all", "russian roulette 5 10%"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void russianRoulette(CommandEvent event, @Context Database database, @Argument(value="bullets") int bullets, @Argument(value="bet", endless=true) String betArgument) {
		if (bullets < 1 || bullets > 5) {
			event.reply("The bullet amount has to be a number between 1 and 5 :no_entry:").queue();
			return;
		}
		
		long balance = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.balance")).getEmbedded(List.of("economy", "balance"), 0L);
		long bet;
		try {
			bet = EconomyUtils.convertMoneyArgument(balance, betArgument);
		} catch(IllegalArgumentException e) {
			event.reply(e.getMessage()).queue();
			return;
		}
		
		if (balance < bet) {
			event.reply("You do not have enough money to bet " + String.format("**$%,d**", bet) + " :no_entry:").queue();
			return;
		}
		
		if (bet < 20) {
			event.reply("Your bet has to be at least **$20** :no_entry:").queue();
			return;
		}
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setAuthor(event.getAuthor().getName(), null, event.getAuthor().getEffectiveAvatarUrl());
		embed.setColor(event.getMember().getColor());
		
		int randomNumber = random.nextInt(6);
		long amountWon;
		if (bullets - 1 < randomNumber) {
			long winnings = (long) Math.ceil((5.7D * bet) / (6 - bullets));
			embed.setDescription(String.format("You're lucky, you get to live another day.\nYou won **$%,d**", winnings));
			amountWon = winnings - bet;
		} else {
			embed.setDescription(String.format("You were shot :gun:\nYou lost your bet of **$%,d**", bet));
			amountWon = -bet;
		}
		
		Bson update = Updates.combine(Updates.inc("economy.winnings", amountWon), Updates.inc("economy.balance", amountWon));
		database.updateUserById(event.getAuthor().getIdLong(), update, (result, exception) -> {
			if (exception != null) {
				exception.printStackTrace();
				event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
			} else {
				event.reply(embed.build()).queue();
			}
		});
	}
	
	public class FactoryCommand extends Sx4Command {
		
		public FactoryCommand() {
			super("factory");
			
			super.setAliases("factories");
			super.setDescription("Buy factories with materials, factories yield you money every 12 hours the more factories the more money");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
			super.setExamples("factory shop", "factory buy", "factory collect");
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="shop", aliases={"list"}, description="View all the factories you can buy with your current materials", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"factory shop"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void shop(CommandEvent event, @Context Database database) {
			List<Document> items = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.items")).getEmbedded(List.of("economy", "items"), Collections.emptyList());
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setColor(Settings.EMBED_COLOUR);
			embed.setAuthor("Factory Shop", null, event.getSelfUser().getEffectiveAvatarUrl());
			embed.setDescription("Factories are a good way to make money from materials you have gained through mining");
			embed.setFooter("Use " + event.getPrefix() + "factory buy <factory> to buy a factory", event.getAuthor().getEffectiveAvatarUrl());
			
			for (Factory factory : Factory.ALL) {
				ItemStack<Item> userItem = EconomyUtils.getUserItem(items, factory.getMaterial());
				embed.addField(factory.getName(), String.format("Price: %,d/%,d %s (%,d)", userItem.getAmount(), factory.getMaterialAmount(), factory.getMaterial().getName(), (long) Math.floor((double) userItem.getAmount() / factory.getMaterialAmount())), true);
			}
			
			event.reply(embed.build()).queue();
		}
		
		@Command(value="buy", description="Buy a factory which is listed in factory shop")
		@Examples({"factory buy Iron Factory 10", "factory buy Platinum", "factory buy all"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void buy(CommandEvent event, @Context Database database, @Argument(value="factory name", endless=true) String factoryArgument) {
			List<Document> items = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.items")).getEmbedded(List.of("economy", "items"), new ArrayList<>());
			
			if (factoryArgument.toLowerCase().equals("all")) {
				if (items.isEmpty()) {
					event.reply("You do not have enough materials to buy any factories :no_entry:").queue();
					return;
				}
				
				List<ItemStack<Factory>> factoriesBought = new ArrayList<>();
				for (Factory factory : Factory.ALL) {
					ItemStack<Item> userItem = EconomyUtils.getUserItem(items, factory.getMaterial());
					long buyableAmount = (long) Math.floor((double) userItem.getAmount() / factory.getMaterialAmount());
					if (buyableAmount > 0) {
						factoriesBought.add(new ItemStack<>(factory, buyableAmount));
						
						EconomyUtils.removeItem(items, factory.getMaterial(), factory.getMaterialAmount() * buyableAmount);
						EconomyUtils.addItem(items, factory, buyableAmount);
					}
				}
				
				if (factoriesBought.isEmpty()) {
					event.reply("You do not have enough materials to buy any factories :no_entry:").queue();
					return;
				}
				
				factoriesBought.sort((a, b) -> Long.compare(b.getAmount(), a.getAmount()));
				
				EmbedBuilder embed = new EmbedBuilder();
				embed.setColor(event.getMember().getColor());
				embed.setAuthor(event.getAuthor().getName(), null, event.getAuthor().getEffectiveAvatarUrl());
				embed.setDescription("With all your materials you have bought the following factories\n\n• " + GeneralUtils.join(factoriesBought, "\n• "));
				
				database.updateUserById(event.getAuthor().getIdLong(), Updates.set("economy.items", items), (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						event.reply(embed.build()).queue();
					}
				});
			} else {
				Pair<String, BigInteger> factoryPair = EconomyUtils.getItemAndAmount(factoryArgument);
				String factoryName = factoryPair.getLeft();
				BigInteger factoryAmount = factoryPair.getRight();
				
				if (factoryAmount.compareTo(BigInteger.ONE) == -1) {
					event.reply("You have to purchase at least one factory :no_entry:").queue();
					return;
				}
				
				Factory factory = Factory.getFactoryByName(factoryName);
				if (factory == null) {
					event.reply("I could not find that factory :no_entry:").queue();
					return;
				}
				
				ItemStack<Item> userItem = EconomyUtils.getUserItem(items, factory.getMaterial());
				
				BigInteger price = factoryAmount.multiply(BigInteger.valueOf(factory.getMaterialAmount()));
				if (BigInteger.valueOf(userItem.getAmount()).compareTo(price) != -1) {					
					EconomyUtils.removeItem(items, factory.getMaterial(), price.longValue());
					EconomyUtils.addItem(items, factory, factoryAmount.longValue());
					
					database.updateUserById(event.getAuthor().getIdLong(), Updates.set("economy.items", items), (result, exception) -> {
						if (exception != null) {
							exception.printStackTrace();
							event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
						} else {
							event.replyFormat("You just bought `%,d %s` :ok_hand:", factoryAmount, factory.getName()).queue();
						}
					});
				} else {
					event.reply(String.format("You do not have enough `%s` to buy `%,d %s` :no_entry:", factory.getMaterial().getName(), factoryAmount, factory.getName())).queue();
					return;
				}
			}
		}
		
		@Command(value="collect", description="Collect all the money from your factories you own", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"factory collect"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void collect(CommandEvent event, @Context Database database) {
			Document data = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.items", "economy.factoryCooldown")).get("economy", Database.EMPTY_DOCUMENT);
			
			long timestampNow = Clock.systemUTC().instant().getEpochSecond();
			Long factoryTime = data.getLong("factoryCooldown");
			if (factoryTime != null && timestampNow - factoryTime <= EconomyUtils.FACTORY_COOLDOWN) {
				event.reply("Slow down! You can collect from your factory in " + TimeUtils.toTimeString(factoryTime - timestampNow + EconomyUtils.FACTORY_COOLDOWN, ChronoUnit.SECONDS) + " :stopwatch:").queue();
			} else {
				long moneyGained = 0;
				StringBuilder factoryContent = new StringBuilder();
				List<Document> items = data.getList("items", Document.class, Collections.emptyList());
				for (Document item : items) {
					for (Factory factory : Factory.ALL) {
						if (item.getString("name").equals(factory.getName())) {
							long amount = item.getLong("amount");
							long moneyGainedFactory = GeneralUtils.getRandomNumber(factory.getMinimumYield(), factory.getMaximumYield()) * amount;
							moneyGained += moneyGainedFactory;
							
							factoryContent.append(String.format("• %,d %s: $%,d\n", amount, factory.getName(), moneyGainedFactory));
						}
					}
				}
				
				if (moneyGained == 0) {
					event.reply("You do not own any factories :no_entry:").queue();
					return;
				}
				
				EmbedBuilder embed = new EmbedBuilder();
				embed.setAuthor(event.getAuthor().getName(), null, event.getAuthor().getEffectiveAvatarUrl());
				embed.setColor(event.getMember().getColor());
				embed.setDescription(String.format("Your factories made you **$%,d**\n\n%s", moneyGained, factoryContent.toString()));

				database.updateUserById(event.getAuthor().getIdLong(), Updates.combine(Updates.set("economy.factoryCooldown", timestampNow), Updates.inc("economy.balance", moneyGained)), (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						event.reply(embed.build()).queue();
					}
				});
			}
		}
		
	}
	
	public class AuctionCommand extends Sx4Command {
		
		public AuctionCommand() {
			super("auction");
			
			super.setDescription("Put your items on the auction house for other users to buy");
			super.setAliases("auction house", "auctionhouse");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
			super.setExamples("auction list", "auction sell", "auction buy");
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
	
		@Command(value="list", description="View a list of all the items or a specified item on the auction house")
		@Examples({"auction list", "auction list Gold", "auction list Platinum --sort=price", "auction list --sort=name --reverse"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void list(CommandEvent event, @Context Database database, @Argument(value="item name", endless=true, nullDefault=true) String itemName, @Option(value="sort", description="Sort by the `name`, `amount`, `price` or `price-per-item` (default)") String sort, @Option(value="reverse", description="Reverses the order the items are shown in") boolean reverse) {
			List<Document> shownData;
			if (itemName != null) {
				Item item = Item.getItemByName(itemName);
				if (item == null) {
					event.reply("I could not find that item :no_entry:").queue();
					return;
				}
				
				shownData = database.getAuction().find(Filters.eq("item.name", item.getName())).into(new ArrayList<>());
			} else {
				shownData = database.getAuction().find().into(new ArrayList<>());
			}
			
			if (shownData.isEmpty()) {
				event.reply("There are no items on the auction house :no_entry:").queue();
				return;
			}
			
			List<String> itemNameEmbed = List.of("item", "name"), itemAmountEmbed = List.of("item", "amount");
			sort = sort == null ? "price-per-item" : sort;
			switch (sort.toLowerCase()) {
				case "name":
					shownData.sort((a, b) -> (reverse ? 1 : -1) * b.getEmbedded(itemNameEmbed, String.class).toLowerCase().compareTo(a.getEmbedded(itemNameEmbed, String.class).toLowerCase()));
					break;
				case "amount":
					shownData.sort((a, b) -> (reverse ? 1 : -1) * Long.compare(b.getEmbedded(itemAmountEmbed, Long.class), a.getEmbedded(itemAmountEmbed, Long.class)));
					break;
				case "price": 
					shownData.sort((a, b) -> (reverse ? 1 : -1) * Long.compare(b.getLong("price"), a.getLong("price")));
					break;
				default:
					shownData.sort((a, b) -> (reverse ? 1 : -1) * Double.compare(b.getLong("price") / b.getEmbedded(itemAmountEmbed, Long.class), a.getLong("price") / a.getEmbedded(itemAmountEmbed, Long.class)));
					break;
			}
			
			PagedResult<Document> paged = new PagedResult<>(shownData)
					.setPerPage(6)
					.setDeleteMessage(false)
					.setCustomFunction(page -> {
						List<Document> list = page.getArray();
						
						EmbedBuilder embed = new EmbedBuilder();
						embed.setAuthor("Auction List", null, event.getSelfUser().getEffectiveAvatarUrl());
						embed.setTitle("Page " + page.getCurrentPage() + "/" + page.getMaxPage());
						embed.setFooter("next | previous | go to <page_number> | cancel", null);
						embed.setColor(Settings.EMBED_COLOUR);
						
						for (int i = page.getCurrentPage() * page.getPerPage() - page.getPerPage(); i < (page.getCurrentPage() == page.getMaxPage() ? list.size() : page.getCurrentPage() * page.getPerPage()); i++) {
							Document auctionData = list.get(i);
							AuctionItem item = new AuctionItem(auctionData);
							
							String extra = "";
							if (item.isTool()) {
								extra = "\nDurability: " + item.getTool().getCurrentDurability() + "/" + item.getTool().getDurability() + "\nUpgrades: " + item.getTool().getUpgrades();
							}
							
							embed.addField(item.getItem().getName(), String.format("Price: $%,d\nPrice Per Item: $%,.2f\nAmount: %,d", item.getPrice(), item.getPricePerItem(), item.getAmount())  + extra, true);
						}
						
						return embed.build();
					});
			
			PagedUtils.getPagedResult(event, paged, 300, null);
		}
		
		@Command(value="sell", description="Put an item on the auction for the chance of it being bought by someone else")
		@Examples({"auction sell 1000 Coal 20", "auction sell 50000 Platinum Pickaxe"})
		public void sell(CommandEvent event, @Context Database database, @Argument(value="price") long price, @Argument(value="item", endless=true) String itemArgument) {
			Pair<String, BigInteger> itemPair = EconomyUtils.getItemAndAmount(itemArgument);
			String itemName = itemPair.getLeft();
			BigInteger itemAmount = itemPair.getRight();
			
			if (itemAmount.compareTo(BigInteger.ONE) == -1) {
				event.reply("You have to sell at least one item :no_entry:").queue();
				return;
			}
			
			Item item = Item.getItemByName(itemName);
			if (item == null) {
				event.reply("I could not find that item :no_entry:").queue();
				return;
			}
			
			if (item instanceof Envelope) {
				event.reply("You cannot sell envelopes on the auction :no_entry:").queue();
				return;
			}
			
			List<Document> items = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.items")).getEmbedded(List.of("economy", "items"), new ArrayList<>());
			ItemStack<Item> userItem = EconomyUtils.getUserItem(items, item);
			
			if (item.isBuyable()) {
				BigInteger itemPrice = BigInteger.valueOf(userItem.getItem().getPrice()).multiply(itemAmount);
				if (itemPrice.divide(BigInteger.valueOf(price)).compareTo(BigInteger.valueOf(20)) == 1) {
					event.reply(String.format("You have to sell this item for at least 5%% its worth (**$%,d**) :no_entry:", itemPrice.divide(BigInteger.valueOf(20)))).queue();
					return;
				}
				
				if (itemPrice.multiply(BigInteger.valueOf(5)).compareTo(BigInteger.valueOf(price)) == -1) {
					event.reply(String.format("You cannot sell this item for more than 500%% its worth (**$%,d**) :no_entry:", itemPrice.multiply(BigInteger.valueOf(5)))).queue();
					return;
				}
			}
	
			if (BigInteger.valueOf(userItem.getAmount()).compareTo(itemAmount) != -1) {		
				Document rawItem = EconomyUtils.getUserItemRaw(items, item);
				rawItem.put("amount", itemAmount.longValue());
				
				EconomyUtils.removeItem(items, item, itemAmount.longValue());
				database.updateUserById(event.getAuthor().getIdLong(), Updates.set("economy.items", items), (userResult, userException) -> {
					if (userException != null) {
						userException.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(userException)).queue();
					} else {
						database.insertAuction(event.getAuthor().getIdLong(), price, rawItem, (auctionResult, auctionException) -> {
							if (auctionException != null) {
								auctionException.printStackTrace();
								event.reply(Sx4CommandEventListener.getUserErrorMessage(auctionException)).queue();
							} else {
								event.reply(String.format("Your `%,d %s` has been put on the auction house for **$%,d** :ok_hand:", itemAmount, item.getName(), price)).queue();
							}
						});
					}
				});
			} else {
				event.reply(String.format("You do not have `%,d %s` :no_entry:", itemAmount, item.getName())).queue();
				return;
			}
		}
		
		@Command(value="buy", description="Buy an item off the auction, look up by item name or look through all items from lowest to highest price per item")
		@Examples({"auction buy", "auction buy Platinum"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void buy(CommandEvent event, @Context Database database, @Argument(value="item name", endless=true, nullDefault=true) String itemName) {
			List<Document> shownData;
			if (itemName != null) {
				Item item = Item.getItemByName(itemName);
				if (item == null) {
					event.reply("I could not find that item :no_entry:").queue();
					return;
				}
				
				shownData = database.getAuction().find(Filters.eq("item.name", item.getName())).into(new ArrayList<>());
				
				if (shownData.isEmpty()) {
					event.replyFormat("There is no `%s` on the auction house :no_entry:", item.getName()).queue();
					return;
				}
			} else {
				shownData = database.getAuction().find().into(new ArrayList<>());
				
				if (shownData.isEmpty()) {
					event.reply("There are not items on the auction house :no_entry:").queue();
					return;
				}
			}
			
			shownData.sort((a, b) -> Double.compare(b.getLong("price") / b.getEmbedded(List.of("item", "amount"), Long.class), a.getLong("price") / a.getEmbedded(List.of("item", "amount"), Long.class)));
			
			PagedResult<Document> paged = new PagedResult<>(shownData)
					.setIncreasedIndex(true)
					.setPerPage(6)
					.setSelectableByIndex(true)
					.setCustomFunction(page -> {
						List<Document> list = page.getArray();
						
						EmbedBuilder embed = new EmbedBuilder();
						embed.setAuthor("Auction List", null, event.getSelfUser().getEffectiveAvatarUrl());
						embed.setTitle("Page " + page.getCurrentPage() + "/" + page.getMaxPage());
						embed.setFooter("next | previous | go to <page_number> | cancel", null);
						embed.setColor(Settings.EMBED_COLOUR);
						
						for (int i = page.getCurrentPage() * page.getPerPage() - page.getPerPage(); i < (page.getCurrentPage() == page.getMaxPage() ? list.size() : page.getCurrentPage() * page.getPerPage()); i++) {
							Document auctionData = list.get(i);
							AuctionItem auctionItem = new AuctionItem(auctionData);
							
							String extra = "";
							if (auctionItem.isTool()) {
								extra = "\nDurability: " + auctionItem.getTool().getCurrentDurability() + "/" + auctionItem.getTool().getDurability() + "\nUpgrades: " + auctionItem.getTool().getUpgrades();
							}
							
							embed.addField((i + 1) + ": " + auctionItem.getItem().getName(), String.format("Price: $%,d\nPrice Per Item: $%,.2f\nAmount: %,d", auctionItem.getPrice(), auctionItem.getPricePerItem(), auctionItem.getAmount())  + extra, true);      
						}
						
						return embed.build();
					});
			
			PagedUtils.getPagedResult(event, paged, 60, pagedReturn -> {
				Document auction = pagedReturn.getData();
				AuctionItem auctionItem = new AuctionItem(auction);
				Document data = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.items", "economy.balance")).get("economy", Database.EMPTY_DOCUMENT);
				
				Document auctionData = database.getAuction().find(Filters.eq("_id", auctionItem.getId())).first();
				if (auctionData == null) {
					event.reply("That item has already been bought off the auction :no_entry:").queue();
					return;
				}
				
				User owner = auctionItem.getOwner();
				if (owner != null) {
					if (owner.equals(event.getAuthor())) {
						event.reply("You cannot buy your own items, however you can refund them using `" + event.getPrefix() + "auction refund` :no_entry:").queue();
						return;
					}
				}
				
				if (data.get("balance", 0L) < auctionItem.getPrice()) {
					event.reply("You do not have enough money to purchase that auction :no_entry:").queue();
					return;
				}
				
				List<Document> items = data.getList("items", Document.class, new ArrayList<>());
				if (auctionItem.isAxe()) {
					if (EconomyUtils.hasAxe(items)) {
						event.reply("You already own an axe :no_entry:").queue();
						return;
					}
				} else if (auctionItem.isRod()) {
					if (EconomyUtils.hasRod(items)) {
						event.reply("You already own a fishing rod :no_entry:").queue();
						return;
					}
				} else if (auctionItem.isPickaxe()) {
					if (EconomyUtils.hasPickaxe(items)) {
						event.reply("You already own a pickaxe :no_entry:").queue();
						return;
					} 
				}
				
				database.deleteAuction(auctionItem.getId(), (auctionResult, auctionException) -> {
					if (auctionException != null) {
						auctionException.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(auctionException)).queue();
					} else {
						EconomyUtils.addItem(items, auction.get("item", Document.class));
						
						Bson authorUpdate = Updates.combine(Updates.inc("economy.balance", -auctionItem.getPrice()), Updates.set("economy.items", items));
						UpdateOptions updateOptions = new UpdateOptions().upsert(true);
						List<WriteModel<Document>> bulkData = List.of(
								new UpdateOneModel<>(Filters.eq("_id", event.getAuthor().getIdLong()), authorUpdate, updateOptions),
								new UpdateOneModel<>(Filters.eq("_id", auctionItem.getOwnerId()), Updates.inc("economy.balance", auctionItem.getPrice()), updateOptions)
						);
						
						database.bulkWriteUsers(bulkData, (userResult, userException) -> {
							if (userException != null) {
								userException.printStackTrace();
								event.reply(Sx4CommandEventListener.getUserErrorMessage(userException)).queue();
							} else {
								event.replyFormat("You just bought `%,d %s` for **$%,d** :ok_hand:", auctionItem.getAmount(), auctionItem.getItem().getName(), auctionItem.getPrice()).queue();
								
								if (owner != null) {
									owner.openPrivateChannel().queue(channel -> {
										channel.sendMessageFormat("Your `%,d %s` was just bought for **$%,d** :tada:", auctionItem.getAmount(), auctionItem.getItem().getName(), auctionItem.getPrice()).queue();
									}, e -> {});
								}
							}
						});
					}
				});
			});
		}

		@Command(value="refund", description="Refund an item you have put on the auction")
		@Examples({"auction refund", "auction refund Platinum"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void refund(CommandEvent event, @Context Database database, @Argument(value="item name", endless=true, nullDefault=true) String itemName) {
			Bson ownerFilter = Filters.eq("ownerId", event.getAuthor().getIdLong());
			List<Document> shownData;
			if (itemName != null) {
				Item item = Item.getItemByName(itemName);
				if (item == null) {
					event.reply("I could not find that item :no_entry:").queue();
					return;
				}
				
				shownData = database.getAuction().find(Filters.and(Filters.eq("item.name", item.getName()), ownerFilter)).into(new ArrayList<>());
				
				if (shownData.isEmpty()) {
					event.replyFormat("You do not have any `%s` on the auction house", item.getName()).queue();
					return;
				}
			} else {
				shownData = database.getAuction().find(ownerFilter).into(new ArrayList<>());
				
				if (shownData.isEmpty()) {
					event.reply("You do not have any items listed on the auction house :no_entry:").queue();
					return;
				}
			}
			
			shownData.sort((a, b) -> Double.compare(b.getLong("price") / b.getEmbedded(List.of("item", "amount"), Long.class), a.getLong("price") / a.getEmbedded(List.of("item", "amount"), Long.class)));
			
			PagedResult<Document> paged = new PagedResult<>(shownData)
					.setIncreasedIndex(true)
					.setPerPage(6)
					.setSelectableByIndex(true)
					.setCustomFunction(page -> {
						List<Document> list = page.getArray();
						
						EmbedBuilder embed = new EmbedBuilder();
						embed.setAuthor("Auction List", null, event.getSelfUser().getEffectiveAvatarUrl());
						embed.setTitle("Page " + page.getCurrentPage() + "/" + page.getMaxPage());
						embed.setFooter("next | previous | go to <page_number> | cancel", null);
						embed.setColor(Settings.EMBED_COLOUR);
						
						for (int i = page.getCurrentPage() * page.getPerPage() - page.getPerPage(); i < (page.getCurrentPage() == page.getMaxPage() ? list.size() : page.getCurrentPage() * page.getPerPage()); i++) {
							Document auctionData = list.get(i);
							AuctionItem auctionItem = new AuctionItem(auctionData);
							
							String extra = "";
							if (auctionItem.isTool()) {
								extra = "\nDurability: " + auctionItem.getTool().getCurrentDurability() + "/" + auctionItem.getTool().getDurability() + "\nUpgrades: " + auctionItem.getTool().getUpgrades();
							}
							
							embed.addField((i + 1) + ": " + auctionItem.getItem().getName(), String.format("Price: $%,d\nPrice Per Item: $%,.2f\nAmount: %,d", auctionItem.getPrice(), auctionItem.getPricePerItem(), auctionItem.getAmount())  + extra, true);
						}
						
						return embed.build();
					});
			
			PagedUtils.getPagedResult(event, paged, 60, pagedReturn -> {
				Document auction = pagedReturn.getData();
				AuctionItem auctionItem = new AuctionItem(auction);
				
				Document auctionData = database.getAuction().find(Filters.eq("_id", auctionItem.getId())).first();
				if (auctionData == null) {
					event.reply("You have already refunded that item :no_entry:").queue();
					return;
				}
				
				List<Document> items = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.items")).getEmbedded(List.of("economy", "items"), new ArrayList<>());
				if (auctionItem.isAxe()) {
					if (EconomyUtils.hasAxe(items)) {
						event.reply("You already own an axe :no_entry:").queue();
						return;
					}
				} else if (auctionItem.isRod()) {
					if (EconomyUtils.hasRod(items)) {
						event.reply("You already own a fishing rod :no_entry:").queue();
						return;
					}
				} else if (auctionItem.isPickaxe()) {
					if (EconomyUtils.hasPickaxe(items)) {
						event.reply("You already own a pickaxe :no_entry:").queue();
						return;
					}
				}
				
				database.deleteAuction(auctionItem.getId(), (auctionResult, auctionException) -> {
					if (auctionException != null) {
						auctionException.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(auctionException)).queue();
					} else {
						EconomyUtils.addItem(items, auction.get("item", Document.class));
						database.updateUserById(event.getAuthor().getIdLong(), Updates.set("economy.items", items), (userResult, userException) -> {
							if (userException != null) {
								userException.printStackTrace();
								event.reply(Sx4CommandEventListener.getUserErrorMessage(userException)).queue();
							} else {
								event.reply(String.format("You just refunded your `%,d %s` :ok_hand:", auctionItem.getAmount(), auctionItem.getItem().getName())).queue();
							}
						});
					}
				});
			});
		}
		
	}
	
	@Command(value="fish", description="An easy way to start making money every 5 minutes, buy fishing rods to increase your yield per fish", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"fish"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void fish(CommandEvent event, @Context Database database) {
		Document data = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.items", "economy.fishCooldown")).get("economy", Database.EMPTY_DOCUMENT);
		
		long timestampNow = Clock.systemUTC().instant().getEpochSecond();
		Long fishTime = data.getLong("fishCooldown");
		if (fishTime != null && timestampNow - fishTime <= EconomyUtils.FISH_COOLDOWN) {
			event.reply("Slow down! You can go fishing in " + TimeUtils.toTimeString(fishTime - timestampNow + EconomyUtils.FISH_COOLDOWN, ChronoUnit.SECONDS) + " :stopwatch:").queue();
		} else {
			List<Document> items = data.getList("items", Document.class, Collections.emptyList());
			
			long money;
			boolean brokenRod = false;
			String warning = "";
			Rod userRod = EconomyUtils.getUserRod(items);
			if (userRod != null) {
				money = GeneralUtils.getRandomNumber(userRod.getMinimumYield(), userRod.getMaximumYield());
				
				if (userRod.getCurrentDurability() == 2) {
					warning = "Your fishing rod will break the next time you use it :warning:";
				} else if (userRod.getCurrentDurability() == 1) {
					warning = "Your fishing rod broke in the process";
					brokenRod = true;
				}
			} else {
				money = GeneralUtils.getRandomNumber(2, 10);
			}
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setColor(event.getMember().getColor());
			embed.setAuthor(event.getAuthor().getName(), null, event.getAuthor().getEffectiveAvatarUrl());
			embed.setDescription("You fish for 5 minutes and sell your fish! " + String.format("(**$%,d**)", money) + " :fish:\n\n" + warning);
			
			List<Bson> arrayFilters = new ArrayList<>();
			Bson update = Updates.combine(
					Updates.set("economy.fishCooldown", timestampNow),
					Updates.inc("economy.balance", money)
			);
			
			if (brokenRod) {
				update = Updates.combine(update, Updates.pull("economy.items", Filters.eq("name", userRod.getName())));
			} else {
				if (userRod != null) {
					update = Updates.combine(update, Updates.inc("economy.items.$[rod].currentDurability", -1));
					arrayFilters.add(Filters.eq("rod.name", userRod.getName()));
				}
			}
			
			UpdateOptions updateOptions = new UpdateOptions().arrayFilters(arrayFilters).upsert(true);
			database.updateUserById(event.getAuthor().getIdLong(), null, update, updateOptions, (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply(embed.build()).queue();
				}
			});
		}
	}
	
	@Command(value="chop", description="Use your axe to chop some trees down to gather wood for crafting", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"chop"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void chop(CommandEvent event, @Context Database database) {
		Document data = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.items", "economy.chopCooldown")).get("economy", Database.EMPTY_DOCUMENT);
		
		List<Document> items = data.getList("items", Document.class, new ArrayList<>());
		if (!EconomyUtils.hasAxe(items)) {
			event.reply("You do not have an axe :no_entry:").queue();
			return;
		}
		
		long timestampNow = Clock.systemUTC().instant().getEpochSecond();
		Long chopTime = data.getLong("chopCooldown");
		
		if (chopTime != null && timestampNow - chopTime <= EconomyUtils.CHOP_COOLDOWN) {
			event.reply("Slow down! You can chop down trees in " + TimeUtils.toTimeString(chopTime - timestampNow + EconomyUtils.CHOP_COOLDOWN, ChronoUnit.SECONDS) + " :stopwatch:").queue();
		} else {
			Axe userAxe = EconomyUtils.getUserAxe(items);
			String warning = "";
			boolean brokenAxe = false;
			if (userAxe.getCurrentDurability() == 2) {
				warning = "Your axe will break the next time you use it :warning:";
			} else if (userAxe.getCurrentDurability() == 1) {
				warning = "Your axe broke in the process";
				brokenAxe = true;
			}
			
			Map<Wood, Long> woodGathered = new HashMap<>();
			for (Wood wood : Wood.ALL) {
				for (int i = 0; i < userAxe.getMaximumMaterials(); i++) {
					int randomInt = random.nextInt((int) Math.ceil(wood.getChance() / userAxe.getMultiplier()) + 1);
					if (randomInt == 0) {
						woodGathered.compute(wood, (key, value) -> value != null ? ++value : 1L);
					}
				}
			}
			
			if (brokenAxe) {
				EconomyUtils.removeItem(items, userAxe, 1);
			} else {
				EconomyUtils.editItem(items, userAxe, "currentDurability", userAxe.getCurrentDurability() - 1);
			}
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setDescription("You chopped down some trees and found the following wood: ");
			embed.setColor(event.getMember().getColor());
			embed.setAuthor(event.getAuthor().getName(), null, event.getAuthor().getEffectiveAvatarUrl());
			if (!woodGathered.isEmpty()) {
				List<Wood> keys = new ArrayList<>(woodGathered.keySet());
				keys.sort((a, b) -> Long.compare(woodGathered.get(b), woodGathered.get(a)));
				for (int i = 0; i < keys.size(); i++) {
					Wood key = keys.get(i);
					long amount = woodGathered.get(key);
					
					embed.appendDescription(String.format("%,dx %s", amount, key.getName()));
					if (i != keys.size() - 1) {
						embed.appendDescription(", ");
					} else {
						embed.appendDescription("\n\n");
					}
					
					EconomyUtils.addItem(items, key, amount);
				}
			} else {
				embed.appendDescription("Absolutely nothing\n\n");
			}
			
			embed.appendDescription(warning);
			
			Bson update = Updates.combine(Updates.set("economy.chopCooldown", timestampNow), Updates.set("economy.items", items));
			database.updateUserById(event.getAuthor().getIdLong(), update, (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply(embed.build()).queue();
				}
			});
		}
	}
	
	@Command(value="mine", description="Use your pickaxe to mine, mining will gather money aswell as the chance to get some materials", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"mine"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void mine(CommandEvent event, @Context Database database) {
		Document data = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.items", "economy.mineCooldown")).get("economy", Database.EMPTY_DOCUMENT);
		
		List<Document> items = data.getList("items", Document.class, new ArrayList<>());
		if (!EconomyUtils.hasPickaxe(items)) {
			event.reply("You do not have a pickaxe :no_entry:").queue();
			return;
		}
		
		long timestampNow = Clock.systemUTC().instant().getEpochSecond();
		Long mineTime = data.getLong("mineCooldown");
		
		if (mineTime != null && timestampNow - mineTime <= EconomyUtils.MINE_COOLDOWN) {
			event.reply("Slow down! You can go mining in " + TimeUtils.toTimeString(mineTime - timestampNow + EconomyUtils.MINE_COOLDOWN, ChronoUnit.SECONDS) + " :stopwatch:").queue();
		} else {
			Pickaxe userPickaxe = EconomyUtils.getUserPickaxe(items);
			
			String warning = "";
			boolean brokenPickaxe = false;
			if (userPickaxe.getCurrentDurability() == 2) {
				warning = "Your pickaxe will break the next time you use it :warning:";
			} else if (userPickaxe.getCurrentDurability() == 1) {
				warning = "Your pickaxe broke in the process";
				brokenPickaxe = true;
			}
			
			long money = GeneralUtils.getRandomNumber(userPickaxe.getMinimumYield(), userPickaxe.getMaximumYield());
			
			if (brokenPickaxe) {
				EconomyUtils.removeItem(items, userPickaxe, 1);
			} else {
				EconomyUtils.editItem(items, userPickaxe, "currentDurability", userPickaxe.getCurrentDurability() - 1);
			}
			
			StringBuilder materialContent = new StringBuilder();
			for (Material material : Material.ALL) {
				if (!material.isHidden()) {
					if (random.nextInt((int) Math.ceil(material.getChance() / userPickaxe.getMultiplier()) + 1) == 0) {
						materialContent.append(material.getName() + material.getEmote() + ", ");
						
						EconomyUtils.addItem(items, material, 1);
					}
				}
			}
			
			if (materialContent.length() > 0) {
				materialContent.setLength(materialContent.length() - 2);
			} else {
				materialContent.append("Absolutely nothing");
			}
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setColor(event.getMember().getColor());
			embed.setAuthor(event.getAuthor().getName(), null, event.getAuthor().getEffectiveAvatarUrl());
			embed.setDescription(String.format("You mined resources and made **$%,d** :pick:\nMaterials found: %s\n\n%s", money, materialContent.toString(), warning));
			
			Bson update = Updates.combine(Updates.set("economy.mineCooldown", timestampNow), Updates.inc("economy.balance", money), Updates.set("economy.items", items));
			database.updateUserById(event.getAuthor().getIdLong(), update, (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply(embed.build()).queue();
				}
			});
		}
	}
	
	@Command(value="items", aliases={"inventory", "inv"}, description="View all the items you currently have")
	@Examples({"items", "items @Shea#6653", "items 402557516728369153", "items Shea"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void items(CommandEvent event, @Context Database database, @Argument(value="user", endless=true, nullDefault=true) String userArgument) {
		Member member;
		if (userArgument == null) {
			member = event.getMember();
		} else {
			member = ArgumentUtils.getMember(event.getGuild(), userArgument);
			if (member == null) {
				event.reply("I could not find that user :no_entry:").queue();
				return;
			}
		}
		
		Document data = database.getUserById(member.getIdLong(), null, Projections.include("economy.items", "economy.balance")).get("economy", Database.EMPTY_DOCUMENT);
		
		List<Document> items = data.getList("items", Document.class, Collections.emptyList());
		if (items.isEmpty()) {
			event.reply((member.equals(event.getMember()) ? "You do not" : "That user does not") + " have any items :no_entry:").queue();
			return;
		}
		
		items.sort((a, b) -> Long.compare(b.getLong("amount"), a.getLong("amount"))); 

		
		Map<String, List<String>> userItems = new HashMap<>();
		userItems.put("Materials", new ArrayList<>());
		userItems.put("Tools", new ArrayList<>());
		userItems.put("Boosters", new ArrayList<>());
		userItems.put("Miners", new ArrayList<>());
		userItems.put("Factories", new ArrayList<>());
		userItems.put("Crates", new ArrayList<>());
		userItems.put("Wood", new ArrayList<>());
		userItems.put("Envelopes", new ArrayList<>());
		for (Document item : items) {
			Item actualItem = Item.getItemByName(item.getString("name"));
			ItemStack<Item> userItem = new ItemStack<>(actualItem, item.getLong("amount"));
			
			if (actualItem instanceof Tool) {
				userItems.get("Tools").add(String.format("%s x%,d (%,d Durability)", actualItem.getName(), userItem.getAmount(), item.getInteger("currentDurability")));
			} else if (actualItem instanceof Miner) {
				userItems.get("Miners").add(String.format("%s x%,d", actualItem.getName(), userItem.getAmount()));
			} else if (actualItem instanceof Booster) {
				userItems.get("Boosters").add(String.format("%s x%,d", actualItem.getName(), userItem.getAmount()));
			} else if (actualItem instanceof Wood) {
				userItems.get("Wood").add(String.format("%s x%,d", actualItem.getName(), userItem.getAmount()));
			} else if (actualItem instanceof Material) {
				userItems.get("Materials").add(String.format("%s x%,d", actualItem.getName(), userItem.getAmount()));
			} else if (actualItem instanceof Factory) {
				userItems.get("Factories").add(String.format("%s x%,d", actualItem.getName(), userItem.getAmount()));
			} else if (actualItem instanceof Crate) {
				userItems.get("Crates").add(String.format("%s x%,d", actualItem.getName(), userItem.getAmount()));
			} else if (actualItem instanceof Envelope) {
				userItems.get("Envelopes").add(String.format("%s x%,d", actualItem.getName(), userItem.getAmount()));
			}
		}
		
		List<String> keys = new ArrayList<>(userItems.keySet());
		keys.sort((a, b) -> Integer.compare(userItems.get(a).size(), userItems.get(b).size()));
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setAuthor(member.getUser().getName() + "'s Items", null, member.getUser().getEffectiveAvatarUrl());
		embed.setColor(member.getColor());
		embed.setFooter("If a category isn't shown it means you have no items in that category | Balance: " + String.format("$%,d", data.get("balance", 0L)), null);
		for (String key : keys) {
			List<String> list = userItems.get(key);
			if (!list.isEmpty()) {
				embed.addField(key, String.join("\n", list), true);
			}
		}
		
		event.reply(embed.build()).queue();
	}
	
	@Command(value="slot", aliases={"slots"}, description="Bet your money on the slots if you get 3 in a row you win, the rarer the 3 items the more the payout")
	@Examples({"slot", "slot 5000", "slot all", "slot 12%"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void slot(CommandEvent event, @Context Database database, @Argument(value="bet", endless=true, nullDefault=true) String betArgument) {
		long bet = 0;
		if (betArgument != null) {
			long balance = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("economy.balance")).getEmbedded(List.of("economy", "balance"), 0L);

			try {
				bet = EconomyUtils.convertMoneyArgument(balance, betArgument);
			} catch(IllegalArgumentException e) {
				event.reply(e.getMessage()).queue();
				return;
			}
			
			if (bet > balance) {
				event.reply("You do not have that much money to bet :no_entry:").queue();
				return;
			}
		}
		
		Slot[] slots = new Slot[3];
		for (int i = 0; i < 3; i++) {
			double randomNumber = Math.random();
			double total = 0;
			for (Slot slot : Slot.values()) {
				double chanceDouble = (double) slot.getChance() / Slot.getTotal();
				if (randomNumber >= total && randomNumber < total + chanceDouble) {
					slots[i] = slot;
					break;
				}
				
				total += chanceDouble;
			}
		}
		
		Slot firstSlot = slots[0];
		Slot secondSlot = slots[1];
		Slot thirdSlot = slots[2];
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setAuthor("🎰 Slot Machine 🎰");
		embed.setFooter(event.getAuthor().getAsTag(), event.getAuthor().getEffectiveAvatarUrl());
		embed.setThumbnail("https://images.emojiterra.com/twitter/512px/1f3b0.png");
		embed.setDescription(firstSlot.getAbove().getMaterial().getEmote() + secondSlot.getAbove().getMaterial().getEmote() + thirdSlot.getAbove().getMaterial().getEmote() + "\n" +
				firstSlot.getMaterial().getEmote() + secondSlot.getMaterial().getEmote() + thirdSlot.getMaterial().getEmote() + "\n" +
				firstSlot.getBelow().getMaterial().getEmote() + secondSlot.getBelow().getMaterial().getEmote() + thirdSlot.getBelow().getMaterial().getEmote() + "\n\n");
		
		long actualWinnings = 0;
		if (firstSlot.equals(secondSlot) && firstSlot.equals(thirdSlot)) {
			if (betArgument == null) {
				embed.appendDescription(String.format("You would have won **x%,.2f** your bet!", firstSlot.getMultiplier()));
			} else {
				long winnings = (long) Math.round(bet * firstSlot.getMultiplier());
				actualWinnings = winnings - bet;
				
				embed.appendDescription(String.format("You won **$%,d**", winnings));
			}
		} else {
			if (betArgument == null) {
				embed.appendDescription("You would have won nothing!");
			} else {
				actualWinnings = -bet;
				
				embed.appendDescription(String.format("You lost **$%,d**!", bet));
			}
		}
		
		if (actualWinnings != 0) {
			Bson update = Updates.combine(Updates.inc("economy.winnings", actualWinnings), Updates.inc("economy.balance", actualWinnings));
			database.updateUserById(event.getAuthor().getIdLong(), update, (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply(embed.build()).queue();
				}
			});
		} else {
			event.reply(embed.build()).queue();
		}
	}
	
	public class LeaderboardCommand extends Sx4Command {
		
		public LeaderboardCommand() {
			super("leaderboard");

			super.setAliases("lb", "ranks", "rank");
			super.setDescription("View the leaderboards for the economy");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
			super.setExamples("leaderboard bank", "leaderboard networth", "leaderboard winnings");
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="bank", aliases={"money", "balance"}, description="View the leaderboard for people with the most money in their balance", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"leaderboard bank", "leaderboard bank --server", "leaderboard bank --reverse", "leaderboard bank --sort=name --reverse"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void bank(CommandEvent event, @Context Database database, @Option(value="server", /*aliases={"guild"},*/ description="Filters the leaderboard so only people in the current server are shown") boolean guild, @Option(value="sort", description="Sort the leaderboard by `name` or `balance` (default)") String sort, @Option(value="reverse", description="Reverses the sorting order") boolean reverse) {
			FindIterable<Document> data = database.getUsers().find(Filters.and(Filters.exists("economy.balance"), Filters.ne("economy.balance", 0))).projection(Projections.include("economy.balance"));

			List<Document> compressedData = new ArrayList<>();
			for (Document dataObject : data) {
				User user;
				if (guild) {
					Member member = event.getGuild().getMemberById(dataObject.getLong("_id"));
					user = member == null ? null : member.getUser();
				} else {
					user = event.getShardManager().getUserById(dataObject.getLong("_id"));
				}

				if (user == null) {
					continue;
				}

				Document dataDocument = new Document("user", user)
						.append("balance", dataObject.getEmbedded(List.of("economy", "balance"), Long.class));

				compressedData.add(dataDocument);
			}

			sort = sort == null ? "balance" : sort;
			switch (sort.toLowerCase()) {
				case "name":
					compressedData.sort((a, b) -> (reverse ? 1 : -1) * b.get("user", User.class).getName().compareTo(a.get("user", User.class).getName()));
					break;
				default:
					compressedData.sort((a, b) -> (reverse ? 1 : -1) * Long.compare(a.getLong("balance"), b.getLong("balance")));
					break;
			}

			PagedResult<Document> paged = new PagedResult<>(compressedData)
					.setDeleteMessage(false)
					.setCustomFunction(page -> {
						Integer index = null;
						for (int i = 0; i < compressedData.size(); i++) {
							Document userData = compressedData.get(i);
							if (userData.get("user", User.class).equals(event.getAuthor())) {
								index = i + 1;
							}
						}

						EmbedBuilder embed = new EmbedBuilder();
						embed.setColor(Settings.EMBED_COLOUR);
						embed.setTitle("Bank Leaderboard");
						embed.setFooter(event.getAuthor().getName() + "'s Rank: " + (index == null ? "Unranked" : GeneralUtils.getNumberSuffix(index)) + " | Page " + page.getCurrentPage() + "/" + page.getMaxPage(), event.getAuthor().getEffectiveAvatarUrl());

						for (int i = page.getCurrentPage() * page.getPerPage() - page.getPerPage(); i < (page.getCurrentPage() == page.getMaxPage() ? compressedData.size() : page.getCurrentPage() * page.getPerPage()); i++) {
							Document userData = compressedData.get(i);
							embed.appendDescription(String.format("%d. `%s` - $%,d\n", i + 1, userData.get("user", User.class).getAsTag(), userData.getLong("balance")));
						}

						return embed.build();
					});

			PagedUtils.getPagedResult(event, paged, 300, null);
		}

		@Command(value="networth", description="View the leaderboard for people with the most networth (All items worth + their balance)", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"leaderboard networth", "leaderboard networth --server", "leaderboard networth --reverse", "leaderboard networth --sort=name --reverse"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void networth(CommandEvent event, @Context Database database, @Option(value="server", /*aliases={"guild"},*/ description="Filters the leaderboard so only people in the current server are shown") boolean guild, @Option(value="sort", description="Sort the leaderboard by `name` or `networth` (default)") String sort, @Option(value="reverse", description="Reverses the sorting order") boolean reverse) {
			FindIterable<Document> data = database.getUsers().find().projection(Projections.include("economy.balance", "economy.items"));

			List<Document> compressedData = new ArrayList<>();
			for (Document dataObject : data) {
				long networth = EconomyUtils.getUserNetworth(dataObject.get("economy", Database.EMPTY_DOCUMENT));
				if (networth == 0) {
					continue;
				}

				User user;
				if (guild) {
					Member member = event.getGuild().getMemberById(dataObject.getLong("_id"));
					user = member == null ? null : member.getUser();
				} else {
					user = event.getShardManager().getUserById(dataObject.getLong("_id"));
				}

				if (user == null) {
					continue;
				}

				Document dataDocument = new Document("user", user)
						.append("networth", networth);

				compressedData.add(dataDocument);
			}

			sort = sort == null ? "networth" : sort;
			switch (sort.toLowerCase()) {
				case "name":
					compressedData.sort((a, b) -> (reverse ? 1 : -1) * b.get("user", User.class).getName().compareTo(a.get("user", User.class).getName()));
					break;
				default:
					compressedData.sort((a, b) -> (reverse ? 1 : -1) * Long.compare(a.getLong("networth"), b.getLong("networth")));
					break;
			}

			PagedResult<Document> paged = new PagedResult<>(compressedData)
					.setDeleteMessage(false)
					.setCustomFunction(page -> {
						Integer index = null;
						for (int i = 0; i < compressedData.size(); i++) {
							Document userData = compressedData.get(i);
							if (userData.get("user", User.class).equals(event.getAuthor())) {
								index = i + 1;
							}
						}

						EmbedBuilder embed = new EmbedBuilder();
						embed.setColor(Settings.EMBED_COLOUR);
						embed.setTitle("Networth Leaderboard");
						embed.setFooter(event.getAuthor().getName() + "'s Rank: " + (index == null ? "Unranked" : GeneralUtils.getNumberSuffix(index)) + " | Page " + page.getCurrentPage() + "/" + page.getMaxPage(), event.getAuthor().getEffectiveAvatarUrl());

						for (int i = page.getCurrentPage() * page.getPerPage() - page.getPerPage(); i < (page.getCurrentPage() == page.getMaxPage() ? compressedData.size() : page.getCurrentPage() * page.getPerPage()); i++) {
							Document userData = compressedData.get(i);
							embed.appendDescription(String.format("%d. `%s` - $%,d\n", i + 1, userData.get("user", User.class).getAsTag(), userData.getLong("networth")));
						}

						return embed.build();
					});

			PagedUtils.getPagedResult(event, paged, 300, null);
		}

		@Command(value="winnings", description="View the leaderboard for people with the highest winnings", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"leaderboard winnings", "leaderboard winnings --server", "leaderboard winnings --reverse", "leaderboard winnings --sort=name --reverse"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void winnings(CommandEvent event, @Context Database database, @Option(value="server", /*aliases={"guild"},*/ description="Filters the leaderboard so only people in the current server are shown") boolean guild, @Option(value="sort", description="Sort the leaderboard by `name` or `winnings` (default)") String sort, @Option(value="reverse", description="Reverses the sorting order") boolean reverse) {
			FindIterable<Document> data = database.getUsers().find(Filters.and(Filters.exists("economy.winnings"), Filters.ne("economy.winnings", 0))).projection(Projections.include("economy.winnings"));

			List<Document> compressedData = new ArrayList<>();
			for (Document dataObject : data) {
				User user;
				if (guild) {
					Member member = event.getGuild().getMemberById(dataObject.getLong("_id"));
					user = member == null ? null : member.getUser();
				} else {
					user = event.getShardManager().getUserById(dataObject.getLong("_id"));
				}

				if (user == null) {
					continue;
				}

				Document dataDocument = new Document("user", user)
						.append("winnings", dataObject.getEmbedded(List.of("economy", "winnings"), Long.class));

				compressedData.add(dataDocument);
			}

			sort = sort == null ? "winnings" : sort;
			switch (sort.toLowerCase()) {
				case "name":
					compressedData.sort((a, b) -> (reverse ? 1 : -1) * b.get("user", User.class).getName().compareTo(a.get("user", User.class).getName()));
					break;
				default:
					compressedData.sort((a, b) -> (reverse ? 1 : -1) * Long.compare(a.getLong("winnings"), b.getLong("winnings")));
					break;
			}

			PagedResult<Document> paged = new PagedResult<>(compressedData)
					.setDeleteMessage(false)
					.setCustomFunction(page -> {
						Integer index = null;
						for (int i = 0; i < compressedData.size(); i++) {
							Document userData = compressedData.get(i);
							if (userData.get("user", User.class).equals(event.getAuthor())) {
								index = i + 1;
							}
						}

						EmbedBuilder embed = new EmbedBuilder();
						embed.setColor(Settings.EMBED_COLOUR);
						embed.setTitle("Winnings Leaderboard");
						embed.setFooter(event.getAuthor().getName() + "'s Rank: " + (index == null ? "Unranked" : GeneralUtils.getNumberSuffix(index)) + " | Page " + page.getCurrentPage() + "/" + page.getMaxPage(), event.getAuthor().getEffectiveAvatarUrl());

						for (int i = page.getCurrentPage() * page.getPerPage() - page.getPerPage(); i < (page.getCurrentPage() == page.getMaxPage() ? compressedData.size() : page.getCurrentPage() * page.getPerPage()); i++) {
							Document userData = compressedData.get(i);
							embed.appendDescription(String.format("%d. `%s` - $%,d\n", i + 1, userData.get("user", User.class).getAsTag(), userData.getLong("winnings")));
						}

						return embed.build();
					});

			PagedUtils.getPagedResult(event, paged, 300, null);
		}

		@Command(value="items", aliases={"item"}, description="View the leaderboard for the people with the most of a specific item")
		@Examples({"leaderboard items Platinum", "leaderboard items Diamond --server", "leaderboard items Gold --reverse", "leaderboard items Shoe --sort=name --reverse"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void items(CommandEvent event, @Context Database database, @Argument(value="item name", endless=true) String itemName, @Option(value="server", /*aliases={"guild"},*/ description="Filters the leaderboard so only people in the current server are shown") boolean guild, @Option(value="sort", description="Sort the leaderboard by `name` or `items` (default)") String sort, @Option(value="reverse", description="Reverses the sorting order") boolean reverse) {
			Item item = Item.getItemByName(itemName);
			if (item == null) {
				event.reply("I could not find that item :no_entry:").queue();
				return;
			}

			FindIterable<Document> data = database.getUsers().find().projection(Projections.include("economy.items"));

			List<Document> compressedData = new ArrayList<>();
			for (Document dataObject : data) {
				List<Document> userItems = dataObject.getEmbedded(List.of("economy", "items"), Collections.emptyList());

				ItemStack<Item> userItem = EconomyUtils.getUserItem(userItems, item);
				if (userItem.getAmount() == 0) {
					continue;
				}

				User user;
				if (guild) {
					Member member = event.getGuild().getMemberById(dataObject.getLong("_id"));
					user = member == null ? null : member.getUser();
				} else {
					user = event.getShardManager().getUserById(dataObject.getLong("_id"));
				}

				if (user == null) {
					continue;
				}

				Document dataDocument = new Document("user", user)
						.append("itemAmount", userItem.getAmount());

				compressedData.add(dataDocument);
			}

			sort = sort == null ? "items" : sort;
			switch (sort.toLowerCase()) {
				case "name":
					compressedData.sort((a, b) -> (reverse ? 1 : -1) * b.get("user", User.class).getName().compareTo(a.get("user", User.class).getName()));
					break;
				default:
					compressedData.sort((a, b) -> (reverse ? 1 : -1) * Long.compare(a.getLong("itemAmount"), b.getLong("itemAmount")));
					break;
			}

			PagedResult<Document> paged = new PagedResult<>(compressedData)
					.setDeleteMessage(false)
					.setCustomFunction(page -> {
						Integer index = null;
						for (int i = 0; i < compressedData.size(); i++) {
							Document userData = compressedData.get(i);
							if (userData.get("user", User.class).equals(event.getAuthor())) {
								index = i + 1;
							}
						}

						EmbedBuilder embed = new EmbedBuilder();
						embed.setColor(Settings.EMBED_COLOUR);
						embed.setTitle(item.getName() + " Leaderboard");
						embed.setFooter(event.getAuthor().getName() + "'s Rank: " + (index == null ? "Unranked" : GeneralUtils.getNumberSuffix(index)) + " | Page " + page.getCurrentPage() + "/" + page.getMaxPage(), event.getAuthor().getEffectiveAvatarUrl());

						for (int i = page.getCurrentPage() * page.getPerPage() - page.getPerPage(); i < (page.getCurrentPage() == page.getMaxPage() ? compressedData.size() : page.getCurrentPage() * page.getPerPage()); i++) {
							Document userData = compressedData.get(i);
							embed.appendDescription(String.format("%d. `%s` - %,d %s\n", i + 1, userData.get("user", User.class).getAsTag(), userData.getLong("itemAmount"), item.getName()));
						}

						return embed.build();
					});

			PagedUtils.getPagedResult(event, paged, 300, null);
		}

		@Command(value="reputation", aliases={"rep", "reps"}, description="View the leaderboard for people with the highest reputation", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"leaderboard reputation", "leaderboard repuatation --server", "leaderboard reputation --reverse", "leaderboard reputation --sort=name --reverse"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void reputation(CommandEvent event, @Context Database database, @Option(value="server", /*aliases={"guild"},*/ description="Filters the leaderboard so only people in the current server are shown") boolean guild, @Option(value="sort", description="Sort the leaderboard by `name` or `reputation` (default)") String sort, @Option(value="reverse", description="Reverses the sorting order") boolean reverse) {
			FindIterable<Document> data = database.getUsers().find(Filters.and(Filters.exists("reputation.amount"), Filters.ne("reputation.amount", 0))).projection(Projections.include("reputation.amount"));

			List<Document> compressedData = new ArrayList<>();
			for (Document dataObject : data) {
				User user;
				if (guild) {
					Member member = event.getGuild().getMemberById(dataObject.getLong("_id"));
					user = member == null ? null : member.getUser();
				} else {
					user = event.getShardManager().getUserById(dataObject.getLong("_id"));
				}

				if (user == null) {
					continue;
				}

				Document dataDocument = new Document("user", user)
						.append("reputation", dataObject.getEmbedded(List.of("reputation", "amount"), Integer.class));

				compressedData.add(dataDocument);
			}

			sort = sort == null ? "reputation" : sort;
			switch (sort.toLowerCase()) {
				case "name":
					compressedData.sort((a, b) -> (reverse ? 1 : -1) * b.get("user", User.class).getName().compareTo(a.get("user", User.class).getName()));
					break;
				default:
					compressedData.sort((a, b) -> (reverse ? 1 : -1) * Integer.compare(a.getInteger("reputation"), b.getInteger("reputation")));
					break;
			}

			PagedResult<Document> paged = new PagedResult<>(compressedData)
					.setDeleteMessage(false)
					.setCustomFunction(page -> {
						Integer index = null;
						for (int i = 0; i < compressedData.size(); i++) {
							Document userData = compressedData.get(i);
							if (userData.get("user", User.class).equals(event.getAuthor())) {
								index = i + 1;
							}
						}

						EmbedBuilder embed = new EmbedBuilder();
						embed.setColor(Settings.EMBED_COLOUR);
						embed.setTitle("Reputation Leaderboard");
						embed.setFooter(event.getAuthor().getName() + "'s Rank: " + (index == null ? "Unranked" : GeneralUtils.getNumberSuffix(index)) + " | Page " + page.getCurrentPage() + "/" + page.getMaxPage(), event.getAuthor().getEffectiveAvatarUrl());

						for (int i = page.getCurrentPage() * page.getPerPage() - page.getPerPage(); i < (page.getCurrentPage() == page.getMaxPage() ? compressedData.size() : page.getCurrentPage() * page.getPerPage()); i++) {
							Document userData = compressedData.get(i);
							embed.appendDescription(String.format("%d. `%s` - %,d reputation\n", i + 1, userData.get("user", User.class).getAsTag(), userData.getInteger("reputation")));
						}

						return embed.build();
					});

			PagedUtils.getPagedResult(event, paged, 300, null);
		}

		@Command(value="streak", description="View the leaderboard for people who have the highest streak for using `daily`", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"leaderboard streak", "leaderboard streak --server", "leaderboard streak --reverse", "leaderboard streak --sort=name --reverse"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void streak(CommandEvent event, @Context Database database, @Option(value="server", /*aliases={"guild"},*/ description="Filters the leaderboard so only people in the current server are shown") boolean guild, @Option(value="sort", description="Sort the leaderboard by `name` or `streak` (default)") String sort, @Option(value="reverse", description="Reverses the sorting order") boolean reverse) {
			FindIterable<Document> data = database.getUsers().find(Filters.and(Filters.exists("economy.streak"), Filters.ne("economy.streak", 0))).projection(Projections.include("economy.streak"));

			List<Document> compressedData = new ArrayList<>();
			for (Document dataObject : data) {
				User user;
				if (guild) {
					Member member = event.getGuild().getMemberById(dataObject.getLong("_id"));
					user = member == null ? null : member.getUser();
				} else {
					user = event.getShardManager().getUserById(dataObject.getLong("_id"));
				}

				if (user == null) {
					continue;
				}

				Document dataDocument = new Document("user", user)
						.append("streak", dataObject.getEmbedded(List.of("economy", "streak"), Integer.class));

				compressedData.add(dataDocument);
			}

			sort = sort == null ? "streak" : sort;
			switch (sort.toLowerCase()) {
				case "name":
					compressedData.sort((a, b) -> (reverse ? 1 : -1) * b.get("user", User.class).getName().compareTo(a.get("user", User.class).getName()));
					break;
				default:
					compressedData.sort((a, b) -> (reverse ? 1 : -1) * Long.compare(a.getInteger("streak"), b.getInteger("streak")));
					break;
			}

			PagedResult<Document> paged = new PagedResult<>(compressedData)
					.setDeleteMessage(false)
					.setCustomFunction(page -> {
						Integer index = null;
						for (int i = 0; i < compressedData.size(); i++) {
							Document userData = compressedData.get(i);
							if (userData.get("user", User.class).equals(event.getAuthor())) {
								index = i + 1;
							}
						}

						EmbedBuilder embed = new EmbedBuilder();
						embed.setColor(Settings.EMBED_COLOUR);
						embed.setTitle("Streak Leaderboard");
						embed.setFooter(event.getAuthor().getName() + "'s Rank: " + (index == null ? "Unranked" : GeneralUtils.getNumberSuffix(index)) + " | Page " + page.getCurrentPage() + "/" + page.getMaxPage(), event.getAuthor().getEffectiveAvatarUrl());

						for (int i = page.getCurrentPage() * page.getPerPage() - page.getPerPage(); i < (page.getCurrentPage() == page.getMaxPage() ? compressedData.size() : page.getCurrentPage() * page.getPerPage()); i++) {
							Document userData = compressedData.get(i);
							embed.appendDescription(String.format("%d. `%s` - %,d day streak\n", i + 1, userData.get("user", User.class).getAsTag(), userData.getInteger("streak")));
						}

						return embed.build();
					});

			PagedUtils.getPagedResult(event, paged, 300, null);
		}

		@Command(value="votes", aliases={"vote"}, description="View the leaderboard for the highest votes of the month/all time")
		@Examples({"leaderboard votes", "leaderboard votes --all", "leaderboard votes --all --server"})
		public void votes(CommandEvent event, @Argument(value="month", nullDefault=true) String monthArgument, @Option(value="all", description="Displays the leaderboard for votes all time") boolean all, @Option(value="server", /*aliases={"guild"},*/ description="Filters the leaderboard so only people in the current server are shown") boolean guild) {
			LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
			Month month;
			if (monthArgument == null) {
				month = now.getMonth();
			} else {
				try {
					month = ArgumentUtils.getMonthValue(monthArgument);
				} catch(IllegalArgumentException e) {
					event.reply(e.getMessage()).queue();
					return;
				}
			}
			
			int year = month.getValue() > now.getMonthValue() ? now.getYear() - 1 : now.getYear();
			
			Request requestSx4 = new Request.Builder().url("http://" + Settings.LOCAL_HOST + ":8080/440996323156819968/votes" + (all ? "?ids=true" : "")).build();
			Request requestJockie = new Request.Builder().url("http://" + Settings.LOCAL_HOST + ":8080/411916947773587456/votes" + (all ? "?ids=true" : "")).build();
			
			ImageModule.client.newCall(requestSx4).enqueue((Sx4Callback) responseSx4 -> {
				ImageModule.client.newCall(requestJockie).enqueue((Sx4Callback) responseJockie -> {
					JSONObject jsonSx4 = new JSONObject(responseSx4.body().string()).getJSONObject("votes");
					JSONObject jsonJockie = new JSONObject(responseJockie.body().string()).getJSONObject("votes");
					Set<String> keysSx4 = jsonSx4.keySet();
					Set<String> keysJockie = jsonJockie.keySet();
					
					Map<User, Integer> votesMap = new HashMap<>();
					SnowflakeCacheView<User> cache = event.getShardManager().getUserCache();
					if (all) {
						for (String keySx4 : keysSx4) {
							User user = cache.getElementById(keySx4);
							if (user != null) {
								if (!guild || event.getGuild().isMember(user)) {
									votesMap.compute(user, (key, value) -> value != null ? value + jsonSx4.getJSONObject(keySx4).getJSONArray("votes").length() : jsonSx4.getJSONObject(keySx4).getJSONArray("votes").length());
								}
							}
						}
						
						for (String keyJockie : keysJockie) {
							User user = cache.getElementById(keyJockie);
							if (user != null) {
								if (!guild || event.getGuild().isMember(user)) {
									votesMap.compute(user, (key, value) -> value != null ? value + jsonJockie.getJSONObject(keyJockie).getJSONArray("votes").length() : jsonJockie.getJSONObject(keyJockie).getJSONArray("votes").length());
								}
							}
						}
					} else {
						for (String keySx4 : keysSx4) {
							User user = cache.getElementById(keySx4);
							if (user != null) {
								for (Object voteObject : jsonSx4.getJSONObject(keySx4).getJSONArray("votes")) {
									JSONObject vote = (JSONObject) voteObject;
									LocalDateTime voteTime = LocalDateTime.ofEpochSecond(vote.getLong("time"), 0, ZoneOffset.UTC);
									if (voteTime.getMonth() == month && voteTime.getYear() == year) {
										if (!guild || event.getGuild().isMember(user)) {
											votesMap.compute(user, (key, value) -> value != null ? ++value : 1);
										}
									}
								}
							}
						}
						
						for (String keyJockie : keysJockie) {
							User user = cache.getElementById(keyJockie);
							if (user != null) {
								for (Object voteObject : jsonJockie.getJSONObject(keyJockie).getJSONArray("votes")) {
									JSONObject vote = (JSONObject) voteObject;
									LocalDateTime voteTime = LocalDateTime.ofEpochSecond(vote.getLong("time"), 0, ZoneOffset.UTC);
									if (voteTime.getMonth() == month && voteTime.getYear() == year) {
										if (!guild || event.getGuild().isMember(user)) {
											votesMap.compute(user, (key, value) -> value != null ? ++value : 1);
										}
									}
								}
							}
						}
					}
					
					List<Entry<User, Integer>> votes = new ArrayList<>(votesMap.entrySet());
					votes.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
					
					PagedResult<Entry<User, Integer>> paged = new PagedResult<>(votes)
							.setDeleteMessage(false)
							.setCustomFunction(page -> {
								Integer index = null;
								for (int i = 0; i < votes.size(); i++) {
									Entry<User, Integer> userData = votes.get(i);
									if (userData.getKey().equals(event.getAuthor())) {
										index = i + 1;
									}
								}
								
								EmbedBuilder embed = new EmbedBuilder();
								embed.setColor(Settings.EMBED_COLOUR);
								embed.setTitle("Votes Leaderboard" + (all ? "" : " for " + month.getDisplayName(TextStyle.FULL, Locale.UK) + " " + year));
								embed.setFooter(event.getAuthor().getName() + "'s Rank: " + (index == null ? "Unranked" : GeneralUtils.getNumberSuffix(index)) + " | Page " + page.getCurrentPage() + "/" + page.getMaxPage(), event.getAuthor().getEffectiveAvatarUrl());
								
								for (int i = page.getCurrentPage() * page.getPerPage() - page.getPerPage(); i < page.getCurrentPage() * page.getPerPage(); i++) {
									try {
										Entry<User, Integer> userData = votes.get(i);
										int votesAmount = userData.getValue();
										embed.appendDescription(String.format("%d. `%s` - %,d vote%s\n", i + 1, userData.getKey().getAsTag(), votesAmount, votesAmount == 1 ? "" : "s"));
									} catch (IndexOutOfBoundsException e) {
										break;
									}
								}
								
								return embed.build();
							});
					
					PagedUtils.getPagedResult(event, paged, 300, null);
				});
			});
		}
		
	}
	
	@Initialize(all=true, subCommands=true, recursive=true)
	public void initialize(CommandImpl command) {
		command.setCategory(Categories.ECONOMY);
	}
	
}
