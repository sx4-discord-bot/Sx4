package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.utility.TimeUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.Region;
import net.dv8tion.jda.api.entities.*;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class ChannelInfoCommand extends Sx4Command {

	public ChannelInfoCommand() {
		super("channel info", 330);

		super.setDescription("View basic information on a channel");
		super.setAliases("channelinfo", "ci", "cinfo");
		super.setExamples("channel info", "channel info voice", "channel info Category", "channel info #channel");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="channel", endless=true, nullDefault=true) GuildChannel channel) {
		channel = channel == null ? (GuildMessageChannel) event.getChannel() : channel;

		EmbedBuilder embed = new EmbedBuilder();
		embed.setAuthor(channel.getName(), null, event.getGuild().getIconUrl());
		embed.addField("Created", channel.getTimeCreated().format(TimeUtility.DEFAULT_FORMATTER), true);

		if (channel instanceof IPositionableChannel positionableChannel) {
			embed.addField("Channel Position", String.valueOf(positionableChannel.getPosition() + 1), true);
		}

		embed.addField("Channel ID", channel.getId(), true);

		if (channel instanceof IMemberContainer container) {
			embed.addField("Members", String.valueOf(container.getMembers().size()), true);
		}

		if (channel instanceof TextChannel textChannel) {
			embed.addField("NSFW Channel", textChannel.isNSFW() ? "Yes" : "No", true);
			embed.addField("Slow Mode", textChannel.getSlowmode() != 0 ? TimeUtility.LONG_TIME_FORMATTER.parse(Duration.of(textChannel.getSlowmode(), ChronoUnit.SECONDS)) : "No Slowmode Set", true);
			embed.addField("Channel Category", textChannel.getParentCategory() == null ? "Not in a Category" : textChannel.getParentCategory().getName(), true);
			embed.addField("Announcement Channel", textChannel instanceof NewsChannel ? "Yes" : "No", true);
		} else if (channel instanceof VoiceChannel voiceChannel) {
			Region region = voiceChannel.getRegion();

			embed.addField("Channel Category", voiceChannel.getParentCategory() == null ? "Not in a Category" : voiceChannel.getParentCategory().getName(), true);
			embed.addField("Voice Region", region.getName() + (region == Region.AUTOMATIC ? "" : " " + region.getEmoji()), true);
			embed.addField("User Limit", voiceChannel.getUserLimit() == 0 ? "Unlimited" : String.valueOf(voiceChannel.getUserLimit()), true);
			embed.addField("Bitrate", voiceChannel.getBitrate() / 1000 + " kbps", true);
		} else if (channel instanceof Category category) {
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
