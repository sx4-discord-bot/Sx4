package com.sx4.api.endpoints;

import com.sx4.bot.database.Database;
import org.bson.Document;

import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Random;

@Path("")
public class RedirectEndpoint {

	private final Random random = new Random();

	private static final String ALPHA_NUMERIC = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

	private String getAlphaNumericId(int length) {
		StringBuilder id = new StringBuilder();
		for (int i = 0; i < length; i++) {
			id.append(ALPHA_NUMERIC.charAt(this.random.nextInt(ALPHA_NUMERIC.length())));
		}

		return id.toString();
	}

	@GET
	@Path("{id: [a-zA-Z0-9]{6}}")
	public Response getRedirect(@PathParam("id") final String id) {
		Document redirect = Database.get().getRedirectById(id);
		if (redirect == null) {
			return Response.status(404).build();
		}

		return Response.temporaryRedirect(URI.create(redirect.getString("url"))).build();
	}

	@POST
	@Path("api/shorten")
	@Produces(MediaType.APPLICATION_JSON)
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
			id = this.getAlphaNumericId(6);
			result = Database.get().getRedirectById(id);
		} while (result != null);

		Database.get().insertRedirect(id, url).whenComplete((data, exception) -> {
			if (exception != null) {
				response.resume(exception);
				return;
			}

			response.resume(Response.ok(data.toJson()).build());
		});
	}

}
