package com.sx4.bot.entities.trigger;

import com.sx4.bot.formatter.output.FormatterManager;
import org.bson.Document;

import java.util.concurrent.CompletableFuture;

public abstract class TriggerAction {

	protected final FormatterManager manager;
	protected final Document data;

	public TriggerAction(FormatterManager manager, Document data) {
		this.manager = manager;
		this.data = data;
	}

	public abstract CompletableFuture<Void> execute();

}
