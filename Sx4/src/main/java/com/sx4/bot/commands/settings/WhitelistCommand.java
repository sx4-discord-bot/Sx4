package com.sx4.bot.commands.settings;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.client.model.*;
import com.sx4.bot.annotations.argument.Options;
import com.sx4.bot.annotations.command.AuthorPermissions;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.model.Operators;
import com.sx4.bot.entities.argument.Option;
import com.sx4.bot.entities.settings.HolderType;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.ExceptionUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.IPermissionHolder;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.*;
import java.util.stream.Collectors;

public class WhitelistCommand extends Sx4Command {

	public WhitelistCommand() {
		super("whitelist", 183);

		super.setDescription("Whitelist roles/users from being able to use specific commands/modules in channels, this only works in correlation with blacklist");
		super.setExamples("whitelist add", "whitelist remove", "whitelist list");
		super.setCategoryAll(ModuleCategory.SETTINGS);
	}

	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}

	@Command(value="add", description="Add a role/user to be whitelisted from a specified command/module in a channel")
	@CommandId(184)
	@Examples({"whitelist add #general @Shea#6653 fish", "whitelist add #bots @Members ban"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void add(Sx4CommandEvent event, @Argument(value="channel") TextChannel channel, @Argument(value="user | role") IPermissionHolder holder, @Argument(value="command | module", endless=true) List<Sx4Command> commands) {
		boolean role = holder instanceof Role;
		int type = role ? HolderType.ROLE.getType() : HolderType.USER.getType();

		BitSet bitSet = new BitSet();
		commands.stream().map(Sx4Command::getId).forEach(bitSet::set);

		Document defaultData = new Document("id", holder.getIdLong())
			.append("type", type)
			.append("whitelisted", Collections.EMPTY_LIST);

		List<Long> longArray = Arrays.stream(bitSet.toLongArray()).boxed().collect(Collectors.toList());

		List<Bson> update = List.of(
			Operators.set("blacklist.holders", Operators.let(new Document("holders", Operators.ifNull("$blacklist.holders", Collections.EMPTY_LIST)), Operators.let(new Document("holder", Operators.filter("$$holders", Operators.eq("$$this.id", holder.getIdLong()))), Operators.concatArrays(Operators.ifNull(Operators.filter("$$holders", Operators.ne("$$this.id", holder.getIdLong())), Collections.EMPTY_LIST), List.of(Operators.mergeObjects(Operators.ifNull(Operators.first("$$holder"), defaultData), new Document("whitelisted", Operators.bitSetOr(longArray, Operators.ifNull(Operators.first(Operators.map("$$holder", "$$this.whitelisted")), Collections.EMPTY_LIST))))))))),
			Operators.setOnInsert("guildId", event.getGuild().getIdLong())
		);

		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).upsert(true).projection(Projections.include("blacklist.holders"));
		this.database.findAndUpdateChannelById(channel.getIdLong(), update, options).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			List<Document> oldHolders = data == null ? Collections.emptyList() : data.getEmbedded(List.of("blacklist", "holders"), Collections.emptyList());
			Document oldHolder = oldHolders.stream()
				.filter(d -> d.getLong("id") == holder.getIdLong())
				.findFirst()
				.orElse(null);

			long[] oldLongArray = oldHolder == null ? new long[0] : oldHolder.getList("whitelisted", Long.class, Collections.emptyList()).stream().mapToLong(l -> l).toArray();

			BitSet oldBitSet = BitSet.valueOf(oldLongArray);
			oldBitSet.and(bitSet);

			if (oldBitSet.equals(bitSet)) {
				event.replyFailure((commands.size() == 1 ? "That command is" :  "Those commands are") +  " already whitelisted for that " + (role ? "role" : "user") + " in " +  channel.getAsMention()).queue();
				return;
			}

			event.replySuccess((commands.size() == 1 ? "That command is" :  "Those commands are") +  " now whitelisted for that " + (role ? "role" : "user") + " in " +  channel.getAsMention()).queue();
		});
	}

	@Command(value="remove", description="Remove a role/user from being whitelisted from a specified command/module in a channel")
	@CommandId(185)
	@Examples({"whitelist remove #general @Shea#6653 fish", "whitelist remove #bots @Members ban"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void remove(Sx4CommandEvent event, @Argument(value="channel") TextChannel channel, @Argument(value="user | role") IPermissionHolder holder, @Argument(value="command | module", endless=true) List<Sx4Command> commands) {
		boolean role = holder instanceof Role;

		BitSet bitSet = new BitSet();
		commands.stream().map(Sx4Command::getId).forEach(bitSet::set);

		List<Long> longArray = Arrays.stream(bitSet.toLongArray()).boxed().collect(Collectors.toList());

		List<Bson> update = List.of(Operators.set("blacklist.holders", Operators.let(new Document("holder", Operators.filter("$blacklist.holders", Operators.eq("$$this.id", holder.getIdLong()))), Operators.cond(Operators.or(Operators.extinct("$blacklist.holders"), Operators.isEmpty("$$holder")), "$blacklist.holders", Operators.concatArrays(Operators.filter("$blacklist.holders", Operators.ne("$$this.id", holder.getIdLong())), Operators.let(new Document("result", Operators.bitSetAndNot(Operators.ifNull(Operators.first(Operators.map("$$holder", "$$this.whitelisted")), Collections.EMPTY_LIST), longArray)), Operators.cond(Operators.and(Operators.isEmpty(Operators.ifNull(Operators.first(Operators.map("$$holder", "$$this.blacklisted")), Collections.EMPTY_LIST)), Operators.bitSetIsEmpty("$$result")), Collections.EMPTY_LIST, List.of(Operators.cond(Operators.bitSetIsEmpty("$$result"), Operators.removeObject(Operators.first("$$holder"), "whitelisted"), Operators.mergeObjects(Operators.first("$$holder"), new Document("whitelisted", "$$result")))))))))));

		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.include("blacklist.holders"));
		this.database.findAndUpdateChannelById(channel.getIdLong(), update, options).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			List<Document> oldHolders = data == null ? Collections.emptyList() : data.getEmbedded(List.of("blacklist", "holders"), Collections.emptyList());
			Document oldHolder = oldHolders.stream()
				.filter(d -> d.getLong("id") == holder.getIdLong())
				.findFirst()
				.orElse(null);

			if (oldHolder == null) {
				event.replyFailure((commands.size() == 1 ? "That command is" :  "Those commands are") +  " not whitelisted for that " + (role ? "role" : "user") + " in " +  channel.getAsMention()).queue();
				return;
			}

			long[] oldLongArray = oldHolder.getList("whitelisted", Long.class, Collections.emptyList()).stream().mapToLong(l -> l).toArray();

			BitSet oldBitSet = BitSet.valueOf(oldLongArray);
			oldBitSet.and(bitSet);

			if (oldBitSet.isEmpty()) {
				event.replyFailure((commands.size() == 1 ? "That command is" :  "Those commands are") +  " not whitelisted for that " + (role ? "role" : "user") + " in " +  channel.getAsMention()).queue();
				return;
			}

			event.replySuccess((commands.size() == 1 ? "That command is" :  "Those commands are") +  " no longer whitelisted for that " + (role ? "role" : "user") + " in " +  channel.getAsMention()).queue();
		});
	}

	@Command(value="reset", description="Reset the whitelist for a specific role/user in a channel")
	@CommandId(186)
	@Examples({"whitelist reset #channel", "whitelist reset all"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void reset(Sx4CommandEvent event, @Argument(value="channel", endless=true) @Options("all") Option<TextChannel> option) {
		List<Bson> update = List.of(Operators.set("blacklist", Operators.cond(Operators.extinct("$blacklist.holders"), Operators.REMOVE, new Document("holders", Operators.reduce("$blacklist.holders", Collections.EMPTY_LIST, Operators.concatArrays("$$value", Operators.cond(Operators.isEmpty(Operators.ifNull(Operators.first(Operators.map(List.of("$$this"), "$$holder.blacklisted", "holder")), Collections.EMPTY_LIST)), Collections.EMPTY_LIST, List.of(Operators.removeObject("$$this", "whitelisted")))))))));
		if (option.isAlternative()) {
			this.database.updateManyChannels(Filters.eq("guildId", event.getGuild().getIdLong()), update, new UpdateOptions()).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (result.getModifiedCount() == 0) {
					event.replyFailure("Nothing was whitelisted in this server").queue();
					return;
				}

				event.replySuccess("Reset **" + result.getModifiedCount() + "** channels of their whitelist configurations").queue();
			});
		} else {
			TextChannel channel = option.getValue();
			this.database.updateChannelById(channel.getIdLong(), update, new UpdateOptions()).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (result.getModifiedCount() == 0) {
					event.replyFailure("Nothing was whitelisted in that channel").queue();
					return;
				}

				event.replySuccess("That channel no longer has any whitelists").queue();
			});
		}
	}

	@Command(value="list", description="Lists the commands roles/users whitelisted from using in a specific channel")
	@CommandId(187)
	@Examples({"whitelist list", "whitelist list #channel"})
	public void list(Sx4CommandEvent event, @Argument(value="channel", nullDefault=true, endless=true) TextChannel channel) {
		List<TextChannel> channels = channel == null ? event.getGuild().getTextChannels() : List.of(channel);

		PagedResult<TextChannel> channelPaged = new PagedResult<>(channels)
			.setAutoSelect(true)
			.setAuthor("Channels", null, event.getGuild().getIconUrl())
			.setDisplayFunction(TextChannel::getAsMention);

		channelPaged.onSelect(channelSelect -> {
			TextChannel selectedChannel = channelSelect.getSelected();
			List<Document> holders = this.database.getChannelById(selectedChannel.getIdLong(), Projections.include("blacklist.holders")).getEmbedded(List.of("blacklist", "holders"), Collections.emptyList());

			holders = holders.stream()
				.filter(holder -> !holder.getList("whitelisted", Long.class, Collections.emptyList()).isEmpty())
				.sorted(Comparator.comparingInt(a -> a.getInteger("type")))
				.collect(Collectors.toList());

			if (holders.isEmpty()) {
				event.replyFailure("Nothing is whitelisted in " + selectedChannel.getAsMention()).queue();
				return;
			}

			PagedResult<Document> holderPaged = new PagedResult<>(holders)
				.setAuthor("Users/Roles", null, event.getGuild().getIconUrl())
				.setDisplayFunction(holder -> {
					long id = holder.getLong("id");
					int type = holder.getInteger("type");
					if (type == HolderType.ROLE.getType()) {
						Role role = event.getGuild().getRoleById(id);
						return role == null ? "Deleted Role (" + id + ")" : role.getAsMention();
					} else {
						User user = event.getShardManager().getUserById(id);
						return user == null ? "Unknown User (" + id + ")" : user.getAsTag();
					}
				});

			holderPaged.onSelect(holderSelect -> {
				Document holder = holderSelect.getSelected();
				List<Long> whitelisted = holder.getList("whitelisted", Long.class, Collections.emptyList());

				BitSet bitSet = BitSet.valueOf(whitelisted.stream().mapToLong(l -> l).toArray());

				List<Sx4Command> commands = event.getCommandListener().getAllCommands().stream()
					.map(Sx4Command.class::cast)
					.filter(command -> bitSet.get(command.getId()))
					.collect(Collectors.toList());

				PagedResult<Sx4Command> commandPaged = new PagedResult<>(commands)
					.setAuthor("Whitelisted Commands", null, event.getGuild().getIconUrl())
					.setDisplayFunction(Sx4Command::getCommandTrigger)
					.setSelect()
					.setIndexed(false);

				commandPaged.execute(event);
			});

			holderPaged.execute(event);
		});

		channelPaged.execute(event);
	}

}
