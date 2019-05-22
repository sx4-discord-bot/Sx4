package com.sx4.utils;

import static com.rethinkdb.RethinkDB.r;

import java.util.List;

import com.rethinkdb.model.OptArgs;
import com.rethinkdb.net.Connection;
import com.sx4.core.Sx4Bot;

public class DatabaseUtils {

	public static void ensureTables(String... tables) {
		List<String> currentTables = r.tableList().run(Sx4Bot.getConnection());
		for (String tableString : tables) {
			if (!currentTables.contains(tableString)) {
				r.tableCreate(tableString).run(Sx4Bot.getConnection(), OptArgs.of("durability", "soft"));
				System.out.println("Table " + tableString + " has been created");
			}
		}
	}
	
	public static void ensureTableData() {
		Connection connection = Sx4Bot.getConnection();
		r.table("blacklist").insert(r.hashMap("id", "owner").with("users", new Object[0])).run(connection, OptArgs.of("durability", "soft"));
		r.table("tax").insert(r.hashMap("id", "tax").with("tax", 0)).run(connection, OptArgs.of("durability", "soft"));
		r.table("botstats").insert(r.hashMap("id", "stats")
				.with("servercountbefore", Sx4Bot.getShardManager().getGuilds().size())
				.with("commandcounter", new Object[0])
				.with("commands", 0)
				.with("messages", 0)
				.with("users", new Object[0])).run(connection, OptArgs.of("durability", "soft"));
	}
	
}
