package com.sx4.bot.commands.mod;

import java.util.function.BiConsumer;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.category.Category;
import com.sx4.bot.core.Sx4Bot;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.mod.PartialEmote;
import com.sx4.bot.http.HttpCallback;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Icon;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import okhttp3.Request;

public class CreateEmoteCommand extends Sx4Command {
	
	public CreateEmoteCommand() {
		super("create emote");
		
		super.setDescription("Creates an emote from a url, emote mention or emote name");
		super.setAliases("createemote", "ce");
		super.setExamples("create emote <:sx4:637715282995183636>", "create emote sx4", "create emote https://cdn.discordapp.com/emojis/637715282995183636.png?v=1");
		super.setCategory(Category.MODERATION);
		super.setCooldownDuration(5);
		super.setAuthorDiscordPermissions(Permission.MANAGE_EMOTES);
		super.setBotDiscordPermissions(Permission.MANAGE_EMOTES);
	}
	
	private void getBytes(String url, BiConsumer<byte[], Integer> bytes) {
		Request request = new Request.Builder()
			.url(url)
			.build();
		
		Sx4Bot.getClient().newCall(request).enqueue((HttpCallback) response -> {
			if (response.code() == 200) {
				bytes.accept(response.body().bytes(), 200);
				return;
			} else if (response.code() == 415) {
				if (url.contains(".")) {
					int periodIndex = url.lastIndexOf('.') + 1;
					String extension = url.substring(periodIndex);
					
					if (extension.equalsIgnoreCase("gif")) {
						this.getBytes(url.substring(0, periodIndex) + "png", bytes);
						return;
					}
				}
			}
				
			bytes.accept(null, response.code());
		});
	}
	
	public void onCommand(Sx4CommandEvent event, @Argument(value="emote", endless=true, acceptEmpty=true) PartialEmote emote) {
		// add checks
		
		this.getBytes(emote.getUrl(), (bytes, code) -> {
			if (bytes == null) {
				event.reply("Failed to get url from the emote argument with status code: " + code + " :no_entry:").queue();
				return;
			}
			
			event.getGuild().createEmote(emote.hasName() ? emote.getName() : "Unnamed_Emote", Icon.from(bytes))
				.flatMap(createdEmote -> event.reply(createdEmote.getAsMention() + " has been created <:done:403285928233402378>"))
				.queue(null, exception -> {
					if (exception instanceof ErrorResponseException && ((ErrorResponseException) exception).getErrorCode() == 400) {
						event.reply("You cannot created an emote larger than 256KB :no_entry:").queue();
					}
					
					System.out.println(exception.getMessage());
				});
		});
	}

}
