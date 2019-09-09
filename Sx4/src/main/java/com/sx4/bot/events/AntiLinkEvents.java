package com.sx4.bot.events;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.sx4.bot.database.Database;
import com.sx4.bot.utils.ModUtils;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class AntiLinkEvents extends ListenerAdapter {

	private Pattern linkRegex = Pattern.compile(".*(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|].*");
	
	public void onGuildMessageReceived(GuildMessageReceivedEvent event) {		
		if (event.getJDA().getSelfUser().equals(event.getAuthor()) || event.isWebhookMessage() || event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
			return;
		}
		
		Matcher linkMatch = this.linkRegex.matcher(event.getMessage().getContentRaw());
		if (linkMatch.matches()) {
			Bson projection = Projections.include("antilink.enabled", "antilink.whitelist", "antilink.users", "antilink.action", "antilink.attempts", "mute.users");
			Document allData = Database.get().getGuildById(event.getGuild().getIdLong(), null, projection);
			
			Document data = allData.get("antilink", Database.EMPTY_DOCUMENT);
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
			
			String action = data.getString("action");
				
			event.getMessage().delete().queue();
			if (action == null) {
				event.getChannel().sendMessage(event.getAuthor().getAsMention() + ", You are not allowed to send links here :no_entry:").queue();
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
					String reason = "Sent " + dataAttempts + " link" + (dataAttempts == 1 ? "" : "s");
					if (action.equals("ban")) {
						if (!event.getGuild().getSelfMember().hasPermission(Permission.BAN_MEMBERS)) {
							event.getChannel().sendMessage("I was unable to ban **" + event.getAuthor().getAsTag() + "** as I am missing the `Ban Members` permission :no_entry:").queue();
							return;
						} else if (event.getGuild().getSelfMember().getRoles().get(0).getPosition() <= event.getMember().getRoles().get(0).getPosition()) {
							event.getChannel().sendMessage("I was unable to ban **" + event.getAuthor().getAsTag() + "** as their top role is higher or equal than my top role :no_entry:").queue();
							return;
						} else {
							event.getChannel().sendMessage("**" + event.getAuthor().getAsTag() + "** has been banned for sending **" + dataAttempts + "** link" + (dataAttempts == 1 ? "" : "s") + " <:done:403285928233402378>").queue();
							event.getAuthor().openPrivateChannel().queue(channel -> channel.sendMessage(ModUtils.getBanEmbed(event.getGuild(), event.getJDA().getSelfUser(), reason)).queue(), e -> {});
							event.getGuild().ban(event.getMember(), 1, reason).queue();
							ModUtils.createModLogAndOffence(event.getGuild(), event.getJDA().getSelfUser(), event.getAuthor(), "Ban (Automatic)", reason);
							
							Database.get().updateGuildById(event.getGuild().getIdLong(), Updates.pull("antilink.users", Filters.eq("id", event.getAuthor().getIdLong())), (result, exception) -> {
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
							event.getChannel().sendMessage("**" + event.getAuthor().getAsTag() + "** has been kicked for sending **" + dataAttempts + "** link" + (dataAttempts == 1 ? "" : "s") + " <:done:403285928233402378>").queue();
							event.getAuthor().openPrivateChannel().queue(channel -> channel.sendMessage(ModUtils.getKickEmbed(event.getGuild(), event.getJDA().getSelfUser(), reason)).queue(), e -> {});
							event.getGuild().kick(event.getMember(), reason).queue();
							ModUtils.createModLogAndOffence(event.getGuild(), event.getJDA().getSelfUser(), event.getAuthor(), "Kick (Automatic)", reason);
							
							Database.get().updateGuildById(event.getGuild().getIdLong(), Updates.pull("antilink.users", Filters.eq("id", event.getAuthor().getIdLong())), (result, exception) -> {
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
							
							event.getChannel().sendMessage("**" + event.getAuthor().getAsTag() + "** has been muted for sending **" + dataAttempts + "** link" + (dataAttempts == 1 ? "" : "s") + " <:done:403285928233402378>").queue();
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
					event.getChannel().sendMessage(String.format("%s, You are not allowed to send links here. If you continue you will receive a %s. **(%d/%d)** :no_entry:", event.getAuthor().getAsMention(), action, currentAttempts + 1, dataAttempts)).queue();
					
					Bson update = null;
					List<Bson> arrayFilters = null;
					for (Document userData : users) {
						if (userData.getLong("id") == event.getAuthor().getIdLong()) {
							update = Updates.inc("antilink.users.$[user].attempts", 1);
							arrayFilters = List.of(Filters.eq("user.id", event.getAuthor().getIdLong()));
						}
					}
					
					if (update == null) {
						update = Updates.push("antilink.users", new Document("id", event.getAuthor().getIdLong()).append("attempts", 1));
					}
					
					UpdateOptions updateOptions = new UpdateOptions().arrayFilters(arrayFilters).upsert(true);
					Database.get().updateGuildById(event.getGuild().getIdLong(), null, update, updateOptions, (result, exception) -> {
						if (exception != null) {
							exception.printStackTrace();
						}
					});
				}
			}
		}
	}
	
	public void onGuildMessageUpdate(GuildMessageUpdateEvent event) {	
		if (event.getJDA().getSelfUser().equals(event.getAuthor()) || event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
			return;
		}
		
		Matcher linkMatch = this.linkRegex.matcher(event.getMessage().getContentRaw());
		if (linkMatch.matches()) {
			Bson projection = Projections.include("antilink.enabled", "antilink.whitelist", "antilink.users", "antilink.action", "antilink.attempts", "mute.users");
			Document allData = Database.get().getGuildById(event.getGuild().getIdLong(), null, projection);
			
			Document data = allData.get("antilink", Database.EMPTY_DOCUMENT);
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
			
			String action = data.getString("action");
				
			event.getMessage().delete().queue();
			if (action == null) {
				event.getChannel().sendMessage(event.getAuthor().getAsMention() + ", You are not allowed to send links here :no_entry:").queue();
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
					String reason = "Sent " + dataAttempts + " link" + (dataAttempts == 1 ? "" : "s");
					if (action.equals("ban")) {
						if (!event.getGuild().getSelfMember().hasPermission(Permission.BAN_MEMBERS)) {
							event.getChannel().sendMessage("I was unable to ban **" + event.getAuthor().getAsTag() + "** as I am missing the `Ban Members` permission :no_entry:").queue();
							return;
						} else if (event.getGuild().getSelfMember().getRoles().get(0).getPosition() <= event.getMember().getRoles().get(0).getPosition()) {
							event.getChannel().sendMessage("I was unable to ban **" + event.getAuthor().getAsTag() + "** as their top role is higher or equal than my top role :no_entry:").queue();
							return;
						} else {
							event.getChannel().sendMessage("**" + event.getAuthor().getAsTag() + "** has been banned for sending **" + dataAttempts + "** link" + (dataAttempts == 1 ? "" : "s") + " <:done:403285928233402378>").queue();
							event.getAuthor().openPrivateChannel().queue(channel -> channel.sendMessage(ModUtils.getBanEmbed(event.getGuild(), event.getJDA().getSelfUser(), reason)).queue(), e -> {});
							event.getGuild().ban(event.getMember(), 1, reason).queue();
							ModUtils.createModLogAndOffence(event.getGuild(), event.getJDA().getSelfUser(), event.getAuthor(), "Ban (Automatic)", reason);
							
							Database.get().updateGuildById(event.getGuild().getIdLong(), Updates.pull("antilink.users", Filters.eq("id", event.getAuthor().getIdLong())), (result, exception) -> {
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
							event.getChannel().sendMessage("**" + event.getAuthor().getAsTag() + "** has been kicked for sending **" + dataAttempts + "** link" + (dataAttempts == 1 ? "" : "s") + " <:done:403285928233402378>").queue();
							event.getAuthor().openPrivateChannel().queue(channel -> channel.sendMessage(ModUtils.getKickEmbed(event.getGuild(), event.getJDA().getSelfUser(), reason)).queue(), e -> {});
							event.getGuild().kick(event.getMember(), reason).queue();
							ModUtils.createModLogAndOffence(event.getGuild(), event.getJDA().getSelfUser(), event.getAuthor(), "Kick (Automatic)", reason);
							
							Database.get().updateGuildById(event.getGuild().getIdLong(), Updates.pull("antilink.users", Filters.eq("id", event.getAuthor().getIdLong())), (result, exception) -> {
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
							
							event.getChannel().sendMessage("**" + event.getAuthor().getAsTag() + "** has been muted for sending **" + dataAttempts + "** link" + (dataAttempts == 1 ? "" : "s") + " <:done:403285928233402378>").queue();
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
					event.getChannel().sendMessage(String.format("%s, You are not allowed to send links here. If you continue you will receive a %s. **(%d/%d)** :no_entry:", event.getAuthor().getAsMention(), action, currentAttempts + 1, dataAttempts)).queue();
					
					Bson update = null;
					List<Bson> arrayFilters = null;
					for (Document userData : users) {
						if (userData.getLong("id") == event.getAuthor().getIdLong()) {
							update = Updates.inc("antilink.users.$[user].attempts", 1);
							arrayFilters = List.of(Filters.eq("user.id", event.getAuthor().getIdLong()));
						}
					}
					
					if (update == null) {
						update = Updates.push("antilink.users", new Document("id", event.getAuthor().getIdLong()).append("attempts", 1));
					}
					
					UpdateOptions updateOptions = new UpdateOptions().arrayFilters(arrayFilters).upsert(true);
					Database.get().updateGuildById(event.getGuild().getIdLong(), null, update, updateOptions, (result, exception) -> {
						if (exception != null) {
							exception.printStackTrace();
						}
					});
				}
			}
		}
	}
	
}
