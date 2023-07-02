package com.sx4.bot.paged;

import com.sx4.bot.core.Sx4;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

import java.util.EnumSet;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

public class InteractionPagedResult<Type> extends PagedResult<Type, GenericComponentInteractionCreateEvent> {

	private InteractionHook hook;
	private final boolean ephemeral;

	public InteractionPagedResult(Sx4 bot, List<Type> list, long timeout, int page, int perPage, int colour, boolean pageOverflow,
								  boolean indexed, boolean increasedIndex, boolean embed, boolean autoSelect, String authorName,
								  String authorUrl, String authorImage, EnumSet<SelectType> select, boolean cache,
								  BiConsumer<PagedResult<Type, GenericComponentInteractionCreateEvent>, Consumer<MessageCreateBuilder>> asyncFunction,
								  Function<PagedResult<Type, GenericComponentInteractionCreateEvent>, MessageCreateBuilder> customFunction,
								  Function<Type, String> displayFunction, Function<Integer, String> indexFunction, Function<Type, String> selectFunction,
								  BiPredicate<String, Type> selectablePredicate, Consumer<PagedSelectEvent<Type>> onSelect,
								  Runnable onTimeout, boolean ephemeral) {
		super(
			bot, list, timeout, page, perPage, colour, pageOverflow, indexed, increasedIndex, embed, autoSelect, authorName, authorUrl,
			authorImage, select, cache, asyncFunction, customFunction, displayFunction, indexFunction, selectFunction, selectablePredicate,
			onSelect, onTimeout
		);

		this.ephemeral = ephemeral;
	}

	public boolean isEphemeral() {
		return this.ephemeral;
	}

	public void update() {
		this.getPagedMessage(message -> {
			this.cacheMessage(message);

			this.hook.editOriginal(MessageEditData.fromCreateData(message)).queue(null, ErrorResponseException.ignore(ErrorResponse.UNKNOWN_MESSAGE));

			this.bot.getPagedManager().setTimeout(this);
		});
	}

	public void delete() {
		this.hook.deleteOriginal().queue();

		this.bot.getPagedManager().deletePagedResult(this);
	}

	public void execute(GenericComponentInteractionCreateEvent event) {
		MessageChannel channel = event.getChannel();

		this.channelId = channel.getIdLong();
		this.ownerId = event.getUser().getIdLong();

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
			event.reply(readMessage).setEphemeral(this.ephemeral)
				.flatMap(hook -> (this.hook = hook).retrieveOriginal())
				.queue(message -> {
					this.cacheMessage(readMessage);
					this.messageId = message.getIdLong();

					this.bot.getPagedManager().createPagedResult(this);
					this.bot.getPagedManager().setTimeout(this);
				});
		});
	}

	public static class Builder<T> extends PagedResult.Builder<T, GenericComponentInteractionCreateEvent, Builder<T>, InteractionPagedResult<T>> {

		private boolean ephemeral;

		public Builder(Sx4 bot, List<T> list) {
			super(bot, list);
		}

		public Builder<T> setEphemeral(boolean ephemeral) {
			this.ephemeral = ephemeral;

			return this;
		}

		public InteractionPagedResult<T> build() {
			return new InteractionPagedResult<>(this.bot, this.list, this.timeout, this.page, this.perPage, this.colour, this.pageOverflow,
				this.indexed, this.increasedIndex, this.embed, this.autoSelect, this.authorName, this.authorUrl,
				this.authorImage, this.select, this.cache, this.asyncFunction, this.customFunction, this.displayFunction,
				this.indexFunction, this.selectFunction, this.selectablePredicate, this.onSelect, this.onTimeout, this.ephemeral
			);
		}

	}

}
