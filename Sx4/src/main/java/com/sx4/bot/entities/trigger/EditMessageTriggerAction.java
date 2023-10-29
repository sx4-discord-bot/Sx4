package com.sx4.bot.entities.trigger;

import com.sx4.bot.formatter.output.FormatterManager;
import com.sx4.bot.formatter.output.JsonFormatter;
import com.sx4.bot.utility.MessageUtility;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class EditMessageTriggerAction extends TriggerAction {

	private final GenericComponentInteractionCreateEvent event;

	public EditMessageTriggerAction(FormatterManager manager, Document data, GenericComponentInteractionCreateEvent event) {
		super(manager, data);

		this.event = event;
	}

	@Override
	public CompletableFuture<Void> execute() {
		Document message = new JsonFormatter(this.data.get("data", Document.class), this.manager).parse();
		boolean replace = this.data.getBoolean("replace", false);
		boolean combineEmbeds = this.data.getBoolean("combineEmbeds", false);

		MessageEditBuilder builder = MessageUtility.fromEditJson(message, true);
		builder.setReplace(replace);

		if (combineEmbeds) {
			List<MessageEmbed> embeds = this.event.getMessage().getEmbeds();
			List<MessageEmbed> builderEmbeds = builder.getEmbeds();

			List<MessageEmbed> newEmbeds = new ArrayList<>();
			for (int i = 0; i < Math.min(embeds.size(), builderEmbeds.size()); i++) {
				MessageEmbed embed = MessageUtility.combineEmbeds(embeds.get(i), builderEmbeds.get(i));
				newEmbeds.add(embed);
			}

			builder.setEmbeds(newEmbeds);
		}

		return this.event.editMessage(builder.build()).submit().thenApply($ -> null);
	}

}
