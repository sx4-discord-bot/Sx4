package com.sx4.api.endpoints;

import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.HmacUtility;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.*;
import okhttp3.Request;
import org.bson.conversions.Bson;
import org.json.JSONObject;
import org.json.XML;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Path("redirect")
public class SteamEndpoint {

	private final Document success, failure;

	private final Sx4 bot;

	public SteamEndpoint(Sx4 bot) throws IOException {
		this.bot = bot;
		this.success = Jsoup.parse(new File("resources/steam_success.html"), "UTF-8", "");
		this.failure = Jsoup.parse(new File("resources/steam_failure.html"), "UTF-8", "");
	}

	public String setText(Document document, String text) {
		document.select("h1").first().text(text);
		return document.html();
	}

	@GET
	@Path("steam")
	@Produces(MediaType.TEXT_HTML)
	public void getSteam(@Context UriInfo info, @QueryParam("openid.identity") final String identity, @QueryParam("user_id") final long userId, @QueryParam("timestamp") long timestamp, @QueryParam("signature") String signature, @Suspended final AsyncResponse response) {
		Document failureCopy = this.failure.clone();

		long timestampNow = Instant.now().getEpochSecond();
		if (timestampNow - timestamp >= 300) {
			response.resume(Response.status(401).entity(this.setText(failureCopy, "Authorization link timed out")).build());
			return;
		}

		String hash;
		try {
			hash = HmacUtility.getSignatureHex(this.bot.getConfig().getSteam(), Long.toString(userId) + timestamp, HmacUtility.HMAC_MD5);
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			response.resume(Response.status(500).entity(this.setText(failureCopy, "Something went wrong while authenticating your account")).build());
			return;
		}

		if (!hash.equals(signature)) {
			response.resume(Response.status(401).entity(this.setText(failureCopy, "Unauthorized")).build());
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
					response.resume(Response.status(401).entity(this.setText(failureCopy, "Unauthorized")).build());
					return;
				}
			}

			long steamId = Long.parseLong(identity.substring(identity.lastIndexOf('/') + 1));

			Request steamRequest = new Request.Builder()
				.url("https://steamcommunity.com/profiles/" + steamId + "?xml=1")
				.build();

			this.bot.getHttpClient().newCall(steamRequest).enqueue((HttpCallback) profileResponse -> {
				JSONObject data = XML.toJSONObject(profileResponse.body().string());
				JSONObject profile = data.getJSONObject("profile");

				org.bson.Document connection = new org.bson.Document("id", steamId)
					.append("name", profile.getString("steamID"));

				List<Bson> update = List.of(Operators.set("connections.steam", Operators.concatArrays(List.of(connection), Operators.filter(Operators.ifNull("$connections.steam", Collections.EMPTY_LIST), Operators.ne("$$this.id", steamId)))));

				this.bot.getMongo().updateUserById(userId, update).whenComplete((result, exception) -> {
					if (ExceptionUtility.sendErrorMessage(exception)) {
						response.resume(Response.status(500).entity(this.setText(failureCopy, "Something went wrong while authenticating your account")).build());
						return;
					}

					Document successCopy = this.success.clone();

					response.resume(Response.ok(this.setText(successCopy, "Connected your steam account to Sx4")).build());
				});
			});
		});
	}

}
