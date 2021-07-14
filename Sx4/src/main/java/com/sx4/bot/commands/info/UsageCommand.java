package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.option.Option;
import com.mongodb.client.model.Filters;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.sql.Date;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class UsageCommand extends Sx4Command {

	public UsageCommand() {
		super("usage", 235);

		super.setDescription("View the usage of a specific command");
		super.setExamples("usage fish", "usage fish --user=Shea#6653", "usage ship --from=1612183011 --to=1612528611");
		super.setExecuteAsync(true);
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="command", endless=true) Sx4Command command, @Option(value="server", aliases={"guild"}, description="Provide a server filter") Guild guild, @Option(value="channel", description="Provide a channel filter") TextChannel channel, @Option(value="user", description="Provide a user id argument") long userId, @Option(value="from", description="When the data should start in epoch seconds") long from, @Option(value="to", description="When the data should end in epoch seconds") long to) {
		List<Bson> filters = new ArrayList<>();
		filters.add(Filters.eq("command.id", command.getId()));

		if (guild != null) {
			filters.add(Filters.eq("guildId", guild.getIdLong()));
		}

		if (userId != 0L) {
			filters.add(Filters.eq("authorId", userId));
		}

		if (channel != null) {
			filters.add(Filters.eq("channelId", channel.getIdLong()));
		}

		if (from != 0L) {
			filters.add(Filters.gte("_id", new ObjectId(Date.from(Instant.ofEpochSecond(from)))));
		}

		if (to != 0L) {
			filters.add(Filters.lte("_id", new ObjectId(Date.from(Instant.ofEpochSecond(to)))));
		}

		long amount = event.getMongo().countCommands(Filters.and(filters));
		event.replyFormat("`%s` has been used **%,d** time%s", command.getCommandTrigger(), amount, (amount == 1 ? "" : "s")).queue();
	}

}
