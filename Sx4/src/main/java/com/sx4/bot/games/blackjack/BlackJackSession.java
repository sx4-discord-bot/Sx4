package com.sx4.bot.games.blackjack;

import java.util.Collections;
import java.util.List;

import com.sx4.bot.core.Sx4Bot;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.internal.requests.RestActionImpl;

public class BlackJackSession {
	
	private List<BlackJackCard> deck = BlackJackCard.CARDS;
	private List<BlackJackPlayer> players;
	
	private BlackJackPlayer currentPlayer;
	
	private long guildId;
	private long channelId;
	private long messageId;
	
	public BlackJackSession(TextChannel channel, Member member) {
		this.guildId = channel.getGuild().getIdLong();
		this.channelId = channel.getIdLong();
		
		this.players.add(new BlackJackPlayer(1));
		this.players.add(new BlackJackPlayer(member));
	}
	
	public BlackJackSession(TextChannel channel) {
		this.guildId = channel.getGuild().getIdLong();
		this.channelId = channel.getIdLong();
		
		this.players.add(new BlackJackPlayer(1));
	}
	
	public long getGuildId() {
		return this.guildId;
	}
	
	public long getChannelId() {
		return this.channelId;
	}
	
	public long getMessageId() {
		return this.messageId;
	}
	
	public Guild getGuild() {
		return Sx4Bot.getShardManager().getGuildById(this.guildId);
	}
	
	public TextChannel getChannel() {
		Guild guild = this.getGuild();
		
		return guild == null ? null : guild.getTextChannelById(this.channelId);
	}
	
	public MessageEmbed getEmbed() {
		EmbedBuilder embed = new EmbedBuilder();
		embed.setTitle("Black Jack");
		
		for (BlackJackPlayer player : this.players) {
			if (player.isDealer()) {
				List<BlackJackCard> dealerCards = player.getDeck();
				if (dealerCards.size() == 2) {
					BlackJackCard firstCard = dealerCards.get(0); 
					embed.addField("Dealer", firstCard.getEmote() + "\nWorth: " + firstCard.getWorth(), false);
				} else {
					embed.addField("Dealer", player.getDeckDisplay() + "\nWorth: " + player.getDeckWorth(), false);
				}
			} else {
				User user = player.getUser();
				if (user != null) {
					embed.addField(user.getAsTag(), player.getDeckDisplay() + "\nWorth: " + player.getDeckWorth(), false);
				}
			}
		}
		
		return embed.build();
	}
	
	public RestAction<Message> retrieveMessage() {
		TextChannel channel = this.getChannel();
		
		return channel == null ? new RestActionImpl<>(Sx4Bot.getShardManager().getShardById(0), null) : channel.retrieveMessageById(this.messageId);
	}
	
	public void refreshMessage() {
		this.retrieveMessage().queue(message -> {
			if (message != null) {
				message.delete().queue();
			}
		}, e -> {});
		
		TextChannel channel = this.getChannel();
		if (channel != null) {
			channel.sendMessage(this.getEmbed()).queue();
		}
	}
	
	public BlackJackPlayer getDealer() {
		return this.players.get(0);
	}
	
	public List<BlackJackPlayer> getPlayers() {
		return this.players;
	}
	
	public boolean isFull() {
		return this.players.size() == 5;
	}
	
	public BlackJackPlayer getCurrentPlayer() {
		return this.currentPlayer;
	}
	
	public boolean isNextPlayer() {
		int playable = 0;
		for (BlackJackPlayer player : this.players) {
			if (!player.isDealer() && !player.isBust() && !player.isStanding()) {
				playable++;
			}
		}
		
		return playable != 0;
	}
	
	public BlackJackPlayer getNextPlayer() {
		int index = this.players.indexOf(this.currentPlayer) + 1;
	
		BlackJackPlayer nextPlayer = this.players.get(index);
		while (nextPlayer.isDealer() || nextPlayer.isBust() || nextPlayer.isStanding()) {
			index++;
			if (index > this.players.size()) {
				index = index - this.players.size();
			}
			
			nextPlayer = this.players.get(index);
		}
		
		return nextPlayer;
	}
	
	public void nextPlayer() {
		this.currentPlayer = this.getNextPlayer();
	}
	
	public void drawCard(BlackJackPlayer player) {
		BlackJackCard card = this.deck.get(0);
		
		this.deck.remove(card);
		player.addCard(card);
	}
	
	public void startGame() {
		Collections.shuffle(this.deck);
		for (BlackJackPlayer player : this.players) {
			List<BlackJackCard> cards = this.deck.subList(0, 2);
			this.deck.removeAll(cards);
			player.addCards(cards);
		}
		
		TextChannel channel = this.getChannel();
		if (channel == null) {
			return;
		}
		
		channel.sendMessage(this.getEmbed()).queue();
		
		this.currentPlayer = this.players.get(1);
	}
	
}
