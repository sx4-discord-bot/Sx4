package com.sx4.api.endpoints;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.sx4.bot.config.Config;
import com.sx4.bot.database.Database;
import com.sx4.bot.entities.youtube.YouTubeChannel;
import com.sx4.bot.entities.youtube.YouTubeVideo;
import com.sx4.bot.events.youtube.YouTubeDeleteEvent;
import com.sx4.bot.events.youtube.YouTubeUpdateEvent;
import com.sx4.bot.events.youtube.YouTubeUpdateTitleEvent;
import com.sx4.bot.events.youtube.YouTubeUploadEvent;
import com.sx4.bot.managers.YouTubeManager;
import com.sx4.bot.utility.ExceptionUtility;
import org.bson.Document;
import org.json.JSONObject;
import org.json.XML;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@Path("api")
public class YouTubeEndpoint {

	@GET
	@Path("youtube")
	@Produces(MediaType.TEXT_PLAIN)
	public Response getYoutube(@QueryParam("hub.topic") final String topic, @QueryParam("hub.verify_token") final String authorization, @QueryParam("hub.challenge") final String challenge, @QueryParam("hub.lease_seconds") final long seconds) {
		if (authorization != null && authorization.equals(Config.get().getYoutube())) {
			YouTubeManager manager = YouTubeManager.get();
			String channelId = topic.substring(topic.lastIndexOf('=') + 1);
			
			Database.get().updateYouTubeSubscriptionById(channelId, Updates.set("resubscribeAt", Clock.systemUTC().instant().getEpochSecond() + seconds)).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendErrorMessage(exception)) {
					return;
				}
				
				manager.putResubscription(channelId, seconds);
			});
			
			return Response.ok(challenge).build();
		} else {
			return Response.status(401).build();
		}
	}
	
	@POST
	@Path("/youtube")
	public Response postYoutube(final String body) {
		YouTubeManager manager = YouTubeManager.get();
		Database database = Database.get();
		
		JSONObject json = XML.toJSONObject(body);
		
		JSONObject feed = json.getJSONObject("feed");
		if (feed.has("at:deleted-entry")) {
			JSONObject entry = feed.getJSONObject("at:deleted-entry");
			JSONObject channel = entry.getJSONObject("at:by");
			
			String videoId = entry.getString("ref").substring(9), videoDeletedAt = entry.getString("when");
			String channelId = channel.getString("uri").substring(32), channelName = channel.getString("name");
			
			manager.onYouTube(new YouTubeDeleteEvent(new YouTubeChannel(channelId, channelName), videoId, videoDeletedAt));
		} else {
			JSONObject entry = feed.getJSONObject("entry");
			
			String videoTitle = entry.getString("title"), videoId = entry.getString("yt:videoId"), videoUpdatedAt = entry.getString("updated"), videoPublishedAt = entry.getString("published");
			String channelId = entry.getString("yt:channelId"), channelName = entry.getJSONObject("author").getString("name");
			
			YouTubeChannel channel = new YouTubeChannel(channelId, channelName);
			YouTubeVideo video = new YouTubeVideo(videoId, videoTitle, videoUpdatedAt, videoPublishedAt);
			
			Document data = database.getYouTubeNotificationLog(Filters.eq("videoId", videoId), Projections.include("title"));
			String oldTitle = data.getString("title");
			
			if (data.isEmpty() && Duration.between(video.getPublishedAt(), ZonedDateTime.now(ZoneOffset.UTC)).toMinutes() <= 60) {
				manager.onYouTube(new YouTubeUploadEvent(channel, video));
			} else if (!data.isEmpty() && oldTitle.equals(videoTitle)) {
				manager.onYouTube(new YouTubeUpdateEvent(channel, video));
			} else {
				manager.onYouTube(new YouTubeUpdateTitleEvent(channel, video, oldTitle));
			}
		}
		
		return Response.status(204).build();
	}
	
}
