package com.sx4.api.endpoints;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.entities.youtube.YouTubeChannel;
import com.sx4.bot.entities.youtube.YouTubeVideo;
import com.sx4.bot.events.youtube.YouTubeDeleteEvent;
import com.sx4.bot.events.youtube.YouTubeUpdateEvent;
import com.sx4.bot.events.youtube.YouTubeUpdateTitleEvent;
import com.sx4.bot.events.youtube.YouTubeUploadEvent;
import com.sx4.bot.utility.ExceptionUtility;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.bson.Document;
import org.json.JSONObject;
import org.json.XML;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Path("api")
public class YouTubeEndpoint {

	private final Sx4 bot;

	public YouTubeEndpoint(Sx4 bot) {
		this.bot = bot;
	}

	@GET
	@Path("youtube")
	@Produces(MediaType.TEXT_PLAIN)
	public Response getYouTube(@QueryParam("hub.topic") final String topic, @QueryParam("hub.verify_token") final String authorization, @QueryParam("hub.challenge") final String challenge, @QueryParam("hub.lease_seconds") final long seconds) {
		if (authorization != null && authorization.equals(this.bot.getConfig().getYouTube())) {
			String channelId = topic.substring(topic.lastIndexOf('=') + 1);
			
			this.bot.getMongo().updateYouTubeSubscriptionById(channelId, Updates.set("resubscribeAt", Clock.systemUTC().instant().getEpochSecond() + seconds)).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendErrorMessage(exception)) {
					return;
				}
				
				this.bot.getYouTubeManager().putResubscription(channelId, seconds);
			});
			
			return Response.ok(challenge).build();
		} else {
			return Response.status(401).build();
		}
	}
	
	@POST
	@Path("youtube")
	public Response postYouTube(final String body) {
		JSONObject json = XML.toJSONObject(body);
		
		JSONObject feed = json.getJSONObject("feed");
		if (feed.has("at:deleted-entry")) {
			JSONObject entry = feed.getJSONObject("at:deleted-entry");
			JSONObject channel = entry.getJSONObject("at:by");
			
			String videoId = entry.getString("ref").substring(9), videoDeletedAt = entry.getString("when");
			String channelId = channel.getString("uri").substring(32), channelName = channel.getString("name");
			
			this.bot.getYouTubeManager().onYouTube(new YouTubeDeleteEvent(new YouTubeChannel(channelId, channelName), videoId, videoDeletedAt));
		} else {
			JSONObject entry = feed.getJSONObject("entry");

			// If the title is '2' then YouTube will return it as an Integer
			Object videoTitle = entry.get("title");

			String videoId = entry.getString("yt:videoId"), videoUpdatedAt = entry.getString("updated"), videoPublishedAt = entry.getString("published");
			String channelId = entry.getString("yt:channelId"), channelName = entry.getJSONObject("author").getString("name");

			YouTubeChannel channel = new YouTubeChannel(channelId, channelName);
			YouTubeVideo video = new YouTubeVideo(videoId, String.valueOf(videoTitle), videoUpdatedAt, videoPublishedAt);
			
			Document data = this.bot.getMongo().getYouTubeNotificationLog(Filters.eq("videoId", videoId), Projections.include("title"));
			String oldTitle = data == null ? null : data.getString("title");
			
			if (data == null && Duration.between(video.getPublishedAt(), OffsetDateTime.now(ZoneOffset.UTC)).toDays() <= 1) {
				this.bot.getYouTubeManager().onYouTube(new YouTubeUploadEvent(channel, video));
			} else if (data != null && oldTitle.equals(videoTitle)) {
				this.bot.getYouTubeManager().onYouTube(new YouTubeUpdateEvent(channel, video));
			} else {
				this.bot.getYouTubeManager().onYouTube(new YouTubeUpdateTitleEvent(channel, video, oldTitle));
			}
		}
		
		return Response.status(204).build();
	}
	
}
