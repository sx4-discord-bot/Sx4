package com.sx4.api.endpoints;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import org.json.JSONObject;

import com.sx4.bot.config.Config;
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
		
		JSONObject json = new JSONObject(body), data = json.getJSONObject("data"), attributes = data.getJSONObject("attributes");
		
		int centsDonated = attributes.getInt("campaign_pledge_amount_cents");
		
		return Response.ok().build();
	}
	
}
