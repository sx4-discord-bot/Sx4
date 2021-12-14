package com.sx4.bot.managers;

import com.sx4.bot.core.Sx4;
import com.sx4.bot.http.HttpCallback;
import okhttp3.Request;
import org.bson.Document;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SkinPortManager {

	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

	private final Sx4 bot;

	private String csrf;
	private String cookie;

	private String currency;
	private Document currencies;

	public SkinPortManager(Sx4 bot) {
		this.bot = bot;

		this.executor.scheduleAtFixedRate(this::retrieveCSRFData, 0, 3, TimeUnit.HOURS);
	}

	public String getCSRFToken() {
		return this.csrf;
	}

	public String getCSRFCookie() {
		return this.cookie;
	}

	public String getCurrentCurrency() {
		return this.currency;
	}

	public double getCurrencyRate(String currency) {
		Number amount = this.currencies.get(currency, Number.class);
		if (amount == null) {
			return -1D;
		}

		return amount.doubleValue();
	}

	public void retrieveCSRFData() {
		Request request = new Request.Builder()
			.url("https://skinport.com/api/data")
			.build();

		this.bot.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
			List<String> cookies = response.headers("Set-Cookie");
			String cookie = cookies.get(0);

			this.cookie = cookie.substring(0, cookie.indexOf(';'));

			Document data = Document.parse(response.body().string());
			this.csrf = data.getString("csrf");

			this.currency = data.getString("currency");
			this.currencies = data.get("rates", Document.class);
		});
	}

}
