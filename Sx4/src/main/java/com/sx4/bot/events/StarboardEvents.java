package com.sx4.bot.events;

import java.util.List;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.sx4.bot.database.Database;
import com.sx4.bot.starboard.Starboard;
import com.sx4.bot.starboard.StarboardConfiguration;
import com.sx4.bot.starboard.StarboardMessage;
import com.sx4.bot.utils.StarboardUtils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.guild.GuildMessageDeleteEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class StarboardEvents extends ListenerAdapter {

	public void onGuildMessageReactionAdd(GuildMessageReactionAddEvent event) {
		if (event.getReactionEmote().isEmoji() && event.getReactionEmote().getEmoji().equals("⭐") && !event.getUser().isBot()) {
			Database database = Database.get();
			
			Document data = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("starboard.messages", "starboard.enabled", "starboard.configuration", "starboard.channelId", "starboard.deleted")).get("starboard", Database.EMPTY_DOCUMENT);
			Starboard starboard = new Starboard(data);
			if (starboard.isEnabled()) {
				TextChannel channel = starboard.getChannel(event.getGuild());
				if (channel == null) {
					return;
				}
				
				if (starboard.isDeletedMessage(event.getMessageIdLong())) {
					return;
				}
				
				StarboardMessage message = starboard.getMessageById(event.getMessageIdLong());
				if (message != null) {
					Long starboardId = message.getStarboardId();
					
					List<Long> stars = message.getStars();
					if (stars.contains(event.getUser().getIdLong()) || message.getAuthorId() == event.getUser().getIdLong()) {
						return;
					} else {
						int newSize = stars.size() + 1;
						
						UpdateOptions updateOptions = new UpdateOptions().arrayFilters(List.of(Filters.eq("message.id", message.getMessageId())));
						
						StarboardConfiguration star = starboard.getConfigurationById(newSize);
						if (star != null) {
							String display = star.getMessage();
							
							if (starboardId == null) {
								Message starboardMessageRaw = StarboardUtils.getStarboard(message, event.getChannel(), event.getUser(), starboard.getConfiguration(), display);
								
								channel.sendMessage(starboardMessageRaw).queue(starboardMessage -> {
									starboardMessage.addReaction("⭐").queue();
									
									Bson update = Updates.combine(
											Updates.addToSet("starboard.messages.$[message].stars", event.getUser().getIdLong()), 
											Updates.set("starboard.messages.$[message].starboardId", starboardMessage.getIdLong())
									);
									
									database.updateGuildById(event.getGuild().getIdLong(), null, update, updateOptions, (result, exception) -> {
										if (exception != null) {
											exception.printStackTrace();
										}
									});
								});
								
								return;
							} 
						}
						
						channel.editMessageById(starboardId, StarboardUtils.getCurrentMessage(event.getUser(), event.getChannel(), event.getMessageIdLong(), starboard.getConfiguration(), newSize)).queue();
						
						Bson update = Updates.addToSet("starboard.messages.$[message].stars", event.getUser().getIdLong());
						
						database.updateGuildById(event.getGuild().getIdLong(), null, update, updateOptions, (result, exception) -> {
							if (exception != null) {
								exception.printStackTrace();
							}
						});
					}
				} else {
					event.getChannel().retrieveMessageById(event.getMessageIdLong()).queue(originalMessage -> {
						if (originalMessage.getAuthor().getIdLong() == event.getUser().getIdLong()) {
							return;
						}
						
						String imageUrl = originalMessage.getAttachments()
								.stream()
								.filter(Attachment::isImage)
								.map(Attachment::getUrl)
								.findFirst()
								.orElse(null);
						
						StarboardConfiguration star = starboard.getConfigurationById(1);
						if (star != null) {
							String display = star.getMessage();
							
							channel.sendMessage(StarboardUtils.getStarboard(originalMessage, event.getUser(), 1, starboard.getConfiguration(), display)).queue(starboardMessage -> {
								starboardMessage.addReaction("⭐").queue();
								
								Document newData = new Document("stars", List.of(event.getUser().getIdLong()))
										.append("starboardId", starboardMessage.getIdLong())
										.append("id", originalMessage.getIdLong())
										.append("channelId", originalMessage.getChannel().getIdLong())
										.append("authorId", originalMessage.getAuthor().getIdLong())
										.append("content", originalMessage.getContentRaw())
										.append("image", imageUrl);
								
								database.updateGuildById(event.getGuild().getIdLong(), Updates.push("starboard.messages", newData), (result, exception) -> {
									if (exception != null) {
										exception.printStackTrace();
									}
								});
							});
						} else {
							Document newData = new Document("stars", List.of(event.getUser().getIdLong()))
									.append("id", event.getMessageIdLong())
									.append("channelId", originalMessage.getChannel().getIdLong())
									.append("authorId", originalMessage.getAuthor().getIdLong())
									.append("content", originalMessage.getContentRaw())
									.append("image", imageUrl);
							
							database.updateGuildById(event.getGuild().getIdLong(), Updates.push("starboard.messages", newData), (result, exception) -> {
								if (exception != null) {
									exception.printStackTrace();
								}
							});
						}
					});
				}
			}
		}
	}
	
	public void onGuildMessageReactionRemove(GuildMessageReactionRemoveEvent event) {
		if (event.getReactionEmote().isEmoji() && event.getReactionEmote().getEmoji().equals("⭐") && !event.getUser().isBot()) {
			Database database = Database.get();
			
			Document data = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("starboard.messages", "starboard.enabled", "starboard.configuration", "starboard.channelId")).get("starboard", Database.EMPTY_DOCUMENT);
			Starboard starboard = new Starboard(data);
			if (starboard.isEnabled()) {
				TextChannel channel = starboard.getChannel(event.getGuild());
				if (channel == null) {
					return;
				}

				StarboardMessage message = starboard.getMessageById(event.getMessageIdLong());
				if (message != null) {
					Long starboardId = message.getStarboardId();
					
					List<Long> stars = message.getStars();
					if (stars.contains(event.getUser().getIdLong())) {
						int newSize = stars.size() - 1;
						
						Bson update;
						List<Bson> arrayFilters = null;
						if (newSize == 0) {
							update = Updates.pull("starboard.messages", Filters.eq("id", message.getMessageId()));
							
							if (starboardId != null) {
								channel.deleteMessageById(starboardId).queue();
							}
						} else {
							update = Updates.pull("starboard.messages.$[message].stars", event.getUser().getIdLong());
							arrayFilters = List.of(Filters.eq("message.id", message.getMessageId()));
							
							if (starboardId != null) {
								channel.editMessageById(starboardId, StarboardUtils.getCurrentMessage(event.getUser(), event.getChannel(), event.getMessageIdLong(), starboard.getConfiguration(), newSize)).queue();
							}
						}
						
						UpdateOptions updateOptions = new UpdateOptions().arrayFilters(arrayFilters);
						database.updateGuildById(event.getGuild().getIdLong(), null, update, updateOptions, (result, exception) -> {
							if (exception != null) {
								exception.printStackTrace();
							}
						});
					}
				}
			}
		}
	}
	
	public void onGuildMessageUpdate(GuildMessageUpdateEvent event) {
		Database database = Database.get();
		
		Document data = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("starboard.messages", "starboard.enabled", "starboard.channelId")).get("starboard", Database.EMPTY_DOCUMENT);
		Starboard starboard = new Starboard(data);
		if (starboard.isEnabled()) {
			TextChannel channel = starboard.getChannel(event.getGuild());
			if (channel == null) {
				return;
			}
			
			String newContent = event.getMessage().getContentRaw();
			
			StarboardMessage message = starboard.getMessageByOriginalId(event.getMessageIdLong());
			if (message != null) {
				Long starboardId = message.getStarboardId();
				if (starboardId != null && !message.getContent().equals(newContent)) {
					channel.retrieveMessageById(starboardId).queue(starboardMessage -> {
						MessageEmbed oldEmbed = starboardMessage.getEmbeds().get(0);
						
						EmbedBuilder embed = new EmbedBuilder();
						embed.setAuthor(oldEmbed.getAuthor().getName(), oldEmbed.getAuthor().getUrl(), oldEmbed.getAuthor().getIconUrl());
						embed.setColor(oldEmbed.getColor());
						embed.addField(oldEmbed.getFields().get(0));
						embed.setImage(oldEmbed.getImage().getUrl());
						
						if (!newContent.isEmpty()) {
							embed.addField("Message", newContent, false);
						}
						
						starboardMessage.editMessage(embed.build()).queue(newMessage -> {
							UpdateOptions updateOptions = new UpdateOptions().arrayFilters(List.of(Filters.eq("message.id", message.getMessageId())));
							database.updateGuildById(event.getGuild().getIdLong(), null, Updates.set("starboard.messages.$[message].content", newContent), updateOptions, (result, exception) -> {
								if (exception != null) {
									exception.printStackTrace();
								}
							});
						});
					});
				}
			}
		}
	}
	
	public void onGuildMessageDelete(GuildMessageDeleteEvent event) {
		Database database = Database.get();
		
		Document data = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("starboard.messages", "starboard.enabled", "starboard.channelId")).get("starboard", Database.EMPTY_DOCUMENT);
		Starboard starboard = new Starboard(data);
		if (starboard.isEnabled()) {
			TextChannel channel = starboard.getChannel(event.getGuild());
			if (channel == null) {
				return;
			}
			
			StarboardMessage message = starboard.getMessageById(event.getMessageIdLong());
			if (message != null) {
				if (message.hasStarboard() && event.getMessageIdLong() != message.getStarboardId()) {
					channel.deleteMessageById(message.getStarboardId()).queue();
				}
				
				database.updateGuildById(event.getGuild().getIdLong(), Updates.pull("starboard.messages", Filters.eq("id", message.getMessageId())), (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
					}
				});
			}
		}
	}
	
}
