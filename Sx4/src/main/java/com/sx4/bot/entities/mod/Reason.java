package com.sx4.bot.entities.mod;

import org.bson.Document;

import com.mongodb.client.model.Projections;
import java.util.List;
import com.sx4.bot.database.Database;

public class Reason {

	private final String unparsed;
	private final String parsed;
	
	public Reason(long guildId, String reason) {
		this.unparsed = reason;
		this.parsed = this.parse(guildId, reason);
		
		System.out.println(this.unparsed + " - " + this.parsed);
	}
	
	public Reason(String reason) {
		this.unparsed = reason;
		this.parsed = reason;
	}
	
	public String getUnparsed() {
		return this.unparsed;
	}
	
	public String getParsed() {
		return this.parsed;
	}
	
	@SuppressWarnings("unchecked")
	private String parse(long guildId, String reason) {
		if (reason == null) {
			return null;
		}
		
		List<Document> templates = Database.get().getGuildById(guildId, Projections.include("template")).getEmbedded(List.of("template", "templates"), List.class);
		
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
