package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.annotations.argument.Global;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Guild;

public class EmoteInfoCommand extends Sx4Command {

	public EmoteInfoCommand() {
		super("emote info", 262);

		super.setAliases("emote", "emoteinfo", "einfo");
		super.setDescription("Get information about an emote");
		super.setExamples("emote info <:emote:720020237952483490>", "emote info 720020237952483490", "emote info https://cdn.discordapp.com/emojis/720020237952483490.png");
		super.setCategoryAll(ModuleCategory.INFORMATION);
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="emote") @Global Emote emote) {
		Guild guild = emote.getGuild();

		EmbedBuilder embed = new EmbedBuilder()
			.setAuthor(emote.getName(), emote.getImageUrl(), emote.getImageUrl())
			.setThumbnail(emote.getImageUrl())
			.setTimestamp(emote.getTimeCreated())
			.setFooter("Created", null)
			.addField("ID", emote.getId(), false)
			.addField("Server", guild.getName() + " (" + guild.getId() + ")", false);

		if (guild.getSelfMember().hasPermission(Permission.MANAGE_EMOTES_AND_STICKERS)) {
			guild.retrieveEmote(emote).queue(e -> {
				if (e.hasUser()) {
					embed.addField("Uploader", e.getUser().getAsTag(), false);
				}

				event.reply(embed.build()).queue();
			});

			return;
		}

		event.reply(embed.build()).queue();
	}

}
