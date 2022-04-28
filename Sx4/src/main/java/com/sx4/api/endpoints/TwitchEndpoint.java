package com.sx4.api.endpoints;

import com.mongodb.client.model.Filters;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.entities.twitch.TwitchStream;
import com.sx4.bot.entities.twitch.TwitchStreamType;
import com.sx4.bot.entities.twitch.TwitchStreamer;
import com.sx4.bot.events.twitch.TwitchStreamStartEvent;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.utility.HmacUtility;
import okhttp3.Request;
import org.bson.Document;

import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Path("api")
public class TwitchEndpoint {

	private final Sx4 bot;

	private final Set<String> messageIds;

	public TwitchEndpoint(Sx4 bot) {
		this.bot = bot;
		this.messageIds = new HashSet<>();
	}

	@POST
	@Path("twitch")
	public Response postTwitch(final String body, @HeaderParam("Twitch-Eventsub-Message-Id") String messageId, @HeaderParam("Twitch-Eventsub-Message-Timestamp") String timestamp, @HeaderParam("Twitch-Eventsub-Message-Signature") String signature, @HeaderParam("Twitch-Eventsub-Message-Type") String type) {
		String hash;
		try {
			hash = "sha256=" + HmacUtility.getSignatureHex(this.bot.getConfig().getTwitchEventSecret(), messageId + timestamp + body, HmacUtility.HMAC_SHA256);
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			return Response.status(500).build();
		}

		if (!hash.equals(signature)) {
			return Response.status(401).build();
		}

		if (!this.messageIds.add(messageId)) {
			return Response.status(204).build();
		}

		Document data = Document.parse(body);
		Document subscription = data.get("subscription", Document.class);

		if (type.equals("webhook_callback_verification")) {
			Document subscriptionData = new Document("subscriptionId", subscription.getString("id"))
				.append("streamerId", subscription.getEmbedded(List.of("condition", "broadcaster_user_id"), String.class))
				.append("type", type);

			this.bot.getMongo().insertTwitchSubscription(subscriptionData).whenComplete(MongoDatabase.exceptionally());

			return Response.ok(data.getString("challenge")).build();
		}

		String subscriptionType = subscription.getString("type");
		if (subscriptionType.equals("stream.online")) {
			Document event = data.get("event", Document.class);

			String streamId = event.getString("id");
			String streamerName = event.getString("broadcaster_user_name");
			String streamerId = event.getString("broadcaster_user_id");
			String streamerLogin = event.getString("broadcaster_user_login");
			TwitchStreamType streamType = TwitchStreamType.fromIdentifier(event.getString("type"));
			OffsetDateTime streamStart = OffsetDateTime.parse(event.getString("started_at"));

			Request request = new Request.Builder()
				.url("https://api.twitch.tv/helix/streams?user_id=" + streamerId)
				.addHeader("Authorization", "Bearer " + this.bot.getTwitchConfig().getToken())
				.addHeader("Client-Id", this.bot.getConfig().getTwitchClientId())
				.build();

			this.bot.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
				Document json = Document.parse(response.body().string());

				Document stream = json.getList("data", Document.class).stream()
					.filter(d -> d.getString("type").equals(streamType.getIdentifier()))
					.findFirst()
					.orElse(null);

				if (stream == null) {
					System.err.println("Failed to retrieve stream data for streamer id: " + streamerId);
					return;
				}

				String title = stream.getString("title");
				String game = stream.getString("game_name");
				String preview = "https://static-cdn.jtvnw.net/previews-ttv/live_user_" + streamerLogin + "-1320x744.png";

				this.bot.getTwitchManager().onEvent(new TwitchStreamStartEvent(new TwitchStream(streamId, streamType, preview, title, game, streamStart), new TwitchStreamer(streamerId, streamerName, streamerLogin)));
			});
		} else if (subscriptionType.equals("revocation")) {
			String status = subscription.getString("status");
			if (status.equals("user_removed")) {
				this.bot.getMongo().deleteManyTwitchNotifications(Filters.eq("streamerId", subscription.getEmbedded(List.of("condition", "broadcaster_user_id"), String.class))).whenComplete(MongoDatabase.exceptionally());
			}
		}

		return Response.status(204).build();
	}

}
