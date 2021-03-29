package com.sx4.bot.handlers;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.Database;
import com.sx4.bot.entities.management.MediaType;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.channel.text.TextChannelDeleteEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import org.bson.Document;

import java.util.EnumSet;

public class MediaModeHandler implements EventListener {

	private final Sx4 bot;

	public MediaModeHandler(Sx4 bot) {
		this.bot = bot;
	}

	public void handle(Message message) {
		if (message.getAuthor().isBot()) { //|| message.getMember().hasPermission(Permission.ADMINISTRATOR)) {
			return;
		}

		TextChannel channel = message.getTextChannel();

		Document media = this.bot.getDatabase().getMediaChannel(Filters.eq("channelId", channel.getIdLong()), Projections.include("types"));
		if (media == null) {
			return;
		}

		EnumSet<MediaType> types = MediaType.getMediaTypes(media.get("types", MediaType.ALL));

		Attachment attachment = message.getAttachments().stream()
			.filter(file -> types.stream().anyMatch(type -> type.getExtension().equalsIgnoreCase(file.getFileExtension())))
			.findFirst()
			.orElse(null);

		if (attachment == null && message.getGuild().getSelfMember().hasPermission(channel, Permission.MESSAGE_MANAGE)) {
			message.delete().queue();
		}
	}

	public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
		this.handle(event.getMessage());
	}

	public void onGuildMessageUpdate(GuildMessageUpdateEvent event) {
		this.handle(event.getMessage());
	}

	public void onTextChannelDelete(TextChannelDeleteEvent event) {
		this.bot.getDatabase().deleteMediaChannel(Filters.eq("channelId", event.getChannel().getIdLong())).whenComplete(Database.exceptionally(this.bot.getShardManager()));
	}

	@Override
	public void onEvent(GenericEvent event) {
		if (event instanceof GuildMessageReceivedEvent) {
			this.onGuildMessageReceived((GuildMessageReceivedEvent) event);
		} else if (event instanceof GuildMessageUpdateEvent) {
			this.onGuildMessageUpdate((GuildMessageUpdateEvent) event);
		}
	}

}
