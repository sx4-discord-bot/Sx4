package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.sx4.bot.annotations.command.BotPermissions;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.annotations.command.Redirects;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.NumberUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class InvitesCommand extends Sx4Command {

	public InvitesCommand() {
		super("invites", 265);

		super.setAliases("invite");
		super.setDescription("View the amount of invites a specific user has");
		super.setExamples("invites", "invites @Shea#6653", "invites leaderboard");
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="user", endless=true, nullDefault=true) Member member) {
		User user = member == null ? event.getAuthor() : member.getUser();
		event.getGuild().retrieveInvites().queue(invites -> {
			if (invites.isEmpty()) {
				event.replyFailure("That user doesn't have any invites").queue();
				return;
			}

			Map<Long, Integer> count = new HashMap<>();
			int total = 0;
			for (Invite invite : invites) {
				User inviter = invite.getInviter();
				if (inviter != null) {
					count.compute(inviter.getIdLong(), (key, value) -> value == null ? invite.getUses() : value + invite.getUses());
				}

				total += invite.getUses();
			}

			if (!count.containsKey(user.getIdLong())) {
				event.replyFailure("That user doesn't have any invites").queue();
				return;
			}

			List<Map.Entry<Long, Integer>> sortedCount = new ArrayList<>(count.entrySet());
			sortedCount.sort(Collections.reverseOrder(Comparator.comparingInt(Map.Entry::getValue)));

			int percentInvited = Math.round((((float) count.get(user.getIdLong()) / total) * 100));
			String percent = percentInvited >= 1 ? String.valueOf(percentInvited) : "<1";

			for (int i = 0; i < sortedCount.size(); i++) {
				Map.Entry<Long, Integer> entry = sortedCount.get(i);
				if (entry.getKey() == user.getIdLong()) {
					event.replyFormat("%s has **%,d** invites which means they have the **%s** most invites. They have invited **%s%%** of all users.", user.getAsTag(), count.get(user.getIdLong()), NumberUtility.getSuffixed(i + 1), percent).queue();
					return;
				}
			}
		});
	}

	@Command(value="leaderboard", aliases={"lb"}, description="View a leaderboard of all invites in the server sorted by user")
	@CommandId(266)
	@Redirects({"lb invites", "leaderboard invites"})
	@Examples({"invites leaderboard"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void leaderboard(Sx4CommandEvent event) {
		event.getGuild().retrieveInvites().queue(invites -> {
			if (invites.isEmpty()) {
				event.replyFailure("There are no invites in this server").queue();
				return;
			}

			Map<Long, Integer> count = new HashMap<>();
			AtomicInteger total = new AtomicInteger(0);
			for (Invite invite : invites) {
				User inviter = invite.getInviter();
				if (inviter != null) {
					count.compute(inviter.getIdLong(), (key, value) -> value == null ? invite.getUses() : value + invite.getUses());
				}

				total.addAndGet(invite.getUses());
			}

			List<Map.Entry<Long, Integer>> sortedCount = new ArrayList<>(count.entrySet());
			sortedCount.sort(Collections.reverseOrder(Comparator.comparingInt(Map.Entry::getValue)));

			PagedResult<Map.Entry<Long, Integer>> paged = new PagedResult<>(sortedCount)
				.setIncreasedIndex(true)
				.setAuthor("Invites Leaderboard", null, event.getGuild().getIconUrl())
				.setDisplayFunction(data -> {
					int percentInvited = Math.round(((float) data.getValue() / total.get()) * 100);
					String percent = percentInvited >= 1 ? String.valueOf(percentInvited) : "<1";

					Member member = event.getGuild().getMemberById(data.getKey());
					String memberString = member == null ? String.valueOf(data.getKey()) : member.getUser().getAsTag();

					return String.format("`%s` - %,d %s (%s%%)", memberString, data.getValue(), data.getValue() == 1 ? "invite" : "invites", percent);
				});

			paged.execute(event);
		});
	}

}
