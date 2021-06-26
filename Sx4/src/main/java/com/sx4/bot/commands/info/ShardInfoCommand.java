package com.sx4.bot.commands.info;

import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.paged.PagedResult;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ShardInfoCommand extends Sx4Command {

	public ShardInfoCommand() {
		super("shard info", 234);

		super.setDescription("View the shards of Sx4");
		super.setExamples("shard info");
		super.setAliases("shardinfo", "shards");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.INFORMATION);
	}

	public void onCommand(Sx4CommandEvent event) {
		long totalGuilds = event.getShardManager().getGuildCache().size(), totalUsers = event.getShardManager().getUserCache().size();

		List<JDA> shards = new ArrayList<>(event.getShardManager().getShards());
		shards.sort(Comparator.comparingInt(a -> a.getShardInfo().getShardId()));

		JDA.ShardInfo shardInfo = event.getJDA().getShardInfo();
		PagedResult<JDA> paged = new PagedResult<>(event.getBot(), shards)
			.setPerPage(9)
			.setCustomFunction(page -> {
				EmbedBuilder embed = new EmbedBuilder();
				embed.setDescription(String.format("```prolog\nTotal Shards: %d\nTotal Servers: %,d\nTotal Members: %,d\nAverage Ping: %.0fms```", shardInfo.getShardTotal(), totalGuilds, totalUsers, event.getShardManager().getAverageGatewayPing()));
				embed.setAuthor("Shard Info!", null, event.getSelfUser().getEffectiveAvatarUrl());
				embed.setFooter("next | previous | go to <page> | cancel");

				page.forEach((shard, index) -> {
					String currentShard = shardInfo.getShardId() == index ? "\\> " : "";
					embed.addField(currentShard + "Shard " + (index + 1), String.format("%,d servers\n%,d members\n%dms\n%s", shard.getGuilds().size(), shard.getUsers().size(), shard.getGatewayPing(), shard.getStatus().toString()), true);
				});

				return new MessageBuilder().setEmbed(embed.build()).build();
			});

		paged.execute(event);
	}

}
