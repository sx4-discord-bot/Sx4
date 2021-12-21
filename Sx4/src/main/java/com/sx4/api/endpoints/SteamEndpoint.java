package com.sx4.api.endpoints;

import com.mongodb.client.model.Updates;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.HmacUtility;
import okhttp3.Request;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

@Path("redirect")
public class SteamEndpoint {

	private final Sx4 bot;

	public SteamEndpoint(Sx4 bot) {
		this.bot = bot;
	}

	@GET
	@Path("steam")
	@Produces(MediaType.TEXT_PLAIN)
	public void getSteam(@Context UriInfo info, @QueryParam("openid.identity") final String identity, @QueryParam("user_id") final long userId, @QueryParam("timestamp") long timestamp, @QueryParam("signature") String signature, @Suspended final AsyncResponse response) {
		long timestampNow = Instant.now().getEpochSecond();
		if (timestampNow - timestamp >= 300) {
			response.resume(Response.status(401).entity("Timed out").build());
			return;
		}

		String hash;
		try {
			hash = HmacUtility.getSignatureHex(this.bot.getConfig().getSteam(), Long.toString(userId) + timestamp, HmacUtility.HMAC_MD5);
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			response.resume(Response.status(500).entity("Something went wrong while authenticating your account").build());
			return;
		}

		if (!hash.equals(signature)) {
			response.resume(Response.status(401).entity("Unauthorized").build());
		}

		StringBuilder url = new StringBuilder("https://steamcommunity.com/openid/login");

		MultivaluedMap<String, String> parameters = info.getQueryParameters();
		boolean first = true;
		for (String key : parameters.keySet()) {
			if (first) {
				url.append("?");
				first = false;
			} else {
				url.append("&");
			}

			url.append(key).append("=").append(URLEncoder.encode(key.equals("openid.mode") ? "check_authentication" : parameters.get(key).get(0), StandardCharsets.UTF_8));
		}

		Request request = new Request.Builder()
			.url(url.toString())
			.build();

		this.bot.getHttpClient().newCall(request).enqueue((HttpCallback) steamResponse -> {
			String body = steamResponse.body().string();

			String[] lines = body.split("\n");
			for (String line : lines) {
				String[] keyValue = line.split(":");
				if (keyValue[0].equals("is_valid") && !keyValue[1].equals("true")) {
					response.resume(Response.status(401).entity("Unauthorized").build());
					return;
				}
			}

			long steamId = Long.parseLong(identity.substring(identity.lastIndexOf('/') + 1));
			this.bot.getMongo().updateUserById(userId, Updates.set("connections.steam", steamId)).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendErrorMessage(exception)) {
					response.resume(Response.status(500).entity("Something went wrong while authenticating your account").build());
					return;
				}

				response.resume(Response.ok("Connected your steam account to Sx4").build());
			});
		});
	}

}
