package com.sx4.bot.http;

import java.io.IOException;

import com.sx4.bot.utility.ExceptionUtility;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

@FunctionalInterface
public interface HttpCallback extends Callback {
	
	public void onResponse(Response response) throws IOException;
	
	public default void onFailure(Call call, IOException e) {
		if (!call.isCanceled()) {
			e.printStackTrace();
			ExceptionUtility.sendErrorMessage(e);
		}
	}
	
	public default void onResponse(Call call, Response response) throws IOException {
		try {
			this.onResponse(response);
		} catch (Exception e) {
			e.printStackTrace();
			ExceptionUtility.sendErrorMessage(e);
		}
	}
}
