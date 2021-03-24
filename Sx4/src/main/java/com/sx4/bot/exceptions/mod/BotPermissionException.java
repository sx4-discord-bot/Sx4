package com.sx4.bot.exceptions.mod;

import net.dv8tion.jda.api.Permission;

public class BotPermissionException extends PermissionException {
	
	public BotPermissionException(Permission permission) {
		super(permission);
	}

	public BotPermissionException(Permission permission, String message) {
		super(permission, message);
	}
	
}
