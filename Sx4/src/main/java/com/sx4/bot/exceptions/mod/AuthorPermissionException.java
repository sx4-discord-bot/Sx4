package com.sx4.bot.exceptions.mod;

import net.dv8tion.jda.api.Permission;

public class AuthorPermissionException extends PermissionException {
	
	public AuthorPermissionException(Permission permission) {
		super(permission, "You do not have the `" + permission.getName() + "` permission");
	}

	public AuthorPermissionException(Permission permission, String message) {
		super(permission, message);
	}
	
}
