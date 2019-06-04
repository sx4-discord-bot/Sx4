package com.sx4.utils;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import com.jockie.bot.core.command.impl.CommandEvent;
import com.sx4.core.Sx4Bot;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

public class PagedUtils {
	
	public static class PagedReturn<Type> {
		Type object;
		int page;
		int index;
		int indexOnPage;
		
		public PagedReturn(Type object, int page, int index, int indexOnPage) {
			this.object = object;
			this.page = page;
			this.index = index;
			this.indexOnPage = indexOnPage;
		}
		
		public Type getObject() {
			return this.object;
		}
		
		public int getPage() {
			return this.page;
		}
		
		public int getIndex() {
			return this.index;
		}
		
		public int getIndexOnPage() {
			return this.indexOnPage;
		}
		
	}
	
	public static class PagedResult<Type> {
		private int currentPage = 1;
		private List<Type> array;
		private Function<Type, String> function = (e) -> e.toString();
		private String authorName = null;
		private String authorUrl = null;
		private String authorIconUrl = null;
		private int perPage = 10;
		private boolean indexed = true;
		private boolean increasedIndex = false;
		private boolean autoSelect = false;
		private Color colour = null;
		private boolean deleteMessage = true;
		private boolean selectableByIndex = false;
		private boolean selectableByObject = false;
		private Function<Type, String> selectableObject = (e) -> e.toString();
		private Function<Integer, String> indexString = (e) -> e + ".";
		private boolean custom = false;
		private Function<PagedResult<Type>, MessageEmbed> customFunction = null;
		
		public PagedResult(List<Type> array) {
			this.array = array;
		}
		
		public int getNextPage() {
			int maxPage = this.getMaxPage();
			int nextPage;
			if (this.currentPage == maxPage) {
				nextPage = 1;
			} else {
				nextPage = this.currentPage + 1;
			}
			
			return nextPage;
		}
		
		public int getPreviousPage() {
			int maxPage = this.getMaxPage();
			int previousPage;
			if (this.currentPage == 1) {
				previousPage = maxPage;
			} else {
				previousPage = this.currentPage - 1;
			}
			
			return previousPage;
		}
		
		public int getCurrentPage() {
			return this.currentPage;
		}
		
		public PagedResult<Type> setCustomFunction(Function<PagedResult<Type>, MessageEmbed> function) {
			this.customFunction = function;
			
			return this;
		}
		
		public PagedResult<Type> setCustom(boolean custom) {
			this.custom = custom;
			
			return this;
		}
		
		public boolean isCustom() {
			return this.custom;
		}
		
		public PagedResult<Type> setIndexString(Function<Integer, String> function) {
			this.indexString = function;
			
			return this;
		}
		
		public String getIndexString(Integer index) {
			return this.indexString.apply(index);
		}
		
		public PagedResult<Type> setSelectableObject(Function<Type, String> function) {
			this.selectableObject = function;
			
			return this;
		}
		
		public String getSelectableObject(Type object) {
			return this.selectableObject.apply(object);
		}
		
		public PagedResult<Type> setDeleteMessage(boolean deleteMessage) {
			this.deleteMessage = deleteMessage;
			
			return this;
		}
		
		public boolean deleteMessage() {
			return this.deleteMessage;
		}
		
		public List<Type> getArray() {
			return this.array;
		}
		
		public boolean isIncreasedIndex() {
			return this.increasedIndex;
		}
		
		public PagedResult<Type> setIncreasedIndex(boolean increasedIndex) {
			this.increasedIndex = increasedIndex;
			if (increasedIndex == true) {
				this.indexed = false;
			}
			
			return this;
		}
		
		public PagedResult<Type> setEmbedColour(Color colour) {
			this.colour = colour;
			
			return this;
		}
		
		public PagedResult<Type> setFunction(Function<Type, String> function) {
			this.function = function;
			
			return this;
		}
		
		public PagedResult<Type> setAuthor(String authorName, String authorUrl, String authorIconUrl) {
			this.authorName = authorName;
			this.authorUrl = authorUrl;
			this.authorIconUrl = authorIconUrl;
			
			return this;
		} 
		
		public PagedResult<Type> setSelectableByIndex(boolean selectable) {
			this.selectableByIndex = selectable;
			
			return this;
		}
		
		public PagedResult<Type> setSelectableByObject(boolean selectable) {
			this.selectableByObject = selectable;
			
			return this;
		}
		
		public boolean isSelectableByIndex() {
			return this.selectableByIndex;
		}
		
		public boolean isSelectableByObject() {
			return this.selectableByObject;
		}
		
		public boolean isSelectable() {
			return this.selectableByIndex || this.selectableByObject;
		}
		
		public PagedResult<Type> setIndexed(boolean indexed) {
			this.indexed = indexed;
			if (indexed == true) {
				this.increasedIndex = false;
			}
			
			return this;
		}
		
