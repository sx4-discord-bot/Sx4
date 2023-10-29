package com.sx4.bot.entities.trigger;

import com.sx4.bot.formatter.output.FormatterManager;
import com.sx4.bot.formatter.output.JsonFormatter;
import com.sx4.bot.utility.MessageUtility;
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent;
import org.bson.Document;

import java.util.concurrent.CompletableFuture;

public class ReplyMessageTriggerAction extends TriggerAction {

	private final GenericComponentInteractionCreateEvent event;

	public ReplyMessageTriggerAction(FormatterManager manager, Document data, GenericComponentInteractionCreateEvent event) {
		super(manager, data);

		this.event = event;
	}

	@Override
	public CompletableFuture<Void> execute() {
		Document message = new JsonFormatter(this.data.get("response", Document.class), this.manager).parse();

		return this.event.reply(MessageUtility.fromCreateJson(message, true).build()).submit().thenApply($ -> null);
	}

}
