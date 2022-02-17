package com.sx4.bot.exceptions.mod;

import net.dv8tion.jda.api.Permission;

public class PermissionException extends ModException {

	private final Permission permission;

	public PermissionException(Permission permission) {
		this(permission, "I do not have the `" + permission.getName() + "` permission");
	}

	public PermissionException(Permission permission, String message) {
		super(message);

		this.permission = permission;
	}

	public Permission getPermission() {
		return this.permission;
	}
}
