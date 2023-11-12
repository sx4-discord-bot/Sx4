package com.sx4.bot.utility;

import com.sx4.bot.config.Config;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class RequestUtility {

	public static String getWorkerUrl(String url) {
		Config config = Config.get();

		URI uri = URI.create(url);
		for (String domain : config.getCloudflareWhitelistedDomains()) {
			if (uri.getHost().endsWith(domain)) {
				return url;
			}
		}

		return config.getCloudflareWorkerUrl() + "?url=" + URLEncoder.encode(url, StandardCharsets.UTF_8);
	}

}
