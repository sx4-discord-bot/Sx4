package com.sx4.modules;

import java.awt.Color;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
import com.sx4.categories.Categories;
import com.sx4.interfaces.Sx4Callback;
import com.sx4.settings.Settings;
import com.sx4.utils.ArgumentUtils;
import com.sx4.utils.FunUtils;
import com.sx4.utils.GeneralUtils;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Message.Attachment;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

@Module
public class ImageModule {
	
	public static OkHttpClient client = new OkHttpClient.Builder().build();
	
	private static Random random = new Random();
	
	@Command(value="how to google", aliases={"htg", "howtogoogle"}, description="Returns a gif of your text being googled")
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
			client.newCall(request).enqueue((Sx4Callback) response -> {
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
			client.newCall(request).enqueue((Sx4Callback) response -> {
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
	@Cooldown(value=5)
	@BotPermissions({Permission.MESSAGE_ATTACH_FILES})
	public void discord(CommandEvent event, @Argument(value="user") String userArgument, @Argument(value="text", endless=true) String text, @Option(value="white") boolean white) {
		Member member = ArgumentUtils.getMember(event.getGuild(), userArgument);
		if (member == null) {
			event.reply("I could not find that user :no_entry:").queue();
		}
		
		String url = String.format("http://%s:8443/api/discord?image=%s&theme=%s&text=%s&colour=%s&name=%s&bot=%s", Settings.LOCAL_HOST, member.getUser().getEffectiveAvatarUrl(), white ? "white" : "dark",
				text, Integer.toHexString(member.getColorRaw()), member.getEffectiveName(), member.getUser().isBot());
		
		Request request;
		try {
			request = new Request.Builder().url(new URL(url)).build();
		} catch (MalformedURLException e) {
			event.reply("Oops something went wrong there, try again :no_entry:").queue();
			return;
		}
		
		event.getTextChannel().sendTyping().queue($ -> {
			client.newCall(request).enqueue((Sx4Callback) response -> {
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
			client.newCall(request).enqueue((Sx4Callback) response -> {
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
			client.newCall(request).enqueue((Sx4Callback) response -> {
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
			client.newCall(request).enqueue((Sx4Callback) response -> {
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
			client.newCall(request).enqueue((Sx4Callback) response -> {
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
			client.newCall(request).enqueue((Sx4Callback) response -> {
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
			client.newCall(request).enqueue((Sx4Callback) response -> {
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
			client.newCall(request).enqueue((Sx4Callback) response -> {
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
			client.newCall(request).enqueue((Sx4Callback) response -> {
				if (response.code() == 200) {
					random.setSeed(secondMember.getUser().getIdLong() + firstMember.getUser().getIdLong());
					int shipPercentage = random.nextInt(100) + 1;
					
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
			client.newCall(request).enqueue((Sx4Callback) response -> {
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
			client.newCall(request).enqueue((Sx4Callback) response -> {
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
			client.newCall(request).enqueue((Sx4Callback) response -> {
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
			client.newCall(request).enqueue((Sx4Callback) response -> {
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
			client.newCall(request).enqueue((Sx4Callback) response -> {
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
	@Cooldown(value=7)
	@BotPermissions({Permission.MESSAGE_ATTACH_FILES})
	public void tweet(CommandEvent event, @Argument(value="user") String userArgument, @Argument(value="text", endless=true) String text) {
		Member member = ArgumentUtils.getMember(event.getGuild(), userArgument);
		if (member == null) {
			event.reply("I could not find that user :no_entry:").queue();
			return;
		}
		
		int likes = random.nextInt(event.getGuild().getMembers().size()) + 1;
		int retweets = random.nextInt(event.getGuild().getMembers().size()) + 1;
		
		List<String> avatarUrls = new ArrayList<>();
		int minimumAmount = Math.min(Math.min(event.getGuild().getMembers().size(), likes), 10);
		if (minimumAmount == event.getGuild().getMembers().size()) {
			for (int i = 0; i < minimumAmount; i++) {
				avatarUrls.add(event.getGuild().getMembers().get(i).getUser().getEffectiveAvatarUrl() + "?size=64");
			}
		} else {
			while (avatarUrls.size() != minimumAmount) {
				int randomInt = random.nextInt(event.getGuild().getMembers().size());
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
			client.newCall(request).enqueue((Sx4Callback) response -> {
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
	@Cooldown(value=3)
	@BotPermissions({Permission.MESSAGE_ATTACH_FILES, Permission.MESSAGE_EMBED_LINKS})
	public void colour(CommandEvent event, @Argument(value="hex | rgb", endless=true, nullDefault=true) String argument) {
		Color colour;
		if (argument == null) {
			colour = new Color(random.nextFloat(), random.nextFloat(), random.nextFloat());
		} else {
			colour = ArgumentUtils.getColourFromString(argument);
			if (colour == null) {
				event.reply("Invalid hex or RGB value :no_entry:").queue();
				return;
			}
		}
		
		String hex = Integer.toHexString(colour.hashCode()).toUpperCase().substring(2);
		
		Request request = new Request.Builder().url("http://" + Settings.LOCAL_HOST + ":8443/api/colour?hex=" + hex).build();
		
		event.getTextChannel().sendTyping().queue($ -> {
			client.newCall(request).enqueue((Sx4Callback) response -> {
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
			client.newCall(request).enqueue((Sx4Callback) response -> {
				if (response.code() == 200) {
					int colourRaw;
					colourRaw = Integer.parseInt(response.body().string());
					
					EmbedBuilder embed = new EmbedBuilder();
					embed.setThumbnail(newUrl);
					embed.setColor(colourRaw);
					embed.setTitle("Most Common Colour");
					embed.setDescription("Hex: #" + Integer.toHexString(colourRaw).substring(2).toUpperCase() + "\nRGB: " + GeneralUtils.getRGB(colourRaw));		
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
	@Cooldown(value=3)
	@BotPermissions({Permission.MESSAGE_ATTACH_FILES})
	public void scroll(CommandEvent event, @Argument(value="text", endless=true) String text) {
		if (text.length() > 30) {
			event.reply("The text cannot be longer than 30 characters :no_entry:").queue();
			return;
		}
		
		Request request = new Request.Builder().url("http://" + Settings.LOCAL_HOST + ":8443/api/scroll?text=" + text).build();
		
		event.getTextChannel().sendTyping().queue($ -> {
			client.newCall(request).enqueue((Sx4Callback) response -> {
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
			client.newCall(request).enqueue((Sx4Callback) response -> {
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

	@Initialize(all=true)
	public void initialize(CommandImpl command) {
	    command.setCategory(Categories.IMAGE);
	}
	
}
