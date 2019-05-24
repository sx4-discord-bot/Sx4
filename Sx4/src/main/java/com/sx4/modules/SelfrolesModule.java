package com.sx4.modules;

import static com.rethinkdb.RethinkDB.r;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jockie.bot.core.Context;
import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.command.Command.AuthorPermissions;
import com.jockie.bot.core.command.Command.BotPermissions;
import com.jockie.bot.core.command.ICommand.ContentOverflowPolicy;
import com.jockie.bot.core.command.Initialize;
import com.jockie.bot.core.command.impl.CommandEvent;
import com.jockie.bot.core.command.impl.CommandImpl;
import com.jockie.bot.core.module.Module;
import com.rethinkdb.gen.ast.Get;
import com.rethinkdb.model.OptArgs;
import com.rethinkdb.net.Connection;
import com.sx4.categories.Categories;
import com.sx4.core.Sx4Command;
import com.sx4.utils.ArgumentUtils;
import com.sx4.utils.HelpUtils;
import com.sx4.utils.PagedUtils;
import com.sx4.utils.PagedUtils.PagedResult;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Emote;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.entities.MessageReaction;
import net.dv8tion.jda.core.entities.Role;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.exceptions.ErrorResponseException;

@Module
public class SelfrolesModule {
	
	public class ReactionRoleCommand extends Sx4Command {
		
		private List<String> nullStrings = List.of("off", "none", "null", "reset");
		
		private boolean isBotMenu(Message message, Map<String, Object> data) {
			if (data.containsKey("bot_menu")) {
				return (boolean) data.get("bot_menu");
			} else {
				if (message.getAuthor().equals(message.getJDA().getSelfUser()) && !message.getEmbeds().isEmpty()) {
					return true;
				}
			}
			
			return false;
		}
		
