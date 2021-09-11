package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.annotations.argument.DefaultNumber;
import com.sx4.bot.annotations.argument.Uppercase;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.http.HttpCallback;
import okhttp3.Request;
import org.bson.Document;

public class ConvertCommand extends Sx4Command {

	public ConvertCommand() {
		super("convert", 30);

		super.setDescription("Convert one currency to another");
		super.setExamples("convert 5 GBP USD", "convert 10.50 SEK GBP");
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="amount") @DefaultNumber(1) double amount, @Argument(value="currency from") @Uppercase String from, @Argument(value="currency to") @Uppercase String to) {
		if (to.equals(from)) {
			event.replyFormat("**%,.2f** %s \\➡ **%,.2f** %s", amount, from, amount, to).queue();
			return;
		}

		Request request = new Request.Builder()
			.url(String.format("https://free.currconv.com/api/v7/convert?q=%s_%s&apiKey=%s&compact=y", from, to, event.getConfig().getCurrencyConvertor()))
			.build();

		event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
			if (!response.isSuccessful()) {
				event.replyFailure("Failed to convert, try again if this repeats it's likely due to the API being down").queue();
				return;
			}

			Document json = Document.parse(response.body().string());

			Document result = json.get(from + "_" + to, Document.class);
			if (result == null) {
				event.replyFailure("I could not find one or both of those currencies").queue();
				return;
			}

			event.replyFormat("**%,.2f** %s \\➡ **%,.2f** %s", amount, from, amount * result.getDouble("val"), to).queue();
		});
	}

}
