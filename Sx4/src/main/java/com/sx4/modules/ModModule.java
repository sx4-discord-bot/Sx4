package com.sx4.modules;

import static com.rethinkdb.RethinkDB.r;

import java.awt.Color;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.jockie.bot.core.Context;
import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.category.impl.CategoryImpl;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.command.Command.Async;
import com.jockie.bot.core.command.Command.AuthorPermissions;
import com.jockie.bot.core.command.Command.BotPermissions;
import com.jockie.bot.core.command.ICommand;
import com.jockie.bot.core.command.ICommand.ContentOverflowPolicy;
import com.jockie.bot.core.command.Initialize;
import com.jockie.bot.core.command.impl.CommandEvent;
import com.jockie.bot.core.command.impl.CommandImpl;
import com.jockie.bot.core.module.Module;
import com.rethinkdb.gen.ast.Get;
import com.rethinkdb.model.OptArgs;
import com.rethinkdb.net.Connection;
import com.sx4.categories.Categories;
import com.sx4.core.Sx4Bot;
import com.sx4.core.Sx4Command;
import com.sx4.events.MuteEvents;
import com.sx4.utils.ArgumentUtils;
import com.sx4.utils.GeneralUtils;
import com.sx4.utils.HelpUtils;
import com.sx4.utils.ModUtils;
import com.sx4.utils.PagedUtils;
import com.sx4.utils.PagedUtils.PagedResult;
import com.sx4.utils.TimeUtils;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.Region;
import net.dv8tion.jda.core.entities.Channel;
import net.dv8tion.jda.core.entities.Emote;
import net.dv8tion.jda.core.entities.Icon;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.Message.Attachment;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.entities.PermissionOverride;
import net.dv8tion.jda.core.entities.Role;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.entities.VoiceChannel;
import net.dv8tion.jda.core.exceptions.ErrorResponseException;
import okhttp3.Request;
import okhttp3.Response;

@Module
public class ModModule {

