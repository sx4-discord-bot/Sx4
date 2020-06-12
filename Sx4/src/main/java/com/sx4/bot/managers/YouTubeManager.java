package com.sx4.bot.managers;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.bson.Document;

import com.mongodb.client.model.DeleteOneModel;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.WriteModel;
import com.sx4.bot.config.Config;
import com.sx4.bot.core.Sx4Bot;
import com.sx4.bot.database.Database;
import com.sx4.bot.events.youtube.YouTubeDeleteEvent;
import com.sx4.bot.events.youtube.YouTubeEvent;
import com.sx4.bot.events.youtube.YouTubeUpdateEvent;
import com.sx4.bot.events.youtube.YouTubeUpdateTitleEvent;
import com.sx4.bot.events.youtube.YouTubeUploadEvent;
import com.sx4.bot.hooks.YouTubeListener;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.utility.ExceptionUtility;

import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;

public class YouTubeManager {
	
	private static final YouTubeManager INSTANCE = new YouTubeManager();
	
	public static YouTubeManager get() {
		return YouTubeManager.INSTANCE;
	}
	
	public static final String DEFAULT_MESSAGE = "**[{channel.name}]({channel.url})** just uploaded a new video!\n{video.url}";
	
	private final List<YouTubeListener> listeners;

	private final Map<String, ScheduledFuture<?>> executors;
	
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	
	private YouTubeManager() {
		this.executors = new HashMap<>();
		this.listeners = new ArrayList<>();
	}
	
	public YouTubeManager addListener(YouTubeListener... listeners) {
		this.listeners.addAll(Arrays.asList(listeners));
		
		return this;
	}
	
	public YouTubeManager removeListener(YouTubeListener... listeners) {
		this.listeners.removeAll(Arrays.asList(listeners));
		
		return this;
	}
	
	public void onYouTube(YouTubeEvent event) {
		for (YouTubeListener listener : this.listeners) {
			listener.onYouTube(event);
			
			if (event instanceof YouTubeUpdateEvent) {
				listener.onYouTubeUpdate((YouTubeUpdateEvent) event);
			}
			
			if (event instanceof YouTubeUpdateTitleEvent) {
				listener.onYouTubeUpdateTitle((YouTubeUpdateTitleEvent) event);
			} 
			
			if (event instanceof YouTubeUploadEvent) {
				listener.onYouTubeUpload((YouTubeUploadEvent) event);
			}
			
			if (event instanceof YouTubeDeleteEvent) {
				listener.onYouTubeDelete((YouTubeDeleteEvent) event);
			}
		}
	}
	
	public ScheduledExecutorService getExecutor() {
		return this.executor;
	}
	
	public boolean hasExecutor(String channelId) {
		return this.executors.containsKey(channelId);
	}
	
	public ScheduledFuture<?> getExecutor(String channelId) {
		return this.executors.get(channelId);
	}
	
	public void putExecutor(String channelId, ScheduledFuture<?> executor) {
		this.executors.put(channelId, executor);
	}
	
	public void deleteExecutor(String channelId) {
		ScheduledFuture<?> executor = this.executors.remove(channelId);
		if (executor != null && !executor.isDone()) {
			executor.cancel(true);
		}
	}
	
	public void putResubscription(String channelId, long seconds) {
		ScheduledFuture<?> executor = this.getExecutor(channelId);
		if (executor != null && !executor.isDone()) {
			executor.cancel(true);
		}
		
		this.putExecutor(channelId, this.executor.schedule(() -> this.resubscribe(channelId), seconds, TimeUnit.SECONDS));
	}
	
	public DeleteOneModel<Document> resubscribeAndGet(String channelId) {
		Config config = Config.get();
		
		long amount = Database.get().countGuilds(Filters.elemMatch("youtubeNotifications", Filters.eq("uploaderId", channelId)));
		
		DeleteOneModel<Document> model = null;
		if (amount != 0) {
			RequestBody body = new MultipartBody.Builder()
				.addFormDataPart("hub.mode", "subscribe")
				.addFormDataPart("hub.topic", "https://www.youtube.com/xml/feeds/videos.xml?channel_id=" + channelId)
				.addFormDataPart("hub.callback", "http://" + config.getDomain() + ":" + config.getPort() + "/api/v1/youtube")
				.addFormDataPart("hub.verify", "sync")
				.addFormDataPart("hub.verify_token", config.getYoutube())
				.setType(MultipartBody.FORM)
				.build();
			
			Request request = new Request.Builder()
				.url("https://pubsubhubbub.appspot.com/subscribe")
				.post(body)
				.build();
			
			Sx4Bot.getClient().newCall(request).enqueue((HttpCallback) response -> {
				if (response.isSuccessful()) {
					System.out.println("Resubscribed to " + channelId + " for YouTube notifications");
				} else {
					System.err.println(String.format("Failed to resubscribe to %s for YouTube notifications, Code: %d, Message: %s", channelId, response.code(), response.body().string()));
				}
				
				response.close();
			});
		} else {
			model = new DeleteOneModel<>(Filters.eq("_id", channelId));
		}
		
		this.deleteExecutor(channelId);
		
		return model;
	}
	
	public void resubscribe(String channelId) {
		DeleteOneModel<Document> model = this.resubscribeAndGet(channelId);
		if (model != null) {
			Database.get().deleteResubscription(model.getFilter()).whenComplete((result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
				}
			});
		}
	}
	
	public void ensureResubscriptions() {
		List<WriteModel<Document>> bulkData = new ArrayList<>();
		
		Database.get().getResubscriptions().find().forEach((Document data) -> {
			String channelId = data.getString("_id");
			
			long timeTill = data.getLong("resubscribeAt") - Clock.systemUTC().instant().getEpochSecond();
			if (timeTill <= 0) { 
				DeleteOneModel<Document> model = this.resubscribeAndGet(channelId);
				if (model != null) {
					bulkData.add(model);
				}
			} else {
				this.putResubscription(channelId, timeTill);
			}
		});
		
		if (!bulkData.isEmpty()) {
			Database.get().bulkWriteResubscriptions(bulkData).whenComplete((result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					ExceptionUtility.sendErrorMessage(exception);
				}
			});
		}
	}
	
}
