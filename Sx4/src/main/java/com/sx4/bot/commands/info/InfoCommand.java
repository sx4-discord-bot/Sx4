package com.sx4.bot.commands.info;

import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import org.bson.Document;

public class InfoCommand extends Sx4Command {

	public InfoCommand() {
		super("info", 241);

		super.setDescription("Gives a back story of Sx4 aswell as some other info");
		super.setExamples("info");
		super.setCategoryAll(ModuleCategory.INFORMATION);
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
	}

	public void onCommand(Sx4CommandEvent event) {
		EmbedBuilder embed = new EmbedBuilder();
		embed.setAuthor("Info!", "https://sx4.dev", event.getSelfUser().getEffectiveAvatarUrl());
		embed.setColor(event.getConfig().getColour());
		embed.setDescription(event.getConfig().getBackStory());

		StringBuilder developers = new StringBuilder();
		for (Document developer : event.getConfig().getOwners()) {
			User user = event.getShardManager().getUserById(developer.getLong("id"));
			if (user == null) {
				continue;
			}

			developers.append("[").append(user.getAsTag()).append("](").append(developer.containsKey("link") ? developer.getString("link") : "https://sx4.dev").append(")").append("\n");
		}

		embed.addField("Developers", developers.toString(), true);
		embed.addBlankField(true);

		StringBuilder credits = new StringBuilder();
		for (Document credit : event.getConfig().getCredits()) {
			credits.append("[").append(credit.getString("name")).append("](").append(credit.containsKey("link") ? credit.getString("link") : "https://sx4.dev").append(")").append("\n");
		}

		embed.addField("Credits", credits.toString(), true);

		embed.addField("Stats", String.format("Version: [%s](https://github.com/sx4-discord-bot/Sx4/tree/%<s)\nAverage Gateway Ping: %,.0fms\nServers: %,d\nCommands: %,d", Sx4.GIT_HASH, event.getShardManager().getAverageGatewayPing(), event.getShardManager().getGuildCache().size(), event.getCommandListener().getAllCommands().size()), true);
		embed.addBlankField(true);
		embed.addField("Other Info", "Invite: [Click Here](https://discord.com/oauth2/authorize?client_id=" + event.getSelfUser().getId() + "&permissions=8&scope=bot)\nSupport Server: [Click Here](" + event.getConfig().getSupportGuildInvite() + ")\nDonate: [PayPal](https://www.paypal.com/paypalme/SheaCartwright), [Patreon](https://patreon.com/Sx4)", true);

		event.reply(embed.build()).queue();
	}

}
