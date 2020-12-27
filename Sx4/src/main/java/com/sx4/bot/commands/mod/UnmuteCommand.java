package com.sx4.bot.commands.mod;

import com.jockie.bot.core.argument.Argument;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.events.mod.UnmuteEvent;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.ModUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

import java.util.List;

public class UnmuteCommand extends Sx4Command {

	public UnmuteCommand() {
		super("unmute", 150);
		
		super.setDescription("Unmute a user which is currently muted in the current server");
		super.setExamples("unmute @Shea#6653", "unmute Shea Misunderstanding", "unmute 402557516728369153 template:wrong-person");
		super.setBotDiscordPermissions(Permission.MANAGE_ROLES);
		super.setAuthorDiscordPermissions(Permission.MESSAGE_MANAGE);
		super.setCategoryAll(ModuleCategory.MODERATION);
	}
	
	public void onCommand(Sx4CommandEvent event, @Argument(value="user") Member member, @Argument(value="reason", endless=true, nullDefault=true) Reason reason) {
		long roleId = this.database.getGuildById(event.getGuild().getIdLong(), Projections.include("mute.roleId")).getEmbedded(List.of("mute", "roleId"), 0L);
		
		Role role = roleId == 0L ? null : event.getGuild().getRoleById(roleId);
		if (role == null || !member.getRoles().contains(role)) {
			event.replyFailure("That user is not muted").queue();
			return;
		}
		
		if (!event.getSelfMember().canInteract(role)) {
			event.replyFailure("I am unable to unmute that user as the mute role is higher or equal than my top role").queue();
			return;
		}
		
		this.database.updateMemberById(member.getUser().getIdLong(), event.getGuild().getIdLong(), Updates.unset("mute.unmuteAt")).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}
			
			event.getGuild().removeRoleFromMember(member, role).reason(ModUtility.getAuditReason(reason, event.getAuthor())).queue($ -> {
				event.replySuccess("**" + member.getUser().getAsTag() + "** has been unmuted").queue();
				
				this.muteManager.deleteExecutor(event.getGuild().getIdLong(), member.getIdLong());
				this.modManager.onModAction(new UnmuteEvent(event.getMember(), member.getUser(), reason));
			});
		});
	}
	
}
