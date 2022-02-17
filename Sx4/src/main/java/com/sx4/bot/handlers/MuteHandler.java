package com.sx4.bot.handlers;

import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.entities.mod.action.Action;
import com.sx4.bot.exceptions.mod.ModException;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.ModUtility;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletionException;

public class MuteHandler implements EventListener {

	private final Sx4 bot;

	public MuteHandler(Sx4 bot) {
		this.bot = bot;
	}

	public void onGuildMemberJoin(GuildMemberJoinEvent event) {
		List<Bson> mutePipeline = List.of(
			Aggregates.match(Filters.and(Filters.eq("userId", event.getMember().getIdLong()), Filters.eq("guildId", event.getGuild().getIdLong()))),
			Aggregates.project(Projections.include("unmuteAt"))
		);

		List<Bson> pipeline = List.of(
			Aggregates.project(Projections.include("mute")),
			Aggregates.match(Filters.eq("_id", event.getGuild().getIdLong())),
			Aggregates.unionWith("mutes", mutePipeline),
			Aggregates.group(null, Accumulators.max("mute", "$mute"), Accumulators.max("muted", Operators.lt(Operators.nowEpochSecond(), Operators.ifNull("$unmuteAt", 0L))))
		);

		this.bot.getMongo().aggregateGuilds(pipeline).whenComplete((documents, exception) -> {
			if (documents.isEmpty()) {
				return;
			}

			Document data = documents.get(0);
			if (!data.getBoolean("muted")) {
				return;
			}

			Document mute = data.get("mute", MongoDatabase.EMPTY_DOCUMENT);

			Document leaveAction = mute.get("leaveAction", Document.class);
			if (leaveAction == null) {
				long roleId = mute.get("roleId", 0L);
				if (roleId != 0L) {
					Role role = event.getGuild().getRoleById(roleId);
					if (role != null) {
						event.getGuild().addRoleToMember(event.getMember(), role).queue();
					}
				}

				return;
			}

			ModUtility.performAction(this.bot, Action.fromData(leaveAction), event.getMember(), event.getGuild().getSelfMember(), new Reason("Mute evasion")).whenComplete((result, modException) -> {
				Throwable cause = modException instanceof CompletionException ? modException.getCause() : modException;
				if (cause instanceof ModException) {
					return;
				}

				ExceptionUtility.sendErrorMessage(modException);
			});
		});
	}

	@Override
	public void onEvent(@NotNull GenericEvent event) {
		if (event instanceof GuildMemberJoinEvent) {
			this.onGuildMemberJoin((GuildMemberJoinEvent) event);
		}
	}

}
