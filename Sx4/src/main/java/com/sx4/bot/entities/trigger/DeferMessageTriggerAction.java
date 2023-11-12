package com.sx4.bot.entities.trigger;

import com.sx4.bot.formatter.output.FormatterManager;
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent;
import org.bson.Document;

import java.util.concurrent.CompletableFuture;

public class DeferMessageTriggerAction extends TriggerAction {

	private final GenericComponentInteractionCreateEvent event;

	public DeferMessageTriggerAction(FormatterManager manager, Document data, GenericComponentInteractionCreateEvent event) {
		super(manager, data);

		this.event = event;
	}

	@Override
	public CompletableFuture<Void> execute() {
		return this.event.deferEdit().submit().thenApply($ -> null);
	}

}
