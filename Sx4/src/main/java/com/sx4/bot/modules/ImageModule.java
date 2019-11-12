package com.sx4.bot.modules;

import java.awt.Color;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.command.Command.BotPermissions;
import com.jockie.bot.core.command.Command.Cooldown;
import com.jockie.bot.core.command.Initialize;
import com.jockie.bot.core.command.impl.CommandEvent;
import com.jockie.bot.core.command.impl.CommandImpl;
import com.jockie.bot.core.module.Module;
import com.jockie.bot.core.option.Option;
import com.sx4.bot.categories.Categories;
import com.sx4.bot.interfaces.Examples;
import com.sx4.bot.interfaces.Sx4Callback;
import com.sx4.bot.settings.Settings;
import com.sx4.bot.utils.ArgumentUtils;
import com.sx4.bot.utils.FunUtils;
import com.sx4.bot.utils.GeneralUtils;
import com.sx4.bot.utils.ImageUtils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message.Attachment;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

@Module
public class ImageModule {
	
	public static OkHttpClient client = new OkHttpClient.Builder()
			.connectTimeout(60, TimeUnit.SECONDS)
			.callTimeout(60, TimeUnit.SECONDS)
			.writeTimeout(60, TimeUnit.SECONDS)
			.readTimeout(60, TimeUnit.SECONDS)
			.build();
	
	private static final Random RANDOM = new Random();
	
	@Command(value="canny", description="Returns an image with the canny effect")
	@Examples({"canny", "canny https://i.imgur.com/i87lyNO.png", "canny @Shea#6653"})
	@Cooldown(value=5)
	@BotPermissions({Permission.MESSAGE_ATTACH_FILES})
	public void canny(CommandEvent event, @Argument(value="url | user", endless=true, nullDefault=true) String argument) {
		String url = null;
		if (!event.getMessage().getAttachments().isEmpty() && argument == null) {
			for (Attachment attachment : event.getMessage().getAttachments()) {
				if (attachment.isImage()) {
					url = attachment.getUrl();
				}
			}
		} else if (event.getMessage().getAttachments().isEmpty() && argument == null) {
			url = event.getAuthor().getEffectiveAvatarUrl();
		} else {
			Member member = ArgumentUtils.getMember(event.getGuild(), argument);
			if (member == null) {
				url = argument;
			} else {
				url = member.getUser().getEffectiveAvatarUrl();
			}
		}
		
		Request request = new Request.Builder().url("http://" + Settings.LOCAL_HOST + ":8443/api/canny?image=" + url).build();
		
		event.getTextChannel().sendTyping().queue($ -> {
			ImageModule.client.newCall(request).enqueue((Sx4Callback) response -> {
				if (response.code() == 200) {
					event.getTextChannel().sendFile(response.body().bytes(), "canny." + response.headers().get("Content-Type").split("/")[1]).queue();
				} else if (response.code() == 400) {
					event.reply(response.body().string()).queue();
				} else {
					event.reply("Oops something went wrong there! Status code: " + response.code() +  " :no_entry:\n```java\n" + response.body().string() + "```").queue();
				}	
			});
		});
	}
	
	@Command(value="invert", description="Returns an image with inverted colours")
	@Examples({"invert", "invert https://i.imgur.com/i87lyNO.png", "invert @Shea#6653"})
	@Cooldown(value=5)
	@BotPermissions({Permission.MESSAGE_ATTACH_FILES})
	public void invert(CommandEvent event, @Argument(value="url | user", endless=true, nullDefault=true) String argument) {
		String url = null;
		if (!event.getMessage().getAttachments().isEmpty() && argument == null) {
			for (Attachment attachment : event.getMessage().getAttachments()) {
				if (attachment.isImage()) {
					url = attachment.getUrl();
				}
			}
		} else if (event.getMessage().getAttachments().isEmpty() && argument == null) {
			url = event.getAuthor().getEffectiveAvatarUrl();
		} else {
			Member member = ArgumentUtils.getMember(event.getGuild(), argument);
			if (member == null) {
				url = argument;
			} else {
				url = member.getUser().getEffectiveAvatarUrl();
			}
		}
		
		Request request = new Request.Builder().url("http://" + Settings.LOCAL_HOST + ":8443/api/invert?image=" + url).build();
		
		event.getTextChannel().sendTyping().queue($ -> {
			ImageModule.client.newCall(request).enqueue((Sx4Callback) response -> {
				if (response.code() == 200) {
					event.getTextChannel().sendFile(response.body().bytes(), "invert." + response.headers().get("Content-Type").split("/")[1]).queue();
				} else if (response.code() == 400) {
					event.reply(response.body().string()).queue();
				} else {
					event.reply("Oops something went wrong there! Status code: " + response.code() +  " :no_entry:\n```java\n" + response.body().string() + "```").queue();
				}	
			});
		});
	}
	
	@Command(value="edge", description="Returns an image with the edge effect")
	@Examples({"edge", "edge https://i.imgur.com/i87lyNO.png", "edge @Shea#6653"})
	@Cooldown(value=5)
	@BotPermissions({Permission.MESSAGE_ATTACH_FILES})
	public void edge(CommandEvent event, @Argument(value="url | user", endless=true, nullDefault=true) String argument) {
		String url = null;
		if (!event.getMessage().getAttachments().isEmpty() && argument == null) {
			for (Attachment attachment : event.getMessage().getAttachments()) {
				if (attachment.isImage()) {
					url = attachment.getUrl();
				}
			}
		} else if (event.getMessage().getAttachments().isEmpty() && argument == null) {
			url = event.getAuthor().getEffectiveAvatarUrl();
		} else {
			Member member = ArgumentUtils.getMember(event.getGuild(), argument);
			if (member == null) {
				url = argument;
			} else {
				url = member.getUser().getEffectiveAvatarUrl();
			}
		}
		
		Request request = new Request.Builder().url("http://" + Settings.LOCAL_HOST + ":8443/api/edge?image=" + url).build();
		
		event.getTextChannel().sendTyping().queue($ -> {
			ImageModule.client.newCall(request).enqueue((Sx4Callback) response -> {
				if (response.code() == 200) {
					event.getTextChannel().sendFile(response.body().bytes(), "edge." + response.headers().get("Content-Type").split("/")[1]).queue();
				} else if (response.code() == 400) {
					event.reply(response.body().string()).queue();
				} else {
					event.reply("Oops something went wrong there! Status code: " + response.code() +  " :no_entry:\n```java\n" + response.body().string() + "```").queue();
				}	
			});
		});
	}
	
