package com.sx4.events;

import static com.rethinkdb.RethinkDB.r;

import java.util.List;
import java.util.Map;

import com.sx4.core.Sx4Bot;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

public class TriggerEvents extends ListenerAdapter {
	
	private String getTriggerText(GuildMessageReceivedEvent event, String text) {
		text = text.replace("{user}", event.getAuthor().getAsTag());
		text = text.replace("{user.name}", event.getAuthor().getName());
		text = text.replace("{user.mention}", event.getAuthor().getAsMention());
		text = text.replace("{channel.name}", event.getChannel().getName());
		text = text.replace("{channel.mention}", event.getChannel().getAsMention());
		
		return text;
	}

	@SuppressWarnings("unchecked")
	public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
		if (event.getMessage().getAuthor().equals(event.getJDA().getSelfUser())) {
			return;
		}
		
		Map<String, Object> data = r.table("triggers").get(event.getGuild().getId()).run(Sx4Bot.getConnection());	
		if (data == null || (boolean) data.get("toggle") == false) {
			return;
		}
		
		boolean isCaseSensitive = (boolean) data.get("case");
		List<Map<String, Object>> triggers = (List<Map<String, Object>>) data.get("triggers");
		for (Map<String, Object> trigger : triggers) {
			String triggerText;
			if (trigger.get("trigger") instanceof byte[]) {
				triggerText = new String((byte[]) trigger.get("trigger"));
			} else {
				triggerText = (String) trigger.get("trigger");
			}
			
			triggerText = isCaseSensitive ? triggerText : triggerText.toLowerCase();
			String messageContent = isCaseSensitive ? event.getMessage().getContentRaw() : event.getMessage().getContentRaw().toLowerCase();

			if (messageContent.equals(triggerText)) {
				String response;
				if (trigger.get("response") instanceof byte[]) {
					response = new String((byte[]) trigger.get("response"));
				} else {
					response = (String) trigger.get("response");
				}
				
				event.getChannel().sendMessage(this.getTriggerText(event, response)).queue();
			}
		}
	}
	
}
