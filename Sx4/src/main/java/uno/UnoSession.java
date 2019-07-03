package uno;

import java.util.List;

import com.sx4.core.Sx4Bot;
import com.sx4.utils.GiveawayUtils;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;

public class UnoSession {
	
	private List<UnoCard> deck;
	private List<Player> players; 
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
	
	public List<UnoCard> getDeck() {
		return this.deck;
	}
	
	public UnoCard getLastCard() {
		return this.deck.get(this.deck.size() - 1);
	}
	
	public UnoSession addPlayer(Member member) {
		if (this.players.size() == 4) {
			throw new IllegalArgumentException("The session already has max players");
		}
		
		this.players.add(new Player(member));

		return this;
	}
	
	public UnoSession removePlayer(Member member) {
		for (Player player : this.players) {
			if (player.getPlayerId() == member.getIdLong()) {
				this.players.remove(player);
				this.deck.addAll(player.getCards());
				
				return this;
			}
		}
		
		throw new IllegalArgumentException("That member is not in the player list");
	}
	
	public void shuffleCards() {
		for (Player player : this.players) {
			List<UnoCard> cards = GiveawayUtils.getRandomSampleAndRemove(this.deck, 7);
			player.addCards(cards);
		}
	}

}