		public boolean isIndexed() {
			return this.indexed;
		}
		
		public PagedResult<Type> setAutoSelect(boolean autoSelect) {
			this.autoSelect = autoSelect;
			
			return this;
		}
		
		public boolean isAutoSelected() {
			return this.autoSelect;
		}
		
		public PagedResult<Type> setPerPage(int perPage) {
			this.perPage = perPage;
			
			return this;
		}
		
		public int getMaxPage() {
			return (int) Math.ceil(this.array.size()/(double) perPage);
		}
		
		public int getPerPage() {
			return this.perPage;
		}
		
		public int getLastPageEntries() {
			return this.array.size() % this.perPage != 0 ? this.array.size() % this.perPage : this.perPage;
		}
		
		public PagedResult<Type> nextPage() {
			int maxPage = this.getMaxPage();
			if (this.currentPage == maxPage) {
				this.currentPage = 1;
			} else {
				this.currentPage += 1;
			}
			
			return this;
		}
		
		public PagedResult<Type> previousPage() {
			int maxPage = this.getMaxPage();
			if (this.currentPage == 1) {
				this.currentPage = maxPage;
			} else {
				this.currentPage -= 1;
			}
			
			return this;
		}
		
		public PagedResult<Type> setPage(int page) {
			this.currentPage = page;
			
			return this;
		}
		
		public MessageEmbed getEmbed() {
			if (this.custom == false) {
				int maxPage = this.getMaxPage();
				EmbedBuilder embed = new EmbedBuilder();
				if (this.authorName != null || this.authorUrl != null || this.authorIconUrl != null) {
					embed.setAuthor(this.authorName, this.authorUrl, this.authorIconUrl);
				}
				embed.setTitle("Page " + this.currentPage + "/" + maxPage);
				embed.setColor(this.colour);
				embed.setFooter("next | previous | go to <page_number> | cancel", null);
				for (int i = this.currentPage * this.perPage - this.perPage; i < this.currentPage * this.perPage; i++) {
					try {
						embed.appendDescription((increasedIndex == true ? this.getIndexString(i + 1) : (indexed == true ? (this.getIndexString(i + 1 - ((this.currentPage - 1) * this.perPage))) : "")) + " " + this.function.apply(this.array.get(i)) + "\n");
					} catch (IndexOutOfBoundsException e) {
						break;
					}
				}
				
				return embed.build();
			} else {
				return this.customFunction.apply(this);
			}
		}
		
	}
	
	private static List<String> previous = List.of("previous", "prev", "p", "previous page");
	private static List<String> next = List.of("next", "n", "next page");
	private static List<String> cancel = List.of("cancel", "c");
	private static List<String> confirmation = List.of("yes", "y", "accept");
	private static List<String> allAliases = new ArrayList<>();
	static {
		allAliases.addAll(previous);
		allAliases.addAll(next);
		allAliases.addAll(cancel);
	}
	