	@Command(value="create emote", aliases={"createemote", "create emoji", "createemoji"}, description="Allows you to create an emote in your server by providing an emote name/id/mention, attachment or image url", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@AuthorPermissions({Permission.MANAGE_EMOTES})
	@BotPermissions({Permission.MANAGE_EMOTES})
	@Async
	public void createEmote(CommandEvent event, @Argument(value="emote | image", nullDefault=true) String argument) {
		if (argument != null) {
			Emote emote = ArgumentUtils.getEmote(event.getGuild(), argument);
			if (emote != null) {
				try {
					event.getGuild().getController().createEmote(emote.getName(), Icon.from(new URL(emote.getImageUrl()).openStream())).queue(e -> {
						event.reply(e.getAsMention() + " has been created <:done:403285928233402378>").queue();
					}, e -> {
						if (e instanceof ErrorResponseException) {
							ErrorResponseException exception = (ErrorResponseException) e;
							if (exception.getErrorCode() == 30008) {
								event.reply("The server has reached the maximum emotes it can have :no_entry:").queue();
								return;
							}
						}
					});
				} catch (MalformedURLException e) {
					e.getMessage();
				} catch (IOException e) {
					event.reply("Oops something went wrong there, try again :no_entry:").queue();
					return;
				} 
			} else {
				String url = null, name = null;
				Request request;
				String id = null;
				if (!event.getMessage().getAttachments().isEmpty()) {	
					for (Attachment attachment : event.getMessage().getAttachments()) {
						if (attachment.isImage()) {
							url = attachment.getUrl();
							name = attachment.getFileName().replace("-", "_").replace(" ", "_");
							int periodIndex = name.lastIndexOf(".");
							name = name.substring(0, periodIndex);
							break;
						}
					}
				} else if (argument.matches("<(?:a|):(.{2,32}):(\\d+)>")) {
					id = argument.split(":")[2];
					id = id.substring(0, id.length() - 1);	
					System.out.println(id);
				} else if (argument.matches("\\d+")) {
					id = argument;
				} else {
					try {
						request = new Request.Builder().url(argument).build();
					} catch(IllegalArgumentException e) {
						event.reply("You didn't provide a valid url :no_entry:").queue();
						return;
					}
					try {
						Response response = Sx4Bot.client.newCall(request).execute();
						if (response.code() == 200) {
							String type;
							if (response.header("Content-Type") != null && response.header("Content-Type").contains("/")) {
								type = response.header("Content-Type").split("/")[1];
							} else {
								event.reply("The url you provided wasn't an image or a gif :no_entry:").queue();
								return;
							}
							if (type.equals("gif") || type.equals("png") || type.equals("jpg") || type.equals("jpeg")) {
								url = argument;
							} else {
								event.reply("The url you provided wasn't an image or a gif :no_entry:").queue();
								return;
							}
						} else {
							event.reply("The url you provided was invalid :no_entry:").queue();
							return;
						}
					} catch (IOException e) {
						event.reply("Oops something went wrong there, try again :no_entry:").queue();
						return;
					}
				}
				
				if (id != null) {
					try {
						request = new Request.Builder().url("https://cdn.discordapp.com/emojis/" + id + ".gif").build();
					} catch(IllegalArgumentException e) {
						event.reply("You didn't provide a valid url :no_entry:").queue();
						return;
					}
					try {
						Response response = Sx4Bot.client.newCall(request).execute();
						if (response.code() == 415) {
							url = "https://cdn.discordapp.com/emojis/" + id + ".png";
						} else if (response.code() == 200) {
							url = "https://cdn.discordapp.com/emojis/" + id + ".gif";
						} else {
							event.reply("I could not find that emote :no_entry:").queue();
							return;
						}
					} catch (IOException e) {
						event.reply("Oops something went wrong there, try again :no_entry:").queue();
						return;
					}
				}
				
				if (url == null) {
					event.reply("None of the attachments you supplied were images or gifs :no_entry:").queue();
					return;
				}
				
				try {
					event.getGuild().getController().createEmote(name == null ? "Unnamed_Emote" : name, Icon.from(new URL(url).openStream())).queue(e -> {
						event.reply(e.getAsMention() + " has been created <:done:403285928233402378>").queue();
					}, e -> {
						if (e instanceof ErrorResponseException) {
							ErrorResponseException exception = (ErrorResponseException) e;
							if (exception.getErrorCode() == 30008) {
								event.reply("The server has reached the maximum of 50 emotes :no_entry:").queue();
								return;
							}
						}
					});
				} catch (MalformedURLException e) {
					e.getMessage();
				} catch (IOException e) {
					event.reply("Oops something went wrong there, try again :no_entry:").queue();
					return;
				}
			}
		} else {
			if (!event.getMessage().getAttachments().isEmpty()) {
				for (Attachment attachment : event.getMessage().getAttachments()) {
					if (attachment.isImage()) {
						try {
							String name = attachment.getFileName().replace("-", "_").replace(" ", "_");
							int periodIndex = name.lastIndexOf(".");
							name = name.substring(0, periodIndex);
							event.getGuild().getController().createEmote(name, attachment.getAsIcon()).queue(e -> {
								event.reply(e.getAsMention() + " has been created <:done:403285928233402378>").queue();
							}, e -> {
								if (e instanceof ErrorResponseException) {
									ErrorResponseException exception = (ErrorResponseException) e;
									if (exception.getErrorCode() == 30008) {
										event.reply("The server has reached the maximum of 50 emotes :no_entry:").queue();
										return;
									}
								}
							});
						} catch (IOException e) {
							event.reply("Oops something went wrong there, try again :no_entry:").queue();
							return;
						} 
						return;
					}
				}
				
				event.reply("None of the attachments you supplied were images or gifs :no_entry:").queue();
			} else {
				event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
			}
		}
	}
	
	public class CreateChannelCommand extends Sx4Command {
		
		public CreateChannelCommand() {
			super("create channel");
			
			super.setDescription("Create a voice or text channel");
			super.setAliases("createchannel", "cc");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="text", description="Create a text channel in the current server")
		@AuthorPermissions({Permission.MANAGE_CHANNEL})
		@BotPermissions({Permission.MANAGE_CHANNEL})
		public void text(CommandEvent event, @Argument(value="channel name", endless=true) String textChannelName) {
			if (textChannelName.length() > 100) {
				event.reply("Text channel names can not be longer than 100 characters :no_entry:").queue();
				return;
			}
			event.getGuild().getController().createTextChannel(textChannelName).queue(channel -> {
				event.reply("Created the text channel <#" + channel.getId() + "> <:done:403285928233402378>").queue();
			});
		}
		
		@Command(value="voice", description="Create a voice channel in the current server")
		@AuthorPermissions({Permission.MANAGE_CHANNEL})
		@BotPermissions({Permission.MANAGE_CHANNEL})
		public void voice(CommandEvent event, @Argument(value="channel name", endless=true) String voiceChannelName) {
			if (voiceChannelName.length() > 100) {
				event.reply("Voice channel names can not be longer than 100 characters :no_entry:").queue();
				return;
			}
			event.getGuild().getController().createVoiceChannel(voiceChannelName).queue(channel -> {
				event.reply("Created the voice channel `" + channel.getName() + "` <:done:403285928233402378>").queue();
			});
		}
		
	}
	
	public class DeleteChannelCommand extends Sx4Command {
		
		public DeleteChannelCommand() {
			super("delete channel");
			
			super.setDescription("Delete a voice or text channel");
			super.setAliases("deletechannel", "dc");
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="text", description="Delete a specified text channel")
		@AuthorPermissions({Permission.MANAGE_CHANNEL})
		@BotPermissions({Permission.MANAGE_CHANNEL})
		public void text(CommandEvent event, @Argument(value="channel", endless=true) String argument) {
			TextChannel channel = ArgumentUtils.getTextChannel(event.getGuild(), argument);
			if (channel == null) {
				event.reply("I could not find that text channel :no_entry:").queue();
				return;
			}
			
			channel.delete().queue();
			event.reply("Deleted the text channel `" + channel.getName() + "` <:done:403285928233402378>").queue();
		}
		
		@Command(value="voice", description="Delete a specified voice channel")
		@AuthorPermissions({Permission.MANAGE_CHANNEL})
		@BotPermissions({Permission.MANAGE_CHANNEL})
		public void voice(CommandEvent event, @Argument(value="channel", endless=true) String argument) {
			VoiceChannel channel = ArgumentUtils.getVoiceChannel(event.getGuild(), argument);
			if (channel == null) {
				event.reply("I could not find that voice channel :no_entry:").queue();
				return;
			}
			
			channel.delete().queue();
			event.reply("Deleted the voice channel `" + channel.getName() + "` <:done:403285928233402378>").queue();
		}
		
	}
	
	@Command(value="voice kick", aliases={"kick voice", "kickvoice", "voicekick", "vk"}, description="Kicks a user from their current voice channel, It will disconnect the user")
	@AuthorPermissions({Permission.VOICE_MOVE_OTHERS})
	@BotPermissions({Permission.VOICE_MOVE_OTHERS, Permission.MANAGE_CHANNEL})
	public void voiceKick(CommandEvent event, @Argument(value="user", endless=true) String argument) {
		Member member = ArgumentUtils.getMember(event.getGuild(), argument);
		if (member == null) {
			event.reply("I could not find that user :no_entry:").queue();
			return;
		}
		
		if (member.equals(event.getMember())) {
			event.reply("You cannot voice kick yourself :no_entry:").queue();
			return;
		}
		
		if (member.getVoiceState().getChannel() == null) {
			event.reply("That user is not in a voice channel :no_entry:").queue();
			return;
		}
		
		event.getGuild().getController().createVoiceChannel("Temporary Voice Kick Channel").queue(channel -> {
			event.getGuild().getController().moveVoiceMember(member, (VoiceChannel) channel).queue($ -> {
				channel.delete().queue();
				event.reply("**" + member.getUser().getAsTag() + "** has been voice kicked <:done:403285928233402378>:ok_hand:").queue();
			});
		});
	}
	
	@Command(value="clear reactions", aliases={"remove reactions", "removereactions", "clearreactions"}, description="Clears all the reactions off a message, has to be executed in the same channel as the message to work", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@AuthorPermissions({Permission.MESSAGE_MANAGE})
	@BotPermissions({Permission.MESSAGE_MANAGE})
	public void clearReactions(CommandEvent event, @Argument(value="message id") String messageId) {
		try {
			event.getTextChannel().getMessageById(messageId).queue(message -> {
				message.clearReactions().queue();
				event.reply("Cleared all reactions from that message <:done:403285928233402378>").queue();
			}, e -> {
				if (e instanceof ErrorResponseException) {
	    			ErrorResponseException exception = (ErrorResponseException) e;
	    			if (exception.getErrorCode() == 10008) {
	    				event.reply("I could not find that message within this channel :no_entry:").queue();
	    				return;
	    			}
			    }
			});
		} catch(IllegalArgumentException e) {
			event.reply("I could not find that message within this channel :no_entry:").queue();
		}
	}
	
	public class BlacklistCommand extends Sx4Command {
		
		public BlacklistCommand() {
			super("blacklist");
			
			super.setDescription("Blacklist roles/users/channels from being able to use specific commands/modules");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="add", description="Add a role/user/channel to be blacklisted from a specified command/module")
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void add(CommandEvent event, @Context Connection connection, @Argument(value="user | role | channel") String argument, @Argument(value="command | module", endless=true) String commandArgument) {
			r.table("blacklist").insert(r.hashMap("id", event.getGuild().getId()).with("commands", new Object[0]).with("disabled", new Object[0])).run(connection, OptArgs.of("durability", "soft"));
			Get data = r.table("blacklist").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			CategoryImpl module = ArgumentUtils.getModule(commandArgument);
			ICommand command = ArgumentUtils.getCommand(commandArgument);
			if (command == null && module == null) {
				event.reply("I could not find that command/module :no_entry:").queue();
				return;
			}
			String commandName = command == null ? module.getName() : command.getCommandTrigger(); 
			
			Role role = ArgumentUtils.getRole(event.getGuild(), argument);
			Member member = ArgumentUtils.getMember(event.getGuild(), argument);
			Channel channel = ArgumentUtils.getTextChannelOrParent(event.getGuild(), argument);
			if (channel == null && role == null && member == null) {
				event.reply("I could not find that user/role/channel :no_entry:").queue();
				return;
			}
			
			List<Map<String, Object>> commands = (List<Map<String, Object>>) dataRan.get("commands");
			if (channel != null) {
				String channelDisplay = channel instanceof TextChannel ? ((TextChannel) channel).getAsMention() : channel.getName();
				for (Map<String, Object> commandData : commands) {
					if (commandData.get("id").equals(commandName)) {
						List<Map<String, String>> blacklisted = (List<Map<String, String>>) commandData.get("blacklisted");					
						for (Map<String, String> blacklist : blacklisted) {
							if (blacklist.get("type").equals("channel")) {
								if (channel.getId().equals(blacklist.get("id"))) {
									event.reply("The channel " + channelDisplay + " is already blacklisted from using that " + (command == null ? "module" : "command") + " :no_entry:").queue();
									return;
								}
							}
						}
						
						event.reply("The " + (command == null ? "module" : "command") + " `" + commandName + "` is now blacklisted in " + channelDisplay + " <:done:403285928233402378>").queue();
						Map<String, String> newMap = new HashMap<String, String>();
						newMap.put("type", "channel");
						newMap.put("id", channel.getId());
						blacklisted.add(newMap);
						commands.remove(commandData);
						commandData.put("blacklisted", blacklisted);
						commands.add(commandData);
						data.update(r.hashMap("commands", commands)).runNoReply(connection);
						return;
					}
				}
				
				event.reply("The " + (command == null ? "module" : "command") + " `" + commandName + "` is now blacklisted in " + channelDisplay + " <:done:403285928233402378>").queue();
				data.update(row -> {
					return r.hashMap("commands", row.g("commands").append(r.hashMap("id", commandName)
							.with("blacklisted", List.of(r.hashMap("id", channel.getId()).with("type", "channel")))
							.with("whitelisted", new Object[0])));
				}).runNoReply(connection);
			} else if (role != null) {
				for (Map<String, Object> commandData : commands) {
					if (commandData.get("id").equals(commandName)) {
						List<Map<String, String>> blacklisted = (List<Map<String, String>>) commandData.get("blacklisted");					
						for (Map<String, String> blacklist : blacklisted) {
							if (blacklist.get("type").equals("role")) {
								if (role.getId().equals(blacklist.get("id"))) {
									event.reply("The role `" + role.getName() + "` is already blacklisted from using that " + (command == null ? "module" : "command") + " :no_entry:").queue();
									return;
								}
							}
						}
						
						event.reply("The " + (command == null ? "module" : "command") + " `" + commandName + "` is now blacklisted for anyone with the role `" + role.getName() + "` <:done:403285928233402378>").queue();
						Map<String, String> newMap = new HashMap<String, String>();
						newMap.put("type", "role");
						newMap.put("id", role.getId());
						blacklisted.add(newMap);
						commands.remove(commandData);
						commandData.put("blacklisted", blacklisted);
						commands.add(commandData);
						data.update(r.hashMap("commands", commands)).runNoReply(connection);
						return;
					}
				}
				
				event.reply("The " + (command == null ? "module" : "command") + " `" + commandName + "` is now blacklisted for anyone with the role `" + role.getName() + "` <:done:403285928233402378>").queue();
				data.update(row -> {
					return r.hashMap("commands", row.g("commands").append(r.hashMap("id", commandName)
							.with("blacklisted", List.of(r.hashMap("id", role.getId()).with("type", "role")))
							.with("whitelisted", new Object[0])));
				}).runNoReply(connection);
			} else if (member != null) {
				for (Map<String, Object> commandData : commands) {
					if (commandData.get("id").equals(commandName)) {
						List<Map<String, String>> blacklisted = (List<Map<String, String>>) commandData.get("blacklisted");					
						for (Map<String, String> blacklist : blacklisted) {
							if (blacklist.get("type").equals("user")) {
								if (member.getUser().getId().equals(blacklist.get("id"))) {
									event.reply("The user `" + member.getUser().getAsTag() + "` is already blacklisted from using that " + (command == null ? "module" : "command") + " :no_entry:").queue();
									return;
								}
							}
						}
						
						event.reply("**" + member.getUser().getAsTag() + "** is now blacklisted from using `" + commandName + "` in this server <:done:403285928233402378>").queue();
						Map<String, String> newMap = new HashMap<String, String>();
						newMap.put("type", "user");
						newMap.put("id", member.getUser().getId());
						blacklisted.add(newMap);
						commands.remove(commandData);
						commandData.put("blacklisted", blacklisted);
						commands.add(commandData);
						data.update(r.hashMap("commands", commands)).runNoReply(connection);
						return;
					}
				}
				
				event.reply("**" + member.getUser().getAsTag() + "** is now blacklisted from using `" + commandName + "` in this server <:done:403285928233402378>").queue();
				data.update(row -> {
					return r.hashMap("commands", row.g("commands").append(r.hashMap("id", commandName)
							.with("blacklisted", List.of(r.hashMap("id", member.getUser().getId()).with("type", "user")))
							.with("whitelisted", new Object[0])));
				}).runNoReply(connection);
			}
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="remove", description="Remove a blacklist from a user/role/channel from a specified command/module")
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void remove(CommandEvent event, @Context Connection connection, @Argument(value="user | role | channel") String argument, @Argument(value="command | module", endless=true) String commandArgument) {
			Get data = r.table("blacklist").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			if (dataRan == null) {
				event.reply("No blacklist data has been created for this server, so there is nothing to remove :no_entry:").queue();
				return;
			}
			
			CategoryImpl module = ArgumentUtils.getModule(commandArgument);
			ICommand command = ArgumentUtils.getCommand(commandArgument);
			if (command == null && module == null) {
				event.reply("I could not find that command/module :no_entry:").queue();
				return;
			}
			String commandName = command == null ? module.getName() : command.getCommandTrigger(); 
			
			Role role = ArgumentUtils.getRole(event.getGuild(), argument);
			Member member = ArgumentUtils.getMember(event.getGuild(), argument);
			Channel channel = ArgumentUtils.getTextChannelOrParent(event.getGuild(), argument);
			if (channel == null && role == null && member == null) {
				event.reply("I could not find that user/role/channel :no_entry:").queue();
				return;
			}
			
			List<Map<String, Object>> commands = (List<Map<String, Object>>) dataRan.get("commands");
			if (channel != null) {
				String channelDisplay = channel instanceof TextChannel ? ((TextChannel) channel).getAsMention() : channel.getName();
				for (Map<String, Object> commandData : commands) {
					if (commandData.get("id").equals(commandName)) {
						List<Map<String, String>> blacklisted = (List<Map<String, String>>) commandData.get("blacklisted"), whitelisted = (List<Map<String, String>>) commandData.getOrDefault("whitelisted", List.of());	
						for (Map<String, String> blacklist : blacklisted) {
							if (blacklist.get("type").equals("channel")) {
								if (channel.getId().equals(blacklist.get("id"))) {
									event.reply("The " + (command == null ? "module" : "command") + " `" + commandName + "` is no longer blacklisted in " + channelDisplay + " <:done:403285928233402378>").queue();
									blacklisted.remove(blacklist);
									commands.remove(commandData);
									if (!blacklisted.isEmpty() || !whitelisted.isEmpty()) {
										commandData.put("blacklisted", blacklisted);
										commands.add(commandData);
									}
									data.update(r.hashMap("commands", commands)).runNoReply(connection);
									return;
								}
							}
						}
					}
				}
				
				event.reply("The channel " + channelDisplay + " is not blacklisted from using that " + (command == null ? "module" : "command") + " :no_entry:").queue();
			} else if (role != null) {
				for (Map<String, Object> commandData : commands) {
					if (commandData.get("id").equals(commandName)) {
						List<Map<String, String>> blacklisted = (List<Map<String, String>>) commandData.get("blacklisted"), whitelisted = (List<Map<String, String>>) commandData.getOrDefault("whitelisted", List.of());	
						for (Map<String, String> blacklist : blacklisted) {
							if (blacklist.get("type").equals("role")) {
								if (role.getId().equals(blacklist.get("id"))) {
									event.reply("The " + (command == null ? "module" : "command") + " `" + commandName + "` is no longer blacklisted if a user has the role `" + role.getName() + "` <:done:403285928233402378>").queue();
									blacklisted.remove(blacklist);
									commands.remove(commandData);
									if (!blacklisted.isEmpty() || !whitelisted.isEmpty()) {
										commandData.put("blacklisted", blacklisted);
										commands.add(commandData);
									}
									data.update(r.hashMap("commands", commands)).runNoReply(connection);
									return;
								}
							}
						}
					}
				}
				
				event.reply("The role `" + role.getName() + "` is not blacklisted from using that " + (command == null ? "module" : "command") + " :no_entry:").queue();
			} else if (member != null) {
				for (Map<String, Object> commandData : commands) {
					if (commandData.get("id").equals(commandName)) {
						List<Map<String, String>> blacklisted = (List<Map<String, String>>) commandData.get("blacklisted"), whitelisted = (List<Map<String, String>>) commandData.getOrDefault("whitelisted", List.of());	
						for (Map<String, String> blacklist : blacklisted) {
							if (blacklist.get("type").equals("user")) {
								if (member.getUser().getId().equals(blacklist.get("id"))) {
									event.reply("**" + member.getUser().getAsTag() + "** is no longer blacklisted from using `" + commandName + "` in this server <:done:403285928233402378>").queue();
									blacklisted.remove(blacklist);
									commands.remove(commandData);
									if (!blacklisted.isEmpty() || !whitelisted.isEmpty()) {
										commandData.put("blacklisted", blacklisted);
										commands.add(commandData);
									}
									data.update(r.hashMap("commands", commands)).runNoReply(connection);
									return;
								}
							}
						}
					}
				}
				
				event.reply("The user `" + member.getUser().getAsTag() + "` is not blacklisted from using that " + (command == null ? "module" : "command") + " :no_entry:").queue();
			}
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="delete", aliases={"del"}, description="Deletes all the blacklist data for a specified command or module")
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void delete(CommandEvent event, @Context Connection connection, @Argument(value="command | module", endless=true) String commandArgument) {
			Get data = r.table("blacklist").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			if (dataRan == null) {
				event.reply("No blacklist data has been created for this server, so there is nothing to delete :no_entry:").queue();
				return;
			}
			
			CategoryImpl module = ArgumentUtils.getModule(commandArgument);
			ICommand command = ArgumentUtils.getCommand(commandArgument);
			if (command == null && module == null) {
				event.reply("I could not find that command/module :no_entry:").queue();
				return;
			}
			String commandName = command == null ? module.getName() : command.getCommandTrigger();
			
			List<Map<String, Object>> commands = (List<Map<String, Object>>) dataRan.get("commands");
			for (Map<String, Object> commandData : commands) {
				if (commandData.get("id").equals(commandName)) {
					List<Map<String, String>> blacklisted = (List<Map<String, String>>) commandData.get("blacklisted"), whitelisted = (List<Map<String, String>>) commandData.getOrDefault("whitelisted", List.of());
					if (blacklisted.isEmpty()) {
						event.reply("Nothing is blacklisted for that " + (command == null ? "module" : "command") + " :no_entry:").queue();
						return;
					}
					
					event.reply("All blacklist data for `" + commandName + "` has been deleted <:done:403285928233402378>").queue();
					commands.remove(commandData);
					if (!whitelisted.isEmpty()) {
						commandData.put("blacklisted", new Object[0]);
						commands.add(commandData);
					}
					data.update(r.hashMap("commands", commands)).runNoReply(connection);
					return;
				}
			}
			
			event.reply("Nothing is blacklisted for that " + (command == null ? "module" : "command") + " :no_entry:").queue();
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="reset", aliases={"wipe"}, description="Wipes all blacklist data set in the server, it will give you a prompt to confirm this decision", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void reset(CommandEvent event, @Context Connection connection) {
			Get data = r.table("blacklist").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			if (dataRan == null) {
				event.reply("No blacklist data has been created for this server, so there is nothing to reset :no_entry:").queue();
				return;
			}
			
			event.reply(event.getAuthor().getName() + ", are you sure you want to wipe all blacklist data? (Yes or No)").queue(message -> {
				PagedUtils.getConfirmation(event, 60, event.getAuthor(), confirmed -> {
					if (confirmed == true) {
						event.reply("All blacklist data has been deleted <:done:403285928233402378>").queue();
						message.delete().queue();
						
						List<Map<String, Object>> commands = (List<Map<String, Object>>) dataRan.get("commands");
						for (Map<String, Object> commandData : new ArrayList<>(commands)) {
							List<Map<String, String>> whitelisted = (List<Map<String, String>>) commandData.getOrDefault("whitelisted", List.of());
							
							commands.remove(commandData);
							if (!whitelisted.isEmpty()) {
								commandData.put("blacklisted", new Object[0]);
								commands.add(commandData);
							}
						}
						
						data.update(r.hashMap("commands", commands)).runNoReply(connection);
					} else {
						event.reply("Cancelled <:done:403285928233402378>").queue();
						message.delete().queue();
					}
				});
			});
			
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="toggle", aliases={"enable", "disable"}, description="Enable or disable a command or module in the current server")
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void toggle(CommandEvent event, @Context Connection connection, @Argument(value="command | module", endless=true) String argument) {
			r.table("blacklist").insert(r.hashMap("id", event.getGuild().getId()).with("commands", new Object[0]).with("disabled", new Object[0])).run(connection, OptArgs.of("durability", "soft"));
			Get data = r.table("blacklist").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			CategoryImpl module = ArgumentUtils.getModule(argument);
			ICommand command = ArgumentUtils.getCommand(argument);
			if (command == null && module == null) {
				event.reply("I could not find that command/module :no_entry:").queue();
				return;
			}
			String commandName = command == null ? module.getName() : command.getCommandTrigger();
			
			for (String disabledCommand : (List<String>) dataRan.get("disabled")) {
				if (disabledCommand.equals(commandName)) {
					event.reply("The " + (command == null ? "module" : "command") + " `" + commandName + "` is no longer disabled in this server <:done:403285928233402378>").queue();
					data.update(row -> r.hashMap("disabled", row.g("disabled").filter(d -> d.ne(commandName)))).runNoReply(connection);
					return;
				}
			}
			
			event.reply("The " + (command == null ? "module" : "command") + " `" + commandName + "` is now disabled in this server <:done:403285928233402378>").queue();
			data.update(row -> r.hashMap("disabled", row.g("disabled").append(commandName))).runNoReply(connection);
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="disabled", aliases={"disabled commands", "disabledcommands", "disabled modules", "disabledmodules"}, description="View all the disabled commands on the current server", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		public void disabledCommands(CommandEvent event, @Context Connection connection) {
			Map<String, Object> data = r.table("blacklist").get(event.getGuild().getId()).run(connection);
			if (data == null) {
				event.reply("There are no disabled commands or modules in this server :no_entry:").queue();
				return;
			}
			
			List<String> disabled = (List<String>) data.get("disabled");
			List<String> disabledCommands = new ArrayList<String>();
			
			CategoryImpl module;
			ICommand command;
			for (String disabledCommand : disabled) {
				module = ArgumentUtils.getModule(disabledCommand);
				command = ArgumentUtils.getCommand(disabledCommand);
				if (command != null || module != null) {
					disabledCommands.add(disabledCommand + " (" + (command == null ? "Module" : "Command") + ")");
				}
			}
			
			if (disabledCommands.isEmpty()) {
				event.reply("There are no disabled commands or modules in this server :no_entry:").queue();
				return;
			}
			
			PagedResult<String> paged = new PagedResult<>(disabledCommands)
					.setAuthor("Disabled Commands/Modules", null, event.getGuild().getIconUrl())
					.setDeleteMessage(false)
					.setIndexed(false)
					.setPerPage(15);
			
			PagedUtils.getPagedResult(event, paged, 300, null);
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="info", description="View all the users, roles and channels which are blacklisted from using a specified command or module")
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void info(CommandEvent event, @Context Connection connection, @Argument(value="command | module", endless=true) String argument) {
			Map<String, Object> data = r.table("blacklist").get(event.getGuild().getId()).run(connection);
			if (data == null) {
				event.reply("There is no blacklist data for this server, so no one is blacklisted from using that command/module :no_entry:").queue();
				return;
			}
			
			CategoryImpl module = ArgumentUtils.getModule(argument);
			ICommand command = ArgumentUtils.getCommand(argument);
			if (command == null && module == null) {
				event.reply("I could not find that command/module :no_entry:").queue();
				return;
			}
			String commandName = command == null ? module.getName() : command.getCommandTrigger();
			
			for (Map<String, Object> commandData : (List<Map<String, Object>>) data.get("commands")) {
				if (commandData.get("id").equals(commandName)) {
					List<Map<String, String>> blacklisted = (List<Map<String, String>>) commandData.get("blacklisted");
					List<String> viewedBlacklisted = new ArrayList<String>();
					
					TextChannel channel;
					Role role;
					Member member;
					for (Map<String, String> blacklist : blacklisted) {
						if (blacklist.get("type").equals("channel")) {
							channel = event.getGuild().getTextChannelById(blacklist.get("id"));
							if (channel != null) {
								viewedBlacklisted.add(channel.getAsMention());
							} 
						} else if (blacklist.get("type").equals("role")) {
							role = event.getGuild().getRoleById(blacklist.get("id"));
							if (role != null) {
								viewedBlacklisted.add(role.getAsMention());
							}
						} else if (blacklist.get("type").equals("user")) {
							member = event.getGuild().getMemberById(blacklist.get("id"));       
							if (member != null) {
								viewedBlacklisted.add(member.getUser().getAsTag());
							}
						}
					}
					
					if (viewedBlacklisted.isEmpty()) {
						event.reply("Nothing is blacklisted from using that " + (command == null ? "module" : "command") + " :no_entry:").queue();
						return;
					}
					
					PagedResult<String> paged = new PagedResult<>(viewedBlacklisted)
							.setDeleteMessage(false)
							.setIncreasedIndex(true)
							.setPerPage(15)
							.setAuthor("Blacklisted from " + commandName, null, event.getGuild().getIconUrl());
				
					PagedUtils.getPagedResult(event, paged, 300, null);
					return;
				}
			}
			
			event.reply("Nothing is blacklisted from using that " + (command == null ? "module" : "command") + " :no_entry:").queue();
		}
		
	}
	
	public class WhitelistCommand extends Sx4Command {
		
		public WhitelistCommand() {
			super ("whitelist");
			
			super.setDescription("Whitelist roles/users/channels so they can use specific commands/modules, whitelists override blacklists");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="add", description="Add a role/user/channel to be whitelisted to use a specified command/module")
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void add(CommandEvent event, @Context Connection connection, @Argument(value="user | role | channel") String argument, @Argument(value="command | module", endless=true) String commandArgument) {
			r.table("blacklist").insert(r.hashMap("id", event.getGuild().getId()).with("commands", new Object[0]).with("disabled", new Object[0])).run(connection, OptArgs.of("durability", "soft"));
			Get data = r.table("blacklist").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			CategoryImpl module = ArgumentUtils.getModule(commandArgument);
			ICommand command = ArgumentUtils.getCommand(commandArgument);
			if (command == null && module == null) {
				event.reply("I could not find that command/module :no_entry:").queue();
				return;
			}
			String commandName = command == null ? module.getName() : command.getCommandTrigger(); 
			
			Role role = ArgumentUtils.getRole(event.getGuild(), argument);
			Member member = ArgumentUtils.getMember(event.getGuild(), argument);
			Channel channel = ArgumentUtils.getTextChannelOrParent(event.getGuild(), argument);
			if (channel == null && role == null && member == null) {
				event.reply("I could not find that user/role/channel :no_entry:").queue();
				return;
			}
			
			List<Map<String, Object>> commands = (List<Map<String, Object>>) dataRan.get("commands");
			if (channel != null) {
				String channelDisplay = channel instanceof TextChannel ? ((TextChannel) channel).getAsMention() : channel.getName();
				for (Map<String, Object> commandData : commands) {
					if (commandData.get("id").equals(commandName)) {
						List<Map<String, String>> whitelisted = (List<Map<String, String>>) commandData.getOrDefault("whitelisted", List.of());
						for (Map<String, String> whitelist : whitelisted) {
							if (whitelist.get("type").equals("channel")) {
								if (channel.getId().equals(whitelist.get("id"))) {
									event.reply("The channel " + channelDisplay + " is already whitelisted to use that " + (command == null ? "module" : "command") + " :no_entry:").queue();
									return;
								}
							}
						}
						
						event.reply("The " + (command == null ? "module" : "command") + " `" + commandName + "` is now whitelisted in " + channelDisplay + " <:done:403285928233402378>").queue();
						Map<String, String> newMap = new HashMap<String, String>();
						newMap.put("type", "channel");
						newMap.put("id", channel.getId());
						whitelisted.add(newMap);
						commands.remove(commandData);
						commandData.put("whitelisted", whitelisted);
						commands.add(commandData);
						data.update(r.hashMap("commands", commands)).runNoReply(connection);
						return;
					}
				}
				
				event.reply("The " + (command == null ? "module" : "command") + " `" + commandName + "` is now whitelisted in " + channelDisplay + " <:done:403285928233402378>").queue();
				data.update(row -> {
					return r.hashMap("commands", row.g("commands").append(r.hashMap("id", commandName)
							.with("whitelisted", List.of(r.hashMap("id", channel.getId()).with("type", "channel")))
							.with("blacklisted", new Object[0])));
				}).runNoReply(connection);
			} else if (role != null) {
				for (Map<String, Object> commandData : commands) {
					if (commandData.get("id").equals(commandName)) {
						List<Map<String, String>> whitelisted = (List<Map<String, String>>) commandData.getOrDefault("whitelisted", List.of());		
						for (Map<String, String> whitelist : whitelisted) {
							if (whitelist.get("type").equals("role")) {
								if (role.getId().equals(whitelist.get("id"))) {
									event.reply("The role `" + role.getName() + "` is already whitelisted to use that " + (command == null ? "module" : "command") + " :no_entry:").queue();
									return;
								}
							}
						}
						
						event.reply("The " + (command == null ? "module" : "command") + " `" + commandName + "` is now whitelisted for anyone with the role `" + role.getName() + "` <:done:403285928233402378>").queue();
						Map<String, String> newMap = new HashMap<String, String>();
						newMap.put("type", "role");
						newMap.put("id", role.getId());
						whitelisted.add(newMap);
						commands.remove(commandData);
						commandData.put("whitelisted", whitelisted);
						commands.add(commandData);
						data.update(r.hashMap("commands", commands)).runNoReply(connection);
						return;
					}
				}
				
				event.reply("The " + (command == null ? "module" : "command") + " `" + commandName + "` is now whitelisted for anyone with the role `" + role.getName() + "` <:done:403285928233402378>").queue();
				data.update(row -> {
					return r.hashMap("commands", row.g("commands").append(r.hashMap("id", commandName)
							.with("whitelisted", List.of(r.hashMap("id", role.getId()).with("type", "role")))
							.with("blacklisted", new Object[0])));
				}).runNoReply(connection);
			} else if (member != null) {
				for (Map<String, Object> commandData : commands) {
					if (commandData.get("id").equals(commandName)) {
						List<Map<String, String>> whitelisted = (List<Map<String, String>>) commandData.getOrDefault("whitelisted", List.of());			
						for (Map<String, String> whitelist : whitelisted) {
							if (whitelist.get("type").equals("user")) {
								if (member.getUser().getId().equals(whitelist.get("id"))) {
									event.reply("The user `" + member.getUser().getAsTag() + "` is already whitelisted to use that " + (command == null ? "module" : "command") + " :no_entry:").queue();
									return;
								}
							}
						}
						
						event.reply("**" + member.getUser().getAsTag() + "** is now whitelisted to use `" + commandName + "` in this server <:done:403285928233402378>").queue();
						Map<String, String> newMap = new HashMap<String, String>();
						newMap.put("type", "user");
						newMap.put("id", member.getUser().getId());
						whitelisted.add(newMap);
						commands.remove(commandData);
						commandData.put("whitelisted", whitelisted);
						commands.add(commandData);
						data.update(r.hashMap("commands", commands)).runNoReply(connection);
						return;
					}
				}
				
				event.reply("**" + member.getUser().getAsTag() + "** is now whitelisted to use `" + commandName + "` in this server <:done:403285928233402378>").queue();
				data.update(row -> {
					return r.hashMap("commands", row.g("commands").append(r.hashMap("id", commandName)
							.with("whitelisted", List.of(r.hashMap("id", member.getUser().getId()).with("type", "user")))
							.with("blacklisted", new Object[0])));
				}).runNoReply(connection);
			}
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="remove", description="Remove a whitelist from a user/role/channel from a specified command/module")
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void remove(CommandEvent event, @Context Connection connection, @Argument(value="user | role | channel") String argument, @Argument(value="command | module", endless=true) String commandArgument) {
			Get data = r.table("blacklist").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			if (dataRan == null) {
				event.reply("No whitelist data has been created for this server, so there is nothing to remove :no_entry:").queue();
				return;
			}
			
			CategoryImpl module = ArgumentUtils.getModule(commandArgument);
			ICommand command = ArgumentUtils.getCommand(commandArgument);
			if (command == null && module == null) {
				event.reply("I could not find that command/module :no_entry:").queue();
				return;
			}
			String commandName = command == null ? module.getName() : command.getCommandTrigger(); 
			
			Role role = ArgumentUtils.getRole(event.getGuild(), argument);
			Member member = ArgumentUtils.getMember(event.getGuild(), argument);
			Channel channel = ArgumentUtils.getTextChannelOrParent(event.getGuild(), argument);
			if (channel == null && role == null && member == null) {
				event.reply("I could not find that user/role/channel :no_entry:").queue();
				return;
			}
			
			List<Map<String, Object>> commands = (List<Map<String, Object>>) dataRan.get("commands");
			if (channel != null) {
				String channelDisplay = channel instanceof TextChannel ? ((TextChannel) channel).getAsMention() : channel.getName();
				for (Map<String, Object> commandData : commands) {
					if (commandData.get("id").equals(commandName)) {
						List<Map<String, String>> blacklisted = (List<Map<String, String>>) commandData.get("blacklisted"), whitelisted = (List<Map<String, String>>) commandData.getOrDefault("whitelisted", List.of());	
						for (Map<String, String> whitelist : whitelisted) {
							if (whitelist.get("type").equals("channel")) {
								if (channel.getId().equals(whitelist.get("id"))) {
									event.reply("The " + (command == null ? "module" : "command") + " `" + commandName + "` is no longer whitelisted in " + channelDisplay + " <:done:403285928233402378>").queue();
									whitelisted.remove(whitelist);
									commands.remove(commandData);
									if (!blacklisted.isEmpty() || !whitelisted.isEmpty()) {
										commandData.put("whitelisted", whitelisted);
										commands.add(commandData);
									}
									data.update(r.hashMap("commands", commands)).runNoReply(connection);
									return;
								}
							}
						}
					}
				}
				
				event.reply("The channel " + channelDisplay + " is not whitelisted to use that " + (command == null ? "module" : "command") + " :no_entry:").queue();
			} else if (role != null) {
				for (Map<String, Object> commandData : commands) {
					if (commandData.get("id").equals(commandName)) {
						List<Map<String, String>> blacklisted = (List<Map<String, String>>) commandData.get("blacklisted"), whitelisted = (List<Map<String, String>>) commandData.getOrDefault("whitelisted", List.of());	
						for (Map<String, String> whitelist : whitelisted) {
							if (whitelist.get("type").equals("role")) {
								if (role.getId().equals(whitelist.get("id"))) {
									event.reply("The " + (command == null ? "module" : "command") + " `" + commandName + "` is no longer whitelisted if a user has the role `" + role.getName() + "` <:done:403285928233402378>").queue();
									whitelisted.remove(whitelist);
									commands.remove(commandData);
									if (!blacklisted.isEmpty() || !whitelisted.isEmpty()) {
										commandData.put("whitelisted", whitelisted);
										commands.add(commandData);
									}
									data.update(r.hashMap("commands", commands)).runNoReply(connection);
									return;
								}
							}
						}
					}
				}
				
				event.reply("The role `" + role.getName() + "` is not whitelisted to use that " + (command == null ? "module" : "command") + " :no_entry:").queue();
			} else if (member != null) {
				for (Map<String, Object> commandData : commands) {
					if (commandData.get("id").equals(commandName)) {
						List<Map<String, String>> blacklisted = (List<Map<String, String>>) commandData.get("blacklisted"), whitelisted = (List<Map<String, String>>) commandData.getOrDefault("whitelisted", List.of());	
						for (Map<String, String> whitelist : whitelisted) {
							if (whitelist.get("type").equals("user")) {
								if (member.getUser().getId().equals(whitelist.get("id"))) {
									event.reply("**" + member.getUser().getAsTag() + "** is no longer whitelisted to use `" + commandName + "` in this server <:done:403285928233402378>").queue();
									whitelisted.remove(whitelist);
									commands.remove(commandData);
									if (!blacklisted.isEmpty() || !whitelisted.isEmpty()) {
										commandData.put("whitelisted", whitelisted);
										commands.add(commandData);
									}
									data.update(r.hashMap("commands", commands)).runNoReply(connection);
									return;
								}
							}
						}
					}
				}
				
				event.reply("The user `" + member.getUser().getAsTag() + "` is not whitelisted to use that " + (command == null ? "module" : "command") + " :no_entry:").queue();
			}
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="delete", aliases={"del"}, description="Deletes all the whitelist data for a specified command or module")
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void delete(CommandEvent event, @Context Connection connection, @Argument(value="command | module", endless=true) String commandArgument) {
			Get data = r.table("blacklist").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			if (dataRan == null) {
				event.reply("No whitelist data has been created for this server, so there is nothing to delete :no_entry:").queue();
				return;
			}
			
			CategoryImpl module = ArgumentUtils.getModule(commandArgument);
			ICommand command = ArgumentUtils.getCommand(commandArgument);
			if (command == null && module == null) {
				event.reply("I could not find that command/module :no_entry:").queue();
				return;
			}
			String commandName = command == null ? module.getName() : command.getCommandTrigger();
			
			List<Map<String, Object>> commands = (List<Map<String, Object>>) dataRan.get("commands");
			for (Map<String, Object> commandData : commands) {
				if (commandData.get("id").equals(commandName)) {
					List<Map<String, String>> blacklisted = (List<Map<String, String>>) commandData.get("blacklisted"), whitelisted = (List<Map<String, String>>) commandData.getOrDefault("whitelisted", List.of());
					
					if (whitelisted.isEmpty()) {
						event.reply("Nothing is whitelisted for that " + (command == null ? "module" : "command") + " :no_entry:").queue();
						return;
					}
					
					event.reply("All whitelist data for `" + commandName + "` has been deleted <:done:403285928233402378>").queue();
					commands.remove(commandData);
					if (!blacklisted.isEmpty()) {
						commandData.put("whitelisted", new Object[0]);
						commands.add(commandData);
					}
					data.update(r.hashMap("commands", commands)).runNoReply(connection);
					return;
				}
			}
			
			event.reply("Nothing is whitelisted for that " + (command == null ? "module" : "command") + " :no_entry:").queue();
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="reset", aliases={"wipe"}, description="Wipes all whitelist data set in the server, it will give you a prompt to confirm this decision", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void reset(CommandEvent event, @Context Connection connection) {
			Get data = r.table("blacklist").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			if (dataRan == null) {
				event.reply("No whitelist data has been created for this server, so there is nothing to reset :no_entry:").queue();
				return;
			}
			
			event.reply(event.getAuthor().getName() + ", are you sure you want to wipe all whitelist data? (Yes or No)").queue(message -> {
				PagedUtils.getConfirmation(event, 60, event.getAuthor(), confirmed -> {
					if (confirmed == true) {
						event.reply("All whitelist data has been deleted <:done:403285928233402378>").queue();
						message.delete().queue();
						
						List<Map<String, Object>> commands = (List<Map<String, Object>>) dataRan.get("commands");
						for (Map<String, Object> commandData : new ArrayList<>(commands)) {
							List<Map<String, String>> blacklisted = (List<Map<String, String>>) commandData.get("blacklisted");
							
							commands.remove(commandData);
							if (!blacklisted.isEmpty()) {
								commandData.put("whitelisted", new Object[0]);
								commands.add(commandData);
							}
						}
						
						data.update(r.hashMap("commands", commands)).runNoReply(connection);
					} else {
						event.reply("Cancelled <:done:403285928233402378>").queue();
						message.delete().queue();
					}
				});
			});
			
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="info", description="View all the users, roles and channels which are whitelisted from using a specified command or module")
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void info(CommandEvent event, @Context Connection connection, @Argument(value="command | module", endless=true) String argument) {
			Map<String, Object> data = r.table("blacklist").get(event.getGuild().getId()).run(connection);
			if (data == null) {
				event.reply("There is no whitelist data for this server, so no one is whitelisted from using that command/module :no_entry:").queue();
				return;
			}
			
			CategoryImpl module = ArgumentUtils.getModule(argument);
			ICommand command = ArgumentUtils.getCommand(argument);
			if (command == null && module == null) {
				event.reply("I could not find that command/module :no_entry:").queue();
				return;
			}
			String commandName = command == null ? module.getName() : command.getCommandTrigger();
			
			for (Map<String, Object> commandData : (List<Map<String, Object>>) data.get("commands")) {
				if (commandData.get("id").equals(commandName)) {
					List<Map<String, String>> whitelisted = (List<Map<String, String>>) commandData.getOrDefault("whitelisted", List.of());
					List<String> viewedWhitelisted = new ArrayList<String>();
					
					TextChannel channel;
					Role role;
					Member member;
					for (Map<String, String> whitelist : whitelisted) {
						if (whitelist.get("type").equals("channel")) {
							channel = event.getGuild().getTextChannelById(whitelist.get("id"));
							if (channel != null) {
								viewedWhitelisted.add(channel.getAsMention());
							} 
						} else if (whitelist.get("type").equals("role")) {
							role = event.getGuild().getRoleById(whitelist.get("id"));
							if (role != null) {
								viewedWhitelisted.add(role.getAsMention());
							}
						} else if (whitelist.get("type").equals("user")) {
							member = event.getGuild().getMemberById(whitelist.get("id"));       
							if (member != null) {
								viewedWhitelisted.add(member.getUser().getAsTag());
							}
						}
					}
					
					if (viewedWhitelisted.isEmpty()) {
						event.reply("Nothing is whitelisted to use that " + (command == null ? "module" : "command") + " :no_entry:").queue();
						return;
					}
					
					PagedResult<String> paged = new PagedResult<>(viewedWhitelisted)
							.setDeleteMessage(false)
							.setIncreasedIndex(true)
							.setPerPage(15)
							.setAuthor("Whitelisted to use " + commandName, null, event.getGuild().getIconUrl());
				
					PagedUtils.getPagedResult(event, paged, 300, null);
					return;
				}
			}
			
			event.reply("Nothing is whitelisted to use that " + (command == null ? "module" : "command") + " :no_entry:").queue();
		}
		
	}
	
	public class FakePermissionsCommand extends Sx4Command {
		
		public FakePermissionsCommand() {
			super("fake permissions");
			
			super.setAliases("fake perms", "fakeperms", "fakepermissions", "imaginary permissions", "imaginarypermissions", "img permissions", "imgpermissions", "img perms", "imaginary perms");
			super.setDescription("Allows you to give a role/user permissions which will only work on the bot");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="add", description="Add permissions to a specified user or role which will only be appicable on the bot")
		@AuthorPermissions({Permission.ADMINISTRATOR})
		public void add(CommandEvent event, @Context Connection connection, @Argument("user | role") String argument, @Argument(value="permission(s)") String[] permissions) {
			r.table("fakeperms").insert(r.hashMap("id", event.getGuild().getId()).with("roles", new Object[0]).with("users", new Object[0])).run(connection, OptArgs.of("durability", "soft"));
			Get data = r.table("fakeperms").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			Role role = ArgumentUtils.getRole(event.getGuild(), argument);
			Member member = ArgumentUtils.getMember(event.getGuild(), argument);
			if (member == null && role == null) {
				event.reply("I could not find that user/role :no_entry:").queue();
				return;
			}
			
			int permissionValue = 0;
			for (String permission : permissions) {
				for (Permission permissionObject : Permission.values()) {
					if (permission.toLowerCase().equals(permissionObject.getName().replace(" ", "_").replace("&", "and").toLowerCase())) {
						permissionValue |= permissionObject.getRawValue();
					}
				}
			}
			
			if (permissionValue == 0) {
				event.reply("None of the permissions you supplied were valid, check `" + event.getPrefix() + "fake permissions list` for a full list of permissions :no_entry:").queue();
				return;
			}
			
			List<String> permissionNames = new ArrayList<String>();
			for (Permission finalPermission : Permission.getPermissions(permissionValue)) {
				permissionNames.add(finalPermission.getName());
			}
			
			if (role != null) {
				event.reply("`" + role.getName() + "` can now use commands with the required permissions of " + String.join(", ", permissionNames) + " <:done:403285928233402378>").queue();
				List<Map<String, Object>> roles = (List<Map<String, Object>>) dataRan.get("roles");
				for (Map<String, Object> roleData : roles) {
					if (roleData.get("id").equals(role.getId())) {
						roles.remove(roleData);
						roleData.put("perms", (long) roleData.get("perms") | permissionValue);
						roles.add(roleData);
						data.update(r.hashMap("roles", roles)).runNoReply(connection);
						return;
					}
				}
				
				int permissionValueData = permissionValue;
				data.update(row -> r.hashMap("roles", row.g("roles").append(r.hashMap("id", role.getId()).with("perms", permissionValueData)))).runNoReply(connection);
			} else if (member != null) {		
				event.reply("**" + member.getUser().getAsTag() + "** can now use commands with the required permissions of " + String.join(", ", permissionNames) + " <:done:403285928233402378>").queue();
				List<Map<String, Object>> users = (List<Map<String, Object>>) dataRan.get("users");
				for (Map<String, Object> userData : users) {
					if (userData.get("id").equals(member.getUser().getId())) {
						users.remove(userData);
						userData.put("perms", (long) userData.get("perms") | permissionValue);
						users.add(userData);
						data.update(r.hashMap("users", users)).runNoReply(connection);
						return;
					}
				}
				
				int permissionValueData = permissionValue;
				data.update(row -> r.hashMap("users", row.g("users").append(r.hashMap("id", member.getUser().getId()).with("perms", permissionValueData)))).runNoReply(connection);
			}
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="remove", description="Remove fake permission(s) from a user/role that have been added to them previously")
		@AuthorPermissions({Permission.ADMINISTRATOR})
		public void remove(CommandEvent event, @Context Connection connection, @Argument(value="user | role") String argument, @Argument(value="permissions") String[] permissions) {
			Get data = r.table("fakeperms").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			if (dataRan == null) {
				event.reply("No fake permissions data has been created in this server, so there is nothing to remove :no_entry:").queue();
				return;
			}
			
			Role role = ArgumentUtils.getRole(event.getGuild(), argument);
			Member member = ArgumentUtils.getMember(event.getGuild(), argument);
			if (member == null && role == null) {
				event.reply("I could not find that user/role :no_entry:").queue();
				return;
			}
			
			if (role != null) {
				Map<String, Object> roleObject = null;
				List<Map<String, Object>> roles = (List<Map<String, Object>>) dataRan.get("roles");
				for (Map<String, Object> roleData : roles) {
					if (roleData.get("id").equals(role.getId())) {
						roleObject = roleData;
						break;
					}
				}
				
				if (roleObject == null) {
					event.reply("That role doesn't have any permissions :no_entry:").queue();
					return;
				}
				
				if ((long) roleObject.get("perms") == 0) {
					event.reply("That role doesn't have any permissions :no_entry:").queue();
					return;
				}
				
				List<Permission> rolePermissions = Permission.getPermissions((long) roleObject.get("perms"));
				
				int permissionValue = 0;
				for (String permission : permissions) {
					for (Permission permissionObject : Permission.values()) {
						if (permission.toLowerCase().equals(permissionObject.getName().replace(" ", "_").replace("&", "and").toLowerCase())) {
							if (rolePermissions.contains(permissionObject)) {
								permissionValue |= permissionObject.getRawValue();
							}
						}
					}
				}
				
				if (permissionValue == 0) {
					event.reply("The role didn't have any of those permissions, check `" + event.getPrefix() + "fake permissions info " + role.getId() + "` for a full list of permissions the role has :no_entry:").queue();
					return;
				}
				
				List<String> permissionNames = new ArrayList<String>();
				for (Permission finalPermission : Permission.getPermissions(permissionValue)) {
					permissionNames.add(finalPermission.getName());
				}
				
				event.reply("`" + role.getName() + "` can no longer use commands with the required permissions of " + String.join(", ", permissionNames) + " <:done:403285928233402378>").queue();
				roles.remove(roleObject);
				long permissionsAfter = (long) roleObject.get("perms") - permissionValue;
				if (permissionsAfter != 0) {
					roleObject.put("perms", (long) roleObject.get("perms") - permissionValue);
					roles.add(roleObject);
				}
				data.update(r.hashMap("roles", roles)).runNoReply(connection);
			} else if (member != null) {
				Map<String, Object> userObject = null;
				List<Map<String, Object>> users = (List<Map<String, Object>>) dataRan.get("users");
				for (Map<String, Object> userData : users) {
					if (userData.get("id").equals(member.getUser().getId())) {
						userObject = userData;
						break;
					}
				}
				
				if (userObject == null) {
					event.reply("That user doesn't have any permissions :no_entry:").queue();
					return;
				}
				
				if ((long) userObject.get("perms") == 0) {
					event.reply("That user doesn't have any permissions :no_entry:").queue();
					return;
				}
				
				List<Permission> userPermissions = Permission.getPermissions((long) userObject.get("perms"));
				
				int permissionValue = 0;
				for (String permission : permissions) {
					for (Permission permissionObject : Permission.values()) {
						if (permission.toLowerCase().equals(permissionObject.getName().replace(" ", "_").replace("&", "and").toLowerCase())) {
							if (userPermissions.contains(permissionObject)) {
								permissionValue |= permissionObject.getRawValue();
							}
						}
					}
				}
				
				if (permissionValue == 0) {
					event.reply("The user didn't have any of those permissions, check `" + event.getPrefix() + "fake permissions info " + member.getUser().getId() + "` for a full list of permissions the user has :no_entry:").queue();
					return;
				}
				
				List<String> permissionNames = new ArrayList<String>();
				for (Permission finalPermission : Permission.getPermissions(permissionValue)) {
					permissionNames.add(finalPermission.getName());
				}
				
				event.reply("**" + member.getUser().getAsTag() + "** can no longer use commands with the required permissions of " + String.join(", ", permissionNames) + " <:done:403285928233402378>").queue();
				users.remove(userObject);
				long permissionsAfter = (long) userObject.get("perms") - permissionValue;
				if (permissionsAfter != 0) {
					userObject.put("perms", permissionsAfter);
					users.add(userObject);
				}
				data.update(r.hashMap("users", users)).runNoReply(connection);
			}
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="info", description="Shows you what fake permissions a user/role has")
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void info(CommandEvent event, @Context Connection connection, @Argument(value="user | role", endless=true, nullDefault=true) String argument) {
			Map<String, Object> data = r.table("fakeperms").get(event.getGuild().getId()).run(connection);
			if (data == null) {
				event.reply("No roles/users in this server have any fake permissions :no_entry:").queue();
				return;
			}
			
			Member member = null;
			Role role = null;
			if (argument == null) {
				member = event.getMember();
			} else {
				member = ArgumentUtils.getMember(event.getGuild(), argument);
				role = ArgumentUtils.getRole(event.getGuild(), argument);
			}
			
			if (role == null && member == null) {
				event.reply("I could not find that user/role :no_entry:").queue();
				return;
			}
			
			if (role != null) {
				for (Map<String, Object> roleData : (List<Map<String, Object>>) data.get("roles")) {
					if (roleData.get("id").equals(role.getId())) {
						List<Permission> permissions = Permission.getPermissions((long) roleData.get("perms"));
						if (permissions.isEmpty()) {
							event.reply("That role doesn't have any fake permissions :no_entry:").queue();
							return;
						}
						
						EmbedBuilder embed = new EmbedBuilder();
						embed.setAuthor(role.getName() + " Fake Permissions", null, event.getGuild().getIconUrl());
						embed.setColor(role.getColor());
						embed.setDescription(String.join("\n", permissions.stream().map(permission -> permission.getName().replace(" ", "_").replace("&", "and").toLowerCase()).collect(Collectors.toList())));
						
						event.reply(embed.build()).queue();
						return;
					}
				}
				
				event.reply("That role doesn't have any fake permissions :no_entry:").queue();
			} else if (member != null) {
				for (Map<String, Object> userData : (List<Map<String, Object>>) data.get("users")) {
					if (userData.get("id").equals(member.getUser().getId())) {
						List<Permission> permissions = Permission.getPermissions((long) userData.get("perms"));
						if (permissions.isEmpty()) {
							event.reply("That user doesn't have any fake permissions :no_entry:").queue();
							return;
						}
						
						EmbedBuilder embed = new EmbedBuilder();
						embed.setAuthor(member.getUser().getAsTag() + " Fake Permissions", null, member.getUser().getEffectiveAvatarUrl());
						embed.setColor(member.getColor());
						embed.setDescription(String.join("\n", permissions.stream().map(permission -> permission.getName().replace(" ", "_").replace("&", "and").toLowerCase()).collect(Collectors.toList())));
						
						event.reply(embed.build()).queue();
						return;
					}
				}
				
				event.reply("That user doesn't have any fake permissions :no_entry:").queue();
			}
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="in permission", aliases={"inpermission"}, description="Shows you all the users and roles in a certain permission", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void inPermission(CommandEvent event, @Context Connection connection, @Argument(value="permission") String permission) {
			Map<String, Object> data = r.table("fakeperms").get(event.getGuild().getId()).run(connection);
			if (data == null) {
				event.reply("No roles/users in this server have any fake permissions :no_entry:").queue();
				return;
			}
			
			Permission permissionObject = null;
			for (Permission p : Permission.values()) {
				if (permission.toLowerCase().equals(p.getName().replace(" ", "_").replace("&", "and").toLowerCase())) {
					permissionObject = p;
				}
			}
			
			if (permissionObject == null) {
				event.reply("I could not find that permission :no_entry:").queue();
				return;
			}
			
			List<String> rolesAndUsers = new ArrayList<String>();
			for (Map<String, Object> userData : (List<Map<String, Object>>) data.get("users")) {
				if (Permission.getPermissions((long) userData.get("perms")).contains(permissionObject)) {
					Member member = event.getGuild().getMemberById((String) userData.get("id")); 
					rolesAndUsers.add(member == null ? (String) userData.get("id") + " (Left Guild)" : member.getUser().getAsTag());
				}
			}
			
			for (Map<String, Object> roleData : (List<Map<String, Object>>) data.get("roles")) {
				if (Permission.getPermissions((long) roleData.get("perms")).contains(permissionObject)) {
					Role role = event.getGuild().getRoleById((String) roleData.get("id")); 
					rolesAndUsers.add(role == null ? (String) roleData.get("id") + " (Deleted Role)" : role.getAsMention());
				}
			}
			
			PagedResult<String> paged = new PagedResult<>(rolesAndUsers)
					.setDeleteMessage(false)
					.setAuthor("Users and Roles In " + permissionObject.getName(), null, event.getGuild().getIconUrl())
					.setPerPage(15)
					.setIndexed(false);
			
			PagedUtils.getPagedResult(event, paged, 300, null);
		}
		
		@Command(value="list", description="Gives a list of permissions you can use when using fake permissions", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void list(CommandEvent event) {
			List<String> permissionNames = new ArrayList<String>();
			for (Permission permission : Permission.values()) {
				permissionNames.add(permission.getName().replace(" ", "_").replace("&", "and").toLowerCase());
			}
			
			EmbedBuilder embed = new EmbedBuilder()
					.setDescription(String.join("\n", permissionNames))
					.setTitle("Supported Permissions");
			
			event.reply(embed.build()).queue();	
		}
	}
	
	@Command(value="slowmode", aliases={"slow", "sm"}, description="Set the slowmode for the current channel or a specified channel", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@AuthorPermissions({Permission.MANAGE_CHANNEL})
	@BotPermissions({Permission.MANAGE_CHANNEL})
	public void slowmode(CommandEvent event, @Argument(value="time", nullDefault=true) String seconds, @Argument(value="channel", nullDefault=true, endless=true) String channelArgument) {
		int slowmodeSeconds;
		
		TextChannel channel;
		if (channelArgument == null) {
			channel = event.getTextChannel();
		} else {
			channel = ArgumentUtils.getTextChannel(event.getGuild(), channelArgument);
			if (channel == null) {
				event.reply("I could not find that text channel :no_entry:").queue();
				return;
			}
		}
		
		if (seconds == null) {
			slowmodeSeconds = 0;
		} else {
			if (seconds.toLowerCase().equals("off") || seconds.toLowerCase().equals("none")) {
				slowmodeSeconds = 0;
			} else {
				slowmodeSeconds = TimeUtils.convertToSeconds(seconds);
				if (slowmodeSeconds == 0) {
					event.reply("Invalid time and unit :no_entry:").queue();
					return;
				}
			}
		}
		
		if (slowmodeSeconds < 0 || slowmodeSeconds > 21600) {
			event.reply("The slowmode cannot not be any lower than 0 seconds (Off) or any higher than 6 hours :no_entry:").queue();
			return;
		}
		
		if (slowmodeSeconds == channel.getSlowmode()) {
			event.reply("Slowmode in " + channel.getAsMention() + " is already set to that :no_entry:").queue();
			return;
		}
		
		if (slowmodeSeconds != 0) {
			event.reply("Set the slowmode for " + channel.getAsMention() + " to " + TimeUtils.toTimeString(slowmodeSeconds, ChronoUnit.SECONDS) + "<:done:403285928233402378>").queue();
		} else {
			event.reply("Turned off the slowmode in the channel " + channel.getAsMention() + " <:done:403285928233402378>").queue();
		}
		channel.getManager().setSlowmode(slowmodeSeconds).queue();
	}
	
	@Command(value="lockdown", description="Makes it so anyone who doesn't override the @everyone roles permissions in the specified channel, can no longer speak in the channel")
	@AuthorPermissions({Permission.MANAGE_PERMISSIONS})
	@BotPermissions({Permission.MANAGE_PERMISSIONS})
	public void lockdown(CommandEvent event, @Argument(value="channel", endless=true, nullDefault=true) String channelArgument) {
		TextChannel channel;
		if (channelArgument == null) {
			channel = event.getTextChannel();
		} else {
			channel = ArgumentUtils.getTextChannel(event.getGuild(), channelArgument);
			if (channel == null) {
				event.reply("I could not find that text channel :no_entry:").queue();
				return;
			}
		}
		
		PermissionOverride channelOverrides = channel.getPermissionOverride(event.getGuild().getPublicRole()); 
		if (channelOverrides.getAllowed().contains(Permission.MESSAGE_WRITE) || channelOverrides.getInherit().contains(Permission.MESSAGE_WRITE)) {
			event.reply(channel.getAsMention() + " has been locked down <:done:403285928233402378>").queue();
			List<Permission> channelDeniedPermissions = new ArrayList<>(channelOverrides.getDenied());
			channelDeniedPermissions.add(Permission.MESSAGE_WRITE);
			channel.putPermissionOverride(event.getGuild().getPublicRole()).setPermissions(channelOverrides.getAllowed(), channelDeniedPermissions).queue();
		} else {
			event.reply(channel.getAsMention() + " is no longer locked down <:done:403285928233402378>").queue();
			List<Permission> channelDeniedPermissions = new ArrayList<>(channelOverrides.getDenied());
			channelDeniedPermissions.remove(Permission.MESSAGE_WRITE);
			channel.putPermissionOverride(event.getGuild().getPublicRole()).setPermissions(channelOverrides.getAllowed(), channelDeniedPermissions).queue();
		}
	}
	
	@Command(value="region", description="Set the current servers voice region")
	@AuthorPermissions({Permission.MANAGE_SERVER})
	@BotPermissions({Permission.MANAGE_SERVER})
	public void region(CommandEvent event, @Argument(value="region", endless=true) String regionName) {
		Region region = ArgumentUtils.getGuildRegion(regionName);
		if (region == null) {
			event.reply("I could not find that voice region :no_entry:").queue();
			return;
		}
		
		if (region.isVip() && !event.getGuild().getFeatures().contains("VIP_REGIONS")) {
			event.reply("You cannot set the voice region to a vip voice region when the server doesn't have access to them :no_entry:").queue();
			return;
		}
		
		if (region.equals(event.getGuild().getRegion())) {
			event.reply("The voice region is already set to that :no_entry:").queue();
			return;
		}
		
		event.reply("Succesfully changed the voice region to **" + region.getName() + " " + (region.getEmoji() == null ? "" : region.getEmoji()) + "** <:done:403285928233402378>").queue();
		event.getGuild().getManager().setRegion(region).queue();
	}
	
	@Command(value="colour role", aliases={"colourrole", "colorrole", "color role", "role colour", "rolecolour", "rolecolor", "role color"}, description="Edit the colour of a role in the current server")
	@AuthorPermissions({Permission.MANAGE_ROLES})
	@BotPermissions({Permission.MANAGE_ROLES})
	public void colourRole(CommandEvent event, @Argument(value="role") String roleArgument, @Argument(value="hex | rgb", endless=true) String colourArgument) {
		Color colour = ArgumentUtils.getColourFromString(colourArgument);	
		if (colour == null) {
			event.reply("Invalid hex or RGB value :no_entry:").queue();
			return;
		}
		
		Role role = ArgumentUtils.getRole(event.getGuild(), roleArgument);
		if (role == null) {
			event.reply("I could not find that role :no_entry:").queue();
			return;
		}
		
		if (event.getSelfMember().getRoles().get(0).getPosition() <= role.getPosition()) {
			event.reply("I cannot edit roles higher or equal to my top role :no_entry:").queue();
			return;
		}
		
		if (role.getColor().equals(colour)) {
			event.reply("The role colour is already set to that colour :no_entry:").queue();
			return;
		}
		
		event.reply("The role `" + role.getName() + "` now has a hex of **#" + Integer.toHexString(colour.hashCode()).substring(2) + "** <:done:403285928233402378>").queue();
		role.getManager().setColor(colour).queue();
	}
	
	public class PrefixCommand extends Sx4Command {
		
		public PrefixCommand() {
			super("prefix");
			
			super.setDescription("Set prefixes for either the current server or your own personal ones, Personal prefixes > server prefixes > default prefixes");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
			super.setContentOverflowPolicy(ContentOverflowPolicy.IGNORE);
		}
		
		@SuppressWarnings("unchecked")
		public void onCommand(CommandEvent event, @Context Connection connection) {
			Map<String, Object> serverData = r.table("prefix").get(event.getGuild().getId()).run(connection);
			Map<String, Object> userData = r.table("prefix").get(event.getAuthor().getId()).run(connection);
			String serverPrefixes = serverData == null ? "None" : ((List<String>) serverData.get("prefixes")).isEmpty() ? "None" : String.join(", ", (List<String>) serverData.get("prefixes"));
			String userPrefixes = userData == null ? "None" : ((List<String>) userData.get("prefixes")).isEmpty() ? "None" : String.join(", ", (List<String>) userData.get("prefixes"));
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setAuthor("Prefix Settings", null, event.getAuthor().getEffectiveAvatarUrl());
			embed.setColor(event.getMember().getColor());
			embed.addField("Default Prefixes", String.join(", ", event.getCommandListener().getDefaultPrefixes()), false);
			embed.addField("Server Prefixes", serverPrefixes, false);
			embed.addField(event.getAuthor().getName() + "'s Prefixes", userPrefixes, false);
			
			event.reply(new MessageBuilder().setEmbed(embed.build()).setContent("For help on setting the prefix use `" + event.getPrefix() + "help prefix`").build()).queue();
		}
		
		public class SelfCommand extends Sx4Command {
			
			public SelfCommand() {
				super("self");
				
				super.setDescription("Set personal prefixes that you can use in any server");
				super.setAliases("personal");
			}
			
			public void onCommand(CommandEvent event, @Context Connection connection, @Argument(value="prefixes") String[] prefixes) {
				r.table("prefix").insert(r.hashMap("id", event.getAuthor().getId()).with("prefixes", new Object[0])).run(connection, OptArgs.of("durability", "soft"));
				Get data = r.table("prefix").get(event.getAuthor().getId());
				
				List<String> cleanPrefixes = new ArrayList<String>();
				for (String prefix : prefixes) {
					if (!prefix.equals("") && !prefix.equals(" ")) {
						cleanPrefixes.add(prefix);
					}
				}
				
				if (cleanPrefixes.isEmpty()) {
					event.reply("You cannot have spaces and/or an empty character as a prefix :no_entry:").queue();
					return;
				}
				
				event.reply("Your prefixes have been set to `" + String.join("`,  `", cleanPrefixes) + "` <:done:403285928233402378>").queue();
				data.update(r.hashMap("prefixes", cleanPrefixes)).runNoReply(connection);
			}
			
			@SuppressWarnings("unchecked")
			@Command(value="add", description="Adds specified prefixes to your current personal prefixes")
			public void add(CommandEvent event, @Context Connection connection, @Argument(value="prefixes") String[] prefixes) {
				r.table("prefix").insert(r.hashMap("id", event.getAuthor().getId()).with("prefixes", new Object[0])).run(connection, OptArgs.of("durability", "soft"));
				Get data = r.table("prefix").get(event.getAuthor().getId());
				Map<String, Object> dataRan = data.run(connection);
				
				List<String> cleanPrefixes = new ArrayList<String>();
				for (String prefix : prefixes) {
					if (!prefix.equals("") && !prefix.equals(" ")) {
						if (((List<String>) dataRan.get("prefixes")).contains(prefix)) {
							event.reply("You already have `" + prefix + "` as a prefix :no_entry:").queue();
							return;
						}
						
						cleanPrefixes.add(prefix);				
					}
				}
				
				if (cleanPrefixes.isEmpty()) {
					event.reply("You cannot have spaces and/or an empty character as a prefix :no_entry:").queue();
					return;
				}
				
				event.reply("The prefixes `" + String.join("`, `", cleanPrefixes) + "` have been added to your personal prefixes <:done:403285928233402378>").queue();
				data.update(row -> r.hashMap("prefixes", row.g("prefixes").add(cleanPrefixes))).runNoReply(connection);
			}
			
			@SuppressWarnings("unchecked")
			@Command(value="remove", description="Removes specified prefixes from your current personal prefixes")
			public void remove(CommandEvent event, @Context Connection connection, @Argument(value="prefixes") String[] prefixes) {
				Get data = r.table("prefix").get(event.getAuthor().getId());
				Map<String, Object> dataRan = data.run(connection);
				if (dataRan == null) {
					event.reply("You have no prefixes to remove :no_entry:").queue();
					return;
				}
				
				List<String> userPrefixes = (List<String>) dataRan.get("prefixes");
				
				if (userPrefixes.isEmpty()) {
					event.reply("You have no prefixes to remove :no_entry:").queue();
					return;
				}
				
				for (String prefix : prefixes) {
					if (!userPrefixes.contains(prefix)) {
						event.reply("You don't have `" + prefix + "` as a prefix :no_entry:").queue();
						return;
					}				
				}
				
				event.reply("The prefixes `" + String.join("`, `", prefixes) + "` have been removed from your personal prefixes <:done:403285928233402378>").queue();
				data.update(row -> r.hashMap("prefixes", row.g("prefixes").difference(prefixes))).runNoReply(connection);
			}
			
			@SuppressWarnings("unchecked")
			@Command(value="reset", description="Reset your personal prefixes, without personal prefixes you will default to server prefixes if any are set or else default prefixes", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
			public void reset(CommandEvent event, @Context Connection connection) {
				Get data = r.table("prefix").get(event.getAuthor().getId());
				Map<String, Object> dataRan = data.run(connection);
				
				if (dataRan == null) {
					event.reply("You have no prefixes to reset :no_entry:").queue();
					return;
				}
				
				List<String> userPrefixes = (List<String>) dataRan.get("prefixes");
				
				if (userPrefixes.isEmpty()) {
					event.reply("You have no prefixes to reset :no_entry:").queue();
					return;
				}
				
				event.reply("Your prefixes have been reset <:done:403285928233402378>").queue();
				data.update(r.hashMap("prefixes", new Object[0])).runNoReply(connection);
			}
			
		}
		
		public class ServerCommand extends Sx4Command {
			
			public ServerCommand() {
				super("server");
				
				super.setDescription("Set server prefixes which users will have to use unless they have personal ones");
				super.setAliases("guild");
			}
			
			public void onCommand(CommandEvent event, @Context Connection connection, @Argument(value="prefixes") String[] prefixes) {
				r.table("prefix").insert(r.hashMap("id", event.getGuild().getId()).with("prefixes", new Object[0])).run(connection, OptArgs.of("durability", "soft"));
				Get data = r.table("prefix").get(event.getGuild().getId());
				
				List<String> cleanPrefixes = new ArrayList<String>();
				for (String prefix : prefixes) {
					if (!prefix.equals("") && !prefix.equals(" ")) {
						cleanPrefixes.add(prefix);
					}
				}
				
				if (cleanPrefixes.isEmpty()) {
					event.reply("You cannot have spaces and/or an empty character as a prefix :no_entry:").queue();
					return;
				}
				
				event.reply("The servers prefixes have been set to `" + String.join("`, `", cleanPrefixes) + "` <:done:403285928233402378>").queue();
				data.update(r.hashMap("prefixes", cleanPrefixes)).runNoReply(connection);
			}
			
			@SuppressWarnings("unchecked")
			@Command(value="add", description="Adds specified prefixes to the current servers prefixes")
			public void add(CommandEvent event, @Context Connection connection, @Argument(value="prefixes") String[] prefixes) {
				r.table("prefix").insert(r.hashMap("id", event.getGuild().getId()).with("prefixes", new Object[0])).run(connection, OptArgs.of("durability", "soft"));
				Get data = r.table("prefix").get(event.getGuild().getId());
				Map<String, Object> dataRan = data.run(connection);
				
				List<String> cleanPrefixes = new ArrayList<String>();
				for (String prefix : prefixes) {
					if (!prefix.equals("") && !prefix.equals(" ")) {
						if (((List<String>) dataRan.get("prefixes")).contains(prefix)) {
							event.reply("The server already has `" + prefix + "` as a prefix :no_entry:").queue();
							return;
						}
						
						cleanPrefixes.add(prefix);				
					}
				}
				
				if (cleanPrefixes.isEmpty()) {
					event.reply("You cannot have spaces and/or an empty character as a prefix :no_entry:").queue();
					return;
				}
				
				event.reply("The prefixes `" + String.join("`, `", cleanPrefixes) + "` have been added to the server prefixes <:done:403285928233402378>").queue();
				data.update(row -> r.hashMap("prefixes", row.g("prefixes").add(cleanPrefixes))).runNoReply(connection);
			}
			
			@SuppressWarnings("unchecked")
			@Command(value="remove", description="Removes specified prefixes from the current servers prefixes")
			public void remove(CommandEvent event, @Context Connection connection, @Argument(value="prefixes") String[] prefixes) {
				Get data = r.table("prefix").get(event.getGuild().getId());
				Map<String, Object> dataRan = data.run(connection);
				if (dataRan == null) {
					event.reply("The server has no prefixes to remove :no_entry:").queue();
					return;
				}
				
				List<String> userPrefixes = (List<String>) dataRan.get("prefixes");
				
				if (userPrefixes.isEmpty()) {
					event.reply("The server has no prefixes to remove :no_entry:").queue();
					return;
				}
				
				for (String prefix : prefixes) {
					if (!userPrefixes.contains(prefix)) {
						event.reply("The server doesn't have `" + prefix + "` as a prefix :no_entry:").queue();
						return;
					}				
				}
				
				event.reply("The prefixes `" + String.join("`, `", prefixes) + "` have been removed from the servers prefixes <:done:403285928233402378>").queue();
				data.update(row -> r.hashMap("prefixes", row.g("prefixes").difference(prefixes))).runNoReply(connection);
			}
			
			@SuppressWarnings("unchecked")
			@Command(value="reset", description="Reset the servers prefixes, without server prefixes you will default to the bots default prefixes", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
			public void reset(CommandEvent event, @Context Connection connection) {
				Get data = r.table("prefix").get(event.getGuild().getId());
				Map<String, Object> dataRan = data.run(connection);
				
				if (dataRan == null) {
					event.reply("The server has no prefixes to reset :no_entry:").queue();
					return;
				}
				
				List<String> userPrefixes = (List<String>) dataRan.get("prefixes");
				
				if (userPrefixes.isEmpty()) {
					event.reply("The server has no prefixes to reset :no_entry:").queue();
					return;
				}
				
				event.reply("The servers prefixes have been reset <:done:403285928233402378>").queue();
				data.update(r.hashMap("prefixes", new Object[0])).runNoReply(connection);
			}
			
		}
		
	}
	
	@Command(value="announce", description="Announces anything you input with a mention of a role (If the role isn't mentionable the bot will make it mentionable, ping the role, then make it unmentionable again)")
	@AuthorPermissions({Permission.MANAGE_ROLES, Permission.MESSAGE_MENTION_EVERYONE})
	@BotPermissions({Permission.MANAGE_ROLES, Permission.MESSAGE_MANAGE})
	public void announce(CommandEvent event, @Argument(value="role") String roleArgument, @Argument(value="text", endless=true, nullDefault=true) String text) {
		Role role = ArgumentUtils.getRole(event.getGuild(), roleArgument);
		if (role == null) {
			event.reply("I could not find that role :no_entry:").queue();
			return;
		}
		
		event.getMessage().delete().queue($ -> {
			if (!role.isMentionable()) {
				if (!event.getSelfMember().canInteract(role)) {
					event.reply("I cannot edit roles higher or equal to my top role :no_entry:").queue();
					return;
				}
				
				role.getManager().setMentionable(true).queue(a -> {
					event.reply(role.getAsMention() + (text == null ? "" : ", " + text) + " - " + event.getAuthor().getAsTag()).queue(b -> {
						role.getManager().setMentionable(false).queue();
					});
				});
			} else {
				event.reply(role.getAsMention() + (text == null ? "" : ", " + text) + " - " + event.getAuthor().getAsTag()).queue();
			}
		});
	}
	
	@Command(value="mass move", aliases={"massmove", "mm"}, description="Moves everyone from one voice channel to a specified voice channel")
	@AuthorPermissions({Permission.VOICE_MOVE_OTHERS})
	@BotPermissions({Permission.VOICE_MOVE_OTHERS})
	public void massMove(CommandEvent event, @Argument(value="from voice channel") String fromChannel, @Argument(value="to voice channel", endless=true, nullDefault=true) String toChannel) {
		VoiceChannel fromVoiceChannel = ArgumentUtils.getVoiceChannel(event.getGuild(), fromChannel);
		if (fromVoiceChannel == null) {
			event.reply("I could not find the first voice channel you provided :no_entry:").queue();
			return;
		}
		
		if (fromVoiceChannel.getMembers().isEmpty()) {
			event.reply("There is no one in the first voice channel :no_entry:").queue();
			return;
		}
		
		VoiceChannel toVoiceChannel;
		if (toChannel == null) {
			if (event.getMember().getVoiceState().getChannel() == null) {
				event.reply("You are not in a voice channel :no_entry:").queue();
				return;
			}
			
			toVoiceChannel = event.getMember().getVoiceState().getChannel();
		} else {
			toVoiceChannel = ArgumentUtils.getVoiceChannel(event.getGuild(), toChannel);
			if (toVoiceChannel == null) {
				event.reply("I could not find the second voice channel you provided :no_entry:").queue();
				return;
			}
		}
		
		if (toVoiceChannel.equals(fromVoiceChannel)) {
			event.reply("You cannot mass move to the same voice channel :no_entry:").queue();
			return;
		}
		
		if (!ModUtils.canConnect(event.getSelfMember(), toVoiceChannel)) {
			event.reply("I am not able to join the second voice channel so I cannot move members there :no_entry:").queue();
			return;
		}
		
		int membersMoved;
		for (membersMoved = 0; membersMoved < fromVoiceChannel.getMembers().size(); membersMoved++) {
			event.getGuild().getController().moveVoiceMember(fromVoiceChannel.getMembers().get(membersMoved), toVoiceChannel).queue();
		}
		
		event.reply("Moved **" + membersMoved + "** member" + (membersMoved == 1 ? "" : "s") + " from `" + fromVoiceChannel.getName() + "` to `" + toVoiceChannel.getName() + "` <:done:403285928233402378>").queue();
	}
	
	@Command(value="move", aliases={"move member", "movemember", "moveuser", "move user"}, description="Move a specific user to a specified voice channel")
	@AuthorPermissions({Permission.VOICE_MOVE_OTHERS})
	@BotPermissions({Permission.VOICE_MOVE_OTHERS})
	public void move(CommandEvent event, @Argument(value="user") String userArgument, @Argument(value="voice channel", endless=true, nullDefault=true) String channelArgument) {
		Member member = ArgumentUtils.getMember(event.getGuild(), userArgument);
		if (member == null) {
			event.reply("I could not find that user :no_entry:").queue();
			return;
		}
		
		if (member.getVoiceState().getChannel() == null) {
			event.reply("That user isn't in a voice channel :no_entry:").queue();
			return;
		}
		
		VoiceChannel voiceChannel;
		if (channelArgument == null) {
			if (event.getMember().getVoiceState().getChannel() == null) {
				event.reply("You are not in a voice channel :no_entry:").queue();
				return;
			}
			
			voiceChannel = event.getMember().getVoiceState().getChannel();
		} else {
			voiceChannel = ArgumentUtils.getVoiceChannel(event.getGuild(), channelArgument);
			if (voiceChannel == null) {
				event.reply("I could not find that voice channel :no_entry:").queue();
				return;
			}
		}
		
		if (member.getVoiceState().getChannel().equals(voiceChannel)) {
			event.reply("That user is already in that voice channel :no_entry:").queue();
			return;
		}
		
		if (!ModUtils.canConnect(event.getSelfMember(), voiceChannel)) {
			event.reply("I am not able to join the second voice channel so I cannot move members there :no_entry:").queue();
			return;
		}
		
		event.reply("Moved **" + member.getUser().getAsTag() + "** to `" + voiceChannel.getName() + "` <:done:403285928233402378>").queue();
		event.getGuild().getController().moveVoiceMember(member, voiceChannel).queue();
	}
	
	@Command(value="rename", aliases={"nick", "nickname"}, description="Set a users nickname in the current server")
	@AuthorPermissions({Permission.NICKNAME_MANAGE})
	@BotPermissions({Permission.NICKNAME_MANAGE})
	public void rename(CommandEvent event, @Argument(value="user") String userArgument, @Argument(value="nickname", endless=true, nullDefault=true) String nickname) {
		Member member = ArgumentUtils.getMember(event.getGuild(), userArgument);
		if (member == null) {
			event.reply("I could not find that user :no_entry:").queue();
		}
		
		if (!event.getSelfMember().canInteract(member)) {
			event.reply("I cannot edit users higher or equal to my top role :no_entry:").queue();
			return;
		}
		
		if (!event.getMember().canInteract(member)) {
			event.reply("You cannot rename someone higher or equal than your top role :no_entry:").queue();
			return;
		}
		
		nickname = nickname == null ? member.getUser().getName() : nickname;
		if (nickname.length() > 32) {
			event.reply("Nicknames can be no longer than 32 characters :no_entry:").queue();
			return;
		}
		
		if (nickname.equals(member.getEffectiveName())) {
			event.reply("The user is already named that :no_entry:").queue();
			return;
		}
		
		event.reply("Renamed **" + member.getUser().getAsTag() + "** to `" + nickname + "` <:done:403285928233402378>:ok_hand:").queue();
		event.getGuild().getController().setNickname(member, nickname).queue();
	}
	
	/*public class PruneCommand extends Sx4Command {
		
		public PruneCommand() {
			super("prune");
			
			super.setDescription("Clear a certain amount of messages in the current channel or clear a certain amount of messages from a specified user");
			super.setAliases("purge", "c", "clear");
			super.setArgumentInfo("<user>* <amount> | <amount>*");
			super.setContentOverflowPolicy(ContentOverflowPolicy.IGNORE);
			super.setBotDiscordPermissions(Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY);
			super.setAuthorDiscordPermissions(Permission.MESSAGE_MANAGE);
		}
		
		public void onCommand(CommandEvent event, @Argument(value="user") String userArgument, @Argument(value="amount", nullDefault=true) Integer amount) {
			int limit = amount == null ? 100 : amount > 100 ? 100 : amount;
			if (limit < 1) {
				event.reply("You have to delete at least 1 message :no_entry:").queue();
				return;
			}
			
			Member member = ArgumentUtils.getMember(event.getGuild(), userArgument);
			if (member == null) {
				event.reply("I could not find that user :no_entry:").queue();
				return;
			}
			
			event.getMessage().delete().queue($ -> {
				event.getTextChannel().getHistory().retrievePast(100).queue(messages -> {
					long secondsNow = Clock.systemUTC().instant().getEpochSecond();
					for (Message message : new ArrayList<>(messages)) {
						if (!message.getMember().equals(member)) {
							messages.remove(message);
						} else if (secondsNow - message.getCreationTime().toEpochSecond() > 1209600) {
							messages.remove(message);
						}
					}
					
					if (messages.size() == 0) {
						event.reply("No messages in the last 100 were from that user or they were 14 days or older :no_entry:").queue();
						return;
					}
					
					messages = messages.subList(0, Math.min(limit, messages.size()));
					
					if (messages.size() == 1) {
						messages.get(0).delete().queue();
					} else {
						event.getTextChannel().deleteMessages(messages).queue();
					}
				});
			});
		}
		
		public void onCommand(CommandEvent event, @Argument(value="amount") int amount) {
			int limit = amount > 100 ? 100 : amount;
			if (limit < 1) {
				event.reply("You have to delete at least 1 message :no_entry:").queue();
				return;
			}
			
			event.getMessage().delete().queue($ -> {
				event.getTextChannel().getHistory().retrievePast(limit).queue(messages -> {
					long secondsNow = Clock.systemUTC().instant().getEpochSecond();
					for (Message message : new ArrayList<>(messages)) {
						if (secondsNow - message.getCreationTime().toEpochSecond() > 1209600) {
							messages.remove(message);
						}
					}
					
					if (messages.size() == 0) {
						event.reply("All the **" + limit + "** messages were 14 days or older :no_entry:").queue();
						return;
					}
					
					if (messages.size() == 1) {
						messages.get(0).delete().queue();
					} else {
						event.getTextChannel().deleteMessages(messages).queue();
					}
				});
			});
		}
		
	}*/
	
	@Command(value="prune", aliases={"clear", "c", "purge"}, description="Clear a certain amount of messages in the current channel or clear a certain amount of messages from a specified user", argumentInfo="<user> [amount] | [amount]", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@AuthorPermissions({Permission.MESSAGE_MANAGE})
	@BotPermissions({Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY})
	public void prune(CommandEvent event, @Argument(value="user | amount") String argument, @Argument(value="amount", nullDefault=true) Integer amount) {
		if (GeneralUtils.isNumber(argument) && Integer.parseInt(argument) <= 100) {
			int limit = Integer.parseInt(argument);
			if (limit < 1) {
				event.reply("You have to delete at least 1 message :no_entry:").queue();
				return;
			}
			
			event.getMessage().delete().queue($ -> {
				event.getTextChannel().getHistory().retrievePast(limit).queue(messages -> {
					long secondsNow = Clock.systemUTC().instant().getEpochSecond();
					for (Message message : new ArrayList<>(messages)) {
						if (secondsNow - message.getCreationTime().toEpochSecond() > 1209600) {
							messages.remove(message);
						}
					}
					
					if (messages.size() == 0) {
						event.reply("All the **" + limit + "** messages were 14 days or older :no_entry:").queue();
						return;
					}
					
					if (messages.size() == 1) {
						messages.get(0).delete().queue();
					} else {
						event.getTextChannel().deleteMessages(messages).queue();
					}
				});
			});
		} else {
			int limit = amount == null ? 100 : amount > 100 ? 100 : amount;
			if (limit < 1) {
				event.reply("You have to delete at least 1 message :no_entry:").queue();
				return;
			}
			
			Member member = ArgumentUtils.getMember(event.getGuild(), argument);
			if (member == null) {
				event.reply("I could not find that user :no_entry:").queue();
				return;
			}
			
			event.getMessage().delete().queue($ -> {
				event.getTextChannel().getHistory().retrievePast(100).queue(messages -> {
					long secondsNow = Clock.systemUTC().instant().getEpochSecond();
					for (Message message : new ArrayList<>(messages)) {
						if (!message.getMember().equals(member)) {
							messages.remove(message);
						} else if (secondsNow - message.getCreationTime().toEpochSecond() > 1209600) {
							messages.remove(message);
						}
					}
					
					if (messages.size() == 0) {
						event.reply("No messages in the last 100 were from that user or they were 14 days or older :no_entry:").queue();
						return;
					}
					
					messages = messages.subList(0, Math.min(limit, messages.size()));
					
					if (messages.size() == 1) {
						messages.get(0).delete().queue();
					} else {
						event.getTextChannel().deleteMessages(messages).queue();
					}
				});
			});
		}
	}
	
	@Command(value="bot clean", aliases={"bc", "botclean"}, description="Removed a certain amount of bot messages in the current channel", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@AuthorPermissions({Permission.MESSAGE_MANAGE})
	@BotPermissions({Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY})
	public void botClean(CommandEvent event, @Argument(value="amount", nullDefault=true) Integer amount) {
		int limit = amount == null ? 100 : amount > 100 ? 100 : amount;
		if (limit < 1) {
			event.reply("You have to delete at least 1 message :no_entry:").queue();
			return;
		}
		
		event.getMessage().delete().queue($ -> {
			event.getTextChannel().getHistory().retrievePast(100).queue(messages -> {
				long secondsNow = Clock.systemUTC().instant().getEpochSecond();
				for (Message message : new ArrayList<>(messages)) {
					if (!message.getAuthor().isBot()) {
						messages.remove(message);
					} else if (secondsNow - message.getCreationTime().toEpochSecond() > 1209600) {
						messages.remove(message);
					}
				}
				
				if (messages.size() == 0) {
					event.reply("No messages in the last 100 were bot messsages or they were 14 days or older :no_entry:").queue();
					return;
				}
				
				messages = messages.subList(0, Math.min(limit, messages.size()));
				
				if (messages.size() == 1) {
					messages.get(0).delete().queue();
				} else {
					event.getTextChannel().deleteMessages(messages).queue();
				}
			});
		});
	}
	
	@Command(value="contains", description="Mass purge messages which contain a specified phrase/word", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@AuthorPermissions({Permission.MESSAGE_MANAGE})
	@BotPermissions({Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY})
	public void contains(CommandEvent event, @Argument(value="text") String text, @Argument(value="amount", nullDefault=true) Integer amount) {
		int limit = amount == null ? 100 : amount > 100 ? 100 : amount;
		if (limit < 1) {
			event.reply("You have to delete at least 1 message :no_entry:").queue();
			return;
		}
		
		event.getMessage().delete().queue($ -> {
			event.getTextChannel().getHistory().retrievePast(100).queue(messages -> {
				long secondsNow = Clock.systemUTC().instant().getEpochSecond();
				for (Message message : new ArrayList<>(messages)) {
					if (!message.getContentRaw().toLowerCase().contains(text.toLowerCase())) {
						messages.remove(message);
					} else if (secondsNow - message.getCreationTime().toEpochSecond() > 1209600) {
						messages.remove(message);
					}
				}
				
				if (messages.size() == 0) {
					event.reply("No messages in the last 100 had that text in their content or they were 14 days or older :no_entry:").queue();
					return;
				}
				
				messages = messages.subList(0, Math.min(limit, messages.size()));
				
				if (messages.size() == 1) {
					messages.get(0).delete().queue();
				} else {
					event.getTextChannel().deleteMessages(messages).queue();
				}
			});
		});
	}
	
	public class ModLogCommand extends Sx4Command {
		
		public ModLogCommand() {  
			super("modlog");
			
			super.setAliases("modlogs", "mod log", "mod logs");
			super.setDescription("Log all mod actions which occur in your server");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="toggle", aliases={"enable", "disable"}, description="Enable/disable modlogs in the current server", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void toggle(CommandEvent event, @Context Connection connection) {
			r.table("modlogs").insert(r.hashMap("id", event.getGuild().getId()).with("channel", null).with("toggle", false).with("case#", 0).with("case", new Object[0])).run(connection, OptArgs.of("durability", "soft"));
			Get data = r.table("modlogs").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if ((boolean) dataRan.get("toggle") == false) {
				event.reply("Modlogs are now enabled <:done:403285928233402378>").queue();
				data.update(r.hashMap("toggle", true)).runNoReply(connection);
			} else if ((boolean) dataRan.get("toggle") == true) {
				event.reply("Modlogs are now disabled <:done:403285928233402378>").queue();
				data.update(r.hashMap("toggle", false)).runNoReply(connection);
			}
		}
		
		@Command(value="channel", description="Sets the modlog channel, this is where modlogs will be sent to")
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void channel(CommandEvent event, @Context Connection connection, @Argument(value="channel", endless=true, nullDefault=true) String channelArgument) {
			r.table("modlogs").insert(r.hashMap("id", event.getGuild().getId()).with("channel", null).with("toggle", false).with("case#", 0).with("case", new Object[0])).run(connection, OptArgs.of("durability", "soft"));
			Get data = r.table("modlogs").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			TextChannel channel;
			if (channelArgument == null) {
				channel = event.getTextChannel();
			} else {
				channel = ArgumentUtils.getTextChannel(event.getGuild(), channelArgument);
				if (channel == null) {
					event.reply("I could not find that text channel :no_entry:").queue();
					return;
				}
			}
			
			if (dataRan.get("channel") != null && dataRan.get("channel").equals(channel.getId())) {
				event.reply("The modlog channel is already set to that channel :no_entry:").queue();
				return;
			}
			
			event.reply("The modlog channel has been set to " + channel.getAsMention() + " <:done:403285928233402378>").queue();
			data.update(r.hashMap("channel", channel.getId())).runNoReply(connection);
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="case", description="Edit a modlog case reason providing the moderator is unknown or you are the moderator of the case")
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		@BotPermissions({Permission.MESSAGE_HISTORY})
		public void case_(CommandEvent event, @Context Connection connection, @Argument(value="case numbers") String rangeArgument, @Argument(value="reason", endless=true) String reason) {
			Get data = r.table("modlogs").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			if (dataRan == null) {
				event.reply("There are no cases to edit in this server :no_entry:").queue();
				return;
			}
			
			List<Map<String, Object>> cases = (List<Map<String, Object>>) dataRan.get("case");
			if (cases.isEmpty()) {
				event.reply("There are no cases to edit in this server :no_entry:").queue();
				return;
			}
			
			TextChannel channel;
			if (dataRan.get("channel") == null) {
				event.reply("The modlog channel isn't set :no_entry:").queue();
				return;
			} else {
				channel = event.getGuild().getTextChannelById((String) dataRan.get("channel"));
				if (channel == null) {
					event.reply("The modlog channel no longer exists :no_entry:").queue();
					return;
				}
			}
			
			List<Integer> caseNumbers;
			try {
				caseNumbers = ArgumentUtils.getRange(rangeArgument);
			} catch(NumberFormatException e) {
				event.reply(e.getMessage() + " :no_entry:").queue();
				return;
			}
			
			if (caseNumbers.isEmpty()) {
				event.reply("Invalid case number format :no_entry:").queue();
				return;
			}
			
			if (caseNumbers.size() > 100) {
				event.reply("You can only edit up to 100 cases at one time :no_entry:").queue();
			}
			
			List<String> updatedCases = new ArrayList<>();
			for (Integer caseNumber : caseNumbers) {
				for (Map<String, Object> caseObject : new ArrayList<>(cases)) {
					if ((long) caseObject.get("id") == caseNumber) {
						if (caseObject.get("mod") != null) {
							if (!caseObject.get("mod").equals(event.getAuthor().getId())) {
								continue;
							}
						}
						
						if ((String) caseObject.get("message") == null) {
							cases.remove(caseObject);
							if (caseObject.get("mod") == null) {
								caseObject.put("mod", event.getAuthor().getId());
							}		
							caseObject.put("reason", reason);
							cases.add(caseObject);
						} else {
							channel.getMessageById((String) caseObject.get("message")).queue(message -> {
								MessageEmbed oldEmbed = message.getEmbeds().get(0);
								EmbedBuilder embed = new EmbedBuilder();
								embed.setTitle(oldEmbed.getTitle());
								embed.setTimestamp(oldEmbed.getTimestamp());
								embed.addField(oldEmbed.getFields().get(0));
								if (caseObject.get("mod") == null) {
									embed.addField("Moderator", event.getAuthor().getAsTag(), false);
								} else {
									embed.addField(oldEmbed.getFields().get(1));
								}
								embed.addField("Reason", reason, false);
									
								message.editMessage(embed.build()).queue(null, e -> {});
									
								cases.remove(caseObject);
								if (caseObject.get("mod") == null) {
									caseObject.put("mod", event.getAuthor().getId());
								}		
								caseObject.put("reason", reason);
								cases.add(caseObject);
									
							}, e -> {});
						}
						
						updatedCases.add("#" + caseNumber);
					}
				}
			}
			
			if (updatedCases.isEmpty()) {
				event.reply("None of those modlog cases existed and/or you did not have ownership to them :no_entry:").queue();
				return;
			}
			
			event.reply("Case" + (updatedCases.size() == 1 ? "" : "s") + " `" + String.join(", ", updatedCases) + "` " + (updatedCases.size() == 1 ? "has" : "have") + " been updated <:done:403285928233402378>").queue(message -> {
				data.update(r.hashMap("case", cases)).runNoReply(connection);
			});
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="view case", aliases={"viewcase"}, description="View any case from the modlogs even if it's been deleted", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void viewCase(CommandEvent event, @Context Connection connection, @Argument(value="case number") int caseNumber) {
			Get data = r.table("modlogs").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			if (dataRan == null) {
				event.reply("There are no cases to view in this server :no_entry:").queue();
				return;
			}
			
			List<Map<String, Object>> cases = (List<Map<String, Object>>) dataRan.get("case");
			if (cases.isEmpty()) {
				event.reply("There are no cases to view in this server :no_entry:").queue();
				return;
			}
			
			TextChannel channel;
			if (dataRan.get("channel") == null) {
				event.reply("The modlog channel isn't set :no_entry:").queue();
				return;
			} else {
				channel = event.getGuild().getTextChannelById((String) dataRan.get("channel"));
				if (channel == null) {
					event.reply("The modlog channel no longer exists :no_entry:").queue();
					return;
				}
			}
			
			for (Map<String, Object> caseObject : cases) {
				if ((long) caseObject.get("id") == caseNumber) {
					if ((String) caseObject.get("message") == null) {
						User user = event.getShardManager().getUserById((String) caseObject.get("user"));
						String userString = user == null ? "Unknown User (" + (String) caseObject.get("user") + ")" : user.getAsTag();
						String reason = caseObject.get("reason") == null ? "None (Update using `" + event.getPrefix() + "modlog case " + caseNumber + " <reason>`)" : (String) caseObject.get("reason");
						
						String modString;
						String modData = (String) caseObject.get("mod");
						if (modData == null) {
							modString = "Unknown (Update using `" + event.getPrefix() + "modlog case " + caseNumber + " <reason>`)";
						} else {
							User mod = event.getShardManager().getUserById(modData);
							modString = mod == null ? "Unknown Mod (" + modData + ")" : mod.getAsTag();
						}
						
						EmbedBuilder embed = new EmbedBuilder();
						embed.setTitle("Case " + caseNumber + " | " + (String) caseObject.get("action"));
						embed.setTimestamp(Instant.ofEpochSecond((long) caseObject.get("time")));
						embed.addField("User", userString, false);
						embed.addField("Moderator", modString, false);
						embed.addField("Reason", reason, false);
						event.reply(embed.build()).queue();
					} else {
						channel.getMessageById((String) caseObject.get("message")).queue(message -> {
							event.reply(message.getEmbeds().get(0)).queue();
						}, e -> {
							if (e instanceof ErrorResponseException) {
								ErrorResponseException exception = (ErrorResponseException) e;
								if (exception.getErrorCode() == 10008) {
									User user = event.getShardManager().getUserById((String) caseObject.get("user"));
									String userString = user == null ? "Unknown User (" + (String) caseObject.get("user") + ")" : user.getAsTag();
									String reason = caseObject.get("reason") == null ? "None (Update using `" + event.getPrefix() + "modlog case " + caseNumber + " <reason>`)" : (String) caseObject.get("reason");
									
									String modString;
									String modData = (String) caseObject.get("mod");
									if (modData == null) {
										modString = "Unknown (Update using `" + event.getPrefix() + "modlog case " + caseNumber + " <reason>`)";
									} else {
										User mod = event.getShardManager().getUserById(modData);
										modString = mod == null ? "Unknown Mod (" + modData + ")" : mod.getAsTag();
									}
									
									EmbedBuilder embed = new EmbedBuilder();
									embed.setTitle("Case " + caseNumber + " | " + (String) caseObject.get("action"));
									embed.setTimestamp(Instant.ofEpochSecond((long) caseObject.get("time")));
									embed.addField("User", userString, false);
									embed.addField("Moderator", modString, false);
									embed.addField("Reason", reason, false);
									event.reply(embed.build()).queue();
								}
							}
						});
					}
					
					return;
				}
			}
			
			event.reply("I could not find that modlog case :no_entry:").queue();
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="reset", aliases={"resetcases", "reset cases", "wipe"}, description="This will delete all modlog data and cases will start from 1 again", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void reset(CommandEvent event, @Context Connection connection) {
			Get data = r.table("modlogs").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			if (dataRan == null) {
				event.reply("There are no cases to delete in this server :no_entry:").queue();
				return;
			}
			
			List<Map<String, Object>> cases = (List<Map<String, Object>>) dataRan.get("case");
			if (cases.isEmpty()) {
				event.reply("There are no cases to delete in this server :no_entry:").queue();
				return;
			}
			
			event.reply(event.getAuthor().getName() + ", are you sure you want to delete all modlog cases? (Yes or No)").queue();
			PagedUtils.getConfirmation(event, 30, event.getAuthor(), confirmation -> {
				if (confirmation == true) {
					event.reply("All modlog cases have been deleted <:done:403285928233402378>").queue();
					data.update(r.hashMap("cases", new Object[0]).with("case#", 0)).runNoReply(connection);
				} else if (confirmation == false) {
					event.reply("Cancelled <:done:403285928233402378>").queue();
				}
			});
		}
		
		@Command(value="stats", aliases={"settings", "setting"}, description="View the settings for modlogs in the current server", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void stats(CommandEvent event, @Context Connection connection) {
			Map<String, Object> data = r.table("modlogs").get(event.getGuild().getId()).run(connection);
			
			TextChannel channel;
			String channelData = (String) data.get("channel");
			if (channelData == null) {
				channel = null;
			} else {
				channel = event.getGuild().getTextChannelById(channelData);
			}
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setAuthor("ModLog Settings", null, event.getGuild().getIconUrl());
			embed.addField("Status", (boolean) data.get("toggle") ? "Enabled" : "Disabled", true);
			embed.addField("Channel", channel == null ? "Not Set" : channel.getAsMention(), true);
			embed.addField("Number of Cases", String.valueOf((long) data.get("case#")), true);
			event.reply(embed.build()).queue();
		}
		
	}
	
	@Command(value="create role", aliases={"createrole", "cr"}, description="Create a role in the current server with an optional colour")
	@AuthorPermissions({Permission.MANAGE_ROLES})
	@BotPermissions({Permission.MANAGE_ROLES})
	public void createRole(CommandEvent event, @Argument(value="role name") String roleName, @Argument(value="hex | rgb", endless=true, nullDefault=true) String colourArgument) {
		if (roleName.length() > 100) {
			event.reply("Role names can be no longer than 100 characters :no_entry:").queue();
			return;
		}
		
		if (event.getGuild().getRoles().size() >= 250) {
			event.reply("This server has the max amount of roles (250) so I cannot create a role :no_entry:").queue();
			return;
		}
		
		Color colour;
		if (colourArgument == null) {
			colour = null;
		} else {
			colour = ArgumentUtils.getColourFromString(colourArgument);
			if (colour == null) {
				event.reply("Invalid hex or RGB value :no_entry:").queue();
				return;
			}
		}
		
		event.getGuild().getController().createRole().setName(roleName).setColor(colour).queue(role -> {
			event.reply("I have created the role **" + role.getName() + "** <:done:403285928233402378>:ok_hand:").queue();
		});
	}
	
	@Command(value="delete role", aliases={"deleterole", "dr"}, description="Delete a role in the current server")
	@AuthorPermissions({Permission.MANAGE_ROLES})
	@BotPermissions({Permission.MANAGE_ROLES})
	public void deleteRole(CommandEvent event, @Argument(value="role", endless=true) String roleArgument) {
		Role role = ArgumentUtils.getRole(event.getGuild(), roleArgument);
		if (role == null) {
			event.reply("I could not find that role :no_entry:").queue();
			return;
		}
		
		if (role.isPublicRole()) {
			event.reply("I cannot delete the `@everyone` role :no_entry:").queue();
			return;
		}
		
		if (!event.getMember().canInteract(role)) {
			event.reply("You cannot delete a role higher or equal to your top role :no_entry:").queue();
			return;
		}
		
		if (!event.getSelfMember().canInteract(role)) {
			event.reply("I cannot delete a role higher or equal to my top role :no_entry:").queue();
			return;
		}
		
		event.reply("I have deleted the role **" + role.getName() + "** <:done:403285928233402378>:ok_hand:").queue();
		role.delete().queue();
	}
	
	@Command(value="add role", aliases={"addrole", "ar"}, description="Add a specified role to any user")
	@AuthorPermissions({Permission.MANAGE_ROLES})
	@BotPermissions({Permission.MANAGE_ROLES})
	public void addRole(CommandEvent event, @Argument(value="user") String userArgument, @Argument(value="role", endless=true) String roleArgument) {
		Member member = ArgumentUtils.getMember(event.getGuild(), userArgument);
		if (member == null) {
			event.reply("I could not find that user :no_entry:").queue();
			return;
		}
		
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
			event.reply("You cannot give a role which is higher or equal than your top role :no_entry:").queue();
			return;
		}
		
		if (!event.getSelfMember().canInteract(role)) {
			event.reply("I cannot give a role which is higher or equal than my top role :no_entry:").queue();
			return;
		}
		
		if (member.getRoles().contains(role)) {
			event.reply("The user already has the role `" + role.getName() + "` :no_entry:").queue();
			return;
		}
		
		event.reply("**" + role.getName() + "** has been added to **" + member.getUser().getAsTag() + "** <:done:403285928233402378>:ok_hand:").queue();
		event.getGuild().getController().addSingleRoleToMember(member, role).queue();
	}
	
	@Command(value="remove role", aliases={"removerole", "rr"}, description="Remove a specified role from any user")
	@AuthorPermissions({Permission.MANAGE_ROLES})
	@BotPermissions({Permission.MANAGE_ROLES})
	public void removeRole(CommandEvent event, @Argument(value="user") String userArgument, @Argument(value="role", endless=true) String roleArgument) {
		Member member = ArgumentUtils.getMember(event.getGuild(), userArgument);
		if (member == null) {
			event.reply("I could not find that user :no_entry:").queue();
			return;
		}
		
		Role role = ArgumentUtils.getRole(event.getGuild(), roleArgument);
		if (role == null) {
			event.reply("I could not find that role :no_entry:").queue();
			return;
		}
		
		if (role.isManaged()) {
			event.reply("I cannot remove a role which is managed :no_entry:").queue();
			return;
		}
		
		if (role.isPublicRole()) {
			event.reply("I cannot remove users from the `@everyone` role :no_entry:").queue();
			return;
		}
		
		if (!event.getMember().canInteract(role)) {
			event.reply("You cannot remove a role which is higher or equal than your top role :no_entry:").queue();
			return;
		}
		
		if (!event.getSelfMember().canInteract(role)) {
			event.reply("I cannot remove a role which is higher or equal than my top role :no_entry:").queue();
			return;
		}
		
		if (!member.getRoles().contains(role)) {
			event.reply("The user doesn't have the role `" + role.getName() + "` :no_entry:").queue();
			return;
		}
		
		event.reply("**" + role.getName() + "** has been removed from **" + member.getUser().getAsTag() + "** <:done:403285928233402378>:ok_hand:").queue();
		event.getGuild().getController().removeSingleRoleFromMember(member, role).queue();
	}
	
	@Command(value="kick", description="Kick a user from the current server")
	@AuthorPermissions({Permission.KICK_MEMBERS})
	@BotPermissions({Permission.KICK_MEMBERS})
	public void kick(CommandEvent event, @Context Connection connection, @Argument(value="user") String userArgument, @Argument(value="reason", endless=true, nullDefault=true) String reason) {
		Member member = ArgumentUtils.getMember(event.getGuild(), userArgument);
		if (member == null) {
			event.reply("I could not find that user :no_entry:").queue();
		}
		
		if (member.equals(event.getSelfMember())) {
			event.reply("I cannot kick myself :no_entry:").queue();
			return;
		}
		
		if (member.equals(event.getMember())) {
			event.reply("You cannot kick yourself :no_entry:").queue();
			return;
		}
		
		if (!event.getMember().canInteract(member)) {
			event.reply("You cannot kick someone higher or equal than your top role :no_entry:").queue();
			return;
		}
		
		if (!event.getSelfMember().canInteract(member)) {
			event.reply("I cannot kick someone higher or equal than my top role :no_entry:").queue();
			return;
		}
		
		event.reply("**" + member.getUser().getAsTag() + "** has been kicked <:done:403285928233402378>:ok_hand:").queue();
		
		if (member.getUser().isBot()) {
			member.getUser().openPrivateChannel().queue(channel -> {
				channel.sendMessage(ModUtils.getKickEmbed(event.getGuild(), event.getAuthor(), reason)).queue();
			}, e -> {});
		}
		
		event.getGuild().getController().kick(member, (reason == null ? "" : reason) + " [" + event.getAuthor().getAsTag() + "]").queue();
		ModUtils.createModLogAndOffence(event.getGuild(), connection, event.getAuthor(), member.getUser(), "Kick", reason);
	}
	
	@Command(value="ban", description="Ban a user from the current server", caseSensitive=true)
	@AuthorPermissions({Permission.BAN_MEMBERS})
	@BotPermissions({Permission.BAN_MEMBERS})
	public void ban(CommandEvent event, @Context Connection connection, @Argument(value="user") String userArgument, @Argument(value="reason", endless=true, nullDefault=true) String reason) {
		Member member = ArgumentUtils.getMember(event.getGuild(), userArgument);
		if (member == null) {
			User user = ArgumentUtils.getUser(event.getGuild(), userArgument);
			if (user == null) {
				ArgumentUtils.getUserInfo(event.getGuild(), userArgument, userObject -> {
					if (userObject == null) {
						event.reply("I could not find that user :no_entry:").queue();
						return;
					} else {
						event.getGuild().getBan(userObject).queue($ -> {
							event.reply("That user is already banned :no_entry:").queue();
						}, e -> {
							event.reply("**" + userObject.getAsTag() + "** has been banned <:done:403285928233402378>:ok_hand:").queue();
							event.getGuild().getController().ban(userObject, 1, (reason == null ? "" : reason) + " [" + event.getAuthor().getAsTag() + "]").queue();
							ModUtils.createModLogAndOffence(event.getGuild(), connection, event.getAuthor(), userObject, "Ban", reason);
						});
					}
				});
				
				return;
			} else {
				event.getGuild().getBan(user).queue($ -> {
					event.reply("That user is already banned :no_entry:").queue();
				}, e -> {
					event.reply("**" + user.getAsTag() + "** has been banned <:done:403285928233402378>:ok_hand:").queue();
					event.getGuild().getController().ban(user, 1, (reason == null ? "" : reason) + " [" + event.getAuthor().getAsTag() + "]").queue();
					ModUtils.createModLogAndOffence(event.getGuild(), connection, event.getAuthor(), user, "Ban", reason);
				});
			}
		} else {
			if (member.equals(event.getSelfMember())) {
				event.reply("I cannot ban myself :no_entry:").queue();
				return;
			}
			
			if (member.equals(event.getMember())) {
				event.reply("You cannot ban yourself :no_entry:").queue();
				return;
			}
			
			if (!event.getMember().canInteract(member)) {
				event.reply("You cannot ban someone higher or equal than your top role :no_entry:").queue();
				return;
			}
			
			if (!event.getSelfMember().canInteract(member)) {
				event.reply("I cannot ban someone higher or equal than my top role :no_entry:").queue();
				return;
			}
			
			event.reply("**" + member.getUser().getAsTag() + "** has been banned <:done:403285928233402378>:ok_hand:").queue();
			
			if (!member.getUser().isBot()) {
				member.getUser().openPrivateChannel().queue(channel -> {
					channel.sendMessage(ModUtils.getBanEmbed(event.getGuild(), event.getAuthor(), reason)).queue();
				}, e -> {});
			}
			
			event.getGuild().getController().ban(member, 1, (reason == null ? "" : reason) + " [" + event.getAuthor().getAsTag() + "]").queue();
			ModUtils.createModLogAndOffence(event.getGuild(), connection, event.getAuthor(), member.getUser(), "Ban", reason);
		}
	}
	
	@Command(value="Ban", description="A ban which doesn't ban, please don't expose", caseSensitive=true)
	@AuthorPermissions({Permission.BAN_MEMBERS})
	public void fakeBan(CommandEvent event, @Argument(value="user", endless=true) String userArgument) {
		Member member = ArgumentUtils.getMember(event.getGuild(), userArgument);
		if (member == null) {
			event.reply("I could not find that user :no_entry:").queue();
			return;
		}
		
		event.reply("**" + member.getUser().getAsTag() + "** has been banned <:done:403285928233402378>:ok_hand:").queue();
	}
	
	@Command(value="unban", description="Unban a user who is banned from the current server")
	@AuthorPermissions({Permission.BAN_MEMBERS})
	@BotPermissions({Permission.BAN_MEMBERS})
	public void unban(CommandEvent event, @Context Connection connection, @Argument(value="user") String userArgument, @Argument(value="reason", endless=true, nullDefault=true) String reason) {
		User user = ArgumentUtils.getUser(event.getGuild(), userArgument);
		if (user == null) {
			ArgumentUtils.getUserInfo(event.getGuild(), userArgument, userObject -> {
				event.getGuild().getBan(userObject).queue($ -> {
					event.reply("**" + userObject.getAsTag() + "** has been unbanned <:done:403285928233402378>:ok_hand:").queue();
					event.getGuild().getController().unban(userObject).queue();
					ModUtils.createModLog(event.getGuild(), connection, event.getAuthor(), userObject, "Unban", reason);
				}, e -> {
					event.reply("That user is not banned :no_entry:").queue();
					return;
				});
			});
		} else {
			if (event.getGuild().isMember(user)) {
				event.reply("That user is not banned :no_entry:").queue();
				return;
			}
			
			event.getGuild().getBan(user).queue($ -> {
				event.reply("**" + user.getAsTag() + "** has been unbanned <:done:403285928233402378>:ok_hand:").queue();
				event.getGuild().getController().unban(user).queue();
				ModUtils.createModLog(event.getGuild(), connection, event.getAuthor(), user, "Unban", reason);
			}, e -> {
				event.reply("That user is not banned :no_entry:").queue();
				return;
			});
		}
	}
	
	@Command(value="channel mute", aliases={"cmute", "channelmute"}, description="Mute a user in the current channel")
	@AuthorPermissions({Permission.MESSAGE_MANAGE})
	@BotPermissions({Permission.MANAGE_PERMISSIONS})
	public void channelMute(CommandEvent event, @Context Connection connection, @Argument(value="user") String userArgument, @Argument(value="reason", endless=true, nullDefault=true) String reason) {
		Member member = ArgumentUtils.getMember(event.getGuild(), userArgument);
		if (member == null) {
			event.reply("I could not find that user :no_entry:").queue();
			return;
		}
		
		if (member.equals(event.getMember())) {
			event.reply("You cannot mute yourself :no_entry:").queue();
			return;
		}
		
		if (member.equals(event.getSelfMember())) {
			event.reply("I cannot mute myself :no_entry:").queue();
			return;
		}
		
		if (member.hasPermission(Permission.ADMINISTRATOR)) {
			event.reply("I cannot mute someone with administrator permissions :no_entry:").queue();
			return;
		}
		
		if (!event.getMember().canInteract(member)) {
			event.reply("You cannot mute someone higher or equal than your top role :no_entry:").queue();
			return;
		}
		
		PermissionOverride userOverrides = event.getTextChannel().getPermissionOverride(member);
		if (userOverrides != null) {
			if (userOverrides.getDenied().contains(Permission.MESSAGE_WRITE)) {
				event.reply("That user is already muted in this channel :no_entry:").queue();
				return;
			} else {
				List<Permission> deniedPermissions =  new ArrayList<>(userOverrides.getDenied());
				deniedPermissions.add(Permission.MESSAGE_WRITE);
				event.getTextChannel().putPermissionOverride(member).setPermissions(userOverrides.getAllowed(), deniedPermissions).queue();
			}
		} else {
			event.getTextChannel().putPermissionOverride(member).setPermissions(null, List.of(Permission.MESSAGE_WRITE)).queue();
		}
		
		event.reply("**" + member.getUser().getAsTag() + "** has been muted in " + event.getTextChannel().getAsMention() + " <:done:403285928233402378>:ok_hand:").queue();
		
		if (!member.getUser().isBot()) {
			member.getUser().openPrivateChannel().queue(channel -> {
				channel.sendMessage(ModUtils.getMuteEmbed(event.getGuild(), event.getTextChannel(), event.getAuthor(), 0, reason)).queue();
			}, e -> {});
		} 
		
		ModUtils.createModLogAndOffence(event.getGuild(), connection, event.getAuthor(), member.getUser(), "Mute", reason);
	}
	
	@Command(value="channel unmute", aliases={"cunmute", "channelunmute"}, description="Unmute a user in the current channel")
	@AuthorPermissions({Permission.MESSAGE_MANAGE})
	@BotPermissions({Permission.MANAGE_PERMISSIONS})
	public void channelUnmute(CommandEvent event, @Context Connection connection, @Argument(value="user") String userArgument, @Argument(value="reason", endless=true, nullDefault=true) String reason) {
		Member member = ArgumentUtils.getMember(event.getGuild(), userArgument);
		if (member == null) {
			event.reply("I could not find that user :no_entry:").queue();
			return;
		}
		
		if (member.equals(event.getMember())) {
			event.reply("You are not muted :no_entry:").queue();
			return;
		}
		
		if (member.equals(event.getSelfMember())) {
			event.reply("I am not muted :no_entry:").queue();
			return;
		}
		
		if (!event.getMember().canInteract(member)) {
			event.reply("You cannot unmute someone higher or equal than your top role :no_entry:").queue();
			return;
		}
		
		PermissionOverride userOverrides = event.getTextChannel().getPermissionOverride(member);
		if (userOverrides != null) {
			if (userOverrides.getDenied().contains(Permission.MESSAGE_WRITE)) {
				List<Permission> deniedPermissions =  new ArrayList<>(userOverrides.getDenied());
				deniedPermissions.remove(Permission.MESSAGE_WRITE);
				event.getTextChannel().putPermissionOverride(member).setPermissions(userOverrides.getAllowed(), deniedPermissions).queue();
			} else {
				event.reply("That user is not muted in this channel :no_entry:").queue();
				return;	
			}
		} else {
			event.reply("That user is not muted in this channel :no_entry:").queue();
		}
		
		event.reply("**" + member.getUser().getAsTag() + "** has been unmuted in " + event.getTextChannel().getAsMention() + " <:done:403285928233402378>:ok_hand:").queue();
		
		if (!member.getUser().isBot()) {
			member.getUser().openPrivateChannel().queue(channel -> {
				channel.sendMessage(ModUtils.getUnmuteEmbed(event.getGuild(), event.getTextChannel(), event.getAuthor(), reason)).queue();
			}, e -> {});
		}
		
		ModUtils.createModLog(event.getGuild(), connection, event.getAuthor(), member.getUser(), "Unmute", reason);
	}
	
	@SuppressWarnings("unchecked")
	@Command(value="mute", description="Mute a user server wide for a specified amount of time")
	@AuthorPermissions({Permission.MESSAGE_MANAGE})
	@BotPermissions({Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS})
	public void mute(CommandEvent event, @Context Connection connection, @Argument(value="user") String userArgument, @Argument(value="time and unit", nullDefault=true) String muteLengthArgument, @Argument(value="reason", endless=true, nullDefault=true) String reason) {
		r.table("mute").insert(r.hashMap("id", event.getGuild().getId()).with("users", new Object[0])).run(connection, OptArgs.of("durability", "soft"));
		Get data = r.table("mute").get(event.getGuild().getId());
		Map<String, Object> dataRan = data.run(connection);
		
		String muteString;
		int muteLength;
		if (muteLengthArgument == null) {
			muteLength = 1800;
			muteString = "30 minutes";
		} else {
			muteLength = TimeUtils.convertToSeconds(muteLengthArgument);
			muteString = TimeUtils.toTimeString(muteLength, ChronoUnit.SECONDS);
			if (muteLength <= 0) {
				event.reply("Invalid time format, make sure it's formatted with a numerical value then a letter representing the time (d for days, h for hours, m for minutes, s for seconds) and make sure it's in order :no_entry:").queue();
				return;
			}
		}
		
		Member member = ArgumentUtils.getMember(event.getGuild(), userArgument);
		if (member == null) {
			event.reply("I could not find that user :no_entry:").queue();
			return;
		}
		
		if (member.equals(event.getMember())) {
			event.reply("You cannot mute yourself :no_entry:").queue();
			return;
		}
		
		if (member.equals(event.getSelfMember())) {
			event.reply("I cannot mute myself :no_entry:").queue();
			return;
		}
		
		if (!event.getMember().canInteract(member)) {
			event.reply("You cannot mute someone higher or equal than your top role :no_entry:").queue();
			return;
		}
		
		if (member.hasPermission(Permission.ADMINISTRATOR)) {
			event.reply("I cannot mute someone with administrator permissions :no_entry:").queue();
			return;
		}
		
		ModUtils.setupMuteRole(event.getGuild(), role -> {
			if (role == null) {
				return;
			}
			
			if (role.getPosition() >= event.getSelfMember().getRoles().get(0).getPosition()) {
				event.reply("I am unable to mute that user as the mute role is higher or equal than my top role :no_entry:").queue();
				return;
			}
			
			if (member.getRoles().contains(role)) {
				event.reply("That user is already muted :no_entry:").queue();
				return;
			}
			
			event.reply("**" + member.getUser().getAsTag() + "** has been muted for " + muteString + " <:done:403285928233402378>:ok_hand:").queue();
			event.getGuild().getController().addSingleRoleToMember(member, role).queue();
			
			if (!member.getUser().isBot()) {
				member.getUser().openPrivateChannel().queue(channel -> {
					channel.sendMessage(ModUtils.getMuteEmbed(event.getGuild(), null, event.getAuthor(), muteLength, reason)).queue();
				}, e -> {});
			}
			
			ModUtils.createModLogAndOffence(event.getGuild(), connection, event.getAuthor(), member.getUser(), "Mute (" + muteString + ")", reason);
			
			List<Map<String, Object>> users = (List<Map<String, Object>>) dataRan.get("users");
			data.update(r.hashMap("users", ModUtils.getMuteData(member.getUser().getId(), users, muteLength))).runNoReply(connection);
			
			ScheduledFuture<?> executor = MuteEvents.scheduledExectuor.schedule(() -> MuteEvents.removeUserMute(member, role), muteLength, TimeUnit.SECONDS);
			MuteEvents.putExecutor(event.getGuild().getId(), member.getUser().getId(), executor);
		}, error -> {
			event.reply(error).queue();
			return;
		});
	}
	
	@Command(value="unmute", description="Unmute a user early who is currently muted in the server")
	@AuthorPermissions({Permission.MESSAGE_MANAGE})
	@BotPermissions({Permission.MANAGE_ROLES})
	public void unmute(CommandEvent event, @Context Connection connection, @Argument(value="user") String userArgument, @Argument(value="reason", endless=true, nullDefault=true) String reason) {
		Get data = r.table("mute").get(event.getGuild().getId());
		
		Member member = ArgumentUtils.getMember(event.getGuild(), userArgument);
		if (member == null) {
			event.reply("I could not find that user :no_entry:").queue();
			return;
		}
		
		if (member.equals(event.getSelfMember())) {
			event.reply("I am not muted :no_entry:").queue();
			return;
		}
		
		if (!event.getMember().canInteract(member)) {
			event.reply("You cannot unmute someone higher or equal than your top role :no_entry:").queue();
			return;
		}
		
		for (Role role : event.getGuild().getRoles()) {
			if (role.getName().equals("Muted - " + event.getSelfUser().getName())) {
				if (!member.getRoles().contains(role)) {
					event.reply("**" + member.getUser().getAsTag() + "** is not muted :no_entry:").queue();
					return;
				}
				
				event.reply("**" + member.getUser().getAsTag() + "** has been unmuted <:done:403285928233402378>:ok_hand:").queue();
				event.getGuild().getController().removeSingleRoleFromMember(member, role).queue();
				
				if (!member.getUser().isBot()) {
					member.getUser().openPrivateChannel().queue(channel -> {
						channel.sendMessage(ModUtils.getUnmuteEmbed(event.getGuild(), null, event.getAuthor(), reason)).queue();
					}, e -> {});
				}
				
				ModUtils.createModLog(event.getGuild(), connection, event.getAuthor(), member.getUser(), "Unmute", reason);
				
				data.update(row -> r.hashMap("users", row.g("users").filter(d -> d.g("id").ne(member.getUser().getId())))).runNoReply(connection);
				MuteEvents.cancelExecutor(event.getGuild().getId(), member.getUser().getId());
				return;
			}
		}
		
		event.reply("**" + member.getUser().getAsTag() + "** is not muted :no_entry:").queue();
	}
	
	@SuppressWarnings("unchecked")
	@Command(value="muted list", aliases={"mutedlist", "muted"}, description="Gives a list of all the current users who are muted and the time they have left", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void mutedList(CommandEvent event, @Context Connection connection) {
		Map<String, Object> data = r.table("mute").get(event.getGuild().getId()).run(connection);
		
		long timestamp = Clock.systemUTC().instant().getEpochSecond();
		
		List<Map<String, Object>> mutedUsers = (List<Map<String, Object>>) data.get("users");
		for (Map<String, Object> userData : new ArrayList<>(mutedUsers)) {
			Member member = event.getGuild().getMemberById((String) userData.get("id"));
			if (member == null) {
				mutedUsers.remove(userData);
			}
		}
		
		if (mutedUsers.isEmpty()) {
			event.reply("No one is muted in the server :no_entry:").queue();
			return;
		}
		
		mutedUsers.sort((a, b) -> Long.compare((long) a.get("time") - timestamp + (long) a.get("amount"), (long) b.get("time") - timestamp + (long) b.get("amount")));
		PagedResult<Map<String, Object>> paged = new PagedResult<>(mutedUsers)
				.setAuthor("Muted Users", null, event.getGuild().getIconUrl())
				.setIndexed(false)
				.setDeleteMessage(false)
				.setPerPage(20)
				.setFunction(user -> {
					Member member = event.getGuild().getMemberById((String) user.get("id"));
					
					long timeTillUnmute;
					if (user.get("amount") == null) {
						timeTillUnmute = -1;
					} else {
						timeTillUnmute = (long) user.get("time") - timestamp + (long) user.get("amount");
					}
					
					return member.getUser().getAsTag() + " - " + (timeTillUnmute <= 0 ? "Infinite" : TimeUtils.toTimeString(timeTillUnmute, ChronoUnit.SECONDS));
				});
		
		PagedUtils.getPagedResult(event, paged, 300, null);
	}
	
	/*public static class templates extends Sx4Command {
		
		public static String getReason(Guild guild, Connection connection, String template) {
			List<Map<String, Object>> templates = r.table("warn").get(guild.getId()).g("templates").run(connection);
			for (Map<String, Object> templateData : templates) {
				if (templateData.get("name").equals(template)) {
					return (String) templateData.get("reason");
				}
			}
			
			return null;
		}
		
		public templates() {
			super("templates");
			
			super.setDescription("Add preset templates which can be used with an option when using any moderation command");
			super.setAliases("template");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="add", description="Add a template which can be used across all mod commands")
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void add(CommandEvent event, @Context Connection connection, @Argument(value="template name") String templateName, @Argument(value="reason", endless=true) String reason) {
			r.table("warn").insert(r.hashMap("id", event.getGuild().getId()).with("users", new Object[0]).with("punishments", true).with("config", new Object[0]).with("templates", new Object[0])).run(connection, OptArgs.of("durability", "soft"));
			Get data = r.table("warn").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			
		}
		
	}*/
	
	public class WarnConfigurationCommand extends Sx4Command {
		
		public WarnConfigurationCommand() {
			super("warn configuration");
			
			super.setDescription("Configure your warn system to have different stages per warn you can choose between mute of any duration, kick, ban and just warn");
			super.setAliases("warn config", "warnconfig", "warnconfiguration");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="punishments", aliases={"punish"}, description="Enables/disables punishments for warnings, this changes whether warns have actions depending on the amount of warns a user has", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void punishments(CommandEvent event, @Context Connection connection) {
			r.table("warn").insert(r.hashMap("id", event.getGuild().getId()).with("users", new Object[0]).with("punishments", true).with("config", new Object[0]).with("templates", new Object[0])).run(connection, OptArgs.of("durability", "soft"));
			Get data = r.table("warn").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if ((boolean) dataRan.get("punishments") == true) {
				event.reply("Punishments for warnings have been disabled <:done:403285928233402378>").queue();
				data.update(r.hashMap("punishments", false)).runNoReply(connection);
			} else if ((boolean) dataRan.get("punishments") == false) {
				event.reply("Punishments for warnings have been enabled <:done:403285928233402378>").queue();
				data.update(r.hashMap("punishments", true)).runNoReply(connection);
			}
		}
		
		private List<String> actions = new ArrayList<>();
		{
			actions.add("mute");
			actions.add("kick");
			actions.add("ban");
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="set", aliases={"add"}, description="Set a certain warning to a specified action to happen when a user reaches that warning")
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void set(CommandEvent event, @Context Connection connection, @Argument(value="warning number") int warningNumber, @Argument(value="action", endless=true) String action) {
			r.table("warn").insert(r.hashMap("id", event.getGuild().getId()).with("users", new Object[0]).with("punishments", true).with("config", new Object[0]).with("templates", new Object[0])).run(connection, OptArgs.of("durability", "soft"));
			Get data = r.table("warn").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			action = action.toLowerCase();
			
			if (warningNumber <= 0 || warningNumber > 50) {
				event.reply("Warnings start at 1 and have a max warning of 50 :no_entry:").queue();
				return;
			}
			
			Map<String, Object> configuration = new HashMap<>();
			if (actions.contains(action) || action.contains("mute")) {
				if (action.equals("mute")) {
					configuration.put("warning", warningNumber);
					configuration.put("action", action);
					configuration.put("time", 1800);
				} else if (action.contains("mute")) {
					String timeString = action.split(" ", 2)[1];
					int muteLength = TimeUtils.convertToSeconds(timeString);
					if (muteLength <= 0) {
						event.reply("Invalid time format, make sure it's formatted with a numerical value then a letter representing the time (d for days, h for hours, m for minutes, s for seconds) and make sure it's in order :no_entry:").queue();
						return;
					}
					
					action = "mute";
					configuration.put("warning", warningNumber);
					configuration.put("action", action);
					configuration.put("time", muteLength);
				} else {
					configuration.put("warning", warningNumber);
					configuration.put("action", action);
				}
				
				event.reply(String.format("Warning #%s will now %s the user %s <:done:403285928233402378>", warningNumber, action, 
						configuration.containsKey("time") ? "for " + TimeUtils.toTimeString((int) configuration.get("time"), ChronoUnit.SECONDS) : "")).queue();
				
				List<Map<String, Object>> warnConfig = (List<Map<String, Object>>) dataRan.get("config");
				for (Map<String, Object> warning : warnConfig) {
					if ((long) warning.get("warning") == warningNumber) {
						warnConfig.remove(warning);
						break;
					}
				}
				
				warnConfig.add(configuration);
				data.update(r.hashMap("config", warnConfig)).runNoReply(connection);
			} else {
				event.reply("Invalid action, make sure it is either mute, kick or ban :no_entry:").queue();
			}
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="remove", description="Removes a warning which is set in the server, to view the configuration use `warn configuration list`", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void remove(CommandEvent event, @Context Connection connection, @Argument(value="warning number") int warningNumber) {
			Get data = r.table("warn").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if (dataRan == null) {
				event.reply("Warn configuration has not been set up in this server :no_entry:").queue();
				return;
			}
			
			List<Map<String, Object>> warnConfig = (List<Map<String, Object>>) dataRan.get("config");
			if (warnConfig.isEmpty()) {
				event.reply("Warn configuration has not been set up in this server :no_entry:").queue();
				return;
			}
			
			long warningNumberData;
			for (Map<String, Object> warning : warnConfig) {
				warningNumberData = (long) warning.get("warning");
				if (warningNumberData == warningNumber) { 
					event.reply("Warning #" + warningNumber + " has been removed <:done:403285928233402378>").queue();
					data.update(row -> r.hashMap("config", row.g("config").filter(d -> d.g("warning").ne(warningNumber)))).runNoReply(connection);
					return;
				}
			}
			
			event.reply("That warning is not set up to an action :no_entry:").queue();
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="reset", aliases={"wipe", "delete"}, description="Reset all warn configuration data set up in the server", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void reset(CommandEvent event, @Context Connection connection) {
			Get data = r.table("warn").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if (dataRan == null) {
				event.reply("Warn configuration has not been set up in this server :no_entry:").queue();
				return;
			}
			
			List<Map<String, Object>> warnConfig = (List<Map<String, Object>>) dataRan.get("config");
			if (warnConfig.isEmpty()) {
				event.reply("Warn configuration has not been set up in this server :no_entry:").queue();
				return;
			}
			
			event.reply(event.getAuthor().getName() + ", are you sure you want to reset **all** warn configuration data? (Yes or No)").queue();
			PagedUtils.getConfirmation(event, 300, event.getAuthor(), confirmation -> {
				if (confirmation == true) {
					event.reply("All warning configuration data has been reset <:done:403285928233402378>").queue();
					data.update(r.hashMap("config", new Object[0])).runNoReply(connection);
				} else {
					event.reply("Cancelled <:done:403285928233402378>").queue();
				}
			});
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="list", description="Shows the current configuration for warnings in the current server", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void list(CommandEvent event, @Context Connection connection) {
			Get data = r.table("warn").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if (dataRan == null) {
				event.reply("Warn configuration has not been set up in this server :no_entry:").queue();
				return;
			}
			
			List<Map<String, Object>> warnConfig = (List<Map<String, Object>>) dataRan.get("config");
			if (warnConfig.isEmpty()) {
				event.reply("Warn configuration has not been set up in this server :no_entry:").queue();
				return;
			}
			
			warnConfig.sort((a, b) -> Long.compare((long) a.get("warning"), (long) b.get("warning")));
			PagedResult<Map<String, Object>> paged = new PagedResult<>(warnConfig)
					.setDeleteMessage(false)
					.setPerPage(20)
					.setIndexed(false)
					.setAuthor("Warn Configuration", null, event.getGuild().getIconUrl())
					.setFunction(warning -> {
						if (warning.containsKey("time")) {
							return "Warning #" + warning.get("warning") + ": " + GeneralUtils.title((String) warning.get("action")) + " (" + TimeUtils.toTimeString((long) warning.get("time"), ChronoUnit.SECONDS) + ")";
						} else {
							return "Warning #" + warning.get("warning") + ": " + GeneralUtils.title((String) warning.get("action"));
						}
					});
			
			PagedUtils.getPagedResult(event, paged, 300, null);
		}
		
	}
	
	@SuppressWarnings("unchecked")
	@Command(value="warn", description="Warn a user in the current server")
	@AuthorPermissions({Permission.MESSAGE_MANAGE})
	public void warn(CommandEvent event, @Context Connection connection, @Argument(value="user") String userArgument, @Argument(value="reason", endless=true, nullDefault=true) String reason) {
		r.table("warn").insert(r.hashMap("id", event.getGuild().getId()).with("users", new Object[0]).with("punishments", true).with("config", new Object[0]).with("templates", new Object[0])).run(connection, OptArgs.of("durability", "soft"));
		Get data = r.table("warn").get(event.getGuild().getId());
		Map<String, Object> dataRan = data.run(connection);
		
		Member member = ArgumentUtils.getMember(event.getGuild(), userArgument);
		if (member == null) {
			event.reply("I could not find that user :no_entry:").queue();
			return;
		}
		
		if (member.equals(event.getMember())) {
			event.reply("You cannot warn yourself :no_entry:").queue();
			return;
		}
		
		if (member.equals(event.getSelfMember())) {
			event.reply("I cannot warn myself :no_entry:").queue();
			return;
		}
		
		if (!event.getMember().canInteract(member)) {
			event.reply("You cannot warn someone higher or equal than your top role :no_entry:").queue();
			return;
		}
		
		if (!event.getSelfMember().canInteract(member)) {
			event.reply("I cannot warn someone higher or equal than my top role :no_entry:").queue();
			return;
		}
		
		List<Map<String, Object>> users = (List<Map<String, Object>>) dataRan.get("users");
		List<Map<String, Object>> warnConfig = (List<Map<String, Object>>) dataRan.get("config");
		boolean punishments = (boolean) dataRan.get("punishments");
		long maxWarning = ModUtils.getMaxWarning(warnConfig);
		long userWarningsData = 0;
		List<String> reasons = new ArrayList<>();
		for (Map<String, Object> user : users) {
			if (user.get("id").equals(member.getUser().getId())) {
				users.remove(user);
				reasons.addAll((List<String>) user.get("reasons"));
				
				if (punishments == true) {
					userWarningsData = (long) user.get("warnings") >= maxWarning ? 1 : (long) user.get("warnings") + 1;	
				} else {
					userWarningsData = (long) user.get("warnings") + 1;
				}
				break;
			}
		}
		
		if (userWarningsData == 0) {
			userWarningsData = 1;
		}
		
		long userWarnings = userWarningsData;
		String suffixWarning = GeneralUtils.getNumberSuffix(Math.toIntExact(userWarnings));
		if (punishments == true) {
			Map<String, Object> userWarning = ModUtils.getWarning(warnConfig, userWarnings);
			String action = userWarning == null ? "warn" : (String) userWarning.get("action");	
			if (action.equals("mute")) {
				r.table("mute").insert(r.hashMap("id", event.getGuild().getId()).with("users", new Object[0])).run(connection, OptArgs.of("durability", "soft"));
				Get muteData = r.table("mute").get(event.getGuild().getId());
				Map<String, Object> muteDataRan = muteData.run(connection);
				
				if (member.hasPermission(Permission.ADMINISTRATOR)) {
					event.reply("I cannot mute someone with administrator permissions :no_entry:").queue();
					return;
				}
				
				long muteLength = (long) userWarning.get("time");
				ModUtils.setupMuteRole(event.getGuild(), role -> {
					if (role == null) {
						return;
					}
					
					if (role.getPosition() >= event.getSelfMember().getRoles().get(0).getPosition()) {
						event.reply("I am unable to mute that user as the mute role is higher or equal than my top role :no_entry:").queue();
						return;
					}
					
					if (member.getRoles().contains(role)) {
						event.reply("That user is already muted :no_entry:").queue();
						return;
					}
					
					event.reply("**" + member.getUser().getAsTag() + "** has been muted for " + TimeUtils.toTimeString(muteLength, ChronoUnit.SECONDS) + " (" + suffixWarning + " Warning) <:done:403285928233402378>").queue();
					event.getGuild().getController().addSingleRoleToMember(member, role).queue();
					
					member.getUser().openPrivateChannel().queue(channel -> {
						channel.sendMessage(ModUtils.getWarnEmbed(event.getGuild(), event.getAuthor(), warnConfig, userWarnings, true, "muted", reason)).queue();
					}, e -> {});
					
					ModUtils.createModLogAndOffence(event.getGuild(), connection, event.getAuthor(), member.getUser(), "Mute " +
							TimeUtils.toTimeString(muteLength, ChronoUnit.SECONDS) + " (" + suffixWarning + " Warning)", reason);
					
					
					if (reason != null) {
						reasons.add(reason);
					}
					
					Map<String, Object> userData = new HashMap<>();
					userData.put("warnings", userWarnings);
					userData.put("reasons", reasons);
					userData.put("id", member.getUser().getId());
					users.add(userData);
					
					data.update(r.hashMap("users", users)).runNoReply(connection);
					
					List<Map<String, Object>> muteUsers = (List<Map<String, Object>>) muteDataRan.get("users");
					muteData.update(r.hashMap("users", ModUtils.getMuteData(member.getUser().getId(), muteUsers, Math.toIntExact(muteLength)))).runNoReply(connection);
				}, error -> {
					event.reply(error).queue();
					return;
				});
				
				return;
			} else if (action.equals("kick")) {
				event.reply("**" + member.getUser().getAsTag() + "** has been kicked (" + suffixWarning + " Warning) <:done:403285928233402378>").queue();
				event.getGuild().getController().kick(member, (reason == null ? "" : reason) + " [" + event.getAuthor().getAsTag() + "]").queue();
				
				member.getUser().openPrivateChannel().queue(channel -> {
					channel.sendMessage(ModUtils.getWarnEmbed(event.getGuild(), event.getAuthor(), warnConfig, userWarnings, true, "kicked", reason)).queue();
				}, e -> {});
				
				ModUtils.createModLogAndOffence(event.getGuild(), connection, event.getAuthor(), member.getUser(), "Kick (" + suffixWarning + " Warning)", reason);
			} else if (action.equals("ban")) {
				event.reply("**" + member.getUser().getAsTag() + "** has been banned (" + suffixWarning + " Warning) <:done:403285928233402378>").queue();
				event.getGuild().getController().ban(member, 1, (reason == null ? "" : reason) + " [" + event.getAuthor().getAsTag() + "]").queue();
				
				member.getUser().openPrivateChannel().queue(channel -> {
					channel.sendMessage(ModUtils.getWarnEmbed(event.getGuild(), event.getAuthor(), warnConfig, userWarnings, true, "banned", reason)).queue();
				}, e -> {});
				
				ModUtils.createModLogAndOffence(event.getGuild(), connection, event.getAuthor(), member.getUser(), "Ban (" + suffixWarning + " Warning)", reason);
			} else if (action.equals("warn")) {
				event.reply("**" + member.getUser().getAsTag() + "** has been warned (" + suffixWarning + " Warning) :warning:").queue();
				
				member.getUser().openPrivateChannel().queue(channel -> {
					channel.sendMessage(ModUtils.getWarnEmbed(event.getGuild(), event.getAuthor(), warnConfig, userWarnings, true, "warned", reason)).queue();
				}, e -> {});
				
				ModUtils.createModLogAndOffence(event.getGuild(), connection, event.getAuthor(), member.getUser(), "Warn (" + suffixWarning + " Warning)", reason);
			}
			
			if (reason != null) {
				reasons.add(reason);
			}
			
			Map<String, Object> userData = new HashMap<>();
			userData.put("warnings", userWarnings);
			userData.put("reasons", reasons);
			userData.put("id", member.getUser().getId());
			users.add(userData);
			
			data.update(r.hashMap("users", users)).runNoReply(connection);
		} else {
			event.reply("**" + member.getUser().getAsTag() + "** has been warned (" + suffixWarning + " Warning) :warning:").queue();
			
			if (!member.getUser().isBot()) {
				member.getUser().openPrivateChannel().queue(channel -> {
					channel.sendMessage(ModUtils.getWarnEmbed(event.getGuild(), event.getAuthor(), warnConfig, userWarnings, false, "warned", reason)).queue();
				}, e -> {});
			}
			
			ModUtils.createModLogAndOffence(event.getGuild(), connection, event.getAuthor(), member.getUser(), "Warn (" + suffixWarning + " Warning)", reason);
			
			if (reason != null) {
				reasons.add(reason);
			}
			
			Map<String, Object> userData = new HashMap<>();
			userData.put("warnings", userWarnings);
			userData.put("reasons", reasons);
			userData.put("id", member.getUser().getId());
			users.add(userData);
			
			data.update(r.hashMap("users", users)).runNoReply(connection);
		}
	}
	
	@SuppressWarnings("unchecked")
	@Command(value="warn list", aliases={"warnlist", "warns"}, description="Gets a list of users who are warned in the current server", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void warnList(CommandEvent event, @Context Connection connection) {
		Map<String, Object> data = r.table("warn").get(event.getGuild().getId()).run(connection);
		
		if (data == null) {
			event.reply("No one has been warned in this server :no_entry:").queue();
			return;
		}
		
		List<Map<String, Object>> users = (List<Map<String, Object>>) data.get("users");
		if (users.isEmpty()) {
			event.reply("No one has been warned in this server :no_entry:").queue();
			return;
		}
		
		Member member;
		for (Map<String, Object> user : new ArrayList<>(users)) {
			member = event.getGuild().getMemberById((String) user.get("id")); 
			if (member == null) {
				users.remove(user);
			}
		}
		
		users.sort((a, b) -> Long.compare((long) b.get("warnings"), (long) a.get("warnings"))); 
		PagedResult<Map<String, Object>> paged = new PagedResult<>(users)
				.setAuthor("Warned Users", null, event.getGuild().getIconUrl())
				.setPerPage(15)
				.setDeleteMessage(false)
				.setIndexed(false)
				.setFunction(userData -> {
					Member user = event.getGuild().getMemberById((String) userData.get("id")); 
					return "`" + user.getUser().getAsTag() + "` - Warning **#" + (long) userData.get("warnings") + "**";
				});
		
		PagedUtils.getPagedResult(event, paged, 300, null);
	}
	
	@SuppressWarnings("unchecked")
	@Command(value="warnings", description="Displays how many warnings a specified user has", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void warnings(CommandEvent event, @Context Connection connection, @Argument(value="user", endless=true, nullDefault=true) String userArgument) {
		Map<String, Object> data = r.table("warn").get(event.getGuild().getId()).run(connection);
		
		Member member;
		if (userArgument == null) {
			member = event.getMember();
		} else {
			member = ArgumentUtils.getMember(event.getGuild(), userArgument);
			if (member == null) {
				event.reply("I could not find that user :no_entry:").queue();
				return;
			}
		}
		
		if (data == null) {
			event.reply("That user has no warnings :no_entry:").queue();
			return;
		}
		
		List<Map<String, Object>> users = (List<Map<String, Object>>) data.get("users");
		if (users.isEmpty()) {
			event.reply("That user has no warnings :no_entry:").queue();
			return;
		}
		
		for (Map<String, Object> user : users) {
			if (user.get("id").equals(member.getUser().getId())) {
				long warnings = (long) user.get("warnings");
				List<String> reasons = (List<String>) user.get("reasons");
				String nextAction;
				if ((boolean) data.get("punishments") == false) {
					nextAction = "Warn";
				} else {
					Map<String, Object> warning = ModUtils.getWarning((List<Map<String, Object>>) data.get("config"), warnings + 1);
					if (warning == null) {
						nextAction = "Warn";
					} else {
						if (warning.containsKey("time")) {
							nextAction = GeneralUtils.title((String) warning.get("action")) + " (" + TimeUtils.toTimeString((long) warning.get("time"), ChronoUnit.SECONDS) + ")";
						} else {
							nextAction = GeneralUtils.title((String) warning.get("action"));
						}
					}
				}
				
				EmbedBuilder embed = new EmbedBuilder();
				embed.setColor(member.getColor());
				embed.setAuthor(member.getUser().getAsTag(), null, member.getUser().getEffectiveAvatarUrl());
				embed.setDescription(member.getUser().getName() + " is on " + warnings + " warning" + (warnings == 1 ? "" : "s"));
				embed.addField("Next Action", nextAction, false);
				embed.addField("Reasons", reasons.isEmpty() ? "None" : "`" + String.join("`, `", reasons) + "`", false);
				event.reply(embed.build()).queue();
				return;
			}
		}
		
		event.reply("That user has no warnings :no_entry:").queue();
	}
	
	@SuppressWarnings("unchecked")
	@Command(value="set warnings", aliases={"setwarnings", "set warns", "setwarns"}, description="Set the warning amount for a specified user", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@AuthorPermissions({Permission.MESSAGE_MANAGE})
	public void setWarnings(CommandEvent event, @Context Connection connection, @Argument(value="user") String userArgument, @Argument(value="warning amount") int warningAmount) {
		r.table("warn").insert(r.hashMap("id", event.getGuild().getId()).with("users", new Object[0]).with("punishments", true).with("config", new Object[0]).with("templates", new Object[0])).run(connection, OptArgs.of("durability", "soft"));
		Get data = r.table("warn").get(event.getGuild().getId());
		Map<String, Object> dataRan = data.run(connection);
		
		if (warningAmount < 1) {
			event.reply("The warning amount has to be at least 1 :no_entry:").queue();
			return;
		}
		
		Member member = ArgumentUtils.getMember(event.getGuild(), userArgument);
		if (member == null) {
			event.reply("I could not find that user :no_entry:").queue();
			return;
		}
		
		if (!event.getMember().canInteract(member)) {
			event.reply("You cannot set someones warns if they are higher or equal than your top role :no_entry:").queue();
			return;
		}
		
		List<Map<String, Object>> users = (List<Map<String, Object>>) dataRan.get("users");
		for (Map<String, Object> user : users) {
			if (user.get("id").equals(member.getUser().getId())) {
				event.reply("**" + member.getUser().getAsTag() + "** has had their warnings set to **" + warningAmount + "** <:done:403285928233402378>").queue();
				users.remove(user);
				user.put("warnings", warningAmount);
				users.add(user);
				data.update(r.hashMap("users", users)).runNoReply(connection);
				return;
			}
		}
		
		event.reply("**" + member.getUser().getAsTag() + "** has had their warnings set to **" + warningAmount + "** <:done:403285928233402378>").queue();
		data.update(row -> r.hashMap("users", row.g("users").append(r.hashMap("id", member.getUser().getId())
				.with("warnings", warningAmount)
				.with("reasons", new Object[0])))).runNoReply(connection);
	}
	
	@SuppressWarnings("unchecked")
	@Command(value="reset warnings", aliases={"resetwarnings", "resetwarns", "reset warns"}, description="Reset warnings for a specified user, this'll set their warning amount of 0 and get rid of their reasons")
	@AuthorPermissions({Permission.MESSAGE_MANAGE})
	public void resetWarnings(CommandEvent event, @Context Connection connection, @Argument(value="user", endless=true) String userArgument) {
		Get data = r.table("warn").get(event.getGuild().getId());
		Map<String, Object> dataRan = data.run(connection);
		
		if (dataRan == null) {
			event.reply("That user has no warnings :no_entry:").queue();
			return;
		}
		
		List<Map<String, Object>> users = (List<Map<String, Object>>) dataRan.get("users");
		if (users.isEmpty()) {
			event.reply("That user has no warnings :no_entry:").queue();
			return;
		}
		
		Member member = ArgumentUtils.getMember(event.getGuild(), userArgument);
		if (member == null) {
			event.reply("I could not find that user :no_entry:").queue();
			return;
		}
		
		if (!event.getMember().canInteract(member)) {
			event.reply("You cannot reset someones warns if they are higher or equal than your top role :no_entry:").queue();
			return;
		}
		
		for (Map<String, Object> user : users) {
			if (user.get("id").equals(member.getUser().getId())) {
				event.reply("**" + member.getUser().getAsTag() + "** has had their warnings reset <:done:403285928233402378>").queue();
				users.remove(user);
				data.update(r.hashMap("users", users)).runNoReply(connection);
				return;
			}
		}
		
		event.reply("That user has no warnings :no_entry:").queue();
	}
	
	@SuppressWarnings("unchecked")
	@Command(value="offences", description="View the offences of a user in the current server")
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void offences(CommandEvent event, @Context Connection connection, @Argument(value="user", endless=true, nullDefault=true) String userArgument) {
		Member member;
		if (userArgument == null) {
			member = event.getMember();
		} else {
			member = ArgumentUtils.getMember(event.getGuild(), userArgument);
			if (member == null) {
				event.reply("I could not find that user :no_entry:").queue();
				return;
			}
		}
		
		Map<String, Object> data = r.table("offence").get(member.getUser().getId()).run(connection);
		if (data == null) {
			event.reply("That user doesn't have any offences :no_entry:").queue();
			return;
		}
		
		List<Map<String, Object>> offences = (List<Map<String, Object>>) data.get("offences");
		for (Map<String, Object> offence : new ArrayList<>(offences)) {
			if (!event.getGuild().getId().equals(offence.get("server"))) {
				offences.remove(offence);
			}
		}
		
		if (offences.isEmpty()) {
			event.reply("That user doesn't have any offences :no_entry:").queue();
			return;
		}
		
		PagedResult<Map<String, Object>> paged = new PagedResult<>(offences)
				.setPerPage(5)
				.setCustom(true)
				.setCustomFunction(page -> {
					List<Map<String, Object>> userOffences = page.getArray();
					userOffences.sort((a, b) -> Long.compare((long) a.get("time"), (long) b.get("time")));
					
					EmbedBuilder embed = new EmbedBuilder();
					embed.setTitle("Page " + page.getCurrentPage() + "/" + page.getMaxPage());
					embed.setAuthor(member.getUser().getAsTag() + " Offences", null, member.getUser().getEffectiveAvatarUrl());
					embed.setFooter("next | previous | go to <page_number> | cancel", null);
					
					for (int i = page.getCurrentPage() * page.getPerPage() - page.getPerPage(); i < page.getCurrentPage() * page.getPerPage(); i++) {
						Map<String, Object> userOffence;
						try {
							userOffence = userOffences.get(i);
						} catch(IndexOutOfBoundsException e) {
							continue;
						}
						
						String moderatorString;
						if ((String) userOffence.get("mod") != null) {
							Member moderator = event.getGuild().getMemberById((String) userOffence.get("mod"));
							moderatorString = moderator == null ? (String) userOffence.get("mod") : moderator.getUser().getAsTag() + " (" + moderator.getUser().getId() + ")";
						} else {
							moderatorString = "Unknown";
						}
						String reason = (String) userOffence.get("reason") == null ? "None Given" : (String) userOffence.get("reason");
						String proof = (String) userOffence.get("proof") == null ? "None Given" : (String) userOffence.get("proof");
						LocalDateTime date = LocalDateTime.ofEpochSecond((long) userOffence.get("time"), 0, ZoneOffset.UTC);
						String value = String.format("Action: %s\nReason: %s\nModerator: %s\nProof: %s\nTime: %s", (String) userOffence.get("action"), reason, moderatorString, proof, date.format(DateTimeFormatter.ofPattern("dd/MM/yy HH:mm")));
								
						embed.addField("Offence #" + (i + 1), value, false);
					}
					
					return embed.build();
				});
		
		PagedUtils.getPagedResult(event, paged, 300, null);
	}
	
	@SuppressWarnings("unchecked")
	@Command(value="proof", description="Update the proof of a specified users offence") 
	@AuthorPermissions({Permission.MESSAGE_MANAGE})
	public void proof(CommandEvent event, @Context Connection connection, @Argument(value="user") String userArgument, @Argument(value="offence number") int offenceNumber, @Argument(value="proof", endless=true) String proof) {
		Member member = ArgumentUtils.getMember(event.getGuild(), userArgument);
		if (member == null) {
			event.reply("I could not find that user :no_entry:").queue();
			return;
		}
		
		if (proof.length() > 200) {
			event.reply("Proof cannot be any longer than 200 characters :no_entry:").queue();
			return;
		}
		
		if (offenceNumber < 1) {
			event.reply("The offence number has to be at least 1 :no_entry:").queue();
			return;
		}
		
		Get data = r.table("offence").get(member.getUser().getId());
		Map<String, Object> dataRan = data.run(connection);
		
		if (dataRan == null) {
			event.reply("That user doesn't have any offences :no_entry:").queue();
		}
		
		List<Map<String, Object>> offences = (List<Map<String, Object>>) dataRan.get("offences");
		offences.sort((a, b) -> Long.compare((long) a.get("time"), (long) b.get("time")));
		for (Map<String, Object> offence : new ArrayList<>(offences)) {
			if (!event.getGuild().getId().equals(offence.get("server"))) {
				offences.remove(offence);
			}
		}
		
		if (offences.isEmpty()) {
			event.reply("That user doesn't have any offences :no_entry:").queue();
			return;
		}
		
		if (offenceNumber > offences.size()) {
			event.reply("That user only has **" + offences.size() + "** offences :no_entry:").queue();
			return;
		}
		
		Map<String, Object> selectedOffence = offences.get(offenceNumber - 1);
		
		if ((String) selectedOffence.get("mod") != null) {
			Member moderator = event.getGuild().getMemberById((String) selectedOffence.get("mod"));
			if (!moderator.equals(event.getMember())) {
				event.reply("You don't have ownership to that offence :no_entry:").queue();
				return;
			}
		}
		
		event.reply("Proof has been updated for offence **#" + offenceNumber + "** <:done:403285928233402378>").queue();
		
		offences.remove(selectedOffence);
		selectedOffence.put("proof", proof);
		offences.add(selectedOffence);
		data.update(r.hashMap("offences", offences)).runNoReply(connection);
	}
	
	@Initialize(all=true)
	public void initialize(CommandImpl command) {
	    command.setCategory(Categories.MOD);
	}
	
}
