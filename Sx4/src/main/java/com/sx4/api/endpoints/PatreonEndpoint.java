package com.sx4.api.endpoints;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.send.WebhookMessage;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.events.patreon.PatreonEvent;
import com.sx4.bot.utility.HmacUtility;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.bson.Document;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

@Path("api")
public class PatreonEndpoint {

	private final Sx4 bot;

	private final WebhookClient webhook;

	public PatreonEndpoint(Sx4 bot) {
		this.bot = bot;

		this.webhook = new WebhookClientBuilder(this.bot.getConfig().getPatreonWebhookId(), this.bot.getConfig().getPatreonWebhookToken())
			.setHttpClient(this.bot.getHttpClient())
			.build();
	}
	
	@POST
	@Path("patreon")
	public Response postPatreon(final String body, @HeaderParam("X-Patreon-Signature") final String signature, @HeaderParam("X-Patreon-Event") final String event) {
		String hash;
		try {
			hash = HmacUtility.getSignatureHex(this.bot.getConfig().getPatreonWebhookSecret(), body, HmacUtility.HMAC_MD5);
		} catch (InvalidKeyException | NoSuchAlgorithmException e) {
			return Response.status(500).build();
		}
		
		if (!hash.equals(signature)) {
			return Response.status(401).build();
		}
		
		Document document = Document.parse(body);

		WebhookMessage message = new WebhookMessageBuilder()
			.setContent("Patreon payload received")
			.addFile("patreon.json", document.toJson(MongoDatabase.PRETTY_JSON).getBytes(StandardCharsets.UTF_8))
			.build();

		this.webhook.send(message);

		int totalAmount = document.getEmbedded(List.of("data", "attributes", "lifetime_support_cents"), 0);
		if (totalAmount == 0) {
			return Response.status(204).build();
		}
		
		Document user = document.getList("included", Document.class).stream()
			.filter(included -> included.getString("type").equals("user"))
			.findFirst()
			.orElse(null);
	    
	    if (user != null) {
	        String discordId = user.getEmbedded(List.of("attributes", "social_connections", "discord", "user_id"), String.class);
	        if (discordId != null) {
				this.bot.getPatreonManager().onPatreonEvent(new PatreonEvent(Long.parseLong(discordId), totalAmount));
			}
	    }
		
		return Response.status(204).build();
	}
	
}