		public ReactionRoleCommand() {
			super("reaction role");
			
			super.setDescription("Set up a reaction role so users can simply react to an emote and get a specified role");
			super.setAliases("reactionrole");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="create", description="Create a base menu for the reaction role, this is used if you don't have a custom message to use")
		@AuthorPermissions({Permission.MANAGE_ROLES})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void create(CommandEvent event, @Context Connection connection, @Argument(value="channel") String channelArgument, @Argument(value="title", endless=true, nullDefault=true) String title) {
			r.table("reactionrole").insert(r.hashMap("id", event.getGuild().getId()).with("dm", true).with("messages", new Object[0])).run(connection, OptArgs.of("durability", "soft"));
			Get data = r.table("reactionrole").get(event.getGuild().getId());
			
			TextChannel channel = ArgumentUtils.getTextChannel(event.getGuild(), channelArgument);
			if (channel == null) {
				event.reply("I could not find that channel :no_entry:").queue();
				return;
			}
			
			if (title != null) {
				if (title.length() > 256) {
					event.reply("The title cannot be longer than 256 characters :no_entry:").queue();
					return;
				}
			}
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setTitle(title != null ? title : "Reaction Role");
			embed.setDescription("To add reactions use `" + event.getPrefix() + "reaction role add` (This message will be removed upon adding your first reaction)");
			embed.setFooter("React to the corresponding emote to get the desired role", null);
			
			channel.sendMessage(embed.build()).queue(message -> {
				event.reply("Your base reaction role menu has been created in " + channel.getAsMention() + " <:done:403285928233402378>").queue();
				
				data.update(row -> r.hashMap("messages", row.g("messages")
						.append(r.hashMap("id", message.getId())
								.with("channel", channel.getId())
								.with("roles", new Object[0])
								.with("bot_menu", true)
								.with("max_roles", 0)))).runNoReply(connection);
			});
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="add", description="Add a reaction to give a role to a user when reacted on")
		@AuthorPermissions({Permission.MANAGE_ROLES})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS, Permission.MESSAGE_ADD_REACTION})
		public void add(CommandEvent event, @Context Connection connection, @Argument(value="message id") String messageId, @Argument(value="emote") String emoteArgument, @Argument(value="role", endless=true) String roleArgument) throws UnsupportedEncodingException {
			r.table("reactionrole").insert(r.hashMap("id", event.getGuild().getId()).with("dm", true).with("messages", new Object[0])).run(connection, OptArgs.of("durability", "soft"));
			Get data = r.table("reactionrole").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			Role role = ArgumentUtils.getRole(event.getGuild(), roleArgument);
			if (role == null) {
				event.reply("I could not find that role :no_entry:").queue();
				return;
			}
			
			if (role.isManaged()) {
				event.reply("I cannot give a role which is managed :no_entry:").queue();
				return;
			}
			
			if (role.isPublicRole()) {
				event.reply("I cannot give users the `@everyone` role :no_entry:").queue();
				return;
			}
			
			if (!event.getMember().canInteract(role)) {
				event.reply("You cannot add a role which is higher or equal than my your role :no_entry:").queue();
				return;
			}
			
			if (!event.getSelfMember().canInteract(role)) {
				event.reply("I cannot give a role which is higher or equal than my top role :no_entry:").queue();
				return;
			}
			
			String unicodeEmote = emoteArgument;
			Emote emote = ArgumentUtils.getEmote(event.getGuild(), emoteArgument);
			String emoteStore = emote == null ? unicodeEmote : emote.getId();
			
			List<Map<String, Object>> messages = (List<Map<String, Object>>) dataRan.get("messages");
			for (Map<String, Object> messageData : messages) {
				if (messageData.get("id").equals(messageId)) {
					TextChannel channel = event.getGuild().getTextChannelById((String) messageData.get("channel"));
					if (channel == null) {
						event.reply("The channel which the reaction role was in has been deleted :no_entry:").queue();
						data.update(row -> r.hashMap("messages", row.g("messages").filter(d -> d.g("id").ne(messageId)))).runNoReply(connection);
						return;
					}
					
					channel.getMessageById(messageId).queue(message -> {
						if (message.getReactions().size() >= 20) {
							event.reply("That message is at the max amount of reactions (20) :no_entry:").queue();
							return;
						}
						
						List<Map<String, Object>> roles = (List<Map<String, Object>>) messageData.get("roles");
						for (Map<String, Object> roleData : roles) {
							if (roleData.get("id").equals(role.getId())) {
								event.reply("That role is already on the reaction role :no_entry:").queue();
								return;
							}
							
							if (roleData.get("emote").equals(emoteStore)) {
								event.reply("That emote is already on the reaction role :no_entry:").queue();
								return;
							}
						}
						
						if (emote == null) {
							message.addReaction(unicodeEmote).queue($ -> {
								if (isBotMenu(message, messageData)) {
									EmbedBuilder embed = new EmbedBuilder();
									MessageEmbed reactionRole = message.getEmbeds().get(0);
									if (roles.isEmpty()) {
										embed.setDescription((emote != null ? emote.getAsMention() : unicodeEmote) + ": " + role.getAsMention());
									} else {
										embed.setDescription(reactionRole.getDescription() + "\n\n" + (emote != null ? emote.getAsMention() : unicodeEmote) + ": " + role.getAsMention());
									}
										
									embed.setTitle(reactionRole.getTitle());
									embed.setFooter(reactionRole.getFooter().getText(), null);
										
									message.editMessage(embed.build()).queue();
										
									messageData.put("bot_menu", true);
								}
								
								event.reply("The role `" + role.getName() + "` will now be given when reacting to " + (emote == null ? unicodeEmote : emote.getAsMention()) + " <:done:403285928233402378>").queue();
								
								Map<String, Object> newData = new HashMap<>();
								newData.put("id", role.getId());
								newData.put("emote", emoteStore);
								roles.add(newData);
								messages.remove(messageData);
								messageData.put("roles", roles);
								messages.add(messageData);
								data.update(r.hashMap("messages", messages)).runNoReply(connection);
							}, e -> {
								if (e instanceof ErrorResponseException) {
									ErrorResponseException exception = (ErrorResponseException) e;
									if (exception.getErrorCode() == 10014) {
										event.reply("I could not find that emote :no_entry:").queue();
									}
								}
							});
							
							return;
						} else {
							message.addReaction(emote).queue();
						}
						
						if (isBotMenu(message, messageData)) {
							EmbedBuilder embed = new EmbedBuilder();
							MessageEmbed reactionRole = message.getEmbeds().get(0);
							if (roles.isEmpty()) {
								embed.setDescription((emote != null ? emote.getAsMention() : unicodeEmote) + ": " + role.getAsMention());
							} else {
								embed.setDescription(reactionRole.getDescription() + "\n\n" + (emote != null ? emote.getAsMention() : unicodeEmote) + ": " + role.getAsMention());
							}
								
							embed.setTitle(reactionRole.getTitle());
							embed.setFooter(reactionRole.getFooter().getText(), null);
								
							message.editMessage(embed.build()).queue();
								
							messageData.put("bot_menu", true);
						}
						
						event.reply("The role `" + role.getName() + "` will now be given when reacting to " + (emote == null ? unicodeEmote : emote.getAsMention()) + " <:done:403285928233402378>").queue();
						
						Map<String, Object> newData = new HashMap<>();
						newData.put("id", role.getId());
						newData.put("emote", emoteStore);
						roles.add(newData);
						messages.remove(messageData);
						messageData.put("roles", roles);
						messages.add(messageData);
						data.update(r.hashMap("messages", messages)).runNoReply(connection);
					}, e -> {
						if (e instanceof ErrorResponseException) {
	        				ErrorResponseException exception = (ErrorResponseException) e;
	        				if (exception.getErrorCode() == 10008) {
	        					event.reply("I could not find that message :no_entry:").queue();
	        					data.update(row -> r.hashMap("messages", row.g("messages").filter(d -> d.g("id").ne(messageId)))).runNoReply(connection);
	        					return;
	        				}
	        			}
					});
					
					return;
				}
			}
			
			try {
				event.getTextChannel().getMessageById(messageId).queue(message -> {
					if (message.getReactions().size() >= 20) {
						event.reply("That message is at the max amount of reactions (20) :no_entry:").queue();
						return;
					}
					
					if (emote == null) {
						message.addReaction(unicodeEmote).queue($ -> {
							event.reply("The role `" + role.getName() + "` will now be given when reacting to " + (emote == null ? unicodeEmote : emote.getAsMention()) + " <:done:403285928233402378>").queue();
							
							List<Map<String, Object>> roles = new ArrayList<>();
							Map<String, Object> newRoleData = new HashMap<>();
							newRoleData.put("id", role.getId());
							newRoleData.put("emote", emoteStore);
							roles.add(newRoleData);
							
							List<Map<String, Object>> newData = new ArrayList<>();
							Map<String, Object> messageData = new HashMap<>();
							messageData.put("id", message.getId());
							messageData.put("channel", event.getTextChannel().getId());
							messageData.put("bot_menu", false);
							messageData.put("roles", roles);
							newData.add(messageData);
							
							data.update(r.hashMap("messages", newData)).runNoReply(connection);
						}, e -> {
							if (e instanceof ErrorResponseException) {
								ErrorResponseException exception = (ErrorResponseException) e;
								if (exception.getErrorCode() == 10014) {
									event.reply("I could not find that emote :no_entry:").queue();
								}
							}
						});
						
						return;
					} else {
						message.addReaction(emote).queue();
					}
					
					event.reply("The role `" + role.getName() + "` will now be given when reacting to " + (emote == null ? unicodeEmote : emote.getAsMention()) + " <:done:403285928233402378>").queue();
					
					List<Map<String, Object>> roles = new ArrayList<>();
					Map<String, Object> newRoleData = new HashMap<>();
					newRoleData.put("id", role.getId());
					newRoleData.put("emote", emoteStore);
					roles.add(newRoleData);
					
					Map<String, Object> messageData = new HashMap<>();
					messageData.put("id", message.getId());
					messageData.put("channel", event.getTextChannel().getId());
					messageData.put("bot_menu", false);
					messageData.put("max_roles", 0);
					messageData.put("roles", roles);
					messages.add(messageData);
					
					data.update(r.hashMap("messages", messages)).runNoReply(connection);
				});
			} catch(IllegalArgumentException e) {
				event.reply("I could not find that message within this channel :no_entry:").queue();
			}
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="remove", description="Remove a reaction from the reaction role so it can no longer be used")
		@AuthorPermissions({Permission.MANAGE_ROLES})
		@BotPermissions({Permission.MESSAGE_MANAGE})
		public void remove(CommandEvent event, @Context Connection connection, @Argument(value="message id") String messageId, @Argument(value="role", endless=true) String roleArgument) {
			Get data = r.table("reactionrole").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if (dataRan == null) {
				event.reply("There are no reaction roles in this server :no_entry:").queue();
				return;
			}
			
			List<Map<String, Object>> messages = (List<Map<String, Object>>) dataRan.get("messages");
			if (messages.isEmpty()) {
				event.reply("There are no reaction roles in this server :no_entry:").queue();
				return;
			}
			
			Role role = ArgumentUtils.getRole(event.getGuild(), roleArgument);
			if (role == null) {
				event.reply("I could not find that role :no_entry:").queue();
			}
			
			for (Map<String, Object> messageData : messages) {
				if (messageData.get("id").equals(messageId)) {
					TextChannel channel = event.getGuild().getTextChannelById((String) messageData.get("channel"));
					if (channel == null) {
						event.reply("The channel which the reaction role was in has been deleted :no_entry:").queue();
						data.update(row -> r.hashMap("messages", row.g("messages").filter(d -> d.g("id").ne(messageId)))).runNoReply(connection);
						return;
					}
					
					channel.getMessageById(messageId).queue(message -> {
						List<Map<String, Object>> roles = (List<Map<String, Object>>) messageData.get("roles");
						for (Map<String, Object> roleData : roles) {
							if (roleData.get("id").equals(role.getId())) {
								String emoteDisplay;
								Emote emote = null;
								try {
									emote = event.getShardManager().getEmoteById((String) roleData.get("emote"));
									if (emote == null) {
										emoteDisplay = (String) roleData.get("emote");
									} else {
										emoteDisplay = emote.getAsMention();
									}
								} catch(NumberFormatException e) {
									emoteDisplay = (String) roleData.get("emote");
								}
								
								if (isBotMenu(message, messageData)) {
									MessageEmbed reactionRole = message.getEmbeds().get(0);
									
									String content = emoteDisplay + ": " + role.getAsMention();
									
									String newDescription = reactionRole.getDescription();
									String[] descriptionSplit = reactionRole.getDescription().split("\n\n");
									if (descriptionSplit.length != 1) {
										for (int i = 0; i < descriptionSplit.length; i++) {
											if (descriptionSplit[i].equals(content)) {
												if (i == descriptionSplit.length - 1) {
													newDescription = newDescription.replace("\n\n" + content, "");
												} else {
													newDescription = newDescription.replace(content + "\n\n", "");
												}
											}
										}
										
										EmbedBuilder embed = new EmbedBuilder();
										embed.setDescription(newDescription);
										embed.setTitle(reactionRole.getTitle());
										embed.setFooter(reactionRole.getFooter().getText(), null);
											
										message.editMessage(embed.build()).queue();
										
										messageData.put("bot_menu", true);
									} else {
										message.delete().queue();
										event.reply("I have deleted that reaction role <:done:403285928233402378>").queue();
										data.update(row -> r.hashMap("messages", row.g("messages").filter(d -> d.g("id").ne(messageId)))).runNoReply(connection);
										return;
									}
								}
								
								if (emote == null) {
									for (MessageReaction reaction : message.getReactions()) {
										if (reaction.getReactionEmote().getName().equals(emoteDisplay)) {
											reaction.removeReaction(event.getSelfUser()).queue();
										}
									}
								} else {
									for (MessageReaction reaction : message.getReactions()) {
										if (reaction.getReactionEmote().getEmote() != null) {
											if (reaction.getReactionEmote().getEmote().equals(emote)) {
												reaction.removeReaction(event.getSelfUser()).queue();
											}
										}
									}
								}
									
								event.reply("The role `" + role.getName() + "` has been removed from that reaction role <:done:403285928233402378>").queue();
								
								roles.remove(roleData);
								messages.remove(messageData);
								messageData.put("roles", roles);
								messages.add(messageData);
									
								data.update(r.hashMap("messages", messages)).runNoReply(connection);
							}
						}
						
						event.reply("That role is not on that reaction role :no_entry:").queue();
					}, e -> {
						if (e instanceof ErrorResponseException) {
	        				ErrorResponseException exception = (ErrorResponseException) e;
	        				if (exception.getErrorCode() == 10008) {
	        					event.reply("I could not find that message :no_entry:").queue();
	        					data.update(row -> r.hashMap("messages", row.g("messages").filter(d -> d.g("id").ne(messageId)))).runNoReply(connection);
	        					return;
	        				}
	        			}
					});
					
					return;
				}
			}
			
			event.reply("I could not find that reaction role :no_entry:").queue();
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="force remove", aliases={"forceremove"}, description="Removes all reactions which link to a deleted role", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@AuthorPermissions({Permission.MANAGE_ROLES})
		@BotPermissions({Permission.MESSAGE_MANAGE})
		public void forceRemove(CommandEvent event, @Context Connection connection, @Argument(value="message id") String messageId) {
			Get data = r.table("reactionrole").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if (dataRan == null) {
				event.reply("There are no reaction roles in this server :no_entry:").queue();
				return;
			}
			
			List<Map<String, Object>> messages = (List<Map<String, Object>>) dataRan.get("messages");
			if (messages.isEmpty()) {
				event.reply("There are no reaction roles in this server :no_entry:").queue();
				return;
			}
			
			for (Map<String, Object> messageData : messages) {
				if (messageData.get("id").equals(messageId)) {
					TextChannel channel = event.getGuild().getTextChannelById((String) messageData.get("channel"));
					if (channel == null) {
						event.reply("The channel which the reaction role was in has been deleted :no_entry:").queue();
						data.update(row -> r.hashMap("messages", row.g("messages").filter(d -> d.g("id").ne(messageId)))).runNoReply(connection);
						return;
					}
					
					channel.getMessageById(messageId).queue(message -> {
						boolean isBotMenu = isBotMenu(message, messageData);
						MessageEmbed reactionRole = null;
						String newDescription = null;
						if (isBotMenu) {	
							reactionRole = message.getEmbeds().get(0);
							newDescription = reactionRole.getDescription();
						}
						
						List<Map<String, Object>> roles = (List<Map<String, Object>>) messageData.get("roles");
						List<Map<String, Object>> newRoles = new ArrayList<>(roles);
						for (Map<String, Object> roleData : roles) {
							String roleId = (String) roleData.get("id");
							Role role = event.getGuild().getRoleById(roleId);
							if (role == null) {
								String emoteDisplay;
								Emote emote = null;
								try {
									emote = event.getShardManager().getEmoteById((String) roleData.get("emote"));
									if (emote == null) {
										emoteDisplay = (String) roleData.get("emote");
									} else {
										emoteDisplay = emote.getAsMention();
									}
								} catch(NumberFormatException e) {
									emoteDisplay = (String) roleData.get("emote");
								}
								
								if (isBotMenu) {						
									String content = emoteDisplay + ": <@&" + roleId + ">";
									
									String[] descriptionSplit = reactionRole.getDescription().split("\n\n");
									if (descriptionSplit.length != 1) {
										for (int i = 0; i < descriptionSplit.length; i++) {
											if (descriptionSplit[i].equals(content)) {
												if (i == descriptionSplit.length - 1) {
													newDescription = newDescription.replace("\n\n" + content, "");
												} else {
													newDescription = newDescription.replace(content + "\n\n", "");
												}
											}
										}
									} else {
										message.delete().queue();
										event.reply("I have deleted that reaction role <:done:403285928233402378>").queue();
										data.update(row -> r.hashMap("messages", row.g("messages").filter(d -> d.g("id").ne(messageId)))).runNoReply(connection);
										return;
									}
								}
								
								newRoles.remove(roleData);
								
								if (emote == null) {
									for (MessageReaction reaction : message.getReactions()) {
										if (reaction.getReactionEmote().getName().equals(emoteDisplay)) {
											reaction.removeReaction(event.getSelfUser()).queue();
										}
									}
								} else {
									for (MessageReaction reaction : message.getReactions()) {
										if (reaction.getReactionEmote().getEmote() != null) {
											if (reaction.getReactionEmote().getEmote().equals(emote)) {
												reaction.removeReaction(event.getSelfUser()).queue();
											}
										}
									}
								}
							}
						}
						
						if (newRoles.equals(roles)) {
							event.reply("There were no deleted roles on that reaction role :no_entry:").queue();
							return;
						}
						
						if (isBotMenu) {
							EmbedBuilder embed = new EmbedBuilder();
							embed.setDescription(newDescription);
							embed.setTitle(reactionRole.getTitle());
							embed.setFooter(reactionRole.getFooter().getText(), null);
								
							message.editMessage(embed.build()).queue();
							
							messageData.put("bot_menu", true);
						}
						
						event.reply("All deleted roles have been removed from that reaction role <:done:403285928233402378>").queue();
						
						messages.remove(messageData);
						messageData.put("roles", newRoles);
						messages.add(messageData);
						
						data.update(r.hashMap("messages", messages)).runNoReply(connection);
					}, e -> {
						if (e instanceof ErrorResponseException) {
	        				ErrorResponseException exception = (ErrorResponseException) e;
	        				if (exception.getErrorCode() == 10008) {
	        					event.reply("I could not find that message :no_entry:").queue();
	        					data.update(row -> r.hashMap("messages", row.g("messages").filter(d -> d.g("id").ne(messageId)))).runNoReply(connection);
	        					return;
	        				}
	        			}
					});
					
					return;
				}
			}
			
			event.reply("I could not find that reaction role :no_entry:").queue();
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="refresh", description="Edits the reaction role message so the role mentions displayed change to their according colour/name", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@AuthorPermissions({Permission.MANAGE_ROLES})
		public void refresh(CommandEvent event, @Context Connection connection, @Argument(value="message id") String messageId) {
			Get data = r.table("reactionrole").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if (dataRan == null) {
				event.reply("There are no reaction roles in this server :no_entry:").queue();
				return;
			}
			
			List<Map<String, Object>> messages = (List<Map<String, Object>>) dataRan.get("messages");
			if (messages.isEmpty()) {
				event.reply("There are no reaction roles in this server :no_entry:").queue();
				return;
			}
			
			for (Map<String, Object> messageData : messages) {
				if (messageData.get("id").equals(messageId)) {
					TextChannel channel = event.getGuild().getTextChannelById((String) messageData.get("channel"));
					if (channel == null) {
						event.reply("The channel which the reaction role was in has been deleted :no_entry:").queue();
						data.update(row -> r.hashMap("messages", row.g("messages").filter(d -> d.g("id").ne(messageId)))).runNoReply(connection);
						return;
					}
					
					channel.getMessageById(messageId).queue(message -> {
						if (isBotMenu(message, messageData)) {
							MessageEmbed reactionRole = message.getEmbeds().get(0);
							message.editMessage(reactionRole).queue();
							event.reply("Refreshed the reaction role menu <:done:403285928233402378>").queue();
						} else {
							event.reply("You can only refresh reaction roles made by the bot :no_entry:").queue();
						}
					}, e -> {
						if (e instanceof ErrorResponseException) {
	        				ErrorResponseException exception = (ErrorResponseException) e;
	        				if (exception.getErrorCode() == 10008) {
	        					event.reply("I could not find that message :no_entry:").queue();
	        					data.update(row -> r.hashMap("messages", row.g("messages").filter(d -> d.g("id").ne(messageId)))).runNoReply(connection);
	        					return;
	        				}
	        			}
					});
					
					return;
				}
			}
			
			event.reply("I could not find that reaction role :no_entry:").queue();
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="delete", description="Deletes a reaction roles data and message if it's a menu made by the bot", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@AuthorPermissions({Permission.MANAGE_ROLES})
		public void delete(CommandEvent event, @Context Connection connection, @Argument(value="message id") String messageId) {
			Get data = r.table("reactionrole").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if (dataRan == null) {
				event.reply("There are no reaction roles in this server :no_entry:").queue();
				return;
			}
			
			List<Map<String, Object>> messages = (List<Map<String, Object>>) dataRan.get("messages");
			if (messages.isEmpty()) {
				event.reply("There are no reaction roles in this server :no_entry:").queue();
				return;
			}
			
			for (Map<String, Object> messageData : messages) {
				if (messageData.get("id").equals(messageId)) {
					TextChannel channel = event.getGuild().getTextChannelById((String) messageData.get("channel"));
					if (channel == null) {
						event.reply("The channel which the reaction role was in has been deleted :no_entry:").queue();
						data.update(row -> r.hashMap("messages", row.g("messages").filter(d -> d.g("id").ne(messageId)))).runNoReply(connection);
						return;
					}
					
					channel.getMessageById(messageId).queue(message -> {
						if (isBotMenu(message, messageData)) {
							message.delete().queue();
						}
						
						event.reply("That reaction role has been deleted <:done:403285928233402378>").queue();
						
						data.update(row -> r.hashMap("messages", row.g("messages").filter(d -> d.g("id").ne(messageId)))).runNoReply(connection);
					}, e -> {
						if (e instanceof ErrorResponseException) {
	        				ErrorResponseException exception = (ErrorResponseException) e;
	        				if (exception.getErrorCode() == 10008) {
	        					event.reply("I could not find that message :no_entry:").queue();
	        					data.update(row -> r.hashMap("messages", row.g("messages").filter(d -> d.g("id").ne(messageId)))).runNoReply(connection);
	        					return;
	        				}
	        			}
					});
					
					return;
				}
			}
			
			event.reply("I could not find that reaction role :no_entry:").queue();
		}
		
		@Command(value="dm toggle", aliases={"dmtoggle", "toggledm", "toggle dm", "dm"}, description="Enables/disables whether the bot should send a dm to the user when they react to a reaction role", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@AuthorPermissions({Permission.MANAGE_ROLES})
		public void dmToggle(CommandEvent event, @Context Connection connection) {
			r.table("reactionrole").insert(r.hashMap("id", event.getGuild().getId()).with("dm", true).with("messages", new Object[0])).run(connection, OptArgs.of("durability", "soft"));
			Get data = r.table("reactionrole").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if ((boolean) dataRan.get("dm") == true) {
				event.reply("I will no longer dm users when they are given a role <:done:403285928233402378>").queue();
				data.update(r.hashMap("dm", false)).runNoReply(connection);
			} else {
				event.reply("I will now dm users when they are given a role <:done:403285928233402378>").queue();
				data.update(r.hashMap("dm", true)).runNoReply(connection);
			}
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="max roles", aliases={"maxroles"}, description="Set the maximum amount of roles a user can have from a reaction role (0 turns it off)", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@AuthorPermissions({Permission.MANAGE_ROLES})
		public void maxRoles(CommandEvent event, @Context Connection connection, @Argument(value="message id") String messageId, @Argument(value="max roles") String maxRoles) {
			Get data = r.table("reactionrole").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if (dataRan == null) {
				event.reply("There are no reaction roles in this server :no_entry:").queue();
				return;
			}
			
			List<Map<String, Object>> messages = (List<Map<String, Object>>) dataRan.get("messages");
			if (messages.isEmpty()) {
				event.reply("There are no reaction roles in this server :no_entry:").queue();
				return;
			}
			
			int maxRolesInt;
			if (nullStrings.contains(maxRoles)) {
				maxRolesInt = 0;
			} else {
				try {
					maxRolesInt = Integer.parseInt(maxRoles);
				} catch(NumberFormatException e) {
					event.reply("Make sure that `max roles` is a number :no_entry:").queue();
					return;
				}
				
				if (maxRolesInt < 0) {
					event.reply("Max roles cannot be less than 0 :no_entry:").queue();
					return;
				}
			}
			
			for (Map<String, Object> messageData : messages) {
				if (messageData.get("id").equals(messageId)) {
					long maxRolesData = (long) messageData.get("max_roles");
					if (maxRolesInt > ((List<Map<String, Object>>) messageData.get("roles")).size()) {
						event.reply("The max roles cannot be more than the amount of roles on the reaction role (" + maxRolesData + ") :no_entry:").queue();
						return;
					}
					
					if (maxRolesInt == maxRolesData) {
						event.reply("The max roles for that reaction role is already set to **" + maxRolesData + "** :no_entry:").queue();
						return;
					}
					
					if (maxRolesInt != 0) {
						event.reply("The max roles for that reaction role is now **" + maxRolesInt + "** <:done:403285928233402378>").queue();
					} else {
						event.reply("There is no longer a limit to the amount of roles a user can have on that reaction role <:done:403285928233402378>").queue();
					}
					
					messages.remove(messageData);
					messageData.put("max_roles", maxRolesInt);
					messages.add(messageData);
					data.update(r.hashMap("messages", messages)).runNoReply(connection);	
					return;
				}
			}
			
			event.reply("I could not find that reaction role :no_entry:").queue();
		}
		
	}
	
	@SuppressWarnings("unchecked")
	@Command(value="role", description="Assign a self role to yourself, view all self roles in `self roles list`")
	@BotPermissions({Permission.MANAGE_ROLES})
	public void role(CommandEvent event, @Context Connection connection, @Argument(value="role") String roleArgument) {
		Map<String, Object> data = r.table("selfroles").get(event.getGuild().getId()).run(connection);
		
		Role role = ArgumentUtils.getRole(event.getGuild(), roleArgument);
		if (role == null) {
			event.reply("I could not find that role :no_entry:").queue();
			return;
		}
		
		if (data == null) {
			event.reply("That role is not a self role :no_entry:").queue();
			return;
		}
		
		List<String> roles = (List<String>) data.get("roles");
		for (String roleId : roles) {
			if (roleId.equals(role.getId())) {
				if (event.getMember().getRoles().contains(role)) {
					event.reply("You no longer have the role **" + role.getName() + "** <:done:403285928233402378>").queue();
					event.getGuild().getController().removeSingleRoleFromMember(event.getMember(), role).queue();
				} else {
					event.reply("You now have the role **" + role.getName() + "** <:done:403285928233402378>").queue();
					event.getGuild().getController().addSingleRoleToMember(event.getMember(), role).queue();
				}
				
				return;
			}
		}
		
		event.reply("That role is not a self role :no_entry:").queue();
	}
	
	public class SelfRolesCommand extends Sx4Command {
		
		public SelfRolesCommand() {
			super("self roles");
			
			super.setDescription("Set up selfroles so users can simply use a command to be given a role");
			super.setAliases("selfrole", "selfroles", "self role");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="add", description="Add a role which can be gained by any user by simply using the `role` command")
		@AuthorPermissions({Permission.MANAGE_ROLES})
		public void add(CommandEvent event, @Context Connection connection, @Argument(value="role", endless=true) String roleArgument) {
			r.table("selfroles").insert(r.hashMap("id", event.getGuild().getId()).with("roles", new Object[0])).run(connection, OptArgs.of("durability", "soft"));
			Get data = r.table("selfroles").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			Role role = ArgumentUtils.getRole(event.getGuild(), roleArgument);
			if (role == null) {
				event.reply("I could not find that role :no_entry:").queue();
				return;
			}
			
			if (role.isManaged()) {
				event.reply("I cannot give a role which is managed :no_entry:").queue();
				return;
			}
			
			if (role.isPublicRole()) {
				event.reply("I cannot give users the `@everyone` role :no_entry:").queue();
				return;
			}
			
			if (!event.getMember().canInteract(role)) {
				event.reply("You cannot add a role which is higher or equal than your top role").queue();
				return;
			}
			
			if (!event.getSelfMember().canInteract(role)) {
				event.reply("I cannot give a role which is higher or equal than my top role").queue();
				return;
			}
			
			List<String> roles = (List<String>) dataRan.get("roles");
			for (String roleId : roles) {
				if (roleId.equals(role.getId())) {
					event.reply("That role is already a self role :no_entry:").queue();
					return;
				}
			}
			
			event.reply("Added `" + role.getName() + "` as a self role <:done:403285928233402378>").queue();
			data.update(row -> r.hashMap("roles", row.g("roles").append(role.getId()))).runNoReply(connection);
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="remove", description="Remove a role from the self roles in the current server")
		@AuthorPermissions({Permission.MANAGE_ROLES})
		public void remove(CommandEvent event, @Context Connection connection, @Argument(value="roles", endless=true) String roleArgument) {
			Get data = r.table("selfroles").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if (dataRan == null) {
				event.reply("There are no self roles in this server :no_entry:").queue();
				return;
			}
			
			List<String> roles = (List<String>) dataRan.get("roles");
			if (roles.isEmpty()) {
				event.reply("There are no self roles in this server :no_entry:").queue();
				return;
			}
			
			Role role = ArgumentUtils.getRole(event.getGuild(), roleArgument);
			if (role == null) {
				event.reply("I could not find that role :no_entry:").queue();
				return;
			}
			
			for (String roleId : roles) {
				if (roleId.equals(role.getId())) {
					event.reply("Removed `" + role.getName() + "` from the self roles <:done:403285928233402378>").queue();
					data.update(row -> r.hashMap("roles", row.g("roles").filter(d -> d.ne(roleId)))).runNoReply(connection);
					return;
				}
			}
			
			event.reply("That role is not a self role :no_entry:").queue();
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="reset", aliases={"delete", "wipe"}, description="Deletes all self roles which are currently set", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@AuthorPermissions({Permission.MANAGE_ROLES})
		public void reset(CommandEvent event, @Context Connection connection) {
			Get data = r.table("selfroles").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if (dataRan == null) {
				event.reply("There are no self roles in this server :no_entry:").queue();
				return;
			}
			
			List<String> roles = (List<String>) dataRan.get("roles");
			if (roles.isEmpty()) {
				event.reply("There are no self roles in this server :no_entry:").queue();
				return;
			}
			
			event.reply(event.getAuthor().getName() + ", are you sure you want to reset all data for self roles? (Yes or No)").queue(message -> {
				PagedUtils.getConfirmation(event, 60, event.getAuthor(), confirmation -> {
					if (confirmation == true) {
						event.reply("All self roles have been deleted <:done:403285928233402378>").queue();
						data.update(r.hashMap("roles", new Object[0])).runNoReply(connection);
					} else {
						event.reply("Cancelled <:done:403285928233402378>").queue();
					}
				});
			});
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="list", description="Lists all the self roles in the current server")
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void list(CommandEvent event, @Context Connection connection) {
			Map<String, Object> data = r.table("selfroles").get(event.getGuild().getId()).run(connection);
			
			if (data == null) {
				event.reply("There are no self roles in this server :no_entry:").queue();
				return;
			}
			
			List<String> rolesData = (List<String>) data.get("roles");
			if (rolesData.isEmpty()) {
				event.reply("There are no self roles in this server :no_entry:").queue();
				return;
			}
			
			List<Role> roles = new ArrayList<>();
			for (String roleId : rolesData) {
				Role role = event.getGuild().getRoleById(roleId);
				if (role != null) {
					roles.add(role);
				}
			}
			
			if (roles.isEmpty()) {
				event.reply("There are no self roles in this server :no_entry:").queue();
				return;
			}
			
			roles.sort((a, b) -> Integer.compare(b.getPosition(), a.getPosition()));
			PagedResult<Role> paged = new PagedResult<>(roles)
					.setDeleteMessage(false)
					.setPerPage(15)
					.setIncreasedIndex(true)
					.setAuthor("Self Roles (" + roles.size() + ")", null, event.getGuild().getIconUrl())
					.setFunction(role -> role.getAsMention());
			
			PagedUtils.getPagedResult(event, paged, 300, null);
		}
		
	}
	
	@Initialize(all=true)
	public void initialize(CommandImpl command) {
		command.setCategory(Categories.SELF_ROLES);
	}

}
