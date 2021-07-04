package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.utility.ColourUtility;
import com.sx4.bot.utility.NumberUtility;
import com.sx4.bot.utility.TimeUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

import java.util.stream.Collectors;

public class RoleInfoCommand extends Sx4Command {

	public RoleInfoCommand() {
		super("role info", 329);

		super.setDescription("Get basic information on a role");
		super.setAliases("roleinfo", "ri", "rinfo");
		super.setExamples("role info @Role", "role info Role", "role info 330400064541425664");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="role", endless=true) Role role) {
		Role.RoleTags tags = role.getTags();

		String type = "Normal Role";
		if (tags.isBoost()) {
			type = "Booster Role";
		} else if (tags.isBot()) {
			Member bot = event.getGuild().getMemberById(tags.getBotIdLong());

			type = "Bot Role - " + (bot == null ? "Anonymous#0000" : bot.getUser().getAsTag());
		} else if (tags.isIntegration()) {
			type = "Integration Role";
		}

		EmbedBuilder embed = new EmbedBuilder()
			.setAuthor(role.getName(), null, event.getGuild().getIconUrl())
			.setColor(role.getColorRaw())
			.setThumbnail(event.getGuild().getIconUrl())
			.setDescription(role.getAsMention() + " was created on " + role.getTimeCreated().format(TimeUtility.DEFAULT_FORMATTER))
			.addField("Role Colour", String.format("Hex: #%s\nRGB: %s", ColourUtility.toHexString(role.getColorRaw()), ColourUtility.toRGBString(role.getColorRaw())), true)
			.addField("Role Position", String.format("%s (Bottom to Top)\n%s (Top to Bottom)", NumberUtility.getSuffixed(role.getPosition() + 2), NumberUtility.getSuffixed(event.getGuild().getRoles().size() - 1 - role.getPosition())), true)
			.addField("Users in Role", String.valueOf(role.isPublicRole() ? event.getGuild().getMemberCount() : event.getGuild().getMembersWithRoles(role).size()), true)
			.addField("Role Type", type, true)
			.addField("Hoisted Role", role.isHoisted() ? "Yes" : "No", true)
			.addField("Mentionable Role", role.isMentionable() ? "Yes" : "No", true)
			.addField("Managed Role", role.isManaged() ? "Yes" : "No", true)
			.addField("Role ID", role.getId(), true)
			.addField("Role Permissions", role.getPermissions().isEmpty() ? "None" : role.getPermissions().stream().map(Permission::getName).collect(Collectors.joining("\n")), false);

		event.reply(embed.build()).queue();
	}

}
