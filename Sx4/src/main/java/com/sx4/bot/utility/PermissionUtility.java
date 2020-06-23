package com.sx4.bot.utility;

import java.util.EnumSet;

import com.sx4.bot.config.Config;

import net.dv8tion.jda.api.Permission;

public class PermissionUtility {

	public static String formatMissingPermissions(EnumSet<Permission> permissions) {
		StringBuilder permissionsString = new StringBuilder();
		
		int i = 0;
		for (Permission permission : permissions) {
			if (i != permissions.size() - 1) {
				permissionsString.append(permission.getName() + (i != permissions.size() - 2 ? ", " : " "));
			} else {
				permissionsString.append((permissions.size() == 1 ? "" : "and ") + permission.getName());
			}
			
			i++;
		}
		
		return "You are missing the permission" + (permissions.size() == 1 ? " " : "s ") + permissionsString.toString() + " to execute this command " + Config.get().getFailureEmote();
	}
	
}
