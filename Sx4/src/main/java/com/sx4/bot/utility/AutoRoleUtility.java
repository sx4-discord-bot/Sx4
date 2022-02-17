package com.sx4.bot.utility;

import net.dv8tion.jda.api.entities.Member;
import org.bson.Document;

import java.util.List;

public class AutoRoleUtility {

	public static boolean filtersMatch(Member member, List<Document> filters) {
		for (Document filter : filters) {
			int type = filter.getInteger("type");
			Object value = filter.get("value");

			if (type == 0) {
				if (member.getUser().isBot() != (boolean) value) {
					return false;
				}
			}
		}

		return true;
	}

}
