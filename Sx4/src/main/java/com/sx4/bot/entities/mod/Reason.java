package com.sx4.bot.entities.mod;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.sx4.bot.database.Database;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

public class Reason {

	private final String unparsed;
	private final String parsed;
	
	public Reason(long guildId, String reason) {
		this.unparsed = reason;
		this.parsed = this.parse(guildId, reason);
	}
	
	public Reason(String reason) {
		this.unparsed = null;
		this.parsed = reason;
	}
	
	public String getUnparsed() {
		return this.unparsed;
	}
	
	public String getParsed() {
		return this.parsed;
	}

	private String parse(long guildId, String reason) {
		List<Document> templates = Database.get().getTemplates(Filters.eq("guildId", guildId), Projections.include("template", "reason")).into(new ArrayList<>());
		
		int index = 0;
		while ((index = reason.indexOf(':', index + 1)) != -1) {
			int prefixIndex = index;
			StringBuilder prefix = new StringBuilder();
			while (!prefix.toString().equalsIgnoreCase("t") && !prefix.toString().equalsIgnoreCase("template") && prefixIndex > 0) {
				prefix.insert(0, reason.charAt(--prefixIndex));
			}
			
			if (prefix.toString().equalsIgnoreCase("t") || prefix.toString().equalsIgnoreCase("template")) {
				StringBuilder template = new StringBuilder();
				
				if (reason.charAt(index + 1) == '"' && reason.indexOf('"', index + 2) != -1) {
					char character;
					while ((character = reason.charAt(++index + 1)) != '"') {
						template.append(character);
					}
					
					index += 2;
				} else {
					char character;
					while (index != reason.length() - 1 && (character = reason.charAt(++index)) != ' ') {
						template.append(character);
					}
					
					if (index == reason.length() - 1) {
						index++;
					}
				}
				
				for (Document templateData : templates) {
					if (templateData.getString("template").equalsIgnoreCase(template.toString())) {
						reason = reason.substring(0, prefixIndex) + templateData.getString("reason") + reason.substring(index);
					}
				}
			}
		}
		
		return reason;
	}
	
}
