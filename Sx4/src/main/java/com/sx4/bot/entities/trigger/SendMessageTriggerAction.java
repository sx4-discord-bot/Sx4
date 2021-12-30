package com.sx4.bot.entities.trigger;

import com.sx4.bot.formatter.FormatterManager;
import com.sx4.bot.utility.TriggerUtility;
import net.dv8tion.jda.api.entities.TextChannel;
import org.bson.Document;

import java.util.concurrent.CompletableFuture;

public class SendMessageTriggerAction extends TriggerAction {

	private final TextChannel channel;

	public SendMessageTriggerAction(FormatterManager manager, Document data, TextChannel channel) {
		super(manager, data);

		this.channel = channel;
	}

	@Override
	public CompletableFuture<Void> execute() {
		return TriggerUtility.sendMessage(this.manager, this.data, this.channel);
	}

}
