package com.sx4.bot.entities.trigger;

import com.sx4.bot.core.Sx4;
import com.sx4.bot.formatter.output.FormatterManager;
import com.sx4.bot.utility.TriggerUtility;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import org.bson.Document;

import java.util.concurrent.CompletableFuture;

public class SendPagedMessageTriggerAction extends TriggerAction {

	private final GuildMessageChannel channel;
	private final User owner;
	private final Sx4 bot;

	public SendPagedMessageTriggerAction(Sx4 bot, FormatterManager manager, Document data, GuildMessageChannel channel, User owner) {
		super(manager, data);

		this.channel = channel;
		this.owner = owner;
		this.bot = bot;
	}

	@Override
	public CompletableFuture<Void> execute() {
		return TriggerUtility.sendPagedMessage(this.bot, this.manager, this.data, this.channel, this.owner);
	}

}
