package com.sx4.bot.entities.trigger;

import com.sx4.bot.formatter.output.FormatterManager;
import com.sx4.bot.utility.TriggerUtility;
import org.bson.Document;

import java.util.concurrent.CompletableFuture;

public class RequestTriggerAction extends TriggerAction {

	public RequestTriggerAction(FormatterManager manager, Document data) {
		super(manager, data);
	}

	@Override
	public CompletableFuture<Void> execute() {
		return TriggerUtility.executeRequest(this.manager, this.data);
	}

}
