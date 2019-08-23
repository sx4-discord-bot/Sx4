package com.sx4.events;

import java.util.Collections;
import java.util.List;

import org.bson.Document;

import com.mongodb.client.model.Projections;
import com.sx4.database.Database;

import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class TriggerEvents extends ListenerAdapter {
	
	private String getTriggerText(GuildMessageReceivedEvent event, String text) {
		text = text.replace("{user}", event.getAuthor().getAsTag());
		text = text.replace("{user.name}", event.getAuthor().getName());
		text = text.replace("{user.mention}", event.getAuthor().getAsMention());
		text = text.replace("{channel.name}", event.getChannel().getName());
		text = text.replace("{channel.mention}", event.getChannel().getAsMention());
		
		return text;
	}

	public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
		if (event.getMessage().getAuthor().equals(event.getJDA().getSelfUser())) {
			return;
		}
		
		Document data = Database.get().getGuildById(event.getGuild().getIdLong(), null, Projections.include("trigger.enabled", "trigger.triggers", "trigger.case")).get("trigger", Database.EMPTY_DOCUMENT);	
		if (data.isEmpty() || !data.getBoolean("toggle", true)) {
			return;
		}
		
		boolean isCaseSensitive = data.getBoolean("case", true);
		List<Document> triggers = data.getList("triggers", Document.class, Collections.emptyList());
		for (Document triggerData : triggers) {
			String triggerText = triggerData.getString("trigger");
			triggerText = isCaseSensitive ? triggerText : triggerText.toLowerCase();
			String messageContent = isCaseSensitive ? event.getMessage().getContentRaw() : event.getMessage().getContentRaw().toLowerCase();
			if (messageContent.equals(triggerText)) {
				event.getChannel().sendMessage(this.getTriggerText(event, triggerData.getString("response"))).queue();
			}
		}
	}
	
}
