package com.sx4.bot.paged;

import com.sx4.bot.core.Sx4;
import com.sx4.bot.paged.PagedResult.SelectType;
import com.sx4.bot.utility.NumberUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.requests.ErrorResponse;

import java.util.EnumSet;
import java.util.Set;

public class PagedHandler implements EventListener {

	private final Sx4 bot;

	public PagedHandler(Sx4 bot) {
		this.bot = bot;
	}
	
	private final Set<String> next = Set.of("n", "next", "next page");
	private final Set<String> previous = Set.of("p", "previous", "previous page");
	private final Set<String> skip = Set.of("go to", "go to page", "skip to", "skip to page");
	private final Set<String> cancel = Set.of("c", "cancel");
	
	public void attemptDelete(Message message) {
		if (message.isFromGuild() && message.getGuild().getSelfMember().hasPermission(message.getTextChannel(), Permission.MESSAGE_MANAGE)) {
			message.delete().queue(null, ErrorResponseException.ignore(ErrorResponse.UNKNOWN_MESSAGE));
		}
	}

	public void handleButton(ButtonClickEvent event) {
		if (event.isAcknowledged()) {
			return;
		}

		MessageChannel channel = event.getChannel();
		User author = event.getUser();

		PagedResult<?> pagedResult = this.bot.getPagedManager().getPagedResult(channel.getIdLong(), author.getIdLong());
		if (pagedResult == null) {
			if (this.bot.getPagedManager().isPagedResult(event.getMessageIdLong())) {
				event.reply("This is not your paged result " + this.bot.getConfig().getFailureEmote()).setEphemeral(true).queue();
			}

			return;
		}

		if (pagedResult.getMessageId() != event.getMessageIdLong()) {
			return;
		}

		Button button = event.getButton();
		if (button == null || button.isDisabled()) {
			return;
		}

		if (button.getId().equals("next")) {
			pagedResult.nextPage().ensure(event);
		} else if (button.getId().equals("previous")) {
			pagedResult.previousPage().ensure(event);
		}
	}

	public void handleMessage(Message message) {
		MessageChannel channel = message.getChannel();
		User author = message.getAuthor();

		PagedResult<?> pagedResult = this.bot.getPagedManager().getPagedResult(channel.getIdLong(), author.getIdLong());
		if (pagedResult == null) {
			return;
		}

		if (author.getIdLong() != pagedResult.getOwnerId()) {
			return;
		}
		
		String content = message.getContentRaw();
		String contentLower = content.toLowerCase();
		
		EnumSet<SelectType> selectTypes = pagedResult.getSelect();
		
		if (this.next.contains(contentLower)) {
			if (pagedResult.getNextPage() != pagedResult.getPage()) {
				pagedResult.nextPage().ensure(channel);
				this.attemptDelete(message);
			}
		} else if (this.previous.contains(contentLower)) {
			if (pagedResult.getPreviousPage() != pagedResult.getPage()) {
				pagedResult.previousPage().ensure(channel);
				this.attemptDelete(message);
			}
		} else if (this.cancel.contains(contentLower)) {
			this.attemptDelete(message);
			pagedResult.delete();
		} else if (selectTypes.contains(SelectType.INDEX) && NumberUtility.isNumberUnsigned(content)) {
			try {
				int index = Integer.parseInt(content);
				if (index > pagedResult.getLowerPageBound() && index <= pagedResult.getHigherPageBound()) {
					pagedResult.select(pagedResult.isIncreasedIndex() ? index - 1 : (pagedResult.getPage() * pagedResult.getPerPage() - pagedResult.getPerPage()) + (index - 1));
					this.attemptDelete(message);
				}
			} catch (NumberFormatException e) {}
		} else {
			int index = contentLower.lastIndexOf(' ');
			if (index != -1 && this.skip.contains(contentLower.substring(0, index))) {
				try {
					int page = Integer.parseInt(contentLower.substring(index + 1));
					if (page > 0 && page <= pagedResult.getMaxPage() && page != pagedResult.getPage()) {
						pagedResult.setPage(page).ensure(channel);
						this.attemptDelete(message);

						return;
					}
				} catch (NumberFormatException e) {}
			}
			
			if (selectTypes.contains(SelectType.OBJECT)) {
				for (int i = pagedResult.getPage() * pagedResult.getPerPage() - pagedResult.getPerPage(); i < (pagedResult.getPage() == pagedResult.getMaxPage() ? pagedResult.getList().size() : pagedResult.getPage() * pagedResult.getPerPage()); i++) {
					if (pagedResult.runSelectablePredicate(content, i)) {
						pagedResult.select(i);
						this.attemptDelete(message);
					}
				}
			}
		}
	}

	@Override
	public void onEvent(GenericEvent event) {
		if (event instanceof MessageReceivedEvent) {
			this.handleMessage(((MessageReceivedEvent) event).getMessage());
		} else if (event instanceof MessageUpdateEvent) {
			this.handleMessage(((MessageUpdateEvent) event).getMessage());
		} else if (event instanceof ButtonClickEvent) {
			this.handleButton((ButtonClickEvent) event);
		}
	}

}
