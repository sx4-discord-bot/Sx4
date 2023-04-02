package com.sx4.bot.paged;

import com.jockie.bot.core.command.impl.CommandEvent;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.utility.StringUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
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

public class PagedResult<Type> {
	
	public static class PagedSelect<T> {
		
		private final T selected;
		private final int index;
		private final int page;
		
		public PagedSelect(T selected, int index, int page) {
			this.selected = selected;
			this.index = index;
			this.page = page;
		}
		
		public T getSelected() {
			return this.selected;
		}
		
		public int getIndex() {
			return this.index;
		}
		
		public int getPage() {
			return this.page;
		}
		
	}
	
	public enum SelectType {
		INDEX,
		OBJECT
	}

	public static final Button PREVIOUS_BUTTON = Button.secondary("previous", "<");
	public static final Button PREVIOUS_BUTTON_DISABLED = PagedResult.PREVIOUS_BUTTON.asDisabled();

	public static final Button NEXT_BUTTON = Button.secondary("next", ">");
	public static final Button NEXT_BUTTON_DISABLED = PagedResult.NEXT_BUTTON.asDisabled();

	public static final String DEFAULT_FOOTER_TEXT = "next | previous | go to <page_number> | cancel";
	
	private final List<Type> list;
	
	private long messageId = 0L;
	private long channelId = 0L;
	private long guildId = 0L;
	private long ownerId = 0L;
	
	private long timeout = 0;
	
	private int page = 1;
	private int perPage = 10;
	private int colour = Role.DEFAULT_COLOR_RAW;
	
	private boolean pageOverflow = true;
	private boolean indexed = true;
	private boolean increasedIndex = false;
	private boolean embed = true;
	private boolean autoSelect = false;
	
	private String authorName = null;
	private String authorUrl = null;
	private String authorImage = null;
	
	private EnumSet<SelectType> select = EnumSet.allOf(SelectType.class);

	private final Map<Integer, MessageCreateData> pageCache = new HashMap<>();
	private boolean cache = false;

	private BiConsumer<PagedResult<Type>, Consumer<MessageCreateBuilder>> asyncFunction = null;
	
	private Function<PagedResult<Type>, MessageCreateBuilder> customFunction = null;
	private Function<Type, String> displayFunction = Object::toString;
	private Function<Integer, String> indexFunction = a -> a + ". ";
	private Function<Type, String> selectFunction;
	private BiPredicate<String, Type> selectablePredicate = null;
	
	private Consumer<PagedSelect<Type>> onSelect = null;
	private Runnable onTimeout = null;

	private final Sx4 bot;

