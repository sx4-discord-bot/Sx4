package com.sx4.bot.commands.mod;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Emote;

public class DeleteEmoteCommand extends Sx4Command {

	public DeleteEmoteCommand() {
		super("delete emote");
		
		super.setDescription("Deletes an emote in the current server");
		super.setAliases("deleteemote", "de");
		super.setExamples("create emote <:sx4:637715282995183636>", "create emote sx4", "create emote https://cdn.discordapp.com/emojis/637715282995183636.png");
		super.setCategoryAll(ModuleCategory.MODERATION);
		super.setAuthorDiscordPermissions(Permission.MANAGE_EMOTES);
		super.setBotDiscordPermissions(Permission.MANAGE_EMOTES);
	}
	
	public void onCommand(Sx4CommandEvent event, @Argument(value="emote") Emote emote) {
		if (emote.isManaged()) {
			event.reply("I cannot delete emotes that are managed " + this.config.getFailureEmote()).queue();
			return;
		}
		
		emote.delete().flatMap($ -> event.reply("I have deleted the emote `" + emote.getName() + "` " + this.config.getSuccessEmote())).queue();
	}
	
}
