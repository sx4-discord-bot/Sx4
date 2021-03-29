package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.utility.TimeUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class ChannelInfoCommand extends Sx4Command {

	public ChannelInfoCommand() {
		super("channel info", 330);

		super.setDescription("View basic information on a channel");
		super.setAliases("channelinfo", "ci");
		super.setExamples("channel info", "channel info voice", "channel info Category", "channel info #channel");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="channel", endless=true, nullDefault=true) GuildChannel channel) {
		channel = channel == null ? event.getTextChannel() : channel;

		EmbedBuilder embed = new EmbedBuilder()
			.setAuthor(channel.getName(), null, event.getGuild().getIconUrl())
			.addField("Created", channel.getTimeCreated().format(TimeUtility.DEFAULT_FORMATTER), true)
			.addBlankField(true)
			.addField("Channel ID", channel.getId(), true)
			.addField("Channel Position", String.valueOf(channel.getPosition() + 1), true)
			.addBlankField(true)
			.addField("Members", String.valueOf(channel.getMembers().size()), true);

		if (channel instanceof TextChannel) {
			TextChannel textChannel = (TextChannel) channel;
			embed.addField("NSFW Channel", textChannel.isNSFW() ? "Yes" : "No", true);
			embed.addBlankField(true);
			embed.addField("Slow Mode", textChannel.getSlowmode() != 0 ? TimeUtility.getTimeString(textChannel.getSlowmode(), TimeUnit.SECONDS) : "No Slowmode Set", true);
			embed.addField("Channel Category", textChannel.getParent() == null ? "Not in a Category" : textChannel.getParent().getName(), true);
			embed.addBlankField(true);
			embed.addField("Announcement Channel", textChannel.isNews() ? "Yes" : "No", true);
		} else if (channel instanceof VoiceChannel) {
			VoiceChannel voiceChannel = (VoiceChannel) channel;
			embed.addField("Channel Category", voiceChannel.getParent() == null ? "Not in a Category" : voiceChannel.getParent().getName(), true);
			embed.addBlankField(true);
			embed.addField("User Limit", voiceChannel.getUserLimit() == 0 ? "Unlimited" : String.valueOf(voiceChannel.getUserLimit()), true);
			embed.addField("Bitrate", voiceChannel.getBitrate() / 1000 + " kbps", true);
		} else if (channel instanceof Category) {
			Category category = (Category) channel;

			StringBuilder builder = new StringBuilder();

			List<GuildChannel> guildChannels = category.getChannels();
			for (int i = 0; i < guildChannels.size(); i++) {
				GuildChannel guildChannel = guildChannels.get(i);

				String name;
				if (guildChannel.getType() == ChannelType.TEXT) {
					name = "<#" + guildChannel.getId() + ">";
				} else {
					name = guildChannel.getName();
				}

				// Add an extra 13 to make sure it can fit with "and **x** more"
				String remaining = String.valueOf(guildChannels.size() - i - 1);
				if (builder.length() + name.length() + 13 + remaining.length() > MessageEmbed.VALUE_MAX_LENGTH) {
					builder.append("and **").append(remaining).append("** more");
					break;
				} else {
					if (i != 0) {
						builder.append("\n");
					}

					builder.append(name);
				}
			}

			embed.addField("Category Channels", builder.length() == 0 ? "This category is empty" : builder.toString(), false);
		}

		event.reply(embed.build()).queue();
	}

}
