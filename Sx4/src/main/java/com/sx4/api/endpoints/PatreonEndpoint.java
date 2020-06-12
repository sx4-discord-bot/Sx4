package com.sx4.api.endpoints;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import org.bson.Document;

import com.sx4.bot.config.Config;
import com.sx4.bot.events.patreon.PatreonMemberUpdateEvent;
import com.sx4.bot.events.patreon.PatreonPledgeCreateEvent;
import com.sx4.bot.events.patreon.PatreonPledgeDeleteEvent;
import com.sx4.bot.events.patreon.PatreonPledgeUpdateEvent;
import com.sx4.bot.managers.PatreonManager;
import com.sx4.bot.utility.HmacUtility;

@Path("")
public class PatreonEndpoint {
	
	@POST
	@Path("/patreon")
	public Response postPatreon(final String body, @HeaderParam("X-Patreon-Signature") final String signature, @HeaderParam("X-Patreon-Event") final String event) {
		String hash;
		try {
			hash = HmacUtility.getMD5Signature(Config.get().getPatreonWebhookSecret(), body);
		} catch (InvalidKeyException | NoSuchAlgorithmException e) {
			return Response.status(500).build();
		}
		
		if (!hash.equals(signature)) {
			return Response.status(401).build();
		}
		
		Document document = Document.parse(body);
		int centsDonated = document.getEmbedded(List.of("data", "attributes", "currently_entitled_amount_cents"), int.class);
		
		Document user = null;
	    for (Document included : document.getList("included", Document.class)) {
	        if (included.getString("type").equals("user")) {
	            user = included;
	            break;
	        }
	    }
	    
	    if (user != null) {
	        String discordIdString = user.getEmbedded(List.of("attributes", "social_connections", "discord", "user_id"), String.class), id = user.getString("id");
	        long discordId = discordIdString == null ? 0L : Long.valueOf(discordIdString);
	       
	        PatreonManager manager = PatreonManager.get();
	        if (event.equals("members:pledge:delete")) {
	        	manager.onPatreon(new PatreonPledgeDeleteEvent(discordId, id));
	        } else if (event.equals("members:pledge:update")) {
	        	manager.onPatreon(new PatreonPledgeUpdateEvent(discordId, id, centsDonated));
	        } else if (event.equals("members:pledge:create")) {
	        	manager.onPatreon(new PatreonPledgeCreateEvent(discordId, id, centsDonated));
	        } else if (event.equals("members:update")) {
	        	manager.onPatreon(new PatreonMemberUpdateEvent(discordId, id));
	        }
	    }
		
		return Response.status(204).build();
	}
	
}
