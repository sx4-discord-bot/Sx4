package com.sx4.bot.games.uno;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import com.jockie.bot.core.command.impl.CommandEvent;
import com.sx4.bot.core.Sx4Bot;
import com.sx4.bot.games.uno.UnoCard.Colour;
import com.sx4.bot.utils.GeneralUtils;
import com.sx4.bot.utils.GiveawayUtils;
import com.sx4.bot.utils.PagedUtils;
import com.sx4.bot.utils.PagedUtils.PagedResult;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.PrivateChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;

public class UnoSession {
	
	private List<UnoCard> deck = UnoCard.DECK;
	private List<UnoPlayer> players = new ArrayList<>(); 
	private UnoPlayer currentPlayer;
	
	private List<Long> whitelistedRoles = new ArrayList<>();
	private List<Long> whitelistedMembers = new ArrayList<>();
	
	private boolean incremental = true;
	private boolean inviteOnly = false;
	private boolean done = false;
	
	private long ownerId;
	private long channelId;
	private long guildId;
	
	private int twoStack = 0;
	private int fourStack = 0;
	
	//Used when a plus four or colour change has been played
	private Colour lastColour;
	
	public UnoSession(TextChannel channel, Member owner) {
		this.ownerId = owner.getIdLong();
		this.guildId = channel.getGuild().getIdLong();
		this.channelId = channel.getIdLong();
		
		this.addPlayer(owner);
	}
	
	public UnoSession(TextChannel channel, Member owner, boolean inviteOnly) {
		this.ownerId = owner.getIdLong();
		this.guildId = channel.getGuild().getIdLong();
		this.channelId = channel.getIdLong();
		this.inviteOnly = inviteOnly;
		
		this.addPlayer(owner);
	}
	
	public long getGuildId() {
		return this.guildId;
	}
	
	public long getChannelId() {
		return this.channelId;
	}
	
	public long getOwnerId() {
		return this.ownerId;
	}
	
	public Guild getGuild() {
		return Sx4Bot.getShardManager().getGuildById(this.guildId);
	}
	
	public Member getOwner() {
		Guild guild = this.getGuild();
		
		return guild == null ? null : guild.getMemberById(this.ownerId);
	}
	
	public TextChannel getChannel() {
		Guild guild = this.getGuild();
		
		return guild == null ? null : guild.getTextChannelById(this.channelId);
	}
	
	public void invite(Member member) {
		this.whitelistedMembers.add(member.getIdLong());
	}
	
	public void invite(Role role) {
		this.whitelistedRoles.add(role.getIdLong());
	}
	
	public boolean isInvited(Member member) {
		for (long whitelistedMember : this.whitelistedMembers) {
			if (member.getIdLong() == whitelistedMember) {
				return true;
			}
		}
		
		for (long whitelistedRole : this.whitelistedRoles) {
			for (Role role : member.getRoles()) {
				if (role.getIdLong() == whitelistedRole) {
					return true;
				}
			}
		}
		
		return false;
	}
	
	public void finish() {
		this.done = true;
	}
	
	public boolean hasFinished() {
		return this.done;
	}
	
	public boolean isInviteOnly() {
		return this.inviteOnly;
	}
	
	public UnoSession setChannel(TextChannel channel) {
		this.channelId = channel.getIdLong();
		
		return this;
	}
	
	public List<UnoCard> getDeck() {
		return this.deck;
	}
	
	public Colour getLastColour() {
		if (this.lastColour == null) {
			return this.getLastCard().getColour();
		} else {
			return this.lastColour;
		}
	}
	
	public UnoCard getLastCard() {
		return this.deck.get(this.deck.size() - 1);
	}
	
	public List<UnoPlayer> getPlayers() {
		return this.players;
	}
	
	public UnoPlayer getCurrentPlayer() {
		return this.currentPlayer;
	}
	
	public UnoPlayer getNextPlayer(int turns) {
		int index = this.players.indexOf(this.currentPlayer);
		
		if (this.incremental) {
			index += turns;
			if (index > this.players.size() - 1) {
				return this.players.get(index - this.players.size());
			}
		} else {
			index -= turns;
			if (index < 0) {
				return this.players.get(index + this.players.size());
			}
		}
		
		return this.players.get(index);
	}
	