	public static <T> void getPagedResult(CommandEvent event, PagedResult<T> paged, int timeout, Consumer<PagedReturn<T>> returnFunction) {
		if (paged.isAutoSelected() && paged.isSelectable() && paged.getArray().size() == 1) {
			PagedReturn<T> page = new PagedReturn<>(paged.getArray().get(0), paged.getCurrentPage(), (paged.getPerPage() * paged.getCurrentPage()) - paged.getPerPage(), 1);
			returnFunction.accept(page);
			return;
		}
		
		event.reply(paged.getEmbed()).queue(message -> {
			Predicate<MessageReceivedEvent> check = (e) -> {
				String messageContent = e.getMessage().getContentRaw().toLowerCase();
				if (e.getChannel().equals(event.getChannel()) && e.getAuthor().equals(event.getAuthor())) {
					if (messageContent.startsWith("go to ")) {
						int requestedPage;
						try {
							requestedPage = Integer.parseInt(messageContent.substring(6));
						} catch (Exception ex) {
							return false;
						}
						return requestedPage <= paged.getMaxPage() && requestedPage > 0;
					}
						
					if (paged.isSelectable() == false) {
						return allAliases.contains(messageContent);
					} else {
						if (allAliases.contains(messageContent)) {
							return true;
						}	
						
						if (paged.isSelectableByIndex()) {
							if (messageContent.matches("[0-9]+")) {
								int selectedIndex = Integer.parseInt(e.getMessage().getContentRaw().toLowerCase());
								if (paged.isIncreasedIndex()) {
									return selectedIndex > paged.getCurrentPage() * paged.getPerPage() - paged.getPerPage() && selectedIndex <= (paged.getCurrentPage() != paged.getMaxPage() ? paged.getCurrentPage() * paged.getPerPage() : paged.getCurrentPage() * paged.getPerPage() - (paged.getCurrentPage() - paged.getLastPageEntries()));
								} else {
									return selectedIndex > 0 && selectedIndex <= (paged.getCurrentPage() != paged.getMaxPage() ? paged.getPerPage() : paged.getLastPageEntries());
								}
							}
						}
							
						if (paged.isSelectableByObject()) {
							for (int i = paged.getCurrentPage() * paged.getPerPage() - paged.getPerPage(); i < paged.getCurrentPage() * paged.getPerPage(); i++) {
								try {
									if (paged.getSelectableObject(paged.getArray().get(i)).equals(messageContent)) {
										return true;
									}
								} catch(IndexOutOfBoundsException ex) {
									break;
								}
							}
						}
					}
				}
					
				return false;
			};
			
			Consumer<MessageReceivedEvent> handle = new Consumer<MessageReceivedEvent>() {
				public void accept(MessageReceivedEvent e) {
					String messageContent = e.getMessage().getContentRaw().toLowerCase();
					boolean edit = true;
					if (cancel.contains(messageContent)) {
						message.delete().queue(null, $ -> {});
						e.getMessage().delete().queue(null, $ -> {});
						return;
					} else if (next.contains(messageContent)) {
						if (paged.getNextPage() != paged.getCurrentPage()) {
							paged.nextPage();
						} else {
							edit = false;
						}
						e.getMessage().delete().queue(null, $ -> {});
					} else if (previous.contains(messageContent)) {
						if (paged.getPreviousPage() != paged.getCurrentPage()) {
							paged.previousPage();
						} else {
							edit = false;
						}
						e.getMessage().delete().queue(null, $ -> {});
					} else if (messageContent.startsWith("go to ")) {
						int requestedPage;
						try {
							requestedPage = Integer.parseInt(messageContent.substring(6));
							if (requestedPage != paged.getCurrentPage()) {
								paged.setPage(requestedPage);
							} else {
								edit = false;
							}
						} catch (Exception ex) {
							ex.getMessage();
						}		
						e.getMessage().delete().queue(null, $ -> {});
					} else if (messageContent.matches("[0-9]+")) {
						int selectedIndex = Integer.parseInt(messageContent);
						int index;
						if (paged.isIncreasedIndex()) {
							index = selectedIndex - 1;
						} else {
							index = (paged.getCurrentPage() * paged.getPerPage() - paged.getPerPage()) + (selectedIndex - 1);
						}
						PagedReturn<T> page = new PagedReturn<>(paged.getArray().get(index), paged.getCurrentPage(), index, selectedIndex);
						returnFunction.accept(page);
						message.delete().queue(null, $ -> {});
						e.getMessage().delete().queue(null, $ -> {});
						return;
					} else {
						for (int i = paged.getCurrentPage() * paged.getPerPage() - paged.getPerPage(); i < paged.getCurrentPage() * paged.getPerPage(); i++) {
							if (paged.getSelectableObject(paged.getArray().get(i)).equals(messageContent)) {
								int index = i;
								PagedReturn<T> page = new PagedReturn<>(paged.getArray().get(index), paged.getCurrentPage(), index, index);
								returnFunction.accept(page);
								message.delete().queue(null, $ -> {});
								e.getMessage().delete().queue(null, $ -> {});
								return;
							}
						}
					}
					
					if (edit == true) {
						message.editMessage(paged.getEmbed()).queue();
					}
					
					Sx4Bot.waiter.waitForEvent(MessageReceivedEvent.class, check, this, timeout, TimeUnit.SECONDS, paged.deleteMessage() == true ? () -> message.delete().queue() : null);
				}
			};
			Sx4Bot.waiter.waitForEvent(MessageReceivedEvent.class, check, handle, timeout, TimeUnit.SECONDS, paged.deleteMessage() == true ? () -> message.delete().queue() : null);
		});
	}
	
	public static void getConfirmation(CommandEvent event, int timeout, User responder, Consumer<Boolean> returnFunction) {
		Sx4Bot.waiter.waitForEvent(MessageReceivedEvent.class, e -> {
			return e.getChannel().equals(event.getChannel()) && e.getAuthor().equals(responder);
		}, e -> {
			String messageContent = e.getMessage().getContentRaw().toLowerCase();
			if (confirmation.contains(messageContent)) {
				returnFunction.accept(true);
			} else {
				returnFunction.accept(false);
			}
			
			return;
		}, timeout, TimeUnit.SECONDS, () -> {
			event.reply("Response timed out :stopwatch:").queue();
		});
	}
	
	public static void getResponse(CommandEvent event, int timeout, Predicate<MessageReceivedEvent> check, Runnable onTimeout, Consumer<Message> returnFunction) {
		Sx4Bot.waiter.waitForEvent(MessageReceivedEvent.class, check, e -> returnFunction.accept(e.getMessage()), timeout, TimeUnit.SECONDS, () -> onTimeout.run());
	}
	
}
