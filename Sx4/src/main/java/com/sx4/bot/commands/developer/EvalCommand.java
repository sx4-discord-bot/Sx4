package com.sx4.bot.commands.developer;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.option.Option;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import groovy.lang.GroovyShell;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.requests.RestAction;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EvalCommand extends Sx4Command {
	
	private final ExecutorService executor = Executors.newSingleThreadExecutor();

	private CompilerConfiguration configuration;
	{
		CompilerConfiguration parseConfiguration = new CompilerConfiguration();
		ImportCustomizer importCustomizer = new ImportCustomizer();

		importCustomizer.addStarImports("java.util.stream");

		importCustomizer.addStarImports("net.dv8tion.jda.api");
		importCustomizer.addStarImports("net.dv8tion.jda.api.entities");
		importCustomizer.addStarImports("com.sx4.bot.database.model");
		importCustomizer.addStarImports("com.mongodb.client");
		importCustomizer.addStarImports("com.mongodb.client.model");
		importCustomizer.addStarImports("org.bson");
		importCustomizer.addStarImports("okhttp3");

		parseConfiguration.addCompilationCustomizers(importCustomizer);

		this.configuration = parseConfiguration;
	}

	public EvalCommand() {
		super("eval", 0);
		
		super.setDescription("Execute some code, last line will be sent");
		super.setExamples("eval \"hi\"", "eval new EmbedBuilder().setDescription(\"hi\").build();");
		super.setAllowedArgumentParsingTypes(ArgumentParsingType.POSITIONAL);
		super.setCategoryAll(ModuleCategory.DEVELOPER);
		super.setDeveloper(true);
	}
	
	private void execute(Sx4CommandEvent event, GroovyShell shell, String evaluableString) {
		try {
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
	
	public void onCommand(Sx4CommandEvent event, @Argument(value="code", endless=true) String evaluableString, @Option(value="async") boolean async) {
		GroovyShell shell = new GroovyShell(this.configuration);

		shell.setProperty("event", event);
		shell.setProperty("JDA", event.getJDA());
		shell.setProperty("guild", event.getGuild());
		shell.setProperty("channel", event.getTextChannel());
		shell.setProperty("user", event.getAuthor());
		shell.setProperty("member", event.getMember());
		shell.setProperty("database", this.database);
		shell.setProperty("client", event.getClient());
		
		if (async) {
			this.executor.submit(() -> this.execute(event, shell, evaluableString));
		} else {
			this.execute(event, shell, evaluableString);
		}
	}
	
}
