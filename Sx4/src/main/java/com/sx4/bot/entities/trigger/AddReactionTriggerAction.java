package com.sx4.bot.entities.trigger;

import com.sx4.bot.formatter.output.FormatterManager;
import com.sx4.bot.utility.TriggerUtility;
import net.dv8tion.jda.api.entities.Message;
import org.bson.Document;

import java.util.concurrent.CompletableFuture;

public class AddReactionTriggerAction extends TriggerAction {

	private final Message message;

	public AddReactionTriggerAction(FormatterManager manager, Document data, Message message) {
		super(manager, data);

		this.message = message;
	}

	@Override
	public CompletableFuture<Void> execute() {
		return TriggerUtility.addReaction(this.manager, this.data, this.message);
	}

}
