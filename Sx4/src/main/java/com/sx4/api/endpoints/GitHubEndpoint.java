package com.sx4.api.endpoints;

import com.sx4.bot.config.Config;
import com.sx4.bot.utility.HmacUtility;

import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@Path("api")
public class GitHubEndpoint {

	@POST
	@Path("github")
	public Response postGitHub(final String body, @HeaderParam("X-Hub-Signature-256") final String signature, @HeaderParam("X-GitHub-Event") final String event) {
		String hash;
		try {
			hash = "sha256=" + HmacUtility.getSignature(Config.get().getGitHubWebhookSecret(), body, HmacUtility.HMAC_SHA256);
		} catch (InvalidKeyException | NoSuchAlgorithmException e) {
			return Response.status(500).build();
		}

		if (!hash.equals(signature)) {
			return Response.status(401).build();
		}

		return Response.status(204).build();
	}

}
