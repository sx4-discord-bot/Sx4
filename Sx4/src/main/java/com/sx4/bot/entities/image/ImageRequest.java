package com.sx4.bot.entities.image;

import com.sx4.bot.config.Config;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.bson.Document;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ImageRequest {

	private final Config config = Config.get();

	private final StringBuilder url;
	private final Document fields;
	private final Map<String, String> queries;
	private byte[] image;

	public ImageRequest(String endpoint) {
		this.url = new StringBuilder(this.config.getImageWebserverUrl(endpoint));
		this.fields = new Document();
		this.queries = new HashMap<>();
		this.image = new byte[0];
	}

	public ImageRequest setImage(byte[] image) {
		this.image = image;

		return this;
	}

	public ImageRequest addQuery(String query, Object value) {
		this.queries.put(query, URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8));

		return this;
	}

	public ImageRequest addField(String key, Object value) {
		this.fields.append(key, value);

		return this;
	}

	public ImageRequest addAllFields(Map<String, Object> map) {
		this.fields.putAll(map);

		return this;
	}

	public Request build() {
		boolean first = true;
		for (Map.Entry<String, String> entry : this.queries.entrySet()) {
			this.url.append(String.format("%s%s=%s", first ? "?" : "&", entry.getKey(), entry.getValue()));

			if (first) {
				first = false;
			}
		}

		Request.Builder builder = new Request.Builder()
			.url(this.url.toString())
			.addHeader("Authorization", this.config.getImageWebserver());

		if (!this.fields.isEmpty()) {
			builder.post(RequestBody.create(MediaType.parse("application/json"), this.fields.toJson()));
		} else if (this.image.length != 0) {
			builder.post(RequestBody.create(MediaType.parse("image"), this.image));
		}

		return builder.build();
	}

}
