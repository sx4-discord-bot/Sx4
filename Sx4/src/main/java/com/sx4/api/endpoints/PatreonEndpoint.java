package com.sx4.api.endpoints;

import com.sx4.bot.core.Sx4;
import com.sx4.bot.events.patreon.PatreonMemberUpdateEvent;
import com.sx4.bot.events.patreon.PatreonPledgeCreateEvent;
import com.sx4.bot.events.patreon.PatreonPledgeDeleteEvent;
import com.sx4.bot.events.patreon.PatreonPledgeUpdateEvent;
import com.sx4.bot.utility.HmacUtility;
import org.bson.Document;

import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

@Path("api")
public class PatreonEndpoint {

	private final Sx4 bot;

	public PatreonEndpoint(Sx4 bot) {
		this.bot = bot;
	}
	
	@POST
	@Path("patreon")
	public Response postPatreon(final String body, @HeaderParam("X-Patreon-Signature") final String signature, @HeaderParam("X-Patreon-Event") final String event) {
		String hash;
		try {
			hash = HmacUtility.getSignature(this.bot.getConfig().getPatreonWebhookSecret(), body, HmacUtility.HMAC_MD5);
		} catch (InvalidKeyException | NoSuchAlgorithmException e) {
			return Response.status(500).build();
		}
		
		if (!hash.equals(signature)) {
			return Response.status(401).build();
		}
		
		Document document = Document.parse(body);
		int centsDonated = document.getEmbedded(List.of("data", "attributes", "currently_entitled_amount_cents"), int.class);
		
		Document user = document.getList("included", Document.class).stream()
			.filter(included -> included.getString("type").equals("user"))
			.findFirst()
			.orElse(null);
	    
	    if (user != null) {
	        String discordIdString = user.getEmbedded(List.of("attributes", "social_connections", "discord", "user_id"), String.class), id = user.getString("id");
	        long discordId = discordIdString == null ? 0L : Long.parseLong(discordIdString);

			switch (event) {
				case "members:pledge:delete":
					this.bot.getPatreonManager().onPatreon(new PatreonPledgeDeleteEvent(discordId, id));
					break;
				case "members:pledge:update":
					this.bot.getPatreonManager().onPatreon(new PatreonPledgeUpdateEvent(discordId, id, centsDonated));
					break;
				case "members:pledge:create":
					this.bot.getPatreonManager().onPatreon(new PatreonPledgeCreateEvent(discordId, id, centsDonated));
					break;
				case "members:update":
					this.bot.getPatreonManager().onPatreon(new PatreonMemberUpdateEvent(discordId, id));
			}
	    }
		
		return Response.status(204).build();
	}
	
}
