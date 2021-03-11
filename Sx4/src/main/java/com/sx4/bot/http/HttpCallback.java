package com.sx4.bot.http;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

import java.io.IOException;

@FunctionalInterface
public interface HttpCallback extends Callback {
	
	void onResponse(Response response) throws IOException;
	
	default void onFailure(Call call, IOException e) {
		if (!call.isCanceled()) {
			e.printStackTrace();
		}
	}
	
	default void onResponse(Call call, Response response) {
		try {
			this.onResponse(response);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
