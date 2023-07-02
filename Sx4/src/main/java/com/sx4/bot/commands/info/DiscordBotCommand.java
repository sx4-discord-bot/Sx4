package com.sx4.bot.commands.info;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.paged.MessagePagedResult;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.NumberUtility;
import com.sx4.bot.utility.SearchUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import okhttp3.Request;
import org.bson.Document;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;

public class DiscordBotCommand extends Sx4Command {

	private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yy");

	public DiscordBotCommand() {
		super("discord bot", 338);

		super.setDescription("Get some basic information on a discord bot from top.gg");
		super.setAliases("discord bot list", "dbl", "topgg", "top gg");
		super.setExamples("discord bot", "discord bot @Sx4#1617", "discord bot Sx4");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="bot", endless=true, nullDefault=true) String query) {
		CompletableFuture<String> future = new CompletableFuture<>();
		if (query == null) {
			future.complete("440996323156819968");
		} else {
			Matcher mentionMatch = SearchUtility.USER_MENTION.matcher(query);
			if (NumberUtility.isNumber(query)) {
				future.complete(query);
			} else if (mentionMatch.matches()) {
				future.complete(mentionMatch.group(1));
			} else if (query.length() <= 32) {
				Request request = new Request.Builder()
					.url("https://top.gg/api/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&type=bot")
					.build();

				event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
					Document data = Document.parse(response.body().string());

					MessagePagedResult<Document> paged = new MessagePagedResult.Builder<>(event.getBot(), data.getList("results", Document.class))
						.setAutoSelect(true)
						.setDisplayFunction(d -> d.getString("name") + " (" + d.getString("id") + ")")
						.build();

					paged.onSelect(select -> future.complete(select.getSelected().getString("id")));

					paged.execute(event);
				});
			} else {
				event.replyFailure("I could not find that bot").queue();
				return;
			}
		}

		future.whenComplete((id, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			Request request = new Request.Builder()
				.url("https://top.gg/api/bots/" + id)
				.addHeader("Authorization", event.getConfig().getTopGG())
				.build();

			event.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
				if (response.code() == 404) {
					event.replyFailure("I could not find that bot").queue();
					return;
				}

				Document bot = Document.parse(response.body().string());

				String botAvatarUrl = "https://cdn.discordapp.com/avatars/" + bot.getString("id") + "/" + bot.getString("avatar");
				String botInviteUrl = !bot.getString("invite").isEmpty() ? bot.getString("invite") : "https://discord.com/oauth2/authorize?client_id=" + bot.getString("id") + "&scope=bot";
				String guildCount = bot.containsKey("server_count") ? String.format("%,d", bot.getInteger("server_count")) : "N/A";

				String ownerId = bot.getList("owners", String.class).get(0);
				User owner = event.getShardManager().getUserById(ownerId);

				EmbedBuilder embed = new EmbedBuilder()
					.setAuthor(bot.getString("username") + "#" + bot.getString("discriminator"), "https://top.gg/bot/" + bot.getString("id"), botAvatarUrl)
					.setThumbnail(botAvatarUrl)
					.setDescription((bot.getBoolean("certifiedBot") ? "<:certified:438392214545235978> | " : "") + bot.getString("shortdesc"))
					.addField("Guilds", guildCount, true)
					.addField("Prefix", bot.getString("prefix"), true)
					.addField("Library", bot.getString("lib"), true)
					.addField("Approval Date", OffsetDateTime.parse(bot.getString("date")).format(this.formatter), true)
					.addField("Monthly Votes", String.format("%,d :thumbsup:", bot.getInteger("monthlyPoints")), true)
					.addField("Total Votes", String.format("%,d :thumbsup:", bot.getInteger("points")), true)
					.addField("Invite", "**[Invite " + bot.getString("username") + " to your server](" + botInviteUrl + ")**", true);

				if (owner == null) {
					embed.setFooter("Primary Owner ID: " + ownerId, null);
				} else {
					embed.setFooter("Primary Owner: " + owner.getAsTag(), owner.getEffectiveAvatarUrl());
				}

				event.reply(embed.build()).queue();
			});
		});
	}

}
