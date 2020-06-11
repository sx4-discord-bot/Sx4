package com.sx4.bot.commands.info;

import java.util.List;
import java.util.stream.Collectors;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.Category;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.argument.Or;
import com.sx4.bot.utility.NumberUtility;

import net.dv8tion.jda.api.entities.Member;

public class JoinPositionCommand extends Sx4Command {

	public JoinPositionCommand() {
		super("join position");
		
		super.setDescription("View the join position at a specific index or for a specific user");
		super.setExamples("join position 1", "join position Shea#6653");
		super.setAliases("joinposition");
		super.setCategory(Category.INFORMATION);
	}
	
	public void onCommand(Sx4CommandEvent event, @Argument(value="position | user", endless=true, nullDefault=true) Or<Integer, Member> or) {
		if (or == null) {
			or = new Or<>(null, event.getMember());
		}
		
		List<Member> members = event.getGuild().getMemberCache().stream()
			.sorted((a, b) -> a.getTimeJoined().compareTo(b.getTimeJoined()))
			.collect(Collectors.toList());
			
		if (or.hasFirst()) {
			int joinPosition = or.getFirst();
			if (joinPosition > members.size()) {
				event.reply("The join position cannot be more than the member count of this server :no_entry:").queue();
				return;
			}
			
			if (joinPosition < 1) {
				event.reply("The join position cannot be lower than 1 :no_entry:").queue();
				return;
			}
			
			event.replyFormat("**%s** was the %s user to join %s", members.get(joinPosition - 1).getUser().getAsTag(), NumberUtility.getSuffixed(joinPosition), event.getGuild().getName()).queue();
		} else {
			Member member = or.getSecond();
			
			event.replyFormat("%s was the **%s** user to join %s", member.getUser().getAsTag(), NumberUtility.getSuffixed(members.indexOf(member) + 1), event.getGuild().getName()).queue();
		}
	}
	
}
