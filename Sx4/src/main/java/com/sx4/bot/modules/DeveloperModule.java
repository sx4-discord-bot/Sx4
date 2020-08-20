package com.sx4.bot.modules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.category.ICategory;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.command.Command.Developer;
import com.jockie.bot.core.command.Context;
import com.jockie.bot.core.command.ICommand;
import com.jockie.bot.core.command.ICommand.ArgumentParsingType;
import com.jockie.bot.core.command.ICommand.ContentOverflowPolicy;
import com.jockie.bot.core.command.Initialize;
import com.jockie.bot.core.command.impl.CommandEvent;
import com.jockie.bot.core.command.impl.CommandImpl;
import com.jockie.bot.core.module.Module;
import com.mongodb.client.model.*;
import com.sx4.bot.categories.Categories;
import com.sx4.bot.core.Sx4Bot;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEventListener;
import com.sx4.bot.database.Database;
import com.sx4.bot.economy.Item;
import com.sx4.bot.interfaces.Examples;
import com.sx4.bot.logger.Statistics;
import com.sx4.bot.logger.handler.EventHandler;
import com.sx4.bot.logger.util.Utils;
import com.sx4.bot.utils.ArgumentUtils;
import com.sx4.bot.utils.GeneralUtils;
import com.sx4.bot.utils.HelpUtils;
import groovy.lang.GroovyShell;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.internal.entities.AbstractMessage;
import net.dv8tion.jda.internal.entities.ReceivedMessage;
import org.bson.Document;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.json.JSONObject;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Module
public class DeveloperModule {
	
	private static CompilerConfiguration configuration;
	
	private static String[] imports = {
		Color.class.getName(),
		Pattern.class.getName()
	};
	
	static {
		CompilerConfiguration parseConfiguration = new CompilerConfiguration();
		ImportCustomizer importCustomizer = new ImportCustomizer();
		importCustomizer.addImports(imports);
		
		importCustomizer.addStarImports("java.util.stream");
		
		importCustomizer.addStarImports("net.dv8tion.jda.api");
		importCustomizer.addStarImports("net.dv8tion.jda.api.entities");
		importCustomizer.addStarImports("com.sx4.bot.events");
		importCustomizer.addStarImports("com.sx4.bot.economy.materials");
		importCustomizer.addStarImports("com.sx4.bot.economy.items");
		importCustomizer.addStarImports("com.sx4.bot.economy.tools");
		importCustomizer.addStarImports("com.sx4.bot.economy.upgrades");
		importCustomizer.addStarImports("com.sx4.bot.economy");
		importCustomizer.addStarImports("com.sx4.bot.logger");
		importCustomizer.addStarImports("com.sx4.bot.utils");
		importCustomizer.addStarImports("com.sx4.bot.modules");
		importCustomizer.addStarImports("com.sx4.bot.core");
		importCustomizer.addStarImports("com.mongodb.client");
		importCustomizer.addStarImports("com.mongodb.client.model");
		importCustomizer.addStarImports("org.bson");
		importCustomizer.addStarImports("okhttp3");
		
		parseConfiguration.addCompilationCustomizers(importCustomizer);
		
		configuration = parseConfiguration;
	}
	
	static {
		ObjectWriter prettyWriter = new ObjectMapper().writerWithDefaultPrettyPrinter();
		
		String substringBound = "String.metaClass.substringBound = {int start, int end -> delegate.substring(start, Math.min(delegate.length(), end))}";
		String toJson = "Object.metaClass.toJSON = {int page = 1 -> '```json\\n' + prettyWriter.writeValueAsString(delegate).substringBound(1985 * (page - 1), 1985 * page) + '```'}";
		
		GroovyShell initialize = new GroovyShell(configuration);
		initialize.setProperty("prettyWriter", prettyWriter);
		initialize.evaluate(substringBound);
		initialize.evaluate(toJson);
	}
		
	@Command(value="parse", allowedArgumentParsingTypes=ArgumentParsingType.POSITIONAL, description="Execute some code, nothing will be sent unless said to")
	@Examples({"parse event.getTextChannel().sendMessage(\"hi\").queue();", "parse database.updateUserById(402557516728369153, Updates.set(\"economy.balance\", 0L));"})
	@Developer
	public void parse(CommandEvent event, @Context Database database, @Argument(value="code", endless=true, nullDefault=true) String parsableString) throws InterruptedException, ExecutionException {
		if (parsableString == null) {
			if (event.getMessage().getAttachments().size() > 0) {
				try {
					parsableString = new String(event.getMessage().getAttachments().get(0).retrieveInputStream().get().readAllBytes(), StandardCharsets.UTF_8);
				} catch(IOException e) {
					event.reply("Failed to download file").queue();
					return;
				}
			} else {
				event.reply("No files attached").queue();
					
				return;
			}
		}
			
		try {
			GroovyShell shell = new GroovyShell(configuration);
			shell.setProperty("event", event);
			shell.setProperty("JDA", event.getJDA());
			shell.setProperty("database", database);
				
			shell.evaluate(parsableString);
				
			event.getMessage().addReaction("✅").queue();
		} catch(Exception e) {
			event.reply(e.toString().substring(0, Math.min(e.toString().length(), 1990))).queue();
		}
	}
	
