package com.sx4.bot.paged;

import com.sx4.bot.core.Sx4;
import com.sx4.bot.paged.PagedResult.SelectType;
import com.sx4.bot.utility.NumberUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.jetbrains.annotations.NotNull;

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
		if (message.isFromGuild() && message.getGuild().getSelfMember().hasPermission(message.getGuildChannel(), Permission.MESSAGE_MANAGE)) {
			message.delete().queue(null, ErrorResponseException.ignore(ErrorResponse.UNKNOWN_MESSAGE));
		}
	}

	public void handleMenu(StringSelectInteractionEvent event) {
		if (event.isAcknowledged()) {
			return;
		}

		SelectMenu menu = event.getSelectMenu();
		if (!menu.getId().equals("paged-select")) {
			return;
		}

		PagedResult<?> pagedResult = this.bot.getPagedManager().getPagedResult(event.getMessageIdLong());
		if (pagedResult == null) {
			event.reply("This paged result timed out").setEphemeral(true).queue();
			return;
		}

		if (pagedResult.getOwnerId() != event.getUser().getIdLong()) {
			event.reply("This is not your paged result " + this.bot.getConfig().getFailureEmote()).setEphemeral(true).queue();
			return;
		}

		if (menu.isDisabled()) {
			return;
		}

		event.deferEdit().queue();
		pagedResult.select(Integer.parseInt(event.getValues().get(0)));
	}

	public void handleButton(ButtonInteractionEvent event) {
		if (event.isAcknowledged()) {
			return;
		}

		Button button = event.getButton();
		String id = button.getId();
		if (!id.equals("next") && !id.equals("previous")) {
			return;
		}

		PagedResult<?> pagedResult = this.bot.getPagedManager().getPagedResult(event.getMessageIdLong());
		if (pagedResult == null) {
			event.reply("This paged result timed out").setEphemeral(true).queue();
			return;
		}

		if (pagedResult.getOwnerId() != event.getUser().getIdLong()) {
			event.reply("This is not your paged result " + this.bot.getConfig().getFailureEmote()).setEphemeral(true).queue();
			return;
		}

		if (button.isDisabled()) {
			return;
		}

		if (id.equals("next")) {
			pagedResult.nextPage().ensure(event);
		} else {
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
			} catch (NumberFormatException ignored) {}
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
				} catch (NumberFormatException ignored) {}
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
	public void onEvent(@NotNull GenericEvent event) {
		if (event instanceof MessageReceivedEvent) {
			this.handleMessage(((MessageReceivedEvent) event).getMessage());
		} else if (event instanceof MessageUpdateEvent) {
			this.handleMessage(((MessageUpdateEvent) event).getMessage());
		} else if (event instanceof ButtonInteractionEvent) {
			this.handleButton((ButtonInteractionEvent) event);
		} else if (event instanceof StringSelectInteractionEvent) {
			this.handleMenu((StringSelectInteractionEvent) event);
		}
	}

}
