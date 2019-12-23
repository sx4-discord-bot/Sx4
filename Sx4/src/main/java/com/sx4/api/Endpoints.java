package com.sx4.api;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.bson.Document;
import org.json.JSONObject;
import org.json.XML;

import com.jockie.bot.core.category.impl.CategoryImpl;
import com.jockie.bot.core.option.IOption;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.sx4.bot.categories.Categories;
import com.sx4.bot.core.Sx4Bot;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.database.Database;
import com.sx4.bot.utils.TokenUtils;
import com.sx4.bot.youtube.YouTubeEvent;
import com.sx4.bot.youtube.YouTubeManager;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.ClientType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.sharding.ShardManager;

@Path("")
public class Endpoints {
	
	private String getParameterType(Parameter parameter) {
		String typeName = parameter.getType().getName();
		
		String[] typeSplit = typeName.split("\\.");
		return typeSplit[typeSplit.length - 1];
	}
	
	private JSONObject getOptionData(IOption<?> option) {
		JSONObject data = new JSONObject();
		data.put("name", option.getName());
		data.put("aliases", option.getAliases());
		data.put("description", option.getDescription());
		
		return data;
	}
	
	private JSONObject getSubCommandData(Sx4Command command) {
		JSONObject data = new JSONObject();
		data.put("name", command.getCommandTrigger());
		data.put("subCommands", command.getSubCommands().stream().map(Sx4Command.class::cast).map(this::getSubCommandData).collect(Collectors.toList()));
		
		return data;
	}
	
	private JSONObject getCommandData(Sx4Command command) {
		JSONObject data = new JSONObject();
		data.put("name", command.getCommandTrigger());
		data.put("aliases", command.getAliases());
		data.put("description", command.getDescription());
		data.put("usage", command.getUsage());
		data.put("userPermissions", Permission.getRaw(command.getAuthorDiscordPermissions()));
		data.put("botPermissions", Permission.getRaw(command.getBotDiscordPermissions()));
		data.put("donator", command.isDonatorCommand());
		data.put("developer", command.isDeveloperCommand());
		data.put("canary", command.isCanaryCommand());
		data.put("options", command.getOptions().stream().map(this::getOptionData).collect(Collectors.toList()));
		data.put("subCommands", command.getSubCommands().stream().map(Sx4Command.class::cast).map(this::getSubCommandData).collect(Collectors.toList()));
		
		return data;
	}
	
	private JSONObject getModuleData(CategoryImpl module) {
		JSONObject data = new JSONObject();
		data.put("name", module.getName());
		data.put("commands", module.getCommands().stream().map(Sx4Command.class::cast).map(this::getCommandData).collect(Collectors.toList()));
		
		return data;
	}
	
	private JSONObject getGuildData(Guild guild) {
		JSONObject data = new JSONObject();
		data.put("id", guild.getIdLong());
		data.put("name", guild.getName());
		data.put("iconUrl", guild.getIconUrl());
		data.put("createdAt", guild.getTimeCreated().toEpochSecond());
		data.put("memberCount", guild.getMembers().size());
		
		return data;
	}
	
	private JSONObject getUserData(User user) {
		JSONObject data = new JSONObject(); 
		data.put("id", user.getIdLong());
		data.put("name", user.getName());
		data.put("discriminator", user.getDiscriminator());
		data.put("avatarUrl", user.getEffectiveAvatarUrl());
		data.put("createdAt", user.getTimeCreated().toEpochSecond());
		
		return data;
	}
	
