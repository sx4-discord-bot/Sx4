package com.sx4.bot.paged;

import com.jockie.bot.core.command.impl.CommandEvent;
import com.sx4.bot.core.Sx4;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.Route;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.dv8tion.jda.internal.requests.RestActionImpl;

import java.util.EnumSet;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

public class MessagePagedResult<Type> extends PagedResult<Type, Message> {

	public MessagePagedResult(Sx4 bot, List<Type> list, long timeout, int page, int perPage, int colour, boolean pageOverflow,
							  boolean indexed, boolean increasedIndex, boolean embed, boolean autoSelect, String authorName,
							  String authorUrl, String authorImage, EnumSet<SelectType> select, boolean cache,
							  BiConsumer<PagedResult<Type, Message>, Consumer<MessageCreateBuilder>> asyncFunction,
							  Function<PagedResult<Type, Message>, MessageCreateBuilder> customFunction, Function<Type, String> displayFunction,
							  Function<Integer, String> indexFunction, Function<Type, String> selectFunction, BiPredicate<String, Type> selectablePredicate,
							  Consumer<PagedSelectEvent<Type>> onSelect, Runnable onTimeout) {
		super(
			bot, list, timeout, page, perPage, colour, pageOverflow, indexed, increasedIndex, embed, autoSelect, authorName, authorUrl,
			authorImage, select, cache, asyncFunction, customFunction, displayFunction, indexFunction, selectFunction, selectablePredicate,
			onSelect, onTimeout
		);
	}

	public void update() {
		this.getPagedMessage(message -> {
			this.cacheMessage(message);

			this.getChannel().editMessageById(this.messageId, MessageEditData.fromCreateData(message)).queue(null, ErrorResponseException.ignore(ErrorResponse.UNKNOWN_MESSAGE));

			this.bot.getPagedManager().setTimeout(this);
		});
	}

	public void delete() {
		Route.CompiledRoute route = Route.Messages.DELETE_MESSAGE.compile(Long.toString(this.channelId), Long.toString(this.messageId));
		new RestActionImpl<>(this.bot.getShardManager().getShardById(0), route).queue(null, ErrorResponseException.ignore(ErrorResponse.UNKNOWN_MESSAGE));

		this.bot.getPagedManager().deletePagedResult(this);
	}

	public void execute(CommandEvent event) {
		this.execute(event.getMessage());
	}

	public void execute(Message message) {
		MessageChannel channel = message.getChannel();

		this.channelId = channel.getIdLong();
		this.ownerId = message.getAuthor().getIdLong();

		if (channel instanceof GuildChannel guildChannel) {
			Guild guild = guildChannel.getGuild();

			this.guildId = guild.getIdLong();
			this.embed = this.embed && guild.getSelfMember().hasPermission(guildChannel, Permission.MESSAGE_EMBED_LINKS);
		}

		if (this.autoSelect && this.list.size() == 1) {
			this.select(0);

			return;
		}

		this.getPagedMessage(readMessage -> {
			channel.sendMessage(readMessage).queue(m -> {
				this.cacheMessage(readMessage);
				this.messageId = m.getIdLong();

				this.bot.getPagedManager().createPagedResult(this);
				this.bot.getPagedManager().setTimeout(this);
			});
		});
	}

	public static class Builder<T> extends PagedResult.Builder<T, Message, Builder<T>, MessagePagedResult<T>> {

		public Builder(Sx4 bot, List<T> list) {
			super(bot, list);
		}

		public MessagePagedResult<T> build() {
			return new MessagePagedResult<>(this.bot, this.list, this.timeout, this.page, this.perPage, this.colour, this.pageOverflow,
				this.indexed, this.increasedIndex, this.embed, this.autoSelect, this.authorName, this.authorUrl,
				this.authorImage, this.select, this.cache, this.asyncFunction, this.customFunction, this.displayFunction,
				this.indexFunction, this.selectFunction, this.selectablePredicate, this.onSelect, this.onTimeout
			);
		}

	}

}
