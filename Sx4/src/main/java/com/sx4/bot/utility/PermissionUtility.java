package com.sx4.bot.utility;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.AudioChannel;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.internal.utils.PermissionUtil;

import java.util.EnumSet;

public class PermissionUtility {

	public static String formatMissingPermissions(EnumSet<Permission> permissions, String prefix) {
		StringBuilder permissionsString = new StringBuilder();
		
		int i = 0;
		for (Permission permission : permissions) {
			if (i != permissions.size() - 1) {
				permissionsString.append(permission.getName()).append(i != permissions.size() - 2 ? ", " : " ");
			} else {
				permissionsString.append(permissions.size() == 1 ? "" : "and ").append(permission.getName());
			}
			
			i++;
		}
		
		return prefix + " missing the permission" + (permissions.size() == 1 ? " " : "s ") + permissionsString + " to execute this command";
	}

	public static String formatMissingPermissions(EnumSet<Permission> permissions) {
		return PermissionUtility.formatMissingPermissions(permissions, "You are");
	}

	public static boolean canConnect(Member member, AudioChannel channel) {
		EnumSet<Permission> permissions = Permission.getPermissions(PermissionUtil.getEffectivePermission(channel.getPermissionContainer(), member));
		if (permissions.contains(Permission.ADMINISTRATOR)) {
			return true;
		}

		if (!permissions.contains(Permission.VOICE_CONNECT) || !permissions.contains(Permission.VOICE_MOVE_OTHERS)) {
			return false;
		}

		int userLimit = channel instanceof VoiceChannel ? ((VoiceChannel) channel).getUserLimit() : 0;
		if (userLimit > 0) {
			return userLimit >= channel.getMembers().size();
		}

		return true;
	}
	
}
