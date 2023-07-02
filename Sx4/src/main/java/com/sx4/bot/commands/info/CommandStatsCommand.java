package com.sx4.bot.commands.info;

import com.jockie.bot.core.option.Option;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.paged.MessagePagedResult;
import com.sx4.bot.utility.ExceptionUtility;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.sql.Date;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class CommandStatsCommand extends Sx4Command {

	private enum GroupType {

		COMMAND("command.name"),
		USER("authorId"),
		CHANNEL("channelId"),
		SERVER("guildId");

		private final String field;

		private GroupType(String field) {
			this.field = field;
		}

		public String getField() {
			return this.field;
		}

	}

	public CommandStatsCommand() {
		super("command stats", 449);

		super.setDescription("Get stats on commands used");
		super.setExamples("command stats", "command stats --group=user", "command stats --group=server --user=402557516728369153");
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event, @Option(value="group", description="What to group the data by, default is command") GroupType group, @Option(value="command", description="Provide a command filter") Sx4Command command, @Option(value="server", aliases={"guild"}, description="Provide a server filter") Guild guild, @Option(value="channel", description="Provide a channel filter") TextChannel channel, @Option(value="user", description="Provide a user id argument") long userId, @Option(value="from", description="When the data should start in epoch seconds") long from, @Option(value="to", description="When the data should end in epoch seconds") long to) {
		List<Bson> filters = new ArrayList<>();
		if (command != null) {
			filters.add(Filters.eq("command.id", command.getId()));
		}

		if (group == GroupType.CHANNEL) {
			filters.add(Filters.eq("guildId", event.getGuild().getIdLong()));
		} else if (guild != null) {
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

		List<Bson> pipeline = List.of(
			Aggregates.match(filters.isEmpty() ? Filters.empty() : Filters.and(filters)),
			Aggregates.group("$" + (group == null ? GroupType.COMMAND : group).getField(), Accumulators.sum("count", 1L)),
			Aggregates.sort(Sorts.descending("count"))
		);

		event.getMongo().aggregateCommands(pipeline).whenComplete((commands, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (commands.isEmpty()) {
				event.replyFailure("No data was found with those filters").queue();
				return;
			}

			MessagePagedResult<Document> paged = new MessagePagedResult.Builder<>(event.getBot(), commands)
				.setIndexed(false)
				.setSelect()
				.setAuthor("Command Stats", null, event.getSelfUser().getEffectiveAvatarUrl())
				.setDisplayFunction(data -> {
					String prefix = null;
					if (group == null || group == GroupType.COMMAND) {
						prefix = "`" + data.getString("_id") + "`";
					} else if (group == GroupType.CHANNEL) {
						long id = data.getLong("_id");
						GuildMessageChannel messageChannel = event.getGuild().getChannelById(GuildMessageChannel.class, id);
						prefix = messageChannel == null ? "#deleted-channel (" + id + ")" : messageChannel.getAsMention();
					} else if (group == GroupType.USER) {
						long id = data.getLong("_id");
						User user = event.getShardManager().getUserById(id);
						prefix = "`" + (user == null ? "Anonymous#0000 (" + id + ")" : MarkdownSanitizer.escape(user.getAsTag())) + "`";
					} else if (group == GroupType.SERVER) {
						Long id = data.getLong("_id");
						if (id == null) {
							prefix = "`Private Messages`";
						} else {
							Guild guildGroup = event.getShardManager().getGuildById(id);
							prefix = "`" + (guildGroup == null ? "Unknown Server (" + id + ")" : MarkdownSanitizer.escape(guildGroup.getName())) + "`";
						}
					}

					long count = data.getLong("count");
					return (prefix == null ? "Unknown" : prefix) + " - " + String.format("%,d", count) + " use" + (count == 1 ? "" : "s");
				}).build();

			paged.execute(event);
		});
	}

}
