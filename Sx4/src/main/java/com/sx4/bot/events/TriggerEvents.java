package com.sx4.bot.events;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import org.bson.Document;

import com.mongodb.client.model.Projections;
import com.sx4.bot.database.Database;

import net.dv8tion.jda.api.entities.Message.MentionType;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class TriggerEvents extends ListenerAdapter {
	
	private String getTriggerText(GuildMessageReceivedEvent event, String text) {
		int index = -1;
		while ((index = text.indexOf('{', index + 1)) != -1) {
		    if (index > 0 && text.charAt(index - 1) == '\\') {
		        text = text.substring(0, index - 1) + text.substring(index);
		        continue;
		    }

		    int endIndex = text.indexOf('}', index + 1);
		    if (endIndex != -1)  {
		        if (text.charAt(endIndex - 1) == '\\') {
		            text = text.substring(0, endIndex - 1) + text.substring(endIndex);
		            continue;
		        } else {
		            String formatter = text.substring(index + 1, endIndex);
		            String placeHolder = text.substring(0, index) + "%s" + text.substring(endIndex + 1);
		            
		            switch (formatter.trim().toLowerCase()) {
		            	case "user":
		            		text = String.format(placeHolder, event.getAuthor().getAsTag());
		            		break;
		            	case "user.mention":
		            		text = String.format(placeHolder, event.getAuthor().getAsMention());
		            		break;
		            	case "user.name":
		            		text = String.format(placeHolder, event.getAuthor().getName());
		            		break;
		            	case "channel.name":
		            		text = String.format(placeHolder, event.getChannel().getName());
		            		break;
		            	case "channel.mention":
		            		text = String.format(placeHolder, event.getChannel().getAsMention());
		            		break;
		            }
		        }
		    }
		}
		
		return text;
	}

	public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
		if (event.getMessage().getAuthor().equals(event.getJDA().getSelfUser())) {
			return;
		}
		
		Document data = Database.get().getGuildById(event.getGuild().getIdLong(), null, Projections.include("trigger.enabled", "trigger.triggers", "trigger.case")).get("trigger", Database.EMPTY_DOCUMENT);	
		if (data.isEmpty() || !data.getBoolean("enabled", true)) {
			return;
		}
		
		boolean isCaseSensitive = data.getBoolean("case", true);
		List<Document> triggers = data.getList("triggers", Document.class, Collections.emptyList());
		for (Document triggerData : triggers) {
			String triggerText = triggerData.getString("trigger");
			triggerText = isCaseSensitive ? triggerText : triggerText.toLowerCase();
			String messageContent = isCaseSensitive ? event.getMessage().getContentRaw() : event.getMessage().getContentRaw().toLowerCase();
			if (messageContent.equals(triggerText)) {
				event.getChannel().sendMessage(this.getTriggerText(event, triggerData.getString("response"))).allowedMentions(EnumSet.of(MentionType.USER, MentionType.ROLE)).queue();
			}
		}
	}
	
}
