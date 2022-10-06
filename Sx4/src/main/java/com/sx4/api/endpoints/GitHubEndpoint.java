package com.sx4.api.endpoints;

import com.sx4.bot.core.Sx4;
import com.sx4.bot.utility.HmacUtility;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@Path("api")
public class GitHubEndpoint {

	private final Sx4 bot;

	public GitHubEndpoint(Sx4 bot) {
		this.bot = bot;
	}

	@POST
	@Path("github")
	public Response postGitHub(final String body, @HeaderParam("X-Hub-Signature-256") final String signature, @HeaderParam("X-GitHub-Event") final String event) {
		String hash;
		try {
			hash = "sha256=" + HmacUtility.getSignatureHex(this.bot.getConfig().getGitHubWebhookSecret(), body, HmacUtility.HMAC_SHA256);
		} catch (InvalidKeyException | NoSuchAlgorithmException e) {
			return Response.status(500).build();
		}

		if (!hash.equals(signature)) {
			return Response.status(401).build();
		}

		return Response.status(204).build();
	}

}
