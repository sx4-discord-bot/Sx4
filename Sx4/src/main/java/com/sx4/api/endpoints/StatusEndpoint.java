package com.sx4.api.endpoints;

import com.sx4.bot.core.Sx4;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import java.io.IOException;

@Path("api")
public class StatusEndpoint {

	private final Sx4 bot;

	public StatusEndpoint(Sx4 bot) {
		this.bot = bot;
	}

	@POST
	@Path("status")
	public Response getStatus(@QueryParam("token") String token, @QueryParam("messageId") String messageId) throws IOException {
		if (!token.equals(this.bot.getStatusConfig().getStatusWebhookToken())) {
			return Response.status(401).build();
		}

		this.bot.getStatusConfig().set("messageId", messageId).reload();
		this.bot.getConnectionHandler().handleStatusWebhook(messageId);

		return Response.status(204).build();
	}

}
