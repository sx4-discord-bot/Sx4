package com.sx4.bot.exceptions.mod;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.exceptions.PermissionException;

public class BotPermissionException extends PermissionException {

	private static final long serialVersionUID = 1L;
	
	public BotPermissionException(Permission permission) {
		this(permission, "I do not have the `" + permission.getName() + "` permission");
	}

	public BotPermissionException(Permission permission, String reason) {
		super(permission, reason);
	}
	
}