	public UnoPlayer getNextPlayer() {
		return this.getNextPlayer(1);
	}
	
	public UnoSession nextPlayer() {
		CardType lastCardType = this.getLastCard().getCardType(); 
		if (lastCardType.equals(CardType.REVERSE)) {
			this.incremental = !this.incremental;
			
			this.currentPlayer = this.getNextPlayer();
		} else if (lastCardType.equals(CardType.SKIP)) {
			this.currentPlayer = this.getNextPlayer(2);
		} else if (lastCardType.equals(CardType.PLUS_TWO)) {
			this.twoStack += 2;
			this.currentPlayer = this.getNextPlayer();
		} else if (lastCardType.equals(CardType.PLUS_FOUR)) {
			this.fourStack += 4;
			this.currentPlayer = this.getNextPlayer();
		} else {
			this.currentPlayer = this.getNextPlayer();
		}
		
		return this;
	}
	
	public UnoSession removeCardsFromDeck(Collection<UnoCard> cards) {
		this.deck.removeAll(cards);
		
		return this;
	}
	
	public UnoSession removeCardFromDeck(UnoCard card) {
		this.deck.remove(card);
		
		return this;
	}
	
	public UnoSession addCardsToDeck(Collection<UnoCard> cards) {
		this.deck.addAll(cards);
		
		return this;
	}
	
	public UnoSession addCardToDeck(UnoCard card) {
		this.deck.add(card);
		
		return this;
	}
	
	public List<UnoCard> addCardsFromDeck(UnoPlayer player, int amount) {
		List<UnoCard> cards = this.deck.subList(0, amount);
		
		this.removeCardsFromDeck(cards);
		player.addCards(cards);
		
		return cards;
	}
	
	public List<UnoCard> drawCardsFromDeck(UnoPlayer player) {
		List<UnoCard> cards = new ArrayList<>();
		for (UnoCard card : this.deck) {
			cards.add(card);
			if (card.isPlayable(this)) {
				break;
			}
		}
		
		this.removeCardsFromDeck(cards);
		player.addCards(cards);
		
		return cards;
	}
	
	public UnoSession addPlayer(Member member) {
		if (this.players.size() == 4) {
			throw new IllegalArgumentException("The session already has max players");
		}
		
		this.players.add(new UnoPlayer(member));

		return this;
	}
	
	public UnoSession removePlayer(User user) {
		return this.removePlayer(user.getIdLong());
	}
	
	public UnoSession removePlayer(long userId) {
		for (UnoPlayer player : this.players) {
			if (player.getUserId() == userId) {
				this.players.remove(player);
				this.deck.addAll(player.getCards());
				
				return this;
			}
		}
		
		throw new IllegalArgumentException("That member is not in the player list");
	}

	public void start(CommandEvent event) {
		StringBuilder errors = new StringBuilder();
		
		Collections.shuffle(this.deck);
		for (UnoPlayer player : this.players) {
			StringBuilder startingDeck = new StringBuilder("Here is your starting deck: ");
			
			Collection<UnoCard> cards = this.deck.subList(0, 7);
			for (UnoCard card : cards) {
				startingDeck.append(card.getEmote());
			}
			
			this.deck.removeAll(cards);
			player.addCards(cards);
			
			User user = player.getUser();
			if (user != null) {
				user.openPrivateChannel().queue(channel -> {
					channel.sendMessage(startingDeck.toString()).queue();
				}, e -> {
					errors.append("**" + user.getAsTag() + "** has been removed from the game due to having dms closed.\n");
					this.removePlayer(user);
				});
			} else {
				errors.append("**" + player.getUserId() + "** has been removed from the game as I could not find them.\n");
				this.removePlayer(player.getUserId());
			}
		}
		
		if (!errors.toString().isEmpty()) {
			event.getTextChannel().sendMessage(errors.toString()).queue();
		}
		
		if (this.players.size() < 2) {
			throw new IllegalArgumentException("You need at least 2 players to start an uno game");
		}
		
		event.getTextChannel().sendMessage("The starting card is: " + this.getLastCard().getEmote()).queue();
		
		this.currentPlayer = this.players.get(0);
		
		this.playerGo(event);
	}
	
