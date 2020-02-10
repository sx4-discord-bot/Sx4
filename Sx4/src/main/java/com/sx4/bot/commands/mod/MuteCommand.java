package com.sx4.bot.commands.mod;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Context;
import com.jockie.bot.core.command.impl.CommandEvent;
import com.jockie.bot.core.option.Option;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.database.Database;
import com.sx4.bot.entities.mod.MuteData;
import com.sx4.bot.entities.mod.MuteUser;
import com.sx4.bot.exceptions.mod.MaxRolesException;
import com.sx4.bot.utility.ExceptionUtility;
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
	
	public void onCommand(CommandEvent event, @Context Database database, @Argument(value="user") Member member, @Argument(value="time") Duration time, @Argument(value="reason", endless=true, nullDefault=true) String reason, @Option(value="extend", description="Will extend the mute of the user if muted") boolean extend) {
		long seconds = time.toSeconds();
		
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
				
				MuteUser user = data.getUserById(member.getIdLong());
				
				ObjectId id;
				Bson update;
				List<Bson> arrayFilters = null;
				if (user == null) {
					id = ObjectId.get();
					
					Document rawData = new Document("id", id)
							.append("unmuteAt", Clock.systemUTC().instant().getEpochSecond() + seconds)
							.append("userId", member.getIdLong());
					
					update = Updates.push("mute.users", rawData);
				} else {
					id = user.getId();
					
					arrayFilters = List.of(Filters.eq("user.userId", member.getIdLong()));
					
					update = extend ? Updates.inc("mute.users.$[user].unmuteAt", seconds) : Updates.set("mute.users.$[user].unmuteAt", Clock.systemUTC().instant().getEpochSecond() + seconds);
				}
				
				UpdateOptions options = new UpdateOptions().arrayFilters(arrayFilters).upsert(true);
				database.updateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((result, writeException) -> {
					if (writeException != null) {
						ExceptionUtility.sendExceptionally(event, exception);
					} else {
						event.reply("**" + member.getUser().getAsTag() + "** has " + (extend ? "had their mute extended" : "been muted") + " for " + TimeUtility.getTimeString(time.toSeconds()) + " <:done:403285928233402378>").queue();
						
						if (extend) {
							this.muteManager.extendExecutor(id, () -> new HashMap<>().put("a", "a"), seconds);
						} else {
							this.muteManager.putExecutor(id, this.muteExecutor.schedule(() -> new HashMap<>().put("a", "a"), seconds, TimeUnit.SECONDS));
						}
					}
				});
			}
		});
	}
	
}
