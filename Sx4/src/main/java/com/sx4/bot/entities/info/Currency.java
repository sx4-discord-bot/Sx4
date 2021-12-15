package com.sx4.bot.entities.info;

import com.sx4.bot.http.HttpCallback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Currency extends Number {

	private static final Map<String, Double> CURRENCIES = new HashMap<>();
	public static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor();

	private final double amount;
	private final String currency;

	public Currency(double amount, String currency) {
		this.amount = amount;
		this.currency = currency.toUpperCase();
	}

	public Currency(double amount) {
		this(amount, "EUR");
	}

	public double convert(String currency) {
		double rate = Currency.getCurrencyRate(currency.toUpperCase());
		double currentRate = Currency.getCurrencyRate(this.currency);

		return rate == -1D || currentRate == -1D ? -1D : (1D / currentRate) * this.amount * rate;
	}

	@Override
	public String toString() {
		return Double.toString(this.amount);
	}

	@Override
	public int intValue() {
		return (int) this.amount;
	}

	@Override
	public long longValue() {
		return (long) this.amount;
	}

	@Override
	public float floatValue() {
		return (float) this.amount;
	}

	@Override
	public double doubleValue() {
		return this.amount;
	}

	public static double getCurrencyRate(String currency) {
		return Currency.CURRENCIES.getOrDefault(currency, -1D);
	}

	public static void pollCurrencies(OkHttpClient client) {
		Currency.EXECUTOR.scheduleAtFixedRate(() -> {
			Request request = new Request.Builder()
				.url("https://api.exchangerate.host/latest?base=EUR")
				.build();

			client.newCall(request).enqueue((HttpCallback) response -> {
				JSONObject json = new JSONObject(response.body().string());

				JSONObject rates = json.getJSONObject("rates");
				for (String currency : rates.keySet()) {
					Currency.CURRENCIES.put(currency, rates.getDouble(currency));
				}
			});
		}, 0, 1, TimeUnit.HOURS);
	}

}
