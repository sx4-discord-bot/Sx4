package com.sx4.events;

import java.time.Clock;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.sx4.database.Database;
import com.sx4.utils.ModUtils;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class AntiInviteEvents extends ListenerAdapter {

	private Pattern inviteRegex = Pattern.compile("(?:.|\n)*(?:https?://)?(?:www.)?(?:discord.gg|(?:canary.)?discordapp.com/invite)/((?:[a-zA-Z0-9]){2,32})(?:.|\n)*", Pattern.CASE_INSENSITIVE);	
	
	public void onGuildMessageReceived(GuildMessageReceivedEvent event) {		
		if (event.getJDA().getSelfUser().equals(event.getAuthor()) || event.isWebhookMessage() || event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
			return;
		}
		
		Matcher inviteMatch = this.inviteRegex.matcher(event.getMessage().getContentRaw());
		if (inviteMatch.matches()) {
			Bson projection = Projections.include("antiinvite.enabled", "antiinvite.whitelist", "antiinvite.users", "antiinvite.action", "antiinvite.attempts");
			Document data = Database.get().getGuildById(event.getGuild().getIdLong(), null, projection).get("antiinvite", Database.EMPTY_DOCUMENT);
			if (data.isEmpty() || data.getBoolean("enabled") == false) {
				return;
			}
			
			Document whitelist = data.get("whitelist", Database.EMPTY_DOCUMENT);
			List<Long> channelsData = whitelist.getList("channels", Long.class, Collections.emptyList()), 
					rolesData = whitelist.getList("roles", Long.class, Collections.emptyList()), 
					usersData = whitelist.getList("users", Long.class, Collections.emptyList());
			
			if (channelsData.contains(event.getChannel().getIdLong()) || channelsData.contains(event.getChannel().getParent().getIdLong())) {
				return;
			} else if (usersData.contains(event.getAuthor().getIdLong())) {
				return;
			} else {
				for (Role role : event.getMember().getRoles()) {
					if (rolesData.contains(role.getIdLong())) {
						return;
					}
				}
			}
			
			Invite.resolve(event.getJDA(), inviteMatch.group(1), true).queue(invite -> {
				if (invite.getGuild().getIdLong() == event.getGuild().getIdLong()) {
					return;
				}
				
				String action = data.getString("action");
				
				event.getMessage().delete().queue();
				if (action == null) {
					event.getChannel().sendMessage(event.getAuthor().getAsMention() + ", You are not allowed to send invite links here :no_entry:").queue();
					return;
				} else {
					List<Document> users = data.getList("users", Document.class, Collections.emptyList());
					Document user = null;
					for (Document userData : users) {
						if (userData.getLong("id") == event.getAuthor().getIdLong()) {
							user = userData;
						}
					}
					
					int currentAttempts = user == null ? 0 : user.getInteger("attempts", 0);
					int dataAttempts = data.getInteger("attempts", 3);
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
								event.getGuild().ban(event.getMember(), 1, reason).queue();
								ModUtils.createModLogAndOffence(event.getGuild(), event.getJDA().getSelfUser(), event.getAuthor(), "Ban (Automatic)", reason);
								
								Database.get().updateGuildById(event.getGuild().getIdLong(), Updates.pull("antiinvite.users", Filters.eq("id", event.getAuthor().getIdLong())), (result, exception) -> {
									if (exception != null) {
										exception.printStackTrace();
									}
								});
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
								event.getGuild().kick(event.getMember(), reason).queue();
								ModUtils.createModLogAndOffence(event.getGuild(), event.getJDA().getSelfUser(), event.getAuthor(), "Kick (Automatic)", reason);
								
								Database.get().updateGuildById(event.getGuild().getIdLong(), Updates.pull("antiinvite.users", Filters.eq("id", event.getAuthor().getIdLong())), (result, exception) -> {
									if (exception != null) {
										exception.printStackTrace();
									}
								});
							}
						} else if (action.equals("mute")) {
							if (!event.getGuild().getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
								event.getChannel().sendMessage("I was unable to mute **" + event.getAuthor().getAsTag() + "** as I am missing the `Manage Roles` permission :no_entry:").queue();
								return;
							}
							
							ModUtils.getOrCreateMuteRole(event.getGuild(), (role, error) -> {
								if (error != null) {
									event.getChannel().sendMessage("I was unable to mute **" + event.getAuthor().getAsTag() + "** as " + error + " :no_entry:").queue();
									return;
								}
								
								if (role.getPosition() >= event.getGuild().getSelfMember().getRoles().get(0).getPosition()) {
									event.getChannel().sendMessage("I am unable to mute **" + event.getAuthor().getAsTag() + "** as the mute role is higher or equal than my top role :no_entry:").queue();
									return;
								}
								
								event.getChannel().sendMessage("**" + event.getAuthor().getAsTag() + "** has been muted for sending **" + dataAttempts + "** invite" + (dataAttempts == 1 ? "" : "s") + " <:done:403285928233402378>").queue();
								event.getAuthor().openPrivateChannel().queue(channel -> channel.sendMessage(ModUtils.getMuteEmbed(event.getGuild(), null,  event.getJDA().getSelfUser(), 0, reason)).queue(), e -> {});
								event.getGuild().addRoleToMember(event.getMember(), role).queue();
								ModUtils.createModLogAndOffence(event.getGuild(), event.getJDA().getSelfUser(), event.getAuthor(), "Mute (Automatic)", reason);
								
								Bson update = Updates.combine(
										Updates.set("mute.users.$[user].duration", null),
										Updates.set("mute.users.$[user].timestamp", Clock.systemUTC().instant().getEpochSecond()),
										Updates.pull("antiinvite.users", Filters.eq("id", event.getAuthor().getIdLong()))
								);
								
								UpdateOptions updateOptions = new UpdateOptions().arrayFilters(List.of(Filters.eq("user.id", event.getAuthor().getIdLong()))).upsert(true);
								
								Database.get().updateGuildById(event.getGuild().getIdLong(), null, update, updateOptions, (result, exception) -> {
									if (exception != null) {
										exception.printStackTrace();
									}
								});
							});
						}
					} else {
						event.getChannel().sendMessage(String.format("%s, You are not allowed to send invite links here. If you continue you will receive a %s. **(%d/%d)** :no_entry:", event.getAuthor().getAsMention(), action, currentAttempts + 1, dataAttempts)).queue();
						
						Bson update = Updates.inc("antiinvite.users.$[user].attempts", 1);
						UpdateOptions updateOptions = new UpdateOptions().arrayFilters(List.of(Filters.eq("id", event.getAuthor().getIdLong()))).upsert(true);
						Database.get().updateGuildById(event.getGuild().getIdLong(), null, update, updateOptions, (result, exception) -> {
							if (exception != null) {
								exception.printStackTrace();
							}
						});
					}
				}
			}, e -> {});
		}
	}

	public void onGuildMessageUpdate(GuildMessageUpdateEvent event) {	
		if (event.getJDA().getSelfUser().equals(event.getAuthor()) || event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
			return;
		}
		
		Matcher inviteMatch = this.inviteRegex.matcher(event.getMessage().getContentRaw());
		if (inviteMatch.matches()) {
			Bson projection = Projections.include("antiinvite.enabled", "antiinvite.whitelist", "antiinvite.users", "antiinvite.action", "antiinvite.attempts");
			Document data = Database.get().getGuildById(event.getGuild().getIdLong(), null, projection).get("antiinvite", Database.EMPTY_DOCUMENT);
			if (data.isEmpty() || data.getBoolean("enabled") == false) {
				return;
			}
			
			Document whitelist = data.get("whitelist", Database.EMPTY_DOCUMENT);
			List<Long> channelsData = whitelist.getList("channels", Long.class, Collections.emptyList()), 
					rolesData = whitelist.getList("roles", Long.class, Collections.emptyList()), 
					usersData = whitelist.getList("users", Long.class, Collections.emptyList());
			
			if (channelsData.contains(event.getChannel().getIdLong()) || channelsData.contains(event.getChannel().getParent().getIdLong())) {
				return;
			} else if (usersData.contains(event.getAuthor().getIdLong())) {
				return;
			} else {
				for (Role role : event.getMember().getRoles()) {
					if (rolesData.contains(role.getIdLong())) {
						return;
					}
				}
			}
			
			Invite.resolve(event.getJDA(), inviteMatch.group(1), true).queue(invite -> {
				if (invite.getGuild().getIdLong() == event.getGuild().getIdLong()) {
					return;
				}
				
				String action = data.getString("action");
				
				event.getMessage().delete().queue();
				if (action == null) {
					event.getChannel().sendMessage(event.getAuthor().getAsMention() + ", You are not allowed to send invite links here :no_entry:").queue();
					return;
				} else {
					List<Document> users = data.getList("users", Document.class, Collections.emptyList());
					Document user = null;
					for (Document userData : users) {
						if (userData.getLong("id") == event.getAuthor().getIdLong()) {
							user = userData;
						}
					}
					
					int currentAttempts = user == null ? 0 : user.getInteger("attempts", 0);
					int dataAttempts = data.getInteger("attempts", 3);
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
								event.getGuild().ban(event.getMember(), 1, reason).queue();
								ModUtils.createModLogAndOffence(event.getGuild(), event.getJDA().getSelfUser(), event.getAuthor(), "Ban (Automatic)", reason);
								
								Database.get().updateGuildById(event.getGuild().getIdLong(), Updates.pull("antiinvite.users", Filters.eq("id", event.getAuthor().getIdLong())), (result, exception) -> {
									if (exception != null) {
										exception.printStackTrace();
									}
								});
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
								event.getGuild().kick(event.getMember(), reason).queue();
								ModUtils.createModLogAndOffence(event.getGuild(), event.getJDA().getSelfUser(), event.getAuthor(), "Kick (Automatic)", reason);
								
								Database.get().updateGuildById(event.getGuild().getIdLong(), Updates.pull("antiinvite.users", Filters.eq("id", event.getAuthor().getIdLong())), (result, exception) -> {
									if (exception != null) {
										exception.printStackTrace();
									}
								});
							}
						} else if (action.equals("mute")) {
							if (!event.getGuild().getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
								event.getChannel().sendMessage("I was unable to mute **" + event.getAuthor().getAsTag() + "** as I am missing the `Manage Roles` permission :no_entry:").queue();
								return;
							}
							
							ModUtils.getOrCreateMuteRole(event.getGuild(), (role, error) -> {
								if (error != null) {
									event.getChannel().sendMessage("I was unable to mute **" + event.getAuthor().getAsTag() + "** as " + error + " :no_entry:").queue();
									return;
								}
								
								if (role.getPosition() >= event.getGuild().getSelfMember().getRoles().get(0).getPosition()) {
									event.getChannel().sendMessage("I am unable to mute **" + event.getAuthor().getAsTag() + "** as the mute role is higher or equal than my top role :no_entry:").queue();
									return;
								}
								
								event.getChannel().sendMessage("**" + event.getAuthor().getAsTag() + "** has been muted for sending **" + dataAttempts + "** invite" + (dataAttempts == 1 ? "" : "s") + " <:done:403285928233402378>").queue();
								event.getAuthor().openPrivateChannel().queue(channel -> channel.sendMessage(ModUtils.getMuteEmbed(event.getGuild(), null,  event.getJDA().getSelfUser(), 0, reason)).queue(), e -> {});
								event.getGuild().addRoleToMember(event.getMember(), role).queue();
								ModUtils.createModLogAndOffence(event.getGuild(), event.getJDA().getSelfUser(), event.getAuthor(), "Mute (Automatic)", reason);
								
								Bson update = Updates.combine(
										Updates.set("mute.users.$[user].duration", null),
										Updates.set("mute.users.$[user].timestamp", Clock.systemUTC().instant().getEpochSecond()),
										Updates.pull("antiinvite.users", Filters.eq("id", event.getAuthor().getIdLong()))
								);
								
								UpdateOptions updateOptions = new UpdateOptions().arrayFilters(List.of(Filters.eq("user.id", event.getAuthor().getIdLong()))).upsert(true);
								
								Database.get().updateGuildById(event.getGuild().getIdLong(), null, update, updateOptions, (result, exception) -> {
									if (exception != null) {
										exception.printStackTrace();
									}
								});
							});
						}
					} else {
						event.getChannel().sendMessage(String.format("%s, You are not allowed to send invite links here. If you continue you will receive a %s. **(%d/%d)** :no_entry:", event.getAuthor().getAsMention(), action, currentAttempts + 1, dataAttempts)).queue();
						Bson update = Updates.inc("antiinvite.users.$[user].attempts", 1);
						UpdateOptions updateOptions = new UpdateOptions().arrayFilters(List.of(Filters.eq("user.id", event.getAuthor().getIdLong()))).upsert(true);
						Database.get().updateGuildById(event.getGuild().getIdLong(), null, update, updateOptions, (result, exception) -> {
							if (exception != null) {
								exception.printStackTrace();
							}
						});
					}
				}
			}, e -> {});
		}
	}
	
	public void onGuildMemberJoin(GuildMemberJoinEvent event) {
		Matcher inviteMatch = this.inviteRegex.matcher(event.getMember().getEffectiveName());
		if (inviteMatch.matches()) {
			Document data = Database.get().getGuildById(event.getGuild().getIdLong(), null, Projections.include("antiinvite.banInvites"));
			if (data.isEmpty() || data.getBoolean("baninvites", false) == false) {
				return;
			}
			
			Invite.resolve(event.getJDA(), inviteMatch.group(1), true).queue(invite -> {
				if (invite.getGuild().getIdLong() == event.getGuild().getIdLong()) {
					return;
				}
				
				String reason = "Discord invite in username";
				event.getMember().getUser().openPrivateChannel().queue(channel -> channel.sendMessage(ModUtils.getBanEmbed(event.getGuild(), event.getJDA().getSelfUser(), reason)).queue(), e -> {});
				event.getGuild().ban(event.getMember(), 1, reason).queue();
				ModUtils.createModLogAndOffence(event.getGuild(), event.getJDA().getSelfUser(), event.getMember().getUser(), "Ban (Automatic)", reason);
			});
		}
	}
	
}
