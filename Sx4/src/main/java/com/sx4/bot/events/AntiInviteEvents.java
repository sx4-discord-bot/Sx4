package com.sx4.bot.events;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import com.sx4.bot.database.Database;
import com.sx4.bot.utils.ModAction;
import com.sx4.bot.utils.ModUtils;
import com.sx4.bot.utils.TimeUtils;
import com.sx4.bot.utils.WarnUtils;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class AntiInviteEvents extends ListenerAdapter {

	private final Pattern inviteRegex = Pattern.compile("(?:.|\n)*(?:https?://)?(?:www.)?(?:discord.gg|(?:canary.)?discordapp.com/invite)/((?:[a-zA-Z0-9\\-]){2,32})(?:.|\n)*", Pattern.CASE_INSENSITIVE);	
	
	public void onGuildMessageReceived(GuildMessageReceivedEvent event) {		
		if (event.getJDA().getSelfUser().equals(event.getAuthor()) || event.isWebhookMessage() || event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
			return;
		}
		
		Matcher inviteMatch = this.inviteRegex.matcher(event.getMessage().getContentRaw());
		if (inviteMatch.matches()) {
			Bson projection = Projections.include("antiinvite.enabled", "antiinvite.whitelist", "antiinvite.users", "antiinvite.action", "antiinvite.attempts", "mute.users", "mute.role", "warn.users", "warn.configuration");
			Document allData = Database.get().getGuildById(event.getGuild().getIdLong(), null, projection);
			Document muteData = allData.get("mute", Database.EMPTY_DOCUMENT);
			
			Document data = allData.get("antiinvite", Database.EMPTY_DOCUMENT);
			if (!data.getBoolean("enabled", false)) {
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
				
				Document actionData = data.get("action", Document.class);
				
				event.getMessage().delete().queue();
				if (actionData == null) {
					event.getChannel().sendMessage(event.getAuthor().getAsMention() + ", You are not allowed to send invite links here :no_entry:").queue();
					return;
				} else {
					ModAction action = ModAction.getFromType(actionData.getInteger("type"));
					Long duration = actionData.getLong("duration");
					
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
						
						List<WriteModel<Document>> bulkData = new ArrayList<>();
						
						Long roleId = muteData.getLong("roleId");
						Role role = roleId == null ? null : event.getGuild().getRoleById(roleId);
						switch (action) {
							case WARN:
								WarnUtils.handleWarning(allData, event.getGuild(), event.getMember(), event.getGuild().getSelfMember(), reason, (warning, exception) -> {
									if (exception != null) {
										exception.printStackTrace();
										event.getChannel().sendMessage(exception.getMessage() + " :no_entry:").queue();
										return;
									} else {
										Document warnData = allData.get("warn", Database.EMPTY_DOCUMENT);
										
										List<Document> warnConfiguration = warnData.getList("configuration", Document.class, Collections.emptyList());
										if (warnConfiguration.isEmpty()) {
											warnConfiguration = ModUtils.DEFAULT_WARN_CONFIGURATION;
										}
										
										Long muteDuration = warning.getDuration();
										
										if (warning.getAction() == ModAction.MUTE) {
											bulkData.add(ModUtils.getMuteUpdate(event.getGuild().getIdLong(), event.getMember().getIdLong(), users, muteDuration));
										} else if (warning.getAction() == ModAction.MUTE_EXTEND) {
											bulkData.add(ModUtils.getMuteUpdate(event.getGuild().getIdLong(), event.getMember().getIdLong(), users, muteDuration, true));
										}
										
										event.getChannel().sendMessage("**" + event.getAuthor().getAsTag() + "** has received a " + warning.getAction().getName() + (warning.getDuration() == null ? "" : " for " + TimeUtils.toTimeString(warning.getDuration(), ChronoUnit.SECONDS)) + " for sending **" + dataAttempts + "** invite" + (dataAttempts == 1 ? "" : "s") + " <:done:403285928233402378>").queue();
										
										bulkData.add(WarnUtils.getUserUpdate(warnData.getList("users", Document.class, Collections.emptyList()), warnConfiguration, event.getGuild().getIdLong(), event.getMember().getIdLong(), reason));
										bulkData.add(new UpdateOneModel<>(Filters.eq("_id", event.getGuild().getIdLong()), Updates.pull("antiinvite.users", Filters.eq("id", event.getAuthor().getIdLong()))));
										
										Database.get().bulkWriteGuilds(bulkData, (result, writeException) -> {
											if (writeException != null) {
												writeException.printStackTrace();
											}
										});
									}
								});
								
								return;
							case MUTE:
								if (role != null) {
									if (!event.getGuild().getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
										event.getChannel().sendMessage("I was unable to mute **" + event.getAuthor().getAsTag() + "** as I am missing the `Manage Roles` permission :no_entry:").queue();
										return;
									} else if (!event.getGuild().getSelfMember().canInteract(role)) {
										event.getChannel().sendMessage("I was unable to mute **" + event.getAuthor().getAsTag() + "** as the mute role is higher or equal than my top role :no_entry:").queue();
										return;
									} else {
										if (duration != null) {
											UpdateOptions options = new UpdateOptions().arrayFilters(List.of(Filters.eq("user.id", event.getMember().getIdLong())));
											bulkData.add(new UpdateOneModel<>(Filters.eq("_id", event.getGuild().getIdLong()), Updates.set("mute.users.$[user].duration", duration), options));
											
											MuteEvents.cancelExecutor(event.getGuild().getIdLong(), event.getMember().getIdLong());
													
											ScheduledFuture<?> executor = MuteEvents.scheduledExectuor.schedule(() -> MuteEvents.removeUserMute(event.getGuild().getIdLong(), event.getMember().getIdLong(), roleId), duration, TimeUnit.SECONDS);
											MuteEvents.putExecutor(event.getGuild().getIdLong(), event.getMember().getIdLong(), executor);
										}
									}
									
									event.getGuild().addRoleToMember(event.getMember(), role).queue($ -> {
										String timeString = duration != null ? TimeUtils.toTimeString(duration, ChronoUnit.SECONDS) : "Infinite";
										
										event.getChannel().sendMessage("**" + event.getAuthor().getAsTag() + "** has received a mute" + (duration == null ? "" : " for " + timeString) + " for sending **" + dataAttempts + "** invite" + (dataAttempts == 1 ? "" : "s") + " <:done:403285928233402378>").queue();
										event.getAuthor().openPrivateChannel().queue(channel -> channel.sendMessage(ModUtils.getMuteEmbed(event.getGuild(), null, event.getJDA().getSelfUser(), duration == null ? 0 : duration, reason)).queue(), e -> {});
										ModUtils.createModLogAndOffence(event.getGuild(), event.getJDA().getSelfUser(), event.getAuthor(), "Mute (" + timeString + ")", reason);
									});
									
									bulkData.add(new UpdateOneModel<>(Filters.eq("_id", event.getGuild().getIdLong()), Updates.pull("antiinvite.users", Filters.eq("id", event.getAuthor().getIdLong()))));
								} else {
									event.getChannel().sendMessage("I was unable to mute **" + event.getAuthor().getAsTag() + "** due to there being no mute role :no_entry:").queue();
									return;
								}
								
								break;
							case MUTE_EXTEND:
								if (role != null) {
									if (!event.getGuild().getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
										event.getChannel().sendMessage("I was unable to mute **" + event.getAuthor().getAsTag() + "** as I am missing the `Manage Roles` permission :no_entry:").queue();
										return;
									} else if (!event.getGuild().getSelfMember().canInteract(role)) {
										event.getChannel().sendMessage("I was unable to mute **" + event.getAuthor().getAsTag() + "** as the mute role is higher or equal than my top role :no_entry:").queue();
										return;
									} else {
										if (duration != null) {
											UpdateOptions options = new UpdateOptions().arrayFilters(List.of(Filters.eq("user.id", event.getMember().getIdLong())));
											bulkData.add(new UpdateOneModel<>(Filters.eq("_id", event.getGuild().getIdLong()), Updates.inc("mute.users.$[user].duration", duration), options));
												
											ScheduledFuture<?> currentExecutor = MuteEvents.getExecutor(event.getGuild().getIdLong(), event.getMember().getIdLong());
											
											long delay = duration;
											if (currentExecutor != null && !currentExecutor.isDone()) {
												currentExecutor.cancel(true);
												delay += currentExecutor.getDelay(TimeUnit.SECONDS);
											}
											
											ScheduledFuture<?> executor = MuteEvents.scheduledExectuor.schedule(() -> MuteEvents.removeUserMute(event.getGuild().getIdLong(), event.getMember().getIdLong(), roleId), delay, TimeUnit.SECONDS);
											MuteEvents.putExecutor(event.getGuild().getIdLong(), event.getMember().getIdLong(), executor);
										}
									} 
									
									event.getGuild().addRoleToMember(event.getMember(), role).queue($ -> {
										String timeString = duration != null ? TimeUtils.toTimeString(duration, ChronoUnit.SECONDS) : "Infinite";
										
										event.getChannel().sendMessage("**" + event.getAuthor().getAsTag() + "** has received a mute extension" + (duration == null ? "" : " for " + TimeUtils.toTimeString(duration, ChronoUnit.SECONDS)) + " for sending **" + dataAttempts + "** invite" + (dataAttempts == 1 ? "" : "s") + " <:done:403285928233402378>").queue();
										event.getAuthor().openPrivateChannel().queue(channel -> channel.sendMessage(ModUtils.getMuteEmbed(event.getGuild(), null, event.getJDA().getSelfUser(), duration == null ? 0 : duration, reason)).queue(), e -> {});
										ModUtils.createModLogAndOffence(event.getGuild(), event.getJDA().getSelfUser(), event.getAuthor(), "Mute Extension (" + timeString + ")", reason);
									});
									
									bulkData.add(new UpdateOneModel<>(Filters.eq("_id", event.getGuild().getIdLong()), Updates.pull("antiinvite.users", Filters.eq("id", event.getAuthor().getIdLong()))));
								} else {
									event.getChannel().sendMessage("I was unable to mute **" + event.getAuthor().getAsTag() + "** due to there being no mute role :no_entry:").queue();
									return;
								}
								
								break;
							case KICK:
								if (!event.getGuild().getSelfMember().hasPermission(Permission.KICK_MEMBERS)) {
									event.getChannel().sendMessage("I was unable to kick **" + event.getAuthor().getAsTag() + "** as I am missing the `Kick Members` permission :no_entry:").queue();
									return;
								} else if (!event.getGuild().getSelfMember().canInteract(event.getMember())) {
									event.getChannel().sendMessage("I was unable to kick **" + event.getAuthor().getAsTag() + "** as their top role is higher or equal than my top role :no_entry:").queue();
									return;
								} else {
									event.getMember().kick().queue($ -> {
										event.getChannel().sendMessage("**" + event.getAuthor().getAsTag() + "** has been kicked for sending **" + dataAttempts + "** invite" + (dataAttempts == 1 ? "" : "s") + " <:done:403285928233402378>").queue();
										event.getAuthor().openPrivateChannel().queue(channel -> channel.sendMessage(ModUtils.getKickEmbed(event.getGuild(), event.getJDA().getSelfUser(), reason)).queue(), e -> {});
										ModUtils.createModLogAndOffence(event.getGuild(), event.getJDA().getSelfUser(), event.getAuthor(), "Kick", reason);
									});
									
									bulkData.add(new UpdateOneModel<>(Filters.eq("_id", event.getGuild().getIdLong()), Updates.pull("antiinvite.users", Filters.eq("id", event.getAuthor().getIdLong()))));
								}
								
								break;
							case BAN:
								if (!event.getGuild().getSelfMember().hasPermission(Permission.BAN_MEMBERS)) {
									event.getChannel().sendMessage("I was unable to ban **" + event.getAuthor().getAsTag() + "** as I am missing the `Ban Members` permission :no_entry:").queue();
									return;
								} else if (!event.getGuild().getSelfMember().canInteract(event.getMember())) {
									event.getChannel().sendMessage("I was unable to ban **" + event.getAuthor().getAsTag() + "** as their top role is higher or equal than my top role :no_entry:").queue();
									return;
								} else {
									event.getGuild().ban(event.getMember(), 1, reason).queue($ -> {
										event.getChannel().sendMessage("**" + event.getAuthor().getAsTag() + "** has been banned for sending **" + dataAttempts + "** invite" + (dataAttempts == 1 ? "" : "s") + " <:done:403285928233402378>").queue();
										event.getAuthor().openPrivateChannel().queue(channel -> channel.sendMessage(ModUtils.getBanEmbed(event.getGuild(), event.getJDA().getSelfUser(), reason)).queue(), e -> {});
										ModUtils.createModLogAndOffence(event.getGuild(), event.getJDA().getSelfUser(), event.getAuthor(), "Ban (Automatic)", reason);
									});
									
									bulkData.add(new UpdateOneModel<>(Filters.eq("_id", event.getGuild().getIdLong()), Updates.pull("antiinvite.users", Filters.eq("id", event.getAuthor().getIdLong()))));
								}
								
								break;
						}
						
						Database.get().bulkWriteGuilds(bulkData, (result, writeException) -> {
							if (writeException != null) {
								writeException.printStackTrace();
							}
						});
					}
				}
			}, e -> {e.printStackTrace();});
		}
	}

	public void onGuildMessageUpdate(GuildMessageUpdateEvent event) {	
		if (event.getJDA().getSelfUser().equals(event.getAuthor()) || event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
			return;
		}
		
		Matcher inviteMatch = this.inviteRegex.matcher(event.getMessage().getContentRaw());
		if (inviteMatch.matches()) {
			Bson projection = Projections.include("antiinvite.enabled", "antiinvite.whitelist", "antiinvite.users", "antiinvite.action", "antiinvite.attempts", "mute.users", "mute.role", "warn.users", "warn.configuration");
			Document allData = Database.get().getGuildById(event.getGuild().getIdLong(), null, projection);
			Document muteData = allData.get("mute", Database.EMPTY_DOCUMENT);
			
			Document data = allData.get("antiinvite", Database.EMPTY_DOCUMENT);
			if (!data.getBoolean("enabled", false)) {
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
				
				Document actionData = data.get("action", Document.class);
				
				event.getMessage().delete().queue();
				if (actionData == null) {
					event.getChannel().sendMessage(event.getAuthor().getAsMention() + ", You are not allowed to send invite links here :no_entry:").queue();
					return;
				} else {
					ModAction action = ModAction.getFromType(actionData.getInteger("type"));
					Long duration = actionData.getLong("duration");
					
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
						
						List<WriteModel<Document>> bulkData = new ArrayList<>();
						
						Long roleId = muteData.getLong("roleId");
						Role role = roleId == null ? null : event.getGuild().getRoleById(roleId);
						
						switch (action) {
							case WARN:
								WarnUtils.handleWarning(allData, event.getGuild(), event.getMember(), event.getGuild().getSelfMember(), reason, (warning, exception) -> {
									if (exception != null) {
										exception.printStackTrace();
										event.getChannel().sendMessage(exception.getMessage() + " :no_entry:").queue();
										return;
									} else {
										Document warnData = allData.get("warn", Database.EMPTY_DOCUMENT);
										
										List<Document> warnConfiguration = warnData.getList("configuration", Document.class, Collections.emptyList());
										if (warnConfiguration.isEmpty()) {
											warnConfiguration = ModUtils.DEFAULT_WARN_CONFIGURATION;
										}
										
										Long muteDuration = warning.getDuration();
										
										if (warning.getAction() == ModAction.MUTE) {
											bulkData.add(ModUtils.getMuteUpdate(event.getGuild().getIdLong(), event.getMember().getIdLong(), users, muteDuration));
										} else if (warning.getAction() == ModAction.MUTE_EXTEND) {
											bulkData.add(ModUtils.getMuteUpdate(event.getGuild().getIdLong(), event.getMember().getIdLong(), users, muteDuration, true));
										}
										
										event.getChannel().sendMessage("**" + event.getAuthor().getAsTag() + "** has received a " + warning.getAction().getName() + (warning.getDuration() == null ? "" : " for " + TimeUtils.toTimeString(warning.getDuration(), ChronoUnit.SECONDS)) + " for sending **" + dataAttempts + "** invite" + (dataAttempts == 1 ? "" : "s") + " <:done:403285928233402378>").queue();
										
										bulkData.add(WarnUtils.getUserUpdate(warnData.getList("users", Document.class, Collections.emptyList()), warnConfiguration, event.getGuild().getIdLong(), event.getMember().getIdLong(), reason));
										bulkData.add(new UpdateOneModel<>(Filters.eq("_id", event.getGuild().getIdLong()), Updates.pull("antiinvite.users", Filters.eq("id", event.getAuthor().getIdLong()))));

										Database.get().bulkWriteGuilds(bulkData, (result, writeException) -> {
											if (writeException != null) {
												writeException.printStackTrace();
											}
										});
									}
								});
								
								return;
							case MUTE:
								if (role != null) {
									if (!event.getGuild().getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
										event.getChannel().sendMessage("I was unable to mute **" + event.getAuthor().getAsTag() + "** as I am missing the `Manage Roles` permission :no_entry:").queue();
										return;
									} else if (!event.getGuild().getSelfMember().canInteract(role)) {
										event.getChannel().sendMessage("I was unable to mute **" + event.getAuthor().getAsTag() + "** as the mute role is higher or equal than my top role :no_entry:").queue();
										return;
									} else {
										if (duration != null) {
											UpdateOptions options = new UpdateOptions().arrayFilters(List.of(Filters.eq("user.id", event.getMember().getIdLong())));
											bulkData.add(new UpdateOneModel<>(Filters.eq("_id", event.getGuild().getIdLong()), Updates.set("mute.users.$[user].duration", duration), options));
											
											MuteEvents.cancelExecutor(event.getGuild().getIdLong(), event.getMember().getIdLong());
													
											ScheduledFuture<?> executor = MuteEvents.scheduledExectuor.schedule(() -> MuteEvents.removeUserMute(event.getGuild().getIdLong(), event.getMember().getIdLong(), roleId), duration, TimeUnit.SECONDS);
											MuteEvents.putExecutor(event.getGuild().getIdLong(), event.getMember().getIdLong(), executor);
										}
									}
									
									event.getGuild().addRoleToMember(event.getMember(), role).queue($ -> {
										String timeString = duration != null ? TimeUtils.toTimeString(duration, ChronoUnit.SECONDS) : "Infinite";
										
										event.getChannel().sendMessage("**" + event.getAuthor().getAsTag() + "** has received a mute" + (duration == null ? "" : " for " + timeString) + " for sending **" + dataAttempts + "** invite" + (dataAttempts == 1 ? "" : "s") + " <:done:403285928233402378>").queue();
										event.getAuthor().openPrivateChannel().queue(channel -> channel.sendMessage(ModUtils.getMuteEmbed(event.getGuild(), null, event.getJDA().getSelfUser(), duration == null ? 0 : duration, reason)).queue(), e -> {});
										ModUtils.createModLogAndOffence(event.getGuild(), event.getJDA().getSelfUser(), event.getAuthor(), "Mute (" + timeString + ")", reason);
									});
									
									bulkData.add(new UpdateOneModel<>(Filters.eq("_id", event.getGuild().getIdLong()), Updates.pull("antiinvite.users", Filters.eq("id", event.getAuthor().getIdLong()))));
								} else {
									event.getChannel().sendMessage("I was unable to mute **" + event.getAuthor().getAsTag() + "** due to there being no mute role :no_entry:").queue();
									return;
								}
								
								break;
							case MUTE_EXTEND:
								if (role != null) {
									if (!event.getGuild().getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
										event.getChannel().sendMessage("I was unable to mute **" + event.getAuthor().getAsTag() + "** as I am missing the `Manage Roles` permission :no_entry:").queue();
										return;
									} else if (!event.getGuild().getSelfMember().canInteract(role)) {
										event.getChannel().sendMessage("I was unable to mute **" + event.getAuthor().getAsTag() + "** as the mute role is higher or equal than my top role :no_entry:").queue();
										return;
									} else {
										if (duration != null) {
											UpdateOptions options = new UpdateOptions().arrayFilters(List.of(Filters.eq("user.id", event.getMember().getIdLong())));
											bulkData.add(new UpdateOneModel<>(Filters.eq("_id", event.getGuild().getIdLong()), Updates.inc("mute.users.$[user].duration", duration), options));
												
											ScheduledFuture<?> currentExecutor = MuteEvents.getExecutor(event.getGuild().getIdLong(), event.getMember().getIdLong());
											
											long delay = duration;
											if (currentExecutor != null && !currentExecutor.isDone()) {
												currentExecutor.cancel(true);
												delay += currentExecutor.getDelay(TimeUnit.SECONDS);
											}
											
											ScheduledFuture<?> executor = MuteEvents.scheduledExectuor.schedule(() -> MuteEvents.removeUserMute(event.getGuild().getIdLong(), event.getMember().getIdLong(), roleId), delay, TimeUnit.SECONDS);
											MuteEvents.putExecutor(event.getGuild().getIdLong(), event.getMember().getIdLong(), executor);
										}
									} 
									
									event.getGuild().addRoleToMember(event.getMember(), role).queue($ -> {
										String timeString = duration != null ? TimeUtils.toTimeString(duration, ChronoUnit.SECONDS) : "Infinite";
										
										event.getChannel().sendMessage("**" + event.getAuthor().getAsTag() + "** has received a mute extension" + (duration == null ? "" : " for " + TimeUtils.toTimeString(duration, ChronoUnit.SECONDS)) + " for sending **" + dataAttempts + "** invite" + (dataAttempts == 1 ? "" : "s") + " <:done:403285928233402378>").queue();
										event.getAuthor().openPrivateChannel().queue(channel -> channel.sendMessage(ModUtils.getMuteEmbed(event.getGuild(), null, event.getJDA().getSelfUser(), duration == null ? 0 : duration, reason)).queue(), e -> {});
										ModUtils.createModLogAndOffence(event.getGuild(), event.getJDA().getSelfUser(), event.getAuthor(), "Mute Extension (" + timeString + ")", reason);
									});
									
									bulkData.add(new UpdateOneModel<>(Filters.eq("_id", event.getGuild().getIdLong()), Updates.pull("antiinvite.users", Filters.eq("id", event.getAuthor().getIdLong()))));
								} else {
									event.getChannel().sendMessage("I was unable to mute **" + event.getAuthor().getAsTag() + "** due to there being no mute role :no_entry:").queue();
									return;
								}
								
								break;
							case KICK:
								if (!event.getGuild().getSelfMember().hasPermission(Permission.KICK_MEMBERS)) {
									event.getChannel().sendMessage("I was unable to kick **" + event.getAuthor().getAsTag() + "** as I am missing the `Kick Members` permission :no_entry:").queue();
									return;
								} else if (!event.getGuild().getSelfMember().canInteract(event.getMember())) {
									event.getChannel().sendMessage("I was unable to kick **" + event.getAuthor().getAsTag() + "** as their top role is higher or equal than my top role :no_entry:").queue();
									return;
								} else {
									event.getMember().kick().queue($ -> {
										event.getChannel().sendMessage("**" + event.getAuthor().getAsTag() + "** has been kicked for sending **" + dataAttempts + "** invite" + (dataAttempts == 1 ? "" : "s") + " <:done:403285928233402378>").queue();
										event.getAuthor().openPrivateChannel().queue(channel -> channel.sendMessage(ModUtils.getKickEmbed(event.getGuild(), event.getJDA().getSelfUser(), reason)).queue(), e -> {});
										ModUtils.createModLogAndOffence(event.getGuild(), event.getJDA().getSelfUser(), event.getAuthor(), "Kick", reason);
									});
									
									bulkData.add(new UpdateOneModel<>(Filters.eq("_id", event.getGuild().getIdLong()), Updates.pull("antiinvite.users", Filters.eq("id", event.getAuthor().getIdLong()))));
								}
								
								break;
							case BAN:
								if (!event.getGuild().getSelfMember().hasPermission(Permission.BAN_MEMBERS)) {
									event.getChannel().sendMessage("I was unable to ban **" + event.getAuthor().getAsTag() + "** as I am missing the `Ban Members` permission :no_entry:").queue();
									return;
								} else if (!event.getGuild().getSelfMember().canInteract(event.getMember())) {
									event.getChannel().sendMessage("I was unable to ban **" + event.getAuthor().getAsTag() + "** as their top role is higher or equal than my top role :no_entry:").queue();
									return;
								} else {
									event.getGuild().ban(event.getMember(), 1, reason).queue($ -> {
										event.getChannel().sendMessage("**" + event.getAuthor().getAsTag() + "** has been banned for sending **" + dataAttempts + "** invite" + (dataAttempts == 1 ? "" : "s") + " <:done:403285928233402378>").queue();
										event.getAuthor().openPrivateChannel().queue(channel -> channel.sendMessage(ModUtils.getBanEmbed(event.getGuild(), event.getJDA().getSelfUser(), reason)).queue(), e -> {});
										ModUtils.createModLogAndOffence(event.getGuild(), event.getJDA().getSelfUser(), event.getAuthor(), "Ban (Automatic)", reason);
									});
									
									bulkData.add(new UpdateOneModel<>(Filters.eq("_id", event.getGuild().getIdLong()), Updates.pull("antiinvite.users", Filters.eq("id", event.getAuthor().getIdLong()))));
								}
								
								break;
						}
						
						Database.get().bulkWriteGuilds(bulkData, (result, writeException) -> {
							if (writeException != null) {
								writeException.printStackTrace();
							}
						});
					}
				}
			}, e -> {e.printStackTrace();});
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
