package com.sx4.utils;

import static com.rethinkdb.RethinkDB.r;

import com.rethinkdb.gen.ast.Insert;

import net.dv8tion.jda.core.entities.Guild;

public class AntiInviteUtils {

	public static Insert insertData(Guild guild) {
		return r.table("antiad").insert(r.hashMap("id", guild.getId())
				.with("whitelist", r.hashMap("channels", new Object[0]).with("roles", new Object[0]).with("users", new Object[0]))
				.with("toggle", false)
				.with("baninvites", false)
				.with("action", null)
				.with("attempts", 3)
				.with("users", new Object[0]));
	}
	
}