	@Command(value="how to google", aliases={"htg", "howtogoogle"}, description="Returns a gif of your text being googled")
	@Examples({"how to google How do you use Sx4?"})
	@Cooldown(value=10)
	@BotPermissions({Permission.MESSAGE_ATTACH_FILES})
	public void howToGoogle(CommandEvent event, @Argument(value="text", endless=true) String text) {
		if (text.length() > 50) {
			event.reply("You cannot use more than 50 characters :no_entry:").queue();
			return;
		}
		
		Request request;
		try {
			request = new Request.Builder().url(new URL("http://" + Settings.LOCAL_HOST + ":8443/api/google?q=" + text)).build();
		} catch (MalformedURLException e) {
			event.reply("Oops something went wrong there, try again :no_entry:").queue();
			return;
		}
		
		event.getTextChannel().sendTyping().queue($ -> {
			ImageModule.client.newCall(request).enqueue((Sx4Callback) response -> {
				if (response.code() == 200) {
					event.getTextChannel().sendFile(response.body().bytes(), "google." + response.headers().get("Content-Type").split("/")[1]).queue();
				} else if (response.code() == 400) {
					event.reply(response.body().string()).queue();
				} else {
					event.reply("Oops something went wrong there! Status code: " + response.code() +  " :no_entry:\n```java\n" + response.body().string() + "```").queue();
				}
			});
		});
	}
	
	@Command(value="hot", description="Returns Will Smith saying the specified image is hot")
	@Examples({"hot", "hot https://i.imgur.com/i87lyNO.png", "hot @Shea#6653"})
	@Cooldown(value=5)
	@BotPermissions({Permission.MESSAGE_ATTACH_FILES})
	public void hot(CommandEvent event, @Argument(value="url | user", endless=true, nullDefault=true) String argument) {
		String url = null;
		if (!event.getMessage().getAttachments().isEmpty() && argument == null) {
			for (Attachment attachment : event.getMessage().getAttachments()) {
				if (attachment.isImage()) {
					url = attachment.getUrl();
				}
			}
		} else if (event.getMessage().getAttachments().isEmpty() && argument == null) {
			url = event.getAuthor().getEffectiveAvatarUrl();
		} else {
			Member member = ArgumentUtils.getMember(event.getGuild(), argument);
			if (member == null) {
				url = argument;
			} else {
				url = member.getUser().getEffectiveAvatarUrl();
			}
		}
		
		Request request;
		try {
			request = new Request.Builder().url(new URL("http://" + Settings.LOCAL_HOST + ":8443/api/hot?image=" + url)).build();
		} catch (MalformedURLException e) {
			event.reply("Oops something went wrong there, try again :no_entry:").queue();
			return;
		}
		
		event.getTextChannel().sendTyping().queue($ -> {
			ImageModule.client.newCall(request).enqueue((Sx4Callback) response -> {
				if (response.code() == 200) {
					event.getTextChannel().sendFile(response.body().bytes(), "hot." + response.headers().get("Content-Type").split("/")[1]).queue();
				} else if (response.code() == 400) {
					event.reply(response.body().string()).queue();
				} else {
					event.reply("Oops something went wrong there! Status code: " + response.code() +  " :no_entry:\n```java\n" + response.body().string() + "```").queue();
				}	
			});
		});
	}
	