	private JSONObject getMemberData(Member member) {
		List<JSONObject> activities = new ArrayList<>();
		for (Activity activity : member.getActivities()) {
			JSONObject activityData = new JSONObject();
			
			boolean hasEmote = activity.getEmoji() != null;
			
			if (hasEmote) {
				activityData.put("emote", new JSONObject().put("id", activity.getEmoji().isEmote() ? activity.getEmoji().getIdLong() : null).put("name", activity.getEmoji().getName()));
			}
			
			activityData.put("name", hasEmote && activity.getName().equals("Custom Status") ? null : activity.getName());
			activityData.put("type", activity.getType().getKey());
			
			String url = activity.getUrl();
			if (url != null) {
				activityData.put("url", url);
			}
			
			activities.add(activityData);
		}
		
		JSONObject onlineStatus = new JSONObject();
		onlineStatus.put("desktop", member.getOnlineStatus(ClientType.DESKTOP).getKey());
		onlineStatus.put("mobile", member.getOnlineStatus(ClientType.MOBILE).getKey());
		onlineStatus.put("web", member.getOnlineStatus(ClientType.WEB).getKey());
		
		JSONObject data = new JSONObject();
		data.put("id", member.getIdLong());
		data.put("nickname", member.getNickname());
		data.put("name", member.getUser().getName());
		data.put("discriminator", member.getUser().getDiscriminator());
		data.put("avatarUrl", member.getUser().getEffectiveAvatarUrl());
		data.put("createdAt", member.getUser().getTimeCreated().toEpochSecond());
		data.put("onlineStatus", onlineStatus);
		data.put("joinedAt", member.getTimeJoined().toEpochSecond());
		data.put("activities", activities);
		data.put("permissionsRaw", Permission.getRaw(member.getPermissions()));
		
		return data;
	}
	
	@GET
	@Path("/endpoints")
	@Produces(MediaType.TEXT_PLAIN)
	public Response getEndpoints() {
		StringBuilder stringBuilder = new StringBuilder();
		
		int maxLength = 0;
		List<Integer> maxLengthParameters = new ArrayList<>();
		for (Method method : Endpoints.class.getDeclaredMethods()) {	
		    if (method.isAnnotationPresent(Path.class)) {
		    	maxLength = Math.max(maxLength, ("/api" + method.getAnnotation(Path.class).value() + "/").length());
		    	
		    	Parameter[] parameters = method.getParameters();
		    	for (int i = 0; i < parameters.length; i++) {
		    		Parameter parameter = parameters[i];
		    		if (parameter.isAnnotationPresent(QueryParam.class)) {
			    		String query = " " + this.getParameterType(parameter) + " " + parameter.getAnnotation(QueryParam.class).value();
			    		
			    		if (maxLengthParameters.size() - 1 <= i) {
			    			maxLengthParameters.add(query.length());
			    		} else {
			    			maxLengthParameters.remove(i);
			    			maxLengthParameters.add(i, Math.max(query.length(), maxLengthParameters.get(i)));
			    		}
		    		}
		    	}
		    }
		}
		
		for (Method method : Endpoints.class.getDeclaredMethods()) {	
			if (method.isAnnotationPresent(GET.class)) {
		    	stringBuilder.append("GET     ");
		    } else if (method.isAnnotationPresent(POST.class)) {
		    	stringBuilder.append("POST    ");
		    }
			
		    if (method.isAnnotationPresent(Path.class)) {
		    	stringBuilder.append(String.format("%-" + (maxLength + 5) + "s", "/api/v1" + method.getAnnotation(Path.class).value() + "/"));
		    	
		    	Parameter[] newParameters = method.getParameters();
				for (int i = 0; i < newParameters.length; i++) {
					Parameter parameter = newParameters[i];
					if (parameter.isAnnotationPresent(QueryParam.class)) {
			    		stringBuilder.append(String.format("%-" + (maxLengthParameters.get(i) + 5) + "s", " " + this.getParameterType(parameter) + " " + parameter.getAnnotation(QueryParam.class).value()));
					}
				}
				
				stringBuilder.append("\n");
		    }
		}
		
		String[] splitText = stringBuilder.toString().split("\n");
		Arrays.sort(splitText, (a, b) -> Integer.compare(b.trim().length(), a.trim().length()));
		
		return Response.ok("All endpoints are as listed:\n\n" + String.join("\n", splitText)).build();
	}
	
