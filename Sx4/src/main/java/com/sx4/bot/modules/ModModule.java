package com.sx4.bot.modules;

import java.awt.Color;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.category.impl.CategoryImpl;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.command.Command.AuthorPermissions;
import com.jockie.bot.core.command.Command.BotPermissions;
import com.jockie.bot.core.command.Context;
import com.jockie.bot.core.command.ICommand;
import com.jockie.bot.core.command.ICommand.ContentOverflowPolicy;
import com.jockie.bot.core.command.Initialize;
import com.jockie.bot.core.command.impl.CommandEvent;
import com.jockie.bot.core.command.impl.CommandImpl;
import com.jockie.bot.core.module.Module;
import com.jockie.bot.core.option.Option;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import com.sx4.bot.categories.Categories;
import com.sx4.bot.core.Sx4Bot;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEventListener;
import com.sx4.bot.database.Database;
import com.sx4.bot.events.MuteEvents;
import com.sx4.bot.interfaces.Examples;
import com.sx4.bot.interfaces.Sx4Callback;
import com.sx4.bot.utils.ArgumentUtils;
import com.sx4.bot.utils.FunUtils;
import com.sx4.bot.utils.GeneralUtils;
import com.sx4.bot.utils.HelpUtils;
import com.sx4.bot.utils.ModUtils;
import com.sx4.bot.utils.PagedUtils;
import com.sx4.bot.utils.PagedUtils.PagedResult;
import com.sx4.bot.utils.TimeUtils;
import com.sx4.bot.utils.WarnUtils;
import com.sx4.bot.utils.WarnUtils.UserWarning;
import com.sx4.bot.utils.WarnUtils.Warning;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.Region;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Guild.Ban;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.Icon;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.entities.Message.MentionType;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import okhttp3.Request;

@Module
public class ModModule {
	
	private void createEmoteRequest(CommandEvent event, Request request, String name) {
		Sx4Bot.client.newCall(request).enqueue((Sx4Callback) response -> {
			event.getGuild().createEmote(name, Icon.from(response.body().bytes())).queue(e -> {
				event.reply(e.getAsMention() + " has been created <:done:403285928233402378>").queue();
			}, e -> {
				if (e instanceof ErrorResponseException) {
					if (((ErrorResponseException) e).getErrorCode() == 400) {
						event.reply("The emote cannot be any larger than 256KB :no_entry:").queue();
					}
				}
			});
		});
	}

