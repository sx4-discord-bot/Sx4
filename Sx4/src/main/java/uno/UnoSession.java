package uno;

import java.util.List;

import com.sx4.core.Sx4Bot;
import com.sx4.utils.GiveawayUtils;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;

public class UnoSession {
	
	private List<UnoCard> deck;
	private List<Player> players; 
	private Player currentPlayer;
	private boolean incremental = true;
	private long ownerId;
	private long channelId;
	private long guildId;
	
	public UnoSession(TextChannel channel, Member owner) {
		this.ownerId = owner.getIdLong();
		this.guildId = channel.getGuild().getIdLong();
		this.channelId = channel.getIdLong();
		
		this.addPlayer(owner);
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
	
	private Player getNextPlayerByIncrement(int turns) {
		int index = this.players.indexOf(this.currentPlayer);
		
		if (this.incremental) {
			index += turns;
			if (index > this.players.size() - 1) {
				return this.players.get(index - this.players.size());
			} else {
				return this.players.get(index);
			}
		} else {
			index -= turns;
			if (index < 0) {
				return this.players.get(index + this.players.size());
			} else {
				return this.players.get(index);
			}
		}
	}
	
	public UnoSession nextPlayer() {
		CardType lastCardType = this.getLastCard().getCardType(); 
		if (lastCardType.equals(CardType.REVERSE)) {
			this.incremental = !this.incremental;
			
			this.currentPlayer = this.getNextPlayerByIncrement(1);
		} else if (lastCardType.equals(CardType.SKIP)){
			this.currentPlayer = this.getNextPlayerByIncrement(2);
		} else {
			this.currentPlayer = this.getNextPlayerByIncrement(1);
		}
		
		return this;
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
	
	public void start() {
		StringBuilder errors = new StringBuilder();
		for (Player player : this.players) {
			StringBuilder startingDeck = new StringBuilder("Here is your starting deck: ");
			
			List<UnoCard> cards = GiveawayUtils.getRandomSampleAndRemove(this.deck, 7);
			for (UnoCard card : cards) {
				//startingDeck.append(card.getEmote());
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
		
		if (this.players.size() < 2) {
			throw new IllegalArgumentException("You need at least 2 players to start an uno game");
		}
		
		this.getChannel().sendMessage(errors.toString()).queue();
		
		this.currentPlayer = this.players.get(0);
	}

}
