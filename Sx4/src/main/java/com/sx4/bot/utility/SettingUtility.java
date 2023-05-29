package com.sx4.bot.utility;

import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.settings.HolderType;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import org.bson.Document;

import java.util.function.Function;

public class SettingUtility {

	public static Function<Document, String> getRoleUserDisplay(Sx4CommandEvent event, boolean name) {
		return holder -> {
			long id = holder.getLong("id");
			int type = holder.getInteger("type");
			if (type == HolderType.ROLE.getType()) {
				Role role = event.getGuild().getRoleById(id);
				return role == null ? "Deleted Role (" + id + ")" : (name ? role.getName() : role.getAsMention());
			} else {
				User user = event.getShardManager().getUserById(id);
				return user == null ? "Unknown User (" + id + ")" : user.getAsTag();
			}
		};
	}

}