	@Command(value="eval", allowedArgumentParsingTypes=ArgumentParsingType.POSITIONAL, description="Execute some code, last line will be sent")
	@Examples({"eval \"hi\"", "eval new EmbedBuilder().setDescription(\"hi\").build();"})
	@Developer
	public void eval(CommandEvent event, @Context Database database, @Argument(value="code", endless=true) String evaluableString) {
		try {
			GroovyShell shell = new GroovyShell(configuration);
			
			shell.setProperty("event", event);
			shell.setProperty("JDA", event.getJDA());
			shell.setProperty("database", database);
			
			Object object = shell.evaluate(evaluableString);
			
			if (object == null) {
				event.reply("null").queue();
			} else if (object instanceof Message) {
				event.reply((Message) object).queue();
			} else if (object instanceof MessageEmbed) {
				event.reply((MessageEmbed) object).queue();
			} else if (object instanceof RestAction) {
				((RestAction<?>) object).queue();
			} else {
				event.reply(object.toString()).queue();
			}
		} catch(Exception e) {
			if (e.getMessage() != null) {
				event.reply(e.toString()).queue();
			} else {
				event.reply(e.getClass().getName() + " [No error message]").queue();
			}
		}
	}
	
	@Command(value="blacklist user", description="Blacklist a user from the bot")
	@Examples({"blacklist user @Shea#6653 Economy", "blacklist user 402557516728369153 all", "blacklist user Shea ship"})
	@Developer 
	public void blacklistUser(CommandEvent event, @Context Database database, @Argument(value="user") String userArgument, @Argument(value="commands", endless=true) String commandsArgument) {
		User user = ArgumentUtils.getUser(userArgument);
		if (user == null) {
			event.reply("I could not find that user :no_entry:").queue();
			return;
		}

		Collection<ICommand> commands = null;
		if (commandsArgument.equals("all")) {
			commands = event.getCommandListener().getAllCommands();
		} else {
			ICategory category = ArgumentUtils.getModule(commandsArgument);
			if (category != null) {
				commands = category.getCommands();
			} else {
				ICommand command = ArgumentUtils.getCommand(commandsArgument);
				if (command != null) {
					commands = List.of(command);
				}
			}
		}

		if (commands == null) {
			event.reply("I could not find that command :no_entry:").queue();
			return;
		}

		List<String> names = commands.stream()
			.map(command -> command.getCommandTrigger())
			.collect(Collectors.toList());

		database.updateUserById(user.getIdLong(), Updates.addEachToSet("blacklisted", names), (result, exception) -> {
			if (exception != null) {
				exception.printStackTrace();
				event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
			} else {
				event.reply("That user is now blacklisted from those commands <:done:403285928233402378>").queue();
			}
		});
	}

	@Command(value="blacklist user reset", description="Unblacklist a user from the bot")
	@Examples({"blacklist user reset @Shea#6653", "blacklist user reset 402557516728369153", "blacklist user reset Shea"})
	@Developer
	public void blacklistUserReset(CommandEvent event, @Context Database database, @Argument(value="user", endless=true) String userArgument) {
		User user = ArgumentUtils.getUser(userArgument);
		if (user == null) {
			event.reply("I could not find that user :no_entry:").queue();
			return;
		}

		database.updateUserById(user.getIdLong(), Updates.unset("blacklisted"), (result, exception) -> {
			if (exception != null) {
				exception.printStackTrace();
				event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
			} else {
				event.reply("That user is no longer blacklisted from any commands <:done:403285928233402378>").queue();
			}
		});
	}

	@Command(value="as", description="Execute a command as someone else")
	@Examples({"as Joakim fish", "as Joakim#9814 reminder add something in 10 minutes"})
	@Developer
	public void as(CommandEvent event, @Argument(value="user") String userArgument, @Argument(value="command and arguments", endless=true) String commandAndArguments) {
		Member member = ArgumentUtils.getMember(event.getGuild(), userArgument);
		if (member == null) {
			event.reply("I could not find that user :no_entry:").queue();
			return;
		}
		
		Field user;
		try {
			user = ReceivedMessage.class.getDeclaredField("author");
		} catch (NoSuchFieldException | SecurityException e) {
			event.reply("Failed to get the author field in ReceivedMessage :no_entry:").queue();
			return;
		}
		
		user.setAccessible(true);
		
		try {
			user.set(event.getMessage(), member.getUser());
		} catch (IllegalArgumentException | IllegalAccessException e) {
			event.reply("Failed to set the author field in the current message :no_entry:").queue();
			return;
		}
		
		Field content;
		try {
			content = AbstractMessage.class.getDeclaredField("content");
		} catch (NoSuchFieldException | SecurityException e) {
			event.reply("Failed to get the content field in AbstractMessage :no_entry:").queue();
			return;
		}
		
		content.setAccessible(true);
		
		try {
			content.set(event.getMessage(), event.getSelfUser().getAsMention() + " " + commandAndArguments);
		} catch (IllegalArgumentException | IllegalAccessException e) {
			event.reply("Failed to set the content field in the current message :no_entry:").queue();
			return;
		}
		
		event.getCommandListener().handle(event.getMessage());
	}
	
