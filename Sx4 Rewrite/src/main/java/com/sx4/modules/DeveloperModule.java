package com.sx4.modules;

import static com.rethinkdb.RethinkDB.r;

import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.jockie.bot.core.Context;
import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.command.Command.Developer;
import com.jockie.bot.core.command.ICommand.ArgumentParsingType;
import com.jockie.bot.core.command.Initialize;
import com.jockie.bot.core.command.impl.CommandEvent;
import com.jockie.bot.core.command.impl.CommandImpl;
import com.jockie.bot.core.module.Module;
import com.rethinkdb.RethinkDB;
import com.rethinkdb.gen.ast.Get;
import com.rethinkdb.model.OptArgs;
import com.rethinkdb.net.Connection;
import com.sx4.categories.Categories;
import com.sx4.core.Sx4Bot;
import com.sx4.settings.Settings;
import com.sx4.utils.ArgumentUtils;
import com.sx4.utils.EconomyUtils;
import com.sx4.utils.GeneralUtils;
import com.sx4.utils.ModUtils;
import com.sx4.utils.PagedUtils;
import com.sx4.utils.TimeUtils;
import com.sx4.utils.TokenUtils;

import groovy.lang.GroovyShell;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.entities.User;

@Module
public class DeveloperModule {
	
	private static CompilerConfiguration configuration;
	
	private static String[] imports = {
		/* Sx4 */
		Sx4Bot.class.getName(),
		ArgumentUtils.class.getName(),
		GeneralUtils.class.getName(),
		ModUtils.class.getName(),
		PagedUtils.class.getName(),
		TimeUtils.class.getName(),
		TokenUtils.class.getName(),
		EconomyUtils.class.getName(),
		Settings.class.getName(),
		
		/* Java */
		Color.class.getName(),
		Pattern.class.getName(),
		RethinkDB.class.getName()
	};
	
	static {
		CompilerConfiguration parseConfiguration = new CompilerConfiguration();
		ImportCustomizer importCustomizer = new ImportCustomizer();
		importCustomizer.addImports(imports);
		
		importCustomizer.addStarImports("java.util.stream");
		
		importCustomizer.addStarImports("net.dv8tion.jda.core");
		importCustomizer.addStarImports("net.dv8tion.jda.core.entities");
		
		importCustomizer.addStaticImport("com.rethinkdb.RethinkDB", "r");
		
		parseConfiguration.addCompilationCustomizers(importCustomizer);
		
		configuration = parseConfiguration;
	}
	
	static {
	    ObjectWriter prettyWriter = new ObjectMapper().writerWithDefaultPrettyPrinter();
	    
	    String substringBound = "String.metaClass.substringBound = {int start, int end -> delegate.substring(start, Math.min(delegate.length(), end))}";
	    String toJson = "Object.metaClass.toJson = {-> '```json\\n' + prettyWriter.writeValueAsString(delegate).substringBound(0, 1985) + '```'}";
	    String toJsonCusor = "com.rethinkdb.net.Cursor.metaClass.toJson = {-> delegate.toList().toJson()}";
	    
	    GroovyShell initialize = new GroovyShell(configuration);
	    initialize.setProperty("prettyWriter", prettyWriter);
	    initialize.evaluate(substringBound);
	    initialize.evaluate(toJson);
	    initialize.evaluate(toJsonCusor);
	}
		
	@Command(value="parse", allowedArgumentParsingTypes=ArgumentParsingType.POSITIONAL, description="Execute some code, nothing will be sent unless said to")
	@Developer
	public void parse(CommandEvent event, @Context Connection connection, @Argument(value="code", endless=true) String parsableString) {
		if (parsableString == null) {
			if (event.getMessage().getAttachments().size() > 0) {
				try {
					parsableString = new String(event.getMessage().getAttachments().get(0).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
				} catch(IOException e) {
					event.reply("Failed to download file").queue();
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
			shell.setProperty("connection", connection);
				
			shell.evaluate(parsableString);
				
			event.getMessage().addReaction("✅").queue();
		} catch(Exception e) {
			event.reply(e.toString().substring(0, Math.min(e.toString().length(), 1990))).queue();
		}
	}
	
	@Command(value="eval", allowedArgumentParsingTypes=ArgumentParsingType.POSITIONAL, description="Execute some code, last line will be sent")
	@Developer
	public void eval(CommandEvent event, @Context Connection connection, @Argument(value="code", endless=true) String evaluableString) {
		try {
		    GroovyShell shell = new GroovyShell(configuration);
		    
		    shell.setProperty("event", event);
		    shell.setProperty("JDA", event.getJDA());
		    shell.setProperty("connection", connection);
		    
		    Object object = shell.evaluate(evaluableString);
		    
		    if (object == null) {
		        event.reply("null").queue();
		    } else if (object instanceof Message) {
		        event.reply((Message) object).queue();
		    } else if (object instanceof MessageEmbed) {
		        event.reply((MessageEmbed) object).queue();
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
	@Developer 
	public void blacklistUser(CommandEvent event, @Context Connection connection, @Argument(value="user", endless=true) String userArgument) {
		User user = ArgumentUtils.getUser(event.getGuild(), userArgument);
		if (user == null) {
			event.reply("I could not find that user :no_entry:").queue();
			return;
		}
		
		Get data = r.table("blacklist").get("owner");
		Map<String, List<String>> dataRan = data.run(connection);
		List<String> users = dataRan.get("users");
		if (users.contains(user.getId())) {
			event.reply("That user is no longer blacklisted <:done:403285928233402378>").queue();
			data.update(row -> r.hashMap("users", row.g("users").filter(d -> d.ne(user.getId())))).runNoReply(connection);
		} else {
			event.reply("That user is now blacklisted <:done:403285928233402378>").queue();
			data.update(row -> r.hashMap("users", row.g("users").append(user.getId()))).runNoReply(connection);
		}
	}
	
	@Command(value="transfer tax", description="Transfer the tax money to a user")
	@Developer
	public void transferTax(CommandEvent event, @Context Connection connection, @Argument(value="user", endless=true) String userArgument) {
		Member member = ArgumentUtils.getMember(event.getGuild(), userArgument);
		if (member == null) {
			event.reply("I could not find that user :no_entry:").queue();
			return;
		}
		
		EconomyUtils.insertData(member.getUser()).run(connection, OptArgs.of("durability", "soft"));
		Get taxData = r.table("tax").get("tax");
		long tax = taxData.g("tax").run(connection);
		Get data = r.table("bank").get(member.getUser().getId());
		
		data.update(row -> r.hashMap("balance", row.g("balance").add(tax))).runNoReply(connection);
		taxData.update(r.hashMap("tax", 0)).runNoReply(connection);
		
		event.reply("**" + member.getUser().getAsTag() + "** has received " + String.format("**$%,d** in tax <:done:403285928233402378>", tax)).queue();
	}
	
	@Initialize(all=true)
	public void initialize(CommandImpl command) {
	    command.setCategory(Categories.DEVELOPER);
	}
	
}