	@GET
	@Path("/youtube")
	@Produces(MediaType.TEXT_PLAIN)
	public Response getChallenge(@QueryParam("hub.topic") final String topic, @QueryParam("hub.verify_token") final String authorization, @QueryParam("hub.challenge") final String challenge, @QueryParam("hub.lease_seconds") final long seconds) {
		if (authorization != null && authorization.equals(TokenUtils.YOUTUBE)) {
			YouTubeManager manager = Sx4Bot.getYouTubeManager();
			String channelId = topic.substring(topic.lastIndexOf('=') + 1);
			
			Database.get().updateResubscriptionById(channelId, Updates.set("resubscribeAt", Clock.systemUTC().instant().getEpochSecond() + seconds), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
				} else {
					ScheduledFuture<?> resubscription = Sx4Bot.scheduledExectuor.schedule(() -> manager.resubscribe(channelId), seconds, TimeUnit.SECONDS);
					manager.putResubscription(channelId, resubscription);
				}
			});
			
			return Response.ok(challenge).build();
		} else {
			return Response.status(401).build();
		}
	}
	
	@POST
	@Path("/youtube")
	public Response getYoutube(final String body) {
		YouTubeManager manager = Sx4Bot.getYouTubeManager();
		Database database = Database.get();
		
		JSONObject json = XML.toJSONObject(body);
		
		JSONObject feed = json.getJSONObject("feed");
		if (feed.has("at:deleted-entry")) {
			JSONObject entry = feed.getJSONObject("at:deleted-entry");
			JSONObject channel = entry.getJSONObject("at:by");
			
			String videoId = entry.getString("ref").substring(9), videoDeletedAt = entry.getString("when");
			String channelId = channel.getString("uri").substring(32), channelName = channel.getString("name");
			
			YouTubeEvent event = new YouTubeEvent(channelId, channelName, videoId, null, null, null, videoDeletedAt);
			
			manager.onVideoDelete(event);
		} else {
			JSONObject entry = feed.getJSONObject("entry");
			
			String videoTitle = entry.getString("title"), videoId = entry.getString("yt:videoId"), videoUpdatedAt = entry.getString("updated"), videoPublishedAt = entry.getString("published");
			String channelId = entry.getString("yt:channelId"), channelName = entry.getJSONObject("author").getString("name");
			
			YouTubeEvent event = new YouTubeEvent(channelId, channelName, videoId, videoTitle, videoUpdatedAt, videoPublishedAt, null);
			
			Document data = database.getNotification(Filters.eq("videoId", videoId), Projections.include("title", "timestamp"));
			if (data.isEmpty() && Duration.between(event.getVideo().getTimePublishedAt(), ZonedDateTime.now(ZoneOffset.UTC)).toMinutes() <= 60) {
				manager.onVideoUpload(event);
			} else if (data.getString("title").equals(videoTitle)) {
				manager.onVideoDescriptionUpdate(event);
			} else {
				manager.onVideoTitleUpdate(event);
			}
		}
		
		return Response.status(204).build();
	}
	
	@GET
	@Path("/commands")
	@Produces(MediaType.APPLICATION_JSON)
	public Response commands() {
		JSONObject response = new JSONObject();
		
		response.put("status", 200);
		response.put("data", Arrays.stream(Categories.ALL).map(this::getModuleData).collect(Collectors.toList()));
		
		return Response.ok(response.toString()).build();
	}

	@GET
	@Path("/users/{userId}")
	@Produces(MediaType.APPLICATION_JSON)
	public void user(@Suspended final AsyncResponse asyncResponse, @PathParam("userId") final long userId) {
		JSONObject response = new JSONObject();
		
		Sx4Bot.getShardManager().retrieveUserById(userId).queue(user -> {
			response.put("status", 200);
			response.put("data", this.getUserData(user));
			
			asyncResponse.resume(Response.ok(response.toString()).build());
		}, e -> {
			if (e instanceof ErrorResponseException) {
				ErrorResponseException exception = (ErrorResponseException) e;
				if (exception.getErrorResponse().equals(ErrorResponse.UNKNOWN_USER)) {
					response.put("status", 404);
					response.put("message", "Unknown user");
					
					asyncResponse.resume(Response.status(404).entity(response.toString()).build());
					return;
				}
			}
			
			response.put("status", 500);
			response.put("message", e.getMessage());
			
			asyncResponse.resume(Response.status(500).entity(response.toString()).build());
		});
	}
	
	@GET
	@Path("/users/{userId}/guilds")
	@Produces(MediaType.APPLICATION_JSON)
	public Response user(@PathParam("userId") final long userId) {
		JSONObject response = new JSONObject();
		
		ShardManager shardManager = Sx4Bot.getShardManager();
		
		User user = shardManager.getUserById(userId);
		List<Guild> mutualGuilds = user == null ? Collections.emptyList() : shardManager.getMutualGuilds(user);
		
		List<JSONObject> guilds = new ArrayList<>();
		for (Guild mutualGuild : mutualGuilds) {
			guilds.add(this.getGuildData(mutualGuild));
		}
		
		JSONObject data = new JSONObject();
		data.put("guilds", guilds);
		
		response.put("status", 200);
		response.put("data", data);
		
		return Response.ok(response.toString()).build();
	}
	
	@GET
	@Path("/guilds")
	@Produces(MediaType.APPLICATION_JSON)
	public Response guilds() {
		List<JSONObject> guilds = new ArrayList<>();
		for (Guild guild : Sx4Bot.getShardManager().getGuilds()) {
			guilds.add(this.getGuildData(guild));
		}
		
		JSONObject data = new JSONObject();
		data.put("count", guilds.size());
		data.put("guilds", guilds);
		
		JSONObject response = new JSONObject();
		response.put("status", 200);
		response.put("data", data);
		
		return Response.ok(response.toString()).build();
	}
	
	@GET
	@Path("/guilds/{guildId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response guild(@PathParam("guildId") final long guildId) {
		JSONObject response = new JSONObject();
		
		Guild guild = Sx4Bot.getShardManager().getGuildById(guildId);
		if (guild == null) {
			response.put("status", 404);
			response.put("message", "Unknown guild");
			
			return Response.status(404).entity(response.toString()).build();
		} else {
			response.put("status", 200);
			response.put("data", this.getGuildData(guild));
			
			return Response.ok(response.toString()).build();
		}
	}
	
	@GET
	@Path("/guilds/{guildId}/members")
	@Produces(MediaType.APPLICATION_JSON)
	public Response members(@PathParam("guildId") final long guildId) {
		JSONObject response = new JSONObject();
		
		Guild guild = Sx4Bot.getShardManager().getGuildById(guildId);
		if (guild == null) {
			response.put("status", 404);
			response.put("message", "Unknown guild");
			
			return Response.status(404).entity(response.toString()).build();
		} else {
			List<JSONObject> members = new ArrayList<>();
			for (Member member : guild.getMembers()) {
				members.add(this.getMemberData(member));
			}
			
			JSONObject data = new JSONObject();
			data.put("count", members.size());
			data.put("members", members);
			
			response.put("status", 200);
			response.put("data", data);
			
			return Response.ok(response.toString()).build();
		}
	}
	
	@GET
	@Path("/guilds/{guildId}/members/{userId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response member(@PathParam("guildId") final long guildId, @PathParam("userId") final long userId) {
		JSONObject response = new JSONObject();
		
		Guild guild = Sx4Bot.getShardManager().getGuildById(guildId);
		if (guild == null) {
			response.put("status", 404);
			response.put("message", "Unknown guild");
			
			return Response.status(404).entity(response.toString()).build();
		} else {
			Member member = guild.getMemberById(userId);
			if (member == null) {
				response.put("status", 404);
				response.put("message", "Unknown member");
				
				return Response.status(404).entity(response.toString()).build();
			} else {
				response.put("status", 200);
				response.put("data", this.getMemberData(member));
	
				return Response.ok(response.toString()).build();
			}
		}
	}
	
}
