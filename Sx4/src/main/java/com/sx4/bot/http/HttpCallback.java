package com.sx4.bot.http;

import com.sx4.bot.utility.ExceptionUtility;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

@FunctionalInterface
public interface HttpCallback extends Callback {
	
	void onResponse(Response response) throws IOException;
	
	default void onFailure(Call call, @NotNull IOException e) {
		if (!call.isCanceled()) {
			ExceptionUtility.sendErrorMessage(e);
		}
	}
	
	default void onResponse(@NotNull Call call, @NotNull Response response) {
		try {
			this.onResponse(response);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
