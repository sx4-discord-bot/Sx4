package com.sx4.bot.entities.trigger;

import com.sx4.bot.core.Sx4CommandListener;
import com.sx4.bot.formatter.output.FormatterManager;
import com.sx4.bot.utility.TriggerUtility;
import net.dv8tion.jda.api.entities.Message;
import org.bson.Document;

import java.util.concurrent.CompletableFuture;

public class ExecuteCommandTriggerAction extends TriggerAction {

	private final Sx4CommandListener listener;
	private final Message message;

	public ExecuteCommandTriggerAction(FormatterManager manager, Document data, Sx4CommandListener listener, Message message) {
		super(manager, data);

		this.listener = listener;
		this.message = message;
	}


	@Override
	public CompletableFuture<Void> execute() {
		return TriggerUtility.executeCommand(this.manager, this.data, this.listener, this.message);
	}
}
