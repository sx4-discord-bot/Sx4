package com.sx4.bot.entities.mod;

import com.jockie.bot.core.utility.function.TriConsumer;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.utility.RequestUtility;
import com.sx4.bot.utility.StringUtility;
import net.dv8tion.jda.api.entities.sticker.Sticker;
import okhttp3.OkHttpClient;
import okhttp3.Request;

public class StickerArgument {

	private final String url;
	private final String name;

	private StickerArgument(String url, String name) {
		this.url = url;
		this.name = name == null ? null : name.length() < 2 ? name + "a".repeat(2 - name.length()) : StringUtility.limit(name, 30);
	}

	public String getName() {
		return this.name;
	}

	public boolean hasName() {
		return this.name != null;
	}

	public void getBytes(OkHttpClient httpClient, TriConsumer<byte[], String, Integer> bytes) {
		Request request = new Request.Builder()
			.url(RequestUtility.getWorkerUrl(this.url))
			.build();

		httpClient.newCall(request).enqueue((HttpCallback) response -> {
			if (response.code() == 200) {
				String contentType = response.header("Content-Type"), extension = null;
				if (contentType != null && contentType.contains("/")) {
					extension = contentType.split("/")[1].toLowerCase();
				}

				bytes.accept(response.body().bytes(), extension, 200);
				return;
			}

			bytes.accept(null, null, response.code());
		});
	}

	public static StickerArgument fromSticker(Sticker sticker) {
		return new StickerArgument(sticker.getIconUrl(), sticker.getName());
	}

	public static StickerArgument fromUrl(String url) {
		return new StickerArgument(url, StringUtility.getFileName(url));
	}

}
