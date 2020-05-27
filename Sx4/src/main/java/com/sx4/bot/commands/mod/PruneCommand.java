package com.sx4.bot.commands.mod;

import java.time.Clock;
import java.util.ArrayList;
import java.util.function.Function;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.argument.Endless;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.command.Command.AuthorPermissions;
import com.jockie.bot.core.command.Command.BotPermissions;
import com.sx4.bot.annotations.argument.DefaultInt;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.annotations.command.Redirects;
import com.sx4.bot.category.Category;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
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
	
	private void prune(Sx4CommandEvent event, int amount, Function<Message, Boolean> filter) {
		event.getTextChannel().getHistory().retrievePast(100).queue(messages -> {
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
			
			messages.add(0, event.getMessage());
			messages.subList(0, Math.min(messages.size(), amount));
			
			if (messages.size() == 1) {
				messages.get(0).delete().queue();
			} else {
				event.getTextChannel().deleteMessages(messages).queue();
			}
		});
	}
	
	public void onCommand(Sx4CommandEvent event, @Argument(value="amount") @Limit(min=1, max=100) @DefaultInt(100) int amount) {
		this.prune(event, amount, message -> true);
	}
	
	@Command(value="bots", aliases={"bot"}, description="Prunes a set amount of messages sent by bots")
	@Redirects({"bc", "bot clean", "botclean"})
	@Examples({"prune bots", "prune bots 10"})
	@AuthorPermissions({Permission.MESSAGE_MANAGE})
	@BotPermissions({Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY})
	public void bots(Sx4CommandEvent event, @Argument(value="amount") @Limit(min=1, max=100) @DefaultInt(100) int amount) {
		this.prune(event, amount, message -> message.getAuthor().isBot());
	}
	
	@Command(value="images", aliases={"image"}, description="Prunes a set amount of messages sent with images")
	@Examples({"prune images", "prune images 10"})
	@AuthorPermissions({Permission.MESSAGE_MANAGE})
	@BotPermissions({Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY})
	public void images(Sx4CommandEvent event, @Argument(value="amount") @Limit(min=1, max=100) @DefaultInt(100) int amount) {
		this.prune(event, amount, message -> message.getAttachments().stream().anyMatch(Attachment::isImage));
	}
	
	@Command(value="mentions", aliases={"mention"}, description="Prunes a set amount of messages which contain mentions")
	@Examples({"prune mentions", "prune mentions 10", "prune mentions USER CHANNEL"})
	@AuthorPermissions({Permission.MESSAGE_MANAGE})
	@BotPermissions({Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY})
	public void mentions(Sx4CommandEvent event, @Argument(value="amount") @Limit(min=1, max=100) @DefaultInt(100) int amount, @Argument(value="mentions") @Endless(minArguments=0) MentionType... mentions) {
		this.prune(event, amount, message -> !message.getMentions(mentions).isEmpty());
	}
	
	@Command(value="attachments", aliases={"attachments"}, description="Prunes a set amount of messages sent with attachments")
	@Examples({"prune attachments", "prune attachments 10"})
	@AuthorPermissions({Permission.MESSAGE_MANAGE})
	@BotPermissions({Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY})
	public void attachments(Sx4CommandEvent event, @Argument(value="amount") @Limit(min=1, max=100) @DefaultInt(100) int amount) {
		this.prune(event, amount, message -> !message.getAttachments().isEmpty());
	}
	
	@Command(value="contains", aliases={"contain"}, description="Prunes a set amount of messages which contain the content given")
	@Examples({"prune contains hello", "prune contains hello 10"})
	@AuthorPermissions({Permission.MESSAGE_MANAGE})
	@BotPermissions({Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY})
	public void contains(Sx4CommandEvent event, @Argument(value="content") String content, @Argument(value="amount") @Limit(min=1, max=100) @DefaultInt(100) int amount) {
		this.prune(event, amount, message -> message.getContentRaw().contains(content));
	}
	
	@Command(value="user", description="Prunes a set amount of message sent by a specific user")
	@Examples({"prune user @Shea#6653", "prune user Shea 10"})
	@AuthorPermissions({Permission.MESSAGE_MANAGE})
	@BotPermissions({Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY})
	public void user(Sx4CommandEvent event, @Argument(value="user") Member member, @Argument(value="amount") @Limit(min=1, max=100) @DefaultInt(100) int amount) {
		this.prune(event, amount, message -> message.getAuthor().getIdLong() == member.getIdLong());
	}
	
}
