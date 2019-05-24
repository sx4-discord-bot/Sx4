package com.sx4.modules;

import static com.rethinkdb.RethinkDB.r;

import java.math.BigInteger;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONObject;

import com.jockie.bot.core.Context;
import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.command.Command.Async;
import com.jockie.bot.core.command.Command.BotPermissions;
import com.jockie.bot.core.command.Command.Cooldown;
import com.jockie.bot.core.command.ICommand.ContentOverflowPolicy;
import com.jockie.bot.core.command.Initialize;
import com.jockie.bot.core.command.impl.CommandEvent;
import com.jockie.bot.core.command.impl.CommandImpl;
import com.jockie.bot.core.module.Module;
import com.jockie.bot.core.option.Option;
import com.rethinkdb.gen.ast.Get;
import com.rethinkdb.gen.ast.Table;
import com.rethinkdb.model.OptArgs;
import com.rethinkdb.net.Connection;
import com.rethinkdb.net.Cursor;
import com.sx4.categories.Categories;
import com.sx4.core.Sx4Bot;
import com.sx4.economy.AuctionItem;
import com.sx4.economy.Item;
import com.sx4.economy.ItemStack;
import com.sx4.economy.items.Booster;
import com.sx4.economy.items.Crate;
import com.sx4.economy.items.Factory;
import com.sx4.economy.items.Miner;
import com.sx4.economy.materials.Material;
import com.sx4.economy.materials.Wood;
import com.sx4.economy.tools.Axe;
import com.sx4.economy.tools.Pickaxe;
import com.sx4.economy.tools.Rod;
import com.sx4.economy.upgrades.AxeUpgrade;
import com.sx4.economy.upgrades.PickaxeUpgrade;
import com.sx4.economy.upgrades.RodUpgrade;
import com.sx4.interfaces.Sx4Callback;
import com.sx4.settings.Settings;
import com.sx4.utils.ArgumentUtils;
import com.sx4.utils.EconomyUtils;
import com.sx4.utils.GeneralUtils;
import com.sx4.utils.HelpUtils;
import com.sx4.utils.PagedUtils;
import com.sx4.utils.TimeUtils;
import com.sx4.utils.TokenUtils;
import com.sx4.utils.EconomyUtils.Slot;
import com.sx4.utils.PagedUtils.PagedResult;

import net.dv8tion.jda.core.utils.tuple.Pair;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.utils.cache.SnowflakeCacheView;
import okhttp3.Request;

@Module
public class EconomyModule {
	
	Random random = new Random();

	public class CrateCommand extends CommandImpl {
		
		public CrateCommand() {
			super("crate");
			
			super.setAliases("crates");
			super.setDescription("Open crates to get random items in the economy");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="shop", aliases={"list"}, description="View all the crates you can buy", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void shop(CommandEvent event, @Context Connection connection) {
			Map<String, Object> data = r.table("bank").get(event.getAuthor().getId()).run(connection);
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setColor(Settings.EMBED_COLOUR);
			embed.setAuthor("Crate Shop", null, event.getSelfUser().getEffectiveAvatarUrl());
			embed.setDescription("Crates give you a random item, the better the crate the better the chance of a better item");
			embed.setFooter(String.format("Use %scrate buy <crate> to buy a crate | Balance: $%,d", event.getPrefix(), data == null ? 0 : (long) data.get("balance")), event.getAuthor().getEffectiveAvatarUrl());
			
			for (Crate crate : Crate.ALL) {
				if (crate.isBuyable()) {
					embed.addField(crate.getName(), String.format("Price: $%,d", crate.getPrice()), true);
				}
			}
			
			event.reply(embed.build()).queue();
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="buy", description="Buy a crate displayed in the crate shop")
		public void buy(CommandEvent event, @Context Connection connection, @Argument(value="crate name", endless=true) String crateArgument) {
			Get data = r.table("bank").get(event.getAuthor().getId());
			Map<String, Object> dataRan = data.run(connection);
			
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
			
			if (dataRan == null) {
				event.reply(String.format("You do not have enough money to purchase `%,d %s` :no_entry:", crateAmount, crate.getName())).queue();
				return;
			}
			
			BigInteger price = BigInteger.valueOf((long) crate.getPrice()).multiply(crateAmount);
			if (BigInteger.valueOf((long) dataRan.get("balance")).compareTo(price) != -1) {
				event.reply(String.format("You just bought `%,d %s` for **$%,d** :ok_hand:", crateAmount, crate.getName(), price.longValue())).queue();
				data.update(row -> r.hashMap("items", EconomyUtils.addItems((List<Map<String, Object>>) dataRan.get("items"), crate, crateAmount.longValue())).with("balance", row.g("balance").sub(price.longValue()))).runNoReply(connection);
			} else {
				event.reply(String.format("You do not have enough money to purchase `%,d %s` :no_entry:", crateAmount, crate.getName())).queue();
			}
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="open", description="Open a crate you have in your items")
		@Async
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void open(CommandEvent event, @Context Connection connection, @Argument(value="crate name", endless=true) String crateArgument) {
			Get data = r.table("bank").get(event.getAuthor().getId());
			Map<String, Object> dataRan = data.run(connection);
			
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
			
			if (dataRan == null) {
				event.reply(String.format("You do not have `%,d %s` :no_entry:", crateAmount, crate.getName())).queue();
				return;
			}
			
			List<Map<String, Object>> userItems = (List<Map<String, Object>>) dataRan.get("items");
			
			List<Item> itemsWon = new ArrayList<>();
			List<ItemStack> finalItems = new ArrayList<>();
			List<Item> winnableItems = EconomyUtils.WINNABLE_ITEMS;
			winnableItems.remove(crate);
			ItemStack userItem = EconomyUtils.getUserItem(userItems, crate);
			if (BigInteger.valueOf(userItem.getAmount()).compareTo(crateAmount) != -1) {
				for (int i = 0; i < crateAmount.longValue(); i++) { 
					for (Item item : winnableItems) {
						int equation = (int) Math.ceil((1 / ((1 / (double) item.getPrice()) * (double) crate.getPrice())) * 35);
						if (random.nextInt(equation + 1) == 0) {
							itemsWon.add(item);
						}
					}	
					
					itemsWon.sort((a, b) -> Long.compare(b.getPrice(), a.getPrice()));
					if (!itemsWon.isEmpty()) {
						if (finalItems.isEmpty()) {
							finalItems.add(new ItemStack(itemsWon.get(0), 1L));
						} else {
							boolean updated = false;
							for (ItemStack finalItem : new ArrayList<>(finalItems)) {
								if (finalItem.getItem().equals(itemsWon.get(0))) {
									finalItem.incrementAmount();
									
									updated = true;
									break;
								}
							}
							
							if (updated == false) {
								finalItems.add(new ItemStack(itemsWon.get(0), 1L));
							}
						}
						
						itemsWon.clear();
					}
				}
				
				EmbedBuilder embed = new EmbedBuilder();
				embed.setAuthor(event.getAuthor().getName(), null, event.getAuthor().getEffectiveAvatarUrl());
				embed.setColor(event.getMember().getColor());
				if (finalItems.isEmpty()) {
					embed.setDescription(String.format("You opened `%,d %s` and got scammed, there was nothing in the crate.", crateAmount, crate.getName()));
				} else {
					String content = "";
					finalItems.sort((a, b) -> Long.compare(b.getAmount(), a.getAmount()));
					for (ItemStack finalItem : finalItems) {
						content += finalItem.getItem().getName() + " x" + finalItem.getAmount();
						if (finalItems.indexOf(finalItem) != finalItems.size() - 1) {
							content += ", ";
						}
						
						userItems = EconomyUtils.addItems(userItems, finalItem.getItem(), finalItem.getAmount());
					}
					
					embed.setDescription(String.format("You opened `%,d %s` and won **%s** :tada:", crateAmount, crate.getName(), content));
				}
				
				event.reply(embed.build()).queue();
				
			    userItems = EconomyUtils.removeItems(userItems, crate, crateAmount.longValue());
			    data.update(r.hashMap("items", userItems)).runNoReply(connection);
			} else {
				event.reply(String.format("You do not have `%,d %s` :no_entry:", crateAmount, crate.getName())).queue();
				return;
			}
		}
		
	}
	