	@Command(value="transfer tax", description="Transfer the tax money to a user")
	@Examples({"transfer tax"})
	@Developer
	public void transferTax(CommandEvent event, @Context Database database, @Argument(value="user", endless=true) String userArgument) {
		Member member = ArgumentUtils.getMember(event.getGuild(), userArgument);
		if (member == null) {
			event.reply("I could not find that user :no_entry:").queue();
			return;
		}
		
		long tax = database.getUserById(event.getSelfUser().getIdLong(), null, Projections.include("economy.balance")).getEmbedded(List.of("economy", "balance"), 0L);
		
		UpdateOptions updateOptions = new UpdateOptions().upsert(true);
		List<WriteModel<Document>> bulkData = List.of(
				new UpdateOneModel<>(Filters.eq("_id", member.getIdLong()), Updates.inc("economy.balance", tax), updateOptions),
				new UpdateOneModel<>(Filters.eq("_id", event.getSelfUser().getIdLong()), Updates.set("economy.balance", 0L), updateOptions)
		);
		
		database.bulkWriteUsers(bulkData, (result, exception) -> {
			if (exception != null) {
				exception.printStackTrace();
				event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
			} else {
				event.reply("**" + member.getUser().getAsTag() + "** has received " + String.format("**$%,d** in tax <:done:403285928233402378>", tax)).queue();
			}
		});
	}
	
