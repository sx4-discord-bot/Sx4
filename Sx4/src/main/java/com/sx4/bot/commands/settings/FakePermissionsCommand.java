package com.sx4.bot.commands.settings;

import java.util.List;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.sx4.bot.annotations.command.AuthorPermissions;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.Category;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.model.Operators;
import com.sx4.bot.utility.ExceptionUtility;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.IPermissionHolder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

public class FakePermissionsCommand extends Sx4Command {

	public FakePermissionsCommand() {
		super("fake permissions");
		
		super.setDescription("Setup permissions for user or roles which only work within the bot");
		super.setAliases("fakepermissions", "fake perms", "fakeperms");
		super.setExamples("fake permissions add", "fake permissions remove", "fake permissions list");
		super.setCategory(Category.SETTINGS);
	}
	
	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}
	
	@Command(value="add", description="Adds permissions to a user or role within the bot")
	@Examples({"fake permissions add @Shea#6653 manage_messages", "fake permissions add @Mods kick_members ban_members"})
	@AuthorPermissions(permissions={Permission.ADMINISTRATOR})
	public void add(Sx4CommandEvent event, @Argument(value="user | role") IPermissionHolder holder, @Argument(value="permissions") Permission... permissions) {
		long rawPermissions = Permission.getRaw(permissions);
		boolean role = holder instanceof Role;
		
		Document data = new Document("id", holder.getIdLong())
				.append("type", role ? 1 : 0)
				.append("permissions", rawPermissions);
		
		Bson filter = Operators.filter("$fakePermissions.holders", Operators.eq("$$this.id", holder.getIdLong()));
		List<Bson> update = List.of(Operators.set("fakePermissions.holders", Operators.cond(Operators.extinct("$fakePermissions.holders"), List.of(data), Operators.cond(Operators.isEmpty(filter), Operators.concatArrays("$fakePermissions.holders", List.of(data)), Operators.concatArrays(Operators.filter("$fakePermissions.holders", Operators.ne("$$this.id", holder.getIdLong())), List.of(new Document(data).append("permissions", Operators.toLong(Operators.bitwiseOr(Operators.first(Operators.map(filter, "$$this.permissions")), rawPermissions)))))))));
		this.database.updateGuildById(event.getGuild().getIdLong(), update).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}
			
			if (result.getModifiedCount() == 0) {
				event.reply("That " + (role ? "role" : "user") + " already has all those permissions " + this.config.getFailureEmote()).queue();
				return;
			}
			
			event.reply((role ? ((Role) holder).getAsMention() : "**" + ((Member) holder).getUser().getAsTag() + "**") + " now has those permissions " + this.config.getSuccessEmote()).queue();
		});
	}
	
}
