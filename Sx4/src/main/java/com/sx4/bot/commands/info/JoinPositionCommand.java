package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.option.Option;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.annotations.command.Redirects;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.argument.Or;
import com.sx4.bot.paged.MessagePagedResult;
import com.sx4.bot.utility.NumberUtility;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class JoinPositionCommand extends Sx4Command {

	public JoinPositionCommand() {
		super("join position", 32);
		
		super.setDescription("View the join position at a specific index or for a specific user");
		super.setExamples("join position 1", "join position Shea#6653");
		super.setAliases("joinposition", "joinpos", "join pos");
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}
	
	public void onCommand(Sx4CommandEvent event, @Argument(value="position | user", endless=true, nullDefault=true) Or<Integer, Member> or) {
		if (or == null) {
			or = new Or<>(null, event.getMember());
		}
		
		List<Member> members = event.getGuild().getMemberCache().applyStream(stream -> stream.sorted(Comparator.comparing(Member::getTimeJoined)).collect(Collectors.toList()));
			
		if (or.hasFirst()) {
			int joinPosition = or.getFirst();
			if (joinPosition > members.size()) {
				event.replyFailure("The join position cannot be more than the member count of this server").queue();
				return;
			}
			
			if (joinPosition < 1) {
				event.replyFailure("The join position cannot be lower than 1").queue();
				return;
			}
			
			event.replyFormat("**%s** was the %s user to join %s", members.get(joinPosition - 1).getUser().getAsTag(), NumberUtility.getSuffixed(joinPosition), event.getGuild().getName()).queue();
		} else {
			Member member = or.getSecond();
			
			event.replyFormat("%s was the **%s** user to join %s", member.getUser().getAsTag(), NumberUtility.getSuffixed(members.indexOf(member) + 1), event.getGuild().getName()).queue();
		}
	}

	@Command(value="leaderboard", aliases={"lb"}, description="View a leaderboard of users in order of join date")
	@CommandId(466)
	@Redirects({"lb join position", "leaderboard join position"})
	@Examples({"join position leaderboard", "join position leaderboard --reverse"})
	public void leaderboard(Sx4CommandEvent event, @Option(value="reverse", description="Reverses the order") boolean reverse) {
		Comparator<Member> comparator = reverse ? Comparator.comparing(Member::getTimeJoined).reversed() : Comparator.comparing(Member::getTimeJoined);
		List<Member> members = event.getGuild().getMemberCache().applyStream(stream -> stream.sorted(comparator).collect(Collectors.toList()));

		MessagePagedResult<Member> paged = new MessagePagedResult.Builder<>(event.getBot(), members)
			.setIncreasedIndex(true)
			.setSelect()
			.setPerPage(25)
			.setAuthor("Users", null, event.getGuild().getIconUrl())
			.setDisplayFunction(member -> MarkdownSanitizer.escape(member.getUser().getAsTag()))
			.build();

		paged.execute(event);
	}
	
}
