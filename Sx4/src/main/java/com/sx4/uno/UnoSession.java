package com.sx4.uno;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.jockie.bot.core.command.impl.CommandEvent;
import com.sx4.core.Sx4Bot;
import com.sx4.utils.GeneralUtils;
import com.sx4.utils.GiveawayUtils;
import com.sx4.utils.PagedUtils;
import com.sx4.utils.PagedUtils.PagedResult;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;

public class UnoSession {
	
	private List<UnoCard> deck = UnoCard.DECK;
	private List<Player> players = new ArrayList<>(); 
	private Player currentPlayer;
	
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
	
	public UnoCard getLastCard() {
		return this.deck.get(this.deck.size() - 1);
	}
	
	public List<Player> getPlayers() {
		return this.players;
	}
	
	public Player getCurrentPlayer() {
		return this.currentPlayer;
	}
	
	public Player getNextPlayer(int turns) {
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
	
	public Player getNextPlayer() {
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
	
	public List<UnoCard> addCardsFromDeck(Player player, int amount) {
		List<UnoCard> cards = this.deck.subList(0, amount);
		
		this.removeCardsFromDeck(cards);
		player.addCards(cards);
		
		return cards;
	}
	
	public List<UnoCard> drawCardsFromDeck(Player player) {
		List<UnoCard> cards = new ArrayList<>();
		for (UnoCard card : this.deck) {
			cards.add(card);
			if (card.isPlayable(this.getLastCard())) {
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
		
		this.players.add(new Player(member));

		return this;
	}
	
	public UnoSession removePlayer(User user) {
		return this.removePlayer(user.getIdLong());
	}
	
	public UnoSession removePlayer(long userId) {
		for (Player player : this.players) {
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
		for (Player player : this.players) {
			StringBuilder startingDeck = new StringBuilder("Here is your starting deck: ");
			
			List<UnoCard> cards = GiveawayUtils.getRandomSampleAndRemove(this.deck, 7);
			for (UnoCard card : cards) {
				startingDeck.append(card.getEmote());
			}
			
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
		
		//this.deck = GiveawayUtils.shuffle(this.deck);
		
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
		List<UnoCard> playableCards = this.currentPlayer.getPlayableCards(this.getLastCard());
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
		
		playableCards.add(UnoCard.DRAW_CARD);
		
		User currentPlayer = this.currentPlayer.getUser();
		if (currentPlayer != null) {
			currentPlayer.openPrivateChannel().queue(channel -> {
				PagedResult<UnoCard> paged = new PagedResult<>(playableCards)
						.setPerPage(15)
						.setSelectableByIndex(true)
						.setReturnFirstOnTimeout(true)
						.setAuthor("Reply with the number of what card you want to play", null, null)
						.setFunction(playableCard -> playableCard.getEmote());
				
				PagedUtils.getPagedResult(event, channel, paged, 30, pagedReturn -> {
					UnoCard card = pagedReturn.getObject();
					if (card.equals(UnoCard.DRAW_CARD)) {
						this.drawCards(event, () -> runnable.run());
					} else {
						this.currentPlayer.removeCards(card);
						this.addCardToDeck(card);
						
						int cardSize = this.currentPlayer.getCards().size();
						event.getTextChannel().sendMessage((this.currentPlayer.getCards().size() == 1 ? "**UNO!**, " : "") + " **" + currentPlayer.getAsTag() + "** played a " + card.getEmote() + " they now have " + cardSize + " card" + (cardSize == 1 ? "" : "s")).queue();
					}
					
					runnable.run();
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
			event.getTextChannel().sendMessage(errors.toString()).queue();
		}
	}
	
	private void drawCards(CommandEvent event, Runnable runnable) {
		StringBuilder errors = new StringBuilder();
		
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
					
					event.getTextChannel().sendMessage(announcement.append(" (" + this.currentPlayer.getCards().size() + " total) and played a " + playableCard.getEmote()).toString()).queue();
					
					runnable.run();
				}, reply -> {
					int number = Integer.parseInt(reply.getContentRaw());
					if (number == 2) {
						this.currentPlayer.removeCards(playableCard);
						this.addCardToDeck(playableCard);
						
						if (this.currentPlayer.getCards().size() == 1) {
							announcement.insert(0, "**UNO!**, ");
						}
						
						event.getTextChannel().sendMessage(announcement.append(" (" + this.currentPlayer.getCards().size() + " total) and played a " + playableCard.getEmote()).toString()).queue();
					} else {
						if (this.currentPlayer.getCards().size() == 1) {
							announcement.insert(0, "**UNO!**, ");
						}
						
						event.getTextChannel().sendMessage(announcement.append(" (" + this.currentPlayer.getCards().size() + " total) and kept their card").toString());
					}
					
					runnable.run();
				});
				
				channel.sendMessage(message.toString()).queue();
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
			event.getTextChannel().sendMessage(errors.toString()).queue();
		}
	}

}