	public void playerGo(CommandEvent event) {
		List<UnoCard> playableCards = this.currentPlayer.getPlayableCards(this);
		if (playableCards.isEmpty()) {
			this.drawCards(event, () -> {
				if (this.players.size() < 2) {
					throw new IllegalArgumentException("Everyone left the uno game");
				}
				
				this.nextPlayer();
				
				if (!this.hasFinished()) {
					this.playerGo(event);
				}
			});
		} else {
			this.playCards(event, playableCards, () -> {
				if (this.players.size() < 2) {
					throw new IllegalArgumentException("Everyone left the uno game");
				}
				
				this.nextPlayer();
				
				if (!this.hasFinished()) {
					this.playerGo(event);
				}
			});
		}
	}
	
	private void playCards(CommandEvent event, List<UnoCard> playableCards, Runnable runnable) {
		StringBuilder errors = new StringBuilder();
		TextChannel textChannel = this.getChannel();
		if (textChannel == null) {
			this.finish();
			return;
		}
		
		playableCards.add(UnoCard.DRAW_CARD);
		
		User currentPlayer = this.currentPlayer.getUser();
		if (currentPlayer != null) {
			currentPlayer.openPrivateChannel().queue(channel -> {
				PagedResult<UnoCard> paged = new PagedResult<>(playableCards)
						.setPerPage(15)
						.setSelectableByIndex(true)
						.setReturnFirstOnTimeout(true)
						.setAuthor("Reply with the number of what card you want to play", null, null)
						.setFunction(playableCard -> playableCard.getEmote() + " - " + playableCard.getName());
				
				PagedUtils.getPagedResult(event, channel, paged, 30, pagedReturn -> {
					UnoCard card = pagedReturn.getData();
					if (card.equals(UnoCard.DRAW_CARD)) {
						this.drawCards(event, () -> runnable.run());
					} else {
						this.handleColourChange(event, channel, card, colour -> {
							this.currentPlayer.removeCards(card);
							this.addCardToDeck(card);
							
							int cardSize = this.currentPlayer.getCards().size();
							
							textChannel.sendMessageFormat(
									"%s**%s** played a %s and they now have %d card%s%s", 
									this.currentPlayer.getCards().size() == 1 ? "**UNO!**, " : "", 
									currentPlayer.getAsTag(), 
									card.getEmote(), 
									cardSize, 
									cardSize == 1 ? "" : "s", 
									colour.equals(card.getColour()) ? "" : ", the colour has been changed to " + GeneralUtils.title(colour.toString())		
							).queue();
							
							runnable.run();
						});
					}
				});
			}, e -> {
				errors.append("**" + currentPlayer.getAsTag() + "** has been removed from the game due to having dms closed.\n");
				this.removePlayer(currentPlayer);
				
				runnable.run();
			});
		} else {
			errors.append("**" + this.currentPlayer.getUserId() + "** has been removed from the game as I could not find them.\n");
			this.removePlayer(this.currentPlayer.getUserId());
			
			runnable.run();
		}
		
		if (!errors.toString().isEmpty()) {
			textChannel.sendMessage(errors.toString()).queue();
		}
	}
	
