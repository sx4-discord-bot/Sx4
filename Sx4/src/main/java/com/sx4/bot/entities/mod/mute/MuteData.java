package com.sx4.bot.entities.mod.mute;

import java.time.Clock;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.sx4.bot.database.Database;
import com.sx4.bot.exceptions.mod.MaxRolesException;
import com.sx4.bot.utility.ExceptionUtility;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

public class MuteData {
	
	private final List<MuteUser> users;
	
	private final long roleId;
	private final long defaultTime;
	private final boolean autoUpdate;
	
	public MuteData(Document data) {
		List<Document> users = data.getList("users", Document.class, Collections.emptyList());
		
		this.roleId = data.get("roleId", 0L);
		this.defaultTime = data.get("defaultTime", 1800L);
		this.users = MuteUser.fromData(users);
		this.autoUpdate = data.getBoolean("autoUpdate", true);
	}

	public MuteData(long roleId, long defaultTime, boolean autoUpdate, List<MuteUser> users) {
		this.roleId = roleId;
		this.defaultTime = defaultTime;
		this.users = users;
		this.autoUpdate = autoUpdate;
	}
	
	public boolean isAutoUpdated() {
		return this.autoUpdate;
	}
	
	public boolean hasRoleId() {
		return this.roleId != 0L;
	}
	
	public long getRoleId() {
		return this.roleId;
	}
	
	public long getDefaultTime() {
		return this.defaultTime;
	}
	
	public List<MuteUser> getUsers() {
		return this.users;
	}
	
	public MuteUser getUserById(long id) {
		return this.users.stream()
			.filter(user -> user.getId() == id)
			.findFirst()
			.orElse(null);
	}
	
	private void createRole(Guild guild, BiConsumer<Role, Throwable> consumer) {
		Member selfMember = guild.getSelfMember();
		
		if (guild.getRoleCache().size() >= 250) {
			consumer.accept(null, new MaxRolesException("The guild has the max roles possible (250) so I was unable to make the mute role"));
			return;
		}
		
		guild.createRole().setName("Muted - " + selfMember.getUser().getName()).queue(newRole -> {
			Database.get().updateGuildById(guild.getIdLong(), Updates.set("mute.roleId", newRole.getIdLong())).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendErrorMessage(exception)) {
					consumer.accept(null, exception);
				} else {
					consumer.accept(newRole, null);
					
					if (this.autoUpdate && selfMember.hasPermission(Permission.MANAGE_PERMISSIONS)) {
						guild.getTextChannels().forEach(channel -> channel.upsertPermissionOverride(newRole).deny(Permission.MESSAGE_WRITE).queue());
					}
				}
			});
		});
	}
	
	public void getOrCreateRole(Guild guild, BiConsumer<Role, Throwable> consumer) {
		if (this.hasRoleId()) {
			Role role = guild.getRoleById(this.roleId);
			if (role != null) {
				consumer.accept(role, null);
			} else {
				this.createRole(guild, consumer);
			}
		} else {
			this.createRole(guild, consumer);
		}
	}
	
	public UpdateOneModel<Document> getUpdate(Member member, long seconds) {
		return this.getUpdate(member, seconds, false);
	}
	
	public UpdateOneModel<Document> getUpdate(Member member, long seconds, boolean extend) {
		return this.getUpdate(member.getGuild().getIdLong(), member.getIdLong(), seconds, extend);
	}
	
	public UpdateOneModel<Document> getUpdate(long guildId, long userId, long seconds) {
		return this.getUpdate(guildId, userId, seconds, false);
	}
	
	public UpdateOneModel<Document> getUpdate(long guildId, long userId, long seconds, boolean extend) {
		MuteUser user = this.getUserById(userId);
		
		Bson update;
		List<Bson> arrayFilters = null;
		if (user == null) {
			Document rawData = new Document("id", userId)
					.append("unmuteAt", Clock.systemUTC().instant().getEpochSecond() + seconds);
			
			update = Updates.push("mute.users", rawData);
		} else {
			arrayFilters = List.of(Filters.eq("user.id", user.getId()));
			
			update = extend ? Updates.inc("mute.users.$[user].unmuteAt", seconds) : Updates.set("mute.users.$[user].unmuteAt", Clock.systemUTC().instant().getEpochSecond() + seconds);
		}
		
		return new UpdateOneModel<>(Filters.eq("_id", guildId), update, new UpdateOptions().arrayFilters(arrayFilters).upsert(true));
	}
	
}
