package com.sx4.bot.commands.fun;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;

public class EmbedCommand extends Sx4Command {

	public EmbedCommand() {
		super("embed", 290);

		super.setDescription("Creates a simple embed from text");
		super.setExamples("embed hello", "embed My text is in a box");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.FUN);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="text", endless=true) @Limit(max=4096) String text) {
		EmbedBuilder embed = new EmbedBuilder()
			.setAuthor(event.getAuthor().getAsTag(), null, event.getAuthor().getEffectiveAvatarUrl())
			.setColor(event.getMember().getColorRaw())
			.setDescription(text);

		event.reply(embed.build()).queue();
	}

}
