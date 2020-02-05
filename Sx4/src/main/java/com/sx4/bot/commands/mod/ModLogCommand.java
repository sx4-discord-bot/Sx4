package com.sx4.bot.commands.mod;

import java.util.List;

import org.bson.conversions.Bson;

import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.command.Command.AuthorPermissions;
import com.jockie.bot.core.command.Context;
import com.jockie.bot.core.command.impl.CommandEvent;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Field;
import com.mongodb.client.model.Projections;
import com.sx4.bot.annotations.Examples;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.database.Database;
import com.sx4.bot.database.model.Operators;
import com.sx4.bot.utility.HelpUtility;

import net.dv8tion.jda.api.Permission;

public class ModLogCommand extends Sx4Command {

	public ModLogCommand() {
		super("modlog");
		
		super.setAliases("modlogs", "mod log", "mod logs");
		super.setDescription("Setup the mod log in your server to log mod actions which happen within the server");
		super.setExamples("modlog toggle", "modlog channel", "modlog case");
	}
	
	public void onCommand(CommandEvent event) {
		event.reply(HelpUtility.getHelpMessage(event.getCommand(), event.getSelfMember().hasPermission(Permission.MESSAGE_EMBED_LINKS))).queue();
	}
	
	@Command(value="toggle", description="Turn modlogs on/off in your server")
	@AuthorPermissions({Permission.MANAGE_SERVER})
	@Examples({"modlog toggle"})
	public void toggle(CommandEvent event, @Context Database database) {
		List<Bson> update = List.of(Aggregates.addFields(new Field<>("modLog.enabled", Operators.cond("$modLog.enabled", "$$REMOVE", true))));
		database.findAndUpdateGuildById(event.getGuild().getIdLong(), Projections.include("modLog.enabled"), update).whenComplete((data, exception) -> {
			if (exception != null) {
				exception.printStackTrace();
				//send to error channel
			} else {
				event.reply("ModLogs are now **" + (data.getEmbedded(List.of("modLog", "enabled"), false) ? "enabled" : "disabled") + "** <:done:403285928233402378>").queue();
			}
		});
	}
	
}
