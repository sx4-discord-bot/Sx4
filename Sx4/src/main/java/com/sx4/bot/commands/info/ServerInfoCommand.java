package com.sx4.bot.commands.info;

import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.utility.StringUtility;
import com.sx4.bot.utility.TimeUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

public class ServerInfoCommand extends Sx4Command {

	public ServerInfoCommand() {
		super("server info", 303);

		super.setDescription("Provides some basic information about the current server");
		super.setExamples("server info");
		super.setAliases("serverinfo", "si", "sinfo");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event) {
		Guild guild = event.getGuild();

		long totalCount = guild.getMemberCount();
		long botCount = guild.getMemberCache().applyStream(stream -> stream.filter(member -> member.getUser().isBot()).count());
		long memberCount = totalCount - botCount;

		Member owner = guild.getOwner();

		EmbedBuilder embed = new EmbedBuilder()
			.setAuthor(guild.getName(), null, guild.getIconUrl())
			.setThumbnail(guild.getIconUrl())
			.setDescription(guild.getName() + " was created on " + guild.getTimeCreated().format(TimeUtility.DEFAULT_FORMATTER))
			.addField("Language", guild.getLocale().getDisplayLanguage(), true)
			.addField("Total Users", totalCount + " user" + (totalCount == 1 ? "" : "s"), true)
			.addField("Users", memberCount + " user" + (memberCount == 1 ? "" : "s"), true)
			.addField("Bots", botCount + " bot" + (botCount == 1 ? "" : "s"), true)
			.addField("Boosts", guild.getBoostCount() + " booster" + (guild.getBoostCount() == 1 ? "" : "s") + " (Tier " + guild.getBoostTier().getKey() + ")", true)
			.addField("Text Channels", String.valueOf(guild.getTextChannels().size()), true)
			.addField("Voice Channels", String.valueOf(guild.getVoiceChannels().size()), true)
			.addField("Categories", String.valueOf(guild.getCategories().size()), true)
			.addField("Verification Level", StringUtility.title(guild.getVerificationLevel().name()), true)
			.addField("AFK Timeout", TimeUtility.LONG_TIME_FORMATTER.parse(guild.getAfkTimeout().getSeconds()), true)
			.addField("AFK Channel", guild.getAfkChannel() == null ? "None" : guild.getAfkChannel().getName(), true)
			.addField("Explicit Content Filter", guild.getExplicitContentLevel().getDescription(), true)
			.addField("Roles", String.valueOf(guild.getRoles().size()), true)
			.addField("Owner", owner == null ? "Anonymous#0000" : owner.getUser().getAsTag(), true)
			.addField("Server ID", guild.getId(), true);

		event.reply(embed.build()).queue();
	}

}
