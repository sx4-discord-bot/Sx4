package com.sx4.bot.commands.misc;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.option.Option;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.http.HttpCallback;
import net.dv8tion.jda.api.entities.Message;
import okhttp3.Request;

import java.lang.reflect.Method;
import java.util.Arrays;

public class SourceCommand extends Sx4Command {

	public SourceCommand() {
		super("source", 213);

		super.setDescription("Gets the GitHub source code for a command");
		super.setExamples("source help", "source auto role", "source dictionary");
		super.setCategoryAll(ModuleCategory.MISC);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="command", endless=true) Sx4Command command, @Option(value="display", description="Sends the full command in discord") boolean display) {
		Method method = command.getCommandMethod();
		String path = method.getDeclaringClass().getName();

		String className = path.substring(path.lastIndexOf('.'));
		String[] classes = className.split("\\$");
		String lastClassName = classes[classes.length - 1];

		String filePath = path.replace(".", "/").substring(0, classes.length == 1 ? path.length() : path.indexOf("$")) + ".java";
		String fullPath = "rewrite/Sx4/src/main/java/" + filePath;

		Request request = new Request.Builder()
			.url("https://raw.githubusercontent.com/sx4-discord-bot/Sx4/" + fullPath)
			.build();

		event.getClient().newCall(request).enqueue((HttpCallback) response -> {
			String code = response.body().string();
			String[] lines = code.split("\n");

			int classIndex = code.indexOf("class " + lastClassName);

			int startBracketCount = 0, endBracketCount = 0, startLine = 0, leading = 0;
			for (int i = 0; i < lines.length; i++) {
				String line = lines[i];

				boolean foundLine = startBracketCount != 0 || endBracketCount != 0;
				if (code.indexOf(line) < classIndex && !foundLine) {
					continue;
				}

				if (!line.contains(method.getName() + "(Sx4CommandEvent") && !foundLine) {
					continue;
				}

				if (display) {
					if (!foundLine) {
						leading = line.indexOf("public");
						startLine = i;
					}

					if (!line.isBlank()) {
						lines[i] = line.substring(leading);
					}

					for (char character : line.toCharArray()) {
						if (character == '{') {
							startBracketCount++;
						}

						if (character == '}') {
							endBracketCount++;
						}
					}

					if (endBracketCount == startBracketCount) {
						String commandCode = String.join("\n",  Arrays.copyOfRange(lines, startLine, i + 1));

						event.reply("```java\n" + commandCode.substring(0, Math.min(commandCode.length(), Message.MAX_CONTENT_LENGTH - 11)) + "```").queue();
						break;
					}
				} else {
					event.reply("https://github.com/sx4-discord-bot/Sx4/blob/" + fullPath + "#L" + (i + 1)).queue();
					break;
				}
			}
		});
	}

}
