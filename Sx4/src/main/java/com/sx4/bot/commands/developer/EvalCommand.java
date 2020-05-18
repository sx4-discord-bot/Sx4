package com.sx4.bot.commands.developer;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.Category;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;

import groovy.lang.GroovyShell;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.requests.RestAction;

public class EvalCommand extends Sx4Command {

	public EvalCommand() {
		super("eval");
		
		super.setDescription("Execute some code, last line will be sent");
		super.setExamples("eval \"hi\"", "eval new EmbedBuilder().setDescription(\"hi\").build();");
		super.setAllowedArgumentParsingTypes(ArgumentParsingType.POSITIONAL);
		super.setCategory(Category.DEVELOPER);
		super.setDeveloper(true);
	}
	
	public void onCommand(Sx4CommandEvent event, @Argument(value="code", endless=true) String evaluableString) {
		try {
			GroovyShell shell = new GroovyShell();
			
			shell.setProperty("event", event);
			shell.setProperty("JDA", event.getJDA());
			shell.setProperty("database", this.database);
			
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
	
}
