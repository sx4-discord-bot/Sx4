package com.sx4.bot.managers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.sx4.bot.core.Sx4Bot;
import com.sx4.bot.database.Database;
import com.sx4.bot.utility.ExceptionUtility;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

public class AutoRoleManager {
	
	private static final AutoRoleManager INSTANCE = new AutoRoleManager();
	
	public static AutoRoleManager get() {
		return AutoRoleManager.INSTANCE;
	}

	private final Map<Long, Map<Long, Map<ObjectId, ScheduledFuture<?>>>> executors;
	
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	
	public AutoRoleManager() {
		this.executors = new HashMap<>();
	}
	
	public ScheduledExecutorService getExecutor() {
		return this.executor;
	}
	
	public void updateMemberRoles(long guildId, long userId, ObjectId id, List<Long> roleIdsAdd, List<Long> roleIdsRemove) {
		UpdateOneModel<Document> model = this.updateMemberRolesAndGet(guildId, userId, id, roleIdsAdd, roleIdsRemove);
		if (model != null) {
			Database.get().updateGuildById(model).whenComplete((result, exception) -> ExceptionUtility.sendErrorMessage(exception));
		}
	}
	
	public UpdateOneModel<Document> updateMemberRolesAndGet(long guildId, long userId, ObjectId id, List<Long> roleIdsAdd, List<Long> roleIdsRemove) {
		Guild guild = Sx4Bot.getShardManager().getGuildById(guildId);
		if (guild == null) {
			return null;
		}
		
		Member member = guild.getMemberById(userId);
		if (member == null) {
			return null;
		}
		
		List<Role> rolesAdd = roleIdsAdd.stream()
			.map(guild::getRoleById)
			.filter(Objects::nonNull)
			.collect(Collectors.toList());
		
		List<Role> rolesRemove = roleIdsRemove.stream()
			.map(guild::getRoleById)
			.filter(Objects::nonNull)
			.collect(Collectors.toList());
		
		guild.modifyMemberRoles(member, rolesAdd, rolesRemove).queue();
		
		UpdateOptions options = new UpdateOptions().arrayFilters(List.of(Filters.eq("id", userId)));
		return new UpdateOneModel<>(Filters.eq("_id", guildId), Updates.pull("autoRole.users.$[user].tasks", Filters.eq("id", id)), options);
	}
	
}
