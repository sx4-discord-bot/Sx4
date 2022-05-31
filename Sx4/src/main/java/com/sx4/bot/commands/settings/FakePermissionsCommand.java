package com.sx4.bot.commands.settings;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.sx4.bot.annotations.argument.AlternativeOptions;
import com.sx4.bot.annotations.command.AuthorPermissions;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.argument.Alternative;
import com.sx4.bot.entities.interaction.ButtonType;
import com.sx4.bot.entities.interaction.CustomButtonId;
import com.sx4.bot.entities.settings.HolderType;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.ExceptionUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.IPermissionHolder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class FakePermissionsCommand extends Sx4Command {

	public FakePermissionsCommand() {
		super("fake permissions", 169);
		
		super.setDescription("Setup permissions for user or roles which only work within the bot");
		super.setAliases("fakepermissions", "fake perms", "fakeperms");
		super.setExamples("fake permissions add", "fake permissions remove", "fake permissions list");
		super.setCategoryAll(ModuleCategory.SETTINGS);
	}
	
	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}
	
	@Command(value="add", description="Adds permissions to a user or role within the bot")
	@CommandId(170)
	@Examples({"fake permissions add @Shea#6653 message_manage", "fake permissions add @Mods kick_members ban_members"})
	@AuthorPermissions(permissions={Permission.ADMINISTRATOR})
	public void add(Sx4CommandEvent event, @Argument(value="user | role") IPermissionHolder holder, @Argument(value="permissions") Permission... permissions) {
		long rawPermissions = Permission.getRaw(permissions);
		boolean role = holder instanceof Role;
		
		Document data = new Document("id", holder.getIdLong())
			.append("type", role ? HolderType.ROLE.getType() : HolderType.USER.getType())
			.append("permissions", rawPermissions);
		
		Bson filter = Operators.filter("$fakePermissions.holders", Operators.eq("$$this.id", holder.getIdLong()));
		List<Bson> update = List.of(Operators.set("fakePermissions.holders", Operators.cond(Operators.extinct("$fakePermissions.holders"), List.of(data), Operators.cond(Operators.isEmpty(filter), Operators.concatArrays("$fakePermissions.holders", List.of(data)), Operators.concatArrays(Operators.filter("$fakePermissions.holders", Operators.ne("$$this.id", holder.getIdLong())), List.of(new Document(data).append("permissions", Operators.toLong(Operators.bitwiseOr(Operators.first(Operators.map(filter, "$$this.permissions")), rawPermissions)))))))));
		event.getMongo().updateGuildById(event.getGuild().getIdLong(), update).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}
			
			if (result.getModifiedCount() == 0) {
				event.replyFailure("That " + (role ? "role" : "user") + " already has all those permissions").queue();
				return;
			}
			
			event.replySuccess((role ? ((Role) holder).getAsMention() : "**" + ((Member) holder).getUser().getAsTag() + "**") + " now has those permissions").queue();
		});
	}
	
	@Command(value="remove", description="Removes permissions from a user or role within the bot")
	@CommandId(171)
	@Examples({"fake permissions remove @Shea#6653 message_manage", "fake permissions remove @Mods kick_members ban_members", "fake permissions remove @Mods all"})
	@AuthorPermissions(permissions={Permission.ADMINISTRATOR})
	public void remove(Sx4CommandEvent event, @Argument(value="user | role") IPermissionHolder holder, @Argument(value="permissions") Permission... permissions) {
		long rawPermissions = Permission.getRaw(permissions);
		boolean role = holder instanceof Role;
		
		Document data = new Document("id", holder.getIdLong())
			.append("type", role ? 1 : 0);

		Bson filter = Operators.filter("$fakePermissions.holders", Operators.eq("$$this.id", holder.getIdLong()));
		Bson withoutHolder = Operators.filter("$fakePermissions.holders", Operators.ne("$$this.id", holder.getIdLong()));
		Bson newPermissions = Operators.toLong(Operators.bitwiseAnd(Operators.first(Operators.map(filter, "$$this.permissions")), ~rawPermissions));
		
		List<Bson> update = List.of(Operators.set("fakePermissions.holders", Operators.cond(Operators.or(Operators.extinct("$fakePermissions.holders"), Operators.isEmpty(filter)), "$fakePermissions.holders", Operators.cond(Operators.eq(newPermissions, 0L), withoutHolder, Operators.concatArrays(withoutHolder, List.of(data.append("permissions", newPermissions)))))));
		event.getMongo().updateGuildById(event.getGuild().getIdLong(), update).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}
			
			if (result.getModifiedCount() == 0) {
				event.replyFailure("That " + (role ? "role" : "user") + " doesn't have any of those permissions").queue();
				return;
			}
			
			event.replySuccess((role ? ((Role) holder).getAsMention() : "**" + ((Member) holder).getUser().getAsTag() + "**") + " no longer has those permissions").queue();
		});
	}
	
	@Command(value="delete", aliases={"remove"}, description="Deletes fake permissions for a user or role")
	@CommandId(172)
	@Examples({"fake permissions delete @Shea#6653", "fake permissions delete @Mods", "fake permissions delete all"})
	@AuthorPermissions(permissions={Permission.ADMINISTRATOR})
	public void delete(Sx4CommandEvent event, @Argument(value="user | role | all", endless=true, nullDefault=true) @AlternativeOptions("all") Alternative<IPermissionHolder> option) {
		if (option == null) {
			List<Document> fakePermissions = event.getProperty("fakePermissions");
			if (fakePermissions.isEmpty()) {
				event.replyFailure("Nothing has fake permissions in this server").queue();
				return;
			}

			PagedResult<Document> paged = new PagedResult<>(event.getBot(), fakePermissions)
				.setAuthor("User & Roles", null, event.getGuild().getIconUrl())
				.setTimeout(60)
				.setDisplayFunction(data -> {
					int type = data.getInteger("type");
					long id = data.getLong("id");

					if (type == HolderType.USER.getType()) {
						User user = event.getShardManager().getUserById(id);
						return user == null ? "Anonymous#0000 (" + id + ")" : user.getAsTag();
					} else {
						Role role = event.getGuild().getRoleById(id);
						return role == null ? "Deleted Role (" + id + ")" : role.getAsMention();
					}
				});

			paged.onTimeout(() -> event.reply("Timed out :stopwatch:").queue());

			paged.onSelect(select -> {
				Document data = select.getSelected();

				boolean isRole = data.getInteger("type") == HolderType.ROLE.getType();
				long id = data.getLong("id");

				User user = isRole ? null : event.getShardManager().getUserById(id);
				Role role = isRole ? event.getGuild().getRoleById(id) : null;

				event.getMongo().updateGuildById(event.getGuild().getIdLong(), Updates.pull("fakePermissions.holders", Filters.eq("id", id))).whenComplete((result, exception) -> {
					if (ExceptionUtility.sendExceptionally(event, exception)) {
						return;
					}

					if (result.getModifiedCount() == 0) {
						event.replyFailure("That " + (isRole ? "role" : "user") + " doesn't have any fake permissions").queue();
						return;
					}

					event.replySuccess((isRole ? (role == null ? "Deleted Role (" + id + ")" : role.getAsMention()) : "**" + (user == null ? "Anonymous#0000** (" + id + ")" : user.getAsTag() + "**")) + " no longer has any fake permissions").queue();
				});
			});

			paged.execute(event);
		} else if (option.isAlternative()) {
			String acceptId = new CustomButtonId.Builder()
				.setType(ButtonType.FAKE_PERMISSIONS_DELETE_CONFIRM)
				.setOwners(event.getAuthor().getIdLong())
				.setTimeout(60)
				.getId();

			String rejectId = new CustomButtonId.Builder()
				.setType(ButtonType.GENERIC_REJECT)
				.setOwners(event.getAuthor().getIdLong())
				.setTimeout(60)
				.getId();

			List<Button> buttons = List.of(Button.success(acceptId, "Yes"), Button.danger(rejectId, "No"));

			event.reply(event.getAuthor().getName() + ", are you sure you want to delete **all** fake permissions data?")
				.setActionRow(buttons)
				.queue();
		} else {
			IPermissionHolder holder = option.getValue();
			boolean role = holder instanceof Role;
			
			event.getMongo().updateGuildById(event.getGuild().getIdLong(), Updates.pull("fakePermissions.holders", Filters.eq("id", holder.getIdLong()))).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}
				
				if (result.getModifiedCount() == 0) {
					event.replyFailure("That " + (role ? "role" : "user") + " doesn't have any fake permissions").queue();
					return;
				}
				
				event.replySuccess((role ? ((Role) holder).getAsMention() : "**" + ((Member) holder).getUser().getAsTag() + "**") + " no longer has any fake permissions").queue();
			});
		}
	}
	
	@Command(value="stats", description="Lists the permissions a role or user has")
	@CommandId(173)
	@Examples({"fake permissions stats @Shea#6653", "fake permissions stats @Mods"})
	public void stats(Sx4CommandEvent event, @Argument(value="user | role", endless=true) IPermissionHolder holder) {
		boolean role = holder instanceof Role;
		
		List<Document> holders = event.getMongo().getGuildById(event.getGuild().getIdLong(), Projections.include("fakePermissions.holders")).getEmbedded(List.of("fakePermissions", "holders"), Collections.emptyList());
		long permissionsRaw = holders.stream()
			.filter(data -> data.getLong("id") == holder.getIdLong())
			.map(data -> data.getLong("permissions"))
			.findFirst()
			.orElse(0L);
		
		if (permissionsRaw == 0L) {
			event.replyFailure("That " + (role ? "role" : "user") + " doesn't have any fake permissions").queue();
			return;
		}
		
		EmbedBuilder embed = new EmbedBuilder()
			.setDescription(Permission.getPermissions(permissionsRaw).stream().map(Permission::getName).collect(Collectors.joining("\n")))
			.setAuthor((role ? ((Role) holder).getName() : ((Member) holder).getEffectiveName()) + "'s Fake Permissions", null, role ? event.getGuild().getIconUrl() : ((Member) holder).getUser().getEffectiveAvatarUrl());
		
		event.reply(embed.build()).queue();
	}
	
	@Command(value="in permission", aliases={"inpermission", "inperm", "in perm", "in"}, description="Lists all roles and users in a certain permissions")
	@CommandId(174)
	@Examples({"fake permissions in permission message_manage", "fake permissions in permission kick_members ban_members"})
	public void inPermission(Sx4CommandEvent event, @Argument(value="permissions") Permission... permissions) {
		long permissionsRaw = Permission.getRaw(permissions);
		
		List<Document> allHolders = event.getProperty("fakePermissions");
		if (allHolders.isEmpty()) {
			event.replyFailure("Nothing has fake permissions in this server").queue();
			return;
		}
		
		List<Document> holders = allHolders.stream()
			.sorted(Comparator.comparingInt(a -> a.getInteger("type")))
			.filter(data -> (data.getLong("permissions") & permissionsRaw) == permissionsRaw)
			.collect(Collectors.toList());
		
		PagedResult<Document> paged = new PagedResult<>(event.getBot(), holders)
			.setAuthor("Roles & Users", null, event.getGuild().getIconUrl())
			.setPerPage(15)
			.setIndexed(false)
			.setDisplayFunction(data -> {
				int type = data.getInteger("type");
				long id = data.getLong("id");

				if (type == HolderType.USER.getType()) {
					User user = event.getShardManager().getUserById(id);
					return user == null ? "Anonymous#0000 (" + id + ")" : user.getAsTag();
				} else {
					Role role = event.getGuild().getRoleById(id);
					return role == null ? "Deleted Role (" + id + ")" : role.getAsMention();
				}
			});
		
		paged.execute(event);
	}
	
	@Command(value="list", description="Lists all permissions you can use as arguments")
	@CommandId(175)
	@Examples({"fake permissions list"})
	public void list(Sx4CommandEvent event) {
		EmbedBuilder embed = new EmbedBuilder()
			.setDescription(Arrays.stream(Permission.values()).filter(Predicate.not(Permission.UNKNOWN::equals)).map(Permission::name).collect(Collectors.joining("\n")))
			.setAuthor("Fake Permissions", null, event.getGuild().getIconUrl());
		
		event.reply(embed.build()).queue();
	}
	
}