	private void drawCards(CommandEvent event, Runnable runnable) {
		StringBuilder errors = new StringBuilder();
		TextChannel textChannel = this.getChannel();
		if (textChannel == null) {
			this.finish();
			return;
		}
		
		List<UnoCard> drawnCards = this.drawCardsFromDeck(this.currentPlayer);
		UnoCard playableCard = drawnCards.get(drawnCards.size() - 1);
		
		StringBuilder message = new StringBuilder("You drew **" + drawnCards.size() + "** card" + (drawnCards.size() == 1 ? "" : "s") + ": ");
		for (UnoCard drawnCard : drawnCards) {
			message.append(drawnCard.getEmote());
		}
		
		message.append("\n" + playableCard.getEmote() + " is playable would you like to keep or play that card?\n\n1. **Keep**\n2. **Play**");
		
		User currentPlayer = this.currentPlayer.getUser();
		if (currentPlayer != null) {
			currentPlayer.openPrivateChannel().queue(channel -> {
				channel.sendMessage(message.toString()).queue(m -> {
					StringBuilder announcement = new StringBuilder("**" + currentPlayer.getAsTag() + "** drew **" + drawnCards.size() + "** card" + (drawnCards.size() == 1 ? "" : "s"));
					PagedUtils.getResponse(event, 30, messageEvent -> {
						MessageChannel privateChannel = messageEvent.getChannel();
						String content = messageEvent.getMessage().getContentRaw();
						if (channel != null) {
							if (GeneralUtils.isNumber(content) && privateChannel.equals(channel)) {
								int number = Integer.parseInt(content);
								return number == 1 || number == 2;
							}
						}
						
						return false;
					}, () -> {
						channel.sendMessage("You took too long to play, so I played the card for you").queue();
						this.currentPlayer.removeCards(playableCard);
						this.addCardToDeck(playableCard);
						
						if (this.currentPlayer.getCards().size() == 1) {
							announcement.insert(0, "**UNO!**, ");
						}
						
						textChannel.sendMessage(announcement.append(" (" + this.currentPlayer.getCards().size() + " total) and played a " + playableCard.getEmote()).toString()).queue();
						
						runnable.run();
					}, reply -> {
						int number = Integer.parseInt(reply.getContentRaw());
						if (number == 2) {
							this.handleColourChange(event, channel, playableCard, colour -> {
								this.currentPlayer.removeCards(playableCard);
								this.addCardToDeck(playableCard);
								
								if (this.currentPlayer.getCards().size() == 1) {
									announcement.insert(0, "**UNO!**, ");
								}
								
								announcement.append(" (" + this.currentPlayer.getCards().size() + " total) and played a " + playableCard.getEmote() + (colour.equals(playableCard.getColour()) ? "" : ", the colour has been changed to " + GeneralUtils.title(colour.toString())));
								
								textChannel.sendMessage(announcement.toString()).queue();
								
								runnable.run();
							});
						} else {
							if (this.currentPlayer.getCards().size() == 1) {
								announcement.insert(0, "**UNO!**, ");
							}
							
							textChannel.sendMessage(announcement.append(" (" + this.currentPlayer.getCards().size() + " total) and kept their card").toString());
							
							runnable.run();
						}
					});
				});
			}, e -> {
				errors.append("**" + currentPlayer.getAsTag() + "** has been removed from the game due to having dms closed.\n");
				this.removePlayer(currentPlayer);
				
				runnable.run();
			});
		} else {
			errors.append("**" + this.currentPlayer.getUserId() + "** has been removed from the game as I could not find them.\n");
			this.removePlayer(this.currentPlayer.getUserId());
			
			runnable.run();
		}
		
		if (!errors.toString().isEmpty()) {
			textChannel.sendMessage(errors.toString()).queue();
		}
	}
	
	public void handleColourChange(CommandEvent event, PrivateChannel channel, UnoCard lastCard, Consumer<Colour> colour) {
		if (lastCard.equals(UnoCard.PLUS_FOUR) || lastCard.equals(UnoCard.COLOUR_CHANGE)) {
			PagedResult<Colour> paged = new PagedResult<>(Colour.getChooseableColours())
					.setSelectableByIndex(true)
					.setReturnFirstOnTimeout(true)
					.setAuthor("Reply with the number of what colour you want to change to", null, null)
					.setFunction(c -> GeneralUtils.title(c.toString()));
			
			PagedUtils.getPagedResult(event, channel, paged, 30, pagedReturn -> {
				Colour newColour = pagedReturn.getData();
				this.lastColour = newColour;
				colour.accept(newColour);
			});
		} else {
			this.lastColour = lastCard.getColour();
			colour.accept(this.lastColour);
		}
	}

}
