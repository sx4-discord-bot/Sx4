package com.sx4.api;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.json.JSONObject;

import com.sx4.bot.core.Sx4Bot;

import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;

@Path("")
public class Endpoints {

	@GET
	@Path("/users/{userId}")
	@Produces(MediaType.APPLICATION_JSON)
	public void users(@Suspended final AsyncResponse asyncResponse, @PathParam("userId") final long userId) {
		JSONObject response = new JSONObject();
		
		Sx4Bot.getShardManager().retrieveUserById(userId).queue(user -> {
			JSONObject userObject = new JSONObject(); 
			userObject.put("id", user.getIdLong());
			userObject.put("name", user.getName());
			userObject.put("discriminator", user.getDiscriminator());
			userObject.put("avatar", user.getEffectiveAvatarUrl());
			
			response.put("status", 200);
			response.put("user", userObject);
			
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
	
}