	@Command(value="tax", description="View the amount of tax the bot currently has (This is given away every friday in the support server)", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void tax(CommandEvent event, @Context Connection connection) {
		EmbedBuilder embed = new EmbedBuilder();
		embed.setAuthor(event.getSelfUser().getAsTag(), null, event.getSelfUser().getEffectiveAvatarUrl());
		embed.setDescription(String.format("Their balance: **$%,d**", (long) r.table("tax").get("tax").g("tax").run(connection)));
		embed.setColor(Settings.EMBED_COLOUR);
		
		event.reply(embed.build()).queue();
	}
	
	@SuppressWarnings("unchecked")
	@Command(value="trade", description="Trade items and money with another user")
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	@Cooldown(value=10)
	public void trade(CommandEvent event, @Context Connection connection, @Argument(value="user", endless=true) String userArgument) {
		Get authorData = r.table("bank").get(event.getAuthor().getId());
		Map<String, Object> authorDataRan = authorData.run(connection);
		
		if (authorDataRan == null) {
			event.reply("You do not have anything to trade :no_entry:").queue();
			return;
		}
		
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
		
		Get userData = r.table("bank").get(member.getUser().getId());
		Map<String, Object> userDataRan = userData.run(connection);
		
		if (userDataRan == null) {
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
				
				Pair<Long, List<ItemStack>> authorTrade;
				try {
					authorTrade = EconomyUtils.getTrade(authorTradeMessage.getContentRaw());
				} catch(IllegalArgumentException e) {
					event.reply(e.getMessage()).queue();
					return;
				}
				
				long authorMoney = authorTrade.getLeft();
				List<ItemStack> authorItems = authorTrade.getRight();
				
				String authorItemsContent = "";
				for (ItemStack itemStack : authorItems) {
					authorItemsContent += String.format("%s x%,d\n", itemStack.getItem().getName(), itemStack.getAmount());
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
						
						Pair<Long, List<ItemStack>> userTrade;
						try {
							userTrade = EconomyUtils.getTrade(userTradeMessage.getContentRaw());
						} catch(IllegalArgumentException e) {
							event.reply(e.getMessage()).queue();
							return;
						}
						
						long userMoney = userTrade.getLeft();
						List<ItemStack> userItems = userTrade.getRight();
						
						String userItemsContent = "";
						for (ItemStack itemStack : userItems) {
							userItemsContent += String.format("%s x%,d\n", itemStack.getItem().getName(), itemStack.getAmount());
						}
						
						EmbedBuilder embed = new EmbedBuilder();
						embed.setTitle("Final Trade");
						embed.addField(member.getUser().getAsTag() + " Gets", embedAuthor.getDescriptionBuilder().toString(), false);
						embed.addField(event.getAuthor().getAsTag() + " Gets", (userMoney != 0 ? String.format("$%,d\n", userMoney) : "") + userItemsContent, false);
						embed.setFooter(member.getUser().getAsTag() + " needs to type accept or yes to accept the trade", null);
						
						event.reply(embed.build()).queue(€ -> {
							PagedUtils.getConfirmation(event, 60, member.getUser(), confirmation -> {
								if (confirmation == true) {
									Map<String, Object> newAuthorData = authorData.run(connection);
									Map<String, Object> newUserData = userData.run(connection);
									
									long totalAuthorWorth = authorMoney;
									long totalUserWorth = userMoney;
									
									if ((long) newAuthorData.get("balance") < authorMoney) {
										event.reply("**" + event.getAuthor().getAsTag() + "** does not have $" + authorMoney + " :no_entry:").queue();
										return;
									}
									
									if ((long) newUserData.get("balance") < userMoney) {
										event.reply("**" + member.getUser().getAsTag() + "** does not have $" + userMoney + " :no_entry:").queue();
										return;
									}
									
									List<Map<String, Object>> authorItemsData = (List<Map<String, Object>>) newAuthorData.get("items");
									List<Map<String, Object>> userItemsData = (List<Map<String, Object>>) newUserData.get("items");
									
									for (ItemStack itemStack : authorItems) {
										ItemStack authorItem = EconomyUtils.getUserItem(authorItemsData, itemStack.getItem());
										if (authorItem.getAmount() < itemStack.getAmount()) {
											event.reply(String.format("**%s** does not have `%,d %s` :no_entry:", event.getAuthor().getAsTag(), itemStack.getAmount(), itemStack.getItem().getName())).queue();
											return;
										}
										
										totalAuthorWorth += itemStack.getItem().getPrice() == null ? 0 : itemStack.getItem().getPrice();
										
										authorItemsData = EconomyUtils.removeItems(authorItemsData, itemStack.getItem(), itemStack.getAmount());
										userItemsData = EconomyUtils.addItems(userItemsData, itemStack.getItem(), itemStack.getAmount());
									}
									
									for (ItemStack itemStack : userItems) {
										ItemStack userItem = EconomyUtils.getUserItem(userItemsData, itemStack.getItem());
										if (userItem.getAmount() < itemStack.getAmount()) {
											event.reply(String.format("**%s** does not have `%,d %s` :no_entry:", member.getUser().getAsTag(), itemStack.getAmount(), itemStack.getItem().getName())).queue();
											return;
										}
										
										totalUserWorth += itemStack.getItem().getPrice() == null ? 0 : itemStack.getItem().getPrice();
										
										authorItemsData = EconomyUtils.addItems(authorItemsData, itemStack.getItem(), itemStack.getAmount());
										userItemsData = EconomyUtils.removeItems(userItemsData, itemStack.getItem(), itemStack.getAmount());
									}
									
									if (totalUserWorth / totalAuthorWorth > 20 || totalAuthorWorth / totalUserWorth > 20) {
										event.reply("You have to trade at least 5% the worth of the other persons trade :no_entry:").queue();
										return;
									}
									
									event.reply("All items and money have been transferred <:done:403285928233402378>").queue();
									
									authorData.update(r.hashMap("items", authorItemsData).with("balance", (long) newAuthorData.get("balance") + (userMoney - authorMoney))).runNoReply(connection);
									userData.update(r.hashMap("items", userItemsData).with("balance", (long) newUserData.get("balance") + (authorMoney - userMoney))).runNoReply(connection);
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
	
	public class BoosterCommand extends CommandImpl {
		
		public BoosterCommand() {
			super("booster");
			
			super.setAliases("boosters");
			super.setDescription("Buy boosters to be given an advantage at a cost");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="shop", aliases={"list"}, description="View all the boosters in the economy system", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void shop(CommandEvent event, @Context Connection connection) {
			Map<String, Object> data = r.table("bank").get(event.getAuthor().getId()).run(connection);
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setDescription("Buy boosters to be given benefits at a cost");
			embed.setColor(Settings.EMBED_COLOUR);
			embed.setAuthor("Booster Shop", null, event.getSelfUser().getEffectiveAvatarUrl());
			embed.setFooter(String.format("Use %sbooster buy <booster> to buy a booster | Balance: $%,d", event.getPrefix(), data == null ? 0 : (long) data.get("balance")), event.getAuthor().getEffectiveAvatarUrl());
			
			for (Booster booster : Booster.ALL) {
				embed.addField(booster.getName(), String.format("Price: $%,d\nDescription: %s %s", booster.getPrice(), booster.getDescription(), booster.isActivatable() ? "[Activatable]" : ""), false);
			}
			
			event.reply(embed.build()).queue();
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="buy", description="Buy a booster listed in the booster shop")
		public void buy(CommandEvent event, @Context Connection connection, @Argument(value="booster name", endless=true) String boosterArgument) {
			Get data = r.table("bank").get(event.getAuthor().getId());
			Map<String, Object> dataRan = data.run(connection);
			
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
			
			if (dataRan == null) {
				event.reply(String.format("You do not have enough money to purchase `%,d %s` :no_entry:", boosterAmount, booster.getName())).queue();
				return;
			}
			
			BigInteger price = BigInteger.valueOf((long) booster.getPrice()).multiply(boosterAmount);
			if (BigInteger.valueOf((long) dataRan.get("balance")).compareTo(price) != -1) {
				event.reply(String.format("You just bought `%,d %s` for **$%,d** :ok_hand:", boosterAmount, booster.getName(), price.longValue())).queue();
				data.update(row -> r.hashMap("items", EconomyUtils.addItems((List<Map<String, Object>>) dataRan.get("items"), booster, boosterAmount.longValue())).with("balance", row.g("balance").sub(price.longValue()))).runNoReply(connection);
			} else {
				event.reply(String.format("You do not have enough money to purchase `%,d %s` :no_entry:", boosterAmount, booster.getName())).queue();
			}
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="activate", description="Activates a booster which is activatable")
		public void activate(CommandEvent event, @Context Connection connection, @Argument(value="booster name") String boosterName) {
			Get data = r.table("bank").get(event.getAuthor().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			Booster booster = Booster.getBoosterByName(boosterName);
			if (booster == null) {
				event.reply("I could not find that booster :no_entry:").queue();
				return;
			}
			
			if (!booster.isActivatable()) {
				event.reply("That booster is not activatable :no_entry:").queue();
                return;
			}
			
			if (dataRan == null) {
				event.reply(String.format("You do not have a `%s` :no_entry:", booster.getName())).queue();
				return;
			}
			
			List<Map<String, Object>> userItems = (List<Map<String, Object>>) dataRan.get("items");
			if (booster.equals(Booster.LENDED_PICKAXE)) {
				Pickaxe userPickaxe = EconomyUtils.getUserPickaxe(dataRan);
				if (userPickaxe == null) {
					event.reply("You do not own a pickaxe :no_entry:").queue();
					return;
				}
				
				if ((Double) dataRan.get("picktime") == null || Clock.systemUTC().instant().getEpochSecond() - (Double) dataRan.get("picktime") >= EconomyUtils.MINE_COOLDOWN) {
					event.reply("You currently do not have a cooldown on your mine :no_entry:").queue();
					return;
				}
				
				event.reply("Your booster `" + booster.getName() + "` has been activated :ok_hand:").queue();
				data.update(r.hashMap("items", EconomyUtils.removeItem(userItems, booster)).with("picktime", null)).runNoReply(connection);
			}
		}
		
	}
	
	@Command(value="referral", aliases={"referral link", "referrallink"}, description="Gives you a users referral links these can be used to give the user bonus money when you vote")
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
		
		event.reply(String.format("**Referral Links for %s**\n\nSx4: <https://discordbots.org/bot/440996323156819968/vote?referral=%s>\nJockie Music: <https://discordbots.org/bot/411916947773587456/vote?referral=%s>", 
				member.getUser().getAsTag(), member.getUser().getId(), member.getUser().getId())).queue();
	}
	
	@Command(value="vote", aliases={"vote bonus", "votebonus", "upvote"}, description="Upvote the bot on discord bot list to get some free money in the economy", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void vote(CommandEvent event, @Context Connection connection) {
		LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
		boolean weekend = now.getDayOfWeek().equals(DayOfWeek.FRIDAY) || now.getDayOfWeek().equals(DayOfWeek.SATURDAY) || now.getDayOfWeek().equals(DayOfWeek.SUNDAY) ? true : false;
		
		Request requestSx4 = new Request.Builder()
				.url("http://" + Settings.LOCAL_HOST + ":8080/440996323156819968/votes/user/" + event.getAuthor().getId() + "/unused/use")
				.addHeader("Authorization", TokenUtils.VOTE_API_SX4)
				.build();
		
		Request requestJockieMusic = new Request.Builder()
				.url("http://" + Settings.LOCAL_HOST + ":8080/411916947773587456/votes/user/" + event.getAuthor().getId() + "/unused/use")
				.addHeader("Authorization", TokenUtils.VOTE_API_JOCKIE_MUSIC)
				.build();
		
		Sx4Bot.client.newCall(requestSx4).enqueue((Sx4Callback) sx4Response -> {
			Sx4Bot.client.newCall(requestJockieMusic).enqueue((Sx4Callback) jockieMusicResponse -> {
				JSONObject jsonSx4 = new JSONObject(sx4Response.body().string());
				JSONObject jsonJockieMusic = new JSONObject(jockieMusicResponse.body().string());
				 
				if (jsonSx4.getBoolean("success") || jsonJockieMusic.getBoolean("success")) {
					JSONArray sx4Votes = null;
					JSONArray jockieMusicVotes = null;
					long money = 0;
				    if (jsonSx4.has("votes")) {
				    	sx4Votes = jsonSx4.getJSONArray("votes");
				    }
				    
				    if (jsonJockieMusic.has("votes")) {
				    	jockieMusicVotes = jsonJockieMusic.getJSONArray("votes");
				    }
					 
				    Map<User, Integer> referredUsers = new HashMap<>();
				    if (sx4Votes != null) {
					    for (Object sx4VoteObject : sx4Votes) {
					    	JSONObject sx4Vote = (JSONObject) sx4VoteObject;
					    	
					    	money += sx4Vote.getBoolean("weekend") ? 1000 : 500;
					    	
					    	if (sx4Vote.getJSONObject("query").has("referral")) {
					    		User referredUser;
					    		if (sx4Vote.getJSONObject("query").get("referral") instanceof String[]) {
					    			referredUser = event.getShardManager().getUserById(sx4Vote.getJSONObject("query").getJSONArray("referral").getString(0));
					    		} else {
					    			referredUser = event.getShardManager().getUserById(sx4Vote.getJSONObject("query").getString("referral"));
					    		}
					    		
					    		if (referredUser != null) {
				    				if (referredUsers.containsKey(referredUser)) {
				    					referredUsers.put(referredUser, referredUsers.get(referredUser) + (sx4Vote.getBoolean("weekend") ? 500 : 250));
				    				} else {
				    					referredUsers.put(referredUser, sx4Vote.getBoolean("weekend") ? 500 : 250);
				    				}
				    			}
					    	}
					    }
				    }
				    
				    if (jockieMusicVotes != null) {
					    for (Object jockieMusicVoteObject : jockieMusicVotes) {
					    	JSONObject jockieMusicVote = (JSONObject) jockieMusicVoteObject;
					    	
					    	money += jockieMusicVote.getBoolean("weekend") ? 600 : 300;
					    	
					    	if (jockieMusicVote.getJSONObject("query").has("referral")) {
					    		User referredUser;
					    		if (jockieMusicVote.getJSONObject("query").get("referral") instanceof String[]) {
					    			referredUser = event.getShardManager().getUserById(jockieMusicVote.getJSONObject("query").getJSONArray("referral").getString(0));
					    		} else {
					    			referredUser = event.getShardManager().getUserById(jockieMusicVote.getJSONObject("query").getString("referral"));
					    		}
					    		
					    		if (referredUser != null) {
				    				if (referredUsers.containsKey(referredUser)) {
				    					referredUsers.put(referredUser, referredUsers.get(referredUser) + (jockieMusicVote.getBoolean("weekend") ? 300 : 150));
				    				} else {
				    					referredUsers.put(referredUser, jockieMusicVote.getBoolean("weekend") ? 300 : 150);
				    				}
				    			}
					    	}
					    }
				    }
				    
				    String referredContent = "";
				    for (User key : referredUsers.keySet()) {
				    	Get userData = r.table("bank").get(key.getId());
				    	userData.update(row -> r.hashMap("balance", row.g("balance").add(referredUsers.get(key)))).runNoReply(connection);
				    	referredContent += key.getAsTag() + " (**$" + referredUsers.get(key) + "**), ";
				    }
				    
				    if (referredContent != "") {
				    	referredContent = referredContent.substring(0, referredContent.length() - 2);
				    }
				    
				    int totalVotes = (sx4Votes == null ? 0 : sx4Votes.length()) + (jockieMusicVotes == null ? 0 : jockieMusicVotes.length());
				    event.reply(String.format("You have voted for the bot **%,d** time%s since you last used the command gathering you a total of **$%,d**, Vote for the bots again in 12 hours for more money."
				    		+ " Referred users: %s", totalVotes, totalVotes == 1 ? "" : "s", money, referredContent == "" ? "No one" : referredContent)).queue();
					 
				    EconomyUtils.insertData(event.getAuthor()).run(connection, OptArgs.of("durability", "soft"));
				    Get data = r.table("bank").get(event.getAuthor().getId());
				    
				    long dataMoney = money;
				    data.update(row -> r.hashMap("balance", row.g("balance").add(dataMoney))).runNoReply(connection);
			    } else {
					if (jsonSx4.getString("error").equals("This user has no unused votes") && jsonJockieMusic.getString("error").equals("This user has no unused votes")) {
						Request latestSx4 = new Request.Builder()
									.url("http://" + Settings.LOCAL_HOST + ":8080/440996323156819968/votes/user/" + event.getAuthor().getId() + "/latest")
									.addHeader("Authorization", TokenUtils.VOTE_API_SX4)
									.build();
							
						Request latestJockieMusic = new Request.Builder()
									.url("http://" + Settings.LOCAL_HOST + ":8080/411916947773587456/votes/user/" + event.getAuthor().getId() + "/latest")
									.addHeader("Authorization", TokenUtils.VOTE_API_JOCKIE_MUSIC)
									.build();
							
						Sx4Bot.client.newCall(latestSx4).enqueue((Sx4Callback) latestSx4Response -> {
							Sx4Bot.client.newCall(latestJockieMusic).enqueue((Sx4Callback) latestJockieMusicResponse -> {
								JSONObject latestJsonSx4 = new JSONObject(latestSx4Response.body().string());
								JSONObject latestJsonJockieMusic = new JSONObject(latestJockieMusicResponse.body().string());
								
								long timestamp = Clock.systemUTC().instant().getEpochSecond();
								long timestampSx4 = latestJsonSx4.getJSONObject("vote").getLong("time") - timestamp + EconomyUtils.VOTE_COOLDOWN;
								long timestampJockieMusic = latestJsonJockieMusic.getJSONObject("vote").getLong("time") - timestamp + EconomyUtils.VOTE_COOLDOWN; 
								String timeSx4 = latestJsonSx4.getBoolean("success") ? TimeUtils.toTimeString(timestampSx4, ChronoUnit.SECONDS) : null;
								String timeJockieMusic = latestJsonJockieMusic.getBoolean("success") ? TimeUtils.toTimeString(timestampJockieMusic, ChronoUnit.SECONDS) : null;
								
								EmbedBuilder embed = new EmbedBuilder();
								embed.setAuthor("Vote Bonus", null, event.getAuthor().getEffectiveAvatarUrl());
								if (timeSx4 != null && timestampSx4 >= 0) {
									embed.addField("Sx4", "**[You have voted recently you can vote for the bot again in " + timeSx4 + "](https://discordbots.org/bot/440996323156819968/vote)**", false);
								} else {
									embed.addField("Sx4", "**[You can vote for Sx4 for an extra $" + (weekend ? 1000 : 500) + "](https://discordbots.org/bot/440996323156819968/vote)**", false);
								}
								
								if (timeJockieMusic != null && timestampJockieMusic >= 0) {
									embed.addField("Jockie Music", "**[You have voted recently you can vote for the bot again in " + timeJockieMusic + "](https://discordbots.org/bot/411916947773587456/vote)**", false);
								} else {
									embed.addField("Jockie Music", "**[You can vote for Jockie Music for an extra $" + (weekend ? 600 : 300) + "](https://discordbots.org/bot/411916947773587456/vote)**", false);
								}
								
								event.reply(embed.build()).queue();
							});
						});
					} else {
						event.reply("Oops something went wrong there, try again :no_entry:").queue();
					}
				}
			});
		});
	}
	
	@SuppressWarnings("unchecked")
	@Command(value="daily", aliases={"pd", "payday"}, description="Collect your daily money, repeatedly collect it everyday to get streaks the higher your streaks the better chance of getting a higher tier crate", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void daily(CommandEvent event, @Context Connection connection) {
		EconomyUtils.insertData(event.getAuthor()).run(connection, OptArgs.of("durability", "soft"));
		Get data = r.table("bank").get(event.getAuthor().getId());
		Map<String, Object> dataRan = data.run(connection);
		
		long money;
		double timestampNow = (double) Clock.systemUTC().instant().toEpochMilli() / 1000;
		Double streakTime = (Double) dataRan.get("streaktime");
		
		EmbedBuilder embed = new EmbedBuilder();
		if (streakTime == null) {
			money = 100;
			
			embed.setAuthor(event.getAuthor().getName(), null, event.getAuthor().getEffectiveAvatarUrl());
			embed.setDescription("You have collected your daily money! (**+$" + money + "**)");
			embed.setColor(event.getMember().getColor());
			event.reply(embed.build()).queue();
			
			data.update(row -> r.hashMap("streaktime", timestampNow).with("balance", row.g("balance").add(money)).with("streak", 0)).runNoReply(connection);
		} else if (timestampNow - streakTime <= EconomyUtils.DAILY_COOLDOWN) {
			event.reply("Slow down! You can collect your daily in " + TimeUtils.toTimeString((long) (streakTime - timestampNow + EconomyUtils.DAILY_COOLDOWN), ChronoUnit.SECONDS) + " :stopwatch:").queue();
		} else if (timestampNow - streakTime <= EconomyUtils.DAILY_COOLDOWN * 2) {
			long currentStreak = (long) dataRan.get("streak") + 1;
			money = currentStreak >= 5 ? 250 : currentStreak == 4 ? 200 : currentStreak == 3 ? 170 : currentStreak == 2 ? 145 : currentStreak == 1 ? 120 : 100;
			
			List<Crate> crates = new ArrayList<>();
			for (Crate crate : Crate.ALL) {
				if (crate.isBuyable()) {
					System.out.println(currentStreak);
					System.out.println((int) Math.ceil((crate.getChance() / currentStreak) * 4) + 1);
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
			event.reply(embed.build()).queue();
			
			data.update(row -> r.hashMap("streaktime", timestampNow)
					.with("balance", row.g("balance").add(money))
					.with("streak", currentStreak)
					.with("items", crateWon == null ? row.g("items") : EconomyUtils.addItem((List<Map<String, Object>>) dataRan.get("items"), crateWon))).runNoReply(connection);
		} else {
			money = 100;
			
			embed.setAuthor(event.getAuthor().getName(), null, event.getAuthor().getEffectiveAvatarUrl());
			embed.setDescription("You have collected your daily money! (**+$" + money + "**)\n\nIt has been over 2 days since you last used the command, your streak has been reset");
			embed.setColor(event.getMember().getColor());
			event.reply(embed.build()).queue();
			
			data.update(row -> r.hashMap("streaktime", timestampNow).with("balance", row.g("balance").add(money)).with("streak", 0)).runNoReply(connection);
		}
	}
	
	@Command(value="balance", aliases={"bal"}, description="Check the amount of money a user currently has")
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void balance(CommandEvent event, @Context Connection connection, @Argument(value="user", endless=true, nullDefault=true) String userArgument) {
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
		
		Map<String, Object> data = r.table("bank").get(member.getUser().getId()).run(connection);
		EmbedBuilder embed = new EmbedBuilder();
		embed.setColor(member.getColor());
		embed.setAuthor(member.getUser().getName(), null, member.getUser().getEffectiveAvatarUrl());
		embed.setDescription((member.equals(event.getMember()) ? "Your" : "Their") + " balance: " + String.format("**$%,d**", data == null ? 0 : (long) data.get("balance")));
		event.reply(embed.build()).queue();
	}
	
	@Command(value="winnings", description="Check the amount of money a user has won/lost through betting")
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void winnings(CommandEvent event, @Context Connection connection, @Argument(value="user", endless=true, nullDefault=true) String userArgument) {
		Member member;
		if (userArgument == null) {
			member = event.getMember();
		} else {
			member = ArgumentUtils.getMember(event.getGuild(), userArgument);
			if (member == null) {
				event.reply("I could not find that user :no_entry:").queue();
			}
		}
		
		Map<String, Object> data = r.table("bank").get(member.getUser().getId()).run(connection);
		EmbedBuilder embed = new EmbedBuilder();
		embed.setColor(member.getColor());
		embed.setAuthor(member.getUser().getName(), null, member.getUser().getEffectiveAvatarUrl());
		embed.setDescription((member.equals(event.getMember()) ? "Your" : "Their") + " winnings: " + String.format("**$%,d**", data == null ? 0 : (long) data.get("winnings")));
		event.reply(embed.build()).queue();
	}
	
	@Command(value="networth", description="Check the networth of a user")
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void networth(CommandEvent event, @Context Connection connection, @Argument(value="user", endless=true, nullDefault=true) String userArgument) {
		Member member;
		if (userArgument == null) {
			member = event.getMember();
		} else {
			member = ArgumentUtils.getMember(event.getGuild(), userArgument);
			if (member == null) {
				event.reply("I could not find that user :no_entry:").queue();
			}
		}
		
		Map<String, Object> data = r.table("bank").get(member.getUser().getId()).run(connection);
		
		long networth = 0;
		if (data != null) {
			networth = EconomyUtils.getUserNetworth(data);
		}
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setColor(member.getColor());
		embed.setAuthor(member.getUser().getName(), null, member.getUser().getEffectiveAvatarUrl());
		embed.setDescription((member.equals(event.getMember()) ? "Your" : "Their") + " networth: " + String.format("**$%,d**", networth));
		event.reply(embed.build()).queue();
	}
	
	@Command(value="rep", description="Give another user some reputation")
	public void reputation(CommandEvent event, @Context Connection connection, @Argument(value="user", endless=true, nullDefault=true) String userArgument, @Option(value="amount") boolean amountOption) {
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
		
		if (member.equals(event.getMember())) {
			event.reply("You cannot give reputation yourself :no_entry:").queue();
			return;
		}
		
		if (member.getUser().isBot()) {
			event.reply("You cannot give repuation to bots :no_entry:").queue();
			return;
		}
		
		if (amountOption) {
			Map<String, Object> data = r.table("bank").get(member.getUser().getId()).run(connection);
			event.reply(String.format("%s currently %s **%,d** repuatation", member.equals(event.getMember()) ? "You" : member.getUser().getAsTag(), member.equals(event.getMember()) ? "have" : "has", 
					data == null ? 0 : (long) data.get("rep"))).queue();
		} else {
			EconomyUtils.insertData(member.getUser()).run(connection, OptArgs.of("durability", "soft"));
			EconomyUtils.insertData(event.getAuthor()).run(connection, OptArgs.of("durability", "soft"));
			Get userData = r.table("bank").get(member.getUser().getId());
			Get authorData = r.table("bank").get(event.getAuthor().getId());
			Map<String, Object> authorDataRan = authorData.run(connection);
			
			double timestampNow = (double) Clock.systemUTC().instant().toEpochMilli() / 1000;
			Double repTime = (Double) authorDataRan.get("reptime");
			
			if (repTime == null) {
				event.reply("**+1**, " + member.getUser().getName() + " has gained reputation").queue();
				authorData.update(r.hashMap("reptime", timestampNow)).runNoReply(connection);
				userData.update(row -> r.hashMap("rep", row.g("rep").add(1))).runNoReply(connection);
			} else if (timestampNow - repTime <= EconomyUtils.REPUTATION_COOLDOWN) {
				event.reply("Slow down! You can give out reputation in " + TimeUtils.toTimeString((long) (repTime - timestampNow + EconomyUtils.REPUTATION_COOLDOWN), ChronoUnit.SECONDS) + " :stopwatch:").queue();
			} else {
				event.reply("**+1**, " + member.getUser().getName() + " has gained reputation").queue();
				authorData.update(r.hashMap("reptime", timestampNow)).runNoReply(connection);
				userData.update(row -> r.hashMap("rep", row.g("rep").add(1))).runNoReply(connection);
			}
		}
	}
	
	@Command(value="double or nothing", aliases={"don", "doubleornothing", "allin", "all in", "dn"}, description="Risk it all in the hope of doubling your money or losing it all", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Cooldown(value=40)
	public void doubleOrNothing(CommandEvent event, @Context Connection connection) {
		Get data = r.table("bank").get(event.getAuthor().getId());
		Map<String, Object> dataRan = data.run(connection);
		
		if (dataRan == null || (long) dataRan.get("balance") < 1) {
			event.reply("You do not have any money to bet :no_entry:").queue();
			event.removeCooldown();
			return;
		}

		long balance = (long) dataRan.get("balance");
		event.reply(String.format(event.getAuthor().getName() + ", this will bet **$%,d** are you sure you want to bet this (Yes or No)", balance)).queue(originalMessage -> {
			PagedUtils.getConfirmation(event, 30, event.getAuthor(), confirmation -> {
				if (confirmation == true) {
					originalMessage.delete().queue(null, e -> {});
					event.reply(String.format("You just put **$%,d** on the line and...", balance)).queue(message -> {
						if (random.nextInt(2) == 0) {
							message.editMessage(String.format("You double your money! **+$%,d**", balance)).queueAfter(2, TimeUnit.SECONDS);
							data.update(row -> r.hashMap("winnings", row.g("winnings").add(row.g("balance"))).with("balance", row.g("balance").mul(2))).runNoReply(connection);
						} else {
							message.editMessage(String.format("You lost it all! **-$%,d**", balance)).queueAfter(2, TimeUnit.SECONDS);
							data.update(row -> r.hashMap("winnings", row.g("winnings").sub(row.g("balance"))).with("balance", 0)).runNoReply(connection);
						}
							
						event.removeCooldown();
					});
				} else {
					event.reply("Cancelled <:done:403285928233402378>").queue();
					event.removeCooldown();
				}
			});
		});
	}
	
	public class MinerCommand extends CommandImpl {
		
		public MinerCommand() {
			super("miner");
			
			super.setDescription("You can buy miners so that you can gain recources every 2 hours");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="shop", aliases={"list"}, description="View all the miners you can buy", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void shop(CommandEvent event, @Context Connection connection) {
			Map<String, Object> data = r.table("bank").get(event.getAuthor().getId()).run(connection);
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setAuthor("Miner Shop", null, event.getSelfUser().getEffectiveAvatarUrl());
			embed.setDescription("Miners are a good way to easily gather materials");
			embed.setColor(Settings.EMBED_COLOUR);
			embed.setFooter(String.format("Use %sminer buy <miner> to buy a miner | Balance: $%,d", event.getPrefix(), data == null ? 0 : (long) data.get("balance")), event.getAuthor().getEffectiveAvatarUrl());
			
			for (Miner miner : Miner.ALL) {
				if (miner.isBuyable()) {
					embed.addField(miner.getName(), String.format("Price: $%,d", miner.getPrice()), true);
				}
			}
			
			event.reply(embed.build()).queue();
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="buy", description="Buy a miner which is displayed in the miner shop")
		public void buy(CommandEvent event, @Context Connection connection, @Argument(value="miner name", endless=true) String minerArgument) {
			Get data = r.table("bank").get(event.getAuthor().getId());
			Map<String, Object> dataRan = data.run(connection);
			
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
			
			if (dataRan == null) {
				event.reply(String.format("You do not have enough money to purchase `%,d %s` :no_entry:", minerAmount, miner.getName())).queue();
				return;
			}
			
			BigInteger price = BigInteger.valueOf(miner.getPrice()).multiply(minerAmount);
			if (BigInteger.valueOf((long) dataRan.get("balance")).compareTo(price) != -1) {
				event.reply(String.format("You just bought `%,d %s` for **$%,d** :ok_hand:", minerAmount, miner.getName(), price.longValue())).queue();
				data.update(row -> r.hashMap("items", EconomyUtils.addItems((List<Map<String, Object>>) dataRan.get("items"), miner, minerAmount.longValue())).with("balance", row.g("balance").sub(price.longValue()))).runNoReply(connection);
			} else {
				event.reply(String.format("You do not have enough money to purchase `%,d %s` :no_entry:", minerAmount, miner.getName())).queue();
			}
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="collect", description="Collect your materials from all the miners you own", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void collect(CommandEvent event, @Context Connection connection) {
			Get data = r.table("bank").get(event.getAuthor().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if (dataRan == null) {
				event.reply("You do not own any miners :no_entry:").queue();
				return;
			}
			
			Map<Miner, Long> userMiners = new HashMap<>();
			List<Map<String, Object>> userItems = (List<Map<String, Object>>) dataRan.get("items");
			for (Map<String, Object> userItem : userItems) {
				for (Miner miner : Miner.ALL) {
					if (miner.getName().equals((String) userItem.get("name"))) {
						if (userMiners.containsKey(miner)) {
							userMiners.put(miner, userMiners.get(miner) + (long) userItem.get("amount"));
						} else {
							userMiners.put(miner, (long) userItem.get("amount"));
						}
					}
				}
			}
			
			if (userMiners.isEmpty()) {
				event.reply("You do not own any miners :no_entry:").queue();
				return;
			}
			
			double timestampNow = (double) Clock.systemUTC().instant().toEpochMilli() / 1000;
			Double minerTime = (Double) dataRan.get("minertime");
			
			EmbedBuilder embed = new EmbedBuilder();
			if (minerTime == null) {
				Map<Material, Long> materials = new HashMap<>();
				for (Miner userMiner : userMiners.keySet()) {
					for (Material material : Material.ALL) {
						if (!material.isHidden()) {
							double randomFloat = 0.85D + Math.random() * (1D - 0.85D);
							long materialAmount = (long) Math.round((userMiners.get(userMiner) / Math.ceil(material.getChance() * userMiner.getMultiplier())) * userMiner.getMaximumMaterials() * randomFloat);
							if (materialAmount != 0) {
								if (materials.containsKey(material)) {
									materials.put(material, materials.get(material) + materialAmount);
								} else {
									materials.put(material, materialAmount);
								}
							} else {
								if (random.nextInt((int) Math.ceil(material.getChance() * userMiner.getMultiplier()) + 1) == 0) {
									if (materials.containsKey(material)) {
										materials.put(material, materials.get(material) + 1);
									} else {
										materials.put(material, 1L);
									}
								}
							}
						}
					}
				}
				
				String content = "";
				if (!materials.isEmpty()) {
					List<Material> materialKeys = GeneralUtils.convertSetToList(materials.keySet());
					materialKeys.sort((a, b) -> Long.compare(materials.get(b), materials.get(a)));
					for (Material key : materialKeys) {
						long value = materials.get(key);
						
						userItems = EconomyUtils.addItems(userItems, key, value);
						
						content += key.getName() + " x" + String.format("%,d", value) + key.getEmote();
						if (materialKeys.indexOf(key) != materialKeys.size() - 1) {
							content += ", ";
						}
					}
				} else {
					content = "Absolutely nothing";
				}
				
				embed.setAuthor(event.getAuthor().getName(), null, event.getAuthor().getEffectiveAvatarUrl());
				embed.setDescription("You used your miners and gathered these materials: " + content);
				embed.setColor(event.getMember().getColor());
				event.reply(embed.build()).queue();
				
				data.update(r.hashMap("items", userItems).with("minertime", timestampNow)).runNoReply(connection);
			} else if (timestampNow - minerTime <= EconomyUtils.MINER_COOLDOWN) {
				event.reply("Slow down! You can collect from your miner in " + TimeUtils.toTimeString((long) (minerTime - timestampNow + EconomyUtils.MINER_COOLDOWN), ChronoUnit.SECONDS) + " :stopwatch:").queue();
			} else {
				Map<Material, Long> materials = new HashMap<>();
				for (Miner userMiner : userMiners.keySet()) {
					for (Material material : Material.ALL) {
						if (!material.isHidden()) {
							double randomFloat = 0.85D + Math.random() * (1D - 0.85D);
							long materialAmount = (long) Math.round((userMiners.get(userMiner) / Math.ceil(material.getChance() * userMiner.getMultiplier())) * userMiner.getMaximumMaterials() * randomFloat);
							if (materialAmount != 0) {
								if (materials.containsKey(material)) {
									materials.put(material, materials.get(material) + materialAmount);
								} else {
									materials.put(material, materialAmount);
								}
							} else {
								if (random.nextInt((int) Math.ceil(material.getChance() * userMiner.getMultiplier()) + 1) == 0) {
									if (materials.containsKey(material)) {
										materials.put(material, materials.get(material) + 1);
									} else {
										materials.put(material, 1L);
									}
								}
							}
						}
					}
				}
				
				String content = "";
				if (!materials.isEmpty()) {
					List<Material> materialKeys = GeneralUtils.convertSetToList(materials.keySet());
					materialKeys.sort((a, b) -> Long.compare(materials.get(b), materials.get(a)));
					for (Material key : materialKeys) {
						long value = materials.get(key);
						
						userItems = EconomyUtils.addItems(userItems, key, value);
						
						content += key.getName() + " x" + String.format("%,d", value) + key.getEmote();
						if (materialKeys.indexOf(key) != materialKeys.size() - 1) {
							content += ", ";
						}
					}
				} else {
					content = "Absolutely nothing";
				}
				
				embed.setAuthor(event.getAuthor().getName(), null, event.getAuthor().getEffectiveAvatarUrl());
				embed.setDescription("You used your miners and gathered these materials: " + content);
				embed.setColor(event.getMember().getColor());
				event.reply(embed.build()).queue();
				
				data.update(r.hashMap("items", userItems).with("minertime", timestampNow)).runNoReply(connection);
			}
		}
		
	}
	
	public class PickaxeCommand extends CommandImpl {
		
		public PickaxeCommand() {
			super("pickaxe");
			
			super.setAliases("pick");
			super.setDescription("Pickaxes allow you to gain some extra money and gain some materials");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="shop", aliases={"list"}, description="View all the pickaxes you can buy/craft", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void shop(CommandEvent event, @Context Connection connection) {
			Map<String, Object> data = r.table("bank").get(event.getAuthor().getId()).run(connection);
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setAuthor("Pickaxe Shop", null, event.getSelfUser().getEffectiveAvatarUrl());
			embed.setColor(Settings.EMBED_COLOUR);
			embed.setDescription("Pickaxes are a good way to gain some extra money aswell as some materials");
			embed.setFooter(String.format("Use %spickaxe buy <pickaxe> to buy a pickaxe | Balance: $%,d", event.getPrefix(), data == null ? 0 : (long) data.get("balance")), event.getAuthor().getEffectiveAvatarUrl());
			
			for (Pickaxe pickaxe : Pickaxe.ALL) {
				if (pickaxe.isBuyable() || pickaxe.isCraftable()) {
					String craftContent = "";
					if (pickaxe.isCraftable()) {
						List<ItemStack> craftingItems = pickaxe.getCraftingRecipe().getCraftingItems();
						for (ItemStack itemStack : craftingItems) {
							craftContent += itemStack.getAmount() + " " + itemStack.getItem().getName();
							if (craftingItems.indexOf(itemStack) != craftingItems.size() - 1) {
								craftContent += "\n";
							}
						}
					}
					
					if (craftContent.equals("")) {
						craftContent = "Not Craftable";
					}
					
					embed.addField(pickaxe.getName(), "Price: " + (pickaxe.isBuyable() ? String.format("$%,d", pickaxe.getPrice()) : "Not Buyable") + "\nCraft: " + craftContent + "\nDurability: " + pickaxe.getDurability(), true);
				}
			}
			
			event.reply(embed.build()).queue();
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="buy", description="Buy a pickaxe which is listed in the pickaxe shop")
		public void buy(CommandEvent event, @Context Connection connection, @Argument(value="pickaxe name", endless=true) String pickaxeName) {
			Get data = r.table("bank").get(event.getAuthor().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			Pickaxe pickaxe = Pickaxe.getPickaxeByName(pickaxeName);
			if (pickaxe == null) {
				event.reply("I could not find that pickaxe :no_entry:").queue();
				return;
			}
			
			if (!pickaxe.isBuyable()) {
				event.reply("That pickaxe is not buyable :no_entry:").queue();
				return;
			}
			
			if (dataRan == null) {
				event.reply("You do not have enough money to purchase a `" + pickaxe.getName() + "` :no_entry:").queue();
				return;
			}
			
			List<Map<String, Object>> userItems = (List<Map<String, Object>>) dataRan.get("items");
			if (EconomyUtils.hasPickaxe(userItems)) {
				event.reply("You already own a pickaxe :no_entry:").queue();
				return;
			}
			
			if ((long) dataRan.get("balance") >= pickaxe.getPrice()) {
				event.reply("You just bought a `" + pickaxe.getName() + "` for " + String.format("**$%,d**", pickaxe.getPrice()) + " :ok_hand:").queue();
				data.update(row -> r.hashMap("items", EconomyUtils.addItem(userItems, pickaxe, pickaxe.getStoreInfo())).with("balance", row.g("balance").sub(pickaxe.getPrice())).with("pickdur", pickaxe.getDurability())).runNoReply(connection);
			} else {
				event.reply("You do not have enough money to purchase a `" + pickaxe.getName() + "` :no_entry:").queue();
			}
		}
		
		@Command(value="info", description="Gives info of yours or another users pickaxe")
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void info(CommandEvent event, @Context Connection connection, @Argument(value="user", endless=true, nullDefault=true) String userArgument) {
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
			
			Map<String, Object> data = r.table("bank").get(member.getUser().getId()).run(connection);
			if (data == null) {
				event.reply((member.equals(event.getMember()) ? "You do" : "That user does") + " not have a pickaxe :no_entry:").queue();
				return;
			}
			
			Pickaxe pickaxe = EconomyUtils.getUserPickaxe(data);
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
		
		@SuppressWarnings("unchecked")
		@Command(value="craft", description="Craft a pickaxe which is in the pickaxe shop aslong as it displays as craftable")
		public void craft(CommandEvent event, @Context Connection connection, @Argument(value="pickaxe name", endless=true) String pickaxeName) {
			Get data = r.table("bank").get(event.getAuthor().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			Pickaxe pickaxe = Pickaxe.getPickaxeByName(pickaxeName);
			if (pickaxe == null) {
				event.reply("I could not find that pickaxe :no_entry:").queue();
				return;
			}
			
			if (!pickaxe.isCraftable()) {
				event.reply("That pickaxe is not craftable :no_entry:").queue();
				return;
			}
			
			if (dataRan == null) {
				event.reply("You do not have enough money to purchase a `" + pickaxe.getName() + "` :no_entry:").queue();
				return;
			}
			
			List<Map<String, Object>> userItems = (List<Map<String, Object>>) dataRan.get("items");
			if (EconomyUtils.hasPickaxe(userItems)) {
				event.reply("You already own a pickaxe :no_entry:").queue();
				return;
			}
			
			for (ItemStack craftItem : pickaxe.getCraftingRecipe().getCraftingItems()) {
				ItemStack userItem = EconomyUtils.getUserItem(userItems, craftItem.getItem());
				if (userItem.getAmount() < craftItem.getAmount()) {
					event.reply(String.format("You do not have `%,d %s` :no_entry:", craftItem.getAmount(), craftItem.getItem().getName())).queue();
					return;
				}
				
				userItems = EconomyUtils.removeItems(userItems, craftItem.getItem(), craftItem.getAmount());
			}
			
			event.reply("You just crafted a `" + pickaxe.getName() + "` with " + GeneralUtils.joinGrammatical(pickaxe.getCraftingRecipe().getCraftingItems()) + " :ok_hand:").queue();
			
			userItems = EconomyUtils.addItem(userItems, pickaxe);
			data.update(r.hashMap("items", userItems).with("pickdur", pickaxe.getDurability())).runNoReply(connection);
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="upgrade", description="Upgrade your current pickaxe, you can view the upgrades you can use pickaxe upgrades")
		public void upgrade(CommandEvent event, @Context Connection connection, @Argument(value="upgrade name") String upgradeName) {
			Get data = r.table("bank").get(event.getAuthor().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if (dataRan == null) {
				event.reply("You do not own a pickaxe :no_entry:").queue();
				return;
			}
			
			List<Map<String, Object>> items = (List<Map<String, Object>>) dataRan.get("items");
			if (!EconomyUtils.hasPickaxe(items)) {
				event.reply("You do not own a pickaxe :no_entry:").queue();
				return;
			}
			
			PickaxeUpgrade upgrade = PickaxeUpgrade.getPickaxeUpgradeByName(upgradeName);
			if (upgrade == null) {
				event.reply("I could not find that upgrade, the upgrades are " + GeneralUtils.joinGrammatical(Arrays.asList(PickaxeUpgrade.ALL)) + " :no_entry:").queue();
				return;
			}
			
			Pickaxe pickaxe = EconomyUtils.getUserPickaxe(dataRan);
			Pickaxe defaultPickaxe = pickaxe.getDefaultPickaxe();
			
			if (!pickaxe.isBuyable()) {
				event.reply("You cannot upgrade this pickaxe :no_entry:").queue();
				return;
			}
			
			long price = Math.round((defaultPickaxe.getPrice() * 0.025D) + (pickaxe.getUpgrades() * (defaultPickaxe.getPrice() * 0.015D)));
			if ((long) dataRan.get("balance") >= price) {
				Map<String, Object> storeInfo = pickaxe.getStoreInfo();
				storeInfo.put("upgrades", pickaxe.getUpgrades() + 1);
				storeInfo.put("price", pickaxe.getPrice() + Math.round(defaultPickaxe.getPrice() * 0.015D));
				
				if (upgrade.equals(PickaxeUpgrade.MONEY)) {
					long increase = Math.round(defaultPickaxe.getMinimumYield() * upgrade.getIncreasePerUpgrade());
					storeInfo.put("rand_min", pickaxe.getMinimumYield() + increase);
					storeInfo.put("rand_max", pickaxe.getMaximumYield() + increase);
					
					data.update(row -> r.hashMap("items", EconomyUtils.editItem(items, pickaxe, storeInfo)).with("balance", row.g("balance").sub(price))).runNoReply(connection);
				} else if (upgrade.equals(PickaxeUpgrade.DURABILITY)) {
					storeInfo.put("durability", pickaxe.getDurability() + upgrade.getIncreasePerUpgrade());
					
					data.update(row -> r.hashMap("items", EconomyUtils.editItem(items, pickaxe, storeInfo)).with("balance", row.g("balance").sub(price)).with("pickdur", row.g("pickdur").add(upgrade.getIncreasePerUpgrade()))).runNoReply(connection);
				} else if (upgrade.equals(PickaxeUpgrade.MULTIPLIER)) {
					storeInfo.put("multiplier", pickaxe.getMultiplier() + (pickaxe.getMultiplier() * upgrade.getIncreasePerUpgrade()));
					
					data.update(row -> r.hashMap("items", EconomyUtils.editItem(items, pickaxe, storeInfo)).with("balance", row.g("balance").sub(price))).runNoReply(connection);
				}
				
				event.reply("You just upgraded your " + upgrade.getName().toLowerCase() + " for your `" + pickaxe.getName() + "` for " + String.format("**$%,d**", price) + " :ok_hand:").queue();
			} else {
				event.reply("You cannot afford your pickaxes next upgrade it will cost you " + String.format("**$%,d**", price) + " :no_entry:").queue();
			}
		}
		
		@Command(value="upgrades", description="View all the upgrades you can put on your pickaxe and their current cost", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void upgrades(CommandEvent event, @Context Connection connection) {
			Map<String, Object> data = r.table("bank").get(event.getAuthor().getId()).run(connection);
			
			Pickaxe pickaxe = null, defaultPickaxe = null;
			if (data != null) {
				pickaxe = EconomyUtils.getUserPickaxe(data);
				if (pickaxe != null) {
					defaultPickaxe = pickaxe.getDefaultPickaxe();
				}
			}
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setFooter("Use pickaxe upgrade <upgrade> to apply an upgrade to your pickaxe | Balance: " + String.format("$%,d", data != null ? (long) data.get("balance") : 0), event.getAuthor().getEffectiveAvatarUrl());
			embed.setAuthor("Pickaxe Upgrades", null, event.getSelfUser().getEffectiveAvatarUrl());
			embed.setColor(event.getMember().getColor());
			
			for (PickaxeUpgrade upgrade : PickaxeUpgrade.ALL) {
				embed.addField(upgrade.getName(), "Description: " + upgrade.getDescription() + (pickaxe != null ? "\nPrice: " + String.format("$%,d", Math.round((defaultPickaxe.getPrice() * 0.025D) + (pickaxe.getUpgrades() * (defaultPickaxe.getPrice() * 0.015D)))) : ""), false);
			}
			
			event.reply(embed.build()).queue();
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="repair", description="Repair your current pickaxe with its deticated material if it has one", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		public void repair(CommandEvent event, @Context Connection connection, @Argument(value="durability", nullDefault=true) Integer durabilityAmount) {
			Get data = r.table("bank").get(event.getAuthor().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if (dataRan == null) {
				event.reply("You do not own a pickaxe :no_entry:").queue();
				return;
			}
			
			List<Map<String, Object>> items = (List<Map<String, Object>>) dataRan.get("items");
			if (!EconomyUtils.hasPickaxe(items)) {
				event.reply("You do not own a pickaxe :no_entry:").queue();
				return;
			}
			
			Pickaxe pickaxe = EconomyUtils.getUserPickaxe(dataRan);
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
			ItemStack userItem = EconomyUtils.getUserItem(items, repairItem);
			int cost = pickaxe.getAmountOfMaterialsForRepair(durabilityNeeded);
			if (userItem.getAmount() < cost) {
				long fixBy = pickaxe.getEstimateOfDurability(userItem.getAmount());
				event.reply("You do not have enough materials to fix your pickaxe by **" + durabilityNeeded + "** durability, you would need " + cost + " " + repairItem.getName() + "`. You can fix your pickaxe by **" + fixBy + "** durability with your current amount of `" + repairItem.getName() + "` :no_entry:").queue();
				return;
			}
			
			event.reply("It will cost you `" + cost + " " + repairItem.getName() + "` to repair your pickaxe by **" + durabilityNeeded + "** durability, are you sure you want to repair it? (Yes or No)").queue(message -> {
				PagedUtils.getConfirmation(event, 60, event.getAuthor(), confirmation -> {
					if (confirmation) {
						message.delete().queue();
						
						Map<String, Object> dataRanNew = data.run(connection);
						
						if ((long) dataRanNew.get("pickdur") != pickaxe.getCurrentDurability()) {
							event.reply("Your pickaxe durability has changed since answering :no_entry:").queue();
							return;
						}
						
						List<Map<String, Object>> itemsNew = (List<Map<String, Object>>) dataRanNew.get("items");
						ItemStack userItemNew = EconomyUtils.getUserItem(itemsNew, repairItem);
						if (userItemNew.getAmount() < cost) {
							long fixBy = pickaxe.getEstimateOfDurability(userItemNew.getAmount());
							event.reply("You do not have enough materials to fix your pickaxe by **" + durabilityNeeded + "** durability, you would need " + cost + " " + repairItem.getName() + "`. You can fix your pickaxe by **" + fixBy + "** durability with your current amount of `" + repairItem.getName() + "` :no_entry:").queue();
							return;
						}
						
						event.reply("You just repaired your pickaxe by **" + durabilityNeeded + "** durability :ok_hand:").queue();
						
						data.update(row -> r.hashMap("items", EconomyUtils.removeItems(itemsNew, repairItem, cost)).with("pickdur", row.g("pickdur").add(durabilityNeeded))).runNoReply(connection);
					} else {
						event.reply("Cancelled <:done:403285928233402378>").queue();
					}
				});
			});
		}
		
	}
	
	public class FishingRodCommand extends CommandImpl {
		
		public FishingRodCommand() {
			super("fishing rod");
			
			super.setAliases("fishingrod", "rod");
			super.setDescription("Fishing rods increase your yield of money per fish");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="shop", aliases={"list"}, description="View all the rods you can buy/craft")
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void shop(CommandEvent event, @Context Connection connection) {
			Map<String, Object> data = r.table("bank").get(event.getAuthor().getId()).run(connection);
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setAuthor("Fishing Rod Shop", null, event.getSelfUser().getEffectiveAvatarUrl());
			embed.setColor(Settings.EMBED_COLOUR);
			embed.setDescription("Fishing rods are a good way to gain some extra money from each fish");
			embed.setFooter(String.format("Use %sfishing rod buy <fishing rod> to buy a fishing rod | Balance: $%,d", event.getPrefix(), data == null ? 0 : (long) data.get("balance")), event.getAuthor().getEffectiveAvatarUrl());
			
			for (Rod rod : Rod.ALL) {
				if (rod.isBuyable() || rod.isCraftable()) {
					String craftContent = "";
					if (rod.isCraftable()) {
						List<ItemStack> craftingItems = rod.getCraftingRecipe().getCraftingItems();
						for (ItemStack itemStack : craftingItems) {
							craftContent += itemStack.getAmount() + " " + itemStack.getItem().getName();
							if (craftingItems.indexOf(itemStack) != craftingItems.size() - 1) {
								craftContent += "\n";
							}
						}
					}
					
					if (craftContent.equals("")) {
						craftContent = "Not Craftable";
					}
					
					embed.addField(rod.getName(), "Price: " + (rod.isBuyable() ? String.format("$%,d", rod.getPrice()) : "Not Buyable") + "\nCraft: " + craftContent + "\nDurability: " + rod.getDurability(), true);
				}
			}
			
			event.reply(embed.build()).queue();
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="buy", description="Buy a fishing rod which is listed in the fishing rod shop")
		public void buy(CommandEvent event, @Context Connection connection, @Argument(value="fishing rod name", endless=true) String rodName) {
			Get data = r.table("bank").get(event.getAuthor().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			Rod rod = Rod.getRodByName(rodName);
			if (rod == null) {
				event.reply("I could not find that fishing rod :no_entry:").queue();
				return;
			}
			
			if (!rod.isBuyable()) {
				event.reply("That fishing rod is not buyable :no_entry:").queue();
				return;
			}
			
			if (dataRan == null) {
				event.reply("You do not have enough money to purchase a `" + rod.getName() + "` :no_entry:").queue();
				return;
			}
			
			List<Map<String, Object>> userItems = (List<Map<String, Object>>) dataRan.get("items");
			if (EconomyUtils.hasRod(userItems)) {
				event.reply("You already own a fishing rod :no_entry:").queue();
				return;
			}
			
			if ((long) dataRan.get("balance") >= rod.getPrice()) {
				event.reply("You just bought a `" + rod.getName() + "` for " + String.format("**$%,d**", rod.getPrice()) + " :ok_hand:").queue();
				data.update(row -> r.hashMap("items", EconomyUtils.addItem(userItems, rod, rod.getStoreInfo())).with("balance", row.g("balance").sub(rod.getPrice())).with("roddur", rod.getDurability())).runNoReply(connection);
			} else {
				event.reply("You do not have enough money to purchase a `" + rod.getName() + "` :no_entry:").queue();
			}
		}
		
		@Command(value="info", description="Gives info of yours or another users fishing rod")
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void info(CommandEvent event, @Context Connection connection, @Argument(value="user", endless=true, nullDefault=true) String userArgument) {
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
			
			Map<String, Object> data = r.table("bank").get(member.getUser().getId()).run(connection);
			if (data == null) {
				event.reply((member.equals(event.getMember()) ? "You do" : "That user does") + " not have a fishing rod :no_entry:").queue();
				return;
			}
			
			Rod rod = EconomyUtils.getUserRod(data);
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
		
		@SuppressWarnings("unchecked")
		@Command(value="craft", description="Craft a fishing rod which is in the fishing rod shop aslong as it displays as craftable")
		public void craft(CommandEvent event, @Context Connection connection, @Argument(value="fishing rod name", endless=true) String rodName) {
			Get data = r.table("bank").get(event.getAuthor().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			Rod rod = Rod.getRodByName(rodName);
			if (rod == null) {
				event.reply("I could not find that fishing rod :no_entry:").queue();
				return;
			}
			
			if (!rod.isCraftable()) {
				event.reply("That fishing rod is not craftable :no_entry:").queue();
				return;
			}
			
			if (dataRan == null) {
				event.reply("You do not have enough money to purchase a `" + rod.getName() + "` :no_entry:").queue();
				return;
			}
			
			List<Map<String, Object>> userItems = (List<Map<String, Object>>) dataRan.get("items");
			if (EconomyUtils.hasRod(userItems)) {
				event.reply("You already own a fishing rod :no_entry:").queue();
				return;
			}
			
			for (ItemStack craftItem : rod.getCraftingRecipe().getCraftingItems()) {
				ItemStack userItem = EconomyUtils.getUserItem(userItems, craftItem.getItem());
				if (userItem.getAmount() < craftItem.getAmount()) {
					event.reply(String.format("You do not have `%,d %s` :no_entry:", craftItem.getAmount(), craftItem.getItem().getName())).queue();
					return;
				}
				
				userItems = EconomyUtils.removeItems(userItems, craftItem.getItem(), craftItem.getAmount());
			}
			
			event.reply("You just crafted a `" + rod.getName() + "` with " + GeneralUtils.joinGrammatical(rod.getCraftingRecipe().getCraftingItems()) + " :ok_hand:").queue();
			
			userItems = EconomyUtils.addItem(userItems, rod);
			data.update(r.hashMap("items", userItems).with("roddur", rod.getDurability())).runNoReply(connection);
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="upgrade", description="Upgrade your current fishing rod, you can view the upgrades you can use fishing rod upgrades")
		public void upgrade(CommandEvent event, @Context Connection connection, @Argument(value="upgrade name") String upgradeName) {
			Get data = r.table("bank").get(event.getAuthor().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if (dataRan == null) {
				event.reply("You do not own a fishing rod :no_entry:").queue();
				return;
			}
			
			List<Map<String, Object>> items = (List<Map<String, Object>>) dataRan.get("items");
			if (!EconomyUtils.hasRod(items)) {
				event.reply("You do not own a fishing rod :no_entry:").queue();
				return;
			}
			
			RodUpgrade upgrade = RodUpgrade.getRodUpgradeByName(upgradeName);
			if (upgrade == null) {
				event.reply("I could not find that upgrade, the upgrades are " + GeneralUtils.joinGrammatical(Arrays.asList(RodUpgrade.ALL)) + " :no_entry:").queue();
				return;
			}
			
			Rod rod = EconomyUtils.getUserRod(dataRan);
			Rod defaultRod = rod.getDefaultRod();
			
			if (!rod.isBuyable()) {
				event.reply("You cannot upgrade this fishing rod :no_entry:").queue();
				return;
			}
			
			long price = Math.round((defaultRod.getPrice() * 0.025D) + (rod.getUpgrades() * (defaultRod.getPrice() * 0.015D)));
			if ((long) dataRan.get("balance") >= price) {
				Map<String, Object> storeInfo = rod.getStoreInfo();
				storeInfo.put("upgrades", rod.getUpgrades() + 1);
				storeInfo.put("price", rod.getPrice() + Math.round(defaultRod.getPrice() * 0.015D));
				
				if (upgrade.equals(RodUpgrade.MONEY)) {
					long increase = Math.round(defaultRod.getMinimumYield() * upgrade.getIncreasePerUpgrade());
					storeInfo.put("rand_min", rod.getMinimumYield() + increase);
					storeInfo.put("rand_max", rod.getMaximumYield() + increase);
					
					data.update(row -> r.hashMap("items", EconomyUtils.editItem(items, rod, storeInfo)).with("balance", row.g("balance").sub(price))).runNoReply(connection);
				} else if (upgrade.equals(RodUpgrade.DURABILITY)) {
					storeInfo.put("durability", rod.getDurability() + upgrade.getIncreasePerUpgrade());
					
					data.update(row -> r.hashMap("items", EconomyUtils.editItem(items, rod, storeInfo)).with("balance", row.g("balance").sub(price)).with("roddur", row.g("roddur").add(upgrade.getIncreasePerUpgrade()))).runNoReply(connection);
				}
				
				event.reply("You just upgraded your " + upgrade.getName().toLowerCase() + " for your `" + rod.getName() + "` for " + String.format("**$%,d**", price) + " :ok_hand:").queue();
			} else {
				event.reply("You cannot afford your fishing rods next upgrade it will cost you " + String.format("**$%,d**", price) + " :no_entry:").queue();
			}
		}
		
		@Command(value="upgrades", description="View all the upgrades you can put on your fishing rod and their current cost", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void upgrades(CommandEvent event, @Context Connection connection) {
			Map<String, Object> data = r.table("bank").get(event.getAuthor().getId()).run(connection);
			
			Rod rod = null, defaultRod = null;
			if (data != null) {
				rod = EconomyUtils.getUserRod(data);
				if (rod != null) {
					defaultRod = rod.getDefaultRod();
				}
			}
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setFooter("Use fishing rod upgrade <upgrade> to apply an upgrade to your fishing rod | Balance: " + String.format("$%,d", data != null ? (long) data.get("balance") : 0), event.getAuthor().getEffectiveAvatarUrl());
			embed.setAuthor("Fishing Rod Upgrades", null, event.getSelfUser().getEffectiveAvatarUrl());
			embed.setColor(event.getMember().getColor());
			
			for (RodUpgrade upgrade : RodUpgrade.ALL) {
				embed.addField(upgrade.getName(), "Description: " + upgrade.getDescription() + (rod != null ? "\nPrice: " + String.format("$%,d", Math.round((defaultRod.getPrice() * 0.025D) + (rod.getUpgrades() * (defaultRod.getPrice() * 0.015D)))) : ""), false);
			}
			
			event.reply(embed.build()).queue();
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="repair", description="Repair your current fishing rod with its deticated material if it has one", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		public void repair(CommandEvent event, @Context Connection connection, @Argument(value="durability", nullDefault=true) Integer durabilityAmount) {
			Get data = r.table("bank").get(event.getAuthor().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if (dataRan == null) {
				event.reply("You do not own a fishing rod :no_entry:").queue();
				return;
			}
			
			List<Map<String, Object>> items = (List<Map<String, Object>>) dataRan.get("items");
			if (!EconomyUtils.hasRod(items)) {
				event.reply("You do not own a fishing rod :no_entry:").queue();
				return;
			}
			
			Rod rod = EconomyUtils.getUserRod(dataRan);
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
			ItemStack userItem = EconomyUtils.getUserItem(items, repairItem);
			int cost = rod.getAmountOfMaterialsForRepair(durabilityNeeded);
			if (userItem.getAmount() < cost) {
				long fixBy = rod.getEstimateOfDurability(userItem.getAmount());
				event.reply("You do not have enough materials to fix your fishing rod by **" + durabilityNeeded + "** durability, you would need " + cost + " " + repairItem.getName() + "`. You can fix your fishing rod by **" + fixBy + "** durability with your current amount of `" + repairItem.getName() + "` :no_entry:").queue();
				return;
			}
			
			event.reply("It will cost you `" + cost + " " + repairItem.getName() + "` to repair your fishing rod by **" + durabilityNeeded + "** durability, are you sure you want to repair it? (Yes or No)").queue(message -> {
				PagedUtils.getConfirmation(event, 60, event.getAuthor(), confirmation -> {
					if (confirmation) {
						message.delete().queue();
						
						Map<String, Object> dataRanNew = data.run(connection);
						
						if ((long) dataRanNew.get("roddur") != rod.getCurrentDurability()) {
							event.reply("Your fishing rod durability has changed since answering :no_entry:").queue();
							return;
						}
						
						List<Map<String, Object>> itemsNew = (List<Map<String, Object>>) dataRanNew.get("items");
						ItemStack userItemNew = EconomyUtils.getUserItem(itemsNew, repairItem);
						if (userItemNew.getAmount() < cost) {
							long fixBy = rod.getEstimateOfDurability(userItemNew.getAmount());
							event.reply("You do not have enough materials to fix your fishing rod by **" + durabilityNeeded + "** durability, you would need " + cost + " " + repairItem.getName() + "`. You can fix your fishing rod by **" + fixBy + "** durability with your current amount of `" + repairItem.getName() + "` :no_entry:").queue();
							return;
						}
						
						event.reply("You just repaired your fishing rod by **" + durabilityNeeded + "** durability :ok_hand:").queue();
						
						data.update(row -> r.hashMap("items", EconomyUtils.removeItems(itemsNew, repairItem, cost)).with("roddur", row.g("roddur").add(durabilityNeeded))).runNoReply(connection);
					} else {
						event.reply("Cancelled <:done:403285928233402378>").queue();
					}
				});
			});
		}
	}
	
	public class AxeCommand extends CommandImpl {
		
		public AxeCommand() {
			super("axe");
			
			super.setDescription("Axes allow you to gain wood by using the chop command");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="shop", aliases={"list"}, description="View all the axes you can buy/craft", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void shop(CommandEvent event, @Context Connection connection) {
			Map<String, Object> data = r.table("bank").get(event.getAuthor().getId()).run(connection);
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setAuthor("Axe Shop", null, event.getSelfUser().getEffectiveAvatarUrl());
			embed.setColor(Settings.EMBED_COLOUR);
			embed.setDescription("Axes are a quick and easy way to gain some wood so you can craft");
			embed.setFooter(String.format("Use %saxe buy <axe> to buy a axe | Balance: $%,d", event.getPrefix(), data == null ? 0 : (long) data.get("balance")), event.getAuthor().getEffectiveAvatarUrl());
			
			for (Axe axe : Axe.ALL) {
				if (axe.isBuyable() || axe.isCraftable()) {
					String craftContent = "";
					if (axe.isCraftable()) {
						List<ItemStack> craftingItems = axe.getCraftingRecipe().getCraftingItems();
						for (ItemStack itemStack : craftingItems) {
							craftContent += itemStack.getAmount() + " " + itemStack.getItem().getName();
							if (craftingItems.indexOf(itemStack) != craftingItems.size() - 1) {
								craftContent += "\n";
							}
						}
					}
					
					if (craftContent.equals("")) {
						craftContent = "Not Craftable";
					}
					
					embed.addField(axe.getName(), "Price: " + (axe.isBuyable() ? String.format("$%,d", axe.getPrice()) : "Not Buyable") + "\nCraft: " + craftContent + "\nDurability: " + axe.getDurability(), true);
				}
			}
			
			event.reply(embed.build()).queue();
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="buy", description="Buy a axe which is listed in the axe shop")
		public void buy(CommandEvent event, @Context Connection connection, @Argument(value="axe name", endless=true) String axeName) {
			Get data = r.table("bank").get(event.getAuthor().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			Axe axe = Axe.getAxeByName(axeName);
			if (axe == null) {
				event.reply("I could not find that axe :no_entry:").queue();
				return;
			}
			
			if (!axe.isBuyable()) {
				event.reply("That axe is not buyable :no_entry:").queue();
				return;
			}
			
			if (dataRan == null) {
				event.reply("You do not have enough money to purchase a `" + axe.getName() + "` :no_entry:").queue();
				return;
			}
			
			List<Map<String, Object>> userItems = (List<Map<String, Object>>) dataRan.get("items");
			if (EconomyUtils.hasAxe(userItems)) {
				event.reply("You already own an axe :no_entry:").queue();
				return;
			}
			
			if ((long) dataRan.get("balance") >= axe.getPrice()) {
				event.reply("You just bought a `" + axe.getName() + "` for " + String.format("**$%,d**", axe.getPrice()) + " :ok_hand:").queue();
				data.update(row -> r.hashMap("items", EconomyUtils.addItem(userItems, axe, axe.getStoreInfo())).with("balance", row.g("balance").sub(axe.getPrice())).with("axedur", axe.getDurability())).runNoReply(connection);
			} else {
				event.reply("You do not have enough money to purchase a `" + axe.getName() + "` :no_entry:").queue();
			}
		}
		
		@Command(value="info", description="Gives info of yours or another users axe")
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void info(CommandEvent event, @Context Connection connection, @Argument(value="user", endless=true, nullDefault=true) String userArgument) {
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
			
			Map<String, Object> data = r.table("bank").get(member.getUser().getId()).run(connection);
			if (data == null) {
				event.reply((member.equals(event.getMember()) ? "You do" : "That user does") + " not have an axe :no_entry:").queue();
				return;
			}
			
			Axe axe = EconomyUtils.getUserAxe(data);
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
		
		@SuppressWarnings("unchecked")
		@Command(value="craft", description="Craft a axe which is in the axe shop aslong as it displays as craftable")
		public void craft(CommandEvent event, @Context Connection connection, @Argument(value="axe name", endless=true) String axeName) {
			Get data = r.table("bank").get(event.getAuthor().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			Axe axe = Axe.getAxeByName(axeName);
			if (axe == null) {
				event.reply("I could not find that axe :no_entry:").queue();
				return;
			}
			
			if (!axe.isCraftable()) {
				event.reply("That axe is not craftable :no_entry:").queue();
				return;
			}
			
			if (dataRan == null) {
				event.reply("You do not have enough money to purchase a `" + axe.getName() + "` :no_entry:").queue();
				return;
			}
			
			List<Map<String, Object>> userItems = (List<Map<String, Object>>) dataRan.get("items");
			if (EconomyUtils.hasAxe(userItems)) {
				event.reply("You already own an axe :no_entry:").queue();
				return;
			}
			
			for (ItemStack craftItem : axe.getCraftingRecipe().getCraftingItems()) {
				ItemStack userItem = EconomyUtils.getUserItem(userItems, craftItem.getItem());
				if (userItem.getAmount() < craftItem.getAmount()) {
					event.reply(String.format("You do not have `%,d %s` :no_entry:", craftItem.getAmount(), craftItem.getItem().getName())).queue();
					return;
				}
				
				userItems = EconomyUtils.removeItems(userItems, craftItem.getItem(), craftItem.getAmount());
			}
			
			event.reply("You just crafted a `" + axe.getName() + "` with " + GeneralUtils.joinGrammatical(axe.getCraftingRecipe().getCraftingItems()) + " :ok_hand:").queue();
			
			userItems = EconomyUtils.addItem(userItems, axe);
			data.update(r.hashMap("items", userItems).with("axedur", axe.getDurability())).runNoReply(connection);
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="upgrade", description="Upgrade your current axe, you can view the upgrades you can use axe upgrades")
		public void upgrade(CommandEvent event, @Context Connection connection, @Argument(value="upgrade name") String upgradeName) {
			Get data = r.table("bank").get(event.getAuthor().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if (dataRan == null) {
				event.reply("You do not own an axe :no_entry:").queue();
				return;
			}
			
			List<Map<String, Object>> items = (List<Map<String, Object>>) dataRan.get("items");
			if (!EconomyUtils.hasAxe(items)) {
				event.reply("You do not own an axe :no_entry:").queue();
				return;
			}
			
			AxeUpgrade upgrade = AxeUpgrade.getAxeUpgradeByName(upgradeName);
			if (upgrade == null) {
				event.reply("I could not find that upgrade, the upgrades are " + GeneralUtils.joinGrammatical(Arrays.asList(AxeUpgrade.ALL)) + " :no_entry:").queue();
				return;
			}
			
			Axe axe = EconomyUtils.getUserAxe(dataRan);
			Axe defaultAxe = axe.getDefaultAxe();
			
			if (!axe.isBuyable()) {
				event.reply("You cannot upgrade this axe :no_entry:").queue();
				return;
			}
			
			long price = Math.round((defaultAxe.getPrice() * 0.025D) + (axe.getUpgrades() * (defaultAxe.getPrice() * 0.015D)));
			if ((long) dataRan.get("balance") >= price) {
				Map<String, Object> storeInfo = axe.getStoreInfo();
				storeInfo.put("upgrades", axe.getUpgrades() + 1);
				storeInfo.put("price", axe.getPrice() + Math.round(defaultAxe.getPrice() * 0.015D));
				
				if (upgrade.equals(AxeUpgrade.MULTIPLIER)) {
					storeInfo.put("multiplier", axe.getMultiplier() + (axe.getMultiplier() * upgrade.getIncreasePerUpgrade()));
					
					data.update(row -> r.hashMap("items", EconomyUtils.editItem(items, axe, storeInfo)).with("balance", row.g("balance").sub(price))).runNoReply(connection);
				} else if (upgrade.equals(AxeUpgrade.DURABILITY)) {
					storeInfo.put("durability", axe.getDurability() + upgrade.getIncreasePerUpgrade());
					
					data.update(row -> r.hashMap("items", EconomyUtils.editItem(items, axe, storeInfo)).with("balance", row.g("balance").sub(price)).with("axedur", row.g("axedur").add(upgrade.getIncreasePerUpgrade()))).runNoReply(connection);
				}
				
				event.reply("You just upgraded your " + upgrade.getName().toLowerCase() + " for your `" + axe.getName() + "` for " + String.format("**$%,d**", price) + " :ok_hand:").queue();
			} else {
				event.reply("You cannot afford your axes next upgrade it will cost you " + String.format("**$%,d**", price) + " :no_entry:").queue();
			}
		}
		
		@Command(value="upgrades", description="View all the upgrades you can put on your axe and their current cost", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void upgrades(CommandEvent event, @Context Connection connection) {
			Map<String, Object> data = r.table("bank").get(event.getAuthor().getId()).run(connection);
			
			Axe axe = null, defaultAxe = null;
			if (data != null) {
				axe = EconomyUtils.getUserAxe(data);
				if (axe != null) {
					defaultAxe = axe.getDefaultAxe();
				}
			}
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setFooter("Use axe upgrade <upgrade> to apply an upgrade to your axe | Balance: " + String.format("$%,d", data != null ? (long) data.get("balance") : 0), event.getAuthor().getEffectiveAvatarUrl());
			embed.setAuthor("Axe Upgrades", null, event.getSelfUser().getEffectiveAvatarUrl());
			embed.setColor(event.getMember().getColor());
			
			for (AxeUpgrade upgrade : AxeUpgrade.ALL) {
				embed.addField(upgrade.getName(), "Description: " + upgrade.getDescription() + (axe != null ? "\nPrice: " + String.format("$%,d", Math.round((defaultAxe.getPrice() * 0.025D) + (axe.getUpgrades() * (defaultAxe.getPrice() * 0.015D)))) : ""), false);
			}
			
			event.reply(embed.build()).queue();
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="repair", description="Repair your current axe with its deticated material if it has one", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		public void repair(CommandEvent event, @Context Connection connection, @Argument(value="durability", nullDefault=true) Integer durabilityAmount) {
			Get data = r.table("bank").get(event.getAuthor().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if (dataRan == null) {
				event.reply("You do not own an axe :no_entry:").queue();
				return;
			}
			
			List<Map<String, Object>> items = (List<Map<String, Object>>) dataRan.get("items");
			if (!EconomyUtils.hasAxe(items)) {
				event.reply("You do not own an axe :no_entry:").queue();
				return;
			}
			
			Axe axe = EconomyUtils.getUserAxe(dataRan);
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
			ItemStack userItem = EconomyUtils.getUserItem(items, repairItem);
			int cost = axe.getAmountOfMaterialsForRepair(durabilityNeeded);
			if (userItem.getAmount() < cost) {
				long fixBy = axe.getEstimateOfDurability(userItem.getAmount());
				event.reply("You do not have enough materials to fix your axe by **" + durabilityNeeded + "** durability, you would need " + cost + " " + repairItem.getName() + "`. You can fix your axe by **" + fixBy + "** durability with your current amount of `" + repairItem.getName() + "` :no_entry:").queue();
				return;
			}
			
			event.reply("It will cost you `" + cost + " " + repairItem.getName() + "` to repair your axe by **" + durabilityNeeded + "** durability, are you sure you want to repair it? (Yes or No)").queue(message -> {
				PagedUtils.getConfirmation(event, 60, event.getAuthor(), confirmation -> {
					if (confirmation) {
						message.delete().queue();
						
						Map<String, Object> dataRanNew = data.run(connection);
						
						if ((long) dataRanNew.get("axedur") != axe.getCurrentDurability()) {
							event.reply("Your axe durability has changed since answering :no_entry:").queue();
							return;
						}
						
						List<Map<String, Object>> itemsNew = (List<Map<String, Object>>) dataRanNew.get("items");
						ItemStack userItemNew = EconomyUtils.getUserItem(itemsNew, repairItem);
						if (userItemNew.getAmount() < cost) {
							long fixBy = axe.getEstimateOfDurability(userItemNew.getAmount());
							event.reply("You do not have enough materials to fix your axe by **" + durabilityNeeded + "** durability, you would need " + cost + " " + repairItem.getName() + "`. You can fix your axe by **" + fixBy + "** durability with your current amount of `" + repairItem.getName() + "` :no_entry:").queue();
							return;
						}
						
						event.reply("You just repaired your axe by **" + durabilityNeeded + "** durability :ok_hand:").queue();
						
						data.update(row -> r.hashMap("items", EconomyUtils.removeItems(itemsNew, repairItem, cost)).with("axedur", row.g("axedur").add(durabilityNeeded))).runNoReply(connection);
					} else {
						event.reply("Cancelled <:done:403285928233402378>").queue();
					}
				});
			});
		}
	}
	
	@SuppressWarnings("unchecked")
	@Command(value="give", aliases={"gift"}, description="Give money to others users, there is a 5% tax if you do not own a tax avoider", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void give(CommandEvent event, @Context Connection connection, @Argument(value="user") String userArgument, @Argument(value="amount") String amountArgument) {
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
		
		Get data = r.table("bank").get(event.getAuthor().getId());
		Map<String, Object> dataRan = data.run(connection);
		
		if (dataRan == null) {
			event.reply("You do not have any money :no_entry:").queue();
			return;
		}
		
		long fullAmount, tax, amount, balance = (long) dataRan.get("balance");
		try {
			fullAmount = EconomyUtils.convertMoneyArgument(balance, amountArgument);
		} catch(IllegalArgumentException e) {
			event.reply(e.getMessage()).queue();
			return;
		}
		
		if (fullAmount > balance) {
			event.reply("You do not have enough to money give " + String.format("**$%,d**", fullAmount) + " to " + member.getUser().getAsTag() + " :no_entry:").queue();
			return;
		}
		
		List<Map<String, Object>> items = (List<Map<String, Object>>) dataRan.get("items");
		ItemStack taxAvoiders = EconomyUtils.getUserItem(items, Booster.TAX_AVOIDER);
		if (taxBot) {
			tax = fullAmount;
			amount = 0;
		} else if (taxAvoiders.getAmount() > 0) {
			tax = 0;
			amount = fullAmount;
		} else {
			tax = (long) (fullAmount * 0.05D);
			amount = (long) (fullAmount * 0.95D);
		}
	
		if (amount > 0) {
			EconomyUtils.insertData(member.getUser()).run(connection, OptArgs.of("durability", "soft"));
		}
		
		Get userData = r.table("bank").get(member.getUser().getId());
		Map<String, Object> userDataRan = userData.run(connection);
		Get taxData = r.table("tax").get("tax");
		Map<String, Object> taxDataRan = taxData.run(connection);
		
		long authorBalance = (long) dataRan.get("balance") - fullAmount;
		long userBalance = taxBot ? (long) taxDataRan.get("tax") + tax : (long) userDataRan.get("balance") + amount;
		EmbedBuilder embed = new EmbedBuilder();
		embed.setAuthor(event.getAuthor().getName() + " → " + member.getUser().getName(), null, "https://cdn0.iconfinder.com/data/icons/social-messaging-ui-color-shapes/128/money-circle-green-3-512.png");
		embed.setColor(event.getMember().getColor());
		embed.setDescription(String.format("You have gifted **$%,d** to **%s**\n\n%s's new balance: **$%,d**\n%s's new balance: **$%,d**", taxBot ? tax : amount, member.getUser().getName(), event.getAuthor().getName(), authorBalance, member.getUser().getName(), userBalance));
		embed.setFooter(String.format("$%,d (%d%%) tax was taken", tax, Math.round((double) tax / fullAmount * 100)), null);
		
		event.reply(embed.build()).queue();
		
		if (amount > 0) {
			userData.update(row -> r.hashMap("balance", row.g("balance").add(amount))).runNoReply(connection);
		}
		
		if (tax > 0) {
			taxData.update(row -> r.hashMap("tax", row.g("tax").add(tax))).runNoReply(connection);
		}
		
		data.update(row -> r.hashMap("balance", row.g("balance").sub(fullAmount)).with("items", tax == 0 ? EconomyUtils.removeItem(items, taxAvoiders.getItem()) : row.g("items"))).runNoReply(connection);
	}
	
	@SuppressWarnings("unchecked")
	@Command(value="give materials", aliases={"givematerials", "give mats", "givemats"}, description="Give another user some materials you have, 5% the price of the materials will be taxed")
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void giveMaterials(CommandEvent event, @Context Connection connection, @Argument(value="user") String userArgument, @Argument(value="item", endless=true) String itemArgument) {
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
		
		Get data = r.table("bank").get(event.getAuthor().getId());
		Map<String, Object> dataRan = data.run(connection);
		
		if (dataRan == null) {
			event.reply("You do not have any items :no_entry:").queue();
			return;
		}
		
		List<Map<String, Object>> items = (List<Map<String, Object>>) dataRan.get("items");
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
		
		String itemString = String.format("%,d %s", itemAmount, item.getName());
		ItemStack userItem = EconomyUtils.getUserItem(items, item);
		if (BigInteger.valueOf(userItem.getAmount()).compareTo(itemAmount) != -1) {
			EconomyUtils.insertData(member.getUser()).run(connection, OptArgs.of("durability", "soft"));
			Get userData = r.table("bank").get(member.getUser().getId());
			Map<String, Object> userDataRan = userData.run(connection);
			
			long itemAmountLong = itemAmount.longValue();
			List<Map<String, Object>> userItems = (List<Map<String, Object>>) userDataRan.get("items");
			ItemStack memberItem = EconomyUtils.getUserItem(userItems, item);
			
			long fullPrice = item.getPrice() * itemAmountLong;
			long tax = (long) (fullPrice * 0.05D);
			long newAuthorAmount = userItem.getAmount() - itemAmountLong;
			long newUserAmount = memberItem.getAmount() + itemAmountLong;
			EmbedBuilder embed = new EmbedBuilder();
			embed.setColor(event.getMember().getColor());
			embed.setAuthor(event.getAuthor().getName() + " → " + member.getUser().getName(), null, "https://cdn0.iconfinder.com/data/icons/social-messaging-ui-color-shapes/128/money-circle-green-3-512.png");
			embed.setDescription(String.format("You have gifted **%s** to **%s**\n\n%s's new %s amount: **%,d %s**\n%s's new %s amount: **%,d %s**", itemString, member.getUser().getName(), event.getAuthor().getName(), item.getName(), newAuthorAmount, item.getName(), member.getUser().getName(), item.getName(), newUserAmount, item.getName()));
			embed.setFooter(String.format("$%,d (%d%%) tax was taken", tax, Math.round((double) tax / fullPrice * 100)), null);
			event.reply(embed.build()).queue();
			
			if (tax > 0) {
				r.table("tax").get("tax").update(row -> r.hashMap("tax", row.g("tax").add(tax))).runNoReply(connection);
			}
			
			userData.update(r.hashMap("items", EconomyUtils.addItems(userItems, item, itemAmountLong))).runNoReply(connection);
			data.update(row -> r.hashMap("balance", row.g("balance").sub(tax)).with("items", EconomyUtils.removeItems(items, item, itemAmountLong))).runNoReply(connection);
		} else {
			event.reply("You do not have `" + itemString + "` to give to " + member.getUser().getAsTag() + " :no_entry:").queue();
		}
	}
	
	@Command(value="russain roulette", aliases={"rusr", "russianroulette", "roulette"}, description="Put a gun to your head and choose how many bullets go in the chanber if you're shot you lose your bet if you win you gain your winnings and cartain amount depending on how many bullets you put in the chanmber")
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void russianRoulette(CommandEvent event, @Context Connection connection, @Argument(value="bullets") int bullets, @Argument(value="bet", endless=true) String betArgument) {
		if (bullets < 1 || bullets > 5) {
			event.reply("The bullet amount has to be a number between 1 and 5 :no_entry:").queue();
		}
		
		Get data = r.table("bank").get(event.getAuthor().getId());
		Map<String, Object> dataRan = data.run(connection);
		
		if (dataRan == null) {
			event.reply("You do not have any money to bet :no_entry:").queue();
			return;
		}
		
		long bet, balance = (long) dataRan.get("balance");
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
		if (bullets - 1 < randomNumber) {
			long winnings = (long) Math.ceil((5.7D * bet) / (6 - bullets));
			embed.setDescription(String.format("You're lucky, you get to live another day.\nYou won **$%,d**", winnings));
			event.reply(embed.build()).queue();
			
			long actualWinnings = winnings - bet;
			data.update(row -> r.hashMap("balance", row.g("balance").add(actualWinnings)).with("winnings", row.g("winnings").add(actualWinnings))).runNoReply(connection);
		} else {
			embed.setDescription(String.format("You were shot :gun:\nYou lost your bet of **$%,d**", bet));
			event.reply(embed.build()).queue();
			
			data.update(row -> r.hashMap("balance", row.g("balance").sub(bet)).with("winnings", row.g("winnings").sub(bet))).runNoReply(connection);
		}
	}
	
	public class FactoryCommand extends CommandImpl {
		
		public FactoryCommand() {
			super("factory");
			
			super.setAliases("factories");
			super.setDescription("Buy factories with materials, factories yield you money every 12 hours the more factories the more money");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="shop", aliases={"list"}, description="View all the factories you can buy with your current materials", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void shop(CommandEvent event, @Context Connection connection) {
			List<Map<String, Object>> items = r.table("bank").get(event.getAuthor().getId()).g("items").default_(new Object[0]).run(connection);
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setColor(Settings.EMBED_COLOUR);
			embed.setAuthor("Factory Shop", null, event.getSelfUser().getEffectiveAvatarUrl());
			embed.setDescription("Factories are a good way to make money from materials you have gained through mining");
			embed.setFooter("Use " + event.getPrefix() + "factory buy <factory> to buy a factory", event.getAuthor().getEffectiveAvatarUrl());
			
			for (Factory factory : Factory.ALL) {
				if (!factory.isHidden()) {
					ItemStack userItem = EconomyUtils.getUserItem(items, factory.getMaterial());
					embed.addField(factory.getName(), String.format("Price: %,d/%,d %s (%,d)", userItem.getAmount(), factory.getMaterialAmount(), factory.getMaterial().getName(), (long) Math.floor((double) userItem.getAmount() / factory.getMaterialAmount())), true);
				}
			}
			
			event.reply(embed.build()).queue();
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="buy", description="Buy a factory which is listed in factory shop")
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void buy(CommandEvent event, @Context Connection connection, @Argument(value="factory name", endless=true) String factoryArgument) {
			Get data = r.table("bank").get(event.getAuthor().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if (factoryArgument.toLowerCase().equals("all")) {
				if (dataRan == null) {
					event.reply("You do not have enough materials to buy any factories :no_entry:").queue();
					return;
				}
				
				List<Map<String, Object>> items = (List<Map<String, Object>>) dataRan.get("items");
				List<ItemStack> factoriesBought = new ArrayList<>();
				for (Factory factory : Factory.ALL) {
					if (!factory.isHidden()) {
						ItemStack userItem = EconomyUtils.getUserItem(items, factory.getMaterial());
						long buyableAmount = (long) Math.floor((double) userItem.getAmount() / factory.getMaterialAmount());
						if (buyableAmount > 0) {
							factoriesBought.add(new ItemStack(factory, buyableAmount));
							items = EconomyUtils.removeItems(items, factory.getMaterial(), factory.getMaterialAmount() * buyableAmount);
							items = EconomyUtils.addItems(items, factory, buyableAmount);
						}
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
				event.reply(embed.build()).queue();
				
				data.update(r.hashMap("items", items)).runNoReply(connection);
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
				
				if (factory.isHidden()) {
					event.reply("You cannot purchase that factory :no_entry:").queue();
					return;
				}
				
				if (dataRan == null) {
					event.reply(String.format("You do not have enough `%s` to buy `%,d %s` :no_entry:", factory.getMaterial().getName(), factoryAmount, factory.getName())).queue();
					return;
				}
				
				List<Map<String, Object>> items = (List<Map<String, Object>>) dataRan.get("items");
				ItemStack userItem = EconomyUtils.getUserItem(items, factory.getMaterial());
				
				BigInteger price = factoryAmount.multiply(BigInteger.valueOf(factory.getMaterialAmount()));
				if (BigInteger.valueOf(userItem.getAmount()).compareTo(price) != -1) {
					event.reply(String.format("You just bought `%,d %s` :ok_hand:", factoryAmount, factory.getName())).queue();
					
					items = EconomyUtils.removeItems(items, factory.getMaterial(), price.longValue());
					items = EconomyUtils.addItems(items, factory, factoryAmount.longValue());
					data.update(r.hashMap("items", items)).runNoReply(connection);
				} else {
					event.reply(String.format("You do not have enough `%s` to buy `%,d %s` :no_entry:", factory.getMaterial().getName(), factoryAmount, factory.getName())).queue();
					return;
				}
			}
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="collect", description="Collect all the money from your factories you own", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void collect(CommandEvent event, @Context Connection connection) {
			Get data = r.table("bank").get(event.getAuthor().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if (dataRan == null) {
				event.reply("You do not own any factories :no_entry:").queue();
				return;
			}
			
			double timestampNow = (double) Clock.systemUTC().instant().toEpochMilli() / 1000;
			Double factoryTime = (Double) dataRan.get("factorytime");
			
			if (factoryTime == null) {
				long moneyGained = 0;
				StringBuilder factoryContent = new StringBuilder();
				List<Map<String, Object>> items = (List<Map<String, Object>>) dataRan.get("items");
				for (Map<String, Object> item : items) {
					for (Factory factory : Factory.ALL) {
						if (item.get("name").equals(factory.getName())) {
							long amount = (long) item.get("amount");
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
				event.reply(embed.build()).queue();
				
				long moneyGainedData = moneyGained;
				data.update(row -> r.hashMap("balance", row.g("balance").add(moneyGainedData)).with("factorytime", timestampNow)).runNoReply(connection);
			} else if (timestampNow - factoryTime <= EconomyUtils.FACTORY_COOLDOWN) {
				event.reply("Slow down! You can collect from your factory in " + TimeUtils.toTimeString((long) (factoryTime - timestampNow + EconomyUtils.FACTORY_COOLDOWN), ChronoUnit.SECONDS) + " :stopwatch:").queue();
			} else {
				long moneyGained = 0;
				StringBuilder factoryContent = new StringBuilder();
				List<Map<String, Object>> items = (List<Map<String, Object>>) dataRan.get("items");
				for (Map<String, Object> item : items) {
					for (Factory factory : Factory.ALL) {
						if (item.get("name").equals(factory.getName())) {
							long amount = (long) item.get("amount");
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
				event.reply(embed.build()).queue();
				
				long moneyGainedData = moneyGained;
				data.update(row -> r.hashMap("balance", row.g("balance").add(moneyGainedData)).with("factorytime", timestampNow)).runNoReply(connection);
			}
		}
		
	}
	
	public class AuctionCommand extends CommandImpl {
		
		public AuctionCommand() {
			super("auction");
			
			super.setDescription("Put your items on the auction house for other users to buy");
			super.setAliases("auction house", "auctionhouse");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
	
		@SuppressWarnings("unchecked")
		@Command(value="list", description="View a list of all the items or a specified item on the auction house")
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void list(CommandEvent event, @Context Connection connection, @Argument(value="item name", endless=true, nullDefault=true) String itemName) {
			List<Map<String, Object>> shownData;
			if (itemName != null) {
				Item item = EconomyUtils.getItem(itemName);
				if (item == null) {
					event.reply("I could not find that item :no_entry:").queue();
					return;
				}
				
				Cursor<Map<String, Object>> cursor = r.table("auction").filter(d -> d.g("item").g("name").eq(item.getName())).run(connection);
				shownData = cursor.toList();
				
				if (shownData.isEmpty()) {
					event.reply("I could not find any auctions for `" + item.getName() + "` :no_entry:").queue();
					return;
				}
			} else {
				Cursor<Map<String, Object>> cursor = r.table("auction").run(connection);
				shownData = cursor.toList();
			}
			
			shownData.sort((a, b) -> Double.compare((long) a.get("price") / (long) ((Map<String, Object>) a.get("item")).get("amount"), (long) b.get("price") / (long) ((Map<String, Object>) a.get("item")).get("amount")));
			
			PagedResult<Map<String, Object>> paged = new PagedResult<>(shownData)
					.setPerPage(6)
					.setDeleteMessage(false)
					.setCustom(true)
					.setCustomFunction(page -> {
						List<Map<String, Object>> list = page.getArray();
						
						EmbedBuilder embed = new EmbedBuilder();
						embed.setAuthor("Auction List", null, event.getSelfUser().getEffectiveAvatarUrl());
						embed.setTitle("Page " + page.getCurrentPage() + "/" + page.getMaxPage());
						embed.setFooter("next | previous | go to <page_number> | cancel", null);
						embed.setColor(Settings.EMBED_COLOUR);
						
						for (int i = page.getCurrentPage() * page.getPerPage() - page.getPerPage(); i < page.getCurrentPage() * page.getPerPage(); i++) {
							try {
								Map<String, Object> auctionData = list.get(i);
								AuctionItem item = new AuctionItem(auctionData);
								
								String extra = "";
								if (item.isTool()) {
									extra = "\nDurability: " + item.getTool().getCurrentDurability() + "/" + item.getTool().getDurability() + "\nUpgrades: " + item.getTool().getUpgrades();
								}
								
								embed.addField(item.getItem().getName(), String.format("Price: $%,d\nPrice Per Item: $%,.2f\nAmount: %,d", item.getPrice(), item.getPricePerItem(), item.getAmount())  + extra, true);
							} catch(IndexOutOfBoundsException e) {
								break;
							}
						}
						
						return embed.build();
					});
			
			PagedUtils.getPagedResult(event, paged, 300, null);
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="sell", description="Put an item on the auction for the chance of it being bought by someone else")
		public void sell(CommandEvent event, @Context Connection connection, @Argument(value="price") long price, @Argument(value="item", endless=true) String itemArgument) {
			Get data = r.table("bank").get(event.getAuthor().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			Pair<String, BigInteger> itemPair = EconomyUtils.getItemAndAmount(itemArgument);
			String itemName = itemPair.getLeft();
			BigInteger itemAmount = itemPair.getRight();
			
			if (itemAmount.compareTo(BigInteger.ONE) == -1) {
				event.reply("You have to sell at least one item :no_entry:").queue();
				return;
			}
			
			Item item = EconomyUtils.getItem(itemName);
			if (item == null) {
				event.reply("I could not find that item :no_entry:").queue();
				return;
			}
			
			Long durability = null;
			if (item instanceof Pickaxe) {
				durability = (long) dataRan.get("pickdur");
			} else if (item instanceof Rod) {
				durability = (long) dataRan.get("roddur");
			} else if (item instanceof Axe) {
				durability = (long) dataRan.get("axedur");
			}
			
			if (dataRan == null) {
				event.reply(String.format("You do not have `%,d %s` :no_entry:", itemAmount, item.getName())).queue();
				return;
			}
			
			List<Map<String, Object>> items = (List<Map<String, Object>>) dataRan.get("items");
			ItemStack userItem = EconomyUtils.getUserItem(items, item);
			
			if (item.isBuyable()) {
				BigInteger itemPrice = BigInteger.valueOf(userItem.getItem().getPrice()).multiply(itemAmount);
				if (itemPrice.divide(BigInteger.valueOf(price)).compareTo(BigInteger.valueOf(20)) == 1) {
					event.reply(String.format("You have to sell this item for at least 5%% its worth (**$%,d**)", itemPrice.divide(BigInteger.valueOf(20)))).queue();
					return;
				}
			}
	
			if (BigInteger.valueOf(userItem.getAmount()).compareTo(itemAmount) != -1) {
				event.reply(String.format("Your `%,d %s` has been put on the auction house for **$%,d** :ok_hand:", itemAmount, item.getName(), price)).queue();
				
				data.update(r.hashMap("items", EconomyUtils.removeItems(items, item, itemAmount.longValue()))).runNoReply(connection);
				
				Map<String, Object> rawItem = EconomyUtils.getUserItemRaw(items, item);
				rawItem.put("amount", itemAmount.longValue());
				r.table("auction").insert(r.hashMap("ownerid", event.getAuthor().getId()).with("price", price).with("item", rawItem).with("durability", durability)).runNoReply(connection);
			} else {
				event.reply(String.format("You do not have `%,d %s` :no_entry:", itemAmount, item.getName())).queue();
				return;
			}
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="buy", description="Buy an item off the auction, look up by item name or look through all items from lowest to highest price per item")
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void buy(CommandEvent event, @Context Connection connection, @Argument(value="item name", endless=true, nullDefault=true) String itemName) {
			Get data = r.table("bank").get(event.getAuthor().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if (dataRan == null) {
				event.reply("You do not have enough money to buy anything on the auction :no_entry:").queue();
				return;
			}
			
			long balance = (long) dataRan.get("balance");
			if (balance == 0) {
				event.reply("You do not have enough money to buy anything on the auction :no_entry:").queue();
				return;
			}
			
			Item item;
			List<Map<String, Object>> shownData;
			if (itemName != null) {
				item = EconomyUtils.getItem(itemName);
				if (item == null) {
					event.reply("I could not find that item :no_entry:").queue();
					return;
				}
				
				Cursor<Map<String, Object>> cursor = r.table("auction").filter(d -> d.g("item").g("name").eq(item.getName())).run(connection);
				shownData = cursor.toList();
				
				if (shownData.isEmpty()) {
					event.reply("I could not find any auctions for `" + item.getName() + "` :no_entry:").queue();
					return;
				}
			} else {
				Cursor<Map<String, Object>> cursor = r.table("auction").run(connection);
				shownData = cursor.toList();
			}
			
			shownData.sort((a, b) -> Double.compare((long) a.get("price") / (long) ((Map<String, Object>) a.get("item")).get("amount"), (long) b.get("price") / (long) ((Map<String, Object>) a.get("item")).get("amount")));
			
			PagedResult<Map<String, Object>> paged = new PagedResult<>(shownData)
					.setIncreasedIndex(true)
					.setPerPage(6)
					.setSelectableByIndex(true)
					.setCustom(true)
					.setCustomFunction(page -> {
						List<Map<String, Object>> list = page.getArray();
						
						EmbedBuilder embed = new EmbedBuilder();
						embed.setAuthor("Auction List", null, event.getSelfUser().getEffectiveAvatarUrl());
						embed.setTitle("Page " + page.getCurrentPage() + "/" + page.getMaxPage());
						embed.setFooter("next | previous | go to <page_number> | cancel", null);
						embed.setColor(Settings.EMBED_COLOUR);
						
						for (int i = page.getCurrentPage() * page.getPerPage() - page.getPerPage(); i < page.getCurrentPage() * page.getPerPage(); i++) {
							try {
								Map<String, Object> auctionData = list.get(i);
								AuctionItem auctionItem = new AuctionItem(auctionData);
								
								String extra = "";
								if (auctionItem.isTool()) {
									extra = "\nDurability: " + auctionItem.getTool().getCurrentDurability() + "/" + auctionItem.getTool().getDurability() + "\nUpgrades: " + auctionItem.getTool().getUpgrades();
								}
								
								embed.addField((i + 1) + ": " + auctionItem.getItem().getName(), String.format("Price: $%,d\nPrice Per Item: $%,.2f\nAmount: %,d", auctionItem.getPrice(), auctionItem.getPricePerItem(), auctionItem.getAmount())  + extra, true);
							} catch(IndexOutOfBoundsException e) {
								break;
							}
						}
						
						return embed.build();
					});
			
			PagedUtils.getPagedResult(event, paged, 60, pagedReturn -> {
				Table auctionTable = r.table("auction");
				Cursor<Map<String, Object>> cursor = auctionTable.run(connection);
				List<Map<String, Object>> auctionData = cursor.toList();
				Map<String, Object> auction = pagedReturn.getObject();
				AuctionItem auctionItem = new AuctionItem(auction);
				
				if (!auctionData.contains(auction)) {
					event.reply("That item has already been bought off the auction :no_entry:").queue();
					return;
				}
				
				User owner = auctionItem.getOwner(event.getShardManager());
				if (owner != null) {
					if (owner.equals(event.getAuthor())) {
						event.reply("You cannot buy your own items, however you can refund them using `" + event.getPrefix() + "auction refund` :no_entry:").queue();
						return;
					}
				}
				
				if (balance < auctionItem.getPrice()) {
					event.reply("You do not have enough money to purchase that auction :no_entry:").queue();
					return;
				}
				
				Map<String, Object> auctionItemRaw = (Map<String, Object>) auction.get("item");
				List<Map<String, Object>> items = (List<Map<String, Object>>) dataRan.get("items");
				if (auctionItem.isAxe()) {
					if (EconomyUtils.hasAxe(items)) {
						event.reply("You already own an axe :no_entry:").queue();
						return;
					} else {
						data.update(row -> r.hashMap("items", EconomyUtils.addItemsFromRaw(items, auctionItemRaw)).with("balance", row.g("balance").sub(auctionItem.getPrice())).with("axedur", auctionItem.getDurability())).runNoReply(connection);
					}
				} else if (auctionItem.isRod()) {
					if (EconomyUtils.hasRod(items)) {
						event.reply("You already own a fishing rod :no_entry:").queue();
						return;
					} else {
						data.update(row -> r.hashMap("items", EconomyUtils.addItemsFromRaw(items, auctionItemRaw)).with("balance", row.g("balance").sub(auctionItem.getPrice())).with("roddur", auctionItem.getDurability())).runNoReply(connection);
					}
				} else if (auctionItem.isPickaxe()) {
					if (EconomyUtils.hasPickaxe(items)) {
						event.reply("You already own a pickaxe :no_entry:").queue();
						return;
					} else {
						data.update(row -> r.hashMap("items", EconomyUtils.addItemsFromRaw(items, auctionItemRaw)).with("balance", row.g("balance").sub(auctionItem.getPrice())).with("pickdur", auctionItem.getDurability())).runNoReply(connection);
					}
				} else {
					data.update(row -> r.hashMap("items", EconomyUtils.addItemsFromRaw(items, auctionItemRaw)).with("balance", row.g("balance").sub(auctionItem.getPrice()))).runNoReply(connection);
				}
				
				event.reply(String.format("You just bought `%,d %s` for **$%,d** :ok_hand:", auctionItem.getAmount(), auctionItem.getItem().getName(), auctionItem.getPrice())).queue();
				
				auctionTable.get(auctionItem.getId()).delete().runNoReply(connection);
				r.table("bank").get(owner.getId()).update(row -> r.hashMap("balance", row.g("balance").add(auctionItem.getPrice()))).runNoReply(connection);
				owner.openPrivateChannel().queue(channel -> {
					channel.sendMessage(String.format("Your `%,d %s` was just bought for **$%,d** :tada:", auctionItem.getAmount(), auctionItem.getItem().getName(), auctionItem.getPrice())).queue();
				}, e -> {});
			});
		}

		@SuppressWarnings("unchecked")
		@Command(value="refund", description="Refund an item you have put on the auction")
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void refund(CommandEvent event, @Context Connection connection, @Argument(value="item name", endless=true, nullDefault=true) String itemName) {
			Get data = r.table("bank").get(event.getAuthor().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if (dataRan == null) {
				event.reply("You do not have any items listed on the auction :no_entry:").queue();
				return;
			}
			
			Item item;
			List<Map<String, Object>> shownData;
			if (itemName != null) {
				item = EconomyUtils.getItem(itemName);
				if (item == null) {
					event.reply("I could not find that item :no_entry:").queue();
					return;
				}
				
				Cursor<Map<String, Object>> cursor = r.table("auction").filter(d -> d.g("item").g("name").eq(item.getName()).and(d.g("ownerid").eq(event.getAuthor().getId()))).run(connection);
				shownData = cursor.toList();
				
				if (shownData.isEmpty()) {
					event.reply("You do not have any `" + item.getName() + "` on the auction :no_entry:").queue();
					return;
				}
			} else {
				Cursor<Map<String, Object>> cursor = r.table("auction").filter(d -> d.g("ownerid").eq(event.getAuthor().getId())).run(connection);
				shownData = cursor.toList();
			}
			
			if (shownData.isEmpty()) {
				event.reply("You do not have any items listed on the auction :no_entry:").queue();
				return;
			}
			
			shownData.sort((a, b) -> Double.compare((long) a.get("price") / (long) ((Map<String, Object>) a.get("item")).get("amount"), (long) b.get("price") / (long) ((Map<String, Object>) a.get("item")).get("amount")));
			
			PagedResult<Map<String, Object>> paged = new PagedResult<>(shownData)
					.setIncreasedIndex(true)
					.setPerPage(6)
					.setSelectableByIndex(true)
					.setCustom(true)
					.setCustomFunction(page -> {
						List<Map<String, Object>> list = page.getArray();
						
						EmbedBuilder embed = new EmbedBuilder();
						embed.setAuthor("Auction List", null, event.getSelfUser().getEffectiveAvatarUrl());
						embed.setTitle("Page " + page.getCurrentPage() + "/" + page.getMaxPage());
						embed.setFooter("next | previous | go to <page_number> | cancel", null);
						embed.setColor(Settings.EMBED_COLOUR);
						
						for (int i = page.getCurrentPage() * page.getPerPage() - page.getPerPage(); i < page.getCurrentPage() * page.getPerPage(); i++) {
							try {
								Map<String, Object> auctionData = list.get(i);
								AuctionItem auctionItem = new AuctionItem(auctionData);
								
								String extra = "";
								if (auctionItem.isTool()) {
									extra = "\nDurability: " + auctionItem.getTool().getCurrentDurability() + "/" + auctionItem.getTool().getDurability() + "\nUpgrades: " + auctionItem.getTool().getUpgrades();
								}
								
								embed.addField((i + 1) + ": " + auctionItem.getItem().getName(), String.format("Price: $%,d\nPrice Per Item: $%,.2f\nAmount: %,d", auctionItem.getPrice(), auctionItem.getPricePerItem(), auctionItem.getAmount())  + extra, true);
							} catch(IndexOutOfBoundsException e) {
								break;
							}
						}
						
						return embed.build();
					});
			
			PagedUtils.getPagedResult(event, paged, 60, pagedReturn -> {
				Table auctionTable = r.table("auction");
				Cursor<Map<String, Object>> cursor = auctionTable.run(connection);
				List<Map<String, Object>> auctionData = cursor.toList();
				Map<String, Object> auction = pagedReturn.getObject();
				AuctionItem auctionItem = new AuctionItem(auction);
				
				if (!auctionData.contains(auction)) {
					event.reply("You have already refunded that item :no_entry:").queue();
					return;
				}
				
				Map<String, Object> auctionItemRaw = (Map<String, Object>) auction.get("item");
				List<Map<String, Object>> items = (List<Map<String, Object>>) dataRan.get("items");
				if (auctionItem.isAxe()) {
					if (EconomyUtils.hasAxe(items)) {
						event.reply("You already own an axe :no_entry:").queue();
						return;
					} else {
						data.update(row -> r.hashMap("items", EconomyUtils.addItemsFromRaw(items, auctionItemRaw)).with("axedur", auctionItem.getDurability())).runNoReply(connection);
					}
				} else if (auctionItem.isRod()) {
					if (EconomyUtils.hasRod(items)) {
						event.reply("You already own a fishing rod :no_entry:").queue();
						return;
					} else {
						data.update(row -> r.hashMap("items", EconomyUtils.addItemsFromRaw(items, auctionItemRaw)).with("roddur", auctionItem.getDurability())).runNoReply(connection);
					}
				} else if (auctionItem.isPickaxe()) {
					if (EconomyUtils.hasPickaxe(items)) {
						event.reply("You already own a pickaxe :no_entry:").queue();
						return;
					} else {
						data.update(row -> r.hashMap("items", EconomyUtils.addItemsFromRaw(items, auctionItemRaw)).with("pickdur", auctionItem.getDurability())).runNoReply(connection);
					}
				} else {
					data.update(row -> r.hashMap("items", EconomyUtils.addItemsFromRaw(items, auctionItemRaw))).runNoReply(connection);
				}
				
				event.reply(String.format("You just refunded your `%,d %s` :ok_hand:", auctionItem.getAmount(), auctionItem.getItem().getName())).queue();
				
				auctionTable.get(auctionItem.getId()).delete().runNoReply(connection);
			});
		}
		
	}
	
	@SuppressWarnings("unchecked")
	@Command(value="fish", description="An easy way to start making money every 5 minutes, buy fishing rods to increase your yield per fish", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void fish(CommandEvent event, @Context Connection connection) {
		EconomyUtils.insertData(event.getAuthor()).run(connection, OptArgs.of("durability", "soft"));
		Get data = r.table("bank").get(event.getAuthor().getId());
		Map<String, Object> dataRan = data.run(connection);
		List<Map<String, Object>> items = (List<Map<String, Object>>) dataRan.get("items");
		
		double timestampNow = (double) Clock.systemUTC().instant().toEpochMilli() / 1000;
		Double fishTime = (Double) dataRan.get("fishtime");
		
		if (fishTime == null) {
			String extra = "";
			long money;
			boolean hasRod = EconomyUtils.hasRod(items);
			Rod userRod;
			if (hasRod) {
				userRod = EconomyUtils.getUserRod(dataRan);
				money = GeneralUtils.getRandomNumber(userRod.getMinimumYield(), userRod.getMaximumYield());
				
				if (userRod.getCurrentDurability() == 2) {
					extra = "Your fishing rod will break the next time you use it :warning:";
				} else if (userRod.getCurrentDurability() == 1) {
					extra = "Your fishing rod broke in the process";
					items = EconomyUtils.removeItem(items, userRod);
				}
			} else {
				money = GeneralUtils.getRandomNumber(2, 10);
			}
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setColor(event.getMember().getColor());
			embed.setAuthor(event.getAuthor().getName(), null, event.getAuthor().getEffectiveAvatarUrl());
			embed.setDescription("You fish for 5 minutes and sell your fish! " + String.format("(**$%,d**)", money) + " :fish:\n\n" + extra);
			event.reply(embed.build()).queue();
			
			List<Map<String, Object>> itemsData = items;
			data.update(row -> r.hashMap("balance", row.g("balance").add(money)).with("items", itemsData).with("roddur", hasRod ? row.g("roddur").sub(1) : row.g("roddur")).with("fishtime", timestampNow)).runNoReply(connection);
		} else if (timestampNow - fishTime <= EconomyUtils.FISH_COOLDOWN) {
			event.reply("Slow down! You can go fishing in " + TimeUtils.toTimeString((long) (fishTime - timestampNow + EconomyUtils.FISH_COOLDOWN), ChronoUnit.SECONDS) + " :stopwatch:").queue();
		} else {
			String extra = "";
			long money;
			boolean hasRod = EconomyUtils.hasRod(items);
			Rod userRod;
			if (hasRod) {
				userRod = EconomyUtils.getUserRod(dataRan);
				money = GeneralUtils.getRandomNumber(userRod.getMinimumYield(), userRod.getMaximumYield());
				
				if (userRod.getCurrentDurability() == 2) {
					extra = "Your fishing rod will break the next time you use it :warning:";
				} else if (userRod.getCurrentDurability() == 1) {
					extra = "Your fishing rod broke in the process";
					items = EconomyUtils.removeItem(items, userRod);
				}
			} else {
				money = GeneralUtils.getRandomNumber(2, 10);
			}
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setColor(event.getMember().getColor());
			embed.setAuthor(event.getAuthor().getName(), null, event.getAuthor().getEffectiveAvatarUrl());
			embed.setDescription("You fish for 5 minutes and sell your fish! " + String.format("(**$%,d**)", money) + " :fish:\n\n" + extra);
			event.reply(embed.build()).queue();
			
			List<Map<String, Object>> itemsData = items;
			data.update(row -> r.hashMap("balance", row.g("balance").add(money)).with("items", itemsData).with("roddur", hasRod ? row.g("roddur").sub(1) : row.g("roddur")).with("fishtime", timestampNow)).runNoReply(connection);
		}
	}
	
	@SuppressWarnings("unchecked")
	@Command(value="chop", description="Use your axe to chop some trees down to gather wood for crafting", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void chop(CommandEvent event, @Context Connection connection) {
		Get data = r.table("bank").get(event.getAuthor().getId());
		Map<String, Object> dataRan = data.run(connection);
		
		if (dataRan == null) {
			event.reply("You do not have an axe :no_entry:").queue();
			return;
		}
		
		List<Map<String, Object>> items = (List<Map<String, Object>>) dataRan.get("items");
		if (!EconomyUtils.hasAxe(items)) {
			event.reply("You do not have an axe :no_entry:").queue();
			return;
		}
		
		double timestampNow = (double) Clock.systemUTC().instant().toEpochMilli() / 1000;
		Double axeTime = (Double) dataRan.get("axetime");
		
		if (axeTime == null) {
			Axe userAxe = EconomyUtils.getUserAxe(dataRan);
			String extra = "";
			if (userAxe.getCurrentDurability() == 2) {
				extra = "Your axe will break the next time you use it :warning:";
			} else if (userAxe.getCurrentDurability() == 1) {
				extra = "Your axe broke in the process";
				items = EconomyUtils.removeItem(items, userAxe);
			}
			
			Map<Wood, Long> woodGathered = new HashMap<>();
			for (Wood wood : Wood.ALL) {
				for (int i = 0; i < userAxe.getMaximumMaterials(); i++) {
					int randomInt = random.nextInt((int) Math.ceil(wood.getChance() / userAxe.getMultiplier()) + 1);
					if (randomInt == 0) {
						if (woodGathered.containsKey(wood)) {
							woodGathered.put(wood, woodGathered.get(wood) + 1L);
						} else {
							woodGathered.put(wood, 1L);
						}
					}
				}
			}
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setDescription("You chopped down some trees and found the following wood: ");
			embed.setColor(event.getMember().getColor());
			embed.setAuthor(event.getAuthor().getName(), null, event.getAuthor().getEffectiveAvatarUrl());
			if (!woodGathered.isEmpty()) {
				List<Wood> keys = GeneralUtils.convertSetToList(woodGathered.keySet());
				keys.sort((a, b) -> Long.compare(woodGathered.get(b), woodGathered.get(a)));
				for (Wood key : keys) {
					long amount = woodGathered.get(key);
					
					embed.appendDescription(String.format("%,dx %s", amount, key.getName()));
					if (keys.indexOf(key) != keys.size() - 1) {
						embed.appendDescription(", ");
					} else {
						embed.appendDescription("\n\n");
					}
					
					items = EconomyUtils.addItems(items, key, amount);
				}
			} else {
				embed.appendDescription("Absolutely nothing\n\n");
			}
			
			embed.appendDescription(extra);
			
			event.reply(embed.build()).queue();
			
			List<Map<String, Object>> itemsData = items;
			data.update(row -> r.hashMap("items", itemsData).with("axedur", row.g("axedur").sub(1)).with("axetime", timestampNow)).runNoReply(connection);
		} else if (timestampNow - axeTime <= EconomyUtils.CHOP_COOLDOWN) {
			event.reply("Slow down! You can chop down trees in " + TimeUtils.toTimeString((long) (axeTime - timestampNow + EconomyUtils.CHOP_COOLDOWN), ChronoUnit.SECONDS) + " :stopwatch:").queue();
		} else {
			Axe userAxe = EconomyUtils.getUserAxe(dataRan);
			String extra = "";
			if (userAxe.getCurrentDurability() == 2) {
				extra = "Your axe will break the next time you use it :warning:";
			} else if (userAxe.getCurrentDurability() == 1) {
				extra = "Your axe broke in the process";
				items = EconomyUtils.removeItem(items, userAxe);
			}
			
			Map<Wood, Long> woodGathered = new HashMap<>();
			for (Wood wood : Wood.ALL) {
				for (int i = 0; i < userAxe.getMaximumMaterials(); i++) {
					int randomInt = random.nextInt((int) Math.ceil(wood.getChance() / userAxe.getMultiplier()) + 1);
					if (randomInt == 0) {
						if (woodGathered.containsKey(wood)) {
							woodGathered.put(wood, woodGathered.get(wood) + 1L);
						} else {
							woodGathered.put(wood, 1L);
						}
					}
				}
			}
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setDescription("You chopped down some trees and found the following wood: ");
			embed.setColor(event.getMember().getColor());
			embed.setAuthor(event.getAuthor().getName(), null, event.getAuthor().getEffectiveAvatarUrl());
			if (!woodGathered.isEmpty()) {
				List<Wood> keys = GeneralUtils.convertSetToList(woodGathered.keySet());
				keys.sort((a, b) -> Long.compare(woodGathered.get(b), woodGathered.get(a)));
				for (Wood key : keys) {
					long amount = woodGathered.get(key);
					
					embed.appendDescription(String.format("%,dx %s", amount, key.getName()));
					if (keys.indexOf(key) != keys.size() - 1) {
						embed.appendDescription(", ");
					} else {
						embed.appendDescription("\n\n");
					}
					
					items = EconomyUtils.addItems(items, key, amount);
				}
			} else {
				embed.appendDescription("Absolutely nothing\n\n");
			}
			
			embed.appendDescription(extra);
			
			event.reply(embed.build()).queue();
			
			List<Map<String, Object>> itemsData = items;
			data.update(row -> r.hashMap("items", itemsData).with("axedur", row.g("axedur").sub(1)).with("axetime", timestampNow)).runNoReply(connection);
		}
	}
	
	@SuppressWarnings("unchecked")
	@Command(value="mine", description="Use your pickaxe to mine, mining will gather money aswell as the chance to get some materials", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void mine(CommandEvent event, @Context Connection connection) {
		Get data = r.table("bank").get(event.getAuthor().getId());
		Map<String, Object> dataRan = data.run(connection);
		
		if (dataRan == null) {
			event.reply("You do not have a pickaxe :no_entry:").queue();
			return;
		}
		
		List<Map<String, Object>> items = (List<Map<String, Object>>) dataRan.get("items");
		if (!EconomyUtils.hasPickaxe(items)) {
			event.reply("You do not have a pickaxe :no_entry:").queue();
			return;
		}
		
		double timestampNow = (double) Clock.systemUTC().instant().toEpochMilli() / 1000;
		Double pickTime = (Double) dataRan.get("picktime");
		
		if (pickTime == null) {
			Pickaxe userPickaxe = EconomyUtils.getUserPickaxe(dataRan);
			
			String extra = "";
			if (userPickaxe.getCurrentDurability() == 2) {
				extra = "Your pickaxe will break the next time you use it :warning:";
			} else if (userPickaxe.getCurrentDurability() == 1) {
				extra = "Your pickaxe broke in the process";
				items = EconomyUtils.removeItem(items, userPickaxe);
			}
			
			long money = GeneralUtils.getRandomNumber(userPickaxe.getMinimumYield(), userPickaxe.getMaximumYield());
			StringBuilder materialContent = new StringBuilder();
			for (Material material : Material.ALL) {
				if (!material.isHidden()) {
					if (random.nextInt((int) Math.ceil(material.getChance() / userPickaxe.getMultiplier()) + 1) == 0) {
						materialContent.append(material.getName() + material.getEmote() + ", ");
						
						items = EconomyUtils.addItem(items, material);
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
			embed.setDescription(String.format("You mined resources and made **$%,d** :pick:\nMaterials found: %s\n\n%s", money, materialContent.toString(), extra));
			event.reply(embed.build()).queue();
			
			List<Map<String, Object>> itemsData = items;
			data.update(row -> r.hashMap("items", itemsData).with("pickdur", row.g("pickdur").sub(1)).with("balance", row.g("balance").add(money)).with("picktime", timestampNow)).runNoReply(connection);
		} else if (timestampNow - pickTime <= EconomyUtils.MINE_COOLDOWN) {
			event.reply("Slow down! You can go mining in " + TimeUtils.toTimeString((long) (pickTime - timestampNow + EconomyUtils.MINE_COOLDOWN), ChronoUnit.SECONDS) + " :stopwatch:").queue();
		} else {
			Pickaxe userPickaxe = EconomyUtils.getUserPickaxe(dataRan);
			
			String extra = "";
			if (userPickaxe.getCurrentDurability() == 2) {
				extra = "Your pickaxe will break the next time you use it :warning:";
			} else if (userPickaxe.getCurrentDurability() == 1) {
				extra = "Your pickaxe broke in the process";
				items = EconomyUtils.removeItem(items, userPickaxe);
			}
			
			long money = GeneralUtils.getRandomNumber(userPickaxe.getMinimumYield(), userPickaxe.getMaximumYield());
			StringBuilder materialContent = new StringBuilder();
			for (Material material : Material.ALL) {
				if (!material.isHidden()) {
					if (random.nextInt((int) Math.ceil(material.getChance() / userPickaxe.getMultiplier()) + 1) == 0) {
						materialContent.append(material.getName() + material.getEmote() + ", ");
						
						items = EconomyUtils.addItem(items, material);
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
			embed.setDescription(String.format("You mined resources and made **$%,d** :pick:\nMaterials found: %s\n\n%s", money, materialContent.toString(), extra));
			event.reply(embed.build()).queue();
			
			List<Map<String, Object>> itemsData = items;
			data.update(row -> r.hashMap("items", itemsData).with("pickdur", row.g("pickdur").sub(1)).with("balance", row.g("balance").add(money)).with("picktime", timestampNow)).runNoReply(connection);
		}
	}
	
	@SuppressWarnings("unchecked")
	@Command(value="items", aliases={"inventory", "inv"}, description="View all the items you currently have")
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void items(CommandEvent event, @Context Connection connection, @Argument(value="user", endless=true, nullDefault=true) String userArgument) {
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
		
		Map<String, Object> data = r.table("bank").get(member.getUser().getId()).run(connection);
		
		if (data == null) {
			event.reply((member.equals(event.getMember()) ? "You do not" : "That user does not") + " have any items :no_entry:").queue();
			return;
		}
		
		List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
		if (items.isEmpty()) {
			event.reply((member.equals(event.getMember()) ? "You do not" : "That user does not") + " have any items :no_entry:").queue();
			return;
		}
		
		items.sort((a, b) -> Long.compare((long) b.get("amount"), (long) a.get("amount"))); 

		
		Map<String, List<String>> userItems = new HashMap<>();
		userItems.put("Materials", new ArrayList<>());
		userItems.put("Tools", new ArrayList<>());
		userItems.put("Boosters", new ArrayList<>());
		userItems.put("Miners", new ArrayList<>());
		userItems.put("Factories", new ArrayList<>());
		userItems.put("Crates", new ArrayList<>());
		userItems.put("Wood", new ArrayList<>());
		for (Map<String, Object> item : items) {
			Item actualItem = EconomyUtils.getItem((String) item.get("name"));
			ItemStack userItem = new ItemStack(actualItem, (long) item.get("amount"));
			
			if (actualItem instanceof Pickaxe) {
				userItems.get("Tools").add(String.format("%s x%,d (%,d Durability)", actualItem.getName(), userItem.getAmount(), (long) data.get("pickdur")));
			} else if (actualItem instanceof Rod) {
				userItems.get("Tools").add(String.format("%s x%,d (%,d Durability)", actualItem.getName(), userItem.getAmount(), (long) data.get("roddur")));
			} else if (actualItem instanceof Axe) {
				userItems.get("Tools").add(String.format("%s x%,d (%,d Durability)", actualItem.getName(), userItem.getAmount(), (long) data.get("axedur")));
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
			}
		}
		
		List<String> keys = GeneralUtils.convertSetToList(userItems.keySet());
		keys.sort((a, b) -> Integer.compare(userItems.get(a).size(), userItems.get(b).size()));
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setAuthor(member.getUser().getName() + "'s Items", null, member.getUser().getEffectiveAvatarUrl());
		embed.setColor(member.getColor());
		embed.setFooter("If a category isn't shown it means you have no items in that category | Balance: " + String.format("$%,d", (long) data.get("balance")), null);
		for (String key : keys) {
			List<String> list = userItems.get(key);
			if (!list.isEmpty()) {
				embed.addField(key, String.join("\n", list), true);
			}
		}
		
		event.reply(embed.build()).queue();
	}
	
	@Command(value="slot", aliases={"slots"}, description="Bet your money on the slots if you get 3 in a row you win, the rarer the 3 items the more the payout")
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void slot(CommandEvent event, @Context Connection connection, @Argument(value="bet", endless=true, nullDefault=true) String betArgument) {
		long bet = 0;
		Get data = null;
		if (betArgument != null) {
			data = r.table("bank").get(event.getAuthor().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if (dataRan == null) {
				event.reply("You do not have any money to bet :no_entry:").queue();
				return;
			}
			
			try {
				bet = EconomyUtils.convertMoneyArgument((long) dataRan.get("balance"), betArgument);
			} catch(IllegalArgumentException e) {
				event.reply(e.getMessage()).queue();
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
		
		if (firstSlot.equals(secondSlot) && firstSlot.equals(thirdSlot)) {
			if (betArgument == null) {
				embed.appendDescription(String.format("You would have won **x%,.2f** your bet!", firstSlot.getMultiplier()));
			} else {
				long winnings = (long) Math.round(bet * firstSlot.getMultiplier());
				long actualWinnings = winnings - bet;
				
				embed.appendDescription(String.format("You won **$%,d**", winnings));
				
				data.update(row -> r.hashMap("winnings", row.g("winnings").add(actualWinnings)).with("balance", row.g("balance").add(actualWinnings))).runNoReply(connection);
			}
		} else {
			if (betArgument == null) {
				embed.appendDescription("You would have won nothing!");
			} else {
				embed.appendDescription(String.format("You lost **$%,d**!", bet));
				
				long dataBet = bet;
				data.update(row -> r.hashMap("winnings", row.g("winnings").sub(dataBet)).with("balance", row.g("balance").sub(dataBet))).runNoReply(connection);
			}
		}
		
		event.reply(embed.build()).queue();
	}
	
	public class LeaderboardCommand extends CommandImpl {
		
		public LeaderboardCommand() {
			super("leaderboard");
			
			super.setAliases("lb", "ranks", "rank");
			super.setDescription("View the leaderboards for the economy");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="bank", aliases={"money", "balance"}, description="View the leaderboard for people with the most money in their balance", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void bank(CommandEvent event, @Context Connection connection, @Option(value="server", aliases={"guild"}) boolean guild) {
			Cursor<Map<String, Object>> cursor = r.table("bank").withFields("id", "balance").filter(row -> row.g("balance").ne(0)).run(connection);
			List<Map<String, Object>> data = cursor.toList();
			
			List<Map<String, Object>> compressedData = new ArrayList<>();
			for (Map<String, Object> dataObject : data) {		
				User user = event.getShardManager().getUserById((String) dataObject.get("id"));
				if (user == null) {
					continue;
				}
				
				if (guild) {
					if (!event.getGuild().isMember(user)) {
						continue;
					}
				}
				
				Map<String, Object> dataMap = new HashMap<>();
				dataMap.put("user", user);
				dataMap.put("balance", (long) dataObject.get("balance"));
				compressedData.add(dataMap);
			}
			
			compressedData.sort((a, b) -> Long.compare((long) b.get("balance"), (long) a.get("balance")));
			
			PagedResult<Map<String, Object>> paged = new PagedResult<>(compressedData)
					.setDeleteMessage(false)
					.setCustom(true)
					.setCustomFunction(page -> {
						Integer index = null;
						for (int i = 0; i < compressedData.size(); i++) {
							Map<String, Object> userData = compressedData.get(i);
							if (((User) userData.get("user")).equals(event.getAuthor())) {
								index = i + 1;
							}
						}
						
						EmbedBuilder embed = new EmbedBuilder();
						embed.setColor(Settings.EMBED_COLOUR);
						embed.setTitle("Bank Leaderboard");
						embed.setFooter(event.getAuthor().getName() + "'s Rank: " + (index == null ? "Unranked" : GeneralUtils.getNumberSuffix(index)) + " | Page " + page.getCurrentPage() + "/" + page.getMaxPage(), event.getAuthor().getEffectiveAvatarUrl());
						
						for (int i = page.getCurrentPage() * page.getPerPage() - page.getPerPage(); i < page.getCurrentPage() * page.getPerPage(); i++) {
							try {
								Map<String, Object> userData = compressedData.get(i);
								embed.appendDescription(String.format("%d. `%s` - $%,d\n", i + 1, ((User) userData.get("user")).getAsTag(), (long) userData.get("balance")));
							} catch (IndexOutOfBoundsException e) {
								break;
							}
						}
						
						return embed.build();
					});
			
			PagedUtils.getPagedResult(event, paged, 300, null);
		}
		
		@Command(value="networth", description="View the leaderboard for people with the most networth (All items worth + their balance)", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void networth(CommandEvent event, @Context Connection connection, @Option(value="server", aliases={"guild"}) boolean guild) {
			Cursor<Map<String, Object>> cursor = r.table("bank").withFields("id", "items", "balance").run(connection);
			List<Map<String, Object>> data = cursor.toList();
			
			List<Map<String, Object>> compressedData = new ArrayList<>();
			for (Map<String, Object> dataObject : data) {		
				long networth = EconomyUtils.getUserNetworth(dataObject);
				if (networth == 0) {
					continue;
				}
				
				User user = event.getShardManager().getUserById((String) dataObject.get("id"));
				if (user == null) {
					continue;
				}
				
				if (guild) {
					if (!event.getGuild().isMember(user)) {
						continue;
					}
				}
				
				Map<String, Object> dataMap = new HashMap<>();
				dataMap.put("user", user);
				dataMap.put("networth", networth);
				compressedData.add(dataMap);
			}
			
			compressedData.sort((a, b) -> Long.compare((long) b.get("networth"), (long) a.get("networth")));
			
			PagedResult<Map<String, Object>> paged = new PagedResult<>(compressedData)
					.setDeleteMessage(false)
					.setCustom(true)
					.setCustomFunction(page -> {
						Integer index = null;
						for (int i = 0; i < compressedData.size(); i++) {
							Map<String, Object> userData = compressedData.get(i);
							if (((User) userData.get("user")).equals(event.getAuthor())) {
								index = i + 1;
							}
						}
						
						EmbedBuilder embed = new EmbedBuilder();
						embed.setColor(Settings.EMBED_COLOUR);
						embed.setTitle("Networth Leaderboard");
						embed.setFooter(event.getAuthor().getName() + "'s Rank: " + (index == null ? "Unranked" : GeneralUtils.getNumberSuffix(index)) + " | Page " + page.getCurrentPage() + "/" + page.getMaxPage(), event.getAuthor().getEffectiveAvatarUrl());
						
						for (int i = page.getCurrentPage() * page.getPerPage() - page.getPerPage(); i < page.getCurrentPage() * page.getPerPage(); i++) {
							try {
								Map<String, Object> userData = compressedData.get(i);
								embed.appendDescription(String.format("%d. `%s` - $%,d\n", i + 1, ((User) userData.get("user")).getAsTag(), (long) userData.get("networth")));
							} catch (IndexOutOfBoundsException e) {
								break;
							}
						}
						
						return embed.build();
					});
			
			PagedUtils.getPagedResult(event, paged, 300, null);
		}
		
		@Command(value="winnings", description="View the leaderboard for people with the highest winnings", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void winnings(CommandEvent event, @Context Connection connection, @Option(value="server", aliases={"guild"}) boolean guild) {
			Cursor<Map<String, Object>> cursor = r.table("bank").withFields("id", "winnings").filter(row -> row.g("winnings").ne(0)).run(connection);
			List<Map<String, Object>> data = cursor.toList();
			
			List<Map<String, Object>> compressedData = new ArrayList<>();
			for (Map<String, Object> dataObject : data) {						
				User user = event.getShardManager().getUserById((String) dataObject.get("id"));
				if (user == null) {
					continue;
				}
				
				if (guild) {
					if (!event.getGuild().isMember(user)) {
						continue;
					}
				}
				
				Map<String, Object> dataMap = new HashMap<>();
				dataMap.put("user", user);
				dataMap.put("winnings", (long) dataObject.get("winnings"));
				compressedData.add(dataMap);
			}
			
			compressedData.sort((a, b) -> Long.compare((long) b.get("winnings"), (long) a.get("winnings")));
			
			PagedResult<Map<String, Object>> paged = new PagedResult<>(compressedData)
					.setDeleteMessage(false)
					.setCustom(true)
					.setCustomFunction(page -> {
						Integer index = null;
						for (int i = 0; i < compressedData.size(); i++) {
							Map<String, Object> userData = compressedData.get(i);
							if (((User) userData.get("user")).equals(event.getAuthor())) {
								index = i + 1;
							}
						}
						
						EmbedBuilder embed = new EmbedBuilder();
						embed.setColor(Settings.EMBED_COLOUR);
						embed.setTitle("Winnings Leaderboard");
						embed.setFooter(event.getAuthor().getName() + "'s Rank: " + (index == null ? "Unranked" : GeneralUtils.getNumberSuffix(index)) + " | Page " + page.getCurrentPage() + "/" + page.getMaxPage(), event.getAuthor().getEffectiveAvatarUrl());
						
						for (int i = page.getCurrentPage() * page.getPerPage() - page.getPerPage(); i < page.getCurrentPage() * page.getPerPage(); i++) {
							try {
								Map<String, Object> userData = compressedData.get(i);
								embed.appendDescription(String.format("%d. `%s` - $%,d\n", i + 1, ((User) userData.get("user")).getAsTag(), (long) userData.get("winnings")));
							} catch (IndexOutOfBoundsException e) {
								break;
							}
						}
						
						return embed.build();
					});
			
			PagedUtils.getPagedResult(event, paged, 300, null);
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="items", aliases={"item"}, description="View the leaderboard for the people with the most of a specific item")
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void items(CommandEvent event, @Context Connection connection, @Argument(value="item name", endless=true) String itemName, @Option(value="server", aliases={"guild"}) boolean guild) {
			Item item = EconomyUtils.getItem(itemName);
			if (item == null) {
				event.reply("I could not find that item :no_entry:").queue();
				return;
			}
			
			Cursor<Map<String, Object>> cursor = r.table("bank").withFields("id", "items").run(connection);
			List<Map<String, Object>> data = cursor.toList();
			
			List<Map<String, Object>> compressedData = new ArrayList<>();
			for (Map<String, Object> dataObject : data) {		
				ItemStack userItem = EconomyUtils.getUserItem((List<Map<String, Object>>) dataObject.get("items"), item);
				if (userItem.getAmount() == 0) {
					continue;
				}
				
				User user = event.getShardManager().getUserById((String) dataObject.get("id"));
				if (user == null) {
					continue;
				}
				
				if (guild) {
					if (!event.getGuild().isMember(user)) {
						continue;
					}
				}
				
				Map<String, Object> dataMap = new HashMap<>();
				dataMap.put("user", user);
				dataMap.put("itemAmount", userItem.getAmount());
				compressedData.add(dataMap);
			}
			
			compressedData.sort((a, b) -> Long.compare((long) b.get("itemAmount"), (long) a.get("itemAmount")));
			
			PagedResult<Map<String, Object>> paged = new PagedResult<>(compressedData)
					.setDeleteMessage(false)
					.setCustom(true)
					.setCustomFunction(page -> {
						Integer index = null;
						for (int i = 0; i < compressedData.size(); i++) {
							Map<String, Object> userData = compressedData.get(i);
							if (((User) userData.get("user")).equals(event.getAuthor())) {
								index = i + 1;
							}
						}
						
						EmbedBuilder embed = new EmbedBuilder();
						embed.setColor(Settings.EMBED_COLOUR);
						embed.setTitle(item.getName() + " Leaderboard");
						embed.setFooter(event.getAuthor().getName() + "'s Rank: " + (index == null ? "Unranked" : GeneralUtils.getNumberSuffix(index)) + " | Page " + page.getCurrentPage() + "/" + page.getMaxPage(), event.getAuthor().getEffectiveAvatarUrl());
						
						for (int i = page.getCurrentPage() * page.getPerPage() - page.getPerPage(); i < page.getCurrentPage() * page.getPerPage(); i++) {
							try {
								Map<String, Object> userData = compressedData.get(i);
								embed.appendDescription(String.format("%d. `%s` - %,d %s\n", i + 1, ((User) userData.get("user")).getAsTag(), (long) userData.get("itemAmount"), item.getName()));
							} catch (IndexOutOfBoundsException e) {
								break;
							}
						}
						
						return embed.build();
					});
			
			PagedUtils.getPagedResult(event, paged, 300, null);
		}
		
		@Command(value="reputation", aliases={"rep", "reps"}, description="View the leaderboard for people with the highest reputation", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void reputation(CommandEvent event, @Context Connection connection, @Option(value="server", aliases={"guild"}) boolean guild) {
			Cursor<Map<String, Object>> cursor = r.table("bank").withFields("id", "rep").filter(row -> row.g("reputation").ne(0)).run(connection);
			List<Map<String, Object>> data = cursor.toList();
			
			List<Map<String, Object>> compressedData = new ArrayList<>();
			for (Map<String, Object> dataObject : data) {		
				User user = event.getShardManager().getUserById((String) dataObject.get("id"));
				if (user == null) {
					continue;
				}
				
				if (guild) {
					if (!event.getGuild().isMember(user)) {
						continue;
					}
				}
				
				Map<String, Object> dataMap = new HashMap<>();
				dataMap.put("user", user);
				dataMap.put("reputation", (long) dataObject.get("rep"));
				compressedData.add(dataMap);
			}
			
			compressedData.sort((a, b) -> Long.compare((long) b.get("reputation"), (long) a.get("reputation")));
			
			PagedResult<Map<String, Object>> paged = new PagedResult<>(compressedData)
					.setDeleteMessage(false)
					.setCustom(true)
					.setCustomFunction(page -> {
						Integer index = null;
						for (int i = 0; i < compressedData.size(); i++) {
							Map<String, Object> userData = compressedData.get(i);
							if (((User) userData.get("user")).equals(event.getAuthor())) {
								index = i + 1;
							}
						}
						
						EmbedBuilder embed = new EmbedBuilder();
						embed.setColor(Settings.EMBED_COLOUR);
						embed.setTitle("Reputation Leaderboard");
						embed.setFooter(event.getAuthor().getName() + "'s Rank: " + (index == null ? "Unranked" : GeneralUtils.getNumberSuffix(index)) + " | Page " + page.getCurrentPage() + "/" + page.getMaxPage(), event.getAuthor().getEffectiveAvatarUrl());
						
						for (int i = page.getCurrentPage() * page.getPerPage() - page.getPerPage(); i < page.getCurrentPage() * page.getPerPage(); i++) {
							try {
								Map<String, Object> userData = compressedData.get(i);
								embed.appendDescription(String.format("%d. `%s` - %,d reputation\n", i + 1, ((User) userData.get("user")).getAsTag(), (long) userData.get("reputation")));
							} catch (IndexOutOfBoundsException e) {
								break;
							}
						}
						
						return embed.build();
					});
			
			PagedUtils.getPagedResult(event, paged, 300, null);
		}
		
		@Command(value="streak", description="View the leaderboard for people who have the highest streak for using `daily`", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void streak(CommandEvent event, @Context Connection connection, @Option(value="server", aliases={"guild"}) boolean guild) {
			Cursor<Map<String, Object>> cursor = r.table("bank").withFields("id", "streak").filter(row -> row.g("streak").ne(0)).run(connection);
			List<Map<String, Object>> data = cursor.toList();
			
			List<Map<String, Object>> compressedData = new ArrayList<>();
			for (Map<String, Object> dataObject : data) {		
				User user = event.getShardManager().getUserById((String) dataObject.get("id"));
				if (user == null) {
					continue;
				}
				
				if (guild) {
					if (!event.getGuild().isMember(user)) {
						continue;
					}
				}
				
				Map<String, Object> dataMap = new HashMap<>();
				dataMap.put("user", user);
				dataMap.put("streak", (long) dataObject.get("streak"));
				compressedData.add(dataMap);
			}
			
			compressedData.sort((a, b) -> Long.compare((long) b.get("balance"), (long) a.get("balance")));
			
			PagedResult<Map<String, Object>> paged = new PagedResult<>(compressedData)
					.setDeleteMessage(false)
					.setCustom(true)
					.setCustomFunction(page -> {
						Integer index = null;
						for (int i = 0; i < compressedData.size(); i++) {
							Map<String, Object> userData = compressedData.get(i);
							if (((User) userData.get("user")).equals(event.getAuthor())) {
								index = i + 1;
							}
						}
						
						EmbedBuilder embed = new EmbedBuilder();
						embed.setColor(Settings.EMBED_COLOUR);
						embed.setTitle("Streak Leaderboard");
						embed.setFooter(event.getAuthor().getName() + "'s Rank: " + (index == null ? "Unranked" : GeneralUtils.getNumberSuffix(index)) + " | Page " + page.getCurrentPage() + "/" + page.getMaxPage(), event.getAuthor().getEffectiveAvatarUrl());
						
						for (int i = page.getCurrentPage() * page.getPerPage() - page.getPerPage(); i < page.getCurrentPage() * page.getPerPage(); i++) {
							try {
								Map<String, Object> userData = compressedData.get(i);
								embed.appendDescription(String.format("%d. `%s` - %,d day streak\n", i + 1, ((User) userData.get("user")).getAsTag(), (long) userData.get("streak")));
							} catch (IndexOutOfBoundsException e) {
								break;
							}
						}
						
						return embed.build();
					});
			
			PagedUtils.getPagedResult(event, paged, 300, null);
		}
		
		@Command(value="votes", aliases={"vote"}, description="View the leaderboard for the highest votes of the month/all time")
		public void votes(CommandEvent event, @Argument(value="month", nullDefault=true) String monthArgument, @Option(value="all") boolean all, @Option(value="server", aliases={"guild"}) boolean guild) {
			LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
			int year = now.getYear();
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
								if (votesMap.containsKey(user)) {
									votesMap.put(user, votesMap.get(user) + jsonSx4.getJSONObject(keySx4).getJSONArray("votes").length());
								} else {
									votesMap.put(user, jsonSx4.getJSONObject(keySx4).getJSONArray("votes").length());
								}
							}
						}
						
						for (String keyJockie : keysJockie) {
							User user = cache.getElementById(keyJockie);
							if (user != null) {
								if (votesMap.containsKey(user)) {
									votesMap.put(user, votesMap.get(user) + jsonJockie.getJSONObject(keyJockie).getJSONArray("votes").length());
								} else {
									votesMap.put(user, jsonJockie.getJSONObject(keyJockie).getJSONArray("votes").length());
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
										if (votesMap.containsKey(user)) {
											votesMap.put(user, votesMap.get(user) + 1);
										} else {
											votesMap.put(user, 1);
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
										if (votesMap.containsKey(user)) {
											votesMap.put(user, votesMap.get(user) + 1);
										} else {
											votesMap.put(user, 1);
										}
									}
								}
							}
						}
					}
					
					List<Pair<User, Integer>> votes = new ArrayList<>();
					for (User key : votesMap.keySet()) {
						if (guild) {
							if (!event.getGuild().isMember(key)) {
								continue;
							}
						}
						
						Pair<User, Integer> userData = Pair.of(key, votesMap.get(key));
						votes.add(userData);
					}
					
					votes.sort((a, b) -> Integer.compare(b.getRight(), a.getRight()));
					
					PagedResult<Pair<User, Integer>> paged = new PagedResult<>(votes)
							.setDeleteMessage(false)
							.setCustom(true)
							.setCustomFunction(page -> {
								Integer index = null;
								for (int i = 0; i < votes.size(); i++) {
									Pair<User, Integer> userData = votes.get(i);
									if (userData.getLeft().equals(event.getAuthor())) {
										index = i + 1;
									}
								}
								
								EmbedBuilder embed = new EmbedBuilder();
								embed.setColor(Settings.EMBED_COLOUR);
								embed.setTitle("Votes Leaderboard" + (all ? "" : " for " + month.getDisplayName(TextStyle.FULL, Locale.UK)));
								embed.setFooter(event.getAuthor().getName() + "'s Rank: " + (index == null ? "Unranked" : GeneralUtils.getNumberSuffix(index)) + " | Page " + page.getCurrentPage() + "/" + page.getMaxPage(), event.getAuthor().getEffectiveAvatarUrl());
								
								for (int i = page.getCurrentPage() * page.getPerPage() - page.getPerPage(); i < page.getCurrentPage() * page.getPerPage(); i++) {
									try {
										Pair<User, Integer> userData = votes.get(i);
										int votesAmount = userData.getRight();
										embed.appendDescription(String.format("%d. `%s` - %,d vote%s\n", i + 1, userData.getLeft().getAsTag(), votesAmount, votesAmount == 1 ? "" : "s"));
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
	
	@Initialize(all=true)
	public void initialize(CommandImpl command) {
		command.setCategory(Categories.ECONOMY);
	}
	
}
