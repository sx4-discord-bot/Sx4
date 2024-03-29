package com.sx4.bot.commands.settings;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.client.model.*;
import com.sx4.bot.annotations.argument.AlternativeOptions;
import com.sx4.bot.annotations.argument.DefaultNull;
import com.sx4.bot.annotations.command.AuthorPermissions;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.argument.Alternative;
import com.sx4.bot.entities.settings.HolderType;
import com.sx4.bot.paged.MessagePagedResult;
import com.sx4.bot.utility.CheckUtility;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.SettingUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.IPermissionHolder;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.*;
import java.util.stream.Collectors;

public class BlacklistCommand extends Sx4Command {

	public BlacklistCommand() {
		super("blacklist", 167);
		
		super.setDescription("Blacklist roles/users from being able to use specific commands/modules in channels");
		super.setExamples("blacklist add", "blacklist remove", "blacklist list");
		super.setCategoryAll(ModuleCategory.SETTINGS);
	}

	private Bson getHolderPipeline(long guildId) {
		return Operators.let(new Document("ids", Operators.map(Operators.ifNull("$holders", Collections.EMPTY_LIST), "$$this.id")), Operators.concatArrays(Operators.ifNull("$holders", Collections.EMPTY_LIST), Operators.filter(CheckUtility.getDefaultBlacklist(guildId), Operators.not(Operators.in("$$this.id", "$$ids")))));
	}
	
	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}
	
	@Command(value="add", description="Add a role/user to be blacklisted from a specified command/module in a channel")
	@CommandId(168)
	@Examples({"blacklist add #general @Shea#6653 fish", "blacklist add #bots @Members ban"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void add(Sx4CommandEvent event, @Argument(value="channel", nullDefault=true) @DefaultNull GuildMessageChannel channel, @Argument(value="user | role") IPermissionHolder holder, @Argument(value="command | module", endless=true) List<Sx4Command> commands) {
		List<GuildMessageChannel> channels = channel == null ? event.getGuild().getChannels().stream().filter(c -> c instanceof GuildMessageChannel).map(GuildMessageChannel.class::cast).collect(Collectors.toList()) : List.of(channel);

		boolean role = holder instanceof Role;
		int type = role ? HolderType.ROLE.getType() : HolderType.USER.getType();

		BitSet bitSet = new BitSet();
		commands.stream().map(Sx4Command::getId).forEach(bitSet::set);

		Document defaultData = new Document("id", holder.getIdLong())
			.append("type", type)
			.append("blacklisted", Collections.EMPTY_LIST);

		List<Long> longArray = Arrays.stream(bitSet.toLongArray()).boxed().collect(Collectors.toList());

		List<Bson> update = List.of(
			Operators.set("holders", Operators.let(new Document("holders", this.getHolderPipeline(event.getGuild().getIdLong())), Operators.let(new Document("holder", Operators.filter("$$holders", Operators.eq("$$this.id", holder.getIdLong()))), Operators.concatArrays(Operators.ifNull(Operators.filter("$$holders", Operators.ne("$$this.id", holder.getIdLong())), Collections.EMPTY_LIST), List.of(Operators.mergeObjects(Operators.ifNull(Operators.first("$$holder"), defaultData), new Document("blacklisted", Operators.bitSetOr(longArray, Operators.ifNull(Operators.first(Operators.map("$$holder", "$$this.blacklisted")), Collections.EMPTY_LIST))))))))),
			Operators.setOnInsert("guildId", event.getGuild().getIdLong())
		);

		List<WriteModel<Document>> bulkData = channels.stream()
			.map(textChannel -> new UpdateOneModel<Document>(Filters.eq("channelId", textChannel.getIdLong()), update, new UpdateOptions().upsert(true)))
			.collect(Collectors.toList());

		event.getMongo().bulkWriteBlacklists(bulkData).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			int count = result.getModifiedCount() + result.getUpserts().size();
			if (count == 0) {
				event.replyFailure((commands.size() == 1 ? "That command is" :  "Those commands are") +  " already blacklisted for that " + (role ? "role" : "user") + " in those channels").queue();
				return;
			}

			event.replySuccess((commands.size() == 1 ? "That command is" :  "Those commands are") +  " now blacklisted for that " + (role ? "role" : "user") + " in **" + count + "** extra channel" + (count == 1 ? "" : "s")).queue();
		});
	}

	@Command(value="remove", description="Remove a role/user from being blacklisted from a specified command/module in a channel")
	@CommandId(180)
	@Examples({"blacklist remove #general @Shea#6653 fish", "blacklist remove #bots @Members ban"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void remove(Sx4CommandEvent event, @Argument(value="channel", nullDefault=true) @DefaultNull GuildMessageChannel channel, @Argument(value="user | role") IPermissionHolder holder, @Argument(value="command | module", endless=true) List<Sx4Command> commands) {
		List<GuildMessageChannel> channels = channel == null ? event.getGuild().getChannels().stream().filter(c -> c instanceof GuildMessageChannel).map(GuildMessageChannel.class::cast).collect(Collectors.toList()) : List.of(channel);

		boolean role = holder instanceof Role;

		BitSet bitSet = new BitSet();
		commands.stream().map(Sx4Command::getId).forEach(bitSet::set);

		List<Long> longArray = Arrays.stream(bitSet.toLongArray()).boxed().collect(Collectors.toList());

		List<Bson> update = List.of(
			Operators.set("holders", Operators.let(new Document("holders", this.getHolderPipeline(event.getGuild().getIdLong())), Operators.let(new Document("holder", Operators.filter("$$holders", Operators.eq("$$this.id", holder.getIdLong()))), Operators.cond(Operators.isEmpty("$$holder"), "$$holders", Operators.concatArrays(Operators.filter("$$holders", Operators.ne("$$this.id", holder.getIdLong())), Operators.let(new Document("result", Operators.bitSetAndNot(Operators.ifNull(Operators.first(Operators.map("$$holder", "$$this.blacklisted")), Collections.EMPTY_LIST), longArray)), Operators.cond(Operators.and(Operators.isEmpty(Operators.ifNull(Operators.first(Operators.map("$$holder", "$$this.whitelisted")), Collections.EMPTY_LIST)), Operators.bitSetIsEmpty("$$result")), Collections.EMPTY_LIST, List.of(Operators.cond(Operators.bitSetIsEmpty("$$result"), Operators.removeObject(Operators.first("$$holder"), "blacklisted"), Operators.mergeObjects(Operators.first("$$holder"), new Document("blacklisted", "$$result"))))))))))),
			Operators.setOnInsert("guildId", event.getGuild().getIdLong())
		);

		List<WriteModel<Document>> bulkData = channels.stream()
			.map(textChannel -> new UpdateOneModel<Document>(Filters.eq("channelId", textChannel.getIdLong()), update, new UpdateOptions().upsert(true)))
			.collect(Collectors.toList());

		event.getMongo().bulkWriteBlacklists(bulkData).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			int count = result.getModifiedCount() + result.getUpserts().size();
			if (count == 0) {
				event.replyFailure((commands.size() == 1 ? "That command is" :  "Those commands are") +  " not blacklisted for that " + (role ? "role" : "user") + " in those channels").queue();
				return;
			}

			event.replySuccess((commands.size() == 1 ? "That command is" :  "Those commands are") +  " no longer blacklisted for that " + (role ? "role" : "user") + " in **" + count + "** channel" + (count == 1 ? "" : "s")).queue();
		});
	}

	@Command(value="reset", description="Reset the blacklist for a specific role/user in a channel")
	@CommandId(181)
	@Examples({"blacklist reset #channel", "blacklist reset all"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void reset(Sx4CommandEvent event, @Argument(value="channel", endless=true) @AlternativeOptions("all") Alternative<GuildMessageChannel> option) {
		List<Bson> update = List.of(Operators.set("holders", Operators.reduce(Operators.ifNull("$holders", Collections.EMPTY_LIST), Collections.EMPTY_LIST, Operators.concatArrays("$$value", Operators.cond(Operators.isEmpty(Operators.ifNull(Operators.first(Operators.map(List.of("$$this"), "$$holder.whitelisted", "holder")), Collections.EMPTY_LIST)), Collections.EMPTY_LIST, List.of(Operators.removeObject("$$this", "blacklisted")))))));
		if (option.isAlternative()) {
			event.getMongo().updateManyBlacklists(Filters.eq("guildId", event.getGuild().getIdLong()), update, new UpdateOptions()).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (result.getModifiedCount() == 0) {
					event.replyFailure("Nothing was blacklisted in this server").queue();
					return;
				}

				event.replySuccess("Reset **" + result.getModifiedCount() + "** channels of their blacklist configurations").queue();
			});
		} else {
			GuildMessageChannel channel = option.getValue();
			event.getMongo().updateBlacklist(Filters.eq("channelId", channel.getIdLong()), update, new UpdateOptions()).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (result.getModifiedCount() == 0) {
					event.replyFailure("Nothing was blacklisted in that channel").queue();
					return;
				}

				event.replySuccess("That channel no longer has any blacklists").queue();
			});
		}
	}

	@Command(value="list", description="Lists the commands roles/users blacklisted from using in a specific channel")
	@CommandId(182)
	@Examples({"blacklist list", "blacklist list #channel"})
	public void list(Sx4CommandEvent event, @Argument(value="channel", nullDefault=true, endless=true) @DefaultNull GuildMessageChannel channel) {
		List<GuildMessageChannel> channels = channel == null ? event.getGuild().getChannels().stream().filter(c -> c instanceof GuildMessageChannel).map(GuildMessageChannel.class::cast).collect(Collectors.toList()) : List.of(channel);

		MessagePagedResult<GuildMessageChannel> channelPaged = new MessagePagedResult.Builder<>(event.getBot(), channels)
			.setAutoSelect(true)
			.setAuthor("Channels", null, event.getGuild().getIconUrl())
			.setDisplayFunction(Channel::getAsMention)
			.setSelectFunction(Channel::getName)
			.build();

		channelPaged.onSelect(channelSelect -> {
			GuildMessageChannel selectedChannel = channelSelect.getSelected();

			Document blacklist = event.getMongo().getBlacklist(Filters.eq("channelId", selectedChannel.getIdLong()), Projections.include("holders"));
			List<Document> defaultHolders = blacklist == null ? CheckUtility.getDefaultBlacklist(event.getGuild().getIdLong()) : CheckUtility.getCombinedBlacklist(event.getGuild().getIdLong(), blacklist.getList("holders", Document.class));

			List<Document> holders = defaultHolders.stream()
				.filter(holder -> !holder.getList("blacklisted", Long.class, Collections.emptyList()).isEmpty())
				.sorted(Comparator.comparingInt(a -> a.getInteger("type")))
				.collect(Collectors.toList());

			if (holders.isEmpty()) {
				event.replyFailure("Nothing is blacklisted in " + selectedChannel.getAsMention()).queue();
				return;
			}

			MessagePagedResult<Document> holderPaged = new MessagePagedResult.Builder<>(event.getBot(), holders)
				.setAuthor("Users/Roles", null, event.getGuild().getIconUrl())
				.setDisplayFunction(SettingUtility.getRoleUserDisplay(event, false))
				.setSelectFunction(SettingUtility.getRoleUserDisplay(event, true))
				.build();

			holderPaged.onSelect(holderSelect -> {
				Document holder = holderSelect.getSelected();
				List<Long> blacklisted = holder.getList("blacklisted", Long.class, Collections.emptyList());

				BitSet bitSet = BitSet.valueOf(blacklisted.stream().mapToLong(l -> l).toArray());

				List<Sx4Command> commands = event.getCommandListener().getAllCommands().stream()
					.map(Sx4Command.class::cast)
					.filter(command -> bitSet.get(command.getId()))
					.collect(Collectors.toList());

				MessagePagedResult<Sx4Command> commandPaged = new MessagePagedResult.Builder<>(event.getBot(), commands)
					.setAuthor("Blacklisted Commands", null, event.getGuild().getIconUrl())
					.setDisplayFunction(Sx4Command::getCommandTrigger)
					.setSelect()
					.setIndexed(false)
					.build();

				commandPaged.execute(event);
			});

			holderPaged.execute(event);
		});

		channelPaged.execute(event);
	}

}
