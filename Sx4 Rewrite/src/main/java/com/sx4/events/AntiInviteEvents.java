package com.sx4.events;

import static com.rethinkdb.RethinkDB.r;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.rethinkdb.gen.ast.Get;
import com.rethinkdb.model.OptArgs;
import com.rethinkdb.net.Connection;
import com.sx4.core.Sx4Bot;
import com.sx4.utils.ModUtils;

import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Invite;
import net.dv8tion.jda.core.entities.Role;
import net.dv8tion.jda.core.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

public class AntiInviteEvents extends ListenerAdapter {

	private Pattern inviteRegex = Pattern.compile(".*(?:https?://)?(?:www.)?(?:discord.gg|(?:canary.)?discordapp.com/invite)/((?:[a-zA-Z0-9]){2,32}).*", Pattern.CASE_INSENSITIVE);	
	
	@SuppressWarnings("unchecked")
	public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
		if (event.isWebhookMessage()) {
			return;
		}
		
		if (event.getJDA().getSelfUser().equals(event.getAuthor())) {
			return;
		}
		
		if (event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
			return;
		}
		
		Matcher inviteMatch = this.inviteRegex.matcher(event.getMessage().getContentRaw());
		if (inviteMatch.matches()) {
			Connection connection = Sx4Bot.getConnection();
			Get data = r.table("antiad").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			if (dataRan == null || (boolean) dataRan.get("toggle") == false) {
				return;
			}
			
			Map<String, List<String>> whitelist = (Map<String, List<String>>) dataRan.get("whitelist");
			List<String> channelsData = whitelist.get("channels"), rolesData = whitelist.get("roles"), usersData = whitelist.get("users");
			if (channelsData.contains(event.getChannel().getId()) || channelsData.contains(event.getChannel().getParent().getId())) {
				return;
			} else if (usersData.contains(event.getAuthor().getId())) {
				return;
			} else {
				for (Role role : event.getMember().getRoles()) {
					if (rolesData.contains(role.getId())) {
						return;
					}
				}
			}
			
			Invite.resolve(event.getJDA(), inviteMatch.group(1), true).queue(invite -> {
				if (invite.getGuild().getId().equals(event.getGuild().getId())) {
					return;
				}
				
				event.getMessage().delete().queue();
				if (dataRan.get("action") == null) {
					event.getChannel().sendMessage(event.getAuthor().getAsMention() + ", You are not allowed to send invites here :no_entry:").queue();
					return;
				} else {
					List<Map<String, Object>> users = (List<Map<String, Object>>) dataRan.get("users");
					Map<String, Object> user = null;
					for (Map<String, Object> userData : users) {
						if (userData.get("id").equals(event.getAuthor().getId())) {
							user = userData;
						}
					}
					
					long currentAttempts = user == null ? 0 : (long) user.get("attempts");
					long dataAttempts = (long) dataRan.get("attempts");
					String action = (String) dataRan.get("action");
					if (currentAttempts + 1 >= dataAttempts) {
						String reason = "Sent " + dataAttempts + " invite" + (dataAttempts == 1 ? "" : "s");
						if (action.equals("ban")) {
							if (!event.getGuild().getSelfMember().hasPermission(Permission.BAN_MEMBERS)) {
								event.getChannel().sendMessage("I was unable to ban **" + event.getAuthor().getAsTag() + "** as I am missing the `Ban Members` permission :no_entry:").queue();
								return;
							} else if (event.getGuild().getSelfMember().getRoles().get(0).getPosition() <= event.getMember().getRoles().get(0).getPosition()) {
								event.getChannel().sendMessage("I was unable to ban **" + event.getAuthor().getAsTag() + "** as their top role is higher or equal than my top role :no_entry:").queue();
								return;
							} else {
								event.getChannel().sendMessage("**" + event.getAuthor().getAsTag() + "** has been banned for sending **" + dataAttempts + "** invite" + (dataAttempts == 1 ? "" : "s") + " <:done:403285928233402378>").queue();
								event.getAuthor().openPrivateChannel().queue(channel -> channel.sendMessage(ModUtils.getBanEmbed(event.getGuild(), event.getJDA().getSelfUser(), reason)).queue(), e -> {});
								event.getGuild().getController().ban(event.getMember(), 1, reason).queue();
								ModUtils.createModLogAndOffence(event.getGuild(), connection, event.getJDA().getSelfUser(), event.getAuthor(), "Ban (Automatic)", reason);
								
								data.update(row -> r.hashMap("users", row.g("users").filter(d -> d.g("id").ne(event.getAuthor().getId())))).runNoReply(connection);
							}
						} else if (action.equals("kick")) {
							if (!event.getGuild().getSelfMember().hasPermission(Permission.KICK_MEMBERS)) {
								event.getChannel().sendMessage("I was unable to kick **" + event.getAuthor().getAsTag() + "** as I am missing the `Kick Members` permission :no_entry:").queue();
								return;
							} else if (event.getGuild().getSelfMember().getRoles().get(0).getPosition() <= event.getMember().getRoles().get(0).getPosition()) {
								event.getChannel().sendMessage("I was unable to kick **" + event.getAuthor().getAsTag() + "** as their top role is higher or equal than my top role :no_entry:").queue();
								return;
							} else {
								event.getChannel().sendMessage("**" + event.getAuthor().getAsTag() + "** has been kicked for sending **" + dataAttempts + "** invite" + (dataAttempts == 1 ? "" : "s") + " <:done:403285928233402378>").queue();
								event.getAuthor().openPrivateChannel().queue(channel -> channel.sendMessage(ModUtils.getKickEmbed(event.getGuild(), event.getJDA().getSelfUser(), reason)).queue(), e -> {});
								event.getGuild().getController().kick(event.getMember(), reason).queue();
								ModUtils.createModLogAndOffence(event.getGuild(), connection, event.getJDA().getSelfUser(), event.getAuthor(), "Kick (Automatic)", reason);
								
								data.update(row -> r.hashMap("users", row.g("users").filter(d -> d.g("id").ne(event.getAuthor().getId())))).runNoReply(connection);
							}
						} else if (action.equals("mute")) {
							if (!event.getGuild().getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
								event.getChannel().sendMessage("I was unable to mute **" + event.getAuthor().getAsTag() + "** as I am missing the `Manage Roles` permission :no_entry:").queue();
								return;
							}
							
							ModUtils.setupMuteRole(event.getGuild(), role -> {
								if (role == null) {
									return;
								}
								
								if (role.getPosition() >= event.getGuild().getSelfMember().getRoles().get(0).getPosition()) {
									event.getChannel().sendMessage("I am unable to mute **" + event.getAuthor().getAsTag() + "** as the mute role is higher or equal than my top role :no_entry:").queue();
									return;
								}
								
								event.getChannel().sendMessage("**" + event.getAuthor().getAsTag() + "** has been muted for sending **" + dataAttempts + "** invite" + (dataAttempts == 1 ? "" : "s") + " <:done:403285928233402378>").queue();
								event.getAuthor().openPrivateChannel().queue(channel -> channel.sendMessage(ModUtils.getMuteEmbed(event.getGuild(), null,  event.getJDA().getSelfUser(), 0, reason)).queue(), e -> {});
								event.getGuild().getController().addSingleRoleToMember(event.getMember(), role).queue();
								ModUtils.createModLogAndOffence(event.getGuild(), connection, event.getJDA().getSelfUser(), event.getAuthor(), "Mute (Automatic)", reason);
								
								r.table("mute").insert(r.hashMap("id", event.getGuild().getId()).with("users", new Object[0])).run(connection, OptArgs.of("durability", "soft"));
								r.table("mute").get(event.getGuild().getId()).update(row -> r.hashMap("users", row.g("users").append(r.hashMap("id", event.getAuthor().getId()).with("time", r.now().toEpochTime().round()).with("amount", null)))).runNoReply(connection);
								
								data.update(row -> r.hashMap("users", row.g("users").filter(d -> d.g("id").ne(event.getAuthor().getId())))).runNoReply(connection);
							}, error -> {
								event.getChannel().sendMessage("I was unable to mute **" + event.getAuthor().getAsTag() + "** as " + error).queue();
								return;
							});
						}
					} else {
						event.getChannel().sendMessage(String.format("%s, You are not allowed to send invite links here. If you continue you will receive a %s. **(%d/%d)** :no_entry:", event.getAuthor().getAsMention(), action, currentAttempts + 1, dataAttempts)).queue();
						
						if (user != null) {
							users.remove(user);
							user.put("attempts", currentAttempts + 1);
							users.add(user);
							data.update(row -> r.hashMap("users", users)).runNoReply(connection);
						} else {
							data.update(row -> r.hashMap("users", row.g("users").append(r.hashMap("id", event.getAuthor().getId()).with("attempts", 1)))).runNoReply(connection);
						}
					}
				}
			}, e -> {});
		}
	}
	
	@SuppressWarnings("unchecked")
	public void onGuildMessageUpdate(GuildMessageUpdateEvent event) {	
		if (event.getJDA().getSelfUser().equals(event.getAuthor())) {
			return;
		}
		
		if (event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
			return;
		}
		
		Matcher inviteMatch = this.inviteRegex.matcher(event.getMessage().getContentRaw());
		if (inviteMatch.matches()) {
			Connection connection = Sx4Bot.getConnection();
			Get data = r.table("antiad").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			if (dataRan == null || (boolean) dataRan.get("toggle") == false) {
				return;
			}
			
			Map<String, List<String>> whitelist = (Map<String, List<String>>) dataRan.get("whitelist");
			List<String> channelsData = whitelist.get("channels"), rolesData = whitelist.get("roles"), usersData = whitelist.get("users");
			if (channelsData.contains(event.getChannel().getId()) || channelsData.contains(event.getChannel().getParent().getId())) {
				return;
			} else if (usersData.contains(event.getAuthor().getId())) {
				return;
			} else {
				for (Role role : event.getMember().getRoles()) {
					if (rolesData.contains(role.getId())) {
						return;
					}
				}
			}
			
			Invite.resolve(event.getJDA(), inviteMatch.group(1), true).queue(invite -> {
				if (invite.getGuild().getId().equals(event.getGuild().getId())) {
					return;
				}
				
				event.getMessage().delete().queue();
				if (dataRan.get("action") == null) {
					event.getChannel().sendMessage(event.getAuthor().getAsMention() + ", You are not allowed to send invites here :no_entry:").queue();
					return;
				} else {
					List<Map<String, Object>> users = (List<Map<String, Object>>) dataRan.get("users");
					Map<String, Object> user = null;
					for (Map<String, Object> userData : users) {
						if (userData.get("id").equals(event.getAuthor().getId())) {
							user = userData;
						}
					}
					
					long currentAttempts = user == null ? 0 : (long) user.get("attempts");
					long dataAttempts = (long) dataRan.get("attempts");
					String action = (String) dataRan.get("action");
					if (currentAttempts + 1 >= dataAttempts) {
						String reason = "Sent " + dataAttempts + " invite" + (dataAttempts == 1 ? "" : "s");
						if (action.equals("ban")) {
							if (!event.getGuild().getSelfMember().hasPermission(Permission.BAN_MEMBERS)) {
								event.getChannel().sendMessage("I was unable to ban **" + event.getAuthor().getAsTag() + "** as I am missing the `Ban Members` permission :no_entry:").queue();
								return;
							} else if (event.getGuild().getSelfMember().getRoles().get(0).getPosition() <= event.getMember().getRoles().get(0).getPosition()) {
								event.getChannel().sendMessage("I was unable to ban **" + event.getAuthor().getAsTag() + "** as their top role is higher or equal than my top role :no_entry:").queue();
								return;
							} else {
								event.getChannel().sendMessage("**" + event.getAuthor().getAsTag() + "** has been banned for sending **" + dataAttempts + "** invite" + (dataAttempts == 1 ? "" : "s") + " <:done:403285928233402378>").queue();
								event.getAuthor().openPrivateChannel().queue(channel -> channel.sendMessage(ModUtils.getBanEmbed(event.getGuild(), event.getJDA().getSelfUser(), reason)).queue(), e -> {});
								event.getGuild().getController().ban(event.getMember(), 1, reason).queue();
								ModUtils.createModLogAndOffence(event.getGuild(), connection, event.getJDA().getSelfUser(), event.getAuthor(), "Ban (Automatic)", reason);
								
								data.update(row -> r.hashMap("users", row.g("users").filter(d -> d.g("id").ne(event.getAuthor().getId())))).runNoReply(connection);
							}
						} else if (action.equals("kick")) {
							if (!event.getGuild().getSelfMember().hasPermission(Permission.KICK_MEMBERS)) {
								event.getChannel().sendMessage("I was unable to kick **" + event.getAuthor().getAsTag() + "** as I am missing the `Kick Members` permission :no_entry:").queue();
								return;
							} else if (event.getGuild().getSelfMember().getRoles().get(0).getPosition() <= event.getMember().getRoles().get(0).getPosition()) {
								event.getChannel().sendMessage("I was unable to kick **" + event.getAuthor().getAsTag() + "** as their top role is higher or equal than my top role :no_entry:").queue();
								return;
							} else {
								event.getChannel().sendMessage("**" + event.getAuthor().getAsTag() + "** has been kicked for sending **" + dataAttempts + "** invite" + (dataAttempts == 1 ? "" : "s") + " <:done:403285928233402378>").queue();
								event.getAuthor().openPrivateChannel().queue(channel -> channel.sendMessage(ModUtils.getKickEmbed(event.getGuild(), event.getJDA().getSelfUser(), reason)).queue(), e -> {});
								event.getGuild().getController().kick(event.getMember(), reason).queue();
								ModUtils.createModLogAndOffence(event.getGuild(), connection, event.getJDA().getSelfUser(), event.getAuthor(), "Kick (Automatic)", reason);
								
								data.update(row -> r.hashMap("users", row.g("users").filter(d -> d.g("id").ne(event.getAuthor().getId())))).runNoReply(connection);
							}
						} else if (action.equals("mute")) {
							if (!event.getGuild().getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
								event.getChannel().sendMessage("I was unable to mute **" + event.getAuthor().getAsTag() + "** as I am missing the `Manage Roles` permission :no_entry:").queue();
								return;
							}
							
							ModUtils.setupMuteRole(event.getGuild(), role -> {
								if (role == null) {
									return;
								}
								
								if (role.getPosition() >= event.getGuild().getSelfMember().getRoles().get(0).getPosition()) {
									event.getChannel().sendMessage("I am unable to mute **" + event.getAuthor().getAsTag() + "** as the mute role is higher or equal than my top role :no_entry:").queue();
									return;
								}
								
								event.getChannel().sendMessage("**" + event.getAuthor().getAsTag() + "** has been muted for sending **" + dataAttempts + "** invite" + (dataAttempts == 1 ? "" : "s") + " <:done:403285928233402378>").queue();
								event.getAuthor().openPrivateChannel().queue(channel -> channel.sendMessage(ModUtils.getMuteEmbed(event.getGuild(), null,  event.getJDA().getSelfUser(), 0, reason)).queue(), e -> {});
								event.getGuild().getController().addSingleRoleToMember(event.getMember(), role).queue();
								ModUtils.createModLogAndOffence(event.getGuild(), connection, event.getJDA().getSelfUser(), event.getAuthor(), "Mute (Automatic)", reason);
								
								r.table("mute").insert(r.hashMap("id", event.getGuild().getId()).with("users", new Object[0])).run(connection, OptArgs.of("durability", "soft"));
								r.table("mute").get(event.getGuild().getId()).update(row -> r.hashMap("users", row.g("users").append(r.hashMap("id", event.getAuthor().getId()).with("time", r.now().toEpochTime().round()).with("amount", null)))).runNoReply(connection);
								
								data.update(row -> r.hashMap("users", row.g("users").filter(d -> d.g("id").ne(event.getAuthor().getId())))).runNoReply(connection);
							}, error -> {
								event.getChannel().sendMessage("I was unable to mute **" + event.getAuthor().getAsTag() + "** as " + error).queue();
								return;
							});
						}
					} else {
						event.getChannel().sendMessage(String.format("%s, You are not allowed to send invite links here. If you continue you will receive a %s. **(%d/%d)** :no_entry:", event.getAuthor().getAsMention(), action, currentAttempts + 1, dataAttempts)).queue();
						
						if (user != null) {
							users.remove(user);
							user.put("attempts", currentAttempts + 1);
							users.add(user);
							data.update(row -> r.hashMap("users", users)).runNoReply(connection);
						} else {
							data.update(row -> r.hashMap("users", row.g("users").append(r.hashMap("id", event.getAuthor().getId()).with("attempts", 1)))).runNoReply(connection);
						}
					}
				}
			}, e -> {});
		}
	}
	
	public void onGuildMemberJoin(GuildMemberJoinEvent event) {
		Matcher inviteMatch = this.inviteRegex.matcher(event.getMember().getEffectiveName());
		if (inviteMatch.matches()) {
			Get data = r.table("antiad").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(Sx4Bot.getConnection());
			if (dataRan == null || (boolean) dataRan.get("baninvites") == false) {
				return;
			}
			
			Invite.resolve(event.getJDA(), inviteMatch.group(1), true).queue(invite -> {
				if (invite.getGuild().getId().equals(event.getGuild().getId())) {
					return;
				}
				
				String reason = "Discord invite in username";
				event.getMember().getUser().openPrivateChannel().queue(channel -> channel.sendMessage(ModUtils.getMuteEmbed(event.getGuild(), null,  event.getJDA().getSelfUser(), 0, reason)).queue(), e -> {});
				event.getGuild().getController().ban(event.getMember(), 1, reason).queue();
				ModUtils.createModLogAndOffence(event.getGuild(), Sx4Bot.getConnection(), event.getJDA().getSelfUser(), event.getMember().getUser(), "Ban (Automatic)", reason);
			});
		}
	}
	
}
