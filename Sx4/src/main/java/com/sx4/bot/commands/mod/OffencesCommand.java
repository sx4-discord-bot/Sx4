package com.sx4.bot.commands.mod;

import com.jockie.bot.core.argument.Argument;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.mod.action.Action;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.ExceptionUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class OffencesCommand extends Sx4Command {

	private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm");

	public OffencesCommand() {
		super("offences", 447);

		super.setDescription("View the all the offences or offences of a specific user in your server");
		super.setExamples("offences", "offences @Shea#6653", "offences Shea");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.MODERATION);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="user", endless=true, nullDefault=true) Member member) {
		Bson filter = Filters.eq("guildId", event.getGuild().getIdLong());
		if (member != null) {
			filter = Filters.and(filter, Filters.eq("targetId", member.getIdLong()));
		}

		event.getMongo().getOffences(filter, Sorts.descending("_id")).whenComplete((iterable, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			List<Document> data = iterable.into(new ArrayList<>());
			if (data.isEmpty()) {
				event.replyFailure((member == null ? "This server" : "**" + member.getUser().getAsTag() + "**") + " has no offences").queue();
				return;
			}

			User user = member == null ? null : member.getUser();

			PagedResult<Document> paged = new PagedResult<>(event.getBot(), data)
				.setPerPage(6)
				.setSelect()
				.setCustomFunction(page -> {
					EmbedBuilder embed = new EmbedBuilder()
						.setAuthor("Offences", null, member == null ? event.getGuild().getIconUrl() : user.getEffectiveAvatarUrl())
						.setTitle("Page " + page.getPage() + "/" + page.getMaxPage())
						.setFooter(PagedResult.DEFAULT_FOOTER_TEXT);

					page.forEach((offence, index) -> {
						Action action = Action.fromData(offence.get("action", Document.class));

						ObjectId id = offence.getObjectId("_id");
						OffsetDateTime time = OffsetDateTime.ofInstant(Instant.ofEpochSecond(id.getTimestamp()), ZoneOffset.UTC);

						long targetId = offence.getLong("targetId");
						User target = member == null ? event.getShardManager().getUserById(targetId) : user;
						String targetContent = target == null ? "Anonymous#0000 (" + targetId + ")" : target.getAsTag();

						long moderatorId = offence.getLong("moderatorId");
						User moderator = event.getShardManager().getUserById(moderatorId);
						String moderatorContent = moderator == null ? "Anonymous#0000 (" + moderatorId + ")" : moderator.getAsTag();

						embed.addField(action.toString(), String.format("Target: %s\nModerator: %s\nReason: %s\nTime: %s", targetContent, moderatorContent, offence.get("reason", "None Given"), time.format(this.formatter)), true);
					});

					return new MessageCreateBuilder().setEmbeds(embed.build());
				});

			paged.execute(event);
		});
	}

}
