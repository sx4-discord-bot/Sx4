package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.annotations.argument.Global;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;

public class EmoteInfoCommand extends Sx4Command {

	public EmoteInfoCommand() {
		super("emote info", 262);

		super.setAliases("emote", "emoteinfo", "einfo");
		super.setDescription("Get information about an emote");
		super.setExamples("emote info <:emote:720020237952483490>", "emote info 720020237952483490", "emote info https://cdn.discordapp.com/emojis/720020237952483490.png");
		super.setCategoryAll(ModuleCategory.INFORMATION);
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="emote") @Global RichCustomEmoji emoji) {
		Guild guild = emoji.getGuild();

		EmbedBuilder embed = new EmbedBuilder()
			.setAuthor(emoji.getName(), emoji.getImageUrl(), emoji.getImageUrl())
			.setThumbnail(emoji.getImageUrl())
			.setTimestamp(emoji.getTimeCreated())
			.setFooter("Created", null)
			.addField("ID", emoji.getId(), false)
			.addField("Server", guild.getName() + " (" + guild.getId() + ")", false);

		if (guild.getSelfMember().hasPermission(Permission.MANAGE_EMOJIS_AND_STICKERS)) {
			guild.retrieveEmoji(emoji).queue(e -> {
				User owner = e.getOwner();
				if (owner != null) {
					embed.addField("Uploader", owner.getAsTag(), false);
				}

				event.reply(embed.build()).queue();
			});

			return;
		}

		event.reply(embed.build()).queue();
	}

}
