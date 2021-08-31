package com.sx4.bot.utility;

import com.sx4.bot.config.Config;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class RequestUtility {

	public static String getWorkerUrl(String url) {
		URI uri = URI.create(url);
		if (uri.getHost().endsWith("discordapp.com") || uri.getHost().endsWith("discordapp.net")) {
			return url;
		}

		return Config.get().getCloudflareWorkerUrl() + "?url=" + URLEncoder.encode(url, StandardCharsets.UTF_8);
	}

}
