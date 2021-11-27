package com.sx4.bot.managers;

import com.sx4.bot.core.Sx4;
import com.sx4.bot.http.HttpCallback;
import okhttp3.Request;
import org.bson.Document;

import java.util.List;

public class SkinPortManager {

	private final Sx4 bot;

	private String csrf;
	private String cookie;

	public SkinPortManager(Sx4 bot) {
		this.bot = bot;

		this.retrieveCSRFToken();
	}

	public String getCSRFToken() {
		return this.csrf;
	}

	public String getCSRFCookie() {
		return this.cookie;
	}

	public void retrieveCSRFToken() {
		Request request = new Request.Builder()
			.url("https://skinport.com/api/data")
			.build();

		this.bot.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
			List<String> cookies = response.headers().values("Set-Cookie");
			String cookie = cookies.get(0);

			this.cookie = cookie.substring(0, cookie.indexOf(';'));

			Document data = Document.parse(response.body().string());
			this.csrf = data.getString("csrf");
		});
	}

}
