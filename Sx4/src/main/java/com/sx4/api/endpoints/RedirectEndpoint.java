package com.sx4.api.endpoints;

import com.sx4.bot.core.Sx4;
import com.sx4.bot.utility.RandomString;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.bson.Document;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

@Path("")
public class RedirectEndpoint {

	private final Sx4 bot;
	private final RandomString random;

	public RedirectEndpoint(Sx4 bot) {
		this.bot = bot;
		this.random = new RandomString();
	}

	@POST
	@Path("api/shorten")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public void postRedirect(final String body, @Suspended final AsyncResponse response) {
		Document json = Document.parse(body);

		String url = json.getString("url");
		if (url == null) {
			response.resume(Response.status(400).build());
			return;
		}

		try {
			new URL(url);
		} catch (MalformedURLException e) {
			response.resume(Response.status(400).build());
			return;
		}

		Document result;
		String id;
		do {
			id = this.random.nextString(7);
			result = this.bot.getMongoMain().getRedirectById(id);
		} while (result != null);

		this.bot.getMongoMain().insertRedirect(id, url).whenComplete((data, exception) -> {
			if (exception != null) {
				response.resume(exception);
				return;
			}

			response.resume(Response.ok(data.toJson()).build());
		});
	}

	@GET
	@Path("{id: [a-zA-Z0-9]{2,7}}")
	public Response getRedirect(@PathParam("id") final String id) {
		Document redirect = this.bot.getMongoMain().getRedirectById(id);
		if (redirect == null) {
			return Response.status(404).build();
		}

		return Response.status(Response.Status.MOVED_PERMANENTLY).location(URI.create(redirect.getString("url"))).build();
	}

}