	@Command(value="logging stats", description="Sends logger stats", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"logging stats"})
	@Developer
	public void loggerStats(CommandEvent event) {
		event.reply(Statistics.getStatistics()).queue();
	}
	
	@Command(value="logging queue", description="Send the logger queue", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@Examples({"logging queue"})
	@Developer
	public void loggerQueue(CommandEvent event) {
		StringBuilder message = new StringBuilder();
		
		Map<Long, BlockingDeque<EventHandler.Request>> queue = Sx4Bot.getEventHandler().getQueue();
		
		List<Long> mostQueued = queue.keySet().stream()
			.sorted((key, key2) -> -Integer.compare(queue.get(key).size(), queue.get(key2).size()))
			.limit(10)
			.collect(Collectors.toList());
		
		for(long guildId : mostQueued) {
			int queued = queue.get(guildId).size();
			if(queued > 0) {
				Guild guild = Sx4Bot.getShardManager().getGuildById(guildId);
				if(guild != null) {
					message.append('\n').append(guild.getName() + " (" + guildId + ") - " + queued);
				}else{
					message.append('\n').append("Unknown guild (" + guildId + ") - " + queued);
				}
			}
		}
		
		message.append('\n').append("Total queued requests: " + Sx4Bot.getEventHandler().getTotalRequestsQueued());

		event.reply(Utils.getMessageSeperated(message)).queue();
	}
	
	@Command(value="advertisement description", aliases={"ad description"}, description="Updates the description for the sponsor on the help menu")
	@Examples({"advertisement description Check out said sponsor here"})
	@Developer
	public void advertisementDescription(CommandEvent event, @Argument(value="description", endless=true) String description) {
		JSONObject updatedData;
		if (description.toLowerCase().equals("default")) {
			updatedData = HelpUtils.updateAdvertisementDescription(JSONObject.NULL);
		} else {
			updatedData = HelpUtils.updateAdvertisementDescription(description);
		}
		
		try (FileOutputStream file = new FileOutputStream(new File("./advertisement.json"))) { 
            file.write(updatedData.toString().getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
		
		event.reply("Done").queue();
	}
	
	@Command(value="advertisement banner", aliases={"ad banner"}, description="Updates the banner for the sponsor on the help menu")
	@Examples({"advertisement banner https://cdn.discordapp.com/attachments/344091594972069888/634867715860987914/Sponsor.png"})
	@Developer
	public void advertisementBanner(CommandEvent event, @Argument(value="banner", endless=true) String banner) {
		JSONObject updatedData;
		if (banner.toLowerCase().equals("default")) {
			updatedData = HelpUtils.updateAdvertisementImage(JSONObject.NULL);
		} else {
			updatedData = HelpUtils.updateAdvertisementImage(banner);
		}
		
		try (FileOutputStream file = new FileOutputStream(new File("./advertisement.json"))) { 
		    file.write(updatedData.toString().getBytes());
		} catch (IOException e) {
		    e.printStackTrace();
		}
		
		event.reply("Done").queue();
	}
	
	@Command(value="disable command", aliases={"disable"}, description="Disables a command from being used")
	@Examples({"disable command fish", "disable command fish Fish has temporarily been disabled due to an exploitable bug :no_entry:"})
	@Developer
	public void disableCommand(CommandEvent event, @Argument(value="command") String commandName, @Argument(value="message", endless=true, nullDefault=true) String message) {
		Sx4Command command = ArgumentUtils.getCommand(commandName);
		if (command == null) {
			event.reply("I could not find that command :no_entry:").queue();
			return;
		}
		
		if (!command.isDisabled()) {
			command.disable(message);
			event.reply("`" + command.getCommandTrigger() + "` has been disabled <:done:403285928233402378>").queue();
		} else {
			command.enable();
			event.reply("`" + command.getCommandTrigger() + "` has been enabled <:done:403285928233402378>").queue();
		}
	}
	
	@Command(value="economy override add", description="Adds an override to an item in the economy")
	@Examples({"economy override add Snowflake hidden true", "economy override add Pumpkin price 10"})
	@Developer
	public void economyOverrideAdd(CommandEvent event, @Argument(value="item") String itemName, @Argument(value="field") String field, @Argument(value="value") String valueArgument) {
		Object value;
		
		valueArgument = valueArgument.toLowerCase();
		if (valueArgument.equals("true") || valueArgument.equals("false")) {
			value = Boolean.parseBoolean(valueArgument);
		} else if (GeneralUtils.isNumberUnsigned(valueArgument)) {
			value = Long.parseLong(valueArgument);
		} else {
			value = valueArgument;
		}
		
		Item item = Item.getItemByName(itemName);
		if (item == null) {
			event.reply("I could not find that item :no_entry:").queue();
			return;
		}
		
		File economyOverride = new File("economyOverride.json");
		
		try (FileInputStream stream = new FileInputStream(economyOverride)) {
			JSONObject json = new JSONObject(new String(stream.readAllBytes()));
			if (json.has(item.getName())) {
				JSONObject details = json.getJSONObject(item.getName());
				details.put(field, value);
			} else {
				json.put(item.getName(), new JSONObject().put(field, value));
			}
			
			try (FileOutputStream file = new FileOutputStream(economyOverride)) {
				Item.loadConfig(json);
				file.write(json.toString().getBytes());
				
				event.reply("Added override for `" + item.getName() + "` <:done:403285928233402378>").queue();
			} catch (IOException e) {
				event.reply(Sx4CommandEventListener.getUserErrorMessage(e)).queue();
			}
		} catch (IOException e) {
			event.reply(Sx4CommandEventListener.getUserErrorMessage(e)).queue();
		}
	}
	
	@Command(value="economy override remove", description="Removes an override added to an item in the economy")
	@Examples({"economy override remove Snowflake hidden", "economy override remove Pumpkin price"})
	@Developer
	public void economyOverrideRemove(CommandEvent event, @Argument(value="item") String itemName, @Argument(value="field") String field) {
		Item item = Item.getItemByName(itemName);
		if (item == null) {
			event.reply("I could not find that item :no_entry:").queue();
			return;
		}
		
		File economyOverride = new File("economyOverride.json");
		
		try (FileInputStream stream = new FileInputStream(economyOverride)) {
			JSONObject json = new JSONObject(new String(stream.readAllBytes()));
			if (json.has(item.getName())) {
				JSONObject details = json.getJSONObject(item.getName());
				
				if (details.has(field)) {
					details.remove(field);
				} else {
					event.reply("That item doesn't have an override for `" + field + "` :no_entry:").queue();
					return;
				}
			} else {
				event.reply("That item doesn't have an override for `" + field + "` :no_entry:").queue();
				return;
			}
			
			try (FileOutputStream file = new FileOutputStream(economyOverride)) {
				Item.loadConfig(json);
				file.write(json.toString().getBytes());
				
				event.reply("Removed override for `" + item.getName() + "` <:done:403285928233402378>").queue();
			} catch (IOException e) {
				event.reply(Sx4CommandEventListener.getUserErrorMessage(e)).queue();
			}
		} catch (IOException e) {
			event.reply(Sx4CommandEventListener.getUserErrorMessage(e)).queue();
		}
	}
	
	
	@Initialize(all=true, subCommands=true, recursive=true)
	public void initialize(CommandImpl command) {
		command.setCategory(Categories.DEVELOPER);
	}
	
}
