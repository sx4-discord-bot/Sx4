package com.sx4.bot.paged;

import com.sx4.bot.core.Sx4;
import com.sx4.bot.utility.StringUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class PagedResult<Type, Context> {

	public enum SelectType {
		INDEX,
		OBJECT
	}

	public static final Button PREVIOUS_BUTTON = Button.secondary("previous", "<");
	public static final Button PREVIOUS_BUTTON_DISABLED = PagedResult.PREVIOUS_BUTTON.asDisabled();

	public static final Button NEXT_BUTTON = Button.secondary("next", ">");
	public static final Button NEXT_BUTTON_DISABLED = PagedResult.NEXT_BUTTON.asDisabled();

	public static final String DEFAULT_FOOTER_TEXT = "next | previous | go to <page_number> | cancel";

	protected final List<Type> list;

	protected long messageId = 0L;
	protected long channelId = 0L;
	protected long guildId = 0L;
	protected long ownerId = 0L;

	protected final long timeout;

	protected int page;
	protected final int perPage;
	protected final int colour;

	protected final boolean pageOverflow;
	protected final boolean indexed;
	protected final boolean increasedIndex;
	protected boolean embed;
	protected final boolean autoSelect;

	protected final String authorName;
	protected final String authorUrl;
	protected final String authorImage;

	protected final EnumSet<SelectType> select;

	protected final Map<Integer, MessageCreateData> pageCache = new HashMap<>();
	protected final boolean cache;

	protected final BiConsumer<PagedResult<Type, Context>, Consumer<MessageCreateBuilder>> asyncFunction;

	protected final Function<PagedResult<Type, Context>, MessageCreateBuilder> customFunction;
	protected final Function<Type, String> displayFunction;
	protected final Function<Integer, String> indexFunction;
	protected final Function<Type, String> selectFunction;
	protected final BiPredicate<String, Type> selectablePredicate;

	protected Consumer<PagedSelectEvent<Type>> onSelect;
	protected Runnable onTimeout;

	protected final Sx4 bot;

	public PagedResult(Sx4 bot, List<Type> list, long timeout, int page, int perPage, int colour, boolean pageOverflow,
					   boolean indexed, boolean increasedIndex, boolean embed, boolean autoSelect, String authorName,
					   String authorUrl, String authorImage, EnumSet<SelectType> select, boolean cache,
					   BiConsumer<PagedResult<Type, Context>, Consumer<MessageCreateBuilder>> asyncFunction,
					   Function<PagedResult<Type, Context>, MessageCreateBuilder> customFunction, Function<Type, String> displayFunction,
					   Function<Integer, String> indexFunction, Function<Type, String> selectFunction, BiPredicate<String, Type> selectablePredicate,
					   Consumer<PagedSelectEvent<Type>> onSelect, Runnable onTimeout) {
		this.bot = bot;
		this.list = list;
		this.timeout = timeout;
		this.page = page;
		this.perPage = perPage;
		this.colour = colour;
		this.pageOverflow = pageOverflow;
		this.indexed = indexed;
		this.increasedIndex = increasedIndex;
		this.embed = embed;
		this.autoSelect = autoSelect;
		this.authorName = authorName;
		this.authorUrl = authorUrl;
		this.authorImage = authorImage;
		this.select = select;
		this.cache = cache;
		this.asyncFunction = asyncFunction;
		this.customFunction = customFunction;
		this.displayFunction = displayFunction;
		this.indexFunction = indexFunction;
		this.selectFunction = selectFunction;
		this.selectablePredicate = selectablePredicate;
		this.onSelect = onSelect;
		this.onTimeout = onTimeout;
	}

	public Sx4 getBot() {
		return this.bot;
	}

	public long getMessageId() {
		return this.messageId;
	}

	public long getGuildId() {
		return this.guildId;
	}

	public Guild getGuild() {
		return this.guildId == 0 ? null : this.bot.getShardManager().getGuildById(this.guildId);
	}

	public long getChannelId() {
		return this.channelId;
	}

	public MessageChannel getChannel() {
		if (this.guildId == 0 && this.channelId != 0) {
			return this.bot.getShardManager().getPrivateChannelById(this.channelId);
		} else {
			Guild guild = this.getGuild();

			return guild == null || this.channelId == 0 ? null : guild.getChannelById(GuildMessageChannel.class, this.channelId);
		}
	}

	public long getOwnerId() {
		return this.ownerId;
	}

	public User getOwner() {
		return this.ownerId == 0 ? null : this.bot.getShardManager().getUserById(this.ownerId);
	}

	public List<Type> getList() {
		return this.list;
	}

	public long getTimeout() {
		return this.timeout;
	}

	public void timeout() {
		if (this.onTimeout != null) {
			this.onTimeout.run();
		}

		this.delete();
	}

	public int getMaxPage() {
		return (int) Math.ceil((double) this.list.size() / this.perPage);
	}

	public int getMaxPageEntries() {
		return this.list.size() % this.perPage != 0 ? this.list.size() % this.perPage : this.perPage;
	}

	public int getLowerPageBound() {
		return this.increasedIndex ? (this.page - 1) * this.perPage : 0;
	}

	public int getHigherPageBound() {
		int maxPage = this.getMaxPage();
		return this.increasedIndex ? this.page == maxPage ? this.list.size() : this.page * this.perPage : this.page == maxPage ? this.getMaxPageEntries() : this.perPage;
	}

	public int getPage() {
		return this.page;
	}

	public PagedResult<Type, Context> setPage(int page) {
		int maxPage = this.getMaxPage();
		if (page < 1 || page > maxPage) {
			throw new IllegalArgumentException("Page cannot be more than " + maxPage + " or less than 1");
		}

		this.page = page;

		return this;
	}

	public int getNextPage() {
		int maxPage = this.getMaxPage();
		if (this.pageOverflow && this.page == maxPage) {
			return 1;
		} else if (this.page != maxPage) {
			return this.page + 1;
		}

		return this.page;
	}

	public PagedResult<Type, Context> nextPage() {
		this.page = this.getNextPage();

		return this;
	}

	public int getPreviousPage() {
		if (this.pageOverflow && this.page == 1) {
			return this.getMaxPage();
		} else if (this.page != 1) {
			return this.page - 1;
		}

		return this.page;
	}

	public PagedResult<Type, Context> previousPage() {
		this.page = this.getPreviousPage();

		return this;
	}

	public int getPerPage() {
		return this.perPage;
	}

	public boolean isPageOverflow() {
		return this.pageOverflow;
	}

	public boolean isIndexed() {
		return this.indexed;
	}

	public boolean isIncreasedIndex() {
		return this.increasedIndex;
	}

	public boolean isEmbed() {
		return this.embed;
	}

	public boolean isAutoSelect() {
		return this.autoSelect;
	}

	public int getColour() {
		return this.colour;
	}

	public String getAuthorName() {
		return this.authorName;
	}

	public String getAuthorUrl() {
		return this.authorUrl;
	}

	public String getAuthorImage() {
		return this.authorImage;
	}

	public EnumSet<SelectType> getSelect() {
		return this.select;
	}

	public Function<PagedResult<Type, Context>, MessageCreateBuilder> getCustomFunction() {
		return this.customFunction;
	}

	public Function<Type, String> getSelectFunction() {
		return this.selectFunction == null ? this.displayFunction : this.selectFunction;
	}

	public Function<Type, String> getDisplayFunction() {
		return this.displayFunction;
	}

	public Function<Integer, String> getIndexFunction() {
		return this.indexFunction;
	}

	public BiPredicate<String, Type> getSelectablePredicate() {
		return this.selectablePredicate;
	}

	public boolean runSelectablePredicate(String content, int index) {
		Type object = this.list.get(index);
		Function<Type, String> selectFunction;
		if (this.selectablePredicate != null) {
			return this.selectablePredicate.test(content, object);
		} else if ((selectFunction = this.getSelectFunction()) != null) {
			return selectFunction.apply(object).equals(content);
		} else {
			return object.toString().equals(content);
		}
	}

	public void onTimeout(Runnable onTimeout) {
		this.onTimeout = onTimeout;
	}

	public void onSelect(Consumer<PagedSelectEvent<Type>> onSelect) {
		this.onSelect = onSelect;
	}

	public abstract void delete();

	public void select(int index) {
		if (this.onSelect != null) {
			this.onSelect.accept(new PagedSelectEvent<>(this.list.get(index), index, this.page));
		}

		this.delete();
	}

	public List<Type> getPageEntries() {
		return this.list.subList((this.page - 1) * this.perPage, this.page == this.getMaxPage() ? this.list.size() : this.page * this.perPage);
	}

	public void forEach(BiConsumer<Type, Integer> consumer) {
		if (this.list.isEmpty()) {
			return;
		}

		for (int i = (this.page - 1) * this.perPage; i < (this.page == this.getMaxPage() ? this.list.size() : this.page * this.perPage); i++) {
			consumer.accept(this.list.get(i), i);
		}
	}

	private MessageCreateData applyComponents(MessageCreateBuilder message) {
		List<ActionRow> rows = new ArrayList<>();

		int size = this.list.size();
		if (size > this.perPage) {
			ActionRow row;
			if (!this.pageOverflow && this.page == this.getMaxPage()) {
				row = ActionRow.of(PagedResult.PREVIOUS_BUTTON, PagedResult.NEXT_BUTTON_DISABLED);
			} else if (!this.pageOverflow && this.page == 1) {
				row = ActionRow.of(PagedResult.PREVIOUS_BUTTON_DISABLED, PagedResult.NEXT_BUTTON);
			} else {
				row = ActionRow.of(PagedResult.PREVIOUS_BUTTON, PagedResult.NEXT_BUTTON);
			}

			rows.add(row);
		}

		if (size > 0 && this.select.contains(SelectType.OBJECT) && this.perPage <= 25) {
			StringSelectMenu.Builder menu = StringSelectMenu.create("paged-select").setMaxValues(1);

			this.forEach((object, index) -> menu.addOption(StringUtility.limit(this.getSelectFunction().apply(object), 100, "..."), Integer.toString(index)));

			rows.add(ActionRow.of(menu.build()));
		}

		return message.setComponents(rows).build();
	}

	public void getPagedMessage(Consumer<MessageCreateData> consumer) {
		MessageCreateData cachedMessage = this.pageCache.get(this.page);
		if (cachedMessage != null) {
			consumer.accept(cachedMessage);
			return;
		}

		if (this.asyncFunction != null) {
			this.asyncFunction.accept(this, message -> consumer.accept(this.applyComponents(message)));
			return;
		}

		MessageCreateBuilder message;
		if (this.customFunction == null) {
			MessageCreateBuilder builder = new MessageCreateBuilder();

			int maxPage = this.getMaxPage();
			if (this.embed) {
				EmbedBuilder embed = new EmbedBuilder();
				embed.setColor(this.colour);
				embed.setAuthor(this.authorName, this.authorUrl, this.authorImage);
				embed.setTitle("Page " + this.page + "/" + maxPage);
				embed.setFooter(PagedResult.DEFAULT_FOOTER_TEXT, null);

				this.forEach((object, index) -> {
					embed.appendDescription((this.increasedIndex ? this.indexFunction.apply(index + 1) : (this.indexed ? (this.indexFunction.apply(index + 1 - ((this.page - 1) * this.perPage))) : "")) + this.displayFunction.apply(object) + "\n");
				});

				message = builder.setEmbeds(embed.build());
			} else {
				StringBuilder string = new StringBuilder();
				string.append("## Page ").append(this.page).append("/").append(maxPage).append("\n\n");

				this.forEach((object, index) -> {
					string.append(this.increasedIndex ? this.indexFunction.apply(index + 1) : (this.indexed ? (this.indexFunction.apply(index + 1 - ((this.page - 1) * this.perPage))) : "")).append(this.displayFunction.apply(object)).append("\n");
				});

				message = builder.setContent(string.toString());
			}
		} else {
			message = this.customFunction.apply(this);
		}

		consumer.accept(this.applyComponents(message));
	}

	protected void cacheMessage(MessageCreateData message) {
		if (this.cache) {
			this.pageCache.put(this.page, message);
		}
	}

	public abstract void update();

	public void update(GenericComponentInteractionCreateEvent event) {
		this.getPagedMessage(message -> {
			this.cacheMessage(message);

			event.editMessage(MessageEditData.fromCreateData(message)).queue(null, ErrorResponseException.ignore(ErrorResponse.UNKNOWN_MESSAGE));

			this.bot.getPagedManager().setTimeout(this);
		});
	}

	public abstract void execute(Context context);

	@SuppressWarnings({"unchecked"})
	public static abstract class Builder<T, C, B extends Builder<T, C, B, Class>, Class extends PagedResult<T, C>> {

		protected final Sx4 bot;

		protected final List<T> list;

		protected long timeout = 0;

		protected int page = 1;
		protected int perPage = 10;
		protected int colour = Role.DEFAULT_COLOR_RAW;

		protected boolean pageOverflow = true;
		protected boolean indexed = true;
		protected boolean increasedIndex = false;
		protected boolean embed = true;
		protected boolean autoSelect = false;

		protected String authorName = null;
		protected String authorUrl = null;
		protected String authorImage = null;

		protected EnumSet<SelectType> select = EnumSet.allOf(SelectType.class);

		protected boolean cache = false;

		protected BiConsumer<PagedResult<T, C>, Consumer<MessageCreateBuilder>> asyncFunction = null;

		protected Function<PagedResult<T, C>, MessageCreateBuilder> customFunction = null;
		protected Function<T, String> displayFunction = Object::toString;
		protected Function<Integer, String> indexFunction = a -> a + ". ";
		protected Function<T, String> selectFunction;
		protected BiPredicate<String, T> selectablePredicate = null;

		protected Consumer<PagedSelectEvent<T>> onSelect = null;
		protected Runnable onTimeout = null;

		public Builder(Sx4 bot, List<T> list) {
			this.bot = bot;
			this.list = list;
		}

		public B cachePages(boolean cache) {
			this.cache = cache;

			return (B) this;
		}

		public B setTimeout(long duration, TimeUnit timeUnit) {
			this.timeout = timeUnit.toSeconds(duration);

			return (B) this;
		}

		public B setTimeout(long seconds) {
			return this.setTimeout(seconds, TimeUnit.SECONDS);
		}

		public B setPage(int page) {
			this.page = page;

			return (B) this;
		}

		public B setPerPage(int perPage) {
			this.perPage = perPage;

			return (B) this;
		}

		public B setPageOverflow(boolean pageOverflow) {
			this.pageOverflow = pageOverflow;

			return (B) this;
		}

		public B setIndexed(boolean indexed) {
			this.indexed = indexed;

			return (B) this;
		}

		public B setIncreasedIndex(boolean increasedIndex) {
			this.increasedIndex = increasedIndex;

			return (B) this;
		}

		public B setEmbed(boolean embed) {
			this.embed = embed;

			return (B) this;
		}

		public B setAutoSelect(boolean autoSelect) {
			this.autoSelect = autoSelect;

			return (B) this;
		}

		public B setColour(int colour) {
			this.colour = colour;

			return (B) this;
		}

		public B setAuthor(String name, String url, String image) {
			this.authorName = name;
			this.authorUrl = url;
			this.authorImage = image;

			return (B) this;
		}

		public B setSelect(EnumSet<SelectType> select) {
			this.select = select;

			return (B) this;
		}

		public B setSelect(SelectType... select) {
			return this.setSelect(select.length == 0 ? EnumSet.noneOf(SelectType.class) : EnumSet.copyOf(Arrays.asList(select)));
		}

		public B setAsyncFunction(BiConsumer<PagedResult<T, C>, Consumer<MessageCreateBuilder>> asyncFunction) {
			this.asyncFunction = asyncFunction;

			return (B) this;
		}

		public B setCustomFunction(Function<PagedResult<T, C>, MessageCreateBuilder> customFunction) {
			this.customFunction = customFunction;

			return (B) this;
		}

		public B setSelectFunction(Function<T, String> selectFunction) {
			this.selectFunction = selectFunction;

			return (B) this;
		}

		public B setDisplayFunction(Function<T, String> displayFunction) {
			this.displayFunction = displayFunction;

			return (B) this;
		}

		public B setIndexFunction(Function<Integer, String> indexFunction) {
			this.indexFunction = indexFunction;

			return (B) this;
		}

		public B setSelectablePredicate(BiPredicate<String, T> selectablePredicate) {
			this.selectablePredicate = selectablePredicate;

			return (B) this;
		}

		public abstract Class build();

	}

}

