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
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class MemberInfoCommand extends Sx4Command {

	public MemberInfoCommand() {
		super("member info", 348);

		super.setDescription("View some basic information of a user in the current server");
		super.setAliases("memberinfo", "mi", "minfo");
		super.setExamples("member info @Shea#6653", "member info Shea", "member info 402557516728369153");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="user", endless=true, nullDefault=true) Member member) {
		member = member == null ? event.getMember() : member;
		User user = member.getUser();

		List<Member> members = event.getGuild().getMemberCache().applyStream(stream -> {
			return stream.sorted(Comparator.comparing(Member::getTimeJoined)).collect(Collectors.toList());
		});

		int joinPosition = members.indexOf(member) + 1;

		OffsetDateTime boostTime = member.getTimeBoosted();
		List<Role> roles = member.getRoles();
		String nickname = member.getNickname();

		EmbedBuilder embed = new EmbedBuilder()
			.setAuthor(user.getAsTag(), null, user.getEffectiveAvatarUrl())
			.setThumbnail(user.getEffectiveAvatarUrl())
			.setColor(member.getColor())
			.setDescription(event.getConfig().getUserFlagEmotes(user.getFlags()))
			.setFooter("Join Position: " + NumberUtility.getSuffixed(joinPosition), null)
			.addField("Joined Discord", user.getTimeCreated().format(TimeUtility.DEFAULT_FORMATTER), true)
			.addField("Joined Server", member.getTimeJoined().format(TimeUtility.DEFAULT_FORMATTER), true)
			.addField("Boosting Since", boostTime == null ? "Not Boosting" : boostTime.format(TimeUtility.DEFAULT_FORMATTER), true)
			.addField("Nickname", nickname == null ? "None" : nickname, true)
			.addField("Bot", user.isBot() ? "Yes" : "No", true)
			.addField("User Colour", "Hex: #" + ColourUtility.toHexString(member.getColorRaw()) + "\nRGB: " + ColourUtility.toRGBString(member.getColorRaw()), true)
			.addField("Highest Role", roles.isEmpty() ? event.getGuild().getPublicRole().getAsMention() : roles.get(0).getAsMention(), true)
			.addField("Number of Roles", String.valueOf(member.getRoles().size()), true)
			.addField("User ID", member.getId(), true);

		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < roles.size(); i++) {
			String mention = roles.get(i).getAsMention();

			String remaining = String.valueOf(roles.size() - i - 1);
			if (builder.length() + mention.length() + remaining.length() + 13 > MessageEmbed.VALUE_MAX_LENGTH) {
				builder.append(" and **").append(remaining).append("** more");
				break;
			} else {
				if (i != 0) {
					builder.append(", ");
				}

				builder.append(mention);
			}
		}

		embed.addField("Roles", builder.toString(), false);

		event.reply(embed.build()).queue();
	}

}
