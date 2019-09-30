package com.sx4.bot.modules;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.command.Command.AuthorPermissions;
import com.jockie.bot.core.command.Command.BotPermissions;
import com.jockie.bot.core.command.ICommand.ContentOverflowPolicy;
import com.jockie.bot.core.command.Context;
import com.jockie.bot.core.command.Initialize;
import com.jockie.bot.core.command.impl.CommandEvent;
import com.jockie.bot.core.command.impl.CommandImpl;
import com.jockie.bot.core.module.Module;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.sx4.bot.categories.Categories;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEventListener;
import com.sx4.bot.database.Database;
import com.sx4.bot.utils.ArgumentUtils;
import com.sx4.bot.utils.HelpUtils;
import com.sx4.bot.utils.PagedUtils;
import com.sx4.bot.utils.PagedUtils.PagedResult;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;

@Module
public class SelfrolesModule {
	
	public class ReactionRoleCommand extends Sx4Command {
		
		private List<String> nullStrings = List.of("off", "none", "null", "reset");
		
		private MessageEmbed getUpdatedEmbed(MessageEmbed oldEmbed, List<Document> roles, String emoteDisplay, Role role) {
			EmbedBuilder embed = new EmbedBuilder();
			MessageEmbed reactionRoleEmbed = oldEmbed;
			if (roles.isEmpty()) {
				embed.setDescription(emoteDisplay + ": " + role.getAsMention());
			} else {
				embed.setDescription(reactionRoleEmbed.getDescription() + "\n\n" + emoteDisplay + ": " + role.getAsMention());
			}
				
			embed.setTitle(reactionRoleEmbed.getTitle());
			embed.setFooter(reactionRoleEmbed.getFooter().getText(), null);
			
			return embed.build();
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
		public void create(CommandEvent event, @Context Database database, @Argument(value="channel") String channelArgument, @Argument(value="title", endless=true, nullDefault=true) String title) {
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
				Document reactionRole = new Document("id", message.getIdLong()).append("channelId", channel.getIdLong()).append("botMenu", true);
				database.updateGuildById(event.getGuild().getIdLong(), Updates.push("reactionRole.reactionRoles", reactionRole), (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						event.reply("Your base reaction role menu has been created in " + channel.getAsMention() + " <:done:403285928233402378>").queue();
					}
				});
			});
		}
		
		@Command(value="add", description="Add a reaction to give a role to a user when reacted on")
		@AuthorPermissions({Permission.MANAGE_ROLES})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS, Permission.MESSAGE_ADD_REACTION, Permission.MESSAGE_HISTORY})
		public void add(CommandEvent event, @Context Database database, @Argument(value="message id") long messageId, @Argument(value="emote") String emoteArgument, @Argument(value="role", endless=true) String roleArgument) throws UnsupportedEncodingException {
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
			boolean unicode = emote == null;
			
			List<Document> reactionRoles = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("reactionRole.reactionRoles")).getEmbedded(List.of("reactionRole", "reactionRoles"), Collections.emptyList());
			for (Document reactionRole : reactionRoles) {
				if (reactionRole.getLong("id") == messageId) {
					TextChannel channel = event.getGuild().getTextChannelById(reactionRole.getLong("channelId"));
					if (channel == null) {
						database.updateGuildById(event.getGuild().getIdLong(), Updates.pull("reactionRole.reactionRoles", Filters.eq("channelId", reactionRole.getLong("channelId"))), (result, exception) -> {
							if (exception != null) {
								exception.printStackTrace();
								event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
							} else {
								event.reply("The channel which the reaction role was in has been deleted :no_entry:").queue();
							}
						});
						
						return;
					}
					
					channel.retrieveMessageById(messageId).queue(message -> {
						if (message.getReactions().size() >= 20) {
							event.reply("That message is at the max amount of reactions (20) :no_entry:").queue();
							return;
						}
						
						List<Document> roles = reactionRole.getList("roles", Document.class, Collections.emptyList());
						for (Document roleData : roles) {
							if (roleData.get("id").equals(role.getId())) {
								event.reply("That role is already on the reaction role :no_entry:").queue();
								return;
							}
							
							if (unicode) {
								String emoteName = roleData.getEmbedded(List.of("emote", "name"), String.class);
								if (emoteName != null && emoteName.equals(unicodeEmote)) {
									event.reply("That emote is already on the reaction role :no_entry:").queue();
									return;
								}
							} else {
								Long emoteId = roleData.getEmbedded(List.of("emote", "id"), Long.class);
								if (emoteId != null && emoteId == emote.getIdLong()) {
									event.reply("That emote is already on the reaction role :no_entry:").queue();
									return;
								}
							}
							
						}
						
						if (unicode) {
							message.addReaction(unicodeEmote).queue($ -> {
								if (reactionRole.getBoolean("botMenu")) {
									message.editMessage(this.getUpdatedEmbed(message.getEmbeds().get(0), roles, unicodeEmote, role)).queue();
								}
								
								Document roleData = new Document("id", role.getIdLong()).append("emote", new Document("name", unicodeEmote));
								
								UpdateOptions updateOptions = new UpdateOptions().arrayFilters(List.of(Filters.eq("reactionRole.id", messageId))).upsert(true);
								database.updateGuildById(event.getGuild().getIdLong(), null, Updates.push("reactionRole.reactionRoles.$[reactionRole].roles", roleData), updateOptions, (result, exception) -> {
									if (exception != null) {
										exception.printStackTrace();
										event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
									} else {
										event.reply("The role `" + role.getName() + "` will now be given when reacting to " + unicodeEmote + " <:done:403285928233402378>").queue();
									}
								});
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
							
							if (reactionRole.getBoolean("botMenu")) {
								message.editMessage(this.getUpdatedEmbed(message.getEmbeds().get(0), roles, emote.getAsMention(), role)).queue();
							}
							
							Document roleData = new Document("id", role.getIdLong()).append("emote", new Document("id", emote.getIdLong()));
							
							UpdateOptions updateOptions = new UpdateOptions().arrayFilters(List.of(Filters.eq("reactionRole.id", messageId))).upsert(true);
							database.updateGuildById(event.getGuild().getIdLong(), null, Updates.push("reactionRole.reactionRoles.$[reactionRole].roles", roleData), updateOptions, (result, exception) -> {
								if (exception != null) {
									exception.printStackTrace();
									event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
								} else {
									event.reply("The role `" + role.getName() + "` will now be given when reacting to " + emote.getAsMention() + " <:done:403285928233402378>").queue();
								}
							});
						}
					}, e -> {
						if (e instanceof ErrorResponseException) {
							ErrorResponseException exception = (ErrorResponseException) e;
							if (exception.getErrorCode() == 10008) {
								database.updateGuildById(event.getGuild().getIdLong(), Updates.pull("reactionRole.reactionRoles", Filters.eq("id", messageId)), (result, messageException) -> {
									if (messageException != null) {
										messageException.printStackTrace();
										event.reply(Sx4CommandEventListener.getUserErrorMessage(messageException)).queue();
									} else {
										event.reply("I could not find that message :no_entry:").queue();
									}
								});
								
								return;
							}
						}
					});
					
					return;
				}
			}
			
			event.getTextChannel().retrieveMessageById(messageId).queue(message -> {
				if (message.getReactions().size() >= 20) {
					event.reply("That message is at the max amount of reactions (20) :no_entry:").queue();
					return;
				}
				
				if (unicode) {
					message.addReaction(unicodeEmote).queue($ -> {
						Document roleData = new Document("id", role.getIdLong()).append("emote", new Document("name", unicodeEmote));
						Document reactionRole = new Document("id", message.getIdLong()).append("channelId", event.getChannel().getIdLong()).append("botMenu", false).append("roles", List.of(roleData));
						
						database.updateGuildById(event.getGuild().getIdLong(), Updates.push("reactionRole.reactionRoles", reactionRole), (result, exception) -> {
							if (exception != null) {
								exception.printStackTrace();
								event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
							} else {
								event.reply("The role `" + role.getName() + "` will now be given when reacting to " + unicodeEmote + " <:done:403285928233402378>").queue();
							}
						});
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
					
					Document roleData = new Document("id", role.getIdLong()).append("emote", new Document("id", emote.getIdLong()));
					Document reactionRole = new Document("id", message.getIdLong()).append("channelId", event.getChannel().getIdLong()).append("botMenu", false).append("roles", List.of(roleData));
					
					database.updateGuildById(event.getGuild().getIdLong(), Updates.push("reactionRole.reactionRoles", reactionRole), (result, exception) -> {
						if (exception != null) {
							exception.printStackTrace();
							event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
						} else {
							event.reply("The role `" + role.getName() + "` will now be given when reacting to " + emote.getAsMention() + " <:done:403285928233402378>").queue();
						}
					});
				}
			}, e -> {
				if (e instanceof ErrorResponseException) {
					ErrorResponseException exception = (ErrorResponseException) e;
					if (exception.getErrorCode() == 10008) {
						event.reply("I could not find that message within this channel :no_entry:").queue();
						return;
					}
				}
			});
		}
		
		@Command(value="remove", description="Remove a reaction from the reaction role so it can no longer be used")
		@AuthorPermissions({Permission.MANAGE_ROLES})
		@BotPermissions({Permission.MESSAGE_MANAGE})
		public void remove(CommandEvent event, @Context Database database, @Argument(value="message id") long messageId, @Argument(value="role", endless=true) String roleArgument) {			
			Role role = ArgumentUtils.getRole(event.getGuild(), roleArgument);
			if (role == null) {
				event.reply("I could not find that role :no_entry:").queue();
				return;
			}
			
			List<Document> reactionRoles = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("reactionRole.reactionRoles")).getEmbedded(List.of("reactionRole", "reactionRoles"), Collections.emptyList());
			for (Document reactionRole : reactionRoles) {
				if (reactionRole.getLong("id") == messageId) {
					TextChannel channel = event.getGuild().getTextChannelById(reactionRole.getLong("channelId"));
					if (channel == null) {
						database.updateGuildById(event.getGuild().getIdLong(), Updates.pull("reactionRole.reactionRoles", Filters.eq("channelId", reactionRole.getLong("channelId"))), (result, exception) -> {
							if (exception != null) {
								exception.printStackTrace();
								event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
							} else {
								event.reply("The channel which the reaction role was in has been deleted :no_entry:").queue();
							}
						});
						
						return;
					}
	
					channel.retrieveMessageById(messageId).queue(message -> {
						List<Document> roles = reactionRole.getList("roles", Document.class, Collections.emptyList());
						for (Document roleData : roles) {
							if (roleData.getLong("id") == role.getIdLong()) {
								Document emoteData = roleData.get("emote", Document.class);
								String emoteName = emoteData.getString("name");
								Long emoteId = emoteData.getLong("id");
								
								if (reactionRole.getBoolean("botMenu")) {
									MessageEmbed reactionRoleEmbed = message.getEmbeds().get(0);
									
									Emote emote = null;
									if (emoteId != null) {
										emote = event.getShardManager().getEmoteById(emoteId);
									}
									
									String content = (emote == null ? emoteName : emote.getAsMention()) + ": " + role.getAsMention();
									
									String newDescription = reactionRoleEmbed.getDescription();
									String[] descriptionSplit = reactionRoleEmbed.getDescription().split("\n\n");
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
										embed.setTitle(reactionRoleEmbed.getTitle());
										embed.setFooter(reactionRoleEmbed.getFooter().getText(), null);
											
										message.editMessage(embed.build()).queue();
									} else {
										database.updateGuildById(event.getGuild().getIdLong(), Updates.pull("reactionRole.reactionRoles", Filters.eq("id", messageId)), (result, exception) -> {
											if (exception != null) {
												exception.printStackTrace();
												event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
											} else {
												message.delete().queue();
												event.reply("I have deleted that reaction role <:done:403285928233402378>").queue();
											}
										});
										
										return;
									}
								}
								
								if (emoteId == null) {
									for (MessageReaction reaction : message.getReactions()) {
										if (reaction.getReactionEmote().getName().equals(emoteName)) {
											reaction.removeReaction(event.getSelfUser()).queue();
										}
									}
								} else {
									for (MessageReaction reaction : message.getReactions()) {
										if (reaction.getReactionEmote().isEmote()) {
											if (reaction.getReactionEmote().getIdLong() == emoteId) {
												reaction.removeReaction(event.getSelfUser()).queue();
											}
										}
									}
								}
									
								UpdateOptions updateOptions = new UpdateOptions().arrayFilters(List.of(Filters.eq("reactionRole.id", messageId)));
								database.updateGuildById(event.getGuild().getIdLong(), null, Updates.pull("reactionRole.reactionRoles.$[reactionRole].roles", Filters.eq("id", role.getIdLong())), updateOptions, (result, exception) -> {
									if (exception != null) {
										exception.printStackTrace();
										event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
									} else {
										event.reply("The role `" + role.getName() + "` has been removed from that reaction role <:done:403285928233402378>").queue();
									}
								});
								
								return;
							}
						}
						
						event.reply("That role is not on that reaction role :no_entry:").queue();
					}, e -> {
						if (e instanceof ErrorResponseException) {
							ErrorResponseException exception = (ErrorResponseException) e;
							if (exception.getErrorCode() == 10008) {
								database.updateGuildById(event.getGuild().getIdLong(), Updates.pull("reactionRole.reactionRoles", Filters.eq("id", messageId)), (result, writeException) -> {
									if (writeException != null) {
										writeException.printStackTrace();
										event.reply(Sx4CommandEventListener.getUserErrorMessage(writeException)).queue();
									} else {
										event.reply("I could not find that message :no_entry:").queue();
									}
								});
								
								return;
							}
						}
					});
					
					return;
				}
			}
			
			event.reply("I could not find that reaction role :no_entry:").queue();
		}
		
		@Command(value="force remove", aliases={"forceremove"}, description="Removes all reactions which link to a deleted role", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@AuthorPermissions({Permission.MANAGE_ROLES})
		@BotPermissions({Permission.MESSAGE_MANAGE})
		public void forceRemove(CommandEvent event, @Context Database database, @Argument(value="message id") long messageId) {
			List<Document> reactionRoles = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("reactionRole.reactionRoles")).getEmbedded(List.of("reactionRole", "reactionRoles"), Collections.emptyList());
			for (Document reactionRole : reactionRoles) {
				if (reactionRole.getLong("id") == messageId) {
					TextChannel channel = event.getGuild().getTextChannelById(reactionRole.getLong("channelId"));
					if (channel == null) {
						database.updateGuildById(event.getGuild().getIdLong(), Updates.pull("reactionRole.reactionRoles", Filters.eq("channelId", reactionRole.getLong("channelId"))), (result, exception) -> {
							if (exception != null) {
								exception.printStackTrace();
								event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
							} else {
								event.reply("The channel which the reaction role was in has been deleted :no_entry:").queue();
							}
						});
						
						return;
					}
					
					channel.retrieveMessageById(messageId).queue(message -> {
						boolean botMenu = reactionRole.getBoolean("botMenu");
						MessageEmbed reactionRoleEmbed = null;
						String newDescription = null;
						if (botMenu) {	
							reactionRoleEmbed = message.getEmbeds().get(0);
							newDescription = reactionRoleEmbed.getDescription();
						}
						
						List<Document> roles = reactionRole.getList("roles", Document.class);
						Bson filter = null;
						for (Document roleData : roles) {
							long roleId = roleData.getLong("id");
							Role role = event.getGuild().getRoleById(roleId);
							if (role == null) {
								Document emoteData = roleData.get("emote", Document.class);
								Long emoteId = emoteData.getLong("id");
								String emoteName = emoteData.getString("name");				
								Emote emote = emoteId != null ? event.getShardManager().getEmoteById(emoteId) : null;
								
								if (botMenu) {						
									String content = (emote == null ? emoteName : emote.getAsMention()) + ": <@&" + roleId + ">";
									
									String[] descriptionSplit = reactionRoleEmbed.getDescription().split("\n\n");
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
										database.updateGuildById(event.getGuild().getIdLong(), Updates.pull("reactionRole.reactionRoles", Filters.eq("id", messageId)), (result, exception) -> {
											if (exception != null) {
												exception.printStackTrace();
												event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
											} else {
												message.delete().queue();
												event.reply("I have deleted that reaction role <:done:403285928233402378>").queue();
											}
										});
										
										return;
									}
								}
								
								filter = filter == null ? Filters.eq("id", roleId) : Filters.or(filter, Filters.eq("id", roleId));
								
								if (emoteId == null) {
									for (MessageReaction reaction : message.getReactions()) {
										if (reaction.getReactionEmote().getName().equals(emoteName)) {
											reaction.removeReaction(event.getSelfUser()).queue();
										}
									}
								} else {
									for (MessageReaction reaction : message.getReactions()) {
										if (reaction.getReactionEmote().isEmote()) {
											if (reaction.getReactionEmote().getIdLong() == emoteId) {
												reaction.removeReaction(event.getSelfUser()).queue();
											}
										}
									}
								}
							}
						}
						
						if (filter == null) {
							event.reply("There were no deleted roles on that reaction role :no_entry:").queue();
							return;
						}
						
						if (botMenu) {
							EmbedBuilder embed = new EmbedBuilder();
							embed.setDescription(newDescription);
							embed.setTitle(reactionRoleEmbed.getTitle());
							embed.setFooter(reactionRoleEmbed.getFooter().getText(), null);
								
							message.editMessage(embed.build()).queue();
						}
						
						UpdateOptions updateOptions = new UpdateOptions().arrayFilters(List.of(Filters.eq("reactionRole.id", messageId)));
						database.updateGuildById(event.getGuild().getIdLong(), null, Updates.pull("reactionRole.reactionRoles.$[reactionRole].roles", filter), updateOptions, (result, exception) -> {
							if (exception != null) {
								exception.printStackTrace();
								event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
							} else {
								event.reply("All deleted roles have been removed from that reaction role <:done:403285928233402378>").queue();
							}
						});
					}, e -> {
						if (e instanceof ErrorResponseException) {
							ErrorResponseException exception = (ErrorResponseException) e;
							if (exception.getErrorCode() == 10008) {
								database.updateGuildById(event.getGuild().getIdLong(), Updates.pull("reactionRole.reactionRoles", Filters.eq("id", messageId)), (result, writeException) -> {
									if (writeException != null) {
										writeException.printStackTrace();
										event.reply(Sx4CommandEventListener.getUserErrorMessage(writeException)).queue();
									} else {
										event.reply("I could not find that message :no_entry:").queue();
									}
								});
								
								return;
							}
						}
					});
					
					return;
				}
			}
			
			event.reply("I could not find that reaction role :no_entry:").queue();
		}
		
		@Command(value="refresh", description="Edits the reaction role message so the role mentions displayed change to their according colour/name", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@AuthorPermissions({Permission.MANAGE_ROLES})
		public void refresh(CommandEvent event, @Context Database database, @Argument(value="message id") long messageId) {			
			List<Document> reactionRoles = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("reactionRole.reactionRoles")).getEmbedded(List.of("reactionRole", "reactionRoles"), Collections.emptyList());
			for (Document reactionRole : reactionRoles) {
				if (reactionRole.getLong("id") == messageId) {
					TextChannel channel = event.getGuild().getTextChannelById(reactionRole.getLong("channelId"));
					if (channel == null) {
						database.updateGuildById(event.getGuild().getIdLong(), Updates.pull("reactionRole.reactionRoles", Filters.eq("channelId", reactionRole.getLong("channelId"))), (result, exception) -> {
							if (exception != null) {
								exception.printStackTrace();
								event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
							} else {
								event.reply("The channel which the reaction role was in has been deleted :no_entry:").queue();
							}
						});
						
						return;
					}
					
					channel.retrieveMessageById(messageId).queue(message -> {
						if (reactionRole.getBoolean("botMenu")) {
							message.editMessage(message.getEmbeds().get(0)).queue();
							event.reply("Refreshed the reaction role menu <:done:403285928233402378>").queue();
						} else {
							event.reply("You can only refresh reaction roles made by the bot :no_entry:").queue();
						}
					}, e -> {
						if (e instanceof ErrorResponseException) {
							ErrorResponseException exception = (ErrorResponseException) e;
							if (exception.getErrorCode() == 10008) {
								database.updateGuildById(event.getGuild().getIdLong(), Updates.pull("reactionRole.reactionRoles", Filters.eq("id", messageId)), (result, writeException) -> {
									if (writeException != null) {
										writeException.printStackTrace();
										event.reply(Sx4CommandEventListener.getUserErrorMessage(writeException)).queue();
									} else {
										event.reply("I could not find that message :no_entry:").queue();
									}
								});
								
								return;
							}
						}
					});
					
					return;
				}
			}
			
			event.reply("I could not find that reaction role :no_entry:").queue();
		}
		
		@Command(value="delete", description="Deletes a reaction roles data and message if it's a menu made by the bot", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@AuthorPermissions({Permission.MANAGE_ROLES})
		public void delete(CommandEvent event, @Context Database database, @Argument(value="message id") long messageId) {
			List<Document> reactionRoles = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("reactionRole.reactionRoles")).getEmbedded(List.of("reactionRole", "reactionRoles"), Collections.emptyList());
			for (Document reactionRole : reactionRoles) {
				if (reactionRole.getLong("id") == messageId) {
					Long channelId = reactionRole.getLong("channelId");
					TextChannel channel = channelId == null ? null : event.getGuild().getTextChannelById(channelId);
					if (channel == null) {
						database.updateGuildById(event.getGuild().getIdLong(), Updates.pull("reactionRole.reactionRoles", Filters.eq("channelId", channelId)), (result, exception) -> {
							if (exception != null) {
								exception.printStackTrace();
								event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
							} else {
								event.reply("The channel which the reaction role was in has been deleted :no_entry:").queue();
							}
						});
						
						return;
					}
					
					channel.retrieveMessageById(messageId).queue(message -> {
						if (reactionRole.getBoolean("botMenu")) {
							message.delete().queue();
						}
						
						database.updateGuildById(event.getGuild().getIdLong(), Updates.pull("reactionRole.reactionRoles", Filters.eq("id", messageId)), (result, exception) -> {
							if (exception != null) {
								exception.printStackTrace();
								event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
							} else {
								event.reply("That reaction role has been deleted <:done:403285928233402378>").queue();
							}
						});
					}, e -> {
						if (e instanceof ErrorResponseException) {
							ErrorResponseException exception = (ErrorResponseException) e;
							if (exception.getErrorCode() == 10008) {
								database.updateGuildById(event.getGuild().getIdLong(), Updates.pull("reactionRole.reactionRoles", Filters.eq("id", messageId)), (result, writeException) -> {
									if (writeException != null) {
										writeException.printStackTrace();
										event.reply(Sx4CommandEventListener.getUserErrorMessage(writeException)).queue();
									} else {
										event.reply("I could not find that message :no_entry:").queue();
									}
								});
								
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
		public void dmToggle(CommandEvent event, @Context Database database) {
			boolean dm = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("reactionRole.dm")).getEmbedded(List.of("reactionRole", "dm"), true);
			database.updateGuildById(event.getGuild().getIdLong(), Updates.set("reactionRole.dm", !dm), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("I will " + (dm ? "no longer" : "now") + " dm users when they are given a role <:done:403285928233402378>").queue();
				}
			});
		}
		
		@Command(value="max roles", aliases={"maxroles"}, description="Set the maximum amount of roles a user can have from a reaction role (0 turns it off)", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@AuthorPermissions({Permission.MANAGE_ROLES})
		public void maxRoles(CommandEvent event, @Context Database database, @Argument(value="message id") long messageId, @Argument(value="max roles") String maxRolesArgument) {
			int maxRoles;
			if (nullStrings.contains(maxRolesArgument)) {
				maxRoles = 0;
			} else {
				try {
					maxRoles = Integer.parseInt(maxRolesArgument);
				} catch(NumberFormatException e) {
					event.reply("Make sure that the max roles argument is a number :no_entry:").queue();
					return;
				}
				
				if (maxRoles < 0) {
					event.reply("Max roles cannot be less than 0 :no_entry:").queue();
					return;
				}
			}
			
			List<Document> reactionRoles = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("reactionRole.reactionRoles")).getEmbedded(List.of("reactionRole", "reactionRoles"), Collections.emptyList());
			for (Document reactionRole : reactionRoles) {
				if (reactionRole.getLong("id") == messageId) {
					int currentMaxRoles = reactionRole.getInteger("maxRoles", 0);
					if (maxRoles == currentMaxRoles) {
						event.reply("The max roles for that reaction role is already set to **" + currentMaxRoles + "** :no_entry:").queue();
						return;
					}
					
					UpdateOptions updateOptions = new UpdateOptions().arrayFilters(List.of(Filters.eq("reactionRole.id", messageId)));
					database.updateGuildById(event.getGuild().getIdLong(), null, Updates.set("reactionRole.reactionRoles.$[reactionRole].maxRoles", maxRoles), updateOptions, (result, exception) -> {
						if (exception != null) {
							exception.printStackTrace();
							event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
						} else {
							if (maxRoles != 0) {
								event.reply("The max roles for that reaction role is now **" + maxRoles + "** <:done:403285928233402378>").queue();
							} else {
								event.reply("There is no longer a limit to the amount of roles a user can have on that reaction role <:done:403285928233402378>").queue();
							}
						}
					});
					
					return;
				}
			}
			
			event.reply("I could not find that reaction role :no_entry:").queue();
		}
		
	}
	
	@Command(value="role", description="Assign a self role to yourself, view all self roles in `self roles list`")
	@BotPermissions({Permission.MANAGE_ROLES})
	public void role(CommandEvent event, @Context Database database, @Argument(value="role", endless=true) String roleArgument) {
		Role role = ArgumentUtils.getRole(event.getGuild(), roleArgument);
		if (role == null) {
			event.reply("I could not find that role :no_entry:").queue();
			return;
		}
		
		List<Long> roles = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("selfRoles")).getList("selfRoles", Long.class, Collections.emptyList());
		for (long roleId : roles) {
			if (roleId == role.getIdLong()) {
				if (event.getMember().getRoles().contains(role)) {
					event.reply("You no longer have the role **" + role.getName() + "** <:done:403285928233402378>").queue();
					event.getGuild().removeRoleFromMember(event.getMember(), role).queue();
				} else {
					event.reply("You now have the role **" + role.getName() + "** <:done:403285928233402378>").queue();
					event.getGuild().addRoleToMember(event.getMember(), role).queue();
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
		
		@Command(value="add", description="Add a role which can be gained by any user by simply using the `role` command")
		@AuthorPermissions({Permission.MANAGE_ROLES})
		public void add(CommandEvent event, @Context Database database, @Argument(value="role", endless=true) String roleArgument) {
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
			
			List<Long> roles = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("selfRoles")).getList("selfRoles", Long.class, Collections.emptyList());
			for (long roleId : roles) {
				if (roleId == role.getIdLong()) {
					event.reply("That role is already a self role :no_entry:").queue();
					return;
				}
			}
			
			database.updateGuildById(event.getGuild().getIdLong(), Updates.push("selfRoles", role.getIdLong()), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("Added `" + role.getName() + "` as a self role <:done:403285928233402378>").queue();
				}
			});
		}
		
		@Command(value="remove", description="Remove a role from the self roles in the current server")
		@AuthorPermissions({Permission.MANAGE_ROLES})
		public void remove(CommandEvent event, @Context Database database, @Argument(value="roles", endless=true) String roleArgument) {
			Role role = ArgumentUtils.getRole(event.getGuild(), roleArgument);
			if (role == null) {
				event.reply("I could not find that role :no_entry:").queue();
				return;
			}
			
			List<Long> roles = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("selfRoles")).getList("selfRoles", Long.class, Collections.emptyList());
			for (long roleId : roles) {
				if (roleId == role.getIdLong()) {
					database.updateGuildById(event.getGuild().getIdLong(), Updates.pull("selfRoles", role.getIdLong()), (result, exception) -> {
						if (exception != null) {
							exception.printStackTrace();
							event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
						} else {
							event.reply("Removed `" + role.getName() + "` from the self roles <:done:403285928233402378>").queue();
						}
					});
					
					return;
				}
			}
			
			event.reply("That role is not a self role :no_entry:").queue();
		}
		
		@Command(value="reset", aliases={"delete", "wipe"}, description="Deletes all self roles which are currently set", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@AuthorPermissions({Permission.MANAGE_ROLES})
		public void reset(CommandEvent event, @Context Database database) {
			event.reply(event.getAuthor().getName() + ", are you sure you want to reset all data for self roles? (Yes or No)").queue(message -> {
				PagedUtils.getConfirmation(event, 60, event.getAuthor(), confirmation -> {
					if (confirmation) {
						List<Long> roles = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("selfRoles")).getList("selfRoles", Long.class, Collections.emptyList());
						if (roles.isEmpty()) {
							event.reply("There are no self roles in this server :no_entry:").queue();
							return;
						}
						
						database.updateGuildById(event.getGuild().getIdLong(), Updates.unset("selfRoles"), (result, exception) -> {
							if (exception != null) {
								exception.printStackTrace();
								event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
							} else {
								event.reply("All self roles have been deleted <:done:403285928233402378>").queue();
							}
						});
					} else {
						event.reply("Cancelled <:done:403285928233402378>").queue();
					}
				});
			});
		}
		
		@Command(value="list", description="Lists all the self roles in the current server")
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void list(CommandEvent event, @Context Database database) {
			List<Long> roles = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("selfRoles")).getList("selfRoles", Long.class, Collections.emptyList());
			
			List<Role> selfRoles = new ArrayList<>();
			for (long roleId : roles) {
				Role role = event.getGuild().getRoleById(roleId);
				if (role != null) {
					selfRoles.add(role);
				}
			}
			
			if (selfRoles.isEmpty()) {
				event.reply("There are no self roles on this server :no_entry:").queue();
				return;
			}
			
			selfRoles.sort((a, b) -> Integer.compare(b.getPosition(), a.getPosition()));
			PagedResult<Role> paged = new PagedResult<>(selfRoles)
					.setDeleteMessage(false)
					.setPerPage(15)
					.setIncreasedIndex(true)
					.setAuthor("Self Roles (" + selfRoles.size() + ")", null, event.getGuild().getIconUrl())
					.setFunction(role -> role.getAsMention());
			
			PagedUtils.getPagedResult(event, paged, 300, null);
		}
		
	}
	
	@Initialize(all=true)
	public void initialize(CommandImpl command) {
		command.setCategory(Categories.SELF_ROLES);
	}

}
