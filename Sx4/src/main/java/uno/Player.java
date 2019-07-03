package uno;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.sx4.core.Sx4Bot;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

public class Player {

	private List<UnoCard> cards = new ArrayList<>();
	private long playerId;
	
	public Player(Member member) {
		this.playerId = member.getIdLong();
	}
	
	public Player(Member member, List<UnoCard> cards) {
		this.playerId = member.getIdLong();
		this.cards = cards;
	}
	
	public List<UnoCard> getCards() {
		return this.cards;
	}
	
	public Player addCards(UnoCard... cards) {
		this.cards.addAll(List.of(cards));
		
		return this;
	}
	
	public Player addCards(Collection<UnoCard> cards) {
		this.cards.addAll(cards);
		
		return this;
	}
	
	public long getPlayerId() {
		return this.playerId;
	}
	
	public User getPlayer() {
		return Sx4Bot.getShardManager().getUserById(this.playerId);
	}
	
}
