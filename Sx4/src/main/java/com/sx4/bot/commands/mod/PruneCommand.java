package com.sx4.bot.commands.mod;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.argument.Endless;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.command.Command.Cooldown;
import com.jockie.bot.core.cooldown.ICooldown;
import com.jockie.bot.core.option.Option;
import com.sx4.bot.annotations.argument.DefaultInt;
import com.sx4.bot.annotations.argument.DefaultLong;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.annotations.command.AuthorPermissions;
import com.sx4.bot.annotations.command.BotPermissions;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.annotations.command.Redirects;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.entities.Message.MentionType;
import net.dv8tion.jda.api.entities.MessageHistory.MessageRetrieveAction;
import net.dv8tion.jda.api.exceptions.ErrorHandler;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class PruneCommand extends Sx4Command {

	public PruneCommand() {
		super("prune");
		
		super.setDescription("Prune a set amount of messages in the current channel");
		super.setAuthorDiscordPermissions(Permission.MESSAGE_MANAGE);
		super.setBotDiscordPermissions(true, Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY);
		super.setCategoryAll(ModuleCategory.MODERATION);
		super.setExamples("prune", "prune 10");
	}
	
	private CompletableFuture<Void> prune(Sx4CommandEvent event, int amount, long start, long end, Predicate<Message> predicate) {
		CompletableFuture<Void> future = new CompletableFuture<>();

		Message originalMessage = event.getMessage();
		MessageRetrieveAction action = start == 0L ? event.getTextChannel().getHistoryBefore(originalMessage, 100) : event.getTextChannel().getHistoryBefore(start, 100);

		action.queue(history -> {
			List<Message> retrievedHistory = history.getRetrievedHistory();
			List<Message> messages = new ArrayList<>();

			long secondsNow = Clock.systemUTC().instant().getEpochSecond();
			for (Message message : retrievedHistory) {
				if (secondsNow - message.getTimeCreated().toEpochSecond() < 1209600 && predicate.test(message)) {
					if (end != 0L && end == message.getIdLong()) {
						break;
					}

					messages.add(message);
				}
			}

			messages.add(0, originalMessage);
			messages = messages.subList(0, Math.min(messages.size(), amount + 1));

			if (messages.size() == 1) {
				messages.get(0).delete().queue();
			} else {
				event.getTextChannel().deleteMessages(messages).queue();
			}

			future.complete(null);
		}, new ErrorHandler(e -> future.complete(null)));

		return future;
	}
	
	public void onCommand(Sx4CommandEvent event, @Argument(value="amount") @Limit(min=1, max=100) @DefaultInt(100) int amount, @Option(value="start", description="The message id to start pruning from") @DefaultLong(0L) long start, @Option(value="end", description="The message id to end pruning at") @DefaultLong(0L) long end) {
		this.prune(event, amount, start, end, message -> true);
	}
	
	@Command(value="bots", aliases={"bot"}, description="Prunes a set amount of messages sent by bots")
	@Redirects({"bc", "bot clean", "botclean"})
	@Examples({"prune bots", "prune bots 10"})
	@AuthorPermissions(permissions={Permission.MESSAGE_MANAGE})
	@BotPermissions(permissions={Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY}, overwrite=true)
	public void bots(Sx4CommandEvent event, @Argument(value="amount") @Limit(min=1, max=100) @DefaultInt(100) int amount, @Option(value="start", description="The message id to start pruning from") @DefaultLong(0L) long start, @Option(value="end", description="The message id to end pruning at") @DefaultLong(0L) long end) {
		this.prune(event, amount, start, end, message -> message.getAuthor().isBot());
	}
	
	@Command(value="images", aliases={"image"}, description="Prunes a set amount of messages sent with images")
	@Examples({"prune images", "prune images 10"})
	@AuthorPermissions(permissions={Permission.MESSAGE_MANAGE})
	@BotPermissions(permissions={Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY}, overwrite=true)
	public void images(Sx4CommandEvent event, @Argument(value="amount") @Limit(min=1, max=100) @DefaultInt(100) int amount, @Option(value="start", description="The message id to start pruning from") @DefaultLong(0L) long start, @Option(value="end", description="The message id to end pruning at") @DefaultLong(0L) long end) {
		this.prune(event, amount, start, end, message -> message.getAttachments().stream().anyMatch(Attachment::isImage));
	}
	
	@Command(value="mentions", aliases={"mention"}, description="Prunes a set amount of messages which contain mentions")
	@Examples({"prune mentions", "prune mentions 10", "prune mentions USER CHANNEL"})
	@AuthorPermissions(permissions={Permission.MESSAGE_MANAGE})
	@BotPermissions(permissions={Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY}, overwrite=true)
	public void mentions(Sx4CommandEvent event, @Argument(value="amount") @Limit(min=1, max=100) @DefaultInt(100) int amount, @Option(value="start", description="The message id to start pruning from") @DefaultLong(0L) long start, @Option(value="end", description="The message id to end pruning at") @DefaultLong(0L) long end, @Argument(value="mentions") @Endless(minArguments=0) MentionType... mentions) {
		this.prune(event, amount, start, end, message -> !message.getMentions(mentions).isEmpty());
	}
	
	@Command(value="attachments", aliases={"attachments"}, description="Prunes a set amount of messages sent with attachments")
	@Examples({"prune attachments", "prune attachments 10"})
	@AuthorPermissions(permissions={Permission.MESSAGE_MANAGE})
	@BotPermissions(permissions={Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY}, overwrite=true)
	public void attachments(Sx4CommandEvent event, @Argument(value="amount") @Limit(min=1, max=100) @DefaultInt(100) int amount, @Option(value="start", description="The message id to start pruning from") @DefaultLong(0L) long start, @Option(value="end", description="The message id to end pruning at") @DefaultLong(0L) long end) {
		this.prune(event, amount, start, end, message -> !message.getAttachments().isEmpty());
	}
	
	@Command(value="contains", aliases={"contain"}, description="Prunes a set amount of messages which contain the content given")
	@Examples({"prune contains hello", "prune contains hello 10"})
	@AuthorPermissions(permissions={Permission.MESSAGE_MANAGE})
	@BotPermissions(permissions={Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY}, overwrite=true)
	public void contains(Sx4CommandEvent event, @Argument(value="content") String content, @Argument(value="amount") @Limit(min=1, max=100) @DefaultInt(100) int amount, @Option(value="start", description="The message id to start pruning from") @DefaultLong(0L) long start, @Option(value="end", description="The message id to end pruning at") @DefaultLong(0L) long end) {
		this.prune(event, amount, start, end, message -> message.getContentRaw().contains(content));
	}
	
	@Command(value="user", description="Prunes a set amount of messages sent by a specific user")
	@Examples({"prune user @Shea#6653", "prune user Shea 10"})
	@AuthorPermissions(permissions={Permission.MESSAGE_MANAGE})
	@BotPermissions(permissions={Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY}, overwrite=true)
	public void user(Sx4CommandEvent event, @Argument(value="user") Member member, @Argument(value="amount") @Limit(min=1, max=100) @DefaultInt(100) int amount, @Option(value="start", description="The message id to start pruning from") @DefaultLong(0L) long start, @Option(value="end", description="The message id to end pruning at") @DefaultLong(0L) long end) {
		this.prune(event, amount, start, end, message -> message.getAuthor().getIdLong() == member.getIdLong());
	}

	@Command(value="regex", description="Prunes a set amount of messages which match a specific regex")
	@Examples({"prune regex [0-9]+", "prune regex .{2,32}#[0-9]{4}"})
	@Cooldown(value=20, cooldownScope=ICooldown.Scope.GUILD)
	@AuthorPermissions(permissions={Permission.MESSAGE_MANAGE})
	@BotPermissions(permissions={Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY}, overwrite=true)
	public void regex(Sx4CommandEvent event, @Argument(value="regex") Pattern pattern, @Argument(value="amount") @Limit(min=1, max=100) @DefaultInt(100) int amount, @Option(value="start", description="The message id to start pruning from") @DefaultLong(0L) long start, @Option(value="end", description="The message id to end pruning at") @DefaultLong(0L) long end) {
		try {
			this.prune(event, amount, start, end, message -> pattern.matcher(message.getContentRaw()).matches()).get(500, TimeUnit.MILLISECONDS);
		} catch (InterruptedException | ExecutionException | TimeoutException e) {
			event.reply("That regex took longer than 500ms to execute :stopwatch:").queue();
		}
	}
	
}
