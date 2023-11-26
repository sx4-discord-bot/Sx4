package com.sx4.bot.entities.trigger;

import com.sx4.bot.core.Sx4;
import com.sx4.bot.formatter.output.FormatterManager;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent;
import org.bson.Document;

import java.util.concurrent.CompletableFuture;

public class ProxyTriggerAction extends TriggerAction {

	private final Sx4 bot;
	private final Message message;
	private final GenericComponentInteractionCreateEvent event;

	public ProxyTriggerAction(FormatterManager manager, Document data, Sx4 bot, Message message, GenericComponentInteractionCreateEvent event) {
		super(manager, data);

		this.bot = bot;
		this.message = message == null ? event.getMessage() : message;
		this.event = event;
	}

	@Override
	public CompletableFuture<Void> execute() {
		this.bot.getTriggerHandler().handleTriggerProxy(this.data.getString("trigger"), this.manager, this.message, this.event);

		return CompletableFuture.completedFuture(null);
	}

}
