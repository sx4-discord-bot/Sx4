package com.sx4.bot.commands.settings;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.client.model.*;
import com.sx4.bot.annotations.command.Donator;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.config.Config;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.Database;
import com.sx4.bot.database.model.Operators;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.ExceptionUtility;
import net.dv8tion.jda.api.entities.Guild;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class PremiumCommand extends Sx4Command {

	public PremiumCommand() {
		super("premium");
		
		super.setDescription("Make a server premium or remove a server from being premium, you can make a server premium for $5");
		super.setExamples("premium add", "premium remove", "premium list");
		super.setCategoryAll(ModuleCategory.SETTINGS);
	}
	
	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}
	
	@Command(value="add", description="Make a server premium")
	@Examples({"premium add", "premium add Sx4 | Support Server"})
	@Donator
	public void add(Sx4CommandEvent event, @Argument(value="server", endless=true, nullDefault=true) Guild guild) {
		if (guild == null) {
			guild = event.getGuild();
		}
		
		long guildId = guild.getIdLong();
		int price = this.config.getPremiumPrice();
		
		Document guildData = this.database.getGuildById(guildId, Projections.include("premium"));
		if (guildData.containsKey("premium")) {
			event.reply("That server already has premium " + this.config.getFailureEmote()).queue();
			return;
		}

		Document premiumData = new Document("id", guildId)
			.append("since", Operators.nowEpochSecond());

		Bson guildsMap = Operators.ifNull("$guilds", Collections.EMPTY_LIST);
		List<Bson> update = List.of(Operators.set("guilds", Operators.cond(Operators.and(Operators.lt(Operators.size(guildsMap), Operators.floor(Operators.divide("$amount", price))), Operators.isEmpty(Operators.filter(guildsMap, Operators.eq("$$this.id", guildId)))), Operators.concatArrays(guildsMap, List.of(premiumData)), "$guilds")));

		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.include("guilds", "amount"));
		this.database.findAndUpdatePatronById(Filters.eq("discordId", event.getAuthor().getIdLong()), update, options).thenCompose(data -> {
			if (data == null) {
				event.reply("You are not a premium user, you can become one at <https://patreon.com/Sx4> " + this.config.getFailureEmote()).queue();
				return CompletableFuture.completedFuture(null);
			}
			
			int allocatedGuilds = data.getInteger("amount") / price;
			
			List<Document> guilds = data.getList("guilds", Document.class, Collections.emptyList());
			if (guilds.stream().anyMatch(d -> d.getLong("id") == guildId)) {
				event.reply("You are already giving premium to this server " + this.config.getFailureEmote()).queue();
				return CompletableFuture.completedFuture(null);
			}

			if (guilds.size() >= allocatedGuilds) {
				event.replyFormat("You have used all your premium servers (**%d/%<d**) " + this.config.getFailureEmote(), allocatedGuilds).queue();
				return CompletableFuture.completedFuture(null);
			}
			
			return this.database.updateGuildById(guildId, Updates.set("premium", event.getAuthor().getIdLong()));
		}).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception) || result == null) {
				return;
			}

			event.reply("That server is now premium " + this.config.getSuccessEmote()).queue();
		});
	}
	
	@Command(value="remove", description="Remove a server from being premium")
	@Examples({"premium remove", "premium remove Sx4 | Support Server"})
	@Donator
	public void remove(Sx4CommandEvent event, @Argument(value="server", endless=true, nullDefault=true) Guild guild) {
		if (guild == null) {
			guild = event.getGuild();
		}
		
		long guildId = guild.getIdLong(), now = Clock.systemUTC().instant().getEpochSecond();

		Bson guildsMap = Operators.ifNull("$guilds", Collections.EMPTY_LIST);
		Bson since = Operators.first(Operators.map(Operators.filter("$guilds", Operators.eq("$$this.id", guildId)), "$$this.since"));
		List<Bson> update = List.of(Operators.set("guilds", Operators.cond(Operators.lt(Operators.subtract(now, since), 604800L), guildsMap, Operators.filter(guildsMap, Operators.ne("$$this.id", guildId)))));

		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().projection(Projections.include("guilds")).returnDocument(ReturnDocument.BEFORE);
		this.database.findAndUpdatePatronByFilter(Filters.eq("discordId", event.getAuthor().getIdLong()), update, options).thenCompose(data -> {
			data = data == null ? Database.EMPTY_DOCUMENT : data;

			Document guildData = data.getList("guilds", Document.class, Collections.emptyList()).stream()
				.filter(d -> d.getLong("id") == guildId)
				.findFirst()
				.orElse(null);

			if (guildData == null) {
				event.reply("You are not giving premium to that server " + this.config.getFailureEmote()).queue();
				return CompletableFuture.completedFuture(null);
			}

			if (now - guildData.getLong("since") < 604800L) {
				event.reply("You cannot remove premium from a server 7 days within giving it premium " + this.config.getFailureEmote()).queue();
				return CompletableFuture.completedFuture(null);
			}
			
			return this.database.updateGuildById(guildId, Updates.unset("premium"));
		}).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception) || result == null) {
				return;
			}

			event.reply("That server is no longer premium " + this.config.getSuccessEmote()).queue();
		});
	}
	
	@Command(value="list", description="Lists all the servers you are giving premium to")
	@Examples({"premium list"})
	public void list(Sx4CommandEvent event) {
		Document data = this.database.getPatronByFilter(Filters.eq("discordId", event.getAuthor().getIdLong()), Projections.include("guilds", "amount"));
		
		List<Document> guilds = data.getList("guilds", Document.class, Collections.emptyList());
		if (guilds.isEmpty()) {
			event.reply("You are not giving premium to any servers " + this.config.getFailureEmote()).queue();
			return;
		}
		
		int allocatedGuilds = data.getInteger("amount") / Config.get().getPremiumPrice();
		
		List<String> guildsFormat = guilds.stream()
			.sorted(Comparator.comparing(guildData -> guildData.getLong("since")))
			.map(guildData -> {
				long guildId = guildData.getLong("id"), since = guildData.getLong("since");
				Guild guild = event.getShardManager().getGuildById(guildId);

				return String.format("`%s` (%s)", (guild == null ? "Unknown (" + guildId + ")" : guild.getName()), ZonedDateTime.ofInstant(Instant.ofEpochSecond(since), ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("d MMM y")));
			})
			.collect(Collectors.toList());
		
		PagedResult<String> paged = new PagedResult<>(guildsFormat)
			.setAuthor(String.format("Premium Servers (%d/%d)", guilds.size(), allocatedGuilds), null, event.getAuthor().getEffectiveAvatarUrl())
			.setIndexed(false);
		
		paged.execute(event);
	}
	
}
