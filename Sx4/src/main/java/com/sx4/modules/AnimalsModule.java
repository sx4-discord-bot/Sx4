package com.sx4.modules;

import org.json.JSONObject;

import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.command.Command.BotPermissions;
import com.jockie.bot.core.command.ICommand.ContentOverflowPolicy;
import com.jockie.bot.core.command.Initialize;
import com.jockie.bot.core.command.impl.CommandEvent;
import com.jockie.bot.core.command.impl.CommandImpl;
import com.jockie.bot.core.module.Module;
import com.sx4.categories.Categories;
import com.sx4.core.Sx4Bot;
import com.sx4.interfaces.Sx4Callback;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.Permission;
import okhttp3.Request;

@Module
public class AnimalsModule {
	
	@Command(value="cat fact", aliases={"catfact"}, description="Gives you a random cat fact", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void catFact(CommandEvent event) {
		Request request = new Request.Builder().url("https://catfact.ninja/fact").build();
		
		Sx4Bot.client.newCall(request).enqueue((Sx4Callback) response -> {
			JSONObject json = new JSONObject(response.body().string());
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setTitle("Did you know?");
			embed.setDescription(json.getString("fact"));
			embed.setColor(event.getMember().getColor());
			embed.setThumbnail("https://emojipedia-us.s3.amazonaws.com/thumbs/120/twitter/134/cat-face_1f431.png");
			
			event.reply(embed.build()).queue();	
		});
	}
	
	@Command(value="dog", description="Gives you a random picture of a dog", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void dog(CommandEvent event) {
		Request request = new Request.Builder().url("https://dog.ceo/api/breeds/image/random").addHeader("User-Agent", "Mozilla/5.0").build();
		
		Sx4Bot.client.newCall(request).enqueue((Sx4Callback) response -> {
			JSONObject json = new JSONObject(response.body().string());
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setDescription(":dog:");
			embed.setImage(json.getString("message"));
			embed.setColor(event.getMember().getColor());
			
			event.reply(embed.build()).queue();
		});
	}
	
	@Command(value="birb", aliases={"bird"}, description="Gives you a random picture of a bird", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void birb(CommandEvent event) {
		Request request = new Request.Builder().url("https://api.alexflipnote.xyz/birb").addHeader("User-Agent", "Mozilla/5.0").build();
		
		Sx4Bot.client.newCall(request).enqueue((Sx4Callback) response -> {
			JSONObject json = new JSONObject(response.body().string());
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setDescription(":bird:");
			embed.setImage(json.getString("file"));
			embed.setColor(event.getMember().getColor());
			
			event.reply(embed.build()).queue();
		});
	}
	
	@Command(value="duck", description="Gives you a random picture of a duck", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void duck(CommandEvent event) {
		Request request = new Request.Builder().url("https://random-d.uk/api/v1/random").addHeader("User-Agent", "Mozilla/5.0").build();
		
		Sx4Bot.client.newCall(request).enqueue((Sx4Callback) response -> {
			JSONObject json = new JSONObject(response.body().string());
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setDescription(":duck:");
			embed.setImage(json.getString("url"));
			embed.setFooter("Powered by random-d.uk", null);
			embed.setColor(event.getMember().getColor());
			
			event.reply(embed.build()).queue();
		});
	}
	
	@Command(value="cat", description="Gives you a random picture of a cat", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void cat(CommandEvent event) {
		Request request = new Request.Builder().url("http://aws.random.cat/meow").addHeader("User-Agent", "Mozilla/5.0").build();
		
		Sx4Bot.client.newCall(request).enqueue((Sx4Callback) response -> {
			JSONObject json = new JSONObject(response.body().string());
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setDescription(":cat:");
			embed.setImage(json.getString("file"));
			embed.setColor(event.getMember().getColor());
			
			event.reply(embed.build()).queue();
		});
	}
	
	@Command(value="fox", description="Gives you a random picture of a fox", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
	@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
	public void fox(CommandEvent event) {
		Request request = new Request.Builder().url("https://randomfox.ca/floof/").addHeader("User-Agent", "Mozilla/5.0").build();
		
		Sx4Bot.client.newCall(request).enqueue((Sx4Callback) response -> {
			JSONObject json = new JSONObject(response.body().string());
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setDescription(":fox:");
			embed.setImage(json.getString("image"));
			embed.setColor(event.getMember().getColor());
			
			event.reply(embed.build()).queue();
		});
	}
	
	@Initialize(all=true)
	public void initialize(CommandImpl command) {
		command.setCategory(Categories.ANIMALS);
	}

}
