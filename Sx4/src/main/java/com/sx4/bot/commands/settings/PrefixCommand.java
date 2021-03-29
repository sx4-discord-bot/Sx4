package com.sx4.bot.commands.settings;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import com.sx4.bot.annotations.command.AuthorPermissions;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.model.Operators;
import com.sx4.bot.utility.ExceptionUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PrefixCommand extends Sx4Command {

	public PrefixCommand() {
		super("prefix", 224);

		super.setDescription("View your current prefixes");
		super.setExamples("prefix", "prefix self", "prefix server");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.SETTINGS);
	}

	public void onCommand(Sx4CommandEvent event) {
		List<String> guildPrefixes = event.getDatabase().getGuildById(event.getGuild().getIdLong(), Projections.include("prefixes")).getList("prefixes", String.class, Collections.emptyList());
		List<String> userPrefixes = event.getDatabase().getUserById(event.getAuthor().getIdLong(), Projections.include("prefixes")).getList("prefixes", String.class, Collections.emptyList());

		EmbedBuilder embed = new EmbedBuilder();
		embed.setAuthor("Prefix Settings", null, event.getAuthor().getEffectiveAvatarUrl());
		embed.setColor(event.getMember().getColor());
		embed.addField("Default Prefixes", String.join(", ", event.getConfig().getDefaultPrefixes()), false);
		embed.addField("Server Prefixes", guildPrefixes.isEmpty() ? "None" : String.join(", ", guildPrefixes), false);
		embed.addField(event.getAuthor().getName() + "'s Prefixes", userPrefixes.isEmpty() ? "None" : String.join(", ", userPrefixes), false);

		event.reply(new MessageBuilder().setEmbed(embed.build()).setContent("For help on setting the prefix use `" + event.getPrefix() + "help prefix`").build()).queue();
	}

	public static class SelfCommand extends Sx4Command {

		public SelfCommand() {
			super("self", 225);

			super.setDescription("Set personal prefixes that you can use in any server");
			super.setExamples("prefix self !", "prefix self add", "prefix self remove");
		}

		public void onCommand(Sx4CommandEvent event, @Argument(value="prefixes") String[] prefixes) {
			List<String> finalPrefixes = new ArrayList<>();
			for (String prefix : prefixes) {
				if (!prefix.isBlank() && prefix.length() <= 30) {
					finalPrefixes.add(prefix);
				}
			}

			if (finalPrefixes.isEmpty()) {
				event.replyFailure("You did not provide a valid prefix").queue();
				return;
			}

			if (finalPrefixes.size() > 25) {
				event.replyFailure("You cannot have more than 25 prefixes").queue();
				return;
			}

			event.getDatabase().updateUserById(event.getAuthor().getIdLong(), Updates.set("prefixes", finalPrefixes)).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				event.replySuccess("Your prefixes have been updated").queue();
			});
		}

		@Command(value="add", description="Adds prefixes to your current personal ones")
		@CommandId(226)
		@Examples({"prefix self add !", "prefix self add ! ?"})
		public void add(Sx4CommandEvent event, @Argument(value="prefixes") String[] prefixes) {
			List<String> finalPrefixes = new ArrayList<>();
			for (String prefix : prefixes) {
				if (!prefix.isBlank() && prefix.length() <= 30) {
					finalPrefixes.add(prefix);
				}
			}

			if (finalPrefixes.isEmpty()) {
				event.replyFailure("You did not provide a valid prefix").queue();
				return;
			}

			List<Bson> update = List.of(Operators.set("prefixes", Operators.let(new Document("prefixes", Operators.ifNull("$prefixes", event.getConfig().getDefaultPrefixes())), Operators.cond(Operators.gte(Operators.size("$$prefixes"), 25), "$prefixes", Operators.concatArrays(finalPrefixes, Operators.filter("$$prefixes", Operators.not(Operators.in("$$this", finalPrefixes))))))));
			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.BEFORE).projection(Projections.include("prefixes"));

			event.getDatabase().findAndUpdateUserById(event.getAuthor().getIdLong(), update, options).whenComplete((data, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				List<String> previousPrefixes = data == null ? Collections.emptyList() : data.getList("prefixes", String.class, Collections.emptyList());
				if (previousPrefixes.size() >= 25) {
					event.replyFailure("You cannot have more than 25 prefixes").queue();
				}

				if (previousPrefixes.equals(finalPrefixes)) {
					event.replyFailure("You already had all those prefixes").queue();
					return;
				}

				event.replySuccess("Your prefixes have been updated").queue();
			});
		}

		@Command(value="remove", description="Removes prefixes from your current personal ones")
		@CommandId(227)
		@Examples({"prefix self remove !", "prefix self remove ! ?", "prefix self remove all"})
		public void remove(Sx4CommandEvent event, @Argument(value="prefixes | all") String[] prefixes) {
			boolean all = prefixes.length == 1 && prefixes[0].equalsIgnoreCase("all");

			Bson update = all ? Updates.unset("prefixes") : Updates.pullAll("prefixes", Arrays.asList(prefixes));
			event.getDatabase().updateUserById(event.getAuthor().getIdLong(), update).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (result.getModifiedCount() == 0) {
					event.replyFailure("You didn't have any " + (all ? "" : "of those ") + "prefixes").queue();
					return;
				}

				event.replySuccess((all ? "All" : "Those") + " prefixes have been removed from your personal prefixes").queue();
			});
		}

	}

	public static class ServerCommand extends Sx4Command {

		public ServerCommand() {
			super("server", 228);

			super.setDescription("Set server prefixes for the current server");
			super.setAliases("guild");
			super.setExamples("prefix server !", "prefix server add", "prefix server remove");
			super.setAuthorDiscordPermissions(Permission.MANAGE_SERVER);
		}

		public void onCommand(Sx4CommandEvent event, @Argument(value="prefixes") String[] prefixes) {
			List<String> finalPrefixes = new ArrayList<>();
			for (String prefix : prefixes) {
				if (!prefix.isBlank() && prefix.length() <= 30) {
					finalPrefixes.add(prefix);
				}
			}

			if (finalPrefixes.isEmpty()) {
				event.replyFailure("You did not provide a valid prefix").queue();
				return;
			}

			if (finalPrefixes.size() > 25) {
				event.replyFailure("You cannot have more than 25 prefixes").queue();
				return;
			}

			event.getDatabase().updateGuildById(event.getGuild().getIdLong(), Updates.set("prefixes", finalPrefixes)).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				event.replySuccess("The servers prefixes have been updated").queue();
			});
		}

		@Command(value="add", description="Adds prefixes to the current server")
		@CommandId(229)
		@Examples({"prefix server add !", "prefix server add ! ?"})
		@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
		public void add(Sx4CommandEvent event, @Argument(value="prefixes") String[] prefixes) {
			List<String> finalPrefixes = new ArrayList<>();
			for (String prefix : prefixes) {
				if (!prefix.isBlank() && prefix.length() <= 30) {
					finalPrefixes.add(prefix);
				}
			}

			if (finalPrefixes.isEmpty()) {
				event.replyFailure("You did not provide a valid prefix").queue();
				return;
			}

			List<Bson> update = List.of(Operators.set("prefixes", Operators.let(new Document("prefixes", Operators.ifNull("$prefixes", event.getConfig().getDefaultPrefixes())), Operators.cond(Operators.gte(Operators.size("$$prefixes"), 25), "$prefixes", Operators.concatArrays(finalPrefixes, Operators.filter("$$prefixes", Operators.not(Operators.in("$$this", finalPrefixes))))))));
			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().upsert(true).projection(Projections.include("prefixes")).returnDocument(ReturnDocument.BEFORE);

			event.getDatabase().findAndUpdateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((data, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				List<String> previousPrefixes = data == null ? Collections.emptyList() : data.getList("prefixes", String.class, Collections.emptyList());
				if (previousPrefixes.size() >= 25) {
					event.replyFailure("You cannot have more than 25 prefixes").queue();
				}

				if (previousPrefixes.equals(finalPrefixes)) {
					event.replyFailure("You already had all those prefixes").queue();
					return;
				}

				event.replySuccess("The servers prefixes have been updated").queue();
			});
		}

		@Command(value="remove", description="Removes prefixes from the current server")
		@CommandId(230)
		@Examples({"prefix server remove !", "prefix server remove ! ?", "prefix server remove all"})
		@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
		public void remove(Sx4CommandEvent event, @Argument(value="prefixes | all") String[] prefixes) {
			boolean all = prefixes.length == 1 && prefixes[0].equalsIgnoreCase("all");

			Bson update = all ? Updates.unset("prefixes") : Updates.pullAll("prefixes", Arrays.asList(prefixes));
			event.getDatabase().updateGuildById(event.getGuild().getIdLong(), update).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (result.getModifiedCount() == 0) {
					event.replyFailure("The server didn't have any " + (all ? "" : "of those ") + "prefixes").queue();
					return;
				}

				event.replySuccess((all ? "All" : "Those") + " prefixes have been removed from the server").queue();
			});
		}

	}

}
