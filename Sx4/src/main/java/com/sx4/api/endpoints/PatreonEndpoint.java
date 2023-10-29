package com.sx4.api.endpoints;

import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.events.patreon.PatreonEvent;
import com.sx4.bot.utility.HmacUtility;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import net.dv8tion.jda.api.entities.IncomingWebhookClient;
import net.dv8tion.jda.api.entities.WebhookClient;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.bson.Document;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

@Path("api")
public class PatreonEndpoint {

	private final Sx4 bot;

	private final IncomingWebhookClient webhook;

	public PatreonEndpoint(Sx4 bot) {
		this.bot = bot;

		this.webhook = WebhookClient.createClient(this.bot.getShardManager().getShards().get(0), Long.toString(this.bot.getConfig().getPatreonWebhookId()), this.bot.getConfig().getPatreonWebhookToken());
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

		MessageCreateData message = new MessageCreateBuilder()
			.setContent("Patreon payload received")
			.addFiles(FileUpload.fromData(document.toJson(MongoDatabase.PRETTY_JSON).getBytes(StandardCharsets.UTF_8), "patreon.json"))
			.build();

		this.webhook.sendMessage(message).queue();

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