	public PagedResult(Sx4 bot, List<Type> list) {
		this.bot = bot;
		this.list = list;
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

	public PagedResult<Type> cachePages(boolean cache) {
		this.cache = cache;

		return this;
	}
	
	public long getTimeout() {
		return this.timeout;
	}
	
	public PagedResult<Type> setTimeout(long duration, TimeUnit timeUnit) {
		this.timeout = timeUnit.toSeconds(duration);
		
		return this;
	}
	
	public PagedResult<Type> setTimeout(long seconds) {
		return this.setTimeout(seconds, TimeUnit.SECONDS);
	}
	
	public void timeout() {
		if (this.onTimeout != null) {
			this.onTimeout.run();
		}
		
		this.delete();
	}
	
	public void onTimeout(Runnable timeout) {
		this.onTimeout = timeout;
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
	
	public PagedResult<Type> setPage(int page) {
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
	
	public PagedResult<Type> nextPage() {
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
	
	public PagedResult<Type> previousPage() {
		this.page = this.getPreviousPage();
		
		return this;
	}
	
	public int getPerPage() {
		return this.perPage;
	}
	
	public PagedResult<Type> setPerPage(int perPage) {
		this.perPage = perPage;
		
		return this;
	}
	
	public PagedResult<Type> setPageOverflow(boolean pageOverflow) {
		this.pageOverflow = pageOverflow;
		
		return this;
	}

	public boolean isPageOverflow() {
		return this.pageOverflow;
	}
	
	public boolean isIndexed() {
		return this.indexed;
	}
	
	public PagedResult<Type> setIndexed(boolean indexed) {
		this.indexed = indexed;
		
		return this;
	}
	
	public boolean isIncreasedIndex() {
		return this.increasedIndex;
	}
	
	public PagedResult<Type> setIncreasedIndex(boolean increasedIndex) {
		this.increasedIndex = increasedIndex;
		
		return this;
	}
	
	public boolean isEmbed() {
		return this.embed;
	}
	
	public PagedResult<Type> setEmbed(boolean embed) {
		this.embed = embed;
		
		return this;
	}
	
	public boolean isAutoSelect() {
		return this.autoSelect;
	}
	
	public PagedResult<Type> setAutoSelect(boolean autoSelect) {
		this.autoSelect = autoSelect;
		
		return this;
	}
	
	public int getColour() {
		return this.colour;
	}
	
	public PagedResult<Type> setColour(int colour) {
		this.colour = colour;
		
		return this;
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
	
	public PagedResult<Type> setAuthor(String name, String url, String image) {
		this.authorName = name;
		this.authorUrl = url;
		this.authorImage = image;
		
		return this;
	}
	
	public EnumSet<SelectType> getSelect() {
		return this.select;
	}
	
	public PagedResult<Type> setSelect(SelectType... select) {
		this.select = select.length == 0 ? EnumSet.noneOf(SelectType.class) : EnumSet.copyOf(Arrays.asList(select));

		return this;
	}
	
	public Function<PagedResult<Type>, MessageCreateBuilder> getCustomFunction() {
		return this.customFunction;
	}

	public PagedResult<Type> setAsyncFunction(BiConsumer<PagedResult<Type>, Consumer<MessageCreateBuilder>> asyncFunction) {
		this.asyncFunction = asyncFunction;

		return this;
	}
	
	public PagedResult<Type> setCustomFunction(Function<PagedResult<Type>, MessageCreateBuilder> customFunction) {
		this.customFunction = customFunction;
		
		return this;
	}

	public Function<Type, String> getSelectFunction() {
		return this.selectFunction == null ? this.displayFunction : this.selectFunction;
	}

	public PagedResult<Type> setSelectFunction(Function<Type, String> selectFunction) {
		this.selectFunction = selectFunction;

		return this;
	}
	
	public Function<Type, String> getDisplayFunction() {
		return this.displayFunction;
	}
	
	public PagedResult<Type> setDisplayFunction(Function<Type, String> displayFunction) {
		this.displayFunction = displayFunction;
		
		return this;
	}
	
	public Function<Integer, String> getIndexFunction() {
		return this.indexFunction;
	}
	
	public PagedResult<Type> setIndexFunction(Function<Integer, String> indexFunction) {
		this.indexFunction = indexFunction;
		
		return this;
	}
	
	public BiPredicate<String, Type> getSelectablePredicate() {
		return this.selectablePredicate;
	}
	
	public PagedResult<Type> setSelectablePredicate(BiPredicate<String, Type> selectablePredicate) {
		this.selectablePredicate = selectablePredicate;
		
		return this;
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
	
	public void delete() {
		MessageChannel channel = this.getChannel();
		if (channel != null && this.messageId != 0) {
			channel.deleteMessageById(this.messageId).queue(null, ErrorResponseException.ignore(ErrorResponse.UNKNOWN_MESSAGE));
		}

		this.bot.getPagedManager().deletePagedResult(this);
	}
	
	public void select(int index) {
		if (this.onSelect != null) {
			this.onSelect.accept(new PagedSelect<>(this.list.get(index), index, this.page));
		}
		
		this.delete();
	}
	
	public void onSelect(Consumer<PagedSelect<Type>> select) {
		this.onSelect = select;
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
				string.append("Page **").append(this.page).append("/").append(maxPage).append("**\n\n");

				this.forEach((object, index) -> {
					string.append(this.increasedIndex ? this.indexFunction.apply(index + 1) : (this.indexed ? (this.indexFunction.apply(index + 1 - ((this.page - 1) * this.perPage))) : "")).append(this.displayFunction.apply(object)).append("\n");
				});

				string.append("\n" + PagedResult.DEFAULT_FOOTER_TEXT);

				message = builder.setContent(string.toString());
			}
		} else {
			message = this.customFunction.apply(this);
		}

		consumer.accept(this.applyComponents(message));
	}

	private void cacheMessage(MessageCreateData message) {
		if (this.cache) {
			this.pageCache.put(this.page, message);
		}
	}
	
	public void ensure(MessageChannel channel) {
		this.getPagedMessage(message -> {
			this.cacheMessage(message);

			channel.editMessageById(this.messageId, MessageEditData.fromCreateData(message)).queue(null, ErrorResponseException.ignore(ErrorResponse.UNKNOWN_MESSAGE));

			this.bot.getPagedManager().setTimeout(this);
		});
	}

	public void ensure(ButtonInteractionEvent event) {
		this.getPagedMessage(message -> {
			this.cacheMessage(message);

			event.editMessage(MessageEditData.fromCreateData(message)).queue(null, ErrorResponseException.ignore(ErrorResponse.UNKNOWN_MESSAGE));

			this.bot.getPagedManager().setTimeout(this);
		});
	}

	public void execute(CommandEvent event) {
		this.execute(event.getChannel(), event.getAuthor());
	}
	
	public void execute(MessageChannel channel, User owner) {
		this.channelId = channel.getIdLong();
		this.ownerId = owner.getIdLong();
		
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
			channel.sendMessage(readMessage).queue(message -> {
				this.cacheMessage(MessageCreateData.fromMessage(message));
				this.messageId = message.getIdLong();

				this.bot.getPagedManager().createPagedResult(this);
				this.bot.getPagedManager().setTimeout(this);
			});
		});
	}
	
}