	@Command(value="create emote", aliases={"createemote", "create emoji", "createemoji"}, description="Allows you to create an emote in your server by providing an emote name/id/mention, attachment or image url", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"create emote", "create emote https://i.imgur.com/i87lyNO.png", "create emote :doggo:"})
	@AuthorPermissions({Permission.MANAGE_EMOTES})
	@BotPermissions({Permission.MANAGE_EMOTES})
	public void createEmote(CommandEvent event, @Argument(value="emote | image", nullDefault=true) String argument) {
		long animatedEmotes = event.getGuild().getEmoteCache().stream().filter(Emote::isAnimated).count();
		long nonAnimatedEmotes = event.getGuild().getEmoteCache().stream().filter(Predicate.not(Emote::isAnimated)).count();
		int maxEmotes = event.getGuild().getMaxEmotes();
		
		if (argument != null) {
			Emote emote = ArgumentUtils.getEmote(event.getGuild(), argument);
			if (emote != null) {
				if ((emote.isAnimated() && animatedEmotes >= maxEmotes) || (!emote.isAnimated() && nonAnimatedEmotes >= maxEmotes)) {
					event.reply("You already have the max" + (emote.isAnimated() ? "" : " non") + " animated emotes on this server :no_entry:").queue();
					return;
				}
				
				Request request = new Request.Builder()
					.url(emote.getImageUrl())
					.build();
				
				this.createEmoteRequest(event, request, emote.getName());
			} else {
				Matcher emoteMention = MentionType.EMOTE.getPattern().matcher(argument);
				String url = null, name = null;
				Request request;
				String id;
				Boolean animated = null;
				if (!event.getMessage().getAttachments().isEmpty()) {
					id = null;
					for (Attachment attachment : event.getMessage().getAttachments()) {
						if (attachment.isImage()) {
							url = attachment.getUrl();
							String fileName = attachment.getFileName().replace("-", "_").replace(" ", "_");
							int periodIndex = fileName.lastIndexOf(".");
							name = fileName.substring(0, periodIndex);
							break;
						}
					}
				} else if (emoteMention.matches()) {
					name = emoteMention.group(1);
					id = emoteMention.group(2);
					animated = argument.startsWith("<a");
				} else if (GeneralUtils.isNumberUnsigned(argument)) {
					id = argument;
				} else {
					try {
						request = new Request.Builder().url(argument).build();
					} catch(IllegalArgumentException e) {
						event.reply("You didn't provide a valid url :no_entry:").queue();
						return;
					}
					
					Sx4Bot.client.newCall(request).enqueue((Sx4Callback) response -> {
						if (response.code() == 200) {
							String type;
							if (response.header("Content-Type") != null && response.header("Content-Type").contains("/")) {
								type = response.header("Content-Type").split("/")[1];
							} else {
								event.reply("The url you provided wasn't an image or a gif :no_entry:").queue();
								return;
							}
							
							boolean animatedRequest;
							String urlRequest;
							if (type.equals("gif")) {
								urlRequest = argument;
								animatedRequest = true;
							} else if (type.equals("png") || type.equals("jpg") || type.equals("jpeg")) {
								urlRequest = argument;
								animatedRequest = false;
							} else {
								event.reply("The url you provided wasn't an image or a gif :no_entry:").queue();
								return;
							}
							
							if ((animatedRequest && animatedEmotes >= maxEmotes) || (!animatedRequest && nonAnimatedEmotes >= maxEmotes)) {
								event.reply("You already have the max" + (animatedRequest ? "" : " non") + " animated emotes on this server :no_entry:").queue();
								return;
							}
							
							this.createEmoteRequest(event, new Request.Builder().url(urlRequest).build(), "Unnamed_Emote");
						} else {
							event.reply("The url you provided was invalid :no_entry:").queue();
							return;
						}
					});
					
					return;
				}
				
				if (id != null && animated != null) {
					url = "https://cdn.discordapp.com/emojis/" + id + "." + (animated ? "gif" : "png");
				} else if (id != null && animated == null) {
					try {
						request = new Request.Builder().url("https://cdn.discordapp.com/emojis/" + id + ".gif").build();
					} catch(IllegalArgumentException e) {
						event.reply("You didn't provide a valid emote id :no_entry:").queue();
						return;
					}
					
					String nameRequest = name;
					Sx4Bot.client.newCall(request).enqueue((Sx4Callback) response -> {
						boolean animatedRequest;
						String urlRequest;
						if (response.code() == 415) {
							urlRequest = "https://cdn.discordapp.com/emojis/" + id + ".png";
							animatedRequest = false;
						} else if (response.code() == 200) {
							urlRequest = "https://cdn.discordapp.com/emojis/" + id + ".gif";
							animatedRequest = true;
						} else {
							event.reply("I could not find that emote :no_entry:").queue();
							return;
						}
						
						if ((animatedRequest && animatedEmotes >= maxEmotes) || (!animatedRequest && nonAnimatedEmotes >= maxEmotes)) {
							event.reply("You already have the max" + (animatedRequest ? "" : " non") + " animated emotes on this server :no_entry:").queue();
							return;
						}
						
						this.createEmoteRequest(event, new Request.Builder().url(urlRequest).build(), nameRequest == null ? "Unnamed_Emote" : nameRequest);
					});
					
					return;
				}
				
				if (url == null) {
					event.reply("None of the attachments you supplied were images or gifs :no_entry:").queue();
					return;
				}
				
				if ((animated && animatedEmotes >= maxEmotes) || (!animated && nonAnimatedEmotes >= maxEmotes)) {
					event.reply("You already have the max" + (animated ? "" : " non") + " animated emotes on this server :no_entry:").queue();
					return;
				}
				
				this.createEmoteRequest(event, new Request.Builder().url(url).build(), name == null ? "Unnamed_Emote" : name);
			}
		} else {
			if (!event.getMessage().getAttachments().isEmpty()) {
				for (Attachment attachment : event.getMessage().getAttachments()) {
					if (attachment.isImage()) {
						String fileName = attachment.getFileName().replace("-", "_").replace(" ", "_");
						int periodIndex = fileName.lastIndexOf(".");
						String emoteName = fileName.substring(0, periodIndex);
						
						boolean animated = fileName.substring(periodIndex + 1).equals("gif"); 
						if ((animated && animatedEmotes >= maxEmotes) || (!animated && nonAnimatedEmotes >= maxEmotes)) {
							event.reply("You already have the max" + (animated ? "" : " non") + " animated emotes on this server :no_entry:").queue();
							return;
						}
						
						attachment.retrieveAsIcon().thenAcceptAsync(stream -> {
							event.getGuild().createEmote(emoteName, stream).queue(e -> {
								event.reply(e.getAsMention() + " has been created <:done:403285928233402378>").queue();
							}, e -> {
								if (e instanceof ErrorResponseException) {
									if (((ErrorResponseException) e).getErrorCode() == 400) {
										event.reply("The emote cannot be any larger than 256KB :no_entry:").queue();
									}
								}
							});
						}); 
						
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
			super.setExamples("create channel voice", "create channel text");
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="text", description="Create a text channel in the current server")
		@Examples({"create channel text general"})
		@AuthorPermissions({Permission.MANAGE_CHANNEL})
		@BotPermissions({Permission.MANAGE_CHANNEL})
		public void text(CommandEvent event, @Argument(value="channel name", endless=true) String textChannelName) {
			if (textChannelName.length() > 100) {
				event.reply("Text channel names can not be longer than 100 characters :no_entry:").queue();
				return;
			}
			event.getGuild().createTextChannel(textChannelName).queue(channel -> {
				event.reply("Created the text channel <#" + channel.getId() + "> <:done:403285928233402378>").queue();
			});
		}
		
		@Command(value="voice", description="Create a voice channel in the current server")
		@Examples({"create channel voice music lounge"})
		@AuthorPermissions({Permission.MANAGE_CHANNEL})
		@BotPermissions({Permission.MANAGE_CHANNEL})
		public void voice(CommandEvent event, @Argument(value="channel name", endless=true) String voiceChannelName) {
			if (voiceChannelName.length() > 100) {
				event.reply("Voice channel names can not be longer than 100 characters :no_entry:").queue();
				return;
			}
			event.getGuild().createVoiceChannel(voiceChannelName).queue(channel -> {
				event.reply("Created the voice channel `" + channel.getName() + "` <:done:403285928233402378>").queue();
			});
		}
		
	}
	
	public class DeleteChannelCommand extends Sx4Command {
		
		public DeleteChannelCommand() {
			super("delete channel");
			
			super.setDescription("Delete a voice or text channel");
			super.setAliases("deletechannel", "dc");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
			super.setExamples("delete channel voice", "delete channel text");
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="text", description="Delete a specified text channel")
		@Examples({"delete channel text #general", "delete channel text general", "delete channel text 344091594972069888"})
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
		@Examples({"delete channel voice music lounge", "delete channel voice 632981422889500673"})
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
	
	@Command(value="voice kick", aliases={"kick voice", "kickvoice", "voicekick", "vk", "disconnect"}, description="Kicks a user from their current voice channel, It will disconnect the user")
	@Examples({"voice kick @Shea#6653", "voice kick Shea", "voice kick 402557516728369153"})
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
		
		event.getGuild().moveVoiceMember(member, null).queue($ -> {
			event.reply("**" + member.getUser().getAsTag() + "** has been voice kicked <:done:403285928233402378>:ok_hand:").queue();
		});
	}
	
	@Command(value="clear reactions", aliases={"remove reactions", "removereactions", "clearreactions"}, description="Clears all the reactions off a message, has to be executed in the same channel as the message to work", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"clear reactions 643798756604772367"})
	@AuthorPermissions({Permission.MESSAGE_MANAGE})
	@BotPermissions({Permission.MESSAGE_MANAGE})
	public void clearReactions(CommandEvent event, @Argument(value="message id") long messageId) {
		event.getTextChannel().clearReactionsById(messageId).queue($ -> {
			event.reply("Cleared all reactions from that message <:done:403285928233402378>").queue();
		}, e -> {
			if (e instanceof ErrorResponseException) {
				ErrorResponseException exception = (ErrorResponseException) e;
				if (exception.getErrorResponse().equals(ErrorResponse.UNKNOWN_MESSAGE)) {
					event.reply("I could not find that message within this channel :no_entry:").queue();
					return;
				}
			}
		});
	}
	
	public class BlacklistCommand extends Sx4Command {
		
		public BlacklistCommand() {
			super("blacklist");
			
			super.setDescription("Blacklist roles/users/channels from being able to use specific commands/modules");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
			super.setExamples("blacklist add", "blacklist remove", "blacklist info");
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="add", description="Add a role/user/channel to be blacklisted from a specified command/module")
		@Examples({"blacklist add @Shea#6653 fish", "blacklist add #general ship", "blacklist add @Members Mod"})
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void add(CommandEvent event, @Context Database database, @Argument(value="user | role | channel") String argument, @Argument(value="command | module", endless=true) String commandArgument) {
			CategoryImpl module = ArgumentUtils.getModule(commandArgument, true);
			ICommand command = ArgumentUtils.getCommand(commandArgument, true);
			if (command == null && module == null) {
				event.reply("I could not find that command/module :no_entry:").queue();
				return;
			}
			
			String commandName = command == null ? module.getName() : command.getCommandTrigger(); 
			
			Role role = ArgumentUtils.getRole(event.getGuild(), argument);
			Member member = ArgumentUtils.getMember(event.getGuild(), argument);
			GuildChannel channel = ArgumentUtils.getGuildChannel(event.getGuild(), argument);
			if (channel == null && role == null && member == null) {
				event.reply("I could not find that user/role/channel :no_entry:").queue();
				return;
			}
			
			Bson update = null;
			List<Bson> arrayFilters = null;
			String channelDisplay = channel == null ? null : channel instanceof TextChannel ? ((TextChannel) channel).getAsMention() : channel.getName();
			
			List<Document> commands = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("blacklist.commands")).getEmbedded(List.of("blacklist", "commands"), Collections.emptyList());
			for (Document commandData : commands) {
				if (commandData.getString("id").equals(commandName)) {
					arrayFilters = List.of(Filters.eq("command.id", commandName));
					if (channel != null) {
						List<Long> channels = commandData.getEmbedded(List.of("blacklisted", "channels"), Collections.emptyList());					
						for (long channelId : channels) {
							if (channel.getIdLong() == channelId) {
								event.reply("The channel " + channelDisplay + " is already blacklisted from using that " + (command == null ? "module" : "command") + " :no_entry:").queue();
								return;
							}
						}
						
						update = Updates.push("blacklist.commands.$[command].blacklisted.channels", channel.getIdLong());
					} else if (role != null) {
						List<Long> roles = commandData.getEmbedded(List.of("blacklisted", "roles"), Collections.emptyList());				
						for (long roleId : roles) {
							if (role.getIdLong() == roleId) {
								event.reply("The role `" + role.getName() + "` is already blacklisted from using that " + (command == null ? "module" : "command") + " :no_entry:").queue();
								return;
							}
						}
						
						update = Updates.push("blacklist.commands.$[command].blacklisted.roles", role.getIdLong());
					} else if (member != null) {
						List<Long> users = commandData.getEmbedded(List.of("blacklisted", "users"), Collections.emptyList());				
						for (long userId : users) {
							if (member.getIdLong() == userId) {
								event.reply("The user `" + member.getUser().getAsTag() + "` is already blacklisted from using that " + (command == null ? "module" : "command") + " :no_entry:").queue();
								return;
							}
						}
						
						update = Updates.push("blacklist.commands.$[command].blacklisted.users", member.getIdLong());
					}
				}
			}
			
			if (update == null) {
				Document blacklistData = member != null ? new Document("users", List.of(member.getIdLong())) : role != null ? new Document("roles", List.of(role.getIdLong())) : new Document("channels", List.of(channel.getIdLong()));
				Document commandData = new Document("id", commandName).append("blacklisted", blacklistData);
				
				update = Updates.push("blacklist.commands", commandData);
			}
			
			UpdateOptions updateOptions = new UpdateOptions().arrayFilters(arrayFilters).upsert(true);
			database.updateGuildById(event.getGuild().getIdLong(), null, update, updateOptions, (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					String type = channel != null ? "in " + channelDisplay : role != null ? "for anyone in the role `" + role.getName() + "`" : "for **" + member.getUser().getAsTag() + "**";
					event.reply("The " + (command == null ? "module" : "command") + " `" + commandName + "` is now blacklisted " + type + " <:done:403285928233402378>").queue();
				}
			});
		}
		
		@Command(value="remove", description="Remove a blacklist from a user/role/channel from a specified command/module")
		@Examples({"blacklist remove @Shea#6653 fish", "blacklist remove #general ship", "blacklist remove @Members Mod"})
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void remove(CommandEvent event, @Context Database database, @Argument(value="user | role | channel") String argument, @Argument(value="command | module", endless=true) String commandArgument) {
			CategoryImpl module = ArgumentUtils.getModule(commandArgument, true);
			ICommand command = ArgumentUtils.getCommand(commandArgument, true);
			if (command == null && module == null) {
				event.reply("I could not find that command/module :no_entry:").queue();
				return;
			}
			
			String commandName = command == null ? module.getName() : command.getCommandTrigger(); 
			
			Role role = ArgumentUtils.getRole(event.getGuild(), argument);
			Member member = ArgumentUtils.getMember(event.getGuild(), argument);
			GuildChannel channel = ArgumentUtils.getGuildChannel(event.getGuild(), argument);
			if (channel == null && role == null && member == null) {
				event.reply("I could not find that user/role/channel :no_entry:").queue();
				return;
			}
			
			List<Document> commands = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("blacklist.commands")).getEmbedded(List.of("blacklist", "commands"), Collections.emptyList());
			for (Document commandData : commands) {
				if (commandData.getString("id").equals(commandName)) {
					Bson update = null;
					String channelDisplay = channel == null ? null : channel instanceof TextChannel ? ((TextChannel) channel).getAsMention() : channel.getName();
					if (channel != null) {
						List<Long> channels = commandData.getEmbedded(List.of("blacklisted", "channels"), Collections.emptyList());					
						for (long channelId : channels) {
							if (channel.getIdLong() == channelId) {
								if (channels.size() == 1) {
									update = Updates.unset("blacklist.commands.$[command].blacklisted.channels");
								} else {
									update = Updates.pull("blacklist.commands.$[command].blacklisted.channels", channel.getIdLong());
								}
							}
						}
						
						if (update == null) {
							event.reply("The role `" + role.getName() + "` is not blacklisted from using that " + (command == null ? "module" : "command") + " :no_entry:").queue();
							return;
						}
					} else if (role != null) {
						List<Long> roles = commandData.getEmbedded(List.of("blacklisted", "roles"), Collections.emptyList());				
						for (long roleId : roles) {
							if (role.getIdLong() == roleId) {
								if (roles.size() == 1) {
									update = Updates.unset("blacklist.commands.$[command].blacklisted.roles");
								} else {
									update = Updates.pull("blacklist.commands.$[command].blacklisted.roles", role.getIdLong());
								}
							}
						}
						
						if (update == null) {
							event.reply("The role `" + role.getName() + "` is not blacklisted from using that " + (command == null ? "module" : "command") + " :no_entry:").queue();
							return;
						}
					} else if (member != null) {
						List<Long> users = commandData.getEmbedded(List.of("blacklisted", "users"), Collections.emptyList());				
						for (long userId : users) {
							if (member.getIdLong() == userId) {
								if (users.size() == 1) {
									update = Updates.unset("blacklist.commands.$[command].blacklisted.users");
								} else {
									update = Updates.pull("blacklist.commands.$[command].blacklisted.users", member.getIdLong());
								}
							}
						}
						
						if (update == null) {
							event.reply("The user `" + member.getUser().getAsTag() + "` is not blacklisted from using that " + (command == null ? "module" : "command") + " :no_entry:").queue();
							return;
						}
					}
					
					UpdateOptions updateOptions = new UpdateOptions().arrayFilters(List.of(Filters.eq("command.id", commandName)));
					database.updateGuildById(event.getGuild().getIdLong(), null, update, updateOptions, (result, exception) -> {
						if (exception != null) {
							exception.printStackTrace();
							event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
						} else {
							String type = channel != null ? "in " + channelDisplay : role != null ? "for anyone in the role `" + role.getName() + "`" : "for **" + member.getUser().getAsTag() + "**";
							event.reply("The " + (command == null ? "module" : "command") + " `" + commandName + "` is no longer blacklisted " + type + " <:done:403285928233402378>").queue();
						}
					});
					
					return;
				}
			}
			
			event.reply("Nothing is blacklisted from that command/module :no_entry:").queue();
		}
		
		@Command(value="delete", aliases={"del"}, description="Deletes all the blacklist data for a specified command or module")
		@Examples({"blacklist delete fish", "blacklist delete ship", "blacklist delete Mod"})
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void delete(CommandEvent event, @Context Database database, @Argument(value="command | module", endless=true) String commandArgument) {
			CategoryImpl module = ArgumentUtils.getModule(commandArgument, true);
			ICommand command = ArgumentUtils.getCommand(commandArgument, true);
			if (command == null && module == null) {
				event.reply("I could not find that command/module :no_entry:").queue();
				return;
			}
			
			String commandName = command == null ? module.getName() : command.getCommandTrigger();
			
			List<Document> commands = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("blacklist.commands")).getEmbedded(List.of("blacklist", "commands"), Collections.emptyList());
			for (Document commandData : commands) {
				if (commandData.getString("id").equals(commandName)) {
					Document blacklisted = commandData.get("blacklisted", Database.EMPTY_DOCUMENT);
					
					boolean empty = blacklisted.entrySet().stream().allMatch(map -> ((List<?>) map.getValue()).isEmpty());
					if (empty) {
						event.reply("Nothing is blacklisted from that " + (command == null ? "module" : "command") + " :no_entry:").queue();
						return;
					}
					
					UpdateOptions updateOptions = new UpdateOptions().arrayFilters(List.of(Filters.eq("command.id", commandName)));
					database.updateGuildById(event.getGuild().getIdLong(), null, Updates.unset("blacklist.commands.$[command].blacklisted"), updateOptions, (result, exception) -> {
						if (exception != null) {
							exception.printStackTrace();
							event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
						} else {
							event.reply("All blacklist data for `" + commandName + "` has been deleted <:done:403285928233402378>").queue();
						}
					});
					
					return;
				}
			}
			
			event.reply("Nothing is blacklisted from that " + (command == null ? "module" : "command") + " :no_entry:").queue();
		}
		
		@Command(value="reset", aliases={"wipe"}, description="Wipes all blacklist data set in the server, it will give you a prompt to confirm this decision", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"blacklist reset"})
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void reset(CommandEvent event, @Context Database database) {
			event.reply(event.getAuthor().getName() + ", are you sure you want to wipe all blacklist data? (Yes or No)").queue(message -> {
				PagedUtils.getConfirmation(event, 60, event.getAuthor(), confirmed -> {
					if (confirmed == true) {
						List<Document> commands = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("blacklist.commands")).getEmbedded(List.of("blacklist", "commands"), Collections.emptyList());
						if (commands.isEmpty()) {
							event.reply("There is nothing blacklisted in this server :no_entry:").queue();
							return;
						}
						
						database.updateGuildById(event.getGuild().getIdLong(), Updates.unset("blacklist.commands.$[].blacklisted"), (result, exception) -> {
							if (exception != null) {
								exception.printStackTrace();
								event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
							} else {
								message.delete().queue();
								event.reply("All blacklist data has been deleted <:done:403285928233402378>").queue();
							}
						});
					} else {
						event.reply("Cancelled <:done:403285928233402378>").queue();
						message.delete().queue();
					}
				});
			});
		}
		
		@Command(value="toggle", aliases={"enable", "disable"}, description="Enable or disable a command or module in the current server")
		@Examples({"blacklist toggle fish", "blacklist toggle ship", "blacklist toggle Mod"})
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void toggle(CommandEvent event, @Context Database database, @Argument(value="command | module", endless=true) String argument) {
			CategoryImpl module = ArgumentUtils.getModule(argument, true);
			ICommand command = ArgumentUtils.getCommand(argument, true);
			if (command == null && module == null) {
				event.reply("I could not find that command/module :no_entry:").queue();
				return;
			}
			
			String commandName = command == null ? module.getName() : command.getCommandTrigger();
			
			List<String> disabledCommands = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("blacklist.disabled")).getEmbedded(List.of("blacklist", "disabled"), Collections.emptyList());
			for (String disabledCommand : disabledCommands) {
				if (disabledCommand.equals(commandName)) {	
					database.updateGuildById(event.getGuild().getIdLong(), Updates.pull("blacklist.disabled", commandName), (result, exception) -> {
						if (exception != null) {
							exception.printStackTrace();
							event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
						} else {
							event.reply("The " + (command == null ? "module" : "command") + " `" + commandName + "` is no longer disabled in this server <:done:403285928233402378>").queue();
						}
					});
					
					return;
				}
			}
			
			database.updateGuildById(event.getGuild().getIdLong(), Updates.push("blacklist.disabled", commandName), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("The " + (command == null ? "module" : "command") + " `" + commandName + "` is now disabled in this server <:done:403285928233402378>").queue();
				}
			});
		}
		
		@Command(value="disabled", aliases={"disabled commands", "disabledcommands", "disabled modules", "disabledmodules"}, description="View all the disabled commands on the current server", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"blacklist disabled", "blacklist disabled --command", "blacklist disabled --module"})
		public void disabledCommands(CommandEvent event, @Context Database database, @Option(value="module", description="Filters it so it only shows blacklisted modules") boolean moduleOption, @Option(value="command", description="Filters it so it only shows blacklisted commands") boolean commandOption) {
			List<String> disabledCommands = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("blacklist.disabled")).getEmbedded(List.of("blacklist", "disabled"), Collections.emptyList());
			if (disabledCommands.isEmpty()) {
				event.reply("There are no disabled commands or modules in this server :no_entry:").queue();
				return;
			}
			
			if (moduleOption || commandOption) {
				for (String disabledCommand : disabledCommands) {
					if (commandOption) {
						Sx4Command command = ArgumentUtils.getCommand(disabledCommand, true);
						if (command == null) {
							disabledCommands.remove(disabledCommand);
						}
					} else {
						CategoryImpl module = ArgumentUtils.getModule(disabledCommand, true);
						if (module == null) {
							disabledCommands.remove(disabledCommand);
						}
					}
				}
			}
			
			String type = moduleOption == commandOption ? "Commands/Modules" : moduleOption ? "Modules" : "Commands";
			PagedResult<String> paged = new PagedResult<>(disabledCommands)
					.setAuthor("Disabled " + type, null, event.getGuild().getIconUrl())
					.setDeleteMessage(false)
					.setIndexed(false)
					.setPerPage(15);
			
			PagedUtils.getPagedResult(event, paged, 300, null);
		}
		
		@Command(value="info", description="View all the users, roles and channels which are blacklisted from using a specified command or module")
		@Examples({"blacklist info fish", "blacklist info ship", "blacklist info Mod"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void info(CommandEvent event, @Context Database database, @Argument(value="command | module", endless=true) String argument) {
			CategoryImpl module = ArgumentUtils.getModule(argument);
			ICommand command = ArgumentUtils.getCommand(argument);
			if (command == null && module == null) {
				event.reply("I could not find that command/module :no_entry:").queue();
				return;
			}
			
			String commandName = command == null ? module.getName() : command.getCommandTrigger();
			
			List<Document> commands = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("blacklist.commands")).getEmbedded(List.of("blacklist", "commands"), Collections.emptyList());
			for (Document commandData : commands) {
				if (commandData.getString("id").equals(commandName)) {
					List<String> blacklistedString = new ArrayList<>();
					
					Document blacklisted = commandData.get("blacklisted", Database.EMPTY_DOCUMENT);
					if (blacklisted.isEmpty()) {
						event.reply("Nothing is blacklisted from using that " + (command == null ? "module" : "command") + " :no_entry:").queue();
						return;
					}
					
					List<Long> channels = blacklisted.getList("channels", Long.class, Collections.emptyList());
					for (long channelId : channels) {
						GuildChannel channel = event.getGuild().getGuildChannelById(channelId);
						if (channel != null) {
							blacklistedString.add(channel instanceof TextChannel ? ((TextChannel) channel).getAsMention() : channel.getName());
						}
					}
					
					List<Long> roles = blacklisted.getList("roles", Long.class, Collections.emptyList());
					for (long roleId : roles) {
						Role role = event.getGuild().getRoleById(roleId);
						if (role != null) {
							blacklistedString.add(role.getAsMention());
						}
					}
					
					List<Long> users = blacklisted.getList("users", Long.class, Collections.emptyList());
					for (long userId : users) {
						Member member = event.getGuild().getMemberById(userId);
						if (member != null) {
							blacklistedString.add(member.getUser().getAsTag());
						}
					}
					
					if (blacklistedString.isEmpty()) {
						event.reply("Nothing is blacklisted from that " + (command == null ? "module" : "command") + " :no_entry:").queue();
						return;
					}
					
					PagedResult<String> paged = new PagedResult<>(blacklistedString)
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
			super.setExamples("whitelist add", "whitelist remove", "whitelist info");
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="add", description="Add a role/user/channel to be whitelisted to use a specified command/module")
		@Examples({"whitelist add @Shea#6653 fish", "whitelist add #general ship", "whitelist add @Members Mod"})
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void add(CommandEvent event, @Context Database database, @Argument(value="user | role | channel") String argument, @Argument(value="command | module", endless=true) String commandArgument) {
			CategoryImpl module = ArgumentUtils.getModule(commandArgument, true);
			ICommand command = ArgumentUtils.getCommand(commandArgument, true);
			if (command == null && module == null) {
				event.reply("I could not find that command/module :no_entry:").queue();
				return;
			}
			
			String commandName = command == null ? module.getName() : command.getCommandTrigger(); 
			
			Role role = ArgumentUtils.getRole(event.getGuild(), argument);
			Member member = ArgumentUtils.getMember(event.getGuild(), argument);
			GuildChannel channel = ArgumentUtils.getGuildChannel(event.getGuild(), argument);
			if (channel == null && role == null && member == null) {
				event.reply("I could not find that user/role/channel :no_entry:").queue();
				return;
			}
			
			Bson update = null;
			List<Bson> arrayFilters = null;
			String channelDisplay = channel == null ? null : channel instanceof TextChannel ? ((TextChannel) channel).getAsMention() : channel.getName();
			
			List<Document> commands = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("blacklist.commands")).getEmbedded(List.of("blacklist", "commands"), Collections.emptyList());
			for (Document commandData : commands) {
				if (commandData.getString("id").equals(commandName)) {
					arrayFilters = List.of(Filters.eq("command.id", commandName));
					if (channel != null) {
						List<Long> channels = commandData.getEmbedded(List.of("whitelisted", "channels"), Collections.emptyList());					
						for (long channelId : channels) {
							if (channel.getIdLong() == channelId) {
								event.reply("The channel " + channelDisplay + " is already whitelisted from using that " + (command == null ? "module" : "command") + " :no_entry:").queue();
								return;
							}
						}
						
						update = Updates.push("blacklist.commands.$[command].whitelisted.channels", channel.getIdLong());
					} else if (role != null) {
						List<Long> roles = commandData.getEmbedded(List.of("whitelisted", "roles"), Collections.emptyList());				
						for (long roleId : roles) {
							if (role.getIdLong() == roleId) {
								event.reply("The role `" + role.getName() + "` is already whitelisted from using that " + (command == null ? "module" : "command") + " :no_entry:").queue();
								return;
							}
						}
						
						update = Updates.push("blacklist.commands.$[command].whitelisted.roles", role.getIdLong());
					} else if (member != null) {
						List<Long> users = commandData.getEmbedded(List.of("whitelisted", "users"), Collections.emptyList());				
						for (long userId : users) {
							if (member.getIdLong() == userId) {
								event.reply("The user `" + member.getUser().getAsTag() + "` is already whitelisted from using that " + (command == null ? "module" : "command") + " :no_entry:").queue();
								return;
							}
						}
						
						update = Updates.push("blacklist.commands.$[command].whitelisted.users", member.getIdLong());
					}
				}
			}
			
			if (update == null) {
				Document blacklistData = member != null ? new Document("users", List.of(member.getIdLong())) : role != null ? new Document("roles", List.of(role.getIdLong())) : new Document("channels", List.of(channel.getIdLong()));
				Document commandData = new Document("id", commandName).append("whitelisted", blacklistData);
				
				update = Updates.push("blacklist.commands", commandData);
			}
			
			UpdateOptions updateOptions = new UpdateOptions().arrayFilters(arrayFilters).upsert(true);
			database.updateGuildById(event.getGuild().getIdLong(), null, update, updateOptions, (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					String type = channel != null ? "in " + channelDisplay : role != null ? "for anyone in the role `" + role.getName() + "`" : "for **" + member.getUser().getAsTag() + "**";
					event.reply("The " + (command == null ? "module" : "command") + " `" + commandName + "` is now whitelisted " + type + " <:done:403285928233402378>").queue();
				}
			});
		}
		
		@Command(value="remove", description="Remove a whitelist from a user/role/channel from a specified command/module")
		@Examples({"whitelist remove @Shea#6653 fish", "whitelist remove #general ship", "whitelist remove @Members Mod"})
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void remove(CommandEvent event, @Context Database database, @Argument(value="user | role | channel") String argument, @Argument(value="command | module", endless=true) String commandArgument) {
			CategoryImpl module = ArgumentUtils.getModule(commandArgument, true);
			ICommand command = ArgumentUtils.getCommand(commandArgument, true);
			if (command == null && module == null) {
				event.reply("I could not find that command/module :no_entry:").queue();
				return;
			}
			
			String commandName = command == null ? module.getName() : command.getCommandTrigger(); 
			
			Role role = ArgumentUtils.getRole(event.getGuild(), argument);
			Member member = ArgumentUtils.getMember(event.getGuild(), argument);
			GuildChannel channel = ArgumentUtils.getGuildChannel(event.getGuild(), argument);
			if (channel == null && role == null && member == null) {
				event.reply("I could not find that user/role/channel :no_entry:").queue();
				return;
			}
			
			List<Document> commands = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("blacklist.commands")).getEmbedded(List.of("blacklist", "commands"), Collections.emptyList());
			for (Document commandData : commands) {
				if (commandData.getString("id").equals(commandName)) {
					Bson update = null;
					String channelDisplay = channel == null ? null : channel instanceof TextChannel ? ((TextChannel) channel).getAsMention() : channel.getName();
					if (channel != null) {
						List<Long> channels = commandData.getEmbedded(List.of("whitelisted", "channels"), Collections.emptyList());					
						for (long channelId : channels) {
							if (channel.getIdLong() == channelId) {
								if (channels.size() == 1) {
									update = Updates.unset("blacklist.commands.$[command].whitelisted.channels");
								} else {
									update = Updates.pull("blacklist.commands.$[command].whitelisted.channels", channel.getIdLong());
								}
							}
						}
						
						if (update == null) {
							event.reply("The role `" + role.getName() + "` is not whitelisted from using that " + (command == null ? "module" : "command") + " :no_entry:").queue();
							return;
						}
					} else if (role != null) {
						List<Long> roles = commandData.getEmbedded(List.of("whitelisted", "roles"), Collections.emptyList());				
						for (long roleId : roles) {
							if (role.getIdLong() == roleId) {
								if (roles.size() == 1) {
									update = Updates.unset("blacklist.commands.$[command].whitelisted.roles");
								} else {
									update = Updates.pull("blacklist.commands.$[command].whitelisted.roles", role.getIdLong());
								}
							}
						}
						
						if (update == null) {
							event.reply("The role `" + role.getName() + "` is not whitelisted from using that " + (command == null ? "module" : "command") + " :no_entry:").queue();
							return;
						}
					} else if (member != null) {
						List<Long> users = commandData.getEmbedded(List.of("whitelisted", "users"), Collections.emptyList());				
						for (long userId : users) {
							if (member.getIdLong() == userId) {
								if (users.size() == 1) {
									update = Updates.unset("blacklist.commands.$[command].whitelisted.users");
								} else {
									update = Updates.pull("blacklist.commands.$[command].whitelisted.users", member.getIdLong());
								}
							}
						}
						
						if (update == null) {
							event.reply("The user `" + member.getUser().getAsTag() + "` is not whitelisted from using that " + (command == null ? "module" : "command") + " :no_entry:").queue();
							return;
						}
					}
					
					UpdateOptions updateOptions = new UpdateOptions().arrayFilters(List.of(Filters.eq("command.id", commandName)));
					database.updateGuildById(event.getGuild().getIdLong(), null, update, updateOptions, (result, exception) -> {
						if (exception != null) {
							exception.printStackTrace();
							event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
						} else {
							String type = channel != null ? "in " + channelDisplay : role != null ? "for anyone in the role `" + role.getName() + "`" : "for **" + member.getUser().getAsTag() + "**";
							event.reply("The " + (command == null ? "module" : "command") + " `" + commandName + "` is no longer whitelisted " + type + " <:done:403285928233402378>").queue();
						}
					});
					
					return;
				}
			}
			
			event.reply("Nothing is whitelisted from that command/module :no_entry:").queue();
		}
		
		@Command(value="delete", aliases={"del"}, description="Deletes all the whitelist data for a specified command or module")
		@Examples({"whitelist delete fish", "whitelist delete ship", "whitelist delete Mod"})
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void delete(CommandEvent event, @Context Database database, @Argument(value="command | module", endless=true) String commandArgument) {
			CategoryImpl module = ArgumentUtils.getModule(commandArgument, true);
			ICommand command = ArgumentUtils.getCommand(commandArgument, true);
			if (command == null && module == null) {
				event.reply("I could not find that command/module :no_entry:").queue();
				return;
			}
			
			String commandName = command == null ? module.getName() : command.getCommandTrigger();
			
			List<Document> commands = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("blacklist.commands")).getEmbedded(List.of("blacklist", "commands"), Collections.emptyList());
			for (Document commandData : commands) {
				if (commandData.getString("id").equals(commandName)) {
					Document whitelisted = commandData.get("whitelisted", Database.EMPTY_DOCUMENT);
					
					boolean empty = whitelisted.entrySet().stream().allMatch(map -> ((List<?>) map.getValue()).isEmpty());
					if (empty) {
						event.reply("Nothing is whitelisted from that " + (command == null ? "module" : "command") + " :no_entry:").queue();
						return;
					}
					
					UpdateOptions updateOptions = new UpdateOptions().arrayFilters(List.of(Filters.eq("command.id", commandName)));
					database.updateGuildById(event.getGuild().getIdLong(), null, Updates.unset("blacklist.commands.$[command].whitelisted"), updateOptions, (result, exception) -> {
						if (exception != null) {
							exception.printStackTrace();
							event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
						} else {
							event.reply("All blacklist data for `" + commandName + "` has been deleted <:done:403285928233402378>").queue();
						}
					});
					
					return;
				}
			}
			
			event.reply("Nothing is whitelisted from that " + (command == null ? "module" : "command") + " :no_entry:").queue();
		}
		
		@Command(value="reset", aliases={"wipe"}, description="Wipes all whitelist data set in the server, it will give you a prompt to confirm this decision", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"whitelist reset"})
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void reset(CommandEvent event, @Context Database database) {
			event.reply(event.getAuthor().getName() + ", are you sure you want to wipe all blacklist data? (Yes or No)").queue(message -> {
				PagedUtils.getConfirmation(event, 60, event.getAuthor(), confirmed -> {
					if (confirmed == true) {
						List<Document> commands = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("blacklist.commands")).getEmbedded(List.of("blacklist", "commands"), Collections.emptyList());
						if (commands.isEmpty()) {
							event.reply("There is nothing whitelisted in this server :no_entry:").queue();
							return;
						}
						
						database.updateGuildById(event.getGuild().getIdLong(), Updates.unset("blacklist.commands.$[].whitelisted"), (result, exception) -> {
							if (exception != null) {
								exception.printStackTrace();
								event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
							} else {
								message.delete().queue();
								event.reply("All blacklist data has been deleted <:done:403285928233402378>").queue();
							}
						});
					} else {
						event.reply("Cancelled <:done:403285928233402378>").queue();
						message.delete().queue();
					}
				});
			});
		}
		
		@Command(value="info", description="View all the users, roles and channels which are whitelisted from using a specified command or module")
		@Examples({"whitelist info fish", "whitelist info ship", "whitelist info Mod"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void info(CommandEvent event, @Context Database database, @Argument(value="command | module", endless=true) String argument) {
			CategoryImpl module = ArgumentUtils.getModule(argument);
			ICommand command = ArgumentUtils.getCommand(argument);
			if (command == null && module == null) {
				event.reply("I could not find that command/module :no_entry:").queue();
				return;
			}
			
			String commandName = command == null ? module.getName() : command.getCommandTrigger();
			
			List<Document> commands = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("blacklist.commands")).getEmbedded(List.of("blacklist", "commands"), Collections.emptyList());
			for (Document commandData : commands) {
				if (commandData.getString("id").equals(commandName)) {
					List<String> whitelistedString = new ArrayList<>();
					
					Document whitelisted = commandData.get("whitelisted", Database.EMPTY_DOCUMENT);
					if (whitelisted.isEmpty()) {
						event.reply("Nothing is whitelisted from using that " + (command == null ? "module" : "command") + " :no_entry:").queue();
						return;
					}
					
					List<Long> channels = whitelisted.getList("channels", Long.class, Collections.emptyList());
					for (long channelId : channels) {
						GuildChannel channel = event.getGuild().getGuildChannelById(channelId);
						if (channel != null) {
							whitelistedString.add(channel instanceof TextChannel ? ((TextChannel) channel).getAsMention() : channel.getName());
						}
					}
					
					List<Long> roles = whitelisted.getList("roles", Long.class, Collections.emptyList());
					for (long roleId : roles) {
						Role role = event.getGuild().getRoleById(roleId);
						if (role != null) {
							whitelistedString.add(role.getAsMention());
						}
					}
					
					List<Long> users = whitelisted.getList("users", Long.class, Collections.emptyList());
					for (long userId : users) {
						Member member = event.getGuild().getMemberById(userId);
						if (member != null) {
							whitelistedString.add(member.getUser().getAsTag());
						}
					}
					
					if (whitelistedString.isEmpty()) {
						event.reply("Nothing is blacklisted from that " + (command == null ? "module" : "command") + " :no_entry:").queue();
						return;
					}
					
					PagedResult<String> paged = new PagedResult<>(whitelistedString)
							.setDeleteMessage(false)
							.setIncreasedIndex(true)
							.setPerPage(15)
							.setAuthor("Whitelisted from " + commandName, null, event.getGuild().getIconUrl());
				
					PagedUtils.getPagedResult(event, paged, 300, null);
					return;
				}
			}
			
			event.reply("Nothing is whitelisted from using that " + (command == null ? "module" : "command") + " :no_entry:").queue();
		}
	}
	
	public class FakePermissionsCommand extends Sx4Command {
		
		public FakePermissionsCommand() {
			super("fake permissions");
			
			super.setAliases("fake perms", "fakeperms", "fakepermissions", "imaginary permissions", "imaginarypermissions", "img permissions", "imgpermissions", "img perms", "imaginary perms");
			super.setDescription("Allows you to give a role/user permissions which will only work on the bot");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
			super.setExamples("fake permissions add", "fake permissions remove", "fake permissions info");
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="add", description="Add permissions to a specified user or role which will only be appicable on the bot")
		@Examples({"fake permissions add @Shea#6653 manage_messages", "fake permissions add @Mods kick_members ban_members"})
		@AuthorPermissions({Permission.ADMINISTRATOR})
		public void add(CommandEvent event, @Context Database database, @Argument("user | role") String argument, @Argument(value="permission(s)") String[] permissions) {
			Role role = ArgumentUtils.getRole(event.getGuild(), argument);
			Member member = ArgumentUtils.getMember(event.getGuild(), argument);
			if (member == null && role == null) {
				event.reply("I could not find that user/role :no_entry:").queue();
				return;
			}
			
			long permissionValue = 0;
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
			
			List<String> permissionNames = new ArrayList<>();
			for (Permission finalPermission : Permission.getPermissions(permissionValue)) {
				permissionNames.add(finalPermission.getName());
			}
			
			Bson update = null;
			UpdateOptions updateOptions = null;
			if (role != null) {
				List<Document> roles = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("fakePermissions.roles")).getEmbedded(List.of("fakePermissions", "roles"), Collections.emptyList());
				for (Document roleData : roles) {
					if (roleData.getLong("id") == role.getIdLong()) {
						update = Updates.bitwiseOr("fakePermissions.roles.$[role].permissions", permissionValue);
						updateOptions = new UpdateOptions().arrayFilters(List.of(Filters.eq("role.id", role.getIdLong())));
						
						break;
					}
				}
				
				if (update == null) {
					update = Updates.push("fakePermissions.roles", new Document("id", role.getIdLong()).append("permissions", permissionValue));
				}
			} else if (member != null) {		
				List<Document> users = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("fakePermissions.users")).getEmbedded(List.of("fakePermissions", "users"), Collections.emptyList());
				for (Document userData : users) {
					if (userData.getLong("id") == member.getIdLong()) {
						update = Updates.bitwiseOr("fakePermissions.users.$[user].permissions", permissionValue);
						updateOptions = new UpdateOptions().arrayFilters(List.of(Filters.eq("user.id", member.getIdLong())));
						
						break;
					}
				}
				
				if (update == null) {
					update = Updates.push("fakePermissions.users", new Document("id", member.getIdLong()).append("permissions", permissionValue));
				}
			}
			
			database.updateGuildById(event.getGuild().getIdLong(), null, update, updateOptions, (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply((member != null ? "**" + member.getUser().getAsTag() + "**" : "`" + role.getName() + "`") + " can now use commands with the required permissions of " + String.join(", ", permissionNames) + " <:done:403285928233402378>").queue();
				}
			});
		}
		
		@Command(value="remove", description="Remove fake permission(s) from a user/role that have been added to them previously")
		@Examples({"fake permissions remove @Shea#6653 manage_messages", "fake permissions remove @Mods kick_members ban_members"})
		@AuthorPermissions({Permission.ADMINISTRATOR})
		public void remove(CommandEvent event, @Context Database database, @Argument(value="user | role") String argument, @Argument(value="permissions") String[] permissions) {
			Role role = ArgumentUtils.getRole(event.getGuild(), argument);
			Member member = ArgumentUtils.getMember(event.getGuild(), argument);
			if (member == null && role == null) {
				event.reply("I could not find that user/role :no_entry:").queue();
				return;
			}
			
			Bson update = null;
			UpdateOptions updateOptions = null;
			List<String> permissionNames = new ArrayList<>();
			if (role != null) {
				List<Document> roles = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("fakePermissions.roles")).getEmbedded(List.of("fakePermissions", "roles"), Collections.emptyList());
				for (Document roleData : roles) {
					if (roleData.getLong("id") == role.getIdLong()) {
						long currentPermissionValue = roleData.getLong("permissions");
						if (currentPermissionValue == 0) {
							event.reply("That role doesn't have any permissions :no_entry:").queue();
							return;
						}
						
						EnumSet<Permission> rolePermissions = Permission.getPermissions(currentPermissionValue);
						
						long permissionValue = 0;
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
					
						for (Permission finalPermission : Permission.getPermissions(permissionValue)) {
							permissionNames.add(finalPermission.getName());
						}
						
						if (currentPermissionValue - permissionValue == 0) {
							update = Updates.pull("fakePermissions.roles", Filters.eq("id", role.getIdLong()));
						} else {
							update = Updates.inc("fakePermissions.roles.$[role].permissions", -permissionValue);
							updateOptions = new UpdateOptions().arrayFilters(List.of(Filters.eq("role.id", role.getIdLong())));
						}
						
						break;
					}
				}
				
				if (update == null) {
					event.reply("That role doesn't have any permissions :no_entry:").queue();
					return;
				}
			} else if (member != null) {
				List<Document> users = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("fakePermissions.users")).getEmbedded(List.of("fakePermissions", "users"), Collections.emptyList());
				for (Document userData : users) {
					if (userData.getLong("id") == member.getIdLong()) {
						long currentPermissionValue = userData.getLong("permissions");
						if (currentPermissionValue == 0) {
							event.reply("That role doesn't have any permissions :no_entry:").queue();
							return;
						}
						
						EnumSet<Permission> rolePermissions = Permission.getPermissions(currentPermissionValue);
						
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
							event.reply("The user didn't have any of those permissions, check `" + event.getPrefix() + "fake permissions info " + member.getId() + "` for a full list of permissions the role has :no_entry:").queue();
							return;
						}
					
						for (Permission finalPermission : Permission.getPermissions(permissionValue)) {
							permissionNames.add(finalPermission.getName());
						}
						
						if (currentPermissionValue - permissionValue == 0) {
							update = Updates.pull("fakePermissions.users", Filters.eq("id", member.getIdLong()));
						} else {
							update = Updates.inc("fakePermissions.users.$[user].permissions", -permissionValue);
							updateOptions = new UpdateOptions().arrayFilters(List.of(Filters.eq("user.id", member.getIdLong())));
						}
						
						break;
					}
				}
				
				if (update == null) {
					event.reply("That role doesn't have any permissions :no_entry:").queue();
					return;
				}
			}
			
			database.updateGuildById(event.getGuild().getIdLong(), null, update, updateOptions, (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply((member != null ? "**" + member.getUser().getAsTag() + "**" : "`" + role.getName() + "`") + " can no longer use commands with the required permissions of " + String.join(", ", permissionNames) + " <:done:403285928233402378>").queue();
				}
			});
		}
		
		@Command(value="info", description="Shows you what fake permissions a user/role has")
		@Examples({"fake permissions info @Shea#6653", "fake permissions info @Mods"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void info(CommandEvent event, @Context Database database, @Argument(value="user | role", endless=true, nullDefault=true) String argument) {
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
			
			EnumSet<Permission> permissions = null;
			if (role != null) {
				List<Document> roles = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("fakePermissions.roles")).getEmbedded(List.of("fakePermissions", "roles"), Collections.emptyList());
				for (Document roleData : roles) {
					if (roleData.getLong("id") == role.getIdLong()) {
						long permissionValue = roleData.getLong("permissions");
						if (permissionValue == 0) {
							event.reply("That role doesn't have any fake permissions :no_entry:").queue();
							return;
						}
						
						permissions = Permission.getPermissions(permissionValue);
					}
				}
				
				event.reply("That role doesn't have any fake permissions :no_entry:").queue();
			} else if (member != null) {
				List<Document> users = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("fakePermissions.users")).getEmbedded(List.of("fakePermissions", "users"), Collections.emptyList());
				for (Document userData : users) {
					if (userData.getLong("id") == member.getUser().getIdLong()) {
						long permissionValue = userData.getLong("permissions");
						if (permissionValue == 0) {
							event.reply("That user doesn't have any fake permissions :no_entry:").queue();
							return;
						}
						
						permissions = Permission.getPermissions(permissionValue);
					}
				}
				
			}
			
			if (permissions == null) {
				event.reply("That user doesn't have any fake permissions :no_entry:").queue();
				return;
			} else {
				EmbedBuilder embed = new EmbedBuilder();
				embed.setAuthor((member != null ? member.getUser().getAsTag() : role.getName()) + " Fake Permissions", null, member != null ? member.getUser().getEffectiveAvatarUrl() : event.getGuild().getIconUrl());
				embed.setColor(member != null ? member.getColor() : role.getColor());
				embed.setDescription(String.join("\n", permissions.stream().map(permission -> permission.getName().replace(" ", "_").replace("&", "and").toLowerCase()).collect(Collectors.toList())));
				
				event.reply(embed.build()).queue();
			}
		}
		
		@Command(value="in permission", aliases={"inpermission"}, description="Shows you all the users and roles in a certain permission", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"fake permissions in permission manage_messages", "fake permissions in permission ban_members"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void inPermission(CommandEvent event, @Context Database database, @Argument(value="permission") String permissionName) {
			Permission permission = null;
			for (Permission permissionObject : Permission.values()) {
				if (permissionName.toLowerCase().equals(permissionObject.getName().replace(" ", "_").replace("&", "and").toLowerCase())) {
					permission = permissionObject;
				}
			}
			
			if (permission == null) {
				event.reply("I could not find that permission :no_entry:").queue();
				return;
			}
			
			Document data = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("fakePermissions")).get("fakePermissions", Database.EMPTY_DOCUMENT);
			
			List<String> rolesAndUsers = new ArrayList<>();
			for (Document userData : data.getList("users", Document.class, Collections.emptyList())) {
				if (Permission.getPermissions(userData.getLong("permissions")).contains(permission)) {
					Member member = event.getGuild().getMemberById(userData.getLong("id")); 
					rolesAndUsers.add(member == null ? userData.getLong("id") + " (Left Guild)" : member.getUser().getAsTag());
				}
			}
			
			for (Document roleData : data.getList("roles", Document.class, Collections.emptyList())) {
				if (Permission.getPermissions(roleData.getLong("permissions")).contains(permission)) {
					Role role = event.getGuild().getRoleById(roleData.getLong("id")); 
					rolesAndUsers.add(role == null ? roleData.getLong("id") + " (Deleted Role)" : role.getAsMention());
				}
			}
			
			if (rolesAndUsers.isEmpty()) {
				event.reply("There are no roles/users in that permission :no_entry:").queue();
				return;
			}
			
			PagedResult<String> paged = new PagedResult<>(rolesAndUsers)
					.setDeleteMessage(false)
					.setAuthor("Users and Roles In " + permission.getName(), null, event.getGuild().getIconUrl())
					.setPerPage(15)
					.setIndexed(false);
			
			PagedUtils.getPagedResult(event, paged, 300, null);
		}
		
		@Command(value="list", description="Gives a list of permissions you can use when using fake permissions", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"fake permissions list"})
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
	@Examples({"slowmode 5m", "slowmode 10m #general", "slowmode off"})
	@AuthorPermissions({Permission.MANAGE_CHANNEL})
	@BotPermissions({Permission.MANAGE_CHANNEL})
	public void slowmode(CommandEvent event, @Argument(value="time", nullDefault=true) String timeArgument, @Argument(value="channel", nullDefault=true, endless=true) String channelArgument) {
		long slowmodeSeconds;
		
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
		
		if (timeArgument == null) {
			slowmodeSeconds = 0;
		} else {
			if (timeArgument.toLowerCase().equals("off") || timeArgument.toLowerCase().equals("none")) {
				slowmodeSeconds = 0;
			} else {
				slowmodeSeconds = TimeUtils.convertToSeconds(timeArgument);
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
		
		channel.getManager().setSlowmode((int) slowmodeSeconds).queue();
	}
	
	@Command(value="lockdown", description="Makes it so anyone who doesn't override the @everyone roles permissions in the specified channel, can no longer speak in the channel")
	@Examples({"lockdown", "lockdown #general"})
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
		if (channelOverrides != null && channelOverrides.getDenied().contains(Permission.MESSAGE_WRITE)) {
			channel.upsertPermissionOverride(event.getGuild().getPublicRole()).clear(Permission.MESSAGE_WRITE).queue($ -> {
				event.reply(channel.getAsMention() + " is no longer locked down <:done:403285928233402378>").queue();
			});
		} else {
			channel.upsertPermissionOverride(event.getGuild().getPublicRole()).deny(Permission.MESSAGE_WRITE).queue($ -> {
				event.reply(channel.getAsMention() + " has been locked down <:done:403285928233402378>").queue();
			});
		}
	}
	
	@Command(value="region", description="Set the current servers voice region")
	@Examples({"region europe", "region us west", "region india"})
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
	@Examples({"colour role @Members #ffff00", "colour role Members ffff00", "colour role 345718366373150720 255, 255, 0"})
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
		
		if (role.getColor() != null && role.getColor().equals(colour)) {
			event.reply("The role colour is already set to that colour :no_entry:").queue();
			return;
		}
		
		event.reply("The role `" + role.getName() + "` now has a hex of **#" + GeneralUtils.getHex(colour.hashCode()) + "** <:done:403285928233402378>").queue();
		role.getManager().setColor(colour).queue();
	}
	
	public class PrefixCommand extends Sx4Command {
		
		public PrefixCommand() {
			super("prefix");
			
			super.setDescription("Set prefixes for either the current server or your own personal ones, Personal prefixes > server prefixes > default prefixes");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
			super.setContentOverflowPolicy(ContentOverflowPolicy.IGNORE);
			super.setExamples("prefix", "prefix self", "prefix server");
		}
		
		public void onCommand(CommandEvent event, @Context Database database) {
			List<String> guildPrefixes = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("prefixes")).getList("prefixes", String.class, Collections.emptyList());
			List<String> userPrefixes = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("prefixes")).getList("prefixes", String.class, Collections.emptyList());
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setAuthor("Prefix Settings", null, event.getAuthor().getEffectiveAvatarUrl());
			embed.setColor(event.getMember().getColor());
			embed.addField("Default Prefixes", String.join(", ", event.getCommandListener().getDefaultPrefixes()), false);
			embed.addField("Server Prefixes", guildPrefixes.isEmpty() ? "None" : String.join(", ", guildPrefixes), false);
			embed.addField(event.getAuthor().getName() + "'s Prefixes", userPrefixes.isEmpty() ? "None" : String.join(", ", userPrefixes), false);
			
			event.reply(new MessageBuilder().setEmbed(embed.build()).setContent("For help on setting the prefix use `" + event.getPrefix() + "help prefix`").build()).queue();
		}
		
		public class SelfCommand extends Sx4Command {
			
			public SelfCommand() {
				super("self");
				
				super.setDescription("Set personal prefixes that you can use in any server");
				super.setAliases("personal");
				super.setExamples("prefix self", "prefix self add", "prefix self remove");
			}
			
			public void onCommand(CommandEvent event, @Context Database database, @Argument(value="prefixes") String[] prefixes) {
				List<String> cleanPrefixes = new ArrayList<>();
				for (String prefix : prefixes) {
					if (!prefix.equals("")) {
						cleanPrefixes.add(prefix);
					}
				}
				
				if (cleanPrefixes.isEmpty()) {
					event.reply("You cannot have an empty character as a prefix :no_entry:").queue();
					return;
				}
				
				database.updateUserById(event.getAuthor().getIdLong(), Updates.set("prefixes", cleanPrefixes), (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						event.reply(FunUtils.escapeMentions(event.getGuild(), "Your prefixes have been set to `" + String.join("`,  `", cleanPrefixes) + "` <:done:403285928233402378>")).queue();
					}
				});
			}
			
			@Command(value="add", description="Adds specified prefixes to your current personal prefixes")
			@Examples({"prefix self add ?", "prefix self add ? a? \"bot \""})
			public void add(CommandEvent event, @Context Database database, @Argument(value="prefixes") String[] prefixes) {
				List<String> currentPrefixes = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("prefixes")).getList("prefixes", String.class, Collections.emptyList());

				List<String> cleanPrefixes = new ArrayList<>();
				for (String prefix : prefixes) {
					if (!prefix.equals("")) {
						if (currentPrefixes.contains(prefix)) {
							event.reply("You already have `" + prefix + "` as a prefix :no_entry:").queue();
							return;
						}
						
						cleanPrefixes.add(prefix);				
					}
				}
				
				if (cleanPrefixes.isEmpty()) {
					event.reply("You cannot have an empty character as a prefix :no_entry:").queue();
					return;
				}
				
				database.updateUserById(event.getAuthor().getIdLong(), Updates.addEachToSet("prefixes", cleanPrefixes), (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						event.reply(FunUtils.escapeMentions(event.getGuild(), "The prefixes `" + String.join("`, `", cleanPrefixes) + "` have been added to your personal prefixes <:done:403285928233402378>")).queue();
					}
				});
			}
			
			@Command(value="remove", description="Removes specified prefixes from your current personal prefixes")
			@Examples({"prefix self remove ?", "prefix self remove ? a? \"bot \""})
			public void remove(CommandEvent event, @Context Database database, @Argument(value="prefixes") String[] prefixes) {
				List<String> currentPrefixes = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("prefixes")).getList("prefixes", String.class, Collections.emptyList());
				if (currentPrefixes.isEmpty()) {
					event.reply("You have no prefixes to remove :no_entry:").queue();
					return;
				}
				
				for (String prefix : prefixes) {
					if (!currentPrefixes.contains(prefix)) {
						event.reply("You don't have `" + prefix + "` as a prefix :no_entry:").queue();
						return;
					}				
				}
				
				database.updateUserById(event.getAuthor().getIdLong(), Updates.pullAll("prefixes", Arrays.asList(prefixes)), (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						event.reply(FunUtils.escapeMentions(event.getGuild(), "The prefixes `" + String.join("`, `", prefixes) + "` have been removed from your personal prefixes <:done:403285928233402378>")).queue();
					}
				});
			}
			
			@Command(value="reset", description="Reset your personal prefixes, without personal prefixes you will default to server prefixes if any are set or else default prefixes", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
			@Examples({"prefix self reset"})
			public void reset(CommandEvent event, @Context Database database) {
				List<String> currentPrefixes = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("prefixes")).getList("prefixes", String.class, Collections.emptyList());
				if (currentPrefixes.isEmpty()) {
					event.reply("You have no prefixes to reset :no_entry:").queue();
					return;
				}
				
				database.updateUserById(event.getAuthor().getIdLong(), Updates.unset("prefixes"), (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						event.reply("Your prefixes have been reset <:done:403285928233402378>").queue();
					}
				});
			}
			
		}
		
		public class ServerCommand extends Sx4Command {
			
			public ServerCommand() {
				super("server");
				
				super.setDescription("Set server prefixes which users will have to use unless they have personal ones");
				super.setAuthorDiscordPermissions(Permission.MANAGE_SERVER);
				super.setAliases("guild");
				super.setExamples("prefix server", "prefix server add", "prefix server remove");
			}
			
			public void onCommand(CommandEvent event, @Context Database database, @Argument(value="prefixes") String[] prefixes) {
				List<String> cleanPrefixes = new ArrayList<>();
				for (String prefix : prefixes) {
					if (!prefix.equals("")) {
						cleanPrefixes.add(prefix);
					}
				}
				
				if (cleanPrefixes.isEmpty()) {
					event.reply("You cannot have an empty character as a prefix :no_entry:").queue();
					return;
				}
				
				database.updateGuildById(event.getGuild().getIdLong(), Updates.set("prefixes", cleanPrefixes), (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						event.reply("The servers prefixes have been set to `" + String.join("`, `", cleanPrefixes) + "` <:done:403285928233402378>").queue();
					}
				});
			}
			
			@Command(value="add", description="Adds specified prefixes to the current servers prefixes")
			@Examples({"prefix server add ?", "prefix server add ? a? \"bot \""})
			@AuthorPermissions({Permission.MANAGE_SERVER})
			public void add(CommandEvent event, @Context Database database, @Argument(value="prefixes") String[] prefixes) {
				List<String> currentPrefixes = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("prefixes")).getList("prefixes", String.class, Collections.emptyList());
				
				List<String> cleanPrefixes = new ArrayList<>();
				for (String prefix : prefixes) {
					if (!prefix.equals("")) {
						if (currentPrefixes.contains(prefix)) {
							event.reply("The server already has `" + prefix + "` as a prefix :no_entry:").queue();
							return;
						}
						
						cleanPrefixes.add(prefix);				
					}
				}
				
				if (cleanPrefixes.isEmpty()) {
					event.reply("You cannot have an empty character as a prefix :no_entry:").queue();
					return;
				}
				
				database.updateGuildById(event.getGuild().getIdLong(), Updates.addEachToSet("prefixes", cleanPrefixes), (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						event.reply(FunUtils.escapeMentions(event.getGuild(), "The prefixes `" + String.join("`, `", cleanPrefixes) + "` have been added to the server prefixes <:done:403285928233402378>")).queue();
					}
				});
			}
			
			@Command(value="remove", description="Removes specified prefixes from the current servers prefixes")
			@Examples({"prefix server remove ?", "prefix server remove ? a? \"bot \""})
			@AuthorPermissions({Permission.MANAGE_SERVER})
			public void remove(CommandEvent event, @Context Database database, @Argument(value="prefixes") String[] prefixes) {
				List<String> currentPrefixes = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("prefixes")).getList("prefixes", String.class, Collections.emptyList());
				if (currentPrefixes.isEmpty()) {
					event.reply("The server has no prefixes to remove :no_entry:").queue();
					return;
				}
				
				for (String prefix : prefixes) {
					if (!currentPrefixes.contains(prefix)) {
						event.reply("The server doesn't have `" + prefix + "` as a prefix :no_entry:").queue();
						return;
					}				
				}
				
				database.updateGuildById(event.getGuild().getIdLong(), Updates.pullAll("prefixes", Arrays.asList(prefixes)), (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						event.reply(FunUtils.escapeMentions(event.getGuild(), "The prefixes `" + String.join("`, `", prefixes) + "` have been removed from the servers prefixes <:done:403285928233402378>")).queue();
					}
				});
			}
			
			@Command(value="reset", description="Reset the servers prefixes, without server prefixes you will default to the bots default prefixes", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
			@Examples({"prefix server reset"})
			@AuthorPermissions({Permission.MANAGE_SERVER})
			public void reset(CommandEvent event, @Context Database database) {
				List<String> currentPrefixes = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("prefixes")).getList("prefixes", String.class, Collections.emptyList());
				if (currentPrefixes.isEmpty()) {
					event.reply("The server has no prefixes to remove :no_entry:").queue();
					return;
				}
				
				database.updateGuildById(event.getGuild().getIdLong(), Updates.unset("prefixes"), (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						event.reply("The servers prefixes have been reset <:done:403285928233402378>").queue();
					}
				});
			}
			
		}
		
	}
	
	@Command(value="announce", description="Announces anything you input with a mention of a role (If the role isn't mentionable the bot will make it mentionable, ping the role, then make it unmentionable again)")
	@Examples({"announce @Giveaways", "announce Giveaway We are hosting a giveaway in #giveaways"})
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
	@Examples({"mass move General", "mass move Music General"})
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
			event.getGuild().moveVoiceMember(fromVoiceChannel.getMembers().get(membersMoved), toVoiceChannel).queue();
		}
		
		event.reply("Moved **" + membersMoved + "** member" + (membersMoved == 1 ? "" : "s") + " from `" + fromVoiceChannel.getName() + "` to `" + toVoiceChannel.getName() + "` <:done:403285928233402378>").queue();
	}
	
	@Command(value="move", aliases={"move member", "movemember", "moveuser", "move user"}, description="Move a specific user to a specified voice channel")
	@Examples({"move Shea", "move @Shea#6653 General"})
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
		event.getGuild().moveVoiceMember(member, voiceChannel).queue();
	}
	
	@Command(value="rename", aliases={"nick", "nickname"}, description="Set a users nickname in the current server")
	@Examples({"rename Shea", "rename @Shea#6653 Sheaa"})
	@AuthorPermissions({Permission.NICKNAME_MANAGE})
	@BotPermissions({Permission.NICKNAME_MANAGE})
	public void rename(CommandEvent event, @Argument(value="user") String userArgument, @Argument(value="nickname", endless=true, nullDefault=true) String nickname) {
		Member member = ArgumentUtils.getMember(event.getGuild(), userArgument);
		if (member == null) {
			event.reply("I could not find that user :no_entry:").queue();
			return;
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
		event.getGuild().modifyNickname(member, nickname).queue();
	}
	
	public class PruneCommand extends Sx4Command {
		
		public PruneCommand() {
			super("prune");
			
			super.setAliases("clear", "c", "purge");
			super.setDescription("Prune a set amount of message with or without a filter");
			super.setExamples("prune images", "prune 10", "prune @Shea#6653", "prune embeds");
			super.setAuthorDiscordPermissions(Permission.MESSAGE_MANAGE);
			super.setBotDiscordPermissions(Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY);
		}
		
		public void onCommand(CommandEvent event, @Argument(value="user | amount") String argument, @Argument(value="amount", nullDefault=true) Integer amount) {
			try {
				int limit = Integer.parseInt(argument);
				if (limit < 1) {
					event.reply("You have to delete at least 1 message :no_entry:").queue();
					return;
				}
				
				if (limit > 100) {
					event.reply("You cannot delete more than 100 messages at one time :no_entry:").queue();
					return;
				}
				
				event.getMessage().delete().queue($ -> {
					event.getTextChannel().getHistory().retrievePast(limit).queue(messages -> {
						long secondsNow = Clock.systemUTC().instant().getEpochSecond();
						for (Message message : new ArrayList<>(messages)) {
							if (secondsNow - message.getTimeCreated().toEpochSecond() > 1209600) {
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
			} catch(NumberFormatException e) {
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
							if (!message.getAuthor().equals(member.getUser())) {
								messages.remove(message);
							} else if (secondsNow - message.getTimeCreated().toEpochSecond() > 1209600) {
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
		
		@Command(value="images", description="Prunes a set amount of messages which include images in the last 100 messages")
		@Examples({"prune images", "prune images 10"})
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		@BotPermissions({Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY})
		public void images(CommandEvent event, @Argument(value="amount", nullDefault=true) Integer amount) {
			int limit = amount == null ? 100 : amount > 100 ? 100 : amount;
			if (limit < 1) {
				event.reply("You have to delete at least 1 message :no_entry:").queue();
				return;
			}
			
			event.getMessage().delete().queue($ -> {
				event.getTextChannel().getHistory().retrievePast(100).queue(messages -> {
					long secondsNow = Clock.systemUTC().instant().getEpochSecond();
					for (Message message : new ArrayList<>(messages)) {
						if (!message.getAttachments().stream().anyMatch(Attachment::isImage)) {
							messages.remove(message);
						} else if (secondsNow - message.getTimeCreated().toEpochSecond() > 1209600) {
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
		
		@Command(value="embeds", description="Prunes a set amount of messages which include embeds in the last 100 messages")
		@Examples({"prune embeds", "prune embeds 10"})
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		@BotPermissions({Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY})
		public void embeds(CommandEvent event, @Argument(value="amount", nullDefault=true) Integer amount) {
			int limit = amount == null ? 100 : amount > 100 ? 100 : amount;
			if (limit < 1) {
				event.reply("You have to delete at least 1 message :no_entry:").queue();
				return;
			}
			
			event.getMessage().delete().queue($ -> {
				event.getTextChannel().getHistory().retrievePast(100).queue(messages -> {
					long secondsNow = Clock.systemUTC().instant().getEpochSecond();
					for (Message message : new ArrayList<>(messages)) {
						if (message.getEmbeds().isEmpty()) {
							messages.remove(message);
						} else if (secondsNow - message.getTimeCreated().toEpochSecond() > 1209600) {
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
		
		@Command(value="attachments", description="Prunes a set amount of messages which include attachments in the last 100 messages")
		@Examples({"prune attachments", "prune attachments 10"})
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		@BotPermissions({Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY})
		public void attachments(CommandEvent event, @Argument(value="amount", nullDefault=true) Integer amount) {
			int limit = amount == null ? 100 : amount > 100 ? 100 : amount;
			if (limit < 1) {
				event.reply("You have to delete at least 1 message :no_entry:").queue();
				return;
			}
			
			event.getMessage().delete().queue($ -> {
				event.getTextChannel().getHistory().retrievePast(100).queue(messages -> {
					long secondsNow = Clock.systemUTC().instant().getEpochSecond();
					for (Message message : new ArrayList<>(messages)) {
						if (message.getAttachments().isEmpty()) {
							messages.remove(message);
						} else if (secondsNow - message.getTimeCreated().toEpochSecond() > 1209600) {
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
		
		@Command(value="mentions", description="Prunes a set amount of messages which include mentions in the last 100 messages")
		@Examples({"prune mentions", "prune mentions 10"})
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		@BotPermissions({Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY})
		public void mentions(CommandEvent event, @Argument(value="amount", nullDefault=true) Integer amount) {
			int limit = amount == null ? 100 : amount > 100 ? 100 : amount;
			if (limit < 1) {
				event.reply("You have to delete at least 1 message :no_entry:").queue();
				return;
			}
			
			event.getMessage().delete().queue($ -> {
				event.getTextChannel().getHistory().retrievePast(100).queue(messages -> {
					long secondsNow = Clock.systemUTC().instant().getEpochSecond();
					for (Message message : new ArrayList<>(messages)) {
						if (message.getMentions(MentionType.values()).isEmpty()) {
							messages.remove(message);
						} else if (secondsNow - message.getTimeCreated().toEpochSecond() > 1209600) {
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
	@Examples({"bot clean", "bot clean 10"})
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
					} else if (secondsNow - message.getTimeCreated().toEpochSecond() > 1209600) {
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
	@Examples({"contains hello", "contains hello 10"})
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
					} else if (secondsNow - message.getTimeCreated().toEpochSecond() > 1209600) {
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
			super.setExamples("modlog toggle", "modlog channel", "modlog stats");
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="toggle", aliases={"enable", "disable"}, description="Enable/disable modlogs in the current server", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"modlog toggle"})
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void toggle(CommandEvent event, @Context Database database) {
			boolean enabled = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("modlog.enabled")).getEmbedded(List.of("modlog", "enabled"), false);
			database.updateGuildById(event.getGuild().getIdLong(), Updates.set("modlog.enabled", !enabled), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("Modlogs are now " + (enabled ? "disabled" : "enabled")  + " <:done:403285928233402378>").queue();
				}
			});
		}
		
		@Command(value="channel", description="Sets the modlog channel, this is where modlogs will be sent to")
		@Examples({"modlog channel", "modlog channel #modlogs", "modlog channel modlogs", "modlog channel 432898619943813132"})
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void channel(CommandEvent event, @Context Database database, @Argument(value="channel", endless=true, nullDefault=true) String channelArgument) {
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
			
			Long channelId = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("modlog.channelId")).getEmbedded(List.of("modlog", "channelId"), Long.class);
			if (channelId != null && channelId == channel.getIdLong()) {
				event.reply("The modlog channel is already set to that channel :no_entry:").queue();
				return;
			}
			
			database.updateGuildById(event.getGuild().getIdLong(), Updates.set("modlog.channelId", channel.getIdLong()), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("The modlog channel has been set to " + channel.getAsMention() + " <:done:403285928233402378>").queue();
				}
			});
		}
		
		@Command(value="case", description="Edit a modlog case reason providing the moderator is unknown or you are the moderator of the case")
		@Examples({"modlog case 1 Broke ToS", "modlog case 5 Unbanned read case 6"})
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		@BotPermissions({Permission.MESSAGE_HISTORY})
		public void case_(CommandEvent event, @Context Database database, @Argument(value="case numbers") String rangeArgument, @Argument(value="reason", endless=true) String reasonArgument) {
			Long channelId = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("modlog.channelId")).getEmbedded(List.of("modlog", "channelId"), Long.class);
			
			TextChannel channel;
			if (channelId == null) {
				event.reply("The modlog channel isn't set :no_entry:").queue();
				return;
			} else {
				channel = event.getGuild().getTextChannelById(channelId);
				if (channel == null) {
					event.reply("The modlog channel no longer exists :no_entry:").queue();
					return;
				}
			}
			
			String reason = TemplatesCommand.getReason(event.getGuild().getIdLong(), reasonArgument);
			
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
			
			List<Bson> filters = new ArrayList<>();
			for (Integer caseNumber : caseNumbers) {
				filters.add(Filters.eq("id", caseNumber));
			}
			
			List<Document> cases = database.getModLogs().find(Filters.and(Filters.eq("guildId", event.getGuild().getIdLong()), Filters.or(filters))).projection(Projections.include("moderatorId", "messageId", "id")).into(new ArrayList<>());
			if (cases.isEmpty()) {
				event.reply("There are no cases to edit in this server :no_entry:").queue();
				return;
			}
			
			List<WriteModel<Document>> bulkData = new ArrayList<>();
			for (Document caseObject : cases) {
				Long moderatorId = caseObject.getLong("moderatorId");
				Long messageId = caseObject.getLong("messageId");
				
				if (!event.getMember().hasPermission(Permission.ADMINISTRATOR) && moderatorId != null && moderatorId != event.getAuthor().getIdLong()) {
					continue;
				}
				
				if (messageId != null) {
					channel.retrieveMessageById(messageId).queue(message -> {
						MessageEmbed oldEmbed = message.getEmbeds().get(0);
						EmbedBuilder embed = new EmbedBuilder();
						embed.setTitle(oldEmbed.getTitle());
						embed.setTimestamp(oldEmbed.getTimestamp());
						embed.addField(oldEmbed.getFields().get(0));
						embed.addField("Moderator", event.getAuthor().getAsTag(), false);
						embed.addField("Reason", reason, false);
							
						message.editMessage(embed.build()).queue(null, e -> {});
					}, e -> {});
				}
				
				Bson update = Updates.set("reason", reason);
				if (moderatorId == null || moderatorId != event.getAuthor().getIdLong()) {
					update = Updates.combine(update, Updates.set("moderatorId", event.getAuthor().getIdLong()));
				}
				
				bulkData.add(new UpdateOneModel<>(Filters.eq("_id", caseObject.getObjectId("_id")), update));
			}
			
			if (bulkData.isEmpty()) {
				event.reply("None of those modlog cases existed and/or you did not have ownership to them :no_entry:").queue();
				return;
			}
			
			database.updateModLogCases(bulkData, (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("**" + result.getModifiedCount() + "/" + caseNumbers.size() + "** cases have been updated <:done:403285928233402378>").queue();
				}
			});
		}
		
		@Command(value="view case", aliases={"viewcase"}, description="View any case from the modlogs even if it's been deleted", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"modlog view case 1", "modlog view case 5"})
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void viewCase(CommandEvent event, @Context Database database, @Argument(value="case number") int caseNumber) {
			Document modlogCase = database.getModLogs().find(Filters.and(Filters.eq("id", caseNumber), Filters.eq("guildId", event.getGuild().getIdLong()))).first();
			if (modlogCase == null) {
				event.reply("I could not find that modlog case :no_entry:").queue();
				return;
			}
			
			long userId = modlogCase.getLong("userId");
			Long moderatorId = modlogCase.getLong("moderatorId");
			String reason = modlogCase.getString("reason");
			
			User user = event.getShardManager().getUserById(userId);
			
			String modString;
			if (moderatorId == null) {
				modString = "Unknown (Update using `" + event.getPrefix() + "modlog case " + caseNumber + " <reason>`)";
			} else {
				User moderator = event.getShardManager().getUserById(moderatorId);
				modString = moderator == null ? "Unknown Mod (" + moderatorId + ")" : moderator.getAsTag();
			}
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setTitle("Case " + caseNumber + " | " + modlogCase.getString("action"));
			embed.setTimestamp(Instant.ofEpochSecond(modlogCase.getLong("timestamp")));
			embed.addField("User", user == null ? "Unknown User (" + userId + ")" : user.getAsTag(), false);
			embed.addField("Moderator", modString, false);
			embed.addField("Reason", reason == null ? "None (Update using `" + event.getPrefix() + "modlog case " + caseNumber + " <reason>`)" : reason, false);
			event.reply(embed.build()).queue();
		}
		
		@Command(value="reset", aliases={"resetcases", "reset cases", "wipe"}, description="This will delete all modlog data and cases will start from 1 again", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"modlog reset"})
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void reset(CommandEvent event, @Context Database database) {
			int caseAmount = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("modlog.caseAmount")).getEmbedded(List.of("modlog", "caseAmount"), 0);
			if (caseAmount == 0) {
				event.reply("There are no cases to delete in this server :no_entry:").queue();
				return;
			}
			
			event.reply(event.getAuthor().getName() + ", are you sure you want to delete all modlog cases? (Yes or No)").queue(message -> {
				PagedUtils.getConfirmation(event, 30, event.getAuthor(), confirmation -> {
					if (confirmation) {
						database.deleteModLogCases(Filters.eq("guildId", event.getGuild().getIdLong()), (result, exception) -> {
							if (exception != null) {
								exception.printStackTrace();
								event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
							} else {
								event.reply("All modlog cases have been deleted <:done:403285928233402378>").queue();
							}
						});
					} else {
						event.reply("Cancelled <:done:403285928233402378>").queue();
					}
				});
			});
		}
		
		@Command(value="stats", aliases={"settings", "setting"}, description="View the settings for modlogs in the current server", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"modlog stats"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void stats(CommandEvent event, @Context Database database) {
			Document data = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("modlog.caseAmount", "modlog.channelId", "modlog.enabled")).get("modlog", Database.EMPTY_DOCUMENT);
			
			Long channelId = data.getLong("channelId");
			TextChannel channel = channelId == null ? null : event.getGuild().getTextChannelById(channelId);
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setAuthor("ModLog Settings", null, event.getGuild().getIconUrl());
			embed.addField("Status", data.getBoolean("enabled", false) ? "Enabled" : "Disabled", true);
			embed.addField("Channel", channel == null ? "Not Set" : channel.getAsMention(), true);
			embed.addField("Number of Cases", String.valueOf(data.getInteger("caseAmount", 0)), true);
			event.reply(embed.build()).queue();
		}
		
	}
	
	@Command(value="create role", aliases={"createrole", "cr"}, description="Create a role in the current server with an optional colour")
	@Examples({"create role Members", "create role Members #ffff00", "create role Members 255, 255, 0"})
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
		
		event.getGuild().createRole().setName(roleName).setColor(colour).queue(role -> {
			event.reply("I have created the role **" + role.getName() + "** <:done:403285928233402378>:ok_hand:").queue();
		});
	}
	
	@Command(value="delete role", aliases={"deleterole", "dr"}, description="Delete a role in the current server")
	@Examples({"delete role @Members", "delete role Members", "delete role 345718366373150720"})
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
		
		if (role.isManaged()) {
			event.reply("I cannot deleted managed roles :no_entry:").queue();
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
	@Examples({"add role @Shea#6653 Members", "add role Shea 345718366373150720", "add role 402557516728369153 @Members"})
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
		event.getGuild().addRoleToMember(member, role).queue();
	}
	
	@Command(value="remove role", aliases={"removerole", "rr"}, description="Remove a specified role from any user")
	@Examples({"remove role @Shea#6653 Members", "remove role Shea 345718366373150720", "remove role 402557516728369153 @Members"})
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
		event.getGuild().removeRoleFromMember(member, role).queue();
	}
	
	@Command(value="kick", description="Kick a user from the current server")
	@Examples({"kick @Shea#6653", "kick Shea Spamming", "kick 402557516728369153 template:tos & Spamming"})
	@AuthorPermissions({Permission.KICK_MEMBERS})
	@BotPermissions({Permission.KICK_MEMBERS})
	public void kick(CommandEvent event, @Argument(value="user") String userArgument, @Argument(value="reason", endless=true, nullDefault=true) String reasonArgument) {
		Member member = ArgumentUtils.getMember(event.getGuild(), userArgument);
		if (member == null) {
			event.reply("I could not find that user :no_entry:").queue();
			return;
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
		
		String reason = TemplatesCommand.getReason(event.getGuild().getIdLong(), reasonArgument);
		
		event.getGuild().kick(member, (reason == null ? "" : reason) + " [" + event.getAuthor().getAsTag() + "]").queue(kick -> {
			if (!member.getUser().isBot()) {
				member.getUser().openPrivateChannel().queue(channel -> {
					channel.sendMessage(ModUtils.getKickEmbed(event.getGuild(), event.getAuthor(), reason)).queue();
				}, e -> {});
			}
			
			event.reply("**" + member.getUser().getAsTag() + "** has been kicked <:done:403285928233402378>:ok_hand:").queue();
			ModUtils.createModLogAndOffence(event.getGuild(), event.getAuthor(), member.getUser(), "Kick", reason);
		});
		
	}
	
	@Command(value="ban", description="Ban a user from the current server", caseSensitive=true)
	@Examples({"ban @Shea#6653", "ban Shea Spamming", "ban 402557516728369153 template:tos & Spamming"})
	@AuthorPermissions({Permission.BAN_MEMBERS})
	@BotPermissions({Permission.BAN_MEMBERS})
	public void ban(CommandEvent event, @Argument(value="user") String userArgument, @Argument(value="reason", endless=true, nullDefault=true) String reasonArgument) {
		String reason = TemplatesCommand.getReason(event.getGuild().getIdLong(), reasonArgument);
		Member member = ArgumentUtils.getMember(event.getGuild(), userArgument);
		if (member == null) {
			User user = ArgumentUtils.getUser(userArgument);
			if (user == null) {
				ArgumentUtils.getUserInfo(userArgument, userObject -> {
					if (userObject == null) {
						event.reply("I could not find that user :no_entry:").queue();
						return;
					} else {
						event.getGuild().retrieveBanList().queue(bans -> {
							for (Ban ban : bans) {
								if (ban.getUser().equals(userObject)) {
									event.reply("That user is already banned :no_entry:").queue();
									return;
								}
							}
							
							
							event.getGuild().ban(userObject, 1, (reason == null ? "" : reason) + " [" + event.getAuthor().getAsTag() + "]").queue(ban -> {
								event.reply("**" + userObject.getAsTag() + "** has been banned <:done:403285928233402378>:ok_hand:").queue();
								ModUtils.createModLogAndOffence(event.getGuild(), event.getAuthor(), userObject, "Ban", reason);
							});
						});
					}
				});
			} else {
				event.getGuild().retrieveBanList().queue(bans -> {
					for (Ban ban : bans) {
						if (ban.getUser().equals(user)) {
							event.reply("That user is already banned :no_entry:").queue();
							return;
						}
					}
					
					
					event.getGuild().ban(user, 1, (reason == null ? "" : reason) + " [" + event.getAuthor().getAsTag() + "]").queue(ban -> {
						event.reply("**" + user.getAsTag() + "** has been banned <:done:403285928233402378>:ok_hand:").queue();
						ModUtils.createModLogAndOffence(event.getGuild(), event.getAuthor(), user, "Ban", reason);
					});
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
			
			event.getGuild().ban(member, 1, (reason == null ? "" : reason + " ") + "[" + event.getAuthor().getAsTag() + "]").queue(ban -> {
				event.reply("**" + member.getUser().getAsTag() + "** has been banned <:done:403285928233402378>:ok_hand:").queue();
				
				ModUtils.createModLogAndOffence(event.getGuild(), event.getAuthor(), member.getUser(), "Ban", reason);
				
				if (!member.getUser().isBot()) {
					member.getUser().openPrivateChannel().queue(channel -> {
						channel.sendMessage(ModUtils.getBanEmbed(event.getGuild(), event.getAuthor(), reason)).queue();
					}, e -> {});
				}
			});
		}
	}
	
	@Command(value="Ban", description="A ban which doesn't ban, please don't expose", caseSensitive=true)
	@Examples({"Ban @Shea#6653", "Ban Shea", "Ban 402557516728369153"})
	@AuthorPermissions({Permission.BAN_MEMBERS})
	public void fakeBan(CommandEvent event, @Argument(value="user") String userArgument) {
		Member member = ArgumentUtils.getMember(event.getGuild(), userArgument);
		if (member == null) {
			event.reply("I could not find that user :no_entry:").queue();
			return;
		}
		
		event.reply("**" + member.getUser().getAsTag() + "** has been banned <:done:403285928233402378>:ok_hand:").queue();
	}
	
	@Command(value="unban", description="Unban a user who is banned from the current server")
	@Examples({"unban @Shea#6653", "unban Shea Appealed", "unban 402557516728369153 template:mistake"})
	@AuthorPermissions({Permission.BAN_MEMBERS})
	@BotPermissions({Permission.BAN_MEMBERS})
	public void unban(CommandEvent event, @Argument(value="user") String userArgument, @Argument(value="reason", endless=true, nullDefault=true) String reasonArgument) {
		String reason = TemplatesCommand.getReason(event.getGuild().getIdLong(), reasonArgument);
		User user = ArgumentUtils.getUser(userArgument);
		if (user == null) {
			ArgumentUtils.getUserInfo(userArgument, userObject -> {
				if (userObject == null) {
					event.reply("I could not find that user :no_entry:").queue();
					return;
				}
				
				event.getGuild().retrieveBanList().queue(bans -> {
					for (Ban ban : bans) {
						if (ban.getUser().equals(userObject)) {
							event.getGuild().unban(userObject).queue(unban -> {
								event.reply("**" + userObject.getAsTag() + "** has been unbanned <:done:403285928233402378>:ok_hand:").queue();
								ModUtils.createModLog(event.getGuild(), event.getAuthor(), userObject, "Unban", reason);
							});
							
							return;
						}
					}
					
					event.reply("That user is not banned :no_entry:").queue();
				});
			});
		} else {
			event.getGuild().retrieveBanList().queue(bans -> {
				for (Ban ban : bans) {
					if (ban.getUser().equals(user)) {
						event.getGuild().unban(user).queue(unban -> {
							event.reply("**" + user.getAsTag() + "** has been unbanned <:done:403285928233402378>:ok_hand:").queue();
							ModUtils.createModLog(event.getGuild(), event.getAuthor(), user, "Unban", reason);
						});
						
						return;
					}
				}
				
				event.reply("That user is not banned :no_entry:").queue();
			});
		}
	}
	
	@Command(value="channel mute", aliases={"cmute", "channelmute"}, description="Mute a user in the current channel")
	@Examples({"channel mute @Shea#6653", "channel mute Shea Spamming", "channel mute template:emotes & Spamming"})
	@AuthorPermissions({Permission.MESSAGE_MANAGE})
	@BotPermissions({Permission.MANAGE_PERMISSIONS})
	public void channelMute(CommandEvent event, @Argument(value="user") String userArgument, @Argument(value="reason", endless=true, nullDefault=true) String reasonArgument) {
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
		
		String reason = TemplatesCommand.getReason(event.getGuild().getIdLong(), reasonArgument);
		
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
		
		ModUtils.createModLogAndOffence(event.getGuild(), event.getAuthor(), member.getUser(), "Mute", reason);
	}
	
	@Command(value="channel unmute", aliases={"cunmute", "channelunmute"}, description="Unmute a user in the current channel")
	@Examples({"channel unmute @Shea#6653", "channel unmute Shea Times up", "channel unmute 402557516728369153 template:wrong-person, sorry"})
	@AuthorPermissions({Permission.MESSAGE_MANAGE})
	@BotPermissions({Permission.MANAGE_PERMISSIONS})
	public void channelUnmute(CommandEvent event, @Argument(value="user") String userArgument, @Argument(value="reason", endless=true, nullDefault=true) String reasonArgument) {
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
		
		String reason = TemplatesCommand.getReason(event.getGuild().getIdLong(), reasonArgument);
		
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
		
		ModUtils.createModLog(event.getGuild(), event.getAuthor(), member.getUser(), "Unmute", reason);
	}
	
	@Command(value="mute", description="Mute a user server wide for a specified amount of time")
	@Examples({"mute @Shea#6653 20m", "mute Shea 30m Spamming", "mute 402557516728369153 12h template:offensive & Spamming"})
	@AuthorPermissions({Permission.MESSAGE_MANAGE})
	@BotPermissions({Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS})
	public void mute(CommandEvent event, @Context Database database, @Argument(value="user") String userArgument, @Argument(value="time and unit", nullDefault=true) String muteLengthArgument, @Argument(value="reason", endless=true, nullDefault=true) String reasonArgument) {
		String muteString;
		long muteLength;
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
		
		ModUtils.getOrCreateMuteRole(event.getGuild(), (role, error) -> {
			if (error != null) {
				event.reply(error + " :no_entry:").queue();
				return;
			}
			
			if (!event.getSelfMember().canInteract(role)) {
				event.reply("I am unable to mute that user as the mute role is higher or equal than my top role :no_entry:").queue();
				return;
			}
			
			if (member.getRoles().contains(role)) {
				event.reply("That user is already muted :no_entry:").queue();
				return;
			}
			
			Document data = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("mute.users", "templates"));
			List<Document> users = data.getEmbedded(List.of("mute", "users"), Collections.emptyList());
			database.updateGuildById(ModUtils.getMuteUpdate(event.getGuild().getIdLong(), member.getUser().getIdLong(), users, muteLength), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					String reason = TemplatesCommand.getReason(data.getList("templates", Document.class, Collections.emptyList()), reasonArgument);
					
					event.getGuild().addRoleToMember(member, role).queue(mute -> {
						event.reply("**" + member.getUser().getAsTag() + "** has been muted for " + muteString + " <:done:403285928233402378>:ok_hand:").queue();
						
						if (!member.getUser().isBot()) {
							member.getUser().openPrivateChannel().queue(channel -> {
								channel.sendMessage(ModUtils.getMuteEmbed(event.getGuild(), null, event.getAuthor(), muteLength, reason)).queue();
							}, e -> {});
						}
						
						ModUtils.createModLogAndOffence(event.getGuild(), event.getAuthor(), member.getUser(), "Mute (" + muteString + ")", reason);
						
						ScheduledFuture<?> executor = MuteEvents.scheduledExectuor.schedule(() -> MuteEvents.removeUserMute(event.getGuild().getIdLong(), member.getIdLong(), role.getIdLong()), muteLength, TimeUnit.SECONDS);
						MuteEvents.putExecutor(event.getGuild().getIdLong(), member.getUser().getIdLong(), executor);
					});
				}	
			});
		});
	}
	
	@Command(value="unmute", description="Unmute a user early who is currently muted in the server")
	@Examples({"unmute @Shea#6653", "unmute Shea Misunderstanding", "unmute 402557516728369153 template:wrong-person"})
	@AuthorPermissions({Permission.MESSAGE_MANAGE})
	@BotPermissions({Permission.MANAGE_ROLES})
	public void unmute(CommandEvent event, @Context Database database, @Argument(value="user") String userArgument, @Argument(value="reason", endless=true, nullDefault=true) String reasonArgument) {
		Member member = ArgumentUtils.getMember(event.getGuild(), userArgument);
		if (member == null) {
			event.reply("I could not find that user :no_entry:").queue();
			return;
		}
		
		if (member.equals(event.getSelfMember())) {
			event.reply("I am not muted :no_entry:").queue();
			return;
		}
		
		Role role = MuteEvents.getMuteRole(event.getGuild());
		if (role == null) {
			event.reply("**" + member.getUser().getAsTag() + "** is not muted :no_entry:").queue();
			return;
		}
		
		if (!member.getRoles().contains(role)) {
			event.reply("**" + member.getUser().getAsTag() + "** is not muted :no_entry:").queue();
			return;
		}
		
		if (!event.getSelfMember().canInteract(role)) {
			event.reply("I am unable to unmute that user as the mute role is higher or equal than my top role :no_entry:").queue();
			return;
		}
		
		database.updateGuildById(event.getGuild().getIdLong(), Updates.pull("mute.users", Filters.eq("id", member.getIdLong())), (result, exception) -> {
			if (exception != null) {
				exception.printStackTrace();
				event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
			} else {
				String reason = TemplatesCommand.getReason(event.getGuild().getIdLong(), reasonArgument);
				
				event.getGuild().removeRoleFromMember(member, role).queue(unmute -> {
					event.reply("**" + member.getUser().getAsTag() + "** has been unmuted <:done:403285928233402378>:ok_hand:").queue();
					
					if (!member.getUser().isBot()) {
						member.getUser().openPrivateChannel().queue(channel -> {
							channel.sendMessage(ModUtils.getUnmuteEmbed(event.getGuild(), null, event.getAuthor(), reason)).queue();
						}, e -> {});
					}
					
					ModUtils.createModLog(event.getGuild(), event.getAuthor(), member.getUser(), "Unmute", reason);
					MuteEvents.cancelExecutor(event.getGuild().getIdLong(), member.getUser().getIdLong());
				});
			}
		});
	}
	
	@Command(value="muted list", aliases={"mutedlist", "muted"}, description="Gives a list of all the current users who are muted and the time they have left", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"muted list"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void mutedList(CommandEvent event, @Context Database database) {
		long timestamp = Clock.systemUTC().instant().getEpochSecond();
		
		List<Document> mutedUsers = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("mute.users")).getEmbedded(List.of("mute", "users"), Collections.emptyList());
		for (Document userData : new ArrayList<>(mutedUsers)) {
			Member member = event.getGuild().getMemberById(userData.getLong("id"));
			if (member == null) {
				mutedUsers.remove(userData);
			}
		}
		
		if (mutedUsers.isEmpty()) {
			event.reply("No one is muted in the server :no_entry:").queue();
			return;
		}
		
		mutedUsers.sort((a, b) -> Long.compare(a.getLong("timestamp"), b.getLong("timestamp")));
		PagedResult<Document> paged = new PagedResult<>(mutedUsers)
				.setAuthor("Muted Users", null, event.getGuild().getIconUrl())
				.setIndexed(false)
				.setDeleteMessage(false)
				.setPerPage(20)
				.setFunction(user -> {
					Member member = event.getGuild().getMemberById(user.getLong("id"));
					Long duration = user.getLong("duration");
					long timestampOfMute = user.getLong("timestamp");
					long timeTillUnmute = duration == null ? -1 : timestampOfMute - timestamp + duration;
					
					return member.getUser().getAsTag() + " - " + (timeTillUnmute == -1 ? "Infinite" : TimeUtils.toTimeString(timeTillUnmute, ChronoUnit.SECONDS));
				});
		
		PagedUtils.getPagedResult(event, paged, 300, null);
	}
	
	public static class TemplatesCommand extends Sx4Command {
		
		public static String getReason(long guildId, String reason) {
			if (reason == null) {
				return null;
			}
			
			List<Document> templates = Database.get().getGuildById(guildId, null, Projections.include("templates")).getList("templates", Document.class, Collections.emptyList());
			
			return TemplatesCommand.getReason(templates, reason);
		}
		
		public static String getReason(List<Document> templates, String reason) {
			if (reason == null) {
				return null;
			}
			
			int index = 0;
			while ((index = reason.indexOf(':', index + 1)) != -1) {
				int prefixIndex = index;
				StringBuilder prefix = new StringBuilder();
				while (!prefix.toString().equalsIgnoreCase("t") && !prefix.toString().equalsIgnoreCase("template") && prefixIndex > 0) {
					prefix.insert(0, reason.charAt(--prefixIndex));
				}
				
				if (prefix.toString().equalsIgnoreCase("t") || prefix.toString().equalsIgnoreCase("template")) {
					StringBuilder template = new StringBuilder();
					
					if (reason.charAt(index + 1) == '"' && reason.indexOf('"', index + 2) != -1) {
						char character;
						while ((character = reason.charAt(++index + 1)) != '"') {
							template.append(character);
						}
						
						index += 2;
					} else {
						char character;
						while (index != reason.length() - 1 && (character = reason.charAt(++index)) != ' ') {
							template.append(character);
						}
						
						if (index == reason.length() - 1) {
							index++;
						}
					}
					
					for (Document templateData : templates) {
						if (templateData.getString("template").equalsIgnoreCase(template.toString())) {
							reason = reason.substring(0, prefixIndex) + templateData.getString("reason") + reason.substring(index);
						}
					}
				}
			}
			
			return reason;
		}
		
		public TemplatesCommand() {
			super("templates");
			
			super.setDescription("Add preset templates which can be used in reasons as shortcuts to common reasonings for using a mod command");
			super.setAliases("template");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
			super.setExamples("templates add", "templates remove", "templates list");
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="add", description="Add a template which can be used across all mod commands which can have a reason")
		@Examples({"templates add tos Broke ToS", "templates add spam Too much spamming"})
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void add(CommandEvent event, @Context Database database, @Argument(value="template name") String templateName, @Argument(value="reason", endless=true) String reason) {
			List<Document> templates = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("templates")).getList("templates", Document.class, Collections.emptyList());
			for (Document template : templates) {
				if (template.getString("template").equalsIgnoreCase(templateName)) {
					event.reply("There is already a template named `" + templateName.toLowerCase() + "` :no_entry:").queue();
					return;
				}
			}
			
			Document template = new Document("template", templateName.toLowerCase()).append("reason", reason);
			database.updateGuildById(event.getGuild().getIdLong(), Updates.push("templates", template), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("`" + templateName.toLowerCase() + "` is now a template <:done:403285928233402378>").queue();
				}
			});
		}
		
		@Command(value="edit", description="Edits a template from the templates in the current server")
		@Examples({"templates edit tos Broke discord ToS", "templates edit spam Continuous spamming"})
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void edit(CommandEvent event, @Context Database database, @Argument(value="template name") String templateName, @Argument(value="reason", endless=true) String reason) {
			List<Document> templates = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("templates")).getList("templates", Document.class, Collections.emptyList());
			for (Document template : templates) {
				if (template.getString("template").equalsIgnoreCase(templateName)) {
					UpdateOptions updateOptions = new UpdateOptions().arrayFilters(List.of(Filters.eq("template.template", templateName.toLowerCase())));
					database.updateGuildById(event.getGuild().getIdLong(), null, Updates.set("templates.$[template].reason", reason), updateOptions, (result, exception) -> {
						if (exception != null) {
							exception.printStackTrace();
							event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
						} else {
							event.reply("`" + templateName.toLowerCase() + "` is no longer a template <:done:403285928233402378>").queue();
						}
					});
					
					return;
				}
			}
			
			event.reply("There is no template named `" + templateName.toLowerCase() + "` :no_entry:").queue();
		}
		
		@Command(value="remove", description="Remove a template from the templates in the current server")
		@Examples({"templates remove tos", "templates remove spam"})
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void remove(CommandEvent event, @Context Database database, @Argument(value="template name", endless=true) String templateName) {
			List<Document> templates = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("templates")).getList("templates", Document.class, Collections.emptyList());
			for (Document template : templates) {
				if (template.getString("template").equalsIgnoreCase(templateName)) {
					database.updateGuildById(event.getGuild().getIdLong(), Updates.pull("templates", Filters.eq("template", templateName.toLowerCase())), (result, exception) -> {
						if (exception != null) {
							exception.printStackTrace();
							event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
						} else {
							event.reply("`" + templateName.toLowerCase() + "` is no longer a template <:done:403285928233402378>").queue();
						}
					});
					
					return;
				}
			}
			
			event.reply("There is no template named `" + templateName.toLowerCase() + "` :no_entry:").queue();
		}
		
		@Command(value="list", description="Lists all the templates in the current server", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"templates list"})
		public void list(CommandEvent event, @Context Database database) {
			List<Document> templates = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("templates")).getList("templates", Document.class, Collections.emptyList());
			if (templates.isEmpty()) {
				event.reply("This server has no templates :no_entry:").queue();
				return;
			}
			
			PagedResult<Document> paged = new PagedResult<>(templates)
					.setDeleteMessage(false)
					.setPerPage(5)
					.setCustomFunction(page -> {
						List<Document> templatesArray = page.getArray();
						
						EmbedBuilder embed = new EmbedBuilder();
						embed.setTitle("Page " + page.getCurrentPage() + "/" + page.getMaxPage());
						embed.setAuthor("Templates", null, event.getGuild().getIconUrl());
						embed.setFooter("next | previous | go to <page_number> | cancel", null);
						
						for (int i = page.getCurrentPage() * page.getPerPage() - page.getPerPage(); i < (page.getCurrentPage() == page.getMaxPage() ? templatesArray.size() : page.getCurrentPage() * page.getPerPage()); i++) {
							Document template = templatesArray.get(i);
							
							embed.addField(template.getString("template"), template.getString("reason"), false);
						}
						
						return embed.build();
					});
			
			PagedUtils.getPagedResult(event, paged, 300, null);
		}
	}
	
	public class WarnConfigurationCommand extends Sx4Command {
		
		public WarnConfigurationCommand() {
			super("warn configuration");
			
			super.setDescription("Configure your warn system to have different stages per warn you can choose between mute of any duration, kick, ban and just warn");
			super.setAliases("warn config", "warnconfig", "warnconfiguration");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
			super.setExamples("warn configuration set", "warn configuration punishments", "warn configuration list");
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="punishments", aliases={"punish"}, description="Enables/disables punishments for warnings, this changes whether warns have actions depending on the amount of warns a user has", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"warn configuration punishments"})
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void punishments(CommandEvent event, @Context Database database) {
			boolean punishments = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("warn.punishments")).getEmbedded(List.of("warn", "punishments"), true);
			database.updateGuildById(event.getGuild().getIdLong(), Updates.set("warn.punishments", !punishments), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("Punishments for warnings have been " + (punishments ? "disabled" : "enabled") + " <:done:403285928233402378>").queue();
				}
			});
		}
		
		private final List<String> actions = List.of("ban", "mute", "kick");
		
		@SuppressWarnings("unchecked")
		@Command(value="set", aliases={"add"}, description="Set a certain warning to a specified action to happen when a user reaches that warning")
		@Examples({"warn configuration set 5 ban", "warn configuration set 2 mute", "warn configuration set 3 mute 2 hours"})
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void set(CommandEvent event, @Context Database database, @Argument(value="warning number") int warningNumber, @Argument(value="action", endless=true) String action) {
			String actionLower = action.toLowerCase();
			
			if (warningNumber <= 0 || warningNumber > 50) {
				event.reply("Warnings start at 1 and have a max warning of 50 :no_entry:").queue();
				return;
			}
			
			Document configuration = new Document("warning", warningNumber);
			if (actions.contains(actionLower) || actionLower.startsWith("mute")) {
				if (actionLower.equals("mute")) {
					configuration.append("action", actionLower).append("duration", 1800L);
				} else if (actionLower.startsWith("mute")) {
					String timeString = actionLower.split(" ", 2)[1];
					long muteLength = TimeUtils.convertToSeconds(timeString);
					if (muteLength <= 0) {
						event.reply("Invalid time format, make sure it's formatted with a numerical value then a letter representing the time (d for days, h for hours, m for minutes, s for seconds) and make sure it's in order :no_entry:").queue();
						return;
					}
					
					configuration.append("action", "mute").append("duration", muteLength);
				} else {
					configuration.append("action", actionLower);
				}
				
				Bson update = null;
				UpdateOptions updateOptions = null;
				
				List<Document> warnConfiguration = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("warn.configuration")).getEmbedded(List.of("warn", "configuration"), List.class);
				if (warnConfiguration == null) {
					List<Document> newWarnConfiguration = new ArrayList<>(ModUtils.DEFAULT_WARN_CONFIGURATION);
					boolean updated = false;
					for (Document warning : newWarnConfiguration) {
						if (warning.getInteger("warning") == warningNumber) {
							if (warning.equals(configuration)) {
								event.reply("Warning #" + warningNumber + " already does that action :no_entry:").queue();
								return;
							}
							
							newWarnConfiguration.remove(warning);
							newWarnConfiguration.add(configuration);
							updated = true;
							
							break;
						}
					}
					
					if (!updated) {
						newWarnConfiguration.add(configuration);
					}
					
					update = Updates.set("warn.configuration", newWarnConfiguration);
				} else {	
					for (Document warning : warnConfiguration) {
						if (warning.getInteger("warning") == warningNumber) {
							if (warning.equals(configuration)) {
								event.reply("Warning #" + warningNumber + " already does that action :no_entry:").queue();
								return;
							}
							
							update = Updates.set("warn.configuration.$[warning]", configuration);
							updateOptions = new UpdateOptions().arrayFilters(List.of(Filters.eq("warning.warning", warningNumber)));
							break;
						}
					}
					
					if (update == null) {
						update = Updates.push("warn.configuration", configuration);
					}
				}
				
				database.updateGuildById(event.getGuild().getIdLong(), null, update, updateOptions, (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						event.replyFormat("Warning #%d will now %s the user %s<:done:403285928233402378>", warningNumber, configuration.containsKey("duration") ? "mute" : actionLower, configuration.containsKey("duration") ? "for " + TimeUtils.toTimeString(configuration.getLong("duration"), ChronoUnit.SECONDS) + " " : "").queue();
					}
				});
			} else {
				event.reply("Invalid action, make sure it is either mute, kick or ban :no_entry:").queue();
			}
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="remove", description="Removes a warning which is set in the server, to view the configuration use `warn configuration list`", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"warn configuration remove 5", "warn configuration remove 2", "warn configuration remove 3"})
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void remove(CommandEvent event, @Context Database database, @Argument(value="warning number") int warningNumber) {
			Bson update = null;
			
			List<Document> warnConfiguration = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("warn.configuration")).getEmbedded(List.of("warn", "configuration"), List.class);
			if (warnConfiguration == null) {
				boolean updated = false;
				
				List<Document> newWarnConfiguration = new ArrayList<>(ModUtils.DEFAULT_WARN_CONFIGURATION);
				for (Document warning : newWarnConfiguration) {
					if (warning.getInteger("warning") == warningNumber) {
						newWarnConfiguration.remove(warning);
						updated = true;
						break;
					}
				}
				
				if (!updated) {
					event.reply("That warning is not set up to an action :no_entry:").queue();
					return;
				}
				
				update = Updates.set("warn.configuration", newWarnConfiguration);
			} else {
				for (Document warning : warnConfiguration) {
					if (warning.getInteger("warning") == warningNumber) { 
						if (warnConfiguration.size() == 1) {
							event.reply("This is the last warning you have setup, if you want to go back to the default one use `" + event.getPrefix() + "warn configuration reset` :no_entry:").queue();
							return;
						}
						
						update = Updates.pull("warn.configuration", Filters.eq("warning", warningNumber));
					}
				}
				
				if (update == null) {
					event.reply("That warning is not set up to an action :no_entry:").queue();
					return;
				}
			}
			
			database.updateGuildById(event.getGuild().getIdLong(), update, (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("Warning #" + warningNumber + " has been removed <:done:403285928233402378>").queue();
				}
			});
		}
		
		@Command(value="reset", aliases={"wipe", "delete"}, description="Reset all warn configuration data set up in the server", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"warn configuration reset"})
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void reset(CommandEvent event, @Context Database database) {
			event.reply(event.getAuthor().getName() + ", are you sure you want to reset **all** warn configuration data? (Yes or No)").queue(message -> {
				PagedUtils.getConfirmation(event, 300, event.getAuthor(), confirmation -> {
					if (confirmation) {
						List<Document> warnConfiguration = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("warn.configuration")).getEmbedded(List.of("warn", "configuration"), Collections.emptyList());
						if (warnConfiguration.isEmpty()) {
							event.reply("Warn configuration has not been set up in this server :no_entry:").queue();
							return;
						}
						
						database.updateGuildById(event.getGuild().getIdLong(), Updates.unset("warn.configuration"), (result, exception) -> {
							if (exception != null) {
								exception.printStackTrace();
								event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
							} else {
								event.reply("All warning configuration data has been reset <:done:403285928233402378>").queue();
							}
						});
					} else {
						event.reply("Cancelled <:done:403285928233402378>").queue();
					}
				});
			});
		}
		
		@Command(value="list", description="Shows the current configuration for warnings in the current server", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"warn configuration list"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void list(CommandEvent event, @Context Database database) {
			List<Document> warnConfiguration = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("warn.configuration")).getEmbedded(List.of("warn", "configuration"), ModUtils.DEFAULT_WARN_CONFIGURATION);
			
			warnConfiguration.sort((a, b) -> Integer.compare(a.getInteger("warning"), b.getInteger("warning")));
			PagedResult<Document> paged = new PagedResult<>(warnConfiguration)
					.setDeleteMessage(false)
					.setPerPage(20)
					.setIndexed(false)
					.setAuthor("Warn Configuration", null, event.getGuild().getIconUrl())
					.setFunction(warning -> {
						if (warning.containsKey("duration")) {
							return "Warning #" + warning.getInteger("warning") + ": " + GeneralUtils.title(warning.getString("action")) + " (" + TimeUtils.toTimeString(warning.getLong("duration"), ChronoUnit.SECONDS) + ")";
						} else {
							return "Warning #" + warning.getInteger("warning") + ": " + GeneralUtils.title(warning.getString("action"));
						}
					});
			
			PagedUtils.getPagedResult(event, paged, 300, null);
		}
		
	}
	
	@Command(value="warn", description="Warn a user in the current server")
	@Examples({"warn @Shea#6653", "warn Shea Ads", "warn 402557516728369153 template:tos & Spamming"})
	@AuthorPermissions({Permission.MESSAGE_MANAGE})
	public void warn(CommandEvent event, @Context Database database, @Argument(value="user") String userArgument, @Argument(value="reason", endless=true, nullDefault=true) String reasonArgument) {
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
		
		String reason = TemplatesCommand.getReason(event.getGuild().getIdLong(), reasonArgument);
		
		WarnUtils.handleWarning(event.getGuild(), member, event.getMember(), reason, (warning, exception) -> {
			if (exception != null) {
				event.reply(exception.getMessage() + " :no_entry:").queue();
				return;
			} else {
				Document data = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("mute.users", "warn.users", "warn.configuration"));
				List<Document> mutedUsers = data.getEmbedded(List.of("mute", "users"), Collections.emptyList());
				List<Document> warnedUsers = data.getEmbedded(List.of("warn", "users"), Collections.emptyList());
				
				List<Document> warnConfiguration = data.getEmbedded(List.of("warn", "configuration"), Collections.emptyList());
				if (warnConfiguration.isEmpty()) {
					warnConfiguration = ModUtils.DEFAULT_WARN_CONFIGURATION;
				}
				
				Long duration = warning.getDuration();
				
				List<WriteModel<Document>> bulkData = new ArrayList<>();
				if (warning.getAction().equals("mute")) {
					bulkData.add(ModUtils.getMuteUpdate(event.getGuild().getIdLong(), member.getIdLong(), mutedUsers, duration));
				}
				
				bulkData.add(WarnUtils.getUserUpdate(warnedUsers, warnConfiguration, event.getGuild().getIdLong(), member.getIdLong(), reason));
				
				database.bulkWriteGuilds(bulkData, (result, writeException) -> {
					if (writeException != null) {
						writeException.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(writeException)).queue();
					} else {
						event.replyFormat("**%s** has been %s%s (%s warning) <:done:403285928233402378>", member.getUser().getAsTag(), WarnUtils.getSuffixedAction(warning.getAction()), duration != null ? " for " + TimeUtils.toTimeString(duration, ChronoUnit.SECONDS) : "", GeneralUtils.getNumberSuffix(warning.getWarning())).queue();
					}
				});
			}
		});
	}
	
	@Command(value="warn list", aliases={"warnlist", "warns"}, description="Gets a list of users who are warned in the current server", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"warn list"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void warnList(CommandEvent event, @Context Database database) {
		List<Document> users = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("warn.users")).getEmbedded(List.of("warn", "users"), Collections.emptyList());
		if (users.isEmpty()) {
			event.reply("No one has been warned in this server :no_entry:").queue();
			return;
		}
		
		Member member;
		for (Document user : new ArrayList<>(users)) {
			member = event.getGuild().getMemberById(user.getLong("id")); 
			if (member == null || user.getInteger("warnings") == 0) {
				users.remove(user);
			}
		}
		
		users.sort((a, b) -> Long.compare(b.getInteger("warnings"), a.getInteger("warnings"))); 
		PagedResult<Document> paged = new PagedResult<>(users)
				.setAuthor("Warned Users", null, event.getGuild().getIconUrl())
				.setPerPage(15)
				.setDeleteMessage(false)
				.setIndexed(false)
				.setFunction(userData -> {
					Member user = event.getGuild().getMemberById(userData.getLong("id")); 
					return "`" + user.getUser().getAsTag() + "` - Warning **#" + userData.getInteger("warnings") + "**";
				});
		
		PagedUtils.getPagedResult(event, paged, 300, null);
	}
	
	@Command(value="warnings", description="Displays how many warnings a specified user has", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"warnings", "warnings @Shea#6653", "warnings Shea", "warnings 402557516728369153"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void warnings(CommandEvent event, @Context Database database, @Argument(value="user", endless=true, nullDefault=true) String userArgument) {
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
		
		Document data = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("warn.users", "warn.punishments", "warn.configuration")).get("warn", Database.EMPTY_DOCUMENT);
		
		List<Document> users = data.getList("users", Document.class, Collections.emptyList());
		List<Document> configuration = data.getList("configuration", Document.class, ModUtils.DEFAULT_WARN_CONFIGURATION);
		boolean punishments = data.getBoolean("punishments", true);
		
		UserWarning userWarning = WarnUtils.getUserWarning(users, member.getIdLong());
		Warning nextWarning;
		if (punishments) {
			nextWarning = WarnUtils.getWarning(configuration, userWarning.getWarning() + 1);
		} else {
			nextWarning = new Warning(userWarning.getWarning() + 1, "warn");
		}
		
		StringBuilder reasons = new StringBuilder();
		for (int i = 0; i < userWarning.getReasons().size(); i++) {
			String reason = userWarning.getReasons().get(i);
			if (reasons.length() + reason.length() >= MessageEmbed.VALUE_MAX_LENGTH) {
				continue;
			}
			
			reasons.append("`" + reason + "`, ");
		}
		
		if (reasons.length() != 0) {
			reasons.setLength(reasons.length() - 2);
		}
				
		EmbedBuilder embed = new EmbedBuilder();
		embed.setColor(member.getColor());
		embed.setAuthor(member.getUser().getAsTag(), null, member.getUser().getEffectiveAvatarUrl());
		embed.setDescription(member.getUser().getName() + " is on " + userWarning.getWarning() + " warning" + (userWarning.getWarning() == 1 ? "" : "s"));
		embed.addField("Next Action", GeneralUtils.title(nextWarning.getAction()), false);
		embed.addField("Reasons", reasons.length() == 0 ? "None" : reasons.toString(), false);
		event.reply(embed.build()).queue();
	}
	
	@Command(value="set warnings", aliases={"setwarnings", "set warns", "setwarns"}, description="Set the warning amount for a specified user", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"set warnings @Shea#6653 1", "set warnings Shea 3", "set warnings 402557516728369153 4"})
	@AuthorPermissions({Permission.MESSAGE_MANAGE})
	public void setWarnings(CommandEvent event, @Context Database database, @Argument(value="user") String userArgument, @Argument(value="warning amount") int warningAmount) {
		if (warningAmount < 1) {
			event.reply("The warning amount has to be at least 1 :no_entry:").queue();
			return;
		}
		
		Document data = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("warn.configuration", "warn.users")).get("warn", Database.EMPTY_DOCUMENT);
		List<Document> configuration = data.getList("configuration", Document.class, ModUtils.DEFAULT_WARN_CONFIGURATION);
		
		int maxWarning = WarnUtils.getMaxWarning(configuration);
		if (warningAmount > maxWarning) {
			event.reply("The max amount of warnings you can give is **" + maxWarning + "** :no_entry:").queue();
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
		
		Bson update = null;
		UpdateOptions updateOptions = null;
		
		List<Document> users = data.getList("users", Document.class, Collections.emptyList());
		for (Document user : users) {
			if (user.getLong("id") == member.getIdLong()) {
				updateOptions = new UpdateOptions().arrayFilters(List.of(Filters.eq("user.id", member.getIdLong())));
				update = Updates.set("warn.users.$[user].warnings", warningAmount);
				
				break;
			}
		}
		
		if (update == null) {
			update = Updates.push("warn.users", new Document("id", member.getIdLong()).append("warnings", warningAmount));
		}
		
		database.updateGuildById(event.getGuild().getIdLong(), null, update, updateOptions, (result, exception) -> {
			if (exception != null) {
				exception.printStackTrace();
				event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
			} else {
				event.reply("**" + member.getUser().getAsTag() + "** has had their warnings set to **" + warningAmount + "** <:done:403285928233402378>").queue();
			}
		});
	}
	
	@Command(value="reset warnings", aliases={"resetwarnings", "resetwarns", "reset warns"}, description="Reset warnings for a specified user, this'll set their warning amount of 0 and get rid of their reasons")
	@Examples({"reset warnings @Shea#6653", "reset warnings Shea", "reset warnings 402557516728369153"})
	@AuthorPermissions({Permission.MESSAGE_MANAGE})
	public void resetWarnings(CommandEvent event, @Context Database database, @Argument(value="user", endless=true) String userArgument) {
		Member member = ArgumentUtils.getMember(event.getGuild(), userArgument);
		if (member == null) {
			event.reply("I could not find that user :no_entry:").queue();
			return;
		}
		
		if (!event.getMember().canInteract(member)) {
			event.reply("You cannot reset someones warns if they are higher or equal than your top role :no_entry:").queue();
			return;
		}
		
		List<Document> users = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("warn.users")).getEmbedded(List.of("warn", "users"), Collections.emptyList());
		for (Document user : users) {
			if (user.getLong("id") == member.getIdLong()) {
				database.updateGuildById(event.getGuild().getIdLong(), Updates.pull("warn.users", Filters.eq("id", member.getIdLong())), (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						event.reply("**" + member.getUser().getAsTag() + "** has had their warnings reset <:done:403285928233402378>").queue();
					}
				});
				
				return;
			}
		}
		
		event.reply("That user has no warnings :no_entry:").queue();
	}
	
	@Command(value="offences", description="View the offences of a user in the current server")
	@Examples({"offences", "offences @Shea#6653", "offences Shea", "offences 402557516728369153"})
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void offences(CommandEvent event, @Context Database database, @Argument(value="user", endless=true, nullDefault=true) String userArgument) {
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
		
		List<Document> offences = database.getUserById(member.getIdLong(), null, Projections.include("offences")).getList("offences", Document.class, Collections.emptyList());
		for (Document offence : new ArrayList<>(offences)) {
			if (event.getGuild().getIdLong() != offence.getLong("guildId")) {
				offences.remove(offence);
			}
		}
		
		if (offences.isEmpty()) {
			event.reply("That user doesn't have any offences :no_entry:").queue();
			return;
		}
		
		PagedResult<Document> paged = new PagedResult<>(offences)
				.setPerPage(5)
				.setCustomFunction(page -> {
					List<Document> userOffences = page.getArray();
					
					EmbedBuilder embed = new EmbedBuilder();
					embed.setTitle("Page " + page.getCurrentPage() + "/" + page.getMaxPage());
					embed.setAuthor(member.getUser().getAsTag() + " Offences", null, member.getUser().getEffectiveAvatarUrl());
					embed.setFooter("next | previous | go to <page_number> | cancel", null);
					
					for (int i = page.getCurrentPage() * page.getPerPage() - page.getPerPage(); i < (page.getCurrentPage() == page.getMaxPage() ? userOffences.size() : page.getCurrentPage() * page.getPerPage()); i++) {
						Document userOffence = userOffences.get(i);
						Long moderatorId = userOffence.getLong("moderatorId");
						String reason = userOffence.getString("reason");
						String proof = userOffence.getString("proof");
						
						String moderatorString;
						if (moderatorId != null) {
							Member moderator = event.getGuild().getMemberById(moderatorId);
							moderatorString = moderator == null ? "Unknown (" + moderatorId + ")" : moderator.getUser().getAsTag() + " (" + moderator.getUser().getId() + ")";
						} else {
							moderatorString = "Unknown (" + moderatorId + ")";
						}
						
						reason = reason == null ? "None Given" : reason;
						proof = proof == null ? "None Given" : proof;
						LocalDateTime date = LocalDateTime.ofEpochSecond(userOffence.getLong("timestamp"), 0, ZoneOffset.UTC);
						String value = String.format("Action: %s\nReason: %s\nModerator: %s\nProof: %s\nTime: %s", userOffence.getString("action"), reason, moderatorString, proof, date.format(DateTimeFormatter.ofPattern("dd/MM/yy HH:mm")));
								
						embed.addField("Offence #" + (i + 1), value, false);
					}
					
					return embed.build();
				});
		
		PagedUtils.getPagedResult(event, paged, 300, null);
	}
	
	@Command(value="proof", description="Update the proof of a specified users offence") 
	@Examples({"proof @Shea#6653 1 https://i.imgur.com/i87lyNO.png", "proof Shea 4 https://i.imgur.com/i87lyNO.png", "proof 402557516728369153 10 https://i.imgur.com/i87lyNO.png"})
	@AuthorPermissions({Permission.MESSAGE_MANAGE})
	public void proof(CommandEvent event, @Context Database database, @Argument(value="user") String userArgument, @Argument(value="offence number") int offenceNumber, @Argument(value="proof", endless=true) String proof) {
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
		
		
		
		List<Document> offences = database.getUserById(member.getIdLong(), null, Projections.include("offences")).getList("offences", Document.class, Collections.emptyList());
		for (Document offence : new ArrayList<>(offences)) {
			if (event.getGuild().getIdLong() != offence.getLong("guildId")) {
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
		
		Document offence = offences.get(offenceNumber - 1);
		Long moderatorId = offence.getLong("moderatorId");
		
		if (moderatorId != null) {
			Member moderator = event.getGuild().getMemberById(moderatorId);
			if (moderator != null && !moderator.equals(event.getMember())) {
				event.reply("You don't have ownership to that offence :no_entry:").queue();
				return;
			}
		}
		
		database.updateUserById(member.getIdLong(), Updates.set("offences." + (offenceNumber - 1) + ".proof", proof), (result, exception) -> {
			if (exception != null) {
				exception.printStackTrace();
				event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
			} else {
				event.reply("Proof has been updated for offence **#" + offenceNumber + "** <:done:403285928233402378>").queue();
			}
		});
	}
	
	@Initialize(all=true, subCommands=true, recursive=true)
	public void initialize(CommandImpl command) {
		command.setCategory(Categories.MOD);
	}
	
}
