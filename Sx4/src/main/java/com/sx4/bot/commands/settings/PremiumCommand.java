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
import com.sx4.bot.database.model.Operators;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.ExceptionUtility;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
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
		
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.include("guilds", "amount"));
		
		List<Bson> update = List.of(Operators.set("guilds", Operators.cond(Operators.lt(Operators.size("$guilds"), Operators.floor(Operators.divide("$amount", price))), Operators.concatArrays("$guilds", List.of(guildId)), "$guilds")));
		this.database.findAndUpdatePatronById(Filters.eq("discordId", event.getAuthor().getIdLong()), update, options).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}
			
			if (data == null) {
				event.reply("You are not a premium user, you can become one at <https://patreon.com/Sx4> " + this.config.getFailureEmote()).queue();
				return;
			}
			
			int allocatedGuilds = data.getInteger("amount") / price;
			
			List<Long> guilds = data.getList("guilds", Long.class, Collections.emptyList());
			if (guilds.size() == allocatedGuilds) {
				event.replyFormat("You have used all your premium servers (**%d/%<d**) " + this.config.getFailureEmote(), allocatedGuilds).queue();
				return;
			}
			
			this.database.updateGuildById(guildId, Updates.set("premium", event.getAuthor().getIdLong())).whenComplete((result, guildException) -> {
				if (ExceptionUtility.sendExceptionally(event, guildException)) {
					return;
				}
				
				event.reply("That server is now premium " + this.config.getSuccessEmote()).queue();
			});
		});
	}
	
	@Command(value="remove", description="Remove a server from being premium")
	@Examples({"premium remove", "premium remove Sx4 | Support Server"})
	@Donator
	public void remove(Sx4CommandEvent event, @Argument(value="server", endless=true, nullDefault=true) Guild guild) {
		if (guild == null) {
			guild = event.getGuild();
		}
		
		long guildId = guild.getIdLong();
		
		this.database.updatePatronByFilter(Filters.eq("discordId", event.getAuthor().getIdLong()), Updates.pull("guilds", guildId)).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}
			
			if (result.getModifiedCount() == 0) {
				event.reply("You are not giving premium to that server " + this.config.getFailureEmote()).queue();
				return;
			}
			
			this.database.updateGuildById(guildId, Updates.unset("premium")).whenComplete((guildResult, guildException) -> {
				if (ExceptionUtility.sendExceptionally(event, guildException)) {
					return;
				}
				
				event.reply("That server is no longer premium " + this.config.getSuccessEmote()).queue();
			});
		});
	}
	
	@Command(value="list", description="Lists all the servers you are giving premium to")
	@Examples({"premium list"})
	public void list(Sx4CommandEvent event) {
		Document data = this.database.getPatronByFilter(Filters.eq("discordId", event.getAuthor().getIdLong()), Projections.include("guilds", "amount"));
		
		List<Long> guildIds = data.getList("guilds", Long.class, Collections.emptyList());
		if (guildIds.isEmpty()) {
			event.reply("You are not giving premium to any servers " + this.config.getFailureEmote()).queue();
			return;
		}
		
		int allocatedGuilds = data.getInteger("amount") / Config.get().getPremiumPrice();
		
		ShardManager shardManager = event.getShardManager();	
		
		List<Guild> guilds = guildIds.stream()
			.map(shardManager::getGuildById)
			.filter(Objects::nonNull)
			.collect(Collectors.toList());
		
		PagedResult<Guild> paged = new PagedResult<>(guilds)
			.setAuthor(String.format("Premium Servers (%d/%d)", guilds.size(), allocatedGuilds), null, event.getAuthor().getEffectiveAvatarUrl())
			.setDisplayFunction(Guild::getName);
		
		paged.execute(event);
	}
	
}
