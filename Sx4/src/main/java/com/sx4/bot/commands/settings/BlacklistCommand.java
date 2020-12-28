package com.sx4.bot.commands.settings;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.command.Command.AuthorPermissions;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReturnDocument;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.model.Operators;
import com.sx4.bot.entities.settings.HolderType;
import com.sx4.bot.utility.ExceptionUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.IPermissionHolder;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class BlacklistCommand extends Sx4Command {

	public BlacklistCommand() {
		super("blacklist", 167);
		
		super.setDescription("Blacklist roles/users from being able to use specific commands/modules in channels");
		super.setExamples("blacklist add", "blacklist remove", "blacklist info");
	}
	
	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}
	
	@Command(value="add", description="Add a role/user to be blacklisted from a specified command/module in a channel")
	@CommandId(168)
	@Examples({"blacklist add #general @Shea#6653 fish", "blacklist add #bots @Members ban"})
	@AuthorPermissions({Permission.MANAGE_SERVER})
	public void add(Sx4CommandEvent event, @Argument(value="channel") TextChannel channel, @Argument(value="user | role") IPermissionHolder holder, @Argument(value="command | module", endless=true) List<Sx4Command> commands) {
		boolean role = holder instanceof Role;
		int type = role ? HolderType.ROLE.getType() : HolderType.USER.getType();

		BitSet bitSet = new BitSet();
		commands.stream().map(Sx4Command::getId).forEach(bitSet::set);

		Document defaultData = new Document("id", holder.getIdLong())
			.append("type", type)
			.append("blacklisted", Collections.EMPTY_LIST);

		List<Long> longArray = Arrays.stream(bitSet.toLongArray()).boxed().collect(Collectors.toList());

		List<Bson> update = List.of(
			Operators.set("blacklist.holders", Operators.let(new Document("holders", Operators.ifNull("$blacklist.holders", Collections.EMPTY_LIST)), Operators.let(new Document("holder", Operators.filter("$$holders", Operators.eq("$$this.id", holder.getIdLong()))), Operators.concatArrays(Operators.ifNull(Operators.filter("$$holders", Operators.ne("$$this.id", holder.getIdLong())), Collections.EMPTY_LIST), List.of(Operators.mergeObjects(Operators.ifNull(Operators.first("$$holder"), defaultData), new Document("blacklisted", Operators.bitSetOr(longArray, Operators.ifNull(Operators.first(Operators.map("$$holder", "$$this.blacklisted")), Collections.EMPTY_LIST))))))))),
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

			long[] oldLongArray = oldHolder == null ? new long[0] : oldHolder.getList("blacklisted", Long.class, Collections.emptyList()).stream().mapToLong(l -> l).toArray();

			BitSet oldBitSet = BitSet.valueOf(oldLongArray);
			oldBitSet.and(bitSet);

			if (oldBitSet.equals(bitSet)) {
				event.replyFailure((commands.size() == 1 ? "That command is" :  "Those commands are") +  " already blacklisted for that " + (role ? "role" : "user") + " in " +  channel.getAsMention()).queue();
				return;
			}

			event.replySuccess((commands.size() == 1 ? "That command is" :  "Those commands are") +  " now blacklisted for that " + (role ? "role" : "user") + " in " +  channel.getAsMention()).queue();
		});
	}

	@Command(value="remove", description="Remove a role/user from being blacklisted from a specified command/module in a channel")
	@CommandId(180)
	@Examples({"blacklist remove #general @Shea#6653 fish", "blacklist remove #bots @Members ban"})
	@AuthorPermissions({Permission.MANAGE_SERVER})
	public void remove(Sx4CommandEvent event, @Argument(value="channel") TextChannel channel, @Argument(value="user | role") IPermissionHolder holder, @Argument(value="command | module", endless=true) List<Sx4Command> commands) {
		boolean role = holder instanceof Role;

		BitSet bitSet = new BitSet();
		commands.stream().map(Sx4Command::getId).forEach(bitSet::set);

		List<Long> longArray = Arrays.stream(bitSet.toLongArray()).boxed().collect(Collectors.toList());

		List<Bson> update = List.of(Operators.set("blacklist.holders", Operators.let(new Document("holder", Operators.filter("$blacklist.holders", Operators.eq("$$this.id", holder.getIdLong()))), Operators.cond(Operators.or(Operators.extinct("$blacklist.holders"), Operators.isEmpty("$$holder")), "$blacklist.holders", Operators.concatArrays(Operators.filter("$blacklist.holders", Operators.ne("$$this.id", holder.getIdLong())), Operators.let(new Document("result", Operators.bitSetClear(Operators.first(Operators.map("$$holder", "$$this.blacklisted")), longArray)), Operators.cond(Operators.and(Operators.isEmpty(Operators.ifNull(Operators.first(Operators.map("$$holder", "$$this.whitelisted")), Collections.EMPTY_LIST)), Operators.bitSetIsEmpty("$$result")), Collections.EMPTY_LIST, List.of(Operators.cond(Operators.bitSetIsEmpty("$$result"), Operators.removeObject(Operators.first("$$holder"), "blacklisted"), Operators.mergeObjects(Operators.first("$$holder"), new Document("blacklisted", "$$result")))))))))));

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
				event.replyFailure((commands.size() == 1 ? "That command is" :  "Those commands are") +  " not blacklisted for that " + (role ? "role" : "user") + " in " +  channel.getAsMention()).queue();
				return;
			}

			long[] oldLongArray = oldHolder.getList("blacklisted", Long.class, Collections.emptyList()).stream().mapToLong(l -> l).toArray();

			BitSet oldBitSet = BitSet.valueOf(oldLongArray);
			oldBitSet.and(bitSet);

			if (oldBitSet.isEmpty()) {
				event.replyFailure((commands.size() == 1 ? "That command is" :  "Those commands are") +  " not blacklisted for that " + (role ? "role" : "user") + " in " +  channel.getAsMention()).queue();
				return;
			}

			event.replySuccess((commands.size() == 1 ? "That command is" :  "Those commands are") +  " no longer blacklisted for that " + (role ? "role" : "user") + " in " +  channel.getAsMention()).queue();
		});
	}

}
