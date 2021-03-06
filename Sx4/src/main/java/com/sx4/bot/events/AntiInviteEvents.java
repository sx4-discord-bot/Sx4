package com.sx4.bot.events;

import com.mongodb.client.model.*;
import com.sx4.bot.database.Database;
import com.sx4.bot.utils.ModUtils;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.entities.Message.MentionType;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AntiInviteEvents extends ListenerAdapter {

	private final Pattern inviteRegex = Pattern.compile(".*discord(?:(?:(?:app)?\\.com|\\.co|\\.media)/invite|\\.gg)/([a-z0-9]{2,32}).*", Pattern.CASE_INSENSITIVE);
	
	public void onGuildMessageReceived(GuildMessageReceivedEvent event) {		
		if (event.getJDA().getSelfUser().equals(event.getAuthor()) || event.isWebhookMessage() || event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
			return;
		}
		
		Matcher inviteMatch = this.inviteRegex.matcher(event.getMessage().getContentRaw());
		if (inviteMatch.matches()) {
			Bson projection = Projections.include("antiinvite.enabled", "antiinvite.whitelist", "antiinvite.users", "antiinvite.action", "antiinvite.attempts", "mute.users");
			Document allData = Database.get().getGuildById(event.getGuild().getIdLong(), null, projection);
			
			Document data = allData.get("antiinvite", Database.EMPTY_DOCUMENT);
			if (!data.getBoolean("enabled", false)) {
				return;
			}
			
			Document whitelist = data.get("whitelist", Database.EMPTY_DOCUMENT);
			List<Long> channelsData = whitelist.getList("channels", Long.class, Collections.emptyList()), 
					rolesData = whitelist.getList("roles", Long.class, Collections.emptyList()), 
					usersData = whitelist.getList("users", Long.class, Collections.emptyList());

			GuildChannel parent = event.getChannel().getParent();
			if (channelsData.contains(event.getChannel().getIdLong()) || (parent != null && channelsData.contains(parent.getIdLong()))) {
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
				Invite.Guild guild = invite.getGuild();
				if (guild != null && guild.getIdLong() == event.getGuild().getIdLong()) {
					return;
				}
				
				String action = data.getString("action");
				
				event.getMessage().delete().queue();
				if (action == null) {
					event.getChannel().sendMessage(event.getAuthor().getAsMention() + ", You are not allowed to send invite links here :no_entry:")
						.allowedMentions(EnumSet.of(MentionType.USER))
						.queue();
					
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
							} else if (!event.getGuild().getSelfMember().canInteract(event.getMember())) {
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
							} else if (!event.getGuild().getSelfMember().canInteract(event.getMember())) {
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
								
								if (!event.getGuild().getSelfMember().canInteract(role)) {
									event.getChannel().sendMessage("I am unable to mute **" + event.getAuthor().getAsTag() + "** as the mute role is higher or equal than my top role :no_entry:").queue();
									return;
								}
								
								event.getChannel().sendMessage("**" + event.getAuthor().getAsTag() + "** has been muted for sending **" + dataAttempts + "** invite" + (dataAttempts == 1 ? "" : "s") + " <:done:403285928233402378>").queue();
								event.getAuthor().openPrivateChannel().queue(channel -> channel.sendMessage(ModUtils.getMuteEmbed(event.getGuild(), null,  event.getJDA().getSelfUser(), 0, reason)).queue(), e -> {});
								event.getGuild().addRoleToMember(event.getMember(), role).queue();
								ModUtils.createModLogAndOffence(event.getGuild(), event.getJDA().getSelfUser(), event.getAuthor(), "Mute (Automatic)", reason);

								UpdateOneModel<Document> muteModel = ModUtils.getMuteUpdate(event.getGuild().getIdLong(), event.getAuthor().getIdLong(), allData.getEmbedded(List.of("mute", "users"), Collections.emptyList()), null);		
								Bson update = Updates.combine(
										muteModel.getUpdate(),
										Updates.pull("antiinvite.users", Filters.eq("id", event.getAuthor().getIdLong()))
								);
								
								
								Database.get().updateGuildById(event.getGuild().getIdLong(), null, update, muteModel.getOptions(), (result, exception) -> {
									if (exception != null) {
										exception.printStackTrace();
									}
								});
							});
						}
					} else {
						event.getChannel().sendMessage(String.format("%s, You are not allowed to send invite links here. If you continue you will receive a %s. **(%d/%d)** :no_entry:", event.getAuthor().getAsMention(), action, currentAttempts + 1, dataAttempts))
							.allowedMentions(EnumSet.of(MentionType.USER))
							.queue();
						
						Bson update = null;
						List<Bson> arrayFilters = null;
						for (Document userData : users) {
							if (userData.getLong("id") == event.getAuthor().getIdLong()) {
								update = Updates.inc("antiinvite.users.$[user].attempts", 1);
								arrayFilters = List.of(Filters.eq("user.id", event.getAuthor().getIdLong()));
							}
						}
						
						if (update == null) {
							update = Updates.push("antiinvite.users", new Document("id", event.getAuthor().getIdLong()).append("attempts", 1));
						}
						
						UpdateOptions updateOptions = new UpdateOptions().arrayFilters(arrayFilters).upsert(true);
						Database.get().updateGuildById(event.getGuild().getIdLong(), null, update, updateOptions, (result, exception) -> {
							if (exception != null) {
								exception.printStackTrace();
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
			Bson projection = Projections.include("antiinvite.enabled", "antiinvite.whitelist", "antiinvite.users", "antiinvite.action", "antiinvite.attempts", "mute.users");
			Document allData = Database.get().getGuildById(event.getGuild().getIdLong(), null, projection);
			
			Document data = allData.get("antiinvite", Database.EMPTY_DOCUMENT);
			if (!data.getBoolean("enabled", false)) {
				return;
			}
			
			Document whitelist = data.get("whitelist", Database.EMPTY_DOCUMENT);
			List<Long> channelsData = whitelist.getList("channels", Long.class, Collections.emptyList()), 
					rolesData = whitelist.getList("roles", Long.class, Collections.emptyList()), 
					usersData = whitelist.getList("users", Long.class, Collections.emptyList());

			GuildChannel parent = event.getChannel().getParent();
			if (channelsData.contains(event.getChannel().getIdLong()) || (parent != null && channelsData.contains(parent.getIdLong()))) {
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
				Invite.Guild guild = invite.getGuild();
				if (guild != null && guild.getIdLong() == event.getGuild().getIdLong()) {
					return;
				}
				
				String action = data.getString("action");
				
				event.getMessage().delete().queue();
				if (action == null) {
					event.getChannel().sendMessage(event.getAuthor().getAsMention() + ", You are not allowed to send invite links here :no_entry:")
						.allowedMentions(EnumSet.of(MentionType.USER))
						.queue();
					
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
							} else if (!event.getGuild().getSelfMember().canInteract(event.getMember())) {
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
							} else if (!event.getGuild().getSelfMember().canInteract(event.getMember())) {
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
								
								if (!event.getGuild().getSelfMember().canInteract(role)) {
									event.getChannel().sendMessage("I am unable to mute **" + event.getAuthor().getAsTag() + "** as the mute role is higher or equal than my top role :no_entry:").queue();
									return;
								}
								
								event.getChannel().sendMessage("**" + event.getAuthor().getAsTag() + "** has been muted for sending **" + dataAttempts + "** invite" + (dataAttempts == 1 ? "" : "s") + " <:done:403285928233402378>").queue();
								event.getAuthor().openPrivateChannel().queue(channel -> channel.sendMessage(ModUtils.getMuteEmbed(event.getGuild(), null,  event.getJDA().getSelfUser(), 0, reason)).queue(), e -> {});
								event.getGuild().addRoleToMember(event.getMember(), role).queue();
								ModUtils.createModLogAndOffence(event.getGuild(), event.getJDA().getSelfUser(), event.getAuthor(), "Mute (Automatic)", reason);
								
								UpdateOneModel<Document> muteModel = ModUtils.getMuteUpdate(event.getGuild().getIdLong(), event.getAuthor().getIdLong(), allData.getEmbedded(List.of("mute", "users"), Collections.emptyList()), null);		
								Bson update = Updates.combine(
										muteModel.getUpdate(),
										Updates.pull("antiinvite.users", Filters.eq("id", event.getAuthor().getIdLong()))
								);
								
								
								Database.get().updateGuildById(event.getGuild().getIdLong(), null, update, muteModel.getOptions(), (result, exception) -> {
									if (exception != null) {
										exception.printStackTrace();
									}
								});
							});
						}
					} else {
						event.getChannel().sendMessage(String.format("%s, You are not allowed to send invite links here. If you continue you will receive a %s. **(%d/%d)** :no_entry:", event.getAuthor().getAsMention(), action, currentAttempts + 1, dataAttempts))
							.allowedMentions(EnumSet.of(MentionType.USER))
							.queue();
						
						Bson update = null;
						List<Bson> arrayFilters = null;
						for (Document userData : users) {
							if (userData.getLong("id") == event.getAuthor().getIdLong()) {
								update = Updates.inc("antiinvite.users.$[user].attempts", 1);
								arrayFilters = List.of(Filters.eq("user.id", event.getAuthor().getIdLong()));
							}
						}
						
						if (update == null) {
							update = Updates.push("antiinvite.users", new Document("id", event.getAuthor().getIdLong()).append("attempts", 1));
						}
						
						UpdateOptions updateOptions = new UpdateOptions().arrayFilters(arrayFilters).upsert(true);
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
