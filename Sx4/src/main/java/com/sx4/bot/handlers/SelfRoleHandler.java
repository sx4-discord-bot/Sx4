package com.sx4.bot.handlers;

import com.mongodb.client.model.Filters;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.Database;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.hooks.EventListener;

public class SelfRoleHandler implements EventListener {

	private final Sx4 bot;

	public SelfRoleHandler(Sx4 bot) {
		this.bot = bot;
	}

	public void onRoleDelete(RoleDeleteEvent event) {
		this.bot.getDatabase().deleteSelfRole(Filters.eq("roleId", event.getRole().getIdLong())).whenComplete(Database.exceptionally(this.bot.getShardManager()));
	}

	@Override
	public void onEvent(GenericEvent event) {
		if (event instanceof RoleDeleteEvent) {
			this.onRoleDelete((RoleDeleteEvent) event);
		}
	}

}
