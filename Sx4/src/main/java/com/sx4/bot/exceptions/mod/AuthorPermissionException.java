package com.sx4.bot.exceptions.mod;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.exceptions.PermissionException;

public class AuthorPermissionException extends PermissionException {

	private static final long serialVersionUID = 1L;
	
	public AuthorPermissionException(Permission permission) {
		this(permission, "You do not have the `" + permission.getName() + "` permission");
	}

	public AuthorPermissionException(Permission permission, String reason) {
		super(permission, reason);
	}
	
}
