package com.sx4.bot.commands.mod;

import java.time.Clock;
import java.util.ArrayList;
import java.util.function.Function;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.argument.Endless;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.command.Command.AuthorPermissions;
import com.jockie.bot.core.command.Command.BotPermissions;
import com.jockie.bot.core.command.impl.CommandEvent;
import com.sx4.bot.annotations.DefaultInt;
import com.sx4.bot.annotations.Examples;
import com.sx4.bot.annotations.Limit;
import com.sx4.bot.category.Category;
import com.sx4.bot.core.Sx4Command;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.entities.Message.MentionType;

public class PruneCommand extends Sx4Command {

	public PruneCommand() {
		super("prune");
		
		super.setDescription("Prune a set amount of messages in the current channel");
		super.setAuthorDiscordPermissions(Permission.MESSAGE_MANAGE);
		super.setBotDiscordPermissions(Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY);
		super.setCategory(Category.MODERATION);
		super.setExamples("prune", "prune 10");
	}
	
	private void prune(CommandEvent event, int amount, Function<Message, Boolean> filter) {
		event.getTextChannel().getHistory().retrievePast(Math.min(100, amount + 1)).queue(messages -> {
			long secondsNow = Clock.systemUTC().instant().getEpochSecond();
			for (Message message : new ArrayList<>(messages)) {
				if (secondsNow - message.getTimeCreated().toEpochSecond() > 1209600) {
					messages.remove(message);
				} else if (!filter.apply(message)) {
					messages.remove(message);
				} else if (message.getIdLong() == event.getMessage().getIdLong()) {
					messages.remove(message);
				}
			}
			
			messages.add(event.getMessage());
			
			if (messages.size() == 1) {
				messages.get(0).delete().queue();
			} else {
				event.getTextChannel().deleteMessages(messages).queue();
			}
		});
	}
	
	public void onCommand(CommandEvent event, @Argument(value="amount", nullDefault=true) @Limit(min=1, max=100) @DefaultInt(100) int amount) {
		this.prune(event, amount, message -> true);
	}
	
	@Command(value="bots", aliases={"bot"}, description="Prunes a set amount of messages sent by bots")
	@Examples({"prune bots", "prune bots 10"})
	@AuthorPermissions({Permission.MESSAGE_MANAGE})
	@BotPermissions({Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY})
	public void bots(CommandEvent event, @Argument(value="amount", nullDefault=true) @Limit(min=1, max=100) @DefaultInt(100) int amount) {
		this.prune(event, amount, message -> message.getAuthor().isBot());
	}
	
	@Command(value="images", aliases={"image"}, description="Prunes a set amount of messages sent with images")
	@Examples({"prune images", "prune images 10"})
	@AuthorPermissions({Permission.MESSAGE_MANAGE})
	@BotPermissions({Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY})
	public void images(CommandEvent event, @Argument(value="amount", nullDefault=true) @Limit(min=1, max=100) @DefaultInt(100) int amount) {
		this.prune(event, amount, message -> message.getAttachments().stream().anyMatch(Attachment::isImage));
	}
	
	@Command(value="mentions", aliases={"mention"}, description="Prunes a set amount of messages which contain mentions")
	@Examples({"prune mantions", "prune mentions 10", "prune mentions USER CHANNEL"})
	@AuthorPermissions({Permission.MESSAGE_MANAGE})
	@BotPermissions({Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY})
	public void mentions(CommandEvent event, @Argument(value="amount", nullDefault=true) @Limit(min=1, max=100) @DefaultInt(100) int amount, @Argument(value="mentions") @Endless(minArguments=0) MentionType... mentions) {
		this.prune(event, amount, message -> !message.getMentions(mentions).isEmpty());
	}
	
	@Command(value="attachments", aliases={"attachments"}, description="Prunes a set amount of messages sent with attachments")
	@Examples({"prune attachments", "prune attachments 10"})
	@AuthorPermissions({Permission.MESSAGE_MANAGE})
	@BotPermissions({Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY})
	public void attachments(CommandEvent event, @Argument(value="amount", nullDefault=true) @Limit(min=1, max=100) @DefaultInt(100) int amount) {
		this.prune(event, amount, message -> !message.getAttachments().isEmpty());
	}
	
	@Command(value="contains", aliases={"contain"}, description="Prunes a set amount of messages which contain the content given")
	@Examples({"prune contains hello", "prune contains hello 10"})
	@AuthorPermissions({Permission.MESSAGE_MANAGE})
	@BotPermissions({Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY})
	public void contains(CommandEvent event, @Argument(value="content") String content, @Argument(value="amount", nullDefault=true) @Limit(min=1, max=100) @DefaultInt(100) int amount) {
		this.prune(event, amount, message -> message.getContentRaw().contains(content));
	}
	
}
