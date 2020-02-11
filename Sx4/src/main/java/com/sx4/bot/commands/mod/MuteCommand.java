package com.sx4.bot.commands.mod;

import java.time.Duration;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Context;
import com.jockie.bot.core.command.impl.CommandEvent;
import com.jockie.bot.core.option.Option;
import com.mongodb.client.model.Projections;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.database.Database;
import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.entities.mod.mute.MuteData;
import com.sx4.bot.events.mod.ModActionEvent;
import com.sx4.bot.events.mod.MuteEvent;
import com.sx4.bot.events.mod.MuteExtendEvent;
import com.sx4.bot.exceptions.mod.MaxRolesException;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.ModUtility;
import com.sx4.bot.utility.TimeUtility;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;

public class MuteCommand extends Sx4Command {

	public MuteCommand() {
		super("mute");
		
		super.setExamples("mute @Shea#6653 20m", "mute Shea 30m Spamming", "mute 402557516728369153 12h template:offensive & Spamming");
		super.setDescription("Mute a user server wide");
		super.setAuthorDiscordPermissions(Permission.MESSAGE_MANAGE);
		super.setBotDiscordPermissions(Permission.MANAGE_ROLES);
	}
	
	public void onCommand(CommandEvent event, @Context Database database, @Argument(value="user") Member member, @Argument(value="time", nullDefault=true) Duration time, @Argument(value="reason", endless=true, nullDefault=true) Reason reason, @Option(value="extend", description="Will extend the mute of the user if muted") boolean extend) {
		if (!event.getMember().canInteract(member)) {
			event.reply("You cannot mute someone higher or equal than your top role :no_entry:").queue();
			return;
		}
		
		if (!event.getSelfMember().canInteract(member)) {
			event.reply("I cannot mute someone higher or equal than your top role :no_entry:").queue();
			return;
		}
		
		MuteData data = new MuteData(database.getGuildById(event.getGuild().getIdLong(), Projections.include("mute")).get("mute", Database.EMPTY_DOCUMENT));
		data.getOrCreateRole(event.getGuild(), (role, exception) -> {
			if (exception != null) {
				if (exception instanceof MaxRolesException) {
					event.reply(exception.getMessage() + " :no_entry:").queue();
					return;
				}
				
				event.reply(ExceptionUtility.getSimpleErrorMessage(exception)).queue();
			} else {
				if (member.getRoles().contains(role) && !extend) {
					event.reply("That user is already muted :no_entry:").queue();
					return;
				}
				
				long seconds = time == null ? data.getDefaultTime() : time.toSeconds();
				
				database.updateGuildById(data.getUpdate(event.getGuild().getIdLong(), member.getIdLong(), seconds, extend)).whenComplete((result, writeException) -> {
					if (writeException != null) {
						ExceptionUtility.sendExceptionally(event, writeException);
					} else {
						try {
							event.getGuild().addRoleToMember(member, role).reason(ModUtility.getAuditReason(reason, event.getAuthor())).queue($ -> {
								event.reply("**" + member.getUser().getAsTag() + "** has " + (extend ? "had their mute extended" : "been muted") + " for " + TimeUtility.getTimeString(seconds) + " <:done:403285928233402378>").queue();
								
								this.muteManager.putMute(event.getGuild().getIdLong(), member.getIdLong(), role.getIdLong(), seconds);
								
								ModActionEvent modEvent = extend ? new MuteExtendEvent(event.getMember(), member.getUser(), reason, seconds) : new MuteEvent(event.getMember(), member.getUser(), reason, seconds);
								this.modManager.onModAction(modEvent);
							});
						} catch (Throwable e) {
							e.printStackTrace();
						}
					}
				});
			}
		});
	}
	
}
