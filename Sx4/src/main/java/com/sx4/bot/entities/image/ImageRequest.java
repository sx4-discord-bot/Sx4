package com.sx4.bot.entities.image;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.bson.Document;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ImageRequest {

	private final StringBuilder url;
	private final Document fields;
	private final Map<String, String> queries;
	private byte[] image;

	public ImageRequest(String path) {
		this.url = new StringBuilder(path);
		this.fields = new Document();
		this.queries = new HashMap<>();
		this.image = null;
	}

	public ImageRequest setImage(byte[] image) {
		this.image = image;

		return this;
	}

	public ImageRequest addQuery(String query, Object value) {
		if (value == null) {
			return this;
		}

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

	public Request build(String authorization) {
		boolean first = true;
		for (String key : this.queries.keySet()) {
			if (first) {
				this.url.append("?");
				first = false;
			} else {
				this.url.append("&");
			}

			this.url.append(key).append("=").append(this.queries.get(key));
		}

		Request.Builder builder = new Request.Builder()
			.url(this.url.toString());

		if (authorization != null) {
			builder.addHeader("Authorization", authorization);
		}

		if (!this.fields.isEmpty()) {
			builder.post(RequestBody.create(MediaType.parse("application/json"), this.fields.toJson()));
		} else if (this.image != null) {
			builder.post(RequestBody.create(MediaType.parse("image/*"), this.image));
		}

		return builder.build();
	}

}