	@Command(value="discord", description="Mimick something said in discord")
	@Examples({"discord Shea hello there", "discord Sx4 User has been banned"})
	@Cooldown(value=5)
	@BotPermissions({Permission.MESSAGE_ATTACH_FILES})
	public void discord(CommandEvent event, @Argument(value="user") String userArgument, @Argument(value="text", endless=true) String text, @Option(value="white") boolean white) {
		Member member = ArgumentUtils.getMember(event.getGuild(), userArgument);
		if (member == null) {
			event.reply("I could not find that user :no_entry:").queue();
			return;
		}
		
		JSONObject body = new JSONObject()
				.put("avatarUrl", member.getUser().getEffectiveAvatarUrl())
				.put("userName", member.getEffectiveName())
				.put("colour", GeneralUtils.getHex(member.getColorRaw()))
				.put("text", text)
				.put("darkTheme", !white)
				.put("bot", member.getUser().isBot());
		
		JSONObject mentions = ImageUtils.getMentions(event.getGuild(), text);
		for (String key : mentions.keySet()) {
			body.put(key, mentions.get(key));
		}
		
		Request request = new Request.Builder()
				.url("http://" + Settings.LOCAL_HOST + ":8443/api/discord")
				.post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), body.toString()))
				.build();
		
		event.getTextChannel().sendTyping().queue($ -> {
			ImageModule.client.newCall(request).enqueue((Sx4Callback) response -> {
				if (response.code() == 200) {
					event.getTextChannel().sendFile(response.body().bytes(), "discord." + response.headers().get("Content-Type").split("/")[1]).queue();
				} else if (response.code() == 400) {
					event.reply(response.body().string()).queue();
				} else {
					event.reply("Oops something went wrong there! Status code: " + response.code() +  " :no_entry:\n```java\n" + response.body().string() + "```").queue();
				}
			});
		});
	}
	
	@Command(value="flag", description="Puts a specified flag over a users avatar")
	@Examples({"flag fr", "flag gb Shea", "flag se Joakim"})
	@Cooldown(value=5)
	@BotPermissions({Permission.MESSAGE_ATTACH_FILES})
	public void flag(CommandEvent event, @Argument(value="flag code") String flag, @Argument(value="user", endless=true, nullDefault=true) String userArgument) {
		Member member;
		if (userArgument == null) {
			member = event.getMember();
		} else {
			member = ArgumentUtils.getMember(event.getGuild(), userArgument);
			if (member == null) {
				event.reply("I could not find that user :no_entry:").queue();
				return;
			}
		}
		
		Request request = new Request.Builder().url("http://" + Settings.LOCAL_HOST + ":8443/api/flag?image=" + member.getUser().getEffectiveAvatarUrl() + "&flag=" + flag).build();
		
		event.getTextChannel().sendTyping().queue($ -> {
			ImageModule.client.newCall(request).enqueue((Sx4Callback) response -> {
				if (response.code() == 200) {
					event.getTextChannel().sendFile(response.body().bytes(), "flag." + response.headers().get("Content-Type").split("/")[1]).queue();
				} else if (response.code() == 400) {
					event.reply(response.body().string()).queue();
				} else {
					event.reply("Oops something went wrong there! Status code: " + response.code() +  " :no_entry:\n```java\n" + response.body().string() + "```").queue();
				}	
			});
		});
	}
	
	@Command(value="christmas", description="Turn an image into a christmas themed one")
	@Examples({"christmas", "christmas https://i.imgur.com/i87lyNO.png", "christmas @Shea#6653"})
	@Cooldown(value=7)
	@BotPermissions({Permission.MESSAGE_ATTACH_FILES})
	public void christmas(CommandEvent event, @Argument(value="url | user", endless=true, nullDefault=true) String argument) {
		String url = null;
		if (!event.getMessage().getAttachments().isEmpty() && argument == null) {
			for (Attachment attachment : event.getMessage().getAttachments()) {
				if (attachment.isImage()) {
					url = attachment.getUrl();
				}
			}
		} else if (event.getMessage().getAttachments().isEmpty() && argument == null) {
			url = event.getAuthor().getEffectiveAvatarUrl();
		} else {
			Member member = ArgumentUtils.getMember(event.getGuild(), argument);
			if (member == null) {
				url = argument;
			} else {
				url = member.getUser().getEffectiveAvatarUrl();
			}
		}
		
		Request request = new Request.Builder().url("http://" + Settings.LOCAL_HOST + ":8443/api/christmas?image=" + url).build();
		
		event.getTextChannel().sendTyping().queue($ -> {
			ImageModule.client.newCall(request).enqueue((Sx4Callback) response -> {
				if (response.code() == 200) {
					event.getTextChannel().sendFile(response.body().bytes(), "christmas." + response.headers().get("Content-Type").split("/")[1]).queue();
				} else if (response.code() == 400) {
					event.reply(response.body().string()).queue();
				} else {
					event.reply("Oops something went wrong there! Status code: " + response.code() +  " :no_entry:\n```java\n" + response.body().string() + "```").queue();
				}	
			});
		});
	}
	
	@Command(value="halloween", description="Turn an image into a halloween themed one")
	@Examples({"halloween", "halloween https://i.imgur.com/i87lyNO.png", "halloween @Shea#6653"})
	@Cooldown(value=7)
	@BotPermissions({Permission.MESSAGE_ATTACH_FILES})
	public void halloween(CommandEvent event, @Argument(value="url | user", endless=true, nullDefault=true) String argument) {
		String url = null;
		if (!event.getMessage().getAttachments().isEmpty() && argument == null) {
			for (Attachment attachment : event.getMessage().getAttachments()) {
				if (attachment.isImage()) {
					url = attachment.getUrl();
				}
			}
		} else if (event.getMessage().getAttachments().isEmpty() && argument == null) {
			url = event.getAuthor().getEffectiveAvatarUrl();
		} else {
			Member member = ArgumentUtils.getMember(event.getGuild(), argument);
			if (member == null) {
				url = argument;
			} else {
				url = member.getUser().getEffectiveAvatarUrl();
			}
		}
		
		Request request = new Request.Builder().url("http://" + Settings.LOCAL_HOST + ":8443/api/halloween?image=" + url).build();
		
		event.getTextChannel().sendTyping().queue($ -> {
			ImageModule.client.newCall(request).enqueue((Sx4Callback) response -> {
				if (response.code() == 200) {
					event.getTextChannel().sendFile(response.body().bytes(), "halloween." + response.headers().get("Content-Type").split("/")[1]).queue();
				} else if (response.code() == 400) {
					event.reply(response.body().string()).queue();
				} else {
					event.reply("Oops something went wrong there! Status code: " + response.code() +  " :no_entry:\n```java\n" + response.body().string() + "```").queue();
				}	
			});
		});
	}
	
	@Command(value="trash", description="Make an image look like trash")
	@Examples({"trash", "trash https://i.imgur.com/i87lyNO.png", "trash @Shea#6653"})
	@Cooldown(value=3)
	@BotPermissions({Permission.MESSAGE_ATTACH_FILES})
	public void trash(CommandEvent event, @Argument(value="url | user", endless=true, nullDefault=true) String argument) {
		String url = null;
		if (!event.getMessage().getAttachments().isEmpty() && argument == null) {
			for (Attachment attachment : event.getMessage().getAttachments()) {
				if (attachment.isImage()) {
					url = attachment.getUrl();
				}
			}
		} else if (event.getMessage().getAttachments().isEmpty() && argument == null) {
			url = event.getAuthor().getEffectiveAvatarUrl();
		} else {
			Member member = ArgumentUtils.getMember(event.getGuild(), argument);
			if (member == null) {
				url = argument;
			} else {
				url = member.getUser().getEffectiveAvatarUrl();
			}
		}
		
		Request request = new Request.Builder().url("http://" + Settings.LOCAL_HOST + ":8443/api/trash?image=" + url).build();
		
		event.getTextChannel().sendTyping().queue($ -> {
			ImageModule.client.newCall(request).enqueue((Sx4Callback) response -> {
				if (response.code() == 200) {
					event.getTextChannel().sendFile(response.body().bytes(), "trash." + response.headers().get("Content-Type").split("/")[1]).queue();
				} else if (response.code() == 400) {
					event.reply(response.body().string()).queue();
				} else {
					event.reply("Oops something went wrong there! Status code: " + response.code() +  " :no_entry:\n```java\n" + response.body().string() + "```").queue();
				}	
			});
		});
	}
	
	@Command(value="who would win", aliases={"www", "whowouldwin"}, description="Returns the 2 images provided in an image questioning who would win")
	@Examples({"who would win @Sx4#1617", "who would win Dyno Sx4"})
	@Cooldown(value=3)
	@BotPermissions({Permission.MESSAGE_ATTACH_FILES})
	public void whoWouldWin(CommandEvent event, @Argument(value="first url | user") String firstArgument, @Argument(value="second url | user", endless=true, nullDefault=true) String secondArgument) {
		String firstUrl = null;
		Member firstMember = ArgumentUtils.getMember(event.getGuild(), firstArgument);
		if (firstMember == null) {
			firstUrl = firstArgument;
		} else {
			firstUrl = firstMember.getUser().getEffectiveAvatarUrl();
		}
		
		String secondUrl = null;
		if (!event.getMessage().getAttachments().isEmpty() && secondArgument == null) {
			for (Attachment attachment : event.getMessage().getAttachments()) {
				if (attachment.isImage()) {
					secondUrl = attachment.getUrl();
				}
			}
		} else if (event.getMessage().getAttachments().isEmpty() && secondArgument == null) {
			secondUrl = event.getAuthor().getEffectiveAvatarUrl();
		} else {
			Member secondMember = ArgumentUtils.getMember(event.getGuild(), secondArgument);
			if (secondMember == null) {
				secondUrl = secondArgument;
			} else {
				secondUrl = secondMember.getUser().getEffectiveAvatarUrl();
			}
		}

		Request request = new Request.Builder().url("http://" + Settings.LOCAL_HOST + ":8443/api/www?firstImage=" + firstUrl + "&secondImage=" + secondUrl).build();
		
		event.getTextChannel().sendTyping().queue($ -> {
			ImageModule.client.newCall(request).enqueue((Sx4Callback) response -> {
				if (response.code() == 200) {
					event.getTextChannel().sendFile(response.body().bytes(), "www." + response.headers().get("Content-Type").split("/")[1]).queue();
				} else if (response.code() == 400) {
					event.reply(response.body().string()).queue();
				} else {
					event.reply("Oops something went wrong there! Status code: " + response.code() +  " :no_entry:\n```java\n" + response.body().string() + "```").queue();
				}	
			});
		});
	}
	
	@Command(value="fear", description="Returns an image where someone fears the user/image provided")
	@Examples({"fear", "fear https://i.imgur.com/i87lyNO.png", "fear @Shea#6653"})
	@Cooldown(value=10)
	@BotPermissions({Permission.MESSAGE_ATTACH_FILES})
	public void fear(CommandEvent event, @Argument(value="url | user", endless=true, nullDefault=true) String argument) {
		String url = null;
		if (!event.getMessage().getAttachments().isEmpty() && argument == null) {
			for (Attachment attachment : event.getMessage().getAttachments()) {
				if (attachment.isImage()) {
					url = attachment.getUrl();
				}
			}
		} else if (event.getMessage().getAttachments().isEmpty() && argument == null) {
			url = event.getAuthor().getEffectiveAvatarUrl();
		} else {
			Member member = ArgumentUtils.getMember(event.getGuild(), argument);
			if (member == null) {
				url = argument;
			} else {
				url = member.getUser().getEffectiveAvatarUrl();
			}
		}
		
		Request request = new Request.Builder().url("http://" + Settings.LOCAL_HOST + ":8443/api/fear?image=" + url).build();
		
		event.getTextChannel().sendTyping().queue($ -> {
			ImageModule.client.newCall(request).enqueue((Sx4Callback) response -> {
				if (response.code() == 200) {
					event.getTextChannel().sendFile(response.body().bytes(), "fear." + response.headers().get("Content-Type").split("/")[1]).queue();
				} else if (response.code() == 400) {
					event.reply(response.body().string()).queue();
				} else {
					event.reply("Oops something went wrong there! Status code: " + response.code() +  " :no_entry:\n```java\n" + response.body().string() + "```").queue();
				}
			});		
		});
	}
	
	@Command(value="emboss", description="Returns the image with an emboss effect")
	@Examples({"emboss", "emboss https://i.imgur.com/i87lyNO.png", "emboss @Shea#6653"})
	@Cooldown(value=5)
	@BotPermissions({Permission.MESSAGE_ATTACH_FILES})
	public void emboss(CommandEvent event, @Argument(value="url | user", endless=true, nullDefault=true) String argument) {
		String url = null;
		if (!event.getMessage().getAttachments().isEmpty() && argument == null) {
			for (Attachment attachment : event.getMessage().getAttachments()) {
				if (attachment.isImage()) {
					url = attachment.getUrl();
				}
			}
		} else if (event.getMessage().getAttachments().isEmpty() && argument == null) {
			url = event.getAuthor().getEffectiveAvatarUrl();
		} else {
			Member member = ArgumentUtils.getMember(event.getGuild(), argument);
			if (member == null) {
				url = argument;
			} else {
				url = member.getUser().getEffectiveAvatarUrl();
			}
		}
		
		Request request = new Request.Builder().url("http://" + Settings.LOCAL_HOST + ":8443/api/emboss?image=" + url).build();
		
		event.getTextChannel().sendTyping().queue($ -> {
			ImageModule.client.newCall(request).enqueue((Sx4Callback) response -> {
				if (response.code() == 200) {
					event.getTextChannel().sendFile(response.body().bytes(), "emboss." + response.headers().get("Content-Type").split("/")[1]).queue();
				} else if (response.code() == 400) {
					event.reply(response.body().string()).queue();
				} else {
					event.reply("Oops something went wrong there! Status code: " + response.code() +  " :no_entry:\n```java\n" + response.body().string() + "```").queue();
				}	
			});
		});
	}
	
	@Command(value="ship", description="Ship 2 users together to see their love percentage and their ship name")
	@Examples({"ship @Sx4#1617", "ship Shea 203421890637856768"})
	@Cooldown(value=3)
	@BotPermissions({Permission.MESSAGE_ATTACH_FILES})
	public void ship(CommandEvent event, @Argument(value="first user") String firstUserArgument, @Argument(value="second user", endless=true, nullDefault=true) String secondUserArgument) {
		Member firstMember = ArgumentUtils.getMember(event.getGuild(), firstUserArgument);
		if (firstMember == null) {
			event.reply("I could not find the first user :no_entry:").queue();
			return;
		}
		
		Member secondMember;
		if (secondUserArgument == null) {
			secondMember = event.getMember();
		} else {
			secondMember = ArgumentUtils.getMember(event.getGuild(), secondUserArgument);
			if (secondMember == null) {
				event.reply("I could not find the second user :no_entry:").queue();
				return;
			}
		}
		
		Request request = new Request.Builder().url("http://" + Settings.LOCAL_HOST + ":8443/api/ship?firstImage=" + firstMember.getUser().getEffectiveAvatarUrl() + "&secondImage=" + secondMember.getUser().getEffectiveAvatarUrl()).build();
		
		event.getTextChannel().sendTyping().queue($ -> {
			ImageModule.client.newCall(request).enqueue((Sx4Callback) response -> {
				if (response.code() == 200) {
					RANDOM.setSeed(secondMember.getIdLong() + firstMember.getIdLong());
					int shipPercentage = RANDOM.nextInt(100) + 1;
					
					String firstMemberName = firstMember.getUser().getName();
					String secondMemberName = secondMember.getUser().getName();
					String shipName = firstMemberName.substring(0, (int) Math.ceil((double) firstMemberName.length()/2)) + secondMemberName.substring((int) Math.ceil((double) secondMemberName.length()/2));
					
					event.reply("Ship Name: **" + shipName + "**\nLove Percentage: **" + shipPercentage + "%**").addFile(response.body().bytes(), "ship." + response.headers().get("Content-Type").split("/")[1]).queue();
				} else if (response.code() == 400) {
					event.reply(response.body().string()).queue();
				} else {
					event.reply("Oops something went wrong there! Status code: " + response.code() +  " :no_entry:\n```java\n" + response.body().string() + "```").queue();
				}	
			});
		});
	}
	
	@Command(value="vr", aliases={"virtualreality", "virtual reality"}, description="Makes someone emotional over the image/user provided in vr")
	@Examples({"vr", "vr https://i.imgur.com/i87lyNO.png", "vr @Shea#6653"})
	@Cooldown(value=5)
	@BotPermissions({Permission.MESSAGE_ATTACH_FILES})
	public void vr(CommandEvent event, @Argument(value="url | user", endless=true, nullDefault=true) String argument) {
		String url = null;
		if (!event.getMessage().getAttachments().isEmpty() && argument == null) {
			for (Attachment attachment : event.getMessage().getAttachments()) {
				if (attachment.isImage()) {
					url = attachment.getUrl();
				}
			}
		} else if (event.getMessage().getAttachments().isEmpty() && argument == null) {
			url = event.getAuthor().getEffectiveAvatarUrl();
		} else {
			Member member = ArgumentUtils.getMember(event.getGuild(), argument);
			if (member == null) {
				url = argument;
			} else {
				url = member.getUser().getEffectiveAvatarUrl();
			}
		}
		
		Request request = new Request.Builder().url("http://" + Settings.LOCAL_HOST + ":8443/api/vr?image=" + url).build();
		
		event.getTextChannel().sendTyping().queue($ -> {
			ImageModule.client.newCall(request).enqueue((Sx4Callback) response -> {
				if (response.code() == 200) {
					event.getTextChannel().sendFile(response.body().bytes(), "vr." + response.headers().get("Content-Type").split("/")[1]).queue();
				} else if (response.code() == 400) {
					event.reply(response.body().string()).queue();
				} else {
					event.reply("Oops something went wrong there! Status code: " + response.code() +  " :no_entry:\n```java\n" + response.body().string() + "```").queue();
				}	
			});
		});
	}
	
	@Command(value="shit", description="A man steps in the provided user/image and calls it shit")
	@Examples({"shit", "shit https://i.imgur.com/i87lyNO.png", "shit @Shea#6653"})
	@Cooldown(value=10)
	@BotPermissions({Permission.MESSAGE_ATTACH_FILES})
	public void shit(CommandEvent event, @Argument(value="url | user", endless=true, nullDefault=true) String argument) {
		String url = null;
		if (!event.getMessage().getAttachments().isEmpty() && argument == null) {
			for (Attachment attachment : event.getMessage().getAttachments()) {
				if (attachment.isImage()) {
					url = attachment.getUrl();
				}
			}
		} else if (event.getMessage().getAttachments().isEmpty() && argument == null) {
			url = event.getAuthor().getEffectiveAvatarUrl();
		} else {
			Member member = ArgumentUtils.getMember(event.getGuild(), argument);
			if (member == null) {
				url = argument;
			} else {
				url = member.getUser().getEffectiveAvatarUrl();
			}
		}
		
		Request request = new Request.Builder().url("http://" + Settings.LOCAL_HOST + ":8443/api/shit?image=" + url).build();
		
		event.getTextChannel().sendTyping().queue($ -> {
			ImageModule.client.newCall(request).enqueue((Sx4Callback) response -> {
				if (response.code() == 200) {
					event.getTextChannel().sendFile(response.body().bytes(), "shit." + response.headers().get("Content-Type").split("/")[1]).queue();
				} else if (response.code() == 400) {
					event.reply(response.body().string()).queue();
				} else {
					event.reply("Oops something went wrong there! Status code: " + response.code() +  " :no_entry:\n```java\n" + response.body().string() + "```").queue();
				}	
			});
		});
	}
	
	@Command(value="beautiful", description="A man calls the provided user/image beautiful")
	@Examples({"beautiful", "beautiful https://i.imgur.com/i87lyNO.png", "beautiful @Shea#6653"})
	@Cooldown(value=10)
	@BotPermissions({Permission.MESSAGE_ATTACH_FILES})
	public void beautiful(CommandEvent event, @Argument(value="url | user", endless=true, nullDefault=true) String argument) {
		String url = null;
		if (!event.getMessage().getAttachments().isEmpty() && argument == null) {
			for (Attachment attachment : event.getMessage().getAttachments()) {
				if (attachment.isImage()) {
					url = attachment.getUrl();
				}
			}
		} else if (event.getMessage().getAttachments().isEmpty() && argument == null) {
			url = event.getAuthor().getEffectiveAvatarUrl();
		} else {
			Member member = ArgumentUtils.getMember(event.getGuild(), argument);
			if (member == null) {
				url = argument;
			} else {
				url = member.getUser().getEffectiveAvatarUrl();
			}
		}
		
		Request request = new Request.Builder().url("http://" + Settings.LOCAL_HOST + ":8443/api/beautiful?image=" + url).build();
		
		event.getTextChannel().sendTyping().queue($ -> {
			ImageModule.client.newCall(request).enqueue((Sx4Callback) response -> {
				if (response.code() == 200) {
					event.getTextChannel().sendFile(response.body().bytes(), "beautiful." + response.headers().get("Content-Type").split("/")[1]).queue();
				} else if (response.code() == 400) {
					event.reply(response.body().string()).queue();
				} else {
					event.reply("Oops something went wrong there! Status code: " + response.code() +  " :no_entry:\n```java\n" + response.body().string() + "```").queue();
				}
			});
		});
	}
	
	@Command(value="gay", description="Puts the gay pride flag over the specified user/image")
	@Examples({"gay", "gay https://i.imgur.com/i87lyNO.png", "gay @Shea#6653"})
	@Cooldown(value=5)
	@BotPermissions({Permission.MESSAGE_ATTACH_FILES})
	public void gay(CommandEvent event, @Argument(value="url | user", endless=true, nullDefault=true) String argument) {
		String url = null;
		if (!event.getMessage().getAttachments().isEmpty() && argument == null) {
			for (Attachment attachment : event.getMessage().getAttachments()) {
				if (attachment.isImage()) {
					url = attachment.getUrl();
				}
			}
		} else if (event.getMessage().getAttachments().isEmpty() && argument == null) {
			url = event.getAuthor().getEffectiveAvatarUrl();
		} else {
			Member member = ArgumentUtils.getMember(event.getGuild(), argument);
			if (member == null) {
				url = argument;
			} else {
				url = member.getUser().getEffectiveAvatarUrl();
			}
		}
		
		Request request = new Request.Builder().url("http://" + Settings.LOCAL_HOST + ":8443/api/gay?image=" + url).build();
		
		event.getTextChannel().sendTyping().queue($ -> {
			ImageModule.client.newCall(request).enqueue((Sx4Callback) response -> {
				if (response.code() == 200) {
					event.getTextChannel().sendFile(response.body().bytes(), "gay." + response.headers().get("Content-Type").split("/")[1]).queue();
				} else if (response.code() == 400) {
					event.reply(response.body().string()).queue();
				} else {
					event.reply("Oops something went wrong there! Status code: " + response.code() +  " :no_entry:\n```java\n" + response.body().string() + "```").queue();
				}
			});
		});
	}
	
	@Command(value="trump tweet", aliases={"trumptweet"}, description="Tweet anything from trumps twitter")
	@Examples({"trump tweet something trump would say"})
	@Cooldown(value=3)
	@BotPermissions({Permission.MESSAGE_ATTACH_FILES})
	public void trumpTweet(CommandEvent event, @Argument(value="text", endless=true) String text) {
		if (text.length() > 250) {
			event.reply("The text cannot be longer than 250 characters :no_entry:").queue();
			return;
		}
		
		Request request;
		try {
			request = new Request.Builder().url(new URL("http://" + Settings.LOCAL_HOST + ":8443/api/trump?text=" + text)).build();
		} catch (MalformedURLException e1) {
			event.reply("Oops something went wrong there, try again :no_entry:").queue();
			return;
		}
		
		event.getTextChannel().sendTyping().queue($ -> {
			ImageModule.client.newCall(request).enqueue((Sx4Callback) response -> {
				if (response.code() == 200) {
					event.getTextChannel().sendFile(response.body().bytes(), "trump." + response.headers().get("Content-Type").split("/")[1]).queue();
				} else if (response.code() == 400) {
					event.reply(response.body().string()).queue();
				} else {
					event.reply("Oops something went wrong there! Status code: " + response.code() +  " :no_entry:\n```java\n" + response.body().string() + "```").queue();
				}
			});	
		});
	}
	
	@Command(value="tweet", description="Create a fake tweet from a users discord account")
	@Examples({"tweet @Shea#6653 Sx4 is kind of cool", "tweet Shea hello"})
	@Cooldown(value=7)
	@BotPermissions({Permission.MESSAGE_ATTACH_FILES})
	public void tweet(CommandEvent event, @Argument(value="user") String userArgument, @Argument(value="text", endless=true) String text) {
		Member member = ArgumentUtils.getMember(event.getGuild(), userArgument);
		if (member == null) {
			event.reply("I could not find that user :no_entry:").queue();
			return;
		}
		
		int likes = RANDOM.nextInt(event.getGuild().getMembers().size()) + 1;
		int retweets = RANDOM.nextInt(event.getGuild().getMembers().size()) + 1;
		
		List<String> avatarUrls = new ArrayList<>();
		int minimumAmount = Math.min(Math.min(event.getGuild().getMembers().size(), likes), 10);
		if (minimumAmount == event.getGuild().getMembers().size()) {
			for (int i = 0; i < minimumAmount; i++) {
				avatarUrls.add(event.getGuild().getMembers().get(i).getUser().getEffectiveAvatarUrl() + "?size=64");
			}
		} else {
			while (avatarUrls.size() != minimumAmount) {
				int randomInt = RANDOM.nextInt(event.getGuild().getMembers().size());
				String avatarUrl = event.getGuild().getMembers().get(randomInt).getUser().getEffectiveAvatarUrl() + "?size=64";
				if (!avatarUrls.contains(avatarUrl)) {
					avatarUrls.add(avatarUrl);
				}
			}
		}
		
		JSONObject json = new JSONObject().put("displayName", member.getEffectiveName()).put("name", member.getUser().getName()).put("avatarUrl", member.getUser().getEffectiveAvatarUrl() + "?size=128")
				.put("urls", avatarUrls).put("likes", likes).put("retweets", retweets).put("text", FunUtils.escapeMentions(event.getGuild(), text));
		
		Request request = new Request.Builder().url("http://" + Settings.LOCAL_HOST + ":8443/api/tweet").post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), json.toString())).build();
		
		event.getTextChannel().sendTyping().queue($ -> {
			ImageModule.client.newCall(request).enqueue((Sx4Callback) response -> {
				if (response.code() == 200) {
					event.getTextChannel().sendFile(response.body().bytes(), "tweet." + response.headers().get("Content-Type").split("/")[1]).queue();
				} else if (response.code() == 400) {
					event.reply(response.body().string()).queue();
				} else {
					event.reply("Oops something went wrong there! Status code: " + response.code() +  " :no_entry:\n```java\n" + response.body().string() + "```").queue();
				}
			});
		});
	}
	
	@Command(value="colour", aliases={"color", "hex", "rgb"}, description="View the colour of a hex or rgb value")
	@Examples({"colour", "colour #ffff00", "colour ffff00", "colour 255, 255, 0"})
	@Cooldown(value=3)
	@BotPermissions({Permission.MESSAGE_ATTACH_FILES, Permission.MESSAGE_EMBED_LINKS})
	public void colour(CommandEvent event, @Argument(value="hex | rgb", endless=true, nullDefault=true) String argument) {
		Color colour;
		if (argument == null) {
			colour = new Color(RANDOM.nextFloat(), RANDOM.nextFloat(), RANDOM.nextFloat());
		} else {
			colour = ArgumentUtils.getColourFromString(argument);
			if (colour == null) {
				event.reply("Invalid hex or RGB value :no_entry:").queue();
				return;
			}
		}
		
		String hex = GeneralUtils.getHex(colour.hashCode());
		
		Request request = new Request.Builder().url("http://" + Settings.LOCAL_HOST + ":8443/api/colour?hex=" + hex).build();
		
		event.getTextChannel().sendTyping().queue($ -> {
			ImageModule.client.newCall(request).enqueue((Sx4Callback) response -> {
				if (response.code() == 200) {
					EmbedBuilder embed = new EmbedBuilder();
					embed.setColor(colour);
					embed.setDescription("Hex: #" + hex + "\nRGB: " + GeneralUtils.getRGB(colour.hashCode()));
					embed.setAuthor("#" + hex, null, "attachment://colour.png");
					embed.setImage("attachment://colour.png");			
					event.reply(embed.build()).addFile(response.body().bytes(), "colour.png").queue();
				} else if (response.code() == 400) {
					event.reply(response.body().string()).queue();
				} else {
					event.reply("Oops something went wrong there! Status code: " + response.code() +  " :no_entry:\n```java\n" + response.body().string() + "```").queue();
				}	
			});
		});
	}
	
	@Command(value="most common colour", aliases={"mostcommoncolour", "common colour", "commoncolour", "commoncolor", "common color", "mostcommoncolor", "most common color"}, description="Returns the most common colour in an image")
	@Examples({"most common colour", "most common colour @Shea#6653", "most common colour Shea", "most common colour 402557516728369153"})
	@Cooldown(value=3)
	@BotPermissions({Permission.MESSAGE_ATTACH_FILES, Permission.MESSAGE_EMBED_LINKS})
	public void mostCommonColour(CommandEvent event, @Argument(value="url | user", endless=true, nullDefault=true) String argument) {
		String url = null;
		if (!event.getMessage().getAttachments().isEmpty() && argument == null) {
			for (Attachment attachment : event.getMessage().getAttachments()) {
				if (attachment.isImage()) {
					url = attachment.getUrl();
				}
			}
		} else if (event.getMessage().getAttachments().isEmpty() && argument == null) {
			url = event.getAuthor().getEffectiveAvatarUrl() + "?size=1024";
		} else {
			Member member = ArgumentUtils.getMember(event.getGuild(), argument);
			if (member == null) {
				url = argument;
			} else {
				url = member.getUser().getEffectiveAvatarUrl() + "?size=1024";
			}
		}
		
		Request request = new Request.Builder().url("http://" + Settings.LOCAL_HOST + ":8443/api/commonColour?image=" + url).build();
		
		String newUrl = url;
		event.getTextChannel().sendTyping().queue($ -> {
			ImageModule.client.newCall(request).enqueue((Sx4Callback) response -> {
				if (response.code() == 200) {
					int colourRaw;
					colourRaw = Integer.parseInt(response.body().string());
					
					EmbedBuilder embed = new EmbedBuilder();
					embed.setThumbnail(newUrl);
					embed.setColor(colourRaw);
					embed.setTitle("Most Common Colour");
					embed.setDescription("Hex: #" + GeneralUtils.getHex(colourRaw) + "\nRGB: " + GeneralUtils.getRGB(colourRaw));		
					event.reply(embed.build()).queue();
				} else if (response.code() == 400) {
					event.reply(response.body().string()).queue();
				} else {
					event.reply("Oops something went wrong there! Status code: " + response.code() +  " :no_entry:\n```java\n" + response.body().string() + "```").queue();
				}
			});
		});
	}
	
	@Command(value="scroll", description="A scroll which has been lost for 15 years has just been found your specified text shows what it says")
	@Examples({"scroll I've ran out of ideas", "scroll help me"})
	@Cooldown(value=3)
	@BotPermissions({Permission.MESSAGE_ATTACH_FILES})
	public void scroll(CommandEvent event, @Argument(value="text", endless=true) String text) {
		if (text.length() > 30) {
			event.reply("The text cannot be longer than 30 characters :no_entry:").queue();
			return;
		}
		
		Request request = new Request.Builder().url("http://" + Settings.LOCAL_HOST + ":8443/api/scroll?text=" + text).build();
		
		event.getTextChannel().sendTyping().queue($ -> {
			ImageModule.client.newCall(request).enqueue((Sx4Callback) response -> {
				if (response.code() == 200) {
					event.getTextChannel().sendFile(response.body().bytes(), "scroll.png").queue();
				} else if (response.code() == 400) {
					event.reply(response.body().string()).queue(); 
				} else {
					event.reply("Oops something went wrong there! Status code: " + response.code() +  " :no_entry:\n```java\n" + response.body().string() + "```").queue();
				}	
			});
		});
	}
	
	@Command(value="drift", description="A car quickly drifts into a different intersection to avoid going straight, you specify what is on the left and right sign")
	@Examples({"drift Shea healthy", "drift @Shea#6653 Dyno Sx4"})
	@Cooldown(value=3)
	@BotPermissions({Permission.MESSAGE_ATTACH_FILES})
	public void drift(CommandEvent event, @Argument(value="user") String userArgument, @Argument(value="left sign") String leftText, @Argument(value="right sign", endless=true, nullDefault=true) String rightText) {
		if (leftText.length() > 30) {
			event.reply("The left sign cannot be longer than 30 characters :no_entry:").queue();
			return;
		}
		
		if (rightText != null) {
			if (rightText.length() > 40) {
				event.reply("The right sign cannot be longer than 40 characters :no_entry:").queue();
				return;
			}
		}
		
		Member member = ArgumentUtils.getMember(event.getGuild(), userArgument);
		if (member == null) {
			event.reply("I could not find that user :no_entry:").queue();
			return;
		}
		
		String url = member.getUser().getEffectiveAvatarUrl();
		
		Request request = new Request.Builder().url("http://" + Settings.LOCAL_HOST + ":8443/api/drift?leftText=" + leftText + "&image=" + url + (rightText == null ? "" : "&rightText=" + rightText)).build();
		
		event.getTextChannel().sendTyping().queue($ -> {
			ImageModule.client.newCall(request).enqueue((Sx4Callback) response -> {
				if (response.code() == 200) {
					event.getTextChannel().sendFile(response.body().bytes(), "drift.png").queue();
				} else if (response.code() == 400) {
					event.reply(response.body().string()).queue();
				} else {
					event.reply("Oops something went wrong there! Status code: " + response.code() +  " :no_entry:\n```java\n" + response.body().string() + "```").queue();
				}	
			});
		});
	}
	
	private final List<String> statuses = List.of("online", "idle", "dnd", "offline", "invisible", "streaming");
	
	@Command(value="status", description="Returns the status image of a users profile picture")
	@Examples({"status", "status @Shea#6653", "status Shea idle"})
	@Cooldown(value=5)
	@BotPermissions({Permission.MESSAGE_ATTACH_FILES})
	public void status(CommandEvent event, @Argument(value="user", nullDefault=true) String userArgument, @Argument(value="status", nullDefault=true) String status) {
		Member member;
		if (userArgument == null) {
			member = event.getMember();
		} else {
			member = ArgumentUtils.getMember(event.getGuild(), userArgument);
			if (member == null) {
				event.reply("I could not find that user :no_entry:").queue();
				return;
			}
		}
		
		status = status == null ? member.getOnlineStatus().getKey() : status;
		if (!this.statuses.contains(status)) {
			event.reply("I could not find that status, valid statuses are " + GeneralUtils.joinGrammatical(this.statuses) + " :no_entry:").queue();
			return;
		}
		
		Request request = new Request.Builder().url("http://" + Settings.LOCAL_HOST + ":8443/api/status?image=" + member.getUser().getEffectiveAvatarUrl() + "?size=1024&status=" + status).build();
		
		event.getTextChannel().sendTyping().queue($ -> {
			ImageModule.client.newCall(request).enqueue((Sx4Callback) response -> {
				if (response.code() == 200) {
					event.getTextChannel().sendFile(response.body().bytes(), "status." + response.headers().get("Content-Type").split("/")[1]).queue();
				} else if (response.code() == 400) {
					event.reply(response.body().string()).queue();
				} else {
					event.reply("Oops something went wrong there! Status code: " + response.code() +  " :no_entry:\n```java\n" + response.body().string() + "```").queue();
				}	
			});
		});
		
	}

	@Initialize(all=true, subCommands=true, recursive=true)
	public void initialize(CommandImpl command) {
		command.setCategory(Categories.IMAGE);
	}
	
}
